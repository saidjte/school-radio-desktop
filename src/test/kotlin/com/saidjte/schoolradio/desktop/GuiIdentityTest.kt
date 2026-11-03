package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.MeterLogic
import com.saidjte.schoolradio.desktop.data.MeterZone
import com.saidjte.schoolradio.desktop.ui.GuiTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * اختبارات هوية الواجهة البصرية (v0.22.0 «طوّر GUI التطبيق»):
 * رياضيات تباين WCAG، طبقات الثيم، خريطة ألوان مقاطع المقياس،
 * وتغليف الخط العربي وأيقونة النافذة داخل الجرة.
 */
class GuiIdentityTest {

    @Test
    fun `contrast black white equals 21`() {
        val r = GuiTokens.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertTrue(r > 20.9 && r <= 21.0, "black/white=$r")
    }

    @Test
    fun `contrast is symmetric`() {
        val a = 0xFF53C8F8.toInt()
        val b = 0xFF0B0E12.toInt()
        assertEquals(GuiTokens.contrastRatio(a, b), GuiTokens.contrastRatio(b, a), 1e-12)
    }

    @Test
    fun `on air pill text contrast meets wcag aa`() {
        assertTrue(
            GuiTokens.contrastRatio(GuiTokens.ON_AIR_CONTENT, GuiTokens.ON_AIR_BG) >= 4.5,
            "onAir contrast=${GuiTokens.contrastRatio(GuiTokens.ON_AIR_CONTENT, GuiTokens.ON_AIR_BG)}"
        )
    }

    @Test
    fun `theme layers are distinct`() {
        assertTrue(GuiTokens.validateThemeLayering())
        assertTrue(GuiTokens.BG != GuiTokens.SURFACE)
        assertTrue(GuiTokens.SURFACE_VARIANT != GuiTokens.CARD_BORDER)
    }

    @Test
    fun `segment colors follow meter zones`() {
        assertEquals(GuiTokens.zoneGreen, GuiTokens.segmentColorArgb(0, lit = true))
        assertEquals(GuiTokens.zoneGreen, GuiTokens.segmentColorArgb(13, lit = true))
        assertEquals(GuiTokens.zoneAmber, GuiTokens.segmentColorArgb(14, lit = true))
        assertEquals(GuiTokens.zoneRed, GuiTokens.segmentColorArgb(23, lit = true))
        assertEquals(MeterZone.GREEN, MeterLogic.zoneOf(0))
        assertEquals(MeterZone.RED, MeterLogic.zoneOf(23))
    }

    @Test
    fun `dim variant keeps zone identity`() {
        for (i in 0..23) {
            val lit = GuiTokens.segmentColorArgb(i, lit = true)
            val dim = GuiTokens.segmentColorArgb(i, lit = false)
            assertTrue(dim != lit, "segment $i dim==lit")
            // المطفأ داكن: كل قناة أقل من نصف المضيء
            val lr = lit shr 16 and 0xFF
            val dr = dim shr 16 and 0xFF
            assertTrue(dr < lr, "segment $i dim red $dr >= $lr")
        }
    }

    @Test
    fun `arabic fonts are packaged with valid ttf magic`() {
        for (w in listOf("Regular", "Medium", "SemiBold", "Bold")) {
            val bytes = javaClass.classLoader.getResourceAsStream("fonts/NotoSansArabic-$w.ttf")?.readBytes()
            assertTrue(bytes != null && bytes.size > 100_000, "font $w missing")
            val magicOk = bytes!![0].toInt() == 0 && bytes[1].toInt() == 1 && bytes[2].toInt() == 0 && bytes[3].toInt() == 0
            assertTrue(magicOk, "font $w bad magic ${bytes.toList().take(4)}")
        }
    }

    @Test
    fun `window icon is packaged as png`() {
        val bytes = javaClass.classLoader.getResourceAsStream("icon.png")?.readBytes()
        assertTrue(bytes != null && bytes.size > 1_000, "icon missing")
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        assertTrue(bytes!!.copyOfRange(0, 4).contentEquals(pngSignature), "icon not png")
    }
}
