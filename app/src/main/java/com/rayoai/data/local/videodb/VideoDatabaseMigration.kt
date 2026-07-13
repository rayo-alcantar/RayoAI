package com.rayoai.data.local.videodb

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val VIDEO_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE video_documents ADD COLUMN geminiFileUri TEXT")
        db.execSQL("ALTER TABLE video_documents ADD COLUMN geminiFileName TEXT")
        db.execSQL("ALTER TABLE video_documents ADD COLUMN geminiMimeType TEXT")
        db.execSQL("ALTER TABLE video_documents ADD COLUMN geminiFileExpiresAt INTEGER")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS video_chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                videoDocumentId INTEGER NOT NULL,
                content TEXT NOT NULL,
                isFromUser INTEGER NOT NULL,
                timestamp INTEGER NOT NULL,
                FOREIGN KEY(videoDocumentId) REFERENCES video_documents(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_video_chat_messages_videoDocumentId ON video_chat_messages(videoDocumentId)")
    }
}
