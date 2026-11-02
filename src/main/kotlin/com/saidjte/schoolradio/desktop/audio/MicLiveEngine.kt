package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.data.AecState
import com.saidjte.schoolradio.desktop.data.VoiceFxState
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
 * - مانعا صدى متسلسلان (صدى الموسيقى + صدى سماع المراقبة) — تكافؤ مصدر
 *   VOICE_COMMUNICATION في نسخة الهاتف: مرجع الإلغاء = كل ما يخرج من السماعة.
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

    /** مؤشر بوابة الضجيج: صحيح = المقصلة صامتة (يعمل عند تفعيل البوابة). */
    private val _gateClosed = MutableStateFlow(false)
    val gateClosed: StateFlow<Boolean> = _gateClosed.asStateFlow()

    /** مؤشر إلغاء الصدى: هل مسار الصدى مقفل والطرح فعلي جارٍ؟ */
    private val _aecLocked = MutableStateFlow(false)
    val aecLocked: StateFlow<Boolean> = _aecLocked.asStateFlow()

    /** التأخير المقدّر لمسار الصدى بالمللي ثانية (للعرض الحي). */
    private val _aecDelayMs = MutableStateFlow(0f)
    val aecDelayMs: StateFlow<Float> = _aecDelayMs.asStateFlow()

    /** تحسّن الإرجاع (ERLE) بالديسيبل — مؤشر قوة إلغاء الصدى الحي. */
    private val _aecErleDb = MutableStateFlow(0f)
    val aecErleDb: StateFlow<Float> = _aecErleDb.asStateFlow()

    /** مؤشر مانع صدى المراقبة: هل مسار صوت المذيع العائد من السماعة مقفل؟ */
    private val _aecMonitorLocked = MutableStateFlow(false)
    val aecMonitorLocked: StateFlow<Boolean> = _aecMonitorLocked.asStateFlow()

    /** تحسّن إرجاع مانع صدى المراقبة بالديسيبل. */
    private val _aecMonitorErleDb = MutableStateFlow(0f)
    val aecMonitorErleDb: StateFlow<Float> = _aecMonitorErleDb.asStateFlow()

    /** مؤشر الخفض التلقائي: هل الموسيقى منخفضة الآن لحديث المذيع؟ */
    private val _duckActive = MutableStateFlow(false)
    val duckActive: StateFlow<Boolean> = _duckActive.asStateFlow()

    @Volatile private var running = false
    @Volatile private var monitorWanted = false
    @Volatile private var recordWanted = false
    @Volatile private var recordTarget: File? = null

    /**
     * يُستدعى مرة واحدة لحظة إغلاق ملف تسجيل اكتمل (بعد كتابة الترويسة النهائية)
     * — بوابة توقيع المُسجِّل: المتصل يكتب الملف الجانبي بالاسم والدور هنا.
     */
    var onRecordingSaved: ((File, Long) -> Unit)? = null

    /** مخرج السماع المراقب (السماعة التي يختارها المذيع). */
    @Volatile private var outMixer: Mixer.Info? = null
    @Volatile private var outMixerDirty = false
    private var worker: Thread? = null

    // ================= وحدة المزج: قناة الميكروفون =================

    /** معالج صوت المذيع (بوابة/AGC/ضاغط/منحنى/محدّد) — معاملاته حيةّ. */
    internal val voice = VoiceProcessor()

    /** مانع صدى الميكروفون (NLMS) — يطرح صدى الموسيقى قبل بقية السلسلة. */
    internal val aec = EchoCanceller()

    /**
     * مانع صدى سماع المراقبة — مكافئ مانع النظام في الهاتف: يطرح صوت المذيع العائد
     * من سماعة المراقبة من الالتقاط، فيعيش الميكروفون قرب مكبر الصوت بلا صدى كما
     * في النسخة الهاتفية. بلا كشف حديث مزدوج لأن مرجعه نسخة مؤخّرة من الصوت نفسه
     * — الاشتباه فيه بالضرورة يشلّ التعلّم والكبح معًا.
     */
    internal val aecMonitor = EchoCanceller().apply { doubleTalkGuard = false }

    /** قائد الخفض التلقائي: يقود ناقل DuckBus كل كتلة. */
    private val ducker = Ducker()

    @Volatile private var micFader = 1.0f
    @Volatile private var masterFader = 1.0f
    @Volatile private var micMuted = false

    /** فيدر قناة الميكروفون (0..1.5) — يسري أثناء البث فورًا. */
    fun setMicFader(v: Float) { micFader = MixerLogicFader.clean(v) }

    /** فيدر الماستر (0..1.5) — يسري أثناء البث فورًا. */
    fun setMasterFader(v: Float) { masterFader = MixerLogicFader.clean(v) }

    /** كتم جلسي: لا يُكتب في المحفوظ، وإلغاؤه يعيد المستوى نفسه. */
    fun setMicMuted(m: Boolean) { micMuted = m }

    /** تحديث معاملات معالج المذيع دفعة واحدة. */
    fun setVoiceFx(state: VoiceFxState) {
        voice.setParams(
            VoiceProcessor.Params(
                enabled = state.enabled,
                hpfOn = state.hpfOn,
                gateOn = state.gateOn,
                gateThresholdDb = state.gateThresholdDb,
                gateReleaseMs = state.gateReleaseMs,
                agcOn = state.agcOn,
                agcTargetDb = state.agcTargetDb,
                agcMaxBoostDb = state.agcMaxBoostDb,
                compOn = state.compOn,
                compThresholdDb = state.compThresholdDb,
                compRatio = state.compRatio,
                compMakeupDb = state.compMakeupDb,
                bassDb = state.bassDb,
                presenceDb = state.presenceDb,
                airDb = state.airDb
            )
        )
    }

    /** تفعيل/تعطيل إلغاء الصدى (يعمل قبل البدء وأثناءه) — للمانعين معًا كالنسخة الهاتفية. */
    fun setAec(state: AecState) {
        aec.enabled = state.enabled
        aecMonitor.enabled = state.enabled
        if (!state.enabled) {
            _aecLocked.value = false
            _aecDelayMs.value = 0f
            _aecErleDb.value = 0f
            _aecMonitorLocked.value = false
            _aecMonitorErleDb.value = 0f
        }
    }

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

            // قناة الميكروفون في وحدة المزج: بناء معاملات المعالج حسب الصيغة المتفاوض عليها
            voice.configure(actual.sampleRate.toInt(), actual.channels)
            aec.configure(actual.sampleRate.toInt(), actual.channels)
            aec.reset()
            aecMonitor.configure(actual.sampleRate.toInt(), actual.channels)
            aecMonitor.reset()
            ducker.reset()
            val shorts = ShortArray(chunk / 2)

            val buf = ByteArray(chunk)
            val started = System.nanoTime()
            while (running) {
                val n = l.read(buf, 0, chunk)
                if (n <= 0) continue
                _liveMs.value = (System.nanoTime() - started) / 1_000_000L

                // السلسلة الإذاعية الكاملة: إلغاء الصدى أولًا (يطرح صدى الموسيقى قبل أي
                // معالجة لاحية) ثم معالج المذيع وكسب القناة — المقياس والسماع المراقب
                // والتسجيل كلها على الصوت «على الهواء»
                val sampleCount = Pcm16.bytesToShorts(buf, n, shorts)
                // السلسلة كنسخة الهاتف: طرح صدى الموسيقى ثم صدى صوت المذيع العائد
                // من سماعة المراقبة — مانعان متتاليان يغطيان كل ما تسمعه الغرفة
                aec.process(shorts, sampleCount, MusicFarEnd.tap)
                aecMonitor.process(shorts, sampleCount, MonitorFarEnd.tap)
                _aecLocked.value = aec.locked
                _aecDelayMs.value = aec.delayMs
                _aecErleDb.value = aec.erleDb
                _aecMonitorLocked.value = aecMonitor.locked
                _aecMonitorErleDb.value = aecMonitor.erleDb
                val gain = if (micMuted) 0f else micFader * masterFader
                val level = voice.process(shorts, sampleCount, gain)
                _level.value = level
                _gateClosed.value = voice.gateClosed && voice.params().gateOn

                // قيادة الخفض التلقائي: كلام = بوابة مفتوحة، أو مستوى فوق العتبة حين البوابة معطلة
                val p = voice.params()
                val voiceActive = if (p.gateOn) !voice.gateClosed else level >= DuckBus.thresholdLevel
                val blockMs = sampleCount * 1000.0 / (actual.sampleRate * actual.channels)
                val duckGain = ducker.process(
                    blockMs, running, voiceActive,
                    DuckBus.duckDb.toDouble(), DuckBus.holdMs.toDouble()
                )
                DuckBus.musicGain = if (DuckBus.enabled) duckGain else 1f
                DuckBus.active = DuckBus.enabled && ducker.active
                _duckActive.value = DuckBus.active
                Pcm16.shortsToBytes(shorts, sampleCount, buf)

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
                        // مرجع مانع صدى المراقبة: ما يُكتب فعليًا في السماعة يُدفع كمرجع
                        // قبل كتابته — فيُطرح عائده من الالتقاط كالمانع النظامي في الهاتف
                        pushMonitorFarEnd(shorts, sampleCount, actual.channels)
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
                    val target = recordTarget
                    val durMs = wav.durationMs
                    wav.close()
                    wav = null
                    _recording.value = false
                    // التسجيل اكتمل كتابةً فعلية — الآن فقط يُوقَّع
                    val cb = onRecordingSaved
                    if (target != null && cb != null) cb(target, durMs)
                }
                if (wav != null) {
                    wav.write(buf, 0, n)
                    _recordMs.value = wav.durationMs
                }
            }
        } catch (t: Throwable) {
            // IOException الآتية من openTargetLine رسالتها عربية مترجمة مسبقًا (ومعها السبب الأصلي):
            // لا نمررها على الترجمة مرة ثانية كي لا يتراكم الغلاف «تعذّر فتح الميكروفون: تعذّر فتح…»
            _error.value = if (t is java.io.IOException && t.cause != null) t.message
            else AudioDevices.describeInputFailure(t)
        } finally {
            try { wav?.close() } catch (_: Exception) { }
            try { out?.stop(); out?.close() } catch (_: Exception) { }
            try { line?.stop(); line?.close() } catch (_: Exception) { }
            _recording.value = false
            _level.value = 0f
            _gateClosed.value = false
            _aecLocked.value = false
            _aecDelayMs.value = 0f
            _aecErleDb.value = 0f
            _aecMonitorLocked.value = false
            _aecMonitorErleDb.value = 0f
            _duckActive.value = false
            DuckBus.musicGain = 1f
            DuckBus.active = false
            _state.value = MicState.IDLE
            running = false
            worker = null
        }
    }
}

