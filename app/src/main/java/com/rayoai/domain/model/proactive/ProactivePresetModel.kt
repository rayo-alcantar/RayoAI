package com.rayoai.domain.model.proactive

import android.content.Context
import androidx.annotation.StringRes

data class ProactivePresetModel(
    val id: String,
    val name: String,
    @StringRes val defaultNameRes: Int? = null,
    val isDefault: Boolean = false,
    val standardTargetKeys: Set<String> = emptySet(),
    val customTargets: List<String> = emptyList()
) {
    fun getDisplayName(context: Context): String {
        return if (isDefault && defaultNameRes != null) {
            context.getString(defaultNameRes)
        } else {
            name
        }
    }

    fun getActiveTargetNames(context: Context): List<String> {
        val standardNames = standardTargetKeys.mapNotNull { key ->
            StandardProactiveTarget.fromKey(key)?.let { target ->
                context.getString(target.titleRes)
            }
        }
        val customNames = customTargets.filter { it.isNotBlank() }
        return standardNames + customNames
    }

    fun buildConditionPrompt(languageCode: String): String {
        val isSpanish = languageCode.startsWith("es", ignoreCase = true)
        val targetsList = mutableListOf<String>()

        standardTargetKeys.mapNotNull { StandardProactiveTarget.fromKey(it) }.forEach { target ->
            targetsList.add(if (isSpanish) target.promptEs else target.promptEn)
        }

        customTargets.filter { it.isNotBlank() }.forEach { custom ->
            targetsList.add(custom.trim())
        }

        if (targetsList.isEmpty()) {
            return if (isSpanish) {
                "Obstáculos o cambios relevantes en el entorno visual."
            } else {
                "Obstacles or relevant changes in the visual environment."
            }
        }

        val formattedTargets = targetsList.joinToString(separator = "\n- ", prefix = "- ")

        return if (isSpanish) {
            """
            Objetivos prioritarios de vigilancia:
            $formattedTargets

            Instrucciones para el anuncio:
            Si detectas en el fotograma cualquiera de estos objetivos, establece "detected": true y elabora una frase concisa de 3 a 8 palabras optimizada para TextToSpeech indicando específicamente qué objetivo detectaste y su posición relativa (ejemplo: 'Auto aproximándose por la izquierda', 'Semáforo al frente', 'Persona adelante'). Si no hay ninguno de estos objetivos en la imagen, responde con "detected": false y "announcement": null.
            """.trimIndent()
        } else {
            """
            Priority surveillance targets:
            $formattedTargets

            Alert instructions:
            If you detect any of these targets in the frame, set "detected": true and create a concise phrase of 3 to 8 words optimized for TextToSpeech stating which target was found and its relative location (e.g., 'Car approaching on left', 'Traffic light ahead', 'Person in front'). If none of these targets are present in the image, set "detected": false and "announcement": null.
            """.trimIndent()
        }
    }

    companion object {
        const val PRESET_STREET_ID = "preset_street"
        const val PRESET_INDOOR_ID = "preset_indoor"
        const val PRESET_TRANSIT_ID = "preset_transit"

        fun createDefaultStreetPreset(): ProactivePresetModel {
            return ProactivePresetModel(
                id = PRESET_STREET_ID,
                name = "Calle y Tránsito",
                defaultNameRes = com.rayoai.R.string.proactive_preset_street_title,
                isDefault = true,
                standardTargetKeys = setOf(
                    StandardProactiveTarget.VEHICLES.key,
                    StandardProactiveTarget.TRAFFIC_LIGHTS.key,
                    StandardProactiveTarget.PEOPLE.key,
                    StandardProactiveTarget.CROSSINGS.key,
                    StandardProactiveTarget.OBSTACLES.key
                ),
                customTargets = emptyList()
            )
        }

        fun createDefaultIndoorPreset(): ProactivePresetModel {
            return ProactivePresetModel(
                id = PRESET_INDOOR_ID,
                name = "Hogar e Interiores",
                defaultNameRes = com.rayoai.R.string.proactive_preset_indoor_title,
                isDefault = true,
                standardTargetKeys = setOf(
                    StandardProactiveTarget.DOORS.key,
                    StandardProactiveTarget.SEATS.key,
                    StandardProactiveTarget.OBJECTS.key,
                    StandardProactiveTarget.OBSTACLES.key
                ),
                customTargets = emptyList()
            )
        }

        fun createDefaultTransitPreset(): ProactivePresetModel {
            return ProactivePresetModel(
                id = PRESET_TRANSIT_ID,
                name = "Transporte",
                defaultNameRes = com.rayoai.R.string.proactive_preset_transit_title,
                isDefault = true,
                standardTargetKeys = setOf(
                    StandardProactiveTarget.SEATS.key,
                    StandardProactiveTarget.DOORS.key,
                    StandardProactiveTarget.PEOPLE.key,
                    StandardProactiveTarget.VEHICLES.key
                ),
                customTargets = emptyList()
            )
        }

        fun getDefaultPresets(): List<ProactivePresetModel> {
            return listOf(
                createDefaultStreetPreset(),
                createDefaultIndoorPreset(),
                createDefaultTransitPreset()
            )
        }
    }
}
