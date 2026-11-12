package com.saidjte.schoolradio.desktop.data

import java.util.TimeZone

/**
 * مصدر عرض السكريبت على لوحة القراءة في الاستوديو المدمج.
 */
enum class ScriptSource(val label: String) {
    FROM_SCHEDULE("من الجدولة"),
    MANUAL("اختيار يدوي")
}

/**
 * أبعاد حيزات شاشة الاستوديو — القيم الفعلية بعد الحسم من المحفوظ (v0.21.0):
 * الافتراضي يلبي «في الجانب الايمن اجعل الحيزين متساويين في الارتفاع»
 * (rightTopFrac = 0.5)، وكل قيمة محبوسة داخل حدود الواجهة.
 */
data class StudioLayout(
    val rightWidth: Float = 360f,
    val broadcastWidth: Float = 330f,
    val rightTopFrac: Float = 0.5f,
    val midTopFrac: Float = 0.72f,
    val bcMicFrac: Float = 0.45f
)

/**
 * حدود أبعاد الحيزات وحسمها — نقية قابلة للاختبار، تستعملها الواجهة عند
 * السحب (لتقييد الفاصل) والمخزن عند التحميل (لتعقيم الملف).
 */
object StudioLayoutLogic {
    const val RIGHT_WIDTH_MIN = 280f
    const val RIGHT_WIDTH_MAX = 560f
    const val BC_WIDTH_MIN = 280f
    const val BC_WIDTH_MAX = 480f
    const val RIGHT_TOP_MIN = 0.2f
    const val RIGHT_TOP_MAX = 0.8f
    const val MID_TOP_MIN = 0.3f
    const val MID_TOP_MAX = 0.9f
    const val BC_MIC_MIN = 0.25f
    const val BC_MIC_MAX = 0.8f

    /** حسم الأبعاد: المحفوظ داخل الحدود، وما خارجه أو الفاسد يعود للحد الأقرب. */
    fun resolve(l: LayoutJson?): StudioLayout {
        val d = StudioLayout()
        if (l == null) return d
        return StudioLayout(
            rightWidth = l.rightWidth?.coerceIn(RIGHT_WIDTH_MIN, RIGHT_WIDTH_MAX) ?: d.rightWidth,
            broadcastWidth = l.broadcastWidth?.coerceIn(BC_WIDTH_MIN, BC_WIDTH_MAX) ?: d.broadcastWidth,
            rightTopFrac = l.rightTopFrac?.coerceIn(RIGHT_TOP_MIN, RIGHT_TOP_MAX) ?: d.rightTopFrac,
            midTopFrac = l.midTopFrac?.coerceIn(MID_TOP_MIN, MID_TOP_MAX) ?: d.midTopFrac,
            bcMicFrac = l.bcMicFrac?.coerceIn(BC_MIC_MIN, BC_MIC_MAX) ?: d.bcMicFrac
        )
    }

    /** تحويل حالة الواجهة إلى JSON محفوظ — قيم نهائية مرقّمة. */
    fun toJson(l: StudioLayout) = LayoutJson(
        rightWidth = l.rightWidth,
        broadcastWidth = l.broadcastWidth,
        rightTopFrac = l.rightTopFrac,
        midTopFrac = l.midTopFrac,
        bcMicFrac = l.bcMicFrac
    )

    /** قيد نسبة داخل حدودها — تستعمله فواصل السحب لحظة بلحظة. */
    fun clampFrac(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)
}

/** حالة الهواء المجمّعة من كل مصادر الصوت في الاستوديو. */
data class StudioStatus(
    val micLive: Boolean,
    val recording: Boolean,
    val musicPlaying: Boolean,
    /** شارة إعلان البث المباشر مضاءة (v0.45.0 — طلب «زر الإعلان يجب أن يفعل شارة على الهواء»). */
    val announceActive: Boolean = false,
    /**
     * جلسة البث المعلنة مفتوحة (v0.46.0 — طلب «عند الاعلان عن البث لا يوجد زر
     * انتهاء البث»): الإعلان يفتحها و«إنهاء البث» يغلقها — فتظل شارة الهواء
     * مضاءة بعد خمود شارة الخمس عشرة ثانية حتى ينهيها المذيع بنفسه،
     * وهي حالة جلسة حية لا تُحفَظ: الإقلاع الجديد يفتحها مطفأة.
     */
    val broadcastLive: Boolean = false
) {
    /** على الهواء = أي صوت يخرج أو يُسجَّل الآن أو إعلان بث مضيء أو جلسة معلنة مفتوحة. */
    val onAir: Boolean get() = micLive || recording || musicPlaying || announceActive || broadcastLive
}

/**
 * منطق الاستوديو المدمج: القرار المشترك بين الجدولة والسكريبت والبث.
 * دوال خالصة حتمية كاملة — مغطاة باختبارات وحدوية قبل أي رفع.
 */
object StudioLogic {

    /**
     * الفقرة المعروضة في شاشة القراءة — طلب v0.21.0 «شاشة القراءة يجب أن تضم
     * سكريبت واحد لتجنب ارباك المذيع»: شاشة واحدة تعرض فقرة واحدة فقط.
     * متابعة الجارية مفعّلة (أو لا حلقة حية): نفس أولوية resolveScriptSection
     * (الجارية من الجدولة ثم اليدوي ثم الأولى). متابعة موقوفة أثناء حلقة حية:
     * المذيع أمسك القيادة — الفقرة المختارة يدويًا هي المعروضة ما دامت صالحة،
     * وإن لم يختر شيئًا تبقى الجارية حتى يختار.
     */
    fun resolveReadingSection(
        schedule: List<ScheduleItem>,
        runActive: Boolean,
        runIndex: Int,
        script: List<ScriptSection>,
        manualSectionId: Long?,
        followActive: Boolean
    ): Pair<ScriptSection, ScriptSource>? {
        if (script.isEmpty()) return null
        if (runActive && !followActive && manualSectionId != null && manualSectionId > 0L) {
            script.firstOrNull { it.id == manualSectionId }?.let { return it to ScriptSource.MANUAL }
        }
        return resolveScriptSection(schedule, runActive, runIndex, script, manualSectionId)
    }

    /**
     * أولوية عرض السكريبت على لوحة القراءة في الاستوديو المدمج:
     * 1) أثناء التشغيل الحي: سكريبت الفقرة الجارية المرتبط (من الجدولة).
     * 2) الفقرة المختارة يدويًا إن وجدت ومعروضة.
     * 3) أول فقرة في السكريبت.
     * تعيد null فقط إذا كان السكريبت فارغًا.
     * المعرفات غير الصالحة (محذوفة أو <= 0) تُتجاهل بأمان وتنتقل للأولوية التالية.
     */
    fun resolveScriptSection(
        schedule: List<ScheduleItem>,
        runActive: Boolean,
        runIndex: Int,
        script: List<ScriptSection>,
        manualSectionId: Long?
    ): Pair<ScriptSection, ScriptSource>? {
        if (script.isEmpty()) return null
        if (runActive) {
            val item = schedule.getOrNull(runIndex)
            val sid = item?.scriptSectionId
            if (sid != null && sid > 0L) {
                script.firstOrNull { it.id == sid }?.let { return it to ScriptSource.FROM_SCHEDULE }
            }
        }
        if (manualSectionId != null && manualSectionId > 0L) {
            script.firstOrNull { it.id == manualSectionId }?.let { return it to ScriptSource.MANUAL }
        }
        return script.first() to ScriptSource.MANUAL
    }

    /**
     * مؤشر الفقرة التالية عند إنهاء الحالية في التشغيل الحي للحلقة.
     * يعيد null عند بلوغ نهاية القائمة أو إذا كانت فارغة — إشارةً إلى إنهاء الحلقة.
     * الفهرس الشاذ (سالب أو خارج الحدود) يُعالج بأمان: سالب يعني البدء من الفقرة الأولى،
     * وخارج الحدود يعني الإنهاء.
     */
    fun nextRunIndex(currentIndex: Int, itemCount: Int): Int? {
        if (itemCount <= 0) return null
        val next = currentIndex + 1
        return if (next in 0 until itemCount) next else null
    }

    /** تجميع حالة الهواء من مصادرها الثلاثة وشارة الإعلان وجلسة البث المعلنة (v0.46.0). */
    fun status(
        micLive: Boolean,
        recording: Boolean,
        musicPlaying: Boolean,
        announceActive: Boolean = false,
        broadcastLive: Boolean = false
    ): StudioStatus = StudioStatus(micLive, recording, musicPlaying, announceActive, broadcastLive)

    /**
     * تسمية فقرة في منتقي السكريبت اليدوي على شاشة الاستوديو (v0.45.0 —
     * طلب «أضف إمكانية اختيار السكربتات يدويا من شاشة الاستوديو المدمج»):
     * «1. العنوان» — العنوان الفارغ «بدون عنوان»، والطويل يُقصّ عند 38
     * حرفًا بعلامة الحذف حتى لا يمزّق القائمة تخطيط الشريط.
     */
    fun pickerLabel(index: Int, title: String, maxChars: Int = 38): String {
        val t = title.trim().ifEmpty { "بدون عنوان" }
        val shown = if (t.length > maxChars) t.take(maxChars) + "…" else t
        return "${index + 1}. $shown"
    }

    /** ساعة الحاسوب "HH:mm:ss" من زمن إبوكيلي — صافية وقابلة للاختبار بمنطقة ثابتة. */
    fun clockText(epochMs: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val cal = java.util.Calendar.getInstance(zone).apply { timeInMillis = epochMs }
        return "%02d:%02d:%02d".format(
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            cal.get(java.util.Calendar.SECOND)
        )
    }

    // ================= استرجاع الاختيارات المحفوظة بالاسم (v0.20.0) =================

    /**
     * استرجاع اختيار رئيسي محفوظ بالاسم: الجهاز المطابق بالاسم أولًا، وإلا
     * أول بند (الافتراضي) — الاختيار الرئيسي لا يبقى فارغًا أبدًا. اسم غير
     * موجود (جهاز غير موصول) يرجع للأول دون مساس بالاسم المحفوظ.
     */
    fun <T> restorePrimary(items: List<T>, nameOf: (T) -> String, saved: String?): T? {
        if (items.isEmpty()) return null
        val s = saved?.trim().takeUnless { it.isNullOrEmpty() }
        if (s != null) items.firstOrNull { nameOf(it) == s }?.let { return it }
        return items.first()
    }

    /**
     * استرجاع اختيار ثانٍ اختياري: الجهاز المطابق بالاسم فقط — اسم غير موجود
     * أو null يعني «بلا» (null) فلا يُفتح جهاز ثانٍ سرًا.
     */
    fun <T> restoreOptional(items: List<T>, nameOf: (T) -> String, saved: String?): T? {
        val s = saved?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return items.firstOrNull { nameOf(it) == s }
    }
}
