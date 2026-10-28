package com.pokai.codync.android.ui.workbench

import android.graphics.BitmapFactory
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.pokai.codync.android.BuildConfig
import com.pokai.codync.data.EntryTimeline
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot
import com.pokai.codync.model.Entry
import com.pokai.codync.transport.TransportKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

private val ComputerTabs = listOf("Computer", "Cloud", "Phones", "Browser", "Files", "Routines", "Usage")
private val BotComputerTabs = listOf("Computer", "Browser", "Files", "Routines", "Cloud")
private val GroupComputerTabs = listOf("Browser", "Files", "Skills", "Vault", "Routines")

/** Real host-backed computer surfaces shared by bot details and the Workbench. */
@Composable
fun LiveComputerPanel(
    session: SessionStore,
    initialBotId: String? = null,
    detailsMode: Boolean = false,
    onOpenExtensions: () -> Unit = {},
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bots by session.bots().collectAsState(initial = emptyList())
    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    val actionError by session.actionError().collectAsState(initial = null)
    val roster = remember(bots) { bots.sortedBy { it.position } }
    val allBots = remember(roster) { roster.filterNot { it.hidden } }
    var selectedId by rememberSaveable(initialBotId) { mutableStateOf(initialBotId.orEmpty()) }
    var selectedMemberId by rememberSaveable(initialBotId) { mutableStateOf("") }
    var tab by rememberSaveable(initialBotId) { mutableStateOf("Computer") }
    var refresh by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cloud by remember { mutableStateOf<JsonObject?>(null) }
    var phones by remember { mutableStateOf<JsonArray?>(null) }
    var accessRequests by remember { mutableStateOf<JsonArray?>(null) }
    var usage by remember { mutableStateOf<JsonObject?>(null) }
    var browserState by remember { mutableStateOf<JsonObject?>(null) }
    var browserFrame by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var browserUrl by rememberSaveable(initialBotId) { mutableStateOf("") }
    var files by remember { mutableStateOf<JsonObject?>(null) }
    var filePath by rememberSaveable(initialBotId) { mutableStateOf("") }
    var fileToSave by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var revokeTarget by remember { mutableStateOf<JsonObject?>(null) }
    var approveTarget by remember { mutableStateOf<JsonObject?>(null) }

    val selectedBot = roster.firstOrNull { it.id == selectedId }
    val detailTabs = if (selectedBot?.kind == "group") GroupComputerTabs else BotComputerTabs
    val visibleTabs = if (detailsMode) detailTabs else ComputerTabs
    LaunchedEffect(detailsMode, selectedBot?.id, selectedBot?.kind) {
        if (detailsMode && selectedBot?.kind == "group" && tab == "Computer") tab = "Browser"
    }
    LaunchedEffect(detailsMode, selectedBot?.id, selectedBot?.kind, tab) {
        if (detailsMode && tab !in detailTabs) tab = detailTabs.first()
    }
    val selectedMember = selectedBot?.members
        ?.firstOrNull { it == selectedMemberId }
        ?.let { memberId -> roster.firstOrNull { it.id == memberId } }
        ?: selectedBot?.takeIf { it.kind == "group" }?.members?.firstOrNull()?.let { memberId ->
            roster.firstOrNull { it.id == memberId }
        }
    val browserBotId = if (selectedBot?.kind == "group") {
        selectedMember?.id.orEmpty()
    } else selectedBot?.id.orEmpty()
    val targetBotId = selectedBot?.id.orEmpty()
    val groupHasSharedWorkspace = selectedBot?.let { it.kind == "group" && it.workspace.isNotBlank() } == true
    val filesMemberId = selectedBot?.takeIf { it.kind == "group" }?.let { group ->
        selectedMemberId.takeIf { it in group.members }
            ?: group.members.firstOrNull().takeUnless { groupHasSharedWorkspace }.orEmpty()
    }.orEmpty()
    val filesBotId = if (selectedBot?.kind == "group" && filesMemberId.isNotBlank()) filesMemberId else targetBotId

    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val pending = fileToSave
        fileToSave = null
        if (uri != null && pending != null) scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        writeWorkspaceFile(session, pending.third, pending.first, pending.second.toLongOrNull() ?: 0L, output)
                    } ?: kotlin.error("Couldn't open the selected save location")
                }
            }.onFailure { error = it.message ?: "Couldn't save this file" }
            loading = false
        }
    }

    LaunchedEffect(tab, connection.kind, refresh, targetBotId, selectedMemberId) {
        if (connection.kind == TransportKind.Offline) return@LaunchedEffect
        error = null
        when (tab) {
            "Cloud" -> session.call("cloudStatus", buildJsonObject {}).onSuccess { cloud = it.jsonObject }
                .onFailure { error = it.message ?: "Couldn't load Cloud status" }
            "Phones" -> {
                loading = true
                session.call("devices", buildJsonObject {}).onSuccess { phones = it.jsonObject["devices"]?.jsonArray }
                    .onFailure { error = it.message ?: "Couldn't load paired phones" }
                session.call("accessRequests", buildJsonObject {}).onSuccess { accessRequests = it.jsonObject["requests"]?.jsonArray }
                    .onFailure { error = it.message ?: "Couldn't load access requests" }
                loading = false
            }
            "Usage" -> {
                loading = true
                session.call("usage", buildJsonObject { put("refresh", true) })
                    .onSuccess { usage = it.jsonObject }
                    .onFailure { error = it.message ?: "Couldn't load usage" }
                loading = false
            }
            "Files" -> if (detailsMode && selectedBot?.kind == "group" && !groupHasSharedWorkspace) {
                files = null
            } else if (filesBotId.isNotBlank()) {
                loading = true
                session.call("workspaceFiles", buildJsonObject {
                    put("botId", filesBotId)
                    put("path", filePath)
                }).onSuccess { files = it.jsonObject }
                    .onFailure { error = it.message ?: "Couldn't list workspace files" }
                loading = false
            }
        }
    }

    LaunchedEffect(tab, browserBotId, refresh, connection.kind) {
        browserFrame = null
        browserState = null
        if (tab != "Browser" || connection.kind == TransportKind.Offline || browserBotId.isBlank()) return@LaunchedEffect
        while (true) {
            session.call("browserState", buildJsonObject { put("botId", browserBotId) })
                .onSuccess { state ->
                    browserState = state.jsonObject
                    val info = state.jsonObject
                    browserUrl = info.str("url").orEmpty().ifBlank { browserUrl }
                    if (info.flag("running")) {
                        session.call("browserFrame", buildJsonObject { put("botId", browserBotId) })
                            .onSuccess { frame ->
                                val encoded = frame.jsonObject.str("data")
                                if (encoded != null) {
                                    browserFrame = withContext(Dispatchers.Default) {
                                        val bytes = Base64.decode(encoded, Base64.DEFAULT)
                                        BitmapFactory.decodeByteArray(
                                            bytes,
                                            0,
                                            bytes.size,
                                            BitmapFactory.Options().apply { inSampleSize = 2 },
                                        )
                                    }
                                }
                            }
                    }
                }
            delay(1400)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (detailsMode) {
            SectionCard(Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(4.dp).clip(RoundedCornerShape(13.dp))
                        .background(colors.background).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    visibleTabs.forEach { item ->
                        val selected = tab == item
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (selected) colors.surface else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable { tab = item }
                                .padding(horizontal = 3.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                item,
                                color = if (selected) colors.text else colors.secondary,
                                style = CodyncType.caption.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ComputerTabs.forEach { item ->
                    FilterChip(item, selected = tab == item, onClick = { tab = item })
                }
            }
        }

        if (selectedBot == null && allBots.isNotEmpty()) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                allBots.forEach { bot ->
                    FilterChip(bot.name, selected = selectedId == bot.id, onClick = {
                        selectedId = bot.id
                        selectedMemberId = ""
                        filePath = ""
                    })
                }
            }
        } else if (selectedBot?.kind == "group" && tab in if (detailsMode) listOf("Browser", "Skills", "Vault") else listOf("Browser", "Files")) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                selectedBot.members.mapNotNull { id -> roster.firstOrNull { it.id == id } }.forEach { member ->
                    FilterChip(
                        member.name,
                        selected = selectedMemberId == member.id ||
                            (selectedMemberId.isBlank() &&
                                (if (detailsMode) tab in listOf("Browser", "Skills", "Vault")
                                else tab == "Browser" || (tab == "Files" && !groupHasSharedWorkspace)) &&
                                member.id == selectedBot.members.firstOrNull()),
                        onClick = {
                            selectedMemberId = member.id
                            filePath = ""
                            refresh++
                        },
                    )
                }
                if (tab == "Files" && groupHasSharedWorkspace) {
                    FilterChip("Shared folder", selected = selectedMemberId.isBlank(), onClick = {
                        selectedMemberId = ""
                        filePath = ""
                        refresh++
                    })
                }
            }
        }

        (error ?: actionError)?.let {
            Text(it, Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = colors.warning, style = CodyncType.footnote)
        }
        if (connection.kind == TransportKind.Offline) {
            Text(
                connection.lastError ?: "Connect to a computer to load live tools.",
                Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                color = colors.tertiary,
                style = CodyncType.footnote,
            )
        }

        when (tab) {
            "Computer" -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    SectionCard {
                        SettingsRow(
                            title = connection.computer?.name ?: "No computer connected",
                            subtitle = connection.hello?.let { "${it.os} · Codync ${it.version}" } ?: "Pair a computer to control its bots.",
                            icon = CodyncIcons.Computer,
                            showChevron = false,
                            trailing = { Text(connection.kind.name.lowercase(), color = colors.tertiary, style = CodyncType.caption) },
                        )
                    }
                }
                item {
                    SectionCard {
                        SettingsRow(
                            title = selectedBot?.name ?: "Choose a bot",
                            subtitle = selectedBot?.let { if (it.kind == "group") "Group · ${it.members.size} members" else it.backend }
                                ?: "Select a bot below to open its tools.",
                            icon = if (selectedBot?.kind == "group") CodyncIcons.Groups else CodyncIcons.Bots,
                            showChevron = false,
                        )
                    }
                }
                items(allBots, key = { it.id }) { bot ->
                    SectionCard {
                        SettingsRow(
                            title = bot.name,
                            subtitle = if (bot.kind == "group") "Group · ${bot.members.size} members" else bot.backend,
                            icon = if (bot.kind == "group") CodyncIcons.Groups else CodyncIcons.Bots,
                            onClick = { selectedId = bot.id; selectedMemberId = "" },
                        )
                    }
                }
            }
            "Cloud" -> CloudPanel(session, cloud, loading, onRefresh = { refresh++ }, onChanged = { cloud = it })
            "Phones" -> PhonesPanel(
                session = session,
                devices = phones,
                requests = accessRequests,
                loading = loading,
                onRefresh = { refresh++ },
                onChanged = { refresh++ },
                revokeTarget = revokeTarget,
                onRevokeTarget = { revokeTarget = it },
                approveTarget = approveTarget,
                onApproveTarget = { approveTarget = it },
            )
            "Browser" -> {
                val roomBrowserAllowed = selectedBot?.let { bot ->
                    if (bot.kind == "group") selectedMember?.let { member ->
                        bot.memberSettings?.get(member.id)?.browser == true || member.browser
                    } == true else bot.browser
                } == true
                if (!roomBrowserAllowed) {
                    PanelEmpty(
                        if (selectedBot?.kind == "group") "Browser is off for this member" else "Browser is off for this bot",
                        if (selectedBot?.kind == "group") {
                            "Turn it on in this room's member settings or in ${selectedMember?.name ?: "the bot"}'s own settings."
                        } else "Turn it on in this bot's settings to use its browser.",
                    )
                } else BrowserPanel(
                    session,
                    browserBotId,
                    browserState,
                    browserFrame,
                    browserUrl,
                    accessEnabled = roomBrowserAllowed,
                    onUrl = { browserUrl = it },
                    onChanged = { refresh++ },
                )
            }
            "Files" -> if (detailsMode && selectedBot?.kind == "group") {
                GroupFilesPanel(
                    session = session,
                    group = selectedBot,
                    files = files,
                    loading = loading,
                    sharedFolderBotId = targetBotId.takeIf { groupHasSharedWorkspace }.orEmpty(),
                    path = filePath,
                    onPath = { filePath = it; refresh++ },
                    onSave = { path, size ->
                        fileToSave = Triple(path, size.toString(), targetBotId)
                        documentLauncher.launch(path.substringAfterLast('/'))
                    },
                    onShare = { path, size ->
                        scope.launch {
                            loading = true
                            error = null
                            runCatching {
                                val directory = File(context.cacheDir, "shared-files").apply { mkdirs() }
                                val name = path.substringAfterLast('/').ifBlank { "codync-file" }
                                val file = File(directory, "${System.nanoTime()}-$name")
                                withContext(Dispatchers.IO) {
                                    FileOutputStream(file).use { output ->
                                        writeWorkspaceFile(session, targetBotId, path, size, output)
                                    }
                                }
                                val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, name))
                            }.onFailure { error = it.message ?: "Couldn't share this file" }
                            loading = false
                        }
                    },
                )
            } else FilesPanel(
                files,
                selectedBot,
                selectedMemberId,
                filesBotId,
                loading,
                onPath = { filePath = it; refresh++ },
                onSave = { path, size ->
                    fileToSave = Triple(path, size.toString(), filesBotId)
                    documentLauncher.launch(path.substringAfterLast('/'))
                },
                onShare = { path, size ->
                    scope.launch {
                        loading = true
                        error = null
                        runCatching {
                            val directory = File(context.cacheDir, "shared-files").apply { mkdirs() }
                            val name = path.substringAfterLast('/').ifBlank { "codync-file" }
                            val file = File(directory, "${System.nanoTime()}-$name")
                            withContext(Dispatchers.IO) {
                                FileOutputStream(file).use { output ->
                                    writeWorkspaceFile(session, filesBotId, path, size, output)
                                }
                            }
                            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, name))
                        }.onFailure { error = it.message ?: "Couldn't share this file" }
                        loading = false
                    }
                },
            )
            "Skills" -> selectedMember?.let { BotCapabilitiesTab(session, it, skills = true, onOpenExtensions = onOpenExtensions) }
                ?: PanelEmpty("Choose a member", "Select a group member to see its skills.")
            "Vault" -> selectedMember?.let { BotCapabilitiesTab(session, it, skills = false, onOpenExtensions = onOpenExtensions) }
                ?: PanelEmpty("Choose a member", "Select a group member to see its connected apps.")
            "Routines" -> if (selectedBot == null) {
                PanelEmpty("Choose a bot", "Select a bot above to manage its routines.")
            } else BotRoutinesTab(session, selectedBot.id)
            "Usage" -> UsagePanel(usage, loading, onRefresh = { refresh++ })
        }
    }
}

