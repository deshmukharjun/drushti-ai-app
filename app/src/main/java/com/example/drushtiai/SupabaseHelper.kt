package com.example.drushtiai

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseHelper {
    private var _client: SupabaseClient? = null

    fun init() {
        if (_client != null) return
        if (!isConfigured()) return
        _client = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
    }

    val client: SupabaseClient
        get() = _client ?: error("Supabase is not configured or init failed")

    fun isReady(): Boolean = _client != null

    fun isConfigured(): Boolean {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        return url.isNotBlank() &&
            !url.contains("YOUR_PROJECT") &&
            key.isNotBlank() &&
            key != "YOUR_ANON_KEY"
    }
}
