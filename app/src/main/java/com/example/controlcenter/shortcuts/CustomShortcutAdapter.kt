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

class CustomShortcutAdapter(
    private val onShortcutClick: (ShortcutItem) -> Unit
) : ListAdapter<ShortcutItem, CustomShortcutAdapter.ShortcutViewHolder>(ShortcutDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShortcutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_shortcut, parent, false)
        return ShortcutViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShortcutViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ShortcutViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val background: View = itemView.findViewById(R.id.shortcutBackground)
        private val icon: ImageView = itemView.findViewById(R.id.shortcutIcon)
        private val label: TextView = itemView.findViewById(R.id.shortcutLabel)

        fun bind(shortcut: ShortcutItem) {
            label.text = shortcut.label

            if (shortcut.iconDrawable != null) {
                icon.setImageDrawable(shortcut.iconDrawable)
                icon.clearColorFilter()
            } else if (shortcut.iconResId != null) {
                icon.setImageResource(shortcut.iconResId)
                icon.setColorFilter(ContextCompat.getColor(itemView.context, android.R.color.white))
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
            return oldItem == newItem
        }
    }
}
