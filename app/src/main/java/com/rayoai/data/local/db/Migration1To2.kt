package com.rayoai.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 1. Rename the old imageUri column
        database.execSQL("ALTER TABLE captures RENAME COLUMN imageUri TO oldImageUri")

        // 2. Add the new imageUris column
        database.execSQL("ALTER TABLE captures ADD COLUMN imageUris TEXT NOT NULL DEFAULT '[]'")

        // 3. Migrate data from oldImageUri to imageUris
        // This requires reading data, converting it, and updating the table.
        // Room doesn't provide direct access to entities during migration,
        // so we have to do it with raw SQL and JSON serialization.

        // Get all existing data
        val cursor = database.query("SELECT id, oldImageUri FROM captures")

        val idColumnIndex = cursor.getColumnIndex("id")
        val oldImageUriColumnIndex = cursor.getColumnIndex("oldImageUri")

        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumnIndex)
            val oldImageUri = cursor.getString(oldImageUriColumnIndex)

            // Convert single URI string to a JSON array of strings
            val newImageUrisJson = if (oldImageUri != null) {
                gson.toJson(listOf(oldImageUri))
            } else {
                "[]"
            }

            // Update the row with the new imageUris
            database.execSQL("UPDATE captures SET imageUris = ? WHERE id = ?", arrayOf(newImageUrisJson, id))
        }
        cursor.close()

        // Note: SQLite doesn't support dropping columns directly.
        // For simplicity, we are not dropping the oldImageUri column in this migration.
        // If a completely clean schema is required, a more complex migration involving
        // creating a new table, copying data, dropping the old table, and renaming the new one
        // would be necessary.
    }
}