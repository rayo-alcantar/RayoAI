package com.rayoai.data.local.pdfdb

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PdfDocumentEntity::class], version = 1, exportSchema = false)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao
}
