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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
        model: String
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

        // Convertir imágenes a Base64 y agregarlas como inline data
        images.forEach { bitmap ->
            val base64String = bitmapToBase64(bitmap)
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

        // 4. Construir Request Completo
        val request = GeminiRequest(
            systemInstruction = systemInstructionDto,
            contents = contentsDto,
            generationConfig = null // Podemos agregar configuraciones en el futuro
        )

        // 5. Lógica de Fallback de Modelos (mantiene compatibilidad con lógica original)
        val modelPriority = buildModelPriority(model)
        var lastError: String? = null

        for (modelName in modelPriority) {
            try {
                val response = geminiApiService.generateContent(
                    model = modelName,
                    apiKey = apiKey,
                    request = request
                )

                if (response.isSuccessful && response.body() != null) {
                    // Extraer texto de la respuesta
                    val candidate = response.body()!!.candidates?.firstOrNull()
                    val text = candidate?.content?.parts?.firstOrNull()?.text

                    if (!text.isNullOrBlank()) {
                        emit(ResultWrapper.Success(text))
                        return@flow // Éxito, salimos del flujo
                    } else {
                        // Respuesta vacía, intentar siguiente modelo
                        lastError = "$modelName: Empty response from API"
                    }
                } else {
                    // Error HTTP (400, 429, etc.)
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    lastError = "$modelName: HTTP ${response.code()} - $errorBody"
                }
            } catch (e: CancellationException) {
                // No capturar CancellationException, dejarlo propagarse
                throw e
            } catch (e: Exception) {
                // Cualquier otro error (red, parsing, etc.)
                lastError = "$modelName: ${e.localizedMessage ?: e.message ?: "Unknown error"}"
            }
        }

        // Si llegamos aquí, todos los modelos fallaron
        emit(ResultWrapper.Error(lastError ?: "No se pudo obtener respuesta de los modelos configurados."))
    }

    /**
     * Convierte un Bitmap a Base64 (JPEG comprimido al 80%).
     * Necesario porque la API REST requiere imágenes en Base64.
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Comprimir a JPEG para reducir tamaño del payload
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Construye la lista priorizada de modelos para intentar.
     * Primero el modelo preferido, luego los fallbacks.
     */
    private fun buildModelPriority(preferredModel: String): List<String> {
        val normalizedPreferred = preferredModel.ifBlank { GeminiModelConfig.DEFAULT_MODEL }
        return (listOf(normalizedPreferred) + GeminiModelConfig.fallbackOrder).distinct()
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
