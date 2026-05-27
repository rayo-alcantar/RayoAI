package com.rayoai.presentation.ui.screens.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.domain.model.VideoDocument
import com.rayoai.domain.usecase.video.DeleteVideoDocumentUseCase
import com.rayoai.domain.usecase.video.GetVideoDocumentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ToolsViewModel @Inject constructor(
    getVideoDocumentsUseCase: GetVideoDocumentsUseCase,
    private val deleteVideoDocumentUseCase: DeleteVideoDocumentUseCase
) : ViewModel() {

    val videoDocuments: StateFlow<List<VideoDocument>> = getVideoDocumentsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteVideo(doc: VideoDocument) {
        viewModelScope.launch { deleteVideoDocumentUseCase(doc.id) }
    }
}
