package com.rayoai.domain.model

/**
 * Modelo de dominio para un documento de video analizado.
 */
data class VideoDocument(
    val id: Long,
    val name: String,
    val uri: String,
    val content: String,           // Descripción generada por Gemini
    val timestamp: Long,
    val durationSeconds: Int,      // Duración del video en segundos
    val sizeBytes: Long,           // Tamaño del archivo en bytes
    val geminiFileUri: String? = null,
    val geminiFileName: String? = null,
    val geminiMimeType: String? = null,
    val geminiFileExpiresAt: Long? = null
)
