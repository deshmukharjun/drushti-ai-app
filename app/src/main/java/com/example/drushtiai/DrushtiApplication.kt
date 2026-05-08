package com.example.drushtiai

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DrushtiApplication : Application() {

    /**
     * Application-wide coroutine scope that survives activity and fragment lifecycles.
     * Use this (with NonCancellable if needed) for any operation that must complete
     * even if the user navigates away — e.g. profile saves, exam inserts.
     * SupervisorJob means one failed child never cancels the others.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        SupabaseHelper.init()
        GuestSession.init(this)
    }
}
