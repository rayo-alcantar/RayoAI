package com.rayoai.domain.repository

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.Capture
import com.rayoai.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface VisionRepository {
    /**
     * Genera contenido usando el modelo Gemini.
     * 
     * @param apiKey API key de Google AI Studio
     * @param prompt Prompt del usuario
     * @param systemPrompt Instrucciones del sistema (separadas del contenido del usuario)
     * @param images Lista de imágenes a incluir en el contexto
     * @param history Historial de mensajes de la conversación
     * @param model Nombre del modelo a usar
     */
    fun generateContent(
        apiKey: String,
        prompt: String,
        systemPrompt: String? = null,
        images: List<Bitmap> = emptyList(),
        history: List<ChatMessage>,
        model: String,
        responseMimeType: String? = null
    ): Flow<ResultWrapper<String>>

    fun getHistory(showHidden: Boolean): Flow<List<Capture>>

    suspend fun updateChatHiddenState(id: Long, isHidden: Boolean)

    suspend fun deleteCapture(capture: Capture)

    suspend fun deleteAllCaptures()
}
