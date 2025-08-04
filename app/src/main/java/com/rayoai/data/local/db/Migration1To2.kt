package com.rayoai.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Create the new table with the definitive schema
        database.execSQL("""
            CREATE TABLE captures_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                imageUris TEXT NOT NULL DEFAULT '[]',
                chatHistory TEXT NOT NULL,
                timestamp INTEGER NOT NULL
            )
        """)

        // 2. Copy data from the old table to the new table
        database.execSQL("""
            INSERT INTO captures_new (id, imageUris, chatHistory, timestamp)
            SELECT id,
                   '["' || imageUri || '"]',
                   chatHistory,
                   timestamp
            FROM captures
        """)

        // 3. Drop the old table
        database.execSQL("DROP TABLE captures")

        // 4. Rename the new table to the original name
        database.execSQL("ALTER TABLE captures_new RENAME TO captures")
    }
}
