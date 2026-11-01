package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saidjte.schoolradio.desktop.APP_VERSION
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.Tab
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.SegmentStatus
import com.saidjte.schoolradio.desktop.data.StudioLogic
import java.io.File

/**
 * الاستوديو المدمج: الجدولة + السكريبت + البث في شاشة واحدة للإذاعة.
 * ثلاثة أعمدة متزامنة: (يمين) فقرات الحلقة ولوحة تشغيلها الحي،
 * (وسط) لوحة قراءة السكريبت بخط كبير تنتقل آليًا مع الفقرة الجارية،
 * (يسار) البث: الميكروفون والموسيقى والتسجيلات — بلا تنقّل بين الأقسام.
 */
@Composable
fun StudioScreen(vm: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(vm)
        // شريط الموعد الأسبوعي المنطلق — يظهر أعلاه محتوى الاستوديو لحظة الإطلاق
        WeeklyAlertBanner(
            vm = vm,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StudioSchedulePanel(vm)
            StudioScriptPanel(vm, Modifier.weight(1f))
            StudioBroadcastPanel(vm)
        }
    }
}

// ================= الشريط العلوي: الهواء + الساعة =================

@Composable
private fun StudioTopBar(vm: AppViewModel) {
    val clock by vm.clockText.collectAsState()
    val schoolName by vm.schoolName.collectAsState()
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
        Column {
            Text(
                "الاستوديو المدمج",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (schoolName != null) {
                Text(
                    schoolName ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        // مؤشر الهواء الموحد بنبض حي
        OnAirPill(onAir = onAir)
        if (recording) {
            Text(
                "تسجيل جارٍ",
                style = MaterialTheme.typography.labelLarge,
                color = LocalStudioColors.current.recordingAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            clock,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        SecondaryAction(text = "الأقسام التفصيلية", onClick = { vm.selectTab(Tab.LIVE) })
        Text(
            "v$APP_VERSION",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================= العمود الأيمن: الجدولة =================

@Composable
private fun StudioSchedulePanel(vm: AppViewModel) {
    val schedule by vm.schedule.collectAsState()
    val episodeStart by vm.episodeStartMinutes.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val runElapsedMs by vm.runElapsedMs.collectAsState()
    val playerFile by vm.player.currentFile.collectAsState()
    val playerPlaying by vm.player.isPlaying.collectAsState()
    val playerPaused by vm.player.isPaused.collectAsState()

    val clocks = remember(schedule, episodeStart) {
        ScheduleMath.plannedClocks(schedule, episodeStart)
    }
    val statuses = remember(runIndex, schedule.size) {
        ScheduleMath.statuses(runIndex, schedule.size)
    }

    Column(
        modifier = Modifier.width(360.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "الجدولة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${schedule.size} فقرات · ${formatMs(ScheduleMath.totalSeconds(schedule) * 1000)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // لوحة التحكم الحية للحلقة
        if (!runActive) {
            PrimaryAction(
                text = "بدء الحلقة",
                enabled = schedule.isNotEmpty(),
                onClick = { vm.startRun() },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            val current = schedule.getOrNull(runIndex)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "الفقرة الجارية (${runIndex + 1}/${schedule.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    current?.title ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                current?.let { c ->
                    if (c.plannedSeconds > 0) {
                        val frac = (runElapsedMs / 1000.0 / c.plannedSeconds).coerceIn(0.0, 1.0)
                        LinearProgressIndicator(
                            progress = { frac.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${formatMs(runElapsedMs)} من ${formatMs(c.plannedSeconds * 1000)}" +
                                    if (runElapsedMs / 1000L > c.plannedSeconds) " — تجاوزت المدة" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (runElapsedMs / 1000L > c.plannedSeconds) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        Text(
                            "زمن الفقرة: ${formatMs(runElapsedMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryAction(
                        text = "الفقرة التالية",
                        onClick = { vm.finishCurrentItem() },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryAction(text = "إنهاء الحلقة", onClick = { vm.endRun() })
                }
            }
        }

        // قائمة الفقرات
        if (schedule.isEmpty()) {
            EmptyState(
                icon = AppIconKind.SCHEDULE,
                title = "لا فقرات بعد",
                hint = "أضف فقرات الحلقة من قسم الجدولة التفصيلي، ثم ابدأ الحلقة من هنا وستُدار فقرةً فقرة."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(schedule) { index, item ->
                    val isCurrent = runActive && index == runIndex
                    val playingThis = item.mediaPath != null &&
                            playerFile == File(item.mediaPath) &&
                            playerPlaying && !playerPaused
                    StudioSegmentRow(
                        item = item,
                        index = index,
                        clock = clocks[index],
                        status = statuses[index],
                        isCurrent = isCurrent,
                        runElapsedMs = if (isCurrent) runElapsedMs else 0L,
                        playingThis = playingThis,
                        onClick = {
                            // النقر على الفقرة يعرض سكريبتها على لوحة القراءة
                            val sid = item.scriptSectionId
                            if (sid != null && sid > 0L) vm.selectSection(sid)
                        },
                        onPlayMedia = { vm.runPlayMedia(it) },
                        onStopMedia = { vm.player.stop() }
                    )
                }
            }
        }

        SecondaryAction(
            text = "إدارة الفقرات (إضافة/تحرير/ربط سكريبت)",
            onClick = { vm.selectTab(Tab.SCHEDULE) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StudioSegmentRow(
    item: ScheduleItem,
    index: Int,
    clock: String?,
    status: SegmentStatus,
    isCurrent: Boolean,
    runElapsedMs: Long,
    playingThis: Boolean,
    onClick: () -> Unit,
    onPlayMedia: (ScheduleItem) -> Unit,
    onStopMedia: () -> Unit
) {
    val overdue = isCurrent && item.plannedSeconds > 0 && runElapsedMs / 1000L > item.plannedSeconds
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer
                    playingThis -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            clock ?: "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp)
        )
        Dot(color = segmentKindColor(item.kind), size = 9.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    item.title.ifEmpty { "بدون عنوان" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.scriptSectionId != null && item.scriptSectionId > 0L) {
                    Text(
                        "سكريبت",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                buildString {
                    append("${item.kind.label} · ${formatMs(item.plannedSeconds * 1000)}")
                    if (isCurrent) append(" · جارية ${formatMs(runElapsedMs)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.mediaPath != null) {
            if (playingThis) {
                SecondaryAction(text = "إيقاف", onClick = onStopMedia)
            } else {
                SecondaryAction(text = "تشغيل", onClick = { onPlayMedia(item) })
            }
        }
    }
}

// ================= العمود الأوسط: لوحة السكريبت =================

@Composable
private fun StudioScriptPanel(vm: AppViewModel, modifier: Modifier = Modifier) {
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
                "السكريبت",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // مصدر النص المعروض
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

        // لوحة القراءة الكبيرة
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            val shown: ScriptSection? = resolved?.first
            if (shown == null) {
                Text(
                    "لا يوجد سكريبت بعد — أضف فقرات النص من قسم «سكريبت المذيع» وستظهر هنا بخط كبير للقراءة على الهواء.",
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

        // معلومات أسفل اللوحة + تنقّل سلس بين الفقرات أثناء القراءة
        val shown = resolved?.first
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (shown == null) "لا نص"
                else "${ScriptStats.wordCount(shown.body)} كلمة · " +
                        "زمن تقديري ${formatMs(ScriptStats.secondsFor(shown.body) * 1000)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            SecondaryAction(
                text = "السابقة",
                onClick = { vm.selectAdjacentSection(-1) },
                enabled = script.isNotEmpty()
            )
            SecondaryAction(
                text = "التالية",
                onClick = { vm.selectAdjacentSection(+1) },
                enabled = script.isNotEmpty()
            )
            SecondaryAction(
                text = "تحرير السكريبت",
                onClick = { vm.selectTab(Tab.SCRIPT) }
            )
        }
    }
}

// ================= العمود الأيسر: البث =================

@Composable
private fun StudioBroadcastPanel(vm: AppViewModel) {
    Column(
        modifier = Modifier.width(330.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "البث",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        BroadcastMicCard(vm)
        BroadcastMusicCard(vm)
        StudioRecordingsCard(vm, Modifier.weight(1f))
    }
}

@Composable
private fun StudioRecordingsCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val recordings by vm.recordings.collectAsState()
    val dir by vm.recordingsDir.collectAsState()
    val currentFile by vm.player.currentFile.collectAsState()
    val isPlaying by vm.player.isPlaying.collectAsState()
    val isPaused by vm.player.isPaused.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "التسجيلات",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            SecondaryAction(text = "تحديث", onClick = { vm.refreshRecordings() })
            SecondaryAction(text = "المكتبة", onClick = { vm.selectTab(Tab.RECORDINGS) })
        }
        if (recordings.isEmpty()) {
            EmptyState(
                icon = AppIconKind.RECORDINGS,
                iconSize = 26.dp,
                title = "لا توجد تسجيلات",
                hint = "ابدأ التسجيل أثناء البث المباشر وستظهر هنا."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(recordings) { _, item ->
                    val playingThis = currentFile == item.file && isPlaying && !isPaused
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (playingThis) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${formatMs(item.durationMs)} · %.1f م.ب".format(item.sizeBytes / 1048576.0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (playingThis) {
                            SecondaryAction(text = "إيقاف", onClick = { vm.player.stop() })
                        } else {
                            SecondaryAction(text = "تشغيل", onClick = { vm.player.play(item.file) })
                        }
                    }
                }
            }
        }
        Text(
            "المجلد: ${dir.absolutePath}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
