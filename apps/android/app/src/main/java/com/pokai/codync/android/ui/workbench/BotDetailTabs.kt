package com.pokai.codync.android.ui.workbench

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.DateFormat as AndroidDateFormat
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.UUID

@Composable
internal fun BotLibraryTab(botName: String) {
    val colors = CodyncTheme.colors
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text("Library", color = colors.text, style = CodyncType.title)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pages, files and apps $botName makes show up here.",
            color = colors.secondary,
            style = CodyncType.body,
        )
    }
}

@Composable
internal fun BotCapabilitiesTab(
    session: SessionStore,
    bot: Bot,
    skills: Boolean,
    onOpenExtensions: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    var capabilities by remember(bot.id, skills) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var selected by remember(bot.id, skills) {
        mutableStateOf((if (skills) bot.skills else bot.connectors).toSet())
    }
    var loading by remember(bot.id, skills) { mutableStateOf(false) }
    var saving by remember(bot.id, skills) { mutableStateOf(false) }
    var error by remember(bot.id, skills) { mutableStateOf<String?>(null) }
    var message by remember(bot.id, skills) { mutableStateOf<String?>(null) }
    val method = if (skills) "skills" else "connectors"
    val title = if (skills) "Skills" else "Apps and connectors"

    LaunchedEffect(bot.id, skills, connection.hello?.hostId) {
        if (connection.hello == null) return@LaunchedEffect
        loading = true
        error = null
        session.call(method, buildJsonObject {}).onSuccess { response ->
            capabilities = response.jsonObject["items"]?.jsonArray
                ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()
        }.onFailure { error = it.message ?: "Couldn't load $title" }
        loading = false
    }

    LaunchedEffect(bot.id, skills, bot.skills, bot.connectors) {
        selected = (if (skills) bot.skills else bot.connectors).toSet()
    }

    fun save(next: Set<String>) {
        selected = next
        message = null
        error = null
        scope.launch {
            saving = true
            val result = session.call("updateBot", buildJsonObject {
                put("id", bot.id)
                put(if (skills) "skills" else "connectors", buildJsonArray {
                    next.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                })
            })
            saving = false
            result.onSuccess { message = "Saved for ${bot.name}." }
                .onFailure {
                    selected = (if (skills) bot.skills else bot.connectors).toSet()
                    error = it.message ?: "Couldn't update $title"
                }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingsRow(
                title = title,
                subtitle = if (skills) "Things ${bot.name} has learned to do." else "Apps this bot is allowed to use.",
                icon = if (skills) CodyncIcons.Memory else CodyncIcons.Extension,
                trailing = {
                    SecondaryButton("Extensions", onClick = onOpenExtensions, enabled = !saving)
                },
                showChevron = false,
            )
        }
        when {
            connection.hello == null -> Text(
                "Connect to the computer to manage this bot's ${if (skills) "skills" else "apps"}.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = colors.tertiary,
                style = CodyncType.footnote,
            )
            loading -> Text("Loading $title…", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = colors.tertiary, style = CodyncType.footnote)
            capabilities.isEmpty() -> Text(
                if (skills) "No skills installed on this computer yet." else "No apps or connectors are installed on this computer yet.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = colors.tertiary,
                style = CodyncType.footnote,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                if (!skills) {
                    item {
                        SectionCard(Modifier.padding(bottom = 8.dp)) {
                            SettingsRow(
                                title = "Let ${bot.name} use apps",
                                subtitle = "Switch off to keep every app disconnected from this bot.",
                                showChevron = false,
                                trailing = {
                                    CodyncSwitch(
                                        checked = selected.isNotEmpty(),
                                        enabled = !saving && connection.hello != null,
                                        onCheckedChange = { enabled ->
                                            save(if (enabled) capabilities.mapNotNull { it.text("id") }.toSet() else emptySet())
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                items(capabilities, key = { it.text("id") ?: it.hashCode().toString() }) { item ->
                    val id = item.text("id") ?: return@items
                    SectionCard(Modifier.padding(bottom = 7.dp)) {
                        SettingsRow(
                            title = item.text("name") ?: id,
                            subtitle = item.text("description").orEmpty().ifBlank {
                                item.text("kind") ?: if (skills) "Skill" else "Connected app"
                            },
                            icon = if (skills) CodyncIcons.Memory else CodyncIcons.Extension,
                            showChevron = false,
                            trailing = {
                                CodyncSwitch(
                                    checked = id in selected,
                                    enabled = !saving && connection.hello != null,
                                    onCheckedChange = { enabled ->
                                        save(if (enabled) selected + id else selected - id)
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
        error?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp), color = colors.warning, style = CodyncType.footnote) }
        message?.let { Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp), color = colors.tertiary, style = CodyncType.footnote) }
    }
}

@Composable
internal fun BotMemoryTab(session: SessionStore, botId: String) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember(botId) { mutableStateOf("") }
    var title by remember(botId) { mutableStateOf("") }
    var content by remember(botId) { mutableStateOf("") }
    var facts by remember(botId) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember(botId) { mutableStateOf(false) }
    var error by remember(botId) { mutableStateOf<String?>(null) }
    var transferMessage by remember(botId) { mutableStateOf<String?>(null) }
    var transferring by remember(botId) { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        val result = session.call("memory", buildJsonObject {
            put("botId", botId)
            put("query", query)
            put("limit", 50)
            put("offset", 0)
        })
        result.onSuccess { response ->
            facts = response.jsonObject["facts"]?.jsonArray?.mapNotNull { it.objectOrNull() }.orEmpty()
            error = null
        }.onFailure { error = it.message ?: "Couldn't load this bot's memory" }
        loading = false
    }

    LaunchedEffect(botId, query) {
        delay(if (query.isBlank()) 0 else 250)
        load()
    }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { destination ->
        if (destination != null) scope.launch {
            transferring = true
            transferMessage = "Exporting memory…"
            runCatching {
                withContext(Dispatchers.IO) {
                    val response = session.call("exportMemory", buildJsonObject { put("botId", botId) }).getOrThrow().jsonObject
                    context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                        val json = response["json"]?.jsonPrimitive?.contentOrNull
                        if (json != null) {
                            output.write(json.toByteArray(Charsets.UTF_8))
                        } else {
                            val uploadId = response["uploadId"]?.jsonPrimitive?.contentOrNull
                                ?: error("Host returned no memory backup")
                            var offset = 0L
                            var expectedSize: Long? = null
                            while (true) {
                                val chunk = session.call("readUpload", buildJsonObject {
                                    put("botId", botId)
                                    put("uploadId", uploadId)
                                    put("offset", offset)
                                }).getOrThrow().jsonObject
                                val size = chunk["size"]?.jsonPrimitive?.longOrNull
                                    ?: error("Host returned an invalid backup size")
                                require(expectedSize == null || expectedSize == size) { "Host changed the backup size while downloading" }
                                expectedSize = size
                                if (offset >= size) break
                                val encoded = chunk["data"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                val bytes = if (encoded.isEmpty()) ByteArray(0) else Base64.decode(encoded, Base64.DEFAULT)
                                if (bytes.isEmpty()) break
                                output.write(bytes)
                                offset += bytes.size
                                require(offset <= size) { "Host returned more data than expected" }
                            }
                            val expected = expectedSize ?: error("Host returned no backup size")
                            require(offset == expected) { "Backup download ended early ($offset of $expected bytes)" }
                        }
                    } ?: error("Couldn't open the selected file")
                }
            }.onSuccess {
                transferMessage = "Memory backup exported."
            }.onFailure {
                transferMessage = null
                error = it.message ?: "Couldn't export memory"
            }
            transferring = false
        }
    }

    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { source ->
        if (source != null) scope.launch {
            transferring = true
            transferMessage = "Uploading memory backup…"
            runCatching {
                withContext(Dispatchers.IO) {
                    val uploadId = UUID.randomUUID().toString()
                    context.contentResolver.openInputStream(source)?.use { input ->
                        uploadMemoryBackup(session, botId, uploadId, input)
                    } ?: error("Couldn't read the selected file")
                    session.call("importMemory", buildJsonObject {
                        put("botId", botId)
                        put("uploadId", uploadId)
                    }).getOrThrow()
                }
            }.onSuccess {
                transferMessage = "Memory backup imported."
                load()
            }.onFailure {
                transferMessage = null
                error = it.message ?: "Couldn't import memory"
            }
            transferring = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DetailSearchField(query, "Search this bot's memory") { query = it }
        SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailField("Title", title) { title = it }
                DetailField("Memory", content, multiline = true) { content = it }
                PrimaryButton(
                    text = if (loading) "Saving…" else "Add memory",
                    onClick = {
                        scope.launch {
                            val result = session.call("saveMemory", buildJsonObject {
                                put("botId", botId)
                                put("title", title.trim().ifBlank { content.trim().take(100) })
                                put("content", content.trim())
                                put("scope", "project")
                            })
                            result.onSuccess {
                                title = ""
                                content = ""
                                load()
                            }.onFailure { error = it.message ?: "Couldn't save this memory" }
                        }
                    },
                    enabled = content.isNotBlank() && !loading,
                    leading = CodyncIcons.Add,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SecondaryButton(
                        text = if (transferring) "Working…" else "Import backup",
                        onClick = { importPicker.launch(arrayOf("application/json", "application/*")) },
                        enabled = !transferring,
                    )
                    SecondaryButton(
                        text = "Export backup",
                        onClick = { exportPicker.launch("codync-memory-${botId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}.json") },
                        enabled = !transferring,
                    )
                }
            }
        }
        transferMessage?.let {
            Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp), color = colors.secondary, style = CodyncType.caption)
        }
        if (error != null) DetailError(error!!)
        if (facts.isEmpty() && !loading && error == null) {
            Text(
                if (query.isBlank()) "No saved memories for this bot yet." else "No memories match this search.",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                color = colors.tertiary,
                style = CodyncType.footnote,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            facts.forEach { fact ->
                val id = fact.text("id").orEmpty()
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Text(
                                fact.text("title") ?: fact.text("kind")?.replaceFirstChar { it.uppercase() } ?: "Memory",
                                color = colors.text,
                                style = CodyncType.body,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                fact.text("scope") ?: "project",
                                color = colors.tertiary,
                                style = CodyncType.caption,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(fact.text("content").orEmpty(), color = colors.secondary, style = CodyncType.footnote)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(
                                text = if (fact.flag("pinned")) "Unpin" else "Pin",
                                onClick = {
                                    scope.launch {
                                        session.call("pinMemory", buildJsonObject {
                                            put("botId", botId)
                                            put("id", id)
                                            put("pinned", !fact.flag("pinned"))
                                        }).onSuccess { load() }.onFailure {
                                            error = it.message ?: "Couldn't update the memory"
                                        }
                                    }
                                },
                            )
                            DestructiveButton("Delete", onClick = {
                                scope.launch {
                                    session.call("forgetMemory", buildJsonObject {
                                        put("botId", botId)
                                        put("id", id)
                                    }).onSuccess { load() }.onFailure {
                                        error = it.message ?: "Couldn't delete the memory"
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}

private suspend fun uploadMemoryBackup(
    session: SessionStore,
    botId: String,
    uploadId: String,
    input: java.io.InputStream,
) {
    val chunkSize = 384 * 1024
    val buffer = ByteArray(chunkSize)
    var offset = 0L
    var ended = false
    var finished = false
    while (!finished) {
        var count = 0
        while (count < buffer.size) {
            val read = input.read(buffer, count, buffer.size - count)
            if (read < 0) {
                ended = true
                break
            }
            if (read == 0) continue
            count += read
        }
        require(offset + count <= 100L * 1024 * 1024) { "Memory backups must be 100 MB or smaller" }
        val data = if (count == 0) "" else Base64.encodeToString(buffer.copyOf(count), Base64.NO_WRAP)
        session.call("upload", buildJsonObject {
            put("botId", botId)
            put("uploadId", uploadId)
            put("name", "engram-backup.json")
            put("data", data)
            put("offset", offset)
            put("done", ended)
        }).getOrThrow()
        offset += count
        finished = ended
    }
}

@Composable
internal fun BotRoutinesTab(session: SessionStore, botId: String) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var routines by remember(botId) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var runs by remember(botId) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var editing by remember(botId) { mutableStateOf<RoutineDraft?>(null) }
    var loading by remember(botId) { mutableStateOf(false) }
    var error by remember(botId) { mutableStateOf<String?>(null) }
    var webhookFor by remember(botId) { mutableStateOf<Pair<String, JsonObject>?>(null) }

    suspend fun load() {
        loading = true
        session.call("routines", buildJsonObject { put("botId", botId) })
            .onSuccess { response ->
                val obj = response.jsonObject
                routines = obj["routines"]?.jsonArray?.mapNotNull { it.objectOrNull() }.orEmpty()
                runs = obj["runs"]?.jsonArray?.mapNotNull { it.objectOrNull() }.orEmpty()
                error = null
            }.onFailure { error = it.message ?: "Couldn't load routines" }
        loading = false
    }

    LaunchedEffect(botId) { load() }

    if (editing != null) {
        var draft by remember(editing?.id) { mutableStateOf(editing!!) }
        LaunchedEffect(draft.id, draft.scheduleKind) {
            val id = draft.id ?: return@LaunchedEffect
            if (draft.scheduleKind == "Webhook" && webhookFor?.first != id) {
                session.call("routineWebhook", buildJsonObject {
                    put("botId", botId)
                    put("id", id)
                }).onSuccess { webhookFor = id to it.jsonObject }
                    .onFailure { error = it.message ?: "Couldn't load webhook details" }
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SectionCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(if (draft.id == null) "New routine" else "Edit routine", color = colors.text, style = CodyncType.title)
                    DetailField("Name", draft.name) { draft = draft.copy(name = it) }
                    DetailField("Prompt", draft.instruction, multiline = true) { draft = draft.copy(instruction = it) }
                    Text("Schedule", color = colors.tertiary, style = CodyncType.caption)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Keep", "Once", "Daily", "Weekdays", "Minutes", "Hourly", "Monthly", "Cron", "Interval", "Webhook").forEach { kind ->
                            FilterChip(kind, draft.scheduleKind == kind, onClick = { draft = draft.copy(scheduleKind = kind) })
                        }
                    }
                    when (draft.scheduleKind) {
                        "Once" -> {
                            val at = Calendar.getInstance().apply { timeInMillis = draft.at }
                            SecondaryButton(
                                text = "Run once · ${formatDate(draft.at)}",
                                onClick = {
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val selected = Calendar.getInstance().apply {
                                                timeInMillis = draft.at
                                                set(Calendar.YEAR, year)
                                                set(Calendar.MONTH, month)
                                                set(Calendar.DAY_OF_MONTH, day)
                                            }
                                            TimePickerDialog(
                                                context,
                                                { _, hour, minute ->
                                                    selected.set(Calendar.HOUR_OF_DAY, hour)
                                                    selected.set(Calendar.MINUTE, minute)
                                                    selected.set(Calendar.SECOND, 0)
                                                    selected.set(Calendar.MILLISECOND, 0)
                                                    draft = draft.copy(at = selected.timeInMillis)
                                                },
                                                at.get(Calendar.HOUR_OF_DAY),
                                                at.get(Calendar.MINUTE),
                                                AndroidDateFormat.is24HourFormat(context),
                                            ).show()
                                        },
                                        at.get(Calendar.YEAR),
                                        at.get(Calendar.MONTH),
                                        at.get(Calendar.DAY_OF_MONTH),
                                    ).show()
                                },
                            )
                            Text("Uses this phone's local time zone (${TimeZone.getDefault().id}).", color = colors.secondary, style = CodyncType.footnote)
                        }
                        "Daily", "Weekdays", "Hourly", "Monthly", "Cron" -> {
                            if (draft.scheduleKind == "Monthly") {
                                DetailField("Day of month (1–31)", draft.monthDay) {
                                    draft = draft.copy(monthDay = it.filter(Char::isDigit).take(2))
                                }
                            }
                            if (draft.scheduleKind == "Cron") {
                                DetailField("Five-field cron expression", draft.cronExpression) {
                                    draft = draft.copy(cronExpression = it)
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DetailField("Hour (0–23)", draft.hour, modifier = Modifier.weight(1f)) {
                                        draft = draft.copy(hour = it.filter(Char::isDigit).take(2))
                                    }
                                    DetailField("Minute (0–59)", draft.minute, modifier = Modifier.weight(1f)) {
                                        draft = draft.copy(minute = it.filter(Char::isDigit).take(2))
                                    }
                                }
                            }
                            DetailField("IANA time zone", draft.zone) { draft = draft.copy(zone = it.trim()) }
                        }
                        "Minutes" -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("1", "5", "10", "15", "20", "30").forEach { minutes ->
                                    FilterChip("$minutes min", draft.minuteStep == minutes, onClick = { draft = draft.copy(minuteStep = minutes) })
                                }
                            }
                            DetailField("IANA time zone", draft.zone) { draft = draft.copy(zone = it.trim()) }
                        }
                        "Interval" -> DetailField("Every (seconds)", draft.intervalSeconds) {
                            draft = draft.copy(intervalSeconds = it.filter(Char::isDigit).take(8))
                        }
                        "Keep" -> Text(
                            draft.summary.ifBlank { "Select a schedule for this routine." },
                            color = colors.secondary,
                            style = CodyncType.footnote,
                        )
                        "Webhook" -> Text("Runs when its webhook receives an event.", color = colors.secondary, style = CodyncType.footnote)
                    }
                    DetailField("Timeout (seconds)", draft.timeoutSeconds) {
                        draft = draft.copy(timeoutSeconds = it.filter(Char::isDigit).take(5))
                    }
                    if (draft.scheduleKind == "Webhook" && draft.id != null && webhookFor?.first == draft.id) {
                        val webhook = webhookFor?.second
                        val url = webhook?.text("url") ?: webhook?.text("localUrl").orEmpty()
                        SectionCard {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Webhook endpoint", color = colors.text, style = CodyncType.caption)
                                Text(url.ifBlank { "No URL is available for this host." }, color = colors.secondary, style = CodyncType.footnote)
                                Text("Bearer key: ${webhook?.text("key").orEmpty()}", color = colors.secondary, style = CodyncType.footnote)
                                Text(
                                    if (webhook?.flag("connected") == true) "Cloud delivery connected" else "Local delivery only or cloud relay offline",
                                    color = colors.tertiary,
                                    style = CodyncType.caption,
                                )
                                SecondaryButton(
                                    "Copy webhook details",
                                    onClick = {
                                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Codync routine webhook", "$url\n${webhook?.text("key").orEmpty()}"))
                                    },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enabled", modifier = Modifier.weight(1f), color = colors.secondary, style = CodyncType.footnote)
                        CodyncSwitch(
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                        )
                    }
                    Text("Run after another routine (optional)", color = colors.tertiary, style = CodyncType.caption)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip("None", draft.afterRoutine == null, onClick = { draft = draft.copy(afterRoutine = null) })
                        routines.filter { it.string("id") != draft.id }.forEach { r ->
                            val rid = r.string("id").orEmpty()
                            FilterChip(r.string("name").orEmpty(), draft.afterRoutine == rid, onClick = { draft = draft.copy(afterRoutine = rid) })
                        }
                    }
                }
            }
            if (error != null) DetailError(error!!)
            Spacer(Modifier.height(10.dp))
            PrimaryButton(
                "Save routine",
                onClick = {
                    scope.launch {
                        val args = buildJsonObject {
                            put("botId", botId)
                            draft.id?.let { put("id", it) }
                            put("name", draft.name.trim())
                            put("instruction", draft.instruction.trim())
                            put("enabled", draft.enabled)
                            put("timeoutSeconds", draft.timeoutSeconds.toLongOrNull() ?: 3600L)
                            draft.afterRoutine?.let { put("afterRoutine", it) }
                            put("triggers", draft.triggerArray())
                        }
                        session.call("saveRoutine", args)
                            .onSuccess { editing = null; load() }
                            .onFailure { error = it.message ?: "Couldn't save the routine" }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = draft.name.isNotBlank() && draft.instruction.isNotBlank() && !loading &&
                    (draft.timeoutSeconds.toLongOrNull() ?: 0L) in 1L..86_400L,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Cancel", onClick = { editing = null }, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
            Text("${routines.count { it.flag("enabled") }} active", color = colors.secondary, style = CodyncType.footnote)
            Spacer(Modifier.weight(1f))
            SecondaryButton(
                "New routine",
                onClick = {
                    editing = RoutineDraft(
                        id = null,
                        name = "New routine",
                        instruction = "",
                        enabled = true,
                        scheduleKind = "Daily",
                        zone = TimeZone.getDefault().id,
                    )
                },
                leading = CodyncIcons.Add,
            )
        }
        if (error != null) DetailError(error!!)
        if (routines.isEmpty() && !loading) {
            Text("No routines for this bot yet.", modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), color = colors.tertiary, style = CodyncType.footnote)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(routines, key = { it.text("id") ?: it.hashCode().toString() }) { routine ->
                val id = routine.text("id").orEmpty()
                val descriptions = routine["triggerDescriptions"]?.jsonArray
                    ?.mapNotNull { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
                    .orEmpty()
                val summary = descriptions.joinToString(" · ").ifBlank {
                    routine["triggers"]?.jsonArray?.joinToString(" · ") { it.toString() }.orEmpty()
                }
                SectionCard {
                    Column {
                        SettingsRow(
                            title = routine.text("name") ?: "Routine",
                            subtitle = listOfNotNull(
                                summary,
                                routine.long("nextRunAt")?.let { "Next ${formatDate(it)}" },
                                routine.text("lastError"),
                            ).joinToString(" · "),
                            icon = CodyncIcons.Schedule,
                            trailing = {
                                CodyncSwitch(routine.flag("enabled"), onCheckedChange = { enabled ->
                                    scope.launch {
                                        session.call("setRoutineEnabled", buildJsonObject {
                                            put("botId", botId)
                                            put("id", id)
                                            put("enabled", enabled)
                                        }).onSuccess { load() }.onFailure {
                                            error = it.message ?: "Couldn't update the routine"
                                        }
                                    }
                                })
                            },
                            onClick = { editing = RoutineDraft.from(routine, summary) },
                        )
                        Row(Modifier.padding(start = 54.dp, end = 12.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton("Edit", onClick = { editing = RoutineDraft.from(routine, summary) })
                            SecondaryButton("Run now", leading = CodyncIcons.Play, onClick = {
                                scope.launch {
                                    session.call("runRoutine", buildJsonObject {
                                        put("botId", botId)
                                        put("id", id)
                                    }).onSuccess { load() }.onFailure {
                                        error = it.message ?: "Couldn't start the routine"
                                    }
                                }
                            })
                            DestructiveButton("Delete", onClick = {
                                scope.launch {
                                    session.call("deleteRoutine", buildJsonObject {
                                        put("botId", botId)
                                        put("id", id)
                                    }).onSuccess { load() }.onFailure {
                                        error = it.message ?: "Couldn't delete the routine"
                                    }
                                }
                            })
                        }
                    }
                }
            }
            if (runs.isNotEmpty()) {
                item { Text("Recent runs", modifier = Modifier.padding(top = 10.dp), color = colors.tertiary, style = CodyncType.caption) }
                items(runs.take(12), key = { it.text("id") ?: it.hashCode().toString() }) { run ->
                    SectionCard {
                        SettingsRow(
                            title = run.text("status")?.replaceFirstChar { it.uppercase() } ?: "Run",
                            subtitle = listOfNotNull(
                                run.long("createdAt")?.let(::formatDate),
                                run.text("detail"),
                            ).joinToString(" · "),
                            icon = if (run.text("status") == "succeeded") CodyncIcons.CheckCircle else CodyncIcons.Bolt,
                            showChevron = false,
                        )
                    }
                }
            }
        }
    }
}

private data class RoutineDraft(
    val id: String?,
    val name: String,
    val instruction: String,
    val enabled: Boolean,
    val scheduleKind: String,
    val zone: String,
    val hour: String = "9",
    val minute: String = "0",
    val monthDay: String = "1",
    val minuteStep: String = "15",
    val cronExpression: String = "0 9 * * *",
    val at: Long = System.currentTimeMillis() + 3_600_000L,
    val intervalSeconds: String = "3600",
    val timeoutSeconds: String = "3600",
    val afterRoutine: String? = null,
    val originalTriggers: JsonArray = JsonArray(emptyList()),
    val summary: String = "",
) {
    fun triggerArray(): JsonArray = when (scheduleKind) {
        "Keep" -> originalTriggers
        "Webhook" -> buildJsonArray { add(buildJsonObject { put("type", "webhook") }) }
        "Once" -> buildJsonArray { add(buildJsonObject { put("type", "once"); put("at", at) }) }
        "Interval" -> buildJsonArray {
            add(buildJsonObject { put("type", "interval"); put("seconds", intervalSeconds.toLongOrNull() ?: 0L) })
        }
        "Weekdays", "Daily", "Monthly" -> buildJsonArray {
            val hourValue = hour.toIntOrNull() ?: 9
            val minuteValue = minute.toIntOrNull() ?: 0
            val day = if (scheduleKind == "Monthly") monthDay.toIntOrNull() ?: 1 else null
            add(buildJsonObject {
                put("type", "cron")
                put(
                    "expression",
                    when (scheduleKind) {
                        "Weekdays" -> "$minuteValue $hourValue * * 1-5"
                        "Monthly" -> "$minuteValue $hourValue $day * *"
                        else -> "$minuteValue $hourValue * * *"
                    },
                )
                put("timeZone", zone)
            })
        }
        "Hourly" -> buildJsonArray {
            val minuteValue = minute.toIntOrNull() ?: 0
            add(buildJsonObject { put("type", "cron"); put("expression", "$minuteValue * * * *"); put("timeZone", zone) })
        }
        "Minutes" -> buildJsonArray {
            add(buildJsonObject { put("type", "cron"); put("expression", "*/$minuteStep * * * *"); put("timeZone", zone) })
        }
        "Cron" -> buildJsonArray {
            add(buildJsonObject {
                put("type", "cron")
                put("expression", cronExpression)
                put("timeZone", zone)
            })
        }
        else -> originalTriggers
    }

    companion object {
        fun from(routine: JsonObject, summary: String): RoutineDraft {
            val triggers = routine["triggers"]?.jsonArray ?: JsonArray(emptyList())
            val first = triggers.firstOrNull()?.objectOrNull()
            val type = first?.text("type")
            val schedule = if (triggers.size != 1) "Keep" else when (type) {
                "webhook" -> "Webhook"
                "interval" -> "Interval"
                "once" -> "Once"
                "cron" -> {
                    val expression = first?.text("expression").orEmpty().split(Regex("\\s+"))
                    when {
                        expression.size == 5 && expression[2] == "*" && expression[3] == "*" && expression[4] == "1-5" -> "Weekdays"
                        expression.size == 5 && expression[1] == "*" && expression[0].startsWith("*/") &&
                            expression[0].removePrefix("*/").toIntOrNull()?.let { it in listOf(1, 5, 10, 15, 20, 30) } == true -> "Minutes"
                        expression.size == 5 && expression[1] == "*" -> "Hourly"
                        expression.size == 5 && expression.drop(2) == listOf("*", "*", "*") -> "Daily"
                        expression.size == 5 && expression[2].toIntOrNull()?.let { it in 1..31 } == true && expression[3] == "*" && expression[4] == "*" -> "Monthly"
                        else -> "Cron"
                    }
                }
                else -> "Keep"
            }
            val cron = first?.text("expression")?.split(Regex("\\s+"))
            return RoutineDraft(
                id = routine.text("id"),
                name = routine.text("name").orEmpty(),
                instruction = routine.text("instruction").orEmpty(),
                enabled = routine.flag("enabled"),
                scheduleKind = schedule,
                zone = first?.text("timeZone") ?: TimeZone.getDefault().id,
                hour = cron?.getOrNull(1)?.takeIf { it != "*" } ?: "9",
                minute = cron?.getOrNull(0)?.takeIf { it != "*" } ?: "0",
                monthDay = cron?.getOrNull(2)?.takeIf { it != "*" } ?: "1",
                minuteStep = cron?.firstOrNull()?.let { it.takeIf { value -> value.startsWith("*/") }?.removePrefix("*/") } ?: "15",
                cronExpression = first?.text("expression") ?: "0 9 * * *",
                at = first?.long("at") ?: System.currentTimeMillis() + 3_600_000L,
                intervalSeconds = first?.long("seconds")?.toString() ?: "3600",
                timeoutSeconds = routine.long("timeoutSeconds")?.toString() ?: "3600",
                afterRoutine = routine.string("afterRoutine"),
                originalTriggers = triggers,
                summary = summary,
            )
        }
    }
}

@Composable
private fun DetailSearchField(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    val colors = CodyncTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
        cursorBrush = SolidColor(colors.text),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(16.dp)).background(colors.surface).padding(horizontal = 14.dp, vertical = 12.dp),
        decorationBox = { inner ->
            androidx.compose.foundation.layout.Box {
                if (value.isEmpty()) Text(placeholder, color = colors.tertiary, style = CodyncType.body)
                inner()
            }
        },
    )
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    multiline: Boolean = false,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    val colors = CodyncTheme.colors
    Column(modifier) {
        Text(label, color = colors.tertiary, style = CodyncType.caption)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            maxLines = if (multiline) 7 else 1,
            textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(12.dp),
        )
    }
}

@Composable
private fun DetailError(message: String) {
    Text(message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp), color = CodyncTheme.colors.warning, style = CodyncType.footnote)
}

private fun JsonElement.objectOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
private fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
private fun JsonObject.flag(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull == true
private fun formatDate(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
