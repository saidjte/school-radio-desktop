package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MicLiveEngine
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.RecordingItem
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.concurrent.thread

/** أقسام النافذة الواحدة: دمج الشاشات المتفرقة في نسخة الهاتف. */
enum class Tab(val label: String) {
    LIVE("البث المباشر"),
    MUSIC("الموسيقى"),
    RECORDINGS("التسجيلات")
}

data class Track(val file: File, val title: String, val durationMs: Long)

/** حالة التطبيق المشتركة بين الأقسام الثلاثة. */
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

    init {
        player.onEnded = { next(auto = true) }
        _recordingsDir.value.mkdirs()
        refreshRecordings()
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
    }
}
