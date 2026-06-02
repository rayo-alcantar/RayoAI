package com.rayoai.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.rayoai.core.ResultWrapper
import com.rayoai.R
import com.rayoai.data.remote.GeminiApiService
import com.rayoai.data.remote.GeminiFilesApiService
import com.rayoai.data.remote.dto.*
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/**
 * Implementación del repositorio de videos que usa Google Gemini Files API.
 */
class VideoRepositoryImpl @Inject constructor(
    private val geminiFilesApiService: GeminiFilesApiService,
    private val geminiApiService: GeminiApiService,
    private val userPreferencesRepository: UserPreferencesRepository
) : VideoRepository {

    override suspend fun uploadAndAnalyzeVideo(
        uri: Uri,
        context: Context,
        systemPrompt: String
    ): ResultWrapper<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = userPreferencesRepository.apiKey.firstOrNull()
                ?: return@withContext ResultWrapper.Error(context.getString(R.string.video_upload_missing_api_key))

            // 1. Obtener información del video
            val (fileName, sizeBytes, mimeType) = getVideoInfo(context, uri)

            // Validar tamaño (máximo 2 GB)
            if (sizeBytes > 2_000_000_000L) {
                return@withContext ResultWrapper.Error(context.getString(R.string.scan_video_too_large))
            }

            // 2. Iniciar upload resumable
            val uploadResponse = geminiFilesApiService.startResumableUpload(
                contentLength = sizeBytes,
                contentType = mimeType,
                apiKey = apiKey,
                metadata = FileMetadata(FileInfo(fileName))
            )

            // 3. Extraer URL de upload de los headers
            val uploadUrl = uploadResponse.headers()["x-goog-upload-url"]
                ?: return@withContext ResultWrapper.Error(context.getString(R.string.video_upload_url_missing))

            // 4. Leer bytes del video
            val videoBytes = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes()
            } ?: return@withContext ResultWrapper.Error(context.getString(R.string.video_upload_read_failed))

            // 5. Subir bytes del video
            val requestBody = videoBytes.toRequestBody(mimeType.toMediaTypeOrNull())

            val uploadResult = geminiFilesApiService.uploadFileBytes(
                uploadUrl = uploadUrl,
                contentLength = sizeBytes,
                offset = 0,
                file = requestBody
            )

            // 6. Esperar a que el archivo esté activo (puede estar en estado PROCESSING)
            var fileState = uploadResult.file
            var attempts = 0
            val maxAttempts = 60  // Máximo 1 minuto de espera

            while (fileState.state != "ACTIVE" && attempts < maxAttempts) {
                if (fileState.state == "FAILED") {
                    return@withContext ResultWrapper.Error(context.getString(R.string.video_processing_failed))
                }

                delay(1000)  // Esperar 1 segundo
                attempts++

                fileState = try {
                    geminiFilesApiService.getFile(
                        fileName = uploadResult.file.name,
                        apiKey = apiKey
                    )
                } catch (e: Exception) {
                    // Si falla al consultar, asumir que aún está procesando
                    fileState
                }
            }

            if (fileState.state != "ACTIVE") {
                return@withContext ResultWrapper.Error(context.getString(R.string.video_processing_timeout))
            }

            // 7. Analizar el video con Gemini
            val model = userPreferencesRepository.defaultModel.firstOrNull()
                ?: com.rayoai.domain.model.GeminiModelConfig.DEFAULT_MODEL

            val request = GeminiRequest(
                systemInstruction = SystemInstruction(
                    parts = listOf(PartDto(text = systemPrompt))
                ),
                contents = listOf(
                    ContentDto(
                        role = "user",
                        parts = listOf(
                            PartDto(text = "Describe this video in detail."),
                            PartDto(
                                fileData = FileDataDto(
                                    mimeType = mimeType,
                                    fileUri = fileState.uri
                                )
                            )
                        )
                    )
                ),
                generationConfig = GenerationConfigDto(
                    thinkingConfig = ThinkingConfigDto(
                        includeThoughts = true,
                        thinkingLevel = "MINIMAL"
                    )
                )
            )

            val response = geminiApiService.generateContent(
                apiKey = apiKey,
                model = model,
                request = request
            )

            if (!response.isSuccessful || response.body() == null) {
                return@withContext ResultWrapper.Error(context.getString(R.string.video_api_error, response.code(), response.message()))
            }

            // Extraer texto de la respuesta (Gemini 3.1 puede devolver múltiples partes)
            val candidate = response.body()!!.candidates?.firstOrNull()
            val parts = candidate?.content?.parts
            
            // Algoritmo de extracción para Gemini 3.1:
            // 1. Recorrer partes. 2. Ignorar "thought": true. 3. Concatenar texto.
            val description = parts?.filter { it.thought != true }
                ?.mapNotNull { it.text }
                ?.joinToString("")

            if (description.isNullOrBlank()) {
                val finishReason = candidate?.finishReason ?: "UNKNOWN"
                return@withContext ResultWrapper.Error(context.getString(R.string.video_model_empty_response, finishReason))
            }

            ResultWrapper.Success(description)

        } catch (e: Exception) {
            ResultWrapper.Error(context.getString(R.string.video_process_error, e.message))
        }
    }

    /**
     * Obtiene información del video: nombre, tamaño y tipo MIME.
     */
    private fun getVideoInfo(context: Context, uri: Uri): Triple<String, Long, String> {
        var fileName = "video.mp4"
        var sizeBytes = 0L
        var mimeType = "video/mp4"

        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) fileName = cursor.getString(nameIndex)

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
            }
        }

        mimeType = context.contentResolver.getType(uri) ?: "video/mp4"

        return Triple(fileName, sizeBytes, mimeType)
    }
}
