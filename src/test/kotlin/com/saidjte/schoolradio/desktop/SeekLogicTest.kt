package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.data.SeekLogic
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * اختبارات شريط التقدم القابل للسعي (v0.23.0 «التنقل في المقطع بالنقر والسحب»):
 * تحويلات الموضع، حالة الزحف (نقر وسحب والتزام واحد)، جدول Xing المئوي،
 * وتحليله من ملف MP3 مُصنَّع حقيقي البنية.
 */
class SeekLogicTest {

    // ============== تحويلات الموضع ==============

    @Test
    fun `fraction of zero duration is zero`() {
        assertEquals(0f, SeekLogic.fractionForMs(500L, 0L))
        assertEquals(0L, SeekLogic.msForFraction(0.5f, 0L))
    }

    @Test
    fun `fraction clamps into 0 to 1`() {
        assertEquals(0f, SeekLogic.fractionForMs(-100L, 10_000L))
        assertEquals(1f, SeekLogic.fractionForMs(99_999L, 10_000L))
    }

    @Test
    fun `ms for fraction clamps into duration`() {
        assertEquals(0L, SeekLogic.msForFraction(-0.5f, 10_000L))
        assertEquals(10_000L, SeekLogic.msForFraction(2f, 10_000L))
        assertEquals(10_000L, SeekLogic.msForFraction(1f, 10_000L))
        assertEquals(0L, SeekLogic.msForFraction(0f, 10_000L))
    }

    @Test
    fun `ms for fraction rounds down deterministically`() {
        assertEquals(2_999L, SeekLogic.msForFraction(0.5f, 5_999L))
        assertEquals(5_000L, SeekLogic.msForFraction(0.5f, 10_000L))
    }

    @Test
    fun `roundtrip ms fraction ms stays within 1ms`() {
        val duration = 187_345L
        for (ms in longArrayOf(0L, 1L, 999L, 45_678L, duration - 1, duration)) {
            val back = SeekLogic.msForFraction(SeekLogic.fractionForMs(ms, duration), duration)
            assertTrue(abs(back - ms) <= 1L, "ms=$ms back=$back")
        }
    }

    // ============== حالة الزحف: نقر وسحب والتزام واحد ==============

    @Test
    fun `click is begin then end committing once`() {
        val s = SeekLogic.ScrubState()
        s.begin(1_200L)
        assertTrue(s.active)
        assertEquals(1_200L, s.previewMs)
        assertEquals(1_200L, s.end())
        assertFalse(s.active)
        assertNull(s.end()) // لا التزام ثانٍ أبدًا
        assertEquals(0L, s.previewMs)
    }

    @Test
    fun `drag previews live and commits last value`() {
        val s = SeekLogic.ScrubState()
        s.begin(0L)
        s.update(3_000L)
        s.update(7_500L)
        s.update(9_000L)
        assertEquals(9_000L, s.previewMs)
        assertEquals(9_000L, s.end())
        assertFalse(s.active)
    }

    @Test
    fun `update before begin is ignored`() {
        val s = SeekLogic.ScrubState()
        s.update(4_000L)
        assertFalse(s.active)
        assertEquals(0L, s.previewMs)
        assertNull(s.end())
    }

    @Test
    fun `update after end does not resurrect the scrub`() {
        val s = SeekLogic.ScrubState()
        s.begin(1_000L)
        assertEquals(1_000L, s.end())
        s.update(8_000L)
        assertFalse(s.active)
        assertEquals(0L, s.previewMs)
        assertNull(s.end())
    }

    @Test
    fun `negative preview values are floored at zero`() {
        val s = SeekLogic.ScrubState()
        s.begin(-50L)
        assertEquals(0L, s.previewMs)
        s.update(-1L)
        assertEquals(0L, s.previewMs)
    }

    // ============== جدول Xing المئوي ==============

    /** جدول متدرج خطيًا: toc[i] = i*255/99 — يصعد من 0 إلى 255. */
    private fun rampToc(): ByteArray = ByteArray(100) { i -> (i * 255 / 99).toByte() }

    @Test
    fun `toc byte fraction is monotonic and bounded`() {
        val toc = rampToc()
        var prev = -1f
        for (i in 0..20) {
            val f = SeekLogic.tocByteFraction(toc, i / 20f)
            assertTrue(f >= prev, "not monotonic at i=$i")
            assertTrue(f in 0f..1f, "out of bounds at i=$i")
            prev = f
        }
        assertEquals(0f, SeekLogic.tocByteFraction(toc, 0f))
        assertEquals(1f, SeekLogic.tocByteFraction(toc, 1f))
    }

    @Test
    fun `empty toc falls back to linear fraction`() {
        assertEquals(0.25f, SeekLogic.tocByteFraction(ByteArray(0), 0.25f))
        assertEquals(1f, SeekLogic.tocByteFraction(ByteArray(0), 4f))
    }

    @Test
    fun `ramp toc mid point matches half byte range`() {
        // toc[49] = 49*255/99 = 126 → 126/255 ≈ 0.494
        val f = SeekLogic.tocByteFraction(rampToc(), 49f / 99f)
        assertTrue(abs(f - 126f / 255f) < 1e-3, "f=$f")
    }

    // ============== تحليل ملف MP3 موسوم بـ Xing+TOC ==============

    /**
     * يبني MP3 مُصنَّعًا: إطاران حقيقيان (417 باية للإطار، 128kbps/44.1k/ستيريو)
     * والثاني يحمل وسم Xing بأعلام 0x07 (إطارات + بايتات + جدول 100 باية).
     */
    private fun buildXingTocMp3(dir: File, framesCount: Int, dataBytesField: Int): File {
        val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte())
        val frame = ByteArray(417)
        header.copyInto(frame)

        val out = ByteArray(417 * 2)
        frame.copyInto(out, 0)
        frame.copyInto(out, 417)

        // وسم Xing داخل الإطار الأول عند 4+32 = 36
        var p = 36
        "Xing".toByteArray(Charsets.US_ASCII).copyInto(out, p); p += 4
        val flags = 0x07
        out[p] = ((flags shr 24) and 0xFF).toByte(); out[p + 1] = ((flags shr 16) and 0xFF).toByte()
        out[p + 2] = ((flags shr 8) and 0xFF).toByte(); out[p + 3] = (flags and 0xFF).toByte()
        p += 4
        out[p] = ((framesCount shr 24) and 0xFF).toByte(); out[p + 1] = ((framesCount shr 16) and 0xFF).toByte()
        out[p + 2] = ((framesCount shr 8) and 0xFF).toByte(); out[p + 3] = (framesCount and 0xFF).toByte()
        p += 4
        out[p] = ((dataBytesField shr 24) and 0xFF).toByte(); out[p + 1] = ((dataBytesField shr 16) and 0xFF).toByte()
        out[p + 2] = ((dataBytesField shr 8) and 0xFF).toByte(); out[p + 3] = (dataBytesField and 0xFF).toByte()
        p += 4
        for (i in 0 until 100) out[p + i] = rampToc()[i]

        val f = File(dir, "xing-toc.mp3")
        f.writeBytes(out)
        return f
    }

    @Test
    fun `analyze extracts xing toc and data bytes`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "seek-test-${System.currentTimeMillis()}")
        tmp.mkdirs()
        try {
            val f = buildXingTocMp3(tmp, framesCount = 86, dataBytesField = 834)
            val info = Mp3Info.analyze(f)
            assertNotNull(info, "analyze failed")
            assertTrue(info.isVbr, "isVbr")
            assertEquals(834L, info.dataBytes, "dataBytes")
            assertNotNull(info.toc, "toc missing")
            assertEquals(100, info.toc!!.size, "toc size")
            // المدة من عدد الإطارات: 86*1152*1000/44100
            assertEquals(86L * 1152L * 1000L / 44100L, info.durationMs, "durationMs")
        } finally {
            tmp.deleteRecursively()
        }
    }

    @Test
    fun `plain mp3 without xing has no toc and is not vbr`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "seek-test-${System.currentTimeMillis()}")
        tmp.mkdirs()
        try {
            val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte())
            val out = ByteArray(417 * 2)
            header.copyInto(out, 0)
            header.copyInto(out, 417)
            val f = File(tmp, "plain.mp3")
            f.writeBytes(out)
            val info = Mp3Info.analyze(f)
            assertNotNull(info)
            assertFalse(info.isVbr)
            assertNull(info.toc)
        } finally {
            tmp.deleteRecursively()
        }
    }
}
