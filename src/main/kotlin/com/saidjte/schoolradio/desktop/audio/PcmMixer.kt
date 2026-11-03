package com.saidjte.schoolradio.desktop.audio

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * خلاط المدخل الثاني (طلب المستخدم v0.19.0 «أضف إمكانية اختيار مدخلين»):
 * تحويل التقاط الميكروفون الثاني إلى شبكة المدخل الرئيسي (طيّ قنوات +
 * إعادة تشكيل خطية بالمعدل) ثم جمعه في عينات الرئيسي بحد أقصى آمن.
 *
 * كل الدوال نقية على مصفوفات — تُختبر وحدةً بلا أي جهاز صوت، وهذا مقصود:
 * رياضيات الخلط هي موضع الأخطاء الصامتة (انزياح نصف عينة، قص غير مرئي،
 * قناة معكوسة) والجهاز وحده لا يكشفها.
 */
object PcmMixer {

    /**
     * طيّ قنوات التقاط ثانٍ إلى أحادي عائم: ثنائي القناة = متوسط القناتين،
     * أحادي = كما هو. يعيد عدد الإطارات (العينات الأحادية).
     */
    fun foldToMono(src: ShortArray, count: Int, channels: Int, dst: FloatArray): Int {
        val frames = if (channels >= 2) count / 2 else count
        if (frames <= 0) return 0
        if (channels >= 2) {
            for (f in 0 until frames) dst[f] = (src[2 * f] + src[2 * f + 1]) / 65536.0f
        } else {
            for (f in 0 until frames) dst[f] = src[f] / 32768.0f
        }
        return frames
    }

    /**
     * إعادة تشكيل أحادي من معدل إلى آخر باستيفاء خطي — البديل الرشيق
     * لسلاسل إعادة التشكيل الثقيلة، وأدق من التقريب القريب لميكروفون كلام
     * (الفرق بينه وبين مرشّح كامل غير مسموع في كلام المذيع).
     * يعيد عدد عينات الوجهة المكتوبة.
     */
    fun resample(src: FloatArray, srcFrames: Int, srcRate: Int, dstRate: Int, dst: FloatArray): Int {
        if (srcFrames <= 0 || srcRate <= 0 || dstRate <= 0) return 0
        if (srcRate == dstRate) {
            val n = srcFrames.coerceAtMost(dst.size)
            for (i in 0 until n) dst[i] = src[i]
            return n
        }
        val dstFrames = (srcFrames.toLong() * dstRate / srcRate).toInt().coerceAtMost(dst.size)
        if (dstFrames <= 0) return 0
        val step = srcRate.toDouble() / dstRate.toDouble()
        for (i in 0 until dstFrames) {
            val pos = i * step
            val i0 = pos.toInt()
            val frac = (pos - i0).toFloat()
            val a = src[i0.coerceAtMost(srcFrames - 1)]
            val b = src[(i0 + 1).coerceAtMost(srcFrames - 1)]
            dst[i] = a + (b - a) * frac
        }
        return dstFrames
    }

    /**
     * جمع أحادي في عينات القاعدة مع توسيع القنوات وحد أقصى آمن:
     * قاعدة أحادية تُجمع قيمة عيناتها، وثنائية تُجمع نفس القيمة في القناتين،
     * والمجموع محبوس في [−32768, 32767] — قصٌّ صريح يحرسه الاختبار بدل
     * لفّ الأعداد الصحيحة الصامت (overflow) الذي يصدر طقطقة قاسية على الهواء.
     */
    fun addClamped(base: ShortArray, baseCount: Int, baseChannels: Int, mono: FloatArray, monoFrames: Int): Int {
        val frames = minOf(baseCount / maxOf(baseChannels, 1), monoFrames)
        if (frames <= 0) return 0
        for (f in 0 until frames) {
            val add = mono[f]
            if (baseChannels >= 2) {
                val j = 2 * f
                base[j] = sumClamped(base[j], add)
                base[j + 1] = sumClamped(base[j + 1], add)
            } else {
                base[f] = sumClamped(base[f], add)
            }
        }
        return frames
    }

    /**
     * جمع مباشر بنفس المعدل مع مطابقة القنوات بلا أي طيّ:
     * ثنائي+ثنائي يُجمع عينةً بعينةه فيحفظ فصل القناتين، أحادي+ثنائي
     * يُطوى بالمتوسط، ثنائي+أحادي يُكرّر في القناتين — المعدلان متساويان
     * فلا حاجة لإعادة تشكيل ولا عينات وسيطة.
     */
    fun addSameRate(base: ShortArray, baseCount: Int, baseChannels: Int, add: ShortArray, addCount: Int, addChannels: Int): Int {
        val baseFrames = baseCount / maxOf(baseChannels, 1)
        val addFrames = addCount / maxOf(addChannels, 1)
        val frames = minOf(baseFrames, addFrames)
        if (frames <= 0) return 0
        for (f in 0 until frames) {
            if (baseChannels >= 2 && addChannels >= 2) {
                val j = 2 * f
                base[j] = sumClamped(base[j], add[j] / 32768.0f)
                base[j + 1] = sumClamped(base[j + 1], add[j + 1] / 32768.0f)
            } else if (baseChannels >= 2) {
                val j = 2 * f
                val v = add[f] / 32768.0f
                base[j] = sumClamped(base[j], v)
                base[j + 1] = sumClamped(base[j + 1], v)
            } else if (addChannels >= 2) {
                val j = 2 * f
                base[f] = sumClamped(base[f], (add[j] + add[j + 1]) / 65536.0f)
            } else {
                base[f] = sumClamped(base[f], add[f] / 32768.0f)
            }
        }
        return frames
    }

    private fun sumClamped(base: Short, add: Float): Short {
        val v = (base + add * 32768.0f).roundToInt()
        return v.coerceIn(-32768, 32767).toShort()
    }

    /**
     * هل يحتاج مسار المدخل الثاني تحويلًا؟ المعدلان متساويان = جمع مباشر
     * بلا طيّ ولا إعادة تشكيل (المسار الأسرع). اختلاف المعدل وحده ما يستدعي
     * مسار الطيّ + الإعادة.
     */
    fun needsConversion(baseRate: Int, addRate: Int): Boolean = baseRate != addRate

    /** حجم موجة التسامح في مقارنة الأطوال بعد إعادة التشكيل (عينات). */
    fun lengthTolerance(frames: Int): Int = (frames * 0.02f).toInt().coerceAtLeast(1)

    /** تحقق تناظري: قيمة متوقعة ضمن تسامح. */
    fun approx(a: Float, b: Float, eps: Float = 1e-3f): Boolean = abs(a - b) <= eps
}
