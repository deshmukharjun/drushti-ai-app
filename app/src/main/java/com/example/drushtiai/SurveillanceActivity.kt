package com.example.drushtiai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

/**
 * Full surveillance flow:
 *
 *   [Start surveillance]
 *      ↓
 *   1. Ask backend for an existing camera, register one if none exist.
 *   2. Tell backend to start the YOLO/MediaPipe stream tagged with this exam_id.
 *   3. Mark the exam status = "live" in Supabase.
 *   4. Poll cheating_snapshots for this exam every 4 seconds.
 *   5. For every NEW snapshot row, fire a heads-up notification (sound +
 *      vibration) so the invigilator hears it even with the screen off.
 *
 *   [Stop surveillance]
 *      ↓
 *   1. Tell backend to stop the stream (so it doesn't keep generating
 *      snapshots after the exam).
 *   2. Mark the exam status = "completed" in Supabase.
 *   3. Return to the dashboard.
 */
class SurveillanceActivity : AppCompatActivity() {

    private val examRepo = ExamRepository()
    private val snapshotRepo = SnapshotRepository()

    private lateinit var examId: String
    private val snapshotItems = mutableListOf<CheatingSnapshotRow>()
    private lateinit var adapter: SnapshotGridAdapter
    private var pollJob: Job? = null
    private var surveillanceOn = false

    /** IDs of snapshots already shown to the user — used to detect NEW arrivals. */
    private val seenSnapshotIds = mutableSetOf<String>()

    /** Backend camera id we started — needed to stop it cleanly on exit. */
    private var activeCameraId: String? = null

