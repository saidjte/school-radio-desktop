package com.saidjte.schoolradio.desktop.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.thread

enum class MicState { IDLE, LIVE }

/**
 * محرّك البث المباشر للميكروفون (نسخة الحاسوب):
 * - التقاط من جهاز الإدخال المختار عبر TargetDataLine.
 * - مقياس مستوى حي (RMS).
 * - سماع مراقبة اختياري عبر SourceDataLine (نفس مسار الحلقة الآمن في نسخة الهاتف).
 * - تسجيل WAV متزامن مع البث (كتابة متدفقة).
 * الخيط الواحد يملك كل شيء: لا سباقات، وإغلاق آمن دائمًا في finally.
 */
class MicLiveEngine {

    private val _state = MutableStateFlow(MicState.IDLE)
    val state: StateFlow<MicState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring.asStateFlow()

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _recordFileName = MutableStateFlow<String?>(null)
    val recordFileName: StateFlow<String?> = _recordFileName.asStateFlow()

    private val _recordMs = MutableStateFlow(0L)
    val recordMs: StateFlow<Long> = _recordMs.asStateFlow()

    private val _liveMs = MutableStateFlow(0L)
    val liveMs: StateFlow<Long> = _liveMs.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _activeFormat = MutableStateFlow<String?>(null)
    val activeFormat: StateFlow<String?> = _activeFormat.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    @Volatile private var running = false
    @Volatile private var monitorWanted = false
    @Volatile private var recordWanted = false
    @Volatile private var recordTarget: File? = null

    /** مخرج السماع المراقب (السماعة التي يختارها المذيع). */
    @Volatile private var outMixer: Mixer.Info? = null
    @Volatile private var outMixerDirty = false
    private var worker: Thread? = null

    /** تفعيل/تعطيل السماع المباشر (يعمل قبل البدء وأثناءه). */
    fun setMonitoring(on: Boolean) {
        monitorWanted = on
        if (_state.value == MicState.LIVE) _monitoring.value = on
    }

    /** تغيير مخرج السماع المراقب قبل البدء وأثناءه (إعادة فتح حيّة دون قطع البث). */
    fun setMonitorOutput(mixerInfo: Mixer.Info?) {
        outMixer = mixerInfo
        outMixerDirty = true
    }

    fun start(mixerInfo: Mixer.Info?, outMixerInfo: Mixer.Info? = null) {
        synchronized(this) {
            if (running) return
            running = true
        }
        outMixer = outMixerInfo
        outMixerDirty = false
        _error.value = null
        _liveMs.value = 0L
        _recordMs.value = 0L
        _recordFileName.value = null
        _state.value = MicState.LIVE
        _monitoring.value = monitorWanted
        worker = thread(name = "mic-live", isDaemon = true) { run(mixerInfo) }
    }

    fun stop() {
        running = false
        recordWanted = false
    }

    /** بدء تسجيل ملف WAV محدد أثناء البث المباشر. */
    fun startRecording(targetFile: File): Boolean {
        return try {
            targetFile.parentFile?.mkdirs()
            recordTarget = targetFile
            recordWanted = true
            _recordFileName.value = targetFile.name
            true
        } catch (t: Throwable) {
            _error.value = t.message
            false
        }
    }

    fun stopRecording() {
        recordWanted = false
    }

    private fun run(mixerInfo: Mixer.Info?) {
        var line: javax.sound.sampled.TargetDataLine? = null
        var out: SourceDataLine? = null
        var wav: WavFileWriter? = null
        try {
            val format = AudioDevices.pickFormat(mixerInfo)
            val l = AudioDevices.openTargetLine(mixerInfo, format)
            // الصيغة الفعلية بعد التفاوض (قد يرجع خط احتياطي بصيغة مختلفة)
            val actual = l.format
            val frameSize = actual.frameSize.coerceAtLeast(1)
            val chunk = frameSize * 1024
            l.open(actual, chunk * 4)
            l.start()
            line = l
            _deviceName.value = mixerInfo?.name?.trim()?.ifEmpty { null } ?: "الميكروفون الافتراضي"
            _activeFormat.value = "${actual.sampleRate.toInt()} هرتز · ${actual.channels} قناة · 16 بت"

            val buf = ByteArray(chunk)
            val started = System.nanoTime()
            while (running) {
                val n = l.read(buf, 0, chunk)
                if (n <= 0) continue
                _liveMs.value = (System.nanoTime() - started) / 1_000_000L

                val rms = LevelMeter.rms(buf, n)
                _level.value = LevelMeter.normalized(rms).toFloat()

                // إعادة فتح السماع المراقب على المخرج الجديد دون قطع البث
                if (outMixerDirty) {
                    outMixerDirty = false
                    try { out?.stop(); out?.close() } catch (_: Exception) { }
                    out = null
                }
                if (monitorWanted) {
                    if (out == null) {
                        // فشل السماع المراقب لا يوقف البث المباشر أبدًا
                        out = try {
                            val o = AudioDevices.openSourceLine(outMixer, actual)
                            o.open(actual, chunk * 4)
                            o.start()
                            o
                        } catch (t: Throwable) {
                            monitorWanted = false
                            _monitoring.value = false
                            _error.value = "السماع المراقب: " +
                                    AudioDevices.describeOutputFailure(t)
                            null
                        }
                    }
                    out?.let { o ->
                        try { o.write(buf, 0, n) } catch (_: Exception) { }
                    }
                }

                if (recordWanted && wav == null) {
                    val target = recordTarget
                    if (target != null) {
                        wav = WavFileWriter(target, actual.sampleRate.toInt(), actual.channels)
                        _recording.value = true
                    }
                }
                if (!recordWanted && wav != null) {
                    wav.close()
                    wav = null
                    _recording.value = false
                }
                if (wav != null) {
                    wav.write(buf, 0, n)
                    _recordMs.value = wav.durationMs
                }
            }
        } catch (t: Throwable) {
            _error.value = AudioDevices.describeInputFailure(t)
        } finally {
            try { wav?.close() } catch (_: Exception) { }
            try { out?.stop(); out?.close() } catch (_: Exception) { }
            try { line?.stop(); line?.close() } catch (_: Exception) { }
            _recording.value = false
            _level.value = 0f
            _state.value = MicState.IDLE
            running = false
            worker = null
        }
    }
}
