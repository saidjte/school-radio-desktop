package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.audio.MontageExportFormat
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.ClipTransforms
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageEnvelope
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMarker
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageTemplateCatalog
import com.saidjte.schoolradio.desktop.data.MontageTool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * صفحة المونتاج (v0.33.0 «أضف صفحة المونتاج» + v0.34.0 «الترقية الاحترافية»):
 * خط زمني واحد بموجات صوتية حقيقية — تكبير وخريطة مصغرة ورأس تشغيل ومقابض
 * قصّ بالسحب وسحب لإعادة الترتيب وكروس فيد بين المتجاورة — مع أدوات احترافية
 * (تراجع/إعادة، تقسيم عند الرأس، حذف ممتد، تطبيع، حلقة A/B، مؤشر VU وتحذير
 * تشبع) ومعاينة من أي نقطة وتصدير ملف واحد جاهز للبث (WAV أو MP3 منذ
 * v0.37.0 بجودة قابلة للاختيار)، وحفظ/فتح ملف مشروع. منذ v0.37.0 أيضًا:
 * النقر المزدوج على أي مقطع يفتح تسميته، ورؤوس المسارات بلا كتم ولا منفرد
 * — مسار التعليق هو البرنامج لا يُكتم أبدًا والمسارات الحرة بعقد نفسه.
 * كل ذلك دون اتصال: التصيير فكّ محلي وكتابة محلية بلا جهاز صوت ولا شبكة.
 */
