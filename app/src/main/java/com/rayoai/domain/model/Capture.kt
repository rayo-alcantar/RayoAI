package com.rayoai.domain.model

data class Capture(
    val id: Long,
    val firstDescription: String,
    val timestamp: Long,
    val isHidden: Boolean,
    val isFavorite: Boolean = false,
    val imageUris: List<String>,
    val lastMessage: String = firstDescription
)
