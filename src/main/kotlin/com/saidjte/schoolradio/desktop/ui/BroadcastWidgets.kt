package com.saidjte.schoolradio.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.MeterLogic
import com.saidjte.schoolradio.desktop.data.MeterZone
import java.io.File

// ================= مقياس البث المقطعي =================

/**
 * مقياس مستوى بطابع غرفة التحكم: 24 مقطع LED بثلاث مناطق (أخضر/كهرماني/أحمر)
 * مع مؤشر ذروة يرتفع فورًا وينحسر ببطء. الامتلاء من اليمين إلى اليسار
 * اتساقًا مع اتجاه القراءة العربية. كل القرارات من MeterLogic والرسم فقط هنا.
 */
@Composable
fun BroadcastMeter(level: Float, modifier: Modifier = Modifier) {
    val sc = LocalStudioColors.current
    var peak by remember { mutableStateOf(0f) }
    LaunchedEffect(level) { peak = MeterLogic.peakIndex(peak, level) }

    val lit = MeterLogic.litSegments(level)
    val peakSeg = MeterLogic.peakSegment(peak)

    Canvas(modifier = modifier) {
        val n = MeterLogic.SEGMENTS
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (n - 1)) / n
        val radius = CornerRadius(minOf(segW, size.height) / 2.5f)
        for (i in 0 until n) {
            val zone = when (MeterLogic.zoneOf(i)) {
                MeterZone.GREEN -> sc.meterGreen
                MeterZone.AMBER -> sc.meterAmber
                MeterZone.RED -> sc.meterRed
            }
            val color = when {
                i == peakSeg -> sc.meterPeak
                i < lit -> zone
                else -> zone.copy(alpha = 0.14f)
            }
            // الامتلاء من اليمين (بداية القراءة العربية)
            val xRight = size.width - i * (segW + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(xRight - segW, 0f),
                size = Size(segW, size.height),
                cornerRadius = radius
            )
        }
    }
}

// ================= مؤشرات الهواء =================

/** نقطة نابضة للمؤشرات الحية. */
@Composable
fun PulsingDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape)
    )
}

