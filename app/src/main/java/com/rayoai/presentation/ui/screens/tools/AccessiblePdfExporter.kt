package com.rayoai.presentation.ui.screens.tools

import android.content.ContentResolver
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.rayoai.domain.usecase.AccessiblePdfHtmlRenderer

object AccessiblePdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48

    fun savePdf(resolver: ContentResolver, uri: Uri, html: String) {
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
        resolver.openOutputStream(uri)?.use { output ->
            document.writeTo(output)
        }
        document.close()
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
