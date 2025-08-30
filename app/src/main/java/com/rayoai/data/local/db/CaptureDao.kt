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
    suspend fun insertCapture(capture: CaptureEntity): Long

    @Query("SELECT * FROM captures WHERE isHidden = 0 OR :showHidden = 1 ORDER BY timestamp DESC")
    fun getAllCaptures(showHidden: Boolean): Flow<List<CaptureEntity>>

    @Query("UPDATE captures SET isHidden = :isHidden WHERE id = :captureId")
    suspend fun updateHiddenState(captureId: Long, isHidden: Boolean)

    @Query("DELETE FROM captures WHERE id = :captureId")
    suspend fun deleteCapture(captureId: Long)

    @Query("DELETE FROM captures")
    suspend fun deleteAllCaptures()

    @Query("SELECT * FROM captures WHERE id = :captureId")
    suspend fun getCaptureById(captureId: Long): CaptureEntity?

    @Query("SELECT * FROM captures")
    suspend fun getAllCapturesList(): List<CaptureEntity>
}