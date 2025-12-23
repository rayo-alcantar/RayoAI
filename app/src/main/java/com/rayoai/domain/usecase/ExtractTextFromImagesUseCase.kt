package com.rayoai.domain.usecase

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Caso de uso para extraer texto de imágenes usando OCR de Gemini.
 */
class ExtractTextFromImagesUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    operator fun invoke(
        apiKey: String,
        images: List<Bitmap>,
        languageCode: String,
        model: String = GeminiModelConfig.DEFAULT_MODEL
    ): Flow<ResultWrapper<String>> {
        val prompt = """
            Extrae el texto de este documento. Si encuentras imágenes, describe brevemente su contenido en lugar de ignorarlas.
            No añadas ningún comentario o texto introductorio. Solo devuelve el texto extraído y las descripciones.
            Responde en el idioma del dispositivo: $languageCode.
        """.trimIndent()
        
        // Llamada actualizada con el nuevo signature que incluye systemPrompt
        return visionRepository.generateContent(
            apiKey = apiKey,
            prompt = prompt,
            systemPrompt = null, // Sin system prompt para OCR
            images = images,
            history = emptyList(),
            model = model
        )
    }
}
