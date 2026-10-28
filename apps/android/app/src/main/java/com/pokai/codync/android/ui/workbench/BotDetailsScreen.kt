package com.pokai.codync.android.ui.workbench

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.data.SessionStore
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.toDraft
import com.pokai.codync.transport.TransportKind
import android.content.ClipData
import android.content.ClipboardManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
fun BotDetailsScreen(
    session: SessionStore,
    botId: String,
    initialTab: String = "Details",
    onBack: () -> Unit,
    onOpenChat: (botId: String, chatId: String) -> Unit,
    onOpenExtensions: () -> Unit = {},
) {
    val colors = CodyncTheme.colors
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize().background(colors.background),
    ) {
        BotDetailsPanel(session, botId, initialTab, onBack, onOpenChat, onOpenExtensions)
    }
}

@Composable
private fun BotDetailsPanel(
    session: SessionStore,
    botId: String,
    initialTab: String,
    onBack: () -> Unit,
    onOpenChat: (botId: String, chatId: String) -> Unit,
    onOpenExtensions: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val context = LocalContext.current
    val lookup by session.botLookup(botId).collectAsState(initial = com.pokai.codync.data.BotLookup())
    val bots by session.bots().collectAsState(initial = emptyList())
    val connection by session.connectionStatus().collectAsState(initial = com.pokai.codync.transport.ConnectionState())
    if (lookup.bot == null) {
        val waitingForRoster = connection.computer != null && !lookup.rosterLoaded
        WorkbenchScaffold(
            title = if (waitingForRoster) "Loading bot details" else "Bot unavailable",
            subtitle = if (waitingForRoster) "Connecting to ${connection.computer?.name}" else "It may have been removed from the host",
            onBack = onBack,
        ) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                Text(
                    if (waitingForRoster) "Waiting for the computer to send its bot roster…"
                    else "This bot isn't in the connected host roster.",
                    color = colors.secondary,
                    style = CodyncType.body,
                )
            }
        }
        return
    }
    val current = lookup.bot ?: return
    val tabs = if (current.kind == "group") listOf("Details", "Routines", "Computer")
        else listOf("Details", "Memory", "Library", "Skills", "Routines", "Vault", "Computer")
    var tab by rememberSaveable(current.id, initialTab) {
        mutableStateOf(initialTab.takeIf { it in tabs } ?: "Details")
    }
    var editing by rememberSaveable(current.id) { mutableStateOf(false) }
    var editingSection by rememberSaveable(current.id) { mutableStateOf("Overview") }
    var showTemplate by rememberSaveable(current.id) { mutableStateOf(false) }
    var templateCopied by rememberSaveable(current.id) { mutableStateOf(false) }
    if (editing) {
        BackHandler { editing = false }
        LiveBotEditor(
            session = session,
            bot = current,
            initialSection = editingSection,
            onCancel = { editing = false },
            onSaved = { editing = false },
            onDeleted = onBack,
        )
        return
    }
    WorkbenchScaffold(
        title = if (current.kind == "group") "Settings" else "Details",
        subtitle = "",
        onBack = onBack,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current.kind != "group") {
                    com.pokai.codync.android.ui.HeaderIconButton(
                        CodyncIcons.Share,
                        "Create template from ${current.name}",
                        onClick = { templateCopied = false; showTemplate = true },
                    )
                }
                com.pokai.codync.android.ui.HeaderIconButton(
                    CodyncIcons.Settings,
                    "All settings for ${current.name}",
                    onClick = {
                        editingSection = if (current.kind == "group") "Members & access" else "Overview"
                        editing = true
                    },
                )
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (current.kind == "group") {
                    val groupMembers = current.members.take(3).mapNotNull { memberId ->
                        bots.firstOrNull { it.id == memberId }
                    }
                    val avatarOverlap = 19
                    val avatarStripWidth = if (groupMembers.isEmpty()) 46 else 46 + (groupMembers.size - 1) * avatarOverlap
                    Box(Modifier.width(avatarStripWidth.dp).height(58.dp)) {
                        groupMembers.forEachIndexed { index, member ->
                            com.pokai.codync.design.CharacterAvatar(
                                name = member.name,
                                colorId = member.avatarColor,
                                shapeId = member.avatarShape,
                                modifier = Modifier.align(Alignment.CenterStart).offset(x = (index * avatarOverlap).dp),
                                size = 46.dp,
                            )
                        }
                    }
                } else {
                    com.pokai.codync.design.CharacterAvatar(
                        name = current.name,
                        colorId = current.avatarColor,
                        shapeId = current.avatarShape,
                        size = 58.dp,
                    )
                }
                Text(current.name, color = colors.text, style = CodyncType.title)
                if (current.kind == "group" && current.description.isNotBlank()) {
                    Text(current.description, color = colors.secondary, style = CodyncType.footnote, maxLines = 2)
                }
            }
            val tabStripModifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)
                .clip(RoundedCornerShape(15.dp)).background(colors.surface).padding(4.dp)
            Row(
                if (current.kind == "group") tabStripModifier
                else tabStripModifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { item ->
                    val selected = tab == item
                    Box(
                        Modifier.then(if (current.kind == "group") Modifier.weight(1f) else Modifier)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (selected) colors.bubbleAgent else androidx.compose.ui.graphics.Color.Transparent)
                            .clickable { tab = item }
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item,
                            color = if (selected) colors.text else colors.secondary,
                            style = CodyncType.caption,
                            maxLines = 1,
                        )
                    }
                }
            }
            when (tab) {
                "Memory" -> BotMemoryTab(session, current.id)
                "Library" -> BotLibraryTab(current.name)
                "Skills" -> BotCapabilitiesTab(session, current, skills = true, onOpenExtensions = onOpenExtensions)
                "Routines" -> BotRoutinesTab(session, current.id)
                "Vault" -> BotCapabilitiesTab(session, current, skills = false, onOpenExtensions = onOpenExtensions)
                "Computer" -> LiveComputerPanel(
                    session = session,
                    initialBotId = current.id,
                    detailsMode = true,
                    onOpenExtensions = onOpenExtensions,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (current.kind == "group") {
                        item {
                            SectionCard {
                                SettingsRow(
                                    title = "All group settings",
                                    subtitle = "Name, instructions, workspace, tools and members",
                                    icon = CodyncIcons.Settings,
                                    onClick = { editingSection = "Members & access"; editing = true },
                                )
                            }
                        }
                    }
                    if (current.kind != "group") {
                        item { SectionLabel("Profile") }
                        item {
                            SectionCard {
                                SettingsRow(
                                    title = "Look",
                                    subtitle = "${current.avatarShape.replaceFirstChar { it.uppercase() }} · ${current.avatarColor.replaceFirstChar { it.uppercase() }}",
                                    icon = CodyncIcons.Bots,
                                    onClick = { editingSection = "Identity"; editing = true },
                                )
                                SettingsRow(
                                    title = "Name and job",
                                    subtitle = current.description.ifBlank { current.name },
                                    icon = CodyncIcons.Info,
                                    onClick = { editingSection = "Overview"; editing = true },
                                )
                                SettingsRow(
                                    title = "Instructions",
                                    subtitle = current.description.ifBlank { "None yet" },
                                    icon = CodyncIcons.Memory,
                                    onClick = { editingSection = "Instructions"; editing = true },
                                )
                                SettingsRow(
                                    title = "Default model",
                                    subtitle = current.model ?: "${current.backend} default",
                                    icon = CodyncIcons.Settings,
                                    onClick = { editingSection = "Model"; editing = true },
                                )
                                SettingsRow(
                                    title = "Before ${current.name} acts",
                                    subtitle = when (current.permission) {
                                        "ask" -> "Asks you first"
                                        "full" -> "Full access"
                                        else -> "Approved automatically"
                                    },
                                    icon = CodyncIcons.Shield,
                                    onClick = { editingSection = "Permissions"; editing = true },
                                )
                                SettingsRow(
                                    title = "Where ${current.name} works",
                                    subtitle = when {
                                        current.managedWorkspace -> "Personal workspace"
                                        current.cwd.isBlank() -> "Host default"
                                        else -> current.cwd.substringAfterLast('/').ifBlank { current.cwd }
                                    },
                                    icon = CodyncIcons.Folder,
                                    onClick = { editingSection = "Access"; editing = true },
                                )
                                SettingsRow(
                                    title = "All settings",
                                    subtitle = "Identity, model, permissions, access and alerts",
                                    icon = CodyncIcons.Settings,
                                    onClick = { editingSection = "Overview"; editing = true },
                                )
                            }
                        }
                    }
                    item { SectionLabel("Host details") }
                    item {
                        SectionCard {
                            SettingsRow(
                                title = "Runtime",
                                subtitle = current.backend,
                                icon = CodyncIcons.Terminal,
                                showChevron = false,
                            )
                            SettingsRow(
                                title = "Computer",
                                subtitle = connection.computer?.name ?: "Not connected",
                                icon = CodyncIcons.Computer,
                                showChevron = false,
                            )
                            SettingsRow(
                                title = "Status",
                                subtitle = when (current.status) {
                                    "working" -> current.activity.ifBlank { "Working" }
                                    "needsInput" -> current.activity.ifBlank { "Needs you" }
                                    "error" -> current.activity.ifBlank { "Error" }
                                    else -> "Idle"
                                },
                                icon = CodyncIcons.Bolt,
                                showChevron = false,
                            )
                            SettingsRow(
                                title = "Workspace",
                                subtitle = current.cwd.ifBlank { "Host default" },
                                icon = CodyncIcons.Folder,
                                showChevron = false,
                            )
                        }
                    }
                    if (current.kind == "group") {
                        item { SectionLabel("Members", trailing = "${current.members.size}") }
                        item {
                            SectionCard {
                                SettingsRow(
                                    title = "Add bots",
                                    subtitle = "Search the host roster and choose room access",
                                    icon = CodyncIcons.Add,
                                    onClick = { editingSection = "Members & access"; editing = true },
                                )
                            }
                        }
                        items(current.members, key = { it }) { memberId ->
                            val member = bots.firstOrNull { it.id == memberId }
                            if (member != null) {
                                SectionCard {
                                    Row(
                                        Modifier.fillMaxWidth().clickable { onOpenChat(member.id, "main") }.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        com.pokai.codync.design.CharacterAvatar(member.name, member.avatarColor, member.avatarShape, size = 38.dp)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(member.name, color = colors.text, style = CodyncType.body)
                                            Text("${member.backend} · open bot chat", color = colors.secondary, style = CodyncType.caption)
                                        }
                                    }
                                }
                            }
                        }
                        item { SectionLabel("Instructions") }
                        item {
                            SectionCard {
                                Text(
                                    current.instructions.ifBlank { "No room rules yet." },
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    color = if (current.instructions.isBlank()) colors.tertiary else colors.text,
                                    style = CodyncType.body,
                                )
                            }
                        }
                    }
                    item {
                        SectionLabel("Chat")
                        SectionCard {
                            SettingsRow(
                                title = "Open conversation",
                                subtitle = current.lastMessage ?: "Continue with ${current.name}",
                                icon = CodyncIcons.Chat,
                                onClick = { onOpenChat(current.id, "main") },
                            )
                        }
                    }
                    item {
                        Text(
                            if (connection.kind != TransportKind.Offline) "Live details from ${connection.computer?.name ?: "the host"}."
                            else connection.lastError ?: "Waiting for the host connection.",
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            color = colors.tertiary,
                            style = CodyncType.caption,
                        )
                    }
                }
            }
        }
    }

    if (showTemplate) {
        val template = remember(current) {
            Json { prettyPrint = true; encodeDefaults = false }.encodeToString(
                com.pokai.codync.model.BotDraft.serializer(),
                current.toDraft().copy(id = null, pinned = false, hidden = false),
            )
        }
        AlertDialog(
            onDismissRequest = { showTemplate = false },
            title = { Text("Create template") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Copy ${current.name}'s settings to reuse. Conversation history and IDs are excluded.", color = colors.secondary)
                    Text(
                        template,
                        modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(12.dp))
                            .background(colors.surface).verticalScroll(rememberScrollState()).padding(12.dp),
                        color = colors.text,
                        style = CodyncType.mono,
                    )
                    Text("Includes workspace preferences and agent configuration.", color = colors.tertiary, style = CodyncType.caption)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Codync bot template", template))
                    templateCopied = true
                }) { Text(if (templateCopied) "Copied" else "Copy template") }
            },
            dismissButton = { TextButton(onClick = { showTemplate = false }) { Text("Close") } },
        )
    }
}
