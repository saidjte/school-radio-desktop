package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.saidjte.schoolradio.desktop.Tab
import com.saidjte.schoolradio.desktop.audio.RecordingItem
import com.saidjte.schoolradio.desktop.data.Role
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * صفحة التسجيلات (v0.43.0): استوديو التسجيل الاحترافي في الأعلى
 * (لوحة RecordingStudioPanel بالبنود الأحد عشر) ثم مكتبة المجلد الحالي
 * بتشغيلها وإرسال كل تسجيل إلى مسار مونتاج عند رأس التشغيل —
 * والصفحة كلها تمرّ عموديًا (درس v0.35: الصفحة الجديدة لا تدفع
 * الأزرار خارج النافذة).
 */
@Composable
fun RecordingsTab(vm: AppViewModel) {
    val recordings by vm.recordings.collectAsState()
    val dir by vm.recordingsDir.collectAsState()
    val currentFile by vm.player.currentFile.collectAsState()
    val isPlaying by vm.player.isPlaying.collectAsState()
    val isPaused by vm.player.isPaused.collectAsState()

    var renameTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<RecordingItem?>(null) }
    var sendTarget by remember { mutableStateOf<RecordingItem?>(null) }

    // بوابة الأدوار: إدارة التسجيلات (تسمية/حذف) لمعلم فأعلى — التشغيل والاستماع للجميع
    val me by vm.currentUser.collectAsState()
    val canManage = me?.let { Role.canManage(it.role ?: Role.STUDENT) } ?: false

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            Text(
                "التسجيلات",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            SecondaryAction(text = "تسجيل جديد", onClick = { vm.selectTab(Tab.LIVE) })
            SecondaryAction(text = "تحديث", onClick = { vm.refreshRecordings() })
            SecondaryAction(
                text = "تغيير المجلد",
                onClick = {
                    val d = FilePickers.pickAudioFolder()
                    if (d != null) vm.setRecordingsDir(d)
                }
            )
        }
        Text(
            "المجلد: ${dir.absolutePath}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ================= استوديو التسجيل الاحترافي (v0.43.0) =================
        RecordingStudioPanel(vm)

        // ================= مكتبة التسجيلات =================
        Text(
            "مكتبة التسجيلات (${recordings.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (recordings.isEmpty()) {
            EmptyState(
                icon = AppIconKind.RECORDINGS,
                title = "لا توجد تسجيلات بعد",
                hint = "ابدأ تسجيلًا من استوديو التسجيل أعلاه أو من «البث المباشر» وستظهر تسجيلاتك هنا مع تشغيلها وإرسالها إلى المونتاج."
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recordings.forEach { item ->
                    val playingThis = currentFile == item.file && isPlaying && !isPaused
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (playingThis) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable(enabled = !playingThis) { vm.player.play(item.file) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${formatMs(item.durationMs)} · " +
                                        "%.1f م.ب".format(item.sizeBytes / 1048576.0) + " · " +
                                        dateFormat.format(Date(item.lastModified)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // توقيع المُسجِّل — يُختم آليًا لحظة إتمام التسجيل
                            if (item.recorderName != null) {
                                Text(
                                    "سجّلها: ${item.recorderName}" +
                                            (item.recorderRole?.let { " ($it)" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (playingThis) {
                            SecondaryAction(text = "إيقاف", onClick = { vm.player.stop() })
                        } else {
                            SecondaryAction(text = "تشغيل", onClick = { vm.player.play(item.file) })
                        }
                        SecondaryAction(text = "إلى المونتاج", onClick = { sendTarget = item })
                        if (canManage) {
                            SecondaryAction(
                                text = "إعادة تسمية",
                                onClick = {
                                    renameTarget = item
                                    renameText = item.displayName
                                }
                            )
                            SecondaryAction(text = "حذف", onClick = { deleteTarget = item })
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("إعادة تسمية التسجيل") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.observesTextEditing(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = renameTarget
                    if (t != null) vm.renameRecording(t, renameText)
                    renameTarget = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("إلغاء") }
            }
        )
    }

    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل تريد حذف «${deleteTarget?.displayName}» نهائيًا؟") },
            confirmButton = {
                TextButton(onClick = {
                    val t = deleteTarget
                    deleteTarget = null
                    if (t != null) vm.deleteRecording(t)
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }

    // حوار إرسال تسجيل إلى مسار مونتاج عند رأس التشغيل (التسجيل متعدد المسارات)
    if (sendTarget != null) {
        val choices = remember(sendTarget) { vm.montageTrackChoices() }
        AlertDialog(
            onDismissRequest = { sendTarget = null },
            title = { Text("إرسال إلى المونتاج") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "«${sendTarget?.displayName}» يُدرج عند رأس التشغيل (${formatMs(vm.montagePlayheadMs.value)}) على المسار:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (choices.isEmpty()) {
                        Text(
                            "لا توجد مسارات — افتح صفحة المونتاج أولًا",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            choices.forEach { (id, name) ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val f = sendTarget?.file
                                            sendTarget = null
                                            if (f != null) vm.sendRecordingToMontage(f, id)
                                        }
                                ) {
                                    Text(
                                        name,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sendTarget = null }) { Text("إلغاء") }
            }
        )
    }
}
