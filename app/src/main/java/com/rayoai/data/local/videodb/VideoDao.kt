package com.rayoai.data.local.videodb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operaciones de base de datos relacionadas con videos.
 */
@Dao
interface VideoDao {

    /**
     * Inserta un nuevo video en la base de datos.
     */
    @Insert
    suspend fun insert(video: VideoDocumentEntity): Long

    /**
     * Obtiene todos los videos ordenados por timestamp descendente.
     */
    @Query("SELECT * FROM video_documents ORDER BY timestamp DESC")
    fun getAllVideos(): Flow<List<VideoDocumentEntity>>

    /**
     * Obtiene un video específico por ID.
     */
    @Query("SELECT * FROM video_documents WHERE id = :id")
    fun getVideoById(id: Long): Flow<VideoDocumentEntity?>

    /**
     * Elimina un video por ID.
     */
    @Query("DELETE FROM video_documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Elimina todos los videos.
     */
    @Query("DELETE FROM video_documents")
    suspend fun deleteAll()
}
