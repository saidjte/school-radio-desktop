package com.saidjte.schoolradio.desktop.audio

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * إلغاء صدى الميكروفون (AEC) — الجيل الثاني، نقيّ بلا أي عتاد أو مكتبات خارجية:
 *
 * المشهد الإذاعي الحقيقي: الموسيقى تخرج من السماعات، والميكروفون في الغرفة نفسها
 * يلتقطها متأخرة ومخمّدة (صدى مباشر + ذيل ارتداد يدوم مئات المللي ثواني) فتتسرب
 * إلى هواء البث والتسجيل. درسا النسخة السابقة: فلتر زمني قصير (~11ms) لا يكفي
 * للذيل الارتدادي، والمرجع المأخوذ بنوافذ مُرساة لآخر كتابة يتكرر حين تتباعد
 * دفعات الموسيقى عن كتل الميكروفون فيضعف الطرح.
 *
 * حلا الجيل الثاني:
 * - FarEndTap: مرجع متصل بقراءة منسابلة ذات مؤشر زمني متقدم (لا نوافذ مُرساة)
 *   مع مصحح انسياب يضبط معدل الاستهلاك بجزء من الألف كي لا تتباعد ساعة
 *   الميكروفون عن ساعة السماع أبدًا، وإعادة ربط آلية عند الانقطاع الكبير.
 * - EchoCanceller: مرشح تكيّفي مجزّأ في المجال الترددي (PBFDAF) بطول فعّال
 *   8192 عيّنة (~170ms عند 48كيلوهرتز) عبر FFT نقي — يلتهم الصدى المباشر
 *   والذيل الارتدادي معًا بتكلفة حسابية زهيدة، مع كشف الحديث المزدوج (Geigel)
 *   يجمد التكيّف أثناء كلام المذيع، وكابح بقايا الصدى (NLP) خفيف اليد،
 *   وبحث عن التأخير خشن ثم دقيق بمللي ثانية، وحارس انحراف.
 */

// ================= تحويل فورييه السريع (radix-2 تكراري نقي) =================

/** FFT مركب نقي بالجذر-2 التكراري — حجم قوة للعدد 2. يعمل في مكانه. */
object Fft {

    fun isPowerOfTwo(n: Int): Boolean = n > 0 && (n and (n - 1)) == 0

    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(isPowerOfTwo(n)) { "FFT size" }
        // ترتيب بتات معكوس
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j and bit.inv(); bit = bit shr 1 }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            var start = 0
            while (start < n) {
                var cwr = 1.0; var cwi = 0.0
                var k = start
                val mid = start + len / 2
                while (k < mid) {
                    val k2 = k + len / 2
                    val tr = re[k2] * cwr - im[k2] * cwi
                    val ti = re[k2] * cwi + im[k2] * cwr
                    re[k2] = re[k] - tr; im[k2] = im[k] - ti
                    re[k] += tr; im[k] += ti
                    val nwr = cwr * wr - cwi * wi
                    cwi = cwr * wi + cwi * wr
                    cwr = nwr
                    k++
                }
                start += len
            }
            len = len shl 1
        }
    }

    /** معكوس التحويل: خدعة الاقتران ثم القياس بـ 1/n. */
    fun ifft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        for (i in 0 until n) im[i] = -im[i]
        fft(re, im)
        val inv = 1.0 / n
        for (i in 0 until n) { re[i] *= inv; im[i] *= -inv }
    }

    /** التفاف خطي لكتلتين حقيقيتين عبر FFT — للتحقق في الاختبارات والفحص الذاتي. */
    fun linearConvolve(a: DoubleArray, b: DoubleArray): DoubleArray {
        val outLen = a.size + b.size - 1
        var n = 1
        while (n < outLen) n = n shl 1
        val ar = DoubleArray(n); val ai = DoubleArray(n)
        val br = DoubleArray(n); val bi = DoubleArray(n)
        System.arraycopy(a, 0, ar, 0, a.size)
        System.arraycopy(b, 0, br, 0, b.size)
        fft(ar, ai); fft(br, bi)
        for (i in 0 until n) {
            val rr = ar[i] * br[i] - ai[i] * bi[i]
            val ii = ar[i] * bi[i] + ai[i] * br[i]
            ar[i] = rr; ai[i] = ii
        }
        ifft(ar, ai)
        return ar.copyOf(outLen)
    }
}

