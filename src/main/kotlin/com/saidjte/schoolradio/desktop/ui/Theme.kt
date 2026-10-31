package com.saidjte.schoolradio.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** ثيم داكن بطابع غرفة الإذاعة: خلفيات متدرجة العمق ولمسة سماوية. */
private val StudioDark = darkColorScheme(
    primary = Color(0xFF4FC3F7),
    onPrimary = Color(0xFF00293B),
    primaryContainer = Color(0xFF0F4C67),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF0E4A44),
    onSecondaryContainer = Color(0xFFB2DFDB),
    background = Color(0xFF0E1114),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF15191E),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF1D232B),
    onSurfaceVariant = Color(0xFFB8BEC7),
    outline = Color(0xFF3A424D),
    error = Color(0xFFFF5370),
    onError = Color(0xFF3B000A)
)

/**
 * ألوان بثّية دلالية تُستخدم عبر كل الشاشات: حالة الهواء، التسجيل، مؤشرات التشغيل،
 * ومقياس المستوى المقطعي — بدل الألوان المتفرقة في ملفات الواجهة.
 */
data class StudioColors(
    val onAir: Color,
    val onAirContent: Color,
    val recordingAccent: Color,
    val recDot: Color,
    val liveTimer: Color,
    val liveDot: Color,
    val meterGreen: Color,
    val meterAmber: Color,
    val meterRed: Color,
    val meterTrack: Color,
    val meterPeak: Color,
    val logoTop: Color,
    val logoBottom: Color
)

private val DefaultStudioColors = StudioColors(
    onAir = Color(0xFFC62828),
    onAirContent = Color.White,
    recordingAccent = Color(0xFFEF9A9A),
    recDot = Color(0xFFE53935),
    liveTimer = Color(0xFF81C784),
    liveDot = Color(0xFFFF6E6E),
    meterGreen = Color(0xFF4CAF50),
    meterAmber = Color(0xFFFFB300),
    meterRed = Color(0xFFE53935),
    meterTrack = Color(0xFF232A33),
    meterPeak = Color(0xFFF5F5F5),
    logoTop = Color(0xFF0F4C67),
    logoBottom = Color(0xFF17708F)
)

val LocalStudioColors = staticCompositionLocalOf { DefaultStudioColors }

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStudioColors provides DefaultStudioColors) {
        MaterialTheme(colorScheme = StudioDark, content = content)
    }
}
