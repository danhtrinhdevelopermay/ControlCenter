package com.example.controlcenter

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
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

    private val expandedItems = mutableSetOf<String>()
    private val maxCollapsedLines = 2

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

    private fun getItemKey(notification: NotificationData): String {
        return "${notification.packageName}_${notification.id}"
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
        private val expandArrow: ImageView = itemView.findViewById(R.id.expandArrow)

        fun bind(notification: NotificationData) {
            if (notification.icon != null) {
                appIcon.setImageDrawable(notification.icon)
            } else {
                appIcon.setImageResource(R.drawable.ic_notification)
            }

            appName.text = notification.appName
            notificationTime.text = formatTime(notification.time)
            notificationTitle.text = notification.title

            val itemKey = getItemKey(notification)
            val isExpanded = expandedItems.contains(itemKey)

            if (notification.content.isNotEmpty()) {
                notificationContent.text = notification.content
                notificationContent.visibility = View.VISIBLE
                
                notificationContent.post {
                    val layout = notificationContent.layout
                    if (layout != null) {
                        val lineCount = layout.lineCount
                        val hasEllipsis = if (lineCount > 0) {
                            layout.getEllipsisCount(lineCount - 1) > 0
                        } else false
                        
                        val needsExpansion = lineCount > maxCollapsedLines || hasEllipsis || notification.content.length > 100
                        
                        if (needsExpansion) {
                            expandArrow.visibility = View.VISIBLE
                            
                            if (isExpanded) {
                                notificationContent.maxLines = Integer.MAX_VALUE
                                expandArrow.rotation = 180f
                            } else {
                                notificationContent.maxLines = maxCollapsedLines
                                expandArrow.rotation = 0f
                            }
                        } else {
                            expandArrow.visibility = View.GONE
                            notificationContent.maxLines = maxCollapsedLines
                        }
                    }
                }
                
                if (isExpanded) {
                    notificationContent.maxLines = Integer.MAX_VALUE
                    expandArrow.rotation = 180f
                } else {
                    notificationContent.maxLines = maxCollapsedLines
                    expandArrow.rotation = 0f
                }
            } else {
                notificationContent.visibility = View.GONE
                expandArrow.visibility = View.GONE
            }

            if (notification.largeIcon != null) {
                notificationImage.setImageBitmap(notification.largeIcon)
                previewContainer.visibility = View.VISIBLE
                notificationImage.visibility = View.VISIBLE
            } else {
                previewContainer.visibility = View.GONE
                notificationImage.visibility = View.GONE
            }

            val cardColor = getCardColor()
            val density = itemView.context.resources.displayMetrics.density
            notificationCard.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20 * density
                setColor(cardColor)
            }

            expandArrow.setOnClickListener {
                toggleExpand(notification, itemKey)
            }

            itemView.setOnClickListener {
                onItemClick(notification)
            }
        }

        private fun toggleExpand(notification: NotificationData, itemKey: String) {
            val isCurrentlyExpanded = expandedItems.contains(itemKey)
            
            if (isCurrentlyExpanded) {
                expandedItems.remove(itemKey)
                animateCollapse()
            } else {
                expandedItems.add(itemKey)
                animateExpand()
            }
        }

        private fun animateExpand() {
            notificationContent.maxLines = Integer.MAX_VALUE
            
            ValueAnimator.ofFloat(0f, 180f).apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    expandArrow.rotation = animator.animatedValue as Float
                }
                start()
            }
        }

        private fun animateCollapse() {
            notificationContent.maxLines = maxCollapsedLines
            
            ValueAnimator.ofFloat(180f, 0f).apply {
                duration = 200
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    expandArrow.rotation = animator.animatedValue as Float
                }
                start()
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
