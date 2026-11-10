package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.AudioFx
import com.saidjte.schoolradio.desktop.audio.BundledJingles
import com.saidjte.schoolradio.desktop.audio.BundledSounds
import com.saidjte.schoolradio.desktop.audio.ClipTransforms
import com.saidjte.schoolradio.desktop.audio.FxChainStep
import com.saidjte.schoolradio.desktop.audio.FxPresets
import com.saidjte.schoolradio.desktop.audio.DuckBus
import com.saidjte.schoolradio.desktop.audio.JingleWallEngine
import com.saidjte.schoolradio.desktop.audio.MicLiveEngine
import com.saidjte.schoolradio.desktop.audio.MixBus
import com.saidjte.schoolradio.desktop.audio.MontageExportFormat
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS
import com.saidjte.schoolradio.desktop.audio.RecordingEngine
import com.saidjte.schoolradio.desktop.audio.RecordingStudio
import com.saidjte.schoolradio.desktop.audio.RecordingItem
import com.saidjte.schoolradio.desktop.audio.RecordingMeta
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.SoundPreviewEngine
import com.saidjte.schoolradio.desktop.audio.StreamProbe
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.youtube.YouTubeImportService
import com.saidjte.schoolradio.desktop.youtube.YouTubeJson
import com.saidjte.schoolradio.desktop.youtube.YtDlpLocator
import com.saidjte.schoolradio.desktop.data.AecState
import com.saidjte.schoolradio.desktop.data.Backup
import com.saidjte.schoolradio.desktop.data.CrossfadeLogic
import com.saidjte.schoolradio.desktop.data.DevicesJson
import com.saidjte.schoolradio.desktop.data.ExecutionsStore
import com.saidjte.schoolradio.desktop.data.FireStatus
import com.saidjte.schoolradio.desktop.data.JinglePad
import com.saidjte.schoolradio.desktop.data.JinglePadJson
import com.saidjte.schoolradio.desktop.data.JingleWallLogic
import com.saidjte.schoolradio.desktop.data.LibraryEntry
import com.saidjte.schoolradio.desktop.data.LibraryMath
import com.saidjte.schoolradio.desktop.data.LiveAnnounceLogic
import com.saidjte.schoolradio.desktop.data.MixerLogic
import com.saidjte.schoolradio.desktop.data.MixerState
import com.saidjte.schoolradio.desktop.data.MusicEqState
import com.saidjte.schoolradio.desktop.data.DuckState
import com.saidjte.schoolradio.desktop.data.Json
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageEnvelope
import com.saidjte.schoolradio.desktop.data.MontageHistory
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMarker
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageStore
import com.saidjte.schoolradio.desktop.data.MontageTemplateCatalog
import com.saidjte.schoolradio.desktop.data.MontageTemplatesLogic
import com.saidjte.schoolradio.desktop.data.MontageTool
import com.saidjte.schoolradio.desktop.data.MontageTrack
import com.saidjte.schoolradio.desktop.data.MusicLibraryStore
import com.saidjte.schoolradio.desktop.data.NamedPlaylist
import com.saidjte.schoolradio.desktop.data.PlaylistMath
import com.saidjte.schoolradio.desktop.data.PlaylistStore
import com.saidjte.schoolradio.desktop.data.ReadingJson
import com.saidjte.schoolradio.desktop.data.RecordingJson
import com.saidjte.schoolradio.desktop.data.SchedRecJson
import com.saidjte.schoolradio.desktop.data.RepeatMode
import com.saidjte.schoolradio.desktop.data.Role
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.CustomSound
import com.saidjte.schoolradio.desktop.data.SoundGroups
import com.saidjte.schoolradio.desktop.data.SoundLibraryMath
import com.saidjte.schoolradio.desktop.data.SoundLibraryStore
import com.saidjte.schoolradio.desktop.data.StudioLayout
import com.saidjte.schoolradio.desktop.data.StudioLayoutLogic
import com.saidjte.schoolradio.desktop.data.ScheduleStore
import com.saidjte.schoolradio.desktop.data.SegmentTimerLogic
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStore
import com.saidjte.schoolradio.desktop.data.ScriptTemplate
import com.saidjte.schoolradio.desktop.data.ScriptTemplatesLogic
import com.saidjte.schoolradio.desktop.data.ScriptPdfExporter
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.SessionJson
import com.saidjte.schoolradio.desktop.data.SessionRestore
import com.saidjte.schoolradio.desktop.data.SessionStore
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.SnapshotHistory
import com.saidjte.schoolradio.desktop.data.StudioLogic
import com.saidjte.schoolradio.desktop.data.User
import com.saidjte.schoolradio.desktop.data.UsersStore
import com.saidjte.schoolradio.desktop.data.VoiceFxState
import com.saidjte.schoolradio.desktop.data.WeeklyEvent
import com.saidjte.schoolradio.desktop.data.WeeklyMath
import com.saidjte.schoolradio.desktop.data.WeeklyStore
import com.saidjte.schoolradio.desktop.data.WorkspaceJson
import com.saidjte.schoolradio.desktop.data.WorkspaceLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.sound.sampled.Mixer
import kotlin.concurrent.thread

/**
 * صفحات التطبيق — فلسفة مزدوجة:
 * «الاستوديو المدمج» صفحة كاملة للإذاعة (جدولة + لوحة قراءة + بث) لمن يريد كل شيء أمام عينيه،
 * و«البث المباشر» صفحة مدمجة أخف لمَ يفضّل التركيز، ولِبقيّة الإدارة صفحات مستقلة
 * كي لا تكتظ صفحة واحدة وينقلب المطلوب إلى عكسه.
 */
enum class Tab(val label: String) {
    STUDIO("الاستوديو المدمج"),
    LIVE("البث المباشر"),
    SCHEDULE("جدولة الفقرات"),
    SCRIPT("سكريبت المذيع"),
    MUSIC("الموسيقى"),
    SOUNDS("مكتبة الأصوات"),
    MIXER("وحدة المزج"),
    RECORDINGS("التسجيلات"),
    MONTAGE("المونتاج")
}

data class Track(val file: File, val title: String, val durationMs: Long, val category: String? = null)

/** حالة التطبيق المشتركة بين الصفحات: البث، الجدولة، السكريبت، الموسيقى، التسجيلات، المستخدمون. */
class AppViewModel {

    private val _tab = MutableStateFlow(Tab.STUDIO)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    val mic = MicLiveEngine()
    val player = MusicPlayerEngine()

    // ================= استعادة المشاريع بعد انقطاع مفاجئ (v0.39.0) =================
    // تُنفَّذ قبل بناء أي مخزن — ترتيب تهيئة الخصائص يكفلها أولًا: فحص كل مخازن
    // JSON، عزل التالف في corrupt/، استعادة أحدث نسخة سليمة من النسخ المتدحرجة،
    // وكشف انقطاع الجلسة السابقة عبر قفل التشغيل (يُحذف عند الإغلاق النظيف فقط).
    private val recoveryReport: com.saidjte.schoolradio.desktop.data.Recovery.Report =
        com.saidjte.schoolradio.desktop.data.Recovery.recoverCorruptStores(
            dataDir(), nowMs = System.currentTimeMillis()
        )
    private val lastRunInterrupted: Boolean =
        com.saidjte.schoolradio.desktop.data.Recovery.wasInterrupted(dataDir())

    init {
        com.saidjte.schoolradio.desktop.data.Recovery.acquireLock(dataDir(), System.currentTimeMillis())
    }

    private val _recoveryNotices = MutableStateFlow<List<String>>(
        buildList {
            if (lastRunInterrupted)
                add("يبدو أن التطبيق أُغلق فجأة آخر مرة — فُحصت المشاريع واستُعيدت آخر حالة سليمة")
            addAll(recoveryReport.notices)
        }
    )

    /** شعار الاستعادة بعد انقطاع مفاجئ — قائمة رسائل عربية تُعرض مرة واحدة. */
    val recoveryNotices: StateFlow<List<String>> = _recoveryNotices.asStateFlow()

    fun dismissRecoveryNotices() {
        _recoveryNotices.value = emptyList()
    }

    /** إغلاق نظيف: حفظ الجلسة ثم لقطة نسخ احتياطية أخيرة ثم تحرير قفل التشغيل. */
    fun onCleanShutdown() {
        runCatching { persistSessionNow() }
        runCatching {
            com.saidjte.schoolradio.desktop.data.AutoBackup.snapshot(
                dataDir(), nowMs = System.currentTimeMillis()
            )
        }
        com.saidjte.schoolradio.desktop.data.Recovery.releaseLock(dataDir())
    }

    // ================= جدار الجينغل (v0.28.0 «أضف جدار الجينغل») =================

    /** محرك الجدار: أصوات قصيرة متعددة معًا على مخرجَي الموسيقى نفسهما. */
    val jingles = JingleWallEngine(outputs = { player.outputsSnapshot() })

    /** فتح نافذة الجدار المنبثقة (غير حاجبة) — يقرؤها Main ويطلبها زر الاستوديو. */
    val jingleWallOpen = MutableStateFlow(false)

    // ================= وضع «على الهواء» لشاشة ثانية (v0.32.0) =================

    /**
     * فتح نافذة العرض للطلاب — ملء الشاشة على الشاشة الثانية (يقرؤها Main ويطلبها
     * زر الشريط العلوي): عرض حيّ للفقرة الجارية والموسيقى والساعة ما يراه الطلاب.
     */
    val onAirDisplayOpen = MutableStateFlow(false)

    // ================= شاشة القراءة على شاشة ثانية (v0.35.0) =================

    /**
     * فتح شاشة القراءة على الشاشة الثانية — على طريقة «على الهواء» تمامًا:
     * نافذة تلقيم مستقلة غير ماسكة يقرؤها Main ويطلبها زر لوحة القراءة،
     * وتتبع الفقرة الجارية أو اختيار المذيع لحظة بلحظة.
     */
    val readingDisplayOpen = MutableStateFlow(false)

    // ================= إعلان البث المباشر (v0.35.0) =================

    /** لحظة بدء آخر إعلان بث — الشارة على شاشة الطلاب مضاءة مدة محدودة. */
    private val _liveAnnounceStartedAt = MutableStateFlow<Long?>(null)
    val liveAnnounceStartedAt: StateFlow<Long?> = _liveAnnounceStartedAt.asStateFlow()

    /** ملفات الجينلات المدمجة المستخرجة بمعرفاتها — مصدر جينل الإعلان. */
    @Volatile private var bundledJingleFiles: Map<String, File> = emptyMap()

    /**
     * إعلان البث المباشر بنقرة واحدة: جينل الافتتاح من القناة المستقلة
     * (نفس موضع جينل الفاصل الآلي فلا تتراكم الأصوات) + شارة «البث المباشر
     * بدأ الآن» على شاشة الطلاب + رسالة تأكيد في الاستوديو.
     */
    fun announceLiveBroadcast() {
        val f = bundledJingleFiles[LiveAnnounceLogic.JINGLE_ID]
        if (f == null || !f.isFile) {
            _lastLibraryMessage.value = "جينل الإعلان غير متاح — أعد فتح التطبيق لتُستخرج الجينلات المدمجة"
            return
        }
        jingles.sting(f)
        _liveAnnounceStartedAt.value = System.currentTimeMillis()
        _lastLibraryMessage.value = "إعلان البث المباشر: «${LiveAnnounceLogic.BANNER_TEXT}» — جينل الافتتاح يعمل الآن"
    }

    private val _jinglePads = MutableStateFlow(JingleWallLogic.emptyPads())
    val jinglePads: StateFlow<List<JinglePad>> = _jinglePads.asStateFlow()

    /** استرجاع الجدار: استخراج المدمجة ثم دمج المحفوظ مع الافتراضي — بلا استثناءات. */
    private fun loadJingleWall() {
        try {
            val dir = File(AppViewModel.dataDir(), "jingles")
            val files = BundledJingles.ensureExtracted(dir)
            // ملفات المدمجة بمعرفاتها — مصدر جينل إعلان البث المباشر (v0.35.0)
            bundledJingleFiles = BundledJingles.ALL.map { it.id }.zip(files).toMap()
            val defaults = JingleWallLogic.bundledDefaults(files, BundledJingles.ALL.map { it.title })
            _jinglePads.value = JingleWallLogic.resolve(settingsStore.jinglePads(), defaults)
            jingles.setVolume(settingsStore.jingleVolume())
        } catch (_: Exception) {
            // فشل استخراج الموارد نادر (قرص ممتلئ): الجدار الفارغ أفضل من انهيار الإقلاع
            _jinglePads.value = JingleWallLogic.emptyPads()
        }
    }

    /** النقر على موضع: تشغيل من الصفر أو إيقاف الجاري. */
    fun toggleJingle(slot: Int) {
        val pad = _jinglePads.value.getOrNull(slot) ?: return
        val f = pad.path?.let(::File) ?: return
        jingles.toggle(slot, f)
    }

    fun stopAllJingles() = jingles.stopAll()

    /**
     * تنفيذ اختصار لوحة المفاتيح (v0.29.0) — البوابة الوحيدة من معالج النافذة
     * إلى أفعال الاستوديو: كل الاختصارات تمرّ من هنا فلا تكرار ولا تسريب.
     */
    fun applyShortcut(a: com.saidjte.schoolradio.desktop.ui.ShortcutAction) {
        when (a) {
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.PlayPauseMusic -> player.toggle()
            is com.saidjte.schoolradio.desktop.ui.ShortcutAction.FireJingle -> toggleJingle(a.slot - 1)
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.SeekBack -> player.seek(player.positionMs.value - 5_000L)
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.SeekForward -> player.seek(player.positionMs.value + 5_000L)
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.NextTrack -> next()
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.PreviousTrack -> previous()
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.ToggleMute -> setMusicMuted(!musicMuted.value)
            is com.saidjte.schoolradio.desktop.ui.ShortcutAction.SwitchTab -> {
                val tabs = Tab.entries
                val idx = (a.index - 1).coerceIn(0, tabs.size - 1)
                selectTab(tabs[idx])
            }
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.ShowShortcuts ->
                showShortcutsDialog.value = !showShortcutsDialog.value
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.ContextualUndo -> {
                when (_tab.value) {
                    Tab.SCRIPT -> undoScript()
                    Tab.MUSIC, Tab.STUDIO -> undoPlaylist()
                    else -> { } // بقية الصفحات بلا سجل عام — المونتاج له بروتوكوله
                }
            }
            com.saidjte.schoolradio.desktop.ui.ShortcutAction.ContextualRedo -> {
                when (_tab.value) {
                    Tab.SCRIPT -> redoScript()
                    Tab.MUSIC, Tab.STUDIO -> redoPlaylist()
                    else -> { }
                }
            }
        }
    }

    /** تعيين مقطع من اختيار المذيع على موضع — ملف صوتي قابل للفك فقط. */
    fun assignJingle(slot: Int, file: File): Boolean {
        if (slot !in 0 until JingleWallLogic.PAD_COUNT) return false
        if (!file.isFile || file.extension.lowercase() !in PLAYABLE_EXTENSIONS) return false
        jingles.stop(slot)
        val pads = _jinglePads.value.toMutableList()
        pads[slot] = JinglePad(index = slot, path = file.absolutePath, title = JingleWallLogic.titleForFile(file))
        _jinglePads.value = pads
        persistJingleWall()
        return true
    }

    /** مسح تعيين موضع — يعود فارغًا متاحًا. */
    fun clearJingle(slot: Int) {
        if (slot !in 0 until JingleWallLogic.PAD_COUNT) return
        jingles.stop(slot)
        val pads = _jinglePads.value.toMutableList()
        pads[slot] = JinglePad(index = slot)
        _jinglePads.value = pads
        persistJingleWall()
    }

    fun setJingleVolume(v: Float) {
        jingles.setVolume(v)
        persistJingleWall()
    }

    private fun persistJingleWall() {
        settingsStore.setJingleWall(jingles.volume.value, JingleWallLogic.encodePads(_jinglePads.value))
    }

    private val _playlist = MutableStateFlow<List<Track>>(emptyList())
    val playlist: StateFlow<List<Track>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _recordingsDir = MutableStateFlow(defaultRecordingsDir())
    val recordingsDir: StateFlow<File> = _recordingsDir.asStateFlow()

    private val _recordings = MutableStateFlow<List<RecordingItem>>(emptyList())
    val recordings: StateFlow<List<RecordingItem>> = _recordings.asStateFlow()

    // ================= المستخدمون والأدوار =================
    // نفس مصفوفة نسخة الهاتف: طالب = بث/تسجيل/تحضير، معلم = + جدولة وتسجيلات، مشرف = + مستخدمون ونسخ

    private var usersStore = UsersStore(File(dataDir(), "users.json"))
    private val _currentUser = MutableStateFlow(usersStore.currentUser())
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _users = MutableStateFlow(usersStore.users())
    val users: StateFlow<List<User>> = _users.asStateFlow()

    private fun currentRole(): Role = _currentUser.value?.role ?: Role.STUDENT

    /** الإدارة على المحتوى (جدولة/تسجيلات) = معلم فأعلى. */
    val canManage: Boolean get() = Role.canManage(currentRole())
    /** إدارة المستخدمين والنسخ الاحتياطي = مشرف فقط. */
    val canAdminister: Boolean get() = Role.canAdminister(currentRole())

    /** تبديل الهوية الحالية — متاح للجميع (الدور تنظيم لا حماية، كالنسخة الهاتفية). */
    fun switchUser(id: Long) {
        if (usersStore.setCurrent(id)) {
            _currentUser.value = usersStore.currentUser()
        }
    }

    /** إضافة مستخدم — للمشرف فقط، إلا عند انعدام المستخدمين (تهيئة أول مرة). */
    fun addUser(name: String, role: Role): Boolean {
        if (_users.value.isNotEmpty() && !canAdminister) return false
        val u = usersStore.add(name, role) ?: return false
        _users.value = usersStore.users()
        _currentUser.value = usersStore.currentUser()
        return true
    }

    fun updateUser(id: Long, name: String, role: Role): Boolean {
        if (!canAdminister) return false
        val ok = usersStore.update(id, name, role)
        if (ok) {
            _users.value = usersStore.users()
            _currentUser.value = usersStore.currentUser()
        }
        return ok
    }

    fun deleteUser(id: Long): Boolean {
        if (!canAdminister) return false
        val ok = usersStore.delete(id)
        if (ok) {
            _users.value = usersStore.users()
            _currentUser.value = usersStore.currentUser()
        }
        return ok
    }

    // ================= الإعدادات العامة =================

    private var settingsStore = SettingsStore(File(dataDir(), "settings.json"))
    private val _schoolName = MutableStateFlow(settingsStore.schoolName())
    val schoolName: StateFlow<String?> = _schoolName.asStateFlow()

    /** اسم المدرسة يظهر في اللوحة — تعديله لمعلم فأعلى. */
    fun setSchoolName(name: String) {
        if (!canManage) return
        settingsStore.setSchoolName(name)
        _schoolName.value = settingsStore.schoolName()
    }

    // ================= مساحة عمل قابلة للتخصيص (v0.39.0) =================
    // حجم الخط العام وعرض القائمة الجانبية ولوحات الاستوديو وتذكّر آخر صفحة —
    // كلها في settings.json فتعود بين الجلسات، وكل قيمة محبوسة داخل حدودها.

    private fun workspaceTabNames(): Set<String> = Tab.entries.map { it.name }.toSet()

    private val _workspace = MutableStateFlow(
        com.saidjte.schoolradio.desktop.data.WorkspaceLogic.resolveState(
            settingsStore.workspace(workspaceTabNames()), workspaceTabNames()
        )
    )
    val workspace: StateFlow<com.saidjte.schoolradio.desktop.data.WorkspaceState> = _workspace.asStateFlow()

    /** تعديل حقل واحد من مساحة العمل: كتابة محبوسة ثم إعادة حسم الحالة. */
    private fun patchWorkspace(patch: (WorkspaceJson) -> WorkspaceJson) {
        settingsStore.setWorkspace(patch(settingsStore.workspace(workspaceTabNames())), workspaceTabNames())
        _workspace.value = com.saidjte.schoolradio.desktop.data.WorkspaceLogic.resolveState(
            settingsStore.workspace(workspaceTabNames()), workspaceTabNames()
        )
    }

    fun setFontScale(scale: Double) = patchWorkspace { it.copy(fontScale = scale) }
    fun setSidebarWidth(dp: Double) = patchWorkspace { it.copy(sidebarWidth = dp) }

    /** إظهار/إخفاء لوحة استوديو — اسم الحقل محفوظ نصًّا من الواجهة. */
    fun setStudioPanel(panel: String, visible: Boolean) = patchWorkspace { w ->
        when (panel) {
            "recordings" -> w.copy(showRecordings = visible)
            "schedule" -> w.copy(showSchedule = visible)
            "script" -> w.copy(showScript = visible)
            "broadcast" -> w.copy(showBroadcast = visible)
            else -> w
        }
    }

    fun setRememberLastTab(on: Boolean) = patchWorkspace { it.copy(rememberLastTab = on) }

    // ================= حفظ اختيار الأجهزة بين الجلسات (v0.20.0) =================
    // الأسماء وحدها تُخزَّن في settings.json — عند الإقلاع تُطابق مع الأجهزة
    // الموجودة فعليًا، وجهاز غير موصول لا يُمحى اختياره (لا كتابة إلا عند
    // اختيار صريح من المذيع).

    /** الاختيار المحفوظ من الجلسة السابقة (أسماء). */
    fun savedDevices(): DevicesJson = settingsStore.devices()

    /** حفظ اختيار الأجهزة الحالي (بأسمائه) — عند اختيار صريح من المذيع. */
    fun saveDevices(in1: String?, in2: String?, out1: String?, out2: String?) {
        settingsStore.setDevices(DevicesJson(input1 = in1, input2 = in2, output1 = out1, output2 = out2))
    }

    /** تفضيلات شاشة القراءة المحفوظة (خط/سرعة/متابعة). */
    fun savedReading(): ReadingJson = settingsStore.reading()

    /** حفظ تفضيلات شاشة القراءة — عند كل تغيير من المذيع. */
    fun saveReading(fontSp: Float, speed: String, follow: Boolean) {
        settingsStore.setReading(ReadingJson(fontSp = fontSp, speed = speed, follow = follow))
    }

    // ================= أبعاد حيزات الاستوديو: التحكم بالفأرة + الحفظ (v0.21.0) =================

    /** الأبعاد المحسومة من الجلسة السابقة — الافتراضي إن لم يُضبط قطع. */
    fun savedLayout(): StudioLayout = settingsStore.layout()

    /** حفظ أبعاد الحيزات بعد إفلات الفاصل — تعود كما ضبطها المذيع عند الإقلاع. */
    fun saveLayout(l: StudioLayout) {
        settingsStore.setLayout(StudioLayoutLogic.toJson(l))
    }

    // ================= الكروس فيد بين المقاطع (v0.31.0) =================

    private val _crossfadeEnabled = MutableStateFlow(settingsStore.crossfade().enabled ?: true)
    val crossfadeEnabled: StateFlow<Boolean> = _crossfadeEnabled.asStateFlow()

    private val _crossfadeMs = MutableStateFlow(settingsStore.crossfade().ms ?: CrossfadeLogic.DEFAULT_MS)
    val crossfadeMs: StateFlow<Long> = _crossfadeMs.asStateFlow()

