package com.pokai.codync.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType
import kotlinx.coroutines.delay

@Immutable
data class CodyncMenuItem(
    val label: String,
    val symbol: Symbol? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Custom glass-styled context menu. It grows from the trigger corner, then gets out of the way
 * quickly when dismissed.
 */
@Composable
fun CodyncMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<CodyncMenuItem>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
) {
    if (items.isEmpty()) return

    var renderPopup by remember { mutableStateOf(false) }
    val visibility = remember { MutableTransitionState(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            renderPopup = true
            visibility.targetState = true
        } else if (renderPopup) {
            visibility.targetState = false
            delay(135)
            renderPopup = false
        }
    }

    if (!renderPopup) return

    val colors = CodyncTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(18.dp)
    val fill = if (colors.isDark) Color(0xE51C1C1C) else Color(0xECF7F7F7)
    val stroke = if (colors.isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
    val popupOffset = with(density) {
        IntOffset(offset.x.roundToPx(), offset.y.roundToPx())
    }
    val positionProvider = remember(popupOffset) {
        FitMenuInWindow(
            offset = popupOffset,
            margin = with(density) { 8.dp.roundToPx() },
        )
    }
    val maxMenuHeight = (LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(120).dp

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true, clippingEnabled = true),
    ) {
        AnimatedVisibility(
            visibleState = visibility,
            enter = fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) +
                slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    initialOffsetY = { -with(density) { 8.dp.roundToPx() } },
                ) + scaleIn(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.88f,
                    transformOrigin = TransformOrigin(1f, 0f),
                ),
            exit = fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing)) + slideOutVertically(
                animationSpec = tween(120, easing = FastOutSlowInEasing),
                targetOffsetY = { -with(density) { 4.dp.roundToPx() } },
            ) + scaleOut(
                animationSpec = tween(120, easing = FastOutSlowInEasing),
                targetScale = 0.94f,
                transformOrigin = TransformOrigin(1f, 0f),
            ),
        ) {
            Column(
                modifier
                    .shadow(
                        elevation = 18.dp,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.22f),
                    )
                    .widthIn(min = 196.dp, max = 280.dp)
                    .clip(shape)
                    .background(fill)
                    .border(0.7.dp, stroke, shape)
                    .heightIn(max = maxMenuHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
            ) {
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(0.6.dp)
                                .background(colors.border.copy(alpha = 0.7f)),
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clickable(
                                enabled = item.enabled,
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onDismissRequest()
                                item.onClick()
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.symbol != null) {
                            CodyncIcon(
                                item.symbol,
                                tint = when {
                                    !item.enabled -> colors.tertiary.copy(alpha = 0.45f)
                                    item.destructive -> colors.danger
                                    else -> colors.text
                                },
                                size = 18.dp,
                            )
                            Spacer(Modifier.width(11.dp))
                        }
                        Text(
                            item.label,
                            color = when {
                                !item.enabled -> colors.tertiary.copy(alpha = 0.45f)
                                item.destructive -> colors.danger
                                else -> colors.text
                            },
                            style = CodyncType.body.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Places a floating menu below its actual anchor when possible and keeps it inside the app window. */
private class FitMenuInWindow(
    private val offset: IntOffset,
    private val margin: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val rawX = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width + offset.x
        } else {
            anchorBounds.left + offset.x
        }
        val x = rawX.coerceIn(margin, maxX)

        val below = anchorBounds.bottom + offset.y
        val above = anchorBounds.top - popupContentSize.height - offset.y
        val availableBelow = windowSize.height - anchorBounds.bottom - margin
        val availableAbove = anchorBounds.top - margin
        val rawY = when {
            below + popupContentSize.height <= windowSize.height - margin -> below
            above >= margin -> above
            availableBelow >= availableAbove -> below
            else -> above
        }
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        return IntOffset(x, rawY.coerceIn(margin, maxY))
    }
}

@Composable
fun CodyncMenuAnchor(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<CodyncMenuItem>,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 4.dp),
    content: @Composable () -> Unit,
) {
    GlassMenuAnchorContent(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        items = items,
        offset = offset,
        modifier = modifier,
        fallback = { CodyncMenu(expanded, onDismissRequest, items, offset = offset) },
        content = content,
    )
}

@Composable
fun CodyncActionList(
    items: List<CodyncMenuItem>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
) {
    val colors = CodyncTheme.colors
    val haptics = LocalHapticFeedback.current
    Column(modifier.padding(contentPadding)) {
        items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = item.enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        item.onClick()
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.symbol != null) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (item.destructive) colors.danger.copy(alpha = 0.12f)
                                else colors.bubbleAgent,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CodyncIcon(
                            item.symbol,
                            tint = if (item.destructive) colors.danger else colors.text,
                            size = 18.dp,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    item.label,
                    color = if (item.destructive) colors.danger else colors.text,
                    style = CodyncType.body.copy(fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}
