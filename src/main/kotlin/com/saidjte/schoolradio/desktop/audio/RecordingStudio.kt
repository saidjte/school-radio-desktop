package com.saidjte.schoolradio.desktop.audio

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * استوديو التسجيل الاحترافي (v0.43.0) — منطق خالص حتمي بلا أي عتاد.
 *
 * عشرة من بنود الطلب الأحد عشر تمرّ من هذه الطبقة قبل أن يلمسها المحرك:
 * جودة 16/24/32 بت ومعدلات 44.1/48/96/192 كيلوهرتز، وترميز PCM العريض،
 * ومقياس المستوى مع مؤشر التشبع، وآلة حالات الإيقاف/الاستئناف، وتفاوض
 * صيغة الالتقاط مع خطة التحويل، والجدولة الزمنية للتسجيل.
 * المحرك (RecordingEngine) ينفّذ ما تقوله هذه الطبقة حرفيًا فلا قرار
 * مخفي داخل خيط الالتقاط — وكل قرار مغطى اختبار عقد.
 */
object RecordingStudio {

    // ================= (6) جودة التسجيل: 16/24/32 بت =================

    /** عمق البت المسموح — PCM موقّع صغير الطرفية دائمًا (لا float). */
    val ALLOWED_BITS: List<Int> = listOf(16, 24, 32)

    // ================= (7) معدلات العينات: 44.1/48/96/192 كيلوهرتز =================

    /** معدلات العينات المسموحة بترتيبها العرضي. */
    val ALLOWED_RATES: List<Int> = listOf(44_100, 48_000, 96_000, 192_000)

    /**
     * جودة التسجيل المطلوبة: عمق بت × معدل عينات × قناة واحدة (أحادي) —
     * الإخراج قانوني WAV PCM صغير الطرفية دائمًا أيًا كانت قدرة العتاد:
     * ما يلتقطه الجهاز بصيغة أقل يُحوَّل إليها (إعادة تشكيل + ترميز).
     */
    data class Quality(val bits: Int = 16, val rateHz: Int = 48_000) {

        /** بايتات العينة الواحدة الواحدة القناة. */
        val bytesPerSample: Int get() = bits / 8

        /** حبس القيم داخل المسموح — أي مدخل فاسد يعود لأقرب قيمة قانونية. */
        fun clamp(): Quality = Quality(
            bits = ALLOWED_BITS.minByOrNull { abs(it - bits) } ?: 16,
            rateHz = ALLOWED_RATES.minByOrNull { abs(it - rateHz) } ?: 48_000
        )

