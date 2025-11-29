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
        val wifiLockIcon: ImageView = view.findViewById(R.id.wifiLockIcon)
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
        holder.wifiSignalIcon.setColorFilter(0xFF8E8E93.toInt())
        
        if (network.isSecured) {
            holder.wifiLockIcon.visibility = View.VISIBLE
            holder.wifiLockIcon.setImageResource(R.drawable.ic_lock)
        } else {
            holder.wifiLockIcon.visibility = View.GONE
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
