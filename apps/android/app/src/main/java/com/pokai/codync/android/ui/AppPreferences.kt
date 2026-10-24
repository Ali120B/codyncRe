package com.pokai.codync.android.ui

import android.content.Context

/**
 * Survives process death / app reopen. Theme + notification toggles live here so Settings
 * isn't forgotten every cold start.
 */
class AppPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = ThemeMode.entries.getOrElse(prefs.getInt(KEY_THEME, ThemeMode.System.ordinal)) { ThemeMode.System }
        set(value) {
            prefs.edit().putInt(KEY_THEME, value.ordinal).apply()
        }

    var push: Boolean
        get() = prefs.getBoolean(KEY_PUSH, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PUSH, value).apply()
        }

    var replies: Boolean
        get() = prefs.getBoolean(KEY_REPLIES, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REPLIES, value).apply()
        }

    var needsYou: Boolean
        get() = prefs.getBoolean(KEY_NEEDS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NEEDS, value).apply()
        }

    var analytics: Boolean
        get() = prefs.getBoolean(KEY_ANALYTICS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ANALYTICS, value).apply()
        }

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    companion object {
        private const val PREFS = "codync_prefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_PUSH = "push"
        private const val KEY_REPLIES = "replies"
        private const val KEY_NEEDS = "needs_you"
        private const val KEY_ANALYTICS = "analytics"
        private const val KEY_HAPTICS = "haptics"
    }
}
