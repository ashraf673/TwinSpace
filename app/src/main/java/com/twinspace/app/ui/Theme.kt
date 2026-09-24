package com.twinspace.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF09090B)
val Surface = Color(0xFF131316)
val Elevated = Color(0xFF1C1C21)
val Fg = Color(0xFFF4F4F5)
val Muted = Color(0xFFA1A1AA)
val Subtle = Color(0xFF71717A)
val Accent = Color(0xFFC8CCD4)
val AccentFg = Color(0xFF09090B)
val Danger = Color(0xFFC46B6B)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentFg,
    background = Bg,
    onBackground = Fg,
    surface = Surface,
    onSurface = Fg,
    surfaceVariant = Elevated,
    onSurfaceVariant = Muted,
    error = Danger,
    onError = Fg,
)

@Composable
fun TwinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
