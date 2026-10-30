package com.pokai.codync.android

import android.app.Application
import com.clerk.api.Clerk
import com.google.firebase.FirebaseApp

class CodyncApplication : Application() {
    val hostSession: AndroidHostSession by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidHostSession(applicationContext)
    }

    /** True when this install already paired a computer, i.e. it is not a fresh install. */
    fun hasExistingSetup(): Boolean =
        getSharedPreferences("codync_host_session", MODE_PRIVATE)
            .getString("paired_computers", null)?.isNotBlank() == true

    override fun onCreate() {
        super.onCreate()
        AppPresence.clear(this)
        // Without the private project config the app remains usable; push registers once the
        // Codync Firebase project config is installed for this flavor.
        FirebaseApp.initializeApp(this)
        BuildConfig.CLERK_PUBLISHABLE_KEY.takeIf(String::isNotBlank)?.let { key ->
            Clerk.initialize(this, publishableKey = key)
        }
    }
}
