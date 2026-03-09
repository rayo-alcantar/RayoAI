package com.rayoai.domain.model

/**
 * Centraliza los modelos usados por la app.
 * Se expone un modelo predeterminado (Gemini 3.1 Flash Lite) y un orden de
 * candidatos para intentar de forma secuencial.
 */
object GeminiModelConfig {
    const val DEFAULT_MODEL = "gemini-3.1-flash-lite-preview"
    const val FALLBACK_MODEL = "gemini-2.5-flash"

    /**
        Modelos visibles para el selector de ajustes.
     */
    val selectableModels = listOf(
        DEFAULT_MODEL,
        "gemini-3.1-pro-preview",
        "gemini-3-flash-preview",
        FALLBACK_MODEL,
        "gemini-2.5-pro"
    )

    /**
        Orden sugerido de reintentos.
     */
    val fallbackOrder = listOf(
        "gemini-3.1-pro-preview",
        DEFAULT_MODEL,
        "gemini-3-flash-preview",
        FALLBACK_MODEL,
        "gemini-2.5-pro",
        "gemini-2.5"
    )
}
