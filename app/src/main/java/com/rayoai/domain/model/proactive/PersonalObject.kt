package com.rayoai.domain.model.proactive

data class PersonalObject(
    val id: String,
    val name: String,
    val visualDescription: String,
    val imageUris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