        /** اسم العرض العربي: «24 بت · 48 كيلوهرتز». */
        fun labelAr(): String = "${
            when (rateHz) {
                44_100 -> "44.1"
                192_000 -> "192"
                else -> rateHz / 1000
            }
        } كيلوهرتز · ${bits} بت"
    }

    // ================= (6) ترميز PCM العريض: 24 و32 بت ↔ عائم =================

    /**
     * ترميز وفكّ PCM بعمق 24 و32 بت صغير الطرفية — نقية على مصفوفات.
     * عقد الأرقام: العائم في [−1..1]، والتشفير يقصّ قبل الكتابة فلا
     * يلتفّ كود التشبع أبدًا، وفكّ 32 بت يقسم على 2³¹ (أعلى قيمة سالبة
     * −2³¹ تُعار −1.0 بدقة لا انحراف فيها).
     */
    object PcmWide {

        /** فكّ 24 بت إلى عائم — يعيد عدد العينات المكتوبة في out. */
        fun bytes24ToFloats(bytes: ByteArray, n: Int, out: FloatArray): Int {
            val frames = n / 3
            if (frames <= 0) return 0
            var j = 0
            for (i in 0 until frames) {
                val b0 = bytes[j].toInt() and 0xFF
                val b1 = bytes[j + 1].toInt() and 0xFF
                val b2 = bytes[j + 2].toInt() and 0xFF
                var v = (b2 shl 16) or (b1 shl 8) or b0
                if (v >= 0x800000) v -= 0x1000000 // إشارة تمديد من 24 بت
                out[i] = v / 8_388_608.0f
                j += 3
            }
            return frames
        }

        /** ترميز عائم إلى 24 بت — قصّ إلى [−1..1] قبل الكتابة. */
        fun floatsToBytes24(src: FloatArray, count: Int, out: ByteArray): Int {
            var j = 0
            for (i in 0 until count) {
                val v = (src[i] * 8_388_607.0f).toInt().coerceIn(-8_388_608, 8_388_607)
                out[j] = (v and 0xFF).toByte()
                out[j + 1] = ((v shr 8) and 0xFF).toByte()
                out[j + 2] = ((v shr 16) and 0xFF).toByte()
                j += 3
            }
            return j
        }

        /** فكّ 32 بت موقّع إلى عائم — يعيد عدد العينات المكتوبة. */
        fun bytes32ToFloats(bytes: ByteArray, n: Int, out: FloatArray): Int {
            val frames = n / 4
            if (frames <= 0) return 0
            var j = 0
            for (i in 0 until frames) {
                val b0 = bytes[j].toInt() and 0xFF
                val b1 = bytes[j + 1].toInt() and 0xFF
                val b2 = bytes[j + 2].toInt() and 0xFF
                val b3 = bytes[j + 3].toInt() and 0xFF
                var v = (b3 shl 24) or (b2 shl 16) or (b1 shl 8) or b0
                if (v >= 0x80000000.toInt()) v -= 0x100000000L.toInt()
                out[i] = v / 2_147_483_648.0f
                j += 4
            }
            return frames
        }

        /** ترميز عائم إلى 32 بت موقّع — قصّ قبل الكتابة. */
        fun floatsToBytes32(src: FloatArray, count: Int, out: ByteArray): Int {
            var j = 0
            for (i in 0 until count) {
                val v = (src[i] * 2_147_483_647.0f).toInt().coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
                out[j] = (v and 0xFF).toByte()
                out[j + 1] = ((v shr 8) and 0xFF).toByte()
                out[j + 2] = ((v shr 16) and 0xFF).toByte()
                out[j + 3] = ((v shr 24) and 0xFF).toByte()
                j += 4
            }
            return j
        }

        /** تحويل عينات 16 بت إلى عائم. */
        fun shortsToFloats(src: ShortArray, count: Int, out: FloatArray): Int {
            val n = count.coerceAtMost(out.size)
            for (i in 0 until n) out[i] = src[i] / 32768.0f
            return n
        }

        /** ترميز عائم إلى 16 بت بقصّ آمن. */
        fun floatsToShorts(src: FloatArray, count: Int, out: ShortArray): Int {
            val n = count.coerceAtMost(out.size)
            for (i in 0 until n) {
                val v = (src[i] * 32767.0f).toInt().coerceIn(-32768, 32767)
                out[i] = v.toShort()
            }
            return n
        }
    }

    // ================= (8)+(9) المقياس اللحظي ومؤشر التشبع =================

    /** إطار قياس واحد: جذر تربيعي متوسط + ذروة + علم تشبع. */
    data class FrameMeter(val rmsAmp: Double, val peakAmp: Double, val clipped: Boolean)

    /** عتبة التشبع: ذروة تجاوزت هذا تعُدّ تشبعًا (≈ −0.26 dBFS). */
    const val CLIP_THRESHOLD = 0.997

    /**
     * تحليل كتلة عينات عائمة: ذروة مطلقة وRMS وعلم تشبع.
     * الكتلة الخاوية أو الفاسدة تُعيد صمتًا صريحًا لا استثناءً.
     */
    fun analyzeFrame(floats: FloatArray, count: Int): FrameMeter {
        if (count <= 0) return FrameMeter(0.0, 0.0, false)
        var peak = 0.0
        var sum = 0.0
        val n = count.coerceAtMost(floats.size)
        for (i in 0 until n) {
            val v = abs(floats[i].toDouble())
            if (v > peak) peak = v
            sum += v * v
        }
        val rms = sqrt(sum / n)
        return FrameMeter(rms, peak, peak >= CLIP_THRESHOLD)
    }

    /** تحويل RMS إلى معياري 0..1 على مقياس −54 ديسيبل (عقد LevelMeter نفسه). */
    fun normalized(rmsAmp: Double, floorDb: Double = -54.0): Double {
        if (rmsAmp <= 0.0) return 0.0
        val db = 20.0 * log10(rmsAmp)
        return ((db - floorDb) / -floorDb).coerceIn(0.0, 1.0)
    }

    /**
     * مؤشر التشبع المقفول: أي كتلة مشبعة تُضيء المؤشر أحمر ثمانمئة
     * ملي ثانية حتى إن مرّ الصمت بعدها، ويعدّ الأحداث لا الإطارات —
     * فالذروة الطويلة الواحدة لا تنتفخ في العدّ إلا حدثًا واحدًا.
     */
    class ClipLatch(private val holdMs: Long = 800L) {
        private var lastClipAtMs = Long.MIN_VALUE
        private var _count = 0

        /** عدد أحداث التشبع المتراكمة منذ التصفير. */
        val count: Int get() = _count

        /** هل المؤشر مضيء الآن؟ */
        fun active(nowMs: Long): Boolean = lastClipAtMs != Long.MIN_VALUE && nowMs - lastClipAtMs <= holdMs

        /** تغذية بإطار جديد — الحدث الجديد يُعدّ فقط بعد انحسار القفل. */
        fun onFrame(clipped: Boolean, nowMs: Long) {
            if (!clipped) return
            val isNew = lastClipAtMs == Long.MIN_VALUE || nowMs - lastClipAtMs > holdMs
            lastClipAtMs = nowMs
            if (isNew) _count++
        }

        /** تصفير للجلسة التالية. */
        fun reset() {
            lastClipAtMs = Long.MIN_VALUE
            _count = 0
        }
    }

    // ================= (5) آلة حالات الإيقاف والاستئناف =================

    /** حالات مسجّل احترافي واحد. */
    enum class RecState { IDLE, RECORDING, PAUSED }

    /**
     * آلة حالات التسجيل — القرار كله هنا والمحرك ينفّذ فقط:
     * البدء من الخمول، والإيقاف المؤقت من التسجيل وحده، والاستئناف
     * من التوقف وحده، والإنهاء من أي حالة نشطة — وكل انتقال مخالف
     * يُردّ بأمانة (false) فلا حالة مبتورة تُكتب في الملف.
     */
    class StateMachine(initial: RecState = RecState.IDLE) {
        var state: RecState = initial
            private set

        /** بدء تسجيل جديد من الخمول. */
        fun start(): Boolean =
            if (state == RecState.IDLE) { state = RecState.RECORDING; true } else false

        /** إيقاف مؤقت — الخيط يكفّ عن القراءة فالمدة تتجمد عند المكتوب. */
        fun pause(): Boolean =
            if (state == RecState.RECORDING) { state = RecState.PAUSED; true } else false

        /** استئناف — نفس الملف يُكمل من موضع التوقف. */
        fun resume(): Boolean =
            if (state == RecState.PAUSED) { state = RecState.RECORDING; true } else false

        /** إنهاء وحفظ — مسموح من التسجيل والتوقف المؤقت معًا. */
        fun stop(): Boolean =
            if (state == RecState.IDLE) false else { state = RecState.IDLE; true }
    }

    // ================= تفاوض صيغة الالتقاط وخطة التحويل =================

    /** صيغة التقاط مرشحة من جهاز (معدل × بت × قنوات). */
    data class CaptureFormat(val rateHz: Int, val bits: Int, val channels: Int) {
        fun valid(): Boolean = rateHz > 0 && bits > 0 && channels > 0
    }

    /**
     * اختيار صيغة الالتقاط لجهاز ما وفق الجودة المطلوبة — حتمي بالترتيب:
     * (1) المعدل والبت المطلوبان تمامًا، (2) نفس المعدل وأقرب بت،
     * (3) أقرب معدل لا يتجاوز المطلوب فأقرب معدل مطلقًا، (4) عند غياب
     * أي إعلان: 16 بت على المعدل المطلوب — والتحويل دائمًا يكمل الطريق
     * إلى الجودة المطلوبة أينما التقطنا.
     */
    fun pickCapture(target: Quality, device: List<CaptureFormat>): CaptureFormat {
        val cands = device.filter { it.valid() }.distinct()
        if (cands.isEmpty()) return CaptureFormat(target.rateHz, 16, 1)

        cands.firstOrNull { it.rateHz == target.rateHz && it.bits == target.bits }?.let { return it }

        val sameRate = cands.filter { it.rateHz == target.rateHz }
        if (sameRate.isNotEmpty()) {
            return sameRate.minWithOrNull(
                compareBy<CaptureFormat> { abs(it.bits - target.bits) }.thenByDescending { it.bits }
            )!!
        }

        val below = cands.filter { it.rateHz < target.rateHz }
        val ratePool = if (below.isNotEmpty()) below else cands
        // أقرب معدل إلى المطلوب داخل المجموعة المختارة
        val bestRate = ratePool.minWithOrNull(compareBy<CaptureFormat> { abs(it.rateHz - target.rateHz) })!!.rateHz
        val atRate = cands.filter { it.rateHz == bestRate }
        return atRate.minWithOrNull(
            compareBy<CaptureFormat> { abs(it.bits - target.bits) }.thenByDescending { it.bits }
        )!!
    }

    /** هل خطة التحويل تتطلب إعادة تشكيل المعدل؟ */
    fun needsResample(capture: CaptureFormat, target: Quality): Boolean =
        capture.rateHz != target.rateHz

    /** هل خطة التحويل تتطلب إعادة ترميز العمق؟ */
    fun needsReencode(capture: CaptureFormat, target: Quality): Boolean =
        capture.bits != target.bits

    /**
     * وصف عربي لخطة التحويل إن وُجدت — يُعرض في لوحة الاستوديو فالمذيع
     * يعلم أن ملفه سيخرج بالجودة المطلوبة كاملة وإن كان عتاده أقل.
     */
    fun conversionLabel(capture: CaptureFormat, target: Quality): String? {
        val parts = mutableListOf<String>()
        if (needsResample(capture, target)) {
            parts += "يُعاد تشكيله من ${capture.rateHz / 1000.0} إلى ${target.rateHz / 1000.0} كيلوهرتز"
        }
        if (needsReencode(capture, target)) {
            parts += "يُرمَّز ${target.bits} بت"
        }
        return if (parts.isEmpty()) null else "الالتقاط ${capture.rateHz / 1000.0} كيلوهرتز × ${capture.bits} بت — " + parts.joinToString(" و")
    }

    // ================= (10) التسجيل المجدول =================

    /** قاعدة التكرار: مرة واحدة، أو يوميًا، أو أيام أسبوعية محددة. */
    const val REPEAT_ONCE = "once"
    const val REPEAT_DAILY = "daily"
    const val REPEAT_WEEKLY = "weekly"

    /** نافذة البدء المتأخر (منة الأسبوعية) — الحدث الأحدث من هذا يُعدّ قابلًا للإطلاق. */
    const val GRACE_MINUTES = 15

    /** نافذة الدقيقة الواحدة للتسجيل بلا مدة محددة (يوقفه المذيع يدويًا). */
    const val MANUAL_WINDOW_MIN = 2

    /** بند جدولة تسجيل — خالص من أي مرجع واجهة، يُحفظ كما هو في settings.json. */
    data class ScheduledItem(
        val id: String,
        val title: String,
        /** دقيقة البدء من منتصف الليل 0..1439. */
        val startMinutes: Int,
        /** مدة التسجيل بالدقائق — null = بلا حد يوقفه المذيع يدويًا. */
        val durationMinutes: Int?,
        /** once / daily / weekly. */
        val repeat: String,
        /** أيام الأسبوع بمعيار ISO: 1 = الاثنين … 7 = الأحد (للأسبوعي فقط). */
        val weeklyDays: List<Int> = emptyList(),
        val enabled: Boolean = true,
        /** آخر تاريخ نُفّذ فيه بصيغة yyyy-MM-dd — قيد عدم التكرار نفسه. */
        val lastFiredDateKey: String? = null
    )

    /** حالة قرار البند في نبضة الجدولة. */
    enum class SchedStatus(val labelAr: String) {
        DUE("مستحق الآن"),
        WAITING("بانتظار موعده"),
        FIRED_TODAY("نُفّذ اليوم"),
        DISABLED("معطّل"),
        MISSED("فائت")
    }

    /** نتيجة القرار: البند + حالته + مفتاح تاريخ اليوم + لحظة القرار. */
    data class SchedDecision(val item: ScheduledItem, val status: SchedStatus, val dateKey: String, val atEpochMs: Long)

    /** مفتاح التاريخ yyyy-MM-dd بلحظة زمنية — وقت الجهاز المحلي. */
    fun dateKeyOf(epochMs: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    /** دقيقة اليوم 0..1439 بلحظة زمنية — وقت الجهاز المحلي. */
    fun minutesOfDayOf(epochMs: Long): Int {
        val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
        return t.hour * 60 + t.minute
    }

    /** يوم الأسبوع بمعيار ISO: 1 = الاثنين … 7 = الأحد. */
    fun isoDayOfWeek(epochMs: Long): Int = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).dayOfWeek.value

    /** نهاية نافذة البند بالدقائق من منتصف الليل. */
    fun windowEndMinutes(item: ScheduledItem): Int =
        item.startMinutes + (item.durationMinutes ?: MANUAL_WINDOW_MIN)

    /**
     * قرار بند واحد في نبضة معينة — حتمي كامل كالجدولة الأسبوعية:
     * المعطّل DISABLED، والمنفّذ اليوم FIRED_TODAY، والمرة الواحدة التي
     * نفّذت يومًا ما لا تعود أبدًا، والأسبوعي بنطاق أيامه، والمستحق
     * داخل نافذته DUE، والذي فاتت نافذته من قريب MISSED، وسواه WAITING.
     */
    fun decide(item: ScheduledItem, nowEpochMs: Long): SchedDecision {
        val dateKey = dateKeyOf(nowEpochMs)
        val d = SchedDecision(item, SchedStatus.WAITING, dateKey, nowEpochMs)
        if (!item.enabled) return d.copy(status = SchedStatus.DISABLED)
        if (item.lastFiredDateKey == dateKey) return d.copy(status = SchedStatus.FIRED_TODAY)
        if (item.repeat == REPEAT_ONCE && item.lastFiredDateKey != null) {
            return d.copy(status = SchedStatus.FIRED_TODAY) // «نُفّذ» في يوم سابق — لا عودة
        }
        if (item.repeat == REPEAT_WEEKLY && isoDayOfWeek(nowEpochMs) !in item.weeklyDays) {
            return d.copy(status = SchedStatus.WAITING)
        }
        val now = minutesOfDayOf(nowEpochMs)
        val start = item.startMinutes.coerceIn(0, 1439)
        val end = windowEndMinutes(item)
        return when {
            now >= start && now < end -> d.copy(status = SchedStatus.DUE)
            now < start -> d.copy(status = SchedStatus.WAITING)
            now - end <= GRACE_MINUTES -> d.copy(status = SchedStatus.MISSED)
            else -> d.copy(status = SchedStatus.WAITING)
        }
    }

    /** وصف عربي لموعد البند القادم — «اليوم 08:30» أو اسم اليوم القادم. */
    fun nextLabel(item: ScheduledItem, nowEpochMs: Long): String {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val dayNames = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
        val clock = "%02d:%02d".format(item.startMinutes / 60, item.startMinutes % 60)
        if (!item.enabled) return "معطّل — $clock"
        val todayKey = dateKeyOf(nowEpochMs)
        val firedToday = item.lastFiredDateKey == todayKey
        return when (item.repeat) {
            REPEAT_ONCE ->
                if (item.lastFiredDateKey != null) "نُفّذ سابقًا"
                else if (!firedToday && minutesOfDayOf(nowEpochMs) < item.startMinutes) "اليوم $clock"
                else "قادم: $clock"
            REPEAT_DAILY ->
                if (firedToday || minutesOfDayOf(nowEpochMs) >= windowEndMinutes(item)) "غدًا $clock"
                else "اليوم $clock"
            else -> {
                if (item.weeklyDays.isEmpty()) return "أسبوعي — بلا أيام محددة"
                val day = isoDayOfWeek(nowEpochMs)
                val upcoming = item.weeklyDays.sorted().firstOrNull { it > day }
                    ?: item.weeklyDays.min()
                val label = if (upcoming == day && !firedToday && minutesOfDayOf(nowEpochMs) < windowEndMinutes(item)) {
                    "اليوم $clock"
                } else {
                    "${dayNames[upcoming - 1]} $clock"
                }
                label
            }
        }
    }

    /** وصف التكرار العربي المختصر — رقائق القائمة. */
    fun repeatLabel(item: ScheduledItem): String = when (item.repeat) {
        REPEAT_ONCE -> "مرة واحدة"
        REPEAT_DAILY -> "يوميًا"
        REPEAT_WEEKLY -> {
            val names = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
            if (item.weeklyDays.isEmpty()) "أسبوعيًا (بلا أيام)"
            else item.weeklyDays.sorted().joinToString("، ") { names[(it - 1).coerceIn(0, 6)] }
        }
        else -> "غير معروف"
    }

    /** ساعة العرض «08:30» من دقائق اليوم. */
    fun clockOf(minutes: Int): String = "%02d:%02d".format(minutes.coerceIn(0, 1439) / 60, minutes.coerceIn(0, 1439) % 60)

    /** تعقيم بند قادم من الحوار أو المحفوظ: عنوان معقّم وقيم محبوسة وأيام صالحة. */
    fun sanitize(item: ScheduledItem): ScheduledItem {
        val validRepeat = when (item.repeat) {
            REPEAT_ONCE, REPEAT_DAILY, REPEAT_WEEKLY -> item.repeat
            else -> REPEAT_ONCE
        }
        return item.copy(
            title = item.title.trim().take(60).ifEmpty { "تسجيل مجدول" },
            startMinutes = item.startMinutes.coerceIn(0, 1439),
            durationMinutes = item.durationMinutes?.coerceIn(1, 600),
            repeat = validRepeat,
            weeklyDays = if (validRepeat == REPEAT_WEEKLY) item.weeklyDays.filter { it in 1..7 }.distinct().sorted() else emptyList(),
            enabled = item.enabled,
            lastFiredDateKey = item.lastFiredDateKey?.takeIf { it.length == 10 }
        )
    }
}
