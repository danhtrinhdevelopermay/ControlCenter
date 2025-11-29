package com.example.controlcenter.shortcuts.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ShortcutEntity::class], version = 1, exportSchema = false)
abstract class ShortcutDatabase : RoomDatabase() {
    
    abstract fun shortcutDao(): ShortcutDao
    
    companion object {
        @Volatile
        private var INSTANCE: ShortcutDatabase? = null
        
        fun getDatabase(context: Context): ShortcutDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShortcutDatabase::class.java,
                    "shortcuts_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
