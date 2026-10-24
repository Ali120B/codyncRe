@file:OptIn(ExperimentalFoundationApi::class)

package com.pokai.codync.android.ui.needs

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.fake.FakeNeedsItem
import com.pokai.codync.android.fake.FakeTransport
import com.pokai.codync.android.ui.LargeHeader
import com.pokai.codync.android.ui.relativeShort
import com.pokai.codync.android.ui.roster.BotAvatar
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.EmptyState
import com.pokai.codync.design.Pill
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun NeedsYouScreen(
    fake: FakeHostState,
    onOpenChat: (botId: String, chatId: String, messageId: String?) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val items = fake.needsYou.sortedByDescending { it.createdAt }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LargeHeader(
            title = "Needs You",
            subtitle = if (items.isEmpty()) "Everything is clear" else "${items.size} waiting across ${items.map { it.computerId }.distinct().size} computers",
        )

        CommandSearchBar(onClick = onOpenSearch)

        if (items.isEmpty()) {
            EmptyState(
                title = "All clear",
                subtitle = "Approvals, failed sends and bots waiting for an answer show up here.",
                symbol = CodyncIcons.CheckCircle,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 118.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    NeedsCard(fake, item, onOpenChat)
                }
            }
        }
    }
}

@Composable
private fun CommandSearchBar(onClick: () -> Unit) {
    val colors = CodyncTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodyncIcon(CodyncIcons.Search, tint = colors.tertiary, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text("Search bots, chats and actions", color = colors.tertiary, style = CodyncType.body, modifier = Modifier.weight(1f))
        Pill("⌘ K", fill = colors.bubbleAgent, color = colors.tertiary)
    }
}

@Composable
private fun NeedsCard(
    fake: FakeHostState,
    item: FakeNeedsItem,
    onOpenChat: (botId: String, chatId: String, messageId: String?) -> Unit,
) {
    val colors = CodyncTheme.colors
    val bot = fake.bot(item.botId) ?: return
    val computer = fake.computer(bot.computerId)
    val scope = rememberCoroutineScope()
    var waiting by remember { mutableStateOf(false) }

    SectionCard {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BotAvatar(fake, bot, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bot.name, color = colors.text, style = CodyncType.subheadline.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            computer?.name ?: "Computer",
                            color = colors.tertiary,
                            style = CodyncType.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(relativeShort(item.createdAt), color = colors.tertiary, style = CodyncType.caption)
                }
                Pill(
                    text = when (item.kind) {
                        "approval" -> "Approval"
                        "question" -> "Answer"
                        "failed" -> "Failed"
                        "signin" -> "Sign-in"
                        else -> "Needs you"
                    },
                    color = when (item.kind) {
                        "approval", "question" -> colors.attention
                        "failed" -> colors.danger
                        else -> colors.secondary
                    },
                    fill = when (item.kind) {
                        "approval", "question" -> colors.warning.copy(alpha = 0.13f)
                        "failed" -> colors.danger.copy(alpha = 0.10f)
                        else -> colors.surface
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(item.title, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.Medium))
            Text(item.detail, color = colors.secondary, style = CodyncType.footnote)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (item.kind) {
                    "approval" -> {
                        PrimaryButton("Allow once", onClick = {
                            fake.resolveNeed(item.id)
                            fake.reply(item.chatId, item.botId, "Approved. Running it now.")
                        })
                        SecondaryButton("Always", onClick = {
                            fake.resolveNeed(item.id)
                            fake.reply(item.chatId, item.botId, "Allowed from now on. Running it now.")
                        })
                        DestructiveButton("Deny", onClick = {
                            fake.resolveNeed(item.id)
                            fake.reply(item.chatId, item.botId, "Denied. I'll stop here.")
                        })
                    }
                    "failed" -> {
                        PrimaryButton(
                            text = if (waiting) "Waiting for a link" else "Retry",
                            onClick = {
                                if (fake.transport == FakeTransport.Offline || fake.computer(bot.computerId)?.transport == FakeTransport.Offline) {
                                    waiting = true
                                } else {
                                    fake.resolveNeed(item.id)
                                    scope.launch {
                                        delay(400)
                                        fake.reply(item.chatId, item.botId, "Resent. The computer accepted it.")
                                    }
                                }
                            },
                        )
                        SecondaryButton("Open chat", onClick = {
                            fake.markRead(bot.id)
                            onOpenChat(bot.id, item.chatId, item.messageId)
                        })
                    }
                    else -> {
                        PrimaryButton("Open chat", onClick = {
                            fake.markRead(bot.id)
                            onOpenChat(bot.id, item.chatId, item.messageId)
                        })
                        SecondaryButton("Dismiss", onClick = { fake.resolveNeed(item.id) })
                    }
                }
            }
            if (waiting) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "The phone keeps the message ready; it sends when a link returns.",
                    color = colors.tertiary,
                    style = CodyncType.caption,
                )
            }
        }
    }
}
