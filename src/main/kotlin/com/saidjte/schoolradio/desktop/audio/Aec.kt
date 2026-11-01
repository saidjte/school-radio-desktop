package com.saidjte.schoolradio.desktop.audio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * إلغاء صدى الميكروفون (AEC) — نقيّ بلا أي عتاد أو مكتبات خارجية:
 *
 * المشهد الإذاعي الحقيقي: الموسيقى تخرج من السماعات، والميكروفون في الغرفة نفسها
 * يلتقطها متأخرة ومخمّدة فتتسرب إلى هواء البث والتسجيل كصدي يفسد صوت المذيع.
 * الحل هنا محو تكيّفي (NLMS): نعرف «ما يُكتب في السماعة» بدقة (مرجع الطرف البعيد)،
 * فلتر تكيّفي يتعلم مسار الصوت من السماعة إلى الميكروفون ويطرحه من إشارة الميكروفون،
 * فيبقى صوت المذيع صافيًا والموسيقى في قناتها وحدها.
 *
 * المكوّنان:
 * - FarEndTap: حلقة مرجعية تُغذّى من قناة الموسيقى لحظة معالجة كل كتلة تكتب في الخط،
 *   وتُعيد النافذة المطلوبة مُعادة القياس خطيًا إلى معدل عينات الميكروفون
 *   (كلٌّ بساعته — لا انجراف بين المعدلين).
 * - EchoCanceller: مرشح NLMS بعدد مرشحات لكل قناة ميكروفون، مع بحث دوري عن
 *   التأخير الكلي (مسافة السماعة + مؤخرة خط الخرج)، وتجميد يد التكيّف عند صمت
 *   المرجع كي لا يطارد الضجيج، وحارس انحراف يعيد التصفير إن تدهور المسار.
 */

// ================= مرجع الطرف البعيد =================

/**
 * حلقة زمنية تحفظ آخر ثانيتين من إشارة السماعة (أحادية متوسط القنوات).
 * الكتابة من خيط تشغيل الموسيقى والقراءة من خيط البث المباشر — قسمان حرجان قصيران.
 */
class FarEndTap(private val capacitySeconds: Double = 2.0) {

    private val lock = Any()
    private var rate = 0f
    private var ring = FloatArray(0)
    private var writePos = 0
    private var filled = 0
    private var total = 0L                    // إجمالي العينات منذ configure
    private var newestTime = 0.0              // زمن أحدث عيّنة بساعة المرجع (ثوانٍ)

    @Volatile private var lastPushWallMs = 0L // لحظة آخر كتابة بساعة الحائط

    /** يُستدعى عند فتح قناة موسيقى بمعدل جديد (أو إعادة فتحها). */
    fun configure(rateHz: Int) {
        synchronized(lock) {
            rate = rateHz.coerceAtLeast(8000).toFloat()
            ring = FloatArray((capacitySeconds * rate).toInt().coerceAtLeast(8000))
            writePos = 0; filled = 0; total = 0L; newestTime = 0.0
            lastPushWallMs = 0L
        }
    }

    /** كم مضى منذ آخر إعطاء مرجع؟ (راكد = لا موسيقى تُشغَّل الآن). */
    fun staleMs(): Long = if (lastPushWallMs == 0L) Long.MAX_VALUE
    else System.nanoTime() / 1_000_000L - lastPushWallMs

    /** يكتب كتلة أحادية من إشارة السماعة الفعلية (بعد المعادل والفيدر). */
    fun push(mono: FloatArray, count: Int) {
        if (count <= 0) return
        synchronized(lock) {
            if (rate <= 0f || ring.isEmpty()) return
            val cap = ring.size
            for (i in 0 until count) {
                ring[writePos] = mono[i]
                writePos++
                if (writePos == cap) writePos = 0
            }
            filled = min(filled + count, cap)
            total += count
            newestTime = total / rate.toDouble()
            lastPushWallMs = System.nanoTime() / 1_000_000L
        }
    }

