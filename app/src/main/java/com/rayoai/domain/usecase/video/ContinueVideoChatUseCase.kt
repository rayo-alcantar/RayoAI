package com.rayoai.domain.usecase.video

import android.content.Context
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.VideoDocument
import com.rayoai.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import java.util.Locale
import javax.inject.Inject

class ContinueVideoChatUseCase @Inject constructor(
    private val videoRepository: VideoRepository
) {
    operator fun invoke(
        apiKey: String,
        question: String,
        video: VideoDocument,
        history: List<ChatMessage>,
        context: Context,
        languageCode: String = Locale.getDefault().language
    ): Flow<ResultWrapper<String>> = videoRepository.continueVideoChat(
        apiKey = apiKey,
        question = question,
        video = video,
        history = history,
        systemPrompt = """
            Responde siempre en el idioma del usuario; si no es claro, usa $languageCode.
            Responde preguntas sobre el video adjunto usando solamente lo que es visible o audible en él y el historial del chat.
            Si el dato no aparece en el video, dilo con claridad y no inventes información.
            Cuando sea útil, menciona momentos o marcas de tiempo aproximadas.
            Sé directo, accesible y no uses Markdown salvo que el usuario lo pida.
        """.trimIndent(),
        context = context
    )
}
