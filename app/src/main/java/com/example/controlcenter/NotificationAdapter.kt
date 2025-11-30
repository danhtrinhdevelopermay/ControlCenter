package com.example.controlcenter

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class NotificationAdapter(
    private val onItemClick: (NotificationData) -> Unit,
    private val getCardColor: () -> Int
) : ListAdapter<NotificationData, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun removeItem(position: Int): NotificationData? {
        if (position < 0 || position >= currentList.size) return null
        val item = getItem(position)
        val newList = currentList.toMutableList()
        newList.removeAt(position)
        submitList(newList)
        return item
    }

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val notificationTime: TextView = itemView.findViewById(R.id.notificationTime)
        private val notificationTitle: TextView = itemView.findViewById(R.id.notificationTitle)
        private val notificationContent: TextView = itemView.findViewById(R.id.notificationContent)
        private val notificationImage: ImageView = itemView.findViewById(R.id.notificationImage)
        private val notificationCard: LinearLayout = itemView.findViewById(R.id.notificationCard)
        private val previewContainer: View = itemView.findViewById(R.id.previewContainer)
        private val expandArrow: View = itemView.findViewById(R.id.expandArrow)

        fun bind(notification: NotificationData) {
            if (notification.icon != null) {
                appIcon.setImageDrawable(notification.icon)
            } else {
                appIcon.setImageResource(R.drawable.ic_notification)
            }

            appName.text = notification.appName
            notificationTime.text = formatTime(notification.time)
            notificationTitle.text = notification.title

            if (notification.content.isNotEmpty()) {
                notificationContent.text = notification.content
                notificationContent.visibility = View.VISIBLE
            } else {
                notificationContent.visibility = View.GONE
            }

            if (notification.largeIcon != null) {
                notificationImage.setImageBitmap(notification.largeIcon)
                previewContainer.visibility = View.VISIBLE
                notificationImage.visibility = View.VISIBLE
            } else {
                previewContainer.visibility = View.GONE
                notificationImage.visibility = View.GONE
            }

            expandArrow.visibility = View.GONE

            val cardColor = getCardColor()
            val density = itemView.context.resources.displayMetrics.density
            notificationCard.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * density
                setColor(cardColor)
            }

            itemView.setOnClickListener {
                onItemClick(notification)
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60 * 1000 -> "Vừa xong"
                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} phút"
                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} giờ"
                else -> "${diff / (24 * 60 * 60 * 1000)} ngày"
            }
        }
    }

    class NotificationDiffCallback : DiffUtil.ItemCallback<NotificationData>() {
        override fun areItemsTheSame(oldItem: NotificationData, newItem: NotificationData): Boolean {
            return oldItem.id == newItem.id && oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: NotificationData, newItem: NotificationData): Boolean {
            return oldItem.title == newItem.title &&
                    oldItem.content == newItem.content &&
                    oldItem.time == newItem.time
        }
    }
}
