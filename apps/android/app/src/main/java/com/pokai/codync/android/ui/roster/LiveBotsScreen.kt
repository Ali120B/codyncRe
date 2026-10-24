package com.pokai.codync.android.ui.roster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.model.Bot
import com.pokai.codync.android.ui.LargeHeader
import com.pokai.codync.android.ui.SearchField
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncMenuAnchor
import com.pokai.codync.design.CodyncMenuItem
import com.pokai.codync.design.EmptyState
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put

@Composable
fun LiveBotsScreen(
    session: SessionStore,
    onOpenChat: (botId: String, chatId: String) -> Unit,
    onOpenBotDetails: (String) -> Unit,
    onPair: () -> Unit,
) {
    val hostBots by session.bots().collectAsState(initial = emptyList())
    val connection by session.connection().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    val actionError by session.actionError().collectAsState(initial = null)
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var menuBotId by remember { mutableStateOf<String?>(null) }
    var showNewChat by remember { mutableStateOf(false) }
    val bots = hostBots.filter { bot ->
        val matchesQuery = query.isBlank() ||
            bot.name.contains(query, ignoreCase = true) ||
            bot.description.contains(query, ignoreCase = true) ||
            (bot.lastMessage?.contains(query, ignoreCase = true) == true)
        val matchesFilter = when (filter) {
            "Working" -> !bot.hidden && bot.status == "working"
            "Needs you" -> !bot.hidden && bot.status == "needsInput"
            "Groups" -> !bot.hidden && bot.kind == "group"
            "Hidden" -> bot.hidden
            else -> !bot.hidden
        }
        matchesQuery && matchesFilter
    }.sortedWith(compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() })
    val orderedHostBots = remember(hostBots) {
        hostBots.sortedWith(compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() })
    }
    val computer = connection.computer

    Column(Modifier.fillMaxSize()) {
        LargeHeader(
            title = "Bots",
            subtitle = when {
                computer == null -> "Pair a Codync computer to load its bots"
                connection.kind == com.pokai.codync.transport.TransportKind.Direct -> "${computer.name} · Connected · rev ${connection.rev}"
                connection.kind == com.pokai.codync.transport.TransportKind.Relay -> "${computer.name} · Connected through Codync Cloud · rev ${connection.rev}"
                else -> "${computer.name} · Connecting to host"
            },
            trailing = {
                if (computer == null) {
                    PrimaryButton("Pair", onClick = onPair)
                } else {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        StatusDot(color = if (connection.kind == com.pokai.codync.transport.TransportKind.Offline) colors.warning else colors.added)
                        Spacer(Modifier.width(6.dp))
                        GlassIconButton(CodyncIcons.AddComment, "New chat", onClick = { showNewChat = true })
                    }
                }
            },
        )
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search bots and groups",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
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
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item { SectionLabel(computer?.name ?: "Computer", trailing = "${bots.size}") }
                items(bots, key = { it.id }) { bot ->
                    SectionCard {
                        SettingsRow(
                            title = bot.name,
                            subtitle = when (bot.status) {
                                "working" -> bot.activity.ifBlank { "Working" }
                                "needsInput" -> bot.activity.ifBlank { "Needs you" }
                                else -> bot.lastMessage ?: bot.description.ifBlank { bot.backend }
                            },
                            icon = if (bot.kind == "group") CodyncIcons.Groups else CodyncIcons.Bots,
                            trailing = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    CharacterAvatar(
                                        name = bot.name,
                                        colorId = bot.avatarColor,
                                        shapeId = bot.avatarShape,
                                        size = 34.dp,
                                        modifier = Modifier.clip(RoundedCornerShape(50)),
                                    )
                                    val samePinnedGroup = orderedHostBots.filter { it.pinned == bot.pinned }
                                    val position = samePinnedGroup.indexOfFirst { it.id == bot.id }
                                    CodyncMenuAnchor(
                                        expanded = menuBotId == bot.id,
                                        onDismissRequest = { menuBotId = null },
                                        items = buildList {
                                            add(CodyncMenuItem(
                                                if (bot.kind == "group") "Group details" else "Bot details",
                                                CodyncIcons.Info,
                                                onClick = {
                                                    menuBotId = null
                                                    onOpenBotDetails(bot.id)
                                                },
                                            ))
                                            add(CodyncMenuItem("New chat", CodyncIcons.AddComment, onClick = {
                                                menuBotId = null
                                                onOpenChat(bot.id, "new")
                                            }))
                                            add(CodyncMenuItem(if (bot.pinned) "Unpin" else "Pin to top", CodyncIcons.Pin, onClick = {
                                                menuBotId = null
                                                scope.launch { session.call("updateBot", buildJsonObject {
                                                    put("id", bot.id)
                                                    put("pinned", !bot.pinned)
                                                }) }
                                            }))
                                            add(CodyncMenuItem(if (bot.hidden) "Show bot" else "Hide bot", if (bot.hidden) CodyncIcons.Visible else CodyncIcons.Hide, onClick = {
                                                menuBotId = null
                                                scope.launch { session.call("updateBot", buildJsonObject {
                                                    put("id", bot.id)
                                                    put("hidden", !bot.hidden)
                                                }) }
                                            }))
                                            if (position > 0) add(CodyncMenuItem("Move up", CodyncIcons.ArrowUp, onClick = {
                                                menuBotId = null
                                                val mutable = orderedHostBots.toMutableList()
                                                val current = mutable.indexOfFirst { it.id == bot.id }
                                                val previous = mutable.indexOfFirst { it.id == samePinnedGroup[position - 1].id }
                                                val row = mutable[current]
                                                mutable[current] = mutable[previous]
                                                mutable[previous] = row
                                                scope.launch { session.call("reorderBots", buildJsonObject {
                                                    put("ids", buildJsonArray { mutable.forEach { add(JsonPrimitive(it.id)) } })
                                                }) }
                                            }))
                                            if (position >= 0 && position < samePinnedGroup.lastIndex) add(CodyncMenuItem("Move down", CodyncIcons.ArrowDown, onClick = {
                                                menuBotId = null
                                                val mutable = orderedHostBots.toMutableList()
                                                val current = mutable.indexOfFirst { it.id == bot.id }
                                                val next = mutable.indexOfFirst { it.id == samePinnedGroup[position + 1].id }
                                                val row = mutable[current]
                                                mutable[current] = mutable[next]
                                                mutable[next] = row
                                                scope.launch { session.call("reorderBots", buildJsonObject {
                                                    put("ids", buildJsonArray { mutable.forEach { add(JsonPrimitive(it.id)) } })
                                                }) }
                                            }))
                                            if (bot.unread > 0) add(CodyncMenuItem("Mark as read", CodyncIcons.Done, onClick = {
                                                menuBotId = null
                                                scope.launch { session.call("markRead", buildJsonObject {
                                                    put("botId", bot.id)
                                                    put("all", true)
                                                }) }
                                            }))
                                        },
                                    ) {
                                        GlassIconButton(
                                            CodyncIcons.More,
                                            "Actions for ${bot.name}",
                                            onClick = { menuBotId = bot.id },
                                            size = 38.dp,
                                            iconSize = 20.dp,
                                        )
                                    }
                                }
                            },
                            onClick = { onOpenChat(bot.id, "main") },
                            onLongClick = { menuBotId = bot.id },
                        )
                    }
                }
            }
        }
    }

    if (showNewChat) {
        NewChatSheet(
            session = session,
            bots = hostBots.filterNot { it.hidden },
            onDismiss = { showNewChat = false },
            onOpenChat = { botId, chatId ->
                showNewChat = false
                onOpenChat(botId, chatId)
            },
        )
    }
}
