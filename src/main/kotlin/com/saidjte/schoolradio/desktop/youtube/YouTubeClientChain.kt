package com.saidjte.schoolradio.desktop.youtube

/**
 * سلسلة قنوات التشغيل (v0.44.0) — الإصلاح الجذري لعطل «استيراد اليوتيوب لم يعد يعمل»:
 *
 * يوتيوب منذ أواخر 2026 يفرض «Sign in to confirm you're not a bot» على القناة
 * الافتراضية في كثير من الشبكات (سُئد عليه فعليًا بأداة 2026.10.26 وأخرى 2026.11.04).
 * القنوات البديلة (tv وtv_simply وandroid_vr…) تمرّ كثيرًا حيث تفشل الافتراضية،
 * وقنوات أخرى تكشف صيغًا صوتية لا تكشفها القناة الأولى — لذا الحل الجذري
 * ليس قناة واحدة أعيد اختيارها، بل سلسلة محاولات تلقائية: أول قناة تنجح تُكمل.
 *
 * القواعد:
 * - المحاولة الأولى بلا تعديل قناة (سلوك الأداة الطبيعي).
 * - الخطأ القابل لإعادة المحاولة (تحقق البوت / لا صيغة متاحة / صفحة لا تُحمَّل)
 *   ينقلنا للقناة التالية؛ الخطأ الحاسم (مقطع محذوف/رابط غير مدعوم/رفض إذن)
 *   يوقف السلسلة فورًا برسالته — فلا إعادة عشوائية على أعطال لن تصلحها قناة.
 * - كل محاولة تُبلَّغ في الواجهة («المحاولة 2 من 8 عبر قناة بديلة…») فلا صمت.
 */
object YouTubeClientChain {

    /** محاولة واحدة في السلسلة: client=null يعني القناة الافتراضية للأداة. */
    data class Attempt(
        /** معرّف قناة yt-dlp أو null للقناة الافتراضية. */
        val client: String?,
        /** اسم عربي قصير يظهر في رسالة التقدم. */
        val label: String
    )

    /**
     * ترتيب القنوات: الافتراضية أولًا (أفضل جودة وأسرع)، ثم قنوات التلفاز
     * (الأكثر تجاوزًا لتحقق البوت في 2025–2026)، ثم المحمول والمتصفح المدمج
     * و Safari و iOS — كلها بلا ملفات كوكيز ولا أشخاص، ما تنجح أولًا تُكمل.
     */
    val CHAIN: List<Attempt> = listOf(
        Attempt(null, "القناة الافتراضية"),
        Attempt("tv", "قناة التلفاز"),
        Attempt("tv_simply", "قناة التلفاز المبسطة"),
        Attempt("android_vr", "قناة الواقع الافتراضي"),
        Attempt("mweb", "قناة الويب المحمول"),
        Attempt("web_embedded", "قناة التضمين"),
        Attempt("web_safari", "قناة Safari"),
        Attempt("ios", "قناة iOS")
    )

    /** عدد المحاولات الكلي (لرسائل التقدم «من 8»). */
    val size: Int get() = CHAIN.size

    /** محاولة بالترتيب (0..size-1) — خارج النطاق يعيد الأخيرة بأمان. */
    fun attempt(index: Int): Attempt = CHAIN[index.coerceIn(0, CHAIN.lastIndex)]

    /**
     * وسائط تحديد القناة لمحاولة معطاة — فارغة للقناة الافتراضية،
     * و«--extractor-args youtube:player_client=…» لغيرها.
     */
    fun extractorArgs(a: Attempt): List<String> =
        if (a.client == null) emptyList()
        else listOf("--extractor-args", "youtube:player_client=${a.client}")

    /**
     * هل يستحق الخطأ محاولة قناة تالية؟ نصوص الأداة إنجليزية فنصنّف بالأصل.
     * - تحقق البوت: القناة موبوطة — قناة أخرى غالبًا تنجح → أعِد.
     * - «Requested format is not available»: القنوات تكشف صيغًا مختلفة → أعِد.
     * - صفحة لا تُحمَّل: تعثر شبكي لحظي أو قناة مرفوضة → أعِد.
     * - مقطع غير متاح/غير مدعوم/رفض إذن نظام: حاسم لن يصلحه تغيير القناة → لا.
     */
    fun retryableError(stderr: String): Boolean {
        val s = stderr.lowercase()
        return when {
            "sign in to confirm" in s || "not a bot" in s -> true
            "requested format is not available" in s || "no supported format" in s -> true
            "unable to download webpage" in s -> true
            else -> false
        }
    }

    /**
     * هل استُنفدت السلسلة كلها على تحقق البوت؟ (رسالة الجولة الأخيرة
     * توجّه إلى خيار الكوكيز المرفق في الحوار نفسه — بلا تعليمات خارجية).
     */
    fun botCheck(stderr: String): Boolean =
        "sign in to confirm" in stderr.lowercase() || "not a bot" in stderr.lowercase()

    /**
     * رسالة التقدم لمحاولة معينة — تُعرض في الحوار أثناء السلسلة.
     */
    fun progressMessage(index: Int, base: String): String {
        val a = attempt(index)
        val ch = if (a.client == null) "" else " (عبر ${a.label})"
        return "$base — المحاولة ${index + 1} من $size$ch"
    }
}
