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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.Tab
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.SegmentStatus
import java.io.File

/** ألوان أنواع الفقرات — تمييز بصري سريع في القائمة. */
private fun kindColor(kind: SegmentKind): Color = when (kind) {
    SegmentKind.LIVE -> Color(0xFFEF5350)
    SegmentKind.MUSIC -> Color(0xFF4FC3F7)
    SegmentKind.RECORDING -> Color(0xFF80CBC4)
    SegmentKind.SPEECH -> Color(0xFFCCC2DC)
}

/**
 * جدولة الفقرات: قائمة فقرات الحلقة (نوع/مدة/ملف مرتبط) مع ساعة مخططة
 * ولوحة تشغيل حي تُدير الحلقة فقرةً فقرة.
 */
@Composable
fun ScheduleTab(vm: AppViewModel) {
    val schedule by vm.schedule.collectAsState()
    val episodeStart by vm.episodeStartMinutes.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val runElapsedMs by vm.runElapsedMs.collectAsState()
    val playerPlaying by vm.player.isPlaying.collectAsState()
    val playerPaused by vm.player.isPaused.collectAsState()
    val playerFile by vm.player.currentFile.collectAsState()

    var editorItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ScheduleItem?>(null) }
    var startText by remember { mutableStateOf("") }

    val clocks = remember(schedule, episodeStart) {
        ScheduleMath.plannedClocks(schedule, episodeStart)
    }
    val statuses = remember(runIndex, schedule.size) {
        ScheduleMath.statuses(runIndex, schedule.size)
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ============ قائمة الفقرات ============
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "جدولة الفقرات",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                PrimaryAction(text = "فقرة جديدة", onClick = { showCreate = true })
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "بداية الحلقة:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = startText,
                    onValueChange = { v -> if (v.length <= 5) startText = v },
                    placeholder = { Text(episodeStart?.let { ScheduleMath.clockOf(it) } ?: "08:15") },
                    singleLine = true,
                    modifier = Modifier.width(120.dp)
                )
                SecondaryAction(
                    text = "تعيين",
                    onClick = {
                        parseClock(startText)?.let { vm.setEpisodeStart(it) }
                    }
                )
                if (episodeStart != null) {
                    SecondaryAction(
                        text = "إزالة",
                        onClick = {
                            vm.setEpisodeStart(null)
                            startText = ""
                        }
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "المجموع المخطط: ${formatMs(ScheduleMath.totalSeconds(schedule) * 1000)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (schedule.isEmpty()) {
                Text(
                    "لا فقرات بعد — أضف فقرات الحلقة (تلاوة، نشرة، فاصل موسيقي…) لتديرها على الهواء.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(schedule) { index, item ->
                        val isCurrent = runActive && index == runIndex
                        val playingThis = item.mediaPath != null &&
                                playerFile == File(item.mediaPath) &&
                                playerPlaying && !playerPaused
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
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        item.title.ifEmpty { "بدون عنوان" },
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.kind.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = kindColor(item.kind),
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (runActive) {
                                        Text(
                                            statuses[index].label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (statuses[index]) {
                                                SegmentStatus.CURRENT -> MaterialTheme.colorScheme.primary
                                                SegmentStatus.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
                                                SegmentStatus.UPCOMING -> MaterialTheme.colorScheme.outline
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                val clock = clocks[index]
                                val line = buildString {
                                    if (clock != null) append("تبدأ $clock · ")
                                    append("المدة: ${formatMs(item.plannedSeconds * 1000)}")
                                    if (item.note.isNotBlank()) append(" · ${item.note}")
                                }
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            when {
                                item.kind == SegmentKind.LIVE -> SecondaryAction(
                                    text = "إلى البث",
                                    onClick = { vm.selectTab(Tab.LIVE) }
                                )
                                item.mediaPath != null && playingThis -> SecondaryAction(
                                    text = "إيقاف",
                                    onClick = { vm.player.stop() }
                                )
                                item.mediaPath != null -> SecondaryAction(
                                    text = "تشغيل",
                                    onClick = { vm.runPlayMedia(item) }
                                )
                            }
                            SecondaryAction(
                                text = "أعلى",
                                enabled = index > 0,
                                onClick = { vm.moveScheduleItem(item.id, -1) }
                            )
                            SecondaryAction(
                                text = "أسفل",
                                enabled = index < schedule.size - 1,
                                onClick = { vm.moveScheduleItem(item.id, +1) }
                            )
                            SecondaryAction(
                                text = "تحرير",
                                onClick = { editorItem = item }
                            )
                            SecondaryAction(
                                text = "حذف",
                                onClick = { deleteTarget = item }
                            )
                        }
                    }
                }
            }
        }

        // ============ لوحة التشغيل الحي ============
        Column(
            modifier = Modifier.width(330.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "التشغيل الحي للحلقة",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!runActive) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("الحلقة غير جارية", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (schedule.isEmpty()) "أضف فقرات أولًا ثم ابدأ الحلقة."
                        else "الفقرات: ${schedule.size} · المخطط: ${formatMs(ScheduleMath.totalSeconds(schedule) * 1000)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PrimaryAction(
                        text = "بدء الحلقة",
                        enabled = schedule.isNotEmpty(),
                        onClick = { vm.startRun() }
                    )
                }
            } else {
                val current = schedule.getOrNull(runIndex)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "الفقرة الجارية ${if (runIndex >= 0) "(${runIndex + 1}/${schedule.size})" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        current?.title ?: "—",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    current?.let { c ->
                        Text(
                            "${c.kind.label} · متأخرة ${formatMs(runElapsedMs)} من ${
                                formatMs(c.plannedSeconds * 1000)
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (c.plannedSeconds > 0) {
                            val frac = (runElapsedMs / 1000.0 / c.plannedSeconds).coerceIn(0.0, 1.0)
                            LinearProgressIndicator(
                                progress = { frac.toFloat() },
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (runElapsedMs / 1000L > c.plannedSeconds) {
                                Text(
                                    "تجاوزت المدة المخططة",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (c.kind == SegmentKind.LIVE) {
                            SecondaryAction(text = "إلى البث المباشر", onClick = { vm.selectTab(Tab.LIVE) })
                        } else if (c.mediaPath != null) {
                            val playingThis = playerFile == File(c.mediaPath) && playerPlaying && !playerPaused
                            if (playingThis) {
                                SecondaryAction(text = "إيقاف الملف", onClick = { vm.player.stop() })
                            } else {
                                SecondaryAction(text = "تشغيل الملف المرتبط", onClick = { vm.runPlayMedia(c) })
                            }
                        }
                    }
                    PrimaryAction(text = "إنهاء الفقرة ← التالية", onClick = { vm.finishCurrentItem() })
                    SecondaryAction(text = "إنهاء الحلقة", onClick = { vm.endRun() })
                    val next = schedule.getOrNull(runIndex + 1)
                    if (next != null) {
                        Text(
                            "التالية: ${next.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // ============ حوارات التحرير والحذف ============
    if (showCreate) {
        SegmentEditorDialog(
            vm = vm,
            title = "فقرة جديدة",
            initial = null,
            onDismiss = { showCreate = false }
        )
    }
    if (editorItem != null) {
        SegmentEditorDialog(
            vm = vm,
            title = "تحرير الفقرة",
            initial = editorItem,
            onDismiss = { editorItem = null }
        )
    }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل تريد حذف فقرة «${deleteTarget?.title}» نهائيًا؟") },
            confirmButton = {
                TextButton(onClick = {
                    val t = deleteTarget
                    deleteTarget = null
                    if (t != null) vm.deleteScheduleItem(t.id)
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

/** تحليل نص "HH:mm" إلى دقائق اليوم، أو null إن كان غير صالح. */
internal fun parseClock(text: String): Int? {
    val m = Regex("^(\\d{1,2}):(\\d{2})$").find(text.trim()) ?: return null
    val h = m.groupValues[1].toInt()
    val mm = m.groupValues[2].toInt()
    if (h !in 0..23 || mm !in 0..59) return null
    return h * 60 + mm
}

@Composable
private fun SegmentEditorDialog(
    vm: AppViewModel,
    title: String,
    initial: ScheduleItem?,
    onDismiss: () -> Unit
) {
    var t by remember { mutableStateOf(initial?.title ?: "") }
    var kind by remember { mutableStateOf(initial?.kind ?: SegmentKind.SPEECH) }
    var minutes by remember { mutableStateOf(((initial?.plannedSeconds ?: 0L) / 60).toString()) }
    var seconds by remember { mutableStateOf(((initial?.plannedSeconds ?: 0L) % 60).toString()) }
    var mediaPath by remember { mutableStateOf(initial?.mediaPath) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var kindMenu by remember { mutableStateOf(false) }
    var recMenu by remember { mutableStateOf(false) }

    val recordings by vm.recordings.collectAsState()

    fun plannedSeconds(): Long {
        val m = minutes.toLongOrNull() ?: 0L
        val s = seconds.toLongOrNull() ?: 0L
        return m * 60 + s
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    label = { Text("عنوان الفقرة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Box {
                    SecondaryAction(text = "النوع: ${kind.label}", onClick = { kindMenu = true })
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        SegmentKind.entries.forEach { k ->
                            DropdownMenuItem(
                                text = { Text(k.label) },
                                onClick = {
                                    kind = k
                                    if (k == SegmentKind.SPEECH || k == SegmentKind.LIVE) mediaPath = null
                                    kindMenu = false
                                }
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) minutes = v },
                        label = { Text("دقائق") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = seconds,
                        onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) seconds = v },
                        label = { Text("ثوانٍ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (kind == SegmentKind.MUSIC || kind == SegmentKind.RECORDING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (kind == SegmentKind.MUSIC) {
                            SecondaryAction(
                                text = "اختر ملف موسيقى",
                                onClick = {
                                    val f = FilePickers.pickAudioFiles()?.firstOrNull()
                                    if (f != null) mediaPath = f.absolutePath
                                }
                            )
                        } else {
                            Box {
                                SecondaryAction(text = "من التسجيلات", onClick = { recMenu = true })
                                DropdownMenu(expanded = recMenu, onDismissRequest = { recMenu = false }) {
                                    if (recordings.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("لا توجد تسجيلات") },
                                            onClick = { recMenu = false }
                                        )
                                    }
                                    recordings.forEach { r ->
                                        DropdownMenuItem(
                                            text = { Text(r.displayName) },
                                            onClick = {
                                                mediaPath = r.file.absolutePath
                                                recMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (mediaPath != null) {
                            SecondaryAction(
                                text = "إزالة الملف",
                                onClick = { mediaPath = null }
                            )
                        }
                    }
                    Text(
                        mediaPath ?: "لا ملف مرتبط",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة (اختياري)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ps = plannedSeconds()
                if (initial == null) vm.addScheduleItem(t, kind, ps, mediaPath, note)
                else vm.updateScheduleItem(initial.id, t, kind, ps, mediaPath, note)
                onDismiss()
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
