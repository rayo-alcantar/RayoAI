package com.rayoai.data.remote.live

import android.util.Base64
import android.util.Log
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.LivePromptBuilder
import com.rayoai.domain.model.live.LiveModeConfig
import com.rayoai.domain.model.live.LiveSessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cliente WebSocket de bajo nivel para comunicarse bidireccionalmente
 * con la API Gemini Multimodal Live (BidiGenerateContent).
 */
class GeminiLiveClient(
    private val config: LiveModeConfig,
    private val okHttpClient: OkHttpClient,
    private val onStateChanged: (LiveSessionState) -> Unit,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onTextReceived: (String) -> Unit = {},
    private val onInterrupted: () -> Unit = {},
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val LIVE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    private val isRunning = AtomicBoolean(false)
    private var sessionHandle: String? = null
    private var reconnectAttempts = 0
    private var isConnectedOnce = false
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeModel: String = config.model

    fun start() {
        if (isRunning.getAndSet(true)) return
        connect()
    }

    private fun connect() {
        if (!isRunning.get()) return
        onStateChanged(if (isConnectedOnce) LiveSessionState.RECONNECTING else LiveSessionState.STARTING)

        if (!isConnectedOnce) {
            activeModel = resolveLiveModel(config.model)
            Log.i(TAG, "Modelo Live configurado para la sesión: $activeModel")
        }
        openWebSocket()
    }

    private fun openWebSocket() {
        if (!isRunning.get()) return

        val url = "$LIVE_WS_URL?key=${config.apiKey.trim()}"
        val request = Request.Builder()
            .url(url)
            .addHeader("x-goog-api-key", config.apiKey.trim())
            .addHeader("Content-Type", "application/json")
            .build()

        val liveClient = okHttpClient.newBuilder()
            .pingInterval(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        webSocket = liveClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket conectado.")
                reconnectAttempts = 0
                sendSetup(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Mensaje TEXT recibido del servidor Live: $text")
                handleServerMessage(webSocket, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val text = bytes.utf8()
                Log.d(TAG, "Mensaje BINARY recibido del servidor Live (${bytes.size} bytes): $text")
                handleServerMessage(webSocket, text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "WebSocket cerrándose: $code / $reason")
                if (code == 1008 || code == 1002 || code == 1003) {
                    isRunning.set(false)
                    onStateChanged(LiveSessionState.STOPPED)
                    onError(reason.ifBlank { "Conexión rechazada por el servidor ($code)" })
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket cerrado: $code / $reason")
                if (code == 1008 || code == 1002 || code == 1003) {
                    isRunning.set(false)
                    onStateChanged(LiveSessionState.STOPPED)
                    onError(reason.ifBlank { "Conexión rechazada por el servidor ($code)" })
                    return
                }
                if (isRunning.get()) {
                    scheduleReconnect()
                } else {
                    onStateChanged(LiveSessionState.STOPPED)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error en WebSocket Live: ${t.message}", t)
                if (isRunning.get()) {
                    scheduleReconnect()
                } else {
                    onStateChanged(LiveSessionState.STOPPED)
                    onError(t.localizedMessage ?: "Error de conexión Live")
                }
            }
        })
    }

    private fun resolveLiveModel(preferredModel: String): String {
        val clean = preferredModel.removePrefix("models/").trim()
        return if (GeminiModelConfig.liveSelectableModels.contains(clean)) {
            clean
        } else {
            GeminiModelConfig.DEFAULT_LIVE_MODEL
        }
    }

    private fun sendSetup(ws: WebSocket) {
        try {
            val setupObj = JSONObject()
            val setup = JSONObject()

            // Modelo
            val modelName = if (activeModel.startsWith("models/")) activeModel else "models/$activeModel"
            setup.put("model", modelName)

            // Generation config: modalidades de respuesta y voz
            val genConfig = JSONObject()
            val modalities = JSONArray().put("AUDIO")
            genConfig.put("responseModalities", modalities)

            val speechConfig = JSONObject()
            val voiceConfig = JSONObject()
            val prebuiltVoiceConfig = JSONObject().put("voiceName", config.voice)
            voiceConfig.put("prebuiltVoiceConfig", prebuiltVoiceConfig)
            speechConfig.put("voiceConfig", voiceConfig)
            genConfig.put("speechConfig", speechConfig)

            // Thinking config si el modelo lo admite
            if (GeminiModelConfig.liveModelSupportsThinking(activeModel)) {
                val thinkingConfig = JSONObject()
                thinkingConfig.put("thinkingLevel", config.thinkingLevel.uppercase())
                genConfig.put("thinkingConfig", thinkingConfig)
            }
            setup.put("generationConfig", genConfig)

            // System prompt
            val systemPrompt = LivePromptBuilder.buildSystemPrompt(config.languageCode)
            val systemInstruction = JSONObject()
            val parts = JSONArray().put(JSONObject().put("text", systemPrompt))
            systemInstruction.put("parts", parts)
            setup.put("systemInstruction", systemInstruction)

            // Session resumption si existe
            sessionHandle?.let { handle ->
                setup.put("sessionResumption", JSONObject().put("handle", handle))
            }

            setupObj.put("setup", setup)
            val payload = setupObj.toString()
            ws.send(payload)
            Log.d(TAG, "Mensaje Setup enviado correctamente: $payload")
        } catch (e: Exception) {
            Log.e(TAG, "Error construyendo mensaje setup", e)
            onError("Error configurando sesión Live: ${e.message}")
        }
    }

    private fun handleServerMessage(ws: WebSocket, text: String) {
        try {
            val json = JSONObject(text)

            if (json.has("error")) {
                val errObj = json.optJSONObject("error")
                val errMsg = errObj?.optString("message") ?: "Error desconocido de Gemini Live"
                val errCode = errObj?.optInt("code") ?: 0
                Log.e(TAG, "Error retornado por Gemini Live ($errCode): $errMsg")
                onError("Error de Gemini Live ($errCode): $errMsg")
                return
            }

            if (json.has("setupComplete")) {
                Log.i(TAG, "Sesión Live configurada con éxito.")
                isConnectedOnce = true
                onStateChanged(LiveSessionState.RUNNING)
                sendInitialGreeting(ws)
            }

            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.d(TAG, "Interrupción detectada por Gemini.")
                    onInterrupted()
                }

                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val dataBase64 = inlineData.optString("data")
                                if (dataBase64.isNotEmpty()) {
                                    val pcm = Base64.decode(dataBase64, Base64.DEFAULT)
                                    onAudioReceived(pcm)
                                }
                            }
                            if (part.has("text")) {
                                val txt = part.optString("text")
                                if (txt.isNotEmpty()) {
                                    onTextReceived(txt)
                                }
                            }
                        }
                    }
                }
            }

            if (json.has("sessionResumptionUpdate")) {
                val update = json.getJSONObject("sessionResumptionUpdate")
                if (update.optBoolean("resumable", false)) {
                    val handle = update.optString("newHandle")
                    if (handle.isNotEmpty()) {
                        sessionHandle = handle
                    }
                }
            }

            if (json.has("goAway")) {
                Log.w(TAG, "Gemini solicitó reconexión (goAway).")
                scheduleReconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parseando mensaje del servidor: ${e.message}")
        }
    }

    private fun sendInitialGreeting(ws: WebSocket) {
        try {
            val root = JSONObject()
            val clientContent = JSONObject()
            val turns = JSONArray()
            val turn = JSONObject()
            turn.put("role", "user")
            val parts = JSONArray().put(JSONObject().put("text", LivePromptBuilder.LIVE_GREETING_TRIGGER))
            turn.put("parts", parts)
            turns.put(turn)
            clientContent.put("turns", turns)
            clientContent.put("turnComplete", true)
            root.put("clientContent", clientContent)

            ws.send(root.toString())
            Log.d(TAG, "Disparador de saludo inicial enviado.")
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando saludo inicial", e)
        }
    }

    fun sendAudio(data: ByteArray) {
        val ws = webSocket ?: return
        if (!isRunning.get() || data.isEmpty()) return
        try {
            val root = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()
            val chunk = JSONObject()
            chunk.put("mimeType", "audio/pcm;rate=16000")
            chunk.put("data", Base64.encodeToString(data, Base64.NO_WRAP))
            mediaChunks.put(chunk)
            realtimeInput.put("mediaChunks", mediaChunks)
            root.put("realtimeInput", realtimeInput)
            ws.send(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando chunk de audio", e)
        }
    }

    fun sendVideoFrame(jpegBytes: ByteArray) {
        val ws = webSocket ?: return
        if (!isRunning.get() || jpegBytes.isEmpty()) return
        try {
            val root = JSONObject()
            val realtimeInput = JSONObject()
            val mediaChunks = JSONArray()
            val chunk = JSONObject()
            chunk.put("mimeType", "image/jpeg")
            chunk.put("data", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))
            mediaChunks.put(chunk)
            realtimeInput.put("mediaChunks", mediaChunks)
            root.put("realtimeInput", realtimeInput)
            ws.send(root.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando frame de video", e)
        }
    }

    fun sendActivityStart() {
        val ws = webSocket ?: return
        try {
            val root = JSONObject()
            val realtimeInput = JSONObject()
            realtimeInput.put("activityStart", JSONObject())
            root.put("realtimeInput", realtimeInput)
            ws.send(root.toString())
            Log.d(TAG, "activityStart enviado.")
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando activityStart", e)
        }
    }

    fun sendActivityEnd() {
        val ws = webSocket ?: return
        try {
            val root = JSONObject()
            val realtimeInput = JSONObject()
            realtimeInput.put("activityEnd", JSONObject())
            root.put("realtimeInput", realtimeInput)
            ws.send(root.toString())
            Log.d(TAG, "activityEnd enviado.")
        } catch (e: Exception) {
            Log.w(TAG, "Error enviando activityEnd", e)
        }
    }

    private fun scheduleReconnect() {
        if (!isRunning.get()) return
        reconnectAttempts++
        val maxAttempts = if (isConnectedOnce) 6 else 3
        if (reconnectAttempts > maxAttempts) {
            isRunning.set(false)
            onStateChanged(LiveSessionState.STOPPED)
            onError("Se perdió la conexión con Gemini Live tras $maxAttempts intentos.")
            return
        }

        onStateChanged(LiveSessionState.RECONNECTING)
        val delaySec = (1L shl (reconnectAttempts - 1)).coerceAtMost(8L)
        Thread({
            try {
                Thread.sleep(delaySec * 1000)
                if (isRunning.get()) {
                    connect()
                }
            } catch (e: InterruptedException) {
                // Interrumpido
            }
        }, "RayoAI-LiveReconnect").start()
    }

    fun stop() {
        isRunning.set(false)
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            webSocket?.close(1000, "User ended session")
        } catch (e: Exception) {
            Log.w(TAG, "Error cerrando WebSocket", e)
        }
        webSocket = null
        onStateChanged(LiveSessionState.STOPPED)
    }
}
