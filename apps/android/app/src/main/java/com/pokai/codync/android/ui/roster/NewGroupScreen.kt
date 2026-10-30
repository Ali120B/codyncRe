package com.pokai.codync.android.ui.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.android.ui.SearchField
import com.pokai.codync.android.ui.workbench.HostFolderPickerDialog
import com.pokai.codync.android.ui.workbench.WorkbenchScaffold
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncMenuAnchor
import com.pokai.codync.design.CodyncMenuItem
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.DividerLine
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot
import com.pokai.codync.model.MemberAccess
import com.pokai.codync.transport.ConnectionState
import com.pokai.codync.transport.TransportKind
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Full-screen group composer, matching the New bot page instead of stretching the old modal.
 * Groups are created on one computer at a time and may only include that computer's bots.
 */
@Composable
fun NewGroupScreen(
    session: SessionStore,
    initialComputerId: String? = null,
    onCancel: () -> Unit,
    onCreated: (botId: String, chatId: String) -> Unit,
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val computers by session.computers().collectAsState(initial = emptyList())
    val allBots by session.bots().collectAsState(initial = emptyList())
    val botComputers by session.botComputers().collectAsState(initial = emptyMap())
    var computerId by rememberSaveable(initialComputerId) {
        mutableStateOf(initialComputerId ?: session.activeComputerId() ?: "")
    }
    LaunchedEffect(computers, computerId) {
        if (computerId.isBlank() || computers.none { it.id == computerId }) {
            computerId = computers.firstOrNull()?.id.orEmpty()
        }
    }
    val computer = computers.firstOrNull { it.id == computerId }
    val connection by session.connectionForComputer(computerId).collectAsState(initial = ConnectionState(computer = computer))
    val candidates = remember(allBots, botComputers, computerId) {
        allBots.filter { it.kind != "group" && botComputers[it.id] == computerId }
            .sortedWith(compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() })
    }
    var name by rememberSaveable(computerId) { mutableStateOf("") }
    var description by rememberSaveable(computerId) { mutableStateOf("") }
    var instructions by rememberSaveable(computerId) { mutableStateOf("") }
    var workspace by rememberSaveable(computerId) { mutableStateOf("") }
    var leader by rememberSaveable(computerId) { mutableStateOf("") }
    var notifications by rememberSaveable(computerId) { mutableStateOf(true) }
    var pinned by rememberSaveable(computerId) { mutableStateOf(false) }
    var selected by rememberSaveable(computerId) { mutableStateOf(emptySet<String>()) }
    var memberSettings by remember(computerId) { mutableStateOf<Map<String, MemberAccess>>(emptyMap()) }
    var query by rememberSaveable(computerId) { mutableStateOf("") }
    var computerMenu by remember { mutableStateOf(false) }
    var leaderMenu by remember { mutableStateOf(false) }
    var workspacePicker by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val visible = candidates.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    val selectedBots = candidates.filter { it.id in selected }
    val canCreate = name.isNotBlank() && selected.size in 2..50 && !creating &&
        connection.kind != TransportKind.Offline && connection.hello != null

    fun updateMember(id: String, browser: Boolean? = null, computerAccess: Boolean? = null) {
        val current = memberSettings[id] ?: MemberAccess()
        memberSettings = memberSettings + (id to current.copy(
            browser = browser ?: current.browser,
            computer = computerAccess ?: current.computer,
        ))
    }

    WorkbenchScaffold(
        title = "New group",
        subtitle = computer?.let { "Creating on ${it.name}" } ?: "Choose a computer",
        onBack = onCancel,
    ) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SectionCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row {
                                selectedBots.take(3).forEachIndexed { index, bot ->
                                    CharacterAvatar(
                                        bot.name,
                                        bot.avatarColor,
                                        bot.avatarShape,
                                        size = 44.dp,
                                        modifier = Modifier.padding(start = if (index == 0) 0.dp else (-12).dp),
                                    )
                                }
                                if (selectedBots.isEmpty()) {
                                    Box(
                                        Modifier.width(44.dp).height(44.dp).clip(RoundedCornerShape(14.dp))
                                            .background(colors.bubbleAgent),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        com.pokai.codync.design.CodyncIcon(
                                            CodyncIcons.Groups,
                                            tint = colors.tertiary,
                                            size = 22.dp,
                                        )
                                    }
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (selectedBots.isEmpty()) "Pick two to fifty bots" else "${selectedBots.size} bots in this room",
                                    color = colors.text,
                                    style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Text("Groups share a room, a leader and optional tools.", color = colors.tertiary, style = CodyncType.caption)
                            }
                        }
                        GroupField("Group name", name, "e.g. Release crew", singleLine = true) { name = it.take(80) }
                        GroupField("What this group works on", description, "A short description", singleLine = true) { description = it.take(240) }
                        Text("COMPUTER", color = colors.tertiary, style = CodyncType.caption)
                        CodyncMenuAnchor(
                            expanded = computerMenu,
                            onDismissRequest = { computerMenu = false },
                            items = computers.map { host ->
                                CodyncMenuItem(
                                    label = host.name,
                                    symbol = if (host.id == computerId) CodyncIcons.Done else CodyncIcons.Computer,
                                    enabled = connection.kind != TransportKind.Offline || host.id != computerId,
                                    onClick = {
                                        computerMenu = false
                                        if (host.id != computerId) {
                                            computerId = host.id
                                            selected = emptySet()
                                            leader = ""
                                            memberSettings = emptyMap()
                                        }
                                    },
                                )
                            },
                        ) {
                            SettingsRow(
                                title = computer?.name ?: "No computer",
                                subtitle = when {
                                    computer == null -> "Pair a computer first"
                                    connection.kind == TransportKind.Offline -> "Offline · the group is created when it reconnects"
                                    else -> "Members are chosen from this computer's roster"
                                },
                                icon = CodyncIcons.Computer,
                                onClick = { if (computers.size > 1) computerMenu = true },
                                showChevron = computers.size > 1,
                            )
                        }
                    }
                }

                SectionCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("MEMBERS · ${selected.size} OF 2–50", color = colors.tertiary, style = CodyncType.caption)
                        SearchField(query, { query = it }, placeholder = "Search this computer's bots")
                        if (visible.isEmpty()) {
                            Text(
                                if (candidates.isEmpty()) "There are no bots on this computer yet. Create one first." else "No bots match that search.",
                                color = colors.tertiary,
                                style = CodyncType.footnote,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        visible.forEach { bot ->
                            MemberRow(
                                bot = bot,
                                selected = bot.id in selected,
                                leader = leader == bot.id,
                                access = memberSettings[bot.id] ?: MemberAccess(),
                                onToggle = {
                                    if (bot.id in selected) {
                                        selected -= bot.id
                                        if (leader == bot.id) leader = ""
                                    } else if (selected.size >= 50) {
                                        error = "A room can have at most 50 bots."
                                    } else selected += bot.id
                                    error = null
                                },
                                onAccess = { browser, computerAccess -> updateMember(bot.id, browser, computerAccess) },
                            )
                        }
                    }
                }

                SectionCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ROOM", color = colors.tertiary, style = CodyncType.caption)
                        Text("The leader speaks first and hands work to the other members.", color = colors.secondary, style = CodyncType.footnote)
                        CodyncMenuAnchor(
                            expanded = leaderMenu,
                            onDismissRequest = { leaderMenu = false },
                            items = buildList {
                                add(CodyncMenuItem("No leader", CodyncIcons.Remove, onClick = { leader = ""; leaderMenu = false }))
                                selectedBots.forEach { bot ->
                                    add(CodyncMenuItem(bot.name, if (leader == bot.id) CodyncIcons.Done else CodyncIcons.Person, onClick = {
                                        leader = bot.id
                                        leaderMenu = false
                                    }))
                                }
                            },
                        ) {
                            SettingsRow(
                                title = "Leader",
                                subtitle = selectedBots.firstOrNull { it.id == leader }?.name ?: "No leader",
                                icon = CodyncIcons.Person,
                                onClick = { leaderMenu = true },
                                showChevron = true,
                            )
                        }
                        SettingsRow(
                            title = "Notifications",
                            subtitle = "Get notified when a member finishes or needs you",
                            showChevron = false,
                            onClick = { notifications = !notifications },
                            trailing = { CodyncSwitch(notifications, onCheckedChange = { notifications = it }) },
                        )
                        SettingsRow(
                            title = "Pin to top",
                            subtitle = "Keep this group at the top of Home",
                            showChevron = false,
                            onClick = { pinned = !pinned },
                            trailing = { CodyncSwitch(pinned, onCheckedChange = { pinned = it }) },
                        )
                    }
                }

                SectionCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WORKSPACE", color = colors.tertiary, style = CodyncType.caption)
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                GroupField("Shared folder (optional)", workspace, "Members' own folders", singleLine = true) { workspace = it }
                            }
                            SecondaryButton("Browse", onClick = { workspacePicker = true })
                        }
                        Text("Leave empty to let every member work in its own folder.", color = colors.tertiary, style = CodyncType.caption)
                    }
                }

                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        GroupField("Instructions for all members", instructions, "Rules everyone follows", minLines = 3) { instructions = it }
                    }
                }

                error?.let {
                    Text(it, color = colors.warning, style = CodyncType.footnote)
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(
                Modifier.fillMaxWidth().background(colors.background).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                DestructiveButton("Cancel", onClick = onCancel, enabled = !creating)
                PrimaryButton(
                    text = if (creating) "Creating…" else "Create room",
                    enabled = canCreate,
                    onClick = {
                        scope.launch {
                            creating = true
                            error = null
                            val result = session.callOn(computerId, "createBot", buildJsonObject {
                                put("kind", "group")
                                put("name", name.trim())
                                put("description", description.trim())
                                put("instructions", instructions.trim())
                                put("leader", leader)
                                put("workspace", workspace.trim())
                                put("pinned", pinned)
                                put("notify", notifications)
                                put("members", buildJsonArray { selected.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                                put("memberSettings", buildJsonObject {
                                    selected.forEach { id ->
                                        val access = memberSettings[id] ?: MemberAccess()
                                        put(id, buildJsonObject {
                                            put("browser", access.browser)
                                            put("computer", access.computer)
                                        })
                                    }
                                })
                            })
                            creating = false
                            val created = result.getOrNull()?.jsonObject?.get("bot")?.jsonObject
                                ?.get("id")?.jsonPrimitive?.content
                            if (created != null) onCreated(created, "main")
                            else error = result.exceptionOrNull()?.message ?: "The host didn't return the new room"
                        }
                    },
                )
            }
        }
    }

    if (workspacePicker) {
        HostFolderPickerDialog(
            session = session,
            computerId = computerId,
            initialPath = connection.hello?.home,
            onDismiss = { workspacePicker = false },
            onSelect = { workspace = it; workspacePicker = false },
        )
    }
}

@Composable
private fun MemberRow(
    bot: Bot,
    selected: Boolean,
    leader: Boolean,
    access: MemberAccess,
    onToggle: () -> Unit,
    onAccess: (Boolean?, Boolean?) -> Unit,
) {
    val colors = CodyncTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 2.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterAvatar(bot.name, bot.avatarColor, bot.avatarShape, size = 36.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(bot.name, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.Medium))
                    if (leader) {
                        Spacer(Modifier.width(7.dp))
                        Text("LEADER", color = colors.tertiary, style = CodyncType.caption2)
                    }
                }
                Text(bot.description.ifBlank { bot.backend }, color = colors.secondary, style = CodyncType.caption, maxLines = 1)
            }
            CodyncSwitch(checked = selected, onCheckedChange = { onToggle() })
        }
        if (selected) {
            Column(Modifier.padding(start = 47.dp, bottom = 4.dp)) {
                SettingsRow(
                    title = "Browser in this room",
                    subtitle = if (access.browser) "Allowed in this room" else if (bot.browser) "Allowed by this bot's own setting" else "Off for this member",
                    showChevron = false,
                    trailing = { CodyncSwitch(access.browser, onCheckedChange = { onAccess(it, null) }) },
                )
                SettingsRow(
                    title = "Computer in this room",
                    subtitle = if (access.computer) "Allowed in this room" else if (bot.computer) "Allowed by this bot's own setting" else "Off for this member",
                    showChevron = false,
                    trailing = { CodyncSwitch(access.computer, onCheckedChange = { onAccess(null, it) }) },
                )
                DividerLine(inset = 2.dp)
            }
        }
    }
}

@Composable
private fun GroupField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onChange: (String) -> Unit,
) {
    val colors = CodyncTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = colors.tertiary, style = CodyncType.caption)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else 8,
            textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(12.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isBlank()) Text(placeholder, color = colors.tertiary, style = CodyncType.body)
                    inner()
                }
            },
        )
    }
}
