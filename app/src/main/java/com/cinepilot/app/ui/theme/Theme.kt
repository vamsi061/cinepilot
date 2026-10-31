package com.cinepilot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CinePilotColors = darkColorScheme(
    primary = Color(0xFF00B4D8),
    onPrimary = Color(0xFF001019),
    primaryContainer = Color(0xFF003544),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF2A1700),
    background = Color(0xFF0A1128),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF121A33),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1C2542),
    onSurfaceVariant = Color(0xFFB8C0D9),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0000)
)

@Composable
fun CinePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CinePilotColors,
        content = content
    )
}