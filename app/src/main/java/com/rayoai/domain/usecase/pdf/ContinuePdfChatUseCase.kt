package com.rayoai.domain.usecase.pdf

import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.PdfDocument
import com.rayoai.domain.repository.VisionRepository
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject

class ContinuePdfChatUseCase @Inject constructor(
    private val visionRepository: VisionRepository
) {
    operator fun invoke(
        apiKey: String,
        question: String,
        document: PdfDocument,
        history: List<ChatMessage>,
        languageCode: String = Locale.getDefault().language,
        model: String = GeminiModelConfig.DEFAULT_MODEL
    ): Flow<ResultWrapper<String>> {
        val documentContext = selectRelevantContext(document.content, question)
        val prompt = """
            Documento: ${document.name}

            Contenido remediado del PDF:
            $documentContext

            Pregunta del usuario:
            $question
        """.trimIndent()

        return visionRepository.generateContent(
            apiKey = apiKey,
            prompt = prompt,
            systemPrompt = createSystemPrompt(languageCode),
            images = emptyList(),
            history = history,
            model = model
        )
    }

    private fun createSystemPrompt(languageCode: String): String {
        return """
            Responde siempre en el idioma del usuario; si no es claro, usa $languageCode.
            Eres un asistente especializado en responder preguntas sobre un PDF remediado para accesibilidad.
            Usa únicamente el contenido del documento y el historial de esta conversación.
            Si la respuesta no está en el documento, dilo claramente y no inventes datos.
            Cuando sea útil, menciona secciones, encabezados, tablas o elementos descritos en el contenido.
            Sé claro, directo y accesible para una persona que usa lector de pantalla.
            No uses Markdown salvo que el usuario lo pida explícitamente.
        """.trimIndent()
    }

    private fun selectRelevantContext(html: String, question: String): String {
        val plainText = htmlToPlainText(html)
        if (plainText.length <= MAX_CONTEXT_CHARS) return plainText

        val chunks = plainText.chunked(CHUNK_SIZE)
        val queryTerms = question
            .lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 4 }
            .toSet()

        if (queryTerms.isEmpty()) {
            return chunks.take(MAX_CHUNKS).joinToString("\n\n")
        }

        return chunks
            .mapIndexed { index, chunk -> index to scoreChunk(chunk, queryTerms) }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
            .take(MAX_CHUNKS)
            .sortedBy { it.first }
            .joinToString("\n\n") { chunks[it.first] }
    }

    private fun scoreChunk(chunk: String, queryTerms: Set<String>): Int {
        val normalized = chunk.lowercase()
        return queryTerms.sumOf { term -> Regex("\\b${Regex.escape(term)}\\b").findAll(normalized).count() }
    }

    private fun htmlToPlainText(html: String): String {
        return html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?i)</(p|div|section|article|h[1-6]|li|tr|table)>"), "\n")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private companion object {
        const val MAX_CONTEXT_CHARS = 24_000
        const val CHUNK_SIZE = 4_000
        const val MAX_CHUNKS = 6
    }
}
