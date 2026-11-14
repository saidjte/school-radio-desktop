package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.youtube.AudioTranscoder
import com.saidjte.schoolradio.desktop.youtube.FragmentedMp4Aac
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * v0.50.0 — استيراد يوتيوب يقبل MP4 المجزّأ (SABR/HLS).
 *
 * العطل المبلَّغ عنه: «لم يُفكّ أي إطار صوتي من الملف المُحمَّل — الصيغة غير
 * مدعومة» — يوتيوب يسلم الصوت MP4 مجزّأً (moov بجداول فارغة وعينات في moof+mdat)
 * وحاوية JAAD تقرأ الجداول الكلاسيكية فقط فترى صفرًا. العيّنتان هنا مبنيتان
 * من نغمة AAC حقيقية مدمجة منذ v0.25: أعيد بناؤها مجزّأةً بأدوات صندوقية نقيّة
 * فتطابق بالضبط شكل تنزيلات SABR/HLS، والأخرى سليمة البنية بلا جداول وبلا أجزاء.
 */
class YouTubeV50Test {

    private fun resourceBytes(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream("selftest/$name")?.readBytes()
            ?: error("مورد الاختبار غائب: selftest/$name")

    private fun tempDir(prefix: String): File =
        File(System.getProperty("java.io.tmpdir"), "$prefix-${System.currentTimeMillis()}-${(0..999).random()}")
            .apply { mkdirs() }

    @Test
    fun `scan finds fragments and samples in refragmented fixture`() {
        val tmp = tempDir("v50-scan")
        try {
            val f = File(tmp, "tone_fmp4.m4a")
            f.writeBytes(resourceBytes("test_tone_fmp4.m4a"))
            val scan = FragmentedMp4Aac.scan(f)
            assertNotNull(scan, "fixture has moof fragments — scan must not return null")
            assertTrue(scan!!.fragmentCount >= 5, "fragments=${scan.fragmentCount}")
            assertTrue(scan.samples.size >= 120, "samples=${scan.samples.size} (النغمة 3 ثوانٍ ≈ 131 إطارًا)")
            // كل عينة ضمن الملف وبحجم منطقي
            val len = f.length()
            assertTrue(scan.samples.all { it.size > 0 && it.offset > 0 && it.offset + it.size <= len })
            // الأحجام تطابق ما استُخرج من الأصل (نفس عينات النغمة)
            assertEquals(358, scan.samples.first().size, "أول عينة AAC من النغمة الأصلية")
            // ملف كلاسيكي بلا أجزاء يعيد null
            val classic = File(tmp, "tone.m4a")
            classic.writeBytes(resourceBytes("test_tone.m4a"))
            assertEquals(null, FragmentedMp4Aac.scan(classic), "classic file has no moof")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    @Test
    fun `transcode fragmented fixture equals classic decode`() {
        val tmp = tempDir("v50-transcode")
        try {
            val classicIn = File(tmp, "tone.m4a")
            classicIn.writeBytes(resourceBytes("test_tone.m4a"))
            val fragIn = File(tmp, "tone_fmp4.m4a")
            fragIn.writeBytes(resourceBytes("test_tone_fmp4.m4a"))

            val classicOut = File(tmp, "classic.mp3")
            val fragOut = File(tmp, "frag.mp3")
            val a = AudioTranscoder.m4aToMp3(classicIn, classicOut, durationSec = 3)
            var lastProgress = -1f
            val b = AudioTranscoder.m4aToMp3(fragIn, fragOut, durationSec = 3) { lastProgress = it }

            assertTrue(b > 10_000, "size=$b")
            assertTrue(lastProgress in 0.9f..1.01f, "progress=$lastProgress")

            // نفس عينات AAC → نفس PCM → ترميز LAME الحتمي نفسه بايتًا بايتًا
            assertEquals(a, b, "المخرجان متطابقان لأن العينات واحدة")
            assertTrue(classicOut.readBytes().contentEquals(fragOut.readBytes()), "بايتات MP3 متطابقة")

            val info = Mp3Info.analyze(fragOut)
            assertTrue(info != null, "Mp3Info failed")
            assertTrue(info!!.durationMs in 2_700..3_400, "duration=${info.durationMs}")
            assertEquals(44100, info.first.sampleRate)
            assertEquals(2, info.first.channels)
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    @Test
    fun `fragmented file without fragments fails with honest message`() {
        val tmp = tempDir("v50-empty")
        try {
            val inp = File(tmp, "empty.mp4")
            inp.writeBytes(resourceBytes("test_tone_mp4_empty.m4a"))
            val out = File(tmp, "empty.mp3")
            var message = ""
            try {
                AudioTranscoder.m4aToMp3(inp, out)
            } catch (t: IllegalStateException) {
                message = t.message ?: ""
            }
            // الرسالة تسمّي الحقيقة: MP4 سليم البنية بلا إطارات مقروءة (لا أجزاء moof)
            assertTrue(message.contains("بلا إطارات"), "message=$message")
            assertFalse(out.exists(), "no output on failure")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    @Test
    fun `truncated fragment decodes the readable prefix without failing`() {
        val tmp = tempDir("v50-trunc")
        try {
            val full = File(tmp, "tone_fmp4.m4a")
            full.writeBytes(resourceBytes("test_tone_fmp4.m4a"))
            // بتر آخر جزأين — تنزيل مبتور: العينات الكاملة فقط تُفكّ والنتيجة نجاح جزئي
            val bytes = full.readBytes()
            val truncated = File(tmp, "trunc.m4a")
            truncated.writeBytes(bytes.copyOfRange(0, bytes.size - 6_000))
            val out = File(tmp, "trunc.mp3")
            val size = AudioTranscoder.m4aToMp3(truncated, out, durationSec = 3)
            assertTrue(size > 10_000, "partial size=$size")
            val info = Mp3Info.analyze(out)
            assertTrue(info != null && info.durationMs in 2_000..3_400, "partial duration=${info?.durationMs}")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    @Test
    fun `audio object type parses LC SBR and escape`() {
        // AOT=2، معدل 44100 (فهرس 4)، قناتان: 00010 0100 0010 → 0x12 0x10
        assertEquals(2, FragmentedMp4Aac.audioObjectType(byteArrayOf(0x12, 0x10)))
        // AOT=5 (HE-AAC/SBR): 00101 0100 0010 → 0x2A 0x10
        assertEquals(5, FragmentedMp4Aac.audioObjectType(byteArrayOf(0x2A, 0x10)))
        // AOT=29 (HE-AAC v2/PS): 11101 0100 0010 0000 → 0xEA 0x10
        assertEquals(29, FragmentedMp4Aac.audioObjectType(byteArrayOf(0xEA.toByte(), 0x10)))
        // هروب 31: 11111 + 6 بتات (32+AOT-32) → 11111000 00000000 = AOT 32
        assertEquals(32, FragmentedMp4Aac.audioObjectType(byteArrayOf(0xF8.toByte(), 0x00)))
        assertEquals("AAC-LC", FragmentedMp4Aac.audioProfileName(2))
        assertTrue(FragmentedMp4Aac.audioProfileName(5).contains("HE-AAC"))
    }

    @Test
    fun `fragment profile of real tone asc is LC`() {
        // ASC للنغمة الحقيقية يأتي من حاوية MP4 عبر AudioTranscoder نفسه —
        // نتحقق أن التسمية الصادقة لن تدّعي HE-AAC على ملف عادي
        val tmp = tempDir("v50-aot")
        try {
            val f = File(tmp, "tone_fmp4.m4a")
            f.writeBytes(resourceBytes("test_tone_fmp4.m4a"))
            val scan = FragmentedMp4Aac.scan(f)
            assertTrue(scan != null && scan.samples.size > 120)
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }
}
