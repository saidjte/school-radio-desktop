package com.saidjte.schoolradio.desktop.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * طبقة المعالجة الرقمية للإشارة (DSP) — نقيّة كليًا بلا أي عتاد أو Java Sound،
 * وهي أساس «وحدة المزج» الإذاعية: معالج صوت المذيع ومعادل الصوت.
 *
 * كل مكون يعمل على عينات PCM 16-bit متداخلة (Interleaved ShortArray)
 * ويُعاد تكوينه حسب معدل العينات عند فتح القناة، والمعاملات حيةّ (Volatile)
 * فتتغير أثناء البث دون قطع صوت ولا إعادة فتح خطوط.
 */

// ================= مرشح ثنائي القطبية (RBJ Cookbook) =================

/**
 * مرشح Biquad بصيغة Direct Form I — لبنة بناء كل شرائح المعادل وسلسلة الصوت.
 * الأنواع: تمرير عالٍ، رفّ منخفض، رفّ عالٍ، قمة (Peaking).
 */
class Biquad {

    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

    private fun setCoeffs(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0
        this.a1 = a1 / a0; this.a2 = a2 / a0
    }

    private fun omega(fs: Double, f0: Double): Double {
        val safeF = f0.coerceIn(10.0, fs * 0.45)
        return 2.0 * PI * safeF / fs
    }

    /** تمرير عالٍ بـ 12dB/أوكتاف (Q=0.707 فلتر باترورث تقريبًا). */
    fun highPass(fs: Double, f0: Double, q: Double = 0.707) {
        val w = omega(fs, f0); val c = cos(w); val s = sin(w); val a = s / (2 * q)
        setCoeffs((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + a, -2 * c, 1 - a)
    }

    /** شريحة قمة (Peaking): رفع/خفض عند تردد محدد (A = 10^(g/40) كوصفة RBJ). */
    fun peaking(fs: Double, f0: Double, gainDb: Double, q: Double = 1.0) {
        val w = omega(fs, f0); val c = cos(w); val s = sin(w)
        val aa = s / (2 * q)
        val a = 10.0.pow(gainDb / 40.0)
        setCoeffs(1 + aa * a, -2 * c, 1 - aa * a, 1 + aa / a, -2 * c, 1 - aa / a)
    }

    /** رفّ منخفض (Low Shelf): رفع/خفض كل ما دون التردد. */
    fun lowShelf(fs: Double, f0: Double, gainDb: Double) {
        val w = omega(fs, f0); val c = cos(w); val s = sin(w)
        val a = 10.0.pow(gainDb / 40.0)
        val alpha = s / 2.0 * sqrt(2.0)
        val twoSqrt = 2.0 * sqrt(a) * alpha
        setCoeffs(
            a * ((a + 1) - (a - 1) * c + twoSqrt),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - twoSqrt),
            (a + 1) + (a - 1) * c + twoSqrt,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - twoSqrt
        )
    }

    /** رفّ عالٍ (High Shelf): رفع/خفض كل ما فوق التردد. */
    fun highShelf(fs: Double, f0: Double, gainDb: Double) {
        val w = omega(fs, f0); val c = cos(w); val s = sin(w)
        val a = 10.0.pow(gainDb / 40.0)
        val alpha = s / 2.0 * sqrt(2.0)
        val twoSqrt = 2.0 * sqrt(a) * alpha
        setCoeffs(
            a * ((a + 1) + (a - 1) * c + twoSqrt),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - twoSqrt),
            (a + 1) - (a - 1) * c + twoSqrt,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - twoSqrt
        )
    }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

// ================= تحويلات PCM 16-bit =================

/** تحويلات PCM 16-bit صغيرة الطرفية بين البايتات والعينات — بلا إنشاء قوائم. */
object Pcm16 {