// ================= مرجع الطرف البعيد =================

/**
 * حلقة زمنية تحفظ آخر ثانيتين من إشارة السماع (أحادية متوسط القنوات).
 * الكتابة من خيط تشغيل الموسيقى والقراءة من خيط البث المباشر — قسمان حرجان قصيران.
 *
 * نمطا قراءة:
 * - readWindow: نافذة مُرساة لآخر كتابة (لبحث التأخير عبر الارتباط).
 * - readContinuous: تيار متصل بمؤشر متقدم ومصحح انسياب — لمعالجة الإلغاء نفسها،
 *   بحيث تُستهلك عينات المرجع مرة واحدة بالترتيب مهما تباينت أحجام الدفعات.
 */
class FarEndTap(private val capacitySeconds: Double = 2.0) {

    companion object {
        /** تخلف مؤشر القراءة المستمرة عن حافة المعالجة — صفر: المؤشر يلتزم مرساة البحث
         *  تمامًا (أي تخلف إضافي يدفع مسار الصدى خارج نافذة المرشح). */
        const val CONTINUOUS_LAG_S = 0.0
        /** قفزة تعيد الربط: تجاوز التأخر هذا الحد يعني انقطاعًا كبيرًا (إيقاف/تشغيل). */
        const val REANCHOR_LAG_S = 0.65
    }

    private val lock = Any()
    private var rate = 0f
    private var ring = FloatArray(0)
    private var writePos = 0
    private var filled = 0
    private var total = 0L                    // إجمالي العينات منذ configure
    private var newestTime = 0.0              // زمن أحدث عيّنة بساعة المرجع (ثوانٍ)

    // حالة القراءة المستمرة
    private var readPos = 0.0                 // مؤشر مطلق بوحدة عيّنات المرجع
    private var ratioCorr = 0.0               // تصحيح الانسياب (نسبي)
    private var continuousAligned = false

    @Volatile private var lastPushWallMs = 0L // لحظة آخر كتابة بساعة الحائط

    /** يُستدعى عند فتح قناة موسيقى بمعدل جديد (أو إعادة فتحها). */
    fun configure(rateHz: Int) {
        synchronized(lock) {
            rate = rateHz.coerceAtLeast(8000).toFloat()
            ring = FloatArray((capacitySeconds * rate).toInt().coerceAtLeast(8000))
            writePos = 0; filled = 0; total = 0L; newestTime = 0.0
            readPos = 0.0; ratioCorr = 0.0; continuousAligned = false
            lastPushWallMs = 0L
        }
    }

    /** كم مضى منذ آخر إعطاء مرجع؟ (راكد = لا موسيقى تُشغَّل الآن). */
    fun staleMs(): Long = if (lastPushWallMs == 0L) Long.MAX_VALUE
    else System.nanoTime() / 1_000_000L - lastPushWallMs

    /** يكتب كتلة أحادية من إشارة السماع الفعلية (بعد المعادل والفيدر والخفض). */
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
     * ربط المؤشر المستمر: يوضع عند (أحدث − تأخير − تخلف مستمر).
     * يعيد false إن لم يتوفر تاريخ كافٍ بعد.
     */
    fun alignContinuous(delaySeconds: Double): Boolean {
        synchronized(lock) {
            if (rate <= 0f) return false
            val edge = total - delaySeconds * rate
            val target = edge - CONTINUOUS_LAG_S * rate
            val oldest = total - filled
            if (target < oldest) return false
            readPos = target
            ratioCorr = 0.0
            continuousAligned = true
            return true
        }
    }

