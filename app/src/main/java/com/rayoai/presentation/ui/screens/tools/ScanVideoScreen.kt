@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayoai.presentation.ui.screens.tools

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.R
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.repository.VideoRepository
import com.rayoai.domain.model.VideoDocument
import com.rayoai.domain.usecase.video.SaveVideoDocumentUseCase
import com.rayoai.domain.usecase.video.GetVideoDocumentsUseCase
import com.rayoai.domain.usecase.video.DeleteVideoDocumentUseCase
import com.rayoai.core.ResultWrapper
import com.rayoai.domain.model.VideoLinkValidator
import com.rayoai.domain.model.VideoPromptBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.compose.runtime.collectAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltViewModel
class ScanVideoViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val saveVideoDocumentUseCase: SaveVideoDocumentUseCase,
    private val userPreferencesRepository: UserPreferencesRepository,
    getVideoDocumentsUseCase: GetVideoDocumentsUseCase,
    private val deleteVideoDocumentUseCase: DeleteVideoDocumentUseCase
) : ViewModel() {

    val videoDocuments: StateFlow<List<VideoDocument>> = getVideoDocumentsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var status by mutableStateOf<String?>(null)
        private set

    var resultText by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var uploadProgress by mutableStateOf(0f)
        private set

    fun analyze(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                isLoading = true
                status = context.getString(R.string.scan_video_preparing)
                uploadProgress = 0f

                // Obtener información del video
                val (name, sizeBytes, durationSeconds) = getVideoInfo(context, uri)

                // Validar tamaño (máximo 2 GB)
                if (sizeBytes > 2_000_000_000L) {
                    status = context.getString(R.string.scan_video_too_large)
                    isLoading = false
                    return@launch
                }

                status = context.getString(R.string.scan_video_uploading)
                uploadProgress = 0.3f

                // El system prompt se crea con el mismo que se usa para imágenes
                val languageCode = java.util.Locale.getDefault().language
                val systemPrompt = createSystemPrompt(languageCode)

                uploadProgress = 0.5f
                status = context.getString(R.string.scan_video_processing)

                // Subir y analizar el video
                val result = videoRepository.uploadAndAnalyzeVideo(
                    uri = uri,
                    context = context,
                    systemPrompt = systemPrompt
                )

                when (result) {
                    is ResultWrapper.Success -> {
                        val data = result.data
                        resultText = data.description
                        status = context.getString(R.string.scan_pdf_ready)
                        uploadProgress = 1f
                        isLoading = false

                        // Guardar en la base de datos
                        saveVideoDocumentUseCase(
                            name = name,
                            uri = uri.toString(),
                            content = data.description,
                            timestamp = System.currentTimeMillis(),
                            durationSeconds = durationSeconds.toInt(),
                            sizeBytes = sizeBytes,
                            geminiFileUri = data.geminiFileUri,
                            geminiFileName = data.geminiFileName,
                            geminiMimeType = data.geminiMimeType,
                            geminiFileExpiresAt = data.geminiFileExpiresAt
                        )
                    }
                    is ResultWrapper.Error -> {
                        status = result.message ?: context.getString(R.string.scan_pdf_unknown_error)
                        isLoading = false
                        uploadProgress = 0f
                    }
                    is ResultWrapper.Loading -> {
                        // No debería llegar aquí con la implementación actual
                    }
                }
            } catch (e: Exception) {
                status = context.getString(R.string.scan_video_error, e.message)
                isLoading = false
                uploadProgress = 0f
            }
        }
    }

    fun deleteVideo(doc: VideoDocument) {
        viewModelScope.launch { deleteVideoDocumentUseCase(doc.id) }
    }

    fun analyzeUrl(context: Context, url: String) {
        viewModelScope.launch {
            val supportedUrl = VideoLinkValidator.extractSupportedUrl(url)
            if (supportedUrl == null) {
                status = context.getString(R.string.video_link_unsupported)
                return@launch
            }
            try {
                isLoading = true
                resultText = null
                uploadProgress = 0.1f
                status = context.getString(R.string.video_link_resolving)

                val languageCode = java.util.Locale.getDefault().language
                val systemPrompt = createSystemPrompt(languageCode)
                val result = videoRepository.analyzeVideoFromUrl(
                    url = supportedUrl,
                    context = context,
                    systemPrompt = systemPrompt,
                    onStatus = { newStatus ->
                        viewModelScope.launch {
                            status = newStatus
                            uploadProgress = when (newStatus) {
                                context.getString(R.string.video_link_resolving) -> 0.15f
                                context.getString(R.string.video_link_downloading) -> 0.3f
                                context.getString(R.string.video_link_youtube_direct) -> 0.45f
                                context.getString(R.string.scan_video_uploading) -> 0.55f
                                context.getString(R.string.scan_video_processing) -> 0.75f
                                else -> uploadProgress
                            }
                        }
                    }
                )

                when (result) {
                    is ResultWrapper.Success -> {
                        val data = result.data
                        resultText = data.description
                        status = context.getString(R.string.scan_pdf_ready)
                        uploadProgress = 1f
                        saveVideoDocumentUseCase(
                            name = data.displayName,
                            uri = data.sourceUri,
                            content = data.description,
                            timestamp = System.currentTimeMillis(),
                            durationSeconds = data.durationSeconds,
                            sizeBytes = data.sizeBytes,
                            geminiFileUri = data.geminiFileUri,
                            geminiFileName = data.geminiFileName,
                            geminiMimeType = data.geminiMimeType,
                            geminiFileExpiresAt = data.geminiFileExpiresAt
                        )
                    }
                    is ResultWrapper.Error -> {
                        status = result.message ?: context.getString(R.string.scan_pdf_unknown_error)
                        uploadProgress = 0f
                    }
                    ResultWrapper.Loading -> Unit
                }
            } catch (e: Exception) {
                status = context.getString(R.string.scan_video_error, e.message)
                uploadProgress = 0f
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Obtiene información del video: nombre, tamaño y duración.
     */
    private fun getVideoInfo(context: Context, uri: Uri): Triple<String, Long, Long> {
        var fileName = "video.mp4"
        var sizeBytes = 0L
        var durationSeconds = 0L

        // Obtener nombre y tamaño
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) fileName = cursor.getString(nameIndex)

                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) sizeBytes = cursor.getLong(sizeIndex)
            }
        }

        // Obtener duración del video
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationSeconds = (durationMs?.toLongOrNull() ?: 0L) / 1000L
            retriever.release()
        } catch (e: Exception) {
            // Si falla, dejar duración en 0
        }

        return Triple(fileName, sizeBytes, durationSeconds)
    }

    private fun createSystemPrompt(languageCode: String): String = VideoPromptBuilder.systemPrompt(languageCode)
}

