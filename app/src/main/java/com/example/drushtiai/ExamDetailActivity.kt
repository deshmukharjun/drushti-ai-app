package com.example.drushtiai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.example.drushtiai.data.CheatingSnapshotRow
import com.example.drushtiai.data.ExamRow
import com.example.drushtiai.repo.ExamRepository
import com.example.drushtiai.repo.SnapshotRepository
import kotlinx.coroutines.launch

class ExamDetailActivity : AppCompatActivity() {

    companion object {
        fun intent(ctx: Context, examId: String): Intent =
            Intent(ctx, ExamDetailActivity::class.java).putExtra(IntentExtras.EXAM_ID, examId)
    }

    private val examRepo = ExamRepository()
    private val snapshotRepo = SnapshotRepository()

    private lateinit var examId: String
    private var loaded: ExamRow? = null
    private val snapshotItems = mutableListOf<CheatingSnapshotRow>()
    private lateinit var adapter: SnapshotGridAdapter

    private lateinit var cardReadOnly: View
    private lateinit var containerEdit: View
    private lateinit var btnEdit: MaterialButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var tvReadSubject: TextView
    private lateinit var tvReadDate: TextView
    private lateinit var tvReadTime: TextView
    private lateinit var tvReadStudents: TextView
    private lateinit var tvReadNotes: TextView
    private lateinit var etSubject: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etTime: TextInputEditText
    private lateinit var etStudents: TextInputEditText
    private lateinit var etNotes: TextInputEditText

    private val viewerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val deleted = result.data?.getBooleanExtra(IntentExtras.SNAPSHOT_DELETED, false) == true
            if (deleted) loadSnapshots()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_detail)

        examId = intent.getStringExtra(IntentExtras.EXAM_ID) ?: run {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        cardReadOnly = findViewById(R.id.cardExamReadOnly)
        containerEdit = findViewById(R.id.containerExamEdit)
        btnEdit = findViewById(R.id.btnEditExam)
        btnCancel = findViewById(R.id.btnCancelExamEdit)
        tvReadSubject = findViewById(R.id.tvReadSubject)
        tvReadDate = findViewById(R.id.tvReadDate)
        tvReadTime = findViewById(R.id.tvReadTime)
        tvReadStudents = findViewById(R.id.tvReadStudents)
        tvReadNotes = findViewById(R.id.tvReadNotes)
        etSubject = findViewById(R.id.etSubject)
        etDate = findViewById(R.id.etExamDate)
        etTime = findViewById(R.id.etExamTime)
        etStudents = findViewById(R.id.etStudentCount)
        etNotes = findViewById(R.id.etRoomNotes)

        val rv = findViewById<RecyclerView>(R.id.rvSnapshots)
        rv.layoutManager = GridLayoutManager(this, 2)
        adapter = SnapshotGridAdapter(snapshotItems) { snap ->
            viewerLauncher.launch(SnapshotViewerActivity.intent(this, snap))
        }
        rv.adapter = adapter

        btnEdit.setOnClickListener { setEditing(true) }
        btnCancel.setOnClickListener {
            loaded?.let { applyExamToEditors(it) }
            setEditing(false)
        }

        findViewById<MaterialButton>(R.id.btnSaveExam).setOnClickListener {
            val base = loaded ?: return@setOnClickListener
            val subject = etSubject.text?.toString()?.trim().orEmpty()
            val date = etDate.text?.toString()?.trim().orEmpty()
            val time = etTime.text?.toString()?.trim().orEmpty()
            val students = etStudents.text?.toString()?.trim()?.toIntOrNull() ?: 0
            if (subject.isEmpty() || date.isEmpty() || time.isEmpty()) {
                Toast.makeText(this, "Fill required fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val updated = base.copy(
                subject = subject,
                examDate = date,
                examTime = time,
                studentCount = students,
                roomNotes = etNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            )
            lifecycleScope.launch {
                try {
                    examRepo.updateExam(updated)
                    loaded = updated
                    applyExamToReadOnlyCard(updated)
                    setEditing(false)
                    Toast.makeText(this@ExamDetailActivity, "Saved.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@ExamDetailActivity, e.message ?: "Update failed", Toast.LENGTH_LONG).show()
                }
            }
        }

        setEditing(false)
        loadAll()
    }

    private fun setEditing(editing: Boolean) {
        cardReadOnly.visibility = if (editing) View.GONE else View.VISIBLE
        btnEdit.visibility = if (editing) View.GONE else View.VISIBLE
        containerEdit.visibility = if (editing) View.VISIBLE else View.GONE
    }

    private fun applyExamToReadOnlyCard(exam: ExamRow) {
        tvReadSubject.text = exam.subject
        tvReadDate.text = exam.examDate
        tvReadTime.text = exam.examTime
        tvReadStudents.text = exam.studentCount.toString()
        val n = exam.roomNotes?.trim().orEmpty()
        tvReadNotes.text = if (n.isEmpty()) getString(R.string.exam_notes_empty) else n
    }

    private fun applyExamToEditors(exam: ExamRow) {
        etSubject.setText(exam.subject)
        etDate.setText(exam.examDate)
        etTime.setText(exam.examTime)
        etStudents.setText(exam.studentCount.toString())
        etNotes.setText(exam.roomNotes.orEmpty())
    }

    private fun loadAll() {
        lifecycleScope.launch {
            val exam = try {
                examRepo.getExam(examId)
            } catch (_: Exception) {
                null
            }
            if (exam == null) {
                Toast.makeText(this@ExamDetailActivity, "Exam not found.", Toast.LENGTH_SHORT).show()
                finish()
                return@launch
            }
            loaded = exam
            applyExamToReadOnlyCard(exam)
            applyExamToEditors(exam)
            setEditing(false)
            loadSnapshots()
        }
    }

    private fun loadSnapshots() {
        lifecycleScope.launch {
            val list = try {
                snapshotRepo.listForExam(examId)
            } catch (_: Exception) {
                emptyList()
            }
            adapter.replaceAll(list)
        }
    }
}
