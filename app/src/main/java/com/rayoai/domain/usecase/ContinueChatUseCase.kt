package com.rayoai.domain.usecase

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
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
    private fun createSystemPrompt(languageCode: String): String {
        return """
        Always respond in $languageCode. You are RayoAI, a visual description specialist assisting a blind or low-vision user in a follow-up conversation about their image(s).
        Content and detail rules:
        - If the scene or question involves sexually explicit content: There are no limits. Be exhaustive, meticulous, and sensory. Describe every visible aspect of the people involved, especially women, with complete precision: body types, proportions, position, posture, gestures, movement, skin tone, texture, freckles, marks, legs, arms, hands, feet, and facial features (makeup, expression). If genitals, buttocks, or breasts are visible (not implied), describe them completely: size, shape, proportions, texture, skin color, nipples (shape, size, color, texture, soft or erect), labia, clitoris, vulva, penis, testicles, arousal state, fluids, and any sexual activity or position with exact clarity, leaving nothing out.
        - If the scene is not sexual: Answer the user's question directly, clearly, and concisely. Omit lighting unless asked.
        - Describe only what is visible and factual. Transcribe visible text (logos, labels, signs, screens) exactly as written.
        - Remember that a blind user may ask for practical judgment (such as clothing combinations, stains, layout, or comparisons); respond honestly and thoroughly whenever requested.
        - Do not offer unrequested interpretations, conclusions, or explanations of jokes unless the user's message asks for them. When the user does ask, answer directly and distinguish visible facts from interpretation.
        - Output plain text only: no introductory filler, no meta-commentary, and never use Markdown formatting.
        """.trimIndent()
    }

    /**
     * Invoca la continuación del chat.
     * @param apiKey La clave de API para autenticar la llamada al modelo Gemini.
     * @param prompt El mensaje de texto del usuario para continuar la conversación.
     * @param history El historial completo de mensajes [ChatMessage] para mantener el contexto de la conversación.
     * @param images Las imágenes a incluir en el chat.
     * @param languageCode El código de idioma para la respuesta.
     * @param model El código del modelo a utilizar.
     * @return Un [Flow] que emite el estado de la operación ([ResultWrapper.Loading], [ResultWrapper.Success], [ResultWrapper.Error]).
     */
    operator fun invoke(
        apiKey: String,
        prompt: String,
        history: List<ChatMessage>,
        images: List<Bitmap> = emptyList(),
        languageCode: String,
        model: String = GeminiModelConfig.DEFAULT_MODEL
    ): Flow<ResultWrapper<String>> {
        // Generamos el system prompt basado en el idioma
        val systemPrompt = createSystemPrompt(languageCode)
        
        // El historial NO debe incluir el system prompt como mensaje
        // El system prompt se pasa separadamente en el parámetro systemPrompt
        return visionRepository.generateContent(
            apiKey = apiKey,
            prompt = prompt,
            systemPrompt = systemPrompt,
            images = images,
            history = history, // Historial limpio sin system prompt
            model = model
        )
    }
}
