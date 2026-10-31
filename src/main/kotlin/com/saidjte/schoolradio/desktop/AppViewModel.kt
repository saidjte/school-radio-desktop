package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MicLiveEngine
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.RecordingItem
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScheduleStore
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStore
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.StudioLogic
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
 * صفحات التطبيق الخمس — فلسفة السلاسة:
 * «البث المباشر» وحدها هي الصفحة المدمجة، ومحتواها فقط ما يلزم المذيع على الهواء.
 * أما الإدارة (الجدولة، تحرير السكريبت، الموسيقى، التسجيلات) فلِكلٍّ منها صفحة مستقلة
 * كي لا تكتظ صفحة البث وينقلب المطلوب إلى عكسه.
 */
enum class Tab(val label: String) {
    LIVE("البث المباشر"),
    SCHEDULE("جدولة الفقرات"),
    SCRIPT("سكريبت المذيع"),
    MUSIC("الموسيقى"),
    RECORDINGS("التسجيلات")
}

data class Track(val file: File, val title: String, val durationMs: Long)

/** حالة التطبيق المشتركة بين الصفحات الخمس: البث، الجدولة، السكريبت، الموسيقى، التسجيلات. */
class AppViewModel {

    private val _tab = MutableStateFlow(Tab.LIVE)
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

    // ================= سكريبت المذيع =================

    private val scriptStore = ScriptStore(File(dataDir(), "script.json"))
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

    private val scheduleStore = ScheduleStore(File(dataDir(), "schedule.json"))
    private val _schedule = MutableStateFlow(scheduleStore.items())
    val schedule: StateFlow<List<ScheduleItem>> = _schedule.asStateFlow()

    private val _episodeStartMinutes = MutableStateFlow(scheduleStore.episodeStartMinutes())
    val episodeStartMinutes: StateFlow<Int?> = _episodeStartMinutes.asStateFlow()

    fun setEpisodeStart(minutes: Int?) {
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
        scheduleStore.update(id, title, kind, plannedSeconds, mediaPath, note, scriptSectionId)
        _schedule.value = scheduleStore.items()
        clampRun()
    }

    fun deleteScheduleItem(id: Long) {
        scheduleStore.delete(id)
        _schedule.value = scheduleStore.items()
        clampRun()
    }

    fun moveScheduleItem(id: Long, delta: Int) {
        scheduleStore.move(id, delta)
        _schedule.value = scheduleStore.items()
        clampRun()
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

    init {
        player.onEnded = { next(auto = true) }
        _recordingsDir.value.mkdirs()
        refreshRecordings()
        if (_script.value.isNotEmpty()) {
            _selectedSectionId.value = _script.value.first().id
        }
        runScope.launch {
            while (true) {
                delay(1000)
                _clockText.value = StudioLogic.clockText(System.currentTimeMillis())
            }
        }
    }

    fun selectTab(t: Tab) {
        _tab.value = t
    }

    // ================= الموسيقى =================

    fun addFiles(files: List<File>) {
        if (files.isEmpty()) return
        val existing = _playlist.value.map { it.file }.toSet()
        val newTracks = files
            .filter { it.isFile && it.extension.lowercase() in RecordingsScanner.AUDIO_EXTENSIONS && it !in existing }
            .map { Track(it, it.nameWithoutExtension, durationOf(it)) }
        if (newTracks.isEmpty()) return
        val wasIdle = _playlist.value.isEmpty() || _currentIndex.value < 0
        _playlist.value = _playlist.value + newTracks
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
    }

    fun playAt(index: Int) {
        val list = _playlist.value
        if (index < 0 || index >= list.size) return
        _currentIndex.value = index
        player.play(list[index].file)
    }

    fun togglePlay() {
        if (_currentIndex.value < 0 && _playlist.value.isNotEmpty()) {
            playAt(0)
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
        val n = _currentIndex.value + 1
        when {
            n < list.size -> playAt(n)
            auto -> player.stop()
            else -> playAt(0)
        }
    }

    fun previous() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val p = if (_currentIndex.value <= 0) list.size - 1 else _currentIndex.value - 1
        playAt(p)
    }

    // ================= التسجيلات =================

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
        val clean = newName.trim()
        if (clean.isEmpty() || clean == item.displayName) return
        val wasCurrent = player.currentFile.value == item.file
        if (wasCurrent) {
            player.stop()
            player.awaitIdle(2000)
        }
        val target = File(item.file.parentFile, "$clean.${item.file.extension}")
        if (item.file.renameTo(target)) {
            refreshRecordings()
            _playlist.value = _playlist.value.map {
                if (it.file == item.file) it.copy(file = target, title = clean) else it
            }
        }
    }

    fun deleteRecording(item: RecordingItem) {
        val wasCurrent = player.currentFile.value == item.file
        if (wasCurrent) {
            player.stop()
            player.awaitIdle(2000)
        }
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

    fun durationOf(f: File): Long = when (f.extension.lowercase()) {
        "wav" -> WavHeader.read(f)?.let {
            WavFileWriter.durationMsOf(it.dataBytes, it.sampleRate, it.channels, it.bitsPerSample)
        }
        "mp3" -> Mp3Info.analyze(f)?.durationMs
        else -> null
    } ?: 0L

    companion object {
        fun defaultRecordingsDir(): File = File(System.getProperty("user.home"), "SchoolRadio")

        /** مجلد بيانات التطبيق (سكريبت + جدولة) — بجانب التسجيلات الافتراضية. */
        fun dataDir(): File = defaultRecordingsDir()
    }
}
