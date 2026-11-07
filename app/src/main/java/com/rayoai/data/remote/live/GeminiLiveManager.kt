package com.rayoai.data.remote.live

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.rayoai.domain.model.live.LiveModeConfig
import com.rayoai.domain.model.live.LiveSessionState
import com.rayoai.domain.model.live.VisualSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gestor centralizado del Modo Live en tiempo real.
 * Coordina el cliente WebSocket, la grabación de audio, la reproducción
 * y el flujo de fotogramas visuales (cámara o pantalla).
 */
@Singleton
class GeminiLiveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "GeminiLiveManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _sessionState = MutableStateFlow(LiveSessionState.STOPPED)
    val sessionState: StateFlow<LiveSessionState> = _sessionState.asStateFlow()

    private val _visualSource = MutableStateFlow(VisualSource.CAMERA)
    val visualSource: StateFlow<VisualSource> = _visualSource.asStateFlow()

    private val _isVideoPaused = MutableStateFlow(false)
    val isVideoPaused: StateFlow<Boolean> = _isVideoPaused.asStateFlow()

    private val _isMicMuted = MutableStateFlow(false)
    val isMicMuted: StateFlow<Boolean> = _isMicMuted.asStateFlow()

    private val _isTalking = MutableStateFlow(false)
    val isTalking: StateFlow<Boolean> = _isTalking.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _latestTranscript = MutableStateFlow("")
    val latestTranscript: StateFlow<String> = _latestTranscript.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _audioOutput = MutableStateFlow("speaker")
    val audioOutput: StateFlow<String> = _audioOutput.asStateFlow()

    private var client: GeminiLiveClient? = null
    private var recorder: LiveAudioRecorder? = null
    private var player: LiveAudioPlayer? = null
    private var sessionToken: Any? = null

    @Synchronized
    fun startSession(config: LiveModeConfig) {
        if (sessionToken != null) return

        val token = Any()
        sessionToken = token
        _errorMessage.value = null
        _latestTranscript.value = ""
        _isVideoPaused.value = false
        _isMicMuted.value = false
        _isSpeaking.value = false
        _audioOutput.value = config.audioOutput

        player = LiveAudioPlayer(
            context = context,
            initialOutputMode = config.audioOutput,
            onSpeakingStateChanged = { speaking ->
                _isSpeaking.value = speaking
            }
        ).apply { start() }

        client = GeminiLiveClient(
            config = config,
            okHttpClient = okHttpClient,
            onStateChanged = { state ->
                withActiveSession(token) {
                    _sessionState.value = state
                    if (state == LiveSessionState.RUNNING) {
                        recorder?.start()
                    }
                }
            },
            onAudioReceived = { pcm ->
                withActiveSession(token) { player?.enqueueAudio(pcm) }
            },
            onTextReceived = { text ->
                withActiveSession(token) { _latestTranscript.value = text }
            },
            onTurnComplete = {
                withActiveSession(token) { player?.flush() }
            },
            onInterrupted = {
                withActiveSession(token) {
                    player?.clear()
                    _isSpeaking.value = false
                }
            },
            onError = { err ->
                withActiveSession(token) {
                    _errorMessage.value = err
                    stopSession()
                }
            }
        )

        recorder = LiveAudioRecorder(
            pushToTalk = config.pushToTalk,
            isSpeakerPlaying = { player?.isSpeakingAudio() == true },
            onUserInterrupted = {
                withActiveSession(token) { interruptCurrentResponse() }
            },
            onAudioChunk = { chunk ->
                withActiveSession(token) {
                    if (!_isMicMuted.value) client?.sendAudio(chunk)
                }
            },
            onError = { exc ->
                Log.e(TAG, "Error en grabadora de audio: ${exc.message}")
            }
        )

        client?.start()
    }

    private inline fun withActiveSession(token: Any, action: () -> Unit) {
        synchronized(this) {
            if (sessionToken === token) action()
        }
    }

    fun setVisualSource(source: VisualSource) {
        _visualSource.value = source
    }

    fun toggleVideoPause() {
        _isVideoPaused.value = !_isVideoPaused.value
    }

    fun toggleMicMute() {
        val newMuted = !_isMicMuted.value
        _isMicMuted.value = newMuted
        recorder?.setMuted(newMuted)
    }

    fun setAudioOutput(output: String) {
        _audioOutput.value = output
        player?.setAudioOutput(output)
    }

    fun setPushToTalkState(talking: Boolean) {
        if (_isTalking.value == talking) return
        _isTalking.value = talking
        recorder?.setTalking(talking)

        if (talking) {
            interruptCurrentResponse()
        } else {
            client?.sendActivityEnd()
        }
    }

    fun interruptCurrentResponse() {
        Log.d(TAG, "interruptCurrentResponse: cancelando reproducción de voz y descartando frames en tránsito.")
        player?.clear()
        _isSpeaking.value = false
        client?.cancelCurrentTurn()
    }

    fun sendBitmapFrame(bitmap: Bitmap, quality: Int = 72) {
        if (_sessionState.value != LiveSessionState.RUNNING) return
        if (_isVideoPaused.value || _visualSource.value == VisualSource.NONE) return

        scope.launch {
            try {
                val stream = ByteArrayOutputStream()
                // Redimensionar si es necesario para mantener latencia óptima
                val maxDim = 1024
                val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * scale).toInt(),
                        (bitmap.height * scale).toInt(),
                        true
                    )
                } else {
                    bitmap
                }

                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val bytes = stream.toByteArray()
                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                bitmap.recycle()
                client?.sendVideoFrame(bytes)
            } catch (e: Exception) {
                Log.w(TAG, "Error procesando frame visual", e)
            }
        }
    }

    fun sendJpegFrame(jpegBytes: ByteArray) {
        if (_sessionState.value != LiveSessionState.RUNNING) return
        if (_isVideoPaused.value || _visualSource.value == VisualSource.NONE) return
        client?.sendVideoFrame(jpegBytes)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    @Synchronized
    fun stopSession() {
        if (sessionToken == null) return
        sessionToken = null
        recorder?.stop()
        recorder = null
        player?.stop()
        player = null
        _isSpeaking.value = false
        client?.stop()
        client = null
        _isTalking.value = false
        _latestTranscript.value = ""
        _sessionState.value = LiveSessionState.STOPPED
    }
}