    /** بايتات ← عينات. يعيد عدد العينات المكتوبة. */
    fun bytesToShorts(bytes: ByteArray, n: Int, out: ShortArray): Int {
        val count = n / 2
        for (i in 0 until count) {
            val lo = bytes[2 * i].toInt() and 0xFF
            val hi = bytes[2 * i + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return count
    }

    /** عينات ← بايتات (count عيّنة). */
    fun shortsToBytes(shorts: ShortArray, count: Int, out: ByteArray) {
        for (i in 0 until count) {
            val v = shorts[i].toInt()
            out[2 * i] = (v and 0xFF).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
    }

    /** RMS لعينات متداخلة كنسْبة من المدى الكامل [0..1]. */
    fun rms(shorts: ShortArray, count: Int): Double {
        if (count <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until count) {
            val v = shorts[i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / count)
    }

    /** كسب خطي من ديسيبل. */
    fun dbToAmp(db: Double): Double = 10.0.pow(db / 20.0)

    /** ديسيبل من كسب خطي (مع حماية الصفر). */
    fun ampToDb(amp: Double): Double = 20.0 * log10(max(amp, 1e-9))
}

// ================= معالج صوت المذيع =================

/**
 * معالج صوت المذيع — سلسلة إذاعية احترافية كاملة على قناة الميكروفون:
 *
 *   ميكروفون ← مانع صدى AEC (يطرح صدى الموسيقى — انظر Aec.kt)
 *            ← مرشح تمرير عالٍ (قناع الرعد واحتكاك الورق)
 *            ← بوابة ضجيج (صمت المقصلة بين الجمل)
 *            ← توحيد كسب أوتوماتيكي AGC (مستوى نطق ثابت مهما بعد المذيع)
 *            ← منحنى الصوت (باس 180Hz · حضور 3.2kHz · هواء 8kHz)
 *            ← ضاغط ديناميكي (توحيد مستوى النطق)
 *            ← محدّد أمان بقناع ناعم (لا تشبع أبدًا)
 *            ← فيدر القناة × الماستر
 *
 * المعاملات حيةّ لحظيًا: تغيير أي مفتاح أثناء البث يسري على الكتلة التالية
 * (~50ms) دون قطع. كل الحساب على عينات الميكروفون الفعلية بأي معدل
 * يتفاوض عليه الجهاز، والقناة الواحدة تملك كل الحالات (لا سباقات).
 */
class VoiceProcessor {

    /** معاملات المعالج — تُستبدل مرجعيًا فتقرؤها الكتلة التالية دفعة واحدة. */
    data class Params(
        val enabled: Boolean = true,
        val hpfOn: Boolean = true,
        val gateOn: Boolean = false,
        val gateThresholdDb: Float = -45f,
        val gateReleaseMs: Float = 250f,
        val agcOn: Boolean = true,
        val agcTargetDb: Float = -18f,
        val agcMaxBoostDb: Float = 18f,
        val compOn: Boolean = false,
        val compThresholdDb: Float = -20f,
        val compRatio: Float = 3f,
        val compMakeupDb: Float = 3f,
        val bassDb: Float = 0f,
        val presenceDb: Float = 0f,
        val airDb: Float = 0f
    )

    @Volatile private var paramsRef = Params()
    fun setParams(p: Params) { paramsRef = p }
    fun params(): Params = paramsRef

    /** البوابة مغلقة الآن؟ (مؤشر حي للواجهة) — يُحدَّث مع كل كتلة. */
    @Volatile var gateClosed = false
        private set

    private var sampleRate = 0
    private var channels = 0

    // مرشحات لكل قناة صوتية (ستيريو = نسختان لكل شريحة)
    private var hpf1: Array<Biquad> = emptyArray()
    private var hpf2: Array<Biquad> = emptyArray()
    private var bass: Array<Biquad> = emptyArray()
    private var presence: Array<Biquad> = emptyArray()
    private var air: Array<Biquad> = emptyArray()

    // حالات المغلفات
    private var gateEnv = doubleArrayOf()
    private var gateGain = 1.0
    private var compEnv = doubleArrayOf()
    private var compGainDb = 0.0

    // حالة توحيد الكسب الأوتوماتيكي AGC
    private var agcGainDb = 0.0

    /** كسب AGC الجاري بالديسيبل (مؤشر حي للواجهة: +تعزيز / −خفض). */
    @Volatile var agcCurrentDb = 0.0f
        private set

    // ثابت المغلف المحسوب من معدل العينات (زمن تنعيم 10ms)
    private var envAlpha = 0.02

    // قيم شرائح المنحنى المبنية حاليًا (لتجنب إعادة البناء كل كتلة)
    private var lastBass = Float.NaN
    private var lastPresence = Float.NaN
    private var lastAir = Float.NaN

    private var work = FloatArray(4096)
    private var blockGain = 1.0

    /** يُستدعى عند فتح القناة (أو تغيّر الصيغة): بناء المعاملات حسب معدل العينات. */
    fun configure(sampleRate: Int, channels: Int) {
        val fs = sampleRate.coerceAtLeast(8000).toDouble()
        val ch = channels.coerceIn(1, 2)
        this.sampleRate = sampleRate; this.channels = ch
        fun rebuild(init: (Biquad) -> Unit): Array<Biquad> =
            Array(ch) { Biquad().also(init) }
        hpf1 = rebuild { it.highPass(fs, 85.0) }
        hpf2 = rebuild { it.highPass(fs, 85.0) }
        bass = rebuild { it.peaking(fs, 180.0, 0.0, 1.0) }
        presence = rebuild { it.peaking(fs, 3200.0, 0.0, 1.0) }
        air = rebuild { it.highShelf(fs, 8000.0, 0.0) }
        gateEnv = DoubleArray(ch); compEnv = DoubleArray(ch)
        gateGain = 1.0; compGainDb = 0.0
        agcGainDb = 0.0
        agcCurrentDb = 0.0f
        envAlpha = 1.0 - exp(-1.0 / (0.010 * fs))
        lastBass = Float.NaN; lastPresence = Float.NaN; lastAir = Float.NaN
    }

    /**
     * معالجة كتلة عينات متداخلة في مكانها + كسب القناة النهائي.
     * يعيد مستوى الكتلة بعد المعالجة (RMS منمّط [0..1]) للمقياس الحي.
     */
    fun process(shorts: ShortArray, count: Int, gainTarget: Float): Float {
        if (count <= 0) return 0f
        if (work.size < count) work = FloatArray(count)
        val p = paramsRef

        for (i in 0 until count) work[i] = shorts[i] / 32768.0f

        if (p.enabled && sampleRate > 0) {
            // 1) تمرير عالٍ مزدوج 24dB/أوكتاف عند 85 هرتز
            if (p.hpfOn) {
                for (c in 0 until channels) {
                    var i = c
                    while (i < count) {
                        val v = hpf2[c].process(hpf1[c].process(work[i].toDouble()))
                        work[i] = v.toFloat(); i += channels
                    }
                }
            }
            // 2) بوابة الضجيج: مغلف ذروة 10ms، هجوم كتلة واحدة، إفلات بالزمن المحدد
            if (p.gateOn) {
                val thrAmp = Pcm16.dbToAmp(p.gateThresholdDb.toDouble())
                val relAlpha = blockAlpha(p.gateReleaseMs.toDouble())
                for (c in 0 until channels) {
                    var i = c
                    while (i < count) {
                        val a = abs(work[i].toDouble())
                        gateEnv[c] += (a - gateEnv[c]) * envAlpha
                        i += channels
                    }
                }
                val anyAbove = (0 until channels).any { gateEnv[it] >= thrAmp }
                val target = if (anyAbove) 1.0 else 0.0
                val alpha = if (target > gateGain) 0.8 else relAlpha
                gateGain += (target - gateGain) * alpha
                gateClosed = gateGain < 0.5
                val g = gateGain
                for (i in 0 until count) work[i] = (work[i] * g).toFloat()
            } else {
                gateClosed = false
            }
            // 3) توحيد الكسب الأوتوماتيكي AGC: مستوى نطق ثابت مهما بعد المذيع عن
            //    الميكروفون — هجوم بطيء صعودًا (بلا مضخّ)، وإفلات أسرع نزولًا،
            //    وتجميد كامل عند الصمت كي لا يطارد الكسب أرضية الضجيج.
            if (p.agcOn) {
                var sum = 0.0
                for (i in 0 until count) { val v = work[i].toDouble(); sum += v * v }
                val blockRmsDb = Pcm16.ampToDb(sqrt(sum / count))
                val blockMs = count * 1000.0 / (sampleRate * channels)
                if (blockRmsDb > -55.0) {
                    // حدّ الكسب: خفض حتى -12dB وتعزيز حتى السقف المحدد
                    val target = (p.agcTargetDb - blockRmsDb)
                        .coerceIn(-12.0, p.agcMaxBoostDb.toDouble())
                    val tau = if (target > agcGainDb) 1200.0 else 300.0
                    val alpha = 1.0 - exp(-blockMs / tau)
                    agcGainDb += (target - agcGainDb) * alpha
                }
                agcCurrentDb = agcGainDb.toFloat()
                val g = Pcm16.dbToAmp(agcGainDb)
                for (i in 0 until count) work[i] = (work[i] * g).toFloat()
            } else {
                agcGainDb = 0.0
                agcCurrentDb = 0.0f
            }
            // 4) منحنى الصوت الإذاعي (يُعاد بناؤه فقط عند تغيّر القيم)
            if (p.bassDb != 0f || p.presenceDb != 0f || p.airDb != 0f) {
                if (lastBass != p.bassDb || lastPresence != p.presenceDb || lastAir != p.airDb) {
                    val fs = sampleRate.toDouble()
                    bass.forEach { it.peaking(fs, 180.0, p.bassDb.toDouble(), 1.0) }
                    presence.forEach { it.peaking(fs, 3200.0, p.presenceDb.toDouble(), 1.0) }
                    air.forEach { it.highShelf(fs, 8000.0, p.airDb.toDouble()) }
                    lastBass = p.bassDb; lastPresence = p.presenceDb; lastAir = p.airDb
                }
                for (c in 0 until channels) {
                    var i = c
                    while (i < count) {
                        val v = air[c].process(presence[c].process(bass[c].process(work[i].toDouble())))
                        work[i] = v.toFloat(); i += channels
                    }
                }
            }
            // 5) الضاغط الديناميكي: مغلف ذروة 10ms + هجوم سريع وإفلات ناعم
            if (p.compOn) {
                val thrDb = p.compThresholdDb.toDouble()
                val ratio = p.compRatio.toDouble().coerceAtLeast(1.0)
                val attackAlpha = 0.5
                val releaseAlpha = 0.10
                for (c in 0 until channels) {
                    var i = c
                    while (i < count) {
                        val a = abs(work[i].toDouble())
                        compEnv[c] += (a - compEnv[c]) * envAlpha
                        i += channels
                    }
                }
                val peakDb = Pcm16.ampToDb((0 until channels).maxOf { compEnv[it] })
                val targetGr = if (peakDb > thrDb) thrDb + (peakDb - thrDb) / ratio - peakDb else 0.0
                val alpha = if (targetGr < compGainDb) attackAlpha else releaseAlpha
                compGainDb += (targetGr - compGainDb) * alpha
                val g = Pcm16.dbToAmp(compGainDb + p.compMakeupDb.toDouble())
                for (i in 0 until count) work[i] = (work[i] * g).toFloat()
            } else {
                compGainDb = 0.0
            }
            // 6) محدّد الأمان بقناع ناعم متصل: |خروج| <= سقف 0.935 (-0.6dB) دائمًا،
            //    بلا تلوين تحت الكتف (0.80×السقف) وبلا انقطاع عند الانتقال.
            val ceiling = 0.935
            val knee = 0.80 * ceiling
            for (i in 0 until count) {
                val v = work[i].toDouble()
                val a = abs(v)
                val clamped = if (a <= knee) v else {
                    val over = tanh((a - knee) / (ceiling - knee)) * (ceiling - knee)
                    if (v >= 0) knee + over else -(knee + over)
                }
                work[i] = clamped.toFloat()
            }
        }

        // 7) كسب القناة (فيدر × ماستر × كتم) بانتقال ناعم بلا طقطقة
        val gt = gainTarget.toDouble().coerceIn(0.0, 2.5)
        blockGain += (gt - blockGain) * 0.30
        val g = blockGain
        for (i in 0 until count) work[i] = (work[i] * g).toFloat()

        for (i in 0 until count) {
            shorts[i] = (work[i] * 32768.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return LevelMeter.normalized(Pcm16.rms(shorts, count)).toFloat()
    }

    /** ثابت التنعيم لكتلة ~50ms من زمن بالمللي ثانية. */
    private fun blockAlpha(ms: Double): Double {
        val tau = max(ms, 20.0) / 50.0
        return min(0.9, 1.0 / max(tau, 1.0))
    }
}

// ================= معادل الصوت (قناة الموسيقى) =================

/**
 * معادل صوت بخمس شرائح إذاعية: 80Hz رفّ · 250Hz قمة · 1kHz قمة · 3.5kHz قمة · 10kHz رفّ،
 * ±12dB لكل شريحة. يعمل على قناة الموسيقى كاملة (MP3 وPCM) لحظيًا،
 * والمعاملات تُعاد بناؤها آليًا حسب معدل عينات الملف الجاري.
 */
class MusicEq {

    companion object {
        val BAND_FREQS = intArrayOf(80, 250, 1000, 3500, 10000)
        val BAND_LABELS = arrayOf("باص 80", "دافئ 250", "وسط 1ك", "حضور 3.5ك", "هواء 10ك")
        const val MAX_DB = 12f
        const val BANDS = 5

        fun clampBand(v: Float): Float = v.coerceIn(-MAX_DB, MAX_DB)
    }

    @Volatile private var enabledRef = false
    @Volatile private var bandsRef = FloatArray(BANDS)

    fun setEnabled(on: Boolean) { enabledRef = on }
    fun setBands(b: FloatArray) {
        bandsRef = FloatArray(BANDS) { i -> clampBand(b.getOrElse(i) { 0f }) }
        dirty = true
    }
    fun bands(): FloatArray = bandsRef.copyOf()
    fun isEnabled(): Boolean = enabledRef

    private var sampleRate = 0
    private var channels = 0
    private var filters: Array<Array<Biquad>> = emptyArray() // [band][channel]
    private var dirty = true

    private var work = FloatArray(4096)
    private var blockGain = 1.0

    fun configure(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate.coerceAtLeast(8000)
        this.channels = channels.coerceIn(1, 2)
        filters = Array(BANDS) { Array(this.channels) { Biquad() } }
        dirty = true
    }

    /** معالجة كتلة في مكانها + كسب القناة. يعيد المستوى المنمّط [0..1]. */
    fun process(shorts: ShortArray, count: Int, gainTarget: Float): Float {
        if (count <= 0) return 0f
        if (work.size < count) work = FloatArray(count)
        val bands = bandsRef

        for (i in 0 until count) work[i] = shorts[i] / 32768.0f

        if (enabledRef && sampleRate > 0) {
            if (dirty) {
                val fs = sampleRate.toDouble()
                for (b in 0 until BANDS) {
                    val g = bands[b].toDouble()
                    for (c in 0 until channels) {
                        val f = filters[b][c]
                        if (b == 0) f.lowShelf(fs, BAND_FREQS[b].toDouble(), g)
                        else if (b == BANDS - 1) f.highShelf(fs, BAND_FREQS[b].toDouble(), g)
                        else f.peaking(fs, BAND_FREQS[b].toDouble(), g, 1.0)
                        f.reset()
                    }
                }
                dirty = false
            }
            for (b in 0 until BANDS) {
                for (c in 0 until channels) {
                    val f = filters[b][c]
                    var i = c
                    while (i < count) {
                        work[i] = f.process(work[i].toDouble()).toFloat()
                        i += channels
                    }
                }
            }
        }

        val gt = gainTarget.toDouble().coerceIn(0.0, 2.5)
        blockGain += (gt - blockGain) * 0.30
        val g = blockGain
        for (i in 0 until count) work[i] = (work[i] * g).toFloat()

        for (i in 0 until count) {
            shorts[i] = (work[i] * 32768.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        return LevelMeter.normalized(Pcm16.rms(shorts, count)).toFloat()
    }
}

// ================= مساعد قياس الاستجابة (للاختبارات والفحص الذاتي) =================

/**
 * توليد نغمة جيبية وقياس الكسب بعد سلسلة مرشحات — أداة اختبار نقيّة:
 * تعيد ديسيبل نسبة RMS الخروج إلى RMS الدخل بعد تجاوز المرحلة الانتقالية.
 */
object DspMeasure {

    /**
     * كسب سلسلة مرشحات مركّبة عند تردد معين.
     * stages = عدد المرشحات المتتالية المتطابقة (مثلًا 2 لتمرير عالٍ 24dB/أوكتاف).
     */
    fun chainDb(
        freqHz: Double,
        sampleRate: Int = 44100,
        stages: Int = 1,
        setup: (Biquad) -> Unit
    ): Double {
        val filters = Array(stages) { Biquad().also(setup) }
        val n = sampleRate
        var sum = 0.0; var cnt = 0
        val skip = n / 2
        for (i in 0 until n) {
            var x = 0.5 * sin(2.0 * PI * freqHz * i / sampleRate)
            for (f in filters) x = f.process(x)
            if (i >= skip) { sum += x * x; cnt++ }
        }
        val outRms = sqrt(sum / max(cnt, 1))
        val inRms = 0.5 / sqrt(2.0)
        return Pcm16.ampToDb(outRms / max(inRms, 1e-9))
    }
}
