package com.saidjte.schoolradio.desktop.data

/**
 * إعلان البث المباشر (v0.35.0 — طلب المستخدم «اضافة زر اعلان البث المباشر»):
 * نقرة واحدة تُطلق جينل الافتتاح من قناة الجينل المستقلة وتُضيء شارة
 * «البث المباشر بدأ الآن» على شاشة الطلاب مدة محدودة. المنطق نقي هنا —
 * اختيار الجينل ونص الشارة وحدود نشاطها — والواجهة والمحرك يكتبان ما يقوله.
 */
object LiveAnnounceLogic {

    /** معرّف الجينل المدمج المخصص للإعلان — «افتتاحية مبهجة» من الجينلات السبع. */
    const val JINGLE_ID = "opening_jingle"

    /** نص شارة الإعلان على شاشة الطلاب. */
    const val BANNER_TEXT = "البث المباشر بدأ الآن"

    /** مدة بقاء الشارة مضاءة بالميلي ثانية. */
    const val DURATION_MS = 15_000L

    /** هل الشارة نشطة الآن؟ حدود شاملة البداية والحصرية عند النهاية. */
    fun isActive(startedAtMs: Long?, nowMs: Long): Boolean {
        if (startedAtMs == null) return false
        if (startedAtMs < 0L) return false
        return nowMs >= startedAtMs && nowMs < startedAtMs + DURATION_MS
    }

    /** المتبقي للشارة بالميلي ثانية — صفر خارج النشاط. */
    fun remainingMs(startedAtMs: Long?, nowMs: Long): Long {
        if (!isActive(startedAtMs, nowMs)) return 0L
        return (startedAtMs!! + DURATION_MS - nowMs).coerceAtLeast(0L)
    }

    /**
     * اختيار نغمة الإعلان (v0.45.0 — طلب «أضف إمكانية اختيار نغمة الاعلان»):
     * المحفوظ صالح يُعاد مهذّبًا، والغائب أو الفارغ أو غير المعروف (جينل حُذف
     * من المدمجات في نسخة أقدم) يعود للافتراضي «افتتاحية مبهجة» — فلا يبقى
     * الإعلان بلا نغمة أبدًا. النقاوة هنا كي يُختبر القرار بلا ملفات ولا واجهة.
     */
    fun resolveJingleId(saved: String?, knownIds: Collection<String>): String {
        val s = saved?.trim().takeUnless { it.isNullOrEmpty() } ?: return JINGLE_ID
        if (knownIds.none { it == s }) return JINGLE_ID
        return s
    }

    /** عنوان نغمة الإعلان من معرفها — «نغمة مخصصة» للمعرف الغريب غير المتوقع. */
    fun jingleTitleOf(id: String, titlesOf: (String) -> String?): String =
        titlesOf(id) ?: "نغمة مخصصة"

    // ================= v0.46.0: إنهاء البث + نغمة الإعلان من ملف المستخدم =================

    /** نص رسالة الإنهاء — الزر يطويق الشارة ويطفئ شارة الهواء المشتقة من الإعلان. */
    const val END_MESSAGE = "تم إنهاء البث المباشر"

    /**
     * رسالة الإنهاء الصادقة: إن بقيت مصادر حية تعمل (مايك/تسجيل/موسيقى) فإن
     * شارة الهواء تبقى مضاءة بحقها — فالرسالة تشرح ذلك بدل أن تُخفيه.
     */
    fun endMessage(otherSourcesLive: Boolean): String =
        if (otherSourcesLive) "$END_MESSAGE — مصادر حية (مايك/تسجيل/موسيقى) ما زالت تعمل"
        else END_MESSAGE

    /**
     * مصدر نغمة الإعلان: نغمة مدمجة بمعرفها أو ملف من ملفات المستخدم (v0.46.0 —
     * طلب «اضافة نغمة قابلة التخصيص»). القاعدة: ملف مخصص موجود وقابل للقراءة
     * يفوز دائمًا، وغاب الملف عاد الإعلان للنغمة المدمجة المختارة بدل أن يصمت.
     */
    sealed class AnnounceSource {
        /** نغمة من الجينلات المدمجة السبعة. */
        data class BuiltIn(val jingleId: String) : AnnounceSource()

        /** نغمة من ملف اختاره المستخدم — يعيش على قرصه ويُحفظ مساره في settings.json. */
        data class CustomFile(val path: String) : AnnounceSource()
    }

    /**
     * حسم مصدر نغمة الإعلان لحظة النقر: المسار المخصص المهذّب وملفه موجود
     * يعطي CustomFile، وكل حال أخرى (غائب/فارغ/ملف مفقود) تعطي BuiltIn بمعرّف
     * مهذّب بresolveJingleId نفسها — قرار واحد نقي يُختبر بلا واجهة ولا ملفات.
     */
    fun resolveAnnounceSource(
        customPath: String?,
        selectedJingleId: String,
        knownIds: Collection<String>,
        fileExists: (String) -> Boolean
    ): AnnounceSource {
        val p = customPath?.trim().takeUnless { it.isNullOrEmpty() }
        if (p != null && fileExists(p)) return AnnounceSource.CustomFile(p)
        return AnnounceSource.BuiltIn(resolveJingleId(selectedJingleId, knownIds))
    }

    /**
     * تسمية النغمة المخصصة في منتقي نغمة الإعلان: اسم الملف وحده يُقصّ عند
     * 24 محرفًا بعلامة الحذف حتى لا يمزّق اسمٌ طويل تخطيط الشريط العلوي.
     */
    fun customLabel(path: String, maxChars: Int = 24): String {
        val name = path.trim().replace('\\', '/').substringAfterLast('/').ifEmpty { "نغمة مخصصة" }
        return if (name.length > maxChars) name.take(maxChars) + "…" else name
    }
}