/** مؤشر الهواء الموحد في الأشرطة العلوية: «على الهواء» بنبض / «خارج البث» ساكن. */
@Composable
fun OnAirPill(onAir: Boolean, modifier: Modifier = Modifier) {
    val sc = LocalStudioColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (onAir) sc.onAir else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onAir) {
            PulsingDot(color = sc.onAirContent, size = 10.dp)
        } else {
            Dot(color = MaterialTheme.colorScheme.onSurfaceVariant, size = 10.dp)
        }
        Text(
            if (onAir) "على الهواء" else "خارج البث",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (onAir) sc.onAirContent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================= حالة الفراغ الموحدة =================

/** حالة فراغ موحدة: أيقونة + عنوان + توجيه عملي بدل نص خام وحيد. */
@Composable
fun EmptyState(
    icon: AppIconKind,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppIcon(
            kind = icon,
            size = iconSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ================= بطاقة الميكروفون المشتركة =================

/**
 * بطاقة الميكروفون المشتركة بين الاستوديو المدمج والبث المباشر: اختيار الجهاز
 * والمخرج، مقياس مقطعي بذروة، سماع مراقب، وتسجيل — نسخة واحدة يحين مكان نسختين.
 */
@Composable
fun BroadcastMicCard(vm: AppViewModel) {
    val mic = vm.mic
    val state by mic.state.collectAsState()
    val level by mic.level.collectAsState()
    val monitoring by mic.monitoring.collectAsState()
    val recording by mic.recording.collectAsState()
    val recordMs by mic.recordMs.collectAsState()
    val liveMs by mic.liveMs.collectAsState()
    val error by mic.error.collectAsState()
    val deviceName by mic.deviceName.collectAsState()
    val sc = LocalStudioColors.current

    var devices by remember { mutableStateOf(AudioDevices.listMics()) }
    var outputs by remember { mutableStateOf(AudioDevices.listOutputs()) }
    var selectedDevice by remember { mutableStateOf(devices.firstOrNull()) }
    var selectedOut by remember { mutableStateOf(outputs.firstOrNull()) }
    var devicesMenu by remember { mutableStateOf(false) }
    var outMenu by remember { mutableStateOf(false) }

    /** إعادة تعداد أجهزة الإدخال والإخراج مع الحفاظ على الاختيار إن كان لا يزال موجودًا. */
    fun refreshDeviceLists() {
        val prevIn = selectedDevice
        val prevOut = selectedOut
        devices = AudioDevices.listMics()
        outputs = AudioDevices.listOutputs()
        selectedDevice = devices.firstOrNull { it.name == prevIn?.name } ?: devices.firstOrNull()
        selectedOut = outputs.firstOrNull { it.name == prevOut?.name } ?: outputs.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "الميكروفون",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                SecondaryAction(
                    text = "الجهاز…",
                    onClick = { refreshDeviceLists(); devicesMenu = true }
                )
                DropdownMenu(expanded = devicesMenu, onDismissRequest = { devicesMenu = false }) {
                    devices.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                selectedDevice = d
                                devicesMenu = false
                            }
                        )
                    }
                }
            }
        }

        // محدّد المخرج (السماعات): يوجّه الموسيقى والتسجيلات والسماع المراقب
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "المخرج",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Box {
                SecondaryAction(
                    text = selectedOut?.let { shortenName(it.name, 14) } ?: "المخرج…",
                    onClick = { refreshDeviceLists(); outMenu = true }
                )
                DropdownMenu(expanded = outMenu, onDismissRequest = { outMenu = false }) {
                    outputs.forEach { d ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (d.mixerInfo == selectedOut?.mixerInfo && d.name == selectedOut?.name) "● " else "") + d.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                selectedOut = d
                                vm.player.setOutput(d.mixerInfo)
                                mic.setMonitorOutput(d.mixerInfo)
                                outMenu = false
                            }
                        )
                    }
                }
            }
            SecondaryAction(text = "تحديث", onClick = { refreshDeviceLists() })
        }

        if (state == MicState.LIVE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction(
                    text = "إيقاف البث",
                    danger = true,
                    onClick = {
                        mic.stopRecording()
                        mic.stop()
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatMs(liveMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = sc.liveTimer,
                    fontWeight = FontWeight.Bold
                )
            }
            BroadcastMeter(level = level, modifier = Modifier.fillMaxWidth().height(16.dp))
        } else {
            PrimaryAction(
                text = "بدء البث المباشر",
                onClick = { mic.start(selectedDevice?.mixerInfo, selectedOut?.mixerInfo) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = monitoring, onCheckedChange = { mic.setMonitoring(it) })
            Text("سماع مراقب", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            if (recording) {
                SecondaryAction(text = "إيقاف التسجيل", onClick = { mic.stopRecording() })
                PulsingDot(color = sc.recDot, size = 10.dp)
                Text(
                    formatMs(recordMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = sc.recordingAccent
                )
            } else {
                SecondaryAction(
                    text = "تسجيل",
                    enabled = state == MicState.LIVE,
                    onClick = { mic.startRecording(vm.newRecordingTarget()) }
                )
            }
        }
        deviceName?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        error?.let {
            Text(
                "خطأ: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ================= بطاقة الموسيقى المشتركة =================

/** بطاقة الموسيقى المشتركة: تحكم كامل بشبكة أزرار مريحة + خط تقدم تحت العنوان. */
@Composable
fun BroadcastMusicCard(vm: AppViewModel) {
    val player = vm.player
    val playlist by vm.playlist.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isPaused by player.isPaused.collectAsState()
    val hasTrack by player.hasTrack.collectAsState()
    val title by player.title.collectAsState()
    val positionMs by player.positionMs.collectAsState()
    val durationMs by player.durationMs.collectAsState()
    val volume by player.volume.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "الموسيقى",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            SecondaryAction(
                text = "ملفات",
                onClick = {
                    val files = FilePickers.pickAudioFiles()
                    if (files != null) vm.addFiles(files)
                }
            )
            SecondaryAction(
                text = "مجلد",
                onClick = {
                    val dir = FilePickers.pickAudioFolder()
                    if (dir != null) vm.addFolder(dir)
                }
            )
        }

        Text(
            title ?: "لا شيء قيد التشغيل",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${formatMs(positionMs)} / ${formatMs(durationMs)}${if (playlist.isEmpty()) "" else " · القائمة: ${playlist.size}"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (hasTrack && durationMs > 0) {
            val frac = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LocalStudioColors.current.meterTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(frac)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        // شبكة أزرار 2×2 — أهداف كبيرة مريحة دون أي قصّ داخل عمود ضيق
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isPlaying && !isPaused) {
                PrimaryAction(
                    text = "إيقاف مؤقت",
                    onClick = { player.pause() },
                    modifier = Modifier.weight(1f)
                )
            } else {
                PrimaryAction(
                    text = "تشغيل",
                    onClick = { vm.togglePlay() },
                    enabled = playlist.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
            SecondaryAction(
                text = "إيقاف",
                onClick = { player.stop() },
                enabled = hasTrack,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SecondaryAction(
                text = "السابق",
                onClick = { vm.previous() },
                enabled = playlist.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
            SecondaryAction(
                text = "التالي",
                onClick = { vm.next() },
                enabled = playlist.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }

        Slider(
            value = volume,
            onValueChange = { player.setVolume(it) },
            valueRange = 0f..1f
        )
    }
}

/** تقصير اسم الجهاز في الأزرار الصغيرة للحفاظ على سلاسة العمود. */
internal fun shortenName(s: String, max: Int = 16): String =
    if (s.length <= max) s else s.take(max - 1) + "…"

// ================= شريط الموعد الأسبوعي المنطلق =================

/**
 * تنبيه لحظي أعلى صفحات البث عندما ينطلق موعد أسبوعي مجدول:
 * عنوان الحدث + شارة «الآن» + زر إغلاق. يظهر في الاستوديو المدمج والبث المباشر.
 */
@Composable
fun WeeklyAlertBanner(vm: AppViewModel, modifier: Modifier = Modifier) {
    val alert by vm.activeAlert.collectAsState()
    val sc = LocalStudioColors.current
    alert?.let { ev ->
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF7A1F1F).copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PulsingDot(color = Color(0xFFFFCDD2), size = 9.dp)
            Text(
                "موعد مجدول",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFFFCDD2),
                fontWeight = FontWeight.Bold
            )
            Text(
                ev.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (ev.autoStartRun == true) {
                Text(
                    "انطلقت الحلقة",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFCDD2)
                )
            }
            Text(
                "إغلاق",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.dismissAlert() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
