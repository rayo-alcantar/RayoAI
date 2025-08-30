package com.rayoai.domain.model

data class Capture(
    val id: Long,
    val lastMessage: String,
    val timestamp: Long,
    val isHidden: Boolean,
    val imageUris: List<String> // Keep uris for deletion
)