    /** نتيجة قراءة التيار المستمر. */
    enum class ReadResult { OK, REANCHORED, FAIL }

    /**
     * قراءة [frames] إطارًا من المرجع المتصل بإعادة القياس إلى معدل الميكروفون،
     * مع مصحح انسياب يعدّل معدل الاستهلاك جزءًا من الألف حسب التأخر عن الحافة،
     * وإعادة ربط آلية عند انقطاع كبير أو نزيف للتاريخ.
     */
    fun readContinuous(micRate: Int, frames: Int, delaySeconds: Double, out: FloatArray): ReadResult {
        if (frames <= 0) return ReadResult.FAIL
        synchronized(lock) {
            if (rate <= 0f || ring.isEmpty() || micRate <= 0) return ReadResult.FAIL
            if (!continuousAligned && !alignContinuous(delaySeconds)) return ReadResult.FAIL

            val edge = total - delaySeconds * rate
            val lagSamples = edge - readPos
            if (lagSamples > REANCHOR_LAG_S * rate || readPos < total - filled) {
                // انقطاع كبير (إيقاف موسيقى ثم عودتها) أو تاريخ فُقد — إعادة ربط
                if (!alignContinuous(delaySeconds)) return ReadResult.FAIL
                return ReadResult.REANCHORED
            }

            val cap = ring.size
            val oldest = total - filled
            // مصحح الانسياب: خطأ التأخر بالثواني حول الهدف المستمر
            // تصحيح بطيء جدًا: انسياب الساعات طويل الأمد فقط — تذبذب دفعات الموسيقى
            // يمتصه طول المرشح ولا يجوز أن يموّج معدل الاستهلاك
            val lagErr = lagSamples / rate - CONTINUOUS_LAG_S
            ratioCorr = (ratioCorr + lagErr * 2.0e-4).coerceIn(-0.01, 0.01)
            val ratio = (rate / micRate) * (1.0 + ratioCorr)

            for (i in 0 until frames) {
                if (readPos > total - 1) {
                    // بلوغ أحدث عيّنة: تعليق (تكرار آخر عيّنة) دون تجاوز المستقبل
                    out[i] = ring[(writePos - 1 + cap) % cap]
                    continue
                }
                val j0 = floor(readPos).toInt()
                val frac = readPos - j0
                val back0 = (total - j0).toInt()
                val p0 = ((writePos - back0) % cap + cap) % cap
                val v0 = ring[p0].toDouble()
                val v: Double = if (frac <= 0.0) v0 else {
                    val j1 = j0 + 1
                    val back1 = (total - j1).toInt()
                    val v1 = if (back1 >= 0) ring[((writePos - back1) % cap + cap) % cap].toDouble() else v0
                    v0 + (v1 - v0) * frac
                }
                out[i] = v.toFloat()
                readPos += ratio
            }
            // لم يعد التاريخ كافيًا للقراءة القادمة؟ إشارة إعادة ربط للمكوّن الأعلى
            return if (readPos < oldest) ReadResult.REANCHORED else ReadResult.OK
        }
    }

