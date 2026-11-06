package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.saidjte.schoolradio.desktop.APP_VERSION
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.data.JinglePad
import com.saidjte.schoolradio.desktop.data.JingleWallLogic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * نافذة جدار الجينغل المنبثقة (v0.28.0 «أضف في الاستوديو المندمج زر فتح الجدار
 * في نافذة منبثقة لا تعرقل العمل في الاستوديو المندمج»): نافذة مستقلة غير ماسكة
 * (non-modal) — تبقى مفتوحة بجوار الاستوديو والمذيع يكمل عمله فيها، ويغلقها
 * زرّا الإغلاق أو X كما يشاء. إطلاق الجينلات منها فوري عبر محرك الجدار.
 */
@Composable
fun JingleWallWindow(vm: AppViewModel, onClose: () -> Unit) {
    val windowIcon = remember {
        Thread.currentThread().contextClassLoader
            .getResourceAsStream("icon.png")
            ?.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()) }
            ?.toComposeImageBitmap()
    }
    Window(
        onCloseRequest = onClose,
        title = "جدار الجينغل — إذاعة مدرسية احترافي v$APP_VERSION",
        state = rememberWindowState(width = 820.dp, height = 620.dp),
        icon = windowIcon?.let { BitmapPainter(it) },
        // الاختصارات من نافذة الجدار أيضًا (v0.29.0): 1-9 ومسافة تعمل ما دامت
        // النافذة مركّزة — بلا حقول نصية هنا فلا حارس تحرير
        onPreviewKeyEvent = { ev ->
            com.saidjte.schoolradio.desktop.ui.handleGlobalShortcut(vm, ev, guardTextEditing = false)
        }
    ) {
        AppTheme {
            CompositionLocalProviderRtl {
                JingleWallContent(vm = vm, onClose = onClose)
            }
        }
    }
}

@Composable
private fun CompositionLocalProviderRtl(content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        content()
    }
}

@Composable
private fun JingleWallContent(vm: AppViewModel, onClose: () -> Unit) {
    val pads by vm.jinglePads.collectAsState()
    val playing by vm.jingles.playing.collectAsState()
    val errors by vm.jingles.errors.collectAsState()
    val volume by vm.jingles.volume.collectAsState()
    val sc = LocalStudioColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // الرأس: العنوان + عدّاد الجاري + إيقاف الكل + الإغلاق
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LogoMark(size = 30.dp)
            Column {
                Text(
                    "جدار الجينغل",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "انقر موضعًا لإطلاقه، وانقره ثانية لإيقافه — والمواضع تعمل معًا",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // موضع الفاصل الآلي (STING_SLOT=100) خارج شبكة المواضع — لا يُعدّ في الجدار
            val padPlaying = playing.count { it < JingleWallLogic.PAD_COUNT }
            if (padPlaying > 0) {
                PulsingDot(color = sc.liveDot, size = 9.dp)
                Text(
                    "$padPlaying جارٍ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            SecondaryAction(
                text = "إيقاف الكل",
                enabled = playing.isNotEmpty(),
                onClick = { vm.stopAllJingles() }
            )
            SecondaryAction(text = "إغلاق", onClick = onClose)
        }

        // شبكة المواضع الاثني عشر — تتكيف مع عرض النافذة (4 أعمدة افتراضيًا)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(pads, key = { it.index }) { pad ->
                JinglePadCell(
                    vm = vm,
                    pad = pad,
                    isPlaying = pad.index in playing,
                    error = errors[pad.index]
                )
            }
        }

        // المستوى العام للجينلات — يسري على الجاري فورًا ويُحفظ
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "صوت الجينلات",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Slider(
                value = volume,
                onValueChange = { vm.setJingleVolume(it) },
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge.numeric(),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(52.dp)
            )
        }
    }
}

@Composable
private fun JinglePadCell(vm: AppViewModel, pad: JinglePad, isPlaying: Boolean, error: String?) {
    val file = pad.path?.let { File(it) }
    val missing = file != null && !file.isFile
    var durationMs by remember(pad.path) { mutableStateOf(0L) }

    // مدة المقطع في الخلفية — تحليل MP3 يفتح الملف فلا يجري على خيط الواجهة
    LaunchedEffect(pad.path) {
        val f = file
        if (f != null && f.isFile) {
            durationMs = withContext(Dispatchers.IO) { vm.durationOf(f) }
        } else {
            durationMs = 0L
        }
    }

    val bg = when {
        isPlaying -> MaterialTheme.colorScheme.primaryContainer
        missing -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        pad.isEmpty -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = when {
        isPlaying -> MaterialTheme.colorScheme.primary
        missing -> MaterialTheme.colorScheme.error.copy(alpha = 0.55f)
        pad.isEmpty -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .then(
                if (!pad.isEmpty && !missing) {
                    Modifier.clickable { vm.toggleJingle(pad.index) }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // الرأس: رقم الموضع + الحالة الحية
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "${pad.index + 1}",
                style = MaterialTheme.typography.labelSmall.numeric(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            when {
                isPlaying -> {
                    PulsingDot(color = MaterialTheme.colorScheme.primary, size = 8.dp)
                    Text(
                        "جارٍ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                missing -> Text(
                    "الملف مفقود",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                pad.isEmpty -> Text(
                    "فارغ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // الاسم والمدة — الخط المطاطي في البطاقة
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                pad.title ?: "موضع بانتظار التعيين",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = when {
                missing -> "المسار غير موجود على القرص"
                pad.isEmpty -> "عيّن مقطعًا صوتيًا لهذا الموضع"
                durationMs > 0 -> "${formatMs(durationMs)} · ${file?.extension?.lowercase() ?: ""}"
                else -> file?.extension?.lowercase() ?: ""
            }
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // أفعال مصغّرة تتسع الثلاثة في عرض البطاقة: تشغيل/تعيين/مسح
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (pad.isEmpty || missing) {
                MiniAction(text = "تعيين", filled = true, onClick = { pickAndAssign(vm, pad.index) })
            } else {
                MiniAction(
                    text = if (isPlaying) "إيقاف" else "تشغيل",
                    filled = true,
                    onClick = { vm.toggleJingle(pad.index) }
                )
                MiniAction(text = "تعيين", filled = false, onClick = { pickAndAssign(vm, pad.index) })
                MiniAction(text = "مسح", filled = false, onClick = { vm.clearJingle(pad.index) })
            }
        }
    }
}

/** زر مصغّر لبطاقة الموضع — أخف من SecondaryAction كي تتسع أفعال البطاقة الثلاثة. */
@Composable
private fun MiniAction(text: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = if (filled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (filled) MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.0f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

/** حوار اختيار ملف واحد — التعيين يُحفظ فورًا بين الجلسات. */
private fun pickAndAssign(vm: AppViewModel, slot: Int) {
    val f = FilePickers.pickSingleAudioFile() ?: return
    vm.assignJingle(slot, f)
}
