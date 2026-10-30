package com.pokai.codync.android.ui

import android.content.Context
import com.pokai.codync.design.GlassConfig
import androidx.compose.ui.unit.dp

/**
 * App-local appearance and notification preferences survive process death and cold starts.
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

    var glassConfig: GlassConfig
        get() = GlassConfig(
            blurRadius = prefs.getFloat(KEY_GLASS_BLUR, 16f).dp,
            tintAlpha = prefs.getFloat(KEY_GLASS_TINT, 0.52f),
            refractionStrength = prefs.getFloat(KEY_GLASS_REFRACTION, 0.78f),
            refractionHeight = prefs.getFloat(KEY_GLASS_EDGE_HEIGHT, 0.26f),
            refractionDisplacement = prefs.getFloat(KEY_GLASS_DISPLACEMENT, 22f).dp,
            specular = prefs.getFloat(KEY_GLASS_SPECULAR, 0.46f),
            noise = prefs.getFloat(KEY_GLASS_NOISE, 0.035f),
            quality = prefs.getFloat(KEY_GLASS_QUALITY, 0.38f),
        )
        set(value) {
            prefs.edit()
                .putFloat(KEY_GLASS_BLUR, value.blurRadius.value)
                .putFloat(KEY_GLASS_TINT, value.tintAlpha)
                .putFloat(KEY_GLASS_REFRACTION, value.refractionStrength)
                .putFloat(KEY_GLASS_EDGE_HEIGHT, value.refractionHeight)
                .putFloat(KEY_GLASS_DISPLACEMENT, value.refractionDisplacement.value)
                .putFloat(KEY_GLASS_SPECULAR, value.specular)
                .putFloat(KEY_GLASS_NOISE, value.noise)
                .putFloat(KEY_GLASS_QUALITY, value.quality)
                .apply()
        }

    var welcomeSeen: Boolean
        get() = prefs.getBoolean(KEY_WELCOME_SEEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_WELCOME_SEEN, value).apply()
        }

    var haptics: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTICS, value).apply()
        }

    var workspaces: Set<String>
        get() = prefs.getStringSet(KEY_WORKSPACES, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_WORKSPACES, value).apply()
        }

    companion object {
        private const val PREFS = "codync_prefs"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_WELCOME_SEEN = "welcome_seen"
        private const val KEY_PUSH = "push"
        private const val KEY_REPLIES = "replies"
        private const val KEY_NEEDS = "needs_you"
        private const val KEY_ANALYTICS = "analytics"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_WORKSPACES = "workspaces"
        private const val KEY_GLASS_BLUR = "glass_blur"
        private const val KEY_GLASS_TINT = "glass_tint"
        private const val KEY_GLASS_REFRACTION = "glass_refraction"
        private const val KEY_GLASS_EDGE_HEIGHT = "glass_edge_height"
        private const val KEY_GLASS_DISPLACEMENT = "glass_displacement"
        private const val KEY_GLASS_SPECULAR = "glass_specular"
        private const val KEY_GLASS_NOISE = "glass_noise"
        private const val KEY_GLASS_QUALITY = "glass_quality"
    }
}
