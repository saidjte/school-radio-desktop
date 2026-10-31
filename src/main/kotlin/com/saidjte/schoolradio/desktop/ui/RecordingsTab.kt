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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import com.saidjte.schoolradio.desktop.Tab
import com.saidjte.schoolradio.desktop.audio.RecordingItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** قسم التسجيلات: مكتبة المجلد الحالي مع تشغيل/إعادة تسمية/حذف. */
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

    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "التسجيلات",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
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

        if (recordings.isEmpty()) {
            EmptyState(
                icon = AppIconKind.RECORDINGS,
                title = "لا توجد تسجيلات بعد",
                hint = "ابدأ تسجيلًا من تبويب «البث المباشر» وستظهر تسجيلاتك هنا مع تشغيلها وإعادة تسميتها."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(recordings.size) { index ->
                    val item = recordings[index]
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
                        }
                        if (playingThis) {
                            SecondaryAction(text = "إيقاف", onClick = { vm.player.stop() })
                        } else {
                            SecondaryAction(text = "تشغيل", onClick = { vm.player.play(item.file) })
                        }
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

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("إعادة تسمية التسجيل") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
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
}
