package com.rayoai.data.local.pdfdb

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PdfDocumentEntity::class, PdfChatMessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PdfDatabase : RoomDatabase() {
    abstract fun pdfDao(): PdfDao
    abstract fun pdfChatDao(): PdfChatDao
}

val PDF_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pdf_chat_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pdfDocumentId` INTEGER NOT NULL,
                `content` TEXT NOT NULL,
                `isFromUser` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                FOREIGN KEY(`pdfDocumentId`) REFERENCES `pdf_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pdf_chat_messages_pdfDocumentId` ON `pdf_chat_messages` (`pdfDocumentId`)")
    }
}
