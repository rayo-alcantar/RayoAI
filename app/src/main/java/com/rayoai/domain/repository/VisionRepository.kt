package com.rayoai.domain.repository

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface VisionRepository {
    fun generateContent(
        apiKey: String,
        prompt: String,
        image: Bitmap? = null, // Make image optional
        history: List<ChatMessage>
    ): Flow<ResultWrapper<String>>
}