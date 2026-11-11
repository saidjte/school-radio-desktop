package com.saidjte.schoolradio.desktop.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * ناقل تسجيل مزيج الحصة (v0.31.0 «تسجيل مزيج الحصة كاملًا — مايك + موسيقى
 * في ملف واحد») — **الجيل الثاني (v0.45.0): خلط حقيقي لا تفريغ متتالٍ**.
 *
 * درس رسالة المستخدم (v0.45.0): «تسجيل الحصة الكاملة يتشوش عند تشغيل
 * الميكروفون والموسيقى معًا. أي تشغيل متوازٍ يشوّش التسجيل». جيل v0.31 كان
 * يجمّع مقاطع المصادر بترتيب الوصول في الطابور (concatenation) — صحيحٌ حين
 * يُغذَّى مصدر واحد في كل لحظة، لكنه **متشوش بالضرورة** عند التغذية المتوازية:
 * مقاطع الموسيقى والمايك تتداخل الوصول فتتناوب في الملف المكتوب كأن أحدهما
 * يقاطع الآخر، والمدة تصبح مجموع المدد لا أقصاها. هذا هو جذر «التشوش»
 * وأُزيل من أصله: الجيل الثاني ناقل خلط كما في لوحة استوديو حقيقية.
 *
 * **عقد الجيل الثاني — الخلط لا الترتيب:**
 * - ثلاثة مسارات (موسيقى/مايك/جينل) لكل منها طابور استقبال خاص بعيناته
 *   المُحوَّلة مسبقًا إلى الصيغة الموحدة (44.1kHz ستيريو 16-bit).
 * - خيط الكاتب يسير على **ساعة الحائط**: كل لحظة لها إطارها المستهدف،
 *   ويُسحب من كل مسار ما يوازي الحصة الزمنية نفسها، وتُجمع المسارات
 *   عينةً مقابل عينة (sum) في حقل عائم — فالمتزامنُ يجتمع لحظةً بلحظة
 *   والمدة الناتجة أقصى المدد لا مجموعها.
 * - مسارٌ تأخرت تغذيته يُحشى صمتًا في حصته (jitter buffer)، ومسارٌ سبق
 *   الرَكب تبقى فائضته في طابوره للحصة التالية — لا يُهدر صوت ولا يُقطع.
 * - الجمع قد يتجاوز المدى حين يعلو المذيع والموسيقى معًا فمُشبِع ناعم
 *   ([MixMath.saturate]) يحرس الحدود بلا موجة مربعة قاسية — التشويش
 *   الرقمي المحتمل مُنع من أصله كذلك.
 * - كل مصدر يكتب على خيطه الحقيقي بلا أي انتظار: التحويل والطابور فقط،
 *   والخيط الكاتب يخلط ويكتب — فلا يُبطئ التسجيل البث أبدًا.
 */
object MixBus {

    /** معدل التسجيل الموحد — جودة أرشيف سليمة وحجم معقول (≈10MB للدقيقة). */
    const val RATE = 44100
    const val CHANNELS = 2

    /** مسارات الناقل الثلاثة — كل مصدر له مساره فلا يختلط ترتيبُ مصادر. */
    internal enum class Lane { MUSIC, MIC, JINGLE }

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

    /** طوابير الاستقبال: طابور لكل مسار بعيناته الموحدة (44.1k ستيريو). */
    private val lanes: MutableMap<Lane, ArrayDeque<ShortArray>> = mutableMapOf()
    private var writtenFrames = 0L
    private var startNanos = 0L
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
            lanes.clear()
            Lane.values().forEach { lanes[it] = ArrayDeque() }
            writtenFrames = 0L
            startNanos = System.nanoTime()
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
     * إيقاف التسجيل وإفراغ ما تبقى من طوابير المسارات في الملف (خلطًا لا
     * ترتيبًا) وإغلاقه. يعيد المدة النهائية، أو 0 إن لم يكن هناك تسجيل.
     * آمن للاستدعاء المتكرر.
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
        synchronized(lock) {
            lanes.clear()
            writtenFrames = 0L
        }
        return dur
    }

    // ================= تغذية المصادر — بلا انتظار على أي خيط =================

    /** تغذية قناة الموسيقى (عينات ما بعد قناة المزج — ما يُسمع فعلاً). */
    fun feedMusic(shorts: ShortArray, count: Int, sampleRate: Int, channels: Int) {
        feedLane(Lane.MUSIC, shorts, count, sampleRate, channels)
    }

    /** تغذية صوت جينل (بايتات ما بعد مستوى الجينل البرمحي). */
    fun feedJingle(bytes: ByteArray, off: Int, len: Int, sampleRate: Int, channels: Int) {
        feedBytes(Lane.JINGLE, bytes, off, len, sampleRate, channels)
    }

    /** تغذية الميكروفون (بايتات ما بعد السلسلة الإذاعية الكاملة — على الهواء). */
    fun feedMic(bytes: ByteArray, off: Int, len: Int, sampleRate: Int, channels: Int) {
        feedBytes(Lane.MIC, bytes, off, len, sampleRate, channels)
    }

    private fun feedBytes(lane: Lane, bytes: ByteArray, off: Int, len: Int, sampleRate: Int, channels: Int) {
        if (!active.get() || len < 2) return
        if (off <= 0) {
            val cap = len / 2
            val src = ShortArray(cap)
            val n = Pcm16.bytesToShorts(bytes, cap * 2, src)
            feedLane(lane, src, n, sampleRate, channels)
        } else {
            // مجرى بإزاحة: نسخة المقطع المطلوب فقط — المسار النادر آمن ومكتم
            val seg = ByteArray(len)
            System.arraycopy(bytes, off, seg, 0, len)
            val cap = len / 2
            val s2 = ShortArray(cap)
            val n = Pcm16.bytesToShorts(seg, cap * 2, s2)
            feedLane(lane, s2, n, sampleRate, channels)
        }
    }

    private fun feedLane(lane: Lane, src: ShortArray, count: Int, sampleRate: Int, channels: Int) {
        if (!active.get() || count <= 0 || channels <= 0) return
        val canonical = toCanonical(src, count, sampleRate, channels) ?: return
        synchronized(lock) {
            val q = lanes.getOrPut(lane) { ArrayDeque() }
            q.addLast(canonical)
            // سقف الرَكب: مسار تعثّر خيطه يُهرى أقدم ما فيه فلا ينفخ الذاكرة
            var frames = laneFrames(q)
            while (frames > MAX_LANE_FRAMES) {
                val head = q.first()
                // الفائض بالإطارات يُحوَّل عيناتًا (× القنوات) قبل القصّ من الرأس
                val drop = minOf(head.size, (frames - MAX_LANE_FRAMES) * CHANNELS)
                if (drop >= head.size) {
                    q.removeFirst()
                } else {
                    q[0] = head.copyOfRange(drop, head.size)
                }
                frames -= drop / CHANNELS
            }
        }
    }

    /** عدد الإطارات (عينات لكل قناة) في طابور مسار. */
    private fun laneFrames(q: ArrayDeque<ShortArray>): Int {
        var f = 0
        for (a in q) f += a.size / CHANNELS
        return f
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

    // ================= خيط الكاتب — خلط على ساعة الحائط =================

    private fun writeLoop(w: WavFileWriter) {
        while (active.get()) {
            val wrote = writeQuota(w)
            if (!wrote) {
                try { Thread.sleep(15) } catch (_: InterruptedException) { return }
            }
        }
        // إفراغ نهائي: ما تبقى في طوابير المسارات يُخلط ويُكتب قبل الإغلاق
        while (true) {
            if (!writeQuota(w, finalDrain = true)) break
        }
    }

    /**
     * كتابة حصة زمنية واحدة: الهدف إطارُ ساعة الحائط، والحصة الفارق بينه
     * وبين المكتوب — تُسحب من كل مسار حتى الحصة (الناقص يُحشى صمتًا) وتُجمع
     * عينةً مقابل عينة. في التصريف النهائي [finalDrain] تكون الحصة أكبر ما
     * تبقى في المسارات كلها كي لا يُهدر صوت متأخر الوصول.
     * يعيد true إن كُتب شيء.
     */
    private fun writeQuota(w: WavFileWriter, finalDrain: Boolean = false): Boolean {
        val quota = synchronized(lock) {
            if (finalDrain) {
                var maxF = 0
                for (q in lanes.values) maxF = maxOf(maxF, laneFrames(q))
                maxF
            } else {
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                val target = elapsedMs * RATE / 1000L
                (target - writtenFrames).coerceIn(0L, MAX_CATCHUP_FRAMES).toInt()
            }
        }
        if (quota <= 0) return false
        val mixed = synchronized(lock) {
            // سحب كل مسار حتى الحصة — الفائض يبقى في الطابور للحصة التالية
            val pulled = Array(Lane.values().size) { ShortArray(0) }
            for (lane in Lane.values()) {
                val q = lanes[lane] ?: continue
                var need = quota
                val parts = ArrayList<ShortArray>(2)
                while (need > 0 && q.isNotEmpty()) {
                    val head = q.first()
                    val headFrames = head.size / CHANNELS
                    if (headFrames <= need) {
                        q.removeFirst()
                        parts.add(head)
                        need -= headFrames
                    } else {
                        val take = need * CHANNELS
                        parts.add(head.copyOfRange(0, take))
                        q[0] = head.copyOfRange(take, head.size)
                        need = 0
                    }
                }
                pulled[lane.ordinal] = when (parts.size) {
                    0 -> ShortArray(0)
                    1 -> parts[0]
                    else -> {
                        val total = parts.sumOf { it.size }
                        val all = ShortArray(total)
                        var off = 0
                        for (p in parts) { System.arraycopy(p, 0, all, off, p.size); off += p.size }
                        all
                    }
                }
            }
            MixMath.mixLanes(pulled, quota, CHANNELS)
        }
        writeOne(w, mixed)
        synchronized(lock) { writtenFrames += quota }
        return true
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

    /** سقف رَكب المسار الواحد بالإطارات (≈4 ثوانٍ) — تجاوزه يُهرى الأقدم. */
    internal val MAX_LANE_FRAMES = 4 * RATE

    /** أقصى كسبٍ لحظي للحصة الواحدة (ثانية) — تعثّر طويل يعود بنبضات آمنة. */
    internal val MAX_CATCHUP_FRAMES = RATE.toLong()

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

/**
 * رياضيات الخلط (v0.45.0) — نقية قابلة للاختبار: جمع المسارات المتزامنة
 * عينةً مقابل عينة في حقل عائم مع مُشبِع ناعم يحفظ الحدود بلا موجة مربعة.
 */
object MixMath {

    /** ركبة التشبع الناعم: التعريف إلى 0.92، وفوقه امتداد أُسّي إلى سقف 1.0. */
    const val KNEE = 0.92f

    /**
     * الإشباع الناعم: القيم داخل الركبة تمر كما هي (بلا مساس بالصوت الهادئ
     * والمتوسط)، وفوقها يُثنى المدى بسلاسة نحو السقف 1.0 دون بلوغه أبدًا
     * (سقف داخلي 0.99999 يحرس دقة float عند المدخلات القصوى) — فلا موجة
     * مربعة ولا تشويش رقمي قاسي عند التقاء مصادر عالية. متناظرة ورتيبة تمامًا.
     */
    fun saturate(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val bent = KNEE + (1f - KNEE) * (1f - exp(-(a - KNEE) / (1f - KNEE)))
        val capped = bent.coerceAtMost(0.99999f)
        return if (x < 0f) -capped else capped
    }

    /**
     * خلط مسارات الحصة الواحدة: كل مسار مصفوفته المجمعة (ستيريو متداخلة)،
     * والناقص عن [frames] يُحشى صمتًا — الجمع في حقل عائم ثم إشباع ناعم
     * ثم ترميز 16-bit. تعيد مصفوفة بطول frames×channels بالضبط.
     */
    fun mixLanes(lanes: Array<ShortArray>, frames: Int, channels: Int): ShortArray {
        val out = ShortArray(frames * channels)
        if (frames <= 0 || channels <= 0) return out
        // مراكمة عائمة لكل المسارات أولًا ثم إشباع واحد في النهاية —
        // فلا خسارة تراكمية من إشباعٍ مرحلي بين المصادر
        val acc = FloatArray(out.size)
        for (lane in lanes) {
            if (lane.isEmpty()) continue
            val n = minOf(lane.size, acc.size)
            for (i in 0 until n) acc[i] += lane[i]
        }
        for (i in acc.indices) {
            out[i] = (saturate(acc[i] / 32768f) * 32767f).roundToInt().toShort()
        }
        return out
    }
}
