package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.StudioLogic
import com.saidjte.schoolradio.desktop.data.TeleprompterSpeedLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * السرعات الجاهزة الخمس — طلب المستخدم v0.26.0 «أضف سرعة بطئ جدا» أضاف
 * «بطيئة جدًا» للاستهلال البطيء (قبل الهادئة)، والتحكم الحر المتصل عبر
 * المنزلق يديره TeleprompterSpeedLogic في طبقة البيانات — التسميات يجب
 * أن تطابق TeleprompterNames.ALL (اختبار يمنع الانفصال).
 */
enum class TeleprompterSpeed(val label: String, val pxPerSecond: Float) {
    VERY_SLOW("بطيئة جدًا", 12f),
    SLOW("هادئة", 30f),
    NORMAL("متوسطة", 60f),
    FAST("سريعة", 110f),
    VERY_FAST("سريعة جدًا", 170f)
}

/** ألوان شاشة القراءة الليلية — عالية التباين لعين المذيع في غرفة مضيئة.
 *  internal لتشاركها نافذة القراءة على الشاشة الثانية (v0.35.0) فتبقى الهوية واحدة. */
internal object ReadingColors {
    val background = Color(0xFF12161B)
    val surface = Color(0xFF1B2129)
    val active = Color(0xFF28394B)
    val title = Color(0xFF9FC7FF)
    val body = Color(0xFFECEFF3)
    val dim = Color(0xFF8C97A5)
    val accent = Color(0xFF67B279)
}

/**
 * شاشة القراءة الاحترافية — طلب المستخدم v0.21.0:
 * «شاشة القراءة يجب أن تضم سكريبت واحد لتجنب ارباك المذيع» — الشاشة تعرض
 * فقرة واحدة فقط (الجارية أثناء الحلقة، وإلا اختيار المذيع) فلا تتشتت العين
 * بين فقرات كثيرة،
 * «النص يجب ان يبدأ من الأسفل و ينتهي في الأعلى» — النص يبدأ تحت الحافة
 * السفلية ويزحف حتى تخرج آخر سطر من الحافة العليا، فتمرّ كل سطور الفقرة
 * كاملًا عبر مؤشر القراءة الثابت في منتصف الشاشة (حشوتان بارتفاع الشاشة
 * قبل النص وبعده تضمنان ذلك رياضيًا مهما كان طول الفقرة).
 * ومؤشر القراءة (v0.20.0) ثابت: شريط تركيز + خط إرشادي + سهمان جانبيان،
 * والسرعات أربع، والخط والسرعة والمتابعة تُذكر بين الجلسات، وساعة الحاسوب
 * في الشريط العلوي كغرف التحكم.
 * تُفتح من أي لوحة قراءة (الاستوديو المدمج، البث المباشر، سكريبت المذيع).
 */
