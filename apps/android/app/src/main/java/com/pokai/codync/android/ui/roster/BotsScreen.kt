@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pokai.codync.android.ui.roster

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.android.fake.FakeBot
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.fake.FakeTransport
import com.pokai.codync.android.ui.LargeHeader
import com.pokai.codync.android.ui.SearchField
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.android.ui.relativeShort
import com.pokai.codync.android.ui.rosterStamp
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.Spinner
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.UnreadBadge
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType

private enum class RosterFilter(val label: String) {
    All("All"),
    Working("Working"),
    Needs("Needs you"),
    Rooms("Rooms"),
}

@Composable
fun BotsScreen(
    fake: FakeHostState,
    onOpenChat: (botId: String, chatId: String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenFakeHost: () -> Unit,
) {
    val colors = CodyncTheme.colors
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(RosterFilter.All) }
    var collapsed by rememberSaveable { mutableStateOf(setOf<String>()) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var showNewChat by rememberSaveable { mutableStateOf(false) }

    val visible = fake.visibleBots().filter { bot ->
        val matchesQuery = query.isBlank() ||
            bot.name.contains(query, ignoreCase = true) ||
            bot.description.contains(query, ignoreCase = true) ||
            bot.lastLine.contains(query, ignoreCase = true)
        val matchesFilter = when (filter) {
            RosterFilter.All -> true
            RosterFilter.Working -> bot.status == "working"
            RosterFilter.Needs -> bot.status == "needsInput"
            RosterFilter.Rooms -> bot.kind == "group"
        }
        matchesQuery && matchesFilter
    }
    val pinned = if (query.isBlank() && filter == RosterFilter.All) {
        visible.filter { it.pinned }
    } else {
        emptyList()
    }
    val hidden = fake.bots.filter { it.hidden }
    val online = fake.computers.count { it.transport != FakeTransport.Offline }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeHeader(
            title = "Bots",
            subtitle = "${fake.computers.size} computers · $online online · rev ${fake.rev}",
            trailing = {
                GlassIconButton(CodyncIcons.Search, "Search", onClick = { searching = !searching })
                GlassIconButton(CodyncIcons.Add, "New chat", onClick = { showNewChat = true })
            },
        )

        if (searching) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search bots, rooms and messages",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            RosterFilter.entries.forEach { option ->
                FilterChip(option.label, filter == option, onClick = { filter = option })
            }
        }

        ConnectionStrip(fake = fake, onClick = onOpenFakeHost)

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 118.dp),
        ) {
            if (query.isNotBlank() || filter != RosterFilter.All) {
                if (visible.isEmpty()) {
                    item {
                        com.pokai.codync.design.EmptyState(
                            title = "No matches",
                            subtitle = "Try another name, room or message.",
                            symbol = CodyncIcons.Search,
                        )
                    }
                } else {
                    item { SectionLabel(if (query.isNotBlank()) "Results" else filter.label, trailing = "${visible.size}") }
                    items(visible, key = { it.id }) { bot ->
                        BotListRow(
                            fake = fake,
                            bot = bot,
                            onOpenChat = onOpenChat,
                            onDebug = onOpenFakeHost,
                        )
                    }
                }
            } else {
                if (pinned.isNotEmpty()) {
                    item { SectionLabel("Pinned", trailing = "${pinned.size}") }
                    items(pinned, key = { "pinned_${it.id}" }) { bot ->
                        BotListRow(fake, bot, onOpenChat, onOpenFakeHost)
                    }
                }

                fake.computers.forEach { computer ->
                    val bots = visible.filter { it.computerId == computer.id }
                    if (bots.isNotEmpty() || computer.transport != FakeTransport.Offline) {
                        item(key = "computer_${computer.id}") {
                            ComputerHeader(
                                name = computer.name,
                                platform = computer.platform,
                                transport = computer.transport,
                                count = bots.size,
                                collapsed = computer.id in collapsed,
                                onClick = {
                                    collapsed = if (computer.id in collapsed) collapsed - computer.id else collapsed + computer.id
                                },
                            )
                        }
                        if (computer.id !in collapsed) {
                            if (bots.isEmpty()) {
                                item(key = "empty_${computer.id}") {
                                    Text(
                                        "No bots match this filter",
                                        color = colors.tertiary,
                                        style = CodyncType.footnote,
                                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            items(bots, key = { it.id }) { bot ->
                                BotListRow(fake, bot, onOpenChat, onOpenFakeHost)
                            }
                        }
                    }
                }

                if (hidden.isNotEmpty()) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 22.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CodyncIcon(CodyncIcons.Hide, tint = colors.tertiary, size = 16.dp)
                            Spacer(Modifier.width(7.dp))
                            Text(
                                if (showHidden) "Hide hidden bots" else "Show ${hidden.size} hidden bot${if (hidden.size == 1) "" else "s"}",
                                color = colors.secondary,
                                style = CodyncType.footnote,
                                modifier = Modifier.weight(1f),
                            )
                            Text("Hidden", color = colors.tertiary, style = CodyncType.caption)
                        }
                    }
                    if (showHidden) {
                        items(hidden, key = { "hidden_${it.id}" }) { bot ->
                            BotListRow(fake, bot, onOpenChat, onOpenFakeHost, dimmed = true)
                        }
                    }
                }

                item {
                    Column(Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
                        Text("M0 FOUNDATION", color = colors.tertiary, style = CodyncType.caption)
                        Text(
                            "This roster is a local fixture. Tap the status strip to inspect the fake host, or open Settings → Developer → Glass lab.",
                            color = colors.tertiary,
                            style = CodyncType.footnote,
                        )
                    }
                }
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            fake = fake,
            onDismiss = { showNewChat = false },
            onStart = { botId, chatId ->
                showNewChat = false
                onOpenChat(botId, chatId)
            },
        )
    }
}

