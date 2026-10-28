package com.pokai.codync.android.ui.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CharacterAvatar
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot
import com.pokai.codync.model.MemberAccess
import com.pokai.codync.android.ui.workbench.HostFolderPickerDialog
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * New chat / room composer. One recipient starts a fresh chat with that bot; two or more
 * create a group on the host first. The host owns membership validation (2–50 bots).
 */
@Composable
fun NewChatSheet(
    session: SessionStore,
    bots: List<Bot>,
    forceGroup: Boolean = false,
    onDismiss: () -> Unit,
    onOpenChat: (botId: String, chatId: String) -> Unit,
    onCreateBot: () -> Unit = {},
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    val candidates = remember(bots) {
        bots.filter { it.kind != "group" }.sortedWith(
            compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() },
        )
    }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var workspace by remember { mutableStateOf("") }
    var sharedWorkspace by remember { mutableStateOf(false) }
    var workspacePickerOpen by remember { mutableStateOf(false) }
    var leader by remember { mutableStateOf("") }
    var notifications by remember { mutableStateOf(true) }
    var pinned by remember { mutableStateOf(false) }
    var memberSettings by remember { mutableStateOf<Map<String, MemberAccess>>(emptyMap()) }
    var botQuery by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val group = forceGroup || selected.size > 1
    val defaultName = candidates.filter { it.id in selected }.joinToString(", ") { it.name }
    val visibleCandidates = remember(candidates, botQuery) {
        val query = botQuery.trim()
        candidates.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }

    fun updateMember(memberId: String, browser: Boolean? = null, computer: Boolean? = null) {
        val current = memberSettings[memberId] ?: MemberAccess()
        memberSettings = memberSettings + (memberId to current.copy(
            browser = browser ?: current.browser,
            computer = computer ?: current.computer,
        ))
    }

    Dialog(
        onDismissRequest = { if (!creating) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .then(if (group) Modifier.fillMaxSize() else Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.88f))
                .clip(RoundedCornerShape(if (group) 0.dp else 28.dp))
                .background(colors.surface)
                .padding(top = 18.dp, bottom = 20.dp),
        ) {
            Text(
                if (group) "New group chat" else "New chat",
                modifier = Modifier.padding(horizontal = 22.dp),
                color = colors.text,
                style = CodyncType.headline,
            )
            Text(
                if (group) "Choose who is in the room and how it works." else "Pick a bot. Add another to make a room.",
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 3.dp),
                color = colors.secondary,
                style = CodyncType.footnote,
            )

            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
            ) {

            if (group) {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NewChatField("Group name", name, "${defaultName.ifBlank { "Name" }}", singleLine = true) { name = it.take(80) }
                        NewChatField("What this group works on", description, "A short description", singleLine = true) { description = it.take(240) }
                    }
                }
            }

            if (candidates.isEmpty()) {
                Text(
                    "There are no bots on this computer yet. Create one first, then add it to this room.",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    color = colors.tertiary,
                    style = CodyncType.footnote,
                )
                SecondaryButton(
                    "Create bot",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                    onClick = onCreateBot,
                )
            } else {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp)
                            .clip(RoundedCornerShape(12.dp)).background(colors.background).padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        com.pokai.codync.design.CodyncIcon(CodyncIcons.Search, tint = colors.tertiary, size = 17.dp)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (botQuery.isBlank()) Text("Search bots", color = colors.tertiary, style = CodyncType.body)
                            BasicTextField(
                                value = botQuery,
                                onValueChange = { botQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
                                cursorBrush = SolidColor(colors.text),
                            )
                        }
                        if (botQuery.isNotBlank()) {
                            Text(
                                "Clear",
                                modifier = Modifier.clickable { botQuery = "" },
                                color = colors.secondary,
                                style = CodyncType.caption,
                            )
                        }
                    }
                    if (visibleCandidates.isEmpty()) {
                        Text(
                            if (botQuery.isBlank()) "No bots on this computer yet." else "No bots match “$botQuery”.",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = colors.tertiary,
                            style = CodyncType.footnote,
                        )
                    }
                    visibleCandidates.forEachIndexed { index, bot ->
                        if (index > 0) {
                            com.pokai.codync.design.DividerLine(inset = 54.dp)
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (bot.id in selected) {
                                    selected -= bot.id
                                    if (leader == bot.id) leader = ""
                                    error = null
                                } else if (selected.size >= 50) {
                                    error = "A room can have at most 50 bots."
                                } else {
                                    selected += bot.id
                                    error = null
                                }
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            CharacterAvatar(bot.name, bot.avatarColor, bot.avatarShape, size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(bot.name, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.Medium))
                                Text(bot.description.ifBlank { bot.backend }, color = colors.secondary, style = CodyncType.caption, maxLines = 1)
                            }
                            CodyncSwitch(
                                checked = bot.id in selected,
                                onCheckedChange = { checked ->
                                    if (checked && selected.size >= 50) error = "A room can have at most 50 bots."
                                    else {
                                        selected = if (checked) selected + bot.id else selected - bot.id
                                        if (!checked && leader == bot.id) leader = ""
                                        error = null
                                    }
                                },
                            )
                        }
                        if (group && bot.id in selected) {
                            val access = memberSettings[bot.id] ?: MemberAccess()
                            Column(Modifier.padding(start = 14.dp, end = 12.dp, bottom = 8.dp)) {
                                SettingsRow(
                                    title = "Browser in this room",
                                    subtitle = when {
                                        access.browser -> "Allowed for this member in the room"
                                        bot.browser -> "Also allowed by this bot's own Browser setting"
                                        else -> "Off for this member in this room"
                                    },
                                    showChevron = false,
                                    trailing = { CodyncSwitch(access.browser, onCheckedChange = { updateMember(bot.id, browser = it) }) },
                                )
                                SettingsRow(
                                    title = "Computer in this room",
                                    subtitle = when {
                                        access.computer -> "Allowed for this member in the room"
                                        bot.computer -> "Also allowed by this bot's own Computer setting"
                                        else -> "Off for this member in this room"
                                    },
                                    showChevron = false,
                                    trailing = { CodyncSwitch(access.computer, onCheckedChange = { updateMember(bot.id, computer = it) }) },
                                )
                            }
                        }
                    }
                }
            }

            if (group) {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ROOM", color = colors.tertiary, style = CodyncType.caption)
                        Text("Leader speaks first and hands work to the others.", color = colors.secondary, style = CodyncType.footnote)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip("No leader", leader.isBlank(), onClick = { leader = "" })
                            candidates.filter { it.id in selected }.forEach { member ->
                                FilterChip(member.name, leader == member.id, onClick = { leader = member.id })
                            }
                        }
                        SettingsRow(
                            title = "Notifications",
                            subtitle = "Get notified when a member finishes or needs you",
                            showChevron = false,
                            trailing = { CodyncSwitch(notifications, onCheckedChange = { notifications = it }) },
                        )
                        SettingsRow(
                            title = "Pin to top",
                            subtitle = "Keep this group at the top of Home",
                            showChevron = false,
                            trailing = { CodyncSwitch(pinned, onCheckedChange = { pinned = it }) },
                        )
                    }
                }
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("WORKSPACE", color = colors.tertiary, style = CodyncType.caption)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip("Own folders", !sharedWorkspace, onClick = { sharedWorkspace = false })
                            FilterChip("One shared folder", sharedWorkspace, onClick = { sharedWorkspace = true })
                        }
                        if (sharedWorkspace) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    NewChatField("Shared folder", workspace, "Absolute folder path", singleLine = true) { workspace = it }
                                }
                                SecondaryButton("Browse", onClick = { workspacePickerOpen = true })
                            }
                        }
                    }
                }
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("INSTRUCTIONS", color = colors.tertiary, style = CodyncType.caption)
                        NewChatField("Rules everyone follows", instructions, "Keep answers short. Always cite files you change.", minLines = 3) { instructions = it }
                    }
                }
            }

            if (selected.size == 1) {
                Text(
                    "This starts a separate chat. The bot's main chat is untouched.",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = colors.tertiary,
                    style = CodyncType.caption,
                )
            } else if (group) {
                Text(
                    "${selected.size} bots selected · rooms hold 2–50",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = colors.tertiary,
                    style = CodyncType.caption,
                )
            }
            error?.let {
                Text(it, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp), color = colors.warning, style = CodyncType.footnote)
            }

            }

            PrimaryButton(
                text = when {
                    creating -> "Creating…"
                    group -> "Create room"
                    else -> "Start chat"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                enabled = selected.isNotEmpty() && !creating && (!group || (selected.size in 2..50 && name.isNotBlank() && (!sharedWorkspace || workspace.isNotBlank()))),
                leading = if (group) CodyncIcons.Groups else CodyncIcons.AddComment,
                onClick = {
                    if (!group) {
                        selected.firstOrNull()?.let { onOpenChat(it, "new") }
                    } else scope.launch {
                        creating = true
                        error = null
                        val result = session.call("createBot", buildJsonObject {
                            put("kind", "group")
                            put("name", name.trim())
                            put("description", description.trim())
                            put("instructions", instructions.trim())
                            put("leader", leader)
                            put("workspace", if (sharedWorkspace) workspace.trim() else "")
                            put("pinned", pinned)
                            put("notify", notifications)
                            put("members", kotlinx.serialization.json.buildJsonArray {
                                selected.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                            })
                            put("memberSettings", buildJsonObject {
                                selected.forEach { memberId ->
                                    val access = memberSettings[memberId] ?: MemberAccess()
                                    put(memberId, buildJsonObject {
                                        put("browser", access.browser)
                                        put("computer", access.computer)
                                    })
                                }
                            })
                        })
                        creating = false
                        val created = result.getOrNull()?.jsonObject?.get("bot")?.jsonObject
                            ?.get("id")?.jsonPrimitive?.content
                        if (created != null) onOpenChat(created, "main")
                        else error = result.exceptionOrNull()?.message ?: "The host didn't return the new room"
                    }
                },
            )
            SecondaryButton(
                "Cancel",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp).fillMaxWidth(),
                onClick = onDismiss,
                enabled = !creating,
            )
        }
    }

    if (workspacePickerOpen) {
        HostFolderPickerDialog(
            session = session,
            initialPath = connection.hello?.home,
            onDismiss = { workspacePickerOpen = false },
            onSelect = { workspace = it; workspacePickerOpen = false },
        )
    }
}

@Composable
private fun NewChatField(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    onValueChange: (String) -> Unit,
) {
    val colors = CodyncTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = colors.tertiary, style = CodyncType.caption)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else 6,
            textStyle = TextStyle(color = colors.text, fontSize = 15.sp, fontFamily = CodyncType.body.fontFamily),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface).padding(12.dp),
            decorationBox = { inner ->
                androidx.compose.foundation.layout.Box {
                    if (value.isBlank()) Text(placeholder, color = colors.tertiary, style = CodyncType.body)
                    inner()
                }
            },
        )
    }
}
