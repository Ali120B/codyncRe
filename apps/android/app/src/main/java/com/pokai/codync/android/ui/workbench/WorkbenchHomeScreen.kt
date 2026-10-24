package com.pokai.codync.android.ui.workbench

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.EmptyState
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow

@Composable
fun WorkbenchHomeScreen(
    session: SessionStore,
    onBack: () -> Unit,
    onOpenBotDetails: (String) -> Unit,
) {
    val bots by session.bots().collectAsState(initial = emptyList())
    val computers by session.computers().collectAsState(initial = emptyList())
    val visibleBots = bots.filterNot { it.hidden }
        .sortedWith(compareByDescending<com.pokai.codync.model.Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() })

    WorkbenchScaffold(
        title = "Workbench",
        subtitle = "Bot details, memory and routines",
        onBack = onBack,
    ) {
        if (visibleBots.isEmpty()) {
            EmptyState(
                title = if (computers.isEmpty()) "Pair a computer first" else "No bots on this host",
                subtitle = if (computers.isEmpty()) "Connect to the desktop host to load its bots and groups." else "The connected host doesn't have any visible bots.",
                symbol = CodyncIcons.Build,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item { SectionLabel("Bots and groups", trailing = "${visibleBots.size}") }
                items(visibleBots, key = { it.id }) { bot ->
                    SectionCard(Modifier.padding(bottom = 8.dp)) {
                        SettingsRow(
                            title = bot.name,
                            subtitle = if (bot.kind == "group") {
                                "Group · ${bot.members.size} members · details, routines"
                            } else {
                                "${bot.backend} · details, memory, routines"
                            },
                            icon = if (bot.kind == "group") CodyncIcons.Groups else CodyncIcons.Bots,
                            onClick = { onOpenBotDetails(bot.id) },
                        )
                    }
                }
            }
        }
    }
}