@Composable
fun MontagePage(vm: AppViewModel) {
    val project by vm.montageProject.collectAsState()
    val progress by vm.montageRenderProgress.collectAsState()
    val lastExport by vm.lastMontageExport.collectAsState()
    val message by vm.lastLibraryMessage.collectAsState()
    val recordings by vm.recordings.collectAsState()
    val envelopes by vm.montageEnvelopes.collectAsState()
    val selectedId by vm.montageSelectedClipId.collectAsState()
    val playheadMs by vm.montagePlayheadMs.collectAsState()
    val canUndo by vm.montageCanUndo.collectAsState()
    val canRedo by vm.montageCanRedo.collectAsState()
    val loopStart by vm.montageLoopStart.collectAsState()
    val loopEnd by vm.montageLoopEnd.collectAsState()
    val loopEnabled by vm.montageLoopEnabled.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val selectedMusicId by vm.montageSelectedMusicId.collectAsState()
    val selection by vm.montageSelection.collectAsState()
    val tool by vm.montageTool.collectAsState()
    val snapEnabled by vm.montageSnapEnabled.collectAsState()
    val exportFormat by vm.montageExportFormat.collectAsState()
    val exportBitrate by vm.montageExportBitrate.collectAsState()
    // v0.38.0: الحافظة وحالة تحويلات الارتداد
    val clipboard by vm.montageClipboard.collectAsState()
    val transformBusy by vm.montageTransformBusy.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MontageClip?>(null) }
    var editMusicTarget by remember { mutableStateOf<MontageMusicClip?>(null) }
    var showMarkerDialog by remember { mutableStateOf(false) }
    var showMarkerAtSelection by remember { mutableStateOf(false) }
    var templateConfirm by remember { mutableStateOf<String?>(null) }
    // أهداف إعادة التسمية بالنقر المزدوج (v0.37.0) — تعليقًا كان أو مقطع مسار حر
    var renameVoiceTarget by remember { mutableStateOf<MontageClip?>(null) }
    var renameMusicTarget by remember { mutableStateOf<MontageMusicClip?>(null) }
    // v0.38.0: حوارات أدوات المقطع الحديثة
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showPitchDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    val rendering = progress != null || transformBusy
    val totalMs = remember(project) { MontageLogic.totalDurationFull(project) }
    // تكبير الخط الزمني: بكسل لكل ثانية — 60 الافتراضي، والحدود تمنع ضياع الاتجاه
    var pxPerSec by remember { mutableFloatStateOf(60f) }

    // نبضة الصفحة: موضع المعاينة الحيّ → الرأس، ونبضة الحلقة A/B، ومؤشر VU
    // (قراءة الحالات من التدفقات مباشرة كي لا تتجمد النبضة على قيمة قديمة)
    var vuLevel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(80)
            vm.montageLoopTick()
            if (vm.player.isPlaying.value) {
                val cur = vm.player.currentFile.value
                if (cur != null && cur.name == "preview.wav") {
                    vm.setMontagePlayhead(vm.player.positionMs.value, persist = false)
                }
            }
            val hit = envAndBucketAt(vm.montageProject.value, vm.montagePlayheadMs.value, vm.montageEnvelopes.value)
            val target = hit?.first?.peakAt(hit.second) ?: 0f
            vuLevel = max(target, vuLevel * 0.82f)
        }
    }
    DisposableEffect(Unit) {
        onDispose { vm.stopMontagePreview() }
    }

    // تمرير عمودي للصفحة كلها (v0.35.0): الصفوف الجديدة (قوالب وعلامات وخفض)
    // لا تدفع أزرار المعاينة والتصدير خارج الشاشة على النوافذ القصيرة أبدًا
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // الترويسة: العنوان والمدة الكلية الحيّة وأزرار الإضافة
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "المونتاج",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                // لا التفاف عمودي مهما كبر الخط — العنوان سطر واحد لا ينحشر
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                "المدة الكلية: ${formatMs(totalMs)} · ${project.clips.size} مقاطع" +
                        if (project.musicClips.isNotEmpty()) " · ${project.musicClips.size} موسيقى مرافقة" else "",
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
                text = "موسيقى مرافقة",
                enabled = !rendering,
                onClick = {
                    val fs = FilePickers.pickAudioFiles()
                    if (!fs.isNullOrEmpty()) vm.addMontageMusicFiles(fs)
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
            "مسطرة العمليات فوق الخط الزمني: اسحبها لتحديد منطقة زمنية تُحذف من كل المسارات بنقرة، وانقرها لتضع الرأس — عدة خطوط عمل تحت مسار التعليق تسحب مقاطعها أفقياً وبين الخطوط، وأداة التقسيم ✂ تشطر أي مقطع عند نقطة النقر، والنقر المزدوج على أي مقطع يفتح تسميته، والالتقاط يزمّر العلامات والحواف وشبكة نصف الثانية. أدوات المقطع الحديثة: قص/نسخ/لصق بحافظة المونتاج، تكرار ×N، دمج مع التالي، عكس الموجة، وتعديل السرعة دون تغيير النبرة وتغيير النبرة دون تغيير الطول — كل التحويلات مرتدات غير إتلافية والتراجع يعيد الأصل.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // قوالب جاهزة (v0.35.0 — البند 17): هيكل علامات وإعدادات موصى بها بنقرة
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "قوالب جاهزة:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            MontageTemplateCatalog.all.forEach { t ->
                SecondaryAction(
                    text = t.name,
                    enabled = !rendering,
                    onClick = { templateConfirm = t.id }
                )
            }
            SecondaryAction(
                text = "+ علامة عند الرأس",
                enabled = !rendering,
                onClick = { showMarkerDialog = true }
            )
        }

        // العلامات المسمّاة — رقائق بنقر تقفز إلى موضعها ومسح بجانبها
        if (project.markers.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                project.markers.sortedBy { it.tMs }.forEach { mk ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "⚑ ${mk.name} · ${formatMs(mk.tMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { vm.setMontagePlayhead(mk.tMs) }
                        )
                        Text(
                            "×",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable(enabled = !rendering) { vm.removeMontageMarker(mk.id) }
                        )
                    }
                }
            }
        }

        // شريط الأدوات الاحترافي: تراجع/إعادة/تقسيم/حذف/تطبيع/حفظ وفتح مشروع
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SecondaryAction(
                text = "تراجع",
                enabled = !rendering && canUndo,
                onClick = { vm.undoMontage() }
            )
            SecondaryAction(
                text = "إعادة",
                enabled = !rendering && canRedo,
                onClick = { vm.redoMontage() }
            )
            SecondaryAction(
                text = "تقسيم عند الرأس (S)",
                enabled = !rendering && project.clips.isNotEmpty(),
                onClick = { vm.splitMontageAtPlayhead() }
            )
            SecondaryAction(
                text = "حذف المحدد (Del)",
                enabled = !rendering && selectedId != null,
                onClick = { vm.deleteSelectedMontageClip() }
            )
            SecondaryAction(
                text = "تطبيع المحدد",
                enabled = !rendering && selectedId != null,
                onClick = { vm.normalizeSelectedMontageClip() }
            )
            SecondaryAction(
                text = "حفظ المشروع…",
                enabled = !rendering,
                onClick = {
                    val f = FilePickers.pickJsonSave(vm.montageProjectDefaultName())
                    if (f != null) vm.exportMontageProject(f)
                }
            )
            SecondaryAction(
                text = "فتح مشروع…",
                enabled = !rendering,
                onClick = {
                    val f = FilePickers.pickJsonOpen()
                    if (f != null) vm.importMontageProject(f)
                }
            )
        }

        // أدوات المقطع الحديثة (v0.38.0): الحافظة والتكرار والدمج والعكس والسرعة والنبرة
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            Text(
                "أدوات المقطع:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            SecondaryAction(
                text = "قص (X)",
                enabled = !rendering && (selectedId != null || selectedMusicId != null),
                onClick = { vm.montageCutSelected() }
            )
            SecondaryAction(
                text = "نسخ (C)",
                enabled = !rendering && (selectedId != null || selectedMusicId != null),
                onClick = { vm.montageCopySelected() }
            )
            SecondaryAction(
                text = "لصق (V)",
                enabled = !rendering && clipboard != null,
                onClick = { vm.montagePasteClipboard() }
            )
            SecondaryAction(
                text = "تكرار… (D)",
                enabled = !rendering && (selectedId != null || selectedMusicId != null),
                onClick = { showDuplicateDialog = true }
            )
            SecondaryAction(
                text = "دمج مع التالي (J)",
                enabled = !rendering && (selectedId != null || selectedMusicId != null),
                onClick = { vm.mergeMontageSelectedWithNext() }
            )
            SecondaryAction(
                text = "عكس الموجة",
                enabled = !rendering && (selectedId != null || selectedMusicId != null),
                onClick = { vm.reverseMontageSelected() }
            )
            SecondaryAction(
                text = "السرعة…",
                enabled = !rendering && (selectedId != null || selectedMusicId != null),
                onClick = { showSpeedDialog = true }
            )
            SecondaryAction(
                text = "النبرة…",
                enabled = !rendering && (selectedId != null || selectedMusicId != null),
                onClick = { showPitchDialog = true }
            )
            if (clipboard != null) {
                val label = when (val c = clipboard) {
                    is MontageLogic.MontageClipboardEntry.Voice -> "الحافظة: «${c.clip.title.take(18)}» (تعليق)"
                    is MontageLogic.MontageClipboardEntry.Free -> "الحافظة: «${c.clip.title.take(18)}» (مسار)"
                    null -> ""
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (project.clips.isEmpty() && project.musicClips.isEmpty()) {
            EmptyState(
                icon = AppIconKind.MONTAGE,
                title = "المونتاج فارغ",
                hint = "أضف مقاطع من تسجيلاتك أو من ملفاتك، ورتّبها وقصّها، ثم صدّرها ملفًا واحدًا جاهزًا للفاصل."
            )
        } else {
            MontageTimelineCard(
                vm = vm,
                project = project,
                envelopes = envelopes,
                selectedId = selectedId,
                selectedMusicId = selectedMusicId,
                selection = selection,
                tool = tool,
                snapEnabled = snapEnabled,
                playheadMs = playheadMs,
                loopStart = loopStart,
                loopEnd = loopEnd,
                loopEnabled = loopEnabled,
                vuLevel = vuLevel,
                rendering = rendering,
                pxPerSec = pxPerSec,
                onZoom = { wider ->
                    pxPerSec = (if (wider) pxPerSec * 1.4f else pxPerSec / 1.4f).coerceIn(15f, 800f)
                },
                onTrimStart = { vm.pushMontageHistory() },
                onTrim = { id, s, e ->
                    vm.updateMontageClip(id, { it.copy(startMs = s, endMs = e) }, record = false)
                },
                onMusicMoveStart = { vm.pushMontageHistory() },
                onMusicMove = { id, deltaMs ->
                    vm.moveMontageMusicClip(id, deltaMs, record = false)
                },
                onRenameVoice = { renameVoiceTarget = it },
                onRenameMusic = { renameMusicTarget = it }
            )

            // شريط عمليات المنطقة المحددة على المسطرة (v0.36.0):
            // تحدد على المسطرة → تحذف/تكرر/تعلّم/تكبّر بنقرة
            if (selection != null) {
                val sel = selection
                val (ss, se) = Pair(sel!!.first, sel.second)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "المنطقة المحددة: ${formatMs(ss)} ← ${formatMs(se)} (${(se - ss) / 1000.0} ثوانٍ)",
                        style = MaterialTheme.typography.labelLarge.numeric(),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    SecondaryAction(
                        text = "حذف المنطقة (R)",
                        enabled = !rendering,
                        onClick = { vm.deleteMontageRegion() }
                    )
                    SecondaryAction(
                        text = "حلقة من المحدد",
                        enabled = !rendering,
                        onClick = { vm.setMontageLoopFromSelection() }
                    )
                    SecondaryAction(
                        text = "علامة عند بدايته",
                        enabled = !rendering,
                        onClick = { showMarkerAtSelection = true }
                    )
                    SecondaryAction(
                        text = "تكبير على المحدد",
                        enabled = !rendering && se > ss,
                        onClick = {
                            // ملاءمة التكبير كي تملأ المنطقة العرض تقريبًا
                            val widthPx = 1000f
                            val target = (widthPx / ((se - ss).coerceAtLeast(200L).toFloat() / 1000f))
                                .coerceIn(15f, 800f)
                            pxPerSec = target
                        }
                    )
                    SecondaryAction(
                        text = "مسح التحديد (Esc)",
                        enabled = !rendering,
                        onClick = { vm.clearMontageSelection() }
                    )
                }
            }
        }

        // الفاصل والكروس فيد: تبادليان على حدود المقاطع
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val xfOn = project.crossfadeMs > 0L
            Text(
                if (xfOn) "الكروس فيد: ${project.crossfadeMs} مللي ثانية"
                else "الفاصل بين المقاطع: ${project.gapMs} مللي ثانية",
                style = MaterialTheme.typography.bodyMedium
            )
            SecondaryAction(
                text = "− 250",
                enabled = !rendering && (if (xfOn) project.crossfadeMs > 0 else project.gapMs > 0),
                onClick = {
                    if (xfOn) vm.setMontageCrossfade(project.crossfadeMs - 250)
                    else vm.setMontageGap(project.gapMs - 250)
                }
            )
            SecondaryAction(
                text = "+ 250",
                enabled = !rendering && (if (xfOn) project.crossfadeMs < MontageLogic.FADE_MAX_MS else project.gapMs < MontageLogic.GAP_MAX_MS),
                onClick = {
                    if (xfOn) vm.setMontageCrossfade(project.crossfadeMs + 250)
                    else vm.setMontageGap(project.gapMs + 250)
                }
            )
            SecondaryAction(
                text = if (xfOn) "إيقاف الكروس فيد" else "تفعيل الكروس فيد 1000",
                enabled = !rendering && project.clips.size >= 2,
                onClick = {
                    if (xfOn) vm.setMontageCrossfade(0L)
                    else vm.setMontageCrossfade(1000L)
                }
            )
            Text(
                if (xfOn) "المتجاورة تتداخل بقوة متساوية — بلا فاصل صمت"
                else "الكروس فيد يدمج نهاية كل مقطع ببداية تاليه بدل الصمت",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // الخفض التلقائي للسرير الموسيقي (v0.35.0 — البند 16): عمق قابل للضبط
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "الخفض التلقائي للموسيقى:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            SecondaryAction(
                text = if (project.duckingEnabled) "مفعّل" else "معطّل",
                enabled = !rendering && project.musicClips.isNotEmpty(),
                onClick = { vm.setMontageDuckingEnabled(!project.duckingEnabled) }
            )
            SecondaryAction(
                text = "أعمق −3",
                enabled = !rendering && project.duckDb > MontageLogic.DUCK_DB_MIN,
                onClick = { vm.setMontageDuckDb(project.duckDb - 3) }
            )
            Text(
                "عمق الخفض: ${project.duckDb}dB",
                style = MaterialTheme.typography.labelLarge.numeric(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            SecondaryAction(
                text = "أخف +3",
                enabled = !rendering && project.duckDb < MontageLogic.DUCK_DB_MAX,
                onClick = { vm.setMontageDuckDb(project.duckDb + 3) }
            )
            Text(
                if (project.musicClips.isEmpty()) "أضف موسيقى مرافقة أولًا — تُخفض آليًا تحت كلام المذيع وتعود بعد الإفلات"
                else "الموسيقى تنخفض تحت التعليق (هجوم ${project.duckAttackMs}ms وإفلات ${project.duckReleaseMs}ms) — سلوك المزج الإذاعي الحقيقي",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // قائمة المقاطع التفصيلية — النقر يحدد والخط الزمني يزامن
        // عمود عادي داخل تمرير الصفحة: كل الصفوف والقوائم مضمونة الوصول
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            project.clips.forEachIndexed { i, clip ->
                MontageClipRow(
                    index = i,
                    clip = clip,
                    count = project.clips.size,
                    enabled = !rendering,
                    selected = clip.id == selectedId,
                    onSelect = { vm.selectMontageClip(clip.id) },
                    onUp = { vm.moveMontageClip(clip.id, +1) },
                    onDown = { vm.moveMontageClip(clip.id, -1) },
                    onEdit = { editTarget = clip },
                    onRemove = { vm.removeMontageClip(clip.id) }
                )
            }
            if (project.musicClips.isNotEmpty()) {
                Text(
                        "مقاطع المسارات الحرة — كل مقطع على مساره بالمواضع المطلقة",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
            }
            val effTracks = remember(project.tracks) { MontageLogic.effectiveTracks(project) }
            project.musicClips.forEachIndexed { i, m ->
                val tName = MontageLogic.trackById(effTracks, MontageLogic.effectiveTrackId(m)).name
                MontageMusicRow(
                    index = i,
                    clip = m,
                    trackName = tName,
                    enabled = !rendering,
                    selected = m.id == selectedMusicId,
                    onSelect = { vm.selectMontageMusic(m.id) },
                    onEdit = { editMusicTarget = m },
                    onRemove = { vm.removeMontageMusicClip(m.id) }
                )
            }
        }

        // منتقي صيغة التصدير (v0.37.0): WAV للبث بلا فقد، أو MP3 بجودة قابلة للاختيار
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "صيغة التصدير:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            SecondaryAction(
                if (exportFormat == MontageExportFormat.WAV) "WAV ●" else "WAV",
                enabled = !rendering,
                onClick = { vm.setMontageExportFormat(MontageExportFormat.WAV) }
            )
            SecondaryAction(
                if (exportFormat == MontageExportFormat.MP3) "MP3 ●" else "MP3",
                enabled = !rendering,
                onClick = { vm.setMontageExportFormat(MontageExportFormat.MP3) }
            )
            if (exportFormat == MontageExportFormat.MP3) {
                val rates = MontageRender.MP3_BITRATES
                val idx = rates.indexOf(exportBitrate).coerceAtLeast(0)
                Text("الجودة:", style = MaterialTheme.typography.bodyMedium)
                SecondaryAction(
                    "−",
                    enabled = !rendering && idx > 0,
                    onClick = { vm.setMontageExportBitrate(rates[idx - 1]) }
                )
                Text(
                    "${exportBitrate}k",
                    style = MaterialTheme.typography.labelLarge.numeric(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                SecondaryAction(
                    "+",
                    enabled = !rendering && idx < rates.size - 1,
                    onClick = { vm.setMontageExportBitrate(rates[idx + 1]) }
                )
                Text(
                    "MP3 مضغوط للمشاركة والهاتف — ترميز LAME نقي على الجهاز",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "WAV بلا فقد جاهز للبث — اختر MP3 لمشاركة أخف حجمًا",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        // التذييل: معاينة من الرأس وتصدير وتقدم وإلغاء وإضافة للقائمة
        val hasSource = project.clips.any { File(it.path).isFile } ||
                project.musicClips.any { File(it.path).isFile }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryAction(
                text = if (playing) "إيقاف المعاينة مؤقتًا" else "معاينة من الرأس",
                enabled = !rendering && hasSource,
                onClick = { vm.toggleMontagePreview() }
            )
            PrimaryAction(
                text = if (exportFormat == MontageExportFormat.MP3) "تصدير MP3 ${exportBitrate}k" else "تصدير WAV",
                enabled = !rendering && hasSource,
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
        Text(
            "اختصارات الصفحة: نقر مزدوج على مقطع = تسميته · مسافة معاينة/إيقاف · S تقسيم عند الرأس · Delete حذف المحدد · R حذف المنطقة المحددة · G الالتقاط · Esc مسح التحديد · ←/→ تحريك الرأس (Shift: دقة 10ms) · ↑/↓ تحديد · Ctrl+Z/Y تراجع/إعادة",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
                vm.updateMontageClip(clip.id, {
                    it.copy(startMs = s, endMs = e, volumePercent = vol, fadeInMs = fi, fadeOutMs = fo)
                })
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    // حوار تحرير مقطع الموسيقى المرافقة: الموضع المطلق + النافذة والمستوى والتلاشين
    editMusicTarget?.let { m ->
        MontageMusicEditDialog(
            clip = m,
            onSave = { pos, s, e, vol, fi, fo ->
                vm.updateMontageMusicClip(m.id, {
                    it.copy(startMs = pos, startInFileMs = s, endInFileMs = e,
                        volumePercent = vol, fadeInMs = fi, fadeOutMs = fo)
                })
                editMusicTarget = null
            },
            onDismiss = { editMusicTarget = null }
        )
    }

    // حوارا التسمية بالنقر المزدوج (v0.37.0) — تعليقًا كان أو مقطع مسار حر
    renameVoiceTarget?.let { clip ->
        MontageRenameDialog(
            initial = clip.title,
            onConfirm = { name ->
                vm.renameMontageClip(clip.id, name)
                renameVoiceTarget = null
            },
            onDismiss = { renameVoiceTarget = null }
        )
    }
    renameMusicTarget?.let { m ->
        MontageRenameDialog(
            initial = m.title,
            onConfirm = { name ->
                vm.renameMontageMusicClip(m.id, name)
                renameMusicTarget = null
            },
            onDismiss = { renameMusicTarget = null }
        )
    }

    // حوار إضافة علامة عند الرأس — الاسم فارغ يعني «الفاصل»
    if (showMarkerDialog) {
        var markerName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMarkerDialog = false },
            title = { Text("علامة عند الرأس (${formatMs(playheadMs)})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "اكتب اسم العلامة — «الفاصل» و«النشيد» من القوالب الجاهزة، وهذا حقل حر لعلاماتك.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = markerName,
                        onValueChange = { markerName = it.take(24) },
                        placeholder = { Text("الفاصل") },
                        modifier = Modifier.fillMaxWidth().observesTextEditing(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addMontageMarkerAtPlayhead(markerName)
                    showMarkerDialog = false
                }) { Text("إضافة العلامة") }
            },
            dismissButton = {
                TextButton(onClick = { showMarkerDialog = false }) { Text("إلغاء") }
            }
        )
    }

    // حوار علامة عند بداية المنطقة المحددة على المسطرة (v0.36.0)
    if (showMarkerAtSelection) {
        var markerName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showMarkerAtSelection = false },
            title = { Text("علامة عند بداية المنطقة (${formatMs(selection?.first ?: 0L)})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "تُوضع العلامة عند بداية المنطقة المحددة على المسطرة — قفزات لاحقة تصل إليها بنقرة من رقائق العلامات.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = markerName,
                        onValueChange = { markerName = it.take(24) },
                        placeholder = { Text("الفاصل") },
                        modifier = Modifier.fillMaxWidth().observesTextEditing(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.addMontageMarkerAtSelectionStart(markerName)
                    showMarkerAtSelection = false
                }) { Text("إضافة العلامة") }
            },
            dismissButton = {
                TextButton(onClick = { showMarkerAtSelection = false }) { Text("إلغاء") }
            }
        )
    }

    // تأكيد تطبيق قالب جاهز — يستبدل العلامات والفاصل/الكروس فيد/الخفض بالموصى به
    templateConfirm?.let { tid ->
        val t = MontageTemplateCatalog.byId(tid)
        if (t != null) {
            AlertDialog(
                onDismissRequest = { templateConfirm = null },
                title = { Text("تطبيق قالب «${t.name}»؟") },
                text = {
                    Text(
                        t.description + "\n\nسيُولَّد " + t.markers.size +
                                " علامات مسمّاة على الخط الزمني، ويُعرض الفاصل/الكروس فيد والخفض التلقائي الموصى بهما — مقاطعك وموسيقاك لا تُمسّ."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.applyMontageTemplate(tid)
                        templateConfirm = null
                    }) { Text("تطبيق") }
                },
                dismissButton = {
                    TextButton(onClick = { templateConfirm = null }) { Text("إلغاء") }
                }
            )
        } else templateConfirm = null
    }

    // v0.38.0: حوارات أدوات المقطع — السرعة والنبرة والتكرار
    if (showSpeedDialog) {
        MontageSpeedDialog(
            onApply = { pct ->
                showSpeedDialog = false
                vm.speedMontageSelected(pct)
            },
            onDismiss = { showSpeedDialog = false }
        )
    }
    if (showPitchDialog) {
        MontagePitchDialog(
            onApply = { st ->
                showPitchDialog = false
                vm.pitchMontageSelected(st)
            },
            onDismiss = { showPitchDialog = false }
        )
    }
    if (showDuplicateDialog) {
        MontageDuplicateDialog(
            onApply = { n ->
                showDuplicateDialog = false
                vm.duplicateMontageSelected(n)
            },
            onDismiss = { showDuplicateDialog = false }
        )
    }
}

// ================= أدوات الموضع والمغلّفات =================

/** المغلّف وفهرس دلوِه عند موضع زمني على الخط الزمني الكلي — للويڤ فورم وVU. */
internal fun envAndBucketAt(
    project: MontageProject,
    playheadMs: Long,
    envelopes: Map<String, MontageEnvelope>
): Pair<MontageEnvelope, Int>? {
    if (project.clips.isEmpty()) return null
    val starts = MontageLogic.clipStartMs(project.clips, project.gapMs, project.crossfadeMs)
    for (i in project.clips.indices) {
        val s = starts[i]
        val d = MontageLogic.clipDurationMs(project.clips[i])
        if (playheadMs >= s && playheadMs < s + d) {
            val env = envelopes[project.clips[i].id] ?: return null
            val bucket = if (d > 0) ((playheadMs - s) * env.peaks.size / d).toInt() else 0
            return env to bucket.coerceIn(0, env.peaks.size - 1)
        }
    }
    return null
}

// ================= بطاقة الخط الزمني =================

/** ارتفاعات هندسة الخط الزمني الجديد (v0.36.0): مسطرة + مسار تعليق + مسارات حرة. */
private val RULER_LANE_DP = 34.dp
private val VOICE_LANE_DP = 150.dp
private val FREE_LANE_DP = 56.dp
private val TRACK_HEADERS_W_DP = 168.dp

/**
 * بطاقة الخط الزمني (v0.36.0 «مسطرة العمليات وعدة خطوط عمل»): عمود رؤوس
 * مسارات ثابت — مسار التعليق ثم المسارات الحرة بمستواها (بلا كتم ولا منفرد
 * منذ v0.37.0) —
 * وبجانبه خط زمني مشترك التمرير: مسطرة عمليات تسحب عليها لتحديد منطقة زمنية،
 * ومسار التعليق، وكل مسار حر بخطه الخاص. الأدوات أعلاه: تكبير وتقسيم والتقاط.
 * الاتجاه داخل الخط الزمني LTR — عرف المحررات الصوتية حتى في الواجهات العربية.
 */
@Composable
private fun MontageTimelineCard(
    vm: AppViewModel,
    project: MontageProject,
    envelopes: Map<String, MontageEnvelope>,
    selectedId: String?,
    selectedMusicId: String?,
    selection: Pair<Long, Long>?,
    tool: MontageTool,
    snapEnabled: Boolean,
    playheadMs: Long,
    loopStart: Long?,
    loopEnd: Long?,
    loopEnabled: Boolean,
    vuLevel: Float,
    rendering: Boolean,
    pxPerSec: Float,
    onZoom: (wider: Boolean) -> Unit,
    onTrimStart: () -> Unit,
    onTrim: (clipId: String, startMs: Long, endMs: Long) -> Unit,
    onMusicMoveStart: () -> Unit,
    onMusicMove: (clipId: String, deltaMs: Long) -> Unit,
    onRenameVoice: (MontageClip) -> Unit,
    onRenameMusic: (MontageMusicClip) -> Unit
) {
    val totalMs = remember(project) { MontageLogic.totalDurationFull(project) }
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val contentWidthPx = totalMs.toFloat() * pxPerSec / 1000f + 80f
    val tracks = remember(project.tracks) { MontageLogic.effectiveTracks(project) }

    // مرشحات التقاط: علامات وحواف المقاطع + شبكة نصف الثانية حتى نهاية الخط
    val snapCandidates = remember(project) {
        MontageLogic.snapCandidates(project) +
                (0L..(totalMs + MontageLogic.SNAP_GRID_MS) step MontageLogic.SNAP_GRID_MS)
    }
    fun snap(ms: Long): Long =
        if (snapEnabled) MontageLogic.snapMs(ms, snapCandidates) else ms.coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, LocalStudioColors.current.cardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // صف الأدوات: تكبير + حلقة A/B + أداة التقسيم + الالتقاط
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("تكبير:", style = MaterialTheme.typography.labelMedium)
            SecondaryAction("−", enabled = pxPerSec > 16f, onClick = { onZoom(false) })
            Text(
                "${pxPerSec.toInt()}px/s",
                style = MaterialTheme.typography.labelMedium.numeric(),
                color = MaterialTheme.colorScheme.primary
            )
            SecondaryAction("+", enabled = pxPerSec < 800f, onClick = { onZoom(true) })
            Text(
                "أداة:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
            SecondaryAction(
                if (tool == MontageTool.SELECT) "تحديد ●" else "تحديد",
                enabled = !rendering,
                onClick = { vm.setMontageTool(MontageTool.SELECT) }
            )
            SecondaryAction(
                if (tool == MontageTool.SPLIT) "تقسيم ✂ ●" else "تقسيم ✂",
                enabled = !rendering,
                onClick = { vm.setMontageTool(MontageTool.SPLIT) }
            )
            SecondaryAction(
                if (snapEnabled) "التقاط ●" else "التقاط",
                enabled = !rendering,
                onClick = { vm.toggleMontageSnap() }
            )
            Text(
                "حلقة A/B:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
            SecondaryAction("A", enabled = !rendering, onClick = { vm.setMontageLoopStartAtPlayhead() })
            SecondaryAction("B", enabled = !rendering, onClick = { vm.setMontageLoopEndAtPlayhead() })
            SecondaryAction(
                if (loopEnabled) "إيقاف الحلقة" else "تشغيل الحلقة",
                enabled = loopStart != null && loopEnd != null && (loopEnd ?: 0L) > (loopStart ?: 1L),
                onClick = { vm.toggleMontageLoop() }
            )
            SecondaryAction(
                "مسح",
                enabled = loopStart != null || loopEnd != null,
                onClick = { vm.clearMontageLoop() }
            )
        }
        Text(
            "المسطرة فوق: اسحب عليها لتحديد منطقة زمنية ثم احذفها من كل المسارات، وانقر لتضع الرأس — أداة التقسيم ✂ تشطر أي مقطع عند نقطة النقر، والنقر المزدوج يفتح تسمية أي مقطع — اسحب جسم مقطع المسارات لتحريكه بين الخطوط، وحوافه لتقليمه، والالتقاط يزمّر العلامات والحواف والشبكة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            // الخريطة المصغرة: نظرة عامة على المشروع كله + مستطيل العرض الحالي
            MontageMiniMap(
                project = project,
                envelopes = envelopes,
                selectedId = selectedId,
                playheadMs = playheadMs,
                contentWidthPx = contentWidthPx,
                scrollValue = { scroll.value },
                scrollMax = { scroll.maxValue },
                onPan = { fraction ->
                    scope.launch {
                        val target = (fraction * contentWidthPx).toInt() - (contentWidthPx - scroll.maxValue).toInt() / 2
                        scroll.scrollTo(target.coerceIn(0, scroll.maxValue.coerceAtLeast(0)))
                    }
                }
            )
            // رؤوس المسارات + الخطوط الزمنية المشتركة التمرير
            Row(modifier = Modifier.fillMaxWidth()) {
                // عمود الرؤوس الثابت: مسطرة ثم مسار التعليق ثم المسارات الحرة
                Column(
                    modifier = Modifier.width(TRACK_HEADERS_W_DP),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(RULER_LANE_DP),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            "اسحب المسطرة لتحديد منطقة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MontageVoiceHeaderCell(VOICE_LANE_DP)
                    tracks.forEach { t ->
                        MontageTrackHeaderCell(
                            track = t,
                            height = FREE_LANE_DP,
                            clipCount = MontageLogic.clipsOfTrack(project, t.id).size,
                            enabled = !rendering,
                            onVolumeDown = { vm.setMontageTrackVolume(t.id, t.volumePercent - 10) },
                            onVolumeUp = { vm.setMontageTrackVolume(t.id, t.volumePercent + 10) },
                            onDelete = { vm.removeMontageTrack(t.id) }
                        )
                    }
                    SecondaryAction(
                        text = "+ مسار عمل جديد",
                        enabled = !rendering && tracks.size < MontageLogic.MAX_TRACKS,
                        onClick = { vm.addMontageTrack() }
                    )
                }
                Column(
                    modifier = Modifier.weight(1f).horizontalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // مسطرة العمليات: علامات وتكات ومطابقة منطقة ورأس تشغيل
                    MontageRulerCanvas(
                        project = project,
                        selection = selection,
                        loopStart = loopStart,
                        loopEnd = loopEnd,
                        loopEnabled = loopEnabled,
                        playheadMs = playheadMs,
                        pxPerSec = pxPerSec,
                        onSeek = { vm.setMontagePlayhead(it) },
                        onSelectRegion = { s, e -> vm.setMontageSelection(s, e) },
                        onClearSelection = { vm.clearMontageSelection() }
                    )
                    // مسار التعليق — البرنامج المتسلسل
                    MontageVoiceLaneCanvas(
                        vm = vm,
                        project = project,
                        envelopes = envelopes,
                        selectedId = selectedId,
                        selection = selection,
                        tool = tool,
                        playheadMs = playheadMs,
                        pxPerSec = pxPerSec,
                        snap = ::snap,
                        onTrimStart = onTrimStart,
                        onTrim = onTrim,
                        onRename = onRenameVoice
                    )
                    // المسارات الحرة — عدة خطوط عمل بالسحب بينها
                    MontageFreeTracksCanvas(
                        vm = vm,
                        project = project,
                        tracks = tracks,
                        envelopes = envelopes,
                        selectedId = selectedMusicId,
                        selection = selection,
                        tool = tool,
                        playheadMs = playheadMs,
                        pxPerSec = pxPerSec,
                        snap = ::snap,
                        onMoveStart = onMusicMoveStart,
                        onMove = onMusicMove,
                        onRename = onRenameMusic
                    )
                }
            }
        }

        // صف VU: مقياس المستوى + زمن الرأس + تحذير التشبع
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("المستوى:", style = MaterialTheme.typography.labelMedium)
            LevelBarH(
                level = vuLevel.coerceIn(0f, 1f),
                modifier = Modifier.weight(1f).height(12.dp)
            )
            Text(
                "الرأس: ${formatMs(playheadMs)}",
                style = MaterialTheme.typography.labelLarge.numeric(),
                color = MaterialTheme.colorScheme.primary
            )
            if (vuLevel >= MontageLogic.CLIP_WARN_LEVEL) {
                StatusChip("قرب التشبع", Color(0xFFE53935))
            }
        }
    }
}

// ================= رؤوس المسارات =================

/** رأس مسار التعليق — ثابت لا يُكتم ولا يُحذف فهو البرنامج. */
@Composable
private fun MontageVoiceHeaderCell(height: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
            Text(
                "مسار التعليق",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                "البرنامج — متسلسل بلا كتم ولا منفرد",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** رأس مسار حر: لون واسم ومستوى وحذف — بلا كتم ولا منفرد منذ v0.37.0 (نفس عقد مسار التعليق). */
@Composable
private fun MontageTrackHeaderCell(
    track: com.saidjte.schoolradio.desktop.data.MontageTrack,
    height: androidx.compose.ui.unit.Dp,
    clipCount: Int,
    enabled: Boolean,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onDelete: () -> Unit
) {
    val color = Color(MontageLogic.TRACK_COLORS[Math.floorMod(track.colorIndex, MontageLogic.TRACK_COLORS.size)])
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(if (track.solo) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${track.volumePercent}٪ · $clipCount مقاطع",
                style = MaterialTheme.typography.labelSmall.numeric(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MiniToolButton(text = "−", active = false, enabled = enabled && track.volumePercent > MontageLogic.VOLUME_MIN, tint = MaterialTheme.colorScheme.primary, onClick = onVolumeDown)
            MiniToolButton(text = "+", active = false, enabled = enabled && track.volumePercent < MontageLogic.VOLUME_MAX, tint = MaterialTheme.colorScheme.primary, onClick = onVolumeUp)
        }
        MiniToolButton(text = "×", active = false, enabled = enabled, tint = MaterialTheme.colorScheme.onSurfaceVariant, onClick = onDelete)
    }
}

/** زر أداة مصغّر لرؤوس المسارات — حالة نشطة بلون قوي. */
@Composable
private fun MiniToolButton(
    text: String,
    active: Boolean,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (active) tint.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else tint,
            fontWeight = FontWeight.Bold
        )
    }
}

// ================= مسطرة العمليات (v0.36.0) =================

/**
 * مسطرة العمليات: تكات زمنية وعلامات مسمّاة ومنطقة الحلقة والرأس — وفوق
 * ذلك مطابقة المنطقة: السحب عليها يحدد [بداية، نهاية) عبر كل المسارات،
 * والنقر يضع الرأس ويمسح التحديد. هنا تُنفَّذ «حدّد الجزء الذي تريد حذفه»
 * ثم يُحذف من شريط المنطقة أعلى البطاقة أو بمفتاح R.
 */
@Composable
private fun MontageRulerCanvas(
    project: MontageProject,
    selection: Pair<Long, Long>?,
    loopStart: Long?,
    loopEnd: Long?,
    loopEnabled: Boolean,
    playheadMs: Long,
    pxPerSec: Float,
    onSeek: (Long) -> Unit,
    onSelectRegion: (Long, Long) -> Unit,
    onClearSelection: () -> Unit
) {
    val totalMs = remember(project) { MontageLogic.totalDurationFull(project).coerceAtLeast(1L) }
    val density = LocalDensity.current
    val widthDp = with(density) {
        ((totalMs.toFloat() * pxPerSec / 1000f + 80f) / density.density).dp.coerceAtLeast(320.dp)
    }
    val pxPerMs = pxPerSec / 1000f
    val pad = 10f
    val textMeasurer = rememberTextMeasurer()
    var anchorMs by remember { mutableStateOf(0L) }
    val seekState = rememberUpdatedState(onSeek)
    val selectState = rememberUpdatedState(onSelectRegion)
    val clearState = rememberUpdatedState(onClearSelection)
    val ppmState = rememberUpdatedState(pxPerMs)

    val cOnSurfVar = MaterialTheme.colorScheme.onSurfaceVariant
    val cTertiary = MaterialTheme.colorScheme.tertiary
    val cPrimary = MaterialTheme.colorScheme.primary
    val cBg = MaterialTheme.colorScheme.surfaceVariant
    val amber = Color(0xFFFFB300)

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(RULER_LANE_DP)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { off ->
                        val ms = ((off.x - pad) / ppmState.value).toLong().coerceAtLeast(0L)
                        clearState.value()
                        seekState.value(ms)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { off ->
                        anchorMs = ((off.x - pad) / ppmState.value).toLong().coerceAtLeast(0L)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val ms = ((change.position.x - pad) / ppmState.value).toLong().coerceAtLeast(0L)
                        selectState.value(anchorMs, ms)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            drawRoundRect(
                color = cBg.copy(alpha = 0.30f),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(10f, 10f)
            )
            // تكات الزمن تتكيف مع التكبير
            val stepSec = when {
                pxPerSec >= 120f -> 1
                pxPerSec >= 45f -> 2
                else -> 5
            }
            val totalSec = (totalMs / 1000L).toInt()
            var tSec = 0
            while (tSec <= totalSec + stepSec) {
                val x = pad + tSec * 1000f * pxPerMs
                if (x > size.width) break
                drawLine(
                    color = cOnSurfVar.copy(alpha = 0.35f),
                    start = Offset(x, h - 6f),
                    end = Offset(x, h),
                    strokeWidth = 1.5f
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = formatMs(tSec * 1000L),
                    topLeft = Offset(x + 3f, 2f),
                    style = TextStyle(fontSize = 9.sp, color = cOnSurfVar).numeric()
                )
                tSec += stepSec
            }
            // منطقة الحلقة A/B
            if (loopEnabled && loopStart != null && loopEnd != null && loopEnd > loopStart) {
                val ax = pad + loopStart * pxPerMs
                val bx = pad + loopEnd * pxPerMs
                drawRect(
                    color = cTertiary.copy(alpha = 0.16f),
                    topLeft = Offset(ax, 0f),
                    size = Size(bx - ax, h)
                )
                drawLine(cTertiary, Offset(ax, 0f), Offset(ax, h), 2f)
                drawLine(cTertiary, Offset(bx, 0f), Offset(bx, h), 2f)
            }
            // العلامات المسمّاة: علم كهرماني ثلاثي على المسطرة
            for (mk in project.markers.sortedBy { it.tMs }) {
                val mx = pad + mk.tMs * pxPerMs
                if (mx < 0f || mx > size.width) continue
                drawLine(
                    color = cTertiary.copy(alpha = 0.85f),
                    start = Offset(mx, h * 0.45f),
                    end = Offset(mx, h),
                    strokeWidth = 1.5f
                )
                drawPath(
                    color = cTertiary,
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(mx, h * 0.45f - 9f)
                        lineTo(mx + 10f, h * 0.45f - 4.5f)
                        lineTo(mx, h * 0.45f)
                        close()
                    }
                )
            }
            // المنطقة المحددة — تُسحب من هنا وتُحذف من كل المسارات
            if (selection != null) {
                val ax = pad + selection.first * pxPerMs
                val bx = pad + selection.second * pxPerMs
                drawRect(
                    color = cPrimary.copy(alpha = 0.30f),
                    topLeft = Offset(ax, 0f),
                    size = Size((bx - ax).coerceAtLeast(2f), h)
                )
                drawLine(cPrimary, Offset(ax, 0f), Offset(ax, h), 2f)
                drawLine(cPrimary, Offset(bx, 0f), Offset(bx, h), 2f)
            }
            // رأس التشغيل بمثلثه
            val px = pad + playheadMs * pxPerMs
            if (px >= 0f && px <= size.width) {
                drawLine(amber, Offset(px, 0f), Offset(px, h), 2.5f)
                drawPath(
                    color = amber,
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(px - 6f, 0f)
                        lineTo(px + 6f, 0f)
                        lineTo(px, 10f)
                        close()
                    }
                )
            }
        }
    }
}

