package com.rayoai.domain.repository

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface VisionRepository {
    fun generateContent(
        apiKey: String,
        prompt: String,
        images: List<Bitmap> = emptyList(),
        history: List<ChatMessage>
    ): Flow<ResultWrapper<String>>
}