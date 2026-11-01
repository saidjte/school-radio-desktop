package com.saidjte.schoolradio.desktop.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * الجدولة الأسبوعية الدقيقة: مواعيد ثابتة في الأسبوع (يوم + ساعة:دقيقة)
 * تنطلق تلقائيًا أثناء عمل التطبيق — مكافئ أوقات AlarmManager في نسخة الهاتف.
 *
 * قرار الإطلاق خالص وحتمي (WeeklyMath.decide) ومغطى باختبارات:
 * - الحدث يُطلق مرة واحدة لكل تاريخ (قيد فريد حدث+تاريخ مثل نسخة الهاتف).
 * - الحدث الفائت أثناء إغلاق التطبيق (إقلاع متأخر/س sleep) يُسجَّل «فائت»
 *   عند الفتح — يعيد التهيئة تلقائيًا بعد إعادة التشغيل أو تغيّر الوقت.
 */
data class WeeklyEvent(
    val id: Long,
    val title: String? = null,
    /** يوم الأسبوع بمعيار ISO: 1 = الاثنين … 7 = الأحد. */
    val dayOfWeek: Int? = null,
    /** دقيقة البدء من منتصف الليل 0..1439. */
    val startMinutes: Int? = null,
    val enabled: Boolean? = null,
    /** عند الإطلاق: هل تبدأ الحلقة تلقائيًا أم يكتفي بالتنبيه؟ */
    val autoStartRun: Boolean? = null
)

data class WeeklyDoc(
    val events: List<WeeklyEvent> = emptyList(),
    /** آخر نبضة للجدول الزمني — أساس كشف الفائت بعد إغلاق/سكون. */
    val lastRunAtEpochMs: Long? = null
)

/** نتيجة قرار الإطلاق لحدث واحد في نبضة معينة. */
enum class FireStatus(val labelAr: String) {
    FIRED("نُفّذ"),
    MISSED("فائت")
}

/** قرار جاهز للتسجيل (حدث + حالة + لحظة القرار). */
data class FireDecision(
    val event: WeeklyEvent,
    val status: FireStatus,
    val dateKey: String,
    val atEpochMs: Long
)

/** رياضيات الجدولة الأسبوعية — دوال خالصة حتمية كاملة. */
object WeeklyMath {

    /** نافذة السماح: الحدث أحدث من هذا بالدقائق يُعدّ قابلًا للإطلاق، وأقدم منه يُعدّ فائتًا. */
    const val GRACE_MINUTES = 15

    private val dayNamesAr = listOf(
        "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"
    )

    fun dayName(isoDay: Int): String =
        dayNamesAr.getOrElse(isoDay - 1) { "؟" }

    fun dayNames(): List<String> = dayNamesAr

