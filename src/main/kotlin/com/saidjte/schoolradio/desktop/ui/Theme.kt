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
    // عائلة حمراء واحدة لحالتي التسجيل والحياة (v0.46.0): النقطة بالأحمر الصلب
    // والنص بتدرجه الفاتح المضبوط بقياس WCAG — بدل ثلاثة أحمر متفرقة
    recordingAccent = Color(GuiTokens.RECORDING_TEXT),
    recDot = Color(GuiTokens.zoneRed),
    liveTimer = Color(0xFF81C784),
    liveDot = Color(GuiTokens.zoneRed),
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
 * نظام الطباعة الرباعي المدمج مع التطبيق (v0.24.0 «استخدم للخطوط: Cairo/Changa/Orbitron/Spicy Sale»):
 * أربع عائلات بأدوار منفصلة تُحمَّل من موارد الجرة فتبدو الواجهة واحدة على ويندوز ولينكس وماك
 * بلا اعتماد على خطوط النظام المتفاوتة:
 * - **Cairo** المتون والنصوص (تغطية عربية+لاتينية كاملة، أربعة أوزان) — الخط الأساس.
 * - **Changa** العناوين (display/headline/title) — حضور عربي قوي بأربعة أوزان.
 * - **Orbitron** الأرقام والساعات والأزمنة (لاتيني هندسي) — عبر TextStyle.numeric()
 *   على النصوص الرقمية الصرفة (الساعة، مؤقت البث، أزمنة المشغل، النسب المئوية).
 * - **Spicy Sale** الهوية اللاتينية المرحة (وزن واحد) — شعار «SCHOOL RADIO» وشارات الإصدار.
 * الأوزان مُجسَّدة ثابتة من الخطوط المتغيرة الرسمية فلا تختلف بين الجلسات.
 */
@OptIn(ExperimentalTextApi::class)
val AppFontFamily: FontFamily = FontFamily(
    androidx.compose.ui.text.platform.Font("fonts/Cairo-Regular.ttf", FontWeight.Normal),
    androidx.compose.ui.text.platform.Font("fonts/Cairo-Medium.ttf", FontWeight.Medium),
    androidx.compose.ui.text.platform.Font("fonts/Cairo-SemiBold.ttf", FontWeight.SemiBold),
    androidx.compose.ui.text.platform.Font("fonts/Cairo-Bold.ttf", FontWeight.Bold)
)

/** عائلة العناوين: Changa — لمستويات display/headline/title. */
@OptIn(ExperimentalTextApi::class)
val HeadingFontFamily: FontFamily = FontFamily(
    androidx.compose.ui.text.platform.Font("fonts/Changa-Medium.ttf", FontWeight.Medium),
    androidx.compose.ui.text.platform.Font("fonts/Changa-SemiBold.ttf", FontWeight.SemiBold),
    androidx.compose.ui.text.platform.Font("fonts/Changa-Bold.ttf", FontWeight.Bold),
    androidx.compose.ui.text.platform.Font("fonts/Changa-ExtraBold.ttf", FontWeight.ExtraBold)
)

/** عائلة الأرقام: Orbitron — للساعات والأزمنة والنسب عبر TextStyle.numeric(). */
@OptIn(ExperimentalTextApi::class)
val NumericFontFamily: FontFamily = FontFamily(
    androidx.compose.ui.text.platform.Font("fonts/Orbitron-Medium.ttf", FontWeight.Medium),
    androidx.compose.ui.text.platform.Font("fonts/Orbitron-SemiBold.ttf", FontWeight.SemiBold),
    androidx.compose.ui.text.platform.Font("fonts/Orbitron-Bold.ttf", FontWeight.Bold),
    androidx.compose.ui.text.platform.Font("fonts/Orbitron-ExtraBold.ttf", FontWeight.ExtraBold)
)

/** عائلة الهوية اللاتينية: Spicy Sale — للشعار اللاتيني وشارات الإصدار. */
@OptIn(ExperimentalTextApi::class)
val BrandFontFamily: FontFamily = FontFamily(
    androidx.compose.ui.text.platform.Font("fonts/SpicySale-Regular.otf", FontWeight.Normal)
)

/**
 * إسناد العائلات لمستويات الخط الخمسة عشر: العناوين (display/headline/title) لـ Changa
 * والمتون (body/label) لـ Cairo. دالة نقية بعائلات ممرَّرة كي تُختبر بمنادِلين حارسين
 * بلا فتح أي مورد رسومي (SelfTest وJUnit).
 */
internal fun typographyWithFamilies(base: Typography, body: FontFamily, heading: FontFamily): Typography {
    fun TextStyle.with(f: FontFamily) = copy(fontFamily = f)
    return base.copy(
        displayLarge = base.displayLarge.with(heading),
        displayMedium = base.displayMedium.with(heading),
        displaySmall = base.displaySmall.with(heading),
        headlineLarge = base.headlineLarge.with(heading),
        headlineMedium = base.headlineMedium.with(heading),
        headlineSmall = base.headlineSmall.with(heading),
        titleLarge = base.titleLarge.with(heading),
        titleMedium = base.titleMedium.with(heading),
        titleSmall = base.titleSmall.with(heading),
        bodyLarge = base.bodyLarge.with(body),
        bodyMedium = base.bodyMedium.with(body),
        bodySmall = base.bodySmall.with(body),
        labelLarge = base.labelLarge.with(body),
        labelMedium = base.labelMedium.with(body),
        labelSmall = base.labelSmall.with(body)
    )
}
/** مطابقة كل مستويات الخط المادي على العائلة المدمجة (15 مستوى). */
private fun typographyWithFamily(base: Typography): Typography =
    typographyWithFamilies(base, AppFontFamily, HeadingFontFamily)

/**
 * نمط الأرقام الموحّد (v0.24.0): يعيد النمط بعائلة Orbitron — للنصوص الرقمية
 * الصرفة (ساعة HH:mm:ss، مؤقت البث، أزمنة المشغل، النسب المئوية) حيث تُبرز
 * المحارف الهندسية الواسعة دقة القراءة أثناء البث.
 */
fun TextStyle.numeric(): TextStyle = copy(fontFamily = NumericFontFamily)

/**
 * نمط الهوية اللاتينية الموحّد (v0.24.0): Spicy Sale للشعار اللاتيني وشارات
 * الإصدار — شخصية مرحة مكوَّرة تناسب إذاعة مدرسية.
 */
fun TextStyle.brand(): TextStyle = copy(fontFamily = BrandFontFamily)

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

