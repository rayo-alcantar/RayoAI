package com.rayoai.domain.usecase.pdf

import com.rayoai.data.local.pdfdb.PdfDao
import com.rayoai.domain.model.PdfDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetPdfDocumentByIdUseCase @Inject constructor(
    private val pdfDao: PdfDao
) {
    operator fun invoke(id: Long): Flow<PdfDocument?> = pdfDao.getById(id).map { e ->
        e?.let { PdfDocument(it.id, it.name, it.uri, it.content, it.timestamp) }
    }
}

