package com.pokai.codync.android.ui.search

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.Symbol
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.android.ui.HeaderIconButton
import com.pokai.codync.android.ui.SearchField
import com.pokai.codync.android.ui.SectionLabel
import kotlinx.coroutines.delay

@Composable
fun CommandDeckScreen(
    session: SessionStore,
    onBack: () -> Unit,
    onOpenChat: (botId: String, chatId: String, messageId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWorkbench: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenGlassLab: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val hostBots by session.bots().collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var barReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(30)
        barReady = true
    }
    val barScale by animateFloatAsState(
        targetValue = if (barReady) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "searchBarScale",
    )
    val barAlpha by animateFloatAsState(
        targetValue = if (barReady) 1f else 0f,
        animationSpec = tween(180),
        label = "searchBarAlpha",
    )
    val bots = remember(query, hostBots) {
        hostBots.filterNot { it.hidden }.filter { bot ->
            query.isBlank() || bot.name.contains(query, ignoreCase = true) ||
                bot.description.contains(query, ignoreCase = true) ||
                bot.lastMessage?.contains(query, ignoreCase = true) == true
        }.sortedWith(compareByDescending<com.pokai.codync.model.Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() })
    }
    data class Action(val title: String, val subtitle: String, val symbol: Symbol, val run: () -> Unit)
    val actions = remember(query) {
        listOf(
            Action("Settings", "Appearance, notifications and computers", CodyncIcons.Settings, onOpenSettings),
            Action("Workbench", "Choose a bot to open its details", CodyncIcons.Build, onOpenWorkbench),
            Action("Pair a computer", "Scan its QR code or paste a Codync link", CodyncIcons.QrCode, onOpenPairing),
            Action("Glass lab", "Preview the liquid-glass treatment", CodyncIcons.Palette, onOpenGlassLab),
        ).filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.subtitle.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)
                .graphicsLayer { scaleX = barScale; scaleY = barScale; alpha = barAlpha },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(CodyncIcons.Back, "Back", onBack)
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search bots and actions",
                autofocus = true,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (actions.isNotEmpty()) {
                item { SectionLabel("Actions") }
                item {
                    SectionCard {
                        actions.forEach { action -> ActionRow(action.title, action.subtitle, action.symbol, action.run) }
                    }
                }
            }
            if (bots.isNotEmpty()) {
                item { SectionLabel("Bots and groups", trailing = "${bots.size}") }
                items(bots, key = { it.id }) { bot ->
                    SectionCard {
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpenChat(bot.id, "main", null) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CharacterAvatar(bot.name, bot.avatarColor, bot.avatarShape, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bot.name, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    when (bot.status) {
                                        "working" -> bot.activity.ifBlank { "Working" }
                                        "needsInput" -> bot.activity.ifBlank { "Needs you" }
                                        else -> bot.lastMessage ?: bot.description.ifBlank { bot.backend }
                                    },
                                    color = colors.secondary,
                                    style = CodyncType.footnote,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (bots.isEmpty() && query.isNotBlank()) {
                item {
                    Text("No host bot matches that search.", modifier = Modifier.padding(12.dp), color = colors.tertiary, style = CodyncType.footnote)
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, symbol: Symbol, onClick: () -> Unit) {
    val colors = CodyncTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodyncIcon(symbol, tint = colors.text, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, color = colors.secondary, style = CodyncType.footnote)
        }
    }
}
