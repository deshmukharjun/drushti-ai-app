package com.example.drushtiai

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.drushtiai.data.ExamInsert
import com.example.drushtiai.data.ExamRow
import com.example.drushtiai.repo.AuthRepository
import com.example.drushtiai.repo.ExamRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NewExamActivity : AppCompatActivity() {

    private val authRepo = AuthRepository()
    private val examRepo = ExamRepository()

    private var savedExamId: String? = null
    private var savedExam: ExamRow? = null

    private lateinit var etSubject: TextInputEditText
    private lateinit var etDate: TextInputEditText
    private lateinit var etTime: TextInputEditText
    private lateinit var etStudents: TextInputEditText
    private lateinit var btnStart: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_exam)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        etSubject = findViewById(R.id.etSubject)
        etDate = findViewById(R.id.etExamDate)
        etTime = findViewById(R.id.etExamTime)
        etStudents = findViewById(R.id.etStudentCount)
        val etNotes = findViewById<TextInputEditText>(R.id.etRoomNotes)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveExam)
        btnStart = findViewById(R.id.btnStartExamSession)
        val tilDate = findViewById<TextInputLayout>(R.id.tilExamDate)

        tilDate.setEndIconOnClickListener { openExamDatePicker() }
        etDate.setOnClickListener { openExamDatePicker() }
        tilDate.setOnClickListener { openExamDatePicker() }

        listOf(etSubject, etDate, etTime, etStudents, etNotes).forEach { et ->
            et.doAfterTextChanged { refreshStartButtonState() }
        }

        refreshStartButtonState()

        btnSave.setOnClickListener {
            val uid = if (GuestSession.isGuest) "guest" else authRepo.currentUserId()
            if (uid == null) {
                Toast.makeText(this, "Not signed in.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val subject = etSubject.text?.toString()?.trim().orEmpty()
            val date = etDate.text?.toString()?.trim().orEmpty()
            val time = etTime.text?.toString()?.trim().orEmpty()
            val students = etStudents.text?.toString()?.trim()?.toIntOrNull() ?: 0
            if (subject.isEmpty() || date.isEmpty() || time.isEmpty()) {
                Toast.makeText(this, "Fill subject, date, and time.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnSave.isEnabled = false
            lifecycleScope.launch {
                try {
                    val notes = etNotes.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    val existing = savedExam
                    if (existing != null && !existing.id.isNullOrBlank()) {
                        val updated = existing.copy(
                            subject = subject,
                            examDate = date,
                            examTime = time,
                            studentCount = students,
                            roomNotes = notes
                        )
                        examRepo.updateExam(updated)
                        savedExam = updated
                    } else {
                        val insert = ExamInsert(
                            userId = uid,
                            subject = subject,
                            examDate = date,
                            examTime = time,
                            studentCount = students,
                            roomNotes = notes
                        )
                        val row = examRepo.insertExam(insert)
                        savedExam = row
                        savedExamId = row.id
                    }
                    Toast.makeText(this@NewExamActivity, "Exam saved.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@NewExamActivity, e.message ?: "Save failed", Toast.LENGTH_LONG).show()
                } finally {
                    btnSave.isEnabled = true
                    refreshStartButtonState()
                }
            }
        }

        btnStart.setOnClickListener {
            val id = savedExamId ?: return@setOnClickListener
            startActivity(
                Intent(this, SurveillanceActivity::class.java)
                    .putExtra(IntentExtras.EXAM_ID, id)
            )
        }
    }

    private fun openExamDatePicker() {
        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.pick_exam_date))
        val existing = etDate.text?.toString()?.trim().orEmpty()
        if (existing.isNotEmpty()) {
            runCatching {
                val ld = LocalDate.parse(existing)
                val millis = ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                builder.setSelection(millis)
            }
        }
        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { millis ->
            val ld = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            etDate.setText(ld.format(DateTimeFormatter.ISO_LOCAL_DATE))
            refreshStartButtonState()
        }
        picker.show(supportFragmentManager, "exam_date")
    }

    private fun refreshStartButtonState() {
        val subject = etSubject.text?.toString()?.trim().orEmpty()
        val date = etDate.text?.toString()?.trim().orEmpty()
        val time = etTime.text?.toString()?.trim().orEmpty()
        val saved = savedExamId != null
        val enabled = subject.isNotEmpty() && date.isNotEmpty() && time.isNotEmpty() && saved
        btnStart.isEnabled = enabled
        btnStart.alpha = if (enabled) 1f else 0.45f
    }
}
