package com.rayoai.domain.model

/**
 * Centraliza los modelos usados por la app.
 * Se expone un modelo predeterminado (Gemini 2.5 Flash) y un orden de
 * candidatos para intentar de forma secuencial.
 */
object GeminiModelConfig {
    const val DEFAULT_MODEL = "gemini-2.5-flash"

    /**
        Modelos visibles para el selector de ajustes.
     */
    val selectableModels = listOf(
        DEFAULT_MODEL,
        "gemini-2.5-pro",
        "gemini-3-flash",
        "gemini-3-pro",
        "gemini-3"
    )

    /**
        Orden sugerido de reintentos. Incluye variantes sin sufijos para compatibilidad.
     */
    val fallbackOrder = listOf(
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.5",
        "gemini-3-flash",
        "gemini-3-pro",
        "gemini-3"
    )
}