// ================= مسار التعليق (الرسم والسحب) =================

/** أوضاع السحب على مسار التعليق: تقليم الحافتين أو إعادة ترتيب أو تحريك الرأس. */
private sealed interface VoiceDrag {
    data object None : VoiceDrag
    data class TrimLeft(val clipId: String, val origStart: Long) : VoiceDrag
    data class TrimRight(val clipId: String, val origEnd: Long) : VoiceDrag
    data class Reorder(val clipId: String, var acc: Float) : VoiceDrag
    data object Scrub : VoiceDrag
}

/**
 * مسار التعليق: مقاطع متسلسلة بموجاتها — الضغط والسحب يقصّم من الحواف ويعيد
 * الترتيب، وأداة التقسيم ✂ تشطر المقطع عند نقطة النقر، والمنطقة المحددة
 * على المسطرة تُظلل فوقه فالمذيع يرى ما سيُحذف قبل أن يحذفه.
 */
@Composable
private fun MontageVoiceLaneCanvas(
    vm: AppViewModel,
    project: MontageProject,
    envelopes: Map<String, MontageEnvelope>,
    selectedId: String?,
    selection: Pair<Long, Long>?,
    tool: MontageTool,
    playheadMs: Long,
    pxPerSec: Float,
    snap: (Long) -> Long,
    onTrimStart: () -> Unit,
    onTrim: (clipId: String, startMs: Long, endMs: Long) -> Unit,
    onRename: (MontageClip) -> Unit
) {
    val totalMs = remember(project) {
        MontageLogic.totalDurationMs(project.clips, project.gapMs, project.crossfadeMs)
    }
    val density = LocalDensity.current
    val widthDp = with(density) {
        ((totalMs.toFloat() * pxPerSec / 1000f + 80f) / density.density).dp.coerceAtLeast(320.dp)
    }
    val textMeasurer = rememberTextMeasurer()
    var drag by remember { mutableStateOf<VoiceDrag>(VoiceDrag.None) }
    var dragClipId by remember { mutableStateOf<String?>(null) }
    var scrubMs by remember { mutableStateOf(0L) }

    val pxPerMs = pxPerSec / 1000f
    val pad = 10f
    val startsMs = remember(project) {
        MontageLogic.clipStartMs(project.clips, project.gapMs, project.crossfadeMs)
    }

    val projState = rememberUpdatedState(project)
    val startsState = rememberUpdatedState(startsMs)
    val pxPerMsState = rememberUpdatedState(pxPerMs)
    val trimState = rememberUpdatedState(onTrim)
    val trimStartState = rememberUpdatedState(onTrimStart)
    val snapState = rememberUpdatedState(snap)
    val toolState = rememberUpdatedState(tool)
    val renameState = rememberUpdatedState(onRename)

    fun liveClipX(i: Int): Float = pad + startsState.value[i] * pxPerMsState.value
    fun liveClipW(i: Int): Float =
        max(6f, MontageLogic.clipDurationMs(projState.value.clips[i]) * pxPerMsState.value)
    fun liveClipAt(x: Float): Int = projState.value.clips.indices.firstOrNull {
        x >= liveClipX(it) && x <= liveClipX(it) + liveClipW(it)
    } ?: -1

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(VOICE_LANE_DP)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { off ->
                        val project = projState.value
                        val ppm = pxPerMsState.value
                        val rawMs = ((off.x - pad) / ppm).toLong().coerceAtLeast(0L)
                        val idx = liveClipAt(off.x)
                        if (toolState.value == MontageTool.SPLIT && idx >= 0) {
                            vm.splitMontageVoiceClipAtMs(project.clips[idx].id, snapState.value(rawMs))
                        } else {
                            vm.setMontagePlayhead(rawMs)
                            if (idx >= 0) vm.selectMontageClip(project.clips[idx].id)
                        }
                    },
                    onDoubleTap = { off ->
                        // النقر المزدوج على مقطع يفتح تسميته (v0.37.0) — بأي أداة
                        val idx = liveClipAt(off.x)
                        if (idx >= 0) renameState.value(projState.value.clips[idx])
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { off ->
                        val project = projState.value
                        val ppm = pxPerMsState.value
                        val idx = liveClipAt(off.x)
                        fun cx(i: Int) = pad + startsState.value[i] * ppm
                        fun cw(i: Int) = max(6f, MontageLogic.clipDurationMs(project.clips[i]) * ppm)
                        when {
                            idx < 0 -> drag = VoiceDrag.Scrub
                            off.x - cx(idx) <= 10f -> {
                                drag = VoiceDrag.TrimLeft(project.clips[idx].id, project.clips[idx].startMs)
                                trimStartState.value()
                            }
                            cx(idx) + cw(idx) - off.x <= 10f -> {
                                drag = VoiceDrag.TrimRight(project.clips[idx].id, project.clips[idx].endMs)
                                trimStartState.value()
                            }
                            else -> drag = VoiceDrag.Reorder(project.clips[idx].id, 0f)
                        }
                        dragClipId = if (idx >= 0) project.clips[idx].id else null
                    },
                    onDrag = { change, amt ->
                        change.consume()
                        val project = projState.value
                        val ppm = pxPerMsState.value
                        val x = change.position.x
                        when (val d = drag) {
                            is VoiceDrag.Scrub -> {
                                scrubMs = ((x - pad) / ppm).toLong().coerceAtLeast(0L)
                                vm.setMontagePlayhead(scrubMs, persist = false)
                            }
                            is VoiceDrag.TrimLeft -> {
                                val clip = project.clips.firstOrNull { it.id == d.clipId }
                                if (clip != null) {
                                    val dxMs = (amt.x / ppm).toLong()
                                    val s = (d.origStart + dxMs).coerceIn(0L, clip.endMs - MontageLogic.MIN_CLIP_MS)
                                    trimState.value(d.clipId, s, clip.endMs)
                                }
                            }
                            is VoiceDrag.TrimRight -> {
                                val clip = project.clips.firstOrNull { it.id == d.clipId }
                                if (clip != null) {
                                    val dxMs = (amt.x / ppm).toLong()
                                    val e = (d.origEnd + dxMs).coerceIn(clip.startMs + MontageLogic.MIN_CLIP_MS, clip.durationMs)
                                    trimState.value(d.clipId, clip.startMs, e)
                                }
                            }
                            is VoiceDrag.Reorder -> {
                                d.acc += amt.x
                                if (abs(d.acc) >= 40f) {
                                    vm.moveMontageClip(d.clipId, if (d.acc > 0) 1 else -1)
                                    d.acc = 0f
                                }
                            }
                            VoiceDrag.None -> { }
                        }
                    },
                    onDragEnd = {
                        if (drag is VoiceDrag.Scrub) vm.setMontagePlayhead(scrubMs)
                        drag = VoiceDrag.None
                        dragClipId = null
                    },
                    onDragCancel = {
                        drag = VoiceDrag.None
                        dragClipId = null
                    }
                )
            }
    ) {
        val cPrimary = MaterialTheme.colorScheme.primary
        val cSurface = MaterialTheme.colorScheme.surface
        val cOutline = MaterialTheme.colorScheme.outlineVariant
        val cOnSurfVar = MaterialTheme.colorScheme.onSurfaceVariant
        val cOnSurf = MaterialTheme.colorScheme.onSurface
        val cErr = MaterialTheme.colorScheme.error
        val cSecondaryContainer = MaterialTheme.colorScheme.secondaryContainer
        val cBg = MaterialTheme.colorScheme.surfaceVariant

        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val clipTop = 8f
            val clipBottom = h - 12f
            val blockH = clipBottom - clipTop
            // خلفية الموجات
            drawRoundRect(
                color = cBg.copy(alpha = 0.30f),
                topLeft = Offset(0f, 0f),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(10f, 10f)
            )

            fun clipX(i: Int): Float = pad + startsMs[i] * pxPerMs
            fun clipW(i: Int): Float =
                max(6f, MontageLogic.clipDurationMs(project.clips[i]) * pxPerMs)

            // المنطقة المحددة تُظلل عبر مسار التعليق أيضًا
            if (selection != null) {
                val ax = pad + selection.first * pxPerMs
                val bx = pad + selection.second * pxPerMs
                drawRect(
                    color = cPrimary.copy(alpha = 0.14f),
                    topLeft = Offset(ax.coerceAtLeast(0f), 0f),
                    size = Size((bx - ax).coerceAtLeast(2f), h)
                )
            }

            // الفواصل: صمت بين المقاطع حين لا كروس فيد
            if (project.crossfadeMs <= 0L && project.gapMs > 0L) {
                for (i in 0 until project.clips.size - 1) {
                    val gx0 = clipX(i) + clipW(i)
                    val gx1 = clipX(i + 1)
                    if (gx1 > gx0) {
                        drawRect(
                            color = cOnSurfVar.copy(alpha = 0.10f),
                            topLeft = Offset(gx0, clipTop),
                            size = Size(gx1 - gx0, blockH)
                        )
                        if (gx1 - gx0 > 26f) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "فاصل",
                                topLeft = Offset(gx0 + 4f, clipTop + 4f),
                                style = TextStyle(fontSize = 9.sp, color = cOnSurfVar)
                            )
                        }
                    }
                }
            }

            // جسور الكروس فيد: X متقاطعان فوق منطقة التداخل
            if (project.crossfadeMs > 0L) {
                for (i in 0 until project.clips.size - 1) {
                    val d0 = MontageLogic.clipDurationMs(project.clips[i])
                    val d1 = MontageLogic.clipDurationMs(project.clips[i + 1])
                    val xf = MontageLogic.effectiveOverlap(d0, d1, project.crossfadeMs)
                    if (xf > 0L) {
                        val x0 = pad + (startsMs[i] + d0 - xf) * pxPerMs
                        val x1 = pad + (startsMs[i + 1] + xf) * pxPerMs
                        drawRect(
                            color = cPrimary.copy(alpha = 0.16f),
                            topLeft = Offset(x0, clipTop),
                            size = Size(x1 - x0, blockH)
                        )
                        drawLine(
                            color = cPrimary.copy(alpha = 0.6f),
                            start = Offset(x0, clipTop),
                            end = Offset(x1, clipBottom),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = cPrimary.copy(alpha = 0.6f),
                            start = Offset(x0, clipBottom),
                            end = Offset(x1, clipTop),
                            strokeWidth = 2f
                        )
                    }
                }
            }

            // المقاطع: مستطيل + ويڤ فورم + عنوان + مقابض القصّ للمحدد
            for (i in project.clips.indices) {
                val clip = project.clips[i]
                val cx = clipX(i)
                val cw = clipW(i)
                val missing = !File(clip.path).isFile
                val sel = clip.id == selectedId
                drawRoundRect(
                    color = if (missing) cErr.copy(alpha = 0.25f) else cSurface.copy(alpha = 0.92f),
                    topLeft = Offset(cx, clipTop),
                    size = Size(cw, blockH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = when {
                        dragClipId == clip.id -> cPrimary
                        sel -> cPrimary
                        missing -> cErr
                        else -> cOutline
                    },
                    topLeft = Offset(cx, clipTop),
                    size = Size(cw, blockH),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (sel) 2.5f else 1f)
                )
                // الويڤ فورم من المغلّف المحسوب
                val env = envelopes[clip.id]
                if (env != null && env.peaks.isNotEmpty()) {
                    val n = env.peaks.size
                    val midY = clipTop + blockH / 2f
                    val barW = max(1.2f, cw / n - 0.6f)
                    for (b in 0 until n) {
                        val bx = cx + (b + 0.5f) / n * cw
                        if (bx < cx + 2f || bx > cx + cw - 2f) continue
                        val halfH = env.peaks[b] * (blockH / 2f - 3f)
                        if (halfH <= 0.4f) continue
                        drawLine(
                            color = if (env.clippedAt(b)) cErr else cPrimary.copy(alpha = 0.85f),
                            start = Offset(bx, midY - halfH),
                            end = Offset(bx, midY + halfH),
                            strokeWidth = barW,
                            cap = StrokeCap.Round
                        )
                    }
                } else {
                    drawLine(
                        color = cOnSurfVar.copy(alpha = 0.35f),
                        start = Offset(cx + 4f, clipTop + blockH / 2f),
                        end = Offset(cx + cw - 4f, clipTop + blockH / 2f),
                        strokeWidth = 1.5f
                    )
                }
                if (cw > 70f) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${i + 1}. ${clip.title.take(16)}",
                        topLeft = Offset(cx + 6f, clipTop + 3f),
                        style = TextStyle(fontSize = 10.sp, color = cOnSurf)
                    )
                }
                // مقابض القصّ عند المحدد
                if (sel && !missing) {
                    drawRoundRect(
                        color = cSecondaryContainer,
                        topLeft = Offset(cx + 1f, clipTop + 3f),
                        size = Size(6f, blockH - 6f),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                    drawRoundRect(
                        color = cSecondaryContainer,
                        topLeft = Offset(cx + cw - 7f, clipTop + 3f),
                        size = Size(6f, blockH - 6f),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
            }

            // رأس التشغيل: خط كهرماني
            val px = pad + playheadMs * pxPerMs
            if (px >= 0f && px <= size.width) {
                drawLine(
                    color = Color(0xFFFFB300),
                    start = Offset(px, 0f),
                    end = Offset(px, h),
                    strokeWidth = 2.5f
                )
            }
        }
    }
}

