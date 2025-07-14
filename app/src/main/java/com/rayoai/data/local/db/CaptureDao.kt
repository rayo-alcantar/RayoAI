package com.rayoai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rayoai.data.local.model.CaptureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCapture(capture: CaptureEntity)

    @Query("SELECT * FROM captures ORDER BY timestamp DESC")
    fun getAllCaptures(): Flow<List<CaptureEntity>>

    @Query("DELETE FROM captures WHERE id = :captureId")
    suspend fun deleteCapture(captureId: Long)

    @Query("DELETE FROM captures")
    suspend fun deleteAllCaptures()

    @Query("SELECT * FROM captures WHERE id = :captureId")
    suspend fun getCaptureById(captureId: Long): CaptureEntity?

    @Query("SELECT * FROM captures")
    suspend fun getAllCapturesList(): List<CaptureEntity>
}