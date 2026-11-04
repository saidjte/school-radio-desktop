package com.saidjte.schoolradio.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saidjte.schoolradio.desktop.APP_VERSION
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.Tab
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.SegmentStatus
import com.saidjte.schoolradio.desktop.data.StudioLayout
import com.saidjte.schoolradio.desktop.data.StudioLayoutLogic
import com.saidjte.schoolradio.desktop.data.StudioLogic
import java.awt.Cursor
import java.io.File

/**
 * الاستوديو المدمج: الجدولة + السكريبت + البث في شاشة واحدة للإذاعة.
 * (يمين) حيز التسجيلات وحيز الجدولة متساويان في الارتفاع افتراضيًا
 *       (طلب المستخدم v0.21.0) وبفاصل سحب بينهما،
 * (وسط) لوحة قراءة السكريبت بخط كبير وأسفلها مشغل الموسيقى المستقل
 *       بنفس العرض وبفاصل سحب بينهما،
 * (يسار) حيّز البث: الميكروفون وحيّز موسيقى واسع تظهر فيه القوائم جيدًا
 *       (طلب المستخدم v0.21.0) ووحدة المزج.
 * كل الحيزات تُضبط أبعادها بالفأرة عبر فواصل السحب (طلب v0.21.0: «أعطي
 * المستعمل طريقة التحكم في ابعاد كل الحيزات الموجودة في شاشة الاستوديو
 * بواسطة الفأرة») والأبعاد تُحفظ في settings.json فتعود بين الجلسات.
 */
@Composable
fun StudioScreen(vm: AppViewModel) {
    // أبعاد الحيزات: المحفوظة من الجلسة السابقة، والسحب يعدّلها ويحفظ عند الإفلات
    val saved = remember { vm.savedLayout() }
    var rightW by remember { mutableFloatStateOf(saved.rightWidth) }
    var bcW by remember { mutableFloatStateOf(saved.broadcastWidth) }
    var rightTop by remember { mutableFloatStateOf(saved.rightTopFrac) }
    var midTop by remember { mutableFloatStateOf(saved.midTopFrac) }
    var bcMic by remember { mutableFloatStateOf(saved.bcMicFrac) }
    val dens = LocalDensity.current.density

    fun persist() {
        vm.saveLayout(StudioLayout(rightW, bcW, rightTop, midTop, bcMic))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StudioTopBar(vm)
        // شريط الموعد الأسبوعي المنطلق — يظهر أعلاه محتوى الاستوديو لحظة الإطلاق
        WeeklyAlertBanner(
            vm = vm,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // العمود الأيمن: حيّزا التسجيلات والجدولة متساويان في الارتفاع (v0.21.0)
            StudioRightColumn(
                vm = vm,
                modifier = Modifier.width(rightW.dp),
                topFrac = rightTop,
                onTopFrac = { rightTop = it },
                onEnd = { persist() }
            )
            // فاصل سحب: عرض الجانب الأيمن
            StudioHSplit(
                onDragX = { dx ->
                    rightW = (rightW + dx / dens).coerceIn(StudioLayoutLogic.RIGHT_WIDTH_MIN, StudioLayoutLogic.RIGHT_WIDTH_MAX)
                },
                onEnd = { persist() }
            )
            // المنطقة الوسطى: شاشة السكريبت ثم فاصل سحب ثم المشغل المستقل —
            // بنفس عرض الشاشة (طلب المستخدم v0.15.0)
            StudioMiddleColumn(
                vm = vm,
                midTop = midTop,
                onMidTop = { midTop = it },
                onEnd = { persist() },
                modifier = Modifier.weight(1f)
            )
            // فاصل سحب: عرض عمود البث
            StudioHSplit(
                onDragX = { dx ->
                    bcW = (bcW - dx / dens).coerceIn(StudioLayoutLogic.BC_WIDTH_MIN, StudioLayoutLogic.BC_WIDTH_MAX)
                },
                onEnd = { persist() }
            )
            // عمود البث: الميكروفون + حيّز موسيقى واسع + رابط المزج
            StudioBroadcastPanel(
                vm = vm,
                modifier = Modifier.width(bcW.dp),
                micFrac = bcMic,
                onMicFrac = { bcMic = it },
                onEnd = { persist() }
            )
        }
    }
}

