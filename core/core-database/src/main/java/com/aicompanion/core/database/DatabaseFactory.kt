package com.aicompanion.core.database

import android.content.Context
import android.util.Log
import androidx.room.Room
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.io.File

object DatabaseFactory {

    private const val TAG = "DatabaseFactory"

    fun create(context: Context, passphrase: ByteArray): AICompanionDatabase {
        SQLiteDatabase.loadLibs(context)
        val factory = SupportFactory(passphrase)
        val dbName = com.aicompanion.core.common.Constants.DATABASE_NAME

        return try {
            Room.databaseBuilder(context.applicationContext, AICompanionDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open database, deleting and recreating", e)
            // Delete corrupted / incompatible database
            context.applicationContext.getDatabasePath(dbName).let { dbFile ->
                dbFile.delete()
                File(dbFile.parent, "$dbName-shm").delete()
                File(dbFile.parent, "$dbName-wal").delete()
                File(dbFile.parent, "$dbName-journal").delete()
            }
            // Retry
            Room.databaseBuilder(context.applicationContext, AICompanionDatabase::class.java, dbName)
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    fun createInMemory(context: Context): AICompanionDatabase {
        return Room.inMemoryDatabaseBuilder(context.applicationContext, AICompanionDatabase::class.java).build()
    }
}
