package com.rayoai.presentation.ui.screens.tools

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.rayoai.domain.usecase.AccessiblePdfHtmlRenderer
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object AccessiblePdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48

    fun savePdf(resolver: ContentResolver, uri: Uri, html: String, openFailedMessage: String) {
        resolver.openOutputStream(uri)?.use { output ->
            writePdf(output, html)
        } ?: throw IllegalStateException(openFailedMessage)
    }

    fun createShareIntent(context: Context, html: String, fileName: String, chooserTitle: String): Intent {
        val dir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
        val file = File(dir, sanitizeFileName(fileName).ifBlank { "rayoai_pdf_accesible.pdf" })
        FileOutputStream(file).use { output ->
            writePdf(output, html)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, chooserTitle)
    }

    private fun writePdf(output: OutputStream, html: String) {
        val text = AccessiblePdfHtmlRenderer.renderPlainTextFromHtml(html)
        val document = PdfDocument()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val lineHeight = (paint.textSize * 1.55f).toInt()
        val lines = buildWrappedLines(text, paint, PAGE_WIDTH - (MARGIN * 2))

        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var canvas: Canvas = page.canvas
        var y = MARGIN

        lines.forEach { line ->
            if (y + lineHeight > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber += 1
                page = document.startPage(
                    PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                )
                canvas = page.canvas
                y = MARGIN
            }
            canvas.drawText(line, MARGIN.toFloat(), y.toFloat(), paint)
            y += if (line.isBlank()) lineHeight else lineHeight
        }

        document.finishPage(page)
        document.writeTo(output)
        document.close()
    }

    private fun sanitizeFileName(value: String): String {
        val cleaned = value
            .substringAfterLast('/')
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
        return if (cleaned.endsWith(".pdf", ignoreCase = true)) cleaned else "${cleaned}_accesible.pdf"
    }

    private fun buildWrappedLines(text: String, paint: TextPaint, width: Int): List<String> {
        val result = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) {
                result.add("")
            } else {
                val layout = StaticLayout.Builder
                    .obtain(paragraph, 0, paragraph.length, paint, width)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build()

                repeat(layout.lineCount) { index ->
                    val start = layout.getLineStart(index)
                    val end = layout.getLineEnd(index)
                    result.add(paragraph.substring(start, end).trimEnd())
                }
            }
        }
        return result
    }
}
