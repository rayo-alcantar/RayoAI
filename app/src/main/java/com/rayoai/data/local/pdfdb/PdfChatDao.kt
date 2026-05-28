package com.rayoai.data.local.pdfdb

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfChatDao {
    @Query("SELECT * FROM pdf_chat_messages WHERE pdfDocumentId = :pdfDocumentId ORDER BY timestamp ASC, id ASC")
    fun getMessages(pdfDocumentId: Long): Flow<List<PdfChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PdfChatMessageEntity): Long

    @Query("DELETE FROM pdf_chat_messages WHERE pdfDocumentId = :pdfDocumentId")
    suspend fun deleteForDocument(pdfDocumentId: Long)
}