    /**
     * يقرأ نافذة من المرجع تنتهي قبل أحدث عيّنة بمقدار delaySeconds،
     * مُعادة القياس خطيًا إلى معدل الميكروفون (لبحث التأخير).
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
                val t = startT + (i + 1).toDouble() / micRate
                val pos = t * rate - 1.0
                val j0 = floor(pos).toInt()
                val frac = pos - j0
                val back0 = (newestIdx - j0).toInt()
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

// ================= مانع الصدى التكيّفي (NLMS زمني بمرجع متصل) =================

/**
 * مانع صدى تكيّفي زمني (NLMS لكل عيّنة) يعالج كتل الميكروفون في مكانها:
 *
 *   ميكروفون ← [AEC: طرح تقدير الصدى من مرجع الموسيقى المتصل] ← بقية سلسلة المذيع
 *
 * درسا الجيل الثاني (البحث عن الجذر):
 * - الخلل الأعمق في النسخة السابقة لم يكن طول الفلتر بل **محاذاة المرجع**:
 *   نوافذ مُرساة لآخر كتابة تتكرر حين تتباعد دفعات الموسيقى عن كتل الميكروفون.
 *   المرجع المتصل بمؤشر متقدم يستهلك عيّنة مقابل عيّنة — الطرقة المباشرة تجلس
 *   بعمق صغير معلوم داخل الفلتر ولا تغادرها.
 * - الفلتر الزمني (1024 طرقة ≈ 21ms بعد الطرقة المباشرة) يغطي الصدى المباشر
 *   والارتدادات المبكرة التي تحمل معظم طاقة الصدى المسموعة — بتكلفة حتمية
 *   وباستقرار NLMS المضمون رياضيًا (μ < 2).
 * - كشف الحديث المزدوج يخفض خطوة التعلّم للربع أثناء كلام المذيع (لا تجميد —
 *   درس: التجميد الصارم يمنع التعلّم كليًا في الإذاعة حيث الكلام دائم).
 * - كابح بقايا خفيف عند القفل، حارس انحراف ينكمش ولا يصفّر، وبحث تأخير
 *   خشن (12ms) ثم دقيق (1ms) كل ثانيتين مع تحيّز يحمي الطرقة المباشرة
 *   من تذبذب عمق مخزن الخرج.
 * - صمت المرجع أو ركوده أو تعطيل المعالجة = مرور نظيف بلا أي مساس.
 */
class EchoCanceller {

    companion object {
        /** طرقات الفلتر بعد الطرقة المباشرة (~46ms عند 44.1ك) — تغطي تحيّز
         *  البحث (30ms) + تذبذب مخزن الخرج + الارتدادات المبكرة. */
        const val TAPS = 2048
        /** خطوة NLMS لكل عيّنة (مطبّعة بطاقة نافذة المرجع). */
        const val MU = 0.25
        /** أرضية الطاقة في مقام التطبيع. */
        const val ENERGY_FLOOR = 1e-6
        /** مدى البحث الخشن بالمللي ثانية وخطوته. */
        const val SEARCH_SPAN_MS = 750.0
        const val SEARCH_STEP_MS = 12.0
        /** نصف مدى التنقيح الدقيق وخطوته. */
        const val FINE_SPAN_MS = 14.0
        const val FINE_STEP_MS = 1.0
        /** تحيّز تأخير المرشح بعد البحث — يبقي الطرقة المباشرة داخل النافذة
         *  رغم تذبذب عمق مخزن الخرج بين الدفعات (~±26ms). */
        const val SEARCH_BIAS_S = 0.03
        /** كثافة الطاقة الدنيا كي يُعد المرجع ناطقًا لا صمتًا. */
        const val REF_FLOOR = 1e-5
        /** عتبة قفل المسار: تحسّن الإرجاع بالديسيبل. */
        const val LOCK_DB = 6.0
        /** عتبة جيلجل: نسبة ذروة الميكروفون إلى ذروة المرجع مؤشرًا للحديث المزدوج. */
        const val DT_RATIO = 0.5
        /** تعليق اشتباه الحديث المزدوج (كتل ~0.4s عند 48ك). */
        const val DT_HANGOVER_BLOCKS = 80
        /** أرضية كابح البقايا — لا كتم أعلى من هذا كي لا يُمزّق كلام المذيع. */
        const val NLP_FLOOR = 0.40
        /** طول نافذة البحث (عينات ميكروفون) — آخر ~43ms عند 48ك. */
        const val SEARCH_MIC_SAMPLES = 2048
    }

