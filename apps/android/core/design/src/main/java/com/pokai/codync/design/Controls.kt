package com.pokai.codync.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: Symbol? = null,
) {
    val colors = CodyncTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.accentFill else colors.accentDim)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                onClick()
            }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            CodyncIcon(leading, tint = colors.onAccent, size = 17.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            color = colors.onAccent,
            style = CodyncType.body.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: Symbol? = null,
) {
    val colors = CodyncTheme.colors
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.bubbleUser)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            CodyncIcon(leading, tint = colors.text, size = 17.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = colors.text, style = CodyncType.body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
fun GlassIconButton(
    symbol: Symbol,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    tint: Color = CodyncTheme.colors.text,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        CodyncIcon(symbol, tint = tint, size = iconSize, contentDescription = contentDescription)
    }
}

/** Soft destructive action — same pill shape as Secondary, danger tint. */
@Composable
fun DestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: Symbol? = null,
) {
    val colors = CodyncTheme.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(colors.danger.copy(alpha = if (colors.isDark) 0.18f else 0.10f))
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 18.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            CodyncIcon(leading, tint = colors.danger, size = 17.dp)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, color = colors.danger, style = CodyncType.body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
    }
}

@Composable
fun CodyncSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = CodyncTheme.colors
    val haptics = LocalHapticFeedback.current
    val offset by animateFloatAsState(if (checked) 14f else 0f, tween(160), label = "switch")
    Box(
        modifier = modifier
            .size(width = 34.dp, height = 20.dp)
            .clip(CircleShape)
            .background(if (checked) colors.accentFill else colors.bubbleUser)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                onCheckedChange(!checked)
            }
            .alpha(if (enabled) 1f else 0.4f),
    ) {
        Box(
            Modifier
                .padding(start = 2.dp + offset.dp, top = 2.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(if (checked) colors.onAccent else Color.White),
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = CodyncTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .border(0.6.dp, colors.border, RoundedCornerShape(20.dp)),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Symbol? = null,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = CodyncTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    onClick != null || onLongClick != null -> Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick,
                    )
                    else -> Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(11.dp))
        } else if (icon != null) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colors.bubbleAgent),
                contentAlignment = Alignment.Center,
            ) {
                CodyncIcon(icon, tint = iconTint ?: colors.text, size = 18.dp)
            }
            Spacer(Modifier.width(11.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (destructive) colors.danger else colors.text,
                style = CodyncType.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = colors.secondary,
                    style = CodyncType.footnote,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (showChevron && onClick != null) {
            Spacer(Modifier.width(6.dp))
            CodyncIcon(CodyncIcons.ChevronRight, tint = colors.tertiary, size = 17.dp)
        }
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    val colors = CodyncTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = inset)
            .height(0.6.dp)
            .background(colors.border),
    )
}

@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    val colors = CodyncTheme.colors
    Box(
        modifier
            .height(19.dp)
            .clip(CircleShape)
            .background(if (emphasis) colors.attention else colors.accentFill)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = if (emphasis) Color.White else colors.onAccent,
            style = CodyncType.caption2.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
        )
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = CodyncTheme.colors.secondary,
    fill: Color = CodyncTheme.colors.surface,
    symbol: Symbol? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(fill)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (symbol != null) CodyncIcon(symbol, tint = color, size = 13.dp)
        Text(text, color = color, style = CodyncType.caption, maxLines = 1)
    }
}

@Composable
fun FilterChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = CodyncTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) colors.accentFill else colors.surface)
            .border(0.6.dp, if (selected) colors.accentFill else colors.border, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) colors.onAccent else colors.secondary,
            style = CodyncType.caption.copy(fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
fun Spinner(size: Dp = 16.dp, color: Color = CodyncTheme.colors.secondary) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "turn",
    )
    Canvas(Modifier.size(size)) {
        drawArc(
            color = color,
            startAngle = turn - 20f,
            sweepAngle = 280f,
            useCenter = false,
            style = Stroke(width = size.toPx() / 9f, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String,
    symbol: Symbol,
    modifier: Modifier = Modifier,
) {
    val colors = CodyncTheme.colors
    Column(
        modifier.padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            CodyncIcon(symbol, tint = colors.tertiary, size = 26.dp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = colors.text, style = CodyncType.headline)
        Spacer(Modifier.height(4.dp))
        Text(
            subtitle,
            color = colors.secondary,
            style = CodyncType.footnote,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Long-press wrapper that does not consume taps. */
fun Modifier.onLongPressOnly(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    // Long press is provided by combinedClickable at call sites; this exists for future rows.
}
