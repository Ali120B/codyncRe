package com.pokai.codync.android

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokai.codync.android.ui.AppPreferences
import com.pokai.codync.android.ui.AppTab
import com.pokai.codync.android.ui.BotLink
import com.pokai.codync.android.ui.TabsScreen
import com.pokai.codync.android.ui.ThemeMode
import com.pokai.codync.android.ui.WelcomeScreen
import com.pokai.codync.android.ui.chat.LiveChatScreen
import com.pokai.codync.android.ui.debug.GlassLabScreen
import com.pokai.codync.android.ui.pairing.PairingScreen
import com.pokai.codync.android.ui.roster.NewGroupScreen
import com.pokai.codync.android.ui.search.CommandDeckScreen
import com.pokai.codync.android.ui.workbench.BotDetailsScreen
import com.pokai.codync.android.ui.workbench.LiveBotEditor
import com.pokai.codync.android.ui.workbench.LiveExtensionsScreen
import com.pokai.codync.android.ui.workbench.WorkbenchHomeScreen
import com.pokai.codync.model.Bot
import com.pokai.codync.transport.PairUrl
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.pokai.codync.design.GlassConfig
import com.pokai.codync.design.LocalGlassConfig
import com.pokai.codync.design.ProvideGlassMenuHost
import com.pokai.codync.design.theme.CodyncTheme
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private object Routes {
    const val Welcome = "welcome"
    const val Tabs = "tabs"
    const val Search = "search"
    const val GlassLab = "glass-lab"
    const val Pairing = "pairing"
    const val Workbench = "workbench"
    const val Extensions = "extensions"
    const val TeamMap = "team-map"
    const val NewBot = "new-bot?computerId={computerId}"
    const val NewGroup = "new-group?computerId={computerId}"
    const val BotDetails = "bot-details/{botId}?tab={tab}"
    const val Chat = "chat/{botId}/{chatId}?messageId={messageId}"

    fun newBot(computerId: String?): String =
        if (computerId.isNullOrBlank()) "new-bot" else "new-bot?computerId=${Uri.encode(computerId)}"

    fun newGroup(computerId: String?): String =
        if (computerId.isNullOrBlank()) "new-group" else "new-group?computerId=${Uri.encode(computerId)}"

    fun chat(botId: String, chatId: String, messageId: String?): String {
        val base = "chat/${Uri.encode(botId)}/${Uri.encode(chatId)}"
        return if (messageId.isNullOrBlank()) base else "$base?messageId=${Uri.encode(messageId)}"
    }

    fun botDetails(botId: String, tab: String? = null): String {
        val route = "bot-details/${Uri.encode(botId)}"
        return if (tab.isNullOrBlank()) route else "$route?tab=${Uri.encode(tab)}"
    }
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
    oauthLink: String? = null,
    onPairingLinkConsumed: () -> Unit = {},
    onBotLinkConsumed: () -> Unit = {},
    onOAuthLinkConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val application = context.applicationContext as CodyncApplication
    val hostSession = application.hostSession
    val accountSession = remember { AndroidAccountSession(hostSession.identity, hostSession.store, BuildConfig.CLOUD_URL) }
    var themeMode by rememberSaveable { mutableStateOf(prefs.themeMode) }
    var glassConfig by remember { mutableStateOf(prefs.glassConfig) }
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.Home) }
    // Upgrade path: installs that already paired a computer skip the first-run screen.
    val firstRun = remember { !prefs.welcomeSeen && !application.hasExistingSetup() }
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val hazeState = rememberHazeState()
    val nav = rememberNavController()
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val botComputers by hostSession.store.botComputers().collectAsState(initial = emptyMap())
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val surface = when {
        route == Routes.Chat -> {
            val chatBotId = backStackEntry?.arguments?.getString("botId").orEmpty()
            AppPresence.chat(botComputers[chatBotId], chatBotId)
        }
        route == Routes.Tabs && selectedTab == AppTab.Home -> AppPresence.HOME
        else -> AppPresence.OTHER
    }
    var foreground by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    foreground = true
                    AppPresence.foreground(context, true)
                }
                Lifecycle.Event.ON_STOP -> {
                    foreground = false
                    AppPresence.foreground(context, false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(surface) { AppPresence.surface(context, surface) }

    LaunchedEffect(firstRun) {
        if (!firstRun && !prefs.welcomeSeen) prefs.welcomeSeen = true
    }

    LaunchedEffect(Unit) {
        // Ask once, up front: Android 13+ silently drops every notification without this grant.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // A killed process never cleared its last "viewing" report; the host queue must not
        // keep suppressing alerts for that old chat.
        hostSession.store.setViewing(null)
    }

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
            hostSession.store.selectComputer(computer.id)
            if (hostSession.store.snapshot.value.connections[computer.id]?.hello == null) {
                hostSession.store.connect(computer)
            }
            nav.navigate(Routes.chat(link.botId, link.chatId ?: "main", link.messageId)) {
                launchSingleTop = true
            }
        }
        onBotLinkConsumed()
    }

    LaunchedEffect(oauthLink) {
        val uri = oauthLink?.let(Uri::parse) ?: return@LaunchedEffect
        if (uri.host != "oauth" || uri.scheme !in setOf("codync", "codync-dev")) return@LaunchedEffect
        val state = uri.getQueryParameter("state")
        if (!state.isNullOrBlank()) {
            hostSession.store.call("connectorSignInFinish", buildJsonObject {
                put("state", state)
                uri.getQueryParameter("code")?.let { put("code", it) }
                uri.getQueryParameter("error_description")?.let { put("error", it) }
                    ?: uri.getQueryParameter("error")?.let { put("error", it) }
            })
        }
        onOAuthLinkConsumed()
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

    fun openChat(botId: String, chatId: String, messageId: String?) {
        // Legacy screens that still read the aggregate connection follow the bot being opened.
        hostSession.store.computerIdForBot(botId)?.let(hostSession.store::selectComputer)
        val current = nav.currentBackStackEntry
        val sameChat = nav.currentDestination?.route == Routes.Chat &&
            current?.arguments?.getString("botId") == botId &&
            current.arguments?.getString("chatId") == chatId
        if (sameChat && (messageId.isNullOrBlank() || current?.arguments?.getString("messageId") == messageId)) return
        nav.navigate(Routes.chat(botId, chatId, messageId)) { launchSingleTop = true }
    }

    fun openBotDetails(botId: String, tab: String? = null) {
        hostSession.store.computerIdForBot(botId)?.let(hostSession.store::selectComputer)
        nav.navigate(Routes.botDetails(botId, tab))
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
                val menuBackdrop = rememberLayerBackdrop()
                ProvideGlassMenuHost(backdrop = menuBackdrop) {
                NavHost(
                    navController = nav,
                    startDestination = if (firstRun) Routes.Welcome else Routes.Tabs,
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
                    composable(Routes.Welcome) {
                        WelcomeScreen(
                            onContinue = {
                                prefs.welcomeSeen = true
                                nav.navigate(Routes.Tabs) {
                                    popUpTo(Routes.Welcome) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(Routes.Tabs) {
                        TabsScreen(
                            session = hostSession.store,
                            account = accountSession,
                            hazeState = hazeState,
                            tab = selectedTab,
                            onTab = { selectedTab = it },
                            themeMode = themeMode,
                            onThemeMode = ::applyTheme,
                            prefs = prefs,
                            onOpenChat = ::openChat,
                            onOpenGlassLab = { nav.navigate(Routes.GlassLab) },
                            onOpenPairing = { nav.navigate(Routes.Pairing) },
                            onOpenWorkbench = { nav.navigate(Routes.Workbench) },
                            onOpenBotDetails = { id -> openBotDetails(id) },
                            onCreateBot = { computerId -> nav.navigate(Routes.newBot(computerId)) },
                            onCreateGroup = { computerId -> nav.navigate(Routes.newGroup(computerId)) },
                            onOpenTeamMap = { },
                            onOpenExtensions = { nav.navigate(Routes.Extensions) },
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
                            fadeIn(tween(170)) + slideInHorizontally(
                                tween(260, easing = FastOutSlowInEasing),
                            ) { it }
                        },
                        exitTransition = {
                            fadeOut(tween(150)) + slideOutHorizontally(
                                tween(220, easing = FastOutSlowInEasing),
                            ) { -it / 4 }
                        },
                        popEnterTransition = {
                            fadeIn(tween(180)) + slideInHorizontally(
                                tween(240, easing = FastOutSlowInEasing),
                            ) { -it / 3 }
                        },
                        popExitTransition = {
                            fadeOut(tween(150)) + slideOutHorizontally(
                                tween(220, easing = FastOutSlowInEasing),
                            ) { it }
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
                            onOpenDetails = { id -> openBotDetails(id) },
                            onOpenComputer = { id -> openBotDetails(id, "Computer") },
                            onOpenChat = { targetBot, targetChat -> openChat(targetBot, targetChat, null) },
                            onOpenThread = { targetBot, rootId -> openChat(targetBot, rootId, null) },
                        )
                    }
                    composable(
                        Routes.BotDetails,
                        arguments = listOf(
                            navArgument("botId") { type = NavType.StringType },
                            navArgument("tab") { type = NavType.StringType; defaultValue = "Details" },
                        ),
                        enterTransition = {
                            fadeIn(tween(180)) + slideInHorizontally(
                                tween(230, easing = FastOutSlowInEasing),
                            ) { it }
                        },
                        exitTransition = {
                            fadeOut(tween(150)) + slideOutHorizontally(
                                tween(190, easing = FastOutSlowInEasing),
                            ) { -it / 4 }
                        },
                        popEnterTransition = {
                            fadeIn(tween(180)) + slideInHorizontally(
                                tween(220, easing = FastOutSlowInEasing),
                            ) { -it / 4 }
                        },
                        popExitTransition = {
                            fadeOut(tween(150)) + slideOutHorizontally(
                                tween(200, easing = FastOutSlowInEasing),
                            ) { it }
                        },
                    ) { entry ->
                        val id = entry.arguments?.getString("botId").orEmpty()
                        BotDetailsScreen(
                            session = hostSession.store,
                            botId = id,
                            initialTab = entry.arguments?.getString("tab") ?: "Details",
                            onBack = { nav.popBackStack() },
                            onOpenChat = { botId, chatId -> openChat(botId, chatId, null) },
                            onOpenExtensions = { nav.navigate(Routes.Extensions) },
                        )
                    }
                    composable(Routes.GlassLab) {
                        GlassLabScreen(
                            config = glassConfig,
                            onConfig = {
                                glassConfig = it
                                prefs.glassConfig = it
                            },
                            onBack = { nav.popBackStack() },
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
                            onOpenChat = ::openChat,
                            onOpenSettings = {
                                selectedTab = AppTab.Settings
                                nav.popBackStack(Routes.Tabs, inclusive = false)
                            },
                            onOpenNeeds = {
                                selectedTab = AppTab.Needs
                                nav.popBackStack(Routes.Tabs, inclusive = false)
                            },
                            onCreateBot = { nav.navigate(Routes.newBot(null)) },
                            onOpenGlassLab = { nav.navigate(Routes.GlassLab) },
                            onOpenPairing = { nav.navigate(Routes.Pairing) },
                            onOpenWorkbench = { nav.navigate(Routes.Workbench) },
                            onOpenExtensions = { nav.navigate(Routes.Extensions) },
                            onOpenTeamMap = { },
                        )
                    }
                    composable(Routes.Workbench) {
                        WorkbenchHomeScreen(
                            session = hostSession.store,
                            onBack = { nav.popBackStack() },
                            onOpenBotDetails = { id -> openBotDetails(id) },
                            onOpenExtensions = { nav.navigate(Routes.Extensions) },
                            onOpenTeamMap = { },
                        )
                    }
                    composable(Routes.Extensions) {
                        LiveExtensionsScreen(
                            session = hostSession.store,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        Routes.NewBot,
                        arguments = listOf(
                            navArgument("computerId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        Box(Modifier.fillMaxSize().background(CodyncTheme.colors.background)) {
                            LiveBotEditor(
                                session = hostSession.store,
                                bot = Bot(id = "", name = "New bot", backend = ""),
                                initialComputerId = entry.arguments?.getString("computerId"),
                                onCancel = { nav.popBackStack() },
                                onSaved = { nav.popBackStack() },
                                onDeleted = { nav.popBackStack() },
                            )
                        }
                    }
                    composable(
                        Routes.NewGroup,
                        arguments = listOf(
                            navArgument("computerId") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                    ) { entry ->
                        NewGroupScreen(
                            session = hostSession.store,
                            initialComputerId = entry.arguments?.getString("computerId"),
                            onCancel = { nav.popBackStack() },
                            onCreated = { botId, chatId ->
                                nav.popBackStack()
                                openChat(botId, chatId, null)
                            },
                        )
                    }
                }
                }
            }
        }
    }
}
