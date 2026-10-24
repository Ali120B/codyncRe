package com.pokai.codync.android

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.pokai.codync.android.ui.ThemeMode
import com.clerk.api.Clerk
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : AppCompatActivity() {
    private var pairingLink by mutableStateOf<String?>(null)
    private var botLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply persisted theme BEFORE inflate so splash/window aren't white in dark mode.
        applyStoredNightMode()
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleClerkCallback(intent)
        pairingLink = intent?.dataString
        botLink = intent?.dataString?.takeIf { com.pokai.codync.android.ui.BotLink.parse(it) != null }
        setContent {
            CodyncApp(
                pairingLink = pairingLink,
                botLink = botLink,
                onPairingLinkConsumed = { pairingLink = null },
                onBotLinkConsumed = { botLink = null },
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleClerkCallback(intent)
        pairingLink = intent.dataString
        botLink = intent.dataString?.takeIf { com.pokai.codync.android.ui.BotLink.parse(it) != null }
    }

    private fun handleClerkCallback(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (BuildConfig.CLERK_PUBLISHABLE_KEY.isNotBlank() && uri.scheme == "clerk") {
            lifecycleScope.launch { Clerk.auth.handle(uri) }
        }
    }

    private fun applyStoredNightMode() {
        val prefs = getSharedPreferences("codync_prefs", MODE_PRIVATE)
        val mode = ThemeMode.entries.getOrElse(prefs.getInt("theme_mode", ThemeMode.System.ordinal)) {
            ThemeMode.System
        }
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }
}
