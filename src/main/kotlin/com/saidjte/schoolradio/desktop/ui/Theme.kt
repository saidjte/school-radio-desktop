package com.saidjte.schoolradio.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/**
 * ثيم داكن بطابع غرفة الإذاعة (v0.22.0 «طوّر GUI التطبيق»): خلفيات أعمق
 * بثلاث طبقات واضحة التمايز، لمسة سماوية كالهواء، وألوان بطاقات بحوافّ
 * مرسومة بدل الاعتماد على الفارق اللوني وحده — والقيم مأخوذة من GuiTokens
 * المختبر بـ JUnit فلا تنفصل الواجهة عن مصدرها.
 */
private val StudioDark = darkColorScheme(
    primary = Color(0xFF53C8F8),
    onPrimary = Color(0xFF00293B),
    primaryContainer = Color(0xFF0F4C67),
    onPrimaryContainer = Color(0xFFBDE9FF),
    secondary = Color(0xFF80CBC4),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF0E4A44),
    onSecondaryContainer = Color(0xFFB2DFDB),
    tertiary = Color(0xFFFFC66D),
    onTertiary = Color(0xFF3E2B00),
    background = Color(GuiTokens.BG),
    onBackground = Color(0xFFE4E7EB),
    surface = Color(GuiTokens.SURFACE),
    onSurface = Color(0xFFE4E7EB),
    surfaceVariant = Color(GuiTokens.SURFACE_VARIANT),
    onSurfaceVariant = Color(0xFFB9C0CB),
    outline = Color(0xFF3C4757),
    outlineVariant = Color(GuiTokens.CARD_BORDER),
    error = Color(0xFFFF5370),
    onError = Color(0xFF3B000A)
)

/**
 * ألوان بثّية دلالية تُستخدم عبر كل الشاشات: حالة الهواء، التسجيل، مؤشرات التشغيل،
 * ومقياس المستوى المقطعي — بدل الألوان المتفرقة في ملفات الواجهة.
 * أُضيفت v0.22.0: حوافّ البطاقات ولون النجاح والتحذير كتوكنز رسمية.
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
    val logoBottom: Color,
    val cardBorder: Color = Color(GuiTokens.CARD_BORDER),
    val success: Color = Color(0xFF43D17C),
    val warn: Color = Color(0xFFFFB300)
)

private val DefaultStudioColors = StudioColors(
    onAir = Color(GuiTokens.ON_AIR_BG),
    onAirContent = Color(GuiTokens.ON_AIR_CONTENT),
    recordingAccent = Color(0xFFEF9A9A),
    recDot = Color(0xFFE53935),
    liveTimer = Color(0xFF81C784),
    liveDot = Color(0xFFFF6E6E),
    meterGreen = Color(GuiTokens.zoneGreen),
    meterAmber = Color(GuiTokens.zoneAmber),
    meterRed = Color(GuiTokens.zoneRed),
    meterTrack = Color(0xFF232A33),
    meterPeak = Color(0xFFF5F5F5),
    logoTop = Color(0xFF0F4C67),
    logoBottom = Color(0xFF17708F)
)

val LocalStudioColors = staticCompositionLocalOf { DefaultStudioColors }

/**
 * الخط العربي الموحّد المدمج مع التطبيق (v0.22.0): Noto Sans Arabic بأوزان
 * أربعة مُحمَّلة من موارد الجرة فتبدو الواجهة واحدة على ويندوز ولينكس وماك
 * بلا اعتماد على خطوط النظام المتفاوتة. الأحرف اللاتينية والأرقام الغائبة
 * تعود تلقائيًا لخط النظام عبر سلسلة الاحتياط.
 */
@OptIn(ExperimentalTextApi::class)
val AppFontFamily: FontFamily = FontFamily(
    androidx.compose.ui.text.platform.Font("fonts/NotoSansArabic-Regular.ttf", FontWeight.Normal),
    androidx.compose.ui.text.platform.Font("fonts/NotoSansArabic-Medium.ttf", FontWeight.Medium),
    androidx.compose.ui.text.platform.Font("fonts/NotoSansArabic-SemiBold.ttf", FontWeight.SemiBold),
    androidx.compose.ui.text.platform.Font("fonts/NotoSansArabic-Bold.ttf", FontWeight.Bold)
)

/** مطابقة كل مستويات الخط المادي على العائلة المدمجة (15 مستوى). */
private fun typographyWithFamily(base: Typography): Typography {
    fun TextStyle.withFamily() = copy(fontFamily = AppFontFamily)
    return base.copy(
        displayLarge = base.displayLarge.withFamily(),
        displayMedium = base.displayMedium.withFamily(),
        displaySmall = base.displaySmall.withFamily(),
        headlineLarge = base.headlineLarge.withFamily(),
        headlineMedium = base.headlineMedium.withFamily(),
        headlineSmall = base.headlineSmall.withFamily(),
        titleLarge = base.titleLarge.withFamily(),
        titleMedium = base.titleMedium.withFamily(),
        titleSmall = base.titleSmall.withFamily(),
        bodyLarge = base.bodyLarge.withFamily(),
        bodyMedium = base.bodyMedium.withFamily(),
        bodySmall = base.bodySmall.withFamily(),
        labelLarge = base.labelLarge.withFamily(),
        labelMedium = base.labelMedium.withFamily(),
        labelSmall = base.labelSmall.withFamily()
    )
}

/**
 * سلّم طباعي مضبوط لواجهة بثّية: عناوين أكثر ثقلًا، متون مقروءة،
 * وشِعارات أصغر قليلًا لتناسب كثافة شاشة الاستوديو.
 */
private fun studioTypography(): Typography {
    val b = Typography()
    return typographyWithFamily(
        b.copy(
            headlineSmall = b.headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            titleLarge = b.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            titleMedium = b.titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
            titleSmall = b.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = b.bodyLarge.copy(fontSize = 16.sp, lineHeight = 26.sp),
            bodyMedium = b.bodyMedium.copy(fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = b.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = b.labelLarge.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = b.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = b.labelSmall.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
        )
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStudioColors provides DefaultStudioColors) {
        MaterialTheme(colorScheme = StudioDark, typography = studioTypography(), content = content)
    }
}

