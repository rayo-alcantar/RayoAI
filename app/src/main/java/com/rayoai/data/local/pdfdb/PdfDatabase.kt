package com.rayoai.data.local.pdfdb

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PdfDocumentEntity::class], version = 1)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao
}