    private var sampleRate = 0
    private var channels = 0

    // أوزان الفلتر لكل قناة — الطرقة 0 = أحدث عيّنة مرجعية
    private var w: Array<DoubleArray> = emptyArray()

    // حلقة تاريخ المرجع المتصل (TAPS عيّنة) + طاقة النافذة الجارية (لتطبيع NLMS)
    private val refHist = DoubleArray(TAPS)
    private var refPos = 0
    private var refFilled = 0
    private var runEnergy = 0.0

    // مأخذ خرج كتلة النداء (لتطبيق كابح البقايا ثم الكتابة في shorts)
    private var outBuf: Array<DoubleArray> = emptyArray()

    // ذروات المرجع لآخر 48 كتلة — مقارن كشف الحديث المزدوج عبر مسار الفلتر
    private val peakRing = DoubleArray(48)
    private var peakHead = 0
    private var peakFilled = 0

    private var refCont = FloatArray(0)
    private var searchTmp = FloatArray(0)
    private var decMic = FloatArray(0)
    private var decRef = FloatArray(0)

    // حلقة الميكروفون الخام للبحث (قبل أي معالجة)
    private val searchRing = DoubleArray(SEARCH_MIC_SAMPLES * 2)
    private var searchWrite = 0
    private var searchTotal = 0L

    private var delaySeconds = 0.05
    private var sinceSearchCalls = 0
    private var forceSearchCalls = 0
    private var dtHangover = 0
    private var nlpGain = 1.0
    private var qualityDb = 0.0

    /** التأخير المقدَّر حاليًا بالمللي ثانية (للعرض الحي). */
    @Volatile var delayMs = 0f
        private set

    /** تحسّن الإرجاع المقدّر بالديسيبل (ERLE) — مؤشر قوة الإلغاء الحي. */
    @Volatile var erleDb = 0f
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
        outBuf = Array(ch) { DoubleArray(4096) }
        refCont = FloatArray(max(fs / 10, 1024))
        searchTmp = FloatArray(max(fs / 10, SEARCH_MIC_SAMPLES))
        decMic = FloatArray(128); decRef = FloatArray(128)
        delaySeconds = 0.05
        sinceSearchCalls = 0
        forceSearchCalls = 0
        dtHangover = 0
        nlpGain = 1.0
        qualityDb = 0.0
        locked = false
        clearFilterState()
    }

    /** تصفير كامل (عند بدء البث). */
    fun reset() {
        for (c in w.indices) w[c].fill(0.0)
        qualityDb = 0.0
        locked = false
        sinceSearchCalls = 0
        forceSearchCalls = 0
        dtHangover = 0
        nlpGain = 1.0
        searchWrite = 0; searchTotal = 0L
        clearFilterState()
    }

    private fun clearFilterState() {
        for (c in w.indices) w[c].fill(0.0)
        refHist.fill(0.0)
        refPos = 0; refFilled = 0
        runEnergy = 0.0
        peakRing.fill(0.0)
        peakHead = 0; peakFilled = 0
        outBuf.forEach { it.fill(0.0) }
        nlpGain = 1.0
    }

