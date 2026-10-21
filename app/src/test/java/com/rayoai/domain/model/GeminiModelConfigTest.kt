package com.rayoai.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelConfigTest {

    @Test
    fun buildModelPriority_placesPreferredFirstAndFlashBeforePro() {
        val priority = GeminiModelConfig.buildModelPriority("")
        assertEquals("gemini-3.1-flash-lite-preview", priority.first())
        val flashIndex = priority.indexOf("gemini-3-flash-preview")
        val proIndex = priority.indexOf("gemini-3.1-pro-preview")
        assertTrue(flashIndex in 1 until proIndex)
        assertFalse(priority.contains("gemini-2.5"))
    }

    @Test
    fun thinkingConfigFor_adaptsByModelFamily() {
        val gemini3Config = GeminiModelConfig.thinkingConfigFor("gemini-3.1-flash-lite-preview")
        assertEquals(false, gemini3Config?.includeThoughts)
        assertEquals("MINIMAL", gemini3Config?.thinkingLevel)
        assertNull(gemini3Config?.thinkingBudget)

        val gemini25FlashConfig = GeminiModelConfig.thinkingConfigFor("gemini-2.5-flash")
        assertEquals(false, gemini25FlashConfig?.includeThoughts)
        assertNull(gemini25FlashConfig?.thinkingLevel)
        assertEquals(0, gemini25FlashConfig?.thinkingBudget)

        val gemini25ProConfig = GeminiModelConfig.thinkingConfigFor("gemini-2.5-pro")
        assertNull(gemini25ProConfig)
    }

    @Test
    fun isFatalAuthOrKeyError_detectsInvalidApiKeyAndForbidden() {
        assertTrue(GeminiModelConfig.isFatalAuthOrKeyError(400, """{"error":{"reason":"API_KEY_INVALID"}}"""))
        assertTrue(GeminiModelConfig.isFatalAuthOrKeyError(403, "Forbidden"))
        assertFalse(GeminiModelConfig.isFatalAuthOrKeyError(429, "Quota exceeded"))
        assertFalse(GeminiModelConfig.isFatalAuthOrKeyError(503, "Service unavailable"))
    }
}
