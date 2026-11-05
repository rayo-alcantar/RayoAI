package com.rayoai.domain.usecase.proactive

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GenerateVisualProfileUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    operator fun invoke(
        apiKey: String,
        images: List<Bitmap>,
        objectName: String,
        languageCode: String,
        model: String
    ): Flow<ResultWrapper<String>> {
        val isSpanish = languageCode.startsWith("es", ignoreCase = true)
        val systemPrompt = if (isSpanish) {
            """
            Eres un experto en visión por computadora asistiva para personas con discapacidad visual.
            El usuario te proporciona foto(s) de referencia de su objeto personal denominado: "$objectName".
            Analiza minuciosamente las imágenes y genera un perfil visual inequívoco, distintivo y compacto de 2 a 3 oraciones.
            Enfócate en rasgos físicos observables en tiempo real:
            - Color exacto, tonalidades y contrastes.
            - Forma geométrica, proporciones y material aparente (plástico, metal, madera, tela, cuero).
            - Texturas, estampados, logotipos, calcomanías, marcas de desgaste o llaveros/accesorios adjuntos.
            Este perfil será usado por una cámara en vivo para localizar exactamente este objeto entre otros similares.
            Responde únicamente con la descripción visual en español, directa y sin introducciones.
            """.trimIndent()
        } else {
            """
            You are an assistive computer vision expert for visually impaired users.
            The user provides reference photo(s) of their personal object named: "$objectName".
            Thoroughly examine the image(s) and generate an unmistakable, distinctive, and compact visual fingerprint of 2 to 3 sentences.
            Focus on real-time observable physical traits:
            - Exact color, shades, and contrast.
            - Geometric shape, proportions, and apparent material (plastic, metal, wood, fabric, leather).
            - Textures, patterns, logos, stickers, wear marks, or attached keychains/accessories.
            This profile will be used by a live camera feed to pinpoint this exact object among similar generic items.
            Output only the concise visual description in English, with no introductory or conversational text.
            """.trimIndent()
        }

        val prompt = if (isSpanish) {
            "Genera el perfil visual distintivo para '$objectName' a partir de esta(s) imagen(es)."
        } else {
            "Generate the distinctive visual profile for '$objectName' based on this/these image(s)."
        }

        return visionRepository.generateContent(
            apiKey = apiKey,
            prompt = prompt,
            systemPrompt = systemPrompt,
            images = images,
            history = emptyList(),
            model = model
        )
    }
}
