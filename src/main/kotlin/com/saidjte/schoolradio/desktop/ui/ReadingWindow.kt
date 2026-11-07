package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.saidjte.schoolradio.desktop.APP_VERSION
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.data.ScreenRect
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.StudioLogic
import com.saidjte.schoolradio.desktop.data.TeleprompterSpeedLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * نافذة القراءة على الشاشة الثانية (v0.35.0 — طلب المستخدم «أضف إمكانية عرض
 * شاشة القراءة في شاشة ثانية على طريقة عرض شاشة على الهواء»): تلقيم مسرحي
 * بنفس آلية نافذة «على الهواء» (v0.32.0) — على حاسوب بشاشتين تُكشف الشاشة
 * الأولى غير الافتراضية وتُملأ بلا إطارات، وعلى شاشة واحدة نافذة سحب عادية
 * تلميحُ اسحبها. النافذة مستقلة غير ماسكة فالاستوديو يظل عاملاً بكامل أدواته.
 *
 * ما يراه المذيع: الفقرة الجارية (أو اختياره) تزحف من الأسفل إلى الأعلى عبر
 * مؤشر القراءة الثابت بنفس رياضيات شاشة القراءة الأصلية — أ+/أ− وتلقيم
 * وسرعة ومتابعة الجارية، وتفضيلاته نفسها تُشارك مع شاشة القراءة الداخلية.
 */
@Composable
fun ReadingWindow(vm: AppViewModel, onClose: () -> Unit) {
    val windowIcon = remember {
        Thread.currentThread().contextClassLoader
            .getResourceAsStream("icon.png")
            ?.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()) }
            ?.toComposeImageBitmap()
    }
    val secondary = remember { detectSecondaryScreen() }
    Window(
        onCloseRequest = onClose,
        title = "شاشة القراءة — التلقيم على الشاشة الثانية · إذاعة مدرسية احترافي v$APP_VERSION",
        state = rememberWindowState(width = 1024.dp, height = 640.dp),
        undecorated = secondary != null,
        icon = windowIcon?.let { BitmapPainter(it) }
    ) {
        LaunchedEffect(secondary) {
            val s = secondary
            if (s != null) {
                window.setLocation(s.x, s.y)
                window.setSize(s.width, s.height)
                window.extendedState = java.awt.Frame.MAXIMIZED_BOTH
            }
        }
        CompositionLocalRtl {
            ReadingWindowContent(vm = vm, onClose = onClose, onSecondary = secondary != null)
        }
    }
}

/** توفير اتجاه RTL داخل نافذة القراءة — نفس عرف نوافذ التطبيق. */
@Composable
private fun CompositionLocalRtl(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) { content() }
}

@Composable
private fun ReadingWindowContent(vm: AppViewModel, onClose: () -> Unit, onSecondary: Boolean) {
    val script by vm.script.collectAsState()
    val schedule by vm.schedule.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val selectedId by vm.selectedSectionId.collectAsState()
    val school by vm.schoolName.collectAsState()

    // نفس تفضيلات القراءة المشتركة — أ+/أ− هنا يظهر في شاشة القراءة الداخلية والعكس
    val savedReading = remember { vm.savedReading() }
    var fontSp by remember { mutableStateOf(savedReading.fontSp ?: 40f) }
    var autoScroll by remember { mutableStateOf(false) }
    var speedPx by remember { mutableStateOf(TeleprompterSpeedLogic.resolve(savedReading.speed)) }
    var followActive by remember { mutableStateOf(savedReading.follow ?: true) }
    var clock by remember { mutableStateOf(StudioLogic.clockText(System.currentTimeMillis())) }

    fun persistReading() {
        vm.saveReading(fontSp, TeleprompterSpeedLogic.encode(speedPx), followActive)
    }

    LaunchedEffect(speedPx) {
        delay(600)
        persistReading()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            clock = StudioLogic.clockText(System.currentTimeMillis())
            delay(1000)
        }
    }

    // الفقرة المعروضة — نفس القرار النقي للشاشة الداخلية: الجارية أثناء
    // الحلقة مع المتابعة، واختيار المذيع إن أمسك القيادة
    val shown = StudioLogic.resolveReadingSection(
        schedule, runActive, runIndex, script, selectedId, followActive
    )
    val activeId = shown?.first?.id
    val activeIdx = script.indexOfFirst { it.id == activeId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ReadingColors.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // الرأس: الهوية + مصدر النص + الساعة
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LogoMark(size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "شاشة القراءة — الشاشة الثانية",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ReadingColors.body
                )
                Text(
                    school?.let { "$it — تلقيم المذيع لحظة بلحظة" }
                        ?: "تلقيم المذيع لحظة بلحظة — يتبع الفقرة الجارية في الحلقة",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReadingColors.dim
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(ReadingColors.surface)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    shown?.second?.label ?: "فارغ",
                    style = MaterialTheme.typography.labelSmall,
                    color = ReadingColors.title,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ReadingColors.surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    clock,
                    style = MaterialTheme.typography.labelLarge.numeric(),
                    color = ReadingColors.title,
                    fontWeight = FontWeight.Bold
                )
            }
            OverlayChip(text = "إنهاء العرض", onClick = onClose)
        }

        // شريط التحكم: الخط والتلقيم والسرعة والمتابعة
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
                    style = MaterialTheme.typography.labelMedium,
                    color = ReadingColors.dim,
                    fontWeight = FontWeight.Bold
                )
            }
            OverlayChip(text = "أ+", onClick = { fontSp = (fontSp + 4f).coerceAtMost(96f); persistReading() })
            OverlayChip(
                text = if (autoScroll) "إيقاف التلقيم" else "تشغيل التلقيم",
                filled = autoScroll,
                onClick = { autoScroll = !autoScroll }
            )
            Text(
                "السرعة: ${TeleprompterSpeedLogic.displayName(speedPx)}",
                style = MaterialTheme.typography.bodySmall,
                color = ReadingColors.dim,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = speedPx,
                onValueChange = { speedPx = TeleprompterSpeedLogic.clamp(it) },
                valueRange = TeleprompterSpeedLogic.MIN_PX..TeleprompterSpeedLogic.MAX_PX,
                modifier = Modifier.weight(1f)
            )
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
                    style = MaterialTheme.typography.bodySmall,
                    color = ReadingColors.body
                )
            }
        }

        // تقدم الحلقة وعدّاد الفقرات
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (script.isEmpty()) "لا فقرات"
                else "الفقرة ${(activeIdx + 1).coerceAtLeast(1)} من ${script.size}",
                style = MaterialTheme.typography.labelMedium,
                color = ReadingColors.dim,
                fontWeight = FontWeight.Bold
            )
            Text(
                shown?.first?.title?.ifEmpty { "بدون عنوان" } ?: "—",
                style = MaterialTheme.typography.labelLarge,
                color = ReadingColors.title,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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
        }

        // الفقرة تزحف عبر مؤشر القراءة — نفس مساحة الزحف المشتركة
        if (script.isEmpty() || shown == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "لا يوجد سكريبت بعد — أضف فقرات النص من قسم «سكريبت المذيع».",
                    color = ReadingColors.dim,
                    style = MaterialTheme.typography.bodyLarge
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

        // تعليمات الشاشة الواحدة — مثل نافذة على الهواء تمامًا
        if (!onSecondary) {
            Text(
                "لا شاشة ثانية موصولة — اسحب هذه النافذة إلى شاشة القراءة عند توصيلها فتملؤها كاملة",
                style = MaterialTheme.typography.labelSmall,
                color = ReadingColors.dim,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
