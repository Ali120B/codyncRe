package com.pokai.codync.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokai.codync.design.theme.AvatarPalette
import com.pokai.codync.design.theme.CodyncType

/**
 * A bot avatar: a coloured organic tile with its character. Mirrors the iPhone's
 * CharacterAvatar without pulling a drawing library into the app.
 */
@Composable
fun CharacterAvatar(
    name: String,
    colorId: String,
    shapeId: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    character: String? = null,
    statusRing: Color? = null,
) {
    val fill = AvatarPalette.color(colorId)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val path = avatarPath(shapeId, this.size.width.toFloat(), this.size.height.toFloat())
            if (statusRing != null) {
                drawPath(path, statusRing, alpha = 0.22f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.toPx() * 0.10f))
            }
            drawPath(path, fill)
            // A soft top-left sheen keeps the flat colour from looking like a generic chip.
            drawPath(
                path,
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(this.size.width * 0.28f, this.size.height * 0.18f),
                    radius = this.size.minDimension * 0.75f,
                ),
            )
        }
        val face = character?.takeIf { it.isNotBlank() } ?: name.trim().take(2)
        Text(
            text = face,
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
            style = CodyncType.subheadline.copy(fontSize = (size.value * 0.42f).sp),
        )
    }
}

private fun avatarPath(shape: String, w: Float, h: Float): Path {
    val path = Path()
    val cx = w / 2f
    val cy = h / 2f
    when (shape) {
        "squircle" -> {
            val r = minOf(w, h) * 0.34f
            path.addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(r, r)))
        }
        "tablet" -> {
            val r = minOf(w, h) * 0.27f
            path.addRoundRect(RoundRect(Rect(w * 0.12f, 0f, w * 0.88f, h), CornerRadius(r, r)))
        }
        "hex" -> {
            val r = minOf(w, h) * 0.5f
            repeat(6) { i ->
                val a = Math.toRadians(-90.0 + i * 60.0)
                val x = cx + (r * Math.cos(a)).toFloat()
                val y = cy + (r * Math.sin(a)).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        "wedge" -> {
            path.moveTo(w * 0.08f, h * 0.20f)
            path.cubicTo(w * 0.42f, h * 0.02f, w * 0.94f, h * 0.16f, w * 0.94f, h * 0.56f)
            path.cubicTo(w * 0.94f, h * 0.89f, w * 0.58f, h * 0.99f, w * 0.26f, h * 0.90f)
            path.cubicTo(w * -0.02f, h * 0.81f, w * -0.06f, h * 0.38f, w * 0.08f, h * 0.20f)
            path.close()
        }
        "cloud" -> {
            path.moveTo(w * 0.16f, h * 0.70f)
            path.cubicTo(w * -0.04f, h * 0.58f, w * 0.04f, h * 0.28f, w * 0.25f, h * 0.26f)
            path.cubicTo(w * 0.32f, h * 0.05f, w * 0.63f, h * 0.02f, w * 0.73f, h * 0.23f)
            path.cubicTo(w * 0.95f, h * 0.20f, w * 1.07f, h * 0.51f, w * 0.90f, h * 0.68f)
            path.cubicTo(w * 0.85f, h * 0.88f, w * 0.50f, h * 0.96f, w * 0.33f, h * 0.85f)
            path.cubicTo(w * 0.27f, h * 0.81f, w * 0.22f, h * 0.76f, w * 0.16f, h * 0.70f)
            path.close()
        }
        "teardrop" -> {
            path.moveTo(cx, h * 0.03f)
            path.cubicTo(w * 0.74f, h * 0.26f, w * 0.97f, h * 0.47f, w * 0.94f, h * 0.67f)
            path.cubicTo(w * 0.91f, h * 0.89f, w * 0.70f, h * 0.98f, cx, h * 0.98f)
            path.cubicTo(w * 0.30f, h * 0.98f, w * 0.09f, h * 0.89f, w * 0.06f, h * 0.67f)
            path.cubicTo(w * 0.03f, h * 0.47f, w * 0.26f, h * 0.26f, cx, h * 0.03f)
            path.close()
        }
        "pebble" -> {
            path.moveTo(w * 0.22f, h * 0.06f)
            path.cubicTo(w * 0.58f, h * -0.05f, w * 1.02f, h * 0.18f, w * 0.98f, h * 0.54f)
            path.cubicTo(w * 0.94f, h * 0.91f, w * 0.58f, h * 1.04f, w * 0.27f, h * 0.92f)
            path.cubicTo(w * -0.03f, h * 0.81f, w * -0.10f, h * 0.20f, w * 0.22f, h * 0.06f)
            path.close()
        }
        else -> {
            path.moveTo(w * 0.50f, h * 0.01f)
            path.cubicTo(w * 0.83f, h * 0.00f, w * 0.99f, h * 0.17f, w * 0.98f, h * 0.46f)
            path.cubicTo(w * 0.97f, h * 0.78f, w * 0.78f, h * 0.99f, w * 0.47f, h * 0.99f)
            path.cubicTo(w * 0.15f, h * 0.99f, w * 0.01f, h * 0.80f, w * 0.02f, h * 0.44f)
            path.cubicTo(w * 0.03f, h * 0.15f, w * 0.20f, h * 0.01f, w * 0.50f, h * 0.01f)
            path.close()
        }
    }
    return path
}

/** A small dot showing a computer's transport state. */
@Composable
fun StatusDot(color: Color, size: Dp = 7.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        drawCircle(color)
    }
}

private fun DrawScope.avatarPathPlaceholder() = Unit
