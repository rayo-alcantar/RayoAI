package com.rayoai.domain.model

data class VideoAnalysisResult(
    val displayName: String,
    val sourceUri: String,
    val description: String,
    val durationSeconds: Int,
    val sizeBytes: Long
)
