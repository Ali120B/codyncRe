package com.pokai.codync.android.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.fake.FakeChat
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.ui.HeaderIconButton
import com.pokai.codync.android.ui.SearchField
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.android.ui.roster.BotAvatar
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.Pill
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType

@Composable
fun CommandDeckScreen(
    fake: FakeHostState,
    onBack: () -> Unit,
    onOpenChat: (botId: String, chatId: String, messageId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGlassLab: () -> Unit,
    onOpenFakeHost: () -> Unit,
) {
    val colors = CodyncTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val bots = remember(query) {
        fake.visibleBots().filter {
            query.isBlank() || it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
        }
    }
    val chats = remember(query) {
        fake.chats.filter { chat ->
            val bot = fake.bot(chat.botId)
            query.isBlank() || chat.title.contains(query, ignoreCase = true) || chat.lastLine.contains(query, ignoreCase = true) ||
                (bot?.name?.contains(query, ignoreCase = true) == true)
        }.sortedByDescending { it.updatedAt }
    }
    val actions = remember(query) {
        listOf(
            Triple("Settings", "Account, notifications, appearance and about", CodyncIcons.Settings) to onOpenSettings,
            Triple("Glass lab", "Tune blur, tint, refraction and quality", CodyncIcons.Palette) to onOpenGlassLab,
            Triple("Fake host", "M0 connection state and events", CodyncIcons.Science) to onOpenFakeHost,
        ).filter { query.isBlank() || it.first.first.contains(query, ignoreCase = true) || it.first.second.contains(query, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(CodyncIcons.Back, "Back", onBack)
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Bots, chats and actions",
                autofocus = true,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (actions.isNotEmpty()) {
                item { SectionLabel("Actions") }
                items(actions.size, key = { "action_$it" }) { index ->
                    val (action, callback) = actions[index]
                    ResultRow(
                        leading = {
                            SectionCard(Modifier.width(38.dp).height(38.dp)) {
                                Column(Modifier.fillMaxWidth().height(38.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    CodyncIcon(action.third, tint = colors.text, size = 19.dp)
                                }
                            }
                        },
                        title = action.first,
                        subtitle = action.second,
                        onClick = callback,
                    )
                }
            }

            if (bots.isNotEmpty()) {
                item { SectionLabel("Bots", trailing = "${bots.size}") }
                items(bots, key = { "bot_${it.id}" }) { bot ->
                    ResultRow(
                        leading = { BotAvatar(fake, bot, size = 38.dp) },
                        title = bot.name,
                        subtitle = "${fake.computer(bot.computerId)?.name ?: "Computer"} · ${bot.lastLine}",
                        onClick = { onOpenChat(bot.id, fake.mainChatId(bot.id), null) },
                        trailing = if (bot.unread > 0) ({ com.pokai.codync.design.UnreadBadge(bot.unread) }) else null,
                    )
                }
            }

            if (chats.isNotEmpty()) {
                item { SectionLabel("Chats", trailing = "${chats.size}") }
                items(chats, key = { "chat_${it.id}" }) { chat ->
                    val bot = fake.bot(chat.botId)
                    ResultRow(
                        leading = {
                            SectionCard(Modifier.width(38.dp).height(38.dp)) {
                                Column(Modifier.fillMaxWidth().height(38.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    CodyncIcon(CodyncIcons.Chat, tint = colors.secondary, size = 19.dp)
                                }
                            }
                        },
                        title = chat.title,
                        subtitle = "${bot?.name ?: "Chat"} · ${chat.lastLine}",
                        onClick = { onOpenChat(chat.botId, chat.id, null) },
                        trailing = if (chat.unread > 0) ({ com.pokai.codync.design.UnreadBadge(chat.unread) }) else null,
                    )
                }
            }

            if (actions.isEmpty() && bots.isEmpty() && chats.isEmpty()) {
                item {
                    Column(Modifier.padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No matches", color = colors.text, style = CodyncType.headline)
                        Text("Try a bot name, a chat title or an action.", color = colors.secondary, style = CodyncType.footnote)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = CodyncTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
            Text(subtitle, color = colors.secondary, style = CodyncType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        Spacer(Modifier.width(6.dp))
        CodyncIcon(CodyncIcons.ChevronRight, tint = colors.tertiary, size = 16.dp)
    }
}