// ================= الخريطة المصغرة =================

/** خريطة مصغرة: صمت المشروع كله + مستطيل العرض + الرأس — السحب يقفز بالمشهد. */
@Composable
private fun MontageMiniMap(
    project: MontageProject,
    envelopes: Map<String, MontageEnvelope>,
    selectedId: String?,
    playheadMs: Long,
    contentWidthPx: Float,
    scrollValue: () -> Int,
    scrollMax: () -> Int,
    onPan: (fraction: Float) -> Unit
) {
    val totalMs = remember(project) {
        MontageLogic.totalDurationMs(project.clips, project.gapMs, project.crossfadeMs).coerceAtLeast(1L)
    }
    val cPrimaryMM = MaterialTheme.colorScheme.primary
    val cOutlineMM = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .pointerInput(project) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        onPan((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragStart = { off -> onPan((off.x / size.width).coerceIn(0f, 1f)) }
                )
            }
            .pointerInput(project) {
                detectTapGestures(
                    onTap = { off -> onPan((off.x / size.width).coerceIn(0f, 1f)) }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val scale = w / totalMs
        val primary = cPrimaryMM
        val outline = cOutlineMM
        val amber = Color(0xFFFFB300)
        for ((i, clip) in project.clips.withIndex()) {
            val d = MontageLogic.clipDurationMs(clip)
            if (d <= 0L) continue
            val x0 = MontageLogic.clipStartMs(project.clips, project.gapMs, project.crossfadeMs)[i] * scale
            val x1 = x0 + d * scale
            val env = envelopes[clip.id]
            val peak = env?.maxPeak() ?: 0.25f
            drawRoundRect(
                color = if (clip.id == selectedId) primary.copy(alpha = 0.75f) else primary.copy(alpha = 0.35f),
                topLeft = Offset(x0, h / 2f - (h * 0.8f * peak.coerceIn(0.1f, 1f)) / 2f),
                size = Size((x1 - x0).coerceAtLeast(2f), h * 0.8f * peak.coerceIn(0.1f, 1f)),
                cornerRadius = CornerRadius(3f, 3f)
            )
        }
        // مستطيل العرض الحالي
        val contentW = contentWidthPx.coerceAtLeast(1f)
        val maxScroll = scrollMax().coerceAtLeast(0)
        val viewportW = contentW - maxScroll
        val vpX = scrollValue() / contentW * w
        val vpW = (viewportW / contentW * w).coerceAtMost(w)
        drawRoundRect(
            color = outline,
            topLeft = Offset(vpX, 1f),
            size = Size(vpW.coerceAtLeast(6f), h - 2f),
            cornerRadius = CornerRadius(4f, 4f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
        // الرأس
        val px = playheadMs * scale
        drawLine(amber, Offset(px, 0f), Offset(px, h), 2f)
    }
}

// ================= قائمة المقاطع وحوار التحرير =================

/** صف مقطع في قائمة المونتاج: الترتيب والقص والمستوى والتلاشين وأزرار الإدارة. */
@Composable
private fun MontageClipRow(
    index: Int,
    clip: MontageClip,
    count: Int,
    enabled: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
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
                when {
                    missing -> MaterialTheme.colorScheme.errorContainer
                    selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) { onSelect() }
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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

/**
 * حوار تسمية مقطع (v0.37.0 — النقر المزدوج): حقل حر بالاسم الحالي، والتعقيم
 * (تقليم الاسم وحد 40 حرفًا وعدم الفراغ) في VM عبر sanitizeClipTitle فالتراجع
 * يعيد الاسم السابق كأي تحرير.
 */
@Composable
private fun MontageRenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسمية المقطع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "اكتب الاسم الجديد — يظهر على الموجة وفي قائمة المقاطع ويُحفظ مع المشروع.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    placeholder = { Text(initial) },
                    modifier = Modifier.fillMaxWidth().observesTextEditing(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("حفظ الاسم") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/**
 * حوار تعديل السرعة دون تغيير النبرة (v0.38.0): أنماط جاهزة ومضاعف ±5 —
 * 200٪ ضعف السرعة ونصف المدة، و50٪ نصف السرعة وضعف المدة، والنبرة ثابتة
 * دائمًا (WSOLA). ينطبق على المحدد: تعليقًا كان أو مقطع مسار.
 */
@Composable
private fun MontageSpeedDialog(
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pct by remember { mutableStateOf(100) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل السرعة — دون تغيير النبرة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "النبرة تبقى كما هي مهما تغيرت السرعة (شد WSOLA خالص) — 200٪ نصف مدة، و50٪ ضعف المدة، والمدة الجديدة تظهر بعد التطبيق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecondaryAction("− 5", onClick = { pct = (pct - 5).coerceAtLeast(ClipTransforms.SPEED_MIN_PERCENT) })
                    Text(
                        "$pct٪",
                        style = MaterialTheme.typography.headlineSmall.numeric(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(110.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    SecondaryAction("+ 5", onClick = { pct = (pct + 5).coerceAtMost(ClipTransforms.SPEED_MAX_PERCENT) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ClipTransforms.SPEED_PRESETS.forEach { p ->
                        SecondaryAction(
                            text = "$p٪",
                            onClick = { pct = p }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(pct) }) { Text("تطبيق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/**
 * حوار تغيير النبرة بأنصاف النغمات (v0.38.0): ±1 نغمة دقة و±12 حدًّا،
 * والطول لا يتغير — إعادة تشكيل ثم شد عائد. +12 نغمة = ثُمانية أعلى.
 */
@Composable
private fun MontagePitchDialog(
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var st by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغيير النبرة — دون تغيير الطول") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "أنصاف نغمات موسيقية: +12 نغمة أعلى بثُمانية و−12 أخفض بثُمانية، ومدة المقطع كما هي. يُطبَّق على المحدد.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SecondaryAction("− 12", onClick = { st = (st - 12).coerceAtLeast(ClipTransforms.PITCH_MIN_SEMITONES) })
                    SecondaryAction("− 1", onClick = { st = (st - 1).coerceAtLeast(ClipTransforms.PITCH_MIN_SEMITONES) })
                    Text(
                        "${if (st > 0) "+" else ""}$st",
                        style = MaterialTheme.typography.headlineSmall.numeric(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(90.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    SecondaryAction("+ 1", onClick = { st = (st + 1).coerceAtMost(ClipTransforms.PITCH_MAX_SEMITONES) })
                    SecondaryAction("+ 12", onClick = { st = (st + 12).coerceAtMost(ClipTransforms.PITCH_MAX_SEMITONES) })
                }
                Text(
                    when {
                        st == 0 -> "بلا تغيير — 0 نغمة"
                        st > 0 -> "أعلى بـ$st نغمة (${("%.2f").format(ClipTransforms.pitchFactor(st))}×)"
                        else -> "أخفض بـ${-st} نغمة (${("%.2f").format(ClipTransforms.pitchFactor(st))}×)"
                    },
                    style = MaterialTheme.typography.labelMedium.numeric(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(st) }) { Text("تطبيق") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** حوار تكرار المقطع ×عدد (v0.38.0) — 1..20 والمتلاصق خلف الأصل. */
@Composable
private fun MontageDuplicateDialog(
    onApply: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var n by remember { mutableStateOf(1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تكرار المقطع") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "النسخ متلاصقة خلف الأصل بنفس القصّ والمستوى والتلاشين — على التعليق تتوالى في الترتيب وعلى المسارات تتوضع تباعًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecondaryAction("− 1", onClick = { n = (n - 1).coerceAtLeast(1) })
                    Text(
                        "×$n",
                        style = MaterialTheme.typography.headlineSmall.numeric(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(90.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    SecondaryAction("+ 1", onClick = { n = (n + 1).coerceAtMost(ClipTransforms.DUPLICATE_MAX) })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(n) }) { Text("تكرار") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
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

// ================= المسارات الحرة — عدة خطوط عمل (v0.36.0) =================

/** أوضاع السحب على المسارات الحرة: تقليم حافة أو تحريك بين الخطوط. */
private sealed interface TrackDrag {
    data object None : TrackDrag
    data class TrimLeft(val clipId: String) : TrackDrag
    data class TrimRight(val clipId: String) : TrackDrag
    data class Move(val clipId: String, var lane: Int) : TrackDrag
}

/**
 * كل المسارات الحرة في كانفس واحد بمسارات أفقية — السحب الأفقي يحرك المقطع
 * على زمنه، والسحب العمودي ينقله بين خطوط العمل (سحب عابر للمسارات)، وحوافه
 * تُقلّم من ملفها، وأداة التقسيم ✂ تشطره عند النقر. ظل النطق عبر كل الخطوط
 * يُظهر أين سيُخفض الخفض التلقائي، والمنطقة المحددة تُظلل قبل حذفها.
 */
@Composable
private fun MontageFreeTracksCanvas(
    vm: AppViewModel,
    project: MontageProject,
    tracks: List<com.saidjte.schoolradio.desktop.data.MontageTrack>,
    envelopes: Map<String, MontageEnvelope>,
    selectedId: String?,
    selection: Pair<Long, Long>?,
    tool: MontageTool,
    playheadMs: Long,
    pxPerSec: Float,
    snap: (Long) -> Long,
    onMoveStart: () -> Unit,
    onMove: (clipId: String, deltaMs: Long) -> Unit,
    onRename: (MontageMusicClip) -> Unit
) {
    val totalMs = remember(project) { MontageLogic.totalDurationFull(project).coerceAtLeast(1L) }
    val density = LocalDensity.current
    val widthDp = with(density) {
        ((totalMs.toFloat() * pxPerSec / 1000f + 80f) / density.density).dp.coerceAtLeast(320.dp)
    }
    val pxPerMs = pxPerSec / 1000f
    val pad = 10f
    val laneH = with(density) { FREE_LANE_DP.toPx() }
    val textMeasurer = rememberTextMeasurer()

    val projState = rememberUpdatedState(project)
    val tracksState = rememberUpdatedState(tracks)
    val ppmState = rememberUpdatedState(pxPerMs)
    val moveState = rememberUpdatedState(onMove)
    val moveStartState = rememberUpdatedState(onMoveStart)
    val snapState = rememberUpdatedState(snap)
    val toolState = rememberUpdatedState(tool)
    val renameState = rememberUpdatedState(onRename)

    var drag by remember { mutableStateOf<TrackDrag>(TrackDrag.None) }
    var dragClipId by remember { mutableStateOf<String?>(null) }

    val duckIntervals = remember(project) {
        MontageLogic.narrationIntervals(project.clips, project.gapMs, project.crossfadeMs)
    }

    fun clipsOf(t: com.saidjte.schoolradio.desktop.data.MontageTrack, p: MontageProject) =
        p.musicClips.filter { MontageLogic.effectiveTrackId(it) == t.id }

    fun hitAt(x: Float, y: Float): Pair<MontageMusicClip, Int>? {
        val p = projState.value
        val ts = tracksState.value
        if (ts.isEmpty()) return null
        val lane = (y / laneH).toInt().coerceIn(0, ts.size - 1)
        val t = ts[lane]
        val c = clipsOf(t, p).firstOrNull {
            val cx = pad + it.startMs * ppmState.value
            val cw = max(6f, MontageLogic.musicDurationMs(it) * ppmState.value)
            x >= cx && x <= cx + cw
        } ?: return null
        return c to lane
    }

    val heightDp = FREE_LANE_DP * tracks.size.coerceAtLeast(1)

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { off ->
                        val ms = ((off.x - pad) / ppmState.value).toLong().coerceAtLeast(0L)
                        val hit = hitAt(off.x, off.y)
                        if (toolState.value == MontageTool.SPLIT && hit != null) {
                            vm.splitMontageTrackClipAtMs(hit.first.id, snapState.value(ms))
                        } else {
                            vm.setMontagePlayhead(snapState.value(ms))
                            if (hit != null) vm.selectMontageMusic(hit.first.id)
                        }
                    },
                    onDoubleTap = { off ->
                        // النقر المزدوج على مقطع مسار يفتح تسميته (v0.37.0)
                        val hit = hitAt(off.x, off.y)
                        if (hit != null) renameState.value(hit.first)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { off ->
                        val hit = hitAt(off.x, off.y)
                        if (hit == null) {
                            drag = TrackDrag.None
                            dragClipId = null
                            return@detectDragGestures
                        }
                        val (c, lane) = hit
                        val cx = pad + c.startMs * ppmState.value
                        val cw = max(6f, MontageLogic.musicDurationMs(c) * ppmState.value)
                        dragClipId = c.id
                        when {
                            off.x - cx <= 10f -> {
                                drag = TrackDrag.TrimLeft(c.id)
                                moveStartState.value()
                            }
                            cx + cw - off.x <= 10f -> {
                                drag = TrackDrag.TrimRight(c.id)
                                moveStartState.value()
                            }
                            else -> drag = TrackDrag.Move(c.id, lane)
                        }
                    },
                    onDrag = { change, amt ->
                        change.consume()
                        val id = dragClipId ?: return@detectDragGestures
                        val ppm = ppmState.value
                        when (val d = drag) {
                            is TrackDrag.TrimLeft -> vm.trimMontageTrackClipLeft(id, (amt.x / ppm).toLong())
                            is TrackDrag.TrimRight -> vm.trimMontageTrackClipRight(id, (amt.x / ppm).toLong())
                            is TrackDrag.Move -> {
                                moveState.value(id, (amt.x / ppm).toLong())
                                // سحب عمودي عابر للمسارات — تغيير الخط أثناء السحبة نفسها
                                val laneCount = tracksState.value.size.coerceAtLeast(1)
                                val targetLane = (change.position.y / laneH).toInt().coerceIn(0, laneCount - 1)
                                if (targetLane != d.lane) {
                                    val t = tracksState.value.getOrNull(targetLane)
                                    if (t != null) {
                                        vm.moveMontageTrackClipToTrack(id, t.id)
                                        d.lane = targetLane
                                    }
                                }
                            }
                            TrackDrag.None -> { }
                        }
                    },
                    onDragEnd = {
                        // التقاط نهاية السحب: الموضع النهائي يلتقط أقرب علامة/حافة/شبكة
                        val id = dragClipId
                        if (drag is TrackDrag.Move && id != null) {
                            val c = projState.value.musicClips.firstOrNull { it.id == id }
                            if (c != null) {
                                val target = snapState.value(c.startMs)
                                if (target != c.startMs) moveState.value(id, target - c.startMs)
                            }
                        }
                        drag = TrackDrag.None
                        dragClipId = null
                    },
                    onDragCancel = {
                        drag = TrackDrag.None
                        dragClipId = null
                    }
                )
            }
    ) {
        val cPrimary = MaterialTheme.colorScheme.primary
        val cSurface = MaterialTheme.colorScheme.surface
        val cOutline = MaterialTheme.colorScheme.outlineVariant
        val cOnSurf = MaterialTheme.colorScheme.onSurface
        val cBg = MaterialTheme.colorScheme.surfaceVariant
        val amber = Color(0xFFFFB300)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val ts = tracks
            val laneHpx = if (ts.isEmpty()) h else h / ts.size
            // خلفية كل مسار بلونه وخط فاصل
            for (i in ts.indices) {
                val color = Color(MontageLogic.TRACK_COLORS[Math.floorMod(ts[i].colorIndex, MontageLogic.TRACK_COLORS.size)])
                drawRect(
                    color = color.copy(alpha = 0.06f),
                    topLeft = Offset(0f, i * laneHpx),
                    size = Size(w, laneHpx)
                )
                if (i < ts.size - 1) {
                    drawLine(
                        color = color.copy(alpha = 0.35f),
                        start = Offset(0f, (i + 1) * laneHpx - 0.5f),
                        end = Offset(w, (i + 1) * laneHpx - 0.5f),
                        strokeWidth = 1f
                    )
                }
            }
            // ظل النطق عبر كل الخطوط — هناك يعمل الخفض التلقائي
            for ((s, e) in duckIntervals) {
                val x0 = pad + s * pxPerMs
                val x1 = pad + e * pxPerMs
                if (x1 < 0f || x0 > w) continue
                drawRect(
                    color = cPrimary.copy(alpha = 0.08f),
                    topLeft = Offset(x0.coerceAtLeast(0f), 0f),
                    size = Size(x1 - x0.coerceAtLeast(0f), h)
                )
            }
            // المنطقة المحددة على المسطرة تمر عبر كل الخطوط
            if (selection != null) {
                val ax = pad + selection.first * pxPerMs
                val bx = pad + selection.second * pxPerMs
                drawRect(
                    color = cPrimary.copy(alpha = 0.20f),
                    topLeft = Offset(ax.coerceAtLeast(0f), 0f),
                    size = Size((bx - ax).coerceAtLeast(2f), h)
                )
                drawLine(cPrimary, Offset(ax.coerceAtLeast(0f), 0f), Offset(ax.coerceAtLeast(0f), h), 1.5f)
                drawLine(cPrimary, Offset(bx, 0f), Offset(bx, h), 1.5f)
            }
            // مقاطع كل مسار: مستطيل بلون مساره + ويڤ فورم + عنوان
            for ((ti, t) in ts.withIndex()) {
                val bandTop = ti * laneHpx
                val color = Color(MontageLogic.TRACK_COLORS[Math.floorMod(t.colorIndex, MontageLogic.TRACK_COLORS.size)])
                for (m in clipsOf(t, project)) {
                    val cx = pad + m.startMs * pxPerMs
                    val cw = max(6f, MontageLogic.musicDurationMs(m) * pxPerMs)
                    val missing = !File(m.path).isFile
                    val sel = m.id == selectedId
                    val clipH = laneHpx - 10f
                    drawRoundRect(
                        color = if (missing) cOutline.copy(alpha = 0.25f) else cSurface.copy(alpha = 0.94f),
                        topLeft = Offset(cx, bandTop + 5f),
                        size = Size(cw, clipH),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = when {
                            dragClipId == m.id -> color
                            sel -> color
                            else -> color.copy(alpha = 0.75f)
                        },
                        topLeft = Offset(cx, bandTop + 5f),
                        size = Size(cw, clipH),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (sel) 2f else 1f)
                    )
                    val env = envelopes[m.id]
                    if (env != null && env.peaks.isNotEmpty()) {
                        val n = env.peaks.size
                        val midY = bandTop + 5f + clipH / 2f
                        val barW = max(1f, cw / n - 0.5f)
                        for (bk in 0 until n) {
                            val bx = cx + (bk + 0.5f) / n * cw
                            if (bx < cx + 2f || bx > cx + cw - 2f) continue
                            val halfH = env.peaks[bk] * (clipH / 2f - 3f)
                            if (halfH <= 0.3f) continue
                            drawLine(
                                color = color.copy(alpha = 0.85f),
                                start = Offset(bx, midY - halfH),
                                end = Offset(bx, midY + halfH),
                                strokeWidth = barW,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    if (cw > 60f) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = "♪ " + m.title.take(13),
                            topLeft = Offset(cx + 4f, bandTop + 7f),
                            style = TextStyle(fontSize = 9.sp, color = cOnSurf)
                        )
                    }
                }
            }
            // الرأس يمر عبر كل الخطوط كي يقرأ المذيع التزامن
            val px = pad + playheadMs * pxPerMs
            if (px >= 0f && px <= w) {
                drawLine(amber, Offset(px, 0f), Offset(px, h), 2f)
            }
        }
    }
}

/** صف مقطع موسيقى مرافقة في القائمة — التحرير والحذف والنطّ الدقيق. */
@Composable
private fun MontageMusicRow(
    index: Int,
    clip: MontageMusicClip,
    trackName: String,
    enabled: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val missing = !File(clip.path).isFile
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    missing -> MaterialTheme.colorScheme.errorContainer
                    selected -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.surface
                }
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) { onSelect() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIcon(kind = AppIconKind.MUSIC, size = 18.dp, tint = MaterialTheme.colorScheme.tertiary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                clip.title + if (missing) " — الملف مفقود" else "",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val dur = MontageLogic.musicDurationMs(clip)
            Text(
                "المسار: $trackName · الموضع: ${formatMs(clip.startMs)} · القص: ${formatMs(clip.startInFileMs)} ← ${formatMs(clip.endInFileMs)} (${formatMs(dur)}) · الصوت ${clip.volumePercent}٪",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SecondaryAction(text = "تحرير", enabled = enabled, onClick = onEdit)
        SecondaryAction(text = "حذف", enabled = enabled, onClick = onRemove)
    }
}

/**
 * حوار تحرير مقطع الموسيقى المرافقة: الموضع المطلق على الخط الزمني + نافذة
 * القصّ داخل الملف والمستوى والتلاشين — الحبوس تُطبّق عند الحفظ.
 */
@Composable
private fun MontageMusicEditDialog(
    clip: MontageMusicClip,
    onSave: (pos: Long, start: Long, end: Long, vol: Int, fi: Long, fo: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var posText by remember { mutableStateOf(clip.startMs.toString()) }
    var startText by remember { mutableStateOf(clip.startInFileMs.toString()) }
    var endText by remember { mutableStateOf(clip.endInFileMs.toString()) }
    var volText by remember { mutableStateOf(clip.volumePercent.toString()) }
    var finText by remember { mutableStateOf(clip.fadeInMs.toString()) }
    var foutText by remember { mutableStateOf(clip.fadeOutMs.toString()) }

    fun fieldMs(text: String): Long = text.trim().toLongOrNull()?.coerceAtLeast(0L) ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحرير موسيقى مرافقة: ${clip.title.take(40)}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "مدة الملف كاملة: ${formatMs(clip.durationMs)} — الموضع على الخط الزمني للمونتاج، والقصّ من الملف المصدر.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LabeledNumber("الموضع على الخط (مللي ثانية)", posText, { posText = it }, Modifier.weight(1f))
                    SecondaryAction("− 250", onClick = { posText = (fieldMs(posText) - 250).coerceAtLeast(0).toString() })
                    SecondaryAction("+ 250", onClick = { posText = (fieldMs(posText) + 250).toString() })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LabeledNumber("بداية الملف (ms)", startText, { startText = it }, Modifier.weight(1f))
                    SecondaryAction("− 500", onClick = { startText = (fieldMs(startText) - 500).coerceAtLeast(0).toString() })
                    SecondaryAction("+ 500", onClick = { startText = (fieldMs(startText) + 500).toString() })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LabeledNumber("نهاية الملف (ms)", endText, { endText = it }, Modifier.weight(1f))
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
                val (fs, fe) = MontageLogic.clampWindow(fieldMs(startText), fieldMs(endText), clip.durationMs)
                onSave(
                    fieldMs(posText), fs, fe,
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