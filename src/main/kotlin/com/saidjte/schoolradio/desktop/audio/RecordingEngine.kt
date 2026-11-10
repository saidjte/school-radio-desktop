package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.audio.RecordingStudio.CaptureFormat
import com.saidjte.schoolradio.desktop.audio.RecordingStudio.ClipLatch
import com.saidjte.schoolradio.desktop.audio.RecordingStudio.FrameMeter
import com.saidjte.schoolradio.desktop.audio.RecordingStudio.PcmWide
import com.saidjte.schoolradio.desktop.audio.RecordingStudio.Quality
import com.saidjte.schoolradio.desktop.audio.RecordingStudio.RecState
import com.saidjte.schoolradio.desktop.audio.RecordingStudio.StateMachine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine
import kotlin.concurrent.thread

/**
 * محرك التسجيل الاحترافي (v0.43.0) — التقاط متوازٍ من أجهزة متعددة
 * بجودة موحّدة مضمونة، على نمط MicLiveEngine: خيط واحد يملك جهازه
 * وكل شيء يُغلق في finally.
 *
 * العقد الجوهري: **الملف يخرج بالجودة المطلوبة دائمًا** — أيًا كانت
 * صيغة الالتقاط التي تفاوضها العتاد (أقل معدلًا أو أقل بتًا) تمرّ كل
 * كتلة على خطة التحويل نفسها: فكّ → طيّ أحادي → إعادة تشكيل → ترميز
 * بالعمق المطلوب → كتابة WAV قانونية. فلا ملف مكتوب بجودة عتاد
 * مختلفة عن الجودة المعلنة للمذيع أبدًا.
 *
 * الميزات على المحرك:
 * - أجهزة متعددة بالتوازي، لكل جهاز ملفه الخاص (التسجيل متعدد المسارات).
 * - إيقاف مؤقت واستئناف: خط الالتقاط يُوقَف (stop) ويُعاد (start) والملف
 *   نفسه يُكمل — المدة محسوبة من البايتات المكتوبة فتتجمد عند التوقف.
 * - سماع مراقبة اختياري: كل جهاز يدفعه بعد التحويل إلى خط إخراج مشترك
 *   محمي بمقفلة واحدة — بلا صدى مخفي وبلا سباق كتابة.
 * - مقياس لحظي لكل جهاز (RMS معياري) ومؤشر تشبع مقفول بعدّ أحداث.
 */
class RecordingEngine {

    /** ملف مكتمل: مساره واسم الجهاز ومدته وجودته المضمونة. */
    data class RecordedTake(val file: File, val deviceName: String, val durationMs: Long, val quality: Quality)

    private val _state = MutableStateFlow(RecState.IDLE)
    val state: StateFlow<RecState> = _state.asStateFlow()

    /** المدة الحية (أطول جهاز قيد الكتابة) — تتجمد أثناء الإيقاف المؤقت. */
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    /** المستوى المعياري 0..1 لكل جهاز بالترتيب — يُحدَّث مع كل كتلة. */
    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    /** عدد أحداث التشبع لكل جهاز. */
    private val _clipCounts = MutableStateFlow<List<Int>>(emptyList())
    val clipCounts: StateFlow<List<Int>> = _clipCounts.asStateFlow()

    /** هل مؤشر التشبع مضيء الآن لكل جهاز (قفل ثمانمئة ملي ثانية). */
    private val _clipLatched = MutableStateFlow<List<Boolean>>(emptyList())
    val clipLatched: StateFlow<List<Boolean>> = _clipLatched.asStateFlow()

    private val _quality = MutableStateFlow<Quality?>(null)
    val quality: StateFlow<Quality?> = _quality.asStateFlow()

    /** أسماء الأجهزة الفعلية العاملة — أقل من المطلوب إن فشل أحدهم مع رسالة سببه. */
    private val _deviceNames = MutableStateFlow<List<String>>(emptyList())
    val deviceNames: StateFlow<List<String>> = _deviceNames.asStateFlow()

    /** خطة التحويل المعلنة إن احتاج العتاد طريقًا غير المطلوب مباشرة. */
    private val _conversionNote = MutableStateFlow<String?>(null)
    val conversionNote: StateFlow<String?> = _conversionNote.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** الملفات المكتملة بعد الإنهاء — بوابة الإدراج في المونتاج والجدولة. */
    private val _finishedTakes = MutableStateFlow<List<RecordedTake>>(emptyList())
    val finishedTakes: StateFlow<List<RecordedTake>> = _finishedTakes.asStateFlow()

