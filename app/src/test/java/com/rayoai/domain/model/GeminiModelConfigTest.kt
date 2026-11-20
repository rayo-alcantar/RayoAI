package com.rayoai.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiModelConfigTest {

    @Test
    fun buildModelPriority_placesPreferredFirstAndFreeFastBeforePaidReasoning() {
        val priority = GeminiModelConfig.buildModelPriority("")
        assertEquals("gemini-3.5-flash-lite", priority.first())
        val hydrogenIndex = priority.indexOf("gemini-4-hydrogen")
        val flash38Index = priority.indexOf("gemini-3.8-flash")
        val neonIndex = priority.indexOf("gemini-4-neon")
        val carbonIndex = priority.indexOf("gemini-4-carbon")
        assertTrue(hydrogenIndex in 1 until neonIndex)
        assertTrue(flash38Index in 1 until neonIndex)
        assertTrue(neonIndex < carbonIndex)
        assertFalse(priority.contains("gemini-2.5-pro"))
        assertFalse(priority.contains("gemini-2.5-flash"))
        assertFalse(priority.contains("gemini-3-flash-preview"))
        assertFalse(priority.contains("gemini-3.1-pro-preview"))
    }

    @Test
    fun buildModelPriority_respectsSelectedModelFirstAndMigratesLegacy() {
        val selected = GeminiModelConfig.buildModelPriority("gemini-4-carbon")
        assertEquals("gemini-4-carbon", selected.first())
        assertEquals(selected.size, selected.distinct().size)

        val migrated = GeminiModelConfig.buildModelPriority("gemini-3-flash-preview")
        assertEquals("gemini-4-hydrogen", migrated.first())
    }

    @Test
    fun migrateGeneralModel_mapsLegacyAndDeprecatedModelsToStableSuccessors() {
        assertEquals("gemini-3.5-flash-lite", GeminiModelConfig.migrateGeneralModel(null))
        assertEquals("gemini-3.5-flash-lite", GeminiModelConfig.migrateGeneralModel(""))
        assertEquals("gemini-3.5-flash-lite", GeminiModelConfig.migrateGeneralModel("gemini-2.0-flash"))
        assertEquals("gemini-3.5-flash-lite", GeminiModelConfig.migrateGeneralModel("gemini-2.5-flash"))
        assertEquals("gemini-3.5-flash-lite", GeminiModelConfig.migrateGeneralModel("gemini-3.1-flash-lite"))
        assertEquals("gemini-4-hydrogen", GeminiModelConfig.migrateGeneralModel("gemini-3-flash-preview"))
        assertEquals("gemini-4-hydrogen", GeminiModelConfig.migrateGeneralModel("gemini-3.6-flash"))
        assertEquals("gemini-4-hydrogen", GeminiModelConfig.migrateGeneralModel("gemini-2.5-pro"))
        assertEquals("gemini-4-neon", GeminiModelConfig.migrateGeneralModel("gemini-3.1-pro-preview"))
        assertEquals("gemini-4-carbon", GeminiModelConfig.migrateGeneralModel("gemini-4-carbon"))
        assertEquals("gemini-3.8-flash", GeminiModelConfig.migrateGeneralModel("models/gemini-3.8-flash"))
    }

    @Test
    fun thinkingConfigFor_adaptsByModelFamilyWithoutInvalidMinimal() {
        val flashLiteConfig = GeminiModelConfig.thinkingConfigFor("gemini-3.5-flash-lite")
        assertEquals(false, flashLiteConfig?.includeThoughts)
        assertEquals("MINIMAL", flashLiteConfig?.thinkingLevel)
        assertNull(flashLiteConfig?.thinkingBudget)

        for (lowOnlyModel in listOf(
            "gemini-4-hydrogen",
            "gemini-4-neon",
            "gemini-4-carbon",
            "gemini-3.8-flash"
        )) {
            val cfg = GeminiModelConfig.thinkingConfigFor(lowOnlyModel)
            assertEquals("Expected includeThoughts=false for $lowOnlyModel", false, cfg?.includeThoughts)
            assertEquals("Expected LOW thinkingLevel for $lowOnlyModel", "LOW", cfg?.thinkingLevel)
            assertNull(cfg?.thinkingBudget)
        }

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

    @Test
    fun liveModelsThinkingAndMigration_areConfiguredPerGemini38Spec() {
        assertEquals("gemini-3.8-live", GeminiModelConfig.DEFAULT_LIVE_MODEL)
        assertEquals("gemini-3.8-live-extended-thinking", GeminiModelConfig.EXTENDED_THINKING_LIVE_MODEL)
        assertEquals(
            listOf("gemini-3.8-live", "gemini-3.8-live-extended-thinking"),
            GeminiModelConfig.liveSelectableModels
        )
        assertTrue(GeminiModelConfig.liveVoices.contains("Kore"))
        assertTrue(GeminiModelConfig.liveVoices.contains("Puck"))

        // gemini-3.8-live does NOT support thinkingConfig; extended-thinking DOES
        assertFalse(GeminiModelConfig.liveModelSupportsThinking("gemini-3.8-live"))
        assertTrue(GeminiModelConfig.liveModelSupportsThinking("gemini-3.8-live-extended-thinking"))
        assertFalse(GeminiModelConfig.liveModelSupportsThinking("gemini-2.5-flash"))

        // Live thinking levels exclude minimal
        assertEquals(listOf("low", "medium", "high"), GeminiModelConfig.liveThinkingLevels)
        assertEquals("low", GeminiModelConfig.normalizeLiveThinkingLevel("gemini-3.8-live-extended-thinking", "minimal"))
        assertEquals("low", GeminiModelConfig.normalizeLiveThinkingLevel("gemini-3.8-live-extended-thinking", ""))
        assertEquals("medium", GeminiModelConfig.normalizeLiveThinkingLevel("gemini-3.8-live-extended-thinking", "MEDIUM"))
        assertEquals("high", GeminiModelConfig.normalizeLiveThinkingLevel("gemini-3.8-live-extended-thinking", "high"))

        // Legacy live models migrate to gemini-3.8-live
        assertEquals("gemini-3.8-live", GeminiModelConfig.migrateLiveModel("gemini-3.1-flash-live-preview"))
        assertEquals("gemini-3.8-live", GeminiModelConfig.migrateLiveModel(null))
        assertEquals(
            "gemini-3.8-live-extended-thinking",
            GeminiModelConfig.migrateLiveModel("models/gemini-3.8-live-extended-thinking")
        )
    }
}
