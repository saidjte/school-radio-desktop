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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableLongStateOf
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
 * صفحة الموسيقى — مشغل احترافي بنمط استوديو:
 * عمود المشغل (يمين): بطاقة «قيد التشغيل الآن» بعنوان كبير وشارة تصنيف وموضع في
 * القائمة، بحث زمني بالسحب، تحكم نقل كامل، تكرار، ومستوى صوت مع كتم جلسي.
 * عمود المحتوى: قوائم تشغيل مسماة (إنشاء/تسمية/حذف/تبديل) بقائمة حية قابلة
 * للترتيب والإزالة، ومكتبة صوتية مصنفة بأرقام على رقائق التصنيف ونقل مقطع
 * بين التصنيفات — مع البحث وكشف المفقود وإعادة ربطه.
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
    val repeatMode by vm.repeatMode.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val playerError by player.error.collectAsState()
    val playingFromLibrary by vm.playingFromLibrary.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val activeId by vm.activePlaylistId.collectAsState()
    val activeName = playlists.firstOrNull { it.id == activeId }?.name ?: ""

    var mode by remember { mutableStateOf(MusicMode.PLAYLIST) }

    // زحف شريط التقدم (StudioSeekBar v0.23.0): معاينة حية وسعي واحد عند الإفلات
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }

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
                MusicMode.PLAYLIST -> PlaylistPane(vm)
                MusicMode.LIBRARY -> LibraryPane(vm)
            }
        }

        // ============ عمود المشغل: قيد التشغيل الآن ============
        Column(
            modifier = Modifier.width(360.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // شارة القائمة الجارية
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "قيد التشغيل الآن",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (activeName.isNotEmpty()) {
                    Text(
                        shortenName(activeName, 18),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                title ?: "لا شيء",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // شارات الحالة: بث من المكتبة + قيد التشغيل
            if (playingFromLibrary) {
                Text(
                    "بث مباشر من المكتبة — القائمة لم تتغيّر",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // شارة التصنيف + موضع المقطع في القائمة
            val currentTrack = playlist.getOrNull(currentIndex)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                currentTrack?.category?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                if (playlist.isNotEmpty()) {
                    Text(
                        "المقطع ${currentIndex + 1} من ${playlist.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isPlaying && !isPaused) {
                    Text(
                        "قيد التشغيل",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // سطر «التالي» كما في المشغلات الاحترافية
            if (playlist.isNotEmpty()) {
                val nextTitle = if (shuffle) "ترتيب عشوائي" else {
                    playlist.getOrNull(currentIndex + 1)?.title
                        ?: (if (repeatMode == com.saidjte.schoolradio.desktop.data.RepeatMode.ALL)
                            playlist.first().title else null)
                }
                Text(
                    "التالي: ${nextTitle ?: "نهاية القائمة"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // شريط الخطأ — لا فشل صامت أبدًا
            playerError?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "إخفاء",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { player.clearError() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // شريط التقدم: النقر ينقل فوريًا والسحب يعاين ثم يسعّي عند الإفلات
            StudioSeekBar(
                positionMs = positionMs,
                durationMs = durationMs,
                enabled = hasTrack && durationMs > 0,
                onSeek = { player.seek(it) },
                onScrub = { ms ->
                    if (ms == null) scrubbing = false
                    else { scrubbing = true; scrubMs = ms }
                }
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                val shownPos = if (scrubbing) scrubMs else positionMs
                Text(
                    formatMs(shownPos),
                    style = MaterialTheme.typography.bodySmall.numeric()
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "-" + formatMs((durationMs - shownPos).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.bodySmall.numeric(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // التحكم بالنقل: صف رئيس ثم صف ثانٍ للإيقاف والتكرار
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
                if (isPlaying && !isPaused) {
                    PrimaryAction(
                        text = "إيقاف مؤقت",
                        onClick = { player.pause() },
                        modifier = Modifier.weight(1.4f)
                    )
                } else {
                    PrimaryAction(
                        text = "تشغيل",
                        onClick = { vm.togglePlay() },
                        enabled = playlist.isNotEmpty(),
                        modifier = Modifier.weight(1.4f)
                    )
                }
                SecondaryAction(
                    text = "التالي",
                    onClick = { vm.next() },
                    enabled = playlist.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                SecondaryAction(
                    text = "إيقاف",
                    onClick = { player.stop() },
                    enabled = hasTrack,
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(
                    text = if (shuffle) "عشوائي ✓" else "عشوائي",
                    onClick = { vm.toggleShuffle() },
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(
                    text = "تكرار: ${repeatMode.label}",
                    onClick = { vm.cycleRepeat() },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                "الصوت",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val musicMuted by vm.musicMuted.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Slider(
                    value = volume,
                    onValueChange = { player.setVolume(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                MuteChip(muted = musicMuted, onClick = { vm.setMusicMuted(!musicMuted) })
            }
        }
    }
}

// ================= نمط قائمة التشغيل: قوائم مسماة + قائمة حية =================

@Composable
private fun PlaylistPane(vm: AppViewModel) {
    val playlist by vm.playlist.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val isPlaying by vm.player.isPlaying.collectAsState()
    val isPaused by vm.player.isPaused.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val activeId by vm.activePlaylistId.collectAsState()
    val activeName = playlists.firstOrNull { it.id == activeId }?.name ?: ""

    var playlistsMenu by remember { mutableStateOf(false) }
    var showNewPlaylist by remember { mutableStateOf(false) }
    var showRenamePlaylist by remember { mutableStateOf(false) }
    var showDeletePlaylist by remember { mutableStateOf(false) }
    var nameText by remember { mutableStateOf("") }
    var queueQuery by remember { mutableStateOf("") }

    // قائمة حية مُصفّاة بالبحث — البحث لا يغيّر القائمة نفسها
    val visible = remember(playlist, queueQuery) {
        val q = queueQuery.trim()
        if (q.isEmpty()) playlist
        else playlist.filter {
            it.title.contains(q, ignoreCase = true) ||
                    (it.category ?: "").contains(q, ignoreCase = true)
        }
    }
    val totalMs = playlist.sumOf { it.durationMs }

    // شريط إدارة القوائم المسماة
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box {
            SecondaryAction(text = "القوائم ▾", onClick = { playlistsMenu = true })
            DropdownMenu(expanded = playlistsMenu, onDismissRequest = { playlistsMenu = false }) {
                playlists.forEach { p ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                (if (p.id == activeId) "● " else "") + "${p.name} (${p.paths.size})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            vm.selectPlaylist(p.id)
                            playlistsMenu = false
                        }
                    )
                }
            }
        }
        SecondaryAction(
            text = "قائمة جديدة",
            onClick = {
                nameText = ""
                showNewPlaylist = true
            }
        )
        SecondaryAction(
            text = "تسمية",
            onClick = {
                nameText = activeName
                showRenamePlaylist = true
            }
        )
        SecondaryAction(text = "حذف", onClick = { showDeletePlaylist = true })
        Spacer(Modifier.weight(1f))
        Text(
            shortenName(activeName, 16) + " · ${playlist.size} مقطعًا" +
                    if (totalMs > 0) " · ${formatMs(totalMs)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // بحث داخل القائمة الحية
    if (playlist.size > 3) {
        OutlinedTextField(
            value = queueQuery,
            onValueChange = { queueQuery = it },
            placeholder = { Text("بحث في مقاطع القائمة (عنوان أو تصنيف)…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().observesTextEditing()
        )
    }

    // شريط تعبئة القائمة
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
            title = "القائمة «${activeName}» فارغة",
            hint = "أضف ملفات MP3 أو WAV أو OGG أو FLAC من الأعلى، أو انقل مقاطعًا من المكتبة — القائمة تُحفظ باسمها وتبقى بعد إعادة التشغيل."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(visible, key = { _, t -> t.file.path }) { rowPosition, track ->
                val index = playlist.indexOf(track)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                formatMs(track.durationMs),
                                style = MaterialTheme.typography.bodySmall.numeric(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            track.category?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (isCurrent && isPlaying && !isPaused) {
                        Text(
                            "قيد التشغيل",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (visible.size != playlist.size) {
                        // أثناء البحث: العرض فقط — الترتيب والإزالة من العرض الكامل
                        Text(
                            "#${index + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                    // إعادة الترتيب والإزالة — القائمة الحية قابلة للتشكيل أثناء العمل
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
                    SecondaryAction(
                        text = "إزالة",
                        onClick = { vm.removeTrackAt(index) }
                    )
                }
            }
        }
    }

    // حوار قائمة جديدة
    if (showNewPlaylist) {
        NameDialog(
            title = "قائمة تشغيل جديدة",
            placeholder = "اسم القائمة — مثل: فقرة الصباح",
            value = nameText,
            onValueChange = { nameText = it },
            confirmLabel = "إنشاء",
            onConfirm = {
                if (vm.createPlaylist(nameText)) showNewPlaylist = false
            },
            onDismiss = { showNewPlaylist = false }
        )
    }
    // حوار تسمية القائمة الجارية
    if (showRenamePlaylist) {
        NameDialog(
            title = "تسمية القائمة",
            placeholder = "الاسم الجديد",
            value = nameText,
            onValueChange = { nameText = it },
            confirmLabel = "حفظ",
            onConfirm = {
                if (vm.renamePlaylist(activeId, nameText)) showRenamePlaylist = false
            },
            onDismiss = { showRenamePlaylist = false }
        )
    }
    // حوار حذف القائمة الجارية
    if (showDeletePlaylist) {
        AlertDialog(
            onDismissRequest = { showDeletePlaylist = false },
            title = { Text("حذف القائمة") },
            text = { Text("حذف قائمة «$activeName» (${playlist.size} مقطعًا)؟ مقاطع المكتبة نفسها لا تُحذف.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlaylist(activeId)
                    showDeletePlaylist = false
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeletePlaylist = false }) { Text("إلغاء") }
            }
        )
    }
}

// ================= نمط المكتبة الصوتية المصنفة =================

@Composable
private fun LibraryPane(vm: AppViewModel) {
    val categories by vm.libraryCategories.collectAsState()
    val entries by vm.libraryEntries.collectAsState()
    val counts by vm.categoryCounts.collectAsState()
    val query by vm.libraryQuery.collectAsState()
    val missingIds by vm.missingLibraryIds.collectAsState()
    val message by vm.lastLibraryMessage.collectAsState()

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importFiles by remember { mutableStateOf<List<java.io.File>?>(null) }
    var showYouTubeDialog by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var newCategoryText by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<LibraryEntry?>(null) }
    var moveMenu by remember { mutableStateOf(false) }

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
                    val files = dir.listFiles { f ->
                        f.isFile && f.extension.lowercase() in com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS
                    }?.sortedBy { it.name }
                    if (!files.isNullOrEmpty()) {
                        importFiles = files
                        showImportDialog = true
                    }
                }
            }
        )
        PrimaryAction(
            text = "استيراد من يوتيوب",
            onClick = { showYouTubeDialog = true }
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
        modifier = Modifier.fillMaxWidth().observesTextEditing()
    )

    // رقائق التصنيفات بعدد مقاطع كل تصنيف
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedCategory == null,
            onClick = { selectedCategory = null },
            label = { Text("الكل (${entries.size})") }
        )
        categories.forEach { c ->
            FilterChip(
                selected = selectedCategory == c,
                onClick = { selectedCategory = if (selectedCategory == c) null else c },
                label = { Text("$c (${counts[c] ?: 0})") }
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
                            onClick = { vm.playNow(entry) }
                        )
                        SecondaryAction(
                            text = "التالي ▸",
                            onClick = { vm.playNextFromLibrary(entry) }
                        )
                        SecondaryAction(
                            text = "إلى القائمة",
                            onClick = { vm.addLibraryToPlaylist(entry) }
                        )
                        // نقل المقطع إلى تصنيف آخر
                        Box {
                            SecondaryAction(
                                text = "نقل إلى ▾",
                                onClick = {
                                    moveTarget = entry
                                    moveMenu = true
                                }
                            )
                            DropdownMenu(
                                expanded = moveMenu && moveTarget?.id == entry.id,
                                onDismissRequest = { moveMenu = false }
                            ) {
                                categories.filter { it != entry.category }.forEach { c ->
                                    DropdownMenuItem(
                                        text = { Text(c, maxLines = 1) },
                                        onClick = {
                                            vm.moveLibraryEntry(entry.id, c)
                                            moveMenu = false
                                            moveTarget = null
                                        }
                                    )
                                }
                            }
                        }
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

    // حوار استيراد من اليوتيوب (v0.25.0): بحث/رابط + تحميل حي + تحويل محلي
    if (showYouTubeDialog) {
        YouTubeImportDialog(
            vm = vm,
            onDismiss = { showYouTubeDialog = false }
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
                    modifier = Modifier.observesTextEditing(),
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

/** حوار إدخال اسم عام (قائمة جديدة / تسمية). */
@Composable
private fun NameDialog(
    title: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.observesTextEditing(),
                placeholder = { Text(placeholder) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