    /** اسم المستخدم والدور — يوقَّع بهما كل ملف عند الإتمام (عقد المُسجِّل). */
    var recorderName: String = "مستخدم"
    var recorderRole: String = ""

    /**
     * يُستدعى عند إنهاء جلسة بنجاح (ملف واحد على الأقل بقيمة مدة موجبة)
     * — المتصل يُحدّث مكتبة التسجيلات ويعرض الرسالة.
     */
    var onFinished: ((List<RecordedTake>) -> Unit)? = null

    // ---- الحالة الجارية ----
    private val machine = StateMachine()
    private val workers = mutableListOf<Thread>()
    private val latches = mutableListOf<ClipLatch>()

    @Volatile private var stopRequested = false
    @Volatile private var pauseRequested = false

    /** مخرج سماع المراقبة المشترك + مقفلته — يفتح متأخرًا عند الطلب. */
    private var monitorLine: SourceDataLine? = null
    private val monitorLock = Any()

    /** صيغة سماع المراقبة ثابتة: أحادي 16 بت على 48 كيلوهرتز (أوسع توافق عتاد). */
    private val monitorFormat = AudioFormat(48_000f, 16, 1, true, false)

    /** جودة الجلسة الجارية — تُقرأ من خيوط الالتقاط. */
    @Volatile private var sessionQuality: Quality = Quality()

    /** تفعيل سماع المراقبة — قبل البدء وأثناءه، وفشل فتح المخرج لا يُسقط التسجيل. */
    @Volatile var monitorWanted: Boolean = false
        private set

    fun setMonitor(on: Boolean) {
        monitorWanted = on
        if (!on) closeMonitor()
    }

    private fun closeMonitor() {
        synchronized(monitorLock) {
            try { monitorLine?.stop(); monitorLine?.close() } catch (_: Exception) { }
            monitorLine = null
        }
    }

    /** فكّ كتلة بايتات الالتقاط إلى عائم أحادي على معدل الالتقاط. */
    private fun decodeMono(
        bytes: ByteArray, n: Int, bits: Int, channels: Int,
        fBuf: FloatArray, mono: FloatArray
    ): Int {
        val samples = when (bits) {
            24 -> PcmWide.bytes24ToFloats(bytes, n, fBuf)
            32 -> PcmWide.bytes32ToFloats(bytes, n, fBuf)
            else -> {
                val shorts = ShortArray(n / 2)
                val c = Pcm16.bytesToShorts(bytes, n, shorts)
                PcmWide.shortsToFloats(shorts, c, fBuf)
            }
        }
        val frames = if (channels >= 2) samples / 2 else samples
        if (frames <= 0) return 0
        if (channels >= 2) {
            for (f in 0 until frames) mono[f] = (fBuf[2 * f] + fBuf[2 * f + 1]) / 2.0f
        } else {
            for (f in 0 until frames) mono[f] = fBuf[f]
        }
        return frames
    }

    /** ترميز أحادي عائم بالعمق المطلوب إلى بايتات PCM صغيرة الطرفية. */
    private fun encodeBits(mono: FloatArray, frames: Int, bits: Int, out: ByteArray): Int = when (bits) {
        24 -> PcmWide.floatsToBytes24(mono, frames, out)
        32 -> PcmWide.floatsToBytes32(mono, frames, out)
        else -> {
            val shorts = ShortArray(frames)
            PcmWide.floatsToShorts(mono, frames, shorts)
            Pcm16.shortsToBytes(shorts, frames, out)
            frames * 2
        }
    }

    /** دفعة كتلة إلى سماع المراقبة المشترك: عائم على معدل الجلسة → 48 كيلوهرتز 16 بت. */
    private fun feedMonitor(mono: FloatArray, frames: Int, sessionRate: Int) {
        if (!monitorWanted) return
        synchronized(monitorLock) {
            try {
                if (monitorLine == null) {
                    val info = DataLine.Info(SourceDataLine::class.java, monitorFormat)
                    if (!AudioSystem.isLineSupported(info)) {
                        monitorWanted = false
                        _error.value = "سماع المراقبة: لا يوجد مخرج متاح — التسجيل مستمر"
                        return
                    }
                    val line = AudioSystem.getSourceDataLine(monitorFormat)
                    val buf = monitorFormat.frameSize * 4096
                    line.open(monitorFormat, buf)
                    line.start()
                    monitorLine = line
                }
                val resampled = FloatArray(frames * 3 + 16)
                val rn = PcmMixer.resample(mono, frames, sessionRate, 48_000, resampled)
                if (rn <= 0) return
                val shorts = ShortArray(rn)
                PcmWide.floatsToShorts(resampled, rn, shorts)
                val bytes = ByteArray(rn * 2)
                Pcm16.shortsToBytes(shorts, rn, bytes)
                monitorLine?.write(bytes, 0, bytes.size)
            } catch (_: Exception) { }
        }
    }