    /** تفعيل/تعطيل الكروس فيد — يسري على التبديل التالي فورًا ويُحفظ. */
    fun setCrossfadeEnabled(on: Boolean) {
        _crossfadeEnabled.value = on
        settingsStore.setCrossfade(on, _crossfadeMs.value)
        pushCrossfadeToEngine()
    }

    /** نافذة الكروس فيد (500ms..10s) — تسري فورًا وتُحفظ. */
    fun setCrossfadeMs(ms: Long) {
        _crossfadeMs.value = CrossfadeLogic.clampMs(ms)
        settingsStore.setCrossfade(_crossfadeEnabled.value, _crossfadeMs.value)
        pushCrossfadeToEngine()
    }

    private fun pushCrossfadeToEngine() {
        player.crossfadeMs = if (_crossfadeEnabled.value) _crossfadeMs.value else 0L
    }

    /** هل التبديل الحالي مؤهل لكروس فيد؟ (مفعّل + تشغيل فعلي جارٍ + ملف مختلف) */
    private fun wantCrossfadeTo(target: File): Boolean =
        _crossfadeEnabled.value && player.hasTrack.value && player.isPlaying.value &&
                !player.isPaused.value && player.currentFile.value != target

    /** طلب «التالي» من المحرك لحظة اقتراب النهاية — بلا أثر جانبي، والمؤشر لاحقًا عند التأكيد. */
    @Volatile private var pendingCrossfadeIndex = -1

    private fun wireCrossfade() {
        player.onCrossfadeNext = {
            val list = _playlist.value
            if (list.isEmpty()) null
            else {
                val n = PlaylistMath.nextIndex(
                    list.size, _currentIndex.value,
                    _repeatMode.value, _shuffle.value, true, shuffleRandom
                )
                if (n < 0 || n == _currentIndex.value) null
                else {
                    pendingCrossfadeIndex = n
                    list[n].file
                }
            }
        }
        player.onCrossfadeCommitted = { f ->
            val idx = _playlist.value.indexOfFirst { it.file == f }
            if (idx >= 0) {
                _currentIndex.value = idx
                _playingFromLibrary.value = false
                playlistStore.setIndex(_activePlaylistId.value, idx)
            }
            consecutiveFailures = 0
        }
    }

    // ================= وحدة المزج الإذاعية =================
    // فيدرات القنوات + معالج صوت المذيع + معادل الصوت — تُحفظ في settings.json،
    // وكل تغيير يسري على القنوات الحية فورًا (كتلة واحدة ~50ms) بلا قطع صوت.

    private val _mixer = MutableStateFlow(settingsStore.mixer())
    val mixer: StateFlow<MixerState> = _mixer.asStateFlow()

    // الكتم جلسي فقط — درس v0.16.0: لا يلمس القيم المحفوظة إطلاقًا
    private val _micMuted = MutableStateFlow(false)
    val micMuted: StateFlow<Boolean> = _micMuted.asStateFlow()

    private val _musicMuted = MutableStateFlow(false)
    val musicMuted: StateFlow<Boolean> = _musicMuted.asStateFlow()

    private var mixerPersistJob: Job? = null

    /** دفع حالة الوحدة إلى محرّكي القنوات (ميكروفون + موسيقى) + ناقلات الخفض والصدى. */
    private fun pushMixerToEngines() {
        val m = _mixer.value
        mic.setMicFader(m.micFader)
        mic.setMasterFader(m.masterFader)
        mic.setVoiceFx(m.voice)
        mic.setAec(m.aec)
        player.setMusicFader(m.musicFader)
        player.setMasterFader(m.masterFader)
        player.setMusicEq(m.musicEq)
        mic.setMicMuted(_micMuted.value)
        player.setMusicMuted(_musicMuted.value)
        // ناقل الخفض التلقائي: قناة الميكروفون تقوده وقناة الموسيقى تنفّذه
        DuckBus.enabled = m.duck.enabled
        DuckBus.duckDb = m.duck.duckDb
        DuckBus.holdMs = m.duck.holdMs
        if (!m.duck.enabled) {
            DuckBus.musicGain = 1f
            DuckBus.active = false
        }
    }

    private fun updateMixer(transform: (MixerState) -> MixerState) {
        _mixer.value = transform(_mixer.value)
        pushMixerToEngines()
        scheduleMixerPersist()
    }

    /** حفظ مؤجّل 300ms: سحب الفيدرات لا يولّد عشرات الكتابات في الثانية. */
    private fun scheduleMixerPersist() {
        mixerPersistJob?.cancel()
        mixerPersistJob = runScope.launch {
            delay(300)
            settingsStore.setMixer(_mixer.value)
        }
    }

    fun setMicFader(v: Float) = updateMixer { it.copy(micFader = MixerLogic.cleanFader(v)) }

    fun setMusicFader(v: Float) = updateMixer { it.copy(musicFader = MixerLogic.cleanFader(v)) }

    fun setMasterFader(v: Float) = updateMixer { it.copy(masterFader = MixerLogic.cleanFader(v)) }

    fun setMicMuted(m: Boolean) {
        _micMuted.value = m
        mic.setMicMuted(m)
    }

    fun setMusicMuted(m: Boolean) {
        _musicMuted.value = m
        player.setMusicMuted(m)
    }

    fun updateVoiceFx(transform: (VoiceFxState) -> VoiceFxState) =
        updateMixer { it.copy(voice = transform(it.voice)) }

    fun applyVoicePreset(name: String) =
        updateMixer { it.copy(voice = MixerLogic.applyVoicePreset(it.voice, name)) }

    fun updateMusicEq(transform: (MusicEqState) -> MusicEqState) =
        updateMixer { it.copy(musicEq = transform(it.musicEq)) }

    fun applyMusicEqPreset(name: String) =
        updateMixer { it.copy(musicEq = MixerLogic.applyMusicPreset(it.musicEq, name)) }

    // ================= الخفض التلقائي وإلغاء الصدى =================

    /** تحديث حالة الخفض التلقائي (Voice-Over) دفعة واحدة. */
    fun updateDuck(transform: (DuckState) -> DuckState) =
        updateMixer { it.copy(duck = transform(it.duck)) }

    /** تحديث حالة إلغاء الصدى (AEC). */
    fun updateAec(transform: (AecState) -> AecState) =
        updateMixer { it.copy(aec = transform(it.aec)) }

    // ================= سكريبت المذيع =================

    private var scriptStore = ScriptStore(File(dataDir(), "script.json"))
    private val _script = MutableStateFlow(scriptStore.sections())
    val script: StateFlow<List<ScriptSection>> = _script.asStateFlow()

    private val _selectedSectionId = MutableStateFlow<Long?>(null)
    val selectedSectionId: StateFlow<Long?> = _selectedSectionId.asStateFlow()

    /** وضع لوحة القراءة: المقطع المحدد وحده أو السكريبت كاملًا. */
    val fullScriptReading = MutableStateFlow(false)

    fun addSection(title: String, body: String) {
        pushScriptHistory()
        val s = scriptStore.add(title, body)
        _script.value = scriptStore.sections()
        _selectedSectionId.value = s.id
        refreshScriptUndoStates()
    }

    fun updateSection(id: Long, title: String, body: String) {
        pushScriptHistory()
        scriptStore.update(id, title, body)
        _script.value = scriptStore.sections()
        refreshScriptUndoStates()
    }

    fun deleteSection(id: Long) {
        if (scriptStore.sections().none { it.id == id }) return
        pushScriptHistory()
        scriptStore.delete(id)
        _script.value = scriptStore.sections()
        if (_selectedSectionId.value == id) {
            _selectedSectionId.value = _script.value.firstOrNull()?.id
        }
        refreshScriptUndoStates()
    }

    fun moveSection(id: Long, delta: Int) {
        if (scriptStore.sections().none { it.id == id }) return
        pushScriptHistory()
        scriptStore.move(id, delta)
        _script.value = scriptStore.sections()
        refreshScriptUndoStates()
    }

    fun selectSection(id: Long) {
        _selectedSectionId.value = id
        scheduleSessionSave()
    }

    /**
     * تنقّل سلس على لوحة القراءة أثناء البث: الفقرة السابقة/التالية في السكريبت
     * بضغطة واحدة دون فتح صفحة التحرير. الحدود تُنطّق بأمان (أول/آخر فقرة).
     */
    fun selectAdjacentSection(delta: Int) {
        val list = _script.value
        if (list.isEmpty()) return
        val cur = list.indexOfFirst { it.id == _selectedSectionId.value }
        val idx = if (cur < 0) 0 else (cur + delta).coerceIn(0, list.size - 1)
        _selectedSectionId.value = list[idx].id
        scheduleSessionSave()
    }

    val selectedSection: ScriptSection?
        get() = _script.value.firstOrNull { it.id == _selectedSectionId.value }
            ?: _script.value.firstOrNull()

    /**
     * تطبيق قالب سكريبت جاهز (v0.30.0 «قوالب السكريبت»): الإلحاق يضيف فقراته
     * للنهاية، والاستبدال يفرّغ الحالي أولًا — وكلاهما حفظ دفعة واحدة ويحدد
     * الفقرة الأولى المولّدة لتبدأ لوحة القراءة منها مباشرة.
     */
    fun applyScriptTemplate(template: ScriptTemplate, replaceExisting: Boolean) {
        if (replaceExisting) {
            pushScriptHistory()
            scriptStore.clear()
        }
        val added = scriptStore.addAll(ScriptTemplatesLogic.drafts(template))
        _script.value = scriptStore.sections()
        _selectedSectionId.value = added.firstOrNull()?.id ?: _selectedSectionId.value
        refreshScriptUndoStates()
    }

    /** توليد ملف PDF للسكريبت الحالي (v0.30.0 «تصدير PDF») — يستدعى من خيط خلفي. */
    fun exportScriptPdf(file: File): ScriptPdfExporter.Result {
        val school = try { settingsStore.schoolName() } catch (_: Exception) { null }
        return ScriptPdfExporter.export(_script.value, school, java.time.LocalDate.now(), file)
    }

    // ================= جدولة الفقرات =================
    // تعديل الجدولة محصور بمعلم فأعلى — التشغيل الحي متاح للجميع

    private var scheduleStore = ScheduleStore(File(dataDir(), "schedule.json"))
    private val _schedule = MutableStateFlow(scheduleStore.items())
    val schedule: StateFlow<List<ScheduleItem>> = _schedule.asStateFlow()

    private val _episodeStartMinutes = MutableStateFlow(scheduleStore.episodeStartMinutes())
    val episodeStartMinutes: StateFlow<Int?> = _episodeStartMinutes.asStateFlow()

    fun setEpisodeStart(minutes: Int?) {
        if (!canManage) return
        scheduleStore.setEpisodeStart(minutes)
        _episodeStartMinutes.value = minutes
    }

    fun addScheduleItem(
        title: String,
        kind: SegmentKind,
        plannedSeconds: Long,
        mediaPath: String?,
        note: String,
        scriptSectionId: Long? = null
    ) {
        if (!canManage) return
        scheduleStore.add(title, kind, plannedSeconds, mediaPath, note, scriptSectionId)
        _schedule.value = scheduleStore.items()
    }

    fun updateScheduleItem(
        id: Long,
        title: String,
        kind: SegmentKind,
        plannedSeconds: Long,
        mediaPath: String?,
        note: String,
        scriptSectionId: Long? = null
    ) {
        if (!canManage) return
        scheduleStore.update(id, title, kind, plannedSeconds, mediaPath, note, scriptSectionId)
        _schedule.value = scheduleStore.items()
        clampRun()
    }

    fun deleteScheduleItem(id: Long) {
        if (!canManage) return
        scheduleStore.delete(id)
        _schedule.value = scheduleStore.items()
        clampRun()
    }

    fun moveScheduleItem(id: Long, delta: Int) {
        if (!canManage) return
        scheduleStore.move(id, delta)
        _schedule.value = scheduleStore.items()
        clampRun()
    }

    // ================= الجدولة الأسبوعية الدقيقة + سجل التنفيذ =================

    private var weeklyStore = WeeklyStore(File(dataDir(), "weekly.json"))
    private var executionsStore = ExecutionsStore(File(dataDir(), "executions.json"))

    private val _weeklyEvents = MutableStateFlow(weeklyStore.events())
    val weeklyEvents: StateFlow<List<WeeklyEvent>> = _weeklyEvents.asStateFlow()

    private val _executions = MutableStateFlow(executionsStore.entries())
    val executions: StateFlow<List<com.saidjte.schoolradio.desktop.data.ExecutionEntry>> =
        _executions.asStateFlow()

    /** تنبيه لحظي لموعد أسبوعي انطلق الآن (شريط على صفحات البث). */
    private val _activeAlert = MutableStateFlow<WeeklyEvent?>(null)
    val activeAlert: StateFlow<WeeklyEvent?> = _activeAlert.asStateFlow()
    private var alertSinceMs = 0L

    /** مفاتيح «eventId@تاريخ» المقيدة خلال هذه الجلسة — حارس تكرار فوري. */
    private val firedThisSession = mutableSetOf<String>()

    fun addWeeklyEvent(title: String, dayOfWeek: Int, hour: Int, minute: Int, autoStartRun: Boolean): Boolean {
        if (!canManage) return false
        val e = weeklyStore.add(title, dayOfWeek, WeeklyMath.minutesOfDay(hour, minute), autoStartRun) ?: return false
        _weeklyEvents.value = weeklyStore.events()
        return e.id > 0L
    }

    fun updateWeeklyEvent(id: Long, title: String, dayOfWeek: Int, hour: Int, minute: Int, autoStartRun: Boolean): Boolean {
        if (!canManage) return false
        val ok = weeklyStore.update(id, title, dayOfWeek, WeeklyMath.minutesOfDay(hour, minute), autoStartRun)
        if (ok) _weeklyEvents.value = weeklyStore.events()
        return ok
    }

    fun toggleWeeklyEvent(id: Long, on: Boolean) {
        if (!canManage) return
        if (weeklyStore.setEnabled(id, on)) _weeklyEvents.value = weeklyStore.events()
    }

    fun deleteWeeklyEvent(id: Long) {
        if (!canManage) return
        if (weeklyStore.delete(id)) _weeklyEvents.value = weeklyStore.events()
    }

    fun dismissAlert() {
        _activeAlert.value = null
    }

    /** نبضة الجدول الزمني: قرار حتمي → قيد في السجل → إطلاق التنبيه/الحلقة. */
    private fun tickWeekly() {
        val now = System.currentTimeMillis()
        val keys = firedThisSession + executionsStore.entries()
            .mapNotNull { e -> if (e.eventId != null) "${e.eventId}@${e.dateKey}" else null }
        val decisions = WeeklyMath.decide(weeklyStore.events(), keys, now)
        for (d in decisions) {
            val logged = executionsStore.log(
                d.event.id, d.event.title ?: "", d.dateKey, d.atEpochMs, d.status
            )
            if (!logged) continue // القيد موجود — لا تكرار يومي
            firedThisSession.add("${d.event.id}@${d.dateKey}")
            if (d.status == FireStatus.FIRED) {
                alertSinceMs = now
                _activeAlert.value = d.event
                if (d.event.autoStartRun == true) startRun()
            }
        }
        // تطهير التنبيه بعد 5 دقائق
        if (_activeAlert.value != null && now - alertSinceMs > 5 * 60_000L) {
            _activeAlert.value = null
        }
        weeklyStore.touch(now)
    }

    // ================= تسجيل مزيج الحصة (v0.31.0) =================
    // مايك + موسيقى + جينلات في ملف WAV واحد — نفس عقد التوقيع والمكتبة

    val mixRecording: StateFlow<Boolean> get() = MixBus.recording
    val mixDurationMs: StateFlow<Long> get() = MixBus.durationMs
    val mixLevel: StateFlow<Float> get() = MixBus.level

    private val _mixError = MutableStateFlow<String?>(null)
    val mixError: StateFlow<String?> = _mixError.asStateFlow()

    /** بدء تسجيل المزيج إلى ملف مؤرّخ في مجلد التسجيلات. */
    fun startMixRecording(): Boolean {
        if (MixBus.recording.value) return false
        val f = RecordingsScanner.newRecordingFile(_recordingsDir.value, prefix = "مزيج-الحصة", extension = "wav")
        val ok = MixBus.start(f)
        _mixError.value = if (ok) null else (MixBus.error.value ?: "تعذّر بدء تسجيل المزيج")
        return ok
    }

    /** إيقاف التسجيل وحفظ الملف وتوقيعه بالمُسجّل وتحديث المكتبة. */
    fun stopMixRecording() {
        if (!MixBus.recording.value) return
        val target = MixBus.targetFile
        val dur = MixBus.stop()
        if (target != null && dur > 0L) {
            val u = _currentUser.value
            MixBus.sidecarMeta(target, dur, u?.name ?: "مستخدم", (u?.role ?: Role.STUDENT).labelAr)
        }
        _mixError.value = null
        refreshRecordings()
    }

    // ================= استوديو التسجيل الاحترافي (v0.43.0) =================
    // بنود الطلب الأحد عشر على محرك مستقل عن البث المباشر: جودة موحّدة
    // مضمونة مهما كان العتاد، وأجهزة متعددة بالتوازي (متعدد المسارات)،
    // وإيقاف/استئناف، ومراقبة حية، ومقياس وتشبع، وجدولة، وفوقية مونتاج.

    /** محرك التسجيل الاحترافي — مستقل تمامًا عن MicLiveEngine فلا تعارض قنوات. */
    val recorder = RecordingEngine()

    /** أجهزة الإدخال المتاحة حاليًا — تُحدَّث بطلب المذيع («تحديث»). */
    private val _recorderMics = MutableStateFlow(AudioDevices.listMics())
    val recorderMics: StateFlow<List<AudioDevices.MicDevice>> = _recorderMics.asStateFlow()

    fun refreshRecorderMics() {
        _recorderMics.value = AudioDevices.listMics()
        // إسقاط الأسماء التي لم تعد موجودة من الاختيار الحي فقط —
        // المحفوظ يبقى كما كتبه المذيع (عقد «جهاز غير موصول لا يُمحى»)
        val alive = _recorderMics.value.map { it.name }.toSet()
        if (_recorderSelected.value.any { it !in alive }) {
            _recorderSelected.value = _recorderSelected.value.filter { it in alive }
        }
    }

    /** الأجهزة المختارة للتسجيل المتعدد (أسماء — حد أربعة كأقصى للمسارات المتوازية). */
    private val _recorderSelected = MutableStateFlow(settingsStore.recordingStudio().deviceNames ?: emptyList())
    val recorderSelected: StateFlow<List<String>> = _recorderSelected.asStateFlow()

    /** الجودة المطلوبة (بت × معدل) — الملف يخرج بها دائمًا. */
    private val _recorderQuality = MutableStateFlow(
        RecordingStudio.Quality(
            settingsStore.recordingStudio().bits ?: 16,
            settingsStore.recordingStudio().rateHz ?: 48_000
        ).clamp()
    )
    val recorderQuality: StateFlow<RecordingStudio.Quality> = _recorderQuality.asStateFlow()

    /** سماع المراقبة أثناء التسجيل — الافتراضي معطّل. */
    private val _recorderMonitor = MutableStateFlow(settingsStore.recordingStudio().monitor ?: false)
    val recorderMonitor: StateFlow<Boolean> = _recorderMonitor.asStateFlow()

    /** رسالة الاستوديو الحية (خطأ/تأكيد) تُعرض في اللوحة نفسها. */
    private val _recorderMessage = MutableStateFlow<String?>(null)
    val recorderMessage: StateFlow<String?> = _recorderMessage.asStateFlow()

    /** جدولة التسجيل المحفوظة. */
    private val _recorderSchedules = MutableStateFlow(settingsStore.recordingSchedules())
    val recorderSchedules: StateFlow<List<RecordingStudio.ScheduledItem>> = _recorderSchedules.asStateFlow()

    /** البند المجدول الذي أطلق الجلسة الجارية — لوقفه الآلي عند مدته. */
    @Volatile private var scheduledActiveId: String? = null

    private fun persistRecordingStudio() {
        settingsStore.setRecordingStudio(
            RecordingJson(
                bits = _recorderQuality.value.bits,
                rateHz = _recorderQuality.value.rateHz,
                monitor = _recorderMonitor.value,
                deviceNames = _recorderSelected.value,
                schedules = _recorderSchedules.value.map {
                    SchedRecJson(
                        id = it.id, title = it.title, startMinutes = it.startMinutes,
                        durationMinutes = it.durationMinutes, repeat = it.repeat,
                        weeklyDays = it.weeklyDays, enabled = it.enabled,
                        lastFiredDateKey = it.lastFiredDateKey
                    )
                }
            )
        )
    }

    /** تبديل اختيار جهاز للتسجيل المتعدد — حتى أربعة مسارات متوازية. */
    fun toggleRecorderDevice(name: String) {
        val cur = _recorderSelected.value
        _recorderSelected.value = if (name in cur) {
            cur - name
        } else {
            (cur + name).takeLast(4)
        }
        persistRecordingStudio()
    }

    fun setRecorderBits(bits: Int) {
        _recorderQuality.value = _recorderQuality.value.copy(bits = bits).clamp()
        persistRecordingStudio()
    }

    fun setRecorderRate(rateHz: Int) {
        _recorderQuality.value = _recorderQuality.value.copy(rateHz = rateHz).clamp()
        persistRecordingStudio()
    }

    fun setRecorderMonitor(on: Boolean) {
        _recorderMonitor.value = on
        recorder.setMonitor(on)
        persistRecordingStudio()
    }

    /**
     * حل أسماء الأجهزة المختارة إلى Mixer.Info فعلية — الاسم الغائب
     * (جهاز فُصل) يُسقط بهدوء والبقية تعمل، فلا فشل كامل بسبب جهاز واحد.
     */
    private fun resolveRecorderDevices(): List<Mixer.Info?> {
        val mics = _recorderMics.value
        val byName = mics.associateBy { it.name }
        val chosen = _recorderSelected.value.mapNotNull { byName[it]?.mixerInfo }
        return if (chosen.isEmpty()) listOf(mics.firstOrNull()?.mixerInfo) else chosen
    }

    /** بدء تسجيل احترافي — الأجهزة المختارة كلها بالتوازي بالجودة المطلوبة. */
    fun startProRecording(): Boolean {
        if (recorder.state.value != RecordingStudio.RecState.IDLE) return false
        val devices = resolveRecorderDevices().filterNotNull()
        if (devices.isEmpty()) {
            _recorderMessage.value = "لا يوجد جهاز إدخال متاح — وصّل ميكروفونًا واضغط «تحديث»"
            return false
        }
        val u = _currentUser.value
        recorder.recorderName = u?.name ?: "مستخدم"
        recorder.recorderRole = (u?.role ?: Role.STUDENT).labelAr
        recorder.setMonitor(_recorderMonitor.value)
        val ok = recorder.start(
            devices,
            _recorderQuality.value,
            _recordingsDir.value,
            _recorderMonitor.value
        )
        _recorderMessage.value = if (ok) {
            "بدأ التسجيل الاحترافي على ${devices.size} ${if (devices.size == 1) "جهاز" else "أجهزة"} — ${_recorderQuality.value.labelAr()}"
        } else {
            recorder.error.value ?: "تعذّر بدء التسجيل الاحترافي"
        }
        return ok
    }

