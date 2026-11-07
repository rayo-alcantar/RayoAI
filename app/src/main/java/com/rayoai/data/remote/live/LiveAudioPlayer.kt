@file:Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")

package com.rayoai.data.remote.live

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reproduce el audio PCM devuelto por Gemini Live a 24 kHz Mono,
 * utilizando un búfer anti-jitter fluido que no fragmenta el audio
 * y prioridad para auriculares Bluetooth de comunicación sobre la salida del teléfono.
 */
class LiveAudioPlayer(
    private val context: Context,
    initialOutputMode: String = "speaker",
    private val onSpeakingStateChanged: (Boolean) -> Unit = {}
) {
    companion object {
        const val SAMPLE_RATE = 24_000
        private const val TAG = "LiveAudioPlayer"

        // Búfer inicial suave (280 ms = 13,440 bytes) para absorber el jitter de red
        // antes de iniciar la reproducción de un nuevo turno de voz y evitar cortes.
        private const val INITIAL_PREBUFFER_BYTES = SAMPLE_RATE * 2 * 280 / 1000 // 13,440 bytes
        // Tiempo máximo de espera entre chunks (ms) antes de considerar terminado el turno
        private const val CHUNK_WAIT_TIMEOUT_MS = 400L
    }

    private val isPlaying = AtomicBoolean(false)
    private val isSpeaking = AtomicBoolean(false)
    private val isMuted = AtomicBoolean(initialOutputMode == "muted")
    private var currentMode: String = if (initialOutputMode == "muted") "speaker" else initialOutputMode

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var legacyScoRequested = false
    private var audioDeviceCallbackRegistered = false
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = updateRouting()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = updateRouting()
    }
    private var audioTrack: AudioTrack? = null
    private var playbackThread: Thread? = null

    private val lock = Any()
    private val chunks = ArrayDeque<ByteArray>()
    private var bufferedBytes = 0
    private var playbackActive = false

    @Volatile
    private var lastAudioWriteTime: Long = 0L

    @Synchronized
    fun start() {
        if (isPlaying.getAndSet(true)) return

        updateRouting()
        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
            audioDeviceCallbackRegistered = true
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudieron observar cambios de dispositivos de audio", e)
        }
        createTrack()

        synchronized(lock) {
            chunks.clear()
            bufferedBytes = 0
            playbackActive = false
            isSpeaking.set(false)
        }

        playbackThread = Thread(::playbackLoop, "RayoAI-LivePlayer").apply {
            isDaemon = true
            start()
        }
    }

    private fun createTrack() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = minBufferSize.coerceAtLeast(SAMPLE_RATE * 2) // 48,000 bytes (1s)

        try {
            // USAGE_VOICE_COMMUNICATION vincula la reproducción al subsistema VoIP/AEC
            // de Android para que el hardware AcousticEchoCanceler cancele este sonido
            // del micrófono tanto en modo altavoz como en auricular.
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
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
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando AudioTrack", e)
        }
    }

    @Synchronized
    @Suppress("DEPRECATION") // SCO en Android 10 y 11.
    private fun updateRouting() {
        if (!isPlaying.get()) return
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val bluetooth = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        it.type == AudioDeviceInfo.TYPE_HEARING_AID
                }
                if (bluetooth != null && audioManager.setCommunicationDevice(bluetooth)) return

                val phoneOutput = if (currentMode == "earpiece") {
                    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                } else {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                val fallback = devices.firstOrNull { it.type == phoneOutput }
                if (fallback == null || !audioManager.setCommunicationDevice(fallback)) {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                val hasSco = audioManager.isBluetoothScoAvailableOffCall &&
                    audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                if (hasSco) {
                    audioManager.isSpeakerphoneOn = false
                    if (!legacyScoRequested) {
                        audioManager.startBluetoothSco()
                        legacyScoRequested = true
                    }
                    audioManager.isBluetoothScoOn = true
                } else {
                    releaseLegacySco()
                    audioManager.isSpeakerphoneOn = currentMode != "earpiece"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error aplicando ruteo de audio", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun releaseLegacySco() {
        if (!legacyScoRequested) return
        legacyScoRequested = false
        try {
            audioManager.isBluetoothScoOn = false
        } finally {
            audioManager.stopBluetoothSco()
        }
    }

    @Synchronized
    fun setAudioOutput(mode: String) {
        if (mode == "muted") {
            isMuted.set(true)
            clear()
            return
        }

        currentMode = mode
        updateRouting()
        isMuted.set(false)
    }

    fun enqueueAudio(pcmData: ByteArray) {
        if (!isPlaying.get() || isMuted.get() || pcmData.isEmpty()) return
        synchronized(lock) {
            chunks.addLast(pcmData)
            bufferedBytes += pcmData.size

            if (!playbackActive) {
                if (bufferedBytes >= INITIAL_PREBUFFER_BYTES) {
                    playbackActive = true
                    (lock as java.lang.Object).notifyAll()
                }
            } else {
                (lock as java.lang.Object).notifyAll()
            }
        }
    }

    fun flush() {
        synchronized(lock) {
            if (bufferedBytes > 0) {
                playbackActive = true
            }
            (lock as java.lang.Object).notifyAll()
        }
    }

    fun clear() {
        synchronized(lock) {
            chunks.clear()
            bufferedBytes = 0
            playbackActive = false
            lastAudioWriteTime = 0L
            if (isSpeaking.getAndSet(false)) {
                try {
                    onSpeakingStateChanged(false)
                } catch (e: Exception) {
                    Log.w(TAG, "Error notificando cambio de habla", e)
                }
            }
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

    /**
     * Informa si el reproductor está emitiendo o a punto de emitir audio.
     * Incluye una ventana de gracia tras el último write para cubrir la salida física del altavoz.
     */
    fun isSpeakingAudio(): Boolean {
        if (isSpeaking.get()) return true
        synchronized(lock) {
            if (bufferedBytes > 0 || chunks.isNotEmpty()) return true
        }
        if (lastAudioWriteTime == 0L) return false
        return (SystemClock.elapsedRealtime() - lastAudioWriteTime) < 350L
    }

    @Synchronized
    @Suppress("DEPRECATION")
    fun stop() {
        if (!isPlaying.getAndSet(false)) return
        if (audioDeviceCallbackRegistered) {
            runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
            audioDeviceCallbackRegistered = false
        }
        synchronized(lock) {
            chunks.clear()
            bufferedBytes = 0
            playbackActive = false
            if (isSpeaking.getAndSet(false)) {
                try {
                    onSpeakingStateChanged(false)
                } catch (_: Exception) {}
            }
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
            } else {
                releaseLegacySco()
                audioManager.isSpeakerphoneOn = false
            }
        } catch (_: Exception) {
        } finally {
            runCatching { audioManager.mode = AudioManager.MODE_NORMAL }
        }
    }

    private fun playbackLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)

        while (isPlaying.get()) {
            var chunk: ByteArray? = null

            synchronized(lock) {
                while (isPlaying.get() && (!playbackActive || chunks.isEmpty())) {
                    if (chunks.isEmpty() && playbackActive) {
                        // El búfer se vació momentáneamente: esperamos hasta CHUNK_WAIT_TIMEOUT_MS
                        // por el siguiente chunk de red sin cortar bruscamente ni reiniciar a 720ms.
                        try {
                            (lock as java.lang.Object).wait(CHUNK_WAIT_TIMEOUT_MS)
                        } catch (e: InterruptedException) {
                            return
                        }

                        // Si tras la espera sigue vacío, el turno de respuesta terminó
                        if (chunks.isEmpty()) {
                            playbackActive = false
                            if (isSpeaking.getAndSet(false)) {
                                try {
                                    onSpeakingStateChanged(false)
                                } catch (_: Exception) {}
                            }
                        }
                    } else {
                        // Esperando el prebuffer inicial del siguiente turno
                        if (isSpeaking.getAndSet(false)) {
                            try {
                                onSpeakingStateChanged(false)
                            } catch (_: Exception) {}
                        }
                        try {
                            (lock as java.lang.Object).wait(100)
                        } catch (e: InterruptedException) {
                            return
                        }
                    }
                }

                if (!isPlaying.get()) return
                if (chunks.isNotEmpty()) {
                    val nextChunk = chunks.removeFirst()
                    chunk = nextChunk
                    bufferedBytes -= nextChunk.size
                    if (!isSpeaking.getAndSet(true)) {
                        try {
                            onSpeakingStateChanged(true)
                        } catch (_: Exception) {}
                    }
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
                        lastAudioWriteTime = SystemClock.elapsedRealtime()
                    } else {
                        break
                    }
                }
            }
        }
        if (isSpeaking.getAndSet(false)) {
            try {
                onSpeakingStateChanged(false)
            } catch (_: Exception) {}
        }
    }
}
