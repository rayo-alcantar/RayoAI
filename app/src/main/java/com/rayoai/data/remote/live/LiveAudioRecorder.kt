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
        // 1.8 segundos de gracia para el saludo de bienvenida de RayoAI (igual que en rayoai_win)
        private const val INITIAL_GREETING_SUPPRESSION_MS = 1800L
        // Umbral RMS de energía sensible y natural para distinguir la voz del usuario frente al eco del altavoz
        private const val BARGE_IN_RMS_THRESHOLD = 650.0
    }

    private val isRecording = AtomicBoolean(false)
    private val isTalking = AtomicBoolean(!pushToTalk)
    private val isMuted = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingThread: Thread? = null
    private var acceptInputAt: Long = 0L
    private var highEnergyCount = 0

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

            // Período de gracia inicial para el saludo
            acceptInputAt = if (pushToTalk) 0L else SystemClock.elapsedRealtime() + INITIAL_GREETING_SUPPRESSION_MS
            highEnergyCount = 0

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

        while (isRecording.get()) {
            val record = audioRecord ?: break
            val read = record.read(buffer, 0, buffer.size)

            if (read > 0) {
                // Período de protección del saludo inicial
                if (!pushToTalk && SystemClock.elapsedRealtime() < acceptInputAt) {
                    continue
                }

                if (!pushToTalk && isSpeakerPlaying()) {
                    // El altavoz está emitiendo la respuesta de Gemini.
                    // Calculamos la energía RMS del fragmento para distinguir si es eco del altavoz
                    // o si el usuario está hablando intencionalmente por encima para interrumpir.
                    val rms = calculateRms(buffer, read)
                    if (rms >= BARGE_IN_RMS_THRESHOLD) {
                        Log.d(TAG, "Interrupción intencional del usuario detectada (RMS: ${rms.toInt()}). Deteniendo altavoz.")
                        try {
                            onUserInterrupted()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error en callback de interrupción", e)
                        }
                    } else {
                        // El altavoz suena y el micrófono solo capta eco residual: no enviar este chunk a Gemini.
                        continue
                    }
                }

                if (!isMuted.get() && (!pushToTalk || isTalking.get())) {
                    val payload = if (read == buffer.size) buffer.clone() else buffer.copyOf(read)
                    try {
                        onAudioChunk(payload)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error entregando chunk de audio", e)
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
