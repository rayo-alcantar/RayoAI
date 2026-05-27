package com.rayoai.domain.usecase

import com.google.gson.JsonParser
import com.rayoai.domain.model.AccessiblePdfDocument
import com.rayoai.domain.model.AccessiblePdfElement

object AccessiblePdfHtmlRenderer {
    fun parseJson(raw: String): AccessiblePdfDocument {
        val withoutFence = raw
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val cleaned = withoutFence.substring(
            withoutFence.indexOf('{').coerceAtLeast(0),
            withoutFence.lastIndexOf('}').takeIf { it >= 0 }?.plus(1) ?: withoutFence.length
        )

        val root = JsonParser.parseString(cleaned).asJsonObject
        val elements = root.getAsJsonArray("elementos")?.mapNotNull { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val rows = obj.getAsJsonArray("rows")?.map { row ->
                row.asJsonArray.map { cell -> cell.asStringOrEmpty() }
            }
            AccessiblePdfElement(
                type = obj.get("type").asStringOrDefault("p"),
                content = obj.get("content").asStringOrNull(),
                src = obj.get("src").asStringOrNull(),
                alt = obj.get("alt").asStringOrNull(),
                rows = rows,
                summary = obj.get("summary").asStringOrNull(),
                owner = obj.get("owner").asStringOrNull()
            )
        }.orEmpty()

        return AccessiblePdfDocument(
            idioma = root.get("idioma").asStringOrDefault("es"),
            titulo = root.get("titulo").asStringOrDefault("Documento PDF"),
            elementos = elements
        )
    }

    fun merge(parts: List<AccessiblePdfDocument>): AccessiblePdfDocument {
        val first = parts.firstOrNull()
        return AccessiblePdfDocument(
            idioma = first?.idioma?.ifBlank { "es" } ?: "es",
            titulo = first?.titulo?.ifBlank { "Documento PDF" } ?: "Documento PDF",
            elementos = parts.flatMap { it.elementos }
        )
    }

    fun renderHtml(document: AccessiblePdfDocument): String {
        val lang = escapeAttr(document.idioma.ifBlank { "es" })
        val title = escape(document.titulo.ifBlank { "Documento PDF" })
        val body = buildString {
            append("<h1>").append(title).append("</h1>\n")
            document.elementos.forEach { append(renderElement(it)).append('\n') }
        }

        return """
            <!doctype html>
            <html lang="$lang">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>$title</title>
              <style>
                body { font-family: sans-serif; line-height: 1.55; padding: 20px; color: #111; background: #fff; }
                h1, h2, h3 { line-height: 1.25; }
                table { border-collapse: collapse; width: 100%; margin: 16px 0; }
                caption { text-align: left; font-weight: 600; margin-bottom: 8px; }
                th, td { border: 1px solid #555; padding: 8px; vertical-align: top; }
                figure { margin: 16px 0; }
                figcaption { font-style: italic; }
                .signature { margin-top: 20px; font-weight: 600; }
              </style>
            </head>
            <body>
            $body
            </body>
            </html>
        """.trimIndent()
    }

    fun renderPlainTextFromHtml(html: String): String {
        return html
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("</(h1|h2|h3|p|caption|tr|table|figure)>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }

    private fun renderElement(element: AccessiblePdfElement): String {
        return when (element.type.lowercase()) {
            "h2" -> "<h2>${escape(element.content.orEmpty())}</h2>"
            "h3" -> "<h3>${escape(element.content.orEmpty())}</h3>"
            "img" -> {
                val alt = escapeAttr(element.alt.orEmpty())
                "<figure><div role=\"img\" aria-label=\"$alt\"></div><figcaption>$alt</figcaption></figure>"
            }
            "table" -> renderTable(element)
            "signature" -> "<p class=\"signature\">Firma: ${escape(element.owner.orEmpty())}</p>"
            else -> "<p>${escape(element.content.orEmpty())}</p>"
        }
    }

    private fun renderTable(element: AccessiblePdfElement): String {
        val rows = element.rows.orEmpty()
        if (rows.isEmpty()) return ""

        val caption = element.summary?.takeIf { it.isNotBlank() }
            ?.let { "<caption>${escape(it)}</caption>" }
            .orEmpty()

        val header = rows.first()
            .joinToString("") { "<th scope=\"col\">${escape(it)}</th>" }
            .let { "<thead><tr>$it</tr></thead>" }

        val body = rows.drop(1).joinToString("") { row ->
            row.joinToString("") { "<td>${escape(it)}</td>" }.let { "<tr>$it</tr>" }
        }.let { "<tbody>$it</tbody>" }

        return "<table>$caption$header$body</table>"
    }

    private fun escape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun escapeAttr(value: String): String {
        return escape(value)
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun com.google.gson.JsonElement?.asStringOrNull(): String? {
        return if (this != null && !isJsonNull) asString else null
    }

    private fun com.google.gson.JsonElement?.asStringOrDefault(default: String): String {
        return asStringOrNull()?.ifBlank { default } ?: default
    }

    private fun com.google.gson.JsonElement.asStringOrEmpty(): String {
        return if (!isJsonNull) asString else ""
    }
}