    /**
     * يعالج كتلة ميكروفون في مكانها مقابل مرجع الطرف البعيد المتصل.
     * يعيد true إن جرى طرح فعلي (مرجع ناطق والمسار صالح).
     */
    fun process(shorts: ShortArray, count: Int, tap: FarEndTap?): Boolean {
        if (!enabled || tap == null || sampleRate <= 0 || count <= 0) {
            locked = false
            return false
        }
        val frames = count / channels
        if (frames <= 0) return false
        if (refCont.size < frames) refCont = FloatArray(frames)
        if (outBuf[0].size < frames) outBuf = Array(channels) { DoubleArray(frames) }

        // 0) حلقة البحث: الميكروفون الخام (القناة الأولى) قبل أي معالجة
        for (f in 0 until frames) {
            searchRing[searchWrite % searchRing.size] = shorts[f * channels] / 32768.0
            searchWrite++
            searchTotal++
        }

        // 1) مرجع راكد (لا موسيقى الآن) = مرور نظيف تام
        if (tap.staleMs() > 400) {
            if (refFilled > 0) clearFilterState()
            locked = false
            return false
        }

        // 2) سحب المرجع المتصل بمقدار إطارات هذه الكتلة
        val rr = tap.readContinuous(sampleRate, frames, delaySeconds, refCont)
        if (rr == FarEndTap.ReadResult.FAIL) { locked = false; return false }
        if (rr == FarEndTap.ReadResult.REANCHORED) {
            clearFilterState()
            forceSearchCalls = 2
            qualityDb = 0.0
        }

        // 3) كشف الحديث المزدوج على مستوى الكتلة (جيلجل عبر مسار الفلتر كاملًا)
        var micPeak = 0.0
        for (f in 0 until frames) {
            val m = abs(shorts[f * channels] / 32768.0); if (m > micPeak) micPeak = m
        }
        var curPeak = 0.0
        for (f in 0 until frames) {
            val r = abs(refCont[f].toDouble()); if (r > curPeak) curPeak = r
        }
        peakRing[peakHead] = curPeak
        peakHead = (peakHead + 1) % peakRing.size
        if (peakFilled < peakRing.size) peakFilled++
        var refPeak = 0.0
        for (k in 0 until peakFilled) { if (peakRing[k] > refPeak) refPeak = peakRing[k] }
        val doubleTalk = refPeak > 0.02 && micPeak > DT_RATIO * refPeak + 0.01
        dtHangover = if (doubleTalk) DT_HANGOVER_BLOCKS else max(dtHangover - 1, 0)
        val dtSuspected = doubleTalk || dtHangover > 0
        // خطوة ربعية أثناء الاشتباه — تعلّم بطيء لا تجميد
        val stepScale = if (dtSuspected) 0.25 else 1.0

        // 4) التحرير والطرح والتحديث — عيّنة بعيّنة، بلا أي تخصيص داخل الحلقة
        var refEnergySum = 0.0
        var micEnergySum = 0.0
        var resEnergySum = 0.0
        var echoEnergySum = 0.0
        val nlpActive = locked && !dtSuspected
        for (f in 0 until frames) {
            // أحدث عيّنة مرجعية تدخل الحلقة (الطرقة 0) — طاقة النافذة الجارية
            // (مجموع آخر TAPS عيّنة) هي مقام NLMS الصحيح: ناعمة ومحدودة دائمًا،
            // بخلاف طاقة الإطار الواحد التي تنفجر عند تقاطعات الصفر
            val uNew = refCont[f].toDouble()
            if (refFilled >= TAPS) runEnergy -= refHist[refPos] * refHist[refPos]
            runEnergy += uNew * uNew
            if (runEnergy < 0.0) runEnergy = 0.0
            refHist[refPos] = uNew
            refPos = (refPos + 1) % TAPS
            if (refFilled < TAPS) refFilled++
            refEnergySum += uNew * uNew
            val denom = max(runEnergy, ENERGY_FLOOR)
            val step = MU * stepScale / denom
            for (c in 0 until channels) {
                val wc = w[c]
                val ob = outBuf[c]
                val x = shorts[f * channels + c] / 32768.0
                // تقدير الصدى: الطرقة i تقابل refHist[(refPos-1-i) mod TAPS]
                var y = 0.0
                var h = refPos - 1
                if (h < 0) h += TAPS
                var i = 0
                while (i < TAPS) {
                    y += wc[i] * refHist[h]
                    h--; if (h < 0) h += TAPS
                    i++
                }
                val e0 = x - y
                micEnergySum += x * x
                echoEnergySum += y * y
                resEnergySum += e0 * e0
                ob[f] = e0
                // تحديث NLMS لكل عيّنة — بشرط مسار مستقر
                if (abs(e0) <= abs(x) * 3.0 + 0.1) {
                    var i2 = 0
                    var h2 = refPos - 1
                    if (h2 < 0) h2 += TAPS
                    while (i2 < TAPS) {
                        wc[i2] += step * e0 * refHist[h2]
                        h2--; if (h2 < 0) h2 += TAPS
                        i2++
                    }
                }
            }
        }
        val framesD = frames.toDouble()
        val micEnergy = max(micEnergySum / framesD / channels, 1e-12)
        val resEnergy = resEnergySum / framesD / channels
        val echoEnergy = echoEnergySum / framesD / channels
        val refEnergy = refEnergySum / framesD

        // 5) كابح البقايا على خرج هذه الكتلة (التكيّف استخدم الخطأ الخام)
        if (nlpActive && echoEnergy > 1e-9) {
            val speechEst = max(resEnergy - 0.35 * echoEnergy, 0.0)
            val g = max(sqrt(speechEst / max(resEnergy, 1e-12)), NLP_FLOOR)
            nlpGain = if (g < nlpGain) g else nlpGain + (g - nlpGain) * 0.06
        } else {
            nlpGain += (1.0 - nlpGain) * 0.3
        }
        if (nlpGain < 0.999) {
            for (c in 0 until channels) {
                val ob = outBuf[c]
                for (f in 0 until frames) ob[f] *= nlpGain
            }
        }

        // 6) إعادة الخرج إلى shorts
        var i = 0
        for (f in 0 until frames) {
            for (c in 0 until channels) {
                val v = outBuf[c][f]
                shorts[i] = (v * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                i++
            }
        }

        // 7) الجودة وحارس الانحراف (انكماش لا تصفير)
        val q = 10.0 * log10(micEnergy / max(resEnergy, 1e-12))
        qualityDb += (q - qualityDb) * 0.05
        erleDb = qualityDb.toFloat().coerceIn(0f, 40f)
        if (resEnergy > micEnergy * 9.0 + 1e-9) {
            for (c in 0 until channels) {
                val wc = w[c]
                for (k in 0 until TAPS) wc[k] *= 0.25
            }
            qualityDb = 0.0
            forceSearchCalls = 2
            locked = false
        } else {
            locked = qualityDb > LOCK_DB && refEnergy > REF_FLOOR
        }

        // 8) البحث الدوري عن التأخير (خشن ثم دقيق)
        sinceSearchCalls++
        val callMs = frames * 1000.0 / sampleRate
        val searchInterval = (2000.0 / callMs).toInt().coerceAtLeast(4)
        if (sinceSearchCalls >= searchInterval || forceSearchCalls > 0) {
            sinceSearchCalls = 0
            if (forceSearchCalls > 0) forceSearchCalls--
            runSearch(tap)
        }

        delayMs = (delaySeconds * 1000.0).toFloat()
        return locked || qualityDb > 1.0
    }

    /**
     * كشف التأخير الكلي: ارتباط مضغوط 1:16 على المدى الكامل بخطوة 12ms،
     * ثم تنقيح دقيق بخطوة 1ms حول الذروة على الإشارة الكاملة.
     * مسار جديد فقط إن كان الارتباط مقنعًا والتأخير تغيّر فعلًا.
     */
    private fun runSearch(tap: FarEndTap) {
        val avail = min(SEARCH_MIC_SAMPLES.toLong(), searchTotal).toInt()
        if (avail < 256) return
        if (searchTmp.size < avail) searchTmp = FloatArray(avail)
        val micSnap = DoubleArray(avail)
        val start = searchWrite - avail
        for (k in 0 until avail) micSnap[k] = searchRing[(start + k).floorMod(searchRing.size)]

        val dec = 16
        val dFrames = avail / dec
        if (dFrames < 32) return
        if (decMic.size < dFrames) { decMic = FloatArray(dFrames); decRef = FloatArray(dFrames) }

        var mean = 0.0
        for (k in 0 until dFrames) mean += micSnap[k * dec]
        mean /= dFrames
        var e2 = 0.0
        for (k in 0 until dFrames) {
            val v = micSnap[k * dec] - mean
            decMic[k] = v.toFloat()
            e2 += v * v
        }
        if (e2 <= 1e-12) return

        val span = SEARCH_SPAN_MS / 1000.0
        val step = SEARCH_STEP_MS / 1000.0
        var bestDelay = -1.0
        var bestCorr = -Double.MAX_VALUE
        var d = 0.0
        while (d <= span) {
            if (tap.readWindow(sampleRate, avail, d, searchTmp)) {
                val corr = correlationOf(searchTmp, decMic, dFrames, dec)
                if (corr > bestCorr) { bestCorr = corr; bestDelay = d }
            }
            d += step
        }
        if (bestDelay < 0.0 || bestCorr <= 0.25) return

        val fineLo = max(0.0, bestDelay - FINE_SPAN_MS / 1000.0)
        val fineHi = bestDelay + FINE_SPAN_MS / 1000.0
        var fineBest = bestDelay
        var fineCorr = bestCorr
        var df = fineLo
        while (df <= fineHi) {
            if (tap.readWindow(sampleRate, avail, df, searchTmp)) {
                val corr = fullCorrelation(searchTmp, micSnap)
                if (corr > fineCorr) { fineCorr = corr; fineBest = df }
            }
            df += FINE_STEP_MS / 1000.0
        }

        if (abs(fineBest - SEARCH_BIAS_S - delaySeconds) > 0.0015) {
            // التأخير المُعتمد = مرساة البحث ناقص تحيّز — الطرقة المباشرة تجلس بعمق
            // صغير داخل النافذة ولا تخرج منها بتذبذب مخزن الخرج
            delaySeconds = max(fineBest - SEARCH_BIAS_S, 0.005)
            clearFilterState()
            qualityDb = 0.0
            // إعادة ربط المؤشر المستمر بالتأخير الجديد (أفضل جهد — الفشل يعاد لاحقًا)
            tap.alignContinuous(delaySeconds)
        }
    }

    private fun Int.floorMod(m: Int): Int = ((this % m) + m) % m

    /** ارتباط مضغوط: كل 16 عيّنة من المرجع مقابل عيّنات الميكروفون المنمّقة. */
    private fun correlationOf(ref: FloatArray, decMicArr: FloatArray, n: Int, dec: Int): Double {
        var rm = 0.0
        var idx = 0
        var fi = 0
        while (idx < n && fi < ref.size) { decRef[idx] = ref[fi]; rm += ref[fi]; idx++; fi += dec }
        rm /= max(idx, 1)
        var dot = 0.0
        var r2 = 0.0
        var m2 = 0.0
        for (k in 0 until idx) {
            val rv = decRef[k] - rm
            val mv = decMicArr[k]
            dot += mv * rv
            r2 += rv * rv
            m2 += mv * mv
        }
        return dot / sqrt(max(m2 * r2, 1e-12))
    }

    /** ارتباط كامل غير مضغوط (للتنقيح الدقيق). */
    private fun fullCorrelation(ref: FloatArray, mic: DoubleArray): Double {
        val n = min(ref.size, mic.size)
        var rm = 0.0; var mm = 0.0
        for (k in 0 until n) { rm += ref[k]; mm += mic[k] }
        rm /= n; mm /= n
        var dot = 0.0; var r2 = 0.0; var m2 = 0.0
        for (k in 0 until n) {
            val rv = ref[k] - rm
            val mv = mic[k] - mm
            dot += mv * rv; r2 += rv * rv; m2 += mv * mv
        }
        return dot / sqrt(max(m2 * r2, 1e-12))
    }
}
