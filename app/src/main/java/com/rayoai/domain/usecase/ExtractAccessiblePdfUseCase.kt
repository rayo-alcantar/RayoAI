package com.rayoai.domain.usecase

import android.graphics.Bitmap
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExtractAccessiblePdfUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    operator fun invoke(
        apiKey: String,
        images: List<Bitmap>,
        pageRange: String,
        languageCode: String,
        isFirstRange: Boolean,
        model: String = GeminiModelConfig.DEFAULT_MODEL
    ): Flow<ResultWrapper<String>> {
        val titleInstruction = if (isFirstRange) {
            "Genera el titulo descriptivo del documento a partir de este fragmento."
        } else {
            "Conserva un titulo descriptivo breve para este fragmento, sin inventar portada."
        }
        val languageInstruction = "Responde usando el idioma principal del documento. Si no es claro, usa el idioma del dispositivo: $languageCode."

        val prompt = """
            Eres un experto en remediacion PDF/UA y accesibilidad WCAG 2.2 con especializacion en documentos cientificos y matematicos.
            Estas imagenes corresponden al fragmento: $pageRange.
            $languageInstruction

            REGLAS DE ORO:
            1. FIDELIDAD Y COMPLETITUD: Transcribe TODO el texto. Si una pagina o seccion NO tiene texto legible pero contiene imagenes, diagramas, graficos o fotos, DEBES incluir un elemento tipo "img" con una descripcion detallada en "alt". NO omitas ninguna pagina.
            2. MATEMATICAS Y CIENCIA:
               - Transcribe TODAS las formulas, ecuaciones y simbolos matematicos usando notacion LaTeX.
               - Usa ${'$'} ... ${'$'} para formulas integradas en el texto (inline).
               - Usa ${'$'}${'$'} ... ${'$'}${'$'} para ecuaciones en bloques independientes.
               - Asegurate de que los superindices, subindices y caracteres especiales sean correctos.
            3. CALIDAD DE ESCANEO: Si el documento esta mal escaneado, borroso o tiene ruido, usa el contexto para reconstruir el texto y las formulas de manera logica y profesional.
            4. JERARQUIA SEMANTICA:
               - Usa "h2" para secciones principales y "h3" para subsecciones.
               - NO uses "h1" porque se generara automaticamente.
            5. TABLAS: El primer elemento de "rows" DEBE ser el encabezado. Anade un "summary" descriptivo que explique que datos contiene la tabla.
            6. FIRMAS: Usa type "signature" con el "owner" cuando exista un nombre legible.
            7. $titleInstruction

            Devuelve UNICAMENTE JSON valido, sin Markdown, sin comentarios y sin texto fuera del JSON.
            FORMATO DE SALIDA:
            {
              "idioma": "es",
              "titulo": "Titulo descriptivo del documento",
              "elementos": [
                {"type": "h2", "content": "Seccion de Datos"},
                {"type": "p", "content": "Texto con formula inline ${'$'}E=mc^2${'$'}..."},
                {"type": "p", "content": "${'$'}${'$'}\\int_0^\\infty e^{-x^2} dx = \\frac{\\sqrt{\\pi}}{2}${'$'}${'$'}"},
                {"type": "img", "src": "inline", "alt": "Grafica de la funcion..."},
                {"type": "table", "rows": [["X", "Y"], ["1", "2"]], "summary": "Valores calculados"},
                {"type": "signature", "owner": "Nombre"}
              ]
            }
        """.trimIndent()

        return visionRepository.generateContent(
            apiKey = apiKey,
            prompt = prompt,
            systemPrompt = null,
            images = images,
            history = emptyList(),
            model = model,
            responseMimeType = "application/json"
        )
    }
}
