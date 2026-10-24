package com.pokai.codync.android.ui.workbench

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.transport.TransportKind

@Composable
fun BotDetailsScreen(
    session: SessionStore,
    botId: String,
    onBack: () -> Unit,
    onOpenChat: (botId: String, chatId: String) -> Unit,
) {
    val colors = CodyncTheme.colors
    val bot by session.bot(botId).collectAsState(initial = null)
    val bots by session.bots().collectAsState(initial = emptyList())
    val connection by session.connection().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    if (bot == null) {
        WorkbenchScaffold("Bot unavailable", "It may have been removed from the host", onBack) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                Text("This bot isn't in the connected host roster.", color = colors.secondary, style = CodyncType.body)
            }
        }
        return
    }
    val current = bot ?: return
    var tab by rememberSaveable(current.id) { mutableStateOf("Details") }
    val tabs = if (current.kind == "group") listOf("Details", "Routines") else listOf("Details", "Memory", "Routines")
    WorkbenchScaffold(
        title = if (current.kind == "group") "Group details" else "Bot details",
        subtitle = current.name,
        onBack = onBack,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                tabs.forEach { item ->
                    FilterChip(text = item, selected = tab == item, onClick = { tab = item })
                }
            }
            when (tab) {
                "Memory" -> BotMemoryTab(session, current.id)
                "Routines" -> BotRoutinesTab(session, current.id)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SectionCard {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                com.pokai.codync.design.CharacterAvatar(
                                    name = current.name,
                                    colorId = current.avatarColor,
                                    shapeId = current.avatarShape,
                                    size = 58.dp,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(current.name, color = colors.text, style = CodyncType.title)
                                    Text(
                                        if (current.kind == "group") "Group · ${current.members.size} members" else current.backend,
                                        color = colors.secondary,
                                        style = CodyncType.footnote,
                                    )
                                    if (current.description.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(current.description, color = colors.tertiary, style = CodyncType.caption)
                                    }
                                }
                                StatusDot(
                                    color = when (current.status) {
                                        "working" -> colors.added
                                        "needsInput", "error" -> colors.warning
                                        else -> colors.tertiary
                                    },
                                )
                            }
                        }
                    }
                    item { SectionLabel("Host details") }
                    item {
                        SectionCard {
                            SettingsRow(
                                title = "Runtime",
                                subtitle = current.backend,
                                icon = CodyncIcons.Terminal,
                                showChevron = false,
                            )
                            SettingsRow(
                                title = "Computer",
                                subtitle = connection.computer?.name ?: "Not connected",
                                icon = CodyncIcons.Computer,
                                showChevron = false,
                            )
                            SettingsRow(
                                title = "Status",
                                subtitle = when (current.status) {
                                    "working" -> current.activity.ifBlank { "Working" }
                                    "needsInput" -> current.activity.ifBlank { "Needs you" }
                                    "error" -> current.activity.ifBlank { "Error" }
                                    else -> "Idle"
                                },
                                icon = CodyncIcons.Bolt,
                                showChevron = false,
                            )
                            SettingsRow(
                                title = "Workspace",
                                subtitle = current.cwd.ifBlank { "Host default" },
                                icon = CodyncIcons.Folder,
                                showChevron = false,
                            )
                        }
                    }
                    if (current.kind == "group") {
                        item { SectionLabel("Members", trailing = "${current.members.size}") }
                        items(current.members, key = { it }) { memberId ->
                            val member = bots.firstOrNull { it.id == memberId }
                            if (member != null) {
                                SectionCard {
                                    SettingsRow(
                                        title = member.name,
                                        subtitle = "${member.backend} · open bot chat",
                                        icon = CodyncIcons.Bots,
                                        onClick = { onOpenChat(member.id, "main") },
                                    )
                                }
                            }
                        }
                    }
                    item {
                        SectionLabel("Chat")
                        SectionCard {
                            SettingsRow(
                                title = "Open conversation",
                                subtitle = current.lastMessage ?: "Continue with ${current.name}",
                                icon = CodyncIcons.Chat,
                                onClick = { onOpenChat(current.id, "main") },
                            )
                        }
                    }
                    item {
                        Text(
                            if (connection.kind != TransportKind.Offline) "Live details from ${connection.computer?.name ?: "the host"}."
                            else connection.lastError ?: "Waiting for the host connection.",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            color = colors.tertiary,
                            style = CodyncType.caption,
                        )
                    }
                }
            }
        }
    }
}
