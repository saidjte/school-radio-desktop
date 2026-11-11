package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MixBus
import com.saidjte.schoolradio.desktop.audio.MixMath
import com.saidjte.schoolradio.desktop.audio.WavHeader
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * عقود ناقل مزيج الحصة الجيل الثاني (v0.45.0) — درس رسالة المستخدم
 * «تسجيل الحصة الكاملة يتشوش عند تشغيل الميكروفون والموسيقى معًا. أي
 * تشغيل متوازٍ يشوّش التسجيل»: الجيل الأول كان يجمّع مقاطع المصادر بترتيب
 * الوصول فتتناوب المصادر المتوازية في الملف كأن أحدها يقاطع الآخر،
 * والجيل الثاني يخلط المسارات عينةً مقابل عينة على ساعة الحائط.
 * كل الاختبارات هنا حتمية: التغذية الكاملة ثم الإيقاف يعتمد على التصريف
 * النهائي فلا تدخل ساعة الحائط في النتيجة إلا بهامش متسامح.
 */
class MixBusV45Test {

    /** ستيريو موحّد 44.1k من مستوى ثابت (±amp) — إشارة قياس محسومة. */
    private fun constantStereo(frames: Int, amp: Short): ShortArray =
        ShortArray(frames * 2) { amp }

    /** قراءة عينات ملف WAV 16-bit — يعيد القيم الموقّعة كما هي. */
    private fun readSamples(f: java.io.File): ShortArray {
        val info = assertNotNull(WavHeader.read(f), "wav header")
        val bytes = f.readBytes()
        val dataOff = (bytes.size - info.dataBytes).toInt().coerceAtLeast(44)
        val n = (info.dataBytes / 2).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val lo = bytes[dataOff + i * 2].toInt() and 0xFF
            val hi = bytes[dataOff + i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort()
        }
        return out
    }

    private fun peakOf(samples: ShortArray): Int =
        samples.maxOf { max(it.toInt(), -it.toInt()) }

    @Test
    fun `saturate identity inside knee and bent above it`() {
        // داخل الركبة: مرور تام بلا مساس
        assertEquals(0.0f, MixMath.saturate(0.0f))
        assertEquals(0.4f, MixMath.saturate(0.4f))
        assertEquals(-0.4f, MixMath.saturate(-0.4f))
        assertEquals(MixMath.KNEE, MixMath.saturate(MixMath.KNEE))
        // فوق الركبة: انحناء ناعم إلى سقف 1.0 دون بلوغه
        val s12 = MixMath.saturate(1.2f)
        assertTrue(s12 > MixMath.KNEE && s12 < 1.0f, "bent=$s12")
        val s4 = MixMath.saturate(4.0f)
        assertTrue(s4 > s12 && s4 < 1.0f, "monotone s4=$s4 s12=$s12")
        // تناظر ورتيبة تمامًا على مسار واسع
        var prev = -10f
        var x = -10f
        while (x <= 10f) {
            val s = MixMath.saturate(x)
            assertTrue(abs(s) <= 1.0f, "bound at $x -> $s")
            assertTrue(s >= prev, "monotone at $x")
            assertEquals(MixMath.saturate(-x), -s, "symmetry at $x")
            prev = s
            x += 0.05f
        }
    }

    @Test
    fun `parallel sources are summed not interleaved - duration is max not sum`() {
        val tmp = kotlin.io.path.createTempDirectory("mixv45").toFile()
        val f = java.io.File(tmp, "par.wav")
        try {
            assertTrue(MixBus.start(f), "start")
            // مساران متزامنان كاملان: موسيقى 1 ثانية + مايك 1 ثانية
            // الجيل الأول كان يعطي ~2 ثانية متناوبة — الثاني يعطي ~1 ثانية مجمّعة
            MixBus.feedMusic(constantStereo(44_100, (0.4f * 32767).toInt().toShort()), 88_200, 44_100, 2)
            val micBytes = ByteArray(44_100 * 2) { i ->
                if (i % 2 == 0) (0.4f * 32767).toInt().toByte() else ((0.4f * 32767).toInt() shr 8).toByte()
            }
            MixBus.feedMic(micBytes, 0, micBytes.size, 44_100, 1)
            val dur = MixBus.stop()
            // المدة أقصى المدد (~1.0s) لا مجموعها (~2.0s)
            assertTrue(dur in 850..1350, "duration=$dur must be ~1000 not ~2000")
            val info = assertNotNull(WavHeader.read(f), "header")
            val frames = info.dataBytes / 4
            assertTrue(frames in 37_500..60_000, "frames=$frames ~44100 not ~88200")
            // الذروة مجموع المصرفين (0.4+0.4=0.8) لا ذروة أحدهما (0.4)
            val peak = peakOf(readSamples(f))
            assertTrue(peak in (0.75f * 32767).toInt()..(0.83f * 32767).toInt(), "peak=$peak expect ~26214 (0.8)")
        } finally {
            f.delete(); tmp.delete()
        }
    }

