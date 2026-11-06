package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import java.io.File

/**
 * صفحة المونتاج (v0.33.0 «أضف صفحة المونتاج»): تجميع المقاطع (تسجيلات وملفات)
 * في قائمة مرتّبة، قص بداية كل مقطع ونهايته، مستوى صوت لكل مقطع، تلاشٍ داخل
 * وخارج، فاصل موحّد بين المقاطع — ثم معاينة في المشغل نفسه أو تصدير ملف
 * WAV واحد 44.1k ستيريو يظهر في التسجيلات ويُضاف إلى قائمة البث.
 * كل شيء دون اتصال: التصيير فكّ محلي وكتابة محلية بلا جهاز صوت ولا شبكة.
 */
@Composable
fun MontagePage(vm: AppViewModel) {
    val project by vm.montageProject.collectAsState()
    val progress by vm.montageRenderProgress.collectAsState()
    val lastExport by vm.lastMontageExport.collectAsState()
    val message by vm.lastLibraryMessage.collectAsState()
    val recordings by vm.recordings.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MontageClip?>(null) }
    val rendering = progress != null
    val totalMs = remember(project) { MontageLogic.totalDurationMs(project.clips, project.gapMs) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "المونتاج",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                "المدة الكلية: ${formatMs(totalMs)} · ${project.clips.size} مقاطع",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            SecondaryAction(
                text = "إضافة ملفات",
                enabled = !rendering,
                onClick = {
                    val fs = FilePickers.pickAudioFiles()
                    if (!fs.isNullOrEmpty()) vm.addMontageFiles(fs)
                }
            )
            SecondaryAction(
                text = "من التسجيلات",
                enabled = !rendering,
                onClick = {
                    vm.refreshRecordings()
                    showPicker = true
                }
            )
            SecondaryAction(
                text = "تفريغ",
                enabled = !rendering && project.clips.isNotEmpty(),
                onClick = { vm.clearMontage() }
            )
        }
        Text(
            "رتّب المقاطع واقصص بدايتها ونهايتها وضع لكل مقطع مستوى وتلاشين وفاصلًا بين المقاطع، ثم صدّر المونتاج كله ملفًا واحدًا جاهزًا للبث — كل ذلك دون اتصال.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // الفاصل الموحّد بين المقاطع — صمت يُدرج آليًا عند التصيير
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "الفاصل بين المقاطع: ${project.gapMs} مللي ثانية",
                style = MaterialTheme.typography.bodyMedium
            )
            SecondaryAction(
                text = "− 250",
                enabled = !rendering && project.gapMs > 0,
                onClick = { vm.setMontageGap(project.gapMs - 250) }
            )
            SecondaryAction(
                text = "+ 250",
                enabled = !rendering && project.gapMs < MontageLogic.GAP_MAX_MS,
                onClick = { vm.setMontageGap(project.gapMs + 250) }
            )
        }

        if (project.clips.isEmpty()) {
            EmptyState(
                icon = AppIconKind.MONTAGE,
                title = "المونتاج فارغ",
                hint = "أضف مقاطع من تسجيلاتك أو من ملفاتك، ورتّبها وقصّها، ثم صدّرها ملفًا واحدًا جاهزًا للفاصل."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                itemsIndexed(project.clips) { i, clip ->
                    MontageClipRow(
                        index = i,
                        clip = clip,
                        count = project.clips.size,
                        enabled = !rendering,
                        onUp = { vm.moveMontageClip(clip.id, +1) },
                        onDown = { vm.moveMontageClip(clip.id, -1) },
                        onEdit = { editTarget = clip },
                        onRemove = { vm.removeMontageClip(clip.id) }
                    )
                }
            }
        }

        // التذييل: المعاينة والتصدير والتقدم والإلغاء
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryAction(
                text = "معاينة",
                enabled = !rendering && project.clips.any { File(it.path).isFile },
                onClick = { vm.renderMontage(preview = true) }
            )
            PrimaryAction(
                text = "تصدير ملف واحد",
                enabled = !rendering && project.clips.any { File(it.path).isFile },
                onClick = { vm.renderMontage(preview = false) }
            )
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress ?: 0f },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "جاري التصيير…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecondaryAction(text = "إلغاء", onClick = { vm.cancelMontageRender() })
            }
            lastExport?.let { f ->
                if (f.isFile && !rendering) {
                    SecondaryAction(
                        text = "أضف المصدَّر إلى قائمة البث",
                        onClick = { vm.addMontageExportToPlaylist(f) }
                    )
                }
            }
        }
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    // حوار الاختيار من التسجيلات: تحديد متعدد بلمسات متكررة
    if (showPicker) {
        val selected = remember { mutableStateOf(setOf<File>()) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("إضافة من التسجيلات") },
            text = {
                if (recordings.isEmpty()) {
                    Text("لا توجد تسجيلات في المجلد الحالي بعد.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        itemsIndexed(recordings) { _, item ->
                            val on = item.file in selected.value
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable {
                                        selected.value =
                                            if (on) selected.value - item.file
                                            else selected.value + item.file
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (if (on) "✓ " else "") + item.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatMs(item.durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addMontageFiles(selected.value.toList())
                    showPicker = false
                }) { Text("إضافة المحدد") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("إلغاء") }
            }
        )
    }

    // حوار تحرير المقطع: القص والمستوى والتلاشين
    editTarget?.let { clip ->
        MontageClipEditDialog(
            clip = clip,
            onSave = { s, e, vol, fi, fo ->
                vm.updateMontageClip(clip.id) {
                    it.copy(startMs = s, endMs = e, volumePercent = vol, fadeInMs = fi, fadeOutMs = fo)
                }
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }
}

/** صف مقطع في قائمة المونتاج: الترتيب والقص والمستوى والتلاشين وأزرار الإدارة. */
@Composable
private fun MontageClipRow(
    index: Int,
    clip: MontageClip,
    count: Int,
    enabled: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val missing = !File(clip.path).isFile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (missing) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.titleMedium.numeric(),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                clip.title + if (missing) " — الملف مفقود" else "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dur = MontageLogic.clipDurationMs(clip)
            val fades = buildString {
                if (clip.fadeInMs > 0) append(" · تلاشٍ داخل ${clip.fadeInMs}ms")
                if (clip.fadeOutMs > 0) append(" · تلاشٍ خارج ${clip.fadeOutMs}ms")
            }
            Text(
                "القص: ${formatMs(clip.startMs)} ← ${formatMs(clip.endMs)} (${formatMs(dur)}) · الصوت ${clip.volumePercent}٪$fades",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SecondaryAction(text = "أعلى", enabled = enabled && index > 0, onClick = onUp)
        SecondaryAction(text = "أسفل", enabled = enabled && index < count - 1, onClick = onDown)
        SecondaryAction(text = "تحرير", enabled = enabled, onClick = onEdit)
        SecondaryAction(text = "حذف", enabled = enabled, onClick = onRemove)
    }
}

/**
 * حوار تحرير مقطع المونتاج: نافذة القص بالملي ثانية مع أزرار نتح سريعة،
 * والمستوى والتلاشين — مع مرجع مدة الملف كاملة أمام عين المذيع.
 */
@Composable
private fun MontageClipEditDialog(
    clip: MontageClip,
    onSave: (Long, Long, Int, Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var startText by remember { mutableStateOf(clip.startMs.toString()) }
    var endText by remember { mutableStateOf(clip.endMs.toString()) }
    var volText by remember { mutableStateOf(clip.volumePercent.toString()) }
    var finText by remember { mutableStateOf(clip.fadeInMs.toString()) }
    var foutText by remember { mutableStateOf(clip.fadeOutMs.toString()) }

    fun fieldMs(text: String): Long = text.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحرير المقطع: ${clip.title.take(40)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "مدة الملف كاملة: ${formatMs(clip.durationMs)} — القص منها نافذة تظهر في المونتاج.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LabeledNumber("بداية (مللي ثانية)", startText, { startText = it }, Modifier.weight(1f))
                    SecondaryAction("− 500", onClick = { startText = (fieldMs(startText) - 500).coerceAtLeast(0).toString() })
                    SecondaryAction("+ 500", onClick = { startText = (fieldMs(startText) + 500).toString() })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LabeledNumber("نهاية (مللي ثانية)", endText, { endText = it }, Modifier.weight(1f))
                    SecondaryAction("− 500", onClick = { endText = (fieldMs(endText) - 500).coerceAtLeast(0).toString() })
                    SecondaryAction("+ 500", onClick = { endText = (fieldMs(endText) + 500).toString() })
                }
                LabeledNumber("الصوت (٪، 100 = كما هو)", volText, { volText = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledNumber("تلاشٍ داخل (ms)", finText, { finText = it }, Modifier.weight(1f))
                    LabeledNumber("تلاشٍ خارج (ms)", foutText, { foutText = it }, Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val (s, e) = MontageLogic.clampWindow(fieldMs(startText), fieldMs(endText), clip.durationMs)
                onSave(
                    s, e,
                    MontageLogic.clampVolume(volText.trim().toIntOrNull() ?: MontageLogic.VOLUME_DEFAULT),
                    MontageLogic.clampFade(fieldMs(finText)),
                    MontageLogic.clampFade(fieldMs(foutText))
                )
            }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/** حقل رقمي بعنوان صغير — إدخال حر تحرسه حبوس الحفظ. */
@Composable
private fun LabeledNumber(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(9)) },
            modifier = Modifier.fillMaxWidth().observesTextEditing(),
            singleLine = true
        )
    }
}
