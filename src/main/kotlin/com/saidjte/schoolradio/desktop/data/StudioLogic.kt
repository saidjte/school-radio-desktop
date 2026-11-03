package com.saidjte.schoolradio.desktop.data

import java.util.TimeZone

/**
 * مصدر عرض السكريبت على لوحة القراءة في الاستوديو المدمج.
 */
enum class ScriptSource(val label: String) {
    FROM_SCHEDULE("من الجدولة"),
    MANUAL("اختيار يدوي")
}

/** حالة الهواء المجمّعة من كل مصادر الصوت في الاستوديو. */
data class StudioStatus(
    val micLive: Boolean,
    val recording: Boolean,
    val musicPlaying: Boolean
) {
    /** على الهواء = أي صوت يخرج أو يُسجَّل الآن. */
    val onAir: Boolean get() = micLive || recording || musicPlaying
}

/**
 * منطق الاستوديو المدمج: القرار المشترك بين الجدولة والسكريبت والبث.
 * دوال خالصة حتمية كاملة — مغطاة باختبارات وحدوية قبل أي رفع.
 */
object StudioLogic {

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

    /** تجميع حالة الهواء من مصادرها الثلاثة. */
    fun status(micLive: Boolean, recording: Boolean, musicPlaying: Boolean): StudioStatus =
        StudioStatus(micLive, recording, musicPlaying)

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
