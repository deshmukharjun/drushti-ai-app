package com.example.drushtiai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.drushtiai.data.ExamRow

class PastExamsAdapter(
    private val items: List<ExamRow>,
    private val itemLayoutId: Int = R.layout.item_exam_horizontal,
    private val onClick: (ExamRow) -> Unit
) : RecyclerView.Adapter<PastExamsAdapter.VH>() {

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val subject: TextView = itemView.findViewById(R.id.tvExamSubject)
        val meta: TextView = itemView.findViewById(R.id.tvExamMeta)
        val status: TextView = itemView.findViewById(R.id.tvExamStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(itemLayoutId, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val e = items[position]
        holder.subject.text = e.subject
        holder.meta.text = "${e.examDate} · ${e.examTime}"
        holder.status.text = e.status
        holder.itemView.setOnClickListener { onClick(e) }
    }
}
