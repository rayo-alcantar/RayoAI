package com.rayoai.data.local.videodb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoChatDao {
    @Query("SELECT * FROM video_chat_messages WHERE videoDocumentId = :videoDocumentId ORDER BY timestamp ASC, id ASC")
    fun getMessages(videoDocumentId: Long): Flow<List<VideoChatMessageEntity>>

    @Insert
    suspend fun insert(message: VideoChatMessageEntity): Long
}