    /**
     * يقرأ نافذة من المرجع تنتهي قبل أحدث عيّنة بمقدار delaySeconds،
     * مُعادة القياس خطيًا إلى معدل الميكروفون.
     * الاصطلاح الزمني: العيّنة المرجعية رقم j تقع في اللحظة (j+1)/المعدل،
     * فالاستعلام عن لحظة عيّنة يعيد قيمتها تمامًا بلا انزياح.
     * تعيد false إن لم يتوفر تاريخ كافٍ.
     */
    fun readWindow(micRate: Int, frames: Int, delaySeconds: Double, out: FloatArray): Boolean {
        if (frames <= 0) return false
        synchronized(lock) {
            if (rate <= 0f || filled < ring.size / 2) return false
            val endT = newestTime - delaySeconds
            val startT = endT - frames.toDouble() / micRate
            val oldestT = (total - filled + 1) / rate
            if (startT < oldestT || endT > newestTime) return false
            val cap = ring.size
            val newestIdx = total - 1L
            for (i in 0 until frames) {
                // زمن عيّنة الميكروفون i: نهاية مداها (اصطلاح موحّد مع المرجع)
                val t = startT + (i + 1).toDouble() / micRate
                val pos = t * rate - 1.0              // موضع زمني بوحدة عيّنات المرجع
                val j0 = floor(pos).toInt()
                val frac = pos - j0
                val back0 = (newestIdx - j0).toInt()  // كم عيّنة خلف الأحدث
                val p0 = ((writePos - 1 - back0) % cap + cap) % cap
                val v0 = ring[p0].toDouble()
                val v = if (frac <= 0.0) v0 else {
                    val p1 = (p0 + 1) % cap
                    v0 + (ring[p1].toDouble() - v0) * frac
                }
                out[i] = v.toFloat()
            }
            return true
        }
    }
}

/** المرجع المشترك الوحيد بين قناة الموسيقى (كتابة) وقناة الميكروفون (قراءة). */
object MusicFarEnd {
    val tap = FarEndTap()
}

// ================= مانع الصدى التكيّفي =================

/**
 * مانع صدى تكيّفي NLMS يعمل على كتل الميكروفون في مكانها:
 *
 *   ميكروفون ← [AEC: طرح تقدير الصدى من مرجع الموسيقى] ← بقية سلسلة المذيع
 *
 * - بحث دوري كل ~1.5 ثانية عن التأخير الكلي (ارتباط مضغوط 1:16 على ±750ms).
 * - تكيّف NLMS مطبّع بالطاقة، وتجميد كامل عند صمت المرجع (لا مطاردة ضجيج).
 * - حارس انحراف: إن صار الخرج أسوأ من الدخل عاد المرشح للتصفير وأُعيد البحث فورًا.
 * - صمت المرجع أو ركوده أو تعطيل المعالجة = مرور نظيف بلا أي مساس.
 */
class EchoCanceller {

    companion object {
        /** مرشحات المسار الدقيق: ~11ms عند 48k تغطي الانعكاسات المبكرة. */
        const val TAPS = 512
        /** مدى البحث عن التأخير الكلي بالمللي ثانية. */
        const val SEARCH_SPAN_MS = 750.0
        /** خطوة البحث بالمللي ثانية. */
        const val SEARCH_STEP_MS = 12.0
        /** عامل التكيّف المطبّع. */
        const val MU = 0.25
        /** كثافة الطاقة الدنيا كي يُعد المرجع ناطقًا لا صمتًا. */
        const val REF_FLOOR = 1e-5
        /** عتبة قفل المسار: تحسّن الإرجاع بالديسيبل. */
        const val LOCK_DB = 6.0
    }

    private var sampleRate = 0
    private var channels = 0

    // أوزان مسار الصدى لكل قناة ميكروفون
    private var w: Array<DoubleArray> = emptyArray()

    // ذاكرة مرجع متصلة: [TAPS-1 تاريخًا] ثم [إطارات الكتلة الحالية]
    private var hist = DoubleArray(TAPS - 1)
    private var refWin = FloatArray(0)
    private var searchTmp = FloatArray(0)
    private var decMic = FloatArray(0)
    private var decRef = FloatArray(0)