    /** إيقاف مؤقت — المدة تتجمد والملفات تبقى مفتوحة للاستئناف. */
    fun pauseProRecording(): Boolean {
        val ok = recorder.pause()
        if (ok) _recorderMessage.value = "التسجيل متوقف مؤقتًا — «استئناف» يكمل في الملفات نفسها"
        return ok
    }

    /** استئناف التسجيل في الملفات نفسها. */
    fun resumeProRecording(): Boolean {
        val ok = recorder.resume()
        if (ok) _recorderMessage.value = "استُؤنف التسجيل"
        return ok
    }

    /** إنهاء التسجيل وحفظ الملفات وتوقيعها وتحديث المكتبة. */
    fun stopProRecording() {
        val fromSchedule = scheduledActiveId
        val takes = recorder.stop()
        scheduledActiveId = null
        refreshRecordings()
        if (takes.isNotEmpty()) {
            _recorderMessage.value =
                if (takes.size == 1) "حُفظ «${takes[0].file.nameWithoutExtension}» (${montageClock(takes[0].durationMs)}) في التسجيلات"
                else "حُفظت ${takes.size} ملفات — ملف لكل جهاز — في التسجيلات (التسجيل متعدد المسارات)"
            if (fromSchedule != null) {
                settingsStore.setScheduleFired(fromSchedule, RecordingStudio.dateKeyOf(System.currentTimeMillis()))
            }
        } else {
            _recorderMessage.value = recorder.error.value ?: "انتهى التسجيل بلا ملف صالح"
        }
        refreshRecorderMics()
    }

    // ---- الجدولة: الإضافة والتعديل والحذف والنبضة ----

    fun addRecorderSchedule(item: RecordingStudio.ScheduledItem) {
        val withId = if (item.id.isBlank()) item.copy(id = "sr${System.nanoTime()}") else item
        _recorderSchedules.value = (_recorderSchedules.value + RecordingStudio.sanitize(withId))
            .take(24) // سقف البنود — فلا جدولة لا نهائية تعيث فسادًا
        persistRecordingStudio()
        _recorderMessage.value = "أُضيفت الجدولة «${withId.title}» — ${RecordingStudio.repeatLabel(withId)} ${RecordingStudio.clockOf(withId.startMinutes)}"
    }

    fun toggleRecorderSchedule(id: String) {
        _recorderSchedules.value = _recorderSchedules.value.map {
            if (it.id == id) it.copy(enabled = !it.enabled) else it
        }
        persistRecordingStudio()
    }

    fun removeRecorderSchedule(id: String) {
        _recorderSchedules.value = _recorderSchedules.value.filterNot { it.id == id }
        persistRecordingStudio()
    }

    /**
     * نبضة التسجيل المجدول — كل عشر ثوانٍ: المستحق يُطلق (إن كان المحرك
     * خاملًا وإلا بقي مستحقًا حتى يفرغ)، والمنطلَق بمدة محددة يُوقف آليًا
     * عند اكتمالها، والفائت يُسجَّل فلا يُطلق متأخرًا فيكرّر نفسه.
     */
    fun tickScheduledRecordings() {
        val now = System.currentTimeMillis()
        // الإيقاف الآلي للجلسة المجدولة عند اكتمال مدتها
        val activeId = scheduledActiveId
        if (activeId != null) {
            val item = _recorderSchedules.value.firstOrNull { it.id == activeId }
            val dur = item?.durationMinutes
            if (item == null || (dur != null && recorder.elapsedMs.value >= dur * 60_000L)) {
                stopProRecording()
            }
            return
        }
        if (recorder.state.value != RecordingStudio.RecState.IDLE) return
        for (item in _recorderSchedules.value) {
            when (RecordingStudio.decide(item, now).status) {
                RecordingStudio.SchedStatus.DUE -> {
                    if (startProRecording()) {
                        scheduledActiveId = item.id
                        settingsStore.setScheduleFired(item.id, RecordingStudio.dateKeyOf(now))
                        return
                    }
                    // فشل البدء: تُترك مستحقًا — النبضة القادمة تحاول حتى نهاية النافذة
                }
                RecordingStudio.SchedStatus.MISSED -> {
                    settingsStore.setScheduleFired(item.id, RecordingStudio.dateKeyOf(now))
                    _recorderSchedules.value = _recorderSchedules.value.map {
                        if (it.id == item.id) it.copy(lastFiredDateKey = RecordingStudio.dateKeyOf(now)) else it
                    }
                    _recorderMessage.value = "فات موعد التسجيل المجدول «${item.title}» — سُجّل فائتًا"
                }
                else -> { /* بانتظار موعده أو منفّذ اليوم أو معطّل */ }
            }
        }
    }

    // ---- الفوقية: التسجيل مع سماع المسارات الأخرى وإدراجها في المونتاج ----

    /** هل الجلسة الجارية فوقية على المونتاج؟ */
    private val _overdubActive = MutableStateFlow(false)
    val overdubActive: StateFlow<Boolean> = _overdubActive.asStateFlow()

    /** موضع الرأس لحظة بدء الفوقية — الإدراج عند هذا الموضع بالضبط. */
    @Volatile private var overdubStartMs = 0L

    /** انتظار اكتمال تصيير المعاينة قبل إطلاق مسجّل الفوقية. */
    @Volatile private var overdubPending = false

    /** مسار وجهة الفوقية المحسوم عند البدء. */
    @Volatile private var overdubTrackId: String? = null

    /**
     * بدء الفوقية: تشغيل المعاينة من الرأس (تُسمَع المسارات الأخرى عبر
     * السماعات — هذا هو «سماع المسارات») والتسجيل عليها في آن واحد.
     */
    fun startMontageOverdub() {
        if (recorder.state.value != RecordingStudio.RecState.IDLE) {
            _recorderMessage.value = "التسجيل يعمل بالفعل — أنهِ الجلسة الحالية أولًا"
            return
        }
        val p = _montageProject.value
        val devices = resolveRecorderDevices().filterNotNull()
        if (devices.isEmpty()) {
            _recorderMessage.value = "لا يوجد جهاز إدخال متاح — وصّل ميكروفونًا واضغط «تحديث»"
            return
        }
        overdubStartMs = _montagePlayheadMs.value.coerceAtLeast(0L)
        val (project, trackId) = MontageLogic.ensureOverdubTrack(p)
        if (project != p) applyMontage(project)
        overdubTrackId = trackId
        overdubPending = true
        // المعاينة الجاهزة تبدأ فورًا، وغير الجاهزة تُصيَّر ثم تبدأ —
        // وفي الحالين hook اكتمال التشغيل يطلق المسجّل لحظة بدء الصوت
        toggleMontagePreview()
        if (player.isPlaying.value && player.currentFile.value == montagePreviewFile) {
            launchOverdubRecorder(devices)
        }
    }

    /** إطلاق مسجّل الفوقية لحظة بدء تشغيل المعاينة فعليًا. */
    private fun launchOverdubRecorder(devices: List<Mixer.Info?>) {
        overdubPending = false
        val u = _currentUser.value
        recorder.recorderName = u?.name ?: "مستخدم"
        recorder.recorderRole = (u?.role ?: Role.STUDENT).labelAr
        recorder.setMonitor(false) // سماع المسارات يمر عبر المعاينة نفسها — لا تكرار مزدوج
        val ok = recorder.start(devices, _recorderQuality.value, _recordingsDir.value, false)
        if (ok) {
            _overdubActive.value = true
            _recorderMessage.value =
                "الفوقية جارية من ${montageClock(overdubStartMs)} — سمع المسارات يعمل وأنهِ بالزر حين تنهي"
        } else {
            _recorderMessage.value = recorder.error.value ?: "تعذّر بدء تسجيل الفوقية"
        }
    }

    /**
     * إنهاء الفوقية: إيقاف التشغيل والتسجيل، تثبيت الرأس حيث وصل، ثم
     * إدراج الملف المسجَّل مقطعًا على مسار الوجهة عند موضع البدء —
     * إدراج غير إتلافي يمر ببوابة applyMontage ككل تعديلات المونتاج.
     */
    fun stopMontageOverdub() {
        if (!_overdubActive.value) return
        _overdubActive.value = false
        if (player.currentFile.value == montagePreviewFile && player.hasTrack.value) {
            val pos = player.positionMs.value
            player.stop()
            setMontagePlayhead(pos)
        }
        val takes = recorder.stop()
        val startMs = overdubStartMs
        val trackId = overdubTrackId
        refreshRecordings()
        if (takes.isEmpty()) {
            _recorderMessage.value = recorder.error.value ?: "انتهت الفوقية بلا ملف صالح فلم يُدرج شيء"
            return
        }
        val take = takes.first()
        val clip = MontageLogic.overdubClip(
            path = take.file.absolutePath,
            title = take.file.nameWithoutExtension,
            durationMs = take.durationMs,
            startMs = startMs,
            trackId = trackId ?: MontageLogic.DEFAULT_TRACK_ID
        )
        val p = _montageProject.value
        if (p.musicClips.size >= MontageLogic.MAX_TRACK_CLIPS) {
            _recorderMessage.value = "حُفظ التسجيل في المكتبة — المسار ممتلئ (سقف ${MontageLogic.MAX_TRACK_CLIPS})"
            return
        }
        applyMontage(p.copy(musicClips = p.musicClips + clip))
        _recorderMessage.value =
            "أُدرج التسجيل «${clip.title}» على مسار الفوقية عند ${montageClock(startMs)} — ${montageClock(take.durationMs)}"
    }

    /** خيارات المسارات لحوار «إلى المونتاج» — الافتراضي أولًا ثم المسارات الحرة. */
    fun montageTrackChoices(): List<Pair<String, String>> {
        val p = _montageProject.value
        val eff = MontageLogic.effectiveTracks(p)
        return eff.map { it.id to it.name }
    }

    /** إرسال تسجيل من المكتبة إلى مسار مونتاج عند رأس التشغيل (التسجيل متعدد المسارات). */
    fun sendRecordingToMontage(file: File, trackId: String) {
        if (!file.isFile) {
            _recorderMessage.value = "الملف غير موجود على القرص"
            return
        }
        addMontageMusicFiles(listOf(file), trackId)
        _recorderMessage.value = "أُضيف «${file.nameWithoutExtension}» إلى المونتاج عند رأس التشغيل"
    }

    // ================= مؤقت الفاصل الآلي (v0.31.0) =================

    private val _timerAutoAdvance = MutableStateFlow(settingsStore.segmentTimer().autoAdvance ?: true)
    val timerAutoAdvance: StateFlow<Boolean> = _timerAutoAdvance.asStateFlow()

    private val _timerSting = MutableStateFlow(settingsStore.segmentTimer().sting ?: true)
    val timerSting: StateFlow<Boolean> = _timerSting.asStateFlow()

    private val _timerWarnSeconds = MutableStateFlow(
        settingsStore.segmentTimer().warnSeconds ?: SegmentTimerLogic.DEFAULT_WARN_SECONDS.toInt()
    )
    val timerWarnSeconds: StateFlow<Int> = _timerWarnSeconds.asStateFlow()

    /** المتبقي لفقرة الحلقة الجارية (null = بلا زمن مخطط أو الحلقة موقوفة). */
    private val _runRemainingMs = MutableStateFlow<Long?>(null)
    val runRemainingMs: StateFlow<Long?> = _runRemainingMs.asStateFlow()

    /** داخل نافذة التحذير (آخر ثواني التحذير قبل الإطلاق). */
    private val _runWarn = MutableStateFlow(false)
    val runWarn: StateFlow<Boolean> = _runWarn.asStateFlow()

    @Volatile private var timerFiredForItem = false

    fun setTimerAutoAdvance(on: Boolean) {
        _timerAutoAdvance.value = on
        persistSegmentTimer()
    }

    fun setTimerSting(on: Boolean) {
        _timerSting.value = on
        persistSegmentTimer()
    }

    private fun persistSegmentTimer() {
        settingsStore.setSegmentTimer(_timerAutoAdvance.value, _timerSting.value, _timerWarnSeconds.value)
    }

    /** نبضة المؤقت: قرار حتمي من المنطق النقي ثم أفعاله (جينل الفاصل + تقدم آلي). */
    private fun tickSegmentTimer() {
        if (!_runActive.value) return
        val item = _schedule.value.getOrNull(_runCurrentIndex.value)
        if (item == null || item.plannedSeconds <= 0L) {
            _runRemainingMs.value = null
            _runWarn.value = false
            return
        }
        val d = SegmentTimerLogic.decide(
            _runElapsedMs.value, item.plannedSeconds, _timerWarnSeconds.value.toLong()
        )
        _runRemainingMs.value = d.remainingMs
        _runWarn.value = d.warn && !d.fire
        if (d.fire && !timerFiredForItem) {
            timerFiredForItem = true
            if (_timerSting.value) playIntervalSting()
            if (_timerAutoAdvance.value) finishCurrentItem()
        }
    }

    /** صوت الفاصل: المدمج «مقدمة فقرة» المستخرج مع جدار الجينغل — إطلاق خارج الشبكة. */
    private fun playIntervalSting() {
        val f = File(AppViewModel.dataDir(), "jingles/intro_sting.mp3")
        jingles.sting(f)
    }

    // ================= التشغيل الحي للحلقة =================

    private val runScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _runActive = MutableStateFlow(false)
    val runActive: StateFlow<Boolean> = _runActive.asStateFlow()

    private val _runCurrentIndex = MutableStateFlow(-1)
    val runCurrentIndex: StateFlow<Int> = _runCurrentIndex.asStateFlow()

    private val _runElapsedMs = MutableStateFlow(0L)
    val runElapsedMs: StateFlow<Long> = _runElapsedMs.asStateFlow()

    /** ساعة الحاسوب الحية (HH:mm:ss) لشريط الاستوديو العلوي. */
    private val _clockText = MutableStateFlow(StudioLogic.clockText(System.currentTimeMillis()))
    val clockText: StateFlow<String> = _clockText.asStateFlow()

    private var itemStartedAtNanos = 0L
    private var tickerJob: Job? = null

    /** بدء الحلقة: الفقرة الأولى تصبح «جارية» ويبدأ عدّها. */
    fun startRun() {
        if (_runActive.value || _schedule.value.isEmpty()) return
        _runActive.value = true
        _runCurrentIndex.value = 0
        beginItem()
    }

    /** إنهاء الفقرة الجارية والانتقال إلى التالية (أو إنهاء الحلقة عند بلوغ النهاية). */
    fun finishCurrentItem() {
        if (!_runActive.value) return
        val next = StudioLogic.nextRunIndex(_runCurrentIndex.value, _schedule.value.size)
        if (next != null) {
            _runCurrentIndex.value = next
            beginItem()
        } else {
            endRun()
        }
    }

    /** إنهاء الحلقة: كل الحالات تعود إلى «قادمة». */
    fun endRun() {
        _runActive.value = false
        _runCurrentIndex.value = -1
        _runElapsedMs.value = 0L
        _runRemainingMs.value = null
        _runWarn.value = false
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun beginItem() {
        itemStartedAtNanos = System.nanoTime()
        _runElapsedMs.value = 0L
        timerFiredForItem = false
        _runRemainingMs.value = null
        _runWarn.value = false
        followScriptForCurrent()
        tickerJob?.cancel()
        tickerJob = runScope.launch {
            while (true) {
                delay(200)
                _runElapsedMs.value = (System.nanoTime() - itemStartedAtNanos) / 1_000_000L
                tickSegmentTimer()
            }
        }
    }

    /**
     * تكامل الاستوديو المدمج: عند جريان فقرة مرتبطة بفقرة سكريبت،
     * تنتقل لوحة القراءة إليها آليًا وتخرج من وضع القراءة الكاملة.
     */
    private fun followScriptForCurrent() {
        val sid = _schedule.value.getOrNull(_runCurrentIndex.value)?.scriptSectionId ?: return
        if (sid <= 0L) return
        if (_script.value.any { it.id == sid }) {
            _selectedSectionId.value = sid
            fullScriptReading.value = false
        }
    }

    /** حفظ تماسك المؤشر الحي بعد أي تعديل على القائمة. */
    private fun clampRun() {
        if (!_runActive.value) return
        if (_schedule.value.isEmpty()) {
            endRun()
            return
        }
        if (_runCurrentIndex.value >= _schedule.value.size) {
            _runCurrentIndex.value = _schedule.value.size - 1
            beginItem()
        }
    }

    /** تشغيل الملف المرتبط بفقرة الجدولة (موسيقى/تسجيل) — مع كروس فيد إن كان شيء يشتغل. */
    fun runPlayMedia(item: ScheduleItem) {
        val p = item.mediaPath ?: return
        val f = File(p)
        if (f.isFile) player.play(f, wantCrossfadeTo(f))
    }

    // ================= قوائم التشغيل المسماة الدائمة =================
    // عدة قوائم باسم المستخدم يختارها، واحدة منها «الجارية» في المشغل —
    // تُحفظ في playlist.json: كل قائمة مساراتها + الجاري، وتكرار القائمة الجارية.

    private var playlistStore = PlaylistStore(File(dataDir(), "playlist.json"))
    private val _repeatMode = MutableStateFlow(playlistStore.repeatMode())
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffle = MutableStateFlow(playlistStore.shuffle())
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** هل المقطع الجاري بُثّ من المكتبة مباشرة (لا من القائمة)؟ — شارة في المشغل. */
    private val _playingFromLibrary = MutableStateFlow(false)
    val playingFromLibrary: StateFlow<Boolean> = _playingFromLibrary.asStateFlow()

    /** عشوائية ببذرة زمنية — المنطق نفسه خالص ويُختبر ببذرة مثبّتة. */
    private val shuffleRandom = kotlin.random.Random(System.nanoTime())

    /** كل القوائم المسماة + هوية القائمة الجارية — تُعرض للاختيار في الاستوديو والمشغل. */
    private val _playlists = MutableStateFlow(playlistStore.playlists())
    val playlists: StateFlow<List<NamedPlaylist>> = _playlists.asStateFlow()

    private val _activePlaylistId = MutableStateFlow(playlistStore.activeId())
    val activePlaylistId: StateFlow<Long> = _activePlaylistId.asStateFlow()

    /** اسم القائمة الجارية — شارة العرض في بطاقات البث. */
    val activePlaylistName: String
        get() = playlistStore.playlists().firstOrNull { it.id == _activePlaylistId.value }?.name ?: ""

    /** نظام التكرار الثلاثي: لا → الكل → المقطع → لا. */
    fun cycleRepeat() {
        _repeatMode.value = _repeatMode.value.next()
        playlistStore.setRepeatMode(_repeatMode.value)
    }

    /** تبديل العشوائية: التالي/السابق يُخيَّران من غير الجاري. */
    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
        playlistStore.setShuffle(_shuffle.value)
    }

    /** إنشاء قائمة جديدة وتفعيلها (تفريغ المشغل عن القائمة السابقة). */
    fun createPlaylist(name: String): Boolean {
        val p = playlistStore.create(name) ?: return false
        player.stop()
        refreshPlaylistState(loadTracks = true)
        return true
    }

    fun renamePlaylist(id: Long, name: String): Boolean {
        val ok = playlistStore.rename(id, name)
        if (ok) refreshPlaylistState()
        return ok
    }

    /** حذف قائمة — تبقى واحدة على الأقل؛ وحذف الجارية يحمل الأولى إلى المشغل. */
    fun deletePlaylist(id: Long): Boolean {
        val wasActive = id == _activePlaylistId.value
        val ok = playlistStore.delete(id)
        if (!ok) return false
        if (wasActive) {
            player.stop()
            player.awaitIdle(1500)
        }
        refreshPlaylistState(loadTracks = wasActive)
        return true
    }

    /** اختيار قائمة لتصبح الجارية — إيقاف ما يشتغل الآن وتحميل مقاطعها. */
    fun selectPlaylist(id: Long): Boolean {
        if (id == _activePlaylistId.value) return true
        val ok = playlistStore.setActive(id)
        if (!ok) return false
        player.stop()
        player.awaitIdle(1500)
        refreshPlaylistState(loadTracks = true)
        return true
    }

    /** اختيار قائمة وتشغيلها فورًا من أول مقطع — من الاستوديو والبث المباشر. */
    fun playPlaylist(id: Long): Boolean {
        val alreadyActive = id == _activePlaylistId.value
        if (!alreadyActive && !selectPlaylist(id)) return false
        if (_playlist.value.isEmpty()) return false
        playAt(0)
        return true
    }

    /** مزامنة حالة القوائم من المخزن (+ تحميل مقاطع الجارية عند الطلب). */
    private fun refreshPlaylistState(loadTracks: Boolean = false) {
        _playlists.value = playlistStore.playlists()
        _activePlaylistId.value = playlistStore.activeId()
        _repeatMode.value = playlistStore.repeatMode()
        _shuffle.value = playlistStore.shuffle()
        if (loadTracks) loadActivePlaylistTracks()
    }

    // ================= تراجع/إعادة قائمة التشغيل والسكريبت (v0.39.0) =================
    // سجل لقطات لكل مجال على حِدة: القائمة (المسارات والفهرس) والسكريبت (الفقرات
    // بمعرفاتها) — كل تغيير بشري يدفع لقطة «ما قبله» فيعود التراجع إليها حرفيًا.
    // المونتاج يحتفظ بسجلّه الخاص (v0.34.0) فلا تداخُل، وCtrl+Z يتوجه بالسياق:
    // صفحة الموسيقى/الاستوديو تتراجع عن القائمة، وصفحة السكريبت عن السكريبت.

    /** لقطة القائمة الجارية: مساراتها وفهرس الجاري — خفيفة تُقارن فورًا. */
    private data class PlaylistSnapshot(val paths: List<String>, val currentIndex: Int)

    private val playlistHistory = SnapshotHistory<PlaylistSnapshot>()
    private val _playlistCanUndo = MutableStateFlow(false)
    val playlistCanUndo: StateFlow<Boolean> = _playlistCanUndo.asStateFlow()
    private val _playlistCanRedo = MutableStateFlow(false)
    val playlistCanRedo: StateFlow<Boolean> = _playlistCanRedo.asStateFlow()

    private fun playlistSnapshot() = PlaylistSnapshot(
        _playlist.value.map { it.file.path }, _currentIndex.value
    )

    private fun refreshPlaylistUndoStates() {
        _playlistCanUndo.value = playlistHistory.canUndo
        _playlistCanRedo.value = playlistHistory.canRedo
    }

    private fun pushPlaylistHistory() {
        playlistHistory.push(playlistSnapshot())
        refreshPlaylistUndoStates()
    }

    /** استعادة لقطة قائمة: القائمة تُعاد كما كانت والجاري يُشار إن بقى موجودًا. */
    private fun restorePlaylistSnapshot(s: PlaylistSnapshot) {
        val playingFile = _playlist.value.getOrNull(_currentIndex.value)?.file
        playlistStore.setPaths(_activePlaylistId.value, s.paths, s.currentIndex)
        loadActivePlaylistTracks()
        // الجاري المسموع يبقى مسموعًا: إن كان في القائمة المستعادة يُشار إليه
        val cur = _playlist.value.indexOfFirst { it.file.path == playingFile?.path }
        _currentIndex.value = if (cur >= 0) cur else _currentIndex.value.coerceIn(-1, _playlist.value.size - 1)
        if (playingFile != null && cur < 0 && player.isPlaying.value) {
            // الملف الذي كان يعمل اختفى من القائمة المستعادة — التشغيل يتوقف نظيفًا
            player.stop()
        }
        playlistStore.setIndex(_activePlaylistId.value, _currentIndex.value)
        refreshPlaylistUndoStates()
    }

