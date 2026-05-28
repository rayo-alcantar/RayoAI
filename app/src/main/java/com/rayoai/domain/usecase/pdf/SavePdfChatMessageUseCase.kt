package com.rayoai.domain.usecase.pdf

import com.rayoai.data.local.pdfdb.PdfChatDao
import com.rayoai.data.local.pdfdb.PdfChatMessageEntity
import javax.inject.Inject

class SavePdfChatMessageUseCase @Inject constructor(
    private val pdfChatDao: PdfChatDao
) {
    suspend operator fun invoke(
        pdfDocumentId: Long,
        content: String,
        isFromUser: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        return pdfChatDao.insert(
            PdfChatMessageEntity(
                pdfDocumentId = pdfDocumentId,
                content = content,
                isFromUser = isFromUser,
                timestamp = timestamp
            )
        )
    }
}