/**
 * العمود الأيمن للاستوديو المدمج: حيّزا التسجيلات والجدولة متساويان في
 * الارتفاع افتراضيًا (طلب المستخدم v0.21.0 «في الجانب الايمن اجعل الحيزين
 * متساويين في الارتفاع») — والفاصل بينهما قابل للسحب بالفأرة فمن أراد
 * اختلافًا يعيد توزيعهما بسهولة.
 */
@Composable
private fun StudioRightColumn(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    topFrac: Float,
    onTopFrac: (Float) -> Unit,
    onEnd: () -> Unit
) {
    var heightPx by remember { mutableIntStateOf(1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StudioRecordingsCard(vm, Modifier.fillMaxWidth().weight(topFrac))
        StudioVSplit(
            onDragY = { dy ->
                onTopFrac(
                    StudioLayoutLogic.clampFrac(
                        topFrac + dy / heightPx,
                        StudioLayoutLogic.RIGHT_TOP_MIN, StudioLayoutLogic.RIGHT_TOP_MAX
                    )
                )
            },
            onEnd = onEnd
        )
        StudioSchedulePanel(vm, Modifier.fillMaxWidth().weight(1f - topFrac))
    }
}

/**
 * العمود الأوسط: شاشة السكريبت بحصة قابلة للسحب والمشغل المستقل تحتها —
 * الفاصل بينهما بالفأرة ويُحفظ موضع بين الجلسات.
 */
@Composable
private fun StudioMiddleColumn(
    vm: AppViewModel,
    midTop: Float,
    onMidTop: (Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var heightPx by remember { mutableIntStateOf(1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        StudioScriptPanel(vm, Modifier.weight(midTop))
        StudioVSplit(
            onDragY = { dy ->
                onMidTop(
                    StudioLayoutLogic.clampFrac(
                        midTop + dy / heightPx,
                        StudioLayoutLogic.MID_TOP_MIN, StudioLayoutLogic.MID_TOP_MAX
                    )
                )
            },
            onEnd = onEnd
        )
        // منطقة المشغل المستقل: بتمرير داخلي حين يضيّقها المذيع بلا فقد أي زر
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f - midTop)
                .verticalScroll(rememberScrollState())
        ) {
            BroadcastPlayerCard(vm = vm, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ================= فواصل السحب بالفأرة (طلب v0.21.0) =================

/**
 * فاصل سحب عمودي (بين الأعمدة): سحب أفقي يعدّل عرض العمود المجاور —
 * مؤشر السهم الثنائي والقبضة المرئية يعلمان قابلية السحب، والإفلات يحفظ.
 */
@Composable
private fun StudioHSplit(
    onDragX: (Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dragCb by rememberUpdatedState(onDragX)
    val endCb by rememberUpdatedState(onEnd)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    // توهج القبضة عند المرور: تعليم واضح أن الفاصل قابل للسحب (v0.22.0)
    val gripColor by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
        label = "h-grip"
    )
    Box(
        modifier = modifier
            .width(10.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
            .hoverable(interaction)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        dragCb(drag.x)
                    },
                    onDragEnd = { endCb() },
                    onDragCancel = { endCb() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // قبضة مرئية: مضلّع رقيق عمودي في المنتصف
        Box(
            Modifier
                .width(4.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(gripColor)
        )
    }
}

/**
 * فاصل سحب أفقي (بين حيّزين متراصين): سحب عمودي يعيد توزيع الارتفاع بينهما —
 * الإفلات يحفظ الموضع الجديد في الإعدادات.
 */
@Composable
private fun StudioVSplit(
    onDragY: (Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dragCb by rememberUpdatedState(onDragY)
    val endCb by rememberUpdatedState(onEnd)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val gripColor by animateColorAsState(
        if (hovered) MaterialTheme.colorScheme.primary.copy(alpha = 0.95f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f),
        label = "v-grip"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .hoverable(interaction)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        dragCb(drag.y)
                    },
                    onDragEnd = { endCb() },
                    onDragCancel = { endCb() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // قبضة مرئية: مضلّع رقيق أفقي في المنتصف
        Box(
            Modifier
                .width(52.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(gripColor)
        )
    }
}

// ================= الشريط العلوي: الهواء + الساعة =================

@Composable
private fun StudioTopBar(vm: AppViewModel) {
    val clock by vm.clockText.collectAsState()
    val schoolName by vm.schoolName.collectAsState()
    val micState by vm.mic.state.collectAsState()
    val recording by vm.mic.recording.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val paused by vm.player.isPaused.collectAsState()
    val onAir = StudioLogic.status(
        micLive = micState == MicState.LIVE,
        recording = recording,
        musicPlaying = playing && !paused
    ).onAir
    val sc = LocalStudioColors.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // شعار التطبيق في قلب الشريط العلوي — هوية بصرية ثابتة (v0.22.0)
            LogoMark(size = 38.dp)
            Column {
                Text(
                    "الاستوديو المدمج",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (schoolName != null) {
                    Text(
                        schoolName ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            // مؤشر الهواء الموحد بنبض حي
            OnAirPill(onAir = onAir)
            if (recording) {
                Text(
                    "تسجيل جارٍ",
                    style = MaterialTheme.typography.labelLarge,
                    color = sc.recordingAccent,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.weight(1f))
            Text(
                clock,
                style = MaterialTheme.typography.titleLarge.numeric(),
                fontWeight = FontWeight.Bold
            )
            SecondaryAction(text = "الأقسام التفصيلية", onClick = { vm.selectTab(Tab.LIVE) })
            Text(
                "v$APP_VERSION",
                style = MaterialTheme.typography.bodySmall.brand(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // خط هوية رقيق أسفل الشريط: تدرج الشعار يربط الرأس بالمحتوى (v0.22.0)
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        listOf(sc.logoTop, MaterialTheme.colorScheme.primary, sc.logoBottom)
                    )
                )
        )
    }
}

// ================= العمود الأيمن: الجدولة =================

@Composable
private fun StudioSchedulePanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    val schedule by vm.schedule.collectAsState()
    val episodeStart by vm.episodeStartMinutes.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val runElapsedMs by vm.runElapsedMs.collectAsState()
    val playerFile by vm.player.currentFile.collectAsState()
    val playerPlaying by vm.player.isPlaying.collectAsState()
    val playerPaused by vm.player.isPaused.collectAsState()

    val clocks = remember(schedule, episodeStart) {
        ScheduleMath.plannedClocks(schedule, episodeStart)
    }
    val statuses = remember(runIndex, schedule.size) {
        ScheduleMath.statuses(runIndex, schedule.size)
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(title = "الجدولة") {
            Text(
                "${schedule.size} فقرات · ${formatMs(ScheduleMath.totalSeconds(schedule) * 1000)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // لوحة التحكم الحية للحلقة
        if (!runActive) {
            PrimaryAction(
                text = "بدء الحلقة",
                enabled = schedule.isNotEmpty(),
                onClick = { vm.startRun() },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            val current = schedule.getOrNull(runIndex)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "الفقرة الجارية (${runIndex + 1}/${schedule.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    current?.title ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                current?.let { c ->
                    if (c.plannedSeconds > 0) {
                        val frac = (runElapsedMs / 1000.0 / c.plannedSeconds).coerceIn(0.0, 1.0)
                        LinearProgressIndicator(
                            progress = { frac.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "${formatMs(runElapsedMs)} من ${formatMs(c.plannedSeconds * 1000)}" +
                                    if (runElapsedMs / 1000L > c.plannedSeconds) " — تجاوزت المدة" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (runElapsedMs / 1000L > c.plannedSeconds) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    } else {
                        Text(
                            "زمن الفقرة: ${formatMs(runElapsedMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryAction(
                        text = "الفقرة التالية",
                        onClick = { vm.finishCurrentItem() },
                        modifier = Modifier.weight(1f)
                    )
                    SecondaryAction(text = "إنهاء الحلقة", onClick = { vm.endRun() })
                }
            }
        }

        // قائمة الفقرات
        if (schedule.isEmpty()) {
            EmptyState(
                icon = AppIconKind.SCHEDULE,
                title = "لا فقرات بعد",
                hint = "أضف فقرات الحلقة من قسم الجدولة التفصيلي، ثم ابدأ الحلقة من هنا وستُدار فقرةً فقرة."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(schedule) { index, item ->
                    val isCurrent = runActive && index == runIndex
                    val playingThis = item.mediaPath != null &&
                            playerFile == File(item.mediaPath) &&
                            playerPlaying && !playerPaused
                    StudioSegmentRow(
                        item = item,
                        index = index,
                        clock = clocks[index],
                        status = statuses[index],
                        isCurrent = isCurrent,
                        runElapsedMs = if (isCurrent) runElapsedMs else 0L,
                        playingThis = playingThis,
                        onClick = {
                            // النقر على الفقرة يعرض سكريبتها على لوحة القراءة
                            val sid = item.scriptSectionId
                            if (sid != null && sid > 0L) vm.selectSection(sid)
                        },
                        onPlayMedia = { vm.runPlayMedia(it) },
                        onStopMedia = { vm.player.stop() }
                    )
                }
            }
        }

        SecondaryAction(
            text = "إدارة الفقرات (إضافة/تحرير/ربط سكريبت)",
            onClick = { vm.selectTab(Tab.SCHEDULE) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StudioSegmentRow(
    item: ScheduleItem,
    index: Int,
    clock: String?,
    status: SegmentStatus,
    isCurrent: Boolean,
    runElapsedMs: Long,
    playingThis: Boolean,
    onClick: () -> Unit,
    onPlayMedia: (ScheduleItem) -> Unit,
    onStopMedia: () -> Unit
) {
    val overdue = isCurrent && item.plannedSeconds > 0 && runElapsedMs / 1000L > item.plannedSeconds
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isCurrent -> MaterialTheme.colorScheme.primaryContainer
                    playingThis -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            clock ?: "${index + 1}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp)
        )
        Dot(color = segmentKindColor(item.kind), size = 9.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    item.title.ifEmpty { "بدون عنوان" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.scriptSectionId != null && item.scriptSectionId > 0L) {
                    Text(
                        "سكريبت",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                buildString {
                    append("${item.kind.label} · ${formatMs(item.plannedSeconds * 1000)}")
                    if (isCurrent) append(" · جارية ${formatMs(runElapsedMs)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.mediaPath != null) {
            if (playingThis) {
                SecondaryAction(text = "إيقاف", onClick = onStopMedia)
            } else {
                SecondaryAction(text = "تشغيل", onClick = { onPlayMedia(item) })
            }
        }
    }
}

// ================= العمود الأوسط: لوحة السكريبت =================

@Composable
private fun StudioScriptPanel(vm: AppViewModel, modifier: Modifier = Modifier) {
    val schedule by vm.schedule.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val script by vm.script.collectAsState()
    val selectedId by vm.selectedSectionId.collectAsState()
    val fullReading by vm.fullScriptReading.collectAsState()

    var fontSp by remember { mutableStateOf(34f) }
    var showOverlay by remember { mutableStateOf(false) }
    val readScroll = rememberScrollState()

    val resolved = StudioLogic.resolveScriptSection(schedule, runActive, runIndex, script, selectedId)

    // لحظة انتقال الحلقة لفقرة تالية تعود لوحة القراءة لقمة النص الجديد —
    // كان الموضع القديم يبقى عالقًا والمذيع يقرأ من منتصف الفقرة السابقة
    LaunchedEffect(resolved?.first?.id) {
        readScroll.animateScrollTo(0, tween(220))
    }

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(title = "السكريبت") {
            // مصدر النص المعروض
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    resolved?.second?.label ?: "فارغ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            SecondaryAction(text = "شاشة القراءة", onClick = { showOverlay = true })
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(checked = fullReading, onCheckedChange = { vm.fullScriptReading.value = it })
                Text("كامل", style = MaterialTheme.typography.bodySmall)
            }
            SecondaryAction(text = "أ−", onClick = { fontSp = (fontSp - 4f).coerceAtLeast(16f) })
            SecondaryAction(text = "أ+", onClick = { fontSp = (fontSp + 4f).coerceAtMost(72f) })
        }

        // لوحة القراءة الكبيرة
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(readScroll)
                .padding(20.dp)
        ) {
            val shown: ScriptSection? = resolved?.first
            if (shown == null) {
                Text(
                    "لا يوجد سكريبت بعد — أضف فقرات النص من قسم «سكريبت المذيع» وستظهر هنا بخط كبير للقراءة على الهواء.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    shown.title.ifEmpty { "بدون عنوان" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    shown.body.ifEmpty { "—" },
                    fontSize = fontSp.sp,
                    lineHeight = (fontSp * 1.5f).sp
                )
            }
        }

        // معلومات أسفل اللوحة + تنقّل سلس بين الفقرات أثناء القراءة —
        // نص المعلومات هو المطاط (weight) فلا تنضغط الأزرار حين يضيّق
        // المذيع العمود بالفأرة (v0.21.0)
        val shown = resolved?.first
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                if (shown == null) "لا نص"
                else "${ScriptStats.wordCount(shown.body)} كلمة · " +
                        "زمن تقديري ${formatMs(ScriptStats.secondsFor(shown.body) * 1000)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            SecondaryAction(
                text = "السابقة",
                onClick = { vm.selectAdjacentSection(-1) },
                enabled = script.isNotEmpty()
            )
            SecondaryAction(
                text = "التالية",
                onClick = { vm.selectAdjacentSection(+1) },
                enabled = script.isNotEmpty()
            )
            SecondaryAction(
                text = "تحرير السكريبت",
                onClick = { vm.selectTab(Tab.SCRIPT) }
            )
        }
    }

    // شاشة القراءة التلقيمية بملء النافذة (v0.19.0)
    if (showOverlay) {
        ReadingOverlay(vm) { showOverlay = false }
    }
}

// ================= العمود الأيسر: البث =================

/**
 * عمود البث: الميكروفون وحيّز الموسيقى ووحدة المزج — طلب v0.21.0
 * «اعطي أهمية لحيز الموسيقى كي تظهر القوائم جيداً»: حيّز الموسيقى يأخذ
 * كل المساحة الباقية من العمود فتظهر القوائم بعدد صفوف كبير.
 *
 * طلب v0.26.0 «اجعل طريقة التحكم في مقاس الحيزات الموجودة في العمود الأيسر
 * تعمل بنفس طريقة التحكم في مقاس الحيزات الموجودة في العمود الأيمن»:
 * بطاقتا الميكروفون والموسيقى تأخذان الوزن مباشرة بفاصل سحب بينهما —
 * فالسحب يعيد تشكيل البطاقتين مرئيًا (كما في بطاقتَي التسجيلات والجدولة
 * في العمود الأيمن تمامًا) بدل اقتطاع البطاقة داخل مجلد تمرير،
 * وموضع الفاصل يُحفظ بين الجلسات ومحتوى الميكروفون يُمرَّر داخليًا.
 */
@Composable
private fun StudioBroadcastPanel(
    vm: AppViewModel,
    modifier: Modifier = Modifier,
    micFrac: Float,
    onMicFrac: (Float) -> Unit,
    onEnd: () -> Unit
) {
    var heightPx by remember { mutableIntStateOf(1) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) },
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SectionHeader(title = "البث")
        // حيّز الميكروفون: البطاقة نفسها تأخذ الوزن فتتغير بالفاصل مرئيًا
        BroadcastMicCard(vm, Modifier.fillMaxWidth().weight(micFrac))
        StudioVSplit(
            onDragY = { dy ->
                onMicFrac(
                    StudioLayoutLogic.clampFrac(
                        micFrac + dy / heightPx,
                        StudioLayoutLogic.BC_MIC_MIN, StudioLayoutLogic.BC_MIC_MAX
                    )
                )
            },
            onEnd = onEnd
        )
        // حيّز الموسيقى: البطاقة مباشرة بالوزن الباقي — القائمة والمكتبة تظهران جيدًا
        BroadcastMusicCard(
            vm = vm,
            modifier = Modifier.fillMaxWidth().weight(1f - micFrac),
            expandPane = true
        )
        MixerLinkCard(vm)
    }
}

@Composable
private fun StudioRecordingsCard(vm: AppViewModel, modifier: Modifier = Modifier) {
    val recordings by vm.recordings.collectAsState()
    val dir by vm.recordingsDir.collectAsState()
    val currentFile by vm.player.currentFile.collectAsState()
    val isPlaying by vm.player.isPlaying.collectAsState()
    val isPaused by vm.player.isPaused.collectAsState()

    StudioCard(modifier = modifier.fillMaxWidth()) {
        SectionHeader(title = "التسجيلات") {
            SecondaryAction(text = "تحديث", onClick = { vm.refreshRecordings() })
            SecondaryAction(text = "المكتبة", onClick = { vm.selectTab(Tab.RECORDINGS) })
        }
        if (recordings.isEmpty()) {
            EmptyState(
                icon = AppIconKind.RECORDINGS,
                iconSize = 26.dp,
                title = "لا توجد تسجيلات",
                hint = "ابدأ التسجيل أثناء البث المباشر وستظهر هنا."
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(recordings) { _, item ->
                    val playingThis = currentFile == item.file && isPlaying && !isPaused
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (playingThis) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${formatMs(item.durationMs)} · %.1f م.ب".format(item.sizeBytes / 1048576.0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (playingThis) {
                            SecondaryAction(text = "إيقاف", onClick = { vm.player.stop() })
                        } else {
                            SecondaryAction(text = "تشغيل", onClick = { vm.player.play(item.file) })
                        }
                    }
                }
            }
        }
        Text(
            "المجلد: ${dir.absolutePath}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
