package com.pokai.codync.android

import android.content.Context
import android.content.SharedPreferences

/**
 * What the app is showing right now, persisted where the FCM service can read it. The host
 * suppresses alerts for the chat this phone reports as viewing; the FCM service additionally
 * drops alerts for Home or that exact chat only while the activity is in the foreground.
 */
object AppPresence {
    const val PREFS = "codync_prefs"
    const val KEY_FOREGROUND = "presence_foreground"
    const val KEY_SURFACE = "presence_surface"
    const val HOME = "home"
    const val OTHER = "other"

    fun chat(computerId: String?, botId: String?): String = "chat:$computerId:$botId"

    fun set(context: Context, foreground: Boolean, surface: String) {
        prefs(context).edit()
            .putBoolean(KEY_FOREGROUND, foreground)
            .putString(KEY_SURFACE, surface)
            .apply()
    }

    fun foreground(context: Context, foreground: Boolean) {
        prefs(context).edit().putBoolean(KEY_FOREGROUND, foreground).apply()
    }

    fun surface(context: Context, surface: String) {
        prefs(context).edit().putString(KEY_SURFACE, surface).apply()
    }

    /** A fresh process is never already on screen; the activity reports ON_START itself. */
    fun clear(context: Context) = set(context, foreground = false, surface = OTHER)

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
