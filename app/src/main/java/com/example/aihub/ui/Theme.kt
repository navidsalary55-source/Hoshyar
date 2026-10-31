package com.example.aihub.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.aihub.core.Store

val Brand = listOf(Color(0xFF7C6CFF), Color(0xFF3FA2F7))
fun grad(c: List<Color>) = Brush.linearGradient(c)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9A8CFF), onPrimary = Color.White,
    background = Color(0xFF0E0F1A), onBackground = Color(0xFFEDEDF7),
    surface = Color(0xFF171829), onSurface = Color(0xFFEDEDF7),
    surfaceVariant = Color(0xFF23253C), onSurfaceVariant = Color(0xFFE2E2F2),
    errorContainer = Color(0xFF4A1F2B), onErrorContainer = Color(0xFFFFD9DF),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6C5CE7), onPrimary = Color.White,
    background = Color(0xFFF6F5FF), onBackground = Color(0xFF1A1B2E),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1A1B2E),
    surfaceVariant = Color(0xFFE9E7FB), onSurfaceVariant = Color(0xFF1A1B2E),
    errorContainer = Color(0xFFFFE1E6), onErrorContainer = Color(0xFF7A1A2B),
)

@Composable
fun AIHubTheme(content: @Composable () -> Unit) {
    val dark = when (Store.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
