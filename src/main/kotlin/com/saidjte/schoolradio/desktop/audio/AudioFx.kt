package com.saidjte.schoolradio.desktop.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.tanh

/**
 * مكتبة المؤثرات الصوتية الاحترافية (v0.40.0) — DSP خالص على JVM بلا أي
 * مكتبة خارجية ولا عتاد، كعقد خط الصوت كله في التطبيق.
 *
 * البنود الأربعة عشر: صدى Reverb، تكرار Echo، تأخير Delay، مضاعفة الإحساس
 * Chorus، حَرْف Flanger، طور Phaser، تشويش Distortion، توسيع استريو، ناقل
 * نبرة، معزّز صوت المذيع، مؤثر الراديو، مؤثر الهاتف، صوت الروبوت، ومبدّل
 * الطبيعة الصوتية الإبداعي (أنثوي/رجولي/كائن فضائي/عملاق) — ثم مكتبة جاهزات
 * (Presets) تدمج المؤثرات بسلاسل مدروسة بأسماء عربية.
 *
 * العقد المعماري: كل مؤثر معالج زمني يعمل على ستيريو عائم [0..1] وقد يضيف
 * «ذيلًا زمنيًا» (صدى وتكرار وتأخير) بعد نهاية المقطع، والمخرَج يُطبَّع ذروته
 * تحت 0.98 كي لا يشبع أبدًا. السلسلة تُشفَّر نصًّا محايدًا (id:p=v|id2:…) كي
 * تشارك بروتوكول الارتداد المخزَّن نفسه في [ClipTransforms.bounce] — بصمة
 * SHA-1 للسلسلة تعيد الملف المخزَّن نفسه بلا حساب ثانٍ، والملف الأصلي لا يُمسّ.
 *
 * كل المعاملات تُقيَّد داخل نطاقها القانوني عند البناء فلا قيمة تُفسد الصوت.
 */

// ================= وحدات بناء DSP الصغيرة =================

/** لصق ستيريو متشابك 16-bit إلى قناتين عائمتين منمّطتين [0..1]. */
internal fun fxSplit(pcm: ShortArray): Pair<FloatArray, FloatArray> {
    val frames = pcm.size / 2
    val l = FloatArray(frames)
    val r = FloatArray(frames)
    for (i in 0 until frames) {
        l[i] = pcm[2 * i] / 32768.0f
        r[i] = pcm[2 * i + 1] / 32768.0f
    }
    return l to r
}

