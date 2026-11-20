package com.rayoai.data.local.pdfdb

import android.content.Context
import com.rayoai.domain.repository.PdfTextLayerReader
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.StringWriter
import javax.inject.Inject

class PdfBoxTextLayerReader @Inject constructor(
    @ApplicationContext private val context: Context
) : PdfTextLayerReader {
    override suspend fun read(openStream: () -> InputStream?): List<String>? = withContext(Dispatchers.IO) {
        try {
            val memory = MemoryUsageSetting.setupMixed(8L * 1024 * 1024)
                .setTempDir(context.cacheDir)
            PDFBoxResourceLoader.init(context)
            openStream()?.use { input ->
                PDDocument.load(input, memory).use { pdf ->
                    if (!pdf.currentAccessPermission.canExtractContent()) return@withContext null
                    val pages = MutableList(pdf.numberOfPages) { "" }
                    val buffer = StringWriter()
                    // One traversal. PDFBox skips pages with no streams; keep their slots.
                    val stripper = object : PDFTextStripper() {
                        override fun startPage(page: PDPage) {
                            ensureActive()
                        }

                        override fun endPage(page: PDPage) {
                            pages[currentPageNo - 1] = buffer.toString()
                            buffer.buffer.setLength(0)
                        }
                    }.apply {
                        sortByPosition = true
                        paragraphEnd = "\n"
                    }
                    stripper.writeText(pdf, buffer)
                    pages
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
}
