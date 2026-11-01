package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.saidjte.schoolradio.desktop.data.LibraryEntry

/** نمطا قسم الموسيقى: قائمة التشغيل الحية أو المكتبة الصوتية المصنفة. */
private enum class MusicMode(val label: String) {
    PLAYLIST("قائمة التشغيل"),
    LIBRARY("المكتبة الصوتية")
}

/**
 * قسم الموسيقى: نمطان في صفحة واحدة —
 * قائمة تشغيل دائمة (ترتيب/تكرار/حفظ بعد إعادة التشغيل) ومكتبة صوتية مصنفة
 * (تصنيفات افتراضية سبع + مخصصة، بحث، كشف المفقود وإعادة ربطه).
 * عمود التحكم في التشغيل مشترك بين النمطين.
 */
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
    val repeatAll by vm.repeatAll.collectAsState()

    var mode by remember { mutableStateOf(MusicMode.PLAYLIST) }

    var seekDragging by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ============ العمود الرئيسي: النمطان ============
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
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                // مفاتيح النمطين — كبسولتان واضحتان
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MusicMode.entries.forEach { m ->
                        val selected = mode == m
                        Text(
                            m.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { mode = m }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            when (mode) {
                MusicMode.PLAYLIST -> PlaylistPane(vm, playlist, currentIndex, isPlaying, isPaused)
                MusicMode.LIBRARY -> LibraryPane(vm)
            }
        }

        // ============ عمود التحكم المشترك ============
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
                Spacer(Modifier.weight(1f))
                Text(
                    formatMs(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // شبكة أزرار 2×2 — منع انضغاط أي زر وتكسّر نصه رأسيًا في العمود الضيق
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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

            // تكرار القائمة — نظير ميزة «تكرار» قوائم نسخة الهاتف
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "تكرار القائمة",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Switch(checked = repeatAll, onCheckedChange = { vm.toggleRepeat() })
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

// ================= نمط قائمة التشغيل =================

@Composable
private fun PlaylistPane(
    vm: AppViewModel,
    playlist: List<com.saidjte.schoolradio.desktop.Track>,
    currentIndex: Int,
    isPlaying: Boolean,
    isPaused: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
        EmptyState(
            icon = AppIconKind.MUSIC,
            title = "القائمة فارغة",
            hint = "أضف ملفات WAV أو MP3 من الأعلى — القائمة تُحفظ تلقائيًا وتبقى بعد إعادة التشغيل."
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
                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
                    // إعادة الترتيب — نظير ميزة «إعادة الترتيب» في نسخة الهاتف
                    SecondaryAction(
                        text = "أعلى",
                        enabled = index > 0,
                        onClick = { vm.moveTrack(index, -1) }
                    )
                    SecondaryAction(
                        text = "أسفل",
                        enabled = index < playlist.size - 1,
                        onClick = { vm.moveTrack(index, +1) }
                    )
                }
            }
        }
    }
}

// ================= نمط المكتبة الصوتية المصنفة =================

