package com.rayoai.presentation.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.domain.model.PdfDocument
import com.rayoai.domain.usecase.pdf.DeletePdfDocumentUseCase
import com.rayoai.domain.usecase.pdf.GetPdfDocumentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    getPdfDocumentsUseCase: GetPdfDocumentsUseCase,
    private val deletePdfDocumentUseCase: DeletePdfDocumentUseCase
) : ViewModel() {

    val pdfDocuments: StateFlow<List<PdfDocument>> = getPdfDocumentsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(doc: PdfDocument) {
        viewModelScope.launch { deletePdfDocumentUseCase(doc.id) }
    }
}

