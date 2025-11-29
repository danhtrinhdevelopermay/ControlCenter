package com.example.controlcenter.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.example.controlcenter.shortcuts.database.ShortcutDatabase
import com.example.controlcenter.shortcuts.database.ShortcutEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ShortcutRepository(private val context: Context) {
    
    private val database = ShortcutDatabase.getDatabase(context)
    private val dao = database.shortcutDao()
    private val packageManager = context.packageManager
    
    fun getActiveShortcuts(): Flow<List<ShortcutItem>> {
        return dao.getActiveShortcuts().map { entities ->
            entities.mapNotNull { entity -> entityToShortcutItem(entity) }
        }
    }
    
    suspend fun getActiveShortcutsList(): List<ShortcutItem> {
        return withContext(Dispatchers.IO) {
            dao.getActiveShortcutsList().mapNotNull { entityToShortcutItem(it) }
        }
    }
    
    fun getSystemShortcuts(): List<ShortcutItem> {
        return SystemShortcuts.getSystemShortcuts()
    }
    
    suspend fun getInstalledApps(): List<ShortcutItem> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val apps = packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        
        apps.mapNotNull { resolveInfo ->
            try {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                val packageName = resolveInfo.activityInfo.packageName
                val activityName = resolveInfo.activityInfo.name
                
                if (packageName != context.packageName) {
                    ShortcutItem(
                        id = "app_$packageName",
                        type = ShortcutType.APP,
                        label = label,
                        iconDrawable = icon,
                        packageName = packageName,
                        activityName = activityName
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }.sortedBy { it.label.lowercase() }
    }
    
    suspend fun addShortcut(shortcut: ShortcutItem) {
        withContext(Dispatchers.IO) {
            val maxOrder = dao.getMaxOrder() ?: -1
            val entity = ShortcutEntity(
                id = shortcut.id,
                type = shortcut.type.name,
                label = shortcut.label,
                packageName = shortcut.packageName,
                activityName = shortcut.activityName,
                action = shortcut.action,
                order = maxOrder + 1,
                isActive = true
            )
            dao.insertShortcut(entity)
        }
    }
    
    suspend fun removeShortcut(shortcutId: String) {
        withContext(Dispatchers.IO) {
            dao.deleteShortcutById(shortcutId)
        }
    }
    
    suspend fun isShortcutActive(shortcutId: String): Boolean {
        return withContext(Dispatchers.IO) {
            dao.getShortcutById(shortcutId)?.isActive ?: false
        }
    }
    
    suspend fun updateOrder(shortcuts: List<ShortcutItem>) {
        withContext(Dispatchers.IO) {
            shortcuts.forEachIndexed { index, shortcut ->
                dao.updateOrder(shortcut.id, index)
            }
        }
    }
    
    suspend fun getActiveShortcutIds(): Set<String> {
        return withContext(Dispatchers.IO) {
            dao.getActiveShortcutsList().map { it.id }.toSet()
        }
    }
    
    private fun entityToShortcutItem(entity: ShortcutEntity): ShortcutItem? {
        val type = try {
            ShortcutType.valueOf(entity.type)
        } catch (e: Exception) {
            return null
        }
        
        return when (type) {
            ShortcutType.SYSTEM -> {
                val systemShortcut = SystemShortcuts.getSystemShortcuts()
                    .find { it.id == entity.id }
                systemShortcut?.copy(
                    isActive = entity.isActive,
                    order = entity.order
                )
            }
            ShortcutType.APP -> {
                try {
                    val appInfo = packageManager.getApplicationInfo(
                        entity.packageName ?: return null,
                        PackageManager.GET_META_DATA
                    )
                    val icon = packageManager.getApplicationIcon(appInfo)
                    ShortcutItem(
                        id = entity.id,
                        type = ShortcutType.APP,
                        label = entity.label,
                        iconDrawable = icon,
                        packageName = entity.packageName,
                        activityName = entity.activityName,
                        isActive = entity.isActive,
                        order = entity.order
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
