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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import com.saidjte.schoolradio.desktop.data.StudioLogic
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
 * بطاقة الميكروفون المشتركة بين الاستوديو المدمج والبث المباشر: سطر المداخل
 * وسطر المخارج (طلب v0.21.0)، مقياس مقطعي بذروة، سماع مراقب، وتسجيل —
 * نسخة واحدة يحين مكان نسختين.
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
    // استرجاع اختيار الأجهزة المحفوظ من الجلسة السابقة (طلب v0.20.0 «حفظ اختيار
    // الأجهزة بين الجلسات») — بالمطابقة بالاسم: جهاز غير موصول يعيد الرئيسي
    // للأول ويُترك الثاني «بلا» دون مساس بالاسم المحفوظ.
    val savedDevices = remember { vm.savedDevices() }
    var selectedDevice by remember {
        mutableStateOf(StudioLogic.restorePrimary(devices, { it.name }, savedDevices.input1))
    }
    var selectedDevice2 by remember {
        mutableStateOf(StudioLogic.restoreOptional(devices, { it.name }, savedDevices.input2))
    }
    var selectedOut by remember {
        mutableStateOf(StudioLogic.restorePrimary(outputs, { it.name }, savedDevices.output1))
    }
    var selectedOut2 by remember {
        mutableStateOf(StudioLogic.restoreOptional(outputs, { it.name }, savedDevices.output2))
    }
    var devicesMenu by remember { mutableStateOf(false) }
    var devices2Menu by remember { mutableStateOf(false) }
    var outMenu by remember { mutableStateOf(false) }
    var out2Menu by remember { mutableStateOf(false) }

    /** إعادة تعداد أجهزة الإدخال والإخراج مع الحفاظ على الاختيار إن كان لا يزال موجودًا. */
    fun refreshDeviceLists() {
        val prevIn = selectedDevice
        val prevIn2 = selectedDevice2
        val prevOut = selectedOut
        val prevOut2 = selectedOut2
        devices = AudioDevices.listMics()
        outputs = AudioDevices.listOutputs()
        selectedDevice = devices.firstOrNull { it.name == prevIn?.name } ?: devices.firstOrNull()
        selectedDevice2 = prevIn2?.let { p -> devices.firstOrNull { it.name == p.name } }
        selectedOut = outputs.firstOrNull { it.name == prevOut?.name } ?: outputs.firstOrNull()
        selectedOut2 = prevOut2?.let { p -> outputs.firstOrNull { it.name == p.name } }
    }

    /** حفظ اختيار الأجهزة الحالي باسمائه — يُستدعى عند اختيار صريح فقط. */
    fun persistDevices() {
        vm.saveDevices(
            selectedDevice?.name,
            selectedDevice2?.name,
            selectedOut?.name,
            selectedOut2?.name
        )
    }

    /** تطبيق المخرجين الحاليين على مشغل الموسيقى والسماع المراقب فورًا. */
    fun applyOutputs() {
        vm.player.setOutput(selectedOut?.mixerInfo, selectedOut2?.mixerInfo)
        mic.setMonitorOutput(selectedOut?.mixerInfo, selectedOut2?.mixerInfo)
        persistDevices()
    }

    StudioCard {
        SectionHeader(title = "الميكروفون")

        // سطر المداخل (طلب v0.21.0: «في حيز البث اجعل سطرا مخصصا المداخل و سطر
        // مخصص المخارج») — المدخل ١ هو جهاز المذيع والمدخل ٢ اختياري يُخلط في
        // صوته (مذيعان أو ميكروفون القاعة)، كلاهما في سطر واحد واضح
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "المداخل",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Box {
                SecondaryAction(
                    text = "١: " + (selectedDevice?.let { shortenName(it.name, 12) } ?: "…"),
                    onClick = { refreshDeviceLists(); devicesMenu = true }
                )
                DropdownMenu(expanded = devicesMenu, onDismissRequest = { devicesMenu = false }) {
                    devices.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            onClick = {
                                selectedDevice = d
                                persistDevices()
                                devicesMenu = false
                            }
                        )
                    }
                }
            }
            Box {
                SecondaryAction(
                    text = "٢: " + (selectedDevice2?.let { shortenName(it.name, 12) } ?: "بلا"),
                    onClick = { refreshDeviceLists(); devices2Menu = true }
                )
                DropdownMenu(expanded = devices2Menu, onDismissRequest = { devices2Menu = false }) {
                    DropdownMenuItem(
                        text = { Text("— بلا مدخل ثانٍ —") },
                        onClick = {
                            selectedDevice2 = null
                            persistDevices()
                            devices2Menu = false
                        }
                    )
                    devices.forEach { d ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (d.mixerInfo == selectedDevice2?.mixerInfo && d.name == selectedDevice2?.name) "● " else "") + d.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                selectedDevice2 = d
                                persistDevices()
                                devices2Menu = false
                            }
                        )
                    }
                }
            }
        }

        // سطر المخارج — المخرج ١ يوجّه الموسيقى والتسجيلات والسماع المراقب،
        // والمخرج ٢ اختياري: نفس الصوت يُكتب في الجهازين معًا (قاعة + سماعة المذيع مثلًا)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "المخارج",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Box {
                SecondaryAction(
                    text = "١: " + (selectedOut?.let { shortenName(it.name, 12) } ?: "المخرج…"),
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
                                applyOutputs()
                                outMenu = false
                            }
                        )
                    }
                }
            }
            Box {
                SecondaryAction(
                    text = "٢: " + (selectedOut2?.let { shortenName(it.name, 12) } ?: "بلا"),
                    onClick = { refreshDeviceLists(); out2Menu = true }
                )
                DropdownMenu(expanded = out2Menu, onDismissRequest = { out2Menu = false }) {
                    DropdownMenuItem(
                        text = { Text("— بلا مخرج ثانٍ —") },
                        onClick = {
                            selectedOut2 = null
                            applyOutputs()
                            out2Menu = false
                        }
                    )
                    outputs.forEach { d ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    (if (d.mixerInfo == selectedOut2?.mixerInfo && d.name == selectedOut2?.name) "● " else "") + d.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                selectedOut2 = d
                                applyOutputs()
                                out2Menu = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
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
                onClick = {
                    mic.start(
                        selectedDevice?.mixerInfo,
                        selectedDevice2?.mixerInfo,
                        selectedOut?.mixerInfo,
                        selectedOut2?.mixerInfo
                    )
                },
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

// ================= بطاقة الموسيقى المشتركة (منطقة الاختيار) =================

/** مصدر اختيار المقطع في بطاقة الموسيقى: قائمة التشغيل الجارية أو المكتبة المصنفة. */
private enum class MusicSource(val label: String) {
    QUEUE("القائمة"),
    LIBRARY("المكتبة")
}

/**
 * حيّز الموسيقى في عمود البث — منطقة اختيار فقط: القائمة والمكتبة كلتاهما قائمة
 * نقية بلا أي أزرار نقل، على غرار بعضهما (طلب المستخدم v0.17.0: «أزل أزرار
 * التحكم بمشغل الموسيقى من منطقة الموسيقى لتكون مخصصة القائمة على غرار
 * المكتبة») — النقر على مقطع يبثه فورًا، وكل أزرار النقل في المشغل المستقل
 * BroadcastPlayerCard أسفل شاشة السكريبت.
 *
 * طلب v0.21.0 «اعطي أهمية لحيز الموسيقى كي تظهر القوائم جيداً»: في الاستوديو
 * المدمج تُمرَّر expandPane = true فتتمدد لوحة المقاطع بكل المساحة الباقية
 * من البطاقة (والبطاقة نفسها تتمدد بالوزن من عمود البث) فتظهر القوائم بعدد
 * صفوف كبير بدل الحيّز المقتطع.
 */
@Composable
fun BroadcastMusicCard(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    paneHeight: Dp = 170.dp,
    expandPane: Boolean = false
) {
    val player = vm.player
    val playlist by vm.playlist.collectAsState()
    val currentIndex by vm.currentIndex.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isPaused by player.isPaused.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val activeId by vm.activePlaylistId.collectAsState()
    val activeName = playlists.firstOrNull { it.id == activeId }?.name ?: ""
    val playerError by player.error.collectAsState()

    var source by remember { mutableStateOf(MusicSource.QUEUE) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val categories by vm.libraryCategories.collectAsState()
    val libraryEntries by vm.libraryEntries.collectAsState()
    val onAir = isPlaying && !isPaused

    StudioCard(modifier = modifier) {
        // لوحة المقاطع: متمددة بكل المساحة الباقية (الاستوديو) أو بارتفاع مقتطع
        // (البث المباشر) — weight يعمل داخل نطاق العمود فقط
        val paneModifier = if (expandPane) Modifier.fillMaxWidth().weight(1f)
        else Modifier.fillMaxWidth().height(paneHeight + 24.dp)
        // الرأس: العنوان + شارة الهواء الحية + مفتاحا المصدر
        SectionHeader(title = "الموسيقى") {
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

        when (source) {
            MusicSource.QUEUE -> {
                MusicSourceControls(
                    vm = vm,
                    source = source,
                    playlists = playlists,
                    activeId = activeId,
                    activeName = activeName,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
                MusicErrorLine(playerError) { player.clearError() }
                MusicTrackPane(
                    vm = vm,
                    source = source,
                    playlist = playlist,
                    currentIndex = currentIndex,
                    selectedCategory = selectedCategory,
                    libraryEntries = libraryEntries,
                    modifier = paneModifier
                )
            }
            MusicSource.LIBRARY -> {
                MusicSourceControls(
                    vm = vm,
                    source = source,
                    playlists = playlists,
                    activeId = activeId,
                    activeName = activeName,
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it }
                )
                MusicTrackPane(
                    vm = vm,
                    source = source,
                    playlist = playlist,
                    currentIndex = currentIndex,
                    selectedCategory = selectedCategory,
                    libraryEntries = libraryEntries,
                    modifier = paneModifier
                )
            }
        }
        // فيدر قناة الموسيقى + كتم جلسي (وحدة المزج) — عنصر التحكم الوحيد للمستوى
        MusicFaderRow(vm)
    }
}

// ================= مشغل الموسيقى المستقل (مظهر مشغل حقيقي بأيقونات مرسومة) =================

/** رموز المشغل — مرسومة Canvas بلا اعتماد على خطوط النظام (لا مربعات فارغة). */
private enum class PlayerGlyph { PLAY, PAUSE, STOP, PREV, NEXT, SHUFFLE, REPEAT }

/**
 * المشغل المستقل (طلب المستخدم v0.15.0): منطقة بعرض شاشة السكريبت أسفلها مباشرة،
 * بمظهر مشغل موسيقى احترافي: عنوان جارٍ، شريط تقدم قابل للسعي الزمني، أزرار نقل
 * بأيقونات دائرية مرسومة (بلا نصوص كبيرة)، عشوائي وتكرار، وفيدر القناة مكتمًا.
 */
@Composable
fun BroadcastPlayerCard(
    vm: AppViewModel,
    modifier: Modifier = Modifier
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
    val onAir = isPlaying && !isPaused

    // أثناء سحب المؤشر نعرض القيمة المحلية ثم ننفذ السعي عند الإفلات
    var seekMs by remember { mutableStateOf<Long?>(null) }
    val shownPos = seekMs ?: positionMs

    StudioCard(modifier = modifier, contentPadding = 12.dp) {
        // الرأس: الاسم + شارة الهواء + اسم القائمة الجارية وموضعها
        SectionHeader(title = "مشغل الموسيقى") {
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
            if (playlist.isNotEmpty()) {
                Text(
                    "${shortenName(activeName, 14)} · ${currentIndex + 1}/${playlist.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // العنوان الجاري بخط واضح
        Text(
            title ?: "لا شيء قيد التشغيل",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // شريط التقدم القابل للسعي (اتجاه LTR كعرف المشغلات) + الزمنان
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    formatMs(shownPos),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasTrack && durationMs > 0) {
                    Slider(
                        value = (shownPos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { seekMs = (it * durationMs).toLong() },
                        onValueChangeFinished = {
                            seekMs?.let { player.seek(it) }
                            seekMs = null
                        },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(LocalStudioColors.current.meterTrack)
                    )
                }
                Text(
                    formatMs(durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // صف النقل بأيقونات دائرية مرسومة — بلا نصوص كبيرة (اتجاه LTR كعرف المشغلات)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerIconButton(
                    glyph = PlayerGlyph.SHUFFLE,
                    desc = "عشوائي",
                    active = shuffle,
                    diameter = 40.dp,
                    onClick = { vm.toggleShuffle() }
                )
                PlayerIconButton(
                    glyph = PlayerGlyph.PREV,
                    desc = "السابق",
                    diameter = 44.dp,
                    enabled = playlist.isNotEmpty(),
                    onClick = { vm.previous() }
                )
                PlayerIconButton(
                    glyph = if (onAir) PlayerGlyph.PAUSE else PlayerGlyph.PLAY,
                    desc = if (onAir) "إيقاف مؤقت" else "تشغيل",
                    filled = true,
                    diameter = 56.dp,
                    enabled = playlist.isNotEmpty() || hasTrack,
                    onClick = { if (onAir) player.pause() else vm.togglePlay() }
                )
                PlayerIconButton(
                    glyph = PlayerGlyph.NEXT,
                    desc = "التالي",
                    diameter = 44.dp,
                    enabled = playlist.isNotEmpty(),
                    onClick = { vm.next() }
                )
                PlayerIconButton(
                    glyph = PlayerGlyph.STOP,
                    desc = "إيقاف",
                    diameter = 44.dp,
                    enabled = hasTrack,
                    onClick = { player.stop() }
                )
                Spacer(Modifier.weight(1f))
                PlayerIconButton(
                    glyph = PlayerGlyph.REPEAT,
                    desc = "تكرار: ${repeatMode.label}",
                    active = repeatMode != PlaylistRepeatMode.OFF,
                    badge = if (repeatMode == PlaylistRepeatMode.ONE) "1" else null,
                    diameter = 40.dp,
                    onClick = { vm.cycleRepeat() }
                )
            }
        }

        MusicErrorLine(playerError) { player.clearError() }
        MusicFaderRow(vm)
    }
}

/** زر أيقونة دائري للمشغل: الرمز مرسوم Canvas — filled للزر الرئيسي وactive للتبديلات. */
@Composable
private fun PlayerIconButton(
    glyph: PlayerGlyph,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    filled: Boolean = false,
    active: Boolean = false,
    enabled: Boolean = true,
    badge: String? = null
) {
    val bg = when {
        filled -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        filled -> MaterialTheme.colorScheme.onPrimary
        active -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(diameter * 0.5f)) {
            val w = size.width
            val h = size.height
            val alpha = if (enabled) 1f else 0.35f
            val c = fg.copy(alpha = alpha)
            val sw = w * 0.14f
            val cap = StrokeCap.Round
            when (glyph) {
                PlayerGlyph.PLAY -> drawPath(
                    path = Path().apply {
                        moveTo(w * 0.22f, h * 0.10f)
                        lineTo(w * 0.90f, h * 0.5f)
                        lineTo(w * 0.22f, h * 0.90f)
                        close()
                    },
                    color = c
                )
                PlayerGlyph.PAUSE -> {
                    drawRoundRect(c, Offset(w * 0.12f, h * 0.06f), Size(w * 0.28f, h * 0.88f), CornerRadius(w * 0.09f))
                    drawRoundRect(c, Offset(w * 0.60f, h * 0.06f), Size(w * 0.28f, h * 0.88f), CornerRadius(w * 0.09f))
                }
                PlayerGlyph.STOP -> drawRoundRect(c, Offset(w * 0.10f, h * 0.10f), Size(w * 0.80f, h * 0.80f), CornerRadius(w * 0.14f))
                PlayerGlyph.PREV -> {
                    drawLine(c, Offset(w * 0.14f, h * 0.08f), Offset(w * 0.14f, h * 0.92f), sw, cap)
                    drawPath(
                        path = Path().apply {
                            moveTo(w * 0.88f, h * 0.08f)
                            lineTo(w * 0.28f, h * 0.5f)
                            lineTo(w * 0.88f, h * 0.92f)
                            close()
                        },
                        color = c
                    )
                }
                PlayerGlyph.NEXT -> {
                    drawLine(c, Offset(w * 0.86f, h * 0.08f), Offset(w * 0.86f, h * 0.92f), sw, cap)
                    drawPath(
                        path = Path().apply {
                            moveTo(w * 0.12f, h * 0.08f)
                            lineTo(w * 0.72f, h * 0.5f)
                            lineTo(w * 0.12f, h * 0.92f)
                            close()
                        },
                        color = c
                    )
                }
                PlayerGlyph.SHUFFLE -> {
                    drawLine(c, Offset(w * 0.06f, h * 0.20f), Offset(w * 0.72f, h * 0.80f), sw, cap)
                    drawLine(c, Offset(w * 0.06f, h * 0.80f), Offset(w * 0.72f, h * 0.20f), sw, cap)
                    // رأسا السهمين عند اليمين
                    drawPath(Path().apply {
                        moveTo(w * 0.98f, h * 0.50f)
                        lineTo(w * 0.72f, h * 0.40f)
                        lineTo(w * 0.80f, h * 0.60f)
                        close()
                    }, c)
                }
                PlayerGlyph.REPEAT -> {
                    // حلقة مستديرة بفجوة صغيرة ورأس سهم يوحي بالدوران
                    drawArc(c, startAngle = -80f, sweepAngle = 320f, useCenter = false, style = Stroke(width = sw, cap = cap))
                    drawPath(Path().apply {
                        moveTo(w * 0.62f, h * 0.98f)
                        lineTo(w * 0.94f, h * 0.88f)
                        lineTo(w * 0.72f, h * 0.58f)
                        close()
                    }, c)
                }
            }
        }
        badge?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

/** صفوف اختيار المصدر: القائمة المسماة أو تصنيف المكتبة — اختيار فقط بلا أزرار نقل. */
@Composable
private fun MusicSourceControls(
    vm: AppViewModel,
    source: MusicSource,
    playlists: List<NamedPlaylist>,
    activeId: Long,
    activeName: String,
    categories: List<String>,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit
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
                Spacer(Modifier.weight(1f))
                Text(
                    "انقر مقطعًا لبثه فورًا",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Text(
                    "انقر مقطعًا لبثه فورًا",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
                // قائمة نقية كما لوحة المكتبة تمامًا — بلا رأس ولا أزرار
                Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
