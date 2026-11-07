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
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageEnvelope
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMarker
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageTemplateCatalog
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
 * تشبع) ومعاينة من أي نقطة وتصدير WAV واحد جاهز للبث، وحفظ/فتح ملف مشروع.
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

    var showPicker by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<MontageClip?>(null) }
    var editMusicTarget by remember { mutableStateOf<MontageMusicClip?>(null) }
    var showMarkerDialog by remember { mutableStateOf(false) }
    var templateConfirm by remember { mutableStateOf<String?>(null) }
    val rendering = progress != null
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
            "انقر على الخط الزمني لتضع الرأس، اسحب حافة المقطع لتقصّه بدقة الميلي ثانية، واسحب جسمه لإعادة ترتيبه — مسار الموسيقى المرافقة تحته يعمل بالتوازي والخفض التلقائي يخفضه تحت التعليق آليًا.",
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

        if (project.clips.isEmpty()) {
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
                    vm.moveMontageMusicClip(id, deltaMs)
                }
            )
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
                        "مسار الموسيقى المرافقة — يعمل بالتوازي مع التعليق",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
            }
            project.musicClips.forEachIndexed { i, m ->
                MontageMusicRow(
                    index = i,
                    clip = m,
                    enabled = !rendering,
                    selected = m.id == selectedMusicId,
                    onSelect = { vm.selectMontageMusic(m.id) },
                    onEdit = { editMusicTarget = m },
                    onRemove = { vm.removeMontageMusicClip(m.id) }
                )
            }
        }

        // التذييل: معاينة من الرأس وتصدير وتقدم وإلغاء وإضافة للقائمة
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryAction(
                text = if (playing) "إيقاف المعاينة مؤقتًا" else "معاينة من الرأس",
                enabled = !rendering && project.clips.any { File(it.path).isFile },
                onClick = { vm.toggleMontagePreview() }
            )
            PrimaryAction(
                text = "تصدير ملف واحد",
                enabled = !rendering && project.clips.any { File(it.path).isFile },
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
            "اختصارات الصفحة: مسافة معاينة/إيقاف · S تقسيم · Delete حذف المحدد · ←/→ تحريك الرأس (Shift: دقة 10ms) · ↑/↓ تحديد · Ctrl+Z/Y تراجع/إعادة",
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

/**
 * بطاقة الخط الزمني: صف تكبير وحلقة، خريطة مصغرة بنظرة عامة، الخط الزمني
 * نفسه بموجاته داخل تمرير أفقي، ثم صف VU وزمن الرأس.
 * الاتجاه داخل الخط الزمني LTR — عرف المحررات الصوتية حتى في الواجهات العربية.
 */
@Composable
private fun MontageTimelineCard(
    vm: AppViewModel,
    project: MontageProject,
    envelopes: Map<String, MontageEnvelope>,
    selectedId: String?,
    selectedMusicId: String?,
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
    onMusicMove: (clipId: String, deltaMs: Long) -> Unit
) {
    val totalMs = remember(project) { MontageLogic.totalDurationFull(project) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val contentWidthPx = totalMs.toFloat() * pxPerSec / 1000f + 80f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, LocalStudioColors.current.cardBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // صف التكبير والحلقة A/B
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
                "حلقة A/B:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 10.dp)
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
            // الخط الزمني نفسه داخل تمرير أفقي
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                MontageTimelineCanvas(
                    vm = vm,
                    project = project,
                    envelopes = envelopes,
                    selectedId = selectedId,
                    playheadMs = playheadMs,
                    loopStart = loopStart,
                    loopEnd = loopEnd,
                    loopEnabled = loopEnabled,
                    pxPerSec = pxPerSec,
                    onTrimStart = onTrimStart,
                    onTrim = onTrim
                )
            }
            // مسار الموسيقى المرافقة (v0.35.0 — البند 16): تمرير مشترك مع التعليق
            // فتتحرك المساران معًا، والظل الملوّن = مناطق النطق التي يُخفض عندها السرير
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll)) {
                MontageMusicLane(
                    project = project,
                    envelopes = envelopes,
                    selectedId = selectedMusicId,
                    playheadMs = playheadMs,
                    pxPerSec = pxPerSec,
                    onSeek = { vm.setMontagePlayhead(it) },
                    onSelectMusic = { vm.selectMontageMusic(it) },
                    onMoveStart = onMusicMoveStart,
                    onMove = onMusicMove
                )
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

// ================= الخط الزمني الرسم =================

/** أوضاع السحب على الخط الزمني: تقليم الحافتين أو إعادة ترتيب أو تحريك الرأس. */
private sealed interface TimelineDrag {
    data object None : TimelineDrag
    data class TrimLeft(val clipId: String, val origStart: Long) : TimelineDrag
    data class TrimRight(val clipId: String, val origEnd: Long) : TimelineDrag
    data class Reorder(val clipId: String, var acc: Float) : TimelineDrag
    data object Scrub : TimelineDrag
}

@Composable
private fun MontageTimelineCanvas(
    vm: AppViewModel,
    project: MontageProject,
    envelopes: Map<String, MontageEnvelope>,
    selectedId: String?,
    playheadMs: Long,
    loopStart: Long?,
    loopEnd: Long?,
    loopEnabled: Boolean,
    pxPerSec: Float,
    onTrimStart: () -> Unit,
    onTrim: (clipId: String, startMs: Long, endMs: Long) -> Unit
) {
    val totalMs = remember(project) {
        MontageLogic.totalDurationMs(project.clips, project.gapMs, project.crossfadeMs)
    }
    val density = LocalDensity.current
    val widthDp = with(density) {
        ((totalMs.toFloat() * pxPerSec / 1000f + 80f) / density.density).dp.coerceAtLeast(320.dp)
    }
    val textMeasurer = rememberTextMeasurer()
    var drag by remember { mutableStateOf<TimelineDrag>(TimelineDrag.None) }
    var dragClipId by remember { mutableStateOf<String?>(null) }
    var scrubMs by remember { mutableStateOf(0L) }

    val pxPerMs = pxPerSec / 1000f
    val pad = 10f
    val startsMs = remember(project) {
        MontageLogic.clipStartMs(project.clips, project.gapMs, project.crossfadeMs)
    }

    // المدخلات تقرأ أحدث حالة عبر حوامل محدّثة — كي لا يُقطع السحب
    // عند إعادة تشغيل التركيب مع كل تحديث مشروع أثناء التقليم
    val projState = rememberUpdatedState(project)
    val startsState = rememberUpdatedState(startsMs)
    val pxPerMsState = rememberUpdatedState(pxPerMs)
    val trimState = rememberUpdatedState(onTrim)
    val trimStartState = rememberUpdatedState(onTrimStart)

    // هندسة المقاطع بالبكسل — من الحوامل الحية للمدخلات، ومن القيم المباشرة للرسم
    fun liveClipX(i: Int): Float = pad + startsState.value[i] * pxPerMsState.value
    fun liveClipW(i: Int): Float =
        max(6f, MontageLogic.clipDurationMs(projState.value.clips[i]) * pxPerMsState.value)
    fun liveClipAt(x: Float): Int = projState.value.clips.indices.firstOrNull {
        x >= liveClipX(it) && x <= liveClipX(it) + liveClipW(it)
    } ?: -1

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(180.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { off ->
                        val project = projState.value
                        val ppm = pxPerMsState.value
                        val ms = ((off.x - pad) / ppm).toLong().coerceAtLeast(0L)
                        vm.setMontagePlayhead(ms)
                        val idx = project.clips.indices.firstOrNull {
                            off.x >= pad + startsState.value[it] * ppm &&
                                    off.x <= pad + startsState.value[it] * ppm +
                                    max(6f, MontageLogic.clipDurationMs(project.clips[it]) * ppm)
                        }
                        if (idx != null) vm.selectMontageClip(project.clips[idx].id)
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
                            idx < 0 -> drag = TimelineDrag.Scrub
                            off.x - cx(idx) <= 10f -> {
                                drag = TimelineDrag.TrimLeft(project.clips[idx].id, project.clips[idx].startMs)
                                trimStartState.value()
                            }
                            cx(idx) + cw(idx) - off.x <= 10f -> {
                                drag = TimelineDrag.TrimRight(project.clips[idx].id, project.clips[idx].endMs)
                                trimStartState.value()
                            }
                            else -> drag = TimelineDrag.Reorder(project.clips[idx].id, 0f)
                        }
                        dragClipId = if (idx >= 0) project.clips[idx].id else null
                    },
                    onDrag = { change, amt ->
                        change.consume()
                        val project = projState.value
                        val ppm = pxPerMsState.value
                        val x = change.position.x
                        when (val d = drag) {
                            is TimelineDrag.Scrub -> {
                                scrubMs = ((x - pad) / ppm).toLong().coerceAtLeast(0L)
                                vm.setMontagePlayhead(scrubMs, persist = false)
                            }
                            is TimelineDrag.TrimLeft -> {
                                val clip = project.clips.firstOrNull { it.id == d.clipId }
                                if (clip != null) {
                                    val dxMs = (amt.x / ppm).toLong()
                                    val s = (d.origStart + dxMs).coerceIn(0L, clip.endMs - 50L)
                                    trimState.value(d.clipId, s, clip.endMs)
                                }
                            }
                            is TimelineDrag.TrimRight -> {
                                val clip = project.clips.firstOrNull { it.id == d.clipId }
                                if (clip != null) {
                                    val dxMs = (amt.x / ppm).toLong()
                                    val e = (d.origEnd + dxMs).coerceIn(clip.startMs + 50L, clip.durationMs)
                                    trimState.value(d.clipId, clip.startMs, e)
                                }
                            }
                            is TimelineDrag.Reorder -> {
                                d.acc += amt.x
                                if (abs(d.acc) >= 40f) {
                                    vm.moveMontageClip(d.clipId, if (d.acc > 0) 1 else -1)
                                    d.acc = 0f
                                }
                            }
                            TimelineDrag.None -> { }
                        }
                    },
                    onDragEnd = {
                        if (drag is TimelineDrag.Scrub) vm.setMontagePlayhead(scrubMs)
                        drag = TimelineDrag.None
                        dragClipId = null
                    },
                    onDragCancel = {
                        drag = TimelineDrag.None
                        dragClipId = null
                    }
                )
            }
    ) {
        // الألوان تُلتقط في سياق التركيب — داخل Canvas لا استدعاءات Composable
        val cPrimary = MaterialTheme.colorScheme.primary
        val cSurface = MaterialTheme.colorScheme.surface
        val cOutline = MaterialTheme.colorScheme.outlineVariant
        val cOnSurfVar = MaterialTheme.colorScheme.onSurfaceVariant
        val cOnSurf = MaterialTheme.colorScheme.onSurface
        val cErr = MaterialTheme.colorScheme.error
        val cTertiary = MaterialTheme.colorScheme.tertiary
        val cSecondaryContainer = MaterialTheme.colorScheme.secondaryContainer
        val cBg = MaterialTheme.colorScheme.surfaceVariant
        val amber = Color(0xFFFFB300)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val h = size.height
            val rulerH = 22f
            val clipTop = 30f
            val clipBottom = h - 14f
            val blockH = clipBottom - clipTop
            val primary = cPrimary
            val surface = cSurface
            val outline = cOutline
            val onSurfVar = cOnSurfVar
            val err = cErr
            val tertiary = cTertiary
            // هندسة الرسم: نسخة الرسم من البدايات (القيم المباشرة للتركيب الحالي)
            fun clipX(i: Int): Float = pad + startsMs[i] * pxPerMs
            fun clipW(i: Int): Float =
                max(6f, MontageLogic.clipDurationMs(project.clips[i]) * pxPerMs)

            // خلفية الموجات
            drawRoundRect(
                color = cBg.copy(alpha = 0.30f),
                topLeft = Offset(0f, rulerH),
                size = Size(size.width, h - rulerH),
                cornerRadius = CornerRadius(10f, 10f)
            )

            // مسطرة الزمن: خطوات تتكيف مع التكبير
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
                    color = onSurfVar.copy(alpha = 0.35f),
                    start = Offset(x, rulerH - 6f),
                    end = Offset(x, rulerH),
                    strokeWidth = 1.5f
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = formatMs(tSec * 1000L),
                    topLeft = Offset(x + 3f, 2f),
                    style = TextStyle(fontSize = 9.sp, color = onSurfVar).numeric()
                )
                tSec += stepSec
            }

            // العلامات المسمّاة (v0.35.0 — البند 17): علم كهرماني ثلاثي على المسطرة
            // واسمه في كبسولة — والنقر قربه يحرّك الرأس إلى موضعها تقريبًا
            for (mk in project.markers.sortedBy { it.tMs }) {
                val mx = pad + mk.tMs * pxPerMs
                if (mx < 0f || mx > size.width) continue
                drawLine(
                    color = tertiary.copy(alpha = 0.85f),
                    start = Offset(mx, rulerH - 8f),
                    end = Offset(mx, clipBottom),
                    strokeWidth = 1.5f
                )
                drawPath(
                    color = tertiary,
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(mx, rulerH - 17f)
                        lineTo(mx + 11f, rulerH - 12f)
                        lineTo(mx, rulerH - 7f)
                        close()
                    }
                )
                val label = mk.name.take(14)
                val tr = textMeasurer.measure(
                    text = label,
                    style = TextStyle(fontSize = 9.sp, color = tertiary, fontWeight = FontWeight.Bold)
                )
                drawRoundRect(
                    color = cBg.copy(alpha = 0.85f),
                    topLeft = Offset(mx + 2f, rulerH - 18f),
                    size = Size(tr.size.width + 8f, tr.size.height + 2f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawText(
                    textLayoutResult = tr,
                    topLeft = Offset(mx + 6f, rulerH - 17f)
                )
            }

            // منطقة الحلقة A/B
            if (loopEnabled && loopStart != null && loopEnd != null && loopEnd > loopStart) {
                val ax = pad + loopStart * pxPerMs
                val bx = pad + loopEnd * pxPerMs
                drawRect(
                    color = tertiary.copy(alpha = 0.16f),
                    topLeft = Offset(ax, rulerH),
                    size = Size(bx - ax, h - rulerH)
                )
                drawLine(tertiary, Offset(ax, rulerH), Offset(ax, h), 2f)
                drawLine(tertiary, Offset(bx, rulerH), Offset(bx, h), 2f)
            }

            // الفواصل: صمت بين المقاطع حين لا كروس فيد
            if (project.crossfadeMs <= 0L && project.gapMs > 0L) {
                for (i in 0 until project.clips.size - 1) {
                    val gx0 = clipX(i) + clipW(i)
                    val gx1 = clipX(i + 1)
                    if (gx1 > gx0) {
                        drawRect(
                            color = onSurfVar.copy(alpha = 0.10f),
                            topLeft = Offset(gx0, clipTop),
                            size = Size(gx1 - gx0, blockH)
                        )
                        if (gx1 - gx0 > 26f) {
                            drawText(
                                textMeasurer = textMeasurer,
                                text = "فاصل",
                                topLeft = Offset(gx0 + 4f, clipTop + 4f),
                                style = TextStyle(fontSize = 9.sp, color = onSurfVar)
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
                            color = primary.copy(alpha = 0.16f),
                            topLeft = Offset(x0, clipTop),
                            size = Size(x1 - x0, blockH)
                        )
                        drawLine(
                            color = primary.copy(alpha = 0.6f),
                            start = Offset(x0, clipTop),
                            end = Offset(x1, clipBottom),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = primary.copy(alpha = 0.6f),
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
                    color = if (missing) err.copy(alpha = 0.25f) else surface.copy(alpha = 0.92f),
                    topLeft = Offset(cx, clipTop),
                    size = Size(cw, blockH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = when {
                        dragClipId == clip.id -> primary
                        sel -> primary
                        missing -> err
                        else -> outline
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
                            color = if (env.clippedAt(b)) err else primary.copy(alpha = 0.85f),
                            start = Offset(bx, midY - halfH),
                            end = Offset(bx, midY + halfH),
                            strokeWidth = barW,
                            cap = StrokeCap.Round
                        )
                    }
                } else {
                    drawLine(
                        color = onSurfVar.copy(alpha = 0.35f),
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

            // رأس التشغيل: خط كهرماني بمثلث علوي
            val px = pad + playheadMs * pxPerMs
            if (px >= 0f && px <= size.width) {
                drawLine(
                    color = amber,
                    start = Offset(px, rulerH - 10f),
                    end = Offset(px, h),
                    strokeWidth = 2.5f
                )
                drawPath(
                    color = amber,
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(px - 6f, rulerH - 16f)
                        lineTo(px + 6f, rulerH - 16f)
                        lineTo(px, rulerH - 6f)
                        close()
                    }
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

// ================= المسار المزدوج والقوالب (v0.35.0 — البنود 16–17) =================

/**
 * مسار الموسيقى المرافقة: شريط موازٍ تحت خط التعليق بموضع مطلق لكل مقطع،
 * ومناطق النطق مظللة بعدها (هناك يُخفض السرير آليًا بعمق [MontageProject.duckDb]).
 * السحب الأفقي يجسّم المقطع على مساره (لقطة تاريخ واحدة للسحبة)، والنقر
 * يحدده ويحرّك الرأس إلى موضع النقر.
 */
@Composable
private fun MontageMusicLane(
    project: MontageProject,
    envelopes: Map<String, MontageEnvelope>,
    selectedId: String?,
    playheadMs: Long,
    pxPerSec: Float,
    onSeek: (Long) -> Unit,
    onSelectMusic: (String) -> Unit,
    onMoveStart: () -> Unit,
    onMove: (clipId: String, deltaMs: Long) -> Unit
) {
    val totalMs = remember(project) { MontageLogic.totalDurationFull(project).coerceAtLeast(1L) }
    val density = LocalDensity.current
    val widthDp = with(density) {
        ((totalMs.toFloat() * pxPerSec / 1000f + 80f) / density.density).dp.coerceAtLeast(320.dp)
    }
    val pxPerMs = pxPerSec / 1000f
    val pad = 10f
    val duckIntervals = remember(project) {
        MontageLogic.narrationIntervals(project.clips, project.gapMs, project.crossfadeMs)
    }
    val projState = rememberUpdatedState(project)
    val ppmState = rememberUpdatedState(pxPerMs)
    val moveState = rememberUpdatedState(onMove)
    val moveStartState = rememberUpdatedState(onMoveStart)
    var dragId by remember { mutableStateOf<String?>(null) }

    val cPrimary = MaterialTheme.colorScheme.primary
    val cTertiary = MaterialTheme.colorScheme.tertiary
    val cSurface = MaterialTheme.colorScheme.surface
    val cOutline = MaterialTheme.colorScheme.outlineVariant
    val cBg = MaterialTheme.colorScheme.surfaceVariant
    val cOnSurf = MaterialTheme.colorScheme.onSurface
    val amber = Color(0xFFFFB300)
    val textMeasurer = rememberTextMeasurer()

    fun musicX(m: MontageMusicClip): Float = pad + m.startMs * pxPerMs
    fun musicW(m: MontageMusicClip): Float =
        max(6f, MontageLogic.musicDurationMs(m) * pxPerMs)
    fun musicAt(x: Float, p: MontageProject = projState.value): Int = p.musicClips.indices.firstOrNull {
        x >= pad + p.musicClips[it].startMs * ppmState.value &&
                x <= pad + p.musicClips[it].startMs * ppmState.value +
                max(6f, MontageLogic.musicDurationMs(p.musicClips[it]) * ppmState.value)
    } ?: -1

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Text(
                "مسار الموسيقى المرافقة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "الظل = مناطق النطق (يُخفض السرير آليًا عندها) · اسحب جسم المقطع لتحريكه",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .width(widthDp)
                .height(58.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { off ->
                            val p = projState.value
                            val ms = ((off.x - pad) / ppmState.value).toLong().coerceAtLeast(0L)
                            onSeek(ms)
                            val idx = musicAt(off.x)
                            if (idx >= 0) onSelectMusic(p.musicClips[idx].id)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { off ->
                            val p = projState.value
                            val idx = musicAt(off.x)
                            if (idx >= 0) {
                                dragId = p.musicClips[idx].id
                                moveStartState.value()
                            } else dragId = null
                        },
                        onDrag = { change, amt ->
                            change.consume()
                            val id = dragId ?: return@detectDragGestures
                            val deltaMs = (amt.x / ppmState.value).toLong()
                            if (deltaMs != 0L) moveState.value(id, deltaMs)
                        },
                        onDragEnd = { dragId = null },
                        onDragCancel = { dragId = null }
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
                // مناطق النطق مظللة — الموسيقى تُخفض عندها آليًا
                for ((s, e) in duckIntervals) {
                    val x0 = pad + s * pxPerMs
                    val x1 = pad + e * pxPerMs
                    if (x1 < 0f || x0 > size.width) continue
                    drawRect(
                        color = cPrimary.copy(alpha = 0.10f),
                        topLeft = Offset(x0.coerceAtLeast(0f), 0f),
                        size = Size(x1 - x0.coerceAtLeast(0f), h)
                    )
                }
                // مقاطع الموسيقى: مستطيل + ويڤ فورم مصغّر + عنوان
                for ((i, m) in projState.value.musicClips.withIndex()) {
                    val cx = pad + m.startMs * pxPerMs
                    val cw = max(6f, MontageLogic.musicDurationMs(m) * pxPerMs)
                    val missing = !File(m.path).isFile
                    val sel = m.id == selectedId
                    drawRoundRect(
                        color = if (missing) cOutline.copy(alpha = 0.25f) else cSurface.copy(alpha = 0.92f),
                        topLeft = Offset(cx, 4f),
                        size = Size(cw, h - 8f),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = when {
                            dragId == m.id -> cTertiary
                            sel -> cTertiary
                            else -> cOutline
                        },
                        topLeft = Offset(cx, 4f),
                        size = Size(cw, h - 8f),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (sel) 2f else 1f)
                    )
                    val env = envelopes[m.id]
                    if (env != null && env.peaks.isNotEmpty()) {
                        val n = env.peaks.size
                        val midY = h / 2f
                        val barW = max(1f, cw / n - 0.5f)
                        for (bk in 0 until n) {
                            val bx = cx + (bk + 0.5f) / n * cw
                            if (bx < cx + 2f || bx > cx + cw - 2f) continue
                            val halfH = env.peaks[bk] * (h / 2f - 5f)
                            if (halfH <= 0.3f) continue
                            drawLine(
                                color = cTertiary.copy(alpha = 0.8f),
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
                            text = "♪ ${m.title.take(14)}",
                            topLeft = Offset(cx + 4f, 6f),
                            style = TextStyle(fontSize = 9.sp, color = cOnSurf)
                        )
                    }
                }
                // الرأس يمر عبر المسارين كي يقرأ المذيع التزامن
                val px = pad + playheadMs * pxPerMs
                if (px >= 0f && px <= size.width) {
                    drawLine(amber, Offset(px, 0f), Offset(px, h), 2f)
                }
            }
        }
    }
}

/** صف مقطع موسيقى مرافقة في القائمة — التحرير والحذف والنطّ الدقيق. */
@Composable
private fun MontageMusicRow(
    index: Int,
    clip: MontageMusicClip,
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
                "الموضع: ${formatMs(clip.startMs)} · القص: ${formatMs(clip.startInFileMs)} ← ${formatMs(clip.endInFileMs)} (${formatMs(dur)}) · الصوت ${clip.volumePercent}٪",
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