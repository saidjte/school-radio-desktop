package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.PcmMixer
import com.saidjte.schoolradio.desktop.audio.PcmOutLine
import com.saidjte.schoolradio.desktop.audio.TwinPcmOut
import com.saidjte.schoolradio.desktop.ui.TeleprompterSpeed
import javax.sound.sampled.AudioFormat
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v0.19.0: المخرجان والمدخلان وشاشة القراءة —
 * خلاط المدخل الثاني (طيّ القنوات + إعادة التشكيل + الجمع المحبوس)،
 * خط الإخراج المزدوج (نفس الكتلة في الجهازين)، وسرعات التلقيم الحي.
 * كلها نقية بلا أجهزة صوت — الجهاز لا يكشف انزياح نصف عينة ولا قصًّا صامتًا.
 */
class DualDevicesAndReadingTest {

    // ================= خلاط المدخل الثاني: PcmMixer =================

    @Test
    fun `foldToMono averages stereo channels`() {
        val src = shortArrayOf(200, 100, -100, 100, 300, -300)
        val dst = FloatArray(8)
        val frames = PcmMixer.foldToMono(src, src.size, 2, dst)
        assertEquals(3, frames)
        assertEquals(150f / 32768f, dst[0])
        assertEquals(0f, dst[1])
        assertEquals(0f, dst[2])
    }

    @Test
    fun `foldToMono keeps mono passthrough`() {
        val src = shortArrayOf(1000, -500, 250)
        val dst = FloatArray(4)
        val frames = PcmMixer.foldToMono(src, src.size, 1, dst)
        assertEquals(3, frames)
        assertEquals(1000f / 32768f, dst[0])
        assertEquals(-500f / 32768f, dst[1])
    }

    @Test
    fun `resample same rate is identity`() {
        val src = floatArrayOf(0.1f, -0.2f, 0.3f, -0.4f)
        val dst = FloatArray(8)
        val n = PcmMixer.resample(src, src.size, 48000, 48000, dst)
        assertEquals(4, n)
        for (i in src.indices) assertEquals(src[i], dst[i])
    }

    @Test
    fun `resample downsample halves length and keeps first sample`() {
        val src = FloatArray(480) { i -> (i % 100) / 1000f }
        val dst = FloatArray(1024)
        val n = PcmMixer.resample(src, src.size, 48000, 24000, dst)
        assertEquals(240, n)
        assertEquals(src[0], dst[0])
    }

    @Test
    fun `resample upsample doubles length within tolerance`() {
        val src = FloatArray(80) { i -> (i % 40) / 100f }
        val dst = FloatArray(1024)
        val n = PcmMixer.resample(src, src.size, 8000, 16000, dst)
        assertTrue(abs(n - 160) <= PcmMixer.lengthTolerance(160), "n=$n")
        assertEquals(src[0], dst[0])
    }

    @Test
    fun `addClamped sums into both channels and clamps at limits`() {
        // جمع عادي: قاعدة 100 + إضافة 0.5 = 100 + 16384 ≈ 16484 في القناتين
        val base = shortArrayOf(100, 100, 100, 100)
        val mono = floatArrayOf(0.5f, 0.5f)
        val frames = PcmMixer.addClamped(base, base.size, 2, mono, 2)
        assertEquals(2, frames)
        assertEquals(16484.toShort(), base[0])
        assertEquals(16484.toShort(), base[1])
        // قصّ الأعلى: لا لفّ أعداد صامتة بل حدّ صريح
        val hot = shortArrayOf(32000, 32000, 32000, 32000)
        val loud = floatArrayOf(0.5f, 0.5f)
        PcmMixer.addClamped(hot, hot.size, 2, loud, 2)
        assertEquals(Short.MAX_VALUE, hot[0])
        assertEquals(Short.MAX_VALUE, hot[1])
        // قصّ الأدنى
        val cold = shortArrayOf(-32000, -32000)
        val low = floatArrayOf(-0.5f)
        PcmMixer.addClamped(cold, cold.size, 1, low, 1)
        assertEquals(Short.MIN_VALUE, cold[0])
    }

    @Test
    fun `addSameRate stereo into stereo preserves channels`() {
        val base = shortArrayOf(100, 200, 100, 200)
        val add = shortArrayOf(10, 20, 30, 40)
        val frames = PcmMixer.addSameRate(base, base.size, 2, add, add.size, 2)
        assertEquals(2, frames)
        assertEquals(110.toShort(), base[0])
        assertEquals(220.toShort(), base[1])
        assertEquals(130.toShort(), base[2])
        assertEquals(240.toShort(), base[3])
    }

    @Test
    fun `addSameRate mono into stereo duplicates`() {
        val base = shortArrayOf(100, 100)
        val add = shortArrayOf(50)
        PcmMixer.addSameRate(base, base.size, 2, add, add.size, 1)
        assertEquals(150.toShort(), base[0])
        assertEquals(150.toShort(), base[1])
    }

    @Test
    fun `needsConversion only for differing rates`() {
        assertEquals(false, PcmMixer.needsConversion(48000, 48000))
        assertEquals(true, PcmMixer.needsConversion(48000, 44100))
        assertEquals(true, PcmMixer.needsConversion(44100, 48000))
    }

    // ================= الإخراج المزدوج: TwinPcmOut =================

    /** خط إخراج وهمي يسجّل ما يصله — بديل SourceDataLine بلا أجهزة. */
    private class FakeOut : PcmOutLine {
        var written = mutableListOf<ByteArray>()
        var opened = 0
        var started = 0
        var stopped = 0
        var flushed = 0
        var closed = 0
        var lastVolume = 0f
        var failWrite = false

        override fun open(format: AudioFormat, bufferSize: Int) { opened++ }
        override fun start() { started++ }
        override fun stop() { stopped++ }
        override fun flush() { flushed++ }
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (failWrite) throw java.io.IOException("fake write failure")
            written.add(b.copyOfRange(off, off + len))
        }
        override fun setVolume(v: Float) { lastVolume = v }
        override fun close() { closed++ }
    }

    @Test
    fun `twin out writes same bytes to both devices`() {
        val a = FakeOut()
        val b = FakeOut()
        val twin = TwinPcmOut(a, b)
        val fmt = AudioFormat(44100f, 16, 2, true, false)
        twin.open(fmt, 4096)
        twin.start()
        val data = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        twin.write(data, 0, data.size)
        twin.setVolume(0.7f)
        twin.flush()
        twin.close()
        assertEquals(1, a.opened); assertEquals(1, b.opened)
        assertEquals(1, a.started); assertEquals(1, b.started)
        assertEquals(1, a.written.size); assertEquals(1, b.written.size)
        assertTrue(a.written[0].contentEquals(data))
        assertTrue(b.written[0].contentEquals(data))
        assertEquals(0.7f, a.lastVolume)
        assertEquals(0.7f, b.lastVolume)
        assertEquals(1, a.flushed); assertEquals(1, b.flushed)
        assertEquals(1, a.closed); assertEquals(1, b.closed)
    }

    @Test
    fun `twin out close is safe even when one side already broken`() {
        val a = FakeOut()
        val b = FakeOut()
        val twin = TwinPcmOut(a, b)
        b.failWrite = true
        twin.close()
        assertEquals(1, a.closed)
        assertEquals(1, b.closed)
    }

    // ================= شاشة القراءة: سرعات التلقيم =================

    @Test
    fun `teleprompter speeds are ordered and sane for onair reading`() {
        val values = TeleprompterSpeed.entries.map { it.pxPerSecond }
        assertEquals(values.sorted(), values, "speeds must ascend")
        assertTrue(values.first() >= 20f, "slowest must still move: ${values.first()}")
        assertTrue(values.last() <= 130f, "fastest must stay readable: ${values.last()}")
        assertTrue(TeleprompterSpeed.entries.all { it.label.isNotBlank() })
    }
}
