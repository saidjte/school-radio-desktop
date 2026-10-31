package com.saidjte.schoolradio.desktop.data

/**
 * منطق مقياس البث المقطعي (VU المقطعي): قواعد خالصة حتمية تُغطى بالاختبارات قبل أي رفع،
 * والرسم في واجهة المستخدم لا يضيف أي قرار — يرسم ما تقوله هذه الطبقة فقط.
 *
 * المقياس سلسلة مقاطع LED من 24 مقطعًا، تمتلئ من الجهة الهادئة نحو الأعلى،
 * بثلاث مناطق: أخضر للمستوى المريح، كهرماني للانتباه، أحمر للمستوى القريب من التشبع.
 */
enum class MeterZone { GREEN, AMBER, RED }

object MeterLogic {

    /** عدد المقاطع الكلي في المقياس. */
    const val SEGMENTS: Int = 24

    /** بداية منطقة الانتباه (نسبة من مقياس كامل). */
    const val AMBER_FROM: Float = 0.62f

    /** بداية منطقة التشبع (نسبة من مقياس كامل). */
    const val RED_FROM: Float = 0.85f

    /** معدل انحسار مؤشر الذروة في كل تحديث للمستوى (نسبة من المقياس). */
    const val PEAK_DECAY: Float = 0.02f

    /** تقييد المستوى داخل [0,1] — القيم الشاذة (بما فيها NaN) تعامل كصمت. */
    fun clampLevel(level: Float): Float =
        if (level.isNaN()) 0f else level.coerceIn(0f, 1f)

    /** عدد المقاطع المضاءة لمستوى معين — تقريب لأقرب مقطع. */
    fun litSegments(level: Float): Int =
        (clampLevel(level) * SEGMENTS + 0.5f).toInt().coerceIn(0, SEGMENTS)

    /** منطقة المقطع المحدد (0-based من الجهة الهادئة) — الحدّ الأعلى للمقطع هو الفيصل. */
    fun zoneOf(segment: Int): MeterZone {
        val t = (segment + 1f) / SEGMENTS
        return when {
            t >= RED_FROM -> MeterZone.RED
            t >= AMBER_FROM -> MeterZone.AMBER
            else -> MeterZone.GREEN
        }
    }

    /**
     * مؤشر الذروة مع الحفظ والانحسار: يرتفع فورًا مع أي مستوى أعلى منه،
     * وينحسر بمقدار [decay] في كل تحديث ما لم يدفعه المستوى الحالي لأعلى.
     * لا ينزل أبدًا تحت المستوى الحالي.
     */
    fun peakIndex(prevPeak: Float, level: Float, decay: Float = PEAK_DECAY): Float {
        val lv = clampLevel(level)
        val prev = clampLevel(prevPeak)
        return if (lv >= prev) lv else (prev - decay).coerceAtLeast(lv)
    }

    /**
     * مقطع مؤشر الذروة للرسم (-1 عند الذروة شبه الصفرية أي بلا مؤشر).
     * المؤشر يجلس على أعلى مقطع بلغته الذروة.
     */
    fun peakSegment(peak: Float): Int {
        val p = clampLevel(peak)
        if (p < 0.02f) return -1
        return (kotlin.math.ceil(p * SEGMENTS).toInt() - 1).coerceIn(0, SEGMENTS - 1)
    }
}
