package com.pokai.codync.android

import android.app.Activity
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.ui.AppPreferences
import com.pokai.codync.android.ui.AppTab
import com.pokai.codync.android.ui.BotLink
import com.pokai.codync.android.ui.TabsScreen
import com.pokai.codync.android.ui.ThemeMode
import com.pokai.codync.android.ui.chat.LiveChatScreen
import com.pokai.codync.android.ui.debug.FakeHostScreen
import com.pokai.codync.android.ui.debug.GlassLabScreen
import com.pokai.codync.android.ui.pairing.PairingScreen
import com.pokai.codync.android.ui.search.CommandDeckScreen
import com.pokai.codync.android.ui.workbench.BotDetailsScreen
import com.pokai.codync.android.ui.workbench.WorkbenchHomeScreen
import com.pokai.codync.transport.PairUrl
import com.pokai.codync.design.GlassConfig
import com.pokai.codync.design.LocalGlassConfig
import com.pokai.codync.design.theme.CodyncTheme
import dev.chrisbanes.haze.rememberHazeState

private object Routes {
    const val Tabs = "tabs"
    const val Search = "search"
    const val GlassLab = "glass-lab"
    const val FakeHost = "fake-host"
    const val Pairing = "pairing"
    const val Workbench = "workbench"
    const val BotDetails = "bot-details/{botId}"
    const val Chat = "chat/{botId}/{chatId}?messageId={messageId}"

    fun chat(botId: String, chatId: String, messageId: String?): String {
        val base = "chat/${Uri.encode(botId)}/${Uri.encode(chatId)}"
        return if (messageId.isNullOrBlank()) base else "$base?messageId=${Uri.encode(messageId)}"
    }

    fun botDetails(botId: String) = "bot-details/${Uri.encode(botId)}"
}

private val FastIn = tween<Float>(durationMillis = 140)
private val FastOut = tween<Float>(durationMillis = 100)
private val FastSlide = tween<IntOffset>(durationMillis = 160)
private val SearchIn = tween<Float>(durationMillis = 220)
private val SearchSlide = tween<IntOffset>(durationMillis = 260)