/** تنظيف فيدر داخل الحدود — بوابة واحدة لكل القنوات. */
internal object MixerLogicFader {
    fun clean(v: Float): Float = v.coerceIn(0f, 1.5f)
}

/** مخزن أحادي لمرجع مانع صدى المراقبة داخل المحرك. */
private var monitorMonoBuf = FloatArray(4096)

/**
 * دفع ما يُكتب في سماعة المراقبة كمرجع لمانع صدى المراقبة (أحادي متوسط القنوات)
 * — البديل البرمحي لمصدر VOICE_COMMUNICATION في نسخة الهاتف: مانع النظام هناك
 * يرى خرج السماعة كاملًا، وهنا نراه بيدنا ونقدمه للمانع الثاني.
 */
private fun pushMonitorFarEnd(shorts: ShortArray, count: Int, channels: Int) {
    val frames = count / channels
    if (frames <= 0) return
    if (monitorMonoBuf.size < frames) monitorMonoBuf = FloatArray(frames)
    if (channels == 1) {
        for (f in 0 until frames) monitorMonoBuf[f] = shorts[f] / 32768.0f
    } else {
        var j = 0
        for (f in 0 until frames) {
            monitorMonoBuf[f] = (shorts[j] + shorts[j + 1]) / 65536.0f
            j += 2
        }
    }
    MonitorFarEnd.tap.push(monitorMonoBuf, frames)
}
