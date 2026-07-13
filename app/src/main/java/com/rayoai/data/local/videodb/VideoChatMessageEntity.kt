package com.rayoai.data.local.videodb

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "video_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = VideoDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["videoDocumentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("videoDocumentId")]
)
data class VideoChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoDocumentId: Long,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long
)