    @Test
    fun `staggered overlap sums in shared window and single source elsewhere`() {
        val tmp = kotlin.io.path.createTempDirectory("mixv45b").toFile()
        val f = java.io.File(tmp, "stag.wav")
        try {
            assertTrue(MixBus.start(f), "start")
            // موسيقى ثانية كاملة، ومايك يدخل في منتصفها (نصف ثانية) —
            // النصف الأول مجموع 0.8 والنصف الثاني موسيقى وحدها 0.4
            MixBus.feedMusic(constantStereo(44_100, (0.4f * 32767).toInt().toShort()), 88_200, 44_100, 2)
            val micBytes = ByteArray(22_050 * 2) { i ->
                if (i % 2 == 0) (0.4f * 32767).toInt().toByte() else ((0.4f * 32767).toInt() shr 8).toByte()
            }
            MixBus.feedMic(micBytes, 0, micBytes.size, 44_100, 1)
            MixBus.stop()
            val s = readSamples(f)
            val peak = peakOf(s)
            assertTrue(peak in (0.75f * 32767).toInt()..(0.83f * 32767).toInt(), "overlap peak=$peak expect ~0.8")
            // يوجد موضع بقيمة الموسيقى وحدها (0.4) — خارج نافذة التقاطع
            val solo = s.any { abs(it.toInt()) in (0.36f * 32767).toInt()..(0.44f * 32767).toInt() }
            assertTrue(solo, "music-only window present at 0.4")
        } finally {
            f.delete(); tmp.delete()
        }
    }

    @Test
    fun `loud parallel sources soft-saturate and never hard-clip`() {
        val tmp = kotlin.io.path.createTempDirectory("mixv45c").toFile()
        val f = java.io.File(tmp, "loud.wav")
        try {
            assertTrue(MixBus.start(f), "start")
            // 0.7+0.7=1.4 فوق المدى — الجيل الثاني يشبع ناعمًا تحت 32767
            MixBus.feedMusic(constantStereo(22_050, (0.7f * 32767).toInt().toShort()), 44_100, 44_100, 2)
            val micBytes = ByteArray(22_050 * 2) { i ->
                if (i % 2 == 0) (0.7f * 32767).toInt().toByte() else ((0.7f * 32767).toInt() shr 8).toByte()
            }
            MixBus.feedMic(micBytes, 0, micBytes.size, 44_100, 1)
            MixBus.stop()
            val peak = peakOf(readSamples(f))
            val expect = (MixMath.saturate(1.4f) * 32767f)
            assertTrue(peak <= expect.toInt() + 2, "no hard clip peak=$peak expect<=$expect")
            assertTrue(peak >= (0.99f * 32767).toInt(), "limiter engaged peak=$peak")
            assertFalse(peak == Short.MAX_VALUE.toInt(), "hard clip square wave forbidden")
        } finally {
            f.delete(); tmp.delete()
        }
    }

    @Test
    fun `single source passes through with no accidental gain change`() {
        val tmp = kotlin.io.path.createTempDirectory("mixv45d").toFile()
        val f = java.io.File(tmp, "solo.wav")
        try {
            assertTrue(MixBus.start(f), "start")
            MixBus.feedMusic(constantStereo(11_025, (0.4f * 32767).toInt().toShort()), 22_050, 44_100, 2)
            MixBus.stop()
            val s = readSamples(f)
            assertTrue(s.isNotEmpty(), "samples written")
            // القيمة الثابتة تخرج كما دخلت — لا خسارة ولا كسب خفي (داخل الركبة)
            val expected = (0.4f * 32767).toInt()
            val peak = peakOf(s)
            assertTrue(abs(peak - expected) <= 2, "passthrough peak=$peak expect=$expected")
        } finally {
            f.delete(); tmp.delete()
        }
    }

    @Test
    fun `legacy contracts hold - double start, double stop, empty and inactive feeds`() {
        val tmp = kotlin.io.path.createTempDirectory("mixv45e").toFile()
        val f = java.io.File(tmp, "legacy.wav")
        try {
            assertFalse(MixBus.stop() > 0L || MixBus.recording.value, "inactive stop safe")
            // تغذية قبل البدء تُتجاهل بلا استثناء
            MixBus.feedMusic(constantStereo(100, 1000), 200, 44_100, 2)
            MixBus.feedMic(ByteArray(100), 0, 100, 44_100, 1)
            assertTrue(MixBus.start(f), "start")
            assertFalse(MixBus.start(java.io.File(tmp, "x.wav")), "double start rejected")
            MixBus.feedMic(ByteArray(0), 0, 0, 44_100, 1) // فارغ لا يكسر
            val dur = MixBus.stop()
            assertEquals(0L, MixBus.stop(), "double stop safe")
            assertFalse(MixBus.recording.value, "inactive after stop")
            // تغذية بعد الإيقاع تُتجاهل
            MixBus.feedMusic(constantStereo(100, 1000), 200, 44_100, 2)
            assertTrue(dur >= 0L, "dur=$dur")
        } finally {
            f.delete(); tmp.delete()
        }
    }

    @Test
    fun `resample and channel fold still applied per lane`() {
        val tmp = kotlin.io.path.createTempDirectory("mixv45f").toFile()
        val f = java.io.File(tmp, "conv.wav")
        try {
            assertTrue(MixBus.start(f), "start")
            // موسيقى 48kHz ستيريو تُعاد تشكيلها إلى 44.1k — عقد الصيغة الموحدة باقٍ
            MixBus.feedMusic(constantStereo(480, (0.4f * 32767).toInt().toShort()), 960, 48_000, 2)
            MixBus.stop()
            val info = assertNotNull(WavHeader.read(f), "header")
            assertEquals(44_100, info.sampleRate, "rate")
            assertEquals(2, info.channels, "channels")
            assertTrue(info.dataBytes > 0L, "dataBytes=${info.dataBytes}")
        } finally {
            f.delete(); tmp.delete()
        }
    }
}
