package com.example.controlcenter.shortcuts.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val label: String,
    val packageName: String? = null,
    val activityName: String? = null,
    val action: String? = null,
    val order: Int = 0,
    val isActive: Boolean = true
)
