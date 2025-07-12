package com.rayoai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rayoai.data.local.model.CaptureEntity

@Database(entities = [CaptureEntity::class], version = 1, exportSchema = false)
abstract class RayoAIDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
}