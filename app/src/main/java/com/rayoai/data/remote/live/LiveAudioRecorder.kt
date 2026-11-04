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
 * además de una compuerta temporal inicial para no interrumpir el saludo inicial de RayoAI.
 */
class LiveAudioRecorder(
    private val pushToTalk: Boolean = false,
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
    }

    private val isRecording = AtomicBoolean(false)
    private val isTalking = AtomicBoolean(!pushToTalk)
    private val isMuted = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingThread: Thread? = null
    private var acceptInputAt: Long = 0L

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
                // Fallback a MIC si VOICE_COMMUNICATION falla
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

            // En modo continuo manos libres, no enviar audio durante los primeros 1.8s
            // para que el saludo inicial ("Hola, soy RayoAI...") no sea cortado por eco ambiental
            acceptInputAt = if (pushToTalk) 0L else SystemClock.elapsedRealtime() + INITIAL_GREETING_SUPPRESSION_MS

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
                // Si estamos dentro del período de protección del saludo inicial, descartar fragmento
                if (!pushToTalk && SystemClock.elapsedRealtime() < acceptInputAt) {
                    continue
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
}
