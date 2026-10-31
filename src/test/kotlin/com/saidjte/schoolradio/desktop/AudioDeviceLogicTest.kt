package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.AudioDevices
import javax.sound.sampled.AudioFormat
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
        assertTrue(fs.size >= 12, "candidates=${fs.size}")
        assertTrue(fs.all { it.sampleSizeInBits == 16 && !it.isBigEndian && it.encoding == AudioFormat.Encoding.PCM_SIGNED })
        for (rate in floatArrayOf(48000f, 44100f, 96000f, 22050f, 16000f, 8000f)) {
            assertTrue(fs.any { it.sampleRate == rate && it.channels == 1 }, "mono $rate")
            assertTrue(fs.any { it.sampleRate == rate && it.channels == 2 }, "stereo $rate")
        }
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
