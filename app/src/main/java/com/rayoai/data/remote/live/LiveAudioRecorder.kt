package com.rayoai.data.remote.live

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Graba audio del micrófono a 16 kHz, 16-bit PCM Mono,
 * emitiendo fragmentos en tiempo real para la API Live de Gemini.
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
    }

    private val isRecording = AtomicBoolean(false)
    private val isTalking = AtomicBoolean(!pushToTalk)
    private val isMuted = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null

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

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw IllegalStateException("AudioRecord no se pudo inicializar en ningún modo.")
            }

            audioRecord?.startRecording()
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
