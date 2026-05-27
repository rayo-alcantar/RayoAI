package com.rayoai.domain.model

data class AccessiblePdfDocument(
    val idioma: String = "es",
    val titulo: String = "Documento PDF",
    val elementos: List<AccessiblePdfElement> = emptyList()
)

data class AccessiblePdfElement(
    val type: String = "p",
    val content: String? = null,
    val src: String? = null,
    val alt: String? = null,
    val rows: List<List<String>>? = null,
    val summary: String? = null,
    val owner: String? = null
)
