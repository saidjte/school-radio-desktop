package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.DuckBus
import com.saidjte.schoolradio.desktop.audio.MicLiveEngine
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS
import com.saidjte.schoolradio.desktop.audio.RecordingItem
import com.saidjte.schoolradio.desktop.audio.RecordingMeta
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.StreamProbe
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.AecState
import com.saidjte.schoolradio.desktop.data.Backup
import com.saidjte.schoolradio.desktop.data.DevicesJson
import com.saidjte.schoolradio.desktop.data.ExecutionsStore
import com.saidjte.schoolradio.desktop.data.FireStatus
import com.saidjte.schoolradio.desktop.data.LibraryEntry
import com.saidjte.schoolradio.desktop.data.LibraryMath
import com.saidjte.schoolradio.desktop.data.MixerLogic
import com.saidjte.schoolradio.desktop.data.MixerState
import com.saidjte.schoolradio.desktop.data.MusicEqState
import com.saidjte.schoolradio.desktop.data.DuckState
import com.saidjte.schoolradio.desktop.data.MusicLibraryStore
import com.saidjte.schoolradio.desktop.data.NamedPlaylist
import com.saidjte.schoolradio.desktop.data.PlaylistMath
import com.saidjte.schoolradio.desktop.data.PlaylistStore
import com.saidjte.schoolradio.desktop.data.ReadingJson
import com.saidjte.schoolradio.desktop.data.RepeatMode
import com.saidjte.schoolradio.desktop.data.Role
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.StudioLayout
import com.saidjte.schoolradio.desktop.data.StudioLayoutLogic
import com.saidjte.schoolradio.desktop.data.ScheduleStore
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStore
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.StudioLogic
import com.saidjte.schoolradio.desktop.data.User
import com.saidjte.schoolradio.desktop.data.UsersStore
import com.saidjte.schoolradio.desktop.data.VoiceFxState
import com.saidjte.schoolradio.desktop.data.WeeklyEvent
import com.saidjte.schoolradio.desktop.data.WeeklyMath
import com.saidjte.schoolradio.desktop.data.WeeklyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
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
    MIXER("وحدة المزج"),
    RECORDINGS("التسجيلات")
}

data class Track(val file: File, val title: String, val durationMs: Long, val category: String? = null)

/** حالة التطبيق المشتركة بين الصفحات: البث، الجدولة، السكريبت، الموسيقى، التسجيلات، المستخدمون. */
class AppViewModel {

    private val _tab = MutableStateFlow(Tab.STUDIO)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    val mic = MicLiveEngine()
    val player = MusicPlayerEngine()

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
        val s = scriptStore.add(title, body)
        _script.value = scriptStore.sections()
        _selectedSectionId.value = s.id
    }

    fun updateSection(id: Long, title: String, body: String) {
        scriptStore.update(id, title, body)
        _script.value = scriptStore.sections()
    }

    fun deleteSection(id: Long) {
        scriptStore.delete(id)
        _script.value = scriptStore.sections()
        if (_selectedSectionId.value == id) {
            _selectedSectionId.value = _script.value.firstOrNull()?.id
        }
    }

    fun moveSection(id: Long, delta: Int) {
        scriptStore.move(id, delta)
        _script.value = scriptStore.sections()
    }

    fun selectSection(id: Long) {
        _selectedSectionId.value = id
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
    }

    val selectedSection: ScriptSection?
        get() = _script.value.firstOrNull { it.id == _selectedSectionId.value }
            ?: _script.value.firstOrNull()

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
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun beginItem() {
        itemStartedAtNanos = System.nanoTime()
        _runElapsedMs.value = 0L
        followScriptForCurrent()
        tickerJob?.cancel()
        tickerJob = runScope.launch {
            while (true) {
                delay(200)
                _runElapsedMs.value = (System.nanoTime() - itemStartedAtNanos) / 1_000_000L
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

    /** تشغيل الملف المرتبط بفقرة الجدولة (موسيقى/تسجيل). */
    fun runPlayMedia(item: ScheduleItem) {
        val p = item.mediaPath ?: return
        val f = File(p)
        if (f.isFile) player.play(f)
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

    /** نقل مسار داخل القائمة الجارية مع الحفاظ على تشغيل المسار الحالي. */
    fun moveTrack(index: Int, delta: Int) {
        val list = _playlist.value
        val moved = ScheduleMath.moved(list, index, delta)
        if (moved === list) return
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
        player.play(list[index].file)
    }

    /** إزالة مقطع واحد من القائمة الجارية — إزالة الجاري يوقف التشغيل. */
    fun removeTrackAt(index: Int) {
        val list = _playlist.value
        if (index < 0 || index >= list.size) return
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
        player.play(f)
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
        runScope.launch {
            while (true) {
                delay(1000)
                _clockText.value = StudioLogic.clockText(System.currentTimeMillis())
            }
        }
        // نبضة الجدولة الأسبوعية: كل 20 ثانية
        runScope.launch {
            while (true) {
                delay(20_000)
                tickWeekly()
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
    }

    companion object {
        fun defaultRecordingsDir(): File = File(System.getProperty("user.home"), "SchoolRadio")

        /** مجلد بيانات التطبيق (سكريبت + جدولة + مستخدمون + مكتبة…) — بجانب التسجيلات الافتراضية. */
        fun dataDir(): File = defaultRecordingsDir()
    }
}
