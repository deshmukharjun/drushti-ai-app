package com.example.drushtiai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar

class AlertsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alerts)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { finish() }
        }

        val items = mutableListOf(
            DummyAlert("Drushti AI", "This is a placeholder alerts screen. Hook it to Supabase or push notifications when you are ready."),
            DummyAlert("Exam reminder", "You can surface high-risk detections or system messages here."),
            DummyAlert("Maintenance", "No live data yet — dummy content only.")
        )

        findViewById<RecyclerView>(R.id.rvAlerts).apply {
            layoutManager = LinearLayoutManager(this@AlertsActivity)
            adapter = DummyAlertsAdapter(items)
        }
    }

    data class DummyAlert(val title: String, val body: String)
}
