package com.rayoai.domain.usecase.pdf

import com.rayoai.data.local.pdfdb.PdfDao
import javax.inject.Inject

class DeletePdfDocumentUseCase @Inject constructor(
    private val pdfDao: PdfDao
) {
    suspend operator fun invoke(id: Long) {
        pdfDao.deleteById(id)
    }
}

