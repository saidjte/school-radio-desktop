package com.saidjte.schoolradio.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.saidjte.schoolradio.desktop.ui.AppIcon
import com.saidjte.schoolradio.desktop.ui.brand
import com.saidjte.schoolradio.desktop.ui.AppIconKind
import com.saidjte.schoolradio.desktop.ui.AppTheme
import com.saidjte.schoolradio.desktop.ui.LocalStudioColors
import com.saidjte.schoolradio.desktop.ui.LogoMark
import com.saidjte.schoolradio.desktop.ui.LivePage
import com.saidjte.schoolradio.desktop.ui.MixerPage
import com.saidjte.schoolradio.desktop.ui.MusicTab
import com.saidjte.schoolradio.desktop.ui.SoundsTab
import com.saidjte.schoolradio.desktop.ui.PulsingDot
import com.saidjte.schoolradio.desktop.ui.RecordingsTab
import com.saidjte.schoolradio.desktop.ui.ScheduleTab
import com.saidjte.schoolradio.desktop.ui.ScriptTab
import com.saidjte.schoolradio.desktop.ui.SettingsDialog
import com.saidjte.schoolradio.desktop.ui.StudioScreen
import com.saidjte.schoolradio.desktop.ui.MontagePage
import com.saidjte.schoolradio.desktop.ui.JingleWallWindow
import com.saidjte.schoolradio.desktop.ui.OnAirWindow
import com.saidjte.schoolradio.desktop.ui.ReadingWindow
import com.saidjte.schoolradio.desktop.audio.FakeToneVoice
import com.saidjte.schoolradio.desktop.audio.FakeTrackVoice
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.LiveAnnounceLogic
import com.saidjte.schoolradio.desktop.data.StudioLogic

/** أعلام الإقلاع للأتمتة — يقرؤها AppViewModel عند البناء قبل أي كوروتين. */
object LaunchFlags { var args: List<String> = emptyList() }

