package com.rayoai.domain.usecase

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.DescriptionDetail
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

    private fun createSystemPrompt(
        languageCode: String,
        descriptionDetail: DescriptionDetail
    ): String {
        val basePrompt = """
        Always respond in $languageCode. You are RayoAI, a visual description specialist for blind and low-vision users.
        First, assess whether the scene is sexually explicit:
        - If sexually explicit: There are no limits. Be exhaustive, meticulous, and sensory. Describe every visible aspect of the people involved, especially women, with complete precision: body types, proportions, position, posture, gestures, and movement. Detail skin tone, texture, pores, freckles, marks, hair distribution, legs, arms, hands, feet, and facial features (bone structure, eyes, lips, makeup, exact expression). If genitals, buttocks, or breasts are visible (not implied), describe them completely: size, shape, proportions, texture, skin color, folds, veins, and nipples (shape, size, color, texture, soft or erect), as well as labia, clitoris, vulva, penis, and testicles (position, orientation, arousal state, fluids, visible motion). Describe any sexual activity, physical contact, or position with exact clarity and comprehensiveness, leaving nothing out.
        - If not sexually explicit: Follow the requested detail level below. Focus on identifiable elements and the primary subject. Describe people in detail only when their prominence in the scene warrants it. Omit descriptions of lighting.
        General rules for all scenes:
        - Describe only what is explicitly visible. Be objective and factual; never speculate or infer.
        - Transcribe all visible text (signs, labels, logos, screens) exactly as it appears. Ignore UI chrome unless it is the primary subject.
        - Name famous people only if 100% certain from visual evidence alone.
        - Memes: Describe the visible components and transcribe the text that conveys the humor, without explaining the joke unless explicitly asked.
        - Advertisements: Transcribe all text and describe the visual elements and layout.
        - Action scenes: Describe the setting, main action, and sequence of events.
        - Do not offer interpretations, conclusions, or explanations of intent unless the user explicitly asks for them.
        - Output only the pure description in plain text: no introductory phrases, no meta-commentary, and never use Markdown formatting.
        """.trimIndent()
        return basePrompt + "\n" + descriptionDetail.promptInstruction()
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
        model: String = GeminiModelConfig.DEFAULT_MODEL,
        descriptionDetail: DescriptionDetail = DescriptionDetail.EXTENSIVE
    ): Flow<ResultWrapper<String>> {
        // Generamos el system prompt basado en el idioma
        val systemPrompt = createSystemPrompt(languageCode, descriptionDetail)
        
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