    /**
     * بدء جلسة تسجيل: جهاز لكل ملف، والفشل على جهاز واحد لا يُسقط
     * البقية — رسالة سببه تظهر والأجهزة السليمة تكمل.
     * الملفات تُنشأ هنا باسم فريد لكل جهاز داخل المجلد المحدد.
     */
    fun start(
        devices: List<Mixer.Info?>,
        quality: Quality,
        dir: File,
        monitor: Boolean
    ): Boolean {
        if (machine.state != RecState.IDLE) return false
        if (!machine.start()) return false
        sessionQuality = quality.clamp()
        monitorWanted = monitor
        stopRequested = false
        pauseRequested = false
        _error.value = null
        _finishedTakes.value = emptyList()
        _elapsedMs.value = 0L
        _levels.value = List(devices.size) { 0f }
        _clipCounts.value = List(devices.size) { 0 }
        _clipLatched.value = List(devices.size) { false }
        latches.clear()
        workers.clear()
        _quality.value = sessionQuality
        _state.value = RecState.RECORDING

        val stamped = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val activeNames = mutableListOf<String>()

        devices.forEachIndexed { idx, mixerInfo ->
            val name = mixerInfo?.name?.trim()?.takeIf { it.isNotEmpty() }
                ?: AudioDevices.DEFAULT_INPUT_NAME
            val target = RecordingsScanner.newRecordingFile(
                dir, prefix = "تسجيل-احترافي-${idx + 1}", extension = "wav"
            )
            val latch = ClipLatch()
            latches.add(latch)
            val t = thread(name = "rec-$idx", isDaemon = true) {
                captureLoop(mixerInfo, target, name, latch, idx, stamped)
            }
            workers.add(t)
            activeNames.add(name)
        }

        _deviceNames.value = activeNames
        if (workers.isEmpty()) {
            machine.stop()
            _state.value = RecState.IDLE
            _error.value = "لا يوجد جهاز إدخال متاح للتسجيل"
            return false
        }
        return true
    }

    /** إيقاف مؤقت — خطوط الالتقاط تتوقف والملفات تبقى مفتوحة. */
    fun pause(): Boolean {
        if (!machine.pause()) return false
        pauseRequested = true
        _state.value = RecState.PAUSED
        return true
    }

    /** استئناف — الخطوط نفسها تُعاد والكتابة تُكمل في الملفات نفسها. */
    fun resume(): Boolean {
        if (!machine.resume()) return false
        pauseRequested = false
        _state.value = RecState.RECORDING
        return true
    }

    /** إنهاء الجلسة وحفظ الملفات — يعيد الملفات المكتملة الموقّعة. */
    fun stop(): List<RecordedTake> {
        if (!machine.stop()) return emptyList()
        stopRequested = true
        _state.value = RecState.IDLE
        closeMonitor()
        workers.forEach { it.join(3000) }
        workers.clear()
        val takes = _finishedTakes.value
        _levels.value = emptyList()
        _clipCounts.value = emptyList()
        _clipLatched.value = emptyList()
        latches.clear()
        return takes
    }

