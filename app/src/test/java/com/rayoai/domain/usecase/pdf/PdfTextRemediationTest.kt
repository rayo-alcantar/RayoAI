package com.rayoai.domain.usecase.pdf

import com.rayoai.domain.usecase.AccessiblePdfHtmlRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextRemediationTest {
    @Test
    fun distinguishesSelectableTextFromEmptyOrUnmappedPages() {
        assertTrue(PdfTextRemediation.hasText("Secci6n"))
        assertTrue(PdfTextRemediation.hasText("年度報告"))
        assertTrue(PdfTextRemediation.hasText("2026"))
        assertFalse(PdfTextRemediation.hasText(" \n\t\uFFFD\uFFFD ±"))
    }

    @Test
    fun appliesOnlyAnchoredGlyphCorrectionsAndKeepsPageOrder() {
        val source = "1. Secci6n\n\nT. Ahora vainos a la relaci6n.\nC. Sí."
        val result = PdfTextRemediation.apply(source, """
            {"edits":[
                {"line":3,"original":"vainos","replacement":"vamos"},
                {"line":3,"original":"relaci6n.","replacement":"relación."},
                {"line":1,"original":"Secci6n","replacement":"Sección"}
            ],"uncertain":false}
        """.trimIndent())
        assertEquals("1. Sección\n\nT. Ahora vamos a la relación.\nC. Sí.", result.text)
        assertFalse(result.needsReview)
    }

    @Test
    fun canRemoveIsolatedNoiseButNotWordsOrMeaningfulSymbols() {
        val source = "___\nSección\n±\n5 ± 1\nERE"
        val result = PdfTextRemediation.apply(source, """
            {"edits":[
                {"line":1,"original":"___","replacement":""},
                {"line":2,"original":"Sección","replacement":""},
                {"line":3,"original":"±","replacement":""},
                {"line":4,"original":"±","replacement":""},
                {"line":5,"original":"ERE","replacement":""}
            ],"uncertain":false}
        """.trimIndent())
        assertEquals("\nSección\n±\n5 ± 1\nERE", result.text)
        assertTrue(result.needsReview)
    }

    @Test
    fun rejectsInsertionsLargeRewritesPunctuationChangesAndNumberChanges() {
        val source = "Fue una promesa.\n36 días. H6O.\nNo.\nSecci6n"
        val result = PdfTextRemediation.apply(source, """
            {"edits":[
                {"line":1,"original":"Fue","replacement":"Fue una noche horrible"},
                {"line":1,"original":"promesa.","replacement":"promesa!"},
                {"line":2,"original":"36","replacement":"35"},
                {"line":2,"original":"H6O.","replacement":"HóO."},
                {"line":3,"original":"No.","replacement":"Yo."},
                {"line":4,"original":"Secci6n","replacement":"Conclusión"}
            ],"uncertain":false}
        """.trimIndent())
        assertEquals(source, result.text)
        assertTrue(result.needsReview)
    }

    @Test
    fun dropsConflictsAndAmbiguousOrIncorrectAnchors() {
        val source = "relaci6n relaci6n\naccion\nÁrea"
        val result = PdfTextRemediation.apply(source, """
            {"edits":[
                {"line":1,"original":"relaci6n","replacement":"relación"},
                {"line":2,"original":"accion","replacement":"acción"},
                {"line":2,"original":"accion","replacement":"Acción"},
                {"line":3,"original":"área","replacement":"Área"},
                {"line":4,"original":"Área","replacement":"área"}
            ],"uncertain":false}
        """.trimIndent())
        assertEquals(source, result.text)
        assertTrue(result.needsReview)
    }

    @Test
    fun malformedOrUnavailableResponseKeepsSourceAndReportsUnverifiedText() {
        val source = "Secci6n"
        listOf(null, "", "{edits:[],uncertain:false}", """{"edits":[]}""", "[]").forEach { raw ->
            assertEquals(PdfTextRemediation.Result(source, true), PdfTextRemediation.apply(source, raw))
        }
        assertEquals(
            PdfTextRemediation.Result(source, true),
            PdfTextRemediation.apply(source, """{"edits":[],"uncertain":true}""")
        )
        assertEquals(
            PdfTextRemediation.Result(source, false),
            PdfTextRemediation.apply(source, """{"edits":[],"uncertain":false}""")
        )
        assertFalse(PdfTextRemediation.canReview("x".repeat(12_001)))
    }

    @Test
    fun keepsParagraphsAndEscapesExtractedContentForPreviewAndExport() {
        val fragment = PdfTextRemediation.fragment(
            "T. Texto <original>\ncontinúa.\n\nC. 5 ± 1.",
            title = "Documento PDF",
            language = "es"
        )
        val html = AccessiblePdfHtmlRenderer.renderHtml(fragment)
        assertTrue(html.contains("<p>T. Texto &lt;original&gt; continúa.</p>"))
        assertTrue(html.contains("<p>C. 5 ± 1.</p>"))
        val plain = AccessiblePdfHtmlRenderer.renderPlainTextFromHtml(html)
        assertTrue(plain.contains("T. Texto <original> continúa."))
        assertTrue(plain.contains("C. 5 ± 1."))
    }
}
