package com.pokai.codync.android.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncMenuAnchor
import com.pokai.codync.design.CodyncMenuItem
import com.pokai.codync.design.GlassIconButton
import com.pokai.codync.design.GlassSurface
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Entry
import com.pokai.codync.model.Attachment
import com.pokai.codync.model.WireJson
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.InputStream
import java.util.UUID

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
fun LiveChatScreen(
    session: SessionStore,
    botId: String,
    chatId: String,
    initialMessageId: String? = null,
    hazeState: HazeState,
    onBack: () -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenChat: (String, String) -> Unit,
    onOpenThread: (String, String) -> Unit,
) {
    var showChats by remember(botId, chatId) { mutableStateOf(false) }
    val bot by session.bot(botId).collectAsState(initial = null)
    val rootEntry by session.entry(botId, chatId).collectAsState(initial = null)
    val timelineEntries by session.timelineEntries(botId, chatId).collectAsState(initial = emptyList())
    val chatListFlow = remember(showChats, botId) {
        if (showChats) session.entries(botId) else flowOf(emptyList<Entry>())
    }
    val chatListEntries by chatListFlow.collectAsState(initial = emptyList())
    val connection by session.connection().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    val actionError by session.actionError().collectAsState(initial = null)
    val context = LocalContext.current
    val colors = CodyncTheme.colors
    val startingNewChat = chatId == "new"
    var loadedThread by remember(botId, chatId) { mutableStateOf<List<Entry>>(emptyList()) }
    LaunchedEffect(botId, chatId, connection.kind) {
        if (connection.kind == com.pokai.codync.transport.TransportKind.Offline) return@LaunchedEffect
        session.loadChatTitles(botId)
        session.call("markRead", buildJsonObject {
            put("botId", botId)
            if (chatId.isNotBlank() && chatId != "main" && chatId != "new") put("threadId", chatId)
        })
        if (chatId.isNotBlank() && chatId != "main" && chatId != "new") {
            session.call("thread", buildJsonObject {
                put("botId", botId)
                put("rootId", chatId)
            }).onSuccess { result ->
                loadedThread = result.jsonObject["entries"]?.jsonArray?.mapNotNull { value ->
                    runCatching { WireJson.json.decodeFromJsonElement(Entry.serializer(), value) }.getOrNull()
                }.orEmpty()
            }
        }
    }
    val chatRoomMode = rootEntry?.data?.chat == true
    val threadMode = chatId.isNotBlank() && chatId != "main" && chatId != "new" && !chatRoomMode
    val chatTitles by session.chatTitles(botId).collectAsState(initial = emptyMap())
    val entries = remember(timelineEntries, loadedThread) {
        (timelineEntries + loadedThread).distinctBy { it.id }.sortedBy { it.seq }
    }
    var draft by rememberSaveable(botId, chatId) { mutableStateOf("") }
    var retryNonce by rememberSaveable(botId, chatId) { mutableStateOf<String?>(null) }
    var attachedFiles by remember(botId, chatId) { mutableStateOf<List<PendingFile>>(emptyList()) }
    var sending by remember(botId, chatId) { mutableStateOf(false) }
    var sendError by remember(botId, chatId) { mutableStateOf<String?>(null) }
    var downloadTarget by remember(botId, chatId) { mutableStateOf<Attachment?>(null) }
    var contextMessage by remember(botId, chatId) { mutableStateOf<String?>(null) }
    var rewindTarget by remember(botId, chatId) { mutableStateOf<Entry?>(null) }
    var rewinding by remember(botId, chatId) { mutableStateOf(false) }
    var deleteChatTarget by remember(botId, chatId) { mutableStateOf<String?>(null) }
    var pendingMessageJump by remember(botId, chatId, initialMessageId) { mutableStateOf(initialMessageId) }
    val listState = rememberLazyListState()
    val nearLatest by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            layout.totalItemsCount == 0 || (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 2
        }
    }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var initialScrollDone by remember(botId, chatId) { mutableStateOf(false) }
    val finalListItemIndex = entries.lastIndex + if (threadMode && rootEntry != null) 1 else 0
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val added = uris.mapNotNull { uri -> pendingFile(context, uri) }
        if (added.size != uris.size) sendError = "Some files couldn't be added. Attachments must be under 100 MB each."
        if (attachedFiles.size + added.size > 10) sendError = "You can attach up to 10 files to one message."
        attachedFiles = (attachedFiles + added).distinctBy { it.uri }.take(10)
        retryNonce = null
    }
    val speechRecognition = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognized = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (recognized.isNotBlank()) {
                draft = listOf(draft.trim(), recognized.trim()).filter { it.isNotBlank() }.joinToString(" ")
                retryNonce = null
                sendError = null
            }
        }
    }
    val speechPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) sendError = "Microphone access is needed for dictation."
        else if (!SpeechRecognizer.isRecognitionAvailable(context)) sendError = "Speech recognition isn't available on this device."
        else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your message")
            }
            runCatching { speechRecognition.launch(intent) }
                .onFailure { sendError = it.message ?: "Couldn't start dictation" }
        }
    }
    val saveAttachment = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val file = downloadTarget
        if (uri != null && file != null) {
            scope.launch {
                runCatching { downloadFile(session, context, botId, file, uri) }
                    .onFailure { sendError = it.message ?: "Couldn't save ${file.name}" }
            }
        }
    }
    val chatRows = remember(showChats, chatListEntries, chatTitles) {
        if (!showChats) emptyList() else {
            val repliesByRoot = chatListEntries.asSequence()
                .filter { it.threadId != null }
                .groupBy { it.threadId!! }
            buildList {
                val main = chatListEntries.filter { it.threadId == null && it.data.chat != true }
                if (main.isNotEmpty()) {
                    val firstUser = main.firstOrNull { it.kind == "user" }
                    add(ChatListRow(
                        id = "main",
                        title = chatTitles["main"] ?: firstUser?.data?.text.orEmpty().replace('\n', ' ').take(64).ifBlank { "Main chat" },
                        at = main.maxOfOrNull(Entry::createdAt) ?: 0L,
                        preview = main.lastOrNull()?.data?.text.orEmpty(),
                    ))
                }
                chatListEntries.filter { it.threadId == null && it.data.chat == true }.forEach { root ->
                    val rows = listOf(root) + repliesByRoot[root.id].orEmpty()
                    val fallback = root.data.text.orEmpty().replace('\n', ' ').take(64)
                    add(ChatListRow(
                        id = root.id,
                        title = chatTitles[root.id] ?: fallback.ifBlank { "Chat" },
                        at = rows.maxOfOrNull(Entry::createdAt) ?: 0L,
                        preview = rows.maxByOrNull(Entry::seq)?.data?.text.orEmpty(),
                    ))
                }
                chatTitles.forEach { (id, title) ->
                    if (none { it.id == id }) add(ChatListRow(id, title, 0L, ""))
                }
            }
                .sortedByDescending(ChatListRow::at)
        }
    }

    LaunchedEffect(pendingMessageJump, entries.size) {
        val target = pendingMessageJump ?: return@LaunchedEffect
        session.ensureEntry(botId, target)
        val index = entries.indexOfFirst { it.id == target }
        if (index >= 0) {
            val rootOffset = if (threadMode && rootEntry != null) 1 else 0
            listState.scrollToItem(index + rootOffset)
            pendingMessageJump = null
        }
    }

    LaunchedEffect(entries.lastOrNull()?.id) {
        if (entries.isNotEmpty()) {
            delay(80)
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (!initialScrollDone || total == 0) {
                listState.scrollToItem(finalListItemIndex)
                initialScrollDone = true
            } else if (lastVisible >= total - 2) {
                listState.animateScrollToItem(finalListItemIndex)
            }
        }
    }

    LaunchedEffect(botId, chatId, listState, entries.size, threadMode, chatRoomMode, startingNewChat, initialScrollDone) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { firstVisible ->
                if (initialScrollDone && !threadMode && !chatRoomMode && !startingNewChat && entries.isNotEmpty() && firstVisible <= 1) session.loadOlder(botId)
            }
    }

    if (bot == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("This bot isn't in the host roster yet.", color = colors.text, style = CodyncType.headline)
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Back", onClick = onBack)
        }
        return
    }
    val currentBot = bot ?: return
    val slashQuery = draft.takeIf { it.matches(Regex("^/\\S*$")) }?.drop(1)?.lowercase()
    val offlineCommand = connection.kind == com.pokai.codync.transport.TransportKind.Offline &&
        draft.trim() in setOf("/fast", "/new")
    val slashCommands = if (slashQuery == null || currentBot.kind == "group") emptyList() else buildList {
        if (currentBot.fastMode || currentBot.fastModeSupport != "unsupported") add("fast" to "Fast mode")
        add("new" to "New session")
    }.filter { (name, title) -> name.contains(slashQuery) || title.lowercase().contains(slashQuery) }
    fun runSlashCommand(name: String) {
        if (connection.kind == com.pokai.codync.transport.TransportKind.Offline) {
            sendError = "Commands need a connection to the computer."
            return
        }
        scope.launch {
            val result = if (name == "fast") session.call("updateBot", buildJsonObject {
                put("id", botId)
                put("fastMode", !currentBot.fastMode)
            }) else session.call("newSession", buildJsonObject {
                put("botId", botId)
                if (chatId != "main" && chatId != "new") put("threadId", chatId)
            })
            if (result.isSuccess) draft = ""
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(CodyncIcons.Back, "Back", onClick = onBack)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    currentBot.name,
                    modifier = Modifier.clickable { onOpenDetails(currentBot.id) },
                    color = colors.text,
                    style = CodyncType.headline,
                )
                Text(
                    buildString {
                        when {
                            startingNewChat -> append("New chat · ")
                            chatRoomMode -> append("Chat · ")
                            threadMode -> append("Thread · ")
                        }
                        append(
                            when {
                                connection.kind == com.pokai.codync.transport.TransportKind.Direct -> currentBot.activity.ifBlank { "Direct · ${connection.computer?.name ?: "computer"}" }
                                connection.kind == com.pokai.codync.transport.TransportKind.Relay -> currentBot.activity.ifBlank { "Codync Cloud · ${connection.computer?.name ?: "computer"}" }
                                else -> connection.lastError ?: "Reconnecting…"
                            },
                        )
                    },
                    color = colors.secondary,
                    style = CodyncType.caption,
                    maxLines = 1,
                )
            }
            GlassIconButton(CodyncIcons.Chat, "Chats", onClick = { showChats = true })
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            val threadRoot = rootEntry
            if (threadMode && threadRoot != null) {
                item(key = "thread-root") {
                    SectionCard {
                        Column(Modifier.padding(12.dp)) {
                            Text("Replying to", color = colors.tertiary, style = CodyncType.caption)
                            Spacer(Modifier.height(4.dp))
                            Text(threadRoot.data.text.orEmpty(), color = colors.text, style = CodyncType.body)
                        }
                    }
                }
            }
            items(entries, key = { it.id }, contentType = { it.kind }) { entry ->
                val text = entry.data.text ?: entry.data.output ?: entry.data.detail ?: entry.data.heading.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when (entry.kind) {
                    "user" -> CodyncMenuAnchor(
                        modifier = Modifier.fillMaxWidth(),
                        expanded = contextMessage == entry.id,
                        onDismissRequest = { contextMessage = null },
                        items = if (entry.data.status == "queued") emptyList() else buildList {
                            if (!threadMode && !chatRoomMode && !startingNewChat) add(CodyncMenuItem(
                                "Reply in thread", CodyncIcons.Chat,
                                onClick = { contextMessage = null; onOpenThread(botId, entry.id) },
                            ))
                            add(CodyncMenuItem(
                                "Rewind to before this turn", CodyncIcons.History,
                                onClick = { contextMessage = null; rewindTarget = entry },
                            ))
                            quickReactions.forEach { emoji -> add(CodyncMenuItem(
                                if (emoji in entry.data.reactions.orEmpty()) "Remove $emoji reaction" else "React $emoji",
                                onClick = {
                                    contextMessage = null
                                    scope.launch { session.call("react", buildJsonObject {
                                        put("entryId", entry.id)
                                        put("emoji", emoji)
                                    }) }
                                },
                            )) }
                        },
                    ) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Column(
                                Modifier
                                    .fillMaxWidth(0.86f).clip(RoundedCornerShape(18.dp))
                                    .background(colors.bubbleUser)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { contextMessage = entry.id },
                                    )
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(text, color = colors.text, style = CodyncType.body)
                                if (entry.data.status == "queued") {
                                    Text(
                                        "Waiting for the computer to reconnect",
                                        modifier = Modifier.padding(top = 4.dp),
                                        color = colors.tertiary,
                                        style = CodyncType.caption,
                                    )
                                }
                            }
                        }
                    }
                    "agent" -> CodyncMenuAnchor(
                        modifier = Modifier.fillMaxWidth(),
                        expanded = contextMessage == entry.id,
                        onDismissRequest = { contextMessage = null },
                        items = if (entry.data.final == true) quickReactions.map { emoji ->
                            CodyncMenuItem(
                                if (emoji in entry.data.reactions.orEmpty()) "Remove $emoji reaction" else "React $emoji",
                                onClick = {
                                    contextMessage = null
                                    scope.launch { session.call("react", buildJsonObject {
                                        put("entryId", entry.id)
                                        put("emoji", emoji)
                                    }) }
                                },
                            )
                        } else emptyList(),
                    ) {
                        SectionCard {
                            Column(
                                Modifier
                                    .combinedClickable(onClick = {}, onLongClick = {
                                        if (entry.data.final == true) contextMessage = entry.id
                                    })
                                    .padding(14.dp),
                            ) {
                                val status = entry.data.status
                                if (entry.data.final != true && status != null) {
                                    Text(status, color = colors.tertiary, style = CodyncType.caption)
                                }
                                MarkdownText(text, colors.text)
                                if (!threadMode && !chatRoomMode && !startingNewChat) {
                                    Spacer(Modifier.height(8.dp))
                                    val thread = entry.data.thread
                                    SecondaryButton(
                                        if (thread == null) "Reply in thread" else "${thread.count} replies",
                                        onClick = { onOpenThread(botId, entry.id) },
                                    )
                                }
                            }
                        }
                    }
                    "permission" -> SectionCard {
                        Column(Modifier.padding(14.dp)) {
                            Text(text.ifBlank { "Permission needed" }, color = colors.text, style = CodyncType.body)
                            if (entry.data.status == "pending") {
                                entry.data.options.orEmpty().forEach { option ->
                                    Spacer(Modifier.height(7.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PrimaryButton(option.name, onClick = {
                                            scope.launch { session.respondPermission(entry.id, option.optionId) }
                                        })
                                    }
                                }
                            } else {
                                Text(entry.data.status?.replaceFirstChar { it.uppercase() } ?: "Resolved", color = colors.tertiary, style = CodyncType.caption)
                            }
                        }
                    }
                    "notice" -> Text(text, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = colors.tertiary, style = CodyncType.caption)
                    else -> SectionCard {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.data.title ?: entry.data.toolKind ?: entry.kind.replaceFirstChar { it.uppercase() }, color = colors.secondary, style = CodyncType.caption)
                            if (text.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(text, color = colors.secondary, style = CodyncType.mono)
                            }
                        }
                    }
                }
                entry.data.attachments.orEmpty().forEach { attachment ->
                    SecondaryButton(
                        text = "Save ${attachment.name}",
                        onClick = {
                            downloadTarget = attachment
                            saveAttachment.launch(attachment.name)
                        },
                    )
                }
                if (entry.data.reactions.orEmpty().isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        entry.data.reactions.orEmpty().forEach { emoji ->
                            Text(
                                emoji,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(colors.bubbleUser)
                                    .clickable {
                                        scope.launch {
                                            session.call("react", buildJsonObject {
                                                put("entryId", entry.id)
                                                put("emoji", emoji)
                                            })
                                        }
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                                color = colors.text,
                                style = CodyncType.body,
                            )
                        }
                    }
                }
                }
            }
            }
            if (entries.isNotEmpty() && !nearLatest) {
                GlassIconButton(
                    CodyncIcons.ArrowDown,
                    "Jump to latest",
                    onClick = { scope.launch { listState.animateScrollToItem(finalListItemIndex.coerceAtLeast(0)) } },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                )
            }
        }

        if (attachedFiles.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                attachedFiles.forEach { file ->
                    SecondaryButton("${file.name}  ×", onClick = {
                        attachedFiles = attachedFiles - file
                        retryNonce = null
                    })
                }
            }
        }

        if (connection.kind == com.pokai.codync.transport.TransportKind.Offline) {
            Text(
                if (offlineCommand) "Commands need a connection to the computer."
                else if (chatId == "new") "Connect to start a new chat. Existing chat messages will queue while offline."
                else if (attachedFiles.isNotEmpty()) "Attachments need a connection. Remove them to queue a text message."
                else "Messages queue on this device and send when the computer reconnects.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
                color = colors.tertiary,
                style = CodyncType.caption,
            )
        }

        if (slashCommands.isNotEmpty()) {
            SectionCard(Modifier.padding(horizontal = 14.dp)) {
                slashCommands.forEach { (name, title) ->
                    SettingsRow(
                        title = title,
                        subtitle = if (name == "fast") if (currentBot.fastMode) "Turn off for this bot" else "Turn on for this bot" else "Reset the agent context",
                        icon = if (name == "fast") CodyncIcons.Bolt else CodyncIcons.Refresh,
                        onClick = { runSlashCommand(name) },
                    )
                }
            }
        }

        GlassSurface(
            state = hazeState,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassIconButton(CodyncIcons.Add, "Attach files", onClick = { filePicker.launch("*/*") })
                GlassIconButton(CodyncIcons.Mic, "Dictate message", onClick = { speechPermission.launch(Manifest.permission.RECORD_AUDIO) })
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        retryNonce = null
                    },
                    modifier = Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
                    minLines = 1,
                    maxLines = 5,
                    textStyle = TextStyle(color = colors.text, fontSize = 16.sp, fontFamily = CodyncType.body.fontFamily),
                    cursorBrush = SolidColor(colors.text),
                    decorationBox = { inner ->
                        Box {
                            if (draft.isBlank()) Text(
                                if (threadMode) "Reply in thread…" else "Message ${currentBot.name}…",
                                color = colors.tertiary,
                                style = CodyncType.body,
                            )
                            inner()
                        }
                    },
                )
                PrimaryButton(
                    text = if (sending) "Sending…" else "Send",
                    onClick = {
                        val message = draft.trim()
                        if (message.isNotEmpty() || attachedFiles.isNotEmpty()) {
                            if (currentBot.kind != "group" && attachedFiles.isEmpty() && message == "/fast" &&
                                (currentBot.fastMode || currentBot.fastModeSupport != "unsupported")
                            ) runSlashCommand("fast")
                            else if (currentBot.kind != "group" && attachedFiles.isEmpty() && message == "/new") runSlashCommand("new")
                            else scope.launch {
                                sending = true
                                val nonce = retryNonce ?: UUID.randomUUID().toString().also { retryNonce = it }
                                val result = sendWithFiles(
                                    session = session,
                                    context = context,
                                    botId = currentBot.id,
                                    chatId = when {
                                        startingNewChat -> "new"
                                        threadMode || chatRoomMode -> chatId
                                        else -> "main"
                                    },
                                    text = message,
                                    files = attachedFiles,
                                    clientNonce = nonce,
                                )
                                if (result.isSuccess) {
                                    draft = ""
                                    attachedFiles = emptyList()
                                    retryNonce = null
                                    sendError = null
                                    result.getOrNull()?.takeIf { startingNewChat && it.data.chat == true }?.let {
                                        onOpenChat(botId, it.id)
                                    }
                                } else {
                                    sendError = result.exceptionOrNull()?.message ?: "Couldn't send this message"
                                }
                                sending = false
                            }
                        }
                    },
                    enabled = (draft.isNotBlank() || attachedFiles.isNotEmpty()) && !sending && (
                        connection.kind != com.pokai.codync.transport.TransportKind.Offline ||
                            (attachedFiles.isEmpty() && chatId != "new")
                        ) && !offlineCommand,
                    leading = CodyncIcons.Send,
                )
            }
        }
        if (currentBot.status == "working") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${currentBot.name} is working", modifier = Modifier.weight(1f), color = colors.tertiary, style = CodyncType.caption)
                SecondaryButton("Stop", onClick = { scope.launch { session.call("stop", buildJsonObject { put("botId", currentBot.id) }) } })
            }
        }
        actionError?.let { error ->
            Text(error, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp), color = colors.warning, style = CodyncType.caption)
        }
        sendError?.let { error ->
            Text(error, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp), color = colors.warning, style = CodyncType.caption)
        }
    }
    rewindTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!rewinding) rewindTarget = null },
            title = { Text("Rewind this turn?") },
            text = { Text("Codync will restore the workspace to before this message and remove it and later messages in this conversation. This requires a saved file checkpoint.") },
            confirmButton = {
                TextButton(
                    enabled = !rewinding,
                    onClick = {
                        scope.launch {
                            rewinding = true
                            val result = session.call("rewind", buildJsonObject {
                                put("botId", botId)
                                put("entryId", target.id)
                            })
                            rewinding = false
                            if (result.isSuccess) rewindTarget = null
                        }
                    },
                ) { Text(if (rewinding) "Rewinding…" else "Rewind") }
            },
            dismissButton = {
                TextButton(enabled = !rewinding, onClick = { rewindTarget = null }) { Text("Cancel") }
            },
        )
    }
    if (showChats) {
        ModalBottomSheet(
            onDismissRequest = { showChats = false },
            sheetState = sheetState,
            containerColor = colors.background,
            contentColor = colors.text,
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    "Chats · ${currentBot.name}",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = colors.text,
                    style = CodyncType.headline,
                )
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    SettingsRow(
                        title = "New chat",
                        subtitle = "Start a separate conversation",
                        icon = CodyncIcons.Add,
                        onClick = {
                            showChats = false
                            onOpenChat(botId, "new")
                        },
                    )
                }
                if (chatRows.isEmpty()) {
                    Text(
                        "Your chats appear here after the first message.",
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                        color = colors.tertiary,
                        style = CodyncType.body,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(chatRows, key = { it.id }) { row ->
                            SectionCard {
                                SettingsRow(
                                    title = row.title,
                                    subtitle = row.preview.ifBlank { "No messages yet" },
                                    icon = CodyncIcons.Chat,
                                    trailing = {
                                        if (row.id != "main") {
                                            GlassIconButton(CodyncIcons.Delete, "Delete ${row.title}", onClick = { deleteChatTarget = row.id })
                                        }
                                    },
                                    onClick = {
                                        showChats = false
                                        scope.launch {
                                            if (row.id != "main") session.ensureEntry(botId, row.id)
                                            onOpenChat(botId, row.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    deleteChatTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteChatTarget = null },
            title = { Text("Delete this chat?") },
            text = { Text("This chat and its messages will be removed from ${currentBot.name} on this computer.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val result = session.deleteChat(botId, target)
                        deleteChatTarget = null
                        if (result.isSuccess && target == chatId) {
                            showChats = false
                            onOpenChat(botId, "main")
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteChatTarget = null }) { Text("Cancel") } },
        )
    }
}

private data class ChatListRow(val id: String, val title: String, val at: Long, val preview: String)
private val quickReactions = listOf("👍", "❤️", "🎉", "😄")

private data class PendingFile(val uri: Uri, val name: String, val size: Long)

private fun pendingFile(context: android.content.Context, uri: Uri): PendingFile? = runCatching {
    val resolver = context.contentResolver
    val row = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)
    val metadata = row?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameColumn = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            val sizeColumn = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            (if (nameColumn >= 0) cursor.getString(nameColumn) else "attachment") to
                (if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L)
        } else "attachment" to 0L
    } ?: ("attachment" to 0L)
    require(metadata.second <= 100L * 1024 * 1024 || metadata.second < 0) { "Files must be 100 MB or smaller" }
    PendingFile(uri, metadata.first.substringAfterLast('/').ifBlank { "attachment" }, metadata.second.coerceAtLeast(0))
}.getOrNull()

private suspend fun sendWithFiles(
    session: SessionStore,
    context: android.content.Context,
    botId: String,
    chatId: String,
    text: String,
    files: List<PendingFile>,
    clientNonce: String,
): Result<Entry?> = runCatching {
    val uploadIds = mutableListOf<String>()
    files.forEach { file ->
        val id = UUID.randomUUID().toString()
        context.contentResolver.openInputStream(file.uri)?.use { input ->
            uploadFile(session, botId, id, file.name, input)
        } ?: error("Couldn't open ${file.name}")
        uploadIds += id
    }
    session.send(botId, chatId, text, uploadIds, clientNonce).getOrThrow()
}

private suspend fun uploadFile(
    session: SessionStore,
    botId: String,
    uploadId: String,
    name: String,
    input: InputStream,
) {
    val chunkSize = 384 * 1024
    val buffer = ByteArray(chunkSize)
    var offset = 0L
    var reachedEnd = false
    var finished = false
    while (!finished) {
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) {
                reachedEnd = true
                break
            }
            if (read == 0) continue
            count += read
        }
        if (count == 0 && reachedEnd) {
            session.call("upload", buildJsonObject {
                put("botId", botId)
                put("uploadId", uploadId)
                put("name", name)
                put("data", "")
                put("offset", offset)
                put("done", true)
            }).getOrThrow()
            finished = true
            continue
        }
        require(offset + count <= 100L * 1024 * 1024) { "Files must be 100 MB or smaller" }
        val encoded = Base64.encodeToString(buffer.copyOf(count), Base64.NO_WRAP)
        session.call("upload", buildJsonObject {
            put("botId", botId)
            put("uploadId", uploadId)
            put("name", name)
            put("data", encoded)
            put("offset", offset)
            put("done", reachedEnd)
        }).getOrThrow()
        offset += count
        finished = reachedEnd
    }
}

private suspend fun downloadFile(
    session: SessionStore,
    context: android.content.Context,
    botId: String,
    file: Attachment,
    destination: Uri,
) {
    val output = context.contentResolver.openOutputStream(destination, "w") ?: error("Couldn't open the destination")
    output.use { sink ->
        var offset = 0L
        var size: Long? = null
        while (size == null || offset < size) {
            val response = session.call("readUpload", buildJsonObject {
                put("botId", botId)
                put("uploadId", file.id)
                put("offset", offset)
            }).getOrThrow().jsonObject
            size = response["size"]?.jsonPrimitive?.longOrNull ?: error("Host returned an invalid file size")
            val encoded = response["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val bytes = if (encoded.isEmpty()) ByteArray(0) else Base64.decode(encoded, Base64.DEFAULT)
            if (bytes.isEmpty()) break
            sink.write(bytes)
            offset += bytes.size
            require(offset <= size) { "The downloaded file is larger than the host reported" }
        }
        require(offset == size) { "The download ended early (${offset} of $size bytes)" }
    }
}
