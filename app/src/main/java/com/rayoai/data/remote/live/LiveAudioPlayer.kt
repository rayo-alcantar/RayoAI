package com.rayoai.data.remote.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reproduce el audio PCM devuelto por Gemini Live a 24 kHz Mono,
 * soportando interrupciones inmediatas (VAD) y vaciado instantáneo del búfer.
 */
class LiveAudioPlayer {
    companion object {
        const val SAMPLE_RATE = 24_000
        private const val TAG = "LiveAudioPlayer"
    }

    private val isPlaying = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private val queue = LinkedBlockingQueue<ByteArray>()
    private var playbackThread: Thread? = null

    fun start() {
        if (isPlaying.getAndSet(true)) return

        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE * 2)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            playbackThread = Thread(::playbackLoop, "RayoAI-LivePlayer").apply {
                isDaemon = true
                start()
            }
        } catch (e: Exception) {
            isPlaying.set(false)
            Log.e(TAG, "Error iniciando reproductor de audio", e)
        }
    }

    fun enqueueAudio(pcmData: ByteArray) {
        if (!isPlaying.get() || pcmData.isEmpty()) return
        queue.offer(pcmData)
    }

    /**
     * Vaciado inmediato del audio cuando Gemini detecta una interrupción
     * del usuario o cuando se pausa/cancela el turno.
     */
    fun clear() {
        queue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error al vaciar búfer de audio", e)
        }
    }

    fun stop() {
        isPlaying.set(false)
        queue.clear()
        playbackThread?.interrupt()
        playbackThread = null

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error deteniendo AudioTrack", e)
        }
        try {
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error liberando AudioTrack", e)
        }
        audioTrack = null
    }

    private fun playbackLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        while (isPlaying.get()) {
            try {
                val chunk = queue.take()
                val track = audioTrack ?: break
                if (chunk.isNotEmpty()) {
                    var written = 0
                    while (written < chunk.size && isPlaying.get()) {
                        val res = track.write(chunk, written, chunk.size - written)
                        if (res > 0) {
                            written += res
                        } else {
                            break
                        }
                    }
                }
            } catch (e: InterruptedException) {
                break
            } catch (e: Exception) {
                Log.w(TAG, "Error en bucle de reproducción", e)
            }
        }
    }
}
