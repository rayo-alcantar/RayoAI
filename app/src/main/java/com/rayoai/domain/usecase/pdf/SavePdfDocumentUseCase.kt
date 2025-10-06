package com.rayoai.domain.usecase.pdf

import com.rayoai.data.local.pdfdb.PdfDao
import com.rayoai.data.local.pdfdb.PdfDocumentEntity
import javax.inject.Inject

class SavePdfDocumentUseCase @Inject constructor(
    private val pdfDao: PdfDao
) {
    suspend operator fun invoke(name: String, uri: String, content: String, timestamp: Long): Long {
        return pdfDao.insert(PdfDocumentEntity(name = name, uri = uri, content = content, timestamp = timestamp))
    }
}

