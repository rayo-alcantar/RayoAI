package com.rayoai.domain.repository

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.Capture
import com.rayoai.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface VisionRepository {
    fun generateContent(
        apiKey: String,
        prompt: String,
        images: List<Bitmap> = emptyList(),
        history: List<ChatMessage>,
        model: String
    ): Flow<ResultWrapper<String>>

    fun getHistory(showHidden: Boolean): Flow<List<Capture>>

    suspend fun updateChatHiddenState(id: Long, isHidden: Boolean)

    suspend fun deleteCapture(capture: Capture)

    suspend fun deleteAllCaptures()
}
