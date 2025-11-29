package com.example.controlcenter.shortcuts.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ShortcutDao {
    
    @Query("SELECT * FROM shortcuts WHERE isActive = 1 ORDER BY `order` ASC")
    fun getActiveShortcuts(): Flow<List<ShortcutEntity>>
    
    @Query("SELECT * FROM shortcuts WHERE isActive = 1 ORDER BY `order` ASC")
    suspend fun getActiveShortcutsList(): List<ShortcutEntity>
    
    @Query("SELECT * FROM shortcuts ORDER BY `order` ASC")
    fun getAllShortcuts(): Flow<List<ShortcutEntity>>
    
    @Query("SELECT * FROM shortcuts WHERE id = :id")
    suspend fun getShortcutById(id: String): ShortcutEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: ShortcutEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShortcuts(shortcuts: List<ShortcutEntity>)
    
    @Update
    suspend fun updateShortcut(shortcut: ShortcutEntity)
    
    @Delete
    suspend fun deleteShortcut(shortcut: ShortcutEntity)
    
    @Query("DELETE FROM shortcuts WHERE id = :id")
    suspend fun deleteShortcutById(id: String)
    
    @Query("UPDATE shortcuts SET isActive = :isActive WHERE id = :id")
    suspend fun setShortcutActive(id: String, isActive: Boolean)
    
    @Query("UPDATE shortcuts SET `order` = :order WHERE id = :id")
    suspend fun updateOrder(id: String, order: Int)
    
    @Query("SELECT MAX(`order`) FROM shortcuts")
    suspend fun getMaxOrder(): Int?
}
