package com.pokai.codync.android.ui.workbench

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.android.fake.FakeRoutine
import com.pokai.codync.android.fake.FakeTransport
import com.pokai.codync.android.ui.HeaderIconButton
import com.pokai.codync.android.ui.SearchField
import com.pokai.codync.android.ui.SectionLabel
import com.pokai.codync.android.ui.relativeShort
import com.pokai.codync.android.ui.roster.BotAvatar
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.Pill
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.StatusDot
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import kotlinx.coroutines.delay

@Composable
internal fun WorkbenchScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CodyncTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(16)
        shown = true
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderIconButton(CodyncIcons.Back, "Back", onBack)
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.text, style = CodyncType.title)
                Text(subtitle, color = colors.secondary, style = CodyncType.footnote)
            }
            trailing?.invoke()
        }
        AnimatedVisibility(
            visible = shown,
            enter = fadeIn() + slideInVertically { it / 16 },
            exit = fadeOut(),
            modifier = Modifier.weight(1f),
        ) { content() }
    }
}

@Composable
fun TeamMapScreen(fake: FakeHostState, onBack: () -> Unit, onOpenChat: (botId: String) -> Unit) {
    val colors = CodyncTheme.colors
    val agents = fake.visibleBots().filter { it.kind != "group" }
    WorkbenchScaffold("Team map", "Ask edges, groups and handoffs", onBack) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
            item {
                SectionCard {
                    Box(Modifier.fillMaxWidth().height(220.dp).padding(12.dp)) {
                        Canvas(Modifier.fillMaxSize()) {
                            val n = agents.size.coerceAtLeast(1)
                            val cx = size.width / 2
                            val cy = size.height / 2
                            val r = size.minDimension * 0.32f
                            val pts = agents.mapIndexed { i, _ ->
                                val a = (i.toFloat() / n) * (Math.PI * 2).toFloat() - (Math.PI / 2).toFloat()
                                Offset(cx + kotlin.math.cos(a) * r, cy + kotlin.math.sin(a) * r)
                            }
                            fake.askEdges.forEach { edge ->
                                val from = agents.indexOfFirst { it.id == edge.fromBotId }
                                val to = agents.indexOfFirst { it.id == edge.toBotId }
                                if (from >= 0 && to >= 0) {
                                    drawLine(
                                        color = colors.border,
                                        start = pts[from],
                                        end = pts[to],
                                        strokeWidth = 3f,
                                        cap = StrokeCap.Round,
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
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
                                    .clickable { onOpenChat(bot.id) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BotAvatar(fake, bot, size = 28.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(bot.name, color = colors.text, style = CodyncType.caption.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Ask edges", trailing = "${fake.askEdges.size}") }
            items(fake.askEdges) { edge ->
                val from = fake.bot(edge.fromBotId)
                val to = fake.bot(edge.toBotId)
                SectionCard(Modifier.padding(bottom = 8.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(from?.name ?: "?", color = colors.text, style = CodyncType.body)
                        Spacer(Modifier.width(8.dp))
                        Pill(edge.label, fill = colors.bubbleAgent, color = colors.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(to?.name ?: "?", color = colors.text, style = CodyncType.body)
                    }
                }
            }
            item { SectionLabel("Groups · @all standup") }
            items(fake.visibleBots().filter { it.kind == "group" }) { room ->
                SectionCard(Modifier.padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp).clickable { onOpenChat(room.id) }) {
                        Text(room.name, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            "${room.members.size} members · room tools · skills/vault · ${room.description}",
                            color = colors.secondary,
                            style = CodyncType.footnote,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            room.members.take(5).forEach { mid ->
                                fake.bot(mid)?.let { BotAvatar(fake, it, size = 22.dp) }
                            }
                            if (room.members.size > 5) {
                                Text("+${room.members.size - 5}", color = colors.tertiary, style = CodyncType.caption)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RoutinesScreen(fake: FakeHostState, onBack: () -> Unit) {
    val colors = CodyncTheme.colors
    var editing by remember { mutableStateOf<FakeRoutine?>(null) }
    WorkbenchScaffold(
        title = "Routines",
        subtitle = "${fake.routines.count { it.enabled }} on · schedules, webhooks, test runs",
        onBack = onBack,
        trailing = {
            HeaderIconButton(
                CodyncIcons.Add,
                "New routine",
                onClick = {
                    editing = FakeRoutine(
                        id = "rtn_${System.currentTimeMillis()}",
                        botId = fake.visibleBots().firstOrNull()?.id ?: "",
                        title = "New routine",
                        schedule = "Daily 09:00",
                        enabled = true,
                        lastRunAt = null,
                        prompt = "",
                    )
                },
            )
        },
    ) {
        if (editing != null) {
            RoutineEditor(
                fake = fake,
                routine = editing!!,
                onSave = {
                    fake.updateRoutine(it)
                    editing = null
                },
                onDelete = {
                    fake.deleteRoutine(it.id)
                    editing = null
                },
                onCancel = { editing = null },
                onTest = { fake.testRoutine(it.id); editing = fake.routines.firstOrNull { r -> r.id == it.id } },
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
                items(fake.routines, key = { it.id }) { rtn ->
                    val bot = fake.bot(rtn.botId)
                    SectionCard(Modifier.padding(bottom = 10.dp)) {
                        Column(Modifier.padding(14.dp).clickable { editing = rtn }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(rtn.title, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                                    Text(
                                        "${bot?.name ?: "Bot"} · ${rtn.schedule} · ${rtn.timezone}",
                                        color = colors.secondary,
                                        style = CodyncType.footnote,
                                    )
                                }
                                CodyncSwitch(rtn.enabled, onCheckedChange = { fake.toggleRoutine(rtn.id) })
                            }
                            if (rtn.webhook != null) {
                                Spacer(Modifier.height(6.dp))
                                Pill("Webhook", fill = colors.bubbleAgent, color = colors.tertiary, symbol = CodyncIcons.Cloud)
                            }
                            rtn.lastRunAt?.let {
                                Spacer(Modifier.height(4.dp))
                                Text("Last run ${relativeShort(it)}", color = colors.tertiary, style = CodyncType.caption2)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineEditor(
    fake: FakeHostState,
    routine: FakeRoutine,
    onSave: (FakeRoutine) -> Unit,
    onDelete: (FakeRoutine) -> Unit,
    onCancel: () -> Unit,
    onTest: (FakeRoutine) -> Unit,
) {
    val colors = CodyncTheme.colors
    var draft by remember(routine.id) { mutableStateOf(routine) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Title", draft.title) { draft = draft.copy(title = it) }
                Field("Schedule", draft.schedule) { draft = draft.copy(schedule = it) }
                Field("Timezone", draft.timezone) { draft = draft.copy(timezone = it) }
                Field("Webhook (optional)", draft.webhook.orEmpty()) {
                    draft = draft.copy(webhook = it.ifBlank { null })
                }
                Field("Prompt", draft.prompt, singleLine = false) { draft = draft.copy(prompt = it) }
                Text("Bot", color = colors.tertiary, style = CodyncType.caption)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    fake.visibleBots().forEach { bot ->
                        FilterChip(
                            text = bot.name,
                            selected = draft.botId == bot.id,
                            onClick = { draft = draft.copy(botId = bot.id) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("Run history", trailing = "${draft.runs.size}")
        draft.runs.take(5).forEach { run ->
            SectionCard(Modifier.padding(bottom = 8.dp)) {
                SettingsRow(
                    title = run.status.uppercase(),
                    subtitle = "${relativeShort(run.at)} · ${run.detail}",
                    icon = if (run.status == "ok") CodyncIcons.CheckCircle else CodyncIcons.Warning,
                    showChevron = false,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Save", onClick = { onSave(draft) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Test run", onClick = { onTest(draft) }, modifier = Modifier.fillMaxWidth(), leading = CodyncIcons.Play)
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        DestructiveButton("Delete routine", onClick = { onDelete(draft) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    val colors = CodyncTheme.colors
    Text(label, color = colors.tertiary, style = CodyncType.caption)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = colors.text, fontSize = CodyncType.body.fontSize, fontFamily = CodyncType.body.fontFamily),
        cursorBrush = SolidColor(colors.text),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(12.dp)
            .then(if (!singleLine) Modifier.height(88.dp) else Modifier),
    )
}

@Composable
fun MemoryScreen(fake: FakeHostState, onBack: () -> Unit) {
    val colors = CodyncTheme.colors
    var query by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    val filtered = fake.memories
        .filter { query.isBlank() || it.text.contains(query, true) || fake.bot(it.botId)?.name?.contains(query, true) == true }
        .sortedWith(compareByDescending<com.pokai.codync.android.fake.FakeMemory> { it.pinned }.thenByDescending { it.updatedAt })
    WorkbenchScaffold("Memory", "Search, pin, review, import/export", onBack) {
        Column(Modifier.fillMaxSize()) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search memories",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Field("Add memory", draft, singleLine = false) { draft = it }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("Save", onClick = {
                            fake.addMemory(fake.visibleBots().firstOrNull()?.id ?: return@PrimaryButton, draft)
                            draft = ""
                        })
                        SecondaryButton("Export", onClick = { fake.note("memory export · ${fake.memories.size} rows") })
                        SecondaryButton("Import", onClick = {
                            fake.addMemory(fake.visibleBots().firstOrNull()?.id ?: return@SecondaryButton, "Imported memory from file.")
                        })
                    }
                }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), modifier = Modifier.weight(1f)) {
                items(filtered, key = { it.id }) { mem ->
                    val bot = fake.bot(mem.botId)
                    SectionCard(Modifier.padding(bottom = 10.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(bot?.name ?: "Bot", color = colors.tertiary, style = CodyncType.caption)
                                Spacer(Modifier.width(6.dp))
                                Pill(mem.source, fill = colors.bubbleAgent, color = colors.tertiary)
                                Spacer(Modifier.weight(1f))
                                if (mem.pinned) Pill("Pinned", fill = colors.warning.copy(alpha = 0.15f), color = colors.warning)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(mem.text, color = colors.text, style = CodyncType.body)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton(if (mem.pinned) "Unpin" else "Pin", onClick = { fake.pinMemory(mem.id) })
                                DestructiveButton("Delete", onClick = { fake.deleteMemory(mem.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MarketplaceScreen(fake: FakeHostState, onBack: () -> Unit) {
    val colors = CodyncTheme.colors
    var kind by remember { mutableStateOf("all") }
    var selected by remember { mutableStateOf<String?>(null) }
    val items = fake.marketplace.filter { kind == "all" || it.kind == kind }
    val detail = selected?.let { id -> fake.marketplace.firstOrNull { it.id == id } }
    WorkbenchScaffold("Marketplace", "Agents, MCP, Composio, skills, vault", onBack) {
        if (detail != null) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                SectionCard {
                    Column(Modifier.padding(16.dp)) {
                        Text(detail.name, color = colors.text, style = CodyncType.title)
                        Text("${detail.kind} · ${detail.detail}", color = colors.secondary, style = CodyncType.footnote)
                        Spacer(Modifier.height(12.dp))
                        CodyncSwitch(detail.installed, onCheckedChange = { fake.toggleMarket(detail.id) })
                        if (detail.needsSignIn && !detail.installed) {
                            Spacer(Modifier.height(8.dp))
                            PrimaryButton("Sign in connector", onClick = {
                                fake.toggleMarket(detail.id)
                                fake.note("signed in · ${detail.name}")
                            }, modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Setup terminal", color = colors.tertiary, style = CodyncType.caption)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            detail.setupLog.ifBlank { "$ ${detail.name.lowercase().replace(' ', '-')} setup\n… waiting" },
                            color = colors.secondary,
                            style = CodyncType.mono,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.codeBackground)
                                .padding(12.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Per-bot toggles", color = colors.tertiary, style = CodyncType.caption)
                        if (!detail.installed) {
                            Text("Install this item to enable it for individual bots.", color = colors.secondary, style = CodyncType.footnote)
                        } else {
                            fake.visibleBots().forEach { bot ->
                                val enabled = if (detail.enabledBots.isEmpty()) true else bot.id in detail.enabledBots
                                SettingsRow(
                                    title = bot.name,
                                    subtitle = bot.backend,
                                    showChevron = false,
                                    trailing = {
                                        CodyncSwitch(
                                            checked = enabled,
                                            onCheckedChange = { checked ->
                                                fake.setMarketForBot(detail.id, bot.id, checked)
                                            },
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Back to list", onClick = { selected = null }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    listOf("all", "agent", "mcp", "composio", "skill", "credential").forEach { k ->
                        FilterChip(text = k.replaceFirstChar { it.uppercase() }, selected = kind == k, onClick = { kind = k })
                    }
                }
                if (fake.connectionRequests.isNotEmpty()) {
                    SectionLabel("Connection requests", trailing = "${fake.connectionRequests.size}")
                    fake.connectionRequests.forEach { req ->
                        SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text(req.name, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                                Text("${req.kind} · ${relativeShort(req.createdAt)}", color = colors.secondary, style = CodyncType.footnote)
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PrimaryButton("Allow", onClick = { fake.resolveConnectionRequest(req.id, true) })
                                    DestructiveButton("Deny", onClick = { fake.resolveConnectionRequest(req.id, false) })
                                }
                            }
                        }
                    }
                }
                LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), modifier = Modifier.weight(1f)) {
                    items(items, key = { it.id }) { item ->
                        SectionCard(Modifier.padding(bottom = 10.dp)) {
                            SettingsRow(
                                title = item.name,
                                subtitle = "${item.kind} · ${item.detail}",
                                icon = when (item.kind) {
                                    "mcp", "composio" -> CodyncIcons.Cloud
                                    "skill" -> CodyncIcons.Science
                                    "credential" -> CodyncIcons.Lock
                                    else -> CodyncIcons.Bots
                                },
                                onClick = { selected = item.id },
                                trailing = {
                                    CodyncSwitch(item.installed, onCheckedChange = { fake.toggleMarket(item.id) })
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComputerPanelScreen(fake: FakeHostState, onBack: () -> Unit, onOpenPairing: () -> Unit) {
    val colors = CodyncTheme.colors
    var tab by remember { mutableStateOf("Computer") }
    val tabs = listOf("Computer", "Cloud", "Phones", "Browser", "Files", "Routines", "Usage")
    WorkbenchScaffold("Computer", "Operate a host from your phone", onBack) {
        Column {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                tabs.forEach { t -> FilterChip(text = t, selected = tab == t, onClick = { tab = t }) }
            }
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp)) {
                when (tab) {
                    "Computer" -> {
                        items(fake.computers) { c ->
                            SectionCard(Modifier.padding(bottom = 10.dp)) {
                                SettingsRow(
                                    title = c.name,
                                    subtitle = "${c.platform} · ${c.version} · ${relativeShort(c.lastSeen)}",
                                    icon = CodyncIcons.Computer,
                                    showChevron = false,
                                    trailing = {
                                        StatusDot(
                                            color = when (c.transport) {
                                                FakeTransport.Direct -> colors.added
                                                FakeTransport.Relay -> colors.warning
                                                FakeTransport.Offline -> colors.tertiary
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        item {
                            SectionCard {
                                SettingsRow(
                                    title = "Pair another computer",
                                    subtitle = "QR or paste from desktop",
                                    icon = CodyncIcons.QrCode,
                                    onClick = onOpenPairing,
                                )
                            }
                        }
                    }
                    "Cloud" -> item {
                        SectionCard {
                            Column(Modifier.padding(16.dp)) {
                                Text("Cloudflare relay", color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                                Spacer(Modifier.height(6.dp))
                                Text("Encrypted mailbox when the computer is asleep.", color = colors.secondary, style = CodyncType.footnote)
                                Spacer(Modifier.height(10.dp))
                                Pill("Connected", fill = colors.added.copy(alpha = 0.12f), color = colors.added)
                            }
                        }
                    }
                    "Phones" -> item {
                        SectionCard {
                            SettingsRow(
                                title = "This Android",
                                subtitle = "Codync-Sig · one device identity",
                                icon = CodyncIcons.Phone,
                                showChevron = false,
                                trailing = { Pill("You", fill = colors.bubbleAgent, color = colors.secondary) },
                            )
                        }
                    }
                    "Browser" -> {
                        items(fake.browserFrames) { frame ->
                            SectionCard(Modifier.padding(bottom = 10.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(frame.title, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                                    Text(frame.url, color = colors.secondary, style = CodyncType.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(10.dp))
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.codeBackground),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("Browser frame preview", color = colors.tertiary, style = CodyncType.caption)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PrimaryButton("Takeover", onClick = { fake.note("browser takeover · ${frame.title}") })
                                        SecondaryButton("Expand", onClick = { fake.note("browser expand · ${frame.title}") })
                                    }
                                }
                            }
                        }
                    }
                    "Files" -> items(fake.files) { file ->
                        SectionCard(Modifier.padding(bottom = 8.dp)) {
                            SettingsRow(
                                title = file.name,
                                subtitle = "${file.sizeLabel} · ${relativeShort(file.updatedAt)}" +
                                    (file.botId?.let { " · ${fake.bot(it)?.name}" } ?: ""),
                                icon = CodyncIcons.Folder,
                                showChevron = false,
                                trailing = {
                                    SecondaryButton("Share", onClick = { fake.note("share · ${file.name}") })
                                },
                            )
                        }
                    }
                    "Routines" -> items(fake.routines) { rtn ->
                        SectionCard(Modifier.padding(bottom = 8.dp)) {
                            SettingsRow(
                                title = rtn.title,
                                subtitle = rtn.schedule,
                                icon = CodyncIcons.Schedule,
                                showChevron = false,
                                trailing = { CodyncSwitch(rtn.enabled, onCheckedChange = { fake.toggleRoutine(rtn.id) }) },
                            )
                        }
                    }
                    "Usage" -> items(fake.usage) { u ->
                        SectionCard(Modifier.padding(bottom = 10.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(u.name, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                                    Spacer(Modifier.weight(1f))
                                    Text("${u.used} ${u.limit}", color = colors.secondary, style = CodyncType.footnote)
                                }
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.bubbleAgent),
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth(u.pct.coerceIn(0f, 1f))
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (u.pct > 0.8f) colors.warning else colors.added),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