    /** تراجع عن آخر تغيير في القائمة الجارية (صفحة الموسيقى/الاستوديو). */
    fun undoPlaylist() {
        val snap = playlistHistory.undo(playlistSnapshot()) ?: return
        restorePlaylistSnapshot(snap)
    }

    /** إعادة آخر تراجع في القائمة الجارية. */
    fun redoPlaylist() {
        val snap = playlistHistory.redo(playlistSnapshot()) ?: return
        restorePlaylistSnapshot(snap)
    }

    private val scriptHistory = SnapshotHistory<List<ScriptSection>>()
    private val _scriptCanUndo = MutableStateFlow(false)
    val scriptCanUndo: StateFlow<Boolean> = _scriptCanUndo.asStateFlow()
    private val _scriptCanRedo = MutableStateFlow(false)
    val scriptCanRedo: StateFlow<Boolean> = _scriptCanRedo.asStateFlow()

    private fun refreshScriptUndoStates() {
        _scriptCanUndo.value = scriptHistory.canUndo
        _scriptCanRedo.value = scriptHistory.canRedo
    }

    private fun pushScriptHistory() {
        scriptHistory.push(scriptStore.sections())
        refreshScriptUndoStates()
    }

    /** تراجع عن آخر تغيير في السكريبت — الفقرات تعود بمعرفاتها ونصّها كما كانت. */
    fun undoScript() {
        val snap = scriptHistory.undo(scriptStore.sections()) ?: return
        scriptStore.restoreSections(snap)
        afterScriptRestore()
    }

    /** إعادة آخر تراجع في السكريبت. */
    fun redoScript() {
        val snap = scriptHistory.redo(scriptStore.sections()) ?: return
        scriptStore.restoreSections(snap)
        afterScriptRestore()
    }

    private fun afterScriptRestore() {
        _script.value = scriptStore.sections()
        // الفقرة المحددة إن نجت من الاستعادة تبقى محددة، وإلا أول فقرة موجودة
        val sel = _selectedSectionId.value
        if (sel == null || _script.value.none { it.id == sel }) {
            _selectedSectionId.value = _script.value.firstOrNull()?.id
        }
        refreshScriptUndoStates()
        scheduleSessionSave()
    }

    /** نقل مسار داخل القائمة الجارية مع الحفاظ على تشغيل المسار الحالي. */
    fun moveTrack(index: Int, delta: Int) {
        val list = _playlist.value
        val moved = ScheduleMath.moved(list, index, delta)
        if (moved === list) return
        pushPlaylistHistory()
        val cur = _currentIndex.value
        val curFile = list.getOrNull(cur)?.file
        val newCur = moved.indexOfFirst { it.file == curFile }
        _playlist.value = moved
        _currentIndex.value = if (curFile != null && newCur >= 0) newCur else -1
        playlistStore.setPaths(_activePlaylistId.value, moved.map { it.file.path }, _currentIndex.value)
    }

    fun addFiles(files: List<File>) {
        if (files.isEmpty()) return
        val existing = _playlist.value.map { it.file }.toSet()
        val newTracks = files
            .filter { it.isFile && it.extension.lowercase() in RecordingsScanner.AUDIO_EXTENSIONS && it !in existing }
            .map { trackFor(it) }
        if (newTracks.isEmpty()) return
        pushPlaylistHistory()
        val wasIdle = _playlist.value.isEmpty() || _currentIndex.value < 0
        _playlist.value = _playlist.value + newTracks
        playlistStore.setPaths(_activePlaylistId.value, _playlist.value.map { it.file.path }, _currentIndex.value)
        if (wasIdle) playAt(_playlist.value.indexOf(newTracks.first()))
    }

    fun addFolder(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in RecordingsScanner.AUDIO_EXTENSIONS }
            ?.sortedBy { it.name } ?: return
        addFiles(files)
    }

    fun clearPlaylist() {
        if (_playlist.value.isNotEmpty()) pushPlaylistHistory()
        player.stop()
        player.awaitIdle(1500)
        _playlist.value = emptyList()
        _currentIndex.value = -1
        playlistStore.clear(_activePlaylistId.value)
    }

    fun playAt(index: Int) {
        val list = _playlist.value
        if (index < 0 || index >= list.size) return
        _currentIndex.value = index
        _playingFromLibrary.value = false
        playlistStore.setIndex(_activePlaylistId.value, index)
        consecutiveFailures = 0
        player.play(list[index].file, wantCrossfadeTo(list[index].file))
    }

    /** إزالة مقطع واحد من القائمة الجارية — إزالة الجاري يوقف التشغيل. */
    fun removeTrackAt(index: Int) {
        val list = _playlist.value
        if (index < 0 || index >= list.size) return
        pushPlaylistHistory()
        val wasCurrent = index == _currentIndex.value
        val curFile = list.getOrNull(_currentIndex.value)?.file
        val newList = list.filterIndexed { i, _ -> i != index }
        _playlist.value = newList
        val newCur = if (newList.isEmpty()) -1
        else newList.indexOfFirst { it.file == curFile }.takeIf { it >= 0 } ?: -1
        _currentIndex.value = newCur
        playlistStore.setPaths(_activePlaylistId.value, newList.map { it.file.path }, newCur)
        if (wasCurrent) {
            player.stop()
            player.awaitIdle(1500)
        }
    }

    /** بناء مقطع من ملف: العنوان من اسم الملف والتصنيف من ربط المكتبة إن وُجد. */
    private fun trackFor(f: File, title: String? = null): Track = Track(
        file = f,
        title = title ?: f.nameWithoutExtension,
        durationMs = durationOf(f),
        category = libraryStore.entries().firstOrNull { it.path == f.path }?.category
    )

    /**
     * تخطي الملفات التالفة آليًا (نظير ميزة نسخة الهاتف): عند فشل فتح مقطع
     * ننتقل للتالي — وحتى لا تدور الحلقة على قائمة كلها تالفة، يُتوقف بعد
     * فشل كل المسارات.
     */
    @Volatile private var consecutiveFailures = 0

    private fun skipCorrupt() {
        // أثناء استعادة الجلسة (v0.32.0): فشل فتح المقطع المحفوظ لا يشغّل سلسلة
        // تخطي التالف — المحرك يتوقف والجلسة تبقى مستعادة للقائمة والسكريبت
        if (restoringSession) {
            restoringSession = false
            consecutiveFailures = 0
            player.stop()
            return
        }
        val size = _playlist.value.size
        consecutiveFailures++
        if (size > 0 && consecutiveFailures < size) {
            next(auto = true)
        } else {
            player.stop()
            consecutiveFailures = 0
        }
    }

    fun togglePlay() {
        // v0.23.0: زر التشغيل يعمل دائمًا — بعد إعادة فتح التطبيق تكون القائمة
        // مستعادة بمؤشر جارٍ دون مقطع محمّل في المحرك، فالتبديل وحده لا يفعل شيئًا
        if (!player.hasTrack.value && _playlist.value.isNotEmpty()) {
            playAt(_currentIndex.value.coerceAtLeast(0))
            return
        }
        player.toggle()
    }

    fun next(auto: Boolean = false) {
        val list = _playlist.value
        if (list.isEmpty()) {
            if (auto) player.stop()
            return
        }
        val n = PlaylistMath.nextIndex(
            list.size, _currentIndex.value,
            _repeatMode.value, _shuffle.value, auto, shuffleRandom
        )
        if (n < 0) player.stop() else playAt(n)
    }

    fun previous() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val p = PlaylistMath.prevIndex(list.size, _currentIndex.value, _shuffle.value, shuffleRandom)
        if (p >= 0) playAt(p)
    }

    /** تشغيل فوري لمقطع من المكتبة على الهواء دون تعديل القائمة — الفشل يُعرض لا يُكتم. */
    fun playNow(entry: LibraryEntry) {
        val path = entry.path
        if (path.isNullOrBlank()) {
            player.fail("تعذّر البث: المقطع بلا مسار — أعد ربطه من صفحة الموسيقى")
            return
        }
        val f = File(path)
        if (!f.isFile) {
            player.fail("تعذّر البث: الملف غير موجود على القرص (${f.name}) — أعد ربطه من صفحة الموسيقى")
            return
        }
        if (f.extension.lowercase() !in PLAYABLE_EXTENSIONS) {
            player.fail("تعذّر البث: صيغة غير مدعومة (${f.extension}) — المدعوم: MP3 وWAV وOGG وFLAC")
            return
        }
        consecutiveFailures = 0
        _playingFromLibrary.value = true
        player.play(f, wantCrossfadeTo(f))
    }

    /** إدراج مقطع من المكتبة «التالي» في القائمة الجارية — بدون مقاطعة الجاري. */
    fun playNextFromLibrary(entry: LibraryEntry) {
        val f = File(entry.path ?: return)
        if (!f.isFile) return
        val existing = _playlist.value.map { it.file }.toSet()
        if (f in existing) return
        val track = Track(f, entry.title ?: f.nameWithoutExtension, durationOf(f), entry.category)
        val list = _playlist.value
        val insertAt = if (_currentIndex.value in list.indices) _currentIndex.value + 1 else list.size
        _playlist.value = list.toMutableList().apply { add(insertAt, track) }
        val newCur = if (_currentIndex.value >= insertAt) _currentIndex.value + 1 else _currentIndex.value
        _currentIndex.value = newCur
        playlistStore.setPaths(_activePlaylistId.value, _playlist.value.map { it.file.path }, newCur)
    }

    // ================= المكتبة الصوتية المصنفة =================
    // فهرس دائم بتصنيفات (السبع الافتراضية + المخصصة) + بحث + كشف المفقود وإعادة ربطه

    private var libraryStore = MusicLibraryStore(File(dataDir(), "library.json"))

    private val _libraryEntries = MutableStateFlow(libraryStore.entries())
    val libraryEntries: StateFlow<List<LibraryEntry>> = _libraryEntries.asStateFlow()

    private val _libraryCategories = MutableStateFlow(libraryStore.categories())
    val libraryCategories: StateFlow<List<String>> = _libraryCategories.asStateFlow()

    private val _libraryQuery = MutableStateFlow("")
    val libraryQuery: StateFlow<String> = _libraryQuery.asStateFlow()

    private val _missingLibraryIds = MutableStateFlow<Set<Long>>(emptySet())
    val missingLibraryIds: StateFlow<Set<Long>> = _missingLibraryIds.asStateFlow()

    private val _lastLibraryMessage = MutableStateFlow<String?>(null)
    val lastLibraryMessage: StateFlow<String?> = _lastLibraryMessage.asStateFlow()

    fun setLibraryQuery(q: String) {
        _libraryQuery.value = q
    }

    /** تصفية خالصة: بالبحث فقط — عرض التصنيف يتم في الواجهة. */
    fun visibleLibraryEntries(category: String?): List<LibraryEntry> =
        _libraryEntries.value.filter { e ->
            (category == null || e.category == category) &&
                    LibraryMath.matches(e.title, e.category, _libraryQuery.value)
        }

    fun importToLibrary(files: List<File>, category: String) {
        val r = libraryStore.addFiles(files, category)
        refreshLibraryState()
        _lastLibraryMessage.value = when {
            r.added.isEmpty() && r.skippedUnsupported > 0 ->
                "لا ملفات قابلة للإضافة — تجاوز ${r.skippedUnsupported} بصيغة غير مدعومة " +
                        "(المدعوم: MP3 وWAV وOGG وFLAC)"
            r.added.isEmpty() -> "لا ملفات جديدة للإضافة"
            r.skippedUnsupported > 0 ->
                "أُضيفت ${r.added.size} إلى «$category» · تجاوز ${r.skippedUnsupported} بصيغة غير مدعومة"
            else -> "أُضيفت ${r.added.size} إلى «$category»"
        }
    }

    fun addLibraryCategory(name: String): Boolean {
        val ok = libraryStore.addCategory(name)
        if (ok) refreshLibraryState()
        return ok
    }

    fun deleteLibraryCategory(name: String): Boolean {
        val ok = libraryStore.deleteCategory(name)
        if (ok) refreshLibraryState()
        return ok
    }

    fun removeLibraryEntry(id: Long) {
        if (libraryStore.remove(id)) refreshLibraryState()
    }

    /** نقل مقطع إلى تصنيف آخر — تعديل تصنيف مقطع بعد استيراده. */
    fun moveLibraryEntry(id: Long, category: String): Boolean {
        val ok = libraryStore.moveEntry(id, category)
        if (ok) refreshLibraryState()
        return ok
    }

    /** إعادة ربط ملف مفقود بمسار جديد (نفس ميزة «إعادة التوصيل» في نسخة الهاتف). */
    fun relinkLibraryEntry(id: Long, newFile: File): Boolean {
        val ok = libraryStore.relink(id, newFile)
        if (ok) {
            refreshLibraryState()
            _lastLibraryMessage.value = "أُعيد ربط الملف بنجاح"
        }
        return ok
    }

    /** إضافة عنصر مكتبة إلى القائمة الجارية (وفق ميزة «ربط» نسخة الهاتف). */
    fun addLibraryToPlaylist(entry: LibraryEntry) {
        val f = File(entry.path ?: return)
        if (!f.isFile) return
        val existing = _playlist.value.map { it.file }.toSet()
        if (f in existing) return
        pushPlaylistHistory()
        val wasIdle = _playlist.value.isEmpty() || _currentIndex.value < 0
        val track = Track(f, entry.title ?: f.nameWithoutExtension, durationOf(f), entry.category)
        _playlist.value = _playlist.value + track
        playlistStore.setPaths(_activePlaylistId.value, _playlist.value.map { it.file.path }, _currentIndex.value)
        if (wasIdle) playAt(_playlist.value.indexOf(track))
    }

    private val _categoryCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val categoryCounts: StateFlow<Map<String, Int>> = _categoryCounts.asStateFlow()

    private fun refreshLibraryState() {
        _libraryEntries.value = libraryStore.entries()
        _libraryCategories.value = libraryStore.categories()
        _categoryCounts.value = libraryStore.countByCategory()
        _missingLibraryIds.value = _libraryEntries.value
            .filter { it.path != null && !File(it.path).isFile }
            .mapNotNull { it.id }
            .toSet()
    }

    // ================= مكتبة الأصوات والموسيقى (v0.42.0) =================
    // خمس مجموعات مدمجة مولدة برمجيًا (CC0) + «مكتبتي الخاصة» المستوردة،
    // مع معاينة قبل الإضافة وبحث وتصفية بالتصنيف، والإضافة تمر بالمخزن
    // الرئيسي نفسه فتصل القوائم والمونتاج وجدار الجينل كلها.

    /** محرك المعاينة — صوت واحد في كل لحظة يشارك سماعات الموسيقى. */
    val soundPreview = SoundPreviewEngine(outputs = { player.outputsSnapshot() })

    /** ملفات المدمجة المستخرجة بمعرّفاتها — البطاقات تشغل منها. */
    private val _bundledSoundFiles = MutableStateFlow<Map<String, File>>(emptyMap())
    val bundledSoundFiles: StateFlow<Map<String, File>> = _bundledSoundFiles.asStateFlow()

    private var soundStore = SoundLibraryStore(File(dataDir(), "soundlib.json"))

    private val _customSounds = MutableStateFlow(soundStore.custom())
    val customSounds: StateFlow<List<CustomSound>> = _customSounds.asStateFlow()

    /** استعلام البحث في مكتبة الأصوات (العنوان والوصف والوسوم). */
    private val _soundQuery = MutableStateFlow("")
    val soundQuery: StateFlow<String> = _soundQuery.asStateFlow()

    /** المجموعة المرشحة (null = الكل). */
    private val _soundGroup = MutableStateFlow<String?>(null)
    val soundGroup: StateFlow<String?> = _soundGroup.asStateFlow()

    /** حوار التراخيص والاستخدام التجاري. */
    private val _licenseOpen = MutableStateFlow(false)
    val licenseOpen: StateFlow<Boolean> = _licenseOpen.asStateFlow()

    /** إضافة معلّقة: صوت اختاره المذيع وينتِ اختيار التصنيف — مدمج أو خاص. */
    data class PendingSoundAdd(val title: String, val file: File, val group: String, val fromCustom: Boolean)

    private val _pendingAdd = MutableStateFlow<PendingSoundAdd?>(null)
    val pendingAdd: StateFlow<PendingSoundAdd?> = _pendingAdd.asStateFlow()

    /** استخراج المدمجة وقراءة المخزن الخاص — بلا استثناءات كعاد الجدران. */
    private fun loadSounds() {
        try {
            _bundledSoundFiles.value = BundledSounds.ensureExtracted(File(dataDir(), "sounds"))
        } catch (_: Exception) {
            _bundledSoundFiles.value = emptyMap()
        }
        _customSounds.value = soundStore.custom()
    }

    fun setSoundQuery(q: String) { _soundQuery.value = q }
    fun setSoundGroupFilter(g: String?) { _soundGroup.value = g }

    fun openLicenseDialog() { _licenseOpen.value = true }
    fun closeLicenseDialog() { _licenseOpen.value = false }

    /** معاينة صوت مدمج بمعرّفه — إن غاب ملفه فالخطأ يظهر على البطاقة. */
    fun togglePreviewBundled(soundId: String) {
        val f = _bundledSoundFiles.value[soundId]
        if (f == null) {
            soundPreview.stop()
            return
        }
        soundPreview.toggle(f)
    }

    /** معاينة صوت من مكتبتي الخاصة. */
    fun togglePreviewCustom(id: Long) {
        val c = _customSounds.value.firstOrNull { it.id == id } ?: return
        val f = File(c.path)
        if (!f.isFile) {
            soundPreview.stop()
            return
        }
        soundPreview.toggle(f)
    }

    /** فتح حوار الإضافة لصوت مدمج. */
    fun openBundledAddDialog(soundId: String) {
        val s = BundledSounds.ALL.firstOrNull { it.id == soundId } ?: return
        val f = _bundledSoundFiles.value[soundId] ?: return
        _pendingAdd.value = PendingSoundAdd(s.title, f, s.group, fromCustom = false)
    }

    /** فتح حوار الإضافة لصوت خاص. */
    fun openCustomAddDialog(id: Long) {
        val c = _customSounds.value.firstOrNull { it.id == id } ?: return
        val f = File(c.path)
        if (!f.isFile) return
        _pendingAdd.value = PendingSoundAdd(c.title, f, SoundGroups.CUSTOM, fromCustom = true)
    }

    fun dismissAddDialog() { _pendingAdd.value = null }

    /**
     * تأكيد الإضافة إلى المكتبة الرئيسية — بالمخزن نفسه فالصوت يصل
     * القوائم والمونتاج والجدولة وجدار الجينل كلها، ولا يدخل ملف فاشل الفك أبدًا.
     */
    fun confirmAddSound(category: String) {
        val p = _pendingAdd.value ?: return
        _pendingAdd.value = null
        importToLibrary(listOf(p.file), category)
    }

    /** استيراد مكتبة خاصة: ملفات مفردة أو مجلد ممسوح — يفهرس ويُبقي الأصل كما هو. */
    fun importCustomLibrary(files: List<File>) {
        val r = soundStore.addFiles(files)
        _customSounds.value = soundStore.custom()
        _lastLibraryMessage.value = when {
            r.added.isEmpty() && r.skippedUnsupported > 0 ->
                "لا ملفات صوتية قابلة للاستيراد — تجاوز ${r.skippedUnsupported} بصيغة غير مدعومة"
            r.added.isEmpty() -> "لا ملفات جديدة للاستيراد — الموجودة مسبقًا مُتجاهَلة"
            r.skippedUnsupported > 0 ->
                "استُوردت ${r.added.size} إلى «مكتبتي الخاصة» · تجاوز ${r.skippedUnsupported} بصيغة غير مدعومة"
            else -> "استُوردت ${r.added.size} إلى «مكتبتي الخاصة»"
        }
    }

    /** إزالة صوت من مكتبتي الخاصة (الملف الأصلي لا يُمسّ). */
    fun removeCustomSound(id: Long) {
        if (soundStore.remove(id)) {
            _customSounds.value = soundStore.custom()
            _lastLibraryMessage.value = "أُزيل من «مكتبتي الخاصة» — الملف الأصلي كما هو على قرصك"
        }
    }

    // ================= استيراد من اليوتيوب (v0.25.0) =================
    // الأداة مدمجة لكل منصة، والتحويل M4A→MP3 محلي نقي — المقاطع تدخل المكتبة بأضمن صيغة.

    private val youtubeService = YouTubeImportService(
        YtDlpLocator(dataDir()),
        File(dataDir(), "youtube").apply { mkdirs() }
    )

    val youtubeImportState get() = youtubeService.state
    val youtubeResults get() = youtubeService.results

    /** جلب بيانات رابط أو تنفيذ بحث — في خيط عمل، والنتيجة تنبض في الحوار. */
    fun youtubeSearch(input: String) {
        val q = input.trim()
        if (q.isEmpty()) return
        runScope.launch(Dispatchers.IO) {
            try {
                youtubeService.resolve(q)
            } catch (t: Throwable) {
                // الخدمة تلتقط أخطاءها — هذا حاجز أمان إضافي
                youtubeService.fail(t.message ?: "تعذّر جلب المعلومات")
            }
        }
    }

    /** تحميل مقطع وتحويله وإضافته إلى المكتبة في تصنيف — في خيط عمل. */
    fun youtubeImport(video: YouTubeJson.VideoInfo, category: String) {
        runScope.launch(Dispatchers.IO) {
            try {
                youtubeService.import(video) { file, v ->
                    // الإضافة للمكتبة تُمارَس في خيط الواجهة الافتراضي كبقية عمليات المكتبة
                    runScope.launch(Dispatchers.Default) { onYouTubeImported(file, v, category) }
                }
            } catch (t: Throwable) {
                youtubeService.fail(t.message ?: "تعذّر الاستيراد")
            }
        }
    }

    private fun onYouTubeImported(file: File, video: YouTubeJson.VideoInfo, category: String) {
        val r = libraryStore.addFiles(listOf(file), category)
        refreshLibraryState()
        _lastLibraryMessage.value = when {
            r.added.isNotEmpty() -> "أُضيف «${video.title.take(60)}» إلى «$category» — جاهز للبث"
            else -> "المقطع «${video.title.take(60)}» موجود في المكتبة مسبقًا"
        }
    }

    fun youtubeCancel() = youtubeService.cancel()
    fun youtubeReset() = youtubeService.reset()
    fun setYouTubeCookies(file: File?) { youtubeService.cookiesFile = file }
    fun youtubeCookiesFile(): File? = youtubeService.cookiesFile

    // ================= التسجيلات + توقيع المُسجِّل =================

    fun refreshRecordings() {
        _recordings.value = RecordingsScanner.scan(_recordingsDir.value)
    }

    fun setRecordingsDir(dir: File) {
        dir.mkdirs()
        _recordingsDir.value = dir
        refreshRecordings()
    }

    fun newRecordingTarget(): File =
        RecordingsScanner.newRecordingFile(_recordingsDir.value, prefix = "بث-مباشر")

    fun renameRecording(item: RecordingItem, newName: String) {
        if (!canManage) return
        val clean = newName.trim()
        if (clean.isEmpty() || clean == item.displayName) return
        val wasCurrent = player.currentFile.value == item.file
        if (wasCurrent) {
            player.stop()
            player.awaitIdle(2000)
        }
        val target = File(item.file.parentFile, "$clean.${item.file.extension}")
        if (item.file.renameTo(target)) {
            RecordingsScanner.moveMeta(item.file, target)
            refreshRecordings()
            _playlist.value = _playlist.value.map {
                if (it.file == item.file) it.copy(file = target, title = clean) else it
            }
            playlistStore.setPaths(_activePlaylistId.value, _playlist.value.map { it.file.path }, _currentIndex.value)
        }
    }

    fun deleteRecording(item: RecordingItem) {
        if (!canManage) return
        val wasCurrent = player.currentFile.value == item.file
        if (wasCurrent) {
            player.stop()
            player.awaitIdle(2000)
        }
        RecordingsScanner.deleteMeta(item.file)
        thread(name = "delete-recording", isDaemon = true) {
            var deleted = item.file.delete()
            var tries = 0
            while (!deleted && tries < 10) {
                Thread.sleep(200)
                deleted = item.file.delete()
                tries++
            }
            refreshRecordings()
        }
    }

    /** بوابة توقيع المُسجِّل: لحظة اكتمال ملف التسجيل تُختم باسم المستخدم ودوره. */
    private fun signRecording(file: File, durationMs: Long) {
        val u = _currentUser.value
        val meta = RecordingMeta(
            recorderName = u?.name ?: "مستخدم",
            recorderRole = (u?.role ?: Role.STUDENT).labelAr,
            startedAtEpochMs = System.currentTimeMillis() - durationMs,
            durationMs = durationMs
        )
        meta.save(RecordingMeta.sidecarOf(file))
        refreshRecordings()
    }

    // ================= صفحة المونتاج (v0.33.0 «أضف صفحة المونتاج») =================
    // مشروع تحرير صوتي كامل دون اتصال: مقاطع مرتّبة تُقص وتُوضع لها مستويات
    // وتلاشين وفواصل، ثم تصيير في ملف WAV واحد 44.1k ستيريو — التصيير في خيط
    // عمل مع تقدم حي وإلغاء، والمشروع محفوظ آليًا في montage.json.
    //
    // v0.34.0 «الترقية الاحترافية» (البنود 1–15): سجل تراجع/إعادة وتحديد ورأس
    // تشغيل محفوظ مع الجلسة، كروس فيد موحّد بين المتجاورة، مغلّفات ويڤ فورم
    // وVU ومناطق التشبع، تقسيم عند الرأس وتطبيع وحلقة A/B ومعاينة من أي نقطة،
    // وحفظ/فتح ملف مشروع.

    private var montageStore = MontageStore(File(dataDir(), "montage.json"))
    private val _montageProject = MutableStateFlow(montageStore.project())
    val montageProject: StateFlow<MontageProject> = _montageProject.asStateFlow()

    /** تقدم التصيير الجاري: null = خامل، وإلا 0..1 — الواجهة تشترط خمولًا قبل إطلاق جديد. */
    private val _montageRenderProgress = MutableStateFlow<Float?>(null)
    val montageRenderProgress: StateFlow<Float?> = _montageRenderProgress.asStateFlow()

    private var montageRenderJob: Job? = null
    @Volatile private var montageRenderCancelled = false

    /** آخر ملف صُدِّر في هذه الجلسة — لزر «أضف المصدَّر إلى قائمة البث». */
    private val _lastMontageExport = MutableStateFlow<File?>(null)
    val lastMontageExport: StateFlow<File?> = _lastMontageExport.asStateFlow()

    // ---- v0.37.0: صيغة التصدير وجودة MP3 ----

    /** صيغة تصدير المونتاج: WAV للبث بلا فقد، أو MP3 مضغوط للمشاركة (المعاينة WAV دائمًا). */
    private val _montageExportFormat = MutableStateFlow(MontageExportFormat.WAV)
    val montageExportFormat: StateFlow<MontageExportFormat> = _montageExportFormat.asStateFlow()

    /** جودة MP3 (CBR) — من الشبكة المسموحة فقط. */
    private val _montageExportBitrate = MutableStateFlow(MontageRender.MP3_DEFAULT_KBPS)
    val montageExportBitrate: StateFlow<Int> = _montageExportBitrate.asStateFlow()

    fun setMontageExportFormat(f: MontageExportFormat) {
        _montageExportFormat.value = f
    }

    /** جودة MP3: أقرب قيمة مسموحة تُثبّت (128/192/256/320). */
    fun setMontageExportBitrate(kbps: Int) {
        _montageExportBitrate.value = MontageRender.MP3_BITRATES
            .minByOrNull { kotlin.math.abs(it - kbps) } ?: MontageRender.MP3_DEFAULT_KBPS
    }

    // ---- v0.34.0: حالة الاحتراف ----

    /** سجل التراجع/الإعادة — لقطة قبل كل تغيير (وأثناء السحب لقطة واحدة). */
    private val montageHistory = MontageHistory()

    private val _montageCanUndo = MutableStateFlow(false)
    val montageCanUndo: StateFlow<Boolean> = _montageCanUndo.asStateFlow()
    private val _montageCanRedo = MutableStateFlow(false)
    val montageCanRedo: StateFlow<Boolean> = _montageCanRedo.asStateFlow()

    /** المقطع المحدد — هدف الحذف والتطبيع والاختصارات. */
    private val _montageSelectedClipId = MutableStateFlow<String?>(null)
    val montageSelectedClipId: StateFlow<String?> = _montageSelectedClipId.asStateFlow()

    /** مقطع الموسيقى المحدد في المسار الثاني (v0.35.0) — هدف التحرير والحذف. */
    private val _montageSelectedMusicId = MutableStateFlow<String?>(null)
    val montageSelectedMusicId: StateFlow<String?> = _montageSelectedMusicId.asStateFlow()

    /** رأس التشغيل على الخط الزمني — يُحفظ في المشروع ويُستعاد مع الجلسة. */
    private val _montagePlayheadMs = MutableStateFlow(_montageProject.value.playheadMs)
    val montagePlayheadMs: StateFlow<Long> = _montagePlayheadMs.asStateFlow()

    /** حلقة A/B: بداية ونهاية بالمللي ثانية — null = غير مضبوطة. */
    private val _montageLoopStart = MutableStateFlow<Long?>(null)
    val montageLoopStart: StateFlow<Long?> = _montageLoopStart.asStateFlow()
    private val _montageLoopEnd = MutableStateFlow<Long?>(null)
    val montageLoopEnd: StateFlow<Long?> = _montageLoopEnd.asStateFlow()
    private val _montageLoopEnabled = MutableStateFlow(false)
    val montageLoopEnabled: StateFlow<Boolean> = _montageLoopEnabled.asStateFlow()

    // ---- v0.36.0: مسطرة العمليات والأدوات والمسارات المتعددة ----

    /** المنطقة المحددة على المسطرة [بداية، نهاية) — null = لا تحديد (حالة عرض لا تُحفظ بتاريخ التحرير). */
    private val _montageSelection = MutableStateFlow<Pair<Long, Long>?>(null)
    val montageSelection: StateFlow<Pair<Long, Long>?> = _montageSelection.asStateFlow()

    /** الأداة النشطة: تحديد/حركة أو مقصّ تقسيم بالنقر. */
    private val _montageTool = MutableStateFlow(MontageTool.SELECT)
    val montageTool: StateFlow<MontageTool> = _montageTool.asStateFlow()

    /** التقاط السحب على العلامات وحواف المقاطع وشبكة نصف الثانية. */
    private val _montageSnapEnabled = MutableStateFlow(true)
    val montageSnapEnabled: StateFlow<Boolean> = _montageSnapEnabled.asStateFlow()

    // ---- v0.38.0: الحافظة وتحويلات المقطع (عكس/سرعة/نبرة/دمج/تكرار) ----

    /** خانة الحافظة الموحّدة — القص والنسخ يملآنانها واللصق يقرؤها. */
    private val _montageClipboard = MutableStateFlow<MontageLogic.MontageClipboardEntry?>(null)
    val montageClipboard: StateFlow<MontageLogic.MontageClipboardEntry?> = _montageClipboard.asStateFlow()

    /** هل جرى تحويل ارتداد الآن (عكس/سرعة/نبرة/دمج)؟ — يقفل أدوات التحويل. */
    private val _montageTransformBusy = MutableStateFlow(false)
    val montageTransformBusy: StateFlow<Boolean> = _montageTransformBusy.asStateFlow()

    // ---- v0.40.0: مكتبة المؤثرات الصوتية ----

    /** نبضة طلب فتح مكتبة المؤثرات من الاختصار E — الواجهة تجمعها فتفتح الحوار. */
    private val _montageEffectsOpenTick = MutableStateFlow(0)
    val montageEffectsOpenTick: StateFlow<Int> = _montageEffectsOpenTick.asStateFlow()

    /** مخزن ملفات الارتداد (عكس/سرعة/نبرة/دمج) — أسماء ببصمة SHA-1 فتُعاد بلا حساب. */
    private val montageBounceDir: File
        get() = File(File(dataDir(), "montage"), "bounce")

    /** مغلّفات المقاطع (ويڤ فورم/VU/تشبع) بحسب المقطع — تُحسب في خيط عمل. */
    private val _montageEnvelopes = MutableStateFlow<Map<String, MontageEnvelope>>(emptyMap())
    val montageEnvelopes: StateFlow<Map<String, MontageEnvelope>> = _montageEnvelopes.asStateFlow()
    private var envelopeStamps: Map<String, String> = emptyMap()
    private var envelopeJob: Job? = null

    /** عند الإقلاع: مغلّفات المشروع المستعاد من الجلسة تُبنى فورًا فالويڤ فورم لا يولد فارغًا. */
    init { refreshMontageStates() }

    /** ختم المشروع الذي صُيِّرت معاينته الحالية — تُعاد استعمالها إن لم يتغير المشروع. */
    @Volatile private var montagePreviewStamp: String? = null
    private val montagePreviewFile: File
        get() = File(File(dataDir(), "montage"), "preview.wav")

    private fun persistMontage() {
        runCatching { montageStore.save(_montageProject.value) }
    }

    /**
     * بوابة كل تغيير في المشروع: لقطة تراجع، حفظ ذرّي، ثم تحديث حالات
     * الاحتراف (التحديد الصالح/المغلّفات) — كل التعديلات تمرّ من هنا فلا
     * مسار يتسلل بلا تاريخ ولا بلا حفظ.
     */
    private fun applyMontage(after: MontageProject, record: Boolean = true) {
        val before = _montageProject.value
        if (before == after) return
        if (record) montageHistory.record(before)
        _montageProject.value = after
        persistMontage()
        refreshMontageStates()
    }

    /** لقطة يدوية عند بدء سحبٍ متعدد التعديلات (قصّ بالمقابض) — تغيير واحد تاريخيًا. */
    fun pushMontageHistory() {
        montageHistory.pushCurrent(_montageProject.value)
        refreshMontageStates()
    }

    private fun refreshMontageStates() {
        _montageCanUndo.value = montageHistory.canUndo
        _montageCanRedo.value = montageHistory.canRedo
        val p = _montageProject.value
        val ids = p.clips.map { it.id }.toSet()
        if (_montageSelectedClipId.value !in ids) {
            _montageSelectedClipId.value = p.clips.firstOrNull()?.id
        }
        val musicIds = p.musicClips.map { it.id }.toSet()
        // التحديد وحيد: بلا نقر على مقطع مسار يبقى بلا تحديد مسارات — أدوات
        // المقطع تستهدف ما نقره المستخدم فعلًا لا أول مقطع مسار بالقائمة
        if (_montageSelectedMusicId.value !in musicIds) {
            _montageSelectedMusicId.value = null
        }
        requestMontageEnvelopes()
    }

    /** ساعة mm:ss لرسائل المونتاج. */
    private fun montageClock(ms: Long): String =
        "%02d:%02d".format(ms / 60000L, (ms / 1000L) % 60L)

    /**
     * إضافة ملفات إلى نهاية المشروع: القابلة للفك وغير المكررة فقط — المدد
     * تُحسب في خيط عمل ثم تُحبس نوافذ القص ويُحفظ المشروع (الاستكمال بلا
     * لقطة تراجع فالحل نفسه ليس تعديلًا بشريًا).
     */
    fun addMontageFiles(files: List<File>) {
        val existing = _montageProject.value.clips.map { it.path }.toSet()
        val fresh = files.filter {
            it.isFile && it.extension.lowercase() in RecordingsScanner.AUDIO_EXTENSIONS &&
                    it.absolutePath !in existing
        }
        if (fresh.isEmpty()) return
        val now = _montageProject.value
        val pending = fresh.map { f ->
            MontageClip(
                id = MontageLogic.newClipId(),
                path = f.absolutePath,
                title = f.nameWithoutExtension
            )
        }
        applyMontage(now.copy(clips = (now.clips + pending).take(MontageLogic.MAX_CLIPS)))
        runScope.launch(Dispatchers.IO) {
            val resolved = fresh.associate { it.absolutePath to durationOf(it) }
            val clips = _montageProject.value.clips.map { c ->
                val d = resolved[c.path] ?: return@map c
                val (s, e) = MontageLogic.clampWindow(c.startMs, c.endMs, d)
                c.copy(durationMs = d, startMs = s, endMs = e)
            }
            applyMontage(_montageProject.value.copy(clips = clips), record = false)
        }
    }

    /** تحرير مقطع (قص/مستوى/تلاشٍ) — الحبوس تُطبق هنا والمشروع يُحفظ. */
    fun updateMontageClip(clipId: String, transform: (MontageClip) -> MontageClip, record: Boolean = true) {
        val clips = _montageProject.value.clips.map {
            if (it.id == clipId) {
                val t = transform(it)
                val (s, e) = MontageLogic.clampWindow(t.startMs, t.endMs, t.durationMs)
                t.copy(
                    startMs = s, endMs = e,
                    volumePercent = MontageLogic.clampVolume(t.volumePercent),
                    fadeInMs = MontageLogic.clampFade(t.fadeInMs),
                    fadeOutMs = MontageLogic.clampFade(t.fadeOutMs)
                )
            } else it
        }
        applyMontage(_montageProject.value.copy(clips = clips), record)
    }

    /** ترتيب: نقل مقطع خطوة لأعلى (+1) أو لأسفل (−1) — والسحب على الخط الزمني يستعمله تِباعًا. */
    fun moveMontageClip(clipId: String, delta: Int) {
        val clips = _montageProject.value.clips.toMutableList()
        val i = clips.indexOfFirst { it.id == clipId }
        if (i < 0) return
        val j = i + delta
        if (j < 0 || j >= clips.size) return
        val tmp = clips[i]; clips[i] = clips[j]; clips[j] = tmp
        applyMontage(_montageProject.value.copy(clips = clips))
    }

    fun removeMontageClip(clipId: String) {
        applyMontage(
            _montageProject.value.copy(
                clips = _montageProject.value.clips.filterNot { it.id == clipId }
            )
        )
    }

    fun clearMontage() {
        applyMontage(_montageProject.value.copy(clips = emptyList()))
    }

    fun setMontageGap(ms: Long) {
        applyMontage(_montageProject.value.copy(gapMs = MontageLogic.clampGap(ms)))
    }

    /** كروس فيد موحّد بين المتجاورة (v0.34.0 — البند 5) — أكبر من صفر يُغني عن الفاصل. */
    fun setMontageCrossfade(ms: Long) {
        val v = MontageLogic.clampCrossfade(ms)
        applyMontage(_montageProject.value.copy(crossfadeMs = v))
        if (v > 0L) {
            _lastLibraryMessage.value =
                "الكروس فيد ${v}ms بين كل مقطعين متجاورين — يُغني عن الفاصل عند الحدود"
        }
    }

    fun setMontageTitle(name: String) {
        applyMontage(_montageProject.value.copy(title = name.trim().take(80)))
    }

    // ---- v0.34.0: التراجع/الإعادة (البند 3) والتحديد والرأس ----

    fun undoMontage() {
        val prev = montageHistory.undo(_montageProject.value)
        if (prev == null) {
            _lastLibraryMessage.value = "لا يوجد ما يُتراجع عنه"
            return
        }
        _montageProject.value = prev
        persistMontage()
        refreshMontageStates()
        _lastLibraryMessage.value = "تم التراجع — Ctrl+Y للإعادة"
    }

    fun redoMontage() {
        val next = montageHistory.redo(_montageProject.value)
        if (next == null) {
            _lastLibraryMessage.value = "لا يوجد ما يُعاد"
            return
        }
        _montageProject.value = next
        persistMontage()
        refreshMontageStates()
        _lastLibraryMessage.value = "تمت الإعادة"
    }

    fun selectMontageClip(clipId: String?) {
        // حصرية التحديد بين التعليق والمسارات الحرة — نقر خطٍّ يستهدفه وحده
        _montageSelectedClipId.value = clipId
        if (clipId != null) _montageSelectedMusicId.value = null
    }

    /**
     * ضبط رأس التشغيل: التنقل اليدوي يُحفظ في المشروع (استعادة الجلسة)،
     * ونبضة التشغيل الحيّة لا تُكتب كل مرة كي لا يُغرق القرص — تُثبّت عند
     * الإيقاف وعند مغادرة الصفحة.
     */
    fun setMontagePlayhead(ms: Long, persist: Boolean = true) {
        val p = _montageProject.value
        val total = MontageLogic.totalDurationMs(p.clips, p.gapMs, p.crossfadeMs)
        val v = ms.coerceIn(0L, total)
        _montagePlayheadMs.value = v
        if (persist) {
            _montageProject.value = p.copy(playheadMs = v)
            persistMontage()
        }
    }

    // ---- v0.34.0: التقسيم (البند 2) والحذف الممتد والتطبيع (البند 7) ----

    /** تقسيم المقطع الحاوي للرأس عند الرأس (مفتاح S) — الرأس يبقى عند نقطة الفصل. */
    fun splitMontageAtPlayhead() {
        val p = _montageProject.value
        val head = _montagePlayheadMs.value
        val starts = MontageLogic.clipStartMs(p.clips, p.gapMs, p.crossfadeMs)
        val idx = p.clips.indices.firstOrNull { i ->
            val s = starts[i]
            head > s && head < s + MontageLogic.clipDurationMs(p.clips[i])
        }
        if (idx == null) {
            _lastLibraryMessage.value = "ضع الرأس داخل مقطع أولًا كي تُقسّمه"
            return
        }
        val clip = p.clips[idx]
        val pair = MontageLogic.splitClipAt(clip, head - starts[idx])
        if (pair == null) {
            _lastLibraryMessage.value = "التقسيم عند حافة المقطع لا يُنتج مقطعين"
            return
        }
        val clips = p.clips.toMutableList()
        clips.removeAt(idx)
        clips.add(idx, pair.second)
        clips.add(idx, pair.first)
        applyMontage(p.copy(clips = clips))
        _montageSelectedClipId.value = pair.second.id
        _lastLibraryMessage.value = "انقسم «${clip.title}» إلى مقطعين عند الرأس"
    }

    /** حذف المقطع المحدد — الخط الزمني التسلسلي يُغلق مكانه تلقائيًا (حذف ممتد). */
    fun deleteSelectedMontageClip() {
        val id = _montageSelectedClipId.value
        if (id == null) {
            _lastLibraryMessage.value = "حدّد مقطعًا أولًا (نقرًا عليه)"
            return
        }
        val title = _montageProject.value.clips.firstOrNull { it.id == id }?.title ?: return
        removeMontageClip(id)
        _lastLibraryMessage.value = "حُذف «$title» وأُغلق مكانه"
    }

    /**
     * تطبيع المقطع (البند 7): الذروة الخام تُقاس من المصدر في خيط عمل،
     * والمستوى يُضبط ليلامس هدف التطبيع — محبوسًا 1..200٪.
     */
    fun normalizeMontageClip(clipId: String) {
        val clip = _montageProject.value.clips.firstOrNull { it.id == clipId } ?: return
        if (!File(clip.path).isFile) {
            _lastLibraryMessage.value = "ملف المقطع مفقود فلا تطبيع ممكن"
            return
        }
        runScope.launch(Dispatchers.IO) {
            val raw = MontageRender.computeRawPeak(clip)
            val newVol = MontageLogic.normalizedVolumePercent(raw)
            val old = clip.volumePercent
            updateMontageClip(clipId, { it.copy(volumePercent = newVol) })
            _lastLibraryMessage.value =
                if (raw > 0) "تطبيع «${clip.title}»: الذروة الخام $raw والمستوى $old٪ ← $newVol٪"
                else "تطبيع «${clip.title}»: الذروة صفر — أُبقي المستوى كما هو"
        }
    }

    fun normalizeSelectedMontageClip() {
        val id = _montageSelectedClipId.value
        if (id == null) {
            _lastLibraryMessage.value = "حدّد مقطعًا أولًا (نقرًا عليه)"
            return
        }
        normalizeMontageClip(id)
    }

    // ---- v0.34.0: مغلّفات العرض (ويڤ فورم + VU + تشبع — البندان 1 و8) ----

    /**
     * طلب إعادة حساب المغلّفات: فقط المقاطع التي تغيّر ختمها (النافذة أو
     * المستوى أو التلاشين) تُعاد — والبقية من الكاش فالسحب الحيّ رشيق.
     */
    private fun requestMontageEnvelopes() {
        val p = _montageProject.value
        val wanted = p.clips.associate { c ->
            c.id to "${c.path}|${c.startMs}|${c.endMs}|${c.durationMs}|${c.volumePercent}|${c.fadeInMs}|${c.fadeOutMs}"
        } + p.musicClips.associate { m ->
            m.id to "${m.path}|${m.startInFileMs}|${m.endInFileMs}|${m.durationMs}|${m.volumePercent}|${m.fadeInMs}|${m.fadeOutMs}"
        }
        val missing = wanted.filter { (id, stamp) -> envelopeStamps[id] != stamp }
        envelopeStamps = envelopeStamps.filterKeys { it in wanted }
        if (missing.isEmpty()) return
        envelopeJob?.cancel()
        envelopeJob = runScope.launch(Dispatchers.IO) {
            for ((id, stamp) in missing) {
                if (!isActive) break
                val clip = _montageProject.value.clips.firstOrNull { it.id == id }
                    ?: _montageProject.value.musicClips.firstOrNull { it.id == id }
                    ?: continue
                val src = if (clip is MontageMusicClip) MontageRender.musicAsSourceClip(clip) else clip as MontageClip
                val env = MontageRender.computeEnvelope(src, montageEnvelopeBuckets(src))
                envelopeStamps = envelopeStamps + (id to stamp)
                _montageEnvelopes.value = _montageEnvelopes.value + (id to env)
            }
        }
    }

    /** نقاط دلاء المغلّف بحسب المدة — كثافة عرض ثابتة بصريًا على كل الأطوال. */
    private fun montageEnvelopeBuckets(clip: MontageClip): Int {
        val ms = MontageLogic.clipDurationMs(clip)
        return when {
            ms <= 0L -> 16
            ms <= 5_000L -> 80
            ms <= 30_000L -> 200
            else -> 400
        }
    }

    // ---- v0.34.0: المعاينة من أي نقطة (البند 9) وحلقة A/B ----

    /** ختم محتوى المشروع — المعاينة تُعاد فقط عند تغيّره فعليًا. */
    private fun montageStamp(p: MontageProject): String =
        p.clips.joinToString("|") {
            "${it.id}:${it.path}:${it.startMs}-${it.endMs}:${it.volumePercent}:${it.fadeInMs}-${it.fadeOutMs}"
        } + "#" + p.musicClips.joinToString("|") { m ->
            "${m.id}@${m.path}:${m.startMs}:${m.startInFileMs}-${m.endInFileMs}:${m.volumePercent}:${m.fadeInMs}-${m.fadeOutMs}:${m.trackId}"
        } + "#duck=${p.duckingEnabled}:${p.duckDb}" + "#${p.gapMs}#${p.crossfadeMs}" +
            "#trks=" + p.tracks.joinToString(",") { t -> "${t.id}:${t.volumePercent}:${t.muted}:${t.solo}" }

    /** هل للمشروع ما يُصيَّر؟ (مقاطع تعليق أو موسيقى مرافقة موجودة على القرص) */
    private fun montageHasSource(p: MontageProject): Boolean =
        p.clips.any { File(it.path).isFile } || p.musicClips.any { File(it.path).isFile }

    /**
     * تشغيل/إيقاف المعاينة من موضع الرأس: إن كانت معاينة المشروع الحالي جاهزة
     * فُتحت موقوفة عند الرأس مباشرة، وإلا صُيِّرت أولًا ثم فُتحت عند الرأس —
     * بأي الحالتين أول ضغطة تكمل من الرأس لا تعيد من الصفر.
     */
    fun toggleMontagePreview() {
        val p = _montageProject.value
        if (!montageHasSource(p)) {
            _lastLibraryMessage.value = "أضف مقاطع موجودة على القرص أولًا"
            return
        }
        // إن كانت المعاينة تعمل بالفعل — إيقاف/تشغيل فقط
        if (player.currentFile.value == montagePreviewFile && player.hasTrack.value) {
            player.toggle()
            if (!player.isPlaying.value) setMontagePlayhead(player.positionMs.value)
            return
        }
        val stamp = montageStamp(p)
        if (montagePreviewStamp == stamp && montagePreviewFile.isFile &&
            player.preparePaused(montagePreviewFile, _montagePlayheadMs.value)
        ) {
            player.toggle()
            _lastLibraryMessage.value =
                "معاينة من ${montageClock(_montagePlayheadMs.value)} — مسافة للإيقاف"
        } else {
            renderMontage(preview = true, thenPlayFromPlayhead = true)
        }
    }

    /** إيقاف المعاينة نهائيًا — يُثبّت الرأس حيث وصل (يُستعاد مع الجلسة). */
    fun stopMontagePreview() {
        if (player.currentFile.value == montagePreviewFile && player.hasTrack.value) {
            val pos = player.positionMs.value
            player.stop()
            setMontagePlayhead(pos)
        }
    }

    fun setMontageLoopStartAtPlayhead() {
        _montageLoopStart.value = _montagePlayheadMs.value
        _lastLibraryMessage.value = "بداية الحلقة عند ${montageClock(_montageLoopStart.value ?: 0L)}"
    }

    fun setMontageLoopEndAtPlayhead() {
        _montageLoopEnd.value = _montagePlayheadMs.value
        _lastLibraryMessage.value = "نهاية الحلقة عند ${montageClock(_montageLoopEnd.value ?: 0L)}"
    }

    fun toggleMontageLoop() {
        val s = _montageLoopStart.value
        val e = _montageLoopEnd.value
        if (s == null || e == null || e <= s) {
            _lastLibraryMessage.value = "اضبط A ثم B بنقطتين صالحتين كي تعمل الحلقة"
            return
        }
        _montageLoopEnabled.value = !_montageLoopEnabled.value
        _lastLibraryMessage.value =
            if (_montageLoopEnabled.value) "الحلقة تعمل: ${montageClock(s)} ← ${montageClock(e)}"
            else "أُوقفت الحلقة"
    }

    fun clearMontageLoop() {
        _montageLoopEnabled.value = false
        _montageLoopStart.value = null
        _montageLoopEnd.value = null
        _lastLibraryMessage.value = "مُسحت الحلقة"
    }

    /**
     * نبضة الحلقة تُستدعى دوريًا من صفحة المونتاج أثناء التشغيل — قرار نقّي
     * على المحرك: تجاوز النهاية يعود للبداية بسعيٍ محسوب.
     */
    fun montageLoopTick() {
        if (!_montageLoopEnabled.value) return
        val s = _montageLoopStart.value ?: return
        val e = _montageLoopEnd.value ?: return
        if (player.currentFile.value != montagePreviewFile || !player.isPlaying.value) return
        val target = MontageLogic.loopSeekMs(player.positionMs.value, s, e) ?: return
        player.seek(target)
        _montagePlayheadMs.value = target
    }

    // ---- v0.34.0: حفظ/فتح ملف المشروع (البند 11) ----

    /** حفظ نسخة المشروع في ملف يختاره المذيع — JSON غير إتلافي قابل لإعادة الفتح. */
    fun exportMontageProject(file: File) {
        if (Json.save(file, _montageProject.value)) {
            _lastLibraryMessage.value = "حُفظ المشروع في «${file.name}»"
        } else {
            _lastLibraryMessage.value = "تعذّر حفظ المشروع في «${file.name}»"
        }
    }

    /** فتح ملف مشروع: لقطة تراجع ثم استبدال كامل — الفساد يُردّ بأمان. */
    fun importMontageProject(file: File) {
        if (!file.isFile) {
            _lastLibraryMessage.value = "ملف المشروع غير موجود"
            return
        }
        val loaded = runCatching {
            Json.gson.fromJson(file.readText(Charsets.UTF_8), MontageProject::class.java)
        }.getOrNull()
        if (loaded == null) {
            _lastLibraryMessage.value = "ملف المشروع تالف فلم يُفتح"
            return
        }
        applyMontage(loaded)
        _montagePlayheadMs.value = loaded.playheadMs.coerceAtLeast(0L)
        _lastLibraryMessage.value = "فُتح المشروع «${file.nameWithoutExtension}» — ${loaded.clips.size} مقاطع"
    }

    /** اسم ملف مشروع افتراضي مسمّى بالتاريخ — لحفظ نسخ منظمة. */
    fun montageProjectDefaultName(): String {
        val t = java.time.LocalDateTime.now()
        return "مشروع-مونتاج-%04d%02d%02d-%02d%02d.json".format(
            t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute
        )
    }

    /**
     * إطلاق تصيير المونتاج: المعاينة تعمل في المشغل نفسه (نفس قناة الموسيقى)،
     * والتصدير يكتب في مجلد التسجيلات ويوقَّع باسم المذيع ويظهر في مكتبة
     * التسجيلات فورًا. الحارس: لا تصييران معًا. thenPlayFromPlayhead يفتح
     * المعاينة موقوفة عند الرأس — «معاينة من أي نقطة» (البند 9).
     *
     * v0.37.0: التصدير يتبع صيغة [montageExportFormat] — WAV كما هو أو MP3
     * بجودة [montageExportBitrate] عبر LAME، بامتداد صحيح ورسالة تُسمّي الصيغة.
     * المعاينة WAV دائمًا — ملف مؤقت للمشغل لا للمشاركة.
     */
    fun renderMontage(preview: Boolean, thenPlayFromPlayhead: Boolean = false) {
        if (_montageRenderProgress.value != null) return
        val p = _montageProject.value
        if (!montageHasSource(p)) {
            _lastLibraryMessage.value = "أضف مقاطع موجودة على القرص أولًا"
            return
        }
        val fmt = if (preview) MontageExportFormat.WAV else _montageExportFormat.value
        val kbps = _montageExportBitrate.value
        val target = if (preview)
            File(File(dataDir(), "montage"), "preview.wav")
        else
            RecordingsScanner.newRecordingFile(
                _recordingsDir.value,
                prefix = "مونتاج",
                extension = fmt.extension
            )
        if (preview) montagePreviewStamp = null // ما يُصيَّر الآن هو الأحدث دائمًا
        montageRenderCancelled = false
        _montageRenderProgress.value = 0f
        montageRenderJob = runScope.launch(Dispatchers.IO) {
            val r = MontageRender.render(
                p, target,
                format = fmt,
                bitrateKbps = kbps,
                onProgress = { pr -> _montageRenderProgress.value = pr },
                cancelled = { montageRenderCancelled }
            )
            _montageRenderProgress.value = null
            when {
                r.ok && preview -> {
                    if (thenPlayFromPlayhead) {
                        montagePreviewStamp = montageStamp(p)
                        if (player.preparePaused(target, _montagePlayheadMs.value)) {
                            player.toggle()
                            _lastLibraryMessage.value =
                                "المعاينة جاهزة — تشغيل من ${montageClock(_montagePlayheadMs.value)} — مسافة للإيقاف"
                            // الفوقية المنتظرة تُطلق لحظة بدء الصوت الفعلي (v0.43.0)
                            if (overdubPending) {
                                val devs = resolveRecorderDevices().filterNotNull()
                                if (devs.isNotEmpty()) launchOverdubRecorder(devs) else overdubPending = false
                            }
                        } else {
                            player.play(target)
                            _lastLibraryMessage.value = "المعاينة جاهزة (${montageClock(r.durationMs)})"
                            if (overdubPending) {
                                val devs = resolveRecorderDevices().filterNotNull()
                                if (devs.isNotEmpty()) launchOverdubRecorder(devs) else overdubPending = false
                            }
                        }
                    } else {
                        player.play(target)
                        _lastLibraryMessage.value =
                            "المعاينة جاهزة (${montageClock(r.durationMs)}) — تعمل في المشغل نفسه"
                    }
                }
                r.ok -> {
                    signRecording(target, r.durationMs)
                    _lastMontageExport.value = target
                    val fmtTag = if (fmt == MontageExportFormat.MP3) "MP3 ${kbps}k" else "WAV"
                    _lastLibraryMessage.value =
                        "صُدِّر المونتاج «${target.name}» — $fmtTag — مدته ${montageClock(r.durationMs)} — جاهز في التسجيلات"
                }
                montageRenderCancelled -> _lastLibraryMessage.value = "أُلغي التصيير"
                else -> _lastLibraryMessage.value = r.message ?: "فشل التصيير"
            }
        }
    }

    fun cancelMontageRender() {
        if (_montageRenderProgress.value == null) return
        montageRenderCancelled = true
        montageRenderJob?.cancel()
        _montageRenderProgress.value = null
        _lastLibraryMessage.value = "أُلغي التصيير"
    }

    /** إضافة الملف المصدَّر إلى القائمة الجارية بلا تشغيل فوري — قرار البث للمذيع (البند 13). */
    fun addMontageExportToPlaylist(file: File): Boolean {
        if (!file.isFile) return false
        if (_playlist.value.any { it.file == file }) return false
        val track = trackFor(file)
        _playlist.value = _playlist.value + track
        playlistStore.setPaths(
            _activePlaylistId.value,
            _playlist.value.map { it.file.path },
            _currentIndex.value
        )
        _lastLibraryMessage.value = "أُضيف «${file.nameWithoutExtension}» إلى قائمة البث"
        return true
    }

    /** مقاطع عرض تجريبية للفحص البصري والأتمتة — نوافذ قص وتلاشين حقيقية بلا عتاد. */
    fun montageDemo() {
        val dir = File(System.getProperty("java.io.tmpdir"), "montage-demo-${System.currentTimeMillis()}")
        val files = com.saidjte.schoolradio.desktop.audio.MontageDemo.make(dir)
        addMontageFiles(files)
        runScope.launch(Dispatchers.IO) {
            // انتظر حل المدد ثم ضع نوافذ قص حية تُظهر قوة الصفحة
            var guard = 0
            while (_montageProject.value.clips.any { it.durationMs == 0L } && guard < 60) {
                delay(100); guard++
            }
            val clips = _montageProject.value.clips
            if (clips.size >= 3 && clips.all { it.durationMs > 0L }) {
                applyMontage(
                    _montageProject.value.copy(
                        gapMs = 500L,
                        crossfadeMs = 0L,
                        clips = listOf(
                            clips[0].copy(startMs = 500, endMs = clips[0].durationMs - 500, fadeInMs = 400, fadeOutMs = 400),
                            clips[1].copy(startMs = 1000, endMs = clips[1].durationMs, volumePercent = 70),
                            clips[2].copy(startMs = 0, endMs = clips[2].durationMs, fadeInMs = 300)
                        )
                    ),
                    record = false
                )
                // سرير موسيقى مرافقة للأتمتة (v0.35.0): يُظهر المسار الثاني وظل النطق
                val bed = clips[1]
                applyMontage(
                    _montageProject.value.copy(
                        musicClips = listOf(
                            MontageMusicClip(
                                id = MontageLogic.newMusicClipId(),
                                path = bed.path, title = "سرير العرض التجريبي",
                                durationMs = bed.durationMs, startMs = 0L,
                                volumePercent = 60
                            )
                        )
                    ),
                    record = false
                )
            }
        }
    }

    // ================= v0.35.0: المسار المزدوج والخفض والعلامات والقوالب =================

    /**
     * إضافة ملفات موسيقى مرافقة إلى المسار الثاني (البند 16): تبدأ عند الرأس
     * إن كان قد تحرك وعند الصفر أول مرة، والمدد تُحل في خيط عمل كما في التعليق.
     */
    fun addMontageMusicFiles(files: List<File>, trackId: String = MontageLogic.DEFAULT_TRACK_ID) {
        val existing = _montageProject.value.musicClips.map { it.path }.toSet()
        val fresh = files.filter {
            it.isFile && it.extension.lowercase() in RecordingsScanner.AUDIO_EXTENSIONS &&
                    it.absolutePath !in existing
        }
        if (fresh.isEmpty()) return
        val startAt = _montagePlayheadMs.value.coerceAtLeast(0L)
        val pending = fresh.map { f ->
            MontageMusicClip(
                id = MontageLogic.newMusicClipId(),
                path = f.absolutePath,
                title = f.nameWithoutExtension,
                startMs = startAt,
                trackId = trackId
            )
        }
        applyMontage(
            _montageProject.value.copy(
                musicClips = (_montageProject.value.musicClips + pending).take(MontageLogic.MAX_TRACK_CLIPS)
            )
        )
        runScope.launch(Dispatchers.IO) {
            val resolved = fresh.associate { it.absolutePath to durationOf(it) }
            val clips = _montageProject.value.musicClips.map { m ->
                val d = resolved[m.path] ?: return@map m
                val (s, e) = MontageLogic.clampWindow(m.startInFileMs, m.endInFileMs, d)
                m.copy(durationMs = d, startInFileMs = s, endInFileMs = e)
            }
            applyMontage(_montageProject.value.copy(musicClips = clips), record = false)
        }
    }

    /** تحديد مقطع موسيقى في المسار الثاني — وإسقاط تحديد التعليق (حصرية الأدوات). */
    fun selectMontageMusic(clipId: String?) {
        _montageSelectedMusicId.value = clipId
        if (clipId != null) _montageSelectedClipId.value = null
    }

    /** تحرير مقطع موسيقى (موضع/نافذة/مستوى/تلاشين) — الحبوس تُطبق هنا والمشروع يُحفظ. */
    fun updateMontageMusicClip(
        clipId: String,
        transform: (MontageMusicClip) -> MontageMusicClip,
        record: Boolean = true
    ) {
        val clips = _montageProject.value.musicClips.map {
            if (it.id == clipId) {
                val t = transform(it)
                val (s, e) = MontageLogic.clampWindow(t.startInFileMs, t.endInFileMs, t.durationMs)
                t.copy(
                    startMs = t.startMs.coerceAtLeast(0L),
                    startInFileMs = s, endInFileMs = e,
                    volumePercent = MontageLogic.clampVolume(t.volumePercent),
                    fadeInMs = MontageLogic.clampFade(t.fadeInMs),
                    fadeOutMs = MontageLogic.clampFade(t.fadeOutMs)
                )
            } else it
        }
        applyMontage(_montageProject.value.copy(musicClips = clips), record)
    }

    /** إزاحة مقطع موسيقى أفقياً على مساره (سحب الجسم) — بلا مواضع سالبة. */
    fun moveMontageMusicClip(clipId: String, deltaMs: Long, record: Boolean = true) =
        updateMontageMusicClip(clipId, { it.copy(startMs = (it.startMs + deltaMs).coerceAtLeast(0L)) }, record)

    /** إزالة مقطع موسيقى من المسار الثاني. */
    fun removeMontageMusicClip(clipId: String) {
        applyMontage(_montageProject.value.copy(musicClips = _montageProject.value.musicClips.filterNot { it.id == clipId }))
    }

    /**
     * إعادة تسمية مقطع تعليق بالنقر المزدوج (v0.37.0) — الاسم يُعقَّم ويُحفظ
     * في المشروع ويظهر على الموجة وفي القائمة، والتراجع يعيد الاسم السابق.
     */
    fun renameMontageClip(clipId: String, title: String) {
        val t = MontageLogic.sanitizeClipTitle(title)
        updateMontageClip(clipId, { it.copy(title = t) })
    }

    /** إعادة تسمية مقطع مسار حر بالنقر المزدوج (v0.37.0) — نفس عقد التعليق. */
    fun renameMontageMusicClip(clipId: String, title: String) {
        val t = MontageLogic.sanitizeClipTitle(title)
        updateMontageMusicClip(clipId, { it.copy(title = t) })
    }

    // ================= v0.38.0: الحافظة والتكرار والدمج والتحويلات =================
    // «قص الصوت / نسخ ولصق / دمج المقاطع / تكرار المقاطع / عكس الصوت /
    //  تعديل السرعة دون تغيير النبرة / تغيير طبقة الصوت» — أدوات المقطع
    // الحديثة: الحافظة رياضيات فورية، والتحويلات ارتداد في خيط عمل عبر
    // ClipTransforms ثم توجيه المقطع إلى ملفه الجديد — الأصل لا يُمسّ أبدًا.

    /**
     * المقطع الهدف لأدوات المقطع: محدد المسارات الحر أولويةً إن كان محددًا،
     * وإلا محدد التعليق — زر واحد يخدم الخطّين بلا ازدواج.
     */
    private fun focusedMontageTarget(): Pair<Boolean, String>? {
        val mid = _montageSelectedMusicId.value
        if (mid != null && _montageProject.value.musicClips.any { it.id == mid }) return true to mid
        val vid = _montageSelectedClipId.value
        if (vid != null && _montageProject.value.clips.any { it.id == vid }) return false to vid
        return null
    }

    /** نسخ المحدد إلى الحافظة — بلا مساس بالترتيب. */
    fun montageCopySelected() {
        val p = _montageProject.value
        val mid = _montageSelectedMusicId.value
        val music = mid?.let { id -> p.musicClips.firstOrNull { it.id == id } }
        if (music != null) {
            _montageClipboard.value = MontageLogic.MontageClipboardEntry.Free(music)
            _lastLibraryMessage.value = "نُسخ «${music.title.take(24)}» من مسار ${trackNameOf(music)} — Ctrl+V للصقه"
            return
        }
        val voice = _montageSelectedClipId.value?.let { id -> p.clips.firstOrNull { it.id == id } }
        if (voice != null) {
            _montageClipboard.value = MontageLogic.MontageClipboardEntry.Voice(voice)
            _lastLibraryMessage.value = "نُسخ «${voice.title.take(24)}» — Ctrl+V للصقه بعد أي محدد"
        } else _lastLibraryMessage.value = "حدّد مقطعًا أولًا (نقرًا عليه)"
    }

    /** قص المحدد: نسخ إلى الحافظة ثم إزالة — التراجع يعيد المقطع كاملًا. */
    fun montageCutSelected() {
        val p = _montageProject.value
        val mid = _montageSelectedMusicId.value
        val music = mid?.let { id -> p.musicClips.firstOrNull { it.id == id } }
        if (music != null) {
            _montageClipboard.value = MontageLogic.MontageClipboardEntry.Free(music)
            removeMontageMusicClip(music.id)
            _montageSelectedMusicId.value = null
            _lastLibraryMessage.value = "قُصّ «${music.title.take(24)}» — حُذف من مساره وهو في الحافظة"
            return
        }
        val voice = _montageSelectedClipId.value?.let { id -> p.clips.firstOrNull { it.id == id } }
        if (voice != null) {
            _montageClipboard.value = MontageLogic.MontageClipboardEntry.Voice(voice)
            removeMontageClip(voice.id)
            _montageSelectedClipId.value = null
            _lastLibraryMessage.value = "قُصّ «${voice.title.take(24)}» — أُغلق مكانه وهو في الحافظة"
        } else _lastLibraryMessage.value = "حدّد مقطعًا أولًا (نقرًا عليه)"
    }

    /** لصق الحافظة: تعليقًا بعد المحدد أو في النهاية، ومسارًا عند الرأس على مساره. */
    fun montagePasteClipboard() {
        val entry = _montageClipboard.value
        if (entry == null) {
            _lastLibraryMessage.value = "الحافظة فارغة — انسخ أو اقص مقطعًا أولًا"
            return
        }
        val p = _montageProject.value
        when (entry) {
            is MontageLogic.MontageClipboardEntry.Voice -> {
                val copy = MontageLogic.pastedVoiceCopy(entry.clip)
                val afterId = _montageSelectedClipId.value
                applyMontage(p.copy(clips = MontageLogic.pasteVoiceAfter(p.clips, copy, afterId)))
                _montageSelectedClipId.value = copy.id
                _lastLibraryMessage.value = "لُصق «${copy.title.take(24)}» — لصق جديد يعيد نسخة جديدة"
            }
            is MontageLogic.MontageClipboardEntry.Free -> {
                val copy = MontageLogic.pastedTrackCopy(entry.clip, _montagePlayheadMs.value)
                applyMontage(p.copy(musicClips = MontageLogic.pasteTrackAt(p.musicClips, copy)))
                _montageSelectedMusicId.value = copy.id
                _lastLibraryMessage.value =
                    "لُصق «${copy.title.take(24)}» على مسار ${trackNameOf(copy)} عند ${montageClock(copy.startMs)}"
            }
        }
    }

    /** اسم مسار مقطع حر للرسائل — «التعليق» احتياطًا للمجهول. */
    private fun trackNameOf(m: MontageMusicClip): String =
        MontageLogic.trackById(MontageLogic.effectiveTracks(_montageProject.value), MontageLogic.effectiveTrackId(m)).name

    /** تكرار المحدد ×عدد — حوار العدد في الواجهة، والحد [MontageLogic.DUPLICATE_MAX]. */
    fun duplicateMontageSelected(times: Int) {
        val target = focusedMontageTarget() ?: run {
            _lastLibraryMessage.value = "حدّد مقطعًا أولًا (نقرًا عليه)"
            return
        }
        val n = times.coerceIn(0, MontageLogic.DUPLICATE_MAX)
        if (n == 0) return
        val p = _montageProject.value
        if (target.first) {
            val src = p.musicClips.firstOrNull { it.id == target.second } ?: return
            val copies = MontageLogic.duplicateTrackClip(src, n)
            applyMontage(p.copy(musicClips = (p.musicClips + copies).take(MontageLogic.MAX_TRACK_CLIPS)))
            _lastLibraryMessage.value = "تكرر «${src.title.take(24)}» ×$n متلاصقًا على مساره — ${montageClock(src.startMs)} حتى ${montageClock(src.startMs + n * MontageLogic.musicDurationMs(src))}"
        } else {
            val clips = MontageLogic.duplicateVoiceAfter(p.clips, target.second, n)
            if (clips == p.clips) return
            applyMontage(p.copy(clips = clips))
            _lastLibraryMessage.value = "تكرر المقطع ×$n خلف الأصل — المدة زادت ${montageClock(n * MontageLogic.clipDurationMs(p.clips.first { it.id == target.second }))}"
        }
    }

    /** دمج المحدد مع التالي: على التعليق بالفاصل/الكروس فيد الحاليين، وعلى المسار لصقًا متلاصقًا. */
    fun mergeMontageSelectedWithNext() {
        val target = focusedMontageTarget() ?: run {
            _lastLibraryMessage.value = "حدّد مقطعًا أولًا (نقرًا عليه)"
            return
        }
        val p = _montageProject.value
        if (target.first) {
            val pair = MontageLogic.mergeableTrackPair(p.musicClips, target.second)
            if (pair == null) {
                _lastLibraryMessage.value = "الدمج يحتاج تالٍ على نفس المسار — اختر مقطعًا ليس أخير مساره"
                return
            }
            val (a, b) = pair
            bounceTransform("merge") {
                val r = ClipTransforms.bounceMerge(
                    MontageRender.musicAsSourceClip(a),
                    MontageRender.musicAsSourceClip(b),
                    gapMs = 0L, crossfadeMs = 0L,
                    cacheDir = montageBounceDir
                )
                val merged = MontageMusicClip(
                    id = MontageLogic.newMusicClipId(),
                    path = r.file.absolutePath,
                    title = ClipTransforms.mergedTitle(a.title, b.title),
                    durationMs = r.durationMs,
                    startMs = a.startMs,
                    startInFileMs = 0L,
                    endInFileMs = r.durationMs,
                    volumePercent = MontageLogic.VOLUME_DEFAULT,
                    fadeInMs = 0L, fadeOutMs = 0L,
                    trackId = a.trackId
                )
                applyMontage(
                    _montageProject.value.copy(
                        musicClips = _montageProject.value.musicClips.filterNot { it.id == a.id || it.id == b.id } + merged
                    ),
                    record = true
                )
                _montageSelectedMusicId.value = merged.id
                "دُمج «${a.title.take(20)}» مع «${b.title.take(20)}» متلاصقين عند ${montageClock(a.startMs)} — مدة الدمج ${montageClock(r.durationMs)}"
            }
        } else {
            val pair = MontageLogic.mergeableVoicePair(p.clips, target.second)
            if (pair == null) {
                _lastLibraryMessage.value = "الدمج يحتاج مقطعًا تالٍ — المحدد الأخير لا يُدمج"
                return
            }
            val (a, b) = pair
            bounceTransform("merge") {
                val r = ClipTransforms.bounceMerge(a, b, p.gapMs, p.crossfadeMs, montageBounceDir)
                val merged = MontageClip(
                    id = MontageLogic.newClipId(),
                    path = r.file.absolutePath,
                    title = ClipTransforms.mergedTitle(a.title, b.title),
                    durationMs = r.durationMs,
                    startMs = 0L, endMs = r.durationMs,
                    // المزج مخبوز في الملف المدموج (المستويات والتلاشين الداخلية)
                    // فالمقطع الناتج محايد — تلاشي الخارجية عادت عبر المُصيِّر نفسه
                    volumePercent = MontageLogic.VOLUME_DEFAULT,
                    fadeInMs = 0L, fadeOutMs = 0L
                )
                applyMontage(
                    _montageProject.value.copy(
                        // المدموج يحل مكان المتتاليتين نفسه — ما قبلهما وما بعدهما بلا مساس
                        clips = p.clips.let { all ->
                            val i = all.indexOfFirst { it.id == a.id }
                            if (i < 0) all
                            else all.subList(0, i) + merged + all.subList((i + 2).coerceAtMost(all.size), all.size)
                        }
                    ),
                    record = true
                )
                _montageSelectedClipId.value = merged.id
                "دُمج «${a.title.take(20)}» مع «${b.title.take(20)}» في مقطع واحد مدته ${montageClock(r.durationMs)}"
            }
        }
    }

    /** عكس الموجة: المقطع يُشغَّل من نهايته إلى بدايته — ارتداد مخزَّن. */
    fun reverseMontageSelected() {
        transformSelectedClip("العكس") { src ->
            ClipTransforms.bounce(src, "reverse", "-", ClipTransforms::reversePcm, montageBounceDir)
        }
    }

    /** تعديل السرعة دون تغيير النبرة — 200٪ ضعف السرعة ونصف المدة. */
    fun speedMontageSelected(percent: Int) {
        val sp = ClipTransforms.clampSpeedPercent(percent)
        transformSelectedClip("السرعة $sp٪") { src ->
            ClipTransforms.bounce(src, "speed", "$sp", { pcm -> ClipTransforms.changeSpeed(pcm, sp) }, montageBounceDir)
        }
    }

    /** تغيير النبرة بأنصاف النغمات دون تغيير الطول — ±12 حدًّا موسيقيًا. */
    fun pitchMontageSelected(semitones: Int) {
        val st = ClipTransforms.clampPitchSemitones(semitones)
        transformSelectedClip("النبرة ${if (st > 0) "+" else ""}$st نغمة") { src ->
            ClipTransforms.bounce(src, "pitch", "$st", { pcm -> ClipTransforms.pitchShift(pcm, st) }, montageBounceDir)
        }
    }

    /**
     * تطبيق مؤثر فردي من مكتبة المؤثرات على المحدد (v0.40.0): القيم تُقيَّد
     * داخل نطاقاتها، والسلسلة تُشفَّر نصًّا محايدًا فيشارك بروتوكول الارتداد
     * المخزّن نفسه — بصمة SHA-1 (ملف|نافذة|مؤثر|قيم) تعيد الملف بلا حساب،
     * والذيل الزمني (صدى/تكرار/تأخير) يُضاف لمدة المقطع الجديدة تلقائيًا.
     */
    fun applyMontageEffect(effectId: String, values: Map<String, Double>) {
        val def = AudioFx.byId(effectId) ?: run {
            _lastLibraryMessage.value = "مؤثر مجهول — اختر من مكتبة المؤثرات"
            return
        }
        val clamped = AudioFx.clampValues(def, values)
        val spec = AudioFx.encodeChain(listOf(FxChainStep(effectId, clamped)))
        transformSelectedClip("مؤثر ${def.nameAr}") { src ->
            ClipTransforms.bounce(src, "fx", spec, { pcm -> AudioFx.applySpec(pcm, spec) }, montageBounceDir)
        }
    }

    /** تطبيق جاهزة جاهزة من المكتبة — سلسلة مؤثرات مدروسة بنقرة واحدة. */
    fun applyMontagePreset(presetId: String) {
        val preset = FxPresets.byId(presetId) ?: run {
            _lastLibraryMessage.value = "جاهزة مجهولة — اختر من المكتبة"
            return
        }
        val steps = preset.steps.map { s ->
            val def = AudioFx.byId(s.defId)
            FxChainStep(s.defId, def?.let { AudioFx.clampValues(it, s.values) } ?: s.values)
        }
        val spec = AudioFx.encodeChain(steps)
        transformSelectedClip("جاهزة ${preset.nameAr}") { src ->
            ClipTransforms.bounce(src, "fx", spec, { pcm -> AudioFx.applySpec(pcm, spec) }, montageBounceDir)
        }
    }

    /**
     * بوابة التحويلات على المحدد: يفكّ الهدف ويطبّق الارتداد في خيط عمل
     * ثم يوجّه المقطع إلى ملفه الجديد بنافذة كاملة — المدة تُحل من الملف
     * الجديد نفسه، والتراجع يعيد المقطع إلى ملفه الأصلي كما هو.
     */
    private fun transformSelectedClip(labelAr: String, op: (MontageClip) -> ClipTransforms.Bounced) {
        val target = focusedMontageTarget() ?: run {
            _lastLibraryMessage.value = "حدّد مقطعًا أولًا (نقرًا عليه)"
            return
        }
        val p = _montageProject.value
        if (target.first) {
            val music = p.musicClips.firstOrNull { it.id == target.second } ?: return
            if (!File(music.path).isFile) {
                _lastLibraryMessage.value = "ملف المقطع مفقود فلا تحويل ممكن"
                return
            }
            bounceTransform(labelAr) {
                val r = op(MontageRender.musicAsSourceClip(music))
                applyMontage(
                    _montageProject.value.copy(
                        musicClips = _montageProject.value.musicClips.map {
                            if (it.id == music.id) it.copy(
                                path = r.file.absolutePath,
                                durationMs = r.durationMs,
                                startInFileMs = 0L, endInFileMs = r.durationMs
                            ) else it
                        }
                    ),
                    record = true
                )
                "$labelAr أُطبّق على «${music.title.take(24)}» — ارتداد ${montageClock(r.durationMs)} والملف الأصلي سليم"
            }
        } else {
            val voice = p.clips.firstOrNull { it.id == target.second } ?: return
            if (!File(voice.path).isFile) {
                _lastLibraryMessage.value = "ملف المقطع مفقود فلا تحويل ممكن"
                return
            }
            bounceTransform(labelAr) {
                val r = op(voice)
                applyMontage(
                    _montageProject.value.copy(
                        clips = _montageProject.value.clips.map {
                            if (it.id == voice.id) it.copy(
                                path = r.file.absolutePath,
                                durationMs = r.durationMs,
                                startMs = 0L, endMs = r.durationMs
                            ) else it
                        }
                    ),
                    record = true
                )
                "$labelAr أُطبّق على «${voice.title.take(24)}» — ارتداد ${montageClock(r.durationMs)} والملف الأصلي سليم"
            }
        }
    }

    /**
     * حارس الارتداد: علم انشغال واحد فلا تحويلان يتسابقان، والفشل يُترجَم —
     * تجاوز الحد وملف مفقود ونافذة فارغة لكلٍّ رسالة عربية واضحة.
     */
    private fun bounceTransform(labelAr: String, work: () -> String) {
        if (_montageTransformBusy.value) {
            _lastLibraryMessage.value = "تحويل آخر قيد التنفيذ — انتظر انتهاءه"
            return
        }
        _montageTransformBusy.value = true
        runScope.launch(Dispatchers.IO) {
            try {
                val msg = work()
                _lastLibraryMessage.value = msg
            } catch (t: ClipTransforms.TransformTooLong) {
                _lastLibraryMessage.value = ClipTransforms.tooLongMessage(t.durationMs)
            } catch (t: Exception) {
                _lastLibraryMessage.value = "فشل $labelAr: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                _montageTransformBusy.value = false
            }
        }
    }

    // ================= v0.36.0: مسطرة العمليات وأدوات المونتاج والمسارات المتعددة =================

    /** ضبط المنطقة المحددة على المسطرة — تحت الحد الأدنى تُمسح لا تُقبَل. */
    fun setMontageSelection(startMs: Long, endMs: Long) {
        val s = minOf(startMs, endMs).coerceAtLeast(0L)
        val e = maxOf(startMs, endMs).coerceAtLeast(0L)
        _montageSelection.value = if (e - s >= MontageLogic.SELECTION_MIN_MS) s to e else null
    }

    /** مسح تحديد المسطرة. */
    fun clearMontageSelection() {
        _montageSelection.value = null
    }

    /**
     * حذف المنطقة المحددة من المسطرة متموّجًا — العملية الجوهرية «حدّد الجزء
     * الذي تريد حذفه»: التعليق والمسارات والعلامات والرأس تُغلق مكان المنطقة.
     */
    fun deleteMontageRegion() {
        val sel = _montageSelection.value ?: return
        val np = MontageLogic.deleteRegionRipple(_montageProject.value, sel.first, sel.second)
        if (np == null) {
            _lastLibraryMessage.value = "المنطقة المحددة ضئيلة جدًا للحذف"
            return
        }
        val cut = sel.second - sel.first
        applyMontage(np.copy(selectionStartMs = -1L, selectionEndMs = -1L))
        _montageSelection.value =
            if (np.selectionStartMs >= 0L) np.selectionStartMs to np.selectionEndMs else null
        _montagePlayheadMs.value = np.playheadMs.coerceAtLeast(0L)
        _lastLibraryMessage.value =
            "حُذفت المنطقة ${montageClock(sel.first)} ← ${montageClock(sel.second)} — أُغلق مكانها (${cut / 1000L} ثوانٍ)"
    }

    /** حلقة A/B جاهزة من المنطقة المحددة على المسطرة — بضغطة واحدة. */
    fun setMontageLoopFromSelection() {
        val sel = _montageSelection.value ?: return
        _montageLoopStart.value = sel.first
        _montageLoopEnd.value = sel.second
        _montageLoopEnabled.value = true
        _lastLibraryMessage.value = "الحلقة تعمل: ${montageClock(sel.first)} ← ${montageClock(sel.second)}"
    }

    /** علامة مسمّاة عند بداية المنطقة المحددة. */
    fun addMontageMarkerAtSelectionStart(name: String) {
        val sel = _montageSelection.value ?: return
        addMontageMarkerAt(name, sel.first)
    }

    /** اختيار الأداة النشطة على الخط الزمني. */
    fun setMontageTool(t: MontageTool) {
        _montageTool.value = t
    }

    /** تفعيل/تعطيل التقاط السحب. */
    fun setMontageSnapEnabled(on: Boolean) {
        _montageSnapEnabled.value = on
    }

    fun toggleMontageSnap() {
        _montageSnapEnabled.value = !_montageSnapEnabled.value
        _lastLibraryMessage.value = if (_montageSnapEnabled.value) "الالتقاط مفعل — السحب يلتقط العلامات والحواف والشبكة" else "الالتقاط معطّل — حركة حرة"
    }

    // ---- المسارات المتعددة (عدة خطوط عمل) ----

    /** قائمة المسارات الفعّالة للمشروع الحالي. */
    fun montageTracksEffective(): List<MontageTrack> = MontageLogic.effectiveTracks(_montageProject.value)

    /**
     * إضافة مسار عمل حر جديد بنوع معين (v0.41.0): موسيقى خلفية/مؤثرات/حوار —
     * الاسم يُشتق من النوع بلا تكرار واللون يُنتقى من غير المستخدم، والسقف
     * 16 مسارًا يحمي الواجهة. المشروع الخاوي يُرسّخ الثلاثي الافتراضي أولًا.
     */
    fun addMontageTrack(type: String = MontageLogic.TYPE_MUSIC) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        if (base.size >= MontageLogic.MAX_TRACKS) {
            _lastLibraryMessage.value = "بلغت سقف المسارات (${MontageLogic.MAX_TRACKS}) — احذف مسارًا فارغًا أولًا"
            return
        }
        val t = MontageLogic.newTrackOfType(base, type)
        applyMontage(p.copy(tracks = base + t))
        _lastLibraryMessage.value =
            "أُضيف مسار «${t.name}» (${MontageLogic.trackTypeLabelAr(type)}) — اسحب مقاطع إليه أو أضف ملفات وهو محدد"
    }

    /** حذف مسار حر مع مقاطعه وخط أتمتته — آخر مسار لا يُحذف، والتراجع يستعيد الكل. */
    fun removeMontageTrack(trackId: String) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        if (base.size <= 1) {
            _lastLibraryMessage.value = "آخر مسار لا يُحذف — قلب المشروع بلا خطوط عمل"
            return
        }
        val target = base.firstOrNull { it.id == trackId } ?: return
        applyMontage(
            p.copy(
                tracks = base.filterNot { it.id == trackId },
                musicClips = p.musicClips.filter { MontageLogic.effectiveTrackId(it) != trackId },
                automation = MontageLogic.automationLanesOf(p).filterNot { it.trackId == trackId }
            )
        )
        _lastLibraryMessage.value = "حُذف مسار «${target.name}» مع مقاطعه — تراجع يعيده"
    }

    /** إعادة تسمية مسار. */
    fun renameMontageTrack(trackId: String, name: String) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        applyMontage(p.copy(tracks = base.map { if (it.id == trackId) it.copy(name = name.trim().take(24)) else it }))
    }

    /** مستوى مسار بالبئة — كسب المزج لكله مقاطعه. */
    fun setMontageTrackVolume(trackId: String, percent: Int) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        applyMontage(
            p.copy(tracks = base.map { if (it.id == trackId) it.copy(volumePercent = MontageLogic.clampVolume(percent)) else it }),
            record = false
        )
    }

    /**
     * كتم مسار حر (v0.41.0 — طلب نظام المونتاج متعدد المسارات): صفر كسب
     * في المزج والمعاينة والتصدير معًا — ومسار التعليق وحده خارج العقد.
     */
    fun setMontageTrackMute(trackId: String, muted: Boolean) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        applyMontage(p.copy(tracks = base.map { if (it.id == trackId) it.copy(muted = muted) else it }))
    }

    /** عزل مسار حر — يبقى حيًّا وحده وسط أي منفردَين، ووحدة المزج تقرر البقية. */
    fun setMontageTrackSolo(trackId: String, solo: Boolean) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        applyMontage(p.copy(tracks = base.map { if (it.id == trackId) it.copy(solo = solo) else it }))
    }

    /** توازن استريو لمسار −100..+100 — توزيع قناة إلى قناة في المزج. */
    fun setMontageTrackPan(trackId: String, pan: Int) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        applyMontage(
            p.copy(tracks = base.map { if (it.id == trackId) it.copy(pan = MontageLogic.clampPan(pan)) else it }),
            record = false
        )
    }

    /** تغيير نوع مسار — الاسم يبقى ما سمّاه المذيع فلا مفاجأة تسمية. */
    fun setMontageTrackType(trackId: String, type: String) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        applyMontage(p.copy(tracks = base.map { if (it.id == trackId) it.copy(type = type) else it }))
    }

    /** تحريك مسار خطوة لأعلى أو لأسفل — ترتيب خطوط العمل بلا مساس مقاطعها. */
    fun moveMontageTrack(trackId: String, delta: Int) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        val moved = MontageLogic.moveTrack(base, trackId, delta) ?: return
        applyMontage(p.copy(tracks = moved))
    }

    // ---- المجموعات (v0.41.0: تجميع المسارات) ----

    /**
     * إضافة مجموعة باسم — تُحفظ في المشروع وتظهر في شريط المجموعات، والمسار
     * ينتسب إليها من رأسه. الاسم الفارغ يولّد أول «مجموعة N» غير مستخدم
     * والسقف 8 مجموعات.
     */
    fun addMontageGroup(name: String) {
        val p = _montageProject.value
        val groups = MontageLogic.groupsOf(p)
        if (groups.size >= MontageLogic.MAX_GROUPS) {
            _lastLibraryMessage.value = "بلغت سقف المجموعات (${MontageLogic.MAX_GROUPS}) — احذف مجموعة أولًا"
            return
        }
        val trimmed = name.trim().take(24)
        val n = if (trimmed.isNotEmpty()) {
            trimmed
        } else {
            // الاسم الفارغ يولّد أول «مجموعة N» غير مستخدم فلا أسماء متكررة
            var i = 1
            while (groups.any { it.name == "مجموعة $i" }) i++
            "مجموعة $i"
        }
        val g = com.saidjte.schoolradio.desktop.data.MontageGroup(
            id = MontageLogic.newGroupId(),
            name = n,
            colorIndex = groups.size % MontageLogic.TRACK_COLORS.size
        )
        applyMontage(p.copy(groups = groups + g))
        _lastLibraryMessage.value = "أُنشئت مجموعة «${g.name}» — انتسب إليها المسارات من رؤوسها"
    }

    /** حذف مجموعة — أعضاؤها يعودون بلا مجموعة بقيمهم الخاصة كما هي. */
    fun removeMontageGroup(groupId: String) {
        val p = _montageProject.value
        val groups = MontageLogic.groupsOf(p)
        applyMontage(
            p.copy(
                groups = groups.filterNot { it.id == groupId },
                tracks = MontageLogic.effectiveTracks(p).map {
                    if (it.groupId == groupId) it.copy(groupId = null) else it
                }
            )
        )
    }

    /** إعادة تسمية مجموعة. */
    fun renameMontageGroup(groupId: String, name: String) {
        val p = _montageProject.value
        val groups = MontageLogic.groupsOf(p)
        applyMontage(p.copy(groups = groups.map { if (it.id == groupId) it.copy(name = name.trim().take(24)) else it }))
    }

    /** مستوى مجموعة — يتراكب مع مستوى كل عضو في المزج. */
    fun setMontageGroupVolume(groupId: String, percent: Int) {
        val p = _montageProject.value
        val groups = MontageLogic.groupsOf(p)
        applyMontage(
            p.copy(groups = groups.map { if (it.id == groupId) it.copy(volumePercent = MontageLogic.clampVolume(percent)) else it }),
            record = false
        )
    }

    /** كتم مجموعة — يصمّم كل أعضائها بصرف النظر عن أزرارهم. */
    fun setMontageGroupMute(groupId: String, muted: Boolean) {
        val p = _montageProject.value
        val groups = MontageLogic.groupsOf(p)
        applyMontage(p.copy(groups = groups.map { if (it.id == groupId) it.copy(muted = muted) else it }))
    }

    /** عزل مجموعة — أعضاؤها يبقون حيّين وسط أي منفردَين آخرين. */
    fun setMontageGroupSolo(groupId: String, solo: Boolean) {
        val p = _montageProject.value
        val groups = MontageLogic.groupsOf(p)
        applyMontage(p.copy(groups = groups.map { if (it.id == groupId) it.copy(solo = solo) else it }))
    }

    /** انتساب مسار إلى مجموعة — أو خروجه منها حين تُمرَّر null. */
    fun setMontageTrackGroup(trackId: String, groupId: String?) {
        val p = _montageProject.value
        val base = MontageLogic.effectiveTracks(p)
        applyMontage(p.copy(tracks = base.map { if (it.id == trackId) it.copy(groupId = groupId) else it }))
    }

    // ---- الأتمتة (v0.41.0: أتمتة مستوى الصوت) ----

    /**
     * تفعيل/تعطيل أتمتة مسار: التفعيل ينشئ خطًا بنقطة واحدة عند الصفر
     * بمستوى المسار الحيّ (سلوك بلا تغيير صوتي فورًا)، والتعطيل يمحو الخط
     * فيرجع المستوى قيمة رأس المسار الثابتة — وكلاهما داخل سجل التراجع.
     */
    fun toggleMontageAutomation(trackId: String) {
        val p = _montageProject.value
        val lanes = MontageLogic.automationLanesOf(p)
        val existing = lanes.firstOrNull { it.trackId == trackId }
        if (existing != null) {
            applyMontage(p.copy(automation = lanes.filterNot { it.trackId == trackId }))
            _lastLibraryMessage.value = "أُوقفت أتمتة المسار — عاد مستواه ثابتًا"
        } else {
            val tr = MontageLogic.effectiveTracks(p).firstOrNull { it.id == trackId } ?: return
            val lane = com.saidjte.schoolradio.desktop.data.MontageAutomationLane(
                trackId = trackId,
                points = MontageLogic.sanitizeAutoPoints(MontageLogic.defaultAutoPoints(tr))
            )
            applyMontage(p.copy(automation = lanes + lane))
            _lastLibraryMessage.value = "أتمتة المسار تعمل — انقر على شريطها لإضافة نقطة وسحب النقطة يحرّكها"
        }
    }

    /** إضافة نقطة أتمتة عند زمن ومستوى — محبوسة بالسقف والنطاق. */
    fun addMontageAutoPoint(trackId: String, tMs: Long, percent: Int) {
        val p = _montageProject.value
        val lanes = MontageLogic.automationLanesOf(p)
        val lane = lanes.firstOrNull { it.trackId == trackId } ?: return
        if (lane.points.size >= MontageLogic.MAX_AUTO_POINTS) {
            _lastLibraryMessage.value = "بلغت سقف النقاط (${MontageLogic.MAX_AUTO_POINTS}) — امسح نقطة أولًا"
            return
        }
        val pt = com.saidjte.schoolradio.desktop.data.MontageAutoPoint(
            id = MontageLogic.newAutoPointId(),
            tMs = tMs.coerceAtLeast(0L),
            percent = MontageLogic.clampVolume(percent)
        )
        val updated = lane.copy(points = MontageLogic.sanitizeAutoPoints(lane.points + pt))
        applyMontage(p.copy(automation = lanes.map { if (it.trackId == trackId) updated else it }))
    }

    /** تحريك نقطة أتمتة (سحب في الواجهة) — زمن جديد ومستوى جديد بمعرّفها الثابت. */
    fun moveMontageAutoPoint(trackId: String, pointId: String, tMs: Long, percent: Int) {
        val p = _montageProject.value
        val lanes = MontageLogic.automationLanesOf(p)
        val lane = lanes.firstOrNull { it.trackId == trackId } ?: return
        val updated = lane.copy(
            points = MontageLogic.sanitizeAutoPoints(
                lane.points.map {
                    if (it.id == pointId)
                        it.copy(tMs = tMs.coerceAtLeast(0L), percent = MontageLogic.clampVolume(percent))
                    else it
                }
            )
        )
        applyMontage(p.copy(automation = lanes.map { if (it.trackId == trackId) updated else it }), record = false)
    }

    /** إزالة نقطة أتمتة بمعرّفها — النقطة الوحيدة لا تُحذف فلا يتبع خط بلا مرساة. */
    fun removeMontageAutoPoint(trackId: String, pointId: String) {
        val p = _montageProject.value
        val lanes = MontageLogic.automationLanesOf(p)
        val lane = lanes.firstOrNull { it.trackId == trackId } ?: return
        if (lane.points.size <= 1) return
        applyMontage(
            p.copy(
                automation = lanes.map {
                    if (it.trackId == trackId)
                        it.copy(points = MontageLogic.sanitizeAutoPoints(it.points.filterNot { pt -> pt.id == pointId }))
                    else it
                }
            )
        )
    }

    /** نقل مقطع مسار إلى مسار آخر (سحب عمودي بين الخطوط). */
    fun moveMontageTrackClipToTrack(clipId: String, trackId: String) {
        updateMontageMusicClip(clipId, { it.copy(trackId = trackId) })
    }

    /**
     * تقسيم مقطع مسار عند موضع زمني مطلق على الخط (أداة المقصّ بالنقر) —
     * خارج نافذة المقطع لا شيء يحدث، وداخلها يولد نصفان بمعرّفين جديدين.
     */
    fun splitMontageTrackClipAtMs(clipId: String, timelineMs: Long) {
        val p = _montageProject.value
        val clip = p.musicClips.firstOrNull { it.id == clipId } ?: return
        val pair = MontageLogic.splitTrackClipAt(clip, timelineMs - clip.startMs) ?: return
        val (a, b) = pair
        applyMontage(
            p.copy(musicClips = p.musicClips.filterNot { it.id == clipId } + a + b)
        )
        _montageSelectedMusicId.value = b.id
        _lastLibraryMessage.value = "انشطر «${clip.title.take(20)}» عند ${montageClock(timelineMs)} — نصفان على نفس المسار"
    }

    /**
     * تقسيم مقطع تعليق عند موضع زمني مطلق (أداة المقصّ بالنقر على مسار التعليق)
     * — نفس عقد التقسيم عند الرأس لكن من نقطة النقر مباشرة.
     */
    fun splitMontageVoiceClipAtMs(clipId: String, timelineMs: Long) {
        val p = _montageProject.value
        val starts = MontageLogic.clipStartMs(p.clips, p.gapMs, p.crossfadeMs)
        val idx = p.clips.indexOfFirst { it.id == clipId }
        if (idx < 0) return
        val clip = p.clips[idx]
        val pair = MontageLogic.splitClipAt(clip, timelineMs - starts[idx]) ?: return
        val (a, b) = pair
        val clips = p.clips.toMutableList()
        clips[idx] = a
        clips.add(idx + 1, b)
        applyMontage(p.copy(clips = clips))
        _montageSelectedClipId.value = b.id
        _lastLibraryMessage.value = "انشطر «${clip.title.take(20)}» عند ${montageClock(timelineMs)}"
    }

    /** تقليم حافة يسرى لمقطع مسار (سحب المقابض) — لقطة تاريخ واحدة للسحبة. */
    fun trimMontageTrackClipLeft(clipId: String, deltaMs: Long) {
        updateMontageMusicClip(clipId, { MontageLogic.trimTrackClipLeft(it, deltaMs) }, record = false)
    }

    /** تقليم حافة يمنى لمقطع مسار. */
    fun trimMontageTrackClipRight(clipId: String, deltaMs: Long) {
        updateMontageMusicClip(clipId, { MontageLogic.trimTrackClipRight(it, deltaMs) }, record = false)
    }

    /** تفعيل/تعطيل الخفض التلقائي للسرير الموسيقي. */
    fun setMontageDuckingEnabled(on: Boolean) =
        applyMontage(_montageProject.value.copy(duckingEnabled = on))

    /** عمق الخفض بالديسيبل — محبوس −30..−3. */
    fun setMontageDuckDb(db: Int) =
        applyMontage(_montageProject.value.copy(duckDb = MontageLogic.clampDuckDb(db)))

    // ---- العلامات المسمّاة (البند 17) ----

    /** إضافة علامة عند الرأس باسم — «الفاصل» افتراضيًا حين يترك الاسم فارغًا. */
    fun addMontageMarkerAtPlayhead(name: String) {
        addMontageMarkerAt(name, _montagePlayheadMs.value)
    }

    /** إضافة علامة مسمّاة عند موضع زمني صريح (الرأس أو بداية المنطقة المحددة). */
    fun addMontageMarkerAt(name: String, tMs: Long) {
        val n = name.trim().ifEmpty { "الفاصل" }.take(24)
        val mk = MontageMarker(
            id = MontageLogic.newMarkerId(),
            name = n,
            tMs = tMs.coerceAtLeast(0L)
        )
        applyMontage(
            _montageProject.value.copy(
                markers = MontageLogic.sanitizeMarkers(_montageProject.value.markers + mk)
            )
        )
    }

    /** إزالة علامة بمعرفها. */
    fun removeMontageMarker(markerId: String) {
        applyMontage(_montageProject.value.copy(markers = _montageProject.value.markers.filterNot { it.id == markerId }))
    }

    /** تطبيق قالب جاهز: علامات موصى بها + فاصل/كروس فيد/خفض موصى به — المقاطع تبقى كما هي. */
    fun applyMontageTemplate(templateId: String) {
        val t = MontageTemplateCatalog.byId(templateId) ?: return
        applyMontage(MontageTemplatesLogic.apply(_montageProject.value, t))
        _lastLibraryMessage.value = "طُبّق قالب «${t.name}» — ${t.markers.size} علامات على الخط الزمني"
    }

    /**
     * تنفيذ اختصارات صفحة المونتاج (البند 10) — بوابة واحدة من معالج النافذة
     * كالاستوديو تمامًا، فلا مسار مفتاحٍ يتسلل خارج البروتوكول.
     */
    fun applyMontageShortcut(a: com.saidjte.schoolradio.desktop.ui.MontageShortcutAction) {
        when (a) {
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.PreviewPlayPause -> toggleMontagePreview()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.SplitAtPlayhead -> splitMontageAtPlayhead()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.DeleteSelected -> deleteSelectedMontageClip()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.Undo -> undoMontage()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.Redo -> redoMontage()
            is com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.PlayheadMove ->
                setMontagePlayhead(_montagePlayheadMs.value + a.deltaMs)
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.SelectPrev -> {
                val p = _montageProject.value
                val i = p.clips.indexOfFirst { it.id == _montageSelectedClipId.value }
                val t = if (i <= 0) 0 else i - 1
                _montageSelectedClipId.value = p.clips.getOrNull(t)?.id
            }
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.SelectNext -> {
                val p = _montageProject.value
                val i = p.clips.indexOfFirst { it.id == _montageSelectedClipId.value }
                val t = if (i < 0) 0 else (i + 1).coerceAtMost(p.clips.size - 1)
                _montageSelectedClipId.value = p.clips.getOrNull(t)?.id
            }
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.DeleteRegion -> deleteMontageRegion()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.ToggleSnap -> toggleMontageSnap()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.ClearSelection -> clearMontageSelection()
            // v0.38.0: الحافظة والتكرار والدمج
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.CutSelected -> montageCutSelected()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.CopySelected -> montageCopySelected()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.PasteClipboard -> montagePasteClipboard()
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.DuplicateSelected -> duplicateMontageSelected(1)
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.MergeWithNext -> mergeMontageSelectedWithNext()
            // v0.40.0: الاختصار E يفتح مكتبة المؤثرات — نبضة للواجهة فتفتح الحوار
            com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.OpenEffects ->
                _montageEffectsOpenTick.value = _montageEffectsOpenTick.value + 1
        }
    }
    // ================= النسخ الاحتياطي JSON =================
    // للمشرف فقط — فحص إصدار وصياغة قبل الاستبدال + نسخ أمان .bak (مثل نسخة الهاتف)

    fun exportBackup(target: File): Backup.Report {
        if (!canAdminister) return Backup.Report(false, "النسخ الاحتياطي للمشرف فقط")
        return Backup.export(target, dataDir(), APP_VERSION)
    }

    fun importBackup(file: File): Backup.Report {
        if (!canAdminister) return Backup.Report(false, "النسخ الاحتياطي للمشرف فقط")
        val r = Backup.restore(file, dataDir())
        if (r.ok) reloadAllStores()
        return r
    }

    /** إعادة تحميل كل المخازن من القرص بعد استعادة — تحديث كامل للحالة. */
    private fun reloadAllStores() {
        usersStore = UsersStore(File(dataDir(), "users.json"))
        settingsStore = SettingsStore(File(dataDir(), "settings.json"))
        scriptStore = ScriptStore(File(dataDir(), "script.json"))
        scheduleStore = ScheduleStore(File(dataDir(), "schedule.json"))
        weeklyStore = WeeklyStore(File(dataDir(), "weekly.json"))
        executionsStore = ExecutionsStore(File(dataDir(), "executions.json"))
        libraryStore = MusicLibraryStore(File(dataDir(), "library.json"))
        playlistStore = PlaylistStore(File(dataDir(), "playlist.json"))
        refreshPlaylistState(loadTracks = true)
        // المونتاج: مشروع المستعاد من النسخة الاحتياطية
        montageStore = MontageStore(File(dataDir(), "montage.json"))
        _montageProject.value = montageStore.project()
        // جدار الجينغل: بيانات المجلد المستعاد قد تحمل مواضع ومستوى مختلفين
        loadJingleWall()
        // مكتبة الأصوات: المخزن المستعاد قد يحمل مكتبتي الخاصة بملفات أخرى
        soundStore = SoundLibraryStore(File(dataDir(), "soundlib.json"))
        loadSounds()

        _users.value = usersStore.users()
        _currentUser.value = usersStore.currentUser()
        _schoolName.value = settingsStore.schoolName()
        // وحدة المزج: الحالة المستعادة تُدفع للقنوات الحية أيضًا
        _mixer.value = settingsStore.mixer()
        pushMixerToEngines()
        _script.value = scriptStore.sections()
        _selectedSectionId.value = _script.value.firstOrNull()?.id
        _schedule.value = scheduleStore.items()
        _episodeStartMinutes.value = scheduleStore.episodeStartMinutes()
        _weeklyEvents.value = weeklyStore.events()
        _executions.value = executionsStore.entries()
        firedThisSession.clear()
        refreshLibraryState()
        clampRun()
    }

    /** تحميل مقاطع القائمة الجارية من المخزن — الأمداد تُحسب في الخلفية. */
    private fun loadActivePlaylistTracks() {
        val active = playlistStore.active()
        val files = active.paths.map { File(it) }.filter { it.isFile }
        if (files.isEmpty()) {
            _playlist.value = emptyList()
            _currentIndex.value = -1
            return
        }
        _playlist.value = files.map { trackFor(it) }
        val idx = active.currentIndex ?: -1
        _currentIndex.value = if (idx in files.indices) idx else -1
        val activeId = _activePlaylistId.value
        thread(name = "playlist-durations", isDaemon = true) {
            val withDur = files.map { f ->
                val t = trackFor(f)
                t.copy(durationMs = if (t.durationMs == 0L) durationOf(f) else t.durationMs)
            }
            // قد تكون القائمة الجارية تغيّرت أثناء الحساب — لا نطغى عليها
            if (_activePlaylistId.value == activeId) _playlist.value = withDur
        }
    }

    // ================= استعادة الجلسة الأخيرة (v0.32.0) =================
    // القائمة تُذكر في playlist.json أصلًا — وsession.json يكمل الباقي:
    // المقطع الجاري وموضعه لحظة الإغلاق والفقرة المحددة ووضع القراءة الكاملة.

    private var sessionStore = SessionStore(File(dataDir(), "session.json"))

    /** آخر لقطة حُفظت — للمقارنة فلا كتابة بلا تغيّر فعلي. */
    @Volatile private var lastSessionSnapshot: SessionJson? = null

    /** أثناء استعادة الجلسة: فشل فتح المقطع المحفوظ لا يشغّل سلسلة تخطي التالف. */
    @Volatile private var restoringSession = false

    private var sessionSaveJob: Job? = null

    /** لقطة الجلسة الحالية — بلا طابع زمني كي تقارن بالمحفوظة عدلاً. */
    private fun sessionSnapshot(): SessionJson = SessionJson(
        trackPath = player.currentFile.value?.absolutePath,
        positionMs = if (player.hasTrack.value) player.positionMs.value else null,
        selectedSectionId = _selectedSectionId.value,
        fullScriptReading = fullScriptReading.value
    )

    /** حفظ الجلسة الآن — عند الإغلاق ومن دورة الحفظ الدورية. */
    fun persistSessionNow() {
        val snap = sessionSnapshot()
        lastSessionSnapshot = snap
        runCatching { sessionStore.save(snap.copy(savedAt = System.currentTimeMillis())) }
    }

    /** حفظ مؤجّل 400ms — يجمع التغييرات المتسارعة (تنقّل الفقرات) في كتابة واحدة. */
    private fun scheduleSessionSave() {
        sessionSaveJob?.cancel()
        sessionSaveJob = runScope.launch {
            delay(400)
            val snap = sessionSnapshot()
            if (snap != lastSessionSnapshot) persistSessionNow()
        }
    }

    /**
     * استعادة الجلسة الأخيرة عند الإقلاع (v0.32.0) — تُستدعى بعد تحميل القائمة
     * والسكريبت فتكتمل الصورة: الفقرة المحددة ووضع القراءة، ثم المقطع الجاري
     * يُحمّل موقوفًا عند موضع الإغلاق — الضغطة الأولى تكمل لا تُعيد من الصفر.
     * كل فشل (ملف مفقود/ملف تالف/جلسة فارغة) يعود بأمانة للإقلاع الطبيعي.
     */
    private fun restoreLastSession() {
        val saved = runCatching { sessionStore.session() }.getOrNull() ?: return
        val scriptIds = _script.value.map { it.id }.toSet()
        val paths = _playlist.value.map { it.file.absolutePath }
        val r = runCatching { SessionRestore.resolve(saved, paths, scriptIds) }.getOrNull() ?: return

        // السكريبت: الفقرة المحددة ووضع القراءة الكاملة
        if (r.selectedSectionId != null) {
            _selectedSectionId.value = r.selectedSectionId
            fullScriptReading.value = r.fullReading
        }

        // الموضع: المقطع الجاري لحظة الإغلاق
        val path = saved.trackPath?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val f = File(path)
        if (!f.isFile) return
        // توحيد مؤشر القائمة مع المقطع المستعاد — القائمة والموضع وجهان لعملة واحدة
        if (r.playlistIndex >= 0 && _currentIndex.value != r.playlistIndex) {
            _currentIndex.value = r.playlistIndex
            playlistStore.setIndex(_activePlaylistId.value, r.playlistIndex)
        }
        restoringSession = true
        val ok = player.preparePaused(f, r.positionMs)
        if (ok) {
            // نافذة أمان قصيرة: بعد نجاح الفتح تعود حراسة التخطي طبيعية
            runScope.launch { delay(3000); restoringSession = false }
        } else {
            restoringSession = false
        }
    }

    fun durationOf(f: File): Long = when (f.extension.lowercase()) {
        "wav" -> WavHeader.read(f)?.let {
            WavFileWriter.durationMsOf(it.dataBytes, it.sampleRate, it.channels, it.bitsPerSample)
        }
        "mp3" -> Mp3Info.analyze(f)?.durationMs
        else -> StreamProbe.durationMsOf(f)
    } ?: 0L

    init {
        player.onEnded = { next(auto = true) }
        player.onPlayFailed = { skipCorrupt() }
        // الكروس فيد: الإعدادات المحفوظة إلى المحرك + بوابة «التالي/التأكيد»
        pushCrossfadeToEngine()
        wireCrossfade()
        // جدار الجينغل: استخراج المدمجة واسترجاع المواضع والمستوى المحفوظ
        loadJingleWall()
        // مكتبة الأصوات والموسيقى: استخراج المدمجة الـ36 وقراءة مكتبتي الخاصة
        loadSounds()
        // وحدة المزج: الحالة المحفوظة تُدفع إلى القنوات عند الإقلاع
        pushMixerToEngines()
        _recordingsDir.value.mkdirs()
        refreshRecordings()
        if (_script.value.isNotEmpty()) {
            _selectedSectionId.value = _script.value.first().id
        }
        // توقيع المُسجِّل: كل تسجيل مكتمل يُختم باسم المستخدم ودوره (مثل النسخة الهاتفية)
        mic.onRecordingSaved = { file, durMs -> signRecording(file, durMs) }
        // قوائم التشغيل المسماة: تُستعاد عند الإقلاع وتُحمل مقاطع الجارية
        refreshPlaylistState(loadTracks = true)
        // فحص الفائت بعد انقطاع: من آخر نبضة محفوظة إلى الآن
        val lastRun = weeklyStore.lastRunAt()
        if (lastRun != null) {
            val now = System.currentTimeMillis()
            val keys = executionsStore.entries()
                .mapNotNull { e -> if (e.eventId != null) "${e.eventId}@${e.dateKey}" else null }
                .toSet()
            for (d in WeeklyMath.missedBetween(weeklyStore.events(), keys, lastRun, now)) {
                executionsStore.log(d.event.id, d.event.title ?: "", d.dateKey, d.atEpochMs, d.status)
            }
            _executions.value = executionsStore.entries()
        }
        refreshLibraryState()
        // استعادة الجلسة الأخيرة (v0.32.0): قارئ وهمي للأتمتة ثم الاستعادة ثم دورة الحفظ
        if (LaunchFlags.args.contains("--restore-fake")) {
            player.trackReaderOverride = {
                com.saidjte.schoolradio.desktop.audio.FakeTrackVoice(totalMs = 15_000L, chunkSleepMs = 120L)
            }
        }
        restoreLastSession()
        // ساعة الحاسوب الحية: كل ثانية (v0.22.0)
        runScope.launch {
            while (true) {
                delay(1000)
                _clockText.value = StudioLogic.clockText(System.currentTimeMillis())
            }
        }
        // دورة حفظ الجلسة: كل 4 ثوانٍ عند تغيّر فعلي (موضع متقدم/فقرة جديدة)
        runScope.launch {
            while (true) {
                delay(4_000)
                val snap = sessionSnapshot()
                if (snap != lastSessionSnapshot) persistSessionNow()
            }
        }
        // نبضة الجدولة الأسبوعية: كل 20 ثانية
        runScope.launch {
            while (true) {
                delay(20_000)
                tickWeekly()
            }
        }
        // نبضة التسجيل المجدول (v0.43.0): كل 10 ثوانٍ — الإطلاق والوقف الآلي
        runScope.launch {
            while (true) {
                delay(10_000)
                runCatching { tickScheduledRecordings() }
            }
        }
        // كشف الملفات المفقودة دوريًا
        runScope.launch {
            while (true) {
                delay(30_000)
                refreshLibraryState()
            }
        }
    }

    fun selectTab(t: Tab) {
        _tab.value = t
        // تذكّر آخر صفحة (v0.39.0): إن فعّله المذيع تُكتب الصفحة الحالية
        // فإقلاع بلا علم أتمتة يعيد فتح ما كان مفتوحًا
        if (_workspace.value.rememberLastTab) {
            patchWorkspace { it.copy(lastTab = t.name) }
        }
    }

    // ================= سحب وإفلات الملفات الصوتية (v0.39.0) =================

    /** هل مؤشر السحب من نظام التشغيل فوق التطبيق الآن؟ (لحافة الإبراز الحية) */
    val dropHover = MutableStateFlow(false)

    /**
     * معالجة ملفات أُفلات على النافذة (أو مُمرَّرة من علام الأتمتة): تُصفّى
     * في DropLogic ثم تُوجَّه — المونتاج يضيفها مقاطع تعليق، وغيره يستوردها
     * إلى المكتبة بالتصنيف المعروض أو الافتراضي — فلا إفلات يضيع بلا أثر.
     */
    fun handleDroppedFiles(files: List<File>) {
        val d = com.saidjte.schoolradio.desktop.data.DropLogic.decide(files)
        if (!d.hasAudio) {
            d.message("المكتبة")?.let { _lastLibraryMessage.value = it }
            return
        }
        val dest = com.saidjte.schoolradio.desktop.data.DropLogic.destinationFor(_tab.value.name)
        when (dest) {
            com.saidjte.schoolradio.desktop.data.DropLogic.Destination.MONTAGE -> {
                addMontageFiles(d.audioFiles)
                _lastLibraryMessage.value = d.message("المونتاج (مقاطع تعليق)")
            }
            com.saidjte.schoolradio.desktop.data.DropLogic.Destination.LIBRARY -> {
                importToLibrary(d.audioFiles, com.saidjte.schoolradio.desktop.data.DropLogic.DEFAULT_IMPORT_CATEGORY)
            }
        }
    }

    /** فتح نافذة جدار الجينغل المنبثقة — يقرؤها Main ويطلبها زر الاستوديو وF1. */
    val showShortcutsDialog = MutableStateFlow(false)

    // ================= دورة النسخ الاحتياطية المتدحرجة (v0.39.0 حفظ تلقائي محصّن) =================
    // كل خمس دقائق: المخازن التي تغيّر محتواها عن آخر لقطة تُنسخ مؤرخة في
    // backups/ مع تدوير — فانقطاع بعد ساعة عمل لا يخسر إلا خمس دقائق كثير.

    init {
        runScope.launch {
            while (true) {
                delay(300_000)
                runCatching {
                    com.saidjte.schoolradio.desktop.data.AutoBackup.snapshot(
                        dataDir(), nowMs = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    companion object {
        fun defaultRecordingsDir(): File = File(System.getProperty("user.home"), "SchoolRadio")

        /** مجلد بيانات التطبيق (سكريبت + جدولة + مستخدمون + مكتبة…) — بجانب التسجيلات الافتراضية. */
        fun dataDir(): File = defaultRecordingsDir()
    }
}
