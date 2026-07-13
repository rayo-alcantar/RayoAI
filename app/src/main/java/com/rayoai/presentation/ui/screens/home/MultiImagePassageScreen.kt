package com.rayoai.presentation.ui.screens.home

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.rememberAsyncImagePainter
import com.rayoai.R

@Composable
fun MultiImagePassageScreen(
    imageUris: List<Uri>,
    truncatedCount: Int,
    onOpenChat: (Long) -> Unit,
    viewModel: MultiImagePassageViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val view = LocalView.current

    LaunchedEffect(imageUris) { viewModel.start(imageUris) }

    val current = state.images.getOrNull(state.currentIndex)
    val previousEnabled = state.images.getOrNull(state.currentIndex - 1)?.status == SharedImageDescriptionStatus.SUCCEEDED
    val nextEnabled = state.images.getOrNull(state.currentIndex + 1)?.status == SharedImageDescriptionStatus.SUCCEEDED
    val canOpenChat = !state.isProcessing && current?.captureId != null
    val progressText = stringResource(
        R.string.multi_image_progress,
        (state.images.count { it.status == SharedImageDescriptionStatus.SUCCEEDED || it.status == SharedImageDescriptionStatus.FAILED }),
        state.images.size
    )
    val previousLabel = stringResource(R.string.multi_image_previous)
    val nextLabel = stringResource(R.string.multi_image_next)
    val importingText = stringResource(R.string.multi_image_importing)
    val describingText = stringResource(R.string.multi_image_describing)

    LaunchedEffect(current?.status, state.currentIndex, state.images.size) {
        when (current?.status) {
            SharedImageDescriptionStatus.IMPORTING -> view.announceForAccessibility(importingText)
            SharedImageDescriptionStatus.DESCRIBING -> view.announceForAccessibility(describingText)
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = stringResource(R.string.multi_image_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = progressText,
            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium
        )
        if (truncatedCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.multi_image_limit_reached, truncatedCount),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(16.dp))
        if (current == null) {
            CircularProgressIndicator()
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Image(
                        painter = rememberAsyncImagePainter(current.uri),
                        contentDescription = stringResource(R.string.multi_image_preview, state.currentIndex + 1),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().sizeIn(maxHeight = 320.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    when (current.status) {
                        SharedImageDescriptionStatus.PENDING,
                        SharedImageDescriptionStatus.IMPORTING,
                        SharedImageDescriptionStatus.DESCRIBING -> Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.sizeIn(maxWidth = 28.dp, maxHeight = 28.dp))
                            Text(
                                if (current.status == SharedImageDescriptionStatus.IMPORTING) importingText else describingText
                            )
                        }
                        SharedImageDescriptionStatus.SUCCEEDED -> Text(
                            text = current.description.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        SharedImageDescriptionStatus.FAILED -> {
                            Text(text = current.error ?: stringResource(R.string.unexpected_error), color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = viewModel::retryCurrentImage, enabled = !state.isProcessing) {
                                Text(stringResource(R.string.retry_action))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { current.captureId?.let(onOpenChat) },
                enabled = canOpenChat,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) { Text(stringResource(R.string.multi_image_open_chat)) }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                OutlinedButton(
                    onClick = viewModel::showPrevious,
                    enabled = previousEnabled,
                    modifier = Modifier.semantics { contentDescription = previousLabel }
                ) { Text(previousLabel) }
                OutlinedButton(
                    onClick = viewModel::showNext,
                    enabled = nextEnabled,
                    modifier = Modifier.semantics { contentDescription = nextLabel }
                ) { Text(nextLabel) }
            }
        }
        state.batchError?.let {
            Spacer(Modifier.height(16.dp))
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}
