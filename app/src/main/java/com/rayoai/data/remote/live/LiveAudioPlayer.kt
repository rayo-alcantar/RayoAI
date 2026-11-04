package com.rayoai.data.remote.live

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reproduce el audio PCM devuelto por Gemini Live a 24 kHz Mono,
 * utilizando un búfer de pre-reproducción (jitter prebuffer) para evitar cortes
 * y micro-pausas durante la transmisión, idéntico al mecanismo de RayoAI para Windows.
 */
class LiveAudioPlayer {
    companion object {
        const val SAMPLE_RATE = 24_000
        private const val TAG = "LiveAudioPlayer"

        // Búfer inicial (360 ms) y búfer de recuperación tras falta de datos (720 ms)
        private const val INITIAL_PREBUFFER_BYTES = SAMPLE_RATE * 2 * 360 / 1000 // 17,280 bytes
        private const val REBUFFER_BYTES = SAMPLE_RATE * 2 * 720 / 1000          // 34,560 bytes
    }

    private val isPlaying = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    private val lock = Any()
    private val chunks = ArrayDeque<ByteArray>()
    private var bufferedBytes = 0
    private var playbackReady = false
    private var targetPrebufferBytes = INITIAL_PREBUFFER_BYTES

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
                        // USAGE_VOICE_COMMUNICATION es imprescindible para que el AcousticEchoCanceler
                        // por hardware de Android cancele este audio del micrófono del dispositivo.
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
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

            synchronized(lock) {
                chunks.clear()
                bufferedBytes = 0
                playbackReady = false
                targetPrebufferBytes = INITIAL_PREBUFFER_BYTES
            }

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
        synchronized(lock) {
            chunks.addLast(pcmData)
            bufferedBytes += pcmData.size
            if (!playbackReady && bufferedBytes >= targetPrebufferBytes) {
                playbackReady = true
            }
            (lock as java.lang.Object).notifyAll()
        }
    }

    /**
     * Vuelca y reproduce cualquier fragmento restante cuando Gemini
     * completa su turno o generación, asegurando que no se pierda la última frase.
     */
    fun flush() {
        synchronized(lock) {
            if (bufferedBytes > 0) {
                playbackReady = true
            }
            (lock as java.lang.Object).notifyAll()
        }
    }

    /**
     * Vaciado inmediato del audio cuando Gemini detecta una interrupción
     * del usuario o cuando se pausa/cancela el turno.
     */
    fun clear() {
        synchronized(lock) {
            chunks.clear()
            bufferedBytes = 0
            playbackReady = false
            targetPrebufferBytes = INITIAL_PREBUFFER_BYTES
            isSpeaking.set(false)
            (lock as java.lang.Object).notifyAll()
        }
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error al vaciar búfer de audio", e)
        }
    }

    fun isSpeakingAudio(): Boolean = isSpeaking.get()

    fun stop() {
        isPlaying.set(false)
        synchronized(lock) {
            chunks.clear()
            bufferedBytes = 0
            playbackReady = false
            isSpeaking.set(false)
            (lock as java.lang.Object).notifyAll()
        }
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
            var chunk: ByteArray? = null
            synchronized(lock) {
                while (isPlaying.get() && (!playbackReady || chunks.isEmpty())) {
                    isSpeaking.set(false)
                    if (chunks.isEmpty() && playbackReady) {
                        playbackReady = false
                        targetPrebufferBytes = REBUFFER_BYTES
                    }
                    try {
                        (lock as java.lang.Object).wait(200)
                    } catch (e: InterruptedException) {
                        return
                    }
                }

                if (!isPlaying.get()) return
                if (chunks.isNotEmpty()) {
                    val nextChunk = chunks.removeFirst()
                    chunk = nextChunk
                    bufferedBytes -= nextChunk.size
                    isSpeaking.set(true)
                }
            }

            val toWrite = chunk
            if (toWrite != null && toWrite.isNotEmpty()) {
                val track = audioTrack ?: break
                var written = 0
                while (written < toWrite.size && isPlaying.get()) {
                    val res = track.write(toWrite, written, toWrite.size - written)
                    if (res > 0) {
                        written += res
                    } else {
                        break
                    }
                }
            }
        }
        isSpeaking.set(false)
    }
}
