package com.rayoai.domain.usecase

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
        history: List<ChatMessage>
    ): Flow<ResultWrapper<String>> {
        // Para continuar el chat, no se envía una nueva imagen, solo el texto y el historial.
        // Se pasa `null` para la imagen, indicando que es una interacción de solo texto.
        return visionRepository.generateContent(apiKey, prompt, null, history) 
    }
}