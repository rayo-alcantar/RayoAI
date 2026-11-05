package com.rayoai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rayoai.domain.model.proactive.ProactivePresetModel
import com.rayoai.domain.repository.ProactivePresetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProactivePresetRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val gson: Gson = Gson()
) : ProactivePresetRepository {

    private object PreferencesKeys {
        val PROACTIVE_PRESETS_JSON = stringPreferencesKey("proactive_presets_json")
        val PROACTIVE_ACTIVE_PRESET_ID = stringPreferencesKey("proactive_active_preset_id")
    }

    private data class PresetDto(
        val id: String = "",
        val name: String = "",
        val defaultNameRes: Int? = null,
        val isDefault: Boolean = false,
        val standardTargetKeys: List<String> = emptyList(),
        val customTargets: List<String> = emptyList()
    )

    override val presets: Flow<List<ProactivePresetModel>> = dataStore.data.map { preferences ->
        val rawJson = preferences[PreferencesKeys.PROACTIVE_PRESETS_JSON]
        if (rawJson.isNullOrBlank()) {
            ProactivePresetModel.getDefaultPresets()
        } else {
            try {
                val listType = object : TypeToken<List<PresetDto>>() {}.type
                val dtos: List<PresetDto> = gson.fromJson(rawJson, listType) ?: emptyList()
                if (dtos.isEmpty()) {
                    ProactivePresetModel.getDefaultPresets()
                } else {
                    dtos.map { dto ->
                        ProactivePresetModel(
                            id = dto.id,
                            name = dto.name,
                            defaultNameRes = dto.defaultNameRes,
                            isDefault = dto.isDefault,
                            standardTargetKeys = dto.standardTargetKeys.toSet(),
                            customTargets = dto.customTargets
                        )
                    }
                }
            } catch (_: Exception) {
                ProactivePresetModel.getDefaultPresets()
            }
        }
    }

    override val activePresetId: Flow<String> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PROACTIVE_ACTIVE_PRESET_ID]
            ?: ProactivePresetModel.PRESET_STREET_ID
    }

    override suspend fun setActivePresetId(presetId: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PROACTIVE_ACTIVE_PRESET_ID] = presetId
        }
    }

    override suspend fun savePreset(preset: ProactivePresetModel) {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.PROACTIVE_PRESETS_JSON]
            val currentList: MutableList<ProactivePresetModel> = if (rawJson.isNullOrBlank()) {
                ProactivePresetModel.getDefaultPresets().toMutableList()
            } else {
                try {
                    val listType = object : TypeToken<List<PresetDto>>() {}.type
                    val dtos: List<PresetDto> = gson.fromJson(rawJson, listType) ?: emptyList()
                    dtos.map { dto ->
                        ProactivePresetModel(
                            id = dto.id,
                            name = dto.name,
                            defaultNameRes = dto.defaultNameRes,
                            isDefault = dto.isDefault,
                            standardTargetKeys = dto.standardTargetKeys.toSet(),
                            customTargets = dto.customTargets
                        )
                    }.toMutableList()
                } catch (_: Exception) {
                    ProactivePresetModel.getDefaultPresets().toMutableList()
                }
            }

            val index = currentList.indexOfFirst { it.id == preset.id }
            if (index >= 0) {
                currentList[index] = preset
            } else {
                currentList.add(preset)
            }

            val dtos = currentList.map { model ->
                PresetDto(
                    id = model.id,
                    name = model.name,
                    defaultNameRes = model.defaultNameRes,
                    isDefault = model.isDefault,
                    standardTargetKeys = model.standardTargetKeys.toList(),
                    customTargets = model.customTargets
                )
            }

            preferences[PreferencesKeys.PROACTIVE_PRESETS_JSON] = gson.toJson(dtos)
            preferences[PreferencesKeys.PROACTIVE_ACTIVE_PRESET_ID] = preset.id
        }
    }

    override suspend fun deletePreset(presetId: String) {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.PROACTIVE_PRESETS_JSON] ?: return@edit
            try {
                val listType = object : TypeToken<List<PresetDto>>() {}.type
                val dtos: List<PresetDto> = gson.fromJson(rawJson, listType) ?: return@edit
                val filtered = dtos.filter { it.id != presetId }

                preferences[PreferencesKeys.PROACTIVE_PRESETS_JSON] = gson.toJson(filtered)

                val currentActive = preferences[PreferencesKeys.PROACTIVE_ACTIVE_PRESET_ID]
                if (currentActive == presetId) {
                    preferences[PreferencesKeys.PROACTIVE_ACTIVE_PRESET_ID] =
                        ProactivePresetModel.PRESET_STREET_ID
                }
            } catch (_: Exception) {
                // Preservar estado ante error de serialización
            }
        }
    }
}
