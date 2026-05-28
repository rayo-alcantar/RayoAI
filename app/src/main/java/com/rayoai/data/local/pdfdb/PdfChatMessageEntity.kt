package com.rayoai.data.local.pdfdb

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pdf_chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["pdfDocumentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pdfDocumentId")]
)
data class PdfChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pdfDocumentId: Long,
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long
)
