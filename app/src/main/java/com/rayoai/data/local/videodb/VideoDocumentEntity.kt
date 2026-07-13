package com.rayoai.data.local.videodb

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entidad de Room para almacenar videos analizados en la base de datos local.
 */
@Entity(tableName = "video_documents")
data class VideoDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
