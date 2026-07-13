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
import androidx.compose.ui.platform.LocalContext
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
import com.rayoai.R
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.ChatMessage
import com.rayoai.domain.model.VideoDocument
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.usecase.video.ContinueVideoChatUseCase
import com.rayoai.domain.usecase.video.GetVideoChatMessagesUseCase
import com.rayoai.domain.usecase.video.GetVideoDocumentByIdUseCase
import com.rayoai.domain.usecase.video.SaveVideoChatMessageUseCase
import com.rayoai.presentation.ui.components.ChatBubble
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class VideoChatUiState(val isAiTyping: Boolean = false, val error: String? = null)

@HiltViewModel
class VideoChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getVideoDocumentByIdUseCase: GetVideoDocumentByIdUseCase,
    getVideoChatMessagesUseCase: GetVideoChatMessagesUseCase,
    private val saveVideoChatMessageUseCase: SaveVideoChatMessageUseCase,
    private val continueVideoChatUseCase: ContinueVideoChatUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val videoDocumentId = savedStateHandle.get<Long>("id") ?: 0L

    val document: StateFlow<VideoDocument?> = getVideoDocumentByIdUseCase(videoDocumentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val messages: StateFlow<List<ChatMessage>> = getVideoChatMessagesUseCase(videoDocumentId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _uiState = MutableStateFlow(VideoChatUiState())
    val uiState: StateFlow<VideoChatUiState> = _uiState

    fun sendMessage(context: Context, message: String) {
        val question = message.trim()
        if (question.isBlank() || _uiState.value.isAiTyping) return
        viewModelScope.launch {
            val apiKey = userPreferencesRepository.apiKey.first()
            if (apiKey.isNullOrBlank()) {
                _uiState.value = VideoChatUiState(error = context.getString(R.string.scan_pdf_user_message_missing_api_key))
                return@launch
            }
            val video = document.value
            if (video == null) {
                _uiState.value = VideoChatUiState(error = context.getString(R.string.video_not_found))
                return@launch
            }
            saveVideoChatMessageUseCase(videoDocumentId, question, isFromUser = true)
            _uiState.value = VideoChatUiState(isAiTyping = true)
            continueVideoChatUseCase(
                apiKey = apiKey,
                question = question,
                video = video,
                history = messages.value,
                context = context,
                languageCode = Locale.getDefault().language
            ).collect { result ->
                when (result) {
                    ResultWrapper.Loading -> _uiState.value = VideoChatUiState(isAiTyping = true)
                    is ResultWrapper.Success -> {
                        saveVideoChatMessageUseCase(videoDocumentId, result.data, isFromUser = false)
                        _uiState.value = VideoChatUiState()
                    }
                    is ResultWrapper.Error -> _uiState.value = VideoChatUiState(error = result.message)
                }
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: VideoChatViewModel = hiltViewModel()
) {
    val document by viewModel.document.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    val backText = stringResource(R.string.back)
    val chatTitle = stringResource(R.string.video_chat_title)
    val sendText = stringResource(R.string.send_message)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document?.name ?: chatTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backText)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (document == null) {
                    CircularProgressIndicator()
                } else if (messages.isEmpty()) {
                    Text(
                        stringResource(R.string.video_chat_empty),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { items(messages) { ChatBubble(it) } }
            }
            if (uiState.isAiTyping) {
                Text(
                    stringResource(R.string.ai_typing),
                    modifier = Modifier.fillMaxWidth().padding(8.dp).semantics { liveRegion = LiveRegionMode.Assertive },
                    textAlign = TextAlign.Center
                )
            }
            uiState.error?.let { error ->
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(8.dp).semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; viewModel.clearError() },
                    label = { Text(stringResource(R.string.video_chat_ask_hint)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.sendMessage(context, input); input = "" },
                    enabled = input.isNotBlank() && !uiState.isAiTyping && document != null,
                    modifier = Modifier.semantics { contentDescription = sendText }
                ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) }
            }
        }
    }
}
