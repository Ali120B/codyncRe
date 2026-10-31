package com.pokai.codync.android

import android.app.Application
import com.clerk.api.Clerk
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

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
        initializeFirebase()
        BuildConfig.CLERK_PUBLISHABLE_KEY.takeIf(String::isNotBlank)?.let { key ->
            Clerk.initialize(this, publishableKey = key)
        }
    }

    /**
     * CI builds do not have the untracked google-services.json. The client identifiers are
     * public, so initialize the default app explicitly when no generated config is present.
     */
    private fun initializeFirebase() {
        if (FirebaseApp.getApps(this).isNotEmpty()) return
        if (runCatching { FirebaseApp.initializeApp(this) }.getOrNull() != null) return
        runCatching {
            FirebaseApp.initializeApp(
                this,
                FirebaseOptions.Builder()
                    .setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID)
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                    .build(),
            )
        }.onFailure { error ->
            android.util.Log.e("CodyncFirebase", "Firebase initialization failed", error)
        }
    }
}
