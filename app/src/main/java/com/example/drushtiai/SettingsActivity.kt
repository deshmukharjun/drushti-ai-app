package com.example.drushtiai

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.example.drushtiai.repo.AuthRepository
import com.example.drushtiai.repo.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private val authRepo = AuthRepository()
    private val profileRepo = ProfileRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        val tvDisplayName = findViewById<TextView>(R.id.tvProfileDisplayName)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        val tvMemberSince = findViewById<TextView>(R.id.tvMemberSince)
        val tvVersion = findViewById<TextView>(R.id.tvAppVersion)
        val etName = findViewById<TextInputEditText>(R.id.etDisplayName)

        tvVersion.text = getString(R.string.profile_version_label) + " " + BuildConfig.VERSION_NAME

        if (GuestSession.isGuest) {
            tvDisplayName.text = "Guest"
            tvEmail.text = "—"
            tvMemberSince.text = "—"
            etName.setText("")
            etName.isEnabled = false
            findViewById<MaterialButton>(R.id.btnSaveProfile).isEnabled = false
            findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
                GuestSession.setGuest(this, false)
                goLogin()
            }
            return
        }

        if (!SupabaseHelper.isReady()) {
            Toast.makeText(this, R.string.supabase_config_hint, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        lifecycleScope.launch {
            // Use safeCurrentUserId() instead of currentUserId() — a failed DB call
            // (e.g. profiles INSERT denied by RLS) can cause the SDK to silently clear
            // the in-memory session. safeCurrentUserId() re-validates via awaitInitialization()
            // before giving up and sending the user back to login.
            val uid = authRepo.safeCurrentUserId()
            if (uid == null) {
                goLogin()
                return@launch
            }
            profileRepo.ensureProfileSyncedFromMetadataBestEffort(
                uid,
                authRepo.currentUserMetadataFullName()
            )
            tvEmail.text = authRepo.currentUserEmail().orEmpty()
            val profile = profileRepo.getProfileOrNull(uid)
            val display = profile?.fullName?.trim()?.takeIf { it.isNotEmpty() }
                ?: authRepo.currentUserMetadataFullName()
                ?: authRepo.currentUserEmail()?.substringBefore("@").orEmpty()
            tvDisplayName.text = display.ifEmpty { getString(R.string.profile_fallback_name) }
            etName.setText(profile?.fullName.orEmpty())

            val created = authRepo.currentUserCreatedAtDate()
            tvMemberSince.text = if (created != null) {
                getString(R.string.profile_member_since) + " " + created
            } else {
                ""
            }
        }

        val btnSave = findViewById<MaterialButton>(R.id.btnSaveProfile)
        btnSave.setOnClickListener {
            val uid = authRepo.currentUserId() ?: run {
                goLogin()
                return@setOnClickListener
            }
            val name = etName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter your full name.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false

            // Use applicationScope so this save survives if the user navigates away before
            // it completes. NonCancellable is already applied inside updateDisplayName().
            val appScope = (application as DrushtiApplication).applicationScope
            appScope.launch {
                var authUpdateOk = false
                try {
                    withContext(NonCancellable) {
                        authRepo.updateUserFullNameMetadata(name)
                    }
                    authUpdateOk = true
                } catch (e: Exception) {
                    val msg = messageForAuthFailure(e)
                    withContext(Dispatchers.Main) {
                        if (!isDestroyed) {
                            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                            btnSave.isEnabled = true
                        }
                    }
                    return@launch
                }

                if (authUpdateOk) {
                    try {
                        profileRepo.updateDisplayName(uid, name)
                    } catch (e: Exception) {
                        val msg = if (e.indicatesMissingSupabaseTable("profiles")) {
                            getString(R.string.supabase_profile_saved_auth_only)
                        } else {
                            e.message ?: "Database save failed"
                        }
                        withContext(Dispatchers.Main) {
                            if (!isDestroyed) Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
                        }
                        // Auth metadata was saved even if DB failed — still consider partial success
                    }
                    withContext(Dispatchers.Main) {
                        if (!isDestroyed) {
                            tvDisplayName.text = name
                            Toast.makeText(applicationContext, "Profile updated.", Toast.LENGTH_SHORT).show()
                            btnSave.isEnabled = true
                        }
                    }
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            lifecycleScope.launch {
                try {
                    authRepo.signOut()
                } catch (_: Exception) {
                    // Best-effort network signout — still clear locally
                    authRepo.nukeLocalSession()
                }
                goLogin()
            }
        }
    }

    private fun goLogin() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
