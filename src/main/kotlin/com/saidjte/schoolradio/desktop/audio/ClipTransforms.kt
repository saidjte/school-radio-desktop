package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageProject
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * تحويلات المقطع غير الإتلافية (v0.38.0 «أدوات الحفرة الاحترافية»):
 * عكس الموجة، تعديل السرعة دون تغيير النبرة (WSOLA)، وتغيير النبرة دون
 * تغيير الطول (إعادة تشكيل ثم شد زمني عائد).
 *
 * العقد المعماري: كل تحويل «ارتِداد» (Bounce) — يُفكّ نافذة المقطع من ملفها،
 * يُحوَّل في الذاكرة، ويُكتب ملف WAV جديدًا في مخزن ارتدادات المونتاج باسم
 * مشتق من بصمة (ملف|نافذة|تحويل|قيمة) — ثم يُوجَّه المقطع إلى الملف الجديد.
 * الملف الأصلي لا يُمسّ أبدًا (غير إتلافي كعقد الصفحة كلها)، والتراجع يعيد
 * المقطع إلى ملفه الأصلي، والتكرار بنفس القيمة يعيد نفس الملف المخزَّن بلا
 * حساب ثانٍ (مفتاح بصمة SHA-1).
 *
 * WSOLA (Waveform Similarity Overlap-Add): الشد الزمني المعياري المحافظ على
 * النبرة — إطارات ثابتة تُضاف بتداخل مع بحث تشابه يحافظ على استمرارية الموجة،
 * فتتغير المدة دون انزياح طبقة الصوت. خالص على JVM بلا مكتبات خارجية، مثل
 * باقي خط الصوت في التطبيق.
 */
object ClipTransforms {

    /** حجم إطار WSOLA بالعينات (~46ms عند 44.1k) — توازن جودة/زمن. */
    internal const val WSOLA_FRAME = 2048

    /** قفزة التخليق الثابتة — نصف الإطار (تداخل 50٪). */
    internal const val WSOLA_HOP = 1024

    /** مدى بحث التشابه ±عينات (~11.6ms) — أقصى إزاحة عن القفزة المنتظمة. */
    internal const val WSOLA_TOLERANCE = 512

    /** خطوة البحث الخشن قبل التنقيح الدقيق — سرعة بلا حسّ مسموع. */
    private const val SEARCH_COARSE_STEP = 8

    /** أقصي طول مفكوك يُقبل في الارتداد (10 دقائق) — حماية ذاكرة. */
    const val MAX_TRANSFORM_MS = 10L * 60L * 1000L

    const val SPEED_MIN_PERCENT = 50
    const val SPEED_MAX_PERCENT = 200
    val SPEED_PRESETS = listOf(50, 75, 90, 100, 110, 125, 150, 200)

    const val PITCH_MIN_SEMITONES = -12
    const val PITCH_MAX_SEMITONES = 12

    /** سقف التكرار — المصدر الحقيقة في MontageLogic (الرياضيات المسؤولة عنه هناك). */
    val DUPLICATE_MAX = MontageLogic.DUPLICATE_MAX

    /** استثناء تجاوز حد التحويل — يُترجَم في الواجهة لرسالة عربية. */
    class TransformTooLong(val durationMs: Long) : Exception()

    /** نتيجة الارتداد: الملف الجديد ومدته. */
    data class Bounced(val file: File, val durationMs: Long)

    // ================= الرياضيات النقية (تُختبر وحدةً بلا ملفات) =================

    /** عكس ترتيب الإطارات الستيريو المتشابكة — قلب الموجة زمنيًا. */
    fun reversePcm(pcm: ShortArray): ShortArray {
        val frames = pcm.size / 2
        val out = ShortArray(pcm.size)
        for (i in 0 until frames) {
            val src = (frames - 1 - i) * 2
            out[2 * i] = pcm[src]
            out[2 * i + 1] = pcm[src + 1]
        }
        return out
    }

    /** حبس قيمة السرعة بالمئة داخل نطاقها القانوني. */
    fun clampSpeedPercent(percent: Int): Int = percent.coerceIn(SPEED_MIN_PERCENT, SPEED_MAX_PERCENT)