@Composable
private fun LibraryPane(vm: AppViewModel) {
    val categories by vm.libraryCategories.collectAsState()
    val entries by vm.libraryEntries.collectAsState()
    val query by vm.libraryQuery.collectAsState()
    val missingIds by vm.missingLibraryIds.collectAsState()
    val message by vm.lastLibraryMessage.collectAsState()

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importFiles by remember { mutableStateOf<List<java.io.File>?>(null) }
    var showNewCategory by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }
    var relinkTarget by remember { mutableStateOf<LibraryEntry?>(null) }

    val visible = remember(entries, query, selectedCategory) {
        vm.visibleLibraryEntries(selectedCategory)
    }

    // أزرار الاستيراد
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondaryAction(
            text = "استيراد ملفات",
            onClick = {
                val files = FilePickers.pickAudioFiles()
                if (!files.isNullOrEmpty()) {
                    importFiles = files
                    showImportDialog = true
                }
            }
        )
        SecondaryAction(
            text = "استيراد مجلد",
            onClick = {
                val dir = FilePickers.pickAudioFolder()
                if (dir != null) {
                    val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in setOf("wav", "mp3") }
                        ?.sortedBy { it.name }
                    if (!files.isNullOrEmpty()) {
                        importFiles = files
                        showImportDialog = true
                    }
                }
            }
        )
        SecondaryAction(
            text = "تصنيف جديد",
            onClick = {
                newCategoryText = ""
                showNewCategory = true
            }
        )
        Spacer(Modifier.weight(1f))
        Text(
            "${entries.size} ملفًا",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // البحث
    OutlinedTextField(
        value = query,
        onValueChange = { vm.setLibraryQuery(it) },
        placeholder = { Text("بحث في المكتبة (عنوان أو تصنيف)…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    // رقائق التصنيفات
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { selectedCategory = null },
            label = { Text("الكل") }
        )
        categories.forEach { c ->
            FilterChip(
                selected = selectedCategory == c,
                onClick = { selectedCategory = if (selectedCategory == c) null else c },
                label = { Text(c) }
            )
        }
    }

    message?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }

    if (visible.isEmpty()) {
        EmptyState(
            icon = AppIconKind.MUSIC,
            title = "المكتبة فارغة",
            hint = "استورد ملفاتك وسرّها في تصنيفات — المكتبة دائمة وتظهر بعد إعادة التشغيل، والملف المفقود يمكن إعادة ربطه بضغطة."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(visible, key = { _, e -> e.id }) { _, entry ->
                val isMissing = entry.id in missingIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isMissing) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            (entry.category ?: "") +
                                    if (isMissing) " · الملف مفقود من القرص" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isMissing) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isMissing) {
                        SecondaryAction(
                            text = "إعادة ربط",
                            onClick = {
                                val f = FilePickers.pickSingleAudioFile()
                                if (f != null) vm.relinkLibraryEntry(entry.id, f)
                            }
                        )
                    } else {
                        SecondaryAction(
                            text = "تشغيل",
                            onClick = {
                                val f = entry.path?.let { p -> java.io.File(p) }
                                if (f != null && f.isFile) vm.player.play(f)
                            }
                        )
                        SecondaryAction(
                            text = "إلى القائمة",
                            onClick = { vm.addLibraryToPlaylist(entry) }
                        )
                        SecondaryAction(
                            text = "حذف",
                            onClick = { vm.removeLibraryEntry(entry.id) }
                        )
                    }
                }
            }
        }
    }

    // حوار اختيار تصنيف الاستيراد
    if (showImportDialog && importFiles != null) {
        ImportCategoryDialog(
            categories = categories,
            onConfirm = { cat ->
                vm.importToLibrary(importFiles ?: emptyList(), cat)
                importFiles = null
                showImportDialog = false
            },
            onDismiss = {
                importFiles = null
                showImportDialog = false
            }
        )
    }

    // حوار تصنيف جديد
    if (showNewCategory) {
        AlertDialog(
            onDismissRequest = { showNewCategory = false },
            title = { Text("تصنيف جديد") },
            text = {
                OutlinedTextField(
                    value = newCategoryText,
                    onValueChange = { newCategoryText = it },
                    placeholder = { Text("اسم التصنيف") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (vm.addLibraryCategory(newCategoryText)) showNewCategory = false
                    },
                    enabled = newCategoryText.isNotBlank()
                ) { Text("إضافة") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategory = false }) { Text("إلغاء") }
            }
        )
    }

    // حوار إعادة الربط اليدوي (احتياط لو أُغلق منتقي الملف)
    if (relinkTarget != null) {
        AlertDialog(
            onDismissRequest = { relinkTarget = null },
            title = { Text("إعادة ربط الملف") },
            text = { Text("اختر الملف البديل من القرص لعنوان «${relinkTarget?.title}».") },
            confirmButton = {
                TextButton(onClick = {
                    val f = FilePickers.pickSingleAudioFile()
                    val t = relinkTarget
                    if (f != null && t != null) vm.relinkLibraryEntry(t.id, f)
                    relinkTarget = null
                }) { Text("اختيار") }
            },
            dismissButton = {
                TextButton(onClick = { relinkTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

/** حوار اختيار التصنيف عند الاستيراد — التصنيفات الافتراضية السبع + المخصصة. */
@Composable
private fun ImportCategoryDialog(
    categories: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember { mutableStateOf(categories.firstOrNull() ?: "فقرات متنوعة") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تصنيف الملفات المستوردة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "اختر تصنيفًا (${categories.size} تصنيفًا):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                categories.forEach { c ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (chosen == c) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { chosen = c }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            c,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (chosen == c) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }) { Text("استيراد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
