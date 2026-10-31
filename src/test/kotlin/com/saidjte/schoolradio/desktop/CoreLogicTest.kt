package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.LevelMeter
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.ui.formatMs
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreLogicTest {

    private fun tempDir(): File {
        val d = File(System.getProperty("java.io.tmpdir"), "sr-desktop-test-${System.nanoTime()}")
        d.mkdirs()
        return d
    }

    @Test
    fun `wav writer roundtrip and header parse`() {
        val dir = tempDir()
        try {
            val f = File(dir, "a.wav")
            val writer = WavFileWriter(f, 8000, 1)
            val samples = 8000
            val data = ByteArray(samples * 2)
            for (i in 0 until samples) {
                val v = (sin(2.0 * PI * 220.0 * i / 8000) * 12000).toInt()
                data[2 * i] = (v and 0xFF).toByte()
                data[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
            }
            writer.write(data)
            writer.close()
            val info = assertNotNull(WavHeader.read(f))
            assertEquals(8000, info.sampleRate)
            assertEquals(1, info.channels)
            assertEquals(16, info.bitsPerSample)
            assertEquals(samples * 2L, info.dataBytes)
            assertEquals(
                1000L,
                WavFileWriter.durationMsOf(info.dataBytes, info.sampleRate, info.channels, info.bitsPerSample)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `level meter maps silence and loudness`() {
        assertEquals(0.0, LevelMeter.normalized(0.0))
        assertEquals(0.0, LevelMeter.rms(byteArrayOf(0, 0, 0, 0), 4))
        val loud = LevelMeter.normalized(0.5)   // -6dB تقريبًا
        assertTrue(loud in 0.85..1.0, "loud=$loud")
        val quiet = LevelMeter.normalized(0.01) // -40dB تقريبًا
        assertTrue(quiet in 0.0..0.35, "quiet=$quiet")
        assertTrue(quiet > 0.0)
    }

    @Test
    fun `mp3 frame header parse`() {
        val h = assertNotNull(
            Mp3Info.parseFrame(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()), 0)
        )
        assertEquals(44100, h.sampleRate)
        assertEquals(128, h.bitRateKbps)
        assertEquals(1152, h.samplesPerFrame)
        assertEquals(417, h.frameSize)
        assertEquals(2, h.channels)
        assertNull(Mp3Info.parseFrame(byteArrayOf(0x12, 0x34, 0x56, 0x78), 0))
    }

    @Test
    fun `mp3 duration via synthetic cbr file`() {
        val dir = tempDir()
        try {
            val f = File(dir, "fake.mp3")
            val frame = ByteArray(417)
            frame[0] = 0xFF.toByte(); frame[1] = 0xFB.toByte()
            frame[2] = 0x90.toByte(); frame[3] = 0x00
            val out = f.outputStream().buffered()
            repeat(200) { out.write(frame) }
            out.close()
            val info = assertNotNull(Mp3Info.analyze(f))
            // 200 إطار × 1152 عيّنة / 44100 ≈ 5.22 ثانية
            assertTrue(info.durationMs in 5000..5450, "duration=${info.durationMs}")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `id3v2 tag is skipped with correct offset`() {
        val dir = tempDir()
        try {
            val f = File(dir, "tagged.mp3")
            val frame = ByteArray(417)
            frame[0] = 0xFF.toByte(); frame[1] = 0xFB.toByte()
            frame[2] = 0x90.toByte(); frame[3] = 0x00
            val id3 = ByteArray(10 + 100)
            id3[0] = 'I'.code.toByte(); id3[1] = 'D'.code.toByte(); id3[2] = '3'.code.toByte()
            id3[3] = 3; id3[4] = 0; id3[5] = 0
            id3[6] = 0; id3[7] = 0; id3[8] = 0; id3[9] = 100 // synchsafe = 100
            val out = f.outputStream().buffered()
            out.write(id3)
            repeat(50) { out.write(frame) }
            out.close()
            val info = assertNotNull(Mp3Info.analyze(f))
            assertEquals(110L, info.dataOffset)
            // (417×50)×8/128000 ≈ 1.303 ثانية
            assertTrue(info.durationMs in 1250..1400, "duration=${info.durationMs}")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `scanner lists only audio files sorted by date`() {
        val dir = tempDir()
        try {
            val f1 = File(dir, "rec1.wav")
            WavFileWriter(f1, 8000, 1).use { w -> w.write(ByteArray(1600)) }
            File(dir, "note.txt").writeText("x")
            File(dir, "song.mp3").writeBytes(ByteArray(417))
            Thread.sleep(5)
            val f4 = File(dir, "rec2.wav")
            WavFileWriter(f4, 8000, 2).use { w -> w.write(ByteArray(3200)) }
            val items = RecordingsScanner.scan(dir)
            assertEquals(3, items.size)
            assertEquals("rec2", items[0].displayName) // الأحدث أولًا
            val rec1 = items.first { it.displayName == "rec1" }
            assertEquals(100L, rec1.durationMs)        // 1600 بايت / 8000 عيّنة×2 بايت
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `format ms`() {
        assertEquals("00:05", formatMs(5200L))
        assertEquals("04:05", formatMs(245_000L))
        assertEquals("61:00", formatMs(3_660_000L))
    }
}
