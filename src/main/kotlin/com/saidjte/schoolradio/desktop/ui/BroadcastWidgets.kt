package com.saidjte.schoolradio.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.LibraryEntry
import com.saidjte.schoolradio.desktop.data.NamedPlaylist
import com.saidjte.schoolradio.desktop.data.RepeatMode as PlaylistRepeatMode
import com.saidjte.schoolradio.desktop.Track
import com.saidjte.schoolradio.desktop.data.MeterLogic
import com.saidjte.schoolradio.desktop.data.MeterZone
import com.saidjte.schoolradio.desktop.data.MixerLogic
import java.io.File

// ================= مقياس البث المقطعي =================

/**
 * مقياس مستوى بطابع غرفة التحكم: 24 مقطع LED بثلاث مناطق (أخضر/كهرماني/أحمر)
 * مع مؤشر ذروة يرتفع فورًا وينحسر ببطء. الامتلاء من اليمين إلى اليسار
 * اتساقًا مع اتجاه القراءة العربية. كل القرارات من MeterLogic والرسم فقط هنا.
 */
@Composable
fun BroadcastMeter(level: Float, modifier: Modifier = Modifier) {
    val sc = LocalStudioColors.current
    var peak by remember { mutableStateOf(0f) }
    LaunchedEffect(level) { peak = MeterLogic.peakIndex(peak, level) }

    val lit = MeterLogic.litSegments(level)
    val peakSeg = MeterLogic.peakSegment(peak)

    Canvas(modifier = modifier) {
        val n = MeterLogic.SEGMENTS
        val gap = 2.dp.toPx()
        val segW = (size.width - gap * (n - 1)) / n
        val radius = CornerRadius(minOf(segW, size.height) / 2.5f)
        for (i in 0 until n) {
            val zone = when (MeterLogic.zoneOf(i)) {
                MeterZone.GREEN -> sc.meterGreen
                MeterZone.AMBER -> sc.meterAmber
                MeterZone.RED -> sc.meterRed
            }
            val color = when {
                i == peakSeg -> sc.meterPeak
                i < lit -> zone
                else -> zone.copy(alpha = 0.14f)
            }
            // الامتلاء من اليمين (بداية القراءة العربية)
            val xRight = size.width - i * (segW + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(xRight - segW, 0f),
                size = Size(segW, size.height),
                cornerRadius = radius
            )
        }
    }
}

// ================= مؤشرات الهواء =================

/** نقطة نابضة للمؤشرات الحية. */
@Composable
fun PulsingDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseAlpha"
    )
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape)
    )
}

