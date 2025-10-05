package com.rayoai.presentation.ui.screens.history

sealed interface HistoryEvent {
    data class OnItemLongClick(val id: Long) : HistoryEvent
    data class OnItemClick(val id: Long, val navigateToChat: (Long) -> Unit) : HistoryEvent
    object OnClearSelection : HistoryEvent
    object OnDeleteSelected : HistoryEvent
}
