package com.example.drushtiai

import android.content.Context

/**
 * Maps Supabase GoTrue errors (e.g. email rate limit) to short user-facing copy for toasts.
 * Debug builds append the raw server message so you can match it in the Supabase Auth logs.
 */
internal fun Context.messageForAuthFailure(throwable: Throwable): String {
    val haystack = buildString {
        var t: Throwable? = throwable
        while (t != null) {
            t.message?.let { append(it).append(' ') }
            t = t.cause
        }
    }.lowercase()
    val rawForDebug = generateSequence(throwable) { it.cause }
        .mapNotNull { it.message?.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(" → ")
    // Walk the cause chain to catch wrapped IOExceptions from Ktor
    val isNetworkError = generateSequence(throwable) { it.cause }.any { t ->
        t is java.io.IOException ||
            t is java.net.ConnectException ||
            t is java.net.SocketTimeoutException ||
            t is java.net.UnknownHostException
    } || haystack.contains("unable to resolve host") ||
        haystack.contains("failed to connect") ||
        haystack.contains("connection refused") ||
        haystack.contains("network is unreachable") ||
        haystack.contains("software caused connection abort")

    val userExists = haystack.contains("already registered") ||
        haystack.contains("already been registered") ||
        haystack.contains("user already exists") ||
        haystack.contains("email address is already") ||
        haystack.contains("email already registered") ||
        (haystack.contains("duplicate") && haystack.contains("user"))
    val rateLimited = haystack.contains("rate limit") ||
        haystack.contains("too many requests") ||
        haystack.contains("email rate") ||
        haystack.contains("over_email_send_rate_limit") ||
        (haystack.contains("429") && haystack.contains("email"))
    val main = when {
        isNetworkError -> getString(R.string.auth_error_network)
        userExists -> getString(R.string.auth_error_user_exists)
        rateLimited -> getString(R.string.auth_error_email_rate_limited)
        else -> throwable.message?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.auth_error_generic)
    }
    return if (BuildConfig.DEBUG && rawForDebug.isNotBlank()) {
        "$main\n\n[Debug] $rawForDebug"
    } else {
        main
    }
}
