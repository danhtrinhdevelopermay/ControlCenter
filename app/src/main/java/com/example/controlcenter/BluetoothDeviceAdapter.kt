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
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = devices[position]
        
        holder.deviceNameText.text = device.name
        
        val iconResId = getDeviceIcon(device.name, device.deviceType)
        holder.deviceIcon.setImageResource(iconResId)
        holder.deviceIcon.setColorFilter(0xFF8E8E93.toInt())
        
        holder.itemView.setOnClickListener {
            onDeviceClick(device)
        }
    }

    private fun getDeviceIcon(name: String, deviceType: Int): Int {
        val nameLower = name.lowercase()
        
        return when {
            nameLower.contains("tv") || nameLower.contains("television") -> R.drawable.ic_device_tv
            nameLower.contains("headphone") || nameLower.contains("airpod") || 
            nameLower.contains("buds") || nameLower.contains("earphone") ||
            nameLower.contains("tws") || nameLower.contains("earbuds") -> R.drawable.ic_device_headphone
            nameLower.contains("car") || nameLower.contains("wrx") || 
            nameLower.contains("auto") || nameLower.contains("vehicle") -> R.drawable.ic_device_car
            nameLower.contains("speaker") || nameLower.contains("soundbar") ||
            nameLower.contains("zqs") || nameLower.contains("jbl") ||
            nameLower.contains("bose") || nameLower.contains("sony") -> R.drawable.ic_device_speaker
            nameLower.contains("keyboard") || nameLower.contains("key") -> R.drawable.ic_device_keyboard
            nameLower.contains("phone") || nameLower.contains("iphone") ||
            nameLower.contains("samsung") || nameLower.contains("xiaomi") ||
            nameLower.contains("oppo") || nameLower.contains("vivo") ||
            nameLower.contains("realme") || nameLower.contains("huawei") -> R.drawable.ic_device_phone
            else -> R.drawable.ic_device_phone
        }
    }

    override fun getItemCount() = devices.size

    fun updateDevices(newDevices: List<ShizukuBluetoothDevice>) {
        devices = newDevices
        notifyDataSetChanged()
    }
}
