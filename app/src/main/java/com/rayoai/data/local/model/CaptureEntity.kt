package com.rayoai.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey

import com.rayoai.domain.model.ChatMessage

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageUri: String,
    val chatHistory: List<ChatMessage>,
    val timestamp: Long
)