package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.RecordingStudio

/**
 * لوحة «استوديو التسجيل الاحترافي» (v0.43.0) — بنود الطلب الأحد عشر
 * في لوحة واحدة داخل صفحة التسجيلات: أجهزة متعددة (متعدد المسارات)،
 * جودة 16/24/32 بت ومعدلات 44.1/48/96/192 كيلوهرتز، إيقاف مؤقت واستئناف،
 * سماع مراقبة، مقياس لحظي ومؤشر تشبع، جدولة، وفوقية على المونتاج.
 * كل القرارات في RecordingStudio والمحرك — هنا رسم ونداءات فقط.
 */
@Composable
fun RecordingStudioPanel(vm: AppViewModel) {
    val state by vm.recorder.state.collectAsState()
    val elapsed by vm.recorder.elapsedMs.collectAsState()
    val levels by vm.recorder.levels.collectAsState()
    val clipCounts by vm.recorder.clipCounts.collectAsState()
    val clipLatched by vm.recorder.clipLatched.collectAsState()
    val quality by vm.recorderQuality.collectAsState()
    val mics by vm.recorderMics.collectAsState()
    val selected by vm.recorderSelected.collectAsState()
    val monitor by vm.recorderMonitor.collectAsState()
    val message by vm.recorderMessage.collectAsState()
    val conversionNote by vm.recorder.conversionNote.collectAsState()
    val deviceNames by vm.recorder.deviceNames.collectAsState()
    val error by vm.recorder.error.collectAsState()
    val schedules by vm.recorderSchedules.collectAsState()
    val overdubActive by vm.overdubActive.collectAsState()

    var showScheduleDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ================= رأس اللوحة =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                Text(
                    "استوديو التسجيل الاحترافي",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        quality.labelAr(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                SecondaryAction(text = "تحديث الأجهزة", onClick = { vm.refreshRecorderMics() })
                SecondaryAction(
                    text = if (monitor) "المراقبة: تعمل" else "المراقبة: معطلة",
                    onClick = { vm.setRecorderMonitor(!monitor) }
                )
            }

            // ================= الأجهزة المختارة (التسجيل متعدد المسارات) =================
            Text(
                "أجهزة الإدخال (اختر جهازًا أو أكثر — لكل جهاز ملف مستقل يُرسل إلى مسار المونتاج):",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                if (mics.isEmpty()) {
                    Text(
                        "لا توجد أجهزة إدخال — وصّل ميكروفونًا واضغط «تحديث الأجهزة»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    mics.forEach { m ->
                        val on = m.name in selected
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { vm.toggleRecorderDevice(m.name) }
                        ) {
                            Text(
                                (if (on) "✓ " else "") + m.name,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ================= الجودة: عمق البت ومعدل العينات =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                Text("الجودة:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                RecordingStudio.ALLOWED_BITS.forEach { b ->
                    QualityChip(label = "${b} بت", selected = quality.bits == b) { vm.setRecorderBits(b) }
                }
                RecordingStudio.ALLOWED_RATES.forEach { r ->
                    val lbl = when (r) {
                        44_100 -> "44.1"
                        192_000 -> "192"
                        else -> (r / 1000).toString()
                    }
                    QualityChip(label = "$lbl كيلوهرتز", selected = quality.rateHz == r) { vm.setRecorderRate(r) }
                }
            }

            // ================= المقياس اللحظي ومؤشر التشبع =================
            if (state != RecordingStudio.RecState.IDLE && deviceNames.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    deviceNames.forEachIndexed { i, name ->
                        val lvl = levels.getOrNull(i) ?: 0f
                        val latched = clipLatched.getOrNull(i) ?: false
                        val count = clipCounts.getOrNull(i) ?: 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(170.dp)
                            )
                            BroadcastMeter(
                                level = lvl,
                                modifier = Modifier.fillMaxWidth().height(14.dp).weight(1f)
                            )
                            // مؤشر التشبع (البندين 8 و9): نقطة حمراء مقفولة وعدّ الأحداث
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.width(92.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .width(12.dp)
                                        .height(12.dp)
                                        .background(
                                            if (latched) MaterialTheme.colorScheme.error
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                )
                                Text(
                                    if (count > 0) "تشبع ×$count" else "لا تشبع",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (count > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                    conversionNote?.let {
                        Text(
                            "ملاحظة العتاد: $it — الملف يخرج ${quality.labelAr()} على أي حال",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }

            // ================= أزرار النقل والحالة =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                when (state) {
                    RecordingStudio.RecState.IDLE -> {
                        SecondaryAction(text = "● بدء التسجيل", onClick = { vm.startProRecording() })
                        SecondaryAction(
                            text = "فوقية على المونتاج",
                            enabled = !overdubActive,
                            onClick = { vm.startMontageOverdub() }
                        )
                    }
                    RecordingStudio.RecState.RECORDING -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                "جارٍ التسجيل",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        SecondaryAction(text = "⏸ إيقاف مؤقت", onClick = { vm.pauseProRecording() })
                        SecondaryAction(text = "■ إنهاء وحفظ", onClick = { vm.stopProRecording() })
                        if (overdubActive) {
                            SecondaryAction(text = "إنهاء الفوقية وإدراجها", onClick = { vm.stopMontageOverdub() })
                        }
                    }
                    RecordingStudio.RecState.PAUSED -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                "متوقف مؤقتًا — المدة تجمّدت",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        SecondaryAction(text = "▶ استئناف", onClick = { vm.resumeProRecording() })
                        SecondaryAction(text = "■ إنهاء وحفظ", onClick = { vm.stopProRecording() })
                    }
                }
                if (state != RecordingStudio.RecState.IDLE) {
                    Text(
                        formatMs(elapsed),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (monitor) {
                Text(
                    "سماع المراقبة يعمل — استخدم سماعات رأس كي لا يُلتقط صوت السماعات في التسجيل",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            message?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            // ================= التسجيل المجدول (البند 10) =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "التسجيل المجدول (${schedules.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                SecondaryAction(text = "+ جدولة تسجيل", onClick = { showScheduleDialog = true })
            }
            if (schedules.isEmpty()) {
                Text(
                    "لا توجد جدولة — أضف موعدًا يبدأ فيه التسجيل آليًا أثناء عمل التطبيق (مرة واحدة، يوميًا، أو أيام أسبوعية) بمدة توقفه آليًا أو بلا حد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    schedules.forEach { item ->
                        val now = System.currentTimeMillis()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${RecordingStudio.clockOf(item.startMinutes)} · " +
                                            (item.durationMinutes?.let { "${it} دقيقة" } ?: "بلا حد — يدوي") +
                                            " · ${RecordingStudio.repeatLabel(item)} · ${RecordingStudio.nextLabel(item, now)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SecondaryAction(
                                text = if (item.enabled) "تعطيل" else "تفعيل",
                                onClick = { vm.toggleRecorderSchedule(item.id) }
                            )
                            SecondaryAction(text = "حذف", onClick = { vm.removeRecorderSchedule(item.id) })
                        }
                    }
                }
            }
        }
    }

    if (showScheduleDialog) {
        ScheduleRecordingDialog(
            onDismiss = { showScheduleDialog = false },
            onConfirm = { item ->
                vm.addRecorderSchedule(item)
                showScheduleDialog = false
            }
        )
    }
}

/** رقاقة جودة قابلة للاختيار. */
@Composable
private fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}



/**
 * حوار إضافة جدولة تسجيل: عنوان وساعة (HH:mm) ومدة (أو بلا حد)
 * وتكرار (مرة واحدة / يوميًا / أسبوعيًا بأيام محددة) وتفعيل.
 */
@Composable
private fun ScheduleRecordingDialog(
    onDismiss: () -> Unit,
    onConfirm: (RecordingStudio.ScheduledItem) -> Unit
) {
    var title by remember { mutableStateOf("تسجيل الفاصل") }
    var timeText by remember { mutableStateOf("08:30") }
    var minutesText by remember { mutableStateOf("10") }
    var noLimit by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(RecordingStudio.REPEAT_ONCE) }
    var days by remember { mutableStateOf(setOf(1)) }
    var enabled by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }

    val dayNames = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("جدولة تسجيل جديد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("العنوان") },
                    modifier = Modifier.fillMaxWidth().observesTextEditing(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("الساعة (HH:mm)") },
                    modifier = Modifier.fillMaxWidth().observesTextEditing(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minutesText,
                        onValueChange = { minutesText = it },
                        label = { Text("المدة بالدقائق") },
                        enabled = !noLimit,
                        modifier = Modifier.weight(1f).observesTextEditing(),
                        singleLine = true
                    )
                    QualityChip(label = if (noLimit) "بلا حد ✓" else "بلا حد", selected = noLimit) {
                        noLimit = !noLimit
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityChip(label = "مرة واحدة", selected = repeat == RecordingStudio.REPEAT_ONCE) {
                        repeat = RecordingStudio.REPEAT_ONCE
                    }
                    QualityChip(label = "يوميًا", selected = repeat == RecordingStudio.REPEAT_DAILY) {
                        repeat = RecordingStudio.REPEAT_DAILY
                    }
                    QualityChip(label = "أسبوعيًا", selected = repeat == RecordingStudio.REPEAT_WEEKLY) {
                        repeat = RecordingStudio.REPEAT_WEEKLY
                    }
                }
                if (repeat == RecordingStudio.REPEAT_WEEKLY) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        dayNames.forEachIndexed { idx, name ->
                            val d = idx + 1
                            QualityChip(label = name, selected = d in days) {
                                days = if (d in days) days - d else days + d
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                    Text("مفعّل", style = MaterialTheme.typography.bodySmall)
                }
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val time = timeText.trim().split(":")
                val h = time.getOrNull(0)?.toIntOrNull()
                val m = time.getOrNull(1)?.toIntOrNull()
                val startMin = if (h != null && m != null && h in 0..23 && m in 0..59) h * 60 + m else null
                if (startMin == null) {
                    err = "الساعة يجب أن تكون بصيغة HH:mm (مثال 08:30)"
                    return@TextButton
                }
                val dur = if (noLimit) null else minutesText.trim().toIntOrNull()?.coerceIn(1, 600)
                if (!noLimit && dur == null) {
                    err = "المدة رقم بالدقائق بين 1 و600 — أو فعّل «بلا حد»"
                    return@TextButton
                }
                if (repeat == RecordingStudio.REPEAT_WEEKLY && days.isEmpty()) {
                    err = "اختر يومًا واحدًا على الأقل للتكرار الأسبوعي"
                    return@TextButton
                }
                onConfirm(
                    RecordingStudio.ScheduledItem(
                        id = "",
                        title = title,
                        startMinutes = startMin,
                        durationMinutes = dur,
                        repeat = repeat,
                        weeklyDays = days.toList(),
                        enabled = enabled
                    )
                )
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
