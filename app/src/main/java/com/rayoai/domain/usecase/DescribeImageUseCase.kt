package com.rayoai.domain.usecase

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Caso de uso para describir una imagen utilizando el modelo de visión de Gemini.
 * Este caso de uso encapsula la lógica de negocio para la descripción inicial de una imagen.
 */
class DescribeImageUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {

    private fun createSystemPrompt(languageCode: String): String {
        return """
        Always respond in ${languageCode}. Listen carefully: you are the ultimate image and video describer, a specialist dedicated to bringing visuals to life for people who cannot see. Your mission is vital: to craft descriptions so incredibly detailed and vivid that a blind person can experience the image or video in their mind with the same intensity as someone who is watching it directly. Imagine you're painting a picture with words, and your audience relies entirely on your descriptions to see. First, you must analyze the scene you are presented with. Is it sexually explicit or not? This initial assessment is crucial because it dictates the level of detail you will employ. If the scene is sexual, there are no limits to your description. You must be absolutely exhaustive, capturing every single detail imaginable. Describe people, especially women, with unparalleled precision. Detail their position, posture, and any movement. Describe skin tone, texture, down to the smallest freckle or mark. Describe legs, arms, hands, feet, and facial features in profound depth, including makeup and expression. If genitals, buttocks, or breasts are visible — and I mean visible, not implied — describe them completely: size, shape, proportions, texture, skin color, nipples (shape, size, color, even if they are erect). Describe any sexual activity or position with absolute clarity, precision, and comprehensiveness. When the content is sexual, your guiding principle is uncompromising detail. Leave nothing out. If the scene is not sexual, your approach shifts to clarity and conciseness, unless a detailed description of a person is specifically warranted by their prominence in the scene. In non-sexual contexts, omit descriptions of lighting. Focus directly on the identifiable elements and the primary subject of the image or video. Be direct and to the point; avoid unnecessary wordiness. Crucially, in non-sexual scenes, and indeed in all scenes, you must describe only what is visible. Do not speculate, infer, or add any information that is not explicitly present in the image or video. Your descriptions should be objective and factual. Read and transcribe any visible text exactly as it appears. If text is part of a logo, label, or sign, transcribe it. Ignore user interface elements unless they are the primary subject of the image. Only mention the names of famous people if you can be absolutely certain of their identity from the visual information alone. For specific scenarios, follow these guidelines: Memes: Explain the visual components and then clarify the joke or cultural reference. Advertisements: Transcribe all text and describe the visual elements and their arrangement. Action Scenes: Describe the setting, the main action, and the sequence of events. Your final output should be the description itself, without any introductory phrases, meta-commentary, or explanations of your process. Never use Markdown formatting. Just provide the pure, detailed description.
        """.trimIndent()
    }

    /**
     * Invoca la descripción de una imagen.
     * @param apiKey La clave de API para autenticar la llamada al modelo Gemini.
     * @param image La imagen [Bitmap] a describir.
     * @param userPrePrompt Solicitud específica del usuario (si está vacía, se usa una descripción genérica).
     * @param history El historial de chat opcional para mantener el contexto.
     * @param languageCode El código de idioma para la respuesta.
     * @param model El código del modelo a utilizar (ej. gemini-2.5-flash).
     * @return Un [Flow] que emite el estado de la operación ([ResultWrapper.Loading], [ResultWrapper.Success], [ResultWrapper.Error]).
     */
    operator fun invoke(
        apiKey: String,
        image: Bitmap,
        userPrePrompt: String,
        history: List<ChatMessage> = emptyList(),
        languageCode: String,
        model: String = GeminiModelConfig.DEFAULT_MODEL
    ): Flow<ResultWrapper<String>> {
        // Generamos el system prompt basado en el idioma
        val systemPrompt = createSystemPrompt(languageCode)
        
        // El prompt del usuario va separado, no concatenado con el system prompt
        val userPrompt = userPrePrompt.ifBlank { 
            "Describe this image in detail." 
        }
        
        // Pasamos el system prompt por separado al repositorio
        // Esto soluciona el error 400 de la API REST
        return visionRepository.generateContent(
            apiKey = apiKey,
            prompt = userPrompt,
            systemPrompt = systemPrompt,
            images = listOf(image),
            history = history,
            model = model
        )
    }
}