/** قناتان عائمتان ← ستيريو متشابك 16-bit محبوس. */
internal fun fxJoin(l: FloatArray, r: FloatArray): ShortArray {
    val frames = min(l.size, r.size)
    val out = ShortArray(frames * 2)
    for (i in 0 until frames) {
        out[2 * i] = (l[i] * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
        out[2 * i + 1] = (r[i] * 32768.0f).toInt().coerceIn(-32768, 32767).toShort()
    }
    return out
}

/** تطبيع الذروة: يمنع أي مؤثر من دفع المخرَج للتشبع — السقف 0.98 دائمًا. */
internal fun fxNormalize(l: FloatArray, r: FloatArray) {
    var peak = 1e-9f
    for (v in l) peak = max(peak, abs(v))
    for (v in r) peak = max(peak, abs(v))
    if (peak > 0.98f) {
        val g = 0.98f / peak
        for (i in l.indices) l[i] *= g
        for (i in r.indices) r[i] *= g
    }
}

/** ملء المنطقة البعدية (ذيل المؤثر) بالصفر قبل المعالجة. */
internal fun fxPadWithTail(l: FloatArray, r: FloatArray, frames: Int, tail: Int): Pair<FloatArray, FloatArray> {
    if (tail <= 0) return l to r
    val nl = FloatArray(frames + tail)
    val nr = FloatArray(frames + tail)
    System.arraycopy(l, 0, nl, 0, min(frames, l.size))
    System.arraycopy(r, 0, nr, 0, min(frames, r.size))
    return nl to nr
}

/** مرشح تمرير منخفض بسيط بقطب واحد — لتلوين النغمات الخشنة بعد التشويش. */
internal class FxOnePoleLp(rate: Int, hz: Double) {
    private val a: Double = 1.0 - kotlin.math.exp(-2.0 * PI * hz / rate)
    private var y = 0.0
    fun process(x: Float): Float {
        y += a * (x - y)
        return y.toFloat()
    }
}

/** مغلف ذروة بنصف عمر محسوب من معدل العينات — لضغط كسب الإحساس الواحد. */
internal class FxEnvelope(rate: Int, attackMs: Double, releaseMs: Double) {
    private val atk = 1.0 - kotlin.math.exp(-1.0 / (rate * attackMs / 1000.0))
    private val rel = 1.0 - kotlin.math.exp(-1.0 / (rate * releaseMs / 1000.0))
    var env = 0.0f
        private set
    fun process(x: Float): Float {
        val a = abs(x)
        env = (if (a > env) env + (a - env) * atk else env + (a - env) * rel).toFloat()
        return env
    }
}

/**
 * مرشح Freeverb الكلاسيكي: ذاكرة دائرية بتغذية راجعة وترطيب — ثمانية شُرَب
 * لكل قناة بأزمنة متباعدة كي تختفي الرنينات المترابطة.
 */
internal class FxComb(val buf: FloatArray) {
    var feedback = 0.84f
    var damp = 0.2f
    private var idx = 0
    private var store = 0f
    fun process(x: Float): Float {
        val out = buf[idx]
        store = out * (1f - damp) + store * damp
        buf[idx] = x + store * feedback
        if (++idx >= buf.size) idx = 0
        return out
    }
}

/** مرشح كل الممرات — يزحزح الطور بلا تغيير الكسب، طابع الصدى الناعم. */
internal class FxAllpass(val buf: FloatArray) {
    var feedback = 0.5f
    private var idx = 0
    fun process(x: Float): Float {
        val bufOut = buf[idx]
        val out = bufOut - x
        buf[idx] = x + bufOut * feedback
        if (++idx >= buf.size) idx = 0
        return out
    }
}

// ================= عقد المؤثر =================

/** معامل مؤثر واحد: معرّف ونصّ عربي ونطاق قانوني وخطوة ووحدة للعرض. */
data class FxParam(
    val id: String,
    val labelAr: String,
    val min: Double,
    val max: Double,
    val default: Double,
    val step: Double = 1.0,
    val unitAr: String = ""
) {
    fun clamp(v: Double): Double = v.coerceIn(min, max)
}

/** معالج مؤثر حي — يعمل على قناتي ستيريو ويعيد النتيجة (قد تختلف المدة). */
interface FxProcessor {
    /** الذيل المضاف بعد نهاية المقطع بالعينات — صفر للمؤثرات الحافظة للمدة. */
    val tailSamples: Int get() = 0

    fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray>
}

/** تعريف مؤثر كامل: هوية ووصف عربي ومعاملاته ومصنع معالجه. */
class FxDef(
    val id: String,
    val nameAr: String,
    val descAr: String,
    val params: List<FxParam>,
    val factory: (Map<String, Double>) -> FxProcessor
) {
    fun param(id: String): FxParam = params.first { it.id == id }
}

/** خطوة سلسلة مؤثرات: معرّف المؤثر وقيم معاملاته المقيّدة. */
data class FxChainStep(val defId: String, val values: Map<String, Double>)

// ================= عائلة المؤثرات =================

object AudioFx {

    private const val RATE = MontageRender.RATE

    /** حبس القيم داخل نطاقاتها مع تقريبها على خطوة المعامل وملء النواقص بالافتراضي — لا قيمة غريبة تنجو. */
    fun clampValues(def: FxDef, values: Map<String, Double>): Map<String, Double> =
        def.params.associate { p ->
            val raw = values[p.id] ?: p.default
            val snapped = if (p.step > 0) kotlin.math.round(raw / p.step) * p.step else raw
            val rounded = kotlin.math.round(snapped * 1000.0) / 1000.0
            p.id to p.clamp(rounded)
        }

    /** قيم الافتراضي كاملة — لحالة فتح الحوار أول مرة. */
    fun defaultValues(def: FxDef): Map<String, Double> =
        def.params.associate { it.id to it.default }

    /**
     * الترميز المحايد للسلسلة: معاملات بترتيب التعريف وقيم بثلاث كسور مقلّمة
     * — ترتيبان مختلفان لنفس المؤثر يعطيان السلسلة نفسها فبصمة الارتداد نفسها.
     */
    fun encodeChain(steps: List<FxChainStep>): String =
        steps.joinToString("|") { step ->
            val def = byId(step.defId)
            if (def == null) step.defId
            else step.defId + ":" + def.params.joinToString(",") { p ->
                val v = step.values[p.id] ?: p.default
                val rounded = kotlin.math.round(v * 1000.0) / 1000.0
                "${p.id}=${rounded}"
            }
        }

    /** فكّ السلسلة — المدخل الفاسد يعطي سلسلة فارغة لا استثناء. */
    fun parseChain(spec: String): List<FxChainStep> {
        if (spec.isBlank()) return emptyList()
        return spec.split('|').mapNotNull { chunk ->
            val sep = chunk.indexOf(':')
            if (sep <= 0) return@mapNotNull null
            val defId = chunk.substring(0, sep)
            val def = byId(defId) ?: return@mapNotNull null
            val values = HashMap<String, Double>()
            chunk.substring(sep + 1).split(',').forEach { kv ->
                val eq = kv.indexOf('=')
                if (eq > 0) {
                    val v = kv.substring(eq + 1).toDoubleOrNull()
                    val p = def.params.firstOrNull { it.id == kv.substring(0, eq) }
                    if (v != null && p != null) values[p.id] = v
                }
            }
            FxChainStep(defId, clampValues(def, values))
        }
    }

    /**
     * تطبيق سلسلة مؤثرات على PCM ستيريو متشابك — الذيل يُملأ صفرًا ويُعالج
     * مع البقية، وتطبيع الذروة يضمن مخرجًا بلا تشبع مهما طالت السلسلة، وطول
     * كل خطوة يُوحَّد بالضبط (دخل + ذيل): المؤثرات المغيّرة للطول بنحو إطار
 * WSOLA تُحشى صفرًا فتعود لمدتها الموثقة بالضبط.
     */
    fun applySteps(pcm: ShortArray, steps: List<FxChainStep>): ShortArray {
        var cur = pcm
        for (step in steps) {
            val def = byId(step.defId) ?: continue
            val proc = def.factory(clampValues(def, step.values))
            val (l0, r0) = fxSplit(cur)
            val frames = l0.size
            val (l, r) = fxPadWithTail(l0, r0, frames, proc.tailSamples)
            val (ol, or_) = proc.process(l, r)
            fxNormalize(ol, or_)
            val wantFrames = frames + proc.tailSamples
            cur = fxJoin(fxAlign(ol, wantFrames), fxAlign(or_, wantFrames))
        }
        return cur
    }

    /** تسوية طول القناة إلى العدد المطلوب بالضبط: قصّ الفائض وحشو الناقص صفرًا. */
    private fun fxAlign(a: FloatArray, frames: Int): FloatArray = when {
        a.size == frames -> a
        a.size > frames -> a.copyOf(frames)
        else -> FloatArray(frames).also { System.arraycopy(a, 0, it, 0, a.size) }
    }

    /** تطبيق سلسلة من نصّها المشفَّر — بوابة الارتداد في ClipTransforms. */
    fun applySpec(pcm: ShortArray, spec: String): ShortArray =
        applySteps(pcm, parseChain(spec))

    // ================= أدوات معالجة داخلية =================

    private fun msToSamples(ms: Double): Int = (ms * RATE / 1000.0).toInt().coerceAtLeast(1)

    // ================= المعالجات الأربعة عشر =================

    /** صدى قاعة Freeverb — ثمانية شُرَب وأربعة ممرات لكل قناة + ذيل متدرج. */
    private class ReverbProcessor(values: Map<String, Double>) : FxProcessor {
        private val room = (values.getValue("room") / 100.0).toFloat()
        private val damp = (values.getValue("damp") / 100.0).toFloat()
        private val wet = (values.getValue("wet") / 100.0).toFloat()

        private val combTunings = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        private val apTunings = intArrayOf(556, 441, 341, 225)

        /** معايرة Freeverb بالعينات على معدل التطبيق — ذواكر 25–37ms كثيفة الرنين. */
        private fun samplesOf(n: Int): Int = ((n * RATE) / 44100).coerceAtLeast(1)

        private fun channel(offset: Int): Pair<List<FxComb>, List<FxAllpass>> {
            val off = samplesOf(offset)
            val combs = combTunings.map { t ->
                FxComb(FloatArray(samplesOf(t) + off)).also {
                    it.feedback = (0.72f + room * 0.26f)
                    it.damp = damp * 0.9f + 0.02f
                }
            }
            val aps = apTunings.map { t -> FxAllpass(FloatArray(samplesOf(t) + off)) }
            return combs to aps
        }

        private val chL = channel(0)
        private val chR = channel(23)
        private val combsL = chL.first
        private val apsL = chL.second
        private val combsR = chR.first
        private val apsR = chR.second

        override val tailSamples: Int =
            msToSamples(900.0 + room * 3100.0)

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            // حلقة موحّدة على الدخل والذيل معًا: منطقة الذيل صفرية الدخل فتستمر
            // الشُرَب بالرنين والانحلال كالقاعة الحقيقية بعد نهاية الصوت
            val dry = 1f - wet * 0.35f
            val wetGain = wet * 2.4f
            for (i in l.indices) {
                val inL = l[i] * 0.9f
                val inR = r[i] * 0.9f
                var outL = 0f
                var outR = 0f
                for (c in combsL) outL += c.process(inL)
                for (c in combsR) outR += c.process(inR)
                outL *= 0.125f
                outR *= 0.125f
                for (a in apsL) outL = a.process(outL)
                for (a in apsR) outR = a.process(outR)
                l[i] = l[i] * dry + outL * wetGain
                r[i] = r[i] * dry + outR * wetGain
            }
            return l to r
        }
    }

    /** تكرار متدرج بتغذية راجعة — بصمات متتابعة تذوب تدريجيًا. */
    private class EchoProcessor(values: Map<String, Double>) : FxProcessor {
        private val delayMs = values.getValue("delay")
        private val fb = (values.getValue("feedback") / 100.0).toFloat().coerceIn(0f, 0.88f)
        private val wet = (values.getValue("wet") / 100.0).toFloat()
        private val bufL: FloatArray
        private val bufR: FloatArray
        private var idx = 0

        init {
            val n = msToSamples(delayMs)
            bufL = FloatArray(n)
            bufR = FloatArray(n)
        }

        override val tailSamples: Int = run {
            // عدد التكرارات حتى يذوب البصم أسفل −54dB ثم سقف 14 تكرارًا
            var n = 1
            var amp = fb
            while (amp > 0.002 && n < 14) { amp *= fb; n++ }
            msToSamples(delayMs) * n
        }

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            val n = bufL.size
            for (i in l.indices) {
                val dL = bufL[idx]
                val dR = bufR[idx]
                bufL[idx] = l[i] + dL * fb
                bufR[idx] = r[i] + dR * fb
                if (++idx >= n) idx = 0
                l[i] = l[i] + dL * wet
                r[i] = r[i] + dR * wet
            }
            return l to r
        }
    }

    /** تأخير بطبقات ثابتة بلا تغذية راجعة — صدى نظيف متحكم فيه. */
    private class DelayProcessor(values: Map<String, Double>) : FxProcessor {
        private val taps: List<Pair<Int, Float>> = listOf(
            msToSamples(values.getValue("tap1")) to (values.getValue("tap1Level") / 100.0).toFloat(),
            msToSamples(values.getValue("tap2")) to (values.getValue("tap2Level") / 100.0).toFloat()
        ).filter { it.second > 0.001f }

        override val tailSamples: Int =
            // هامش 10ms فوق آخر طبقة كي لا تقع الطبقة عند نهاية الدخل خارج المصفوفة المحشوة
            (taps.maxOfOrNull { it.first } ?: 0) + RATE / 100

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            for ((delay, gain) in taps) {
                for (i in l.size - 1 downTo delay) {
                    l[i] += l[i - delay] * gain
                    r[i] += r[i - delay] * gain
                }
            }
            return l to r
        }
    }

    /** مضاعفة الإحساس — ثلاثة أصوات منزاحة زمنيًا بموجات بطيئة مختلفة. */
    private class ChorusProcessor(values: Map<String, Double>) : FxProcessor {
        private val depth = (values.getValue("depth") / 100.0).toFloat()
        private val wet = (values.getValue("wet") / 100.0).toFloat()
        private val maxDelayMs = 8.0 + depth * 12.0
        private val voices = listOf(
            Triple(0.35f, 0.0f, 1.0f),
            Triple(0.58f, 2.1f, 0.75f),
            Triple(0.81f, 4.4f, 0.6f)
        )

        override val tailSamples: Int = msToSamples(maxDelayMs + 10.0)

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            val bufL = FloatArray(msToSamples(maxDelayMs) + 2)
            val bufR = FloatArray(bufL.size)
            var pos = 0
            for (i in l.indices) {
                // ذاكرة دائرية قصيرة تُكتب بالترتيب وتُقرأ بإزاحات معدلة حيًّا
                bufL[pos] = l[i]
                bufR[pos] = r[i]
                var wl = 0f
                var wr = 0f
                val t = i / RATE.toFloat()
                for ((rateHz, phase, gain) in voices) {
                    val sweep = depth * (0.5f + 0.5f * sin(2.0 * PI * rateHz * t + phase)).toFloat()
                    val read = pos - msToSamples(8.0 + sweep * 12.0) + bufL.size
                    val i0 = ((read % bufL.size) + bufL.size) % bufL.size
                    wl += bufL[i0] * gain
                    wr += bufR[i0] * gain
                }
                pos = (pos + 1) % bufL.size
                l[i] = l[i] * (1f - wet * 0.6f) + (wl / 3f) * wet * 1.4f
                r[i] = r[i] * (1f - wet * 0.6f) + (wr / 3f) * wet * 1.4f
            }
            return l to r
        }
    }

    /** الحَرْف Flanger — تأخير قصير منسحاب بتغذية راجعة، طابع الطائرة المترددة. */
    private class FlangerProcessor(values: Map<String, Double>) : FxProcessor {
        private val rateHz = values.getValue("rate").toFloat()
        private val depthMs = values.getValue("depth")
        private val fb = (values.getValue("feedback") / 100.0).toFloat().coerceIn(0f, 0.9f)
        private val wet = (values.getValue("wet") / 100.0).toFloat()
        private val bufN = msToSamples(depthMs + 2.0)
        private val bufL = FloatArray(bufN)
        private val bufR = FloatArray(bufN)
        private var pos = 0

        override val tailSamples: Int = bufN

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            for (i in l.indices) {
                val t = i / RATE.toFloat()
                val sweep = depthMs * (0.5f + 0.5f * sin(2.0 * PI * rateHz * t)).toFloat()
                val read = pos - msToSamples(0.5 + sweep.toDouble())
                val i0 = ((read % bufN) + bufN) % bufN
                val dL = bufL[i0]
                val dR = bufR[i0]
                bufL[pos] = l[i] + dL * fb
                bufR[pos] = r[i] + dR * fb
                pos = (pos + 1) % bufN
                l[i] = l[i] + dL * wet
                r[i] = r[i] + dR * wet
            }
            return l to r
        }
    }

    /** الطور Phaser — سلسلة ممرات كلية تُسحب بموجة بطيئة فتتشابك الحُفر. */
    private class PhaserProcessor(values: Map<String, Double>) : FxProcessor {
        private val stages = values.getValue("stages").toInt().coerceIn(2, 8)
        private val rateHz = values.getValue("rate").toFloat()
        private val depth = (values.getValue("depth") / 100.0).toFloat()
        private val fb = (values.getValue("feedback") / 100.0).toFloat().coerceIn(0f, 0.85f)
        private val wet = (values.getValue("wet") / 100.0).toFloat()
        private val al = DoubleArray(stages)
        private val ar = DoubleArray(stages)
        private var fbL = 0.0
        private var fbR = 0.0
        private var lfoPhase = 0.0
        private val lfoInc = 2.0 * PI * rateHz / RATE

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            var tick = 0
            var fL = 0.0
            var fR = 0.0
            for (i in l.indices) {
                if (tick == 0) {
                    val s = 0.5 + 0.5 * sin(lfoPhase)
                    lfoPhase += lfoInc * 16
                    val f0L = 280.0 + s * depth * 4200.0
                    val t = tan(PI * f0L / RATE)
                    fL = (1.0 - t) / (1.0 + t)
                    val f0R = 285.0 + s * depth * 4200.0
                    val t2 = tan(PI * f0R / RATE)
                    fR = (1.0 - t2) / (1.0 + t2)
                }
                tick = (tick + 1) % 16
                var xL = l[i].toDouble() + fbL * fb
                var xR = r[i].toDouble() + fbR * fb
                for (st in 0 until stages) {
                    val yL = fL * xL + al[st]
                    al[st] = xL - fL * yL
                    xL = yL
                    val yR = fR * xR + ar[st]
                    ar[st] = xR - fR * yR
                    xR = yR
                }
                fbL = xL; fbR = xR
                l[i] = l[i] * (1f - wet * 0.5f) + (xL * wet).toFloat()
                r[i] = r[i] * (1f - wet * 0.5f) + (xR * wet).toFloat()
            }
            return l to r
        }
    }

    /** التشويش — منحنى تشبع tanh بدرجة قيادة ومرشح لون و مستوى. */
    private class DistortionProcessor(values: Map<String, Double>) : FxProcessor {
        private val drive = values.getValue("drive")
        private val toneL = FxOnePoleLp(RATE, values.getValue("tone").coerceIn(300.0, 18000.0))
        private val toneR = FxOnePoleLp(RATE, values.getValue("tone").coerceIn(300.0, 18000.0))
        private val level = (values.getValue("level") / 100.0).toFloat()
        private val a = (drive * drive / 40.0).toFloat().coerceAtLeast(0.2f)

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            val norm = tanh(a.toDouble()).toFloat().coerceAtLeast(0.05f)
            for (i in l.indices) {
                val shapedL = (tanh((l[i] * a).toDouble()) / norm).toFloat()
                val shapedR = (tanh((r[i] * a).toDouble()) / norm).toFloat()
                l[i] = toneL.process(shapedL) * level
                r[i] = toneR.process(shapedR) * level
            }
            return l to r
        }
    }

    /** توسيع الاستريو — منتصف/جانب بعرض متحكم + انزياح هاس اختياري للمونو. */
    private class WidenerProcessor(values: Map<String, Double>) : FxProcessor {
        private val width = (values.getValue("width") / 100.0).toFloat()
        private val haas = values.getValue("haas").toInt()

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            if (haas > 0) {
                val d = msToSamples(haas.toDouble())
                for (i in r.size - 1 downTo d) r[i] = r[i - d]
                for (i in 0 until d) r[i] = 0f
            }
            for (i in l.indices) {
                val m = (l[i] + r[i]) / 2f
                val s = (l[i] - r[i]) / 2f
                l[i] = m + s * width
                r[i] = m - s * width
            }
            return l to r
        }
    }

    /** ناقل النبرة — يعيد استخدام خوارزمية النبرة المجرّبة في v0.38.0. */
    private class PitchProcessor(values: Map<String, Double>) : FxProcessor {
        private val st = values.getValue("semitones").toInt()

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            val pcm = fxJoin(l, r)
            val out = ClipTransforms.pitchShift(pcm, st)
            return fxSplit(out)
        }
    }

    /** معزّز صوت المذيع — حضور وهواء ودفء + ركوب كسب ناعم للوضوح. */
    private class VocalEnhancerProcessor(values: Map<String, Double>) : FxProcessor {
        private val presenceDb = values.getValue("presence")
        private val warmthDb = values.getValue("warmth")
        private val clarity = (values.getValue("clarity") / 100.0).toFloat()
        private val hp = Biquad().also { it.highPass(RATE.toDouble(), 90.0) }
        private val warm = Biquad().also { it.peaking(RATE.toDouble(), 180.0, warmthDb, 0.9) }
        private val mud = Biquad().also { it.peaking(RATE.toDouble(), 380.0, -2.0, 1.1) }
        private val pres = Biquad().also { it.peaking(RATE.toDouble(), 3200.0, presenceDb, 0.9) }
        private val air = Biquad().also { it.highShelf(RATE.toDouble(), 8200.0, presenceDb * 0.45) }
        private val env = FxEnvelope(RATE, 8.0, 220.0)

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            val targetDb = -16.0
            for (i in l.indices) {
                var vl = hp.process(l[i].toDouble())
                var vr = hp.process(r[i].toDouble())
                vl = warm.process(vl); vr = warm.process(vr)
                vl = mud.process(vl); vr = mud.process(vr)
                vl = pres.process(vl); vr = pres.process(vr)
                vl = air.process(vl); vr = air.process(vr)
                // ركوب الكسب: يرفع الهمس وينزل الزعيق بحدود ±9dB بتركيز واضح
                val block = ((vl + vr) / 2.0)
                env.process(block.toFloat())
                val levelDb = Pcm16.ampToDb(max(env.env.toDouble(), 1e-6))
                val wanted = ((targetDb - levelDb).coerceIn(-6.0, 9.0)) * clarity
                val g = Pcm16.dbToAmp(wanted)
                l[i] = vl.toFloat() * g.toFloat()
                r[i] = vr.toFloat() * g.toFloat()
            }
            return l to r
        }
    }

    /** مؤثر الراديو — نطاق إذاعي ضيق بقيادة تشويش وهمس ضجيج خلفي. */
    private class RadioProcessor(values: Map<String, Double>) : FxProcessor {
        private val intensity = (values.getValue("intensity") / 100.0).toFloat()
        private val hp = Biquad().also { it.highPass(RATE.toDouble(), 380.0) }
        private val hp2 = Biquad().also { it.highPass(RATE.toDouble(), 380.0) }
        private val lp = Biquad().also { it.lowShelf(RATE.toDouble(), 2700.0, -18.0) }
        private val drive = 3.0 + intensity * 14.0

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            for (i in l.indices) {
                var vl = hp2.process(hp.process(l[i].toDouble()))
                var vr = hp2.process(hp.process(r[i].toDouble()))
                vl = lp.process(vl); vr = lp.process(vr)
                vl = tanh(vl * drive) / tanh(drive)
                vr = tanh(vr * drive) / tanh(drive)
                val noise = (Math.random() * 2.0 - 1.0) * 0.004 * intensity
                l[i] = (vl + noise).toFloat()
                r[i] = (vr + noise).toFloat()
            }
            return l to r
        }
    }

    /** مؤثر الهاتف — نطاق هاتفي 300–3400 هرتز بضغط ناعم. */
    private class TelephoneProcessor(values: Map<String, Double>) : FxProcessor {
        private val intensity = (values.getValue("intensity") / 100.0).toFloat()
        private val hp = Biquad().also { it.highPass(RATE.toDouble(), 300.0, 0.85) }
        private val lp = Biquad().also { it.lowShelf(RATE.toDouble(), 3400.0, -30.0) }
        private val drive = 2.5 + intensity * 6.0

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            for (i in l.indices) {
                var vl = hp.process(l[i].toDouble())
                var vr = hp.process(r[i].toDouble())
                vl = lp.process(vl); vr = lp.process(vr)
                vl = tanh(vl * drive) / tanh(drive)
                vr = tanh(vr * drive) / tanh(drive)
                l[i] = (l[i] * (1f - intensity) + vl.toFloat() * intensity)
                r[i] = (r[i] * (1f - intensity) + vr.toFloat() * intensity)
            }
            return l to r
        }
    }

    /** صوت الروبوت — تضمين حلقي بموجة حملة فتتولد حواف ميكانيكية. */
    private class RobotProcessor(values: Map<String, Double>) : FxProcessor {
        private val carrier = values.getValue("carrier").toDouble()
        private val mix = (values.getValue("mix") / 100.0).toFloat()
        private val hp = Biquad().also { it.highPass(RATE.toDouble(), 110.0) }

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            for (i in l.indices) {
                val t = i.toDouble() / RATE
                val ring = sin(2.0 * PI * carrier * t).toFloat()
                val vl = hp.process((l[i] * ring).toDouble()).toFloat()
                val vr = hp.process((r[i] * ring).toDouble()).toFloat()
                l[i] = l[i] * (1f - mix) + vl * mix
                r[i] = r[i] * (1f - mix) + vr * mix
            }
            return l to r
        }
    }

    /**
     * مبدّل الطبيعة الصوتية الإبداعي — أربعة أنماط تجمع النبرة والمعادل
     * والتضمين الحلقي والتلاشيات: أنثوي، رجولي عميق، كائن فضائي، عملاق.
     */
    private class MorpherProcessor(values: Map<String, Double>) : FxProcessor {
        private val mode = values.getValue("mode").toInt().coerceIn(0, 3)
        private val intensity = (values.getValue("intensity") / 100.0).toFloat()

        override val tailSamples: Int = when (mode) {
            2 -> msToSamples(260.0 * 8)
            3 -> msToSamples(900.0 + 0.3 * 3100.0)
            else -> 0
        }

        override fun process(l: FloatArray, r: FloatArray): Pair<FloatArray, FloatArray> {
            val pcm = fxJoin(l, r)
            var out = pcm
            when (mode) {
                0 -> {
                    // أنثوي: نبرة أعلى + حضور وهواء + هُمس كورال خفيف
                    out = ClipTransforms.pitchShift(pcm, (3 + intensity * 2).toInt().coerceIn(3, 5))
                    val (ol, or_) = fxSplit(out)
                    val pres = Biquad().also { it.peaking(RATE.toDouble(), 3100.0, 4.0 + intensity * 2.0, 0.9) }
                    val air = Biquad().also { it.highShelf(RATE.toDouble(), 8000.0, 3.0) }
                    for (i in ol.indices) {
                        val vl = pres.process(air.process(ol[i].toDouble())).toFloat()
                        val vr = pres.process(air.process(or_[i].toDouble())).toFloat()
                        val t = i / RATE.toFloat()
                        val d = ((depthMs() * (0.5f + 0.5f * sin(2.0 * PI * 0.55 * t))).toInt()).coerceAtLeast(1)
                        val j = (i - d).coerceAtLeast(0)
                        ol[i] = vl * (1f - 0.22f * intensity) + out[2 * j] / 32768f * 0.22f * intensity
                        or_[i] = vr * (1f - 0.22f * intensity) + out[2 * j + 1] / 32768f * 0.22f * intensity
                    }
                    return ol to or_
                }
                1 -> {
                    // رجولي عميق: نبرة أخفض + دفء باص + تشبع ناعم خفيف
                    out = ClipTransforms.pitchShift(pcm, -(3 + intensity * 2).toInt().coerceIn(3, 5))
                    val (ol, or_) = fxSplit(out)
                    val bass = Biquad().also { it.peaking(RATE.toDouble(), 150.0, 3.5 + intensity * 2.0, 0.9) }
                    val pres = Biquad().also { it.peaking(RATE.toDouble(), 2900.0, -1.5, 1.0) }
                    val a = (1.5 + intensity * 2.5).toDouble()
                    val norm = tanh(a)
                    for (i in ol.indices) {
                        val vl = bass.process(pres.process(ol[i].toDouble()))
                        val vr = bass.process(pres.process(or_[i].toDouble()))
                        ol[i] = (tanh(vl * a) / norm).toFloat()
                        or_[i] = (tanh(vr * a) / norm).toFloat()
                    }
                    return ol to or_
                }
                2 -> {
                    // كائن فضائي: نبرة أعلى + تضمين حلقي 70هرتز + تكرار فضائي
                    out = ClipTransforms.pitchShift(pcm, (2 + intensity * 3).toInt().coerceIn(2, 5))
                    val (ol, or_) = fxSplit(out)
                    val dL = FloatArray(msToSamples(190.0))
                    val dR = FloatArray(dL.size)
                    var pos = 0
                    for (i in ol.indices) {
                        val t = i.toDouble() / RATE
                        val ring = sin(2.0 * PI * 70.0 * t).toFloat()
                        val vl = ol[i] * ring
                        val vr = or_[i] * ring
                        dL[pos] = vl; dR[pos] = vr
                        val dOutL = dL[pos]; val dOutR = dR[pos]
                        pos = (pos + 1) % dL.size
                        ol[i] = vl * 0.7f + dOutL * 0.35f * intensity
                        or_[i] = vr * 0.7f + dOutR * 0.35f * intensity
                    }
                    return ol to or_
                }
                else -> {
                    // عملاق هائل: نبرة أخفض كثيرًا + باص ثقيل + صدى قاعة خفيف
                    out = ClipTransforms.pitchShift(pcm, -(6 + intensity * 3).toInt().coerceIn(6, 9))
                    val (ol, or_) = fxSplit(out)
                    val bass = Biquad().also { it.peaking(RATE.toDouble(), 130.0, 4.5, 0.8) }
                    val drv = (2.0 + intensity * 3.0).toDouble()
                    val norm = tanh(drv)
                    val tunings = intArrayOf(1116, 1277, 1422, 1557)
                    val combsL = tunings.map { FxComb(FloatArray(it)) }
                    val combsR = tunings.map { FxComb(FloatArray(it + 23)) }
                    combsL.forEach { it.feedback = 0.8f; it.damp = 0.25f }
                    combsR.forEach { it.feedback = 0.8f; it.damp = 0.25f }
                    for (i in ol.indices) {
                        val vl = bass.process(ol[i].toDouble()).toFloat()
                        val vr = bass.process(or_[i].toDouble()).toFloat()
                        ol[i] = (tanh(vl * drv) / norm).toFloat()
                        or_[i] = (tanh(vr * drv) / norm).toFloat()
                        var wL = 0f; var wR = 0f
                        for (c in combsL) wL += c.process(ol[i])
                        for (c in combsR) wR += c.process(or_[i])
                        ol[i] += wL / 4f * 0.5f * intensity
                        or_[i] += wR / 4f * 0.5f * intensity
                    }
                    return ol to or_
                }
            }
        }

        private fun depthMs(): Int = msToSamples(5.0)
    }

    // ================= السجل =================

    val all: List<FxDef> = listOf(
        FxDef(
            "reverb", "الصدى (Reverb)", "قاعة Freeverb كاملة — ثمانية شُرَب وأربعة ممرات لكل قناة بذيل متدرج يضيف عمق المكان.",
            listOf(
                FxParam("room", "حجم القاعة", 0.0, 100.0, 45.0, 1.0, "٪"),
                FxParam("damp", "الترطيب", 0.0, 100.0, 20.0, 1.0, "٪"),
                FxParam("wet", "شدة الرطوب", 0.0, 100.0, 22.0, 1.0, "٪")
            )
        ) { v -> ReverbProcessor(v) },
        FxDef(
            "echo", "التكرار (Echo)", "بصمات متتابعة تذوب تدريجيًا بتغذية راجعة — كلاسيكية الإذاعات بلا رنين.",
            listOf(
                FxParam("delay", "زمن التكرار", 50.0, 1200.0, 320.0, 10.0, "مللي ثانية"),
                FxParam("feedback", "التغذية الراجعة", 0.0, 85.0, 45.0, 5.0, "٪"),
                FxParam("wet", "شدة المزج", 0.0, 100.0, 40.0, 5.0, "٪")
            )
        ) { v -> EchoProcessor(v) },
        FxDef(
            "delay", "التأخير (Delay)", "طبقتان ثابتتان بلا تغذية راجعة — صدى نظيف يضيف اتساعًا متحكمًا فيه.",
            listOf(
                FxParam("tap1", "الطبقة الأولى", 10.0, 2000.0, 280.0, 10.0, "مللي ثانية"),
                FxParam("tap1Level", "مستوى الأولى", 0.0, 100.0, 45.0, 5.0, "٪"),
                FxParam("tap2", "الطبقة الثانية", 10.0, 2000.0, 640.0, 10.0, "مللي ثانية"),
                FxParam("tap2Level", "مستوى الثانية", 0.0, 100.0, 22.0, 5.0, "٪")
            )
        ) { v -> DelayProcessor(v) },
        FxDef(
            "chorus", "مضاعفة الإحساس (Chorus)", "ثلاثة أصوات منزاحة بموجات بطيئة مختلفة — كثافة ودفء للمقطع الواحد.",
            listOf(
                FxParam("depth", "عمق الموجة", 0.0, 100.0, 60.0, 5.0, "٪"),
                FxParam("wet", "شدة المزج", 0.0, 100.0, 45.0, 5.0, "٪")
            )
        ) { v -> ChorusProcessor(v) },
        FxDef(
            "flanger", "الحَرْف (Flanger)", "تأخير قصير منسحاب بتغذية راجعة — الصوت المعدني المتردد الخاشع.",
            listOf(
                FxParam("rate", "سرعة الانسحاب", 0.05, 5.0, 0.28, 0.01, "هرتز"),
                FxParam("depth", "عمق الانسحاب", 0.5, 8.0, 3.2, 0.1, "مللي ثانية"),
                FxParam("feedback", "التغذية الراجعة", 0.0, 90.0, 55.0, 5.0, "٪"),
                FxParam("wet", "شدة المزج", 0.0, 100.0, 40.0, 5.0, "٪")
            )
        ) { v -> FlangerProcessor(v) },
        FxDef(
            "phaser", "الطور (Phaser)", "سلسلة ممرات كلية تُسحب بموجة بطيئة فتتشابك حفر الطور وتنتفخ.",
            listOf(
                FxParam("stages", "عدد المراحل", 2.0, 8.0, 4.0, 1.0, "مرحلة"),
                FxParam("rate", "سرعة السحب", 0.05, 5.0, 0.45, 0.05, "هرتز"),
                FxParam("depth", "عمق السحب", 0.0, 100.0, 70.0, 5.0, "٪"),
                FxParam("feedback", "التغذية الراجعة", 0.0, 80.0, 35.0, 5.0, "٪"),
                FxParam("wet", "شدة المزج", 0.0, 100.0, 45.0, 5.0, "٪")
            )
        ) { v -> PhaserProcessor(v) },
        FxDef(
            "distortion", "التشويش (Distortion)", "منحنى تشبع tanh بقيادة متدرجة ومرشح لون — من التفتيح الناعم إلى الخشونة.",
            listOf(
                FxParam("drive", "القيادة", 1.0, 100.0, 12.0, 1.0, "درجة"),
                FxParam("tone", "لون الصوت", 1000.0, 16000.0, 8000.0, 500.0, "هرتز"),
                FxParam("level", "المستوى", 20.0, 100.0, 85.0, 5.0, "٪")
            )
        ) { v -> DistortionProcessor(v) },
        FxDef(
            "widener", "توسيع الاستريو", "معالجة منتصف/جانب بعرض متحكم، وانزياح هاس اختياري يفتح المونو الخالص.",
            listOf(
                FxParam("width", "العرض", 0.0, 200.0, 135.0, 5.0, "٪"),
                FxParam("haas", "انزياح هاس", 0.0, 20.0, 0.0, 1.0, "مللي ثانية")
            )
        ) { v -> WidenerProcessor(v) },
        FxDef(
            "pitch", "ناقل النبرة (Pitch Shifter)", "±12 نغمة موسيقية دون تغيير الطول — نفس خوارزمية النبرة المجرّبة في أدوات المقطع.",
            listOf(
                FxParam("semitones", "أنصاف النغمات", -12.0, 12.0, 0.0, 1.0, "نغمة")
            )
        ) { v -> PitchProcessor(v) },
        FxDef(
            "vocal", "معزّز صوت المذيع", "حضور 3.2ك وهواء 8ك ودفء 180هرتز مع ركوب كسب ناعم — وضوح بلا زعيق.",
            listOf(
                FxParam("presence", "الحضور", 0.0, 8.0, 4.0, 0.5, "dB"),
                FxParam("warmth", "الدفء", 0.0, 6.0, 2.0, 0.5, "dB"),
                FxParam("clarity", "الوضوح (ركوب الكسب)", 0.0, 100.0, 55.0, 5.0, "٪")
            )
        ) { v -> VocalEnhancerProcessor(v) },
        FxDef(
            "radio", "مؤثر الراديو", "نطاق إذاعي ضيق بقيادة تشويش وهمس ضجيج خلفي — طابع المذياع القديم.",
            listOf(
                FxParam("intensity", "الشدة", 0.0, 100.0, 60.0, 5.0, "٪")
            )
        ) { v -> RadioProcessor(v) },
        FxDef(
            "telephone", "مؤثر الهاتف", "نطاق هاتفي 300–3400 هرتز — كأن المقطع يُبث من سماعة هاتف.",
            listOf(
                FxParam("intensity", "الشدة", 0.0, 100.0, 85.0, 5.0, "٪")
            )
        ) { v -> TelephoneProcessor(v) },
        FxDef(
            "robot", "صوت الروبوت", "تضمين حلقي بموجة حملة متحكمة — حواف ميكانيكية على المقطع كله.",
            listOf(
                FxParam("carrier", "تردد الحاملة", 15.0, 120.0, 45.0, 5.0, "هرتز"),
                FxParam("mix", "شدة المزج", 0.0, 100.0, 80.0, 5.0, "٪")
            )
        ) { v -> RobotProcessor(v) },
        FxDef(
            "morpher", "مبدّل الطبيعة الصوتية", "تحويل إبداعي لطبيعة الصوت — 0 أنثوي، 1 رجولي عميق، 2 كائن فضائي، 3 عملاق هائل.",
            listOf(
                FxParam("mode", "النمط", 0.0, 3.0, 0.0, 1.0, ""),
                FxParam("intensity", "الشدة", 10.0, 100.0, 70.0, 5.0, "٪")
            )
        ) { v -> MorpherProcessor(v) }
    )

    /** المؤثر بالمعرّف — نصف الرسالة العربية عند عدم الوجود. */
    fun byId(id: String): FxDef? = all.firstOrNull { it.id == id }

    /** وصف نمط المبدّل بالعربية لعرضه في الحوار. */
    fun morpherModeName(mode: Int): String = when (mode.coerceIn(0, 3)) {
        0 -> "أنثوي"
        1 -> "رجولي عميق"
        2 -> "كائن فضائي"
        else -> "عملاق هائل"
    }
}

