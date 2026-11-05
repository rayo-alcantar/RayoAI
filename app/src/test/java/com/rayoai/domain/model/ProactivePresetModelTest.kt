package com.rayoai.domain.model

import com.rayoai.domain.model.proactive.ProactivePresetModel
import com.rayoai.domain.model.proactive.StandardProactiveTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProactivePresetModelTest {

    @Test
    fun defaultStreetPreset_containsExpectedTargets() {
        val street = ProactivePresetModel.createDefaultStreetPreset()
        assertEquals(ProactivePresetModel.PRESET_STREET_ID, street.id)
        assertTrue(street.isDefault)
        assertTrue(street.standardTargetKeys.contains(StandardProactiveTarget.VEHICLES.key))
        assertTrue(street.standardTargetKeys.contains(StandardProactiveTarget.TRAFFIC_LIGHTS.key))
        assertTrue(street.standardTargetKeys.contains(StandardProactiveTarget.PEOPLE.key))
        assertTrue(street.standardTargetKeys.contains(StandardProactiveTarget.CROSSINGS.key))
        assertTrue(street.standardTargetKeys.contains(StandardProactiveTarget.OBSTACLES.key))
    }

    @Test
    fun buildConditionPrompt_inSpanish_includesStandardAndCustomTargets() {
        val model = ProactivePresetModel(
            id = "custom_1",
            name = "Calle",
            isDefault = false,
            standardTargetKeys = setOf(
                StandardProactiveTarget.VEHICLES.key,
                StandardProactiveTarget.TRAFFIC_LIGHTS.key
            ),
            customTargets = listOf("baches", "mi perro guía")
        )

        val prompt = model.buildConditionPrompt("es")
        assertTrue(prompt.contains("Autos, colectivos, motocicletas"))
        assertTrue(prompt.contains("Semáforos peatonales o vehiculares"))
        assertTrue(prompt.contains("baches"))
        assertTrue(prompt.contains("mi perro guía"))
        assertTrue(prompt.contains("Objetivos prioritarios de vigilancia:"))
        assertTrue(prompt.contains("detected"))
    }

    @Test
    fun buildConditionPrompt_inEnglish_includesEnglishPrompts() {
        val model = ProactivePresetModel(
            id = "custom_2",
            name = "Office",
            isDefault = false,
            standardTargetKeys = setOf(StandardProactiveTarget.DOORS.key),
            customTargets = listOf("water bottle")
        )

        val prompt = model.buildConditionPrompt("en")
        assertTrue(prompt.contains("Access doors, entrances or exits"))
        assertTrue(prompt.contains("water bottle"))
        assertTrue(prompt.contains("Priority surveillance targets:"))
    }

    @Test
    fun buildConditionPrompt_emptyTargets_returnsSafeFallback() {
        val emptyModel = ProactivePresetModel(
            id = "empty",
            name = "Vacio",
            standardTargetKeys = emptySet(),
            customTargets = emptyList()
        )

        val promptEs = emptyModel.buildConditionPrompt("es")
        assertTrue(promptEs.isNotBlank())
        assertTrue(promptEs.contains("Obstáculos"))

        val promptEn = emptyModel.buildConditionPrompt("en")
        assertTrue(promptEn.isNotBlank())
        assertTrue(promptEn.contains("Obstacles"))
    }

    @Test
    fun standardProactiveTarget_fromKey_matchesAllEntries() {
        for (target in StandardProactiveTarget.entries) {
            val resolved = StandardProactiveTarget.fromKey(target.key)
            assertNotNull(resolved)
            assertEquals(target, resolved)
        }
    }
}
