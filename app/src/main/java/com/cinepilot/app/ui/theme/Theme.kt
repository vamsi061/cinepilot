package com.cinepilot.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CinePilotColors = lightColorScheme(
    primary = Color(0xFF007C91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F1F8),
    onPrimaryContainer = Color(0xFF002B32),
    secondary = Color(0xFF356A73),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC5F0F5),
    onSecondaryContainer = Color(0xFF07363D),
    background = Color(0xFFF8FBFC),
    onBackground = Color(0xFF172124),
    surface = Color.White,
    onSurface = Color(0xFF172124),
    surfaceVariant = Color(0xFFE7F2F4),
    onSurfaceVariant = Color(0xFF405054),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

@Composable
fun CinePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CinePilotColors,
        content = content
    )
}