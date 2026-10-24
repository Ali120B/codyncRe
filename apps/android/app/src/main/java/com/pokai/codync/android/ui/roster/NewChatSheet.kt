package com.pokai.codync.android.ui.roster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * New chat / room composer. One recipient starts a fresh chat with that bot; two or more
 * create a group on the host first. The host owns membership validation (2–50 bots).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatSheet(
    session: SessionStore,
    bots: List<Bot>,
    onDismiss: () -> Unit,
    onOpenChat: (botId: String, chatId: String) -> Unit,
) {
    val colors = CodyncTheme.colors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val candidates = remember(bots) {
        bots.filter { it.kind != "group" }.sortedWith(
            compareByDescending<Bot> { it.pinned }.thenBy { it.position }.thenBy { it.name.lowercase() },
        )
    }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var name by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val group = selected.size > 1

    ModalBottomSheet(
        onDismissRequest = { if (!creating) onDismiss() },
        sheetState = sheetState,
        containerColor = colors.background,
        contentColor = colors.text,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                "New chat",
                modifier = Modifier.padding(horizontal = 22.dp),
                color = colors.text,
                style = CodyncType.headline,
            )
            Text(
                if (group) "Pick the room's bots, then name it." else "Pick a bot. Add another to make a room.",
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 3.dp),
                color = colors.secondary,
                style = CodyncType.footnote,
            )

            if (group) {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ROOM NAME", color = colors.tertiary, style = CodyncType.caption)
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it.take(80) },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = colors.text,
                                fontSize = 16.sp,
                                fontFamily = CodyncType.body.fontFamily,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(colors.text),
                            decorationBox = { inner ->
                                androidx.compose.foundation.layout.Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surface)
                                        .padding(12.dp),
                                ) {
                                    if (name.isBlank()) {
                                        Text("e.g. Release room", color = colors.tertiary, style = CodyncType.body)
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }

            if (candidates.isEmpty()) {
                Text(
                    "No bots on this computer yet. Create one from the desktop app, then pull to refresh.",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                    color = colors.tertiary,
                    style = CodyncType.footnote,
                )
            } else {
                SectionCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    candidates.forEachIndexed { index, bot ->
                        if (index > 0) {
                            com.pokai.codync.design.DividerLine(inset = 54.dp)
                        }
                        SettingsRow(
                            title = bot.name,
                            subtitle = bot.description.ifBlank { bot.backend },
                            icon = null,
                            showChevron = false,
                            trailing = {
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    CharacterAvatar(
                                        name = bot.name,
                                        colorId = bot.avatarColor,
                                        shapeId = bot.avatarShape,
                                        size = 30.dp,
                                    )
                                    CodyncSwitch(
                                        checked = bot.id in selected,
                                        onCheckedChange = { checked ->
                                            selected = if (checked) selected + bot.id else selected - bot.id
                                        },
                                    )
                                }
                            },
                            onClick = {
                                selected = if (bot.id in selected) selected - bot.id else selected + bot.id
                            },
                        )
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
                    "${selected.size} bots · groups hold 2–50",
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = colors.tertiary,
                    style = CodyncType.caption,
                )
            }
            error?.let {
                Text(it, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp), color = colors.warning, style = CodyncType.footnote)
            }

            PrimaryButton(
                text = when {
                    creating -> "Creating…"
                    group -> "Create room"
                    else -> "Start chat"
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
                enabled = selected.isNotEmpty() && !creating && (!group || name.isNotBlank()),
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
                            put("members", kotlinx.serialization.json.buildJsonArray {
                                selected.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
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
}
