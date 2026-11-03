package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.StudioLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * سرعات التلقيم الحي — أربع سرعات بعد طلب المستخدم v0.20.0 «إضافة سرعة رابعة»:
 * الهادئة للاستهلال والنشرات المكتوبة بعناية، السريعة جدًا للإعلانات القصيرة.
 * التسميات يجب أن تطابق TeleprompterNames.ALL في طبقة البيانات (اختبار يمنع الانفصال).
 */
enum class TeleprompterSpeed(val label: String, val pxPerSecond: Float) {
    SLOW("هادئة", 30f),
    NORMAL("متوسطة", 60f),
    FAST("سريعة", 110f),
    VERY_FAST("سريعة جدًا", 170f)
}

/** ألوان شاشة القراءة الليلية — عالية التباين لعين المذيع في غرفة مضيئة. */
private object ReadingColors {
    val background = Color(0xFF12161B)
    val surface = Color(0xFF1B2129)
    val active = Color(0xFF28394B)
    val title = Color(0xFF9FC7FF)
    val body = Color(0xFFECEFF3)
    val dim = Color(0xFF8C97A5)
    val accent = Color(0xFF67B279)
}

/**
 * شاشة القراءة الاحترافية (طلب المستخدم v0.20.0 «طور واجهة شاشة القراءة الى
 * شاشة احترافية» + «طور طريقة ظهور النص» + «مؤشر القراءة»):
 * تلقيم بصري بملء النافذة على خلفية ليلية — مؤشر قراءة ثابت في منتصف الشاشة
 * (شريط تركيز + خط إرشادي + سهمان جانبيان) يحدد سطر القراءة كشاشات الاستوديو
 * الحقيقية، الفقرة الجارية بحدود مضيئة وبقية الفقرات معتمة فتسحب العين إلى
 * الموضع الحي، ساعة الحاسوب في الشريط العلوي كغرفة التحكم، والخط والسرعة
 * والمتابعة تُذكر بين الجلسات — والتمرير الآلي بأربع سرعات.
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
    var speed by remember {
        mutableStateOf(
            TeleprompterSpeed.entries.firstOrNull { it.label == savedReading.speed }
                ?: TeleprompterSpeed.NORMAL
        )
    }
    var followActive by remember { mutableStateOf(savedReading.follow ?: true) }
    var clock by remember { mutableStateOf(StudioLogic.clockText(System.currentTimeMillis())) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /** حفظ التفضيلات الحالية — يُستدعى عند اختيار صريح من المذيع فقط. */
    fun persistReading() {
        vm.saveReading(fontSp, speed.label, followActive)
    }

    // الفقرة الجارية: نفس منطق لوحات القراءة (من الجدولة أثناء الحلقة وإلا الاختيار اليدوي)
    val resolved = StudioLogic.resolveScriptSection(schedule, runActive, runIndex, script, selectedId)
    val activeId = resolved?.first?.id
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

    // التمرير الآلي: خطوات صغيرة متتالية فيبدو الزحف سلسًا لا قفزات
    LaunchedEffect(autoScroll, speed) {
        if (autoScroll) {
            while (isActive) {
                listState.scrollBy(speed.pxPerSecond / 20f)
                delay(50)
            }
        }
    }

    // متابعة الجارية: لحظة تقدّم الحلقة لفقرة تالية تنزلق الشاشة إليها تلقائيًا
    LaunchedEffect(activeIdx, followActive) {
        if (followActive && activeIdx >= 0) {
            listState.animateScrollToItem(activeIdx)
        }
    }

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
                        resolved?.second?.label ?: "فارغ",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = ReadingColors.title,
                        fontWeight = FontWeight.Bold
                    )
                }
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
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
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
                OverlayChip(
                    text = "السرعة: ${speed.label}",
                    onClick = {
                        speed = when (speed) {
                            TeleprompterSpeed.SLOW -> TeleprompterSpeed.NORMAL
                            TeleprompterSpeed.NORMAL -> TeleprompterSpeed.FAST
                            TeleprompterSpeed.FAST -> TeleprompterSpeed.VERY_FAST
                            TeleprompterSpeed.VERY_FAST -> TeleprompterSpeed.SLOW
                        }
                        persistReading()
                    }
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

            // ================= نص السكريبت كاملًا مع مؤشر القراءة =================
            if (script.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "لا يوجد سكريبت بعد — أضف فقرات النص من قسم «سكريبت المذيع».",
                        color = ReadingColors.dim,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ReadingColors.surface)
                            .padding(horizontal = 26.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        itemsIndexed(script, key = { _, s -> s.id }) { i, section ->
                            val isActive = section.id == activeId
                            // طريقة ظهور النص المطوّرة: الجارية بكامل توهجها
                            // وبحدود مضيئة، وبقية الفقرات معتمة فتسحب العين
                            // إلى الموضع الحي بلا عناء
                            val textAlpha = if (isActive) 1f else 0.42f
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isActive) ReadingColors.active else ReadingColors.surface)
                                    .then(
                                        if (isActive) Modifier.border(
                                            1.5.dp,
                                            ReadingColors.accent.copy(alpha = 0.6f),
                                            RoundedCornerShape(12.dp)
                                        ) else Modifier
                                    )
                                    .clickable { vm.selectSection(section.id) }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "${i + 1}.",
                                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                                        color = if (isActive) ReadingColors.accent else ReadingColors.dim.copy(alpha = textAlpha),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        section.title.ifEmpty { "بدون عنوان" },
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = ReadingColors.title.copy(alpha = if (isActive) 1f else 0.55f)
                                    )
                                    if (isActive) {
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            "الجارية",
                                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                            color = ReadingColors.accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    section.body.ifEmpty { "—" },
                                    fontSize = fontSp.sp,
                                    lineHeight = (fontSp * 1.6f).sp,
                                    color = ReadingColors.body.copy(alpha = textAlpha)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${ScriptStats.wordCount(section.body)} كلمة · " +
                                            formatMs(ScriptStats.secondsFor(section.body) * 1000),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = ReadingColors.dim.copy(alpha = textAlpha)
                                )
                            }
                        }
                    }

                    // ================= مؤشر القراءة (v0.20.0) =================
                    // شريط تركيز شفاف عند منتصف الشاشة + خط إرشادي رقيق +
                    // سهمان جانبيان — سطر القراءة ثابت الموضع كشاشات
                    // الاستوديو الحقيقية: العين لا تبحث عن مكانها أبدًا
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val cy = size.height / 2f
                        val bandHeight = size.height * 0.20f
                        // شريط التركيز: مساحة القراءة المقصودة
                        drawRoundRect(
                            color = ReadingColors.accent.copy(alpha = 0.055f),
                            topLeft = Offset(0f, cy - bandHeight / 2f),
                            size = Size(size.width, bandHeight),
                            cornerRadius = CornerRadius(18f, 18f)
                        )
                        // الخط الإرشادي الرقيق عبر كامل العرض
                        drawLine(
                            color = ReadingColors.accent.copy(alpha = 0.28f),
                            start = Offset(0f, cy),
                            end = Offset(size.width, cy),
                            strokeWidth = 2f
                        )
                        // سهمان جانبيان يحدّان سطر القراءة
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
                            scope.launch { listState.animateScrollToItem(activeIdx) }
                        }
                    }
                )
            }
        }
    }
}

/** زر شاشة القراءة — نمط موحّد فوق الخلفية الليلية. */
@Composable
private fun OverlayChip(
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
