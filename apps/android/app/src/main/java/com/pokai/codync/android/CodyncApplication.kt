package com.pokai.codync.android

import android.app.Application
import com.clerk.api.Clerk

class CodyncApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BuildConfig.CLERK_PUBLISHABLE_KEY.takeIf(String::isNotBlank)?.let { key ->
            Clerk.initialize(this, publishableKey = key)
        }
    }
}