// ================= مكتبة الجاهزات (Presets) =================

/**
 * جاهزة سلسلة مؤثرات معرّبة — نقرة واحدة تُطبّق السلسلة كاملة على المحدد،
 * وكل خطوة تُقيَّد داخل نطاقاتها فلا جاهزة تُخرج صوتًا معطوبًا. الجاهزات
 * تشارك بروتوكول الارتداد المخزَّن نفسه فتكرارها بلا حساب ثانٍ.
 */
data class FxPreset(
    val id: String,
    val nameAr: String,
    val descAr: String,
    val steps: List<FxChainStep>
)

object FxPresets {

    /** تكوين قيم مؤثر واحد داخل الجاهزة — قيم مُهذّبة مسبقًا بعرف الاستوديو. */
    private fun step(defId: String, vararg pairs: Pair<String, Double>): FxChainStep {
        val def = AudioFx.byId(defId) ?: throw IllegalStateException("مؤثر مجهول في الجاهزات: $defId")
        return FxChainStep(defId, AudioFx.clampValues(def, pairs.toMap()))
    }

    val all: List<FxPreset> = listOf(
        FxPreset(
            "studio_voice", "تعليق استوديو صافٍ",
            "معزّز المذيع ثم صدى خفيف — النبرة الواضحة الجاهزة لكل إعلان صباحي.",
            listOf(
                step("vocal", "presence" to 4.0, "warmth" to 2.0, "clarity" to 60.0),
                step("reverb", "room" to 30.0, "damp" to 25.0, "wet" to 10.0)
            )
        ),
        FxPreset(
            "school_hall", "قاعة مدرسية",
            "صدى قاعة واسع — كأن التسجيل صُوِّر وسط الصف بعد الحصة.",
            listOf(
                step("reverb", "room" to 72.0, "damp" to 18.0, "wet" to 28.0)
            )
        ),
        FxPreset(
            "female_anchor", "مذيعة أنثوية",
            "تحويل الطبيعة إلى صوت أنثوي ثم تلميع حضور المذيع.",
            listOf(
                step("morpher", "mode" to 0.0, "intensity" to 75.0),
                step("vocal", "presence" to 3.0, "warmth" to 1.0, "clarity" to 50.0)
            )
        ),
        FxPreset(
            "deep_anchor", "مذيع عميق الرنين",
            "نبرة رجولية عميقة بدفء باص وتشبع ناعم — صوت النشرات الرصينة.",
            listOf(
                step("morpher", "mode" to 1.0, "intensity" to 80.0),
                step("vocal", "presence" to 2.0, "warmth" to 3.0, "clarity" to 45.0)
            )
        ),
        FxPreset(
            "old_radio", "إذاعة عتيقة",
            "نطاق هاتفي مع تشويش خفيف — سحر المذياع القديم في الفواصل.",
            listOf(
                step("telephone", "intensity" to 85.0),
                step("distortion", "drive" to 8.0, "tone" to 5000.0, "level" to 80.0)
            )
        ),
        FxPreset(
            "news_robot", "روبوت النشرات",
            "تضمين حلقي مع نطاق هاتفي — الروبوت المذيع في العروض اليومية.",
            listOf(
                step("robot", "carrier" to 45.0, "mix" to 85.0),
                step("telephone", "intensity" to 70.0)
            )
        ),
        FxPreset(
            "alien", "كائن فضائي",
            "تحويل فضائي بتضمين حلقي وتكرار متطاير — شخصية فكاهية للمسرح الإذاعي.",
            listOf(
                step("morpher", "mode" to 2.0, "intensity" to 85.0),
                step("echo", "delay" to 240.0, "feedback" to 35.0, "wet" to 35.0)
            )
        ),
        FxPreset(
            "giant", "عملاق هائل",
            "نبرة أخفض كثيرًا بباص ثقيل وقاعة — العملاق في المشاهد الدرامية.",
            listOf(
                step("morpher", "mode" to 3.0, "intensity" to 90.0)
            )
        ),
        FxPreset(
            "spectral", "طيف شبحي",
            "كورال متعدد مع صدى واسع — الصوت الطيفي للقصص المهيبة.",
            listOf(
                step("chorus", "depth" to 70.0, "wet" to 40.0),
                step("reverb", "room" to 55.0, "damp" to 15.0, "wet" to 22.0)
            )
        ),
        FxPreset(
            "cinematic_wide", "واسع سينمائي",
            "توسيع استريو مع انزياح هاس وصدى خفيف — اتساع مشهدي كامل.",
            listOf(
                step("widener", "width" to 165.0, "haas" to 8.0),
                step("reverb", "room" to 40.0, "damp" to 20.0, "wet" to 14.0)
            )
        ),
        FxPreset(
            "sports_announcer", "معلّق ملاعب",
            "حضور مرتفع مع تشويش خفيف — صوت المعلّق الحماسي فوق الجمهور.",
            listOf(
                step("vocal", "presence" to 6.0, "warmth" to 3.0, "clarity" to 70.0),
                step("distortion", "drive" to 5.0, "tone" to 10000.0, "level" to 90.0)
            )
        ),
        FxPreset(
            "distant_echo", "نداء بعيد",
            "تكرار طويل متلاشٍ فوق قاعة — نداء من وادٍ بعيد في المشاهد.",
            listOf(
                step("delay", "tap1" to 420.0, "tap1Level" to 55.0, "tap2" to 900.0, "tap2Level" to 35.0),
                step("reverb", "room" to 65.0, "damp" to 22.0, "wet" to 24.0)
            )
        )
    )

    /** الجاهزة بالمعرّف — نصف الرسالة العربية عند عدم الوجود. */
    fun byId(id: String): FxPreset? = all.firstOrNull { it.id == id }

    /** تحويل الجاهزة إلى سلسلة تشفير تشارك الارتداد المخزَّن. */
    fun encode(preset: FxPreset): String = AudioFx.encodeChain(preset.steps)
}
