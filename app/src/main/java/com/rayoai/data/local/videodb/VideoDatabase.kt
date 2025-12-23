package com.rayoai.data.local.videodb

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base de datos Room para videos analizados.
 */
@Database(
    entities = [VideoDocumentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VideoDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
}