@Composable
private fun CloudPanel(
    session: SessionStore,
    cloud: JsonObject?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onChanged: (JsonObject) -> Unit,
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val status = cloud
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard {
                SettingsRow(
                    title = "Encrypted Cloud relay",
                    subtitle = status?.str("url") ?: "Cloud status loads from the connected computer.",
                    icon = CodyncIcons.Cloud,
                    trailing = {
                        CodyncSwitch(
                            checked = status?.flag("enabled") == true,
                            enabled = !loading,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    session.call("setCloud", buildJsonObject { put("enabled", enabled) })
                                        .onSuccess { onChanged(it.jsonObject) }
                                }
                            },
                        )
                    },
                )
                SettingsRow(
                    title = if (status?.flag("connected") == true) "Connected" else "Connection status",
                            subtitle = status?.str("lastError")
                                ?: status?.get("owner")?.let { runCatching { it.jsonObject.str("email") }.getOrNull() }
                                ?: "End-to-end encrypted through your Codync Cloud account.",
                    icon = if (status?.flag("connected") == true) CodyncIcons.CloudDone else CodyncIcons.CloudOff,
                    showChevron = false,
                )
            }
        }
        item {
            SectionCard {
                SettingsRow(
                    title = "Access approval",
                    subtitle = "Choose how new phones are approved on this computer.",
                    icon = CodyncIcons.Lock,
                    showChevron = false,
                )
                Row(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("code" to "Confirm code", "auto" to "Auto approve").forEach { (value, label) ->
                        FilterChip(label, status?.str("approval") == value, onClick = {
                            scope.launch {
                                session.call("setApproval", buildJsonObject { put("approval", value) })
                                    .onSuccess { onChanged(it.jsonObject) }
                            }
                        })
                    }
                }
            }
        }
        item { SecondaryButton("Refresh", onClick = onRefresh, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun PhonesPanel(
    session: SessionStore,
    devices: JsonArray?,
    requests: JsonArray?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onChanged: () -> Unit,
    revokeTarget: JsonObject?,
    onRevokeTarget: (JsonObject?) -> Unit,
    approveTarget: JsonObject?,
    onApproveTarget: (JsonObject?) -> Unit,
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionLabel("Paired devices", devices?.size?.toString()) }
        items(devices?.toList().orEmpty().mapNotNull { runCatching { it.jsonObject }.getOrNull() }, key = { it.str("key").orEmpty() }) { device ->
            SectionCard {
                SettingsRow(
                    title = device.str("name") ?: "Android device",
                    subtitle = listOfNotNull(
                        device.str("platform"),
                        if (device.flag("connected")) "Connected" else null,
                        device.longValue("lastSeenAt")?.let { "Seen ${com.pokai.codync.android.ui.relativeShort(it)}" },
                    ).joinToString(" · ").ifBlank { "Paired with this computer" },
                    icon = CodyncIcons.Phone,
                    trailing = {
                        TextButton(onClick = { onRevokeTarget(device) }) { Text("Remove", color = colors.warning) }
                    },
                    showChevron = false,
                )
            }
        }
        item { SectionLabel("Access requests", requests?.size?.toString()) }
        items(requests?.toList().orEmpty().mapNotNull { runCatching { it.jsonObject }.getOrNull() }, key = { it.str("requestId").orEmpty() }) { request ->
            SectionCard {
                SettingsRow(
                    title = request.str("deviceName") ?: request.str("email") ?: "New device",
                    subtitle = listOfNotNull(
                        request.str("platform"),
                        request.str("email"),
                        request.str("code")?.let { "Confirm code $it" } ?: "Waiting for code check",
                    ).distinct().joinToString(" · "),
                    icon = CodyncIcons.Lock,
                    showChevron = false,
                    trailing = {
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    session.call("decideAccessRequest", buildJsonObject {
                                        put("requestId", request.str("requestId").orEmpty())
                                        put("approve", false)
                                    }).onSuccess { onChanged() }
                                }
                            }) { Text("Deny") }
                            TextButton(
                                enabled = request.str("code") != null,
                                onClick = { onApproveTarget(request) },
                            ) { Text("Approve") }
                        }
                    },
                )
            }
        }
        item { SecondaryButton("Refresh", onClick = onRefresh, modifier = Modifier.fillMaxWidth()) }
        if (loading && devices == null) item { Text("Loading devices…", color = colors.tertiary, style = CodyncType.footnote) }
    }
    revokeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { onRevokeTarget(null) },
            title = { Text("Remove ${target.str("name") ?: "this device"}?") },
            text = { Text("It will lose access to this computer. It can pair again later.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        session.call("revokeDevice", buildJsonObject { put("key", target.str("key").orEmpty()) })
                            .onSuccess { onRevokeTarget(null); onChanged() }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { onRevokeTarget(null) }) { Text("Cancel") } },
        )
    }
    approveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { onApproveTarget(null) },
            title = { Text("Approve ${target.str("deviceName") ?: "this device"}?") },
            text = {
                Text("Confirm ${target.str("code").orEmpty()} matches the code shown on that phone, then allow it to control bots and chats on this computer.")
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        session.call("decideAccessRequest", buildJsonObject {
                            put("requestId", target.str("requestId").orEmpty())
                            put("approve", true)
                        }).onSuccess { onApproveTarget(null); onChanged() }
                    }
                }) { Text("Approve") }
            },
            dismissButton = { TextButton(onClick = { onApproveTarget(null) }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BrowserPanel(
    session: SessionStore,
    botId: String,
    state: JsonObject?,
    frame: android.graphics.Bitmap?,
    url: String,
    accessEnabled: Boolean,
    onUrl: (String) -> Unit,
    onChanged: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val running = state?.flag("running") == true
    val enabled = accessEnabled
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state?.str("title")?.ifBlank { null } ?: "Browser", color = colors.text, style = CodyncType.title)
                Text(
                    when {
                        botId.isBlank() -> "Choose a bot to control its browser."
                        !enabled -> "Browser access is off for this bot in the current chat."
                        running -> state?.str("url").orEmpty().ifBlank { "Browser is running" }
                        else -> state?.str("engineError") ?: "Browser is stopped. Opening a page starts it on the computer."
                    },
                    color = colors.secondary,
                    style = CodyncType.footnote,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = url,
                        onValueChange = onUrl,
                        singleLine = true,
                        textStyle = TextStyle(color = colors.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(colors.text),
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(colors.surface).padding(12.dp),
                    )
                    PrimaryButton("Open", enabled = enabled && botId.isNotBlank() && url.isNotBlank(), onClick = {
                        scope.launch {
                            session.call("browserOpen", buildJsonObject {
                                put("botId", botId)
                                put("url", url)
                            }).onSuccess { onChanged() }
                        }
                    })
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Reload", enabled = enabled && running, onClick = {
                        scope.launch { session.call("browserReload", buildJsonObject { put("botId", botId) }).onSuccess { onChanged() } }
                    })
                    SecondaryButton(
                        if (state?.str("control") == "user") "Hand back to bot" else "Take control",
                        enabled = enabled && running,
                        onClick = {
                            scope.launch {
                                session.call("browserControl", buildJsonObject {
                                    put("botId", botId)
                                    put("control", if (state?.str("control") == "user") "bot" else "user")
                                }).onSuccess { onChanged() }
                            }
                        },
                    )
                    SecondaryButton("Stop", enabled = enabled && running, onClick = {
                        scope.launch { session.call("browserStop", buildJsonObject { put("botId", botId) }).onSuccess { onChanged() } }
                    })
                }
            }
        }
        if (frame != null && running) {
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = "Live computer browser. Tap to click the page.",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).pointerInput(frame, botId) {
                    detectTapGestures { offset ->
                        scope.launch {
                            session.call("browserClick", buildJsonObject {
                                put("botId", botId)
                                put("x", (offset.x / size.width * 1280f).toDouble())
                                put("y", (offset.y / size.height * 800f).toDouble())
                            }).onSuccess { onChanged() }
                        }
                    }
                },
            )
        } else if (running) {
            Box(
                Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).background(colors.surface),
                contentAlignment = Alignment.Center,
            ) { Text("Loading live browser frame…", color = colors.tertiary, style = CodyncType.footnote) }
        }
        state?.get("tabs")?.let { element ->
            element.jsonArrayOrNull()?.let { tabs ->
                tabs.forEach { tab ->
                    val obj = runCatching { tab.jsonObject }.getOrNull() ?: return@forEach
                    SectionCard {
                        SettingsRow(
                            title = obj.str("title") ?: obj.str("url") ?: "Tab",
                            subtitle = obj.str("url").orEmpty(),
                            icon = CodyncIcons.Globe,
                            onClick = {
                                scope.launch {
                                    session.call("browserSelectTab", buildJsonObject {
                                        put("botId", botId)
                                        put("tabId", obj.str("id").orEmpty())
                                    }).onSuccess { onChanged() }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesPanel(
    files: JsonObject?,
    bot: Bot?,
    selectedMemberId: String,
    filesBotId: String,
    loading: Boolean,
    onPath: (String) -> Unit,
    onSave: (String, Long) -> Unit,
    onShare: (String, Long) -> Unit,
) {
    val colors = CodyncTheme.colors
    if (bot == null || filesBotId.isBlank()) {
        PanelEmpty("Choose a bot", "Select a bot above to browse its computer files.")
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            SectionCard {
                SettingsRow(
                    title = files?.str("name") ?: if (bot.kind == "group" && selectedMemberId.isBlank()) "Shared workspace" else "Workspace files",
                    subtitle = files?.str("path")?.takeIf { it.isNotBlank() }
                        ?: files?.str("name")
                        ?: bot.workspace.ifBlank { bot.cwd.ifBlank { "Host-managed workspace" } },
                    icon = CodyncIcons.Folder,
                    showChevron = false,
                    trailing = {
                        if (files?.str("parent") != null) TextButton(onClick = { onPath(files?.str("parent").orEmpty()) }) { Text("Up") }
                    },
                )
            }
        }
        files?.get("dirs")?.jsonArrayOrNull()?.let { dirs ->
            items(dirs.toList().mapNotNull { runCatching { it.jsonObject }.getOrNull() }, key = { it.str("path").orEmpty() }) { dir ->
                SectionCard {
                    SettingsRow(
                        title = dir.str("name") ?: "Folder",
                        subtitle = dir.str("path").orEmpty(),
                        icon = CodyncIcons.Folder,
                        onClick = { onPath(dir.str("path").orEmpty()) },
                    )
                }
            }
        }
        files?.get("files")?.jsonArrayOrNull()?.let { entries ->
            items(entries.toList().mapNotNull { runCatching { it.jsonObject }.getOrNull() }, key = { it.str("path").orEmpty() }) { file ->
                val filePath = file.str("path").orEmpty()
                val bytes = file.longValue("size") ?: 0L
                SectionCard {
                    SettingsRow(
                        title = file.str("name") ?: "File",
                        subtitle = "${formatBytes(bytes)} · ${filePath}",
                        icon = CodyncIcons.File,
                        showChevron = false,
                        trailing = {
                            Row {
                                TextButton(onClick = { onSave(filePath, bytes) }) { Text("Save") }
                                TextButton(onClick = { onShare(filePath, bytes) }) { Text("Share") }
                            }
                        },
                    )
                }
            }
        }
        if (loading && files == null) item { Text("Loading files…", color = colors.tertiary, style = CodyncType.footnote) }
    }
}

private data class GroupFileEdit(val entry: Entry, val path: String, val added: Int, val removed: Int, val isNew: Boolean)

@Composable
private fun GroupFilesPanel(
    session: SessionStore,
    group: Bot,
    files: JsonObject?,
    loading: Boolean,
    sharedFolderBotId: String,
    path: String,
    onPath: (String) -> Unit,
    onSave: (String, Long) -> Unit,
    onShare: (String, Long) -> Unit,
) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    val members by session.bots().collectAsState(initial = emptyList())
    val timeline by session.entries(group.id).collectAsState(initial = EntryTimeline(emptyList()))
    var browsingSharedFolder by remember(group.id) { mutableStateOf(false) }
    var copiedPath by remember(group.id) { mutableStateOf<String?>(null) }
    val edits = remember(timeline.entries) {
        val latest = linkedMapOf<String, GroupFileEdit>()
        timeline.entries.asSequence().filter { it.kind == "tool" }.forEach { entry ->
            entry.data.diffs.orEmpty().forEach { diff ->
                val old = latest[diff.path]
                if (old == null || old.entry.createdAt <= entry.createdAt) {
                    latest[diff.path] = GroupFileEdit(entry, diff.path, diff.added, diff.removed, diff.isNew)
                }
            }
        }
        latest.values.sortedByDescending { it.entry.createdAt }
    }

    if (browsingSharedFolder && sharedFolderBotId.isNotBlank()) {
        Column(Modifier.fillMaxSize()) {
            SecondaryButton(
                "Edited by this group",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                onClick = { browsingSharedFolder = false },
            )
            FilesPanel(
                files = files,
                bot = group,
                selectedMemberId = "",
                filesBotId = sharedFolderBotId,
                loading = loading,
                onPath = onPath,
                onSave = onSave,
                onShare = onShare,
            )
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        item {
            SectionCard {
                SettingsRow(
                    title = if (sharedFolderBotId.isNotBlank()) "Browse the shared folder" else "Members use their own folders",
                    subtitle = group.workspace.ifBlank { "Set a shared folder in group settings to browse it here." },
                    icon = CodyncIcons.Folder,
                    showChevron = sharedFolderBotId.isNotBlank(),
                    onClick = if (sharedFolderBotId.isNotBlank()) ({ browsingSharedFolder = true; onPath("") }) else null,
                )
            }
        }
        item {
            SectionLabel("Edited by this group", trailing = edits.size.toString())
        }
        if (edits.isEmpty()) {
            item {
                Text(
                    "No files changed in this group's chats yet.",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    color = colors.tertiary,
                    style = CodyncType.footnote,
                )
            }
        }
        items(edits, key = { it.path }) { edit ->
            val member = members.firstOrNull { it.id == edit.entry.data.author }
            SectionCard {
                SettingsRow(
                    title = edit.path.substringAfterLast('/').ifBlank { edit.path },
                    subtitle = buildString {
                        append(edit.path)
                        append(" · ")
                        append(member?.name ?: "Group member")
                        append(" · ")
                        append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(edit.entry.createdAt)))
                    },
                    icon = CodyncIcons.File,
                    showChevron = false,
                    trailing = {
                        Text(
                            buildString {
                                if (edit.added > 0) append("+${edit.added}")
                                if (edit.removed > 0) append(" −${edit.removed}")
                                if (edit.isNew) append(" new")
                                if (copiedPath == edit.path) append(" · copied")
                            }.ifBlank { "Copy" },
                            color = colors.tertiary,
                            style = CodyncType.caption,
                        )
                    },
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Codync file path", edit.path))
                        copiedPath = edit.path
                    },
                )
            }
        }
    }
}

@Composable
private fun UsagePanel(usage: JsonObject?, loading: Boolean, onRefresh: () -> Unit) {
    val colors = CodyncTheme.colors
    val providers = usage?.get("providers")?.jsonArrayOrNull().orEmpty()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionCard {
                SettingsRow(
                    title = "Provider usage",
                    subtitle = "Usage is read from the connected computer's agent sessions.",
                    icon = CodyncIcons.Bolt,
                    showChevron = false,
                    trailing = { TextButton(onClick = onRefresh) { Text("Refresh") } },
                )
            }
        }
        items(providers, key = { it.jsonObject.str("id") ?: it.hashCode().toString() }) { providerElement ->
            val provider = runCatching { providerElement.jsonObject }.getOrNull() ?: return@items
            SectionCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(provider.str("name") ?: provider.str("id") ?: "Provider", color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                    provider["windows"]?.jsonArrayOrNull()?.forEach { windowElement ->
                        val window = runCatching { windowElement.jsonObject }.getOrNull() ?: return@forEach
                        val percent = (window["percent"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f).coerceIn(0f, 100f)
                        SettingsRow(
                            title = window.str("label") ?: "Usage",
                            subtitle = window.str("resetsText") ?: window.longValue("resetsAt")?.let { "Resets ${com.pokai.codync.android.ui.relativeShort(it)}" }.orEmpty(),
                            showChevron = false,
                            trailing = { Text("${percent.toInt()}%", color = if (percent >= 80) colors.warning else colors.secondary, style = CodyncType.footnote) },
                        )
                    }
                }
            }
        }
        if (providers.isEmpty()) item {
            PanelEmpty("No usage data yet", if (loading) "Refreshing provider limits…" else "Usage appears when supported agent sessions report limits to the host.")
        }
    }
}

@Composable
private fun PanelEmpty(title: String, subtitle: String) {
    val colors = CodyncTheme.colors
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = colors.text, style = CodyncType.title)
        Text(subtitle, color = colors.secondary, style = CodyncType.footnote)
    }
}

