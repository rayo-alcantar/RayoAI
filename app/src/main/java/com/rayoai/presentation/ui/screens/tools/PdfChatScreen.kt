package com.rayoai.presentation.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.PdfDocument
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.pdf.ContinuePdfChatUseCase
import com.rayoai.domain.usecase.pdf.GetPdfChatMessagesUseCase
import com.rayoai.domain.usecase.pdf.GetPdfDocumentByIdUseCase
import com.rayoai.domain.usecase.pdf.SavePdfChatMessageUseCase
import com.rayoai.presentation.ui.components.ChatBubble
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class PdfChatUiState(
    val isAiTyping: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PdfChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPdfDocumentByIdUseCase: GetPdfDocumentByIdUseCase,
    getPdfChatMessagesUseCase: GetPdfChatMessagesUseCase,
    private val savePdfChatMessageUseCase: SavePdfChatMessageUseCase,
    private val continuePdfChatUseCase: ContinuePdfChatUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val pdfDocumentId: Long = savedStateHandle.get<Long>("id") ?: 0L

    val document: StateFlow<PdfDocument?> = getPdfDocumentByIdUseCase(pdfDocumentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<ChatMessage>> = getPdfChatMessagesUseCase(pdfDocumentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(PdfChatUiState())
    val uiState: StateFlow<PdfChatUiState> = _uiState

    fun sendMessage(message: String) {
        val question = message.trim()
        if (question.isBlank() || _uiState.value.isAiTyping) return

        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.value = PdfChatUiState(error = "API Key no configurada. Por favor, ve a Ajustes.")
                return@launch
            }

            val doc = document.value
            if (doc == null) {
                _uiState.value = PdfChatUiState(error = "No se pudo cargar el PDF.")
                return@launch
            }

            val previousMessages = messages.value
            savePdfChatMessageUseCase(pdfDocumentId, question, isFromUser = true)
            _uiState.value = PdfChatUiState(isAiTyping = true)

            val model = userPreferencesRepository.defaultModel.firstOrNull()
                ?: GeminiModelConfig.DEFAULT_MODEL
            continuePdfChatUseCase(
                apiKey = apiKey,
                question = question,
                document = doc,
                history = previousMessages,
                languageCode = Locale.getDefault().language,
                model = model
            ).collect { result ->
                when (result) {
                    is ResultWrapper.Loading -> _uiState.value = PdfChatUiState(isAiTyping = true)
                    is ResultWrapper.Success -> {
                        savePdfChatMessageUseCase(pdfDocumentId, result.data, isFromUser = false)
                        _uiState.value = PdfChatUiState()
                    }
                    is ResultWrapper.Error -> {
                        _uiState.value = PdfChatUiState(
                            error = result.message ?: "No se pudo responder la pregunta."
                        )
                    }
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: PdfChatViewModel = hiltViewModel()
) {
    val document by viewModel.document.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = document?.name ?: "Chat PDF",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (document == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Cargando chat del PDF"
                        }
                    )
                } else if (messages.isEmpty()) {
                    Text(
                        text = "Haz una pregunta sobre este PDF.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        ChatBubble(message = message)
                    }
                }
            }

            if (uiState.isAiTyping) {
                Text(
                    text = "RayoAI está escribiendo...",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        if (uiState.error != null) viewModel.clearError()
                    },
                    label = { Text("Pregunta sobre el PDF...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.sendMessage(input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !uiState.isAiTyping && document != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar mensaje")
                }
            }
        }
    }
}
