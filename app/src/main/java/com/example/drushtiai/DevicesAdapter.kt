package com.example.drushtiai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DevicesAdapter(private val devices: List<Device>) :
    RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgDevice: ImageView = itemView.findViewById(R.id.imgDevice)
        val tvDeviceName: TextView = itemView.findViewById(R.id.tvDeviceName)
        val tvDeviceLocation: TextView = itemView.findViewById(R.id.tvDeviceLocation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_devices, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.imgDevice.setImageResource(device.imageRes)
        holder.tvDeviceName.text = device.name
        holder.tvDeviceLocation.text = device.location
    }

    override fun getItemCount() = devices.size
}