fun main(args: Array<String>) {
    LaunchFlags.args = args.toList()
    // وضع الفحص الذاتي: تشغيل النواة دون واجهة رسومية (للأتمتة والتحقق)
    if (args.contains("selftest")) {
        SelfTest.run()
        return
    }
    // صفحة بداية للأتمتة والفحص البصري: --tab=LIVE | SCHEDULE | SCRIPT | MUSIC | RECORDINGS | MONTAGE
    val startTab = args.firstOrNull { it.startsWith("--tab=") }
        ?.substringAfter("=")
        ?.let { name -> Tab.entries.firstOrNull { it.name == name } }
    application {
        val vm = remember { AppViewModel() }
        // فحص بصري للأتمتة: فتح الجدار عند الإقلاع وتشغيله بصوت اختباري بلا عتاد
        if (args.contains("--jingle-wall")) {
            LaunchedEffect(Unit) { vm.jingleWallOpen.value = true }
        }
        if (args.contains("--jingle-fake")) {
            // 240×25ms ≈ 6 ثوانٍ — تبقى حالة «جارٍ» مرئية عبر لقطات الفحص الآلي
            LaunchedEffect(Unit) { vm.jingles.voiceFactoryOverride = { FakeToneVoice(chunks = 240, chunkMs = 25) } }
        }
        // خط حقن الأتمتة (v0.29.0): مقطع موسيقى وهمي بلا عتاد صوت لفحص الاختصارات
        // بصريًا (مسافة = تشغيل/إيقاف، الأسهم = سعي). بلا العلم يبقى null فالإنتاج سليم
        if (args.contains("--fake-track")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(800)
                vm.player.trackReaderOverride = { FakeTrackVoice() }
                vm.player.play(java.io.File("automation/fake-track.mp3"))
            }
        }
        // كروس فيد للأتمتة (v0.31.0): مقطع 15 ثانية وهمي ينفد فيسأل عن «التالي»
        // فيبدأ انتقال كروس فيد حقيقي تلتقطه لقطات الشاشة بشارة «انتقال…»
        if (args.contains("--xfade")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(800)
                vm.player.crossfadeMs = 1200L
                vm.player.trackReaderOverride = { FakeTrackVoice(totalMs = 15_000L, chunkSleepMs = 200L) }
                vm.player.onCrossfadeNext = { java.io.File("automation/fake-next.mp3") }
                vm.player.play(java.io.File("automation/fake-a.mp3"))
            }
        }
        // وضع «على الهواء» للأتمتة (v0.32.0): فتح شاشة الطلاب عند الإقلاع
        if (args.contains("--onair")) {
            LaunchedEffect(Unit) { vm.onAirDisplayOpen.value = true }
        }
        // شاشة القراءة على الشاشة الثانية للأتمتة (v0.35.0)
        if (args.contains("--reading")) {
            LaunchedEffect(Unit) { vm.readingDisplayOpen.value = true }
        }
        // إعلان البث المباشر للأتمتة (v0.35.0): إطلاق الجينل بصوت اختباري بلا عتاد
        if (args.contains("--announce")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(600)
                vm.jingles.voiceFactoryOverride = { FakeToneVoice(chunks = 240, chunkMs = 25) }
                vm.announceLiveBroadcast()
            }
        }
        // المونتاج للأتمتة (v0.33.0): مقاطع عرض تجريبية بنوافذ قص حية
        if (args.contains("--montage-demo")) {
            LaunchedEffect(Unit) { vm.montageDemo() }
        }
        // المونتاج للأتمتة (v0.35.0): قالب جاهز + علامة عند رأس محرّك + عمق خفض
        // — تُنفَّذ بعد استقرار العرض التجريبي فتظهر العلامات والمسار والخفض دفعة واحدة
        if (args.contains("--montage-v35")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(4500)
                vm.setMontagePlayhead(8000L)
                vm.applyMontageTemplate("full-break")
                vm.addMontageMarkerAtPlayhead("النشيد")
                vm.setMontageDuckDb(-18)
            }
        }
        // المونتاج للأتمتة (v0.36.0): مسار عمل ثانٍ + نقل مقطع إليه + منطقة محددة
        // على المسطرة + علامة عند بدايتها — تُظهر هندسة v0.36 كاملة في اللقطات
        if (args.contains("--montage-v36")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(4500)
                vm.addMontageTrack()
                val firstMusic = vm.montageProject.value.musicClips.firstOrNull()
                val lastTrack = vm.montageTracksEffective().lastOrNull()
                if (firstMusic != null && lastTrack != null) {
                    vm.moveMontageTrackClipToTrack(firstMusic.id, lastTrack.id)
                }
                vm.setMontageSelection(2_000L, 6_500L)
                vm.addMontageMarkerAt("منطقة الفحص", 2_000L)
            }
        }
        // المونتاج للأتمتة (v0.37.0): مساران حرّان بمقاطع + تحديد منطقة — تُظهر
        // رؤوس بلا كتم/منفرد والويڤ فورم الجاهز للنقر المزدوج على مقطع التعليق
        if (args.contains("--montage-v37")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(4500)
                vm.addMontageTrack()
                vm.addMontageTrack()
                vm.setMontageSelection(2_000L, 6_000L)
            }
        }
        // المونتاج للأتمتة (v0.38.0): تحديد أول مقطع ونسخه ولصقه — الحافظة
        // تعمل عند الإقلاع فتبقى النقرات الحقيقية للحوارات والأدوات الجديدة
        if (args.contains("--montage-v38")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(4500)
                val first = vm.montageProject.value.clips.firstOrNull()?.id
                if (first != null) {
                    vm.selectMontageClip(first)
                    vm.montageCopySelected()
                    vm.montagePasteClipboard()
                }
            }
        }
        // مكتبة المؤثرات للأتمتة (v0.40.0): تحديد أول مقطع ثم فتح الحوار عبر
        // الاختصار E نفسه (نبضة النموذج) — فتبقى نقرات الفأرة الحقيقية للجاهزات
        if (args.contains("--montage-v40")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(4500)
                val first = vm.montageProject.value.clips.firstOrNull()?.id
                if (first != null) vm.selectMontageClip(first)
                vm.applyMontageShortcut(
                    com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.OpenEffects
                )
            }
        }
        // نظام المونتاج متعدد المسارات للأتمتة (v0.41.0): مسار مؤثرات ومسار
        // حوار بنوعيهما + مجموعة وانتساب مسار إليها + كتم مسار + توازن + أتمتة
        // مسار موسيقى بنقاط ثلاث — فتظهر الرؤوس الجديدة وشريط المجموعات وشريط
        // الأتمتة وظل الخفض على الموسيقى وحدها في لقطات الفحص
        if (args.contains("--montage-v41")) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(4500)
                vm.addMontageTrack(com.saidjte.schoolradio.desktop.data.MontageLogic.TYPE_SFX)
                vm.addMontageTrack(com.saidjte.schoolradio.desktop.data.MontageLogic.TYPE_DIALOGUE)
                vm.addMontageGroup("أجواء الفاصل")
                val tracks = vm.montageTracksEffective()
                val groupId = vm.montageProject.value.groups?.firstOrNull()?.id
                vm.setMontageTrackGroup(tracks[0].id, groupId)
                vm.setMontageTrackMute(tracks[1].id, true)
                vm.setMontageTrackPan(tracks[3].id, -50)
                vm.toggleMontageAutomation(tracks[0].id)
                vm.addMontageAutoPoint(tracks[0].id, 12_000L, 40)
                vm.addMontageAutoPoint(tracks[0].id, 24_000L, 160)
                val firstMusic = vm.montageProject.value.musicClips.firstOrNull()
                if (firstMusic != null) vm.moveMontageTrackClipToTrack(firstMusic.id, tracks[3].id)
                vm.setMontageSelection(2_000L, 6_500L)
            }
        }
        // سحب وإفلات للأتمتة (v0.39.0): ملف WAV حقيقي يُمرّر عبر مسار الإفلات
        // نفسه فتظهر رسالة الاستيراد والمقطع في المكتبة — بلا فأرة نظام
        if (args.contains(com.saidjte.schoolradio.desktop.data.DropLogic.AUTOMATION_FLAG)) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2500)
                val dir = java.io.File("automation").apply { mkdirs() }
                val wav = java.io.File(dir, "dropped-demo.wav")
                if (!wav.isFile || wav.length() == 0L) {
                    val w = com.saidjte.schoolradio.desktop.audio.WavFileWriter(wav, 8000, 1)
                    val sec = ByteArray(16000)
                    var j = 0
                    while (j < sec.size) {
                        val v = (Math.sin(2.0 * Math.PI * 440.0 * (j / 2) / 8000.0) * 12000).toInt()
                        sec[j] = (v and 0xFF).toByte()
                        sec[j + 1] = ((v shr 8) and 0xFF).toByte()
                        j += 2
                    }
                    w.use { it.write(sec) }
                }
                vm.handleDroppedFiles(listOf(wav.absoluteFile))
            }
        }
        // أيقونة النافذة وشريط المهام من موارد الجرة (v0.22.0 «طوّر GUI التطبيق»)
        val windowIcon = remember {
            Thread.currentThread().contextClassLoader
                .getResourceAsStream("icon.png")
                ?.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()) }
                ?.toComposeImageBitmap()
        }
        Window(
            onCloseRequest = { exitApplication() },
            title = "إذاعة مدرسية احترافي — نسخة الحاسوب v$APP_VERSION",
            state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1280.dp, height = 800.dp),
            icon = windowIcon?.let { BitmapPainter(it) },
            // اختصارات لوحة المفاتيح (v0.29.0): على مستوى النافذة قبل المحتوى —
            // وحارس الكتابة يُهمّلها ما دام حقل نصي في البؤرة فالحقول تكتب حرّة
            onPreviewKeyEvent = { ev ->
                com.saidjte.schoolradio.desktop.ui.handleGlobalShortcut(vm, ev, guardTextEditing = true)
            }
        ) {
            // حد أدنى لحجم النافذة يحفظ تخطيط الاستوديو من الانهيار
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(1024, 640) }
            // سحب وإفلات ملفات نظام التشغيل (v0.39.0): هدف إفلات AWT خالص على
            // النافذة كلها — MP3/WAV/OGG/FLAC تُستقبل في أي صفحة وتُوجَّه بحسبها
            LaunchedEffect(Unit) {
                com.saidjte.schoolradio.desktop.ui.DropSupport.install(
                    window,
                    onHover = { vm.dropHover.value = it },
                    onFiles = { files -> vm.handleDroppedFiles(files) }
                )
            }
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(startTab = startTab, vm = vm)
                    // دليل الاختصارات بF1 (v0.39.0): حالة في النموذج فتعمل من أي
                    // نافذة مركّزة وتُغلق بنفس المفتاح أو زر الإغلاق
                    val showSc by vm.showShortcutsDialog.collectAsState()
                    if (showSc) {
                        com.saidjte.schoolradio.desktop.ui.ShortcutsDialog(
                            onDismiss = { vm.showShortcutsDialog.value = false }
                        )
                    }
                }
            }
        }
        // نافذة جدار الجينغل المنبثقة (v0.28.0) — مستقلة غير ماسكة: الاستوديو
        // يظل قابلًا للعمل بكامل أدواته ما دامت مفتوحة، وإغلاقها لا يمس شيئًا
        val wallOpen by vm.jingleWallOpen.collectAsState()
        if (wallOpen) {
            JingleWallWindow(vm = vm, onClose = { vm.jingleWallOpen.value = false })
        }
        // نافذة «على الهواء» لشاشة ثانية (v0.32.0) — مستقلة غير ماسكة كالجدار:
        // الاستوديو يظل عاملاً بكامل أدواته ما دامت مفتوحة
        val onAirOpen by vm.onAirDisplayOpen.collectAsState()
        if (onAirOpen) {
            OnAirWindow(vm = vm, onClose = { vm.onAirDisplayOpen.value = false })
        }
        // نافذة القراءة على الشاشة الثانية (v0.35.0) — على طريقة «على الهواء» تمامًا
        val readingOpen by vm.readingDisplayOpen.collectAsState()
        if (readingOpen) {
            ReadingWindow(vm = vm, onClose = { vm.readingDisplayOpen.value = false })
        }
        // خروج نظيف: الجلسة الأخيرة تُحفظ أولًا (v0.32.0) ولقطة نسخ احتياطية
        // وتحرير قفل التشغيل (v0.39.0)، ثم صوت الجينلات يُغلق
        androidx.compose.runtime.DisposableEffect(Unit) {
            onDispose {
                vm.onCleanShutdown()
                vm.jingles.shutdown()
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = remember { AppViewModel() }, startTab: Tab? = null) {
    // الانتقال إلى صفحة البداية إن حُددت من سطر الأوامر (للأتمتة) — غير ذلك
    // تُستعاد آخر صفحة إن فعّل المذيع «تذكر آخر صفحة» (v0.39.0)
    val ws0 by vm.workspace.collectAsState()
    val restoredLast = remember { mutableStateOf(false) }
    LaunchedEffect(startTab) {
        if (startTab != null) {
            vm.selectTab(startTab)
        } else if (!restoredLast.value && ws0.rememberLastTab && ws0.lastTab != null) {
            restoredLast.value = true
            Tab.entries.firstOrNull { it.name == ws0.lastTab }?.let(vm::selectTab)
        }
    }
    val tab by vm.tab.collectAsState()
    // مساحة العمل (v0.39.0): مقياس الخط العام يُطبق على كل الصفحات ونوافذ
    // التطبيق — والقائمة الجانبية بعرض المذيع المفضل
    val ws by vm.workspace.collectAsState()
    val baseDensity = LocalDensity.current
    val scaled = Density(
        density = baseDensity.density * ws.fontScale.toFloat(),
        fontScale = baseDensity.fontScale * ws.fontScale.toFloat()
    )
    var showSettings by remember { mutableStateOf(false) }
    val micState by vm.mic.state.collectAsState()
    val recording by vm.mic.recording.collectAsState()
    val mixRec by vm.mixRecording.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val paused by vm.player.isPaused.collectAsState()
    // شارة إعلان البث المباشر تُضيء نقطة الحيّة في القائمة كذلك (v0.45.0)
    val announceAt by vm.liveAnnounceStartedAt.collectAsState()
    var announceNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            announceNow = System.currentTimeMillis()
            delay(500)
        }
    }
    // جلسة البث المعلنة (v0.46.0): الإعلان يفتحها و«إنهاء البث» يغلقها
    val broadcastLive by vm.broadcastLive.collectAsState()
    val onAir = StudioLogic.status(
        micLive = micState == MicState.LIVE,
        recording = recording || mixRec,
        musicPlaying = playing && !paused,
        announceActive = LiveAnnounceLogic.isActive(announceAt, announceNow),
        broadcastLive = broadcastLive
    ).onAir

    CompositionLocalProvider(LocalDensity provides scaled) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            // حوار الإعدادات والمستخدمين — يُفتح من القائمة الجانبية في كل الصفحات
            if (showSettings) {
                SettingsDialog(vm = vm, onDismiss = { showSettings = false })
            }
            // حافة الإبراز أثناء سحب ملفات نظام التشغيل فوق النافذة (v0.39.0)
            val dropHover by vm.dropHover.collectAsState()
            if (dropHover) {
                DropHoverOverlay()
            }
            // انتقال ناعم بين الصفحات يخدم السلاسة دون إبطاء التنقّل
            Crossfade(targetState = tab, animationSpec = tween(160), label = "pages") { t ->
                if (t == Tab.STUDIO) {
                    // الاستوديو المدمج: الجدولة + السكريبت + البث في شاشة واحدة بلا قائمة جانبية
                    StudioScreen(vm)
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        SideNav(
                            selected = t,
                            onSelect = vm::selectTab,
                            onAir = onAir,
                            vm = vm,
                            widthDp = ws.sidebarWidth.dp,
                            onOpenSettings = { showSettings = true }
                        )
                        when (t) {
                            Tab.STUDIO -> StudioScreen(vm)
                            Tab.LIVE -> LivePage(vm)
                            Tab.SCHEDULE -> ScheduleTab(vm)
                            Tab.SCRIPT -> ScriptTab(vm)
                            Tab.MUSIC -> MusicTab(vm)
                            Tab.SOUNDS -> SoundsTab(vm)
                            Tab.MIXER -> MixerPage(vm)
                            Tab.RECORDINGS -> RecordingsTab(vm)
                            Tab.MONTAGE -> MontagePage(vm)
                        }
                    }
                }
            }
        }
    }
}

