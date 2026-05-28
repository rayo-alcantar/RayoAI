package com.rayoai.domain.usecase.pdf

import com.rayoai.data.local.pdfdb.PdfChatDao
import com.rayoai.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetPdfChatMessagesUseCase @Inject constructor(
    private val pdfChatDao: PdfChatDao
) {
    operator fun invoke(pdfDocumentId: Long): Flow<List<ChatMessage>> {
        return pdfChatDao.getMessages(pdfDocumentId).map { messages ->
            messages.map { ChatMessage(content = it.content, isFromUser = it.isFromUser) }
        }
    }
}
