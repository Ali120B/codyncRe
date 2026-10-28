package com.pokai.codync.android.ui.workbench

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.Pill
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot

/** Live roster map: agents on a ring, groups and their members below. */
@Composable
fun LiveTeamMapScreen(
    session: SessionStore,
    onBack: () -> Unit,
    onOpenBotDetails: (String) -> Unit,
) {
    val colors = CodyncTheme.colors
    val allBots by session.bots().collectAsState(initial = emptyList())
    val bots = remember(allBots) {
        allBots.filterNot { it.hidden }.sortedWith(
            compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() },
        )
    }
    val agents = bots.filter { it.kind != "group" }
    val groups = bots.filter { it.kind == "group" }

    WorkbenchScaffold("Team map", "Bots, rooms and who works with whom", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
            if (agents.isEmpty()) {
                item {
                    Text(
                        "No agents on this computer yet.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        color = colors.tertiary,
                        style = CodyncType.body,
                    )
                }
            } else {
                item {
                    SectionCard {
                        Box(Modifier.fillMaxWidth().height(250.dp).padding(12.dp)) {
                            Canvas(Modifier.fillMaxSize()) {
                                val n = agents.size
                                if (n < 2) return@Canvas
                                val cx = size.width / 2
                                val cy = size.height / 2
                                val r = size.minDimension * 0.34f
                                val pts = agents.mapIndexed { i, _ ->
                                    val a = (i.toFloat() / n) * (Math.PI * 2).toFloat() - (Math.PI / 2).toFloat()
                                    Offset(cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r)
                                }
                                // Rooms draw a dashed edge through their members.
                                groups.forEach { group ->
                                    val memberPoints = group.members.mapNotNull { id ->
                                        agents.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { pts[it] }
                                    }
                                    for (i in 0 until memberPoints.lastIndex) {
                                        drawLine(
                                            color = colors.border,
                                            start = memberPoints[i],
                                            end = memberPoints[i + 1],
                                            strokeWidth = 3f,
                                            cap = StrokeCap.Round,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f)),
                                        )
                                    }
                                }
                            }
                            agents.forEachIndexed { i, bot ->
                                val frac = i.toFloat() / agents.size.coerceAtLeast(1)
                                Row(
                                    Modifier
                                        .align(
                                            when {
                                                frac < 0.25f -> Alignment.TopCenter
                                                frac < 0.5f -> Alignment.CenterEnd
                                                frac < 0.75f -> Alignment.BottomCenter
                                                else -> Alignment.CenterStart
                                            },
                                        )
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.surface)
                                        .clickable { onOpenBotDetails(bot.id) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CharacterAvatar(bot.name, bot.avatarColor, bot.avatarShape, size = 28.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(bot.name, color = colors.text, style = CodyncType.caption.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                }
            }

            item { SectionLabel("Agents", trailing = "${agents.size}") }
            items(agents, key = { it.id }) { bot ->
                SectionCard(Modifier.padding(bottom = 7.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenBotDetails(bot.id) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CharacterAvatar(bot.name, bot.avatarColor, bot.avatarShape, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bot.name, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                            Text(bot.description.ifBlank { bot.backend }, color = colors.secondary, style = CodyncType.footnote)
                        }
                        Pill(
                            when (bot.status) {
                                "working" -> "Working"
                                "needsInput" -> "Needs you"
                                "error" -> "Error"
                                else -> "Idle"
                            },
                            fill = if (bot.status == "working") colors.added.copy(alpha = 0.12f) else colors.surface,
                            color = if (bot.status == "working") colors.added else colors.tertiary,
                        )
                    }
                }
            }

            item { SectionLabel("Rooms", trailing = "${groups.size}") }
            items(groups, key = { it.id }) { room ->
                SectionCard(Modifier.padding(bottom = 7.dp)) {
                    Column(Modifier.fillMaxWidth().clickable { onOpenBotDetails(room.id) }.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(room.name, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(Modifier.width(8.dp))
                            Pill("${room.members.size} bots", fill = colors.bubbleAgent, color = colors.secondary)
                        }
                        Text(
                            room.description.ifBlank { "Room tools · Files · Routines · Skills/Vault" },
                            color = colors.secondary,
                            style = CodyncType.footnote,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            room.members.take(8).forEach { id ->
                                bots.firstOrNull { it.id == id }?.let { member ->
                                    CharacterAvatar(member.name, member.avatarColor, member.avatarShape, size = 24.dp)
                                }
                            }
                            if (room.members.size > 8) {
                                Text("+${room.members.size - 8}", color = colors.tertiary, style = CodyncType.caption)
                            }
                        }
                    }
                }
            }
            if (groups.isEmpty()) {
                item {
                    Text(
                        "Create a room from New chat: pick two or more bots.",
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = colors.tertiary,
                        style = CodyncType.footnote,
                    )
                }
            }
        }
    }
}
