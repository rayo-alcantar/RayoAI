package com.rayoai.domain.usecase

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Caso de uso para continuar una conversación de chat sobre una imagen o tema dado.
 * Este caso de uso permite enviar mensajes de texto al modelo Gemini, manteniendo el contexto del chat.
 */
class ContinueChatUseCase @Inject constructor(
    
    private val visionRepository: VisionRepository
) {
    companion object {
        private val SYSTEM_PROMPT = """
        Describe images or videos only in Spanish, with maximum detail for blind people. 
        - For sexual scenes, describe absolutely everything visible: bodies, actions, features, no limits.
        - For non-sexual, be concise and direct. Describe only what is visible, never speculate or add opinions.
        - If people are central, describe their appearance and actions clearly.
        - Read and transcribe any visible text exactly.
        - Ignore interface elements unless essential. Mention famous people only if visible.
        - For memes, explain the joke; for ads, transcribe text; for action, describe setting, main activity, and sequence.
        Never use Markdown or explain your process. Only output the description.
        """.trimIndent()
    }
    /**
     * Invoca la continuación del chat.
     * @param apiKey La clave de API para autenticar la llamada al modelo Gemini.
     * @param prompt El mensaje de texto del usuario para continuar la conversación.
     * @param history El historial completo de mensajes [ChatMessage] para mantener el contexto de la conversación.
     * @return Un [Flow] que emite el estado de la operación ([ResultWrapper.Loading], [ResultWrapper.Success], [ResultWrapper.Error]).
     */
    operator fun invoke(
        apiKey: String,
        prompt: String,
        history: List<ChatMessage>,
        images: List<Bitmap> = emptyList() // Añadir el parámetro de la imagen
    ): Flow<ResultWrapper<String>> {
        val fullHistory = listOf(
            ChatMessage(content = SYSTEM_PROMPT, isFromUser = false)
        ) + history
        // Para continuar el chat, se envía la imagen junto con el texto y el historial.
        return visionRepository.generateContent(apiKey, prompt, images, fullHistory)
    }
}