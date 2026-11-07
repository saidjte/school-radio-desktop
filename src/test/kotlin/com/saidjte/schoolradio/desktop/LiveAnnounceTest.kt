package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.LiveAnnounceLogic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * عقد إعلان البث المباشر (v0.35.0): زر بنقرة واحدة يُطلق جينل الافتتاح من
 * القناة المستقلة ويضيء شارة على شاشة الطلاب مدة محدودة — المنطق النقي هنا:
 * اختيار الجينل المدمج ونص الشارة وحدود النشاط الشاملة/الحصرية.
 */
class LiveAnnounceTest {

    @Test
    fun `banner is active exactly for its duration window`() {
        val t0 = 1_000_000L
        assertFalse(LiveAnnounceLogic.isActive(null, t0))
        assertFalse(LiveAnnounceLogic.isActive(-5L, t0)) // قيمة عبثية تُرفض
        assertFalse(LiveAnnounceLogic.isActive(t0, t0 - 1))
        assertTrue(LiveAnnounceLogic.isActive(t0, t0)) // البداية شاملة
        assertTrue(LiveAnnounceLogic.isActive(t0, t0 + LiveAnnounceLogic.DURATION_MS - 1))
        assertFalse(LiveAnnounceLogic.isActive(t0, t0 + LiveAnnounceLogic.DURATION_MS)) // النهاية حصرية
        assertFalse(LiveAnnounceLogic.isActive(t0, t0 + 60_000L))
    }

    @Test
    fun `remaining time counts down to zero and never negative`() {
        val t0 = 5_000_000L
        assertEquals(LiveAnnounceLogic.DURATION_MS, LiveAnnounceLogic.remainingMs(t0, t0))
        assertEquals(5_000L, LiveAnnounceLogic.remainingMs(t0, t0 + LiveAnnounceLogic.DURATION_MS - 5_000L))
        assertEquals(0L, LiveAnnounceLogic.remainingMs(t0, t0 + LiveAnnounceLogic.DURATION_MS))
        assertEquals(0L, LiveAnnounceLogic.remainingMs(null, t0))
    }

    @Test
    fun `announce constants match the bundled jingle catalog`() {
        // الجينل المخصص للإعلان موجود ضمن الجينلات المدمجة السبع — فالزر يعمل دون شبكة
        val ids = com.saidjte.schoolradio.desktop.audio.BundledJingles.ALL.map { it.id }
        assertTrue(LiveAnnounceLogic.JINGLE_ID in ids)
        assertTrue(LiveAnnounceLogic.BANNER_TEXT.isNotBlank())
        assertTrue(LiveAnnounceLogic.DURATION_MS in 1_000L..60_000L)
    }
}
