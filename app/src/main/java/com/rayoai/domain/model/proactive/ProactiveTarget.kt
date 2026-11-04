package com.rayoai.domain.model.proactive

import androidx.annotation.StringRes
import com.rayoai.R

enum class ProactivePreset(
    @StringRes val titleRes: Int,
    @StringRes val promptConditionRes: Int
) {
    LIGHT(
        R.string.proactive_preset_light_title,
        R.string.proactive_preset_light_condition
    ),
    DOOR(
        R.string.proactive_preset_door_title,
        R.string.proactive_preset_door_condition
    ),
    PERSON(
        R.string.proactive_preset_person_title,
        R.string.proactive_preset_person_condition
    ),
    SEAT(
        R.string.proactive_preset_seat_title,
        R.string.proactive_preset_seat_condition
    ),
    OBSTACLE(
        R.string.proactive_preset_obstacle_title,
        R.string.proactive_preset_obstacle_condition
    ),
    CUSTOM(
        R.string.proactive_preset_custom_title,
        R.string.proactive_preset_custom_condition
    )
}

data class ProactiveDetection(
    val detected: Boolean,
    val stateChanged: Boolean,
    val announcement: String?,
    val details: String?,
    val confidence: String,
    val timestamp: Long = System.currentTimeMillis()
)
