package com.example.drushtiai

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ThreatsAdapter(private val threats: List<Threat>) :
    RecyclerView.Adapter<ThreatsAdapter.ThreatViewHolder>() {

    class ThreatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgThreat: ImageView = itemView.findViewById(R.id.imgThreat)
        val tvCamera: TextView = itemView.findViewById(R.id.tvThreatCamera)
        val tvDateTime: TextView = itemView.findViewById(R.id.tvThreatDateTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThreatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_human_detected, parent, false)
        return ThreatViewHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ThreatViewHolder, position: Int) {
        val threat = threats[position]
        holder.tvCamera.text = "Detected by ${threat.cameraName}"
        holder.tvDateTime.text = threat.dateTime
        holder.imgThreat.setImageResource(threat.imageRes)
    }

    override fun getItemCount() = threats.size
}
