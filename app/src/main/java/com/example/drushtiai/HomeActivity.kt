package com.example.drushtiai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.drushtiai.data.ExamRow
import com.example.drushtiai.repo.AuthRepository
import com.example.drushtiai.repo.ExamRepository
import com.example.drushtiai.repo.ProfileRepository
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private val authRepo = AuthRepository()
    private val examRepo = ExamRepository()
    private val profileRepo = ProfileRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val isGuest = GuestSession.isGuest
        if (!isGuest && (!SupabaseHelper.isReady() || !authRepo.isLoggedIn())) {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_home

        findViewById<MaterialButton>(R.id.btnStartExam).setOnClickListener {
            startActivity(Intent(this, NewExamActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.fabNewExam).setOnClickListener {
            startActivity(Intent(this, NewExamActivity::class.java))
        }

        findViewById<android.widget.ImageView>(R.id.ivProfile).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_notifications -> {
                    startActivity(Intent(this, AlertsActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        loadDashboard()
    }

    override fun onResume() {
        super.onResume()
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_home
        loadDashboard()
    }

    private fun loadDashboard() {
        val tvTitle = findViewById<TextView>(R.id.tvHomeTitle)
        val rv = findViewById<RecyclerView>(R.id.rvDashboardExams)
        val empty = findViewById<TextView>(R.id.tvDashboardExamsEmpty)

        if (GuestSession.isGuest) {
            tvTitle.text = "Hello, Guest"
            empty.visibility = View.VISIBLE
            rv.visibility = View.GONE
            return
        }

        lifecycleScope.launch {
            val uid = authRepo.currentUserId() ?: return@launch
            profileRepo.ensureProfileSyncedFromMetadataBestEffort(
                uid,
                authRepo.currentUserMetadataFullName()
            )
            val profile = profileRepo.getProfileOrNull(uid)
            val email = authRepo.currentUserEmail()
            val name = profile?.fullName?.trim()?.takeIf { it.isNotEmpty() }
                ?: authRepo.currentUserMetadataFullName()
            val short = name ?: email?.substringBefore("@") ?: "there"
            tvTitle.text = "Hello, $short"

            val exams: List<ExamRow> = try {
                examRepo.listExamsForUser(uid)
            } catch (_: Exception) {
                emptyList()
            }
            if (exams.isEmpty()) {
                empty.visibility = View.VISIBLE
                rv.visibility = View.GONE
            } else {
                empty.visibility = View.GONE
                rv.visibility = View.VISIBLE
                rv.layoutManager = LinearLayoutManager(this@HomeActivity)
                rv.adapter = PastExamsAdapter(exams, R.layout.item_exam_list_row) { exam ->
                    val id = exam.id ?: return@PastExamsAdapter
                    startActivity(ExamDetailActivity.intent(this@HomeActivity, id))
                }
            }
        }
    }
}
