@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.pokai.codync.android.ui.chat

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.android.fake.FakeBot
import com.pokai.codync.android.fake.FakeChat
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.fake.FakeMessage
import com.pokai.codync.android.fake.FakeTransport
import com.pokai.codync.android.ui.HeaderIconButton
import com.pokai.codync.android.ui.chatSeparator
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncMenuAnchor
import com.pokai.codync.design.CodyncMenuItem
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.GlassSurface
import com.pokai.codync.design.Pill
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.Spinner
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TimelineRow(
    val key: String,
    val separator: String? = null,
    val message: FakeMessage? = null,
)

@Composable
fun ChatScreen(
    fake: FakeHostState,
    hazeState: HazeState,
    botId: String,
    chatId: String,
    initialMessageId: String?,
    onBack: () -> Unit,
    onOpenChat: (botId: String, chatId: String, messageId: String?) -> Unit,
    onOpenFakeHost: () -> Unit,
    onEditBot: (botId: String) -> Unit = {},
    onOpenBotDetails: (botId: String) -> Unit = {},
) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    val bot = fake.bot(botId)
    val chat = fake.chats.firstOrNull { it.id == chatId }
    if (bot == null || chat == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Chat not found", color = colors.text, style = CodyncType.title)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Back to bots", onBack)
            }
        }
        return
    }

    LaunchedEffect(bot.id) { fake.markRead(bot.id) }

    val messages = fake.messagesFor(chat.id)
    var draft by rememberSaveable(chat.id) { mutableStateOf("") }
    var pendingReply by rememberSaveable(chat.id) { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var findQuery by rememberSaveable { mutableStateOf("") }
    var findMatchIndex by rememberSaveable { mutableIntStateOf(0) }
    var showChats by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var rewindTo by remember { mutableStateOf<String?>(null) }
    var attachments by remember { mutableStateOf(listOf<Pair<String, Uri>>()) }
    var dictationError by remember { mutableStateOf<String?>(null) }
    var highlightId by remember { mutableStateOf(initialMessageId) }
    var offLineNote by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var replyJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(chat.id) {
        onDispose { replyJob?.cancel() }
    }

    val matchIds = remember(messages, findQuery) {
        if (findQuery.isBlank()) emptyList()
        else messages.filter { it.text.contains(findQuery, ignoreCase = true) }.map { it.id }
    }
    val rows = remember(messages) {
        buildTimelineRows(messages)
    }
    val atBottom by remember { derivedStateOf { !listState.canScrollForward } }

    LaunchedEffect(findQuery) {
        findMatchIndex = 0
        if (matchIds.isNotEmpty()) {
            highlightId = matchIds.first()
            val index = rows.indexOfFirst { it.message?.id == matchIds.first() }
            if (index >= 0) listState.animateScrollToItem(index)
        } else if (searching) {
            highlightId = null
        }
    }

    LaunchedEffect(chat.id) {
        if (rows.isNotEmpty()) listState.scrollToItem(rows.lastIndex)
    }
    LaunchedEffect(initialMessageId, rows.size) {
        val id = initialMessageId ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.message?.id == id }
        if (index >= 0) {
            listState.scrollToItem(index)
            highlightId = id
        }
    }
    LaunchedEffect(messages.size) {
        if (atBottom && rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex)
    }
    LaunchedEffect(fake.transport) {
        if (fake.transport != FakeTransport.Offline) offLineNote = false
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val name = displayName(context, uri) ?: "Attachment"
            attachments = attachments + (name to uri)
            dictationError = null
        }
    }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) draft = if (draft.isBlank()) text else "$draft $text"
        }
    }

    fun startDictation() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictate a message")
        }
        dictationError = null
        runCatching { dictation.launch(intent) }
            .onFailure { dictationError = "No speech recognition is available on this phone." }
    }

    fun sendNow() {
        val text = draft.trim()
        if (text.isEmpty() && attachments.isEmpty()) return
        if (fake.transport == FakeTransport.Offline) {
            offLineNote = true
            return
        }
        val body = buildString {
            if (text.isNotEmpty()) append(text)
            if (attachments.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(attachments.joinToString(" ") { "📎 ${it.first}" })
            }
        }
        fake.send(chat.id, body)
        draft = ""
        attachments = emptyList()
        offLineNote = false
        pendingReply = true
        replyJob?.cancel()
        replyJob = scope.launch {
            delay(1200)
            fake.reply(chat.id, bot.id, cannedReply(bot))
            pendingReply = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        ChatHeader(
            fake = fake,
            bot = bot,
            chat = chat,
            onBack = onBack,
            showMenu = showMenu,
            onMenu = { showMenu = it },
            onToggleFind = {
                searching = !searching
                if (!searching) {
                    findQuery = ""
                    findMatchIndex = 0
                    highlightId = null
                }
            },
            onOpenDetails = { onOpenBotDetails(bot.id) },
            menuItems = listOf(
                CodyncMenuItem("Find in chat", CodyncIcons.Search) {
                    searching = true
                },
                CodyncMenuItem("Chats", CodyncIcons.Forum) {
                    showChats = true
                },
                CodyncMenuItem("New chat", CodyncIcons.AddComment) {
                    val id = fake.createChat(bot.id)
                    onOpenChat(bot.id, id, null)
                },
                CodyncMenuItem(if (bot.kind == "group") "Group details" else "Bot details", CodyncIcons.Info) {
                    onOpenBotDetails(bot.id)
                },
                CodyncMenuItem(if (bot.kind == "group") "Edit group" else "Edit bot", CodyncIcons.Tune) {
                    onEditBot(bot.id)
                },
                CodyncMenuItem("Copy transcript", CodyncIcons.Copy) {
                    copyToClipboard(context, "Codync transcript", messages.joinToString("\n\n") { it.text })
                },
                CodyncMenuItem("Fixture host", CodyncIcons.Science, onClick = onOpenFakeHost),
            ),
        )

        if (rewindTo != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { rewindTo = null },
                title = { Text("Rewind before this message?", color = colors.text) },
                text = {
                    Text(
                        "Remove this message and everything after it? Work started from this turn will stop.",
                        color = colors.secondary,
                    )
                },
                confirmButton = {
                    PrimaryButton("Rewind", onClick = {
                        fake.rewindChat(chat.id, rewindTo!!)
                        rewindTo = null
                    })
                },
                dismissButton = {
                    SecondaryButton("Cancel", onClick = { rewindTo = null })
                },
                containerColor = colors.surface,
            )
        }
        if (searching) {
            FindBar(
                query = findQuery,
                onQueryChange = { findQuery = it },
                matchCount = matchIds.size,
                matchIndex = findMatchIndex,
                onClose = {
                    searching = false
                    findQuery = ""
                    findMatchIndex = 0
                    highlightId = null
                },
                onPrev = {
                    if (matchIds.isEmpty()) return@FindBar
                    val next = (findMatchIndex - 1 + matchIds.size) % matchIds.size
                    findMatchIndex = next
                    highlightId = matchIds[next]
                    val index = rows.indexOfFirst { it.message?.id == matchIds[next] }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                },
                onNext = {
                    if (matchIds.isEmpty()) return@FindBar
                    val next = (findMatchIndex + 1) % matchIds.size
                    findMatchIndex = next
                    highlightId = matchIds[next]
                    val index = rows.indexOfFirst { it.message?.id == matchIds[next] }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                },
            )
        }
        if (fake.transport == FakeTransport.Offline) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.warning.copy(alpha = 0.12f))
                    .padding(horizontal = 11.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CodyncIcon(CodyncIcons.CloudOff, tint = colors.attention, size = 16.dp)
                Spacer(Modifier.width(7.dp))
                Text(
                    "Offline · showing cached chat. Sending waits for a link.",
                    color = colors.attention,
                    style = CodyncType.caption,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                items(rows.size, key = { rows[it].key }) { index ->
                    val row = rows[index]
                    if (row.separator != null) {
                        Text(
                            row.separator,
                            color = colors.tertiary,
                            style = CodyncType.caption,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    } else if (row.message != null) {
                        MessageRow(
                            fake = fake,
                            chat = chat,
                            bot = bot,
                            message = row.message,
                            highlighted = highlightId == row.message.id,
                            onCopy = { copyToClipboard(context, "Codync message", row.message.text) },
                            onRewind = { rewindTo = row.message.id },
                            onResolve = { fake.resolveNeedByMessage(row.message.id) },
                        )
                    }
                }
                if (pendingReply) {
                    item(key = "working") {
                        WorkingRow(bot = bot)
                    }
                }
                item(key = "bottom_space") { Spacer(Modifier.height(6.dp)) }
            }

            if (!atBottom) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .clip(CircleShape)
                        .background(colors.bubbleUser)
                        .clickable {
                            scope.launch { listState.animateScrollToItem(rows.lastIndex.coerceAtLeast(0)) }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodyncIcon(CodyncIcons.ArrowDown, tint = colors.text, size = 15.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("Latest", color = colors.text, style = CodyncType.caption)
                }
            }
        }

        if (offLineNote) {
            Text(
                "Still offline. The message stays in the composer until a link returns.",
                color = colors.attention,
                style = CodyncType.caption,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        if (dictationError != null) {
            Text(
                dictationError!!,
                color = colors.danger,
                style = CodyncType.caption,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }
        Composer(
            hazeState = hazeState,
            bot = bot,
            draft = draft,
            onDraft = { draft = it },
            attachments = attachments,
            onRemoveAttachment = { attachments = attachments - it },
            onAttach = { filePicker.launch("*/*") },
            onDictate = ::startDictation,
            onSend = ::sendNow,
            sending = pendingReply,
            onStop = {
                replyJob?.cancel()
                pendingReply = false
                fake.reply(chat.id, bot.id, "Stopped by you.", kind = "notice")
            },
            enabled = true,
        )
    }

    if (showChats) {
        ChatListSheet(fake = fake, bot = bot, current = chat, onDismiss = { showChats = false }, onOpen = {
            showChats = false
            onOpenChat(bot.id, it.id, null)
        })
    }
}

@Composable
private fun ChatHeader(
    fake: FakeHostState,
    bot: FakeBot,
    chat: FakeChat,
    onBack: () -> Unit,
    showMenu: Boolean,
    onMenu: (Boolean) -> Unit,
    onToggleFind: () -> Unit,
    onOpenDetails: () -> Unit,
    menuItems: List<CodyncMenuItem>,
) {
    val colors = CodyncTheme.colors
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(CodyncIcons.Back, "Back", onBack)
            CharacterAvatar(
                bot.name,
                bot.color,
                bot.shape,
                size = 32.dp,
                character = bot.character,
                modifier = Modifier.clickable(onClick = onOpenDetails),
            )
            Spacer(Modifier.width(9.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onOpenDetails)
                    .padding(vertical = 2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bot.name,
                        color = colors.text,
                        style = CodyncType.headline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (bot.kind == "group") {
                        Spacer(Modifier.width(6.dp))
                        Text("${bot.members.size + 1}", color = colors.tertiary, style = CodyncType.caption2)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(chat.title, color = colors.tertiary, style = CodyncType.caption, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val status = when {
                        fake.transport == FakeTransport.Offline -> "Offline · cached"
                        bot.status == "working" -> "Working · ${bot.activity}"
                        bot.status == "needsInput" -> "Needs you"
                        else -> "${bot.backend} · rev ${fake.rev}"
                    }
                    Text(
                        status,
                        color = if (bot.status == "needsInput") colors.attention else colors.secondary,
                        style = CodyncType.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HeaderIconButton(CodyncIcons.Search, "Find in chat", onToggleFind)
            CodyncMenuAnchor(
                expanded = showMenu,
                onDismissRequest = { onMenu(false) },
                items = menuItems,
            ) {
                HeaderIconButton(CodyncIcons.More, "Chat menu", onClick = { onMenu(true) })
            }
        }
        Box(Modifier.fillMaxWidth().height(0.6.dp).background(colors.border))
    }
}

@Composable
private fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    matchIndex: Int,
    onClose: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = CodyncTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CodyncIcon(CodyncIcons.Search, tint = colors.tertiary, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Find in this chat", color = colors.tertiary, style = CodyncType.body)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(
                        color = colors.text,
                        fontSize = CodyncType.body.fontSize,
                        fontFamily = CodyncType.body.fontFamily,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            if (query.isNotEmpty()) {
                CodyncIcon(
                    CodyncIcons.Close,
                    tint = colors.tertiary,
                    size = 16.dp,
                    contentDescription = "Clear",
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .padding(4.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            HeaderIconButton(CodyncIcons.Close, "Close find", onClose, tint = colors.tertiary)
        }
        if (query.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (matchCount == 0) "No matches"
                    else "${matchIndex + 1} of $matchCount",
                    color = colors.secondary,
                    style = CodyncType.caption,
                    modifier = Modifier.weight(1f),
                )
                HeaderIconButton(
                    CodyncIcons.ChevronUp,
                    "Previous match",
                    onPrev,
                    tint = if (matchCount == 0) colors.tertiary.copy(alpha = 0.4f) else colors.text,
                )
                HeaderIconButton(
                    CodyncIcons.ChevronDown,
                    "Next match",
                    onNext,
                    tint = if (matchCount == 0) colors.tertiary.copy(alpha = 0.4f) else colors.text,
                )
            }
        }
    }
}

@Composable
private fun MessageRow(
    fake: FakeHostState,
    chat: FakeChat,
    bot: FakeBot,
    message: FakeMessage,
    highlighted: Boolean,
    onCopy: () -> Unit,
    onRewind: () -> Unit,
    onResolve: () -> Unit,
) {
    val colors = CodyncTheme.colors
    var menu by remember { mutableStateOf(false) }
    val author = fake.bot(message.authorId)
    val isUser = message.kind == "user"

    when (message.kind) {
        "tool" -> {
            var expanded by remember(message.id) { mutableStateOf(false) }
            CodyncMenuAnchor(
                expanded = menu,
                onDismissRequest = { menu = false },
                items = listOf(CodyncMenuItem("Copy", CodyncIcons.Copy, onClick = onCopy)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Column {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.bubbleAgent)
                            .combinedClickable(onClick = { expanded = !expanded }, onLongClick = { menu = true })
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CodyncIcon(CodyncIcons.Terminal, tint = colors.secondary, size = 15.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            message.text,
                            color = colors.secondary,
                            style = CodyncType.caption,
                            maxLines = if (expanded) 20 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (message.detail != null) {
                            Spacer(Modifier.width(5.dp))
                            CodyncIcon(
                                if (expanded) CodyncIcons.ChevronUp else CodyncIcons.ChevronDown,
                                tint = colors.tertiary,
                                size = 14.dp,
                            )
                        }
                    }
                    AnimatedVisibility(expanded) {
                        if (message.detail != null) {
                            Text(
                                message.detail,
                                color = colors.secondary,
                                style = CodyncType.mono,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.codeBackground)
                                    .padding(10.dp)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
        "approval" -> {
            val open = fake.needsYou.any { it.messageId == message.id }
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CodyncIcon(CodyncIcons.Shield, tint = colors.attention, size = 18.dp)
                            Spacer(Modifier.width(7.dp))
                            Text("Permission requested", color = colors.attention, style = CodyncType.caption.copy(fontWeight = FontWeight.SemiBold))
                            Spacer(Modifier.weight(1f))
                            Text(relativeChatTime(message.createdAt), color = colors.tertiary, style = CodyncType.caption2)
                        }
                        Spacer(Modifier.height(7.dp))
                        Text(message.text, color = colors.text, style = CodyncType.body)
                        if (message.detail != null) {
                            Text(message.detail, color = colors.secondary, style = CodyncType.footnote)
                        }
                        Spacer(Modifier.height(10.dp))
                        if (open) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                PrimaryButton("Allow once", onClick = onResolve)
                                SecondaryButton("Always", onClick = onResolve)
                                DestructiveButton("Deny", onClick = onResolve)
                            }
                        } else {
                            Pill("Answered", fill = colors.added.copy(alpha = 0.12f), color = colors.added, symbol = CodyncIcons.Check)
                        }
                    }
                }
            }
        }
        "notice" -> {
            Text(
                message.text,
                color = colors.tertiary,
                style = CodyncType.caption,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        else -> {
            if (isUser) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    CodyncMenuAnchor(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        items = buildList {
                            add(CodyncMenuItem("Copy", CodyncIcons.Copy, onClick = onCopy))
                            if (isUser) add(CodyncMenuItem("Rewind from here", CodyncIcons.History, onClick = onRewind))
                        },
                        modifier = Modifier.widthIn(max = 320.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                message.text,
                                color = colors.text,
                                style = CodyncType.body,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (highlighted) colors.warning.copy(alpha = 0.20f) else colors.bubbleUser)
                                    .combinedClickable(onClick = {}, onLongClick = { menu = true })
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                            Text(relativeChatTime(message.createdAt), color = colors.tertiary, style = CodyncType.caption2)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    if (chat.botId == bot.id && bot.kind == "group" && author != null && author.id != bot.id) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)) {
                            CharacterAvatar(author.name, author.color, author.shape, size = 20.dp, character = author.character)
                            Spacer(Modifier.width(6.dp))
                            Text(author.name, color = com.pokai.codync.design.theme.AvatarPalette.color(author.color), style = CodyncType.caption.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                    CodyncMenuAnchor(
                        expanded = menu,
                        onDismissRequest = { menu = false },
                        items = listOf(CodyncMenuItem("Copy", CodyncIcons.Copy, onClick = onCopy)),
                        modifier = Modifier.widthIn(max = 360.dp),
                    ) {
                        MarkdownText(
                            source = message.text,
                            color = colors.text,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (highlighted) colors.warning.copy(alpha = 0.16f) else colors.bubbleAgent)
                                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkingRow(bot: FakeBot) {
    val colors = CodyncTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CharacterAvatar(bot.name, bot.color, bot.shape, size = 24.dp, character = bot.character)
        Spacer(Modifier.width(8.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(colors.bubbleAgent)
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spinner(size = 12.dp, color = colors.secondary)
            Spacer(Modifier.width(7.dp))
            Text(
                if (bot.kind == "group") "The room is working…" else "${bot.name} is working…",
                color = colors.secondary,
                style = CodyncType.footnote,
            )
        }
    }
}

@Composable
private fun Composer(
    hazeState: HazeState,
    bot: FakeBot,
    draft: String,
    onDraft: (String) -> Unit,
    attachments: List<Pair<String, Uri>>,
    onRemoveAttachment: (Pair<String, Uri>) -> Unit,
    onAttach: () -> Unit,
    onDictate: () -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    onStop: () -> Unit,
    enabled: Boolean,
) {
    val colors = CodyncTheme.colors
    val canSend = draft.isNotBlank() || attachments.isNotEmpty()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        GlassSurface(
            state = hazeState,
            shape = RoundedCornerShape(27.dp),
            contentPadding = PaddingValues(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (attachments.isNotEmpty()) {
                    Row(
                        Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        attachments.forEach { attachment ->
                            Row(
                                Modifier
                                    .clip(CircleShape)
                                    .background(colors.bubbleAgent)
                                    .padding(start = 9.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CodyncIcon(CodyncIcons.File, tint = colors.secondary, size = 14.dp)
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    attachment.first,
                                    color = colors.text,
                                    style = CodyncType.caption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp),
                                )
                                CodyncIcon(
                                    CodyncIcons.Close,
                                    tint = colors.tertiary,
                                    size = 14.dp,
                                    contentDescription = "Remove attachment",
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onRemoveAttachment(attachment) }
                                        .padding(3.dp),
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    GlassIconButton(CodyncIcons.Attach, "Attach a file or photo", onAttach, size = 38.dp, iconSize = 20.dp, tint = colors.secondary)
                    Box(Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 6.dp)) {
                        if (draft.isEmpty()) {
                            Text(
                                "Message ${bot.name}",
                                color = colors.tertiary,
                                style = CodyncType.body,
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value = draft,
                            onValueChange = onDraft,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = colors.text,
                                fontSize = 16.sp,
                                lineHeight = 21.sp,
                                fontFamily = CodyncType.body.fontFamily,
                            ),
                            maxLines = 5,
                            cursorBrush = SolidColor(colors.text),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                        )
                    }
                    if (sending) {
                        RoundAction(CodyncIcons.Stop, "Stop", onStop, colors.accentFill, colors.onAccent, filled = true)
                    } else if (canSend) {
                        RoundAction(CodyncIcons.Send, "Send", onSend, colors.accentFill, colors.onAccent, filled = true, enabled = enabled)
                    } else {
                        RoundAction(CodyncIcons.Mic, "Dictate", onDictate, colors.accentFill, colors.onAccent, filled = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundAction(
    symbol: com.pokai.codync.design.Symbol,
    label: String,
    onClick: () -> Unit,
    fill: Color,
    tint: Color,
    filled: Boolean,
    enabled: Boolean = true,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(if (filled) fill else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CodyncIcon(symbol, tint = if (enabled) tint else tint.copy(alpha = 0.4f), size = 19.dp, contentDescription = label)
    }
}

@Composable
private fun ChatListSheet(
    fake: FakeHostState,
    bot: FakeBot,
    current: FakeChat,
    onDismiss: () -> Unit,
    onOpen: (FakeChat) -> Unit,
) {
    val colors = CodyncTheme.colors
    val chats = fake.chatsFor(bot.id)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        dragHandle = null,
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chats", color = colors.text, style = CodyncType.title)
                    Text("${bot.name} · ${chats.size} chat${if (chats.size == 1) "" else "s"}", color = colors.secondary, style = CodyncType.footnote)
                }
                GlassIconButton(CodyncIcons.Close, "Close", onDismiss)
            }
            Spacer(Modifier.height(10.dp))
            chats.forEach { chat ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (chat.id == current.id) colors.surface else Color.Transparent)
                        .clickable { onOpen(chat) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodyncIcon(CodyncIcons.Chat, tint = colors.secondary, size = 18.dp)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(chat.title, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.Medium))
                        Text(chat.lastLine, color = colors.secondary, style = CodyncType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (chat.unread > 0) com.pokai.codync.design.UnreadBadge(chat.unread)
                    else if (chat.id == current.id) CodyncIcon(CodyncIcons.Check, tint = colors.text, size = 18.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                "New chat",
                onClick = {
                    val id = fake.createChat(bot.id)
                    onOpen(fake.chats.first { it.id == id })
                },
                modifier = Modifier.fillMaxWidth(),
                leading = CodyncIcons.AddComment,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun buildTimelineRows(messages: List<FakeMessage>): List<TimelineRow> {
    val rows = mutableListOf<TimelineRow>()
    var previous: Long? = null
    messages.forEach { message ->
        if (previous == null || message.createdAt - previous!! > 10 * 60_000) {
            rows += TimelineRow(key = "sep_${message.id}", separator = chatSeparator(message.createdAt))
        }
        rows += TimelineRow(key = message.id, message = message)
        previous = message.createdAt
    }
    return rows
}

private fun cannedReply(bot: FakeBot): String = when (bot.id) {
    "bot_atlas" -> "On it. I'll update the shell and post the APK when it builds."
    "bot_scout" -> "Noted. I'll keep the release notes in sync."
    "bot_nova" -> "Confirmed. I'll keep the dev migration and add android in 0004."
    "bot_echo" -> "I'll run it on API 31 and 36 and report back."
    "bot_room" -> "Atlas: Queued for the room. I'll report back here."
    else -> "Working on it."
}

private fun relativeChatTime(millis: Long): String {
    val seconds = ((System.currentTimeMillis() - millis).coerceAtLeast(0)) / 1000
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h"
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
