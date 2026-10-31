package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import com.saidjte.schoolradio.desktop.AppViewModel

/** قسم الموسيقى: قائمة تشغيل + لوحة تحكم كاملة (بحث/صوت/تنقل). */
@Composable
fun MusicTab(vm: AppViewModel) {
    val player = vm.player
    val playlist by vm.playlist.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isPaused by player.isPaused.collectAsState()
    val hasTrack by player.hasTrack.collectAsState()
    val title by player.title.collectAsState()
    val positionMs by player.positionMs.collectAsState()
    val durationMs by player.durationMs.collectAsState()
    val volume by player.volume.collectAsState()

    var seekDragging by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "الموسيقى",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(
                    text = "أضف ملفات",
                    onClick = {
                        val files = FilePickers.pickAudioFiles()
                        if (files != null) vm.addFiles(files)
                    }
                )
                SecondaryAction(
                    text = "أضف مجلدًا",
                    onClick = {
                        val dir = FilePickers.pickAudioFolder()
                        if (dir != null) vm.addFolder(dir)
                    }
                )
                SecondaryAction(
                    text = "مسح القائمة",
                    onClick = { vm.clearPlaylist() },
                    enabled = playlist.isNotEmpty()
                )
            }

            if (playlist.isEmpty()) {
                Text(
                    "القائمة فارغة — أضف ملفات WAV أو MP3 للبدء.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(playlist) { index, track ->
                        val isCurrent = index == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { vm.playAt(index) }
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
                                Text(
                                    track.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatMs(track.durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isCurrent && isPlaying && !isPaused) {
                                Text(
                                    "قيد التشغيل",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.width(340.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "قيد التشغيل الآن",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                title ?: "لا شيء",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (hasTrack && durationMs > 0) {
                val pos = if (seekDragging) seekValue else positionMs.toFloat()
                Slider(
                    value = pos.coerceIn(0f, durationMs.toFloat()),
                    onValueChange = {
                        seekDragging = true
                        seekValue = it
                    },
                    onValueChangeFinished = {
                        player.seek(seekValue.toLong())
                        seekDragging = false
                    },
                    valueRange = 0f..durationMs.toFloat()
                )
            } else {
                Slider(value = 0f, onValueChange = { }, valueRange = 0f..1f, enabled = false)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    formatMs(if (seekDragging) seekValue.toLong() else positionMs),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    formatMs(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryAction(
                    text = "السابق",
                    onClick = { vm.previous() },
                    enabled = playlist.isNotEmpty()
                )
                if (isPlaying && !isPaused) {
                    PrimaryAction(text = "إيقاف مؤقت", onClick = { player.pause() })
                } else {
                    PrimaryAction(
                        text = "تشغيل",
                        onClick = { vm.togglePlay() },
                        enabled = playlist.isNotEmpty()
                    )
                }
                SecondaryAction(
                    text = "التالي",
                    onClick = { vm.next() },
                    enabled = playlist.isNotEmpty()
                )
                SecondaryAction(
                    text = "إيقاف",
                    onClick = { player.stop() },
                    enabled = hasTrack
                )
            }

            Text(
                "الصوت",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = volume,
                onValueChange = { player.setVolume(it) },
                valueRange = 0f..1f
            )
        }
    }
}
