package com.rayoai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.rayoai.data.local.model.CaptureEntity

import androidx.room.TypeConverters

@Database(entities = [CaptureEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class RayoAIDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
}