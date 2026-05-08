package com.example.drushtiai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.drushtiai.data.CheatingSnapshotRow

class SnapshotGridAdapter(
    private val items: MutableList<CheatingSnapshotRow>,
    private val onOpen: (CheatingSnapshotRow) -> Unit
) : RecyclerView.Adapter<SnapshotGridAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val image: ImageView = itemView.findViewById(R.id.ivSnapshot)
        val label: TextView = itemView.findViewById(R.id.tvSnapshotLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_snapshot_tile, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.label.text = s.label ?: "Incident"
        Glide.with(holder.image).load(s.imageUrl).centerCrop().into(holder.image)
        holder.itemView.setOnClickListener { onOpen(s) }
    }

    fun replaceAll(new: List<CheatingSnapshotRow>) {
        items.clear()
        items.addAll(new)
        notifyDataSetChanged()
    }
}
