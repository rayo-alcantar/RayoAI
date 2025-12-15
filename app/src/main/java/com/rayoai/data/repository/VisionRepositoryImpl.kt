package com.rayoai.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.rayoai.core.ResultWrapper
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.data.local.db.CaptureDao
import com.rayoai.domain.model.Capture
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VisionRepositoryImpl @Inject constructor(
    private val captureDao: CaptureDao,
    private val imageStorageManager: ImageStorageManager
) : VisionRepository {

    override fun generateContent(
        apiKey: String,
        prompt: String,
        images: List<Bitmap>,
        history: List<ChatMessage>,
        model: String
    ): Flow<ResultWrapper<String>> = flow {
        emit(ResultWrapper.Loading)

        val contents = mutableListOf<Content>()

        history.forEach { chatMessage ->
            val participant = if (chatMessage.isFromUser) "user" else "model"
            contents.add(content(participant) { text(chatMessage.content) })
        }

        val currentContent = content {
            images.forEach { image ->
                image(image)
            }
            text(prompt)
        }
        contents.add(currentContent)

        val modelPriority = buildModelPriority(model)
        var lastError: String? = null

        for (modelName in modelPriority) {
            try {
                val responseText = generateWithModel(apiKey, modelName, contents)
                if (!responseText.isNullOrBlank()) {
                    emit(ResultWrapper.Success(responseText))
                    return@flow
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = "${modelName}: ${e.localizedMessage ?: "Unknown error"}"
            }
        }

        emit(ResultWrapper.Error(lastError ?: "No se pudo obtener respuesta de los modelos configurados."))
    }

    private fun buildModelPriority(preferredModel: String): List<String> {
        val normalizedPreferred = preferredModel.ifBlank { GeminiModelConfig.DEFAULT_MODEL }
        return (listOf(normalizedPreferred) + GeminiModelConfig.fallbackOrder).distinct()
    }

    private suspend fun generateWithModel(
        apiKey: String,
        modelName: String,
        contents: List<Content>
    ): String? {
        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = apiKey
        )
        val response = generativeModel.generateContent(*contents.toTypedArray())
        val directText = response.text?.takeIf { it.isNotBlank() }
        if (!directText.isNullOrBlank()) return directText

        return response.candidates.firstOrNull()
            ?.content
            ?.parts
            ?.joinToString(separator = "") { part ->
                when (part) {
                    is com.google.ai.client.generativeai.type.TextPart -> part.text
                    else -> ""
                }
            }
            ?.takeIf { it.isNotBlank() }
    }

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
