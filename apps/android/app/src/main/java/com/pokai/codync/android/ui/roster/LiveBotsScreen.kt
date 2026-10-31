package com.pokai.codync.android.ui.roster

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.ui.BotIdentityAvatar
import com.pokai.codync.android.ui.LargeHeader
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncMenuAnchor
import com.pokai.codync.design.CodyncMenuItem
import com.pokai.codync.design.EmptyState
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.GlassSurface
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.Spinner
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot
import com.pokai.codync.model.Computer
import com.pokai.codync.transport.ConnectionState
import com.pokai.codync.transport.TransportKind
import dev.chrisbanes.haze.HazeState

private enum class HomeFilter(val label: String, val symbol: com.pokai.codync.design.Symbol) {
    All("All bots & groups", CodyncIcons.Bots),
    Bots("Bots only", CodyncIcons.Person),
    Groups("Groups only", CodyncIcons.Groups),
    Working("Working", CodyncIcons.Speed),
    Needs("Needs you", CodyncIcons.Notifications),
    Hidden("Hidden", CodyncIcons.Hide),
}

object HomeFilterState {
    var filter: HomeFilter = HomeFilter.All
    var selectedComputerId: String? = null
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun LiveBotsScreen(
    session: SessionStore,
    hazeState: HazeState,
    onOpenChat: (botId: String, chatId: String) -> Unit,
    onOpenBotDetails: (String) -> Unit,
    onPair: () -> Unit,
    onCreateBot: (String?) -> Unit,
    onCreateGroup: (String?) -> Unit,
) {
    val hostBots by session.bots().collectAsState(initial = emptyList())
    val computers by session.computers().collectAsState(initial = emptyList())
    val botComputers by session.botComputers().collectAsState(initial = emptyMap())
    val connections by session.computerConnections().collectAsState(initial = emptyMap())
    val actionError by session.actionError().collectAsState(initial = null)
    val colors = CodyncTheme.colors
    var filter by remember { mutableStateOf(HomeFilterState.filter) }
    var selectedComputerId by remember { mutableStateOf(HomeFilterState.selectedComputerId) }
    LaunchedEffect(filter) { HomeFilterState.filter = filter }
    LaunchedEffect(selectedComputerId) { HomeFilterState.selectedComputerId = selectedComputerId }
    var showNewChat by remember { mutableStateOf(false) }
    var createMenuOpen by remember { mutableStateOf(false) }
    var computerMenuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var botsExpanded by rememberSaveable { mutableStateOf(true) }
    var groupsExpanded by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(computers) {
        if (selectedComputerId != null && computers.none { it.id == selectedComputerId }) selectedComputerId = null
    }

    val scopeId = selectedComputerId
    val scoped = remember(hostBots, botComputers, scopeId) {
        hostBots.filter { bot -> scopeId == null || botComputers[bot.id] == scopeId }
    }
    val visible = remember(scoped, filter) {
        scoped.filter { bot ->
            when (filter) {
                HomeFilter.All -> !bot.hidden
                HomeFilter.Bots -> !bot.hidden && bot.kind != "group"
                HomeFilter.Groups -> !bot.hidden && bot.kind == "group"
                HomeFilter.Working -> !bot.hidden && bot.status == "working"
                HomeFilter.Needs -> !bot.hidden && bot.status == "needsInput"
                HomeFilter.Hidden -> bot.hidden
            }
        }.sortedWith(compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() })
    }
    val online = computers.count { computer ->
        connections[computer.id]?.let { it.kind != TransportKind.Offline && it.hello != null } == true
    }
    val selectedComputer = computers.firstOrNull { it.id == selectedComputerId }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeHeader(
            title = "Home",
            subtitle = when {
                computers.isEmpty() -> "Pair a Codync computer to load its bots"
                computers.size == 1 -> selectedComputer?.name ?: computers.first().name
                selectedComputer != null -> "${selectedComputer.name} · $online of ${computers.size} online"
                else -> "${computers.size} computers · $online online"
            },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (computers.isEmpty()) {
                        PrimaryButton("Pair", onClick = onPair)
                    }
                    CodyncMenuAnchor(
                        expanded = createMenuOpen,
                        onDismissRequest = { createMenuOpen = false },
                        items = listOf(
                            CodyncMenuItem(
                                "Create new bot",
                                CodyncIcons.Add,
                                enabled = computers.isNotEmpty(),
                                onClick = { createMenuOpen = false; onCreateBot(selectedComputerId) },
                            ),
                            CodyncMenuItem(
                                "Create group chat",
                                CodyncIcons.Groups,
                                enabled = computers.isNotEmpty(),
                                onClick = { createMenuOpen = false; onCreateGroup(selectedComputerId) },
                            ),
                        ),
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(colors.surface.copy(alpha = 0.7f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlassIconButton(CodyncIcons.Add, "Create bot or group", onClick = { createMenuOpen = true })
                        }
                    }
                }
            },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (computers.size > 1) {
                GlassDropdown(
                    label = selectedComputer?.name ?: "All computers",
                    symbol = CodyncIcons.Computer,
                    expanded = computerMenuOpen,
                    onExpanded = { computerMenuOpen = true },
                    onDismiss = { computerMenuOpen = false },
                    hazeState = hazeState,
                    modifier = Modifier.weight(1f),
                    items = buildList {
                        add(CodyncMenuItem(
                            "All computers",
                            if (selectedComputerId == null) CodyncIcons.Done else CodyncIcons.Computer,
                            onClick = { selectedComputerId = null; computerMenuOpen = false },
                        ))
                        computers.forEach { computer ->
                            add(CodyncMenuItem(
                                computer.name,
                                if (selectedComputerId == computer.id) CodyncIcons.Done else CodyncIcons.Computer,
                                onClick = { selectedComputerId = computer.id; computerMenuOpen = false },
                            ))
                        }
                    },
                )
            }
            GlassDropdown(
                label = filter.label,
                symbol = filter.symbol,
                expanded = filterMenuOpen,
                onExpanded = { filterMenuOpen = true },
                onDismiss = { filterMenuOpen = false },
                hazeState = hazeState,
                modifier = if (computers.size > 1) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                items = HomeFilter.entries.map { option ->
                    CodyncMenuItem(
                        option.label,
                        if (filter == option) CodyncIcons.Done else option.symbol,
                        onClick = { filter = option; filterMenuOpen = false },
                    )
                },
            )
        }

        actionError?.let { error ->
            Text(error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = colors.warning, style = CodyncType.caption)
        }

        val offline = computers.filter { computer ->
            connections[computer.id]?.let { it.hello == null || it.kind == TransportKind.Offline } == true
        }
        if (offline.isNotEmpty()) {
            Text(
                offline.joinToString(" · ") { "${it.name} offline" },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = colors.tertiary,
                style = CodyncType.footnote,
            )
        }

        if (visible.isEmpty()) {
            EmptyState(
                title = if (computers.isEmpty()) "No computer paired" else "No bots to show",
                subtitle = if (computers.isEmpty()) {
                    "Pair from the desktop QR code. Every paired computer stays connected."
                } else {
                    "The host hasn't sent any bots matching this filter."
                },
                symbol = CodyncIcons.Bots,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                if (scopeId == null && computers.size > 1) {
                    computers.forEach { computer ->
                        val computerBots = visible.filter { botComputers[it.id] == computer.id }
                        if (computerBots.isNotEmpty()) {
                            item(key = "header_${computer.id}") {
                                ComputerGroupHeader(computer, connections[computer.id], computerBots)
                            }
                            rosterItems(computerBots, hostBots, onOpenChat, onOpenBotDetails, keyPrefix = computer.id)
                        }
                    }
                } else {
                    val groups = visible.filter { it.kind == "group" }.sortedByDescending { it.lastAt }
                    val singles = visible.filter { it.kind != "group" }.sortedByDescending { it.lastAt }
                    item { CollapsibleHeader("BOTS", singles.size, botsExpanded) { botsExpanded = !botsExpanded } }
                    if (botsExpanded) rosterItems(singles, hostBots, onOpenChat, onOpenBotDetails, keyPrefix = "bot")
                    item { CollapsibleHeader("GROUPS", groups.size, groupsExpanded) { groupsExpanded = !groupsExpanded } }
                    if (groupsExpanded) rosterItems(groups, hostBots, onOpenChat, onOpenBotDetails, keyPrefix = "group")
                }
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            session = session,
            bots = hostBots,
            onDismiss = { showNewChat = false },
            onCreateBot = { onCreateBot(selectedComputerId) },
            onOpenChat = { botId, chatId ->
                showNewChat = false
                onOpenChat(botId, chatId)
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.rosterItems(
    bots: List<Bot>,
    roster: List<Bot>,
    onOpenChat: (String, String) -> Unit,
    onOpenBotDetails: (String) -> Unit,
    keyPrefix: String,
) {
    items(bots, key = { "${keyPrefix}_${it.id}" }) { bot ->
        SectionCard {
            BotRosterRow(
                bot = bot,
                roster = roster,
                onOpenChat = { onOpenChat(bot.id, "main") },
                onOpenDetails = { onOpenBotDetails(bot.id) },
            )
        }
    }
}

@Composable
private fun BotRosterRow(
    bot: Bot,
    roster: List<Bot>,
    onOpenChat: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val colors = CodyncTheme.colors
    Row(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = onOpenChat,
            onLongClick = onOpenDetails,
        ).padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BotIdentityAvatar(bot, roster, size = 46.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    bot.name,
                    color = colors.text,
                    style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (bot.unread > 0) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "${bot.unread}",
                        color = colors.onAccent,
                        style = CodyncType.caption,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(colors.accentFill)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
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
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (bot.status == "working") {
            Spinner(size = 14.dp, color = colors.secondary)
        } else if (bot.status == "needsInput" || bot.status == "error") {
            StatusDot(color = colors.warning)
        }
    }
}

@Composable
private fun ComputerGroupHeader(
    computer: Computer,
    connection: ConnectionState?,
    bots: List<Bot>,
) {
    val colors = CodyncTheme.colors
    val online = connection != null && connection.kind != TransportKind.Offline && connection.hello != null
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (online) colors.added else colors.tertiary)
        Spacer(Modifier.width(7.dp))
        Text(computer.name, color = colors.secondary, style = CodyncType.caption.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.weight(1f))
        Text("${bots.size} bots", color = colors.tertiary, style = CodyncType.caption)
    }
}

@Composable
private fun CollapsibleHeader(label: String, count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val colors = CodyncTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.tertiary, style = CodyncType.caption)
        Spacer(Modifier.weight(1f))
        Text("$count", color = colors.tertiary, style = CodyncType.caption)
        Spacer(Modifier.width(6.dp))
        CodyncIcon(if (expanded) CodyncIcons.ChevronDown else CodyncIcons.ChevronRight, tint = colors.tertiary, size = 14.dp)
    }
}

/** A capsule glass anchor with the shared animated menu underneath. */
@Composable
private fun GlassDropdown(
    label: String,
    symbol: com.pokai.codync.design.Symbol,
    expanded: Boolean,
    onExpanded: () -> Unit,
    onDismiss: () -> Unit,
    hazeState: HazeState,
    items: List<CodyncMenuItem>,
    modifier: Modifier = Modifier,
) {
    val colors = CodyncTheme.colors
    CodyncMenuAnchor(
        expanded = expanded,
        onDismissRequest = onDismiss,
        items = items,
        modifier = modifier,
    ) {
        GlassSurface(
            state = hazeState,
            shape = CircleShape,
            modifier = Modifier.height(38.dp),
            contentPadding = PaddingValues(horizontal = 13.dp),
        ) {
            Row(
                Modifier.fillMaxHeight().clickable(onClick = onExpanded),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CodyncIcon(symbol, tint = colors.text, size = 17.dp)
                Text(
                    label,
                    color = colors.text,
                    style = CodyncType.footnote.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                CodyncIcon(CodyncIcons.ChevronDown, tint = colors.tertiary, size = 15.dp)
            }
        }
    }
}
