package com.example.controlcenter

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppPickerActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var shortcutCountText: TextView
    private lateinit var adapter: AppListAdapter
    
    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        initViews()
        loadApps()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.appRecyclerView)
        searchEditText = findViewById(R.id.searchEditText)
        shortcutCountText = findViewById(R.id.shortcutCountText)

        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            finish()
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterApps(s?.toString() ?: "")
            }
        })

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppListAdapter { appInfo ->
            onAppClicked(appInfo)
        }
        recyclerView.adapter = adapter
        
        updateShortcutCount()
    }

    private fun loadApps() {
        Thread {
            allApps = AppShortcutManager.getInstalledApps(this)
            filteredApps = allApps
            runOnUiThread {
                adapter.updateApps(filteredApps)
            }
        }.start()
    }

    private fun filterApps(query: String) {
        filteredApps = if (query.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.appName.contains(query, ignoreCase = true) }
        }
        adapter.updateApps(filteredApps)
    }

    private fun onAppClicked(appInfo: AppInfo) {
        val isSaved = AppShortcutManager.isShortcutSaved(this, appInfo.packageName)
        
        if (isSaved) {
            AppShortcutManager.removeShortcut(this, appInfo.packageName)
            Toast.makeText(this, "Đã xóa ${appInfo.appName}", Toast.LENGTH_SHORT).show()
        } else {
            val savedCount = AppShortcutManager.getSavedShortcuts(this).size
            if (savedCount >= AppShortcutManager.getMaxShortcuts()) {
                Toast.makeText(this, "Đã đạt giới hạn ${AppShortcutManager.getMaxShortcuts()} phím tắt", Toast.LENGTH_SHORT).show()
                return
            }
            AppShortcutManager.addShortcut(this, appInfo.packageName)
            Toast.makeText(this, "Đã thêm ${appInfo.appName}", Toast.LENGTH_SHORT).show()
        }
        
        adapter.notifyDataSetChanged()
        updateShortcutCount()
    }

    private fun updateShortcutCount() {
        val count = AppShortcutManager.getSavedShortcuts(this).size
        val max = AppShortcutManager.getMaxShortcuts()
        shortcutCountText.text = "Đã chọn: $count/$max"
    }

    inner class AppListAdapter(
        private val onItemClick: (AppInfo) -> Unit
    ) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

        private var apps: List<AppInfo> = emptyList()

        fun updateApps(newApps: List<AppInfo>) {
            apps = newApps
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val appInfo = apps[position]
            holder.bind(appInfo)
        }

        override fun getItemCount(): Int = apps.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
            private val appName: TextView = itemView.findViewById(R.id.appName)
            private val checkIcon: ImageView = itemView.findViewById(R.id.checkIcon)

            fun bind(appInfo: AppInfo) {
                appName.text = appInfo.appName
                appInfo.icon?.let { appIcon.setImageDrawable(it) }
                
                val isSaved = AppShortcutManager.isShortcutSaved(itemView.context, appInfo.packageName)
                checkIcon.visibility = if (isSaved) View.VISIBLE else View.GONE

                itemView.setOnClickListener {
                    onItemClick(appInfo)
                }
            }
        }
    }
}
