package com.saidjte.schoolradio.desktop.data

/**
 * جدولة الفقرات: فقرات الحلقة مرتبة بالأداء، لكلٍّ نوع ومدة مخططة
 * وملف صوتي مرتبط اختياري (موسيقى/تسجيل). تُحفظ في schedule.json.
 */
enum class SegmentKind(val label: String) {
    LIVE("بث مباشر"),
    MUSIC("موسيقى"),
    RECORDING("تسجيل"),
    SPEECH("كلمة/قراءة")
}

data class ScheduleItem(
    val id: Long,
    val title: String,
    val kind: SegmentKind = SegmentKind.SPEECH,
    val plannedSeconds: Long = 0L,
    val mediaPath: String? = null,
    val note: String = ""
)

data class ScheduleDoc(
    val episodeStartMinutes: Int? = null,
    val items: List<ScheduleItem> = emptyList()
)

/** حالة الفقرة أثناء التشغيل الحي للحلقة. */
enum class SegmentStatus(val label: String) {
    DONE("منتهية"),
    CURRENT("جارية"),
    UPCOMING("قادمة")
}

/** رياضيات الجدولة — دوال خالصة كاملة الحتمية، مغطاة باختبارات وحدوية. */
object ScheduleMath {

    fun totalSeconds(items: List<ScheduleItem>): Long =
        items.sumOf { it.plannedSeconds.coerceAtLeast(0L) }

    /** بداية كل فقرة كإزاحة تراكمية بالثواني من بداية الحلقة. */
    fun offsetsSeconds(items: List<ScheduleItem>): List<Long> {
        var acc = 0L
        return items.map { item ->
            val start = acc
            acc += item.plannedSeconds.coerceAtLeast(0L)
            start
        }
    }

    /** تحويل دقائق اليوم إلى "HH:mm" مع لفّ حول الساعة 24. */
    fun clockOf(totalMinutes: Int): String {
        val m = ((totalMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /** ساعة بدء كل فقرة "HH:mm" — null للجميع إذا لم تُحدَّد بداية الحلقة. */
    fun plannedClocks(items: List<ScheduleItem>, episodeStartMinutes: Int?): List<String?> {
        if (episodeStartMinutes == null) return List(items.size) { null }
        val offs = offsetsSeconds(items)
        return items.mapIndexed { i, _ -> clockOf(episodeStartMinutes + (offs[i] / 60L).toInt()) }
    }

    /** حالة كل فقرة أثناء التشغيل: ما قبل الجارية منتهية، وما بعدها قادمة، وبلا تشغيل كلها قادمة. */
    fun statuses(currentIndex: Int, count: Int): List<SegmentStatus> =
        List(count) { i ->
            when {
                currentIndex < 0 -> SegmentStatus.UPCOMING
                i < currentIndex -> SegmentStatus.DONE
                i == currentIndex -> SegmentStatus.CURRENT
                else -> SegmentStatus.UPCOMING
            }
        }

    /** نقل عنصر داخل قائمة بمقدار delta مع حدود آمنة (يعيد القائمة نفسها إن لم يتحرك شيء). */
    fun <T> moved(list: List<T>, index: Int, delta: Int): List<T> {
        val to = index + delta
        if (index < 0 || index >= list.size || to < 0 || to >= list.size || delta == 0) return list
        val m = list.toMutableList()
        val item = m.removeAt(index)
        m.add(to, item)
        return m
    }
}
