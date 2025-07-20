package com.rayoai.data.local.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rayoai.domain.model.ChatMessage

class Converters {
    @TypeConverter
    fun fromChatMessageList(value: List<ChatMessage>): String {
        val gson = Gson()
        return gson.toJson(value)
    }

    @TypeConverter
    fun toChatMessageList(value: String): List<ChatMessage> {
        val gson = Gson()
        val type = object : TypeToken<List<ChatMessage>>() {}.type
        return gson.fromJson(value, type)
    }
}
