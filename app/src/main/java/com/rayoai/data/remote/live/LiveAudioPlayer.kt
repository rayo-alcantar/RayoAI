package com.rayoai.data.remote.live

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reproduce el audio PCM devuelto por Gemini Live a 24 kHz Mono,
 * utilizando un búfer de pre-reproducción (jitter prebuffer) y ruteo flexible
 * entre Altavoz (multimedia/conversación sin bloqueo de TalkBack ni sensor de proximidad),
 * Auricular privado de llamada, o Silenciado.
 */
class LiveAudioPlayer(
    private val context: Context,
    initialOutputMode: String = "speaker"
) {
    companion object {
        const val SAMPLE_RATE = 24_000
        private const val TAG = "LiveAudioPlayer"

        // Búfer inicial (360 ms) y búfer de recuperación tras falta de datos (720 ms)
        private const val INITIAL_PREBUFFER_BYTES = SAMPLE_RATE * 2 * 360 / 1000 // 17,280 bytes
        private const val REBUFFER_BYTES = SAMPLE_RATE * 2 * 720 / 1000          // 34,560 bytes
    }

    private val isPlaying = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(initialOutputMode == "muted")
    private var currentMode: String = initialOutputMode

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    private val lock = Any()
    private val chunks = ArrayDeque<ByteArray>()
    private var bufferedBytes = 0
    private var playbackReady = false
    private var targetPrebufferBytes = INITIAL_PREBUFFER_BYTES

    fun start() {
        if (isPlaying.getAndSet(true)) return

        createTrack(currentMode)
        applyRouting(currentMode)

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
    }

    private fun createTrack(mode: String) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE * 2)

        try {
            audioTrack?.release()
        } catch (_: Exception) {}

        try {
            val usage = if (mode == "earpiece") {
                AudioAttributes.USAGE_VOICE_COMMUNICATION
            } else {
                // USAGE_MEDIA reproduce fuerte por el altavoz exterior,
                // sin activar el sensor de proximidad ni cortar o atenuar TalkBack al tocar la pantalla.
                AudioAttributes.USAGE_MEDIA
            }

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(usage)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando AudioTrack en modo $mode", e)
        }
    }

    private fun applyRouting(mode: String) {
        try {
            when (mode) {
                "earpiece" -> {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val earpiece = audioManager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                        }
                        if (earpiece != null) {
                            audioManager.setCommunicationDevice(earpiece)
                        }
                    }
                }
                else -> {
                    // "speaker" o "muted": aseguramos que el sistema vuelva a modo normal
                    // para no interferir con la síntesis de voz (TalkBack) ni con la pantalla
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val speaker = audioManager.availableCommunicationDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }
                        if (speaker != null) {
                            audioManager.setCommunicationDevice(speaker)
                        } else {
                            audioManager.clearCommunicationDevice()
                        }
                    }
                    audioManager.mode = AudioManager.MODE_NORMAL
                    @Suppress("DEPRECATION")
                    audioManager.isSpeakerphoneOn = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error aplicando ruteo de audio ($mode): ${e.message}")
        }
    }

    /**
     * Permite conmutar la salida dinámicamente durante la conversación
     * entre "speaker" (altavoz), "earpiece" (auricular) o "muted" (silenciado).
     */
    fun setAudioOutput(mode: String) {
        if (mode == "muted") {
            isMuted.set(true)
            clear()
            return
        }

        isMuted.set(false)
        if (mode != currentMode) {
            currentMode = mode
            createTrack(mode)
            applyRouting(mode)
        }
    }

    fun enqueueAudio(pcmData: ByteArray) {
        if (!isPlaying.get() || isMuted.get() || pcmData.isEmpty()) return
        synchronized(lock) {
            chunks.addLast(pcmData)
            bufferedBytes += pcmData.size
            if (!playbackReady && bufferedBytes >= targetPrebufferBytes) {
                playbackReady = true
            }
            (lock as java.lang.Object).notifyAll()
        }
    }

    fun flush() {
        synchronized(lock) {
            if (bufferedBytes > 0) {
                playbackReady = true
            }
            (lock as java.lang.Object).notifyAll()
        }
    }

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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {}
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
