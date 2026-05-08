package com.example.drushtiai.repo

import android.util.Log
import com.example.drushtiai.BuildConfig
import com.example.drushtiai.SupabaseHelper
import io.github.jan.supabase.gotrue.SignOutScope
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

class AuthRepository {
    private val auth get() = SupabaseHelper.client.auth

    // ─────────────────────────────────────────────────────────────────────────
    // Session lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Safe session initializer — call once at app start (MainActivity.onCreate).
     *
     * Wraps awaitInitialization() in a try/catch so that a revoked refresh token
     * (left over from a paused Supabase project) is caught, the local cache wiped,
     * and false returned instead of leaving a poisonous session in memory.
     *
     * Returns true only when a valid, usable session is confirmed in memory.
     */
    suspend fun initSession(): Boolean {
        if (!SupabaseHelper.isReady()) return false
        return withContext(Dispatchers.IO) {
            try {
                auth.awaitInitialization()
                auth.currentSessionOrNull() != null
            } catch (e: Exception) {
                Log.w("AuthRepo", "initSession: session restore failed — nuking. ${e.message}")
                nukeLocalSession()
                false
            }
        }
    }

    /**
     * Validates the user ID is available and the session is still live.
     *
     * Unlike currentUserId() (which is purely in-memory), this re-checks
     * SDK state via awaitInitialization(). Use this in any Activity guard
     * that runs after a navigation transition, because a failed DB call can
     * trigger the Supabase SDK to silently clear the in-memory session without
     * throwing an exception visible to caller code.
     *
     * Returns null (and nukes local cache) if the session is unrecoverable.
     */
    suspend fun safeCurrentUserId(): String? = withContext(Dispatchers.IO) {
        // Fast path: session is healthy
        val immediateId = auth.currentUserOrNull()?.id
        if (immediateId != null) return@withContext immediateId

        // Slow path: session may have been cleared by an SDK internal handler.
        // Try to re-confirm initialization state before giving up.
        try {
            auth.awaitInitialization()
            auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            Log.w("AuthRepo", "safeCurrentUserId: unrecoverable, nuking. ${e.message}")
            nukeLocalSession()
            null
        }
    }

    /**
     * Nuclear logout — clears the local session cache without a network call.
     * SignOutScope.LOCAL never contacts the server, so it works even when
     * the Supabase project is paused or unreachable.
     */
    suspend fun nukeLocalSession() = withContext(Dispatchers.IO) {
        try {
            auth.signOut(SignOutScope.LOCAL)
        } catch (e: Exception) {
            Log.w("AuthRepo", "nukeLocalSession: ${e.message}")
        }
    }

    /**
     * Lightweight connectivity pre-check against the Supabase project URL.
     * Returns false on DNS failure, connection refused, or any I/O error.
     * Use this to show "Service temporarily unavailable" instead of "Wrong password".
     */
    suspend fun isProjectReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL(BuildConfig.SUPABASE_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 4_000
            conn.readTimeout = 4_000
            conn.requestMethod = "HEAD"
            conn.connect()
            val code = conn.responseCode
            conn.disconnect()
            code < 500
        } catch (_: Exception) {
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auth operations
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun signIn(email: String, password: String) = withContext(Dispatchers.IO) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /**
     * Signs up a new user.
     *
     * Always nukes any locally cached session first — even if currentSessionOrNull()
     * looks null — because the SDK can hold poisonous internal state from a paused
     * project that blocks a fresh signUpWith() attempt without surfacing as an error.
     * The nuke is a cheap local operation (no network) and is always safe.
     */
    suspend fun signUp(email: String, password: String, fullName: String) = withContext(Dispatchers.IO) {
        // Unconditional pre-nuke: clears any ghost/stale session so the SDK
        // starts the signup request with no existing token attached.
        nukeLocalSession()
        auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("full_name", fullName) }
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        auth.signOut()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors (in-memory, no network)
    // ─────────────────────────────────────────────────────────────────────────

    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    fun currentUserEmail(): String? = auth.currentUserOrNull()?.email

    /** Display name stored in Auth user_metadata (set at sign-up). */
    fun currentUserMetadataFullName(): String? {
        val meta = auth.currentUserOrNull()?.userMetadata ?: return null
        val el = meta["full_name"] as? JsonPrimitive ?: return null
        return el.content.takeIf { it.isNotBlank() }
    }

    /** ISO date prefix for display (account created). */
    fun currentUserCreatedAtDate(): String? {
        val s = auth.currentUserOrNull()?.createdAt?.toString() ?: return null
        return s.take(10).takeIf { it.length == 10 }
    }

    fun isLoggedIn(): Boolean = auth.currentSessionOrNull() != null

    suspend fun updateUserFullNameMetadata(fullName: String) = withContext(Dispatchers.IO) {
        auth.updateUser { data { put("full_name", fullName) } }
    }
}
