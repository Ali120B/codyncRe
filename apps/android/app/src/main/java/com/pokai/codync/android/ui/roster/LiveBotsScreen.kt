package com.pokai.codync.android.ui.roster

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.model.Bot
import com.pokai.codync.android.ui.LargeHeader
import com.pokai.codync.android.ui.BotIdentityAvatar
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncMenuAnchor
import com.pokai.codync.design.CodyncMenuItem
import com.pokai.codync.design.EmptyState
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LiveBotsScreen(
    session: SessionStore,
    onOpenChat: (botId: String, chatId: String) -> Unit,
    onOpenBotDetails: (String) -> Unit,
    onPair: () -> Unit,
    onCreateBot: () -> Unit,
) {
    val hostBots by session.bots().collectAsState(initial = emptyList())
    val computers by session.computers().collectAsState(initial = emptyList())
    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    val actionError by session.actionError().collectAsState(initial = null)
    val colors = CodyncTheme.colors
    var filter by rememberSaveable { mutableStateOf("All") }
    var showNewChat by remember { mutableStateOf(false) }
    var createMenuOpen by remember { mutableStateOf(false) }
    val bots = remember(hostBots, filter) {
        hostBots.filter { bot ->
            when (filter) {
                "Working" -> !bot.hidden && bot.status == "working"
                "Needs you" -> !bot.hidden && bot.status == "needsInput"
                "Groups" -> !bot.hidden && bot.kind == "group"
                "Hidden" -> bot.hidden
                else -> !bot.hidden
            }
        }.sortedWith(compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() })
    }
    val computer = connection.computer

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeHeader(
            title = "Home",
            subtitle = when {
                computer == null -> "Pair a Codync computer to load its bots"
                else -> computer.name
            },
            trailing = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (computer == null) {
                        PrimaryButton("Pair", onClick = onPair)
                    } else {
                    }
                    CodyncMenuAnchor(
                        expanded = createMenuOpen,
                        onDismissRequest = { createMenuOpen = false },
                        items = listOf(
                            CodyncMenuItem("Create new bot", CodyncIcons.Add, enabled = computer != null, onClick = onCreateBot),
                            CodyncMenuItem("Create group chat", CodyncIcons.Groups, enabled = computer != null, onClick = { showNewChat = true }),
                        ),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(CodyncTheme.colors.surface.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlassIconButton(CodyncIcons.Add, "Create bot or group", onClick = { createMenuOpen = true })
                        }
                    }
                }
            },
        )
        if (computers.size > 1) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                computers.forEach { host ->
                    FilterChip(
                        text = host.name,
                        selected = connection.computer?.id == host.id,
                        onClick = { if (connection.computer?.id != host.id) session.connect(host) },
                    )
                }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            listOf("All", "Working", "Needs you", "Groups", "Hidden").forEach { item ->
                FilterChip(text = item, selected = filter == item, onClick = { filter = item })
            }
        }
        actionError?.let { error ->
            Text(error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = colors.warning, style = CodyncType.caption)
        }

        if (connection.kind == com.pokai.codync.transport.TransportKind.Offline && computer != null) {
            Text(
                connection.lastError ?: "Waiting for ${computer.name} to come online.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                color = colors.tertiary,
                style = CodyncType.footnote,
            )
        }
        if (bots.isEmpty()) {
            EmptyState(
                title = if (computer == null) "No computer paired" else "No bots to show",
                subtitle = if (computer == null) "Pair from the desktop QR code. This roster loads from the host." else "The host hasn't sent any bots matching this filter.",
                symbol = CodyncIcons.Bots,
            )
        } else {
            val groups = bots.filter { it.kind == "group" }
            val singleBots = bots.filter { it.kind != "group" }
            var botsExpanded by rememberSaveable { mutableStateOf(true) }
            var groupsExpanded by rememberSaveable { mutableStateOf(true) }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { botsExpanded = !botsExpanded }.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 7.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("BOTS", color = colors.tertiary, style = CodyncType.caption)
                        Spacer(Modifier.weight(1f))
                        Text("${singleBots.size}", color = colors.tertiary, style = CodyncType.caption)
                        Spacer(Modifier.width(6.dp))
                        CodyncIcon(if (botsExpanded) CodyncIcons.ChevronDown else CodyncIcons.ChevronRight, tint = colors.tertiary, size = 14.dp)
                    }
                }
                if (botsExpanded) {
                    items(singleBots, key = { it.id }) { bot ->
                    SectionCard {
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = { onOpenChat(bot.id, "main") },
                                onLongClick = { onOpenBotDetails(bot.id) },
                            ).padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            BotIdentityAvatar(bot, hostBots, size = 46.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(bot.name, color = colors.text, style = CodyncType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
                                    if (bot.unread > 0) {
                                        Spacer(Modifier.width(7.dp))
                                        Text("${bot.unread}", color = colors.onAccent, style = CodyncType.caption, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(colors.accentFill).padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text(
                                    when (bot.status) {
                                        "working" -> bot.activity.ifBlank { "Working" }
                                        "needsInput" -> bot.activity.ifBlank { "Needs you" }
                                        else -> bot.lastMessage ?: bot.description.ifBlank { bot.backend }
                                    },
                                    color = colors.secondary,
                                    style = CodyncType.footnote,
                                    maxLines = 1,
                                )
                            }
                            if (bot.status == "working") {
                                com.pokai.codync.design.Spinner(size = 14.dp, color = colors.secondary)
                            } else if (bot.status == "needsInput" || bot.status == "error") {
                                StatusDot(color = colors.warning)
                            }
                        }
                    }
                }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { groupsExpanded = !groupsExpanded }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 7.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("GROUPS", color = colors.tertiary, style = CodyncType.caption)
                        Spacer(Modifier.weight(1f))
                        Text("${groups.size}", color = colors.tertiary, style = CodyncType.caption)
                        Spacer(Modifier.width(6.dp))
                        CodyncIcon(if (groupsExpanded) CodyncIcons.ChevronDown else CodyncIcons.ChevronRight, tint = colors.tertiary, size = 14.dp)
                    }
                }
                if (groupsExpanded) {
                    items(groups, key = { it.id }) { bot ->
                    SectionCard {
                        Row(
                            Modifier.fillMaxWidth().combinedClickable(
                                onClick = { onOpenChat(bot.id, "main") },
                                onLongClick = { onOpenBotDetails(bot.id) },
                            ).padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            BotIdentityAvatar(bot, hostBots, size = 46.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(bot.name, color = colors.text, style = CodyncType.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
                                    if (bot.unread > 0) {
                                        Spacer(Modifier.width(7.dp))
                                        Text("${bot.unread}", color = colors.onAccent, style = CodyncType.caption, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(colors.accentFill).padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                                Text(
                                    when (bot.status) {
                                        "working" -> bot.activity.ifBlank { "Working" }
                                        "needsInput" -> bot.activity.ifBlank { "Needs you" }
                                        else -> bot.lastMessage ?: bot.description.ifBlank { bot.backend }
                                    },
                                    color = colors.secondary,
                                    style = CodyncType.footnote,
                                    maxLines = 1,
                                )
                            }
                            if (bot.status == "working") {
                                com.pokai.codync.design.Spinner(size = 14.dp, color = colors.secondary)
                            } else if (bot.status == "needsInput" || bot.status == "error") {
                                StatusDot(color = colors.warning)
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            session = session,
            bots = hostBots,
            forceGroup = true,
            onDismiss = { showNewChat = false },
            onCreateBot = onCreateBot,
            onOpenChat = { botId, chatId ->
                showNewChat = false
                onOpenChat(botId, chatId)
            },
        )
    }
}
