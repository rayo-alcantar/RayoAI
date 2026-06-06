package com.rayoai.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTOs para la Files API de Google Gemini.
 * Usados para subir archivos grandes (videos, audios) y obtener URIs para usar en generateContent.
 */

// ============ REQUEST MODELS ============

/**
 * Metadata del archivo a subir.
 */
data class FileMetadata(
    @SerializedName("file") val file: FileInfo
)

/**
 * Información básica del archivo.
 */
data class FileInfo(
    @SerializedName("display_name") val displayName: String
)

// ============ RESPONSE MODELS ============

/**
 * Respuesta al subir un archivo.
 */
data class FileUploadResponse(
    @SerializedName("file") val file: UploadedFile
)

/**
 * Información de un archivo subido.
 */
data class UploadedFile(
    @SerializedName("name") val name: String,              // "files/abc123..."
    @SerializedName("uri") val uri: String,                // URI completo
    @SerializedName("mimeType") val mimeType: String,
    @SerializedName("sizeBytes") val sizeBytes: String,
    @SerializedName("state") val state: String,            // "PROCESSING" | "ACTIVE" | "FAILED"
    @SerializedName("createTime") val createTime: String? = null,
    @SerializedName("updateTime") val updateTime: String? = null,
    @SerializedName("expirationTime") val expirationTime: String? = null
)

/**
 * Para usar archivos subidos en generateContent.
 */
data class FileDataDto(
    @SerializedName("mime_type") val mimeType: String? = null,
    @SerializedName("file_uri") val fileUri: String
)
