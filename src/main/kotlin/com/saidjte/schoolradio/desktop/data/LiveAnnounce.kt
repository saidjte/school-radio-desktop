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
}
