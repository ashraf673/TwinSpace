package com.twinspace.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Bg = Color(0xFF09090B)
private val Surface = Color(0xFF131316)
private val Elevated = Color(0xFF1C1C21)
private val Fg = Color(0xFFF4F4F5)
private val Accent = Color(0xFFC8CCD4)

@Composable
fun TwinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Bg,
            background = Bg,
            onBackground = Fg,
            surface = Surface,
            onSurface = Fg,
            surfaceVariant = Elevated,
            onSurfaceVariant = Color(0xFFA1A1AA),
            error = Color(0xFFC46B6B)
        ),
        content = content
    )
}