@Composable
private fun ConnectionStrip(fake: FakeHostState, onClick: () -> Unit) {
    val colors = CodyncTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            color = when (fake.transport) {
                FakeTransport.Direct -> colors.added
                FakeTransport.Relay -> colors.warning
                FakeTransport.Offline -> colors.tertiary
            },
        )
        Spacer(Modifier.width(7.dp))
        Text(fake.transport.label, color = colors.text, style = CodyncType.caption.copy(fontWeight = FontWeight.Medium))
        Spacer(Modifier.width(8.dp))
        Text("rev ${fake.rev}", color = colors.tertiary, style = CodyncType.caption)
        Spacer(Modifier.weight(1f))
        Text("Fake host", color = colors.tertiary, style = CodyncType.caption)
        Spacer(Modifier.width(5.dp))
        CodyncIcon(CodyncIcons.ChevronRight, tint = colors.tertiary, size = 15.dp)
    }
}

@Composable
private fun ComputerHeader(
    name: String,
    platform: String,
    transport: FakeTransport,
    count: Int,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val colors = CodyncTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(start = 22.dp, end = 18.dp, top = 18.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodyncIcon(CodyncIcons.Computer, tint = colors.secondary, size = 16.dp)
        Spacer(Modifier.width(7.dp))
        Text(name, color = colors.secondary, style = CodyncType.caption.copy(fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.width(6.dp))
        StatusDot(
            color = when (transport) {
                FakeTransport.Direct -> colors.added
                FakeTransport.Relay -> colors.warning
                FakeTransport.Offline -> colors.tertiary
            },
            size = 5.dp,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            when (transport) {
                FakeTransport.Direct -> "Direct"
                FakeTransport.Relay -> "Relay"
                FakeTransport.Offline -> "Offline"
            },
            color = colors.tertiary,
            style = CodyncType.caption2,
        )
        Spacer(Modifier.weight(1f))
        Text("$count", color = colors.tertiary, style = CodyncType.caption2)
        CodyncIcon(
            if (collapsed) CodyncIcons.ChevronRight else CodyncIcons.ChevronDown,
            tint = colors.tertiary,
            size = 16.dp,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun BotListRow(
    fake: FakeHostState,
    bot: FakeBot,
    onOpenChat: (String, String) -> Unit,
    onDebug: () -> Unit,
    dimmed: Boolean = false,
) {
    val colors = CodyncTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (dimmed) 0.55f else 1f)
                .combinedClickable(
                    onClick = {
                        fake.markRead(bot.id)
                        onOpenChat(bot.id, fake.mainChatId(bot.id))
                    },
                    onLongClick = { menu = true },
                )
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotAvatar(fake = fake, bot = bot)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bot.pinned) {
                        CodyncIcon(CodyncIcons.Pin, tint = colors.tertiary, size = 11.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        bot.name,
                        color = colors.text,
                        style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (bot.kind == "group") {
                        Spacer(Modifier.width(5.dp))
                        Text("ROOM", color = colors.tertiary, style = CodyncType.caption2)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        rosterStamp(bot.lastAt),
                        color = if (bot.unread > 0) colors.text else colors.tertiary,
                        style = CodyncType.caption,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BotPreview(bot)
                    Spacer(Modifier.weight(1f))
                    if (bot.unread > 0) UnreadBadge(bot.unread)
                }
            }
        }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("New chat") },
                leadingIcon = { CodyncIcon(CodyncIcons.AddComment, size = 18.dp) },
                onClick = {
                    menu = false
                    val id = fake.createChat(bot.id)
                    onOpenChat(bot.id, id)
                },
            )
            DropdownMenuItem(
                text = { Text(if (bot.pinned) "Unpin" else "Pin") },
                leadingIcon = { CodyncIcon(CodyncIcons.Pin, size = 18.dp) },
                onClick = {
                    menu = false
                    fake.togglePin(bot.id)
                },
            )
            DropdownMenuItem(
                text = { Text(if (bot.unread > 0) "Mark read" else "Mark unread") },
                leadingIcon = { CodyncIcon(CodyncIcons.MailRead, size = 18.dp) },
                onClick = {
                    menu = false
                    if (bot.unread > 0) fake.markRead(bot.id)
                },
            )
            DropdownMenuItem(
                text = { Text(if (bot.hidden) "Show bot" else "Hide bot") },
                leadingIcon = { CodyncIcon(CodyncIcons.Hide, size = 18.dp) },
                onClick = {
                    menu = false
                    if (bot.hidden) {
                        val index = fake.bots.indexOfFirst { it.id == bot.id }
                        if (index >= 0) fake.bots[index] = fake.bots[index].copy(hidden = false)
                    } else {
                        fake.hideBot(bot.id)
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Fixture host") },
                leadingIcon = { CodyncIcon(CodyncIcons.Science, size = 18.dp) },
                onClick = {
                    menu = false
                    onDebug()
                },
            )
        }
    }
}