@Composable
fun CodyncApp(
    pairingLink: String? = null,
    botLink: String? = null,
    onPairingLinkConsumed: () -> Unit = {},
    onBotLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val hostSession = remember { AndroidHostSession(context) }
    val accountSession = remember { AndroidAccountSession(hostSession.identity, hostSession.store, BuildConfig.CLOUD_URL) }
    var themeMode by rememberSaveable { mutableStateOf(prefs.themeMode) }
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
    val configuration = LocalConfiguration.current

    LaunchedEffect(pairingLink) {
        if (!pairingLink.isNullOrBlank() && PairUrl.parse(pairingLink) != null) {
            nav.navigate(Routes.Pairing) { launchSingleTop = true }
        }
    }

    LaunchedEffect(botLink) {
        val link = BotLink.parse(botLink) ?: return@LaunchedEffect
        val computer = hostSession.store.snapshot.value.computers.firstOrNull { it.id == link.computerId }
        if (computer == null) {
            // The notification may arrive before this phone has paired with that computer.
            nav.navigate(Routes.Pairing) { launchSingleTop = true }
        } else {
            if (hostSession.store.snapshot.value.connection.computer?.id != computer.id) {
                hostSession.store.connect(computer)
            }
            nav.navigate(Routes.chat(link.botId, link.chatId ?: "main", link.messageId)) {
                launchSingleTop = true
            }
        }
        onBotLinkConsumed()
    }

    fun applyTheme(mode: ThemeMode) {
        themeMode = mode
        prefs.themeMode = mode
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }

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
                    enterTransition = {
                        fadeIn(FastIn) + slideInHorizontally(FastSlide) { it / 14 }
                    },
                    exitTransition = { fadeOut(FastOut) },
                    popEnterTransition = { fadeIn(FastIn) },
                    popExitTransition = {
                        fadeOut(FastOut) + slideOutHorizontally(FastSlide) { it / 14 }
                    },
                ) {
                    composable(Routes.Tabs) {
                        TabsScreen(
                            fake = fake,
                            session = hostSession.store,
                            account = accountSession,
                            hazeState = hazeState,
                            tab = selectedTab,
                            onTab = { selectedTab = it },
                            themeMode = themeMode,
                            onThemeMode = ::applyTheme,
                            prefs = prefs,
                            onOpenChat = { botId, chatId, messageId ->
                                nav.navigate(Routes.chat(botId, chatId, messageId))
                            },
                            onOpenSearch = { nav.navigate(Routes.Search) },
                            onOpenGlassLab = { nav.navigate(Routes.GlassLab) },
                            onOpenFakeHost = { nav.navigate(Routes.FakeHost) },
                            onOpenPairing = { nav.navigate(Routes.Pairing) },
                            onOpenWorkbench = { nav.navigate(Routes.Workbench) },
                            onOpenBotDetails = { id -> nav.navigate(Routes.botDetails(id)) },
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
                        enterTransition = {
                            fadeIn(tween(165)) + slideInHorizontally(
                                tween(195, easing = LinearOutSlowInEasing),
                            ) { it / 5 }
                        },
                        exitTransition = {
                            fadeOut(tween(115)) + slideOutHorizontally(
                                tween(150, easing = FastOutSlowInEasing),
                            ) { -it / 24 }
                        },
                        popEnterTransition = {
                            fadeIn(tween(155)) + slideInHorizontally(
                                tween(185, easing = LinearOutSlowInEasing),
                            ) { -it / 14 }
                        },
                        popExitTransition = {
                            fadeOut(tween(135)) + slideOutHorizontally(
                                tween(175, easing = FastOutSlowInEasing),
                            ) { it / 5 }
                        },
                    ) { entry ->
                        val botId = entry.arguments?.getString("botId").orEmpty()
                        LiveChatScreen(
                            session = hostSession.store,
                            botId = botId,
                            chatId = entry.arguments?.getString("chatId").orEmpty(),
                            initialMessageId = entry.arguments?.getString("messageId"),
                            hazeState = hazeState,
                            onBack = { nav.popBackStack() },
                            onOpenDetails = { id -> nav.navigate(Routes.botDetails(id)) },
                            onOpenChat = { targetBot, targetChat -> nav.navigate(Routes.chat(targetBot, targetChat, null)) },
                            onOpenThread = { targetBot, rootId -> nav.navigate(Routes.chat(targetBot, rootId, null)) },
                        )
                    }
                    composable(
                        Routes.BotDetails,
                        arguments = listOf(navArgument("botId") { type = NavType.StringType }),
                        enterTransition = {
                            fadeIn(tween(165)) + slideInHorizontally(
                                tween(195, easing = LinearOutSlowInEasing),
                            ) { it / 5 }
                        },
                        exitTransition = {
                            fadeOut(tween(115)) + slideOutHorizontally(
                                tween(150, easing = FastOutSlowInEasing),
                            ) { -it / 24 }
                        },
                        popEnterTransition = {
                            fadeIn(tween(155)) + slideInHorizontally(
                                tween(185, easing = LinearOutSlowInEasing),
                            ) { -it / 14 }
                        },
                        popExitTransition = {
                            fadeOut(tween(135)) + slideOutHorizontally(
                                tween(175, easing = FastOutSlowInEasing),
                            ) { it / 5 }
                        },
                    ) { entry ->
                        val id = entry.arguments?.getString("botId").orEmpty()
                        BotDetailsScreen(
                            session = hostSession.store,
                            botId = id,
                            onBack = { nav.popBackStack() },
                            onOpenChat = { botId, chatId -> nav.navigate(Routes.chat(botId, chatId, null)) },
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
                    composable(Routes.Pairing) {
                        PairingScreen(
                            store = hostSession.store,
                            deviceName = hostSession.identity.deviceName,
                            initialLink = pairingLink,
                            onDeepLinkConsumed = onPairingLinkConsumed,
                            onBack = { nav.popBackStack() },
                            onPaired = { nav.popBackStack() },
                        )
                    }
                    composable(
                        Routes.Search,
                        enterTransition = {
                            fadeIn(SearchIn) + slideInVertically(SearchSlide) { it / 5 }
                        },
                        exitTransition = {
                            fadeOut(tween(120)) + slideOutVertically(tween(160)) { it / 8 }
                        },
                        popEnterTransition = { fadeIn(FastIn) },
                        popExitTransition = {
                            fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 6 }
                        },
                    ) {
                        CommandDeckScreen(
                            session = hostSession.store,
                            onBack = { nav.popBackStack() },
                            onOpenChat = { botId, chatId, messageId ->
                                nav.navigate(Routes.chat(botId, chatId, messageId))
                            },
                            onOpenSettings = {
                                selectedTab = AppTab.Settings
                                nav.popBackStack(Routes.Tabs, inclusive = false)
                            },
                            onOpenGlassLab = { nav.navigate(Routes.GlassLab) },
                            onOpenPairing = { nav.navigate(Routes.Pairing) },
                            onOpenWorkbench = { nav.navigate(Routes.Workbench) },
                        )
                    }
                    composable(Routes.Workbench) {
                        WorkbenchHomeScreen(
                            session = hostSession.store,
                            onBack = { nav.popBackStack() },
                            onOpenBotDetails = { id -> nav.navigate(Routes.botDetails(id)) },
                        )
                    }
                }
            }
        }
    }
}
