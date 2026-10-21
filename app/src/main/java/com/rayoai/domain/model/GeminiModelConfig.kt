package com.rayoai.domain.model

import com.rayoai.data.remote.dto.ThinkingConfigDto

/**
 * Centraliza los modelos usados por la app.
 * Se expone un modelo predeterminado (Gemini 3.1 Flash Lite) y un orden de
 * candidatos priorizando baja latencia antes de caer en modelos Pro.
 */
object GeminiModelConfig {
    const val DEFAULT_MODEL = "gemini-3.1-flash-lite-preview"
    const val FALLBACK_MODEL = "gemini-2.5-flash"

    /**
     * Modelos visibles para el selector de ajustes (ordenados de más rápidos a más pesados).
     */
    val selectableModels = listOf(
        DEFAULT_MODEL,
        "gemini-3-flash-preview",
        FALLBACK_MODEL,
        "gemini-3.1-pro-preview",
        "gemini-2.5-pro"
    )

    /**
     * Orden de reintentos priorizando velocidad (Flash primero, Pro como último recurso).
     */
    val fallbackOrder = listOf(
        DEFAULT_MODEL,
        "gemini-3-flash-preview",
        FALLBACK_MODEL,
        "gemini-3.1-pro-preview",
        "gemini-2.5-pro"
    )

    /**
     * Construye la lista priorizada de modelos respetando siempre la elección del usuario primero.
     */
    fun buildModelPriority(preferredModel: String): List<String> {
        val normalizedPreferred = preferredModel.trim().ifBlank { DEFAULT_MODEL }
        return (listOf(normalizedPreferred) + fallbackOrder).distinct()
    }

    /**
     * Devuelve la configuración de razonamiento óptima según la familia del modelo.
     * - Gemini 3 / 3.1 soporta `thinking_level = "MINIMAL"`.
     * - Gemini 2.5 Flash soporta `thinking_budget = 0` para respuesta inmediata sin latencia de pensamiento.
     * - Gemini 2.5 Pro requiere pensamiento activo, por lo que no se envía restricción incompatible.
     */
    fun thinkingConfigFor(model: String): ThinkingConfigDto? {
        return when {
            model.startsWith("gemini-3") -> ThinkingConfigDto(
                includeThoughts = false,
                thinkingLevel = "MINIMAL"
            )
            model == "gemini-2.5-flash" -> ThinkingConfigDto(
                includeThoughts = false,
                thinkingBudget = 0
            )
            else -> null
        }
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
