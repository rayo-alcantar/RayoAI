package com.rayoai.domain.usecase.pdf

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.Strictness
import com.rayoai.domain.model.AccessiblePdfDocument
import com.rayoai.domain.model.AccessiblePdfElement
import java.text.Normalizer

internal object PdfTextRemediation {
    private val json = GsonBuilder().setStrictness(Strictness.STRICT).create()
    private val tokenPattern = Regex("\\S+")
    private val sourceWord = Regex("""^(\p{P}*)([\p{L}\p{M}6±£§_\uFFFD]+)(\p{P}*)$""")
    private val targetWord = Regex("""^(\p{P}*)([\p{L}\p{M}]+)(\p{P}*)$""")
    private val combiningMarks = Regex("""\p{M}+""")
    private val noiseGlyphs = setOf('±', '£', '§', '_', '\uFFFD', '\\', '\u0060', '|')
    private const val MAX_PAGE_LENGTH = 12_000
    private const val MAX_RESPONSE_LENGTH = 32_000
    private const val MAX_EDITS = 96
    private const val MAX_WORD_LENGTH = 48

    data class Result(val text: String, val needsReview: Boolean)

    fun hasText(text: String): Boolean =
        text.codePoints().anyMatch { Character.isLetterOrDigit(it) }

    fun canReview(text: String): Boolean = text.length <= MAX_PAGE_LENGTH

    fun pageData(text: String): String = json.toJson(text.lines())

    fun apply(source: String, response: String?): Result {
        val unchanged = Result(source, needsReview = true)
        if (response.isNullOrBlank() || response.length > MAX_RESPONSE_LENGTH || !canReview(source)) {
            return unchanged
        }
        val root = try {
            json.fromJson(response, JsonElement::class.java)?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: RuntimeException) {
            null
        } ?: return unchanged
        val uncertain = root["uncertain"]?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }
            ?.asBoolean ?: return unchanged
        val items = root["edits"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return unchanged
        if (items.size() > MAX_EDITS) return unchanged

        val lines = source.lines()
        val edits = mutableMapOf<Pair<Int, IntRange>, MutableList<String>>()
        var needsReview = uncertain
        for (item in items) {
            val edit = item.takeIf { it.isJsonObject }?.asJsonObject
            val lineIndex = edit?.get("line")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                ?.asString?.toIntOrNull()?.minus(1)
            val line = lineIndex?.let { lines.getOrNull(it) }
            val original = edit?.get("original").stringOrNull()
            val replacement = edit?.get("replacement").stringOrNull()
            val match = if (line != null && original != null && replacement != null &&
                (isSmallOcrCorrection(original, replacement) ||
                    (replacement.isEmpty() && line.trim() == original && isIsolatedNoise(original)))
            ) {
                tokenPattern.findAll(line).filter { it.value == original }.take(2).toList().singleOrNull()
            } else null
            if (match == null || lineIndex == null || replacement == null) {
                needsReview = true
                continue
            }
            edits.getOrPut(lineIndex to match.range) { mutableListOf() }.add(replacement)
        }
        if (edits.isEmpty()) return Result(source, needsReview)
        val updated = lines.toMutableList()
        for ((lineIndex, changes) in edits.entries.groupBy { it.key.first }) {
            var line = lines[lineIndex]
            for ((key, suggestions) in changes.sortedByDescending { it.key.second.first }) {
                val replacement = suggestions.distinct().singleOrNull()
                if (replacement == null) {
                    needsReview = true
                } else {
                    line = line.replaceRange(key.second, replacement)
                }
            }
            updated[lineIndex] = line
        }
        return Result(updated.joinToString("\n"), needsReview)
    }

    fun fragment(text: String, title: String, language: String): AccessiblePdfDocument =
        AccessiblePdfDocument(
            idioma = language,
            titulo = title,
            elementos = text.split(Regex("""\r?\n[ \t]*\r?\n""")).mapNotNull { paragraph ->
                val prose = paragraph.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
                    .joinToString(" ")
                prose.takeIf { it.isNotEmpty() }?.let { AccessiblePdfElement(type = "p", content = it) }
            }
        )

    private fun JsonElement?.stringOrNull(): String? =
        this?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun isIsolatedNoise(token: String): Boolean =
        token.length in 2..MAX_WORD_LENGTH &&
            token.count { it in noiseGlyphs } >= 2 &&
            token.all { it in noiseGlyphs || it == '.' || it == ',' }

    private fun isSmallOcrCorrection(original: String, replacement: String): Boolean {
        if (original == replacement || original.length > MAX_WORD_LENGTH || replacement.length > MAX_WORD_LENGTH) {
            return false
        }
        val (before, damaged, after) = sourceWord.matchEntire(original)?.destructured ?: return false
        val (newBefore, fixed, newAfter) = targetWord.matchEntire(replacement)?.destructured ?: return false
        if (before != newBefore || after != newAfter || damaged.count { it.isLetter() } < 2 ||
            fixed.count { it.isLetter() } < 2
        ) return false
        // Keep numeric facts: the only digit recognized as a glyph error is 6 → ó within prose.
        if ('6' in damaged && (!damaged.any { it.isLowerCase() } ||
                fixed.count { it == 'ó' || it == 'Ó' } < damaged.count { it == '6' })
        ) return false
        fun fold(word: String) = Normalizer.normalize(word, Normalizer.Form.NFD)
            .replace(combiningMarks, "").lowercase()

        val a = fold(damaged.replace('6', 'ó'))
        val b = fold(fixed)
        if (minOf(a.length, b.length) < 3) return a == b
        return editDistance(a, b) <= minOf(3, minOf(a.length, b.length) / 2)
    }

    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(
                    current[j] + 1, previous[j + 1] + 1, previous[j] + if (a[i] == b[j]) 0 else 1
                )
            }
            previous = current
        }
        return previous.last()
    }
}
