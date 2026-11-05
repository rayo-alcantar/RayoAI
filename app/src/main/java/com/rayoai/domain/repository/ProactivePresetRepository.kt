package com.rayoai.domain.repository

import com.rayoai.domain.model.proactive.ProactivePresetModel
import kotlinx.coroutines.flow.Flow

interface ProactivePresetRepository {
    val presets: Flow<List<ProactivePresetModel>>
    val activePresetId: Flow<String>

    suspend fun setActivePresetId(presetId: String)
    suspend fun savePreset(preset: ProactivePresetModel)
    suspend fun deletePreset(presetId: String)
}
