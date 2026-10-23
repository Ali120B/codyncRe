package com.pokai.codync.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF000000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE2E2E2),
    onPrimaryContainer = Color(0xFF141414),
    secondary = Color(0xFF6B6B6B),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF141414),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141414),
    surfaceVariant = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF6B6B6B),
    outline = Color(0xFFE6E6E6),
    outlineVariant = Color(0xFFF0F0F0),
    error = Color(0xFFC23A2B),
    onError = Color(0xFFFFFFFF),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF363636),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFF9A9A9A),
    onSecondary = Color(0xFF000000),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Color(0xFF9A9A9A),
    outline = Color(0xFF262626),
    outlineVariant = Color(0xFF1C1C1C),
    error = Color(0xFFF0A7A7),
    onError = Color(0xFF000000),
)

@Composable
fun CodyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkCodyncColors else LightCodyncColors
    CompositionLocalProvider(LocalCodyncColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = CodyncTypography,
            content = content,
        )
    }
}

object CodyncTheme {
    val colors: CodyncColors
        @Composable get() = LocalCodyncColors.current
}
