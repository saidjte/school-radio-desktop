package com.saidjte.schoolradio.desktop.data

/**
 * مؤقت الفاصل الآلي (v0.31.0 «مؤقت فاصل آلي»):
 * كل فقرة جدولة لها زمن مخطط؛ المؤقت يعدّ تصاعديًا مع الحلقة القائمة أصلاً
 * ويُصدر قرارًا حتميًا كل نبضة: لا شيء / تحذير قرب النهاية / إطلاق الفاصل.
 *
 * الإطلاق = اللحظة التي بلغ فيها الزمن المخطط: عندها يُطلق جينل الفاصل
 * (تنبيه صوتي على الهواء) وتُنتقل للفقرة التالية آليًا بحسب الإعداد.
 * القرار نقية ومكتوب مرة واحدة: الفقرة بلا زمن مخطط لا تُطلق أبدًا،
 * والإطلاق يحدث مرة واحدة لكل فقرة (المتصل يحمل علامة «أُطلق»).
 */
object SegmentTimerLogic {

    /** قرار نبضة المؤقت لفقرة واحدة. */
    data class Decision(
        /** بلغ الزمن المخطط الآن — أطلق ما يُطلق مرة واحدة. */
        val fire: Boolean,
        /** داخل نافذة التحذير (الدقائق الأخيرة) — تلوين تحذيري في الواجهة. */
        val warn: Boolean,
        /** المتبقي بالمللي ثانية حتى نهاية الزمن المخطط (سالب = تجاوز). */
        val remainingMs: Long
    )

    /** الافتراضي: التحذير في آخر 30 ثانية. */
    const val DEFAULT_WARN_SECONDS = 30L

    /** حبس ثواني التحذير داخل حدود معقولة (5 ثوانٍ .. دقيقتان). */
    fun clampWarnSeconds(v: Int): Int = v.coerceIn(5, 120)

    /**
     * قرار فقرة واحدة عند زمن معطى. الفقرة بلا زمن مخطط (0 أو أقل)
     * تعود بقرار سلبي دائم — لا تحذير ولا إطلاق ولا «متبقي» وهمي.
     */
    fun decide(elapsedMs: Long, plannedSeconds: Long, warnSeconds: Long): Decision {
        if (plannedSeconds <= 0L) return Decision(fire = false, warn = false, remainingMs = 0L)
        val plannedMs = plannedSeconds * 1000L
        val remaining = plannedMs - elapsedMs
        val warnMs = warnSeconds.coerceAtLeast(0L) * 1000L
        return Decision(
            fire = elapsedMs >= plannedMs,
            warn = remaining in 0..warnMs && elapsedMs < plannedMs,
            remainingMs = remaining
        )
    }

    /** نص المتبقي للعرض: mm:ss، والتجاوز يُعرض بالصيغة «تجاوز mm:ss». */
    fun remainingLabel(d: Decision): String {
        val abs = kotlin.math.abs(d.remainingMs)
        val s = abs / 1000L
        val mm = s / 60L
        val ss = s % 60L
        val core = "%02d:%02d".format(mm, ss)
        return if (d.remainingMs < 0) "تجاوز $core" else core
    }
}
