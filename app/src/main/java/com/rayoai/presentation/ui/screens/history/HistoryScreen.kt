package com.rayoai.presentation.ui.screens.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.rayoai.R
import com.rayoai.domain.model.Capture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit
) {
    val history by viewModel.history.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterFavoritesOnly by viewModel.filterFavoritesOnly.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedItemIds.collectAsState()
    val showHiddenChats by viewModel.showHiddenChats.collectAsState()

    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmationDialog = false },
            title = { Text(stringResource(R.string.delete_selection_dialog_title)) },
            text = { Text(stringResource(R.string.delete_selection_dialog_text, selectedItems.size)) },
            confirmButton = {
                Button({
                    viewModel.onEvent(HistoryEvent.OnDeleteSelected)
                    showDeleteConfirmationDialog = false
                }) {
                    Text(stringResource(R.string.dialog_action_delete))
                }
            },
            dismissButton = {
                Button({
                    showDeleteConfirmationDialog = false
                }) {
                    Text(stringResource(R.string.dialog_action_cancel))
                }
            }
        )
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.history_confirm_delete_title)) },
            text = { Text(stringResource(R.string.history_confirm_delete_message)) },
            confirmButton = {
                Button(
                    {
                        viewModel.deleteAllHistory()
                        showDeleteAllDialog = false
                    }
                ) {
                    Text(stringResource(R.string.dialog_action_delete))
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.dialog_action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                SelectionTopAppBar(
                    selectedItemCount = selectedItems.size,
                    onClearSelection = { viewModel.onEvent(HistoryEvent.OnClearSelection) },
                    onDeleteSelected = { showDeleteConfirmationDialog = true }
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(id = R.string.history_title), fontWeight = FontWeight.Bold) },
                    actions = {
                        TextButton(onClick = { viewModel.toggleShowHiddenChats() }) {
                            Text(
                                text = stringResource(
                                    if (showHiddenChats) R.string.show_hidden_chats_button_text_hide
                                    else R.string.show_hidden_chats_button_text_show
                                )
                            )
                        }
                        IconButton(onClick = { showDeleteAllDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.history_delete_all))
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Buscador accesible y filtros si no está en modo selección
            if (!isSelectionMode) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.history_search_hint)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearSearchQuery() }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = stringResource(R.string.history_search_clear)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Filtros rápidos: Todos y Favoritos
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !filterFavoritesOnly,
                            onClick = {
                                if (filterFavoritesOnly) viewModel.toggleFilterFavoritesOnly()
                            },
                            label = { Text(stringResource(R.string.history_filter_all)) }
                        )

                        FilterChip(
                            selected = filterFavoritesOnly,
                            onClick = {
                                if (!filterFavoritesOnly) viewModel.toggleFilterFavoritesOnly()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (filterFavoritesOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (filterFavoritesOnly) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = { Text(stringResource(R.string.history_filter_favorites)) }
                        )
                    }
                }
            }

            // Lista de capturas
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val emptyText = when {
                        searchQuery.isNotBlank() -> stringResource(R.string.history_no_results)
                        filterFavoritesOnly -> stringResource(R.string.history_no_favorites)
                        else -> stringResource(R.string.history_empty)
                    }
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .selectableGroup(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history, key = { it.id }) { capture ->
                        val isSelected = selectedItems.contains(capture.id)
                        HistoryItem(
                            capture = capture,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onItemClick = {
                                viewModel.onEvent(HistoryEvent.OnItemClick(capture.id, onNavigateToChat))
                            },
                            onItemLongClick = {
                                viewModel.onEvent(HistoryEvent.OnItemLongClick(capture.id))
                            },
                            onToggleFavorite = {
                                viewModel.toggleChatFavoriteState(capture)
                            },
                            onToggleHidden = {
                                viewModel.toggleChatHiddenState(capture)
                            },
                            onDelete = {
                                viewModel.deleteCapture(capture)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    capture: Capture,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardColors = if (isSelected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }

    val selectionStateDescription = stringResource(
        if (isSelected) R.string.history_accessibility_state_selected else R.string.history_accessibility_state_not_selected
    )
    val selectionActionLabel = stringResource(
        if (isSelected) R.string.history_accessibility_action_deselect else R.string.history_accessibility_action_select
    )

    val cardModifier = modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = onItemClick,
            onLongClick = onItemLongClick,
            role = if (isSelectionMode) Role.Checkbox else Role.Button
        )
        .semantics {
            if (isSelectionMode) {
                stateDescription = selectionStateDescription
                toggleableState = if (isSelected) ToggleableState.On else ToggleableState.Off
                onClick(selectionActionLabel) {
                    onItemClick()
                    true
                }
            }
        }

    val firstImageUri = capture.imageUris.firstOrNull()

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = cardColors
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniatura de la fotografía capturada
            if (!firstImageUri.isNullOrBlank()) {
                AsyncImage(
                    model = firstImageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Información: primera descripción (no la última) y fecha/insignias
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = capture.firstDescription.ifBlank {
                        stringResource(R.string.captured_image_description)
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(capture.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (capture.isFavorite) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFF8F00),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = stringResource(R.string.history_favorite_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFC67D00),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (capture.isHidden) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = stringResource(R.string.history_hidden_badge),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Acciones directas (favorito, visibilidad, eliminar)
            if (!isSelectionMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val favDesc = stringResource(
                        if (capture.isFavorite) R.string.history_favorite_action_remove
                        else R.string.history_favorite_action_add
                    )
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (capture.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = favDesc,
                            tint = if (capture.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onToggleHidden) {
                        Icon(
                            imageVector = if (capture.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = stringResource(
                                if (capture.isHidden) R.string.show_chat_button_text
                                else R.string.hide_chat_button_text
                            ),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_capture),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTopAppBar(onNavigateBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(id = R.string.history_title), fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(id = R.string.back)
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    selectedItemCount: Int,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selection_mode_title, selectedItemCount)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection_description))
            }
        },
        actions = {
            IconButton(onClick = onDeleteSelected) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selection_description))
            }
        }
    )
}
