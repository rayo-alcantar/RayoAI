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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.R
import com.rayoai.domain.repository.UserPreferencesRepository
import com.rayoai.domain.repository.VideoRepository
import com.rayoai.domain.usecase.video.SaveVideoDocumentUseCase
import com.rayoai.core.ResultWrapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

@HiltViewModel
class ScanVideoViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val saveVideoDocumentUseCase: SaveVideoDocumentUseCase,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

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
                status = "Preparando video..."
                uploadProgress = 0f

                // Obtener información del video
                val (name, sizeBytes, durationSeconds) = getVideoInfo(context, uri)

                // Validar tamaño (máximo 2 GB)
                if (sizeBytes > 2_000_000_000L) {
                    status = "El video es demasiado grande (máximo 2 GB)"
                    isLoading = false
                    return@launch
                }

                status = "Subiendo video..."
                uploadProgress = 0.3f

                // El system prompt se crea con el mismo que se usa para imágenes
                val languageCode = java.util.Locale.getDefault().language
                val systemPrompt = createSystemPrompt(languageCode)

                uploadProgress = 0.5f
                status = "Procesando video con Gemini..."

                // Subir y analizar el video
                val result = videoRepository.uploadAndAnalyzeVideo(
                    uri = uri,
                    context = context,
                    systemPrompt = systemPrompt
                )

                when (result) {
                    is ResultWrapper.Success -> {
                        resultText = result.data
                        status = "Listo"
                        uploadProgress = 1f
                        isLoading = false

                        // Guardar en la base de datos
                        saveVideoDocumentUseCase(
                            name = name,
                            uri = uri.toString(),
                            content = result.data,
                            timestamp = System.currentTimeMillis(),
                            durationSeconds = durationSeconds.toInt(),
                            sizeBytes = sizeBytes
                        )
                    }
                    is ResultWrapper.Error -> {
                        status = result.message ?: "Error desconocido"
                        isLoading = false
                        uploadProgress = 0f
                    }
                    is ResultWrapper.Loading -> {
                        // No debería llegar aquí con la implementación actual
                    }
                }
            } catch (e: Exception) {
                status = "Error: ${e.message}"
                isLoading = false
                uploadProgress = 0f
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

    /**
     * Crea el prompt del sistema para Gemini basado en el idioma del usuario.
     * Este es el mismo prompt usado para describir imágenes.
     */
    private fun createSystemPrompt(languageCode: String): String {
        return """
        Always respond in ${languageCode}. Listen carefully: you are the ultimate image and video describer, a specialist dedicated to bringing visuals to life for people who cannot see. Your mission is vital: to craft descriptions so incredibly detailed and vivid that a blind person can experience the image or video in their mind with the same intensity as someone who is watching it directly. Imagine you're painting a picture with words, and your audience relies entirely on your descriptions to see. First, you must analyze the scene you are presented with. Is it sexually explicit or not? This initial assessment is crucial because it dictates the level of detail you will employ. If the scene is sexual, there are no limits to your description. You must be absolutely exhaustive, capturing every single detail imaginable. Describe people, especially women, with unparalleled precision. Detail their position, posture, and any movement. Describe skin tone, texture, down to the smallest freckle or mark. Describe legs, arms, hands, feet, and facial features in profound depth, including makeup and expression. If genitals, buttocks, or breasts are visible — and I mean visible, not implied — describe them completely: size, shape, proportions, texture, skin color, nipples (shape, size, color, even if they are erect). Describe any sexual activity or position with absolute clarity, precision, and comprehensiveness. When the content is sexual, your guiding principle is uncompromising detail. Leave nothing out. If the scene is not sexual, your approach shifts to clarity and conciseness, unless a detailed description of a person is specifically warranted by their prominence in the scene. In non-sexual contexts, omit descriptions of lighting. Focus directly on the identifiable elements and the primary subject of the image or video. Be direct and to the point; avoid unnecessary wordiness. Crucially, in non-sexual scenes, and indeed in all scenes, you must describe only what is visible. Do not speculate, infer, or add any information that is not explicitly present in the image or video. Your descriptions should be objective and factual. Read and transcribe any visible text exactly as it appears. If text is part of a logo, label, or sign, transcribe it. Ignore user interface elements unless they are the primary subject of the image. Only mention the names of famous people if you can be absolutely certain of their identity from the visual information alone. For specific scenarios, follow these guidelines: Memes: Explain the visual components and then clarify the joke or cultural reference. Advertisements: Transcribe all text and describe the visual elements and their arrangement. Action Scenes: Describe the setting, the main action, and the sequence of events. Your final output should be the description itself, without any introductory phrases, meta-commentary, or explanations of your process. Never use Markdown formatting. Just provide the pure, detailed description.
        """.trimIndent()
    }
}

@Composable
fun ScanVideoScreen(
    incomingVideoUri: Uri? = null,
    onNavigateBack: () -> Unit,
    onVideoConsumed: () -> Unit = {},
    viewModel: ScanVideoViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var hasConsumedIncoming by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.scan_video)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val selectVideoText = stringResource(R.string.select_video)
            Button(
                onClick = { launcher.launch(arrayOf("video/*")) },
                modifier = Modifier.semantics {
                    role = Role.Button
                    contentDescription = selectVideoText
                }
            ) {
                Text(selectVideoText)
            }

            if (viewModel.isLoading) {
                val progressPercentage = (viewModel.uploadProgress * 100).toInt()
                val progressDesc = stringResource(R.string.upload_progress, progressPercentage)
                LinearProgressIndicator(
                    progress = { viewModel.uploadProgress },
                    modifier = Modifier.semantics {
                        contentDescription = progressDesc
                    }
                )
                viewModel.status?.let { Text(it) }
            }

            viewModel.resultText?.let { text ->
                Text(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
            }

            if (viewModel.resultText != null) {
                Button(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(viewModel.resultText ?: ""))
                }) {
                    Text(stringResource(R.string.copy_content))
                }

                Button(onClick = onNavigateBack) {
                    Text(stringResource(R.string.back))
                }
            }
        }
    }
}