/** مؤشر الهواء الموحد في الأشرطة العلوية: «على الهواء» بنبض / «خارج البث» ساكن. */
@Composable
fun OnAirPill(onAir: Boolean, modifier: Modifier = Modifier) {
    val sc = LocalStudioColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (onAir) sc.onAir else MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onAir) {
            PulsingDot(color = sc.onAirContent, size = 10.dp)
        } else {
            Dot(color = MaterialTheme.colorScheme.onSurfaceVariant, size = 10.dp)
        }
        Text(
            if (onAir) "على الهواء" else "خارج البث",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (onAir) sc.onAirContent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================= حالة الفراغ الموحدة =================

/** حالة فراغ موحدة: أيقونة + عنوان + توجيه عملي بدل نص خام وحيد. */
@Composable
fun EmptyState(
    icon: AppIconKind,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppIcon(
            kind = icon,
            size = iconSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ================= بطاقة الميكروفون المشتركة =================

/**
 * بطاقة الميكروفون المشتركة بين الاستوديو المدمج والبث المباشر: اختيار الجهاز
 * والمخرج، مقياس مقطعي بذروة، سماع مراقب، وتسجيل — نسخة واحدة يحين مكان نسختين.
 */
@Composable
fun BroadcastMicCard(vm: AppViewModel) {
    val mic = vm.mic
    val state by mic.state.collectAsState()
    val level by mic.level.collectAsState()
    val monitoring by mic.monitoring.collectAsState()
    val recording by mic.recording.collectAsState()
    val recordMs by mic.recordMs.collectAsState()
    val liveMs by mic.liveMs.collectAsState()
    val error by mic.error.collectAsState()
    val deviceName by mic.deviceName.collectAsState()
    val sc = LocalStudioColors.current

    var devices by remember { mutableStateOf(AudioDevices.listMics()) }
    var outputs by remember { mutableStateOf(AudioDevices.listOutputs()) }
    var selectedDevice by remember { mutableStateOf(devices.firstOrNull()) }
    var selectedOut by remember { mutableStateOf(outputs.firstOrNull()) }
    var devicesMenu by remember { mutableStateOf(false) }
    var outMenu by remember { mutableStateOf(false) }

    /** إعادة تعداد أجهزة الإدخال والإخراج مع الحفاظ على الاختيار إن كان لا يزال موجودًا. */
    fun refreshDeviceLists() {
        val prevIn = selectedDevice
        val prevOut = selectedOut
        devices = AudioDevices.listMics()
        outputs = AudioDevices.listOutputs()
        selectedDevice = devices.firstOrNull { it.name == prevIn?.name } ?: devices.firstOrNull()
        selectedOut = outputs.firstOrNull { it.name == prevOut?.name } ?: outputs.firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "الميكروفون",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box {
                SecondaryAction(
                    text = "الجهاز…",
                    onClick = { refreshDeviceLists(); devicesMenu = true }
                )
                DropdownMenu(expanded = devicesMenu, onDismissRequest = { devicesMenu = false }) {
                    devices.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                selectedDevice = d
                                devicesMenu = false
                            }
                        )
                    }
                }
            }
        }

        // محدّد المخرج (السماعات): يوجّه الموسيقى والتسجيلات والسماع المراقب
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "المخرج",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Box {
                SecondaryAction(
                    text = selectedOut?.let { shortenName(it.name, 14) } ?: "المخرج…",
                    onClick = { refreshDeviceLists(); outMenu = true }
                )
                DropdownMenu(expanded = outMenu, onDismissRequest = { outMenu = false }) {
                    outputs.forEach { d ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (d.mixerInfo == selectedOut?.mixerInfo && d.name == selectedOut?.name) "● " else "") + d.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                selectedOut = d
                                vm.player.setOutput(d.mixerInfo)
                                mic.setMonitorOutput(d.mixerInfo)
                                outMenu = false
                            }
                        )
                    }
                }
            }
            SecondaryAction(text = "تحديث", onClick = { refreshDeviceLists() })
        }

        if (state == MicState.LIVE) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryAction(
                    text = "إيقاف البث",
                    danger = true,
                    onClick = {
                        mic.stopRecording()
                        mic.stop()
                    },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    formatMs(liveMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = sc.liveTimer,
                    fontWeight = FontWeight.Bold
                )
            }
            BroadcastMeter(level = level, modifier = Modifier.fillMaxWidth().height(16.dp))
        } else {
            PrimaryAction(
                text = "بدء البث المباشر",
                onClick = { mic.start(selectedDevice?.mixerInfo, selectedOut?.mixerInfo) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = monitoring, onCheckedChange = { mic.setMonitoring(it) })
            Text("سماع مراقب", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            if (recording) {
                SecondaryAction(text = "إيقاف التسجيل", onClick = { mic.stopRecording() })
                PulsingDot(color = sc.recDot, size = 10.dp)
                Text(
                    formatMs(recordMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = sc.recordingAccent
                )
            } else {
                SecondaryAction(
                    text = "تسجيل",
                    enabled = state == MicState.LIVE,
                    onClick = { mic.startRecording(vm.newRecordingTarget()) }
                )
            }
        }

        // فيدر قناة الميكروفون + كتم جلسي (وحدة المزج)
        val micMuted by vm.micMuted.collectAsState()
        val micFader by vm.mixer.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "فيدر",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${MixerLogic.faderPercent(micFader.micFader)}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(42.dp)
            )
            Slider(
                value = micFader.micFader,
                onValueChange = { vm.setMicFader(it) },
                valueRange = 0f..MixerLogic.FADER_MAX,
                modifier = Modifier.weight(1f)
            )
            MuteChip(muted = micMuted, onClick = { vm.setMicMuted(!micMuted) })
        }
        deviceName?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        error?.let {
            Text(
                "خطأ: $it",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ================= بطاقة الموسيقى المشتركة (مشغل الهواء) =================

/** مصدر اختيار المقطع في بطاقة الموسيقى: قائمة التشغيل الجارية أو المكتبة المصنفة. */
private enum class MusicSource(val label: String) {
    QUEUE("القائمة"),
    LIBRARY("المكتبة")
}

/**
 * مشغل الهواء المشترك بين الاستوديو المدمج والبث المباشر:
 * اختيار القائمة وتشغيلها، اختيار مقاطع من المكتبة المصنفة والبث فورًا،
 * تحكم نقل كامل، فيدر وكتم — كل ما يلزم المذيع أثناء الهواء في بطاقة واحدة.
 *
 * وضعان للعرض:
 * - عادي (wide = false): بطاقة رأسية ضيقة لأعمدة البث الجانبية (البث المباشر).
 * - عريض (wide = true): منطقة الموسيقى المستقلة تحت شاشة السكريبت في الاستوديو
 *   المدمج — تحكم النقل على اليمين وقائمة المقاطع تملأ الارتفاع على اليسار،
 *   فتُشاهَد عشرات المقاطع دفعة واحدة دون تمرير الوحة كله.
 */
@Composable
fun BroadcastMusicCard(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    paneHeight: Dp = 170.dp,
    wide: Boolean = false
) {
    val player = vm.player
    val playlist by vm.playlist.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isPaused by player.isPaused.collectAsState()
    val hasTrack by player.hasTrack.collectAsState()
    val title by player.title.collectAsState()
    val positionMs by player.positionMs.collectAsState()
    val durationMs by player.durationMs.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val activeId by vm.activePlaylistId.collectAsState()
    val activeName = playlists.firstOrNull { it.id == activeId }?.name ?: ""
    val repeatMode by vm.repeatMode.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val playerError by player.error.collectAsState()

    var source by remember { mutableStateOf(MusicSource.QUEUE) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categories by vm.libraryCategories.collectAsState()
    val libraryEntries by vm.libraryEntries.collectAsState()
    val onAir = isPlaying && !isPaused
    val queueInfo = if (playlist.isEmpty()) "" else " · الجاري ${currentIndex + 1} من ${playlist.size}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // الرأس: العنوان + شارة الهواء الحية + مفتاحا المصدر
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "الموسيقى",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (onAir) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PulsingDot(color = MaterialTheme.colorScheme.primary, size = 8.dp)
                    Text(
                        "على الهواء",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            SourceChip(label = MusicSource.QUEUE.label, selected = source == MusicSource.QUEUE) { source = MusicSource.QUEUE }
            SourceChip(label = MusicSource.LIBRARY.label, selected = source == MusicSource.LIBRARY) { source = MusicSource.LIBRARY }
        }

        if (wide) {
            // المنطقة العريضة (الاستوديو المدمج): تحكم على اليمين وقائمة المقاطع تملأ الارتفاع
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier.width(320.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MusicSourceControls(
                        vm = vm,
                        source = source,
                        playlists = playlists,
                        activeId = activeId,
                        activeName = activeName,
                        playlistCount = playlist.size,
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        inlineLibraryHint = false
                    )
                    MusicNowPlayingBlock(
                        title = title,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        hasTrack = hasTrack,
                        queueInfo = queueInfo,
                        big = true
                    )
                    MusicTransportRows(vm, playlist, isPlaying, isPaused, hasTrack, repeatMode, shuffle)
                    MusicErrorLine(playerError) { player.clearError() }
                    Spacer(Modifier.weight(1f))
                    MusicFaderRow(vm)
                }
                MusicTrackPane(
                    vm = vm,
                    source = source,
                    playlist = playlist,
                    currentIndex = currentIndex,
                    selectedCategory = selectedCategory,
                    libraryEntries = libraryEntries,
                    showLibraryCaption = true,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }
        } else {
            when (source) {
                MusicSource.QUEUE -> {
                    MusicSourceControls(
                        vm = vm,
                        source = source,
                        playlists = playlists,
                        activeId = activeId,
                        activeName = activeName,
                        playlistCount = playlist.size,
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        inlineLibraryHint = true
                    )
                    MusicNowPlayingBlock(
                        title = title,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        hasTrack = hasTrack,
                        queueInfo = queueInfo,
                        big = false
                    )
                    MusicTransportRows(vm, playlist, isPlaying, isPaused, hasTrack, repeatMode, shuffle)
                    MusicErrorLine(playerError) { player.clearError() }
                    MusicTrackPane(
                        vm = vm,
                        source = source,
                        playlist = playlist,
                        currentIndex = currentIndex,
                        selectedCategory = selectedCategory,
                        libraryEntries = libraryEntries,
                        showLibraryCaption = false,
                        modifier = Modifier.fillMaxWidth().height(paneHeight + 24.dp)
                    )
                }
                MusicSource.LIBRARY -> {
                    MusicSourceControls(
                        vm = vm,
                        source = source,
                        playlists = playlists,
                        activeId = activeId,
                        activeName = activeName,
                        playlistCount = playlist.size,
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        inlineLibraryHint = true
                    )
                    MusicTrackPane(
                        vm = vm,
                        source = source,
                        playlist = playlist,
                        currentIndex = currentIndex,
                        selectedCategory = selectedCategory,
                        libraryEntries = libraryEntries,
                        showLibraryCaption = false,
                        modifier = Modifier.fillMaxWidth().height(paneHeight + 24.dp)
                    )
                }
            }
            // فيدر قناة الموسيقى + كتم جلسي (وحدة المزج) — عنصر التحكم الوحيد للمستوى
            MusicFaderRow(vm)
        }
    }
}

/** صفوف اختيار المصدر: القائمة المسماة مع زر تشغيلها، أو تصنيف المكتبة. */
@Composable
private fun MusicSourceControls(
    vm: AppViewModel,
    source: MusicSource,
    playlists: List<NamedPlaylist>,
    activeId: Long,
    activeName: String,
    playlistCount: Int,
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    inlineLibraryHint: Boolean
) {
    var playlistsMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    when (source) {
        MusicSource.QUEUE -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    SecondaryAction(
                        text = "القائمة: ${shortenName(activeName, 12)} ▾",
                        onClick = { playlistsMenu = true }
                    )
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
                    text = "تشغيل القائمة",
                    onClick = { vm.playPlaylist(activeId) },
                    enabled = playlistCount > 0
                )
                Spacer(Modifier.weight(1f))
            }
        }
        MusicSource.LIBRARY -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box {
                    SecondaryAction(
                        text = shortenName(selectedCategory ?: "كل التصنيفات", 16) + " ▾",
                        onClick = { categoryMenu = true }
                    )
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("كل التصنيفات") },
                            onClick = {
                                onSelectCategory(null)
                                categoryMenu = false
                            }
                        )
                        categories.forEach { c ->
                            DropdownMenuItem(
                                text = { Text((if (c == selectedCategory) "● " else "") + c, maxLines = 1) },
                                onClick = {
                                    onSelectCategory(c)
                                    categoryMenu = false
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (inlineLibraryHint) {
                    Text(
                        "انقر مقطعًا لبثه فورًا",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** كتلة «قيد التشغيل الآن»: العنوان + الزمن + شريط التقدم. */
@Composable
private fun MusicNowPlayingBlock(
    title: String?,
    positionMs: Long,
    durationMs: Long,
    hasTrack: Boolean,
    queueInfo: String,
    big: Boolean
) {
    Text(
        title ?: "لا شيء قيد التشغيل",
        style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        "${formatMs(positionMs)} / ${formatMs(durationMs)}" + queueInfo,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (hasTrack && durationMs > 0) {
        val frac = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(LocalStudioColors.current.meterTrack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** شبكة النقل: السابق/تشغيل/التالي ثم إيقاف + عشوائي + تكرار. */
@Composable
private fun MusicTransportRows(
    vm: AppViewModel,
    playlist: List<Track>,
    isPlaying: Boolean,
    isPaused: Boolean,
    hasTrack: Boolean,
    repeatMode: PlaylistRepeatMode,
    shuffle: Boolean
) {
    val player = vm.player
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        SecondaryAction(
            text = "السابق",
            onClick = { vm.previous() },
            enabled = playlist.isNotEmpty(),
            modifier = Modifier.weight(1f)
        )
        if (isPlaying && !isPaused) {
            PrimaryAction(text = "إيقاف مؤقت", onClick = { player.pause() }, modifier = Modifier.weight(1f))
        } else {
            PrimaryAction(
                text = "تشغيل",
                onClick = { vm.togglePlay() },
                enabled = playlist.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }
        SecondaryAction(
            text = "التالي",
            onClick = { vm.next() },
            enabled = playlist.isNotEmpty(),
            modifier = Modifier.weight(1f)
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
}

/** سطر الخطأ المرئي — لا فشل صامت أبدًا (نقرة تمسحه). */
@Composable
private fun MusicErrorLine(message: String?, onDismiss: () -> Unit) {
    message?.let { msg ->
        Text(
            msg,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

/** فيدر قناة الموسيقى + كتم الجلسة — عنصر التحكم الوحيد للمستوى (وحدة المزج). */
@Composable
private fun MusicFaderRow(vm: AppViewModel) {
    val musicMuted by vm.musicMuted.collectAsState()
    val mixerState by vm.mixer.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "فيدر",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "${MixerLogic.faderPercent(mixerState.musicFader)}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(42.dp)
        )
        Slider(
            value = mixerState.musicFader,
            onValueChange = { vm.setMusicFader(it) },
            valueRange = 0f..MixerLogic.FADER_MAX,
            modifier = Modifier.weight(1f)
        )
        MuteChip(muted = musicMuted, onClick = { vm.setMusicMuted(!musicMuted) })
    }
}

/** لوحة المقاطع: قائمة التشغيل الجارية أو مقاطع المكتبة حسب التصنيف المختار. */
@Composable
private fun MusicTrackPane(
    vm: AppViewModel,
    source: MusicSource,
    playlist: List<Track>,
    currentIndex: Int,
    selectedCategory: String?,
    libraryEntries: List<LibraryEntry>,
    showLibraryCaption: Boolean,
    modifier: Modifier = Modifier
) {
    when (source) {
        MusicSource.QUEUE -> {
            if (playlist.isEmpty()) {
                MusicPaneHint(
                    text = "القائمة فارغة — أضف ملفات من صفحة «الموسيقى» أو اختر قائمة أخرى من الأعلى.",
                    modifier = modifier
                )
            } else {
                Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "مقاطع القائمة — انقر مقطعًا لبثه",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(playlist) { index, track ->
                            val isCurrent = index == currentIndex
                            PlaylistTrackRow(
                                index = index,
                                track = track,
                                isCurrent = isCurrent,
                                onClick = { vm.playAt(index) }
                            )
                        }
                    }
                }
            }
        }
        MusicSource.LIBRARY -> {
            val visible = libraryEntries
                .filter { selectedCategory == null || it.category == selectedCategory }
                .filter { it.path != null && File(it.path).isFile }
            if (visible.isEmpty()) {
                MusicPaneHint(
                    text = "لا مقاطع في هذا التصنيف — استورد ملفاتك وسرّها في تصنيفات من صفحة «الموسيقى».",
                    modifier = modifier
                )
            } else {
                Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showLibraryCaption) {
                        Text(
                            "مقاطع المكتبة — انقر مقطعًا لبثه فورًا",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(visible, key = { _, e -> e.id }) { _, entry ->
                            LibraryTrackRow(entry) { vm.playNow(entry) }
                        }
                    }
                }
            }
        }
    }
}

/** تنبيه داخلي حين تكون اللوحة فارغة. */
@Composable
private fun MusicPaneHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

/** صف مقطع من القائمة الجارية — النقر يبثه فورًا. */
@Composable
private fun PlaylistTrackRow(index: Int, track: Track, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            track.category?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            formatMs(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** صف مقطع من المكتبة المصنفة — النقر يبثه فورًا. */
@Composable
private fun LibraryTrackRow(entry: LibraryEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.title ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.category ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "بث ▸",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}
/** كبسولة مصدر صغيرة — قائمة/مكتبة. */
@Composable
private fun SourceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** تقصير اسم الجهاز في الأزرار الصغيرة للحفاظ على سلاسة العمود. */
internal fun shortenName(s: String, max: Int = 16): String =
    if (s.length <= max) s else s.take(max - 1) + "…"

// ================= شريط الموعد الأسبوعي المنطلق =================

/**
 * تنبيه لحظي أعلى صفحات البث عندما ينطلق موعد أسبوعي مجدول:
 * عنوان الحدث + شارة «الآن» + زر إغلاق. يظهر في الاستوديو المدمج والبث المباشر.
 */
@Composable
fun WeeklyAlertBanner(vm: AppViewModel, modifier: Modifier = Modifier) {
    val alert by vm.activeAlert.collectAsState()
    val sc = LocalStudioColors.current
    alert?.let { ev ->
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF7A1F1F).copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PulsingDot(color = Color(0xFFFFCDD2), size = 9.dp)
            Text(
                "موعد مجدول",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFFFFCDD2),
                fontWeight = FontWeight.Bold
            )
            Text(
                ev.title ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (ev.autoStartRun == true) {
                Text(
                    "انطلقت الحلقة",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFCDD2)
                )
            }
            Text(
                "إغلاق",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.dismissAlert() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
