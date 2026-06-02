package com.rayoai.presentation.ui.screens.tools

import android.content.Context
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
import androidx.compose.ui.res.stringResource
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
import com.rayoai.R
import com.rayoai.domain.model.AudioRecording
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.GeminiModelConfig
import com.rayoai.domain.model.PdfDocument
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.TranscribeAudioUseCase
import com.rayoai.domain.usecase.pdf.ContinuePdfChatUseCase
import com.rayoai.domain.usecase.pdf.GetPdfChatMessagesUseCase
import com.rayoai.domain.usecase.pdf.GetPdfDocumentByIdUseCase
import com.rayoai.domain.usecase.pdf.SavePdfChatMessageUseCase
import com.rayoai.presentation.ui.components.ChatBubble
import com.rayoai.presentation.ui.components.VoiceInputButton
import com.rayoai.presentation.voice.AndroidSpeechFileTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import androidx.compose.ui.platform.LocalContext

data class PdfChatUiState(
    val isAiTyping: Boolean = false,
    val isTranscribingAudio: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PdfChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPdfDocumentByIdUseCase: GetPdfDocumentByIdUseCase,
    getPdfChatMessagesUseCase: GetPdfChatMessagesUseCase,
    private val savePdfChatMessageUseCase: SavePdfChatMessageUseCase,
    private val continuePdfChatUseCase: ContinuePdfChatUseCase,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val pdfDocumentId: Long = savedStateHandle.get<Long>("id") ?: 0L

    val document: StateFlow<PdfDocument?> = getPdfDocumentByIdUseCase(pdfDocumentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val messages: StateFlow<List<ChatMessage>> = getPdfChatMessagesUseCase(pdfDocumentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(PdfChatUiState())
    val uiState: StateFlow<PdfChatUiState> = _uiState

    fun sendMessage(context: Context, message: String) {
        val question = message.trim()
        if (question.isBlank() || _uiState.value.isAiTyping) return

        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.value = PdfChatUiState(error = context.getString(R.string.scan_pdf_user_message_missing_api_key))
                return@launch
            }

            val doc = document.value
            if (doc == null) {
                _uiState.value = PdfChatUiState(error = context.getString(R.string.scan_pdf_chat_load_failed))
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
                            error = result.message
                        )
                    }
                }
            }
        }
    }

    fun transcribeVoiceAndSend(context: Context, recording: AudioRecording) {
        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.value = PdfChatUiState(error = context.getString(R.string.scan_pdf_user_message_missing_api_key))
                cleanupRecording(recording)
                return@launch
            }

            _uiState.value = PdfChatUiState(isTranscribingAudio = true)
            val model = userPreferencesRepository.defaultModel.firstOrNull()
                ?: GeminiModelConfig.DEFAULT_MODEL
            val text = when (val geminiResult = transcribeAudioUseCase(apiKey, recording.wavFile, model)) {
                is ResultWrapper.Success -> geminiResult.data
                is ResultWrapper.Error -> {
                    AndroidSpeechFileTranscriber(context.applicationContext)
                        .transcribe(recording)
                        .getOrElse { fallbackError ->
                            _uiState.value = PdfChatUiState(
                                error = context.getString(
                                    R.string.scan_pdf_transcribe_failed,
                                    geminiResult.message,
                                    fallbackError.message
                                )
                            )
                            cleanupRecording(recording)
                            return@launch
                        }
                }
                ResultWrapper.Loading -> null
            }

            cleanupRecording(recording)
            _uiState.value = PdfChatUiState()
            if (text.isNullOrBlank()) {
                _uiState.value = PdfChatUiState(error = context.getString(R.string.scan_pdf_transcription_empty))
                return@launch
            }
            sendMessage(context, text)
        }
    }

    private fun cleanupRecording(recording: AudioRecording) {
        recording.wavFile.delete()
        recording.rawPcmFile.delete()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun setError(message: String) {
        _uiState.value = _uiState.value.copy(error = message)
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
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val chatTitle = stringResource(R.string.scan_pdf_chat_title)
    val backText = stringResource(R.string.back)
    val loadingChatText = stringResource(R.string.scan_pdf_loading_chat)
    val askEmptyText = stringResource(R.string.scan_pdf_ask_empty)
    val aiTypingText = stringResource(R.string.ai_typing)
    val transcribingText = stringResource(R.string.scan_pdf_transcribing_audio)
    val askHint = stringResource(R.string.scan_pdf_ask_hint)
    val sendMessageText = stringResource(R.string.send_message)

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
                        text = document?.name ?: chatTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backText)
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
                            contentDescription = loadingChatText
                        }
                    )
                } else if (messages.isEmpty()) {
                    Text(
                        text = askEmptyText,
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
                    text = aiTypingText,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }

            if (uiState.isTranscribingAudio) {
                Text(
                    text = transcribingText,
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
                    label = { Text(askHint) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )
                Spacer(modifier = Modifier.width(8.dp))
                VoiceInputButton(
                    enabled = !uiState.isAiTyping && document != null,
                    isTranscribingAudio = uiState.isTranscribingAudio,
                    onRecordingReady = { recording -> viewModel.transcribeVoiceAndSend(context, recording) },
                    onError = { message -> viewModel.setError(message) }
                )
                IconButton(
                    onClick = {
                        viewModel.sendMessage(context, input)
                        input = ""
                    },
                    enabled = input.isNotBlank() && !uiState.isAiTyping && !uiState.isTranscribingAudio && document != null
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = sendMessageText)
                }
            }
        }
    }
}
