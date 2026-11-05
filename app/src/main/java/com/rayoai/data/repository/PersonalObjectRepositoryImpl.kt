package com.rayoai.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rayoai.data.local.ImageStorageManager
import com.rayoai.domain.model.proactive.PersonalObject
import com.rayoai.domain.repository.PersonalObjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PersonalObjectRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val imageStorageManager: ImageStorageManager,
    private val gson: Gson = Gson()
) : PersonalObjectRepository {

    private object PreferencesKeys {
        val PERSONAL_OBJECTS_JSON = stringPreferencesKey("personal_objects_json")
    }

    private data class PersonalObjectDto(
        val id: String = "",
        val name: String = "",
        val visualDescription: String = "",
        val imageUris: List<String> = emptyList(),
        val createdAt: Long = 0L
    )

    override val personalObjects: Flow<List<PersonalObject>> = dataStore.data.map { preferences ->
        val rawJson = preferences[PreferencesKeys.PERSONAL_OBJECTS_JSON]
        if (rawJson.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                val listType = object : TypeToken<List<PersonalObjectDto>>() {}.type
                val dtos: List<PersonalObjectDto> = gson.fromJson(rawJson, listType) ?: emptyList()
                dtos.map { dto ->
                    PersonalObject(
                        id = dto.id,
                        name = dto.name,
                        visualDescription = dto.visualDescription,
                        imageUris = dto.imageUris,
                        createdAt = dto.createdAt
                    )
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun savePersonalObject(personalObject: PersonalObject) {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.PERSONAL_OBJECTS_JSON]
            val currentList: MutableList<PersonalObject> = if (rawJson.isNullOrBlank()) {
                mutableListOf()
            } else {
                try {
                    val listType = object : TypeToken<List<PersonalObjectDto>>() {}.type
                    val dtos: List<PersonalObjectDto> = gson.fromJson(rawJson, listType) ?: emptyList()
                    dtos.map { dto ->
                        PersonalObject(
                            id = dto.id,
                            name = dto.name,
                            visualDescription = dto.visualDescription,
                            imageUris = dto.imageUris,
                            createdAt = dto.createdAt
                        )
                    }.toMutableList()
                } catch (_: Exception) {
                    mutableListOf()
                }
            }

            val index = currentList.indexOfFirst { it.id == personalObject.id }
            if (index >= 0) {
                // Si se reemplazaron imágenes, eliminar las imágenes anteriores que ya no se usan
                val oldImages = currentList[index].imageUris
                val removedImages = oldImages.filter { it !in personalObject.imageUris }
                removedImages.forEach { uriString ->
                    imageStorageManager.deleteImage(uriString)
                }
                currentList[index] = personalObject
            } else {
                currentList.add(personalObject)
            }

            val dtos = currentList.map { model ->
                PersonalObjectDto(
                    id = model.id,
                    name = model.name,
                    visualDescription = model.visualDescription,
                    imageUris = model.imageUris,
                    createdAt = model.createdAt
                )
            }

            preferences[PreferencesKeys.PERSONAL_OBJECTS_JSON] = gson.toJson(dtos)
        }
    }

    override suspend fun deletePersonalObject(id: String) {
        dataStore.edit { preferences ->
            val rawJson = preferences[PreferencesKeys.PERSONAL_OBJECTS_JSON] ?: return@edit
            try {
                val listType = object : TypeToken<List<PersonalObjectDto>>() {}.type
                val dtos: List<PersonalObjectDto> = gson.fromJson(rawJson, listType) ?: return@edit
                val target = dtos.firstOrNull { it.id == id }

                if (target != null) {
                    target.imageUris.forEach { uriString ->
                        imageStorageManager.deleteImage(uriString)
                    }
                }

                val filtered = dtos.filter { it.id != id }
                preferences[PreferencesKeys.PERSONAL_OBJECTS_JSON] = gson.toJson(filtered)
            } catch (_: Exception) {
                // Preservar estado en caso de error
            }
        }
    }

    override suspend fun getPersonalObject(id: String): PersonalObject? {
        val list = personalObjects.first()
        return list.firstOrNull { it.id == id }
    }
}