    private var delaySeconds = 0.10        // التقدير الأولي: 100ms
    private var sinceSearchBlocks = 0
    private var forceSearchBlocks = 0

    // قياس جودة متدحرج: تحسّن الإرجاع بالديسيبل
    private var qualityDb = 0.0

    /** التأخير المقدَّر حاليًا بالمللي ثانية (للعرض الحي). */
    @Volatile var delayMs = 0f
        private set

    /** هل المسار مقفل والإلغاء فعلي جارٍ؟ */
    @Volatile var locked = false
        private set

    /** تفعيل المعالجة (يُغيَّر أثناء البث لحظيًا). */
    @Volatile var enabled = true

    /** يُستدعى عند فتح قناة الميكروفون أو تغيّر صيغتها. */
    fun configure(sampleRate: Int, channels: Int) {
        val fs = sampleRate.coerceAtLeast(8000)
        val ch = channels.coerceIn(1, 2)
        this.sampleRate = fs; this.channels = ch
        w = Array(ch) { DoubleArray(TAPS) }
        refWin = FloatArray(fs / 10)          // يكفي كتلة 100ms
        searchTmp = FloatArray(fs / 10)
        decMic = FloatArray(64); decRef = FloatArray(64)
        delaySeconds = 0.10
        sinceSearchBlocks = 0
        forceSearchBlocks = 0
        qualityDb = 0.0
        locked = false
        hist = DoubleArray(TAPS - 1)
    }

    /** تصفير كامل (عند بدء البث). */
    fun reset() {
        w.forEach { it.fill(0.0) }
        hist.fill(0.0)
        qualityDb = 0.0
        locked = false
        sinceSearchBlocks = 0
        forceSearchBlocks = 0
    }

