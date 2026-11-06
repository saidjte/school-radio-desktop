package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * حالة الجلسة الأخيرة المحفوظة — طلب المستخدم v0.32.0 «موثوقية: استعادة
 * الجلسة الأخيرة كاملة عند إعادة الفتح (القائمة والسكريبت والموضع)»:
 * القائمة نفسها تُذكر أصلًا في playlist.json (المسارات والمؤشر والتكرار
 * والعشوائية منذ v0.23.0)، وsession.json يكمل الصورة كاملة: المقطع الجاري
 * وموضعه فيه لحظة الإغلاق، والفقرة المحددة على لوحة القراءة ووضع القراءة
 * الكاملة — فيفتح التطبيق على ما كان عليه المذيع بالضبط، وأول ضغطة تشغيل
 * تكمل من حيث توقف لا من الصفر.
 */
data class SessionJson(
    /** المسار المطلق للمقطع الجاري لحظة الحفظ (قد يكون من القائمة أو المكتبة). */
    val trackPath: String? = null,
    /** موضع التشغيل داخل المقطع بالمللي ثانية لحظة الحفظ. */
    val positionMs: Long? = null,
    /** الفقرة المحددة على لوحة القراءة (معرف داخل script.json). */
    val selectedSectionId: Long? = null,
    /** وضع «السكريبت كاملًا» على لوحة القراءة. */
    val fullScriptReading: Boolean? = null,
    /** طابع زمني للحفظ — للعرض التوثيقي فقط لا للقرار. */
    val savedAt: Long? = null
)

/**
 * القرار المعقم لاستعادة الجلسة — ما تفعله الواجهة/المحرك فعليًا بعد التعقيم:
 * لا ملف مفقود ولا موضع سالب ولا فقرة محذوفة يمرّ من هنا إطلاقًا.
 */
data class RestoredSession(
    /**
     * مؤشر المقطع المستعاد داخل القائمة الجارية (-1 = المقطع كان يُبثّ من
     * المكتبة مباشرة خارج القائمة — يُحمّل في المحرك دون مساس القائمة).
     */
    val playlistIndex: Int,
    val positionMs: Long,
    val selectedSectionId: Long?,
    val fullReading: Boolean
)

/**
 * منطق استعادة الجلسة — نقي قابل للاختبار بلا قرص ولا محرك: تعقيم صارم
 * وكل فشل يعود بأمانة للافتراضي فلا يكسر الإقلاع ملف تالف أبدًا.
 */
object SessionRestore {

    /** حبس الموضع داخل الحدود: سالب يعود صفرًا، وأعلى من المدة يعود نهايتها. */
    fun clampPosition(ms: Long?, durationMs: Long): Long {
        val v = (ms ?: 0L).coerceAtLeast(0L)
        return if (durationMs > 0L) v.coerceAtMost(durationMs) else v
    }

    /** مسار محفوظ معقم: غير فارغ بعد القص وإلا null. */
    private fun cleanPath(p: String?): String? = p?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * تحليل الجلسة المحفوظة:
     * @param playlistPaths مسارات القائمة الجارية كما حُمّلت من playlist.json
     * @param scriptIds معرفات فقرات السكريبت الموجودة فعليًا
     * @return القرار — null إن لم يوجد شيء قابل للاستعادة إطلاقًا
     */
    fun resolve(
        saved: SessionJson,
        playlistPaths: List<String>,
        scriptIds: Set<Long>
    ): RestoredSession? {
        val path = cleanPath(saved.trackPath)
        val idx = if (path != null) playlistPaths.indexOfFirst { it == path } else -1
        // المقطع المستعاد: إما في القائمة الجارية أو ملف موجود على القرص (بثّ من المكتبة)
        val fileOk = path != null && (idx >= 0 || File(path).isFile)
        val section = saved.selectedSectionId?.takeIf { it in scriptIds }
        if (!fileOk && section == null) return null
        return RestoredSession(
            playlistIndex = if (fileOk && idx >= 0) idx else -1,
            positionMs = clampPosition(saved.positionMs, 0L),
            selectedSectionId = section,
            fullReading = if (section != null) saved.fullScriptReading == true else false
        )
    }
}

/**
 * مخزن الجلسة الأخيرة: session.json ذريًّا بنمط بقية المخازن — حفظ خفيف
 * متكرر (موضع التشغيل يتقدم) فالكتابة الذرية بلا ثمن يُذكر.
 */
class SessionStore(private val file: File) {

    private var doc: SessionJson = load()

    private fun load(): SessionJson = try {
        Json.load(file, SessionJson())
    } catch (_: Exception) {
        SessionJson()
    }

    /** الجلسة المحفوظة كما هي — التعقيم في SessionRestore.resolve. */
    fun session(): SessionJson = doc

    /** حفظ لقطة الجلسة دفعة واحدة. */
    fun save(s: SessionJson) {
        doc = s
        Json.save(file, s)
    }
}
