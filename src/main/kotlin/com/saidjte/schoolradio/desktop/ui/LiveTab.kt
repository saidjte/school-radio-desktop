package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.MicState

/** قسم البث المباشر: جهاز الإدخال + الحالة + السماع المراقب + التسجيل + المقياس. */
@Composable
fun LiveTab(vm: AppViewModel) {
    val mic = vm.mic
    val state by mic.state.collectAsState()
    val level by mic.level.collectAsState()
    val monitoring by mic.monitoring.collectAsState()
    val recording by mic.recording.collectAsState()
    val recordMs by mic.recordMs.collectAsState()
    val liveMs by mic.liveMs.collectAsState()
    val error by mic.error.collectAsState()
    val deviceName by mic.deviceName.collectAsState()
    val activeFormat by mic.activeFormat.collectAsState()
    val recordFileName by mic.recordFileName.collectAsState()
    val recordDir by vm.recordingsDir.collectAsState()

    val devices = remember { AudioDevices.listMics() }
    var selectedDevice by remember { mutableStateOf(devices.firstOrNull()) }
    var devicesMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "البث المباشر للميكروفون",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "جهاز الإدخال",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                SecondaryAction(
                    text = selectedDevice?.name ?: "اختر الجهاز",
                    onClick = { devicesMenu = true }
                )
                DropdownMenu(expanded = devicesMenu, onDismissRequest = { devicesMenu = false }) {
                    devices.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.name) },
                            onClick = {
                                selectedDevice = d
                                devicesMenu = false
                            }
                        )
                    }
                }
            }

            if (state == MicState.LIVE) {
                PrimaryAction(
                    text = "إيقاف البث المباشر",
                    onClick = {
                        mic.stopRecording()
                        mic.stop()
                    },
                    danger = true
                )
            } else {
                PrimaryAction(
                    text = "بدء البث المباشر",
                    onClick = { mic.start(selectedDevice?.mixerInfo) }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("الحالة:", style = MaterialTheme.typography.titleSmall)
                if (state == MicState.LIVE) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(Color(0xFF66BB6A), CircleShape)
                    )
                    Text("مباشر — ${formatMs(liveMs)}", color = Color(0xFF81C784))
                } else {
                    Text("متوقف", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            deviceName?.let {
                Text("الجهاز: $it", style = MaterialTheme.typography.bodySmall)
            }
            activeFormat?.let {
                Text("الصيغة: $it", style = MaterialTheme.typography.bodySmall)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Switch(checked = monitoring, onCheckedChange = { mic.setMonitoring(it) })
                Column {
                    Text("السماع المباشر", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "استخدم سماعة رأس لتجنّب صدى الصوت",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (recording) {
                    PrimaryAction(
                        text = "إيقاف التسجيل",
                        onClick = { mic.stopRecording() },
                        danger = true
                    )
                } else {
                    SecondaryAction(
                        text = "تسجيل أثناء البث",
                        enabled = state == MicState.LIVE,
                        onClick = { mic.startRecording(vm.newRecordingTarget()) }
                    )
                }
                if (recording) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(Color(0xFFE53935), CircleShape)
                    )
                    Text("جارٍ التسجيل — ${formatMs(recordMs)}", color = Color(0xFFEF9A9A))
                }
            }
            recordFileName?.let {
                Text(
                    "الملف: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "تُحفَظ التسجيلات في: ${recordDir.absolutePath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            error?.let {
                Text(
                    "خطأ: $it",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Column(
            modifier = Modifier.width(180.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "مستوى الصوت",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LevelBar(level = level, modifier = Modifier.width(120.dp).fillMaxHeight())
        }
    }
}
