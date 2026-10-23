package com.pokai.codync.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.ui.needs.NeedsYouScreen
import com.pokai.codync.android.ui.roster.BotsScreen
import com.pokai.codync.android.ui.settings.SettingsScreen
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.GlassSurface
import com.pokai.codync.design.Symbol
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

enum class AppTab(val label: String, val symbol: Symbol) {
    Bots("Bots", CodyncIcons.Bots),
    Needs("Needs You", CodyncIcons.Notifications),
    Settings("Settings", CodyncIcons.Settings),
}

@Composable
fun TabsScreen(
    fake: FakeHostState,
    hazeState: HazeState,
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onOpenChat: (botId: String, chatId: String, messageId: String?) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenGlassLab: () -> Unit,
    onOpenFakeHost: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val stateHolder = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Box(
            Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            stateHolder.SaveableStateProvider(tab.name) {
                when (tab) {
                    AppTab.Bots -> BotsScreen(
                            fake = fake,
                            onOpenChat = { botId, chatId -> onOpenChat(botId, chatId, null) },
                            onOpenSearch = onOpenSearch,
                            onOpenFakeHost = onOpenFakeHost,
                        )
                    AppTab.Needs -> NeedsYouScreen(
                            fake = fake,
                            onOpenChat = onOpenChat,
                            onOpenSearch = onOpenSearch,
                        )
                    AppTab.Settings -> SettingsScreen(
                            fake = fake,
                            themeMode = themeMode,
                            onThemeMode = onThemeMode,
                            onOpenGlassLab = onOpenGlassLab,
                            onOpenFakeHost = onOpenFakeHost,
                            onOpenSearch = onOpenSearch,
                        )
                }
            }
        }

        BottomNav(
            tab = tab,
            onTab = onTab,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun BottomNav(
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val colors = CodyncTheme.colors
    val haptics = LocalHapticFeedback.current
    GlassSurface(
        state = hazeState,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier.fillMaxWidth().height(64.dp),
    ) {
        Row(
            Modifier.fillMaxSize().selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTab.entries.forEach { item ->
                val selected = item == tab
                Column(
                    Modifier
                        .weight(1f)
                        .height(64.dp)
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = {
                                if (!selected) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTab(item)
                                }
                            },
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CodyncIcon(
                        item.symbol,
                        tint = if (selected) colors.text else colors.tertiary,
                        size = 22.dp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        color = if (selected) colors.text else colors.tertiary,
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
