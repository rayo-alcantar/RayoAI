package com.rayoai.domain.model

import com.rayoai.data.remote.dto.ThinkingConfigDto

/**
 * Centraliza los modelos Gemini usados por RayoAI.
 *
 * - General (`generateContent`): prioriza baja latencia y disponibilidad gratuita
 *   con Gemini 3.5 Flash-Lite como predeterminado, Gemini 4 Hydrogen y Gemini 3.8 Flash
 *   como respaldo rápido/equilibrado, y Gemini 4 Neon / Carbon para usuarios con API de pago.
 * - Live (`BidiGenerateContent`): Gemini 3.8 Live (baja latencia, sin `thinkingConfig`) y
 *   Gemini 3.8 Live Extended Thinking (razonamiento en segundo plano `low`/`medium`/`high`).
 */
object GeminiModelConfig {
    const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
    const val FALLBACK_MODEL = "gemini-4-hydrogen"
    const val FLASH_BALANCED_MODEL = "gemini-3.8-flash"
    const val PRO_REASONING_MODEL = "gemini-4-neon"
    const val MAX_REASONING_MODEL = "gemini-4-carbon"

    // Modelos canónicos para Gemini Live API bidireccional (audio + visión)
    const val DEFAULT_LIVE_MODEL = "gemini-3.8-live"
    const val EXTENDED_THINKING_LIVE_MODEL = "gemini-3.8-live-extended-thinking"
    const val DEFAULT_LIVE_THINKING_LEVEL = "low"

    /**
     * Modelos visibles para el selector de ajustes generales (ordenados de menor latencia a mayor razonamiento).
     */
    val selectableModels = listOf(
        DEFAULT_MODEL,
        FALLBACK_MODEL,
        FLASH_BALANCED_MODEL,
        PRO_REASONING_MODEL,
        MAX_REASONING_MODEL
    )

    /**
     * Modelos compatibles con el modo Live (WebSocket bidireccional BidiGenerateContent).
     */
    val liveSelectableModels = listOf(
        DEFAULT_LIVE_MODEL,
        EXTENDED_THINKING_LIVE_MODEL
    )

    /**
     * Voces preconstruidas oficiales de Gemini Live.
     */
    val liveVoices = listOf(
        "Kore",
        "Puck",
        "Charon",
        "Fenrir",
        "Aoede",
        "Zephyr",
        "Leda",
        "Orus",
        "Callirrhoe",
        "Autonoe",
        "Enceladus",
        "Iapetus",
        "Umbriel",
        "Algieba",
        "Despina",
        "Erinome",
        "Algenib",
        "Rasalgethi",
        "Laomedeia",
        "Achernar",
        "Alnilam",
        "Schedar",
        "Gacrux",
        "Pulcherrima",
        "Achird",
        "Zubenelgenubi",
        "Vindemiatrix",
        "Sadachbia",
        "Sadaltager",
        "Sulafat"
    )

    /**
     * Niveles de razonamiento válidos para Gemini 3.8 Live Extended Thinking.
     * Nota: `minimal` no es compatible con `gemini-3.8-live-extended-thinking`.
     */
    val liveThinkingLevels = listOf("low", "medium", "high")

    /**
     * Sensibilidades de detección de voz (VAD) para Gemini Live.
     */
    val liveVadSensitivities = listOf("high", "low")

    /**
     * Orden de reintentos priorizando modelos gratuitos/rápidos antes de modelos exclusivos de pago.
     */
    val fallbackOrder = listOf(
        DEFAULT_MODEL,
        FALLBACK_MODEL,
        FLASH_BALANCED_MODEL,
        PRO_REASONING_MODEL,
        MAX_REASONING_MODEL
    )

    /**
     * Migra preferencias guardadas con modelos obsoletos o preview hacia sus reemplazos estables oficiales.
     */
    fun migrateGeneralModel(savedModel: String?): String {
        val clean = savedModel?.removePrefix("models/")?.trim().orEmpty()
        if (clean.isBlank()) return DEFAULT_MODEL
        if (selectableModels.contains(clean)) return clean

        val normalized = clean.lowercase()
        return when {
            normalized == "gemini-3.1-flash-lite" ||
                normalized == "gemini-3.1-flash-lite-preview" ||
                normalized == "gemini-2.5-flash" ||
                normalized == "gemini-2.5-flash-lite" ||
                normalized.contains("2.0") -> DEFAULT_MODEL

            normalized == "gemini-3-flash-preview" ||
                normalized == "gemini-3.5-flash" ||
                normalized == "gemini-3.6-flash" ||
                normalized == "gemini-3.7-flash" ||
                normalized == "gemini-2.5-pro" -> FALLBACK_MODEL

            normalized == "gemini-3.1-pro-preview" ||
                normalized == "gemini-3-pro-preview" -> PRO_REASONING_MODEL

            else -> DEFAULT_MODEL
        }
    }

