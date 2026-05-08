package com.example.drushtiai

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.drushtiai.repo.AuthRepository
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private val authRepo = AuthRepository()

    private var isSignupMode = false
    private val authRequestInFlight = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val configWarning = findViewById<TextView>(R.id.tvConfigWarning)
        configWarning.visibility =
            if (SupabaseHelper.isConfigured()) View.GONE else View.VISIBLE

        if (GuestSession.isGuest) {
            goHome()
            return
        }

        lifecycleScope.launch {
            // initSession() safely loads the stored session. If the token is revoked
            // (e.g. Supabase project was paused/reset) it wipes local cache and returns
            // false instead of crashing or leaving the user stuck on a broken session.
            if (authRepo.initSession()) {
                goHome()
                return@launch
            }
            bindAuthUi()
        }
    }

    private fun bindAuthUi() {
        val toggle = findViewById<MaterialButtonToggleGroup>(R.id.toggleAuthMode)
        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirm)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirm = findViewById<TextInputEditText>(R.id.etConfirm)
        val btnSubmit = findViewById<MaterialButton>(R.id.btnAuthSubmit)
        val progress = findViewById<ProgressBar>(R.id.progressAuth)

        fun applyMode(signup: Boolean) {
            isSignupMode = signup
            tilName.visibility = if (signup) View.VISIBLE else View.GONE
            tilConfirm.visibility = if (signup) View.VISIBLE else View.GONE
            btnSubmit.text = getString(if (signup) R.string.sign_up else R.string.login)
        }

        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.btnModeSignup -> applyMode(true)
                R.id.btnModeLogin -> applyMode(false)
            }
        }
        applyMode(false)

        findViewById<MaterialButton>(R.id.btnGuestLogin).setOnClickListener {
            GuestSession.setGuest(this, true)
            goHome()
        }

        btnSubmit.setOnClickListener {
            if (!SupabaseHelper.isReady()) {
                Toast.makeText(this, R.string.supabase_config_hint, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val email = etEmail.text?.toString()?.trim().orEmpty()
            val password = etPassword.text?.toString().orEmpty()
            if (email.isEmpty() || password.length < 6) {
                Toast.makeText(this, "Enter a valid email and password (6+ characters).", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isSignupMode) {
                val confirm = etConfirm.text?.toString().orEmpty()
                if (confirm != password) {
                    Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val name = etName.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Enter your full name.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            if (!authRequestInFlight.compareAndSet(false, true)) {
                Toast.makeText(this, R.string.auth_please_wait, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progress.visibility = View.VISIBLE
            btnSubmit.isEnabled = false
            lifecycleScope.launch {
                try {
                    if (isSignupMode) {
                        val name = etName.text?.toString()?.trim().orEmpty()
                        authRepo.signUp(email, password, name)
                        if (authRepo.isLoggedIn()) {
                            Toast.makeText(
                                this@MainActivity,
                                R.string.auth_signup_success_signed_in,
                                Toast.LENGTH_SHORT
                            ).show()
                            goHome()
                        } else {
                            Toast.makeText(
                                this@MainActivity,
                                R.string.auth_signup_success_confirm_email,
                                Toast.LENGTH_LONG
                            ).show()
                            toggle.check(R.id.btnModeLogin)
                            applyMode(false)
                        }
                    } else {
                        authRepo.signIn(email, password)
                        goHome()
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        messageForAuthFailure(e),
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    authRequestInFlight.set(false)
                    progress.visibility = View.GONE
                    btnSubmit.isEnabled = true
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
