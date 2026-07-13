package com.rayoai.data.local.videodb

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base de datos Room para videos analizados.
 */
@Database(
    entities = [VideoDocumentEntity::class, VideoChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class VideoDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun videoChatDao(): VideoChatDao
}
