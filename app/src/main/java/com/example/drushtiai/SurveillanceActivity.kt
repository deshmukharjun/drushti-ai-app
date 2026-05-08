package com.example.drushtiai

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.example.drushtiai.data.CheatingSnapshotRow
import com.example.drushtiai.repo.ExamRepository
import com.example.drushtiai.repo.SnapshotRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SurveillanceActivity : AppCompatActivity() {

    private val examRepo = ExamRepository()
    private val snapshotRepo = SnapshotRepository()

    private lateinit var examId: String
    private val snapshotItems = mutableListOf<CheatingSnapshotRow>()
    private lateinit var adapter: SnapshotGridAdapter
    private var pollJob: Job? = null
    private var surveillanceOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surveillance)

        examId = intent.getStringExtra(IntentExtras.EXAM_ID) ?: run {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val tvSubject = findViewById<TextView>(R.id.tvSurvSubject)
        val tvMeta = findViewById<TextView>(R.id.tvSurvMeta)
        val btn = findViewById<MaterialButton>(R.id.btnToggleSurveillance)
        val rv = findViewById<RecyclerView>(R.id.rvSnapshots)
        rv.layoutManager = GridLayoutManager(this, 2)
        adapter = SnapshotGridAdapter(snapshotItems) { snap ->
            startActivity(SnapshotViewerActivity.intent(this, snap))
        }
        rv.adapter = adapter

        lifecycleScope.launch {
            val exam = try {
                examRepo.getExam(examId)
            } catch (_: Exception) {
                null
            }
            if (exam == null) {
                Toast.makeText(this@SurveillanceActivity, "Exam not found.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            tvSubject.text = exam.subject
            tvMeta.text = "${exam.examDate} · ${exam.examTime} · ${exam.studentCount} students"
        }

        btn.setOnClickListener {
            if (!surveillanceOn) {
                surveillanceOn = true
                btn.text = getString(R.string.stop_surveillance)
                lifecycleScope.launch {
                    try {
                        examRepo.setStatus(examId, "live")
                    } catch (_: Exception) {
                        // Status update is best-effort; surveillance UI still runs
                    }
                }
                startPolling()
            } else {
                surveillanceOn = false
                pollJob?.cancel()
                pollJob = null
                btn.text = getString(R.string.start_surveillance)
                lifecycleScope.launch {
                    try {
                        examRepo.setStatus(examId, "completed")
                    } catch (_: Exception) {
                        // Continue back to dashboard even if update fails
                    }
                    goDashboard()
                }
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (surveillanceOn && isActive) {
                val list = try {
                    snapshotRepo.listForExam(examId)
                } catch (_: Exception) {
                    emptyList<CheatingSnapshotRow>()
                }
                adapter.replaceAll(list)
                delay(4000)
            }
        }
    }

    private fun goDashboard() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }
}