    /** حبس النبرة بأنصاف النغمات داخل نطاقها القانوني. */
    fun clampPitchSemitones(st: Int): Int = st.coerceIn(PITCH_MIN_SEMITONES, PITCH_MAX_SEMITONES)

    /** معامل تغيير النبرة من أنصاف النغمات (توازنات موسيقية حقيقية). */
    fun pitchFactor(semitones: Int): Double = Math.pow(2.0, semitones / 12.0)

    /**
     * معدل المصدر المكافئ لإعادة التشكيل النبرةَ: ضغط الموجة زمنيًا (معدل أعلى)
     * يرفع النبرة ويقصّر المدة، وتمديدها (معدل أدنى) يخفضها ويطيلها — ثم يعيد
     * WSOLA الطول بالمعامل المعاكس. معدل مكافئ صحيح لأقرب هرتز.
     */
    internal fun resampleSourceRateForPitch(semitones: Int): Int {
        val f = pitchFactor(semitones)
        return (MontageRender.RATE * f).roundToLong().coerceAtLeast(1000L).toInt()
    }

    /**
     * شد زمني WSOLA محافظًا على النبرة: [tempo] أكبر من 1 أسرع وأقصر
     * (2.0 = ضعف السرعة ونصف المدة)، وأصغر من 1 أبطأ وأطول. العقد: المدة
     * الناتجة ≈ الأصل ÷ tempo داخل سماحية إطار واحد، والنبرة (كثافة التقاطعات
     * الصفرية) لا تنزاح.
     *
     * الخوارزمية: إطارات [WSOLA_FRAME] تُوضَع كل [WSOLA_HOP] عينة تخليقًا،
     * وموضع القراءة يتقدم [frame/hop] عينة تحليلًا، ومع كل إطار بحث تشابه ±
     * [WSOLA_TOLERANCE] عن «الامتداد الطبيعي» للإطار السابق (ترابط متقاطع
     * خشن بخطوة 8 ثم تنقيح فردي) — فتُخاط الموجة على امتدادها بلا نقرات
     * وبلا انزياح نبرة. نوافذ خطية متكاملة فمجموع الكسب في التداخل = 1.
     */
    fun wsolaChangeTempo(pcm: ShortArray, tempo: Double): ShortArray {
        val t = tempo.coerceIn(0.25, 4.0)
        val frames = pcm.size / 2
        // أقصر من إطارين: التحويل بلا معنى — يعود كما هو (حافة آمنة)
        if (frames <= WSOLA_FRAME * 2) return pcm.copyOf()
        val N = WSOLA_FRAME
        val Hs = WSOLA_HOP
        val tol = WSOLA_TOLERANCE
        // قفزة التحليل = قفزة التخليق × المعامل: تيمبو 2 يقفز قراءةً أكثر فيكتب
        // أقل فيقصر الناتج (أسرع)، وتيمبو 0.5 يقفز أقل فيتمدد — الناتج ≈ الأصل ÷ تيمبو
        val ha = (Hs * t).roundToInt().coerceIn(32, 16_384)

        // مونو خفيف للترابط — القرار على القناة المجموعة والتطبيق على الستيريو
        val mono = ShortArray(frames) { i ->
            ((pcm[2 * i] + pcm[2 * i + 1]) / 2).toShort()
        }

        val maxOutFrames = (frames.toDouble() / t).toInt() + N + Hs
        val out = IntArray(maxOutFrames * 2)

        fun windowGain(n: Int): Float = when {
            n < Hs -> n.toFloat() / Hs
            n >= N - Hs -> (N - n).toFloat() / Hs
            else -> 1f
        }

        /** ترابط متقاطع مجمّع بين موضع تحليل مرشح والامتداد الطبيعي المرجعي. */
        fun correlationAt(pos: Int, ref: ShortArray): Long {
            var sum = 0L
            var p = pos
            for (n in 0 until N step SEARCH_COARSE_STEP) {
                if (p >= frames) break
                sum += mono[p].toLong() * ref[n].toLong()
                p += SEARCH_COARSE_STEP
            }
            return sum
        }

        var analPos = 0
        var synPos = 0
        val natural = ShortArray(N)
        var haveNatural = false
        var outFrames = 0

        while (analPos + N + tol < frames) {
            // بحث التشابه: خشن ثم تنقيح حول الأفضل
            var copyFrom = analPos
            if (haveNatural) {
                val lo = (analPos - tol).coerceAtLeast(0)
                val hi = (analPos + tol).coerceAtMost(frames - N)
                if (hi > lo) {
                    var best = lo
                    var bestScore = Long.MIN_VALUE
                    var d = lo
                    while (d <= hi) {
                        val s = correlationAt(d, natural)
                        if (s > bestScore) { bestScore = s; best = d }
                        d += SEARCH_COARSE_STEP
                    }
                    var refine = (best - SEARCH_COARSE_STEP + 1).coerceAtLeast(lo)
                    val refineHi = (best + SEARCH_COARSE_STEP - 1).coerceAtMost(hi)
                    bestScore = Long.MIN_VALUE
                    d = refine
                    while (d <= refineHi) {
                        var sum = 0L
                        for (n in 0 until N) {
                            val p = d + n
                            if (p >= frames) break
                            sum += mono[p].toLong() * natural[n].toLong()
                        }
                        if (sum > bestScore) { bestScore = sum; copyFrom = d }
                        d++
                    }
                }
            }
            copyFrom = copyFrom.coerceIn(0, (frames - N).coerceAtLeast(0))

            // تراكب-إضافة بنافذة متكاملة
            for (n in 0 until N) {
                val g = windowGain(n)
                val src = (copyFrom + n) * 2
                val dst = (synPos + n) * 2
                if (dst + 1 >= out.size) break
                out[dst] += (pcm[src] * g).roundToInt()
                out[dst + 1] += (pcm[src + 1] * g).roundToInt()
            }
            if (synPos + N > outFrames) outFrames = synPos + N

            // الامتداد الطبيعي التالي: ما يلي نهاية الإطار المنسوخ بقفزة التخليق
            val natStart = copyFrom + Hs
            if (natStart + N <= frames) {
                for (n in 0 until N) natural[n] = mono[natStart + n]
                haveNatural = true
            } else haveNatural = false

            analPos += ha
            synPos += Hs
            if (synPos + N >= maxOutFrames) break
        }

        // تحويل التراكمي الصحيح إلى 16-bit محبوس
        val result = ShortArray(outFrames.coerceAtLeast(0) * 2)
        for (i in 0 until result.size) {
            result[i] = out[i].coerceIn(-32768, 32767).toShort()
        }
        return result
    }