/**
 * حافة الإبراز أثناء سحب ملفات من نظام التشغيل فوق النافذة (v0.39.0) —
 * طبقة شفافة بإطار متقطع ورسالة عربية تعلم المذيع أن الإفلات هنا يعمل.
 */
@Composable
private fun DropHoverOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(
                width = 3.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Text(
                "أفلِت الملفات الصوتية هنا — MP3 وWAV وOGG وFLAC",
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** أيقونة كل صفحة — ترميز بصري ثابت بين القائمة وأي موضع آخر. */
private fun tabIcon(t: Tab): AppIconKind = when (t) {
    Tab.STUDIO -> AppIconKind.STUDIO
    Tab.LIVE -> AppIconKind.LIVE
    Tab.SCHEDULE -> AppIconKind.SCHEDULE
    Tab.SCRIPT -> AppIconKind.SCRIPT
    Tab.MUSIC -> AppIconKind.MUSIC
    Tab.SOUNDS -> AppIconKind.SOUNDS
    Tab.MIXER -> AppIconKind.MIXER
    Tab.RECORDINGS -> AppIconKind.RECORDINGS
    Tab.MONTAGE -> AppIconKind.MONTAGE
}

@Composable
private fun SideNav(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onAir: Boolean,
    vm: AppViewModel,
    widthDp: androidx.compose.ui.unit.Dp = 232.dp,
    onOpenSettings: () -> Unit
) {
    val sc = LocalStudioColors.current
    val me by vm.currentUser.collectAsState()
    val schoolName by vm.schoolName.collectAsState()
    Column(
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // رأس الهوية: شعار البث + الاسم
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            LogoMark(size = 42.dp)
            Column {
                Text(
                    "إذاعة مدرسية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    schoolName ?: "نسخة الحاسوب",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                // شعار لاتيني مرِح بخط الهوية Spicy Sale — توقيع بصري ثنائي اللغة (v0.24.0)
                Text(
                    "SCHOOL RADIO",
                    style = MaterialTheme.typography.labelSmall.brand(),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Tab.entries.forEach { t ->
            NavItem(
                label = t.label,
                icon = tabIcon(t),
                selected = t == selected,
                showLiveDot = (t == Tab.LIVE || t == Tab.STUDIO) && onAir,
                liveDot = sc.liveDot,
                onClick = { onSelect(t) }
            )
        }

        Spacer(Modifier.weight(1f))

        // تذييل: المستخدم الحالي + بوابة الإعدادات
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(kind = AppIconKind.RECORDINGS, size = 18.dp, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    me?.name ?: "ضبط المستخدمين",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    me?.role?.labelAr ?: "اضغط للبدء",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            "v$APP_VERSION",
            style = MaterialTheme.typography.labelSmall.brand(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** عنصر قائمة احترافي: أيقونة + كapsula تحديد + تظليل عند المرور بالفأرة. */
@Composable
private fun NavItem(
    label: String,
    icon: AppIconKind,
    selected: Boolean,
    showLiveDot: Boolean,
    liveDot: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        hovered -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    val contentTint =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .hoverable(interaction)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // شريط تمييز جانبي للعنصر المحدد — إشارة موضع أقوى من اللون وحده (v0.22.0)
        if (selected) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        AppIcon(kind = icon, size = 20.dp, tint = contentTint)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (showLiveDot) {
            PulsingDot(color = liveDot, size = 8.dp)
        }
    }
}