    /**
     * يعالج كتلة ميكروفون في مكانها مقابل مرجع الطرف البعيد.
     * يعيد true إن جرى طرح فعلي (مرجع ناطق والمسار صالح).
     */
    fun process(shorts: ShortArray, count: Int, tap: FarEndTap?): Boolean {
        if (!enabled || tap == null || sampleRate <= 0 || count <= 0) {
            locked = false
            return false
        }
        val frames = count / channels
        if (frames <= 0) return false
        if (refWin.size < frames) { refWin = FloatArray(frames); searchTmp = FloatArray(frames) }

        // 0) مرجع راكد (لا موسيقى الآن) = مرور نظيف تام
        if (tap.staleMs() > 400) { locked = false; return false }
        if (!tap.readWindow(sampleRate, frames, delaySeconds, refWin)) {
            locked = false
            return false
        }

        var refEnergy = 0.0
        for (f in 0 until frames) { val v = refWin[f].toDouble(); refEnergy += v * v }
        refEnergy /= frames
        if (refEnergy <= REF_FLOOR) { locked = false; return false }

        // 1) البحث الدوري عن التأخير الكلي (أو فوري بعد انحراف)
        sinceSearchBlocks++
        val blockMs = frames * 1000.0 / sampleRate
        val searchInterval = (1500.0 / blockMs).toInt().coerceAtLeast(4)
        if (sinceSearchBlocks >= searchInterval || forceSearchBlocks > 0) {
            sinceSearchBlocks = 0
            if (forceSearchBlocks > 0) forceSearchBlocks--
            runSearch(shorts, count, frames, tap)
        }

        // 2) الطرح التكيّفي NLMS لكل قناة — تطبيع بالطاقة العامة للكتلة
        if (hist.size < TAPS - 1 + frames) hist = DoubleArray(TAPS - 1 + frames)
        val h = hist
        for (f in 0 until frames) h[TAPS - 1 + f] = refWin[f].toDouble()

        val norm = MU / (refEnergy * TAPS + 1e-12)
        val micRms = rmsOf(shorts, count)
        var resEnergy = 0.0
        for (c in 0 until channels) {
            val wc = w[c]
            var i = c
            var f = 0
            while (f < frames) {
                val x = shorts[i] / 32768.0
                var y = 0.0
                var k = 0
                var hi = TAPS - 1 + f
                while (k < TAPS) {
                    y += wc[k] * h[hi]
                    k++; hi--
                }
                val e0 = x - y
                // تحديث الأوزان فقط ما دام الخطأ في نطاق مسار مستقر
                if (abs(e0) <= abs(x) * 2.0 + 0.05) {
                    val delta = norm * e0
                    k = 0; hi = TAPS - 1 + f
                    while (k < TAPS) {
                        wc[k] += delta * h[hi]
                        k++; hi--
                    }
                }
                var e = e0
                if (e > 1.0) e = 1.0 else if (e < -1.0) e = -1.0
                resEnergy += e0 * e0
                shorts[i] = (e * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                i += channels
                f++
            }
        }
        // إزاحة التاريخ: آخر TAPS-1 عيّنة مرجعية تبقى للكتلة التالية
        if (h.size >= TAPS - 1 + frames) {
            val keep = min(TAPS - 1, frames)
            if (keep > 0) System.arraycopy(h, TAPS - 1 + frames - keep, h, TAPS - 1 - keep, keep)
        }

        // 3) الجودة وحارس الانحراف
        resEnergy /= count
        val micEnergy = micRms * micRms
        val q = if (micEnergy > 1e-12) 10.0 * log10(micEnergy / max(resEnergy, 1e-12)) else 0.0
        qualityDb += (q - qualityDb) * 0.2

        if (resEnergy > micEnergy * 4.0 + 1e-9) {
            // انحراف: المرشح يضيف بدل أن يطرح — تصفير وبحث فوري
            w.forEach { it.fill(0.0) }
            qualityDb = 0.0
            forceSearchBlocks = 2
            locked = false
            return false
        }

        delayMs = (delaySeconds * 1000.0).toFloat()
        locked = qualityDb > LOCK_DB
        return true
    }

    /** كشف التأخير الكلي: ارتباط مضغوط 1:16 بين الميكروفون والمرجع على المدى الكامل. */
    private fun runSearch(shorts: ShortArray, count: Int, frames: Int, tap: FarEndTap) {
        val dec = 16
        val dFrames = frames / dec
        if (dFrames < 32) return
        if (decMic.size < dFrames) { decMic = FloatArray(dFrames); decRef = FloatArray(dFrames) }

        // الميكروفون: القناة الأولى لكل 16 إطارًا
        var n = 0
        var i = 0
        while (n < dFrames && i < count) { decMic[n] = shorts[i].toFloat(); i += channels * dec; n++ }
        if (n < 32) return
        var mean = 0.0
        for (k in 0 until n) mean += decMic[k]
        mean /= n
        var e2 = 0.0
        for (k in 0 until n) { decMic[k] = (decMic[k] - mean).toFloat(); e2 += decMic[k] * decMic[k] }
        if (e2 <= 1e-12) return

        val span = SEARCH_SPAN_MS / 1000.0
        val step = SEARCH_STEP_MS / 1000.0
        var bestDelay = delaySeconds
        var bestCorr = -Double.MAX_VALUE
        var d = 0.0
        while (d <= span) {
            if (tap.readWindow(sampleRate, frames, d, searchTmp)) {
                var rm = 0.0
                var idx = 0
                var fi = 0
                while (idx < n && fi < frames) { decRef[idx] = searchTmp[fi]; rm += searchTmp[fi]; idx++; fi += dec }
                rm /= max(idx, 1)
                var dot = 0.0
                var r2 = 0.0
                for (k in 0 until n) {
                    val rv = decRef[k] - rm
                    dot += decMic[k] * rv
                    r2 += rv * rv
                }
                val corr = dot / sqrt(max(e2 * r2, 1e-12))
                if (corr > bestCorr) { bestCorr = corr; bestDelay = d }
            }
            d += step
        }
        // مسار جديد فقط إن كان الارتباط مقنعًا والتأخير تغيّر فعلًا
        if (bestCorr > 0.25 && abs(bestDelay - delaySeconds) > 0.004) {
            delaySeconds = bestDelay
            w.forEach { it.fill(0.0) }
            qualityDb = 0.0
        }
    }

    private fun rmsOf(shorts: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) { val v = shorts[i] / 32768.0; sum += v * v }
        return sqrt(sum / max(count, 1))
    }
}