    /**
     * تغيير النبرة بأنصاف النغمات مع حفظ الطول: إعادة تشكيل بمعدل مصدر
     * مكافئ (تنزاح النبرة وتنعكس المدة) ثم شد WSOLA عائد بالمعامل نفسه —
     * فتعود المدة الأصلية وتبقى النبرة منزاحة. صفر أنصاف نغمات = كما هو.
     */
    fun pitchShift(pcm: ShortArray, semitones: Int): ShortArray {
        val st = clampPitchSemitones(semitones)
        if (st == 0) return pcm.copyOf()
        val frames = pcm.size / 2
        if (frames <= WSOLA_FRAME * 2) return pcm.copyOf()
        val srcRate = resampleSourceRateForPitch(st)
        val shifted = PcmMixer.resampleStereo(pcm, frames, srcRate, MontageRender.RATE)
        val shiftedFrames = shifted.size / 2
        val neededTempo = shiftedFrames.toDouble() / frames.toDouble()
        return wsolaChangeTempo(shifted, neededTempo)
    }

    /** تعديل السرعة بالمئة محافظًا على النبرة — 200٪ = ضعف السرعة ونصف المدة. */
    fun changeSpeed(pcm: ShortArray, speedPercent: Int): ShortArray =
        wsolaChangeTempo(pcm, clampSpeedPercent(speedPercent) / 100.0)

