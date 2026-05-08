package com.example.drushtiai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_POSITION

class DummyAlertsAdapter(
    private val items: MutableList<AlertsActivity.DummyAlert>
) : RecyclerView.Adapter<DummyAlertsAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvAlertTitle)
        val body: TextView = v.findViewById(R.id.tvAlertBody)
        val dismiss: ImageButton = v.findViewById(R.id.btnDismissAlert)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_alert_dummy, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val a = items[position]
        holder.title.text = a.title
        holder.body.text = a.body
        holder.dismiss.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != NO_POSITION && pos < items.size) {
                items.removeAt(pos)
                notifyItemRemoved(pos)
            }
        }
    }
}
