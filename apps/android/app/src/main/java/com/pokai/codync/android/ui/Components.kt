package com.pokai.codync.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pokai.codync.design.AvatarMood
import com.pokai.codync.design.CodyncIcon
import com.pokai.codync.design.CodyncIcons
import com.pokai.codync.design.Symbol
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import com.pokai.codync.model.Bot

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    autofocus: Boolean = false,
    focusRequest: Int = 0,
    onSubmit: (() -> Unit)? = null,
    /** When set, the field is a button that opens the full search screen instead of typing inline. */
    onOpen: (() -> Unit)? = null,
) {
    val colors = CodyncTheme.colors
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autofocus, focusRequest) {
        if (autofocus && onOpen == null) {
            androidx.compose.runtime.withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .then(if (onOpen != null) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CodyncIcon(CodyncIcons.Search, tint = colors.tertiary, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        if (onOpen != null) {
            Text(placeholder, color = colors.tertiary, style = CodyncType.body, modifier = Modifier.weight(1f))
        } else {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.tertiary, style = CodyncType.body)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (autofocus) Modifier.focusRequester(focusRequester) else Modifier),
                    textStyle = TextStyle(color = colors.text, fontSize = CodyncType.body.fontSize, fontFamily = CodyncType.body.fontFamily),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.text),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focus.clearFocus()
                            onSubmit?.invoke()
                        },
                    ),
                )
            }
            if (value.isNotEmpty()) {
                CodyncIcon(
                    CodyncIcons.Close,
                    tint = colors.tertiary,
                    size = 17.dp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onValueChange("") },
                    contentDescription = "Clear",
                )
            }
        }
    }
}

@Composable
fun BotIdentityAvatar(bot: Bot, roster: List<Bot>, size: androidx.compose.ui.unit.Dp = 42.dp) {
    val mood = avatarMoodFor(bot.status)
    if (bot.kind != "group") {
        com.pokai.codync.design.CharacterAvatar(bot.name, bot.avatarColor, bot.avatarShape, size = size, mood = mood)
        return
    }
    val members = bot.members.take(2).mapNotNull { memberId -> roster.firstOrNull { it.id == memberId } }
    if (members.isEmpty()) {
        com.pokai.codync.design.CharacterAvatar(bot.name, bot.avatarColor, bot.avatarShape, size = size, mood = mood)
        return
    }
    val faceSize = size * 0.68f
    Box(Modifier.width(size + 4.dp).height(size)) {
        members.forEachIndexed { index, member ->
            com.pokai.codync.design.CharacterAvatar(
                name = member.name,
                colorId = member.avatarColor,
                shapeId = member.avatarShape,
                size = faceSize,
                mood = mood,
                modifier = Modifier.align(if (index == 0) Alignment.CenterStart else Alignment.CenterEnd),
            )
        }
    }
}

fun avatarMoodFor(status: String?): AvatarMood = when (status) {
    "working" -> AvatarMood.Working
    "needsInput", "error" -> AvatarMood.NeedsInput
    else -> AvatarMood.Idle
}

@Composable
fun LargeHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = CodyncTheme.colors.text, style = CodyncType.largeTitle)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = CodyncTheme.colors.secondary, style = CodyncType.footnote)
            }
        }
        if (trailing != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                trailing()
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: String? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), color = CodyncTheme.colors.tertiary, style = CodyncType.caption)
        Spacer(Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = CodyncTheme.colors.tertiary, style = CodyncType.caption)
    }
}

@Composable
fun IconAction(
    symbol: Symbol,
    label: String,
    onClick: () -> Unit,
    tint: Color = CodyncTheme.colors.text,
) {
    com.pokai.codync.design.GlassIconButton(symbol, label, onClick, size = 40.dp, iconSize = 21.dp, tint = tint)
}

@Composable
fun HeaderIconButton(
    symbol: Symbol,
    label: String,
    onClick: () -> Unit,
    tint: Color = CodyncTheme.colors.text,
) {
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CodyncIcon(symbol, tint = tint, size = 21.dp, contentDescription = label)
    }
}

@Composable
fun VerticalDivider(height: Int = 16) {
    Box(
        Modifier
            .width(0.6.dp)
            .height(height.dp)
            .background(CodyncTheme.colors.border),
    )
}
