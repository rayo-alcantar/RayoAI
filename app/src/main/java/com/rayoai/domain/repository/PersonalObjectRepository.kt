package com.rayoai.domain.repository

import com.rayoai.domain.model.proactive.PersonalObject
import kotlinx.coroutines.flow.Flow

interface PersonalObjectRepository {
    val personalObjects: Flow<List<PersonalObject>>

    suspend fun savePersonalObject(personalObject: PersonalObject)
    suspend fun deletePersonalObject(id: String)
    suspend fun getPersonalObject(id: String): PersonalObject?
}
