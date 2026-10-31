package com.saidjte.schoolradio.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** ثيم داكن بطابع غرفة الإذاعة: خلفية داكنة ولمسة سماوية. */
private val StudioDark = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00293B),
    primaryContainer = Color(0xFF0F4C67),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF23272D),
    onSurfaceVariant = Color(0xFFB8BEC7),
    error = Color(0xFFFF5370),
    onError = Color(0xFF3B000A)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = StudioDark, content = content)
}