    /**
     * Migra preferencias del modo Live hacia un modelo Live compatible vigente.
     */
    fun migrateLiveModel(savedModel: String?): String {
        val clean = savedModel?.removePrefix("models/")?.trim().orEmpty()
        return if (liveSelectableModels.contains(clean)) {
            clean
        } else {
            DEFAULT_LIVE_MODEL
        }
    }

    /**
     * Normaliza el nivel de razonamiento del modo Live para evitar valores rechazados por la API
     * (por ejemplo, `minimal` guardado desde versiones anteriores).
     */
    fun normalizeLiveThinkingLevel(model: String, level: String?): String {
        val normalized = level?.trim()?.lowercase().orEmpty()
        if (liveThinkingLevels.contains(normalized)) return normalized
        return DEFAULT_LIVE_THINKING_LEVEL
    }

    /**
     * Construye la lista priorizada de modelos respetando siempre la elección del usuario primero.
     */
    fun buildModelPriority(preferredModel: String): List<String> {
        val migratedPreferred = migrateGeneralModel(preferredModel)
        return (listOf(migratedPreferred) + fallbackOrder).distinct()
    }

    /**
     * Devuelve la configuración de razonamiento óptima según la familia del modelo:
     * - `gemini-3.5-flash-lite` (y Flash-Lite / 3.6 Flash): soporta `thinking_level = "MINIMAL"` para latencia mínima.
     * - Gemini 4 (`gemini-4-*`), `gemini-3.8-flash` y `gemini-3.1-pro*`: rechazan `MINIMAL`; usan `thinking_level = "LOW"`
     *   para respuestas rápidas y precisas en móvil.
     * - `gemini-2.5-flash`: soporta `thinking_budget = 0`.
     */
    fun thinkingConfigFor(model: String): ThinkingConfigDto? {
        val normalized = model.removePrefix("models/").trim().lowercase()
        return when {
            normalized == "gemini-3.5-flash-lite" ||
                normalized == "gemini-3.6-flash" ||
                normalized == "gemini-3.1-flash-lite" ||
                normalized == "gemini-3-flash-preview" -> ThinkingConfigDto(
                includeThoughts = false,
                thinkingLevel = "MINIMAL"
            )
            normalized.startsWith("gemini-4") ||
                normalized == "gemini-3.8-flash" ||
                normalized.startsWith("gemini-3.1-pro") -> ThinkingConfigDto(
                includeThoughts = false,
                thinkingLevel = "LOW"
            )
            normalized == "gemini-2.5-flash" -> ThinkingConfigDto(
                includeThoughts = false,
                thinkingBudget = 0
            )
            else -> null
        }
    }

    /**
     * Determina si un modelo Live admite configuración explícita de `thinkingConfig`.
     * - `gemini-3.8-live`: NO admite `thinkingConfig` (razonamiento intercalado integrado).
     * - `gemini-3.8-live-extended-thinking`: SÍ admite `thinkingLevel` (`low`, `medium`, `high`).
     */
    fun liveModelSupportsThinking(model: String): Boolean {
        val normalized = model.removePrefix("models/").trim().lowercase()
        return normalized == EXTENDED_THINKING_LIVE_MODEL || normalized.contains("extended-thinking")
    }

    /**
     * Determina si un error HTTP es fatal para todos los modelos (por ejemplo, API key inválida),
     * evitando reintentar en cascada contra los demás modelos.
     */
    fun isFatalAuthOrKeyError(httpCode: Int, errorBody: String?): Boolean {
        if (httpCode == 401 || httpCode == 403) return true
        if (httpCode == 400 && errorBody != null) {
            return errorBody.contains("API_KEY_INVALID", ignoreCase = true) ||
                errorBody.contains("API key not valid", ignoreCase = true)
        }
        return false
    }
}