@Composable
fun ReadingOverlay(vm: AppViewModel, onDismiss: () -> Unit) {
    val script by vm.script.collectAsState()
    val schedule by vm.schedule.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val selectedId by vm.selectedSectionId.collectAsState()

    // تفضيلات القراءة تُسترجع من الجلسة السابقة وتُحفظ عند كل تغيير صريح
    val savedReading = remember { vm.savedReading() }
    var fontSp by remember { mutableStateOf(savedReading.fontSp ?: 40f) }
    var autoScroll by remember { mutableStateOf(false) }
    // التحكم الحر في السرعة (v0.26.0): قيمة متصلة بالبكسل/ثانية تُسترجع من
    // المحفوظ (اسم جاهز أو رقم) ويتغير بها الزحف فورًا — والجاهزات الخمس
    // أزرار سريعة فوق المنزلق
    var speedPx by remember { mutableStateOf(TeleprompterSpeedLogic.resolve(savedReading.speed)) }
    var followActive by remember { mutableStateOf(savedReading.follow ?: true) }
    var clock by remember { mutableStateOf(StudioLogic.clockText(System.currentTimeMillis())) }

    /** حفظ التفضيلات الحالية — يُستدعى عند اختيار صريح من المذيع فقط. */
    fun persistReading() {
        vm.saveReading(fontSp, TeleprompterSpeedLogic.encode(speedPx), followActive)
    }

    // حفظ السرعة أثناء سحب المنزلق مُهذّب: يُكتب الإعداد بعد سكون نصف ثانية
    // لا مع كل إطار سحب — فيبقى التغيير الحر محفوظًا بين الجلسات دون إثقال القرص
    LaunchedEffect(speedPx) {
        delay(600)
        persistReading()
    }

    // الفقرة المعروضة — فقرة واحدة: الجارية أثناء الحلقة مع المتابعة،
    // واختيار المذيع اليدوي إن أمسك القيادة (وقف المتابعة)
    val shown = StudioLogic.resolveReadingSection(
        schedule, runActive, runIndex, script, selectedId, followActive
    )
    val activeId = shown?.first?.id
    val activeIdx = script.indexOfFirst { it.id == activeId }
    val progress = if (script.isEmpty()) 0f
    else ((activeIdx + 1).coerceAtLeast(1)).toFloat() / script.size

    // ساعة الحاسوب — كل ثانية (شاشات الاستوديو تعرض الساعة للمذيع)
    LaunchedEffect(Unit) {
        while (isActive) {
            clock = StudioLogic.clockText(System.currentTimeMillis())
            delay(1000)
        }
    }

    // زحف النص ومؤشر القراءة وإعادة التموضع عند تغيّر الفقرة — كلها في
    // [ReadingCrawlArea] المشتركة مع نافذة القراءة على الشاشة الثانية (v0.35.0)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ReadingColors.background)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ================= الشريط العلوي ١: العنوان والقسم والساعة =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "شاشة القراءة",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ReadingColors.body
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(ReadingColors.surface)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        shown?.second?.label ?: "فارغ",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = ReadingColors.title,
                        fontWeight = FontWeight.Bold
                    )
                }
                // عنوان الفقرة المعروضة — المذيع يعرف أي سكريبت بين يديه دائمًا
                Text(
                    shown?.first?.title?.ifEmpty { "بدون عنوان" } ?: "—",
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    color = ReadingColors.title,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.weight(1f))
                // ساعة الحاسوب — للمذيع الذي لا يريد إزعاج نظره عن النص
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ReadingColors.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        clock,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge.numeric(),
                        color = ReadingColors.title,
                        fontWeight = FontWeight.Bold
                    )
                }
                OverlayChip(text = "إغلاق", onClick = onDismiss)
            }

            // ================= الشريط العلوي ٢: التحكم الكامل =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverlayChip(text = "أ−", onClick = { fontSp = (fontSp - 4f).coerceAtLeast(16f); persistReading() })
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(ReadingColors.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        "${fontSp.toInt()}pt",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        color = ReadingColors.dim,
                        fontWeight = FontWeight.Bold
                    )
                }
                OverlayChip(text = "أ+", onClick = { fontSp = (fontSp + 4f).coerceAtMost(96f); persistReading() })
                Spacer(Modifier.width(6.dp))
                OverlayChip(
                    text = if (autoScroll) "إيقاف التلقيم" else "تشغيل التلقيم",
                    filled = autoScroll,
                    onClick = { autoScroll = !autoScroll }
                )
                // التحكم الحر في السرعة (v0.26.0): اسم القيمة + منزلق متصل + القراءة الرقمية
                Text(
                    "السرعة: ${TeleprompterSpeedLogic.displayName(speedPx)}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = ReadingColors.dim,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = speedPx,
                    onValueChange = { speedPx = TeleprompterSpeedLogic.clamp(it) },
                    valueRange = TeleprompterSpeedLogic.MIN_PX..TeleprompterSpeedLogic.MAX_PX,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${speedPx.toInt()} بكسل/ث",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.numeric(),
                    color = ReadingColors.title,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Switch(
                        checked = followActive,
                        onCheckedChange = {
                            followActive = it
                            persistReading()
                        }
                    )
                    Text(
                        "متابعة الجارية",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = ReadingColors.body
                    )
                }
            }

            // السرعات الجاهزة الخمس — طلب «أضف سرعة بطئ جدا»: بطيئة جدًا أولًا،
            // والزر الجاهز يثبت القيمة المتصلة ويحفظ فورًا (المفعّلة مضاءة)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "سرعات جاهزة:",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = ReadingColors.dim,
                    fontWeight = FontWeight.Bold
                )
                TeleprompterSpeed.entries.forEach { preset ->
                    OverlayChip(
                        text = preset.label,
                        filled = speedPx == preset.pxPerSecond,
                        onClick = {
                            speedPx = preset.pxPerSecond
                            persistReading()
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            // شريط تقدّم الحلقة + عدّاد الفقرات
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = ReadingColors.accent,
                    trackColor = ReadingColors.surface
                )
                Text(
                    if (script.isEmpty()) "لا فقرات"
                    else "الفقرة ${(activeIdx + 1).coerceAtLeast(1)} من ${script.size}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = ReadingColors.dim,
                    fontWeight = FontWeight.Bold
                )
            }

            // ================= الفقرة الواحدة تزحف من الأسفل إلى الأعلى =================
            if (script.isEmpty() || shown == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "لا يوجد سكريبت بعد — أضف فقرات النص من قسم «سكريبت المذيع».",
                        color = ReadingColors.dim,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                ReadingCrawlArea(
                    title = shown.first.title.ifEmpty { "بدون عنوان" },
                    body = shown.first.body.ifEmpty { "—" },
                    metaLine = "${ScriptStats.wordCount(shown.first.body)} كلمة · " +
                            formatMs(ScriptStats.secondsFor(shown.first.body) * 1000),
                    fontSp = fontSp,
                    speedPx = speedPx,
                    autoScroll = autoScroll,
                    onStopAutoScroll = { autoScroll = false },
                    resetKey = activeId,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }

            // ================= الشريط السفلي: التنقّل =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OverlayChip(
                    text = "السابقة",
                    enabled = script.isNotEmpty(),
                    onClick = { vm.selectAdjacentSection(-1) }
                )
                OverlayChip(
                    text = "التالية",
                    enabled = script.isNotEmpty(),
                    onClick = { vm.selectAdjacentSection(+1) }
                )
                Spacer(Modifier.weight(1f))
                OverlayChip(
                    text = "إلى الجارية الآن",
                    enabled = activeIdx >= 0,
                    onClick = {
                        if (activeIdx >= 0) {
                            vm.selectSection(activeId!!)
                        }
                    }
                )
            }
        }
    }
}

