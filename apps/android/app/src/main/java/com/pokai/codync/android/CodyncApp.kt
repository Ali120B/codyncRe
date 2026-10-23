package com.pokai.codync.android

import android.app.Activity
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.ui.AppTab
import com.pokai.codync.android.ui.TabsScreen
import com.pokai.codync.android.ui.ThemeMode
import com.pokai.codync.android.ui.chat.ChatScreen
import com.pokai.codync.android.ui.debug.FakeHostScreen
import com.pokai.codync.android.ui.debug.GlassLabScreen
import com.pokai.codync.android.ui.search.CommandDeckScreen
import com.pokai.codync.design.GlassConfig
import com.pokai.codync.design.LocalGlassConfig
import com.pokai.codync.design.theme.CodyncTheme
import dev.chrisbanes.haze.rememberHazeState

private object Routes {
    const val Tabs = "tabs"
    const val Search = "search"
    const val GlassLab = "glass-lab"
    const val FakeHost = "fake-host"
    const val Chat = "chat/{botId}/{chatId}?messageId={messageId}"

    fun chat(botId: String, chatId: String, messageId: String?): String {
        val base = "chat/${Uri.encode(botId)}/${Uri.encode(chatId)}"
        return if (messageId.isNullOrBlank()) base else "$base?messageId=${Uri.encode(messageId)}"
    }
}

@Composable
fun CodyncApp() {
    var themeMode by rememberSaveable { mutableStateOf(ThemeMode.System) }
    var glassConfig by remember { mutableStateOf(GlassConfig()) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Bots) }
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val fake = remember { FakeHostState() }
    val hazeState = rememberHazeState()
    val nav = rememberNavController()
    val context = LocalContext.current
    val view = LocalView.current

    val configuration = LocalConfiguration.current
    DisposableEffect(dark, configuration) {
        val activity = context as? Activity
        if (activity != null) {
            activity.window.decorView.post {
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
        onDispose { }
    }

    CodyncTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalGlassConfig provides glassConfig) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(CodyncTheme.colors.background),
            ) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.Tabs,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Routes.Tabs) {
                        TabsScreen(
                            fake = fake,
                            hazeState = hazeState,
                            tab = selectedTab,
                            onTab = { selectedTab = it },
                            themeMode = themeMode,
                            onThemeMode = { themeMode = it },
                            onOpenChat = { botId, chatId, messageId ->
                                nav.navigate(Routes.chat(botId, chatId, messageId))
                            },
                            onOpenSearch = { nav.navigate(Routes.Search) },
                            onOpenGlassLab = { nav.navigate(Routes.GlassLab) },
                            onOpenFakeHost = { nav.navigate(Routes.FakeHost) },
                        )
                    }
                    composable(
                        route = Routes.Chat,
                        arguments = listOf(
                            navArgument("botId") { type = NavType.StringType },
                            navArgument("chatId") { type = NavType.StringType },
                            navArgument("messageId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        ChatScreen(
                            fake = fake,
                            hazeState = hazeState,
                            botId = entry.arguments?.getString("botId").orEmpty(),
                            chatId = entry.arguments?.getString("chatId").orEmpty(),
                            initialMessageId = entry.arguments?.getString("messageId"),
                            onBack = { nav.popBackStack() },
                            onOpenChat = { botId, chatId, messageId ->
                                nav.navigate(Routes.chat(botId, chatId, messageId))
                            },
                            onOpenFakeHost = { nav.navigate(Routes.FakeHost) },
                        )
                    }
                    composable(Routes.GlassLab) {
                        GlassLabScreen(
                            config = glassConfig,
                            onConfig = { glassConfig = it },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.FakeHost) {
                        FakeHostScreen(
                            fake = fake,
                            onBack = { nav.popBackStack() },
                            onOpenGlassLab = { nav.navigate(Routes.GlassLab) },
                        )
                    }
                    composable(Routes.Search) {
                        CommandDeckScreen(
                            fake = fake,
                            onBack = { nav.popBackStack() },
                            onOpenChat = { botId, chatId, messageId ->
                                nav.navigate(Routes.chat(botId, chatId, messageId))
                            },
                            onOpenSettings = {
                                selectedTab = AppTab.Settings
                                nav.popBackStack(Routes.Tabs, inclusive = false)
                            },
                            onOpenGlassLab = { nav.navigate(Routes.GlassLab) },
                            onOpenFakeHost = { nav.navigate(Routes.FakeHost) },
                        )
                    }
                }
            }
        }
    }
}
