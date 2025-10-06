package com.rayoai.domain.model

data class PdfDocument(
    val id: Long,
    val name: String,
    val uri: String,
    val content: String,
    val timestamp: Long
)

