package com.rayoai.data.repository

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.rayoai.core.ResultWrapper
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.data.local.db.CaptureDao
import com.rayoai.data.remote.GeminiApiService
import com.rayoai.data.remote.dto.*
import com.rayoai.domain.model.Capture
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * Implementación del repositorio de visión usando Retrofit con la API REST v1 beta de Gemini.
 * 
 * Esta implementación reemplaza el SDK deprecado google-generativeai-android con llamadas
 * directas a la API REST, solucionando errores HTTP 400 causados por la mezcla de system prompts
 * con user prompts.
 */
class VisionRepositoryImpl @Inject constructor(
    private val geminiApiService: GeminiApiService,
    private val captureDao: CaptureDao,
    private val imageStorageManager: ImageStorageManager
) : VisionRepository {

    override fun generateContent(
        apiKey: String,
        prompt: String,
        systemPrompt: String?,
        images: List<Bitmap>,
        history: List<ChatMessage>,
        model: String,
        responseMimeType: String?
    ): Flow<ResultWrapper<String>> = flow {
        emit(ResultWrapper.Loading)

        // 1. Preparar System Instruction (Si existe)
        val systemInstructionDto = systemPrompt?.let {
            SystemInstruction(parts = listOf(PartDto(text = it)))
        }

        // 2. Preparar Historial de Contenido
        val contentsDto = mutableListOf<ContentDto>()

        // Agregar historial previo (alternando roles user/model)
        history.forEach { chatMessage ->
            val role = if (chatMessage.isFromUser) "user" else "model"
            contentsDto.add(
                ContentDto(
                    role = role,
                    parts = listOf(PartDto(text = chatMessage.content))
                )
            )
        }

        // 3. Preparar Mensaje Actual (Imágenes + Texto)
        val currentParts = mutableListOf<PartDto>()
        val isStructuredDocument = responseMimeType == "application/json"
        val maxImageSide = if (isStructuredDocument) MAX_DOCUMENT_IMAGE_SIDE else MAX_VISION_IMAGE_SIDE
        val jpegQuality = if (isStructuredDocument) 82 else 76

        // Convertir imágenes a Base64 (escaladas en Dispatchers.IO) y agregarlas como inline data
        images.forEach { bitmap ->
            val base64String = bitmapToBase64(bitmap, maxImageSide, jpegQuality)
            currentParts.add(
                PartDto(
                    inlineData = InlineDataDto(
                        mimeType = "image/jpeg",
                        data = base64String
                    )
                )
            )
        }

        // Agregar texto del usuario
        if (prompt.isNotBlank()) {
            currentParts.add(PartDto(text = prompt))
        }

        // Agregar el mensaje actual del usuario
        contentsDto.add(
            ContentDto(
                role = "user",
                parts = currentParts
            )
        )

        // 4. Lógica de Fallback de Modelos (priorizando velocidad y adaptando ThinkingConfig por familia)
        val modelPriority = GeminiModelConfig.buildModelPriority(model)
        var lastError: String? = null

        for (modelName in modelPriority) {
            try {
                val request = GeminiRequest(
                    systemInstruction = systemInstructionDto,
                    contents = contentsDto,
                    generationConfig = GenerationConfigDto(
                        maxOutputTokens = if (isStructuredDocument) 8192 else null,
                        responseMimeType = responseMimeType,
                        thinkingConfig = GeminiModelConfig.thinkingConfigFor(modelName)
                    )
                )

                val response = geminiApiService.generateContent(
                    model = modelName,
                    apiKey = apiKey,
                    request = request
                )

                if (response.isSuccessful && response.body() != null) {
                    val candidate = response.body()!!.candidates?.firstOrNull()
                    val parts = candidate?.content?.parts

                    val text = parts?.filter { it.thought != true }
                        ?.mapNotNull { it.text }
                        ?.joinToString("")

                    if (!text.isNullOrBlank()) {
                        emit(ResultWrapper.Success(text))
                        return@flow
                    } else {
                        val finishReason = candidate?.finishReason ?: "UNKNOWN"
                        lastError = "$modelName: Empty response (Reason: $finishReason)"
                    }
                } else {
                    val httpCode = response.code()
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    lastError = "$modelName: HTTP $httpCode - $errorBody"
                    if (GeminiModelConfig.isFatalAuthOrKeyError(httpCode, errorBody)) {
                        break
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = "$modelName: ${e.localizedMessage ?: e.message ?: "Unknown error"}"
            }
        }

        emit(ResultWrapper.Error(lastError ?: "No se pudo obtener respuesta de los modelos configurados."))
    }.flowOn(Dispatchers.IO)

    /**
     * Escala y convierte un Bitmap a Base64 JPEG para minimizar el tamaño del payload y tiempo de subida.
     */
    private fun bitmapToBase64(bitmap: Bitmap, maxSide: Int, jpegQuality: Int): String {
        val scaledBitmap = imageStorageManager.scaleBitmapForProcessing(bitmap, maxSide)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private companion object {
        const val MAX_VISION_IMAGE_SIDE = 1280
        const val MAX_DOCUMENT_IMAGE_SIDE = 1536
    }

    // ============ MÉTODOS DE PERSISTENCIA (sin cambios) ============

    override fun getHistory(showHidden: Boolean): Flow<List<Capture>> {
        return captureDao.getAllCaptures(showHidden).map { entities ->
            entities.map { entity ->
                Capture(
                    id = entity.id,
                    lastMessage = entity.chatHistory.lastOrNull()?.content ?: "",
                    timestamp = entity.timestamp,
                    isHidden = entity.isHidden,
                    imageUris = entity.imageUris
                )
            }
        }
    }

    override suspend fun updateChatHiddenState(id: Long, isHidden: Boolean) {
        captureDao.updateHiddenState(id, isHidden)
    }

    override suspend fun deleteCapture(capture: Capture) {
        try {
            imageStorageManager.deleteImages(capture.imageUris.map { Uri.parse(it) })
            captureDao.deleteCapture(capture.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun deleteAllCaptures() {
        try {
            val allCaptures = captureDao.getAllCapturesList()
            allCaptures.forEach { capture ->
                imageStorageManager.deleteImages(capture.imageUris.map { Uri.parse(it) })
            }
            captureDao.deleteAllCaptures()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
