package com.example.controlcenter

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class EditQuickSettingsActivity : AppCompatActivity() {

    private lateinit var selectedTilesGrid: RecyclerView
    private lateinit var availableTilesGrid: RecyclerView
    private lateinit var appShortcutsGrid: RecyclerView
    private lateinit var noAppsText: TextView
    private lateinit var addAppButton: TextView
    private lateinit var backButton: ImageView
    private lateinit var confirmButton: ImageView

    private lateinit var selectedAdapter: QuickSettingsAdapter
    private lateinit var availableAdapter: QuickSettingsAdapter
    private lateinit var appShortcutsAdapter: QuickSettingsAdapter

    private val selectedTiles = mutableListOf<QuickSettingTile>()
    private val availableTiles = mutableListOf<QuickSettingTile>()
    private val appShortcuts = mutableListOf<QuickSettingTile>()

    companion object {
        private const val REQUEST_APP_PICKER = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_quick_settings)

        initViews()
        loadTiles()
        setupAdapters()
        setupDragAndDrop()
        setupListeners()
    }

    private fun initViews() {
        selectedTilesGrid = findViewById(R.id.selectedTilesGrid)
        availableTilesGrid = findViewById(R.id.availableTilesGrid)
        appShortcutsGrid = findViewById(R.id.appShortcutsGrid)
        noAppsText = findViewById(R.id.noAppsText)
        addAppButton = findViewById(R.id.addAppButton)
        backButton = findViewById(R.id.backButton)
        confirmButton = findViewById(R.id.confirmButton)
    }

    private fun loadTiles() {
        val selectedIds = QuickSettingsManager.getSelectedTileIds(this).distinct()
        val allSystemTiles = QuickSettingsManager.getAvailableSystemTiles(this)
        val savedAppShortcuts = QuickSettingsManager.getAppShortcuts(this)

        selectedTiles.clear()
        availableTiles.clear()
        appShortcuts.clear()

        val addedIds = mutableSetOf<String>()
        selectedIds.forEach { id ->
            if (addedIds.contains(id)) return@forEach
            
            val systemTile = allSystemTiles.find { it.id == id }
            if (systemTile != null) {
                selectedTiles.add(systemTile)
                addedIds.add(id)
            } else {
                val appShortcut = savedAppShortcuts.find { it.id == id }
                if (appShortcut != null) {
                    selectedTiles.add(appShortcut)
                    addedIds.add(id)
                }
            }
        }

        allSystemTiles.forEach { tile ->
            if (!addedIds.contains(tile.id)) {
                availableTiles.add(tile)
            }
        }

        savedAppShortcuts.forEach { shortcut ->
            if (!addedIds.contains(shortcut.id)) {
                appShortcuts.add(shortcut)
            }
        }

        updateNoAppsVisibility()
    }

    private fun setupAdapters() {
        selectedAdapter = QuickSettingsAdapter(selectedTiles, QuickSettingsAdapter.Mode.SELECTED) { tile, position ->
            removeFromSelected(tile, position)
        }
        selectedTilesGrid.apply {
            layoutManager = GridLayoutManager(this@EditQuickSettingsActivity, 4)
            adapter = selectedAdapter
        }

        availableAdapter = QuickSettingsAdapter(availableTiles, QuickSettingsAdapter.Mode.AVAILABLE) { tile, position ->
            addToSelected(tile, position, isAppShortcut = false)
        }
        availableTilesGrid.apply {
            layoutManager = GridLayoutManager(this@EditQuickSettingsActivity, 4)
            adapter = availableAdapter
        }

        appShortcutsAdapter = QuickSettingsAdapter(appShortcuts, QuickSettingsAdapter.Mode.APP_SHORTCUT) { tile, position ->
            addToSelected(tile, position, isAppShortcut = true)
        }
        appShortcutsGrid.apply {
            layoutManager = GridLayoutManager(this@EditQuickSettingsActivity, 4)
            adapter = appShortcutsAdapter
        }
    }

    private fun setupDragAndDrop() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                selectedAdapter.moveTile(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled() = true
        })

        itemTouchHelper.attachToRecyclerView(selectedTilesGrid)
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        confirmButton.setOnClickListener {
            saveTiles()
            setResult(Activity.RESULT_OK)
            finish()
        }

        addAppButton.setOnClickListener {
            val intent = Intent(this, AppPickerActivity::class.java)
            startActivityForResult(intent, REQUEST_APP_PICKER)
        }
    }

    private fun removeFromSelected(tile: QuickSettingTile, position: Int) {
        if (position < 0 || position >= selectedTiles.size) return
        
        selectedTiles.removeAt(position)
        selectedAdapter.notifyItemRemoved(position)
        selectedAdapter.notifyItemRangeChanged(position, selectedTiles.size)

        if (tile.type == QuickSettingTile.TileType.SYSTEM) {
            if (!availableTiles.any { it.id == tile.id }) {
                availableTiles.add(tile)
                availableAdapter.addTile(tile)
            }
        } else {
            if (!appShortcuts.any { it.id == tile.id }) {
                appShortcuts.add(tile)
                appShortcutsAdapter.addTile(tile)
            }
            updateNoAppsVisibility()
        }
    }

    private fun addToSelected(tile: QuickSettingTile, position: Int, isAppShortcut: Boolean) {
        if (selectedTiles.any { it.id == tile.id }) return
        
        selectedTiles.add(tile)
        selectedAdapter.addTile(tile)

        if (isAppShortcut) {
            if (position >= 0 && position < appShortcuts.size) {
                appShortcuts.removeAt(position)
                appShortcutsAdapter.notifyItemRemoved(position)
                appShortcutsAdapter.notifyItemRangeChanged(position, appShortcuts.size)
            }
            updateNoAppsVisibility()
        } else {
            if (position >= 0 && position < availableTiles.size) {
                availableTiles.removeAt(position)
                availableAdapter.notifyItemRemoved(position)
                availableAdapter.notifyItemRangeChanged(position, availableTiles.size)
            }
        }
    }

    private fun updateNoAppsVisibility() {
        val allAppShortcuts = QuickSettingsManager.getAppShortcuts(this)
        noAppsText.visibility = if (allAppShortcuts.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun saveTiles() {
        val orderedIds = selectedAdapter.getTiles().map { it.id }
        QuickSettingsManager.setSelectedTileIds(this, orderedIds)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_APP_PICKER && resultCode == Activity.RESULT_OK) {
            val packageName = data?.getStringExtra("package_name") ?: return
            val activityName = data.getStringExtra("activity_name") ?: return
            val appName = data.getStringExtra("app_name") ?: return

            QuickSettingsManager.saveAppShortcut(this, packageName, activityName, appName)

            loadTiles()
            selectedAdapter.updateTiles(selectedTiles)
            availableAdapter.updateTiles(availableTiles)
            appShortcutsAdapter.updateTiles(appShortcuts)
        }
    }
}
