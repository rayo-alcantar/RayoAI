package com.rayoai.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.content
import com.rayoai.core.ResultWrapper
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.data.local.db.CaptureDao
import com.rayoai.data.local.model.CaptureEntity
import com.rayoai.domain.model.Capture
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.repository.VisionRepository
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
        history: List<ChatMessage>
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

        try {
            val generativeModelFlash = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = apiKey
            )
            val responseFlash = generativeModelFlash.generateContent(*contents.toTypedArray())
            val responseTextFlash = responseFlash.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.let { part ->
                    when (part) {
                        is com.google.ai.client.generativeai.type.TextPart -> part.text
                        else -> null
                    }
                }
            responseTextFlash?.let {
                emit(ResultWrapper.Success(it))
                return@flow
            }
        } catch (e: Exception) {
        }

        try {
            val generativeModel1_5Flash = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey
            )
            val response1_5Flash = generativeModel1_5Flash.generateContent(*contents.toTypedArray())
            val responseText1_5Flash = response1_5Flash.candidates.firstOrNull()
                ?.content?.parts?.firstOrNull()
                ?.let { part ->
                    when (part) {
                        is com.google.ai.client.generativeai.type.TextPart -> part.text
                        else -> null
                    }
                }
            responseText1_5Flash?.let {
                emit(ResultWrapper.Success(it))
                return@flow
            } ?: emit(ResultWrapper.Error("Empty response from both APIs."))
        } catch (e: Exception) {
            emit(ResultWrapper.Error(e.localizedMessage ?: "An unknown error occurred after multiple attempts"))
        }
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