package com.rayoai.presentation.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.rayoai.R
import com.rayoai.data.local.model.CaptureEntity
import com.rayoai.presentation.ui.navigation.Screen
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    navController: NavController
) {
    val captures by viewModel.captures.collectAsState(initial = emptyList())
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.history_confirm_delete_title)) },
            text = { Text(stringResource(R.string.history_confirm_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllCaptures()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.history_title)) },
                actions = {
                    IconButton(onClick = { showDeleteAllDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_delete_all))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (captures.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.history_empty))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 128.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(captures) { capture ->
                    HistoryItem(
                        capture = capture,
                        onDelete = { viewModel.deleteCapture(it) },
                        onClick = { 
                            navController.navigate(Screen.Home.createRoute(it.id))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItem(
    capture: CaptureEntity,
    onDelete: (CaptureEntity) -> Unit,
    onClick: (CaptureEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable { onClick(capture) }
            .semantics(mergeDescendants = true) {
                contentDescription = capture.chatHistory.getOrNull(1)?.content ?: ""
            }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (capture.imageUri.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(capture.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Log.d("HistoryScreen", "Image URI is empty for capture ID: ${capture.id}")
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_image), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(
                onClick = { onDelete(capture) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_capture), tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