    fun minutesOfDay(hour: Int, minute: Int): Int =
        hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)

    /** ساعة البدء "HH:mm" من دقائق اليوم. */
    fun clockOf(totalMinutes: Int): String {
        val m = ((totalMinutes % (24 * 60)) + (24 * 60)) % (24 * 60)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun dateKey(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone }.format(Date(epochMs))

    fun isoDayOfWeek(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = epochMs; firstDayOfWeek = Calendar.MONDAY }
        val dow = cal.get(Calendar.DAY_OF_WEEK) // 1=الأحد .. 7=السبت
        return if (dow == Calendar.SUNDAY) 7 else dow - 1 // ISO: 1=الاثنين .. 7=الأحد
    }

    fun minutesOfDay(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = epochMs }
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun sanitize(e: WeeklyEvent): WeeklyEvent? {
        val day = e.dayOfWeek
        val start = e.startMinutes
        val title = e.title?.trim().orEmpty()
        if (e.id <= 0L || title.isEmpty() || day == null || day !in 1..7 ||
            start == null || start !in 0..1439
        ) return null
        return e.copy(title = title, dayOfWeek = day, startMinutes = start, enabled = e.enabled ?: true)
    }

    internal fun sanitized(e: WeeklyEvent): WeeklyEvent? = sanitize(e)

    /**
     * فحص الفائت بعد انقطاع (إغلاق التطبيق/سكون/تغيّر الوقت): يمسح الأيام بين
     * آخر نبضة محفوظة والآن — كل حدث مفعّل حلّ وقته في تلك الفترة ولم يُقيد يُعلن
     * MISSED. سقف المسح 31 يومًا. حتمي كامل.
     */
    fun missedBetween(
        events: List<WeeklyEvent>,
        firedKeys: Set<String>,
        fromEpochMs: Long,
        toEpochMs: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): List<FireDecision> {
        if (toEpochMs <= fromEpochMs) return emptyList()
        val dayMs = 86_400_000L
        var from = fromEpochMs
        if (toEpochMs - from > 31 * dayMs) from = toEpochMs - 31 * dayMs
        val out = mutableListOf<FireDecision>()
        val cal = Calendar.getInstance(zone)
        cal.timeInMillis = from
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        while (cal.timeInMillis <= toEpochMs) {
            val dayStart = cal.timeInMillis
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val isoDay = if (dow == Calendar.SUNDAY) 7 else dow - 1
            val dateK = dateKey(dayStart, zone)
            for (e in events) {
                val s = sanitize(e) ?: continue
                if (s.enabled != true || s.dayOfWeek != isoDay) continue
                val k = "${s.id}@$dateK"
                if (k in firedKeys) continue
                val slot = dayStart + (s.startMinutes ?: 0) * 60_000L
                if (slot in (from + 1)..toEpochMs) {
                    out.add(FireDecision(s, FireStatus.MISSED, dateK, toEpochMs))
                }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return out
    }

    /**
     * قرار نبضة واحدة: لكل حدث مفعّل يتقاطع تاريخه مع اليوم الحالي ولم يُسجَّل له
     * مفتاح «eventId@تاريخ» بعد:
     *  - إذا حان وقته ولم يتجاوز نافذة السماح → FIRED (يُطلق الآن).
     *  - إذا تجاوز وقته بنافذة السماح أو أكثر → MISSED (فائت).
     * الأحداث غير المعنية تعود بقائمة فارغة. حتمية كاملة عبر (zone، epochMs).
     */
    fun decide(
        events: List<WeeklyEvent>,
        firedKeys: Set<String>,
        nowEpochMs: Long,
        zone: TimeZone = TimeZone.getDefault()
    ): List<FireDecision> {
        val out = mutableListOf<FireDecision>()
        val today = isoDayOfWeek(nowEpochMs, zone)
        val nowMinutes = minutesOfDay(nowEpochMs, zone)
        val key = dateKey(nowEpochMs, zone)
        for (e in events) {
            val s = sanitize(e) ?: continue
            if (s.enabled != true) continue
            if (s.dayOfWeek != today) continue
            val k = "${s.id}@$key"
            if (k in firedKeys) continue
            val start = s.startMinutes ?: continue
            val delta = nowMinutes - start
            when {
                delta in 0 until GRACE_MINUTES ->
                    out.add(FireDecision(s, FireStatus.FIRED, key, nowEpochMs))
                delta >= GRACE_MINUTES ->
                    out.add(FireDecision(s, FireStatus.MISSED, key, nowEpochMs))
                else -> { /* لم يحان وقته بعد */ }
            }
        }
        return out
    }
}

/**
 * مخزن الأحداث الأسبوعية: تحميل/حفظ weekly.json ذريًا مع تعقيم الأيام والدقائق.
 */
class WeeklyStore(private val file: File) {

    private var nextId: Long = 1L
    private var doc: WeeklyDoc = load()

    private fun load(): WeeklyDoc {
        val d = Json.load(file, WeeklyDoc())
        val safe = d.copy(
            events = d.events.mapNotNull { e ->
                val title = e.title?.trim().orEmpty()
                val day = e.dayOfWeek
                val start = e.startMinutes
                if (e.id <= 0L || title.isEmpty() || day == null || day !in 1..7 ||
                    start == null || start !in 0..1439
                ) null
                else e.copy(title = title, dayOfWeek = day, startMinutes = start, enabled = e.enabled ?: true)
            },
            lastRunAtEpochMs = d.lastRunAtEpochMs?.takeIf { it > 0L }
        )
        nextId = (safe.events.maxOfOrNull { it.id } ?: 0L) + 1L
        return safe
    }

    fun events(): List<WeeklyEvent> = doc.events
    fun lastRunAt(): Long? = doc.lastRunAtEpochMs

    fun add(title: String, dayOfWeek: Int, startMinutes: Int, autoStartRun: Boolean): WeeklyEvent? {
        val clean = title.trim()
        if (clean.isEmpty() || dayOfWeek !in 1..7 || startMinutes !in 0..1439) return null
        val e = WeeklyEvent(nextId++, clean, dayOfWeek, startMinutes, true, autoStartRun)
        doc = doc.copy(events = doc.events + e)
        persist()
        return e
    }

    fun update(id: Long, title: String, dayOfWeek: Int, startMinutes: Int, autoStartRun: Boolean): Boolean {
        val clean = title.trim()
        if (clean.isEmpty() || dayOfWeek !in 1..7 || startMinutes !in 0..1439) return false
        val i = doc.events.indexOfFirst { it.id == id }
        if (i < 0) return false
        doc = doc.copy(
            events = doc.events.mapIndexed { k, e ->
                if (k == i) e.copy(title = clean, dayOfWeek = dayOfWeek, startMinutes = startMinutes, autoStartRun = autoStartRun)
                else e
            }
        )
        persist()
        return true
    }

    fun setEnabled(id: Long, on: Boolean): Boolean {
        val i = doc.events.indexOfFirst { it.id == id }
        if (i < 0) return false
        doc = doc.copy(events = doc.events.mapIndexed { k, e -> if (k == i) e.copy(enabled = on) else e })
        persist()
        return true
    }

    fun delete(id: Long): Boolean {
        if (doc.events.none { it.id == id }) return false
        doc = doc.copy(events = doc.events.filter { it.id != id })
        persist()
        return true
    }

    fun touch(epochMs: Long) {
        doc = doc.copy(lastRunAtEpochMs = epochMs)
        persist()
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
