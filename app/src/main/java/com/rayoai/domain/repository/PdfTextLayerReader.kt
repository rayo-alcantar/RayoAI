package com.rayoai.domain.repository

import java.io.InputStream

interface PdfTextLayerReader {
    // Null means the layer could not be read. Empty strings represent pages without text.
    suspend fun read(openStream: () -> InputStream?): List<String>?
}
