package com.saidjte.schoolradio.desktop.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * ناقل تسجيل مزيج الحصة (v0.31.0 «تسجيل مزيج الحصة كاملًا — مايك + موسيقى
 * في ملف واحد»): يلتقط كل ما يخرج على الهواء من مصادره الثلاثة عند بابه
 * الخاص — موسيقى ما بعد قناة المزج (معادل/فيدر/خفض) ومايك ما بعد السلسلة
 * (مانعا الصدى/معالج المذيع/الكسب) وجينلات الجدار بمستواها — ويحوّلها
 * دفعةً إلى صيغة التسجيل الموحدة (44.1kHz ستيريو 16-bit) ثم يجمعها في ملف
 * WAV واحد متدفق.
 *
 * كل مصدر يكتب على خيطه الحقيقي (خيط التشغيل/خيط الالتقاط/خيط الجينل)
 * بلا أي انتظار: التحويل والطابور فقط، والخيط الكاتب يفرّغ الطابور
 * في الملف — فلا يُبطئ التسجيل البث أبدًا ولا يعلّق أي قارئ صوت.
 * المصادر الحقيقية إذًا في إيقاع ساعة الحائط (كل خط يُكتب لحظةً بلحظة)
 * والجمع يلتزم ترتيب الوصول — هذا نفس ترتيب الخلط في لوحة بث حقيقية.
 */
object MixBus {

    /** معدل التسجيل الموحد — جودة أرشيف سليمة وحجم معقول (≈10MB للدقيقة). */
    const val RATE = 44100
    const val CHANNELS = 2

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** مستوى المزيج المكتوب فعليًا (RMS منمّط 0..1) — بوصلة المذيع أثناء التسجيل. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** الملف الجاري كتابته — null عند عدم التسجيل. */
    var targetFile: File? = null
        private set

    private val active = AtomicBoolean(false)
    private val lock = Any()
    private val pending = ArrayDeque<ShortArray>()
    private var pendingFrames = 0L
    private var writer: Thread? = null
    private var wav: WavFileWriter? = null

    /** رسالة خطأ بدء/كتابة تُعرض في بطاقة التسجيل. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * بدء تسجيل مزيج إلى ملف محدد. يفشل بأمان إن كان تسجيل جارٍ أو تعذّر
     * فتح الملف — ولا يرمي أبدًا.
     */
    fun start(target: File): Boolean {
        if (!active.compareAndSet(false, true)) return false
        _error.value = null
        val w = try {
            target.parentFile?.mkdirs()
            WavFileWriter(target, RATE, CHANNELS)
        } catch (t: Throwable) {
            active.set(false)
            _error.value = "تعذّر إنشاء ملف التسجيل: ${t.message ?: "سبب غير معروف"}"
            return false
        }
        synchronized(lock) {
            pending.clear()
            pendingFrames = 0L
        }
        targetFile = target
        wav = w
        _durationMs.value = 0L
        _level.value = 0f
        _recording.value = true
        writer = thread(name = "mix-recorder", isDaemon = true) { writeLoop(w) }
        return true
    }

    /**
     * إيقاف التسجيل وإفراغ الطابور في الملف وإغلاقه. يعيد المدة النهائية،
     * أو 0 إن لم يكن هناك تسجيل. آمن للاستدعاء المتكرر.
     */
    fun stop(): Long {
        if (!active.getAndSet(false)) return 0L
        val w = writer
        w?.join(5000)
        writer = null
        val f = wav
        wav = null
        val dur = try {
            f?.durationMs ?: 0L
        } finally {
            try { f?.close() } catch (_: Exception) { }
        }
        _recording.value = false
        _level.value = 0f
        _durationMs.value = dur
        return dur
    }

    // ================= تغذية المصادر — بلا انتظار على أي خيط =================

    /** تغذية قناة الموسيقى (عينات ما بعد قناة المزج — ما يُسمع فعلاً). */
    fun feedMusic(shorts: ShortArray, count: Int, sampleRate: Int, channels: Int) {
        feedShorts(shorts, count, sampleRate, channels)
    }

    /** تغذية صوت جينل (بايتات ما بعد مستوى الجينل البرمحي). */
    fun feedJingle(bytes: ByteArray, off: Int, len: Int, sampleRate: Int, channels: Int) {
        feedBytes(bytes, off, len, sampleRate, channels)
    }

