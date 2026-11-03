package com.rayoai.domain.model.live

import com.rayoai.domain.model.GeminiModelConfig

enum class LiveSessionState {
    STOPPED,
    STARTING,
    RUNNING,
    RECONNECTING,
    PAUSED,
    STOPPING
}

enum class VisualSource {
    CAMERA,
    SCREEN,
    NONE
}

data class LiveModeConfig(
    val apiKey: String,
    val model: String = GeminiModelConfig.DEFAULT_LIVE_MODEL,
    val voice: String = "Kore",
    val thinkingLevel: String = "minimal",
    val vadSensitivity: String = "high",
    val pushToTalk: Boolean = false,
    val captureIntervalSeconds: Float = 1.0f,
    val languageCode: String = "es"
)