    /**
     * حلقة التقاط جهاز واحد — مالك خطه وكاتبه كليًا (بلا سباقات):
     * تفاوض الصيغة ← حلقة قراءة ← فكّ/طيّ/إعادة تشكيل/ترميز ← كتابة
     * وقياس ومراقبة ← وفي finally كل شيء يُغلق والملف يُوقَّع.
     */
    private fun captureLoop(
        mixerInfo: Mixer.Info?,
        target: File,
        deviceName: String,
        latch: ClipLatch,
        deviceIndex: Int,
        stamp: String
    ) {
        var line: TargetDataLine? = null
        var wav: WavFileWriter? = null
        try {
            val q = sessionQuality
            val requested = AudioFormat(q.rateHz.toFloat(), 16, 1, true, false)
            val l = AudioDevices.openTargetLine(mixerInfo, requested)
            val actual = l.format
            val capture = CaptureFormat(
                actual.sampleRate.toInt(),
                actual.sampleSizeInBits.takeIf { it in listOf(16, 24, 32) } ?: 16,
                actual.channels
            )
            val note = RecordingStudio.conversionLabel(capture, q)
            if (note != null) _conversionNote.value = note
            val frameSize = actual.frameSize.coerceAtLeast(1)
            val chunk = frameSize * 1024
            l.open(actual, chunk * 4)
            l.start()
            line = l

            val channels = actual.channels.coerceAtLeast(1)
            val fBuf = FloatArray(chunk / 2 + 64)
            val mono = FloatArray(chunk / 2 + 64)
            // أقصى نمو عند إعادة التشكيل لأعلى: نسبة المعدلات مفرّعة لأعلى + هامش
            val upRatio = ((q.rateHz + capture.rateHz - 1) / capture.rateHz).coerceAtLeast(1)
            var resampled = FloatArray((chunk / 2) * upRatio + 128)
            var out = ByteArray((chunk / 2) * upRatio * q.bytesPerSample + 128)
            val buf = ByteArray(chunk)

            wav = WavFileWriter(target, q.rateHz, 1, q.bits)
            val started = System.nanoTime()

            while (!stopRequested) {
                if (pauseRequested) {
                    try { line.stop() } catch (_: Exception) { }
                    // حلقة انتظار كثيفة القليل — تُقيّم الاستئناف والإنهاء معًا
                    while (pauseRequested && !stopRequested) Thread.sleep(40)
                    if (stopRequested) break
                    try { line.start() } catch (_: Exception) { }
                }
                val n = l.read(buf, 0, chunk)
                if (n <= 0) continue

                val frames = decodeMono(buf, n, capture.bits, channels, fBuf, mono)
                if (frames <= 0) continue

                val rn = if (RecordingStudio.needsResample(capture, q)) {
                    val cap = frames * upRatio + 64
                    if (resampled.size < cap) resampled = FloatArray(cap)
                    PcmMixer.resample(mono, frames, capture.rateHz, q.rateHz, resampled)
                } else {
                    System.arraycopy(mono, 0, resampled, 0, frames)
                    frames
                }
                if (rn <= 0) continue

                val meter = RecordingStudio.analyzeFrame(resampled, rn)
                val nowMs = (System.nanoTime() - started) / 1_000_000L
                latch.onFrame(meter.clipped, nowMs)
                publishMeter(deviceIndex, meter, latch, nowMs)

                val need = rn * q.bytesPerSample
                if (out.size < need) out = ByteArray(need)
                val written = encodeBits(resampled, rn, q.bits, out)
                if (written > 0) {
                    wav.write(out, 0, written)
                    val dur = wav.durationMs
                    publishElapsed(dur)
                }
                feedMonitor(resampled, rn, q.rateHz)
            }
        } catch (t: Throwable) {
            _error.value = "$deviceName: " + AudioDevices.describeInputFailure(t)
        } finally {
            try { wav?.close() } catch (_: Exception) { }
            try { line?.stop(); line?.close() } catch (_: Exception) { }
            val dur = try {
                WavHeader.read(target)?.let {
                    WavFileWriter.durationMsOf(it.dataBytes, it.sampleRate, it.channels, it.bitsPerSample)
                } ?: 0L
            } catch (_: Exception) { 0L }
            val ok = target.isFile && target.length() > 44L && dur > 0L
            if (ok) {
                val meta = RecordingMeta(
                    recorderName = recorderName.takeIf { it.isNotBlank() },
                    recorderRole = recorderRole.takeIf { it.isNotBlank() },
                    startedAtEpochMs = System.currentTimeMillis() - dur,
                    durationMs = dur
                )
                runCatching { meta.save(RecordingMeta.sidecarOf(target)) }
                appendTake(RecordedTake(target, deviceName, dur, sessionQuality))
            } else {
                runCatching { target.delete() }
                runCatching { RecordingMeta.sidecarOf(target).delete() }
                if (_error.value == null) {
                    _error.value = "$deviceName: لم يُكتب أي صوت — جرّب جهازًا آخر أو حدّث القائمة"
                }
            }
        }
    }

    private fun appendTake(take: RecordedTake) {
        synchronized(this) {
            _finishedTakes.value = _finishedTakes.value + take
        }
    }

    private fun publishMeter(index: Int, meter: FrameMeter, latch: ClipLatch, nowMs: Long) {
        val level = RecordingStudio.normalized(meter.rmsAmp).toFloat()
        val levels = _levels.value.toMutableList()
        if (index < levels.size) {
            levels[index] = level
            _levels.value = levels
        }
        val counts = _clipCounts.value.toMutableList()
        if (index < counts.size) {
            counts[index] = latch.count
            _clipCounts.value = counts
        }
        val latched = _clipLatched.value.toMutableList()
        if (index < latched.size) {
            latched[index] = latch.active(nowMs)
            _clipLatched.value = latched
        }
    }

    private fun publishElapsed(dur: Long) {
        if (dur > _elapsedMs.value) _elapsedMs.value = dur
    }
}