@Composable
private fun SectionLabel(title: String, trailing: String? = null) {
    val colors = CodyncTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colors.secondary, style = CodyncType.caption.copy(fontWeight = FontWeight.SemiBold))
        if (trailing != null) Text(trailing, Modifier.padding(start = 8.dp), color = colors.tertiary, style = CodyncType.caption)
    }
}

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.flag(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull == true
private fun JsonObject.longValue(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull(): JsonArray? = runCatching { jsonArray }.getOrNull()

private fun formatBytes(size: Long): String = when {
    size < 1024 -> "$size B"
    size < 1024 * 1024 -> "${size / 1024} KB"
    else -> "${size / (1024 * 1024)} MB"
}

private suspend fun writeWorkspaceFile(
    session: SessionStore,
    botId: String,
    path: String,
    expectedSize: Long,
    output: OutputStream,
) {
    var offset = 0L
    while (offset < expectedSize) {
        val response = session.call("readWorkspaceFile", buildJsonObject {
            put("botId", botId)
            put("path", path)
            put("offset", offset)
        }).getOrThrow().jsonObject
        val hostSize = response.longValue("size") ?: error("The host returned an invalid file size")
        check(hostSize == expectedSize) { "The file changed while it was being copied" }
        val bytes = Base64.decode(response.str("data").orEmpty(), Base64.DEFAULT)
        check(bytes.isNotEmpty()) { "The host stopped before the file was complete" }
        check(bytes.size.toLong() <= expectedSize - offset) { "The host returned extra file data" }
        output.write(bytes)
        offset += bytes.size
    }
}
