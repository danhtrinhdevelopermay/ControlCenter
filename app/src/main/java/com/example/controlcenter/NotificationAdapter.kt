package com.example.controlcenter

import android.graphics.Color
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
    private val onActionClick: (NotificationAction) -> Unit,
    private val getCardColor: () -> Int
) : ListAdapter<NotificationData, NotificationAdapter.NotificationViewHolder>(NotificationDiffCallback()) {

    private val expandedItems = mutableSetOf<String>()
    private val maxCollapsedLines = 2
    private val minContentLengthForExpand = 80
    
    private var cachedCardColor: Int? = null
    private var cachedCornerRadius: Float = 0f
    private var cachedActionButtonRadius: Float = 0f
    private var cachedActionColor: Int = Color.parseColor("#1A007AFF")
    private var cachedActionTextColor: Int = Color.parseColor("#007AFF")

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        
        val density = parent.context.resources.displayMetrics.density
        if (cachedCornerRadius == 0f) {
            cachedCornerRadius = 20 * density
            cachedActionButtonRadius = 14 * density
        }
        
        return NotificationViewHolder(view, density)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            holder.bindPartial(getItem(position), payloads)
        }
    }

    fun removeItem(position: Int): NotificationData? {
        if (position < 0 || position >= currentList.size) return null
        val item = getItem(position)
        val newList = currentList.toMutableList()
        newList.removeAt(position)
        submitList(newList)
        return item
    }
    
    fun invalidateCardColorCache() {
        cachedCardColor = null
    }

    private fun getItemKey(notification: NotificationData): String {
        return "${notification.packageName}_${notification.id}"
    }
    
    private fun getCachedCardColor(): Int {
        if (cachedCardColor == null) {
            cachedCardColor = getCardColor()
        }
        return cachedCardColor!!
    }

    inner class NotificationViewHolder(itemView: View, private val density: Float) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.appIcon)
        private val appName: TextView = itemView.findViewById(R.id.appName)
        private val notificationTime: TextView = itemView.findViewById(R.id.notificationTime)
        private val notificationTitle: TextView = itemView.findViewById(R.id.notificationTitle)
        private val notificationContent: TextView = itemView.findViewById(R.id.notificationContent)
        private val notificationImage: ImageView = itemView.findViewById(R.id.notificationImage)
        private val notificationCard: LinearLayout = itemView.findViewById(R.id.notificationCard)
        private val previewContainer: View = itemView.findViewById(R.id.previewContainer)
        private val expandArrow: ImageView = itemView.findViewById(R.id.expandArrow)
        private val actionsContainer: LinearLayout = itemView.findViewById(R.id.actionsContainer)
        
        private var currentNotification: NotificationData? = null
        private var cardBackground: GradientDrawable? = null
        private val actionButtons = mutableListOf<TextView>()
        private val paddingH = (12 * density).toInt()
        private val paddingV = (6 * density).toInt()
        private val marginEnd = (8 * density).toInt()

        init {
            cardBackground = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cachedCornerRadius
            }
            notificationCard.background = cardBackground
            
            for (i in 0 until 3) {
                val button = TextView(itemView.context).apply {
                    setTextColor(cachedActionTextColor)
                    textSize = 13f
                    setPadding(paddingH, paddingV, paddingH, paddingV)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = cachedActionButtonRadius
                        setColor(cachedActionColor)
                    }
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    params.marginEnd = marginEnd
                    layoutParams = params
                    visibility = View.GONE
                }
                actionButtons.add(button)
                actionsContainer.addView(button)
            }
        }

        fun bind(notification: NotificationData) {
            currentNotification = notification
            
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
                
                val needsExpansion = notification.content.length > minContentLengthForExpand || 
                                     notification.content.contains('\n')
                
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

            cardBackground?.setColor(getCachedCardColor())
            
            setupActions(notification)

            expandArrow.setOnClickListener {
                toggleExpand(notification, itemKey)
            }

            itemView.setOnClickListener {
                onItemClick(notification)
            }
        }
        
        private fun setupActions(notification: NotificationData) {
            val actions = notification.actions.take(3)
            
            if (actions.isEmpty()) {
                actionsContainer.visibility = View.GONE
                actionButtons.forEach { it.visibility = View.GONE }
                return
            }
            
            actionsContainer.visibility = View.VISIBLE
            
            for (i in 0 until 3) {
                if (i < actions.size) {
                    val action = actions[i]
                    actionButtons[i].apply {
                        text = action.title
                        visibility = View.VISIBLE
                        setOnClickListener { onActionClick(action) }
                    }
                } else {
                    actionButtons[i].visibility = View.GONE
                }
            }
        }
        
        fun bindPartial(notification: NotificationData, payloads: MutableList<Any>) {
            for (payload in payloads) {
                when (payload) {
                    "expand_state" -> {
                        val itemKey = getItemKey(notification)
                        val isExpanded = expandedItems.contains(itemKey)
                        if (isExpanded) {
                            notificationContent.maxLines = Integer.MAX_VALUE
                            expandArrow.rotation = 180f
                        } else {
                            notificationContent.maxLines = maxCollapsedLines
                            expandArrow.rotation = 0f
                        }
                    }
                }
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
            expandArrow.animate()
                .rotation(180f)
                .setDuration(120)
                .start()
        }

        private fun animateCollapse() {
            notificationContent.maxLines = maxCollapsedLines
            expandArrow.animate()
                .rotation(0f)
                .setDuration(120)
                .start()
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
            if (oldItem.title != newItem.title ||
                oldItem.content != newItem.content ||
                oldItem.time != newItem.time ||
                oldItem.actions.size != newItem.actions.size) {
                return false
            }
            
            for (i in oldItem.actions.indices) {
                if (oldItem.actions[i].title != newItem.actions[i].title) {
                    return false
                }
            }
            
            return true
        }
    }
}
