package com.pokai.codync.android.ui.needs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.EmptyState
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.android.ui.LargeHeader
import kotlinx.coroutines.launch

@Composable
fun LiveNeedsScreen(session: SessionStore, onOpenChat: (botId: String) -> Unit) {
    val approvals by session.approvalEntries().collectAsState(initial = emptyList())
    val bots by session.bots().collectAsState(initial = emptyList())
    val computers by session.computers().collectAsState(initial = emptyList())
    val botsById = remember(bots) { bots.associateBy { it.id } }
    val waiting = remember(bots) {
        bots.filter { !it.hidden && it.kind != "group" && (it.status == "needsInput" || it.status == "error") }
            .sortedByDescending { it.lastAt }
    }
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeHeader(
            title = "Needs You",
            subtitle = when {
                approvals.isEmpty() && waiting.isEmpty() -> "Approvals and blocked bots from your paired host"
                approvals.isNotEmpty() -> "${approvals.size} approvals waiting"
                else -> "${waiting.size} bots waiting"
            },
        )
        if (approvals.isEmpty() && waiting.isEmpty()) {
            EmptyState(
                title = if (computers.isEmpty()) "Pair a computer first" else "All clear",
                subtitle = if (computers.isEmpty()) "Pending approvals from a host will show here." else "Permission requests and blocked bots will show here.",
                symbol = CodyncIcons.CheckCircle,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (waiting.isNotEmpty()) {
                    item { com.pokai.codync.android.ui.SectionLabel("Waiting on you", trailing = "${waiting.size}") }
                    items(waiting, key = { "waiting-${it.id}" }) { bot ->
                        SectionCard {
                            SettingsRow(
                                title = bot.name,
                                subtitle = bot.activity.ifBlank {
                                    if (bot.status == "error") "This bot hit an error" else "The bot is waiting for your reply"
                                },
                                icon = if (bot.status == "error") CodyncIcons.Warning else CodyncIcons.Fingerprint,
                                onClick = { onOpenChat(bot.id) },
                            )
                        }
                    }
                }
                if (approvals.isNotEmpty()) {
                    item { com.pokai.codync.android.ui.SectionLabel("Approvals", trailing = "${approvals.size}") }
                    items(approvals, key = { it.id }) { entry ->
                        SectionCard {
                            Column(Modifier.padding(bottom = 10.dp)) {
                                SettingsRow(
                                    title = botsById[entry.botId]?.name ?: "Bot approval",
                                    subtitle = entry.data.text ?: entry.data.detail ?: "Permission requested",
                                    icon = CodyncIcons.Fingerprint,
                                    onClick = { onOpenChat(entry.botId) },
                                )
                                entry.data.options.orEmpty().forEach { option ->
                                    Row(Modifier.padding(start = 54.dp, end = 12.dp, top = 6.dp)) {
                                        val action: () -> Unit = {
                                            scope.launch { session.respondPermission(entry.id, option.optionId) }
                                        }
                                        if (option.kind.startsWith("reject")) {
                                            DestructiveButton(option.name, onClick = action)
                                        } else {
                                            PrimaryButton(option.name, onClick = action)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
