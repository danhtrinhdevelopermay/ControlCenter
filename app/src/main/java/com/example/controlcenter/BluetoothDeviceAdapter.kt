package com.example.controlcenter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BluetoothDeviceAdapter(
    private var devices: List<ShizukuBluetoothDevice>,
    private val onDeviceClick: (ShizukuBluetoothDevice) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceIcon: ImageView = view.findViewById(R.id.bluetoothDeviceIcon)
        val deviceNameText: TextView = view.findViewById(R.id.deviceNameText)
        val deviceStatusText: TextView = view.findViewById(R.id.deviceStatusText)
        val pairedIcon: ImageView = view.findViewById(R.id.pairedIcon)
        val connectedIcon: ImageView = view.findViewById(R.id.connectedIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        
        holder.deviceNameText.text = device.name
        
        val statusText = when {
            device.isConnected -> "Đã kết nối"
            device.isPaired -> "Đã ghép đôi"
            else -> "Khả dụng"
        }
        holder.deviceStatusText.text = statusText
        holder.deviceStatusText.visibility = View.VISIBLE
        
        holder.pairedIcon.visibility = if (device.isPaired && !device.isConnected) View.VISIBLE else View.GONE
        holder.connectedIcon.visibility = if (device.isConnected) View.VISIBLE else View.GONE
        
        val iconTint = when {
            device.isConnected -> 0xFF2196F3.toInt()
            device.isPaired -> 0xFF4CAF50.toInt()
            else -> 0xFFFFFFFF.toInt()
        }
        holder.deviceIcon.setColorFilter(iconTint)
        
        holder.itemView.setOnClickListener {
            onDeviceClick(device)
        }
    }

    override fun getItemCount() = devices.size

    fun updateDevices(newDevices: List<ShizukuBluetoothDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }
}