    /**
     * تقدير التردد المهيمن بكثافة تقاطعات الصفر على القناة الأولى — مسطرة
     * الاختبار والفحص الذاتي: موجات جيبية معروفة تتحقق من حفظ النبرة.
     * يقرأ النطاق الأوسط (30٪..70٪) كي يتجنب تلاشيات الأطراف.
     */
    fun estimateDominantHz(pcm: ShortArray, rate: Int = MontageRender.RATE): Double {
        val frames = pcm.size / 2
        if (frames < 64) return 0.0
        val from = (frames * 3 / 10).coerceAtLeast(1)
        val to = (frames * 7 / 10).coerceAtMost(frames - 1)
        if (to <= from) return 0.0
        var crossings = 0
        var prev = pcm[2 * from]
        for (i in from + 1 until to) {
            val cur = pcm[2 * i]
            if ((prev < 0 && cur >= 0) || (prev >= 0 && cur < 0)) crossings++
            prev = cur
        }
        val seconds = (to - from).toDouble() / rate
        if (seconds <= 0.0) return 0.0
        return crossings / 2.0 / seconds
    }

    // ================= الارتداد إلى ملف مخزَّن =================

    /** بصمة الارتداد: اسم ملف حتمي من (ملف|نافذة|تحويل|قيمة). */
    internal fun bounceKey(clip: MontageClip, kind: String, param: String): String {
        val (ws, we) = MontageLogic.clampWindow(clip.startMs, clip.endMs, clip.durationMs)
        val raw = "${clip.path}|$ws|$we|${clip.durationMs}|$kind|$param"
        val dig = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in dig.take(10)) sb.append("%02x".format(b))
        return sb.toString()
    }

    /**
     * فكّ نافذة المقطع إلى ستيريو 44.1k كاملًا في الذاكرة — نفس عقد
     * ClipStream في المُصيِّر (فكّ → طيّ ستيريو → إعادة تشكيل)، محبوسًا
     * بحد [MAX_TRANSFORM_MS] كي لا يبتلع تحويلٌ طويل الذاكرة.
     */
    internal fun decodeWindow(clip: MontageClip): ShortArray {
        val (ws, we) = MontageLogic.clampWindow(clip.startMs, clip.endMs, clip.durationMs)
        val durMs = (we - ws).coerceAtLeast(0L)
        if (durMs <= 0L) return ShortArray(0)
        if (durMs > MAX_TRANSFORM_MS) throw TransformTooLong(durMs)
        val targetFrames = (durMs * MontageRender.RATE / 1000L).coerceAtLeast(1L)
        val sink = java.io.ByteArrayOutputStream(1 shl 20)
        MontageClipSource.open(File(clip.path)).use { src ->
            src.skipTo(ws)
            val ch = src.channels.coerceAtLeast(1)
            val needNative = targetFrames * src.rate / MontageRender.RATE + 1
            val native = ShortArray(64 * 1024)
            var nativeFrames = 0L
            var produced = 0L
            while (produced < targetFrames && nativeFrames < needNative) {
                val n = src.read(native)
                if (n <= 0) break
                nativeFrames += n / ch
                val st = foldStereo(native, n, ch)
                val resampled = if (src.rate == MontageRender.RATE) st
                else PcmMixer.resampleStereo(st, st.size / 2, src.rate, MontageRender.RATE)
                val bytes = ByteArray(resampled.size * 2)
                for (i in resampled.indices) {
                    bytes[2 * i] = (resampled[i].toInt() and 0xFF).toByte()
                    bytes[2 * i + 1] = ((resampled[i].toInt() shr 8) and 0xFF).toByte()
                }
                sink.write(bytes)
                produced += resampled.size / 2
            }
        }
        val all = sink.toByteArray()
        val shorts = ShortArray(all.size / 2)
        for (i in shorts.indices) {
            shorts[i] = ((all[2 * i].toInt() and 0xFF) or (all[2 * i + 1].toInt() shl 8)).toShort()
        }
        val cap = (targetFrames * 2).toInt().coerceAtMost(shorts.size)
        return if (cap == shorts.size) shorts else shorts.copyOf(cap)
    }

    /** كتابة PCM ستيريو إلى ملف WAV قانوني 44.1k/16-bit متدفقًا. */
    internal fun writeWav44k(file: File, pcm: ShortArray): Long {
        file.parentFile?.mkdirs()
        runCatching { if (file.isFile) file.delete() }
        WavFileWriter(file, MontageRender.RATE, MontageRender.CHANNELS).use { w ->
            val buf = ByteArray(64 * 1024)
            var i = 0
            while (i < pcm.size) {
                var bi = 0
                while (i < pcm.size && bi + 1 < buf.size) {
                    val v = pcm[i].toInt()
                    buf[bi++] = (v and 0xFF).toByte()
                    buf[bi++] = ((v shr 8) and 0xFF).toByte()
                    i++
                }
                w.write(buf, 0, bi)
            }
        }
        return WavFileWriter.durationMsOf(pcm.size * 2L, MontageRender.RATE, MontageRender.CHANNELS)
    }

    /** هل ملف الارتداد المخزَّن صالح لإعادة الاستخدام؟ */
    private fun reusable(f: File): Boolean = f.isFile && f.length() > 44L

    /** مدة ملف WAV كانوني (ترويسة 44 بايت) بالمللي ثانية. */
    internal fun canonicalWavDurationMs(f: File): Long =
        ((f.length() - 44L).coerceAtLeast(0L)) * 1000L / (MontageRender.RATE * MontageRender.CHANNELS * 2L)

    /**
     * ارتداد مقطع بتحويل محدد إلى ملف مخزَّن: العودية الثانية بنفس البصمة
     * تعيد الملف نفسه بلا حساب — والعتبة الفارغة تعيد ShortArray(0) فتُردَّ
     * بأمان في الطبقة الأعلى برسالة عربية.
     */
    fun bounce(clip: MontageClip, kind: String, param: String, transform: (ShortArray) -> ShortArray, cacheDir: File): Bounced {
        val key = bounceKey(clip, kind, param)
        val target = File(cacheDir, "t38-$key.wav")
        if (reusable(target)) return Bounced(target, canonicalWavDurationMs(target))
        val decoded = decodeWindow(clip)
        if (decoded.isEmpty()) throw IllegalArgumentException("نافذة المقطع فارغة")
        val transformed = transform(decoded)
        val durMs = writeWav44k(target, transformed)
        return Bounced(target, durMs)
    }

    /**
     * دمج مقطعين متتاليين في ملف واحد مخزَّن: مشروع مصغَّر يمر في المُصيِّر
     * نفسه (بالفاصل والكروس فيد وكل التلاشين والمستويات) — فالناتج يسمع
     * كما يُسمَع في المعاينة تمامًا. يُستعمل لدمج مقاطع التعليق ولصق متلاصق
     * لمقاطع المسارات (فاصل صفري).
     */
    fun bounceMerge(
        a: MontageClip, b: MontageClip,
        gapMs: Long, crossfadeMs: Long,
        cacheDir: File
    ): Bounced {
        val key = bounceKey(a, "merge", "$b.path|$b.startMs|$b.endMs|$b.durationMs|$gapMs|$crossfadeMs")
        val target = File(cacheDir, "t38-merge-$key.wav")
        if (reusable(target)) return Bounced(target, canonicalWavDurationMs(target))
        val mini = MontageProject(clips = listOf(a, b), gapMs = gapMs, crossfadeMs = crossfadeMs)
        val r = MontageRender.render(mini, target)
        if (!r.ok) throw IllegalStateException(r.message ?: "فشل الدمج")
        return Bounced(target, r.durationMs)
    }

    /** عنوان مقطع مدموج: أول اسمن مع «+» — معقّم بعقد التسمية. */
    fun mergedTitle(a: String, b: String): String =
        MontageLogic.sanitizeClipTitle("${a.trim()}+${b.trim()}")

    /** فحص تجاوز حد التحويل برسالة عربية جاهزة. */
    fun tooLongMessage(ms: Long): String =
        "المقطع أطول من حد التحويل (${ms / 60000L} دقائق) — قصّره أولًا كي يُحوَّل بأمان"

    /** تقدير مقاربة تردد ضمن سماحية — لرسائل الفحص. */
    fun freqClose(a: Double, b: Double, toleranceHz: Double): Boolean = abs(a - b) <= toleranceHz
}
