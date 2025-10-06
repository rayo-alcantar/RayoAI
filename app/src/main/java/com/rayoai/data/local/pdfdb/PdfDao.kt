package com.rayoai.data.local.pdfdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdf_documents ORDER BY timestamp DESC")
    fun getAll(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    fun getById(id: Long): Flow<PdfDocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(doc: PdfDocumentEntity): Long

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deleteById(id: Long)
}

