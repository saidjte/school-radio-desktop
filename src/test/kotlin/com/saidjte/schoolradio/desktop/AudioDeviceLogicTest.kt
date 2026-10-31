package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.AudioDevices
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * منطق طبقة أجهزة الصوت — الإدخال والإخراج:
 * صيغ الالتقاط المرشحة، التعداد الآمن (حتى على آلة بلا أجهزة صوت)،
 * فتح المخرج الافتراضي، والرسائل العربية التي تحل محل الرسائل الإنجليزية الخام.
 */
class AudioDeviceLogicTest {

    @Test
    fun `candidate capture formats cover key rates both channels pcm16`() {
        val fs = AudioDevices.CANDIDATE_FORMATS
        assertTrue(fs.size >= 18, "candidates=${fs.size}")
        assertTrue(fs.all { it.sampleSizeInBits == 16 && !it.isBigEndian && it.encoding == AudioFormat.Encoding.PCM_SIGNED })
        for (rate in floatArrayOf(48000f, 44100f, 96000f, 22050f, 16000f, 11025f, 24000f, 32000f, 8000f)) {
            assertTrue(fs.any { it.sampleRate == rate && it.channels == 1 }, "mono $rate")
            assertTrue(fs.any { it.sampleRate == rate && it.channels == 2 }, "stereo $rate")
        }
    }

    @Test
    fun `expandDiscoveredFormats widens placeholders keeps concrete and filters incompatible`() {
        val unspecified = AudioSystem.NOT_SPECIFIED.toFloat()
        // صيغة معلنة بلا معدل ولا قنوات (نمط ويندوز الشائع) ← تتوسع إلى الشبكة كاملة
        val expanded = AudioDevices.expandDiscoveredFormats(listOf(AudioFormat(unspecified, 16, -1, true, false)))
        assertEquals(AudioDevices.CANDIDATE_FORMATS.size, expanded.size)

        // جهاز معلن بصيغة ملموسة وحيدة خارج الشبكة (24000 هرتز) ← تُحفظ كما هي
        val concrete = AudioDevices.expandDiscoveredFormats(listOf(AudioFormat(24000f, 16, 1, true, false)))
        assertEquals(1, concrete.size)
        assertEquals(24000f, concrete.first().sampleRate)

        // الفلترة: 8 بت أو كبير النهاية أو غير PCM موقّع تُسقط
        val filtered = AudioDevices.expandDiscoveredFormats(
            listOf(
                AudioFormat(44100f, 8, 1, true, false),
                AudioFormat(44100f, 16, 1, true, true),
                AudioFormat(44100f, 16, 1, false, false)
            )
        )
        assertTrue(filtered.isEmpty())

        // التكرار بالقيمة يُزال رغم عدم تعريف equals في AudioFormat
        val deduped = AudioDevices.dedupeFormats(
            listOf(AudioFormat(48000f, 16, 1, true, false), AudioFormat(48000f, 16, 1, true, false))
        )
        assertEquals(1, deduped.size)
    }

    @Test
    fun `rankInputFormats prefers standard rates and mono first`() {
        val ranked = AudioDevices.rankInputFormats(
            listOf(
                AudioFormat(8000f, 16, 2, true, false),
                AudioFormat(48000f, 16, 2, true, false),
                AudioFormat(44100f, 16, 1, true, false),
                AudioFormat(48000f, 16, 1, true, false)
            )
        )
        assertEquals(
            listOf(48000f, 48000f, 44100f, 8000f),
            ranked.map { it.sampleRate }
        )
        assertEquals(listOf(1, 2, 1, 2), ranked.map { it.channels })
    }

    @Test
    fun `device lists never throw and always contain default entries`() {
        val mics = AudioDevices.listMics()
        assertTrue(mics.isNotEmpty())
        assertTrue(mics.all { it.name.isNotBlank() })

        val outs = AudioDevices.listOutputs()
        assertTrue(outs.isNotEmpty())
        // أول بند في المخرجات دائمًا هو الافتراضي للنظام
        assertEquals(null, outs.first().mixerInfo)
        assertEquals(AudioDevices.DEFAULT_OUTPUT_NAME, outs.first().name)
    }

    @Test
    fun `pickFormat always returns a valid pcm16 format`() {
        val f = AudioDevices.pickFormat(null)
        assertTrue(f.sampleSizeInBits == 16 && !f.isBigEndian)
        assertTrue(f.sampleRate > 0 && f.channels in 1..2)
    }

    @Test
    fun `input failure maps to clear arabic message`() {
        val raw = "No line matching interface TargetDataLine supporting format " +
                "PCM_SIGNED 44100.0 Hz, 16 bit, mono, 2 bytes/frame, little-endian is supported."
        val msg = AudioDevices.describeInputFailure(RuntimeException(raw))
        assertTrue(msg.contains("ميكروفون"), msg)
        assertTrue(msg.contains("تحديث"), msg)
    }

    @Test
    fun `jdk unsupported raw message never leaks to the user`() {
        // الرسالة الخام نفسها في لقطة المستخدم (v0.6.0 على ويندوز)
        val raw = "unsupported: interface TargetDataLine supporting format " +
                "PCM_SIGNED 8000.0 Hz, 16 bit, stereo, 4 bytes/frame, little-endian"
        val msg = AudioDevices.describeInputFailure(RuntimeException(raw))
        assertTrue(msg.contains("الميكروفون"), msg)
        assertTrue(msg.contains("الخصوصية"), msg)
        assertTrue(!msg.contains("PCM_SIGNED"), "تسرّب خام: $msg")
        assertTrue(!msg.contains("unsupported:"), "تسرّب خام: $msg")
    }

    @Test
    fun `output failure maps to clear arabic message`() {
        val raw = "No line matching interface SourceDataLine supporting format " +
                "PCM_SIGNED 44100.0 Hz, 16 bit, stereo, 4 bytes/frame, little-endian is supported."
        val msg = AudioDevices.describeOutputFailure(RuntimeException(raw))
        assertTrue(msg.contains("سماعة"), msg)
    }

    @Test
    fun `openSourceLine falls back to default output safely`() {
        // بلا جهاز محدد: يجب أن يعمل المسار الافتراضي أو يرمي رسالة عربية (لا رسالة خام)
        try {
            val line = AudioDevices.openSourceLine(null, AudioFormat(44100f, 16, 2, true, false))
            assertTrue(line.format.sampleRate == 44100f)
            try { line.close() } catch (_: Exception) { }
        } catch (e: java.io.IOException) {
            assertTrue(e.message!!.contains("سماعة"), e.message)
        }
    }
}