@Composable
fun ScanVideoScreen(
    incomingVideoUri: Uri? = null,
    incomingVideoUrl: String? = null,
    onNavigateBack: () -> Unit,
    onOpenVideo: (VideoDocument) -> Unit,
    onVideoConsumed: () -> Unit = {},
    viewModel: ScanVideoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val videoDocs by viewModel.videoDocuments.collectAsState()
    var hasConsumedIncoming by remember { mutableStateOf(false) }
    var hasConsumedIncomingUrl by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var videoToDelete by remember { mutableStateOf<VideoDocument?>(null) }

    // Launcher para seleccionar video
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.analyze(context, it)
        }
    }

    // Si viene un video compartido desde otra app
    LaunchedEffect(incomingVideoUri) {
        if (incomingVideoUri != null && !hasConsumedIncoming) {
            hasConsumedIncoming = true
            try {
                context.contentResolver.takePersistableUriPermission(
                    incomingVideoUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            viewModel.analyze(context, incomingVideoUri)
            onVideoConsumed()
        }
    }

    LaunchedEffect(incomingVideoUrl) {
        if (!incomingVideoUrl.isNullOrBlank() && !hasConsumedIncomingUrl) {
            hasConsumedIncomingUrl = true
            viewModel.analyzeUrl(context, incomingVideoUrl)
            onVideoConsumed()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.scan_video)) }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val selectVideoText = stringResource(R.string.select_video)
                Button(
                    onClick = { launcher.launch(arrayOf("video/*")) },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = selectVideoText
                    }
                ) {
                    Text(selectVideoText)
                }
            }

            item {
                val describeFromLinkText = stringResource(R.string.video_link_describe_button)
                Button(
                    onClick = { showUrlDialog = true },
                    enabled = !viewModel.isLoading,
                    modifier = Modifier.semantics {
                        role = Role.Button
                        contentDescription = describeFromLinkText
                    }
                ) {
                    Text(describeFromLinkText)
                }
            }

            if (viewModel.isLoading) {
                item {
                    val progressPercentage = (viewModel.uploadProgress * 100).toInt()
                    val progressDesc = stringResource(R.string.upload_progress, progressPercentage)
                    LinearProgressIndicator(
                        progress = { viewModel.uploadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = progressDesc
                            }
                    )
                }
            }

            viewModel.status?.let { message ->
                item {
                    Text(
                        text = message,
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                    )
                }
            }

            viewModel.resultText?.let { text ->
                item {
                    Text(text = text)
                }
            }

            if (viewModel.resultText != null) {
                item {
                    Button(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(viewModel.resultText ?: ""))
                    }) {
                        Text(stringResource(R.string.copy_content))
                    }
                }

                item {
                    Button(onClick = onNavigateBack) {
                        Text(stringResource(R.string.back))
                    }
                }
            }

            if (videoDocs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.video_described_list_title),
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                    )
                }
            }

            items(videoDocs) { video ->
                VideoDocumentCard(
                    video = video,
                    onOpen = { onOpenVideo(video) },
                    onDelete = { videoToDelete = video }
                )
            }
        }
    }

    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text(stringResource(R.string.video_delete_confirm_title)) },
            text = { Text(stringResource(R.string.video_delete_confirm_text)) },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteVideo(video)
                    videoToDelete = null
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showUrlDialog) {
        VideoUrlDialog(
            onDismiss = { showUrlDialog = false },
            onSubmit = { url ->
                showUrlDialog = false
                viewModel.analyzeUrl(context, url)
            }
        )
    }
}

@Composable
private fun VideoUrlDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    val isValid = VideoLinkValidator.extractSupportedUrl(value) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_link_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(stringResource(R.string.video_link_hint)) },
                    supportingText = {
                        if (value.isNotBlank() && !isValid) {
                            Text(stringResource(R.string.video_link_unsupported))
                        }
                    },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(value) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.video_link_describe_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun VideoDocumentCard(
    video: VideoDocument,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    val deleteVideoTitle = stringResource(R.string.video_delete_confirm_title)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text = video.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(
                text = SimpleDateFormat(
                    "dd/MM/yyyy HH:mm",
                    Locale.getDefault()
                ).format(Date(video.timestamp)),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            val sizeMB = video.sizeBytes / (1024 * 1024)
            Text(
                text = stringResource(R.string.video_size, sizeMB),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = deleteVideoTitle)
            }
        }
    }
}
