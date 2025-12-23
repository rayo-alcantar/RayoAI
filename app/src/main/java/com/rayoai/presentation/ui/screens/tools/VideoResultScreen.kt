@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rayoai.presentation.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.rayoai.R
import com.rayoai.domain.usecase.video.GetVideoDocumentByIdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class VideoResultViewModel @Inject constructor(
    private val getVideoDocumentByIdUseCase: GetVideoDocumentByIdUseCase
) : ViewModel() {

    fun getVideoDocument(id: Long): StateFlow<com.rayoai.domain.model.VideoDocument?> {
        return getVideoDocumentByIdUseCase(id)
            .stateIn(
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
                started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }
}

@Composable
fun VideoResultScreen(
    videoId: Long,
    onNavigateBack: () -> Unit,
    viewModel: VideoResultViewModel = hiltViewModel()
) {
    val doc by viewModel.getVideoDocument(videoId).collectAsState()
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.video_analysis_result)) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            doc?.let { video ->
                Text(
                    text = video.name,
                    style = MaterialTheme.typography.titleLarge
                )

                // Mostrar duración y tamaño
                val durationMinutes = video.durationSeconds / 60
                val durationSecondsRemainder = video.durationSeconds % 60
                val sizeMB = video.sizeBytes / (1024 * 1024)

                Text(
                    text = stringResource(
                        R.string.video_duration,
                        "$durationMinutes:${durationSecondsRemainder.toString().padStart(2, '0')}"
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = stringResource(R.string.video_size, sizeMB),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = video.content,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )

                Button(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(video.content))
                }) {
                    Text(stringResource(R.string.copy_content))
                }

                Button(onClick = onNavigateBack) {
                    Text(stringResource(R.string.back))
                }
            } ?: run {
                Text(stringResource(R.string.video_not_found))
            }
        }
    }
}
