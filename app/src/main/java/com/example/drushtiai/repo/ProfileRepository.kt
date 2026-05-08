package com.example.drushtiai.repo

import com.example.drushtiai.SupabaseHelper
import com.example.drushtiai.data.ProfileRow
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Required Supabase RLS policies for the `public.profiles` table.
 * If profile saves are failing silently, verify these exist in the Supabase dashboard:
 *
 *   ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
 *
 *   CREATE POLICY "Users can insert their own profile"
 *     ON public.profiles FOR INSERT TO authenticated
 *     WITH CHECK (auth.uid() = id);
 *
 *   CREATE POLICY "Users can select their own profile"
 *     ON public.profiles FOR SELECT TO authenticated
 *     USING (auth.uid() = id);
 *
 *   CREATE POLICY "Users can update their own profile"
 *     ON public.profiles FOR UPDATE TO authenticated
 *     USING (auth.uid() = id)
 *     WITH CHECK (auth.uid() = id);
 *
 * A missing INSERT policy is the most common cause of silent profile save failures
 * for newly created users (signUpWith succeeds, but the follow-up INSERT is 403'd).
 */
class ProfileRepository {
    private val db get() = SupabaseHelper.client

    suspend fun getProfile(userId: String): ProfileRow? = withContext(Dispatchers.IO) {
        db.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeList<ProfileRow>().firstOrNull()
    }

    suspend fun getProfileOrNull(userId: String): ProfileRow? =
        try {
            getProfile(userId)
        } catch (_: Exception) {
            null
        }

    /**
     * Creates/syncs profile row when possible. Swallows errors if `public.profiles`
     * does not exist yet (run `supabase_schema.sql` from the project root in Supabase SQL).
     */
    suspend fun ensureProfileSyncedFromMetadataBestEffort(userId: String, metadataName: String?) {
        try {
            ensureProfileRow(userId)
            syncDisplayNameFromMetadataIfEmpty(userId, metadataName)
        } catch (_: Exception) {
            // Missing table or RLS — Home still works using Auth metadata for greeting
        }
    }

    /**
     * Inserts a row if the auth trigger did not (e.g. legacy accounts or missing trigger).
     * Uses NonCancellable so a navigation event cannot orphan a partial insert.
     */
    suspend fun ensureProfileRow(userId: String) = withContext(Dispatchers.IO + NonCancellable) {
        val existing = try {
            db.from("profiles").select {
                filter { eq("id", userId) }
            }.decodeList<ProfileRow>().firstOrNull()
        } catch (_: Exception) {
            null
        }
        if (existing != null) return@withContext
        try {
            db.from("profiles").insert(ProfileRow(id = userId, fullName = null))
        } catch (_: Exception) {
            // Race with trigger, missing table, or RLS — all are non-fatal here
        }
    }

    /** If `profiles.full_name` is empty but Auth metadata has `full_name`, copy it once. */
    suspend fun syncDisplayNameFromMetadataIfEmpty(userId: String, metadataName: String?) =
        withContext(Dispatchers.IO) {
            if (metadataName.isNullOrBlank()) return@withContext
            val row = try {
                db.from("profiles").select {
                    filter { eq("id", userId) }
                }.decodeList<ProfileRow>().firstOrNull()
            } catch (_: Exception) {
                return@withContext
            } ?: return@withContext
            if (!row.fullName.isNullOrBlank()) return@withContext
            try {
                db.from("profiles").update({
                    set("full_name", metadataName)
                }) {
                    filter { eq("id", userId) }
                }
            } catch (_: Exception) { /* non-fatal */ }
        }

    /**
     * Updates display name in the profiles table.
     * NonCancellable: this write must complete even if the user navigates away
     * mid-save. Pair with applicationScope at the call site for full lifecycle safety.
     */
    suspend fun updateDisplayName(userId: String, fullName: String) =
        withContext(Dispatchers.IO + NonCancellable) {
            db.from("profiles").update({
                set("full_name", fullName)
            }) {
                filter { eq("id", userId) }
            }
        }
}
