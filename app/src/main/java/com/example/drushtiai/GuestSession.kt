package com.example.drushtiai

import android.content.Context

object GuestSession {
    private const val PREFS = "drushti_guest"
    private const val KEY = "is_guest"

    var isGuest: Boolean = false
        private set

    fun init(context: Context) {
        isGuest = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY, false)
    }

    fun setGuest(context: Context, value: Boolean) {
        isGuest = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, value).apply()
    }
}
