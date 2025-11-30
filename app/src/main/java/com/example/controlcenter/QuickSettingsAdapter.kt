package com.example.controlcenter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class QuickSettingsAdapter(
    private val tiles: MutableList<QuickSettingTile>,
    private val mode: Mode,
    private val onTileClick: (QuickSettingTile, Int) -> Unit
) : RecyclerView.Adapter<QuickSettingsAdapter.TileViewHolder>() {

    enum class Mode {
        SELECTED,
        AVAILABLE,
        APP_SHORTCUT
    }

    class TileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tileBackground: View = view.findViewById(R.id.tileBackground)
        val tileIcon: ImageView = view.findViewById(R.id.tileIcon)
        val actionBadge: ImageView = view.findViewById(R.id.actionBadge)
        val tileName: TextView = view.findViewById(R.id.tileName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_setting_tile, parent, false)
        return TileViewHolder(view)
    }

    override fun onBindViewHolder(holder: TileViewHolder, position: Int) {
        val tile = tiles[position]

        holder.tileName.text = tile.name

        if (tile.type == QuickSettingTile.TileType.APP_SHORTCUT && tile.appIcon != null) {
            holder.tileIcon.setImageDrawable(tile.appIcon)
            holder.tileIcon.imageTintList = null
        } else {
            holder.tileIcon.setImageResource(tile.iconResId)
            holder.tileIcon.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
        }

        when (mode) {
            Mode.SELECTED -> {
                holder.actionBadge.setImageResource(R.drawable.ic_remove_circle)
                holder.actionBadge.visibility = View.VISIBLE
            }
            Mode.AVAILABLE, Mode.APP_SHORTCUT -> {
                holder.actionBadge.setImageResource(R.drawable.ic_add_circle)
                holder.actionBadge.visibility = View.VISIBLE
            }
        }

        holder.itemView.setOnClickListener {
            onTileClick(tile, position)
        }
    }

    override fun getItemCount() = tiles.size

    fun updateTiles(newTiles: List<QuickSettingTile>) {
        tiles.clear()
        tiles.addAll(newTiles)
        notifyDataSetChanged()
    }

    fun addTile(tile: QuickSettingTile) {
        tiles.add(tile)
        notifyItemInserted(tiles.size - 1)
    }

    fun removeTileAt(position: Int) {
        if (position in tiles.indices) {
            tiles.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, tiles.size)
        }
    }

    fun removeTileById(tileId: String): Int {
        val index = tiles.indexOfFirst { it.id == tileId }
        if (index != -1) {
            tiles.removeAt(index)
            notifyItemRemoved(index)
            notifyItemRangeChanged(index, tiles.size)
        }
        return index
    }

    fun getTiles(): List<QuickSettingTile> = tiles.toList()

    fun moveTile(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                java.util.Collections.swap(tiles, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                java.util.Collections.swap(tiles, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }
}
