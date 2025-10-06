package com.rayoai.presentation.ui.screens.history
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.rayoai.R
import com.rayoai.domain.model.Capture
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToChat: (Long) -> Unit
) {
    val history by viewModel.history.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedItems by viewModel.selectedItemIds.collectAsState()
    var showDeleteConfirmationDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    val showHiddenChats by viewModel.showHiddenChats.collectAsState()

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
                    title = { Text(stringResource(id = R.string.history_title)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .semantics { selectableGroup() },
            contentPadding = PaddingValues(16.dp),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    capture: Capture,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
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
            } else {
            }
        }

    Card(
        modifier = cardModifier,
        elevation = CardDefaults.cardElevation(4.dp),
        colors = cardColors
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = capture.lastMessage,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(capture.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isSelectionMode) {
                    Row {
                        IconButton(onClick = onToggleHidden) {
                            Icon(
                                imageVector = if (capture.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = stringResource(if (capture.isHidden) R.string.show_chat_button_text else R.string.hide_chat_button_text)
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.delete_capture)
                            )
                        }
                    }
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null, // Decorative
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
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
        title = { Text(stringResource(id = R.string.history_title)) },
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


