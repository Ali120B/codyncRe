package com.pokai.codync.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import com.pokai.codync.design.theme.CodyncTheme
import com.pokai.codync.design.theme.CodyncType

/** One open menu request, positioned in window coordinates. */
data class GlassMenuRequest(
    val items: List<CodyncMenuItem>,
    val anchor: Rect,
    val offset: DpOffset,
    val onDismiss: () -> Unit,
)

/** Holds the single open glass menu for the app. */
@Stable
class GlassMenuController internal constructor() {
    internal val request = mutableStateOf<GlassMenuRequest?>(null)

    fun dismiss() {
        request.value = null
    }
}

val LocalGlassMenuHost = staticCompositionLocalOf<GlassMenuController?> { null }

/**
 * Wraps the app content in a backdrop capture and overlays menus on top of it. Because menus
 * live in the same composition (not a separate Popup window) the backdrop can actually be
 * sampled, so context menus get the same liquid glass as the bottom bar.
 */
@Composable
fun ProvideGlassMenuHost(backdrop: LayerBackdrop, content: @Composable () -> Unit) {
    val controller = remember { GlassMenuController() }
    CompositionLocalProvider(LocalGlassMenuHost provides controller) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { content() }
            val request = controller.request.value
            if (request != null) {
                GlassMenuOverlay(request = request, backdrop = backdrop)
            }
        }
    }
}

@Composable
private fun GlassMenuOverlay(request: GlassMenuRequest, backdrop: Backdrop) {
    val colors = CodyncTheme.colors
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val window = LocalWindowInfo.current.containerSize
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var shown by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 460f),
        label = "glass-menu",
    )
    val margin = with(density) { 10.dp.roundToPx() }.toFloat()
    val gap = with(density) { request.offset.y.roundToPx() }.toFloat()
    val maxHeight = with(density) { (window.height / density.density - 48).dp }
    val width = menuSize.width.toFloat()
    val height = menuSize.height.toFloat()
    val x = (request.anchor.right - width)
        .coerceIn(margin, (window.width - width - margin).coerceAtLeast(margin))
    val below = request.anchor.bottom + gap
    val above = request.anchor.top - height - gap
    val maxY = (window.height - height - margin).coerceAtLeast(margin)
    val y = if (height > 0 && below + height > window.height - margin && above >= margin) {
        above.coerceIn(margin, maxY)
    } else {
        below.coerceIn(margin, maxY)
    }
    val shape = RoundedCornerShape(22.dp)
    val fill = if (colors.isDark) Color(0xE61A1A1A) else Color(0xEDFFFFFF)
    val stroke = if (colors.isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.85f)
    val scrim = Color.Black.copy(alpha = 0.16f * progress)
    val visible = menuSize.width > 0
    val appearance = if (visible) progress else 0f

    BackHandler { request.onDismiss() }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = request.onDismiss,
                ),
        )
        Column(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .onSizeChanged { menuSize = it }
                .graphicsLayer {
                    alpha = appearance
                    val scale = 0.88f + 0.12f * appearance
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(1f, 0f)
                }
                .widthIn(min = 212.dp, max = 288.dp)
                .heightIn(max = maxHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(22.dp) },
                    effects = {
                        vibrancy()
                        blur(3f.dp.toPx())
                        lens(18f.dp.toPx(), 22f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(fill) },
                )
                .border(0.7.dp, stroke, shape)
                .clip(shape)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 5.dp),
        ) {
            request.items.forEachIndexed { index, item ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(0.6.dp)
                            .background(colors.border.copy(alpha = 0.65f)),
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .clickable(
                            enabled = item.enabled,
                            role = Role.Button,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            request.onDismiss()
                            item.onClick()
                        }
                        .padding(horizontal = 15.dp, vertical = 10.dp),
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
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        shown = true
    }
}

/** Measures the anchor and opens the shared glass menu; falls back to a Popup without a host. */
@Composable
fun GlassMenuAnchorContent(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<CodyncMenuItem>,
    offset: DpOffset,
    modifier: Modifier,
    fallback: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val host = LocalGlassMenuHost.current
    var anchor by remember { mutableStateOf(Rect.Zero) }
    Box(modifier.onGloballyPositioned { anchor = it.boundsInWindow() }) {
        content()
        if (host == null) fallback()
    }
    androidx.compose.runtime.DisposableEffect(host) {
        onDispose { host?.dismiss() }
    }
    // Keyed only by visibility: the item lambdas are recreated on every recomposition (a
    // streaming chat recomposes often) and must not restart the open animation.
    androidx.compose.runtime.LaunchedEffect(expanded, host) {
        if (host == null) return@LaunchedEffect
        if (expanded && items.isNotEmpty()) {
            host.request.value = GlassMenuRequest(
                items = items,
                anchor = anchor,
                offset = offset,
                onDismiss = {
                    host.dismiss()
                    onDismissRequest()
                },
            )
        } else if (!expanded) {
            host.dismiss()
        }
    }
}
