package com.pokai.codync.android.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pokai.codync.android.fake.FakeHostState
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.CodyncSwitch
import com.pokai.codync.design.DestructiveButton
import com.pokai.codync.design.FilterChip
import com.pokai.codync.design.PrimaryButton
import com.pokai.codync.design.SecondaryButton
import com.pokai.codync.design.SectionCard
import com.pokai.codync.design.SettingsRow
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType

@Composable
fun BotEditorScreen(
    fake: FakeHostState,
    botId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val colors = CodyncTheme.colors
    val bot = fake.bot(botId) ?: run {
        WorkbenchScaffold("Missing bot", "This bot is gone", onBack) {}
        return
    }
    var name by remember(bot.id) { mutableStateOf(bot.name) }
    var description by remember(bot.id) { mutableStateOf(bot.description) }
    var backend by remember(bot.id) { mutableStateOf(bot.backend) }
    var color by remember(bot.id) { mutableStateOf(bot.color) }
    var shape by remember(bot.id) { mutableStateOf(bot.shape) }
    var character by remember(bot.id) { mutableStateOf(bot.character) }
    var hidden by remember(bot.id) { mutableStateOf(bot.hidden) }
    var pinned by remember(bot.id) { mutableStateOf(bot.pinned) }
    var members by remember(bot.id) { mutableStateOf(bot.members.toSet()) }
    val isGroup = bot.kind == "group"

    WorkbenchScaffold(
        title = if (isGroup) "Edit group" else "Edit bot",
        subtitle = if (isGroup) "Members, tools, skills and vault" else "Model, Fast mode, workspace, templates",
        onBack = onBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SectionCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField("Name", name) { name = it }
                    LabeledField("Description", description) { description = it }
                    if (!isGroup) {
                        LabeledField("Backend / model", backend) { backend = it }
                        Text("Color", color = colors.tertiary, style = CodyncType.caption)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("violet", "blue", "teal", "green", "amber", "orange", "magenta", "rose").forEach { c ->
                                FilterChip(text = c, selected = color == c, onClick = { color = c })
                            }
                        }
                        Text("Shape", color = colors.tertiary, style = CodyncType.caption)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("blob", "circle", "squircle", "hex", "cloud").forEach { s ->
                                FilterChip(text = s, selected = shape == s, onClick = { shape = s })
                            }
                        }
                        LabeledField("Character", character) { character = it }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SectionCard {
                Column {
                    SettingsRow(
                        title = "Pinned in roster",
                        showChevron = false,
                        trailing = { CodyncSwitch(pinned, onCheckedChange = { pinned = it }) },
                        onClick = { pinned = !pinned },
                    )
                    SettingsRow(
                        title = "Hidden",
                        subtitle = "Stays out of the main list",
                        showChevron = false,
                        trailing = { CodyncSwitch(hidden, onCheckedChange = { hidden = it }) },
                        onClick = { hidden = !hidden },
                    )
                    if (!isGroup) {
                        SettingsRow(
                            title = "Fast mode",
                            subtitle = "Prefer quicker models for this bot",
                            showChevron = false,
                            trailing = { CodyncSwitch(fake.fastMode, onCheckedChange = { fake.fastMode = it }) },
                            onClick = { fake.fastMode = !fake.fastMode },
                        )
                        SettingsRow(
                            title = "Workspace",
                            subtitle = fake.selectedWorkspace,
                            icon = CodyncIcons.Folder,
                            showChevron = false,
                            onClick = {
                                fake.selectedWorkspace = if (fake.selectedWorkspace.contains("realbot")) "~/projects" else "~/projects/realbot"
                            },
                        )
                        SettingsRow(
                            title = "Template",
                            subtitle = "Coding · shipping · research",
                            icon = CodyncIcons.Science,
                            showChevron = false,
                            onClick = { fake.note("template picker · $name") },
                        )
                    }
                }
            }
            if (isGroup) {
                Spacer(Modifier.height(12.dp))
                SectionCard {
                    Column(Modifier.padding(14.dp)) {
                        Text("Members (2–50)", color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(Modifier.height(8.dp))
                        fake.visibleBots().filter { it.kind != "group" && it.id != bot.id }.forEach { candidate ->
                            SettingsRow(
                                title = candidate.name,
                                subtitle = candidate.backend,
                                showChevron = false,
                                trailing = {
                                    CodyncSwitch(
                                        checked = candidate.id in members,
                                        onCheckedChange = {
                                            members = if (it) members + candidate.id else members - candidate.id
                                        },
                                    )
                                },
                                onClick = {
                                    members = if (candidate.id in members) members - candidate.id else members + candidate.id
                                },
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Room tools · Files · Routines · Skills/Vault", color = colors.secondary, style = CodyncType.footnote)
                        Text("@mentions · one-shot responders · @all standup", color = colors.secondary, style = CodyncType.footnote)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                "Save",
                onClick = {
                    fake.updateBot(
                        id = bot.id,
                        name = name,
                        description = description,
                        backend = backend,
                        color = color,
                        shape = shape,
                        character = character,
                        hidden = hidden,
                        pinned = pinned,
                        members = if (isGroup) members.toList() else null,
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Cancel", onClick = onBack, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            DestructiveButton(
                if (isGroup) "Delete group" else "Delete bot",
                onClick = {
                    fake.deleteBot(bot.id)
                    onDeleted()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    val colors = CodyncTheme.colors
    Text(label, color = colors.tertiary, style = CodyncType.caption)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.text, fontSize = CodyncType.body.fontSize, fontFamily = CodyncType.body.fontFamily),
        cursorBrush = SolidColor(colors.text),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .padding(12.dp),
    )
}
