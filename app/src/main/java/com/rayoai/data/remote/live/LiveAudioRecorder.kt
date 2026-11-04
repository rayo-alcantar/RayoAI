package com.rayoai.data.remote.live

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Graba audio del micrófono a 16 kHz, 16-bit PCM Mono,
 * activando cancelación acústica de eco (AEC) y supresión de ruido (NS) por hardware,
 * además de una compuerta inteligente de energía RMS que evita el eco del altavoz
 * pero permite la interrupción intencional del usuario (barge-in por voz).
 */
class LiveAudioRecorder(
    private val pushToTalk: Boolean = false,
    private val isSpeakerPlaying: () -> Boolean = { false },
    private val onUserInterrupted: () -> Unit = {},
    private val onAudioChunk: (ByteArray) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {
    companion object {
        const val SAMPLE_RATE = 16_000
        const val BLOCK_MS = 40
        const val CHUNK_BYTES = SAMPLE_RATE * 2 * BLOCK_MS / 1000 // 1280 bytes
        private const val TAG = "LiveAudioRecorder"
        // Breve margen de estabilización para el inicio de AudioRecord (350 ms)
        private const val INITIAL_WARMUP_MS = 350L
        // Umbral RMS base sensible para interrupción por voz natural
        private const val MIN_BARGE_IN_RMS = 170.0
        // Umbral RMS para interrupción inmediata en un solo frame (40 ms)
        private const val IMMEDIATE_BARGE_IN_RMS = 280.0
        // Frames de pre-roll para no perder el inicio de la primera palabra al interrumpir (2 frames = 80 ms)
        private const val PRE_ROLL_FRAMES = 2
    }

    private val isRecording = AtomicBoolean(false)
    private val isTalking = AtomicBoolean(!pushToTalk)
    private val isMuted = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingThread: Thread? = null
    private var acceptInputAt: Long = 0L
    private var consecutiveSpeechFrames = 0
    private var echoNoiseFloor = 80.0

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRecording.getAndSet(true)) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(CHUNK_BYTES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            val record = audioRecord ?: throw IllegalStateException("AudioRecord no disponible.")
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord no se pudo inicializar en ningún modo.")
            }

            // Habilitar cancelación de eco acústico (AEC) por hardware si está disponible
            val sessionId = record.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                        enabled = true
                    }
                    Log.d(TAG, "AcousticEchoCanceler habilitado exitosamente en sesión $sessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo activar AcousticEchoCanceler", e)
                }
            }

            // Habilitar supresor de ruido (NS) por hardware si está disponible
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                        enabled = true
                    }
                    Log.d(TAG, "NoiseSuppressor habilitado exitosamente en sesión $sessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo activar NoiseSuppressor", e)
                }
            }

            // Período de gracia mínimo para la inicialización
            acceptInputAt = if (pushToTalk) 0L else SystemClock.elapsedRealtime() + INITIAL_WARMUP_MS
            consecutiveSpeechFrames = 0

            record.startRecording()
            recordingThread = Thread(::recordLoop, "RayoAI-LiveRecorder").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            isRecording.set(false)
            Log.e(TAG, "Error iniciando grabación de audio", e)
            onError(e)
        }
    }

    fun setTalking(talking: Boolean) {
        if (pushToTalk) {
            isTalking.set(talking)
            if (talking) {
                try {
                    onUserInterrupted()
                } catch (e: Exception) {
                    Log.w(TAG, "Error en callback de interrupción PTT", e)
                }
            }
        }
    }

    fun setMuted(muted: Boolean) {
        isMuted.set(muted)
    }

    fun stop() {
        isRecording.set(false)
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error deteniendo AudioRecord", e)
        }
        try {
            echoCanceler?.release()
        } catch (_: Exception) {}
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (_: Exception) {}
        noiseSuppressor = null

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando AudioRecord", e)
        }
        audioRecord = null
        recordingThread?.interrupt()
        recordingThread = null
    }

    private fun recordLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val buffer = ByteArray(CHUNK_BYTES)
        val preRollQueue = ArrayDeque<ByteArray>(PRE_ROLL_FRAMES)

        while (isRecording.get()) {
            val record = audioRecord ?: break
            val read = record.read(buffer, 0, buffer.size)

            if (read > 0) {
                // Período de protección de inicialización
                if (!pushToTalk && SystemClock.elapsedRealtime() < acceptInputAt) {
                    continue
                }

                val payload = if (read == buffer.size) buffer.clone() else buffer.copyOf(read)

                if (!pushToTalk && isSpeakerPlaying()) {
                    // El altavoz está emitiendo la respuesta de Gemini.
                    // Para evitar que el eco residual del altavoz confunda al servidor y cause cortes en pedacitos,
                    // filtramos los paquetes mientras no haya voz humana intencional.
                    val rms = calculateRms(buffer, read)
                    // Adaptación suave del piso de eco residual del altavoz
                    if (rms < 200.0) {
                        echoNoiseFloor = echoNoiseFloor * 0.9 + rms * 0.1
                    }
                    val effectiveThreshold = (echoNoiseFloor * 1.6).coerceIn(MIN_BARGE_IN_RMS, 260.0)
                    val immediateThreshold = (echoNoiseFloor * 2.2).coerceIn(IMMEDIATE_BARGE_IN_RMS, 380.0)

                    if (rms >= effectiveThreshold) {
                        consecutiveSpeechFrames++
                        if (consecutiveSpeechFrames >= PRE_ROLL_FRAMES || rms >= immediateThreshold) {
                            Log.d(TAG, "Barge-in detectado (RMS: ${rms.toInt()} vs umbral: ${effectiveThreshold.toInt()}). Silenciando altavoz.")
                            try {
                                onUserInterrupted()
                            } catch (e: Exception) {
                                Log.w(TAG, "Error en callback de interrupción", e)
                            }
                            consecutiveSpeechFrames = 0

                            // Entregar los frames previos del pre-roll para no perder el inicio de la primera palabra
                            if (!isMuted.get()) {
                                while (preRollQueue.isNotEmpty()) {
                                    try {
                                        onAudioChunk(preRollQueue.removeFirst())
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error entregando chunk pre-roll", e)
                                    }
                                }
                                try {
                                    onAudioChunk(payload)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error entregando chunk de audio", e)
                                }
                            }
                        } else {
                            // Guardar en pre-roll por si el siguiente frame confirma el inicio de voz
                            if (preRollQueue.size >= PRE_ROLL_FRAMES) preRollQueue.removeFirst()
                            preRollQueue.addLast(payload)
                        }
                    } else {
                        consecutiveSpeechFrames = 0
                        if (preRollQueue.size >= PRE_ROLL_FRAMES) preRollQueue.removeFirst()
                        preRollQueue.addLast(payload)
                        // No enviamos el eco residual del altavoz a Gemini: esto elimina los cortes por pedacitos
                    }
                } else {
                    consecutiveSpeechFrames = 0
                    preRollQueue.clear()
                    // Altavoz en silencio (o PTT): entregar directamente a Gemini
                    if (!isMuted.get() && (!pushToTalk || isTalking.get())) {
                        try {
                            onAudioChunk(payload)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error entregando chunk de audio", e)
                        }
                    }
                }
            } else if (read < 0) {
                Log.w(TAG, "AudioRecord error de lectura: $read")
                break
            }
        }
    }

    private fun calculateRms(buffer: ByteArray, read: Int): Double {
        var sum = 0.0
        val numSamples = read / 2
        if (numSamples == 0) return 0.0
        for (i in 0 until numSamples) {
            val sample = ((buffer[i * 2 + 1].toInt() shl 8) or (buffer[i * 2].toInt() and 0xFF)).toShort()
            sum += sample * sample
        }
        return Math.sqrt(sum / numSamples)
    }
}