    /** تغذية الميكروفون (بايتات ما بعد السلسلة الإذاعية الكاملة — على الهواء). */
    fun feedMic(bytes: ByteArray, off: Int, len: Int, sampleRate: Int, channels: Int) {
        feedBytes(bytes, off, len, sampleRate, channels)
    }

    private fun feedBytes(bytes: ByteArray, off: Int, len: Int, sampleRate: Int, channels: Int) {
        if (!active.get() || len < 2) return
        val src: ShortArray
        if (off <= 0) {
            val cap = len / 2
            src = ShortArray(cap)
            val n = Pcm16.bytesToShorts(bytes, cap * 2, src)
            feedShorts(src, n, sampleRate, channels)
        } else {
            // مجرى بإزاحة: نسخة المقطع المطلوب فقط — المسار النادر آمن ومكتم
            val seg = ByteArray(len)
            System.arraycopy(bytes, off, seg, 0, len)
            val cap = len / 2
            val s2 = ShortArray(cap)
            val n = Pcm16.bytesToShorts(seg, cap * 2, s2)
            feedShorts(s2, n, sampleRate, channels)
        }
    }

    private fun feedShorts(src: ShortArray, count: Int, sampleRate: Int, channels: Int) {
        if (!active.get() || count <= 0 || channels <= 0) return
        val canonical = toCanonical(src, count, sampleRate, channels) ?: return
        synchronized(lock) {
            pending.addLast(canonical)
            pendingFrames += canonical.size / CHANNELS
        }
    }

    /**
     * التحويل إلى الصيغة الموحدة: ستيريو 44.1kHz 16-bit — مطابقة المعدل
     * بإعادة تشكيل خطي لكل قناة، وطيّ القنوات الزائدة أو تكرار الأحادي.
     * يعيد null حين لا شيء يُكتب (طول معدوم بعد التحويل).
     */
    private fun toCanonical(src: ShortArray, count: Int, sampleRate: Int, channels: Int): ShortArray? {
        val rate = if (sampleRate > 0) sampleRate else RATE
        val ch = channels.coerceIn(1, 2)
        var stereo = PcmMixer.toStereo(src, count, ch)
        if (rate != RATE) {
            val frames = stereo.size / 2
            stereo = PcmMixer.resampleStereo(stereo, frames, rate, RATE)
        }
        return stereo.takeIf { it.isNotEmpty() }
    }

    // ================= خيط الكاتب =================

    private fun writeLoop(w: WavFileWriter) {
        while (active.get()) {
            val chunk = drainChunk()
            if (chunk == null) {
                try { Thread.sleep(15) } catch (_: InterruptedException) { return }
                continue
            }
            writeOne(w, chunk)
        }
        // إفراغ نهائي لما تبقى في الطابور قبل الإغلاق
        while (true) {
            val chunk = drainChunk() ?: break
            writeOne(w, chunk)
        }
    }

    /** سحب دفعة من الطابور كعينات واحدة — null إن كان فارغًا. */
    private fun drainChunk(): ShortArray? = synchronized(lock) {
        if (pending.isEmpty()) null
        else if (pending.size == 1) pending.removeFirst()
        else {
            var total = 0
            for (p in pending) total += p.size
            val all = ShortArray(total)
            var off = 0
            for (p in pending) {
                System.arraycopy(p, 0, all, off, p.size)
                off += p.size
            }
            pending.clear()
            all
        }
    }

    private fun writeOne(w: WavFileWriter, chunk: ShortArray) {
        val bytes = ByteArray(chunk.size * 2)
        Pcm16.shortsToBytes(chunk, chunk.size, bytes)
        try {
            w.write(bytes)
            _durationMs.value = w.durationMs
            _level.value = Pcm16.rms(chunk, chunk.size).toFloat().coerceIn(0f, 1f)
        } catch (t: Throwable) {
            _error.value = "انقطعت كتابة التسجيل: ${t.message ?: "خطأ قرص"}"
        }
    }

    /** ملف جانبي للتوقيع — نفس عقد تسجيلات البث المباشر. */
    fun sidecarMeta(file: File, durationMs: Long, recorderName: String?, recorderRole: String?) {
        try {
            RecordingMeta(
                recorderName = recorderName,
                recorderRole = recorderRole,
                startedAtEpochMs = System.currentTimeMillis() - durationMs,
                durationMs = durationMs
            ).save(RecordingMeta.sidecarOf(file))
        } catch (_: Exception) { }
    }
}
