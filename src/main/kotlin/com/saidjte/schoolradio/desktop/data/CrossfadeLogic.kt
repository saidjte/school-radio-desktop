package com.saidjte.schoolradio.desktop.data

/**
 * منطق الكروس فيد بين المقاطع (v0.31.0 «كروس فيد بين المقاطع»):
 * الانتقال الإذاعي المهني — المقطع الخارج يخفت خطيًّا إلى الصمت بينما
 * المقطع الداخل يصعد خطيًّا إلى الكسب الكامل في نفس النافذة الزمنية،
 * فيسمع المستمع انتقالًا متصلًا بلا صمت ولا قفزة.
 *
 * التطبيق الصوتي في MusicPlayerEngine يتم على كسب خطَّي الإخراج (MASTER_GAIN)
 * — قناة المزج المشتركة تبقى كسبًا كاملًا فلا يُلمس معادل الصوت ولا الفيدر
 * ولا الخفض التلقائي ولا مرجع إلغاء الصدى. كل الدوال هنا نقية على أرقام —
 * تُختبر وحدةً بلا أي عتاد صوت، والرياضيات هي موضع الأخطاء الصامتة
 * (نافذة صفرية، تلاشٍ يعيد الصعود، مجموع كسبين يفوق الوحدة بوضوح).
 */
object CrossfadeLogic {

    /** أدنى نافذة كروس فيد نافعة بالمللي ثانية — أقصر من هذا صمتٌ ملحوظ بلا انتقال. */
    const val MIN_MS = 500L

    /** أعلى نافذة معقولة — بعدها الانتقال يبتلع مقدمة المقطع الداخل. */
    const val MAX_MS = 10_000L

    /** الافتراضي المهني: ثلاث ثوانٍ كما في محطات البث. */
    const val DEFAULT_MS = 3_000L

    /** حبس نافذة الكروس فيد داخل الحدود — 0 يعني المعطّل ويُحترم كما هو. */
    fun clampMs(ms: Long): Long =
        if (ms <= 0L) 0L else ms.coerceIn(MIN_MS, MAX_MS)

    /**
     * هل حان لحظة التمديد؟ يُقيَّم بعد كل كتلة تشغيل:
     * المتبقي داخل النافذة (وبدون بلوغ النهاية أصلًا) ويوجد مقطع تالٍ —
     * والنافذة المعطلة (0) تُلغي التمديد كليًا فيبقى السلوك القديم.
     */
    fun shouldTrigger(remainingMs: Long, fadeMs: Long): Boolean =
        fadeMs > 0L && remainingMs > 0L && remainingMs <= fadeMs

    /**
     * كسب المقطع الخارج: خطي هابط من الوحدة إلى الصفر عبر النافذة.
     * قبل البدء = كامل، بعد اكتمالها = صمت (فتُغلق خطة الإخراج بعدها).
     */
    fun fadeOutGain(elapsedMs: Long, fadeMs: Long): Float {
        if (fadeMs <= 0L) return 0f
        if (elapsedMs <= 0L) return 1f
        val t = elapsedMs.toFloat() / fadeMs.toFloat()
        return (1f - t).coerceIn(0f, 1f)
    }

    /**
     * كسب المقطع الداخل: خطي صاعد من الصفر إلى الوحدة عبر النافذة نفسها —
     * مجموع الكسبين ≈ 1 في كل اللحظات فتبقى الحجم الكلي مستقرًا للذائع.
     */
    fun fadeInGain(elapsedMs: Long, fadeMs: Long): Float {
        if (fadeMs <= 0L) return 1f
        if (elapsedMs <= 0L) return 0f
        val t = elapsedMs.toFloat() / fadeMs.toFloat()
        return t.coerceIn(0f, 1f)
    }

    /** نص عرض النافذة للمستخدم (ثوانٍ بفاصلة عشرية عند الحاجة). */
    fun labelFor(ms: Long): String {
        if (ms <= 0L) return "معطّل"
        val sec = ms / 1000.0
        return if (sec == sec.toLong().toDouble()) "${sec.toLong()} ث" else String.format(java.util.Locale.ROOT, "%.1f ث", sec)
    }
}
