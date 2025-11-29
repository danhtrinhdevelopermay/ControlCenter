package com.example.controlcenter.shortcuts

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.controlcenter.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditShortcutsActivity : AppCompatActivity() {

    private lateinit var repository: ShortcutRepository
    private lateinit var systemAdapter: ShortcutAdapter
    private lateinit var appAdapter: ShortcutAdapter
    
    private var systemShortcuts = mutableListOf<ShortcutItem>()
    private var appShortcuts = mutableListOf<ShortcutItem>()
    private var activeIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_shortcuts)

        repository = ShortcutRepository(this)

        setupRecyclerViews()
        loadShortcuts()

        findViewById<ImageView>(R.id.btnDone).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerViews() {
        val systemGrid = findViewById<RecyclerView>(R.id.systemShortcutsGrid)
        val appGrid = findViewById<RecyclerView>(R.id.appShortcutsGrid)

        systemAdapter = ShortcutAdapter { shortcut ->
            toggleShortcut(shortcut)
        }

        appAdapter = ShortcutAdapter { shortcut ->
            toggleShortcut(shortcut)
        }

        systemGrid.apply {
            layoutManager = GridLayoutManager(this@EditShortcutsActivity, 4)
            adapter = systemAdapter
        }

        appGrid.apply {
            layoutManager = GridLayoutManager(this@EditShortcutsActivity, 4)
            adapter = appAdapter
        }
    }

    private fun loadShortcuts() {
        lifecycleScope.launch {
            activeIds = repository.getActiveShortcutIds().toMutableSet()

            systemShortcuts = withContext(Dispatchers.Default) {
                repository.getSystemShortcuts().map { shortcut ->
                    shortcut.copy(isActive = activeIds.contains(shortcut.id))
                }.toMutableList()
            }

            appShortcuts = withContext(Dispatchers.IO) {
                repository.getInstalledApps().map { shortcut ->
                    shortcut.copy(isActive = activeIds.contains(shortcut.id))
                }.toMutableList()
            }

            systemAdapter.submitList(systemShortcuts.toList())
            appAdapter.submitList(appShortcuts.toList())
        }
    }

    private fun toggleShortcut(shortcut: ShortcutItem) {
        lifecycleScope.launch {
            if (shortcut.isActive) {
                repository.removeShortcut(shortcut.id)
                activeIds.remove(shortcut.id)
            } else {
                repository.addShortcut(shortcut)
                activeIds.add(shortcut.id)
            }

            if (shortcut.type == ShortcutType.SYSTEM) {
                val index = systemShortcuts.indexOfFirst { it.id == shortcut.id }
                if (index >= 0) {
                    systemShortcuts[index] = shortcut.copy(isActive = !shortcut.isActive)
                    systemAdapter.submitList(systemShortcuts.toList())
                }
            } else {
                val index = appShortcuts.indexOfFirst { it.id == shortcut.id }
                if (index >= 0) {
                    appShortcuts[index] = shortcut.copy(isActive = !shortcut.isActive)
                    appAdapter.submitList(appShortcuts.toList())
                }
            }
        }
    }
}
