package com.example.controlcenter.shortcuts

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.controlcenter.R

class ShortcutAdapter(
    private val onShortcutClick: (ShortcutItem) -> Unit
) : ListAdapter<ShortcutItem, ShortcutAdapter.ShortcutViewHolder>(ShortcutDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shortcut_edit, parent, false)
        return ShortcutViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ShortcutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val background: View = itemView.findViewById(R.id.shortcutBackground)
        private val icon: ImageView = itemView.findViewById(R.id.shortcutIcon)
        private val label: TextView = itemView.findViewById(R.id.shortcutLabel)
        private val badge: ImageView = itemView.findViewById(R.id.addBadge)

        fun bind(shortcut: ShortcutItem) {
            label.text = shortcut.label

            if (shortcut.iconDrawable != null) {
                icon.setImageDrawable(shortcut.iconDrawable)
                icon.clearColorFilter()
            } else if (shortcut.iconResId != null) {
                icon.setImageResource(shortcut.iconResId)
                icon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.white))
            }

            if (shortcut.isActive) {
                badge.setImageResource(R.drawable.ic_remove_badge)
                background.setBackgroundResource(R.drawable.miui_circle_button_active)
            } else {
                badge.setImageResource(R.drawable.ic_add_badge)
                background.setBackgroundResource(R.drawable.miui_circle_button)
            }

            itemView.setOnClickListener {
                onShortcutClick(shortcut)
            }
        }
    }

    class ShortcutDiffCallback : DiffUtil.ItemCallback<ShortcutItem>() {
        override fun areItemsTheSame(oldItem: ShortcutItem, newItem: ShortcutItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ShortcutItem, newItem: ShortcutItem): Boolean {
            return oldItem.id == newItem.id && oldItem.isActive == newItem.isActive
        }
    }
}
