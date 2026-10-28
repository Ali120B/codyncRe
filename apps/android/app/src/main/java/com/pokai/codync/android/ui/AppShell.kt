package com.pokai.codync.android.ui

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.android.AndroidAccountSession
import com.pokai.codync.android.ui.needs.LiveNeedsScreen
import com.pokai.codync.android.ui.roster.LiveBotsScreen
import com.pokai.codync.android.ui.settings.SettingsScreen
import com.pokai.codync.android.ui.search.CommandDeckScreen
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.GlassSurface
import com.pokai.codync.design.Symbol
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

enum class AppTab(val label: String, val symbol: Symbol) {
    Home("Home", CodyncIcons.Home),
    Search("Search", CodyncIcons.Search),
    Needs("Needs You", CodyncIcons.Notifications),
    Settings("Settings", CodyncIcons.Settings),
}

@Composable
fun TabsScreen(
    session: SessionStore,
    account: AndroidAccountSession,
    hazeState: HazeState,
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    prefs: AppPreferences,
    onOpenChat: (botId: String, chatId: String, messageId: String?) -> Unit,
    onOpenGlassLab: () -> Unit,
    onOpenPairing: () -> Unit = {},
    onOpenWorkbench: () -> Unit = {},
    onOpenBotDetails: (String) -> Unit = {},
    onCreateBot: () -> Unit = {},
    onOpenTeamMap: () -> Unit = {},
    onOpenExtensions: () -> Unit = {},
) {
    val colors = CodyncTheme.colors
    var searchFocusRequest by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(
        initialPage = AppTab.entries.indexOf(tab).coerceAtLeast(0),
        pageCount = { AppTab.entries.size },
    )

    // Commit tab when settled (for saveable / external nav).
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val next = AppTab.entries.getOrNull(page) ?: return@collect
                if (next != tab) onTab(next)
            }
    }

    LaunchedEffect(tab) {
        val index = AppTab.entries.indexOf(tab).coerceAtLeast(0)
        if (pagerState.currentPage != index) {
            pagerState.animateScrollToPage(
                index,
                animationSpec = tween(160, easing = FastOutSlowInEasing),
            )
        }
    }

    val backdrop = rememberLayerBackdrop()
    Box(Modifier.fillMaxSize().background(colors.background)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(backdrop)
                .hazeSource(hazeState),
            userScrollEnabled = true,
        ) { page ->
            when (AppTab.entries[page]) {
                AppTab.Home -> LiveBotsScreen(
                    session = session,
                    onOpenChat = { botId, chatId -> onOpenChat(botId, chatId, null) },
                    onOpenBotDetails = onOpenBotDetails,
                    onPair = onOpenPairing,
                    onCreateBot = onCreateBot,
                )
                AppTab.Search -> CommandDeckScreen(
                    session = session,
                    active = pagerState.settledPage == page,
                    focusRequest = searchFocusRequest,
                    showBack = false,
                    onBack = { onTab(AppTab.Home) },
                    onOpenChat = onOpenChat,
                    onOpenSettings = { onTab(AppTab.Settings) },
                    onOpenNeeds = { onTab(AppTab.Needs) },
                    onCreateBot = onCreateBot,
                    onOpenGlassLab = onOpenGlassLab,
                    onOpenPairing = onOpenPairing,
                    onOpenWorkbench = onOpenWorkbench,
                    onOpenExtensions = onOpenExtensions,
                    onOpenTeamMap = onOpenTeamMap,
                )
                AppTab.Needs -> LiveNeedsScreen(
                    session = session,
                    onOpenChat = { botId -> onOpenChat(botId, "main", null) },
                )
                AppTab.Settings -> SettingsScreen(
                    session = session,
                    account = account,
                    active = page == pagerState.settledPage,
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    prefs = prefs,
                    onOpenGlassLab = onOpenGlassLab,
                    onOpenPairing = onOpenPairing,
                    onOpenWorkbench = onOpenWorkbench,
                )
            }
        }

        BottomNav(
            pagerState = pagerState,
            onTab = onTab,
            onReselect = { if (it == AppTab.Search) searchFocusRequest += 1 },
            hazeState = hazeState,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun BottomNav(
    pagerState: PagerState,
    onTab: (AppTab) -> Unit,
    onReselect: (AppTab) -> Unit,
    hazeState: HazeState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val colors = CodyncTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val cornerPx = with(density) { 32.dp.toPx() }
    // Follow the finger — no waiting for settledPage.
    val pagePos = pagerState.currentPage + pagerState.currentPageOffsetFraction
    val nearest = pagePos.roundToInt().coerceIn(0, AppTab.entries.lastIndex)

    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(32.dp) },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(
                        if (colors.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f),
                    )
                },
            )
            .clip(RoundedCornerShape(32.dp)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().selectableGroup()) {
            val tabWidth = maxWidth / AppTab.entries.size
            val pillInset = 5.dp
            val pillWidth = tabWidth - pillInset * 2
            val pillOffsetPx = with(density) {
                (tabWidth * pagePos + pillInset).roundToPx()
            }

            Box(
                Modifier
                    .offset { IntOffset(pillOffsetPx, 0) }
                    .width(pillWidth)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = if (colors.isDark) {
                                listOf(
                                    Color.White.copy(alpha = 0.18f),
                                    Color.White.copy(alpha = 0.08f),
                                )
                            } else {
                                listOf(
                                    Color.White.copy(alpha = 0.72f),
                                    Color.White.copy(alpha = 0.38f),
                                )
                            },
                        ),
                    )
                    .background(
                        if (colors.isDark) Color.White.copy(alpha = 0.08f)
                        else Color.Black.copy(alpha = 0.05f),
                    ),
            )

            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
    AppTab.entries.forEachIndexed { index, item ->
                    key(item) {
                        val selected = index == nearest
                        val interaction = remember { MutableInteractionSource() }
                        // Soft weight toward the nearest page while swiping.
                        val proximity = (1f - kotlin.math.abs(pagePos - index)).coerceIn(0f, 1f)
                        val tint = androidx.compose.ui.graphics.lerp(colors.tertiary, colors.text, proximity)
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .semantics { this.selected = selected }
                                .clickable(
                                    interactionSource = interaction,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (!selected) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onTab(item)
                                        } else onReselect(item)
                                    },
                                )
                                .scale(1f + 0.04f * proximity),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CodyncIcon(item.symbol, tint = tint, size = 22.dp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                item.label,
                                color = tint,
                                style = CodyncType.caption2.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 10.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
