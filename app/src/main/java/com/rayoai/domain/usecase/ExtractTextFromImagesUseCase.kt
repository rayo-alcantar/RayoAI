package com.rayoai.domain.usecase

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

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
        return visionRepository.generateContent(apiKey, prompt, images, emptyList(), model)
    }
}