    private lateinit var btnToggle: MaterialButton

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Notifications disabled — alerts will only show in-app.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_surveillance)

        examId = intent.getStringExtra(IntentExtras.EXAM_ID) ?: run {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            if (surveillanceOn) {
                Toast.makeText(this, "Stop surveillance before leaving.", Toast.LENGTH_SHORT).show()
            } else {
                finish()
            }
        }

        val tvSubject = findViewById<TextView>(R.id.tvSurvSubject)
        val tvMeta = findViewById<TextView>(R.id.tvSurvMeta)
        btnToggle = findViewById(R.id.btnToggleSurveillance)
        val rv = findViewById<RecyclerView>(R.id.rvSnapshots)
        rv.layoutManager = GridLayoutManager(this, 2)
        adapter = SnapshotGridAdapter(snapshotItems) { snap ->
            startActivity(SnapshotViewerActivity.intent(this, snap))
        }
        rv.adapter = adapter

        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

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

        btnToggle.setOnClickListener {
            if (!surveillanceOn) startSurveillance() else stopSurveillance()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val perm = Manifest.permission.POST_NOTIFICATIONS
        val granted = ContextCompat.checkSelfPermission(this, perm) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(perm)
    }

    private fun startSurveillance() {
        btnToggle.isEnabled = false
        // Optimistically flip the UI immediately — if backend control fails we'll
        // still surveil via Supabase polling, which is the source of truth for
        // snapshots/notifications anyway.
        surveillanceOn = true
        btnToggle.text = getString(R.string.stop_surveillance)

        lifecycleScope.launch {
            // Seed the seen-set up front so polling can begin while we negotiate
            // with the backend in parallel. This guarantees notifications fire
            // even if backend control hangs or fails.
            try {
                val existing = snapshotRepo.listForExam(examId)
                existing.mapNotNull { it.id }.forEach { seenSnapshotIds.add(it) }
                adapter.replaceAll(existing)
            } catch (_: Exception) { /* fine — first poll will fill it */ }

            startPolling()
            runCatching { examRepo.setStatus(examId, "live") }
            btnToggle.isEnabled = true

            // ── Best-effort backend control: register + start the camera. ────
            // If this fails (laptop unreachable, slow network, backend started
            // manually), we don't abort surveillance — Supabase polling keeps
            // working and the user still gets notifications when the backend
            // (started elsewhere) uploads a snapshot.
            launch backend@{
                val cam = when (val r = BackendApi.ensureCameraJoined()) {
                    is BackendApi.Result.Failure -> {
                        Toast.makeText(
                            this@SurveillanceActivity,
                            "Backend control failed — surveilling via Supabase only.\n${r.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        return@backend
                    }
                    is BackendApi.Result.Success -> r.value
                }
                activeCameraId = cam.id

                runCatching { examRepo.setCameraLinked(examId, cam.id) }

                when (val start = BackendApi.startStream(cam.id, examId)) {
                    is BackendApi.Result.Failure -> {
                        Toast.makeText(
                            this@SurveillanceActivity,
                            "Stream start failed (continuing anyway): ${start.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    is BackendApi.Result.Success -> {
                        Toast.makeText(
                            this@SurveillanceActivity,
                            "Backend stream started · ${cam.name}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun stopSurveillance() {
        // Flip UI + halt polling immediately. Cleanup happens in the background.
        surveillanceOn = false
        pollJob?.cancel()
        pollJob = null
        btnToggle.text = getString(R.string.start_surveillance)
        btnToggle.isEnabled = false

        // Use the application scope so the cleanup completes even if we
        // navigate away before the network calls return.
        val app = application as? DrushtiApplication
        val camId = activeCameraId
        app?.applicationScope?.launch {
            if (camId != null) runCatching { BackendApi.stopStream(camId) }
            runCatching { examRepo.setStatus(examId, "completed") }
        }

        // Don't wait — go to dashboard right away so the UI feels responsive.
        goDashboard()
    }

    /**
     * Poll Supabase for snapshots tagged with this exam. New rows trigger a
     * sound+vibration notification. Existing rows are reconciled into the grid.
     *
     * Runs in the application scope so it KEEPS POLLING (and notifying) when
     * the activity is in the background — that way the invigilator hears the
     * alert even if they switched apps.
     */
    private fun startPolling() {
        pollJob?.cancel()
        val app = application as? DrushtiApplication
        val scope = app?.applicationScope ?: lifecycleScope
        pollJob = scope.launch {
            while (surveillanceOn && isActive) {
                val list = try {
                    snapshotRepo.listForExam(examId)
                } catch (e: Exception) {
                    android.util.Log.w("Surveillance", "poll failed: ${e.message}")
                    emptyList<CheatingSnapshotRow>()
                }

                val newOnes = list.filter { row ->
                    val id = row.id ?: return@filter false
                    !seenSnapshotIds.contains(id)
                }
                if (newOnes.isNotEmpty()) {
                    android.util.Log.i(
                        "Surveillance",
                        "${newOnes.size} new snapshot(s) for exam $examId — firing notifications"
                    )
                    // Reverse so we notify in chronological order
                    newOnes.reversed().forEachIndexed { idx, snap ->
                        snap.id?.let { seenSnapshotIds.add(it) }
                        if (idx > 0) delay(200)
                        NotificationHelper.notifyNewSnapshot(
                            context = applicationContext,
                            examId = examId,
                            snapshot = snap,
                            totalNew = newOnes.size
                        )
                    }
                }

                // Update grid only when activity is alive (avoids RecyclerView
                // crashes after onDestroy).
                if (!isFinishing && !isDestroyed) {
                    runOnUiThread { adapter.replaceAll(list) }
                }

                // Poll faster than the backend's 5-second snapshot cooldown so
                // notifications feel near-real-time.
                delay(2500)
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
        // Belt and suspenders: if the activity dies without going through Stop,
        // still try to halt the backend stream so it doesn't keep churning.
        if (surveillanceOn) {
            val camId = activeCameraId
            if (camId != null) {
                (application as? DrushtiApplication)?.applicationScope?.launch {
                    runCatching { BackendApi.stopStream(camId) }
                }
            }
        }
        super.onDestroy()
    }
}