/** زر شاشة القراءة — نمط موحّد فوق الخلفية الليلية.
 *  internal لتشاركه نافذة القراءة على الشاشة الثانية (v0.35.0). */
@Composable
internal fun OverlayChip(
    text: String,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) ReadingColors.accent else ReadingColors.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = if (filled) Color(0xFF10141A) else ReadingColors.body,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * مساحة الزحف التلقيمية المشتركة (v0.35.0): فقرة واحدة يبدأ نصها من الحافة
 * السفلية ويمرّ كل سطورها عبر مؤشر القراءة الثابت في منتصف الشاشة — نفس
 * الرياضيات والهوية الليلية لشاشة القراءة الأصلية (v0.20/v0.21)، تعمل الآن
 * في موضعين: الحوار داخل الاستوديو، ونافذة القراءة على الشاشة الثانية.
 * الزحف خطوات صغيرة متتالية (50ms) وعند نهاية النص يتوقف وحده، وتغيّر
 * الفقرة ([resetKey]) يعيده إلى موضعه الأول.
 */
@Composable
internal fun ReadingCrawlArea(
    title: String,
    body: String,
    metaLine: String,
    fontSp: Float,
    speedPx: Float,
    autoScroll: Boolean,
    onStopAutoScroll: () -> Unit,
    resetKey: Any?,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // ارتفاع مساحة الزحف بالبكسل — الحشوتان قبل النص وبعده (بارتفاع المساحة
    // ناقص سطر واحد) تجعلان أول سطر مرئيًا عند الحافة السفلية عند البداية
    // وآخر سطر مرئيًا عند الحافة العليا عند النهاية
    var areaPx by remember { mutableIntStateOf(0) }
    val areaDp = with(density) { areaPx.toDp() }
    val lineDp = with(density) { (fontSp * 1.6f).sp.toDp() }
    val edgePad = (areaDp - lineDp).coerceAtLeast(0.dp)

    // التمرير الآلي: خطوات صغيرة متتالية فيبدو الزحف سلسًا لا قفزات —
    // وعند بلوغ نهاية النص (آخر سطر خرج من الأعلى) يتوقف التلقيم وحده
    LaunchedEffect(autoScroll, speedPx) {
        if (autoScroll) {
            // جلسة انتهت سابقًا والنص عند النهاية؟ نعيده لأسفل الشاشة ليبدأ من جديد
            if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue) {
                scrollState.scrollTo(0)
            }
            while (isActive) {
                scrollState.scrollBy(speedPx / 20f)
                if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue) {
                    onStopAutoScroll()
                    break
                }
                delay(50)
            }
        }
    }

    // تغيّر الفقرة المعروضة (متابعة الحلقة أو اختيار يدوي) يعيد النص إلى
    // موضعه الأول: تحت الحافة السفلية — فتُقرأ الفقرة من بدايتها عبر المؤشر
    LaunchedEffect(resetKey) {
        scrollState.scrollTo(0)
    }

    Box(
        modifier = modifier.onSizeChanged { areaPx = it.height }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // حشوة علوية: عند البدء يظهر أول سطر عند الحافة السفلية مباشرة
            Spacer(Modifier.height(edgePad))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ReadingColors.surface)
                    .border(1.5.dp, ReadingColors.accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 30.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    title,
                    fontSize = (fontSp * 0.62f).sp,
                    lineHeight = (fontSp * 0.95f).sp,
                    fontWeight = FontWeight.Bold,
                    color = ReadingColors.title
                )
                Text(
                    body,
                    fontSize = fontSp.sp,
                    lineHeight = (fontSp * 1.6f).sp,
                    color = ReadingColors.body
                )
                Text(
                    metaLine,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = ReadingColors.dim
                )
            }
            // حشوة سفلية: عند نهاية الزحف يظهر آخر سطر عند الحافة العليا تمامًا
            Spacer(Modifier.height(edgePad))
        }

        // مؤشر القراءة: شريط تركيز + خط إرشادي + سهمان جانبيان — سطر القراءة
        // ثابت الموضع كشاشات الاستوديو الحقيقية: العين لا تبحث عن مكانها أبدًا
        Canvas(modifier = Modifier.matchParentSize()) {
            val cy = size.height / 2f
            val bandHeight = size.height * 0.20f
            drawRoundRect(
                color = ReadingColors.accent.copy(alpha = 0.055f),
                topLeft = Offset(0f, cy - bandHeight / 2f),
                size = Size(size.width, bandHeight),
                cornerRadius = CornerRadius(18f, 18f)
            )
            drawLine(
                color = ReadingColors.accent.copy(alpha = 0.28f),
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 2f
            )
            fun focusArrow(edgeX: Float, dir: Float): Path = Path().apply {
                moveTo(edgeX, cy - 18f)
                lineTo(edgeX + dir * 26f, cy)
                lineTo(edgeX, cy + 18f)
                close()
            }
            val arrowColor = ReadingColors.accent.copy(alpha = 0.85f)
            drawPath(focusArrow(0f, 1f), arrowColor)
            drawPath(focusArrow(size.width, -1f), arrowColor)
        }
    }
}
