package com.example.controlcenter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WiFiNetworkAdapter(
    private var networks: List<WiFiNetwork>,
    private val onNetworkClick: (WiFiNetwork) -> Unit
) : RecyclerView.Adapter<WiFiNetworkAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val wifiSignalIcon: ImageView = view.findViewById(R.id.wifiSignalIcon)
        val wifiSsidText: TextView = view.findViewById(R.id.wifiSsidText)
        val wifiStatusText: TextView = view.findViewById(R.id.wifiStatusText)
        val wifiLockIcon: ImageView = view.findViewById(R.id.wifiLockIcon)
        val wifiConnectedIcon: ImageView = view.findViewById(R.id.wifiConnectedIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wifi_network, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val network = networks[position]
        
        holder.wifiSsidText.text = network.ssid
        
        holder.wifiSignalIcon.setImageResource(R.drawable.ic_wifi)
        val signalAlpha = when (network.signalLevel) {
            4 -> 1.0f
            3 -> 0.85f
            2 -> 0.7f
            1 -> 0.55f
            else -> 0.4f
        }
        holder.wifiSignalIcon.alpha = signalAlpha
        
        if (network.isSecured) {
            holder.wifiLockIcon.visibility = View.VISIBLE
            holder.wifiLockIcon.setImageResource(R.drawable.ic_lock)
        } else {
            holder.wifiLockIcon.visibility = View.GONE
        }
        
        if (network.isConnected) {
            holder.wifiConnectedIcon.visibility = View.VISIBLE
            holder.wifiConnectedIcon.setImageResource(R.drawable.ic_check)
            holder.wifiStatusText.visibility = View.VISIBLE
            holder.wifiStatusText.text = "Đã kết nối"
            holder.wifiSignalIcon.setColorFilter(0xFF3B82F6.toInt())
        } else {
            holder.wifiConnectedIcon.visibility = View.GONE
            holder.wifiStatusText.visibility = View.GONE
            holder.wifiSignalIcon.setColorFilter(0xFFFFFFFF.toInt())
        }
        
        holder.itemView.setOnClickListener {
            onNetworkClick(network)
        }
    }

    override fun getItemCount(): Int = networks.size

    fun updateNetworks(newNetworks: List<WiFiNetwork>) {
        networks = newNetworks
        notifyDataSetChanged()
    }
}
