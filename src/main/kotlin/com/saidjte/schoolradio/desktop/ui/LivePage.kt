package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saidjte.schoolradio.desktop.APP_VERSION
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.Tab
import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.StudioLogic

/**
 * صفحة البث المباشر — الشاشة المدمجة الوحيدة في التطبيق، ومحتواها فقط ما يلزم المذيع
 * أثناء الهواء: الميكروفون والموسيقى ولوحة القراءة الكبيرة. بقية الأقسام (الجدولة،
 * تحرير السكريبت، المكتبة) لِكلٍّ منها صفحته الخاصة — كي لا تكتظ صفحة البث
 * وتبقى السلاسة في العمل أثناء البث المباشر دون تنقّل بين الصفحات.
 */
@Composable
fun LivePage(vm: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        LiveTopBar(vm)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReadingPane(vm, Modifier.weight(1f))
            BroadcastColumn(vm)
        }
    }
}

// ================= الشريط العلوي: الهواء + الساعة =================

@Composable
private fun LiveTopBar(vm: AppViewModel) {
    val clock by vm.clockText.collectAsState()
    val micState by vm.mic.state.collectAsState()
    val recording by vm.mic.recording.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val paused by vm.player.isPaused.collectAsState()
    val onAir = StudioLogic.status(
        micLive = micState == MicState.LIVE,
        recording = recording,
        musicPlaying = playing && !paused
    ).onAir

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "البث المباشر",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // مؤشر الهواء
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(if (onAir) Color(0xFFC62828) else MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Dot(
                color = if (onAir) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                size = 10.dp
            )
            Text(
                if (onAir) "على الهواء" else "خارج البث",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (onAir) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (recording) {
            Text(
                "تسجيل جارٍ",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFEF9A9A),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            clock,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "v$APP_VERSION",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================= لوحة القراءة الكبيرة (العمود الأعرض) =================

@Composable
private fun ReadingPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val schedule by vm.schedule.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val script by vm.script.collectAsState()
    val selectedId by vm.selectedSectionId.collectAsState()
    val fullReading by vm.fullScriptReading.collectAsState()

    var fontSp by remember { mutableStateOf(34f) }

    val resolved = StudioLogic.resolveScriptSection(schedule, runActive, runIndex, script, selectedId)

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "لوحة القراءة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // مصدر النص المعروض: من الجدولة أم اختيار يدوي
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    resolved?.second?.label ?: "فارغ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(checked = fullReading, onCheckedChange = { vm.fullScriptReading.value = it })
                Text("كامل", style = MaterialTheme.typography.bodySmall)
            }
            SecondaryAction(text = "أ−", onClick = { fontSp = (fontSp - 4f).coerceAtLeast(16f) })
            SecondaryAction(text = "أ+", onClick = { fontSp = (fontSp + 4f).coerceAtMost(72f) })
        }

        // سطح القراءة الكبير
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (script.isEmpty()) {
                Text(
                    "لا يوجد سكريبت بعد — أضف فقرات النص من قسم «سكريبت المذيع» وستظهر هنا بخط كبير للقراءة على الهواء.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (fullReading) {
                // وضع «كامل»: السكريبت بأكمله للقراءة المتصلة دون تنقّل
                script.forEachIndexed { i, sec ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            sec.title.ifEmpty { "بدون عنوان" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        sec.body.ifEmpty { "—" },
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.5f).sp
                    )
                    Spacer(Modifier.height(14.dp))
                }
            } else {
                val shown = resolved?.first
                if (shown == null) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        shown.title.ifEmpty { "بدون عنوان" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        shown.body.ifEmpty { "—" },
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.5f).sp
                    )
                }
            }
        }

        // أسفل اللوحة: معلومات + تنقّل سلس بأقل عدد من الأزرار
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (fullReading) {
                    "السكريبت كاملًا · ${script.size} فقرات"
                } else {
                    resolved?.first?.let {
                        "${ScriptStats.wordCount(it.body)} كلمة · " +
                                "زمن تقديري ${formatMs(ScriptStats.secondsFor(it.body) * 1000)}"
                    } ?: "لا نص"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (runActive) {
                // أثناء جريان الحلقة: زر واحد ينهي الفقرة وينتقل للسكريبت التالي آليًا
                PrimaryAction(
                    text = "إنهاء الفقرة (${runIndex + 1}/${schedule.size})",
                    onClick = { vm.finishCurrentItem() }
                )
            } else {
                SecondaryAction(
                    text = "السابقة",
                    onClick = { vm.selectAdjacentSection(-1) }
                )
                SecondaryAction(
                    text = "التالية",
                    onClick = { vm.selectAdjacentSection(+1) }
                )
            }
            SecondaryAction(text = "تحرير", onClick = { vm.selectTab(Tab.SCRIPT) })
        }
    }
}

// ================= عمود البث: الميكروفون + الموسيقى =================

@Composable
private fun BroadcastColumn(vm: AppViewModel) {
    Column(
        modifier = Modifier.width(330.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MicCard(vm)
        MusicCard(vm)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MicCard(vm: AppViewModel) {
    val mic = vm.mic
    val state by mic.state.collectAsState()
    val level by mic.level.collectAsState()
    val monitoring by mic.monitoring.collectAsState()
    val recording by mic.recording.collectAsState()
    val recordMs by mic.recordMs.collectAsState()
    val liveMs by mic.liveMs.collectAsState()
    val error by mic.error.collectAsState()
    val deviceName by mic.deviceName.collectAsState()

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
                    color = Color(0xFF81C784),
                    fontWeight = FontWeight.Bold
                )
            }
            LevelBarH(level = level, modifier = Modifier.fillMaxWidth().height(14.dp))
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
                Dot(color = Color(0xFFE53935), size = 10.dp)
                Text(
                    formatMs(recordMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFEF9A9A)
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

/** تقصير اسم الجهاز لزر صغير دون قصّ تخطيط البطاقة. */
private fun shortenName(s: String, max: Int = 16): String =
    if (s.length <= max) s else s.take(max - 1) + "…"

@Composable
private fun MusicCard(vm: AppViewModel) {
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

        // شبكة أزرار 2×2 — أهداف كبيرة مريحة دون أي قصّ داخل عمود 330dp
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
