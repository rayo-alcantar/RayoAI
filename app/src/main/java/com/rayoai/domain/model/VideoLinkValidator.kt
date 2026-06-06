package com.rayoai.domain.model

object VideoLinkValidator {
    private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
    private val supportedHostRegex = Regex(
        """^(www\.|m\.)?(youtube\.com|youtu\.be|tiktok\.com|vm\.tiktok\.com|vt\.tiktok\.com|instagram\.com|x\.com|twitter\.com)$""",
        RegexOption.IGNORE_CASE
    )

    fun extractSupportedUrl(text: String): String? {
        return urlRegex.findAll(text)
            .map { it.value.trim().trimEnd('.', ',', ';', ')', ']') }
            .firstOrNull(::isSupportedUrl)
    }

    fun isSupportedUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url.trim()).host.orEmpty() }.getOrDefault("")
        return supportedHostRegex.matches(host.removePrefix("mobile."))
    }

    fun isYouTube(url: String): Boolean {
        val host = runCatching { java.net.URI(url.trim()).host.orEmpty() }.getOrDefault("")
        return host.contains("youtube.com", ignoreCase = true) ||
            host.equals("youtu.be", ignoreCase = true)
    }
}