@Composable
private fun BotPreview(bot: FakeBot) {
    val colors = CodyncTheme.colors
    when {
        bot.status == "needsInput" -> Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(colors.warning, size = 6.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                bot.activity.ifBlank { "Needs you" },
                color = colors.attention,
                style = CodyncType.subheadline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        bot.status == "working" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Spinner(size = 12.dp, color = colors.secondary)
            Spacer(Modifier.width(6.dp))
            Text(
                bot.activity.ifBlank { "Working…" },
                color = colors.secondary,
                style = CodyncType.subheadline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        bot.status == "offline" -> Text(
            "Offline · ${bot.lastLine}",
            color = colors.tertiary,
            style = CodyncType.subheadline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        else -> Text(
            bot.lastLine,
            color = colors.secondary,
            style = CodyncType.subheadline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun BotAvatar(fake: FakeHostState, bot: FakeBot, size: androidx.compose.ui.unit.Dp = 46.dp) {
    val colors = CodyncTheme.colors
    Box(Modifier.size(size)) {
        val ring = when (bot.status) {
            "needsInput" -> colors.warning
            "error" -> colors.danger
            else -> null
        }
        CharacterAvatar(
            name = bot.name,
            colorId = bot.color,
            shapeId = bot.shape,
            character = bot.character,
            size = size,
            statusRing = ring,
        )
        if (bot.status == "working") {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colors.background),
                contentAlignment = Alignment.Center,
            ) {
                Spinner(size = 13.dp, color = colors.secondary)
            }
        }
    }
}

@Composable
private fun NewChatSheet(
    fake: FakeHostState,
    onDismiss: () -> Unit,
    onStart: (botId: String, chatId: String) -> Unit,
) {
    val colors = CodyncTheme.colors
    var selected by rememberSaveable { mutableStateOf<String?>(fake.visibleBots().firstOrNull()?.id) }
    val bots = fake.visibleBots()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.text,
        dragHandle = null,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("New chat", color = colors.text, style = CodyncType.title)
                    Text("Choose one bot to start. Group setup lands with M3.", color = colors.secondary, style = CodyncType.footnote)
                }
                GlassIconButton(CodyncIcons.Close, "Close", onDismiss)
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bots.forEach { bot ->
                    val active = selected == bot.id
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (active) colors.accentFill else colors.surface)
                            .combinedClickable(onClick = { selected = bot.id })
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CharacterAvatar(bot.name, bot.color, bot.shape, size = 22.dp, character = bot.character)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            bot.name,
                            color = if (active) colors.onAccent else colors.text,
                            style = CodyncType.caption.copy(fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            SectionCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CodyncIcon(CodyncIcons.Info, tint = colors.tertiary, size = 17.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "The chat starts empty. Each chat keeps its own context and model.",
                        color = colors.secondary,
                        style = CodyncType.footnote,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = "Start chat",
                onClick = {
                val bot = selected?.let(fake::bot) ?: return@PrimaryButton
                val chatId = fake.createChat(bot.id)
                onStart(bot.id, chatId)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selected != null || bots.isNotEmpty(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}
