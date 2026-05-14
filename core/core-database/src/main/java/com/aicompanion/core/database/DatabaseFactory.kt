package com.aicompanion.core.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

object DatabaseFactory {

    private const val TAG = "DatabaseFactory"
    // Increment this when schema changes (adding/removing entity columns, new entities, etc.)
    private const val DB_SCHEMA_VERSION = 11
    private const val PREFS_DB_KEY = "db_schema_version"

    fun create(context: Context, passphrase: ByteArray): AICompanionDatabase {
        SQLiteDatabase.loadLibs(context)

        // Check if schema changed — if so, delete old encrypted DB before Room tries to open it
        checkAndCleanOldDatabase(context)

        val factory = SupportFactory(passphrase)
        val dbName = com.aicompanion.core.common.Constants.DATABASE_NAME

        return try {
            Room.databaseBuilder(context.applicationContext, AICompanionDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        } catch (e: Exception) {
            // If Room still fails, force-delete and retry once
            Log.e(TAG, "Database open failed, force deleting", e)
            deleteDatabaseFiles(context, dbName)
            Room.databaseBuilder(context.applicationContext, AICompanionDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    private fun checkAndCleanOldDatabase(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(
            com.aicompanion.core.common.Constants.PREFS_NAME, Context.MODE_PRIVATE
        )
        val storedVersion = prefs.getInt(PREFS_DB_KEY, 0)
        if (storedVersion != DB_SCHEMA_VERSION) {
            Log.d(TAG, "Schema version changed ($storedVersion → $DB_SCHEMA_VERSION), clearing old database")
            deleteDatabaseFiles(context, com.aicompanion.core.common.Constants.DATABASE_NAME)
            prefs.edit().putInt(PREFS_DB_KEY, DB_SCHEMA_VERSION).apply()
        }
    }

    private fun deleteDatabaseFiles(context: Context, dbName: String) {
        val dbPath = context.applicationContext.getDatabasePath(dbName)
        listOf(
            dbPath,
            File(dbPath.parent, "$dbName-shm"),
            File(dbPath.parent, "$dbName-wal"),
            File(dbPath.parent, "$dbName-journal"),
            File("${dbPath.absolutePath}-journal")
        ).forEach { file ->
            try { if (file.exists()) file.delete() } catch (_: Exception) {}
        }
    }

    fun createInMemory(context: Context): AICompanionDatabase {
        return Room.inMemoryDatabaseBuilder(context.applicationContext, AICompanionDatabase::class.java).build()
    }
}
