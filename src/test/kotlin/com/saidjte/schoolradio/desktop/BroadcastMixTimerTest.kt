package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MixBus
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.PcmMixer
import com.saidjte.schoolradio.desktop.audio.TrackReader
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.CrossfadeLogic
import com.saidjte.schoolradio.desktop.data.SegmentTimerLogic
import com.saidjte.schoolradio.desktop.data.SettingsStore
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * عقد البث الاحترافي v0.31.0: الكروس فيد بين المقاطع + تسجيل مزيج الحصة
 * (مايك + موسيقى في ملف واحد) + مؤقت الفاصل الآلي — كلها بلا عتاد صوت:
 * المنطق النقي وحدةً، والمحرك بخط حقن القرّاء، والناقل بملف حقيقي على قرص.
 */
class BroadcastMixTimerTest {

    // ================= الكروس فيد: المنطق النقي =================

    @Test
    fun `crossfade window clamping keeps disabled zero and bounds the rest`() {
        assertEquals(0L, CrossfadeLogic.clampMs(0L), "disabled preserved")
        assertEquals(0L, CrossfadeLogic.clampMs(-5L), "negative disabled")
        assertEquals(CrossfadeLogic.MIN_MS, CrossfadeLogic.clampMs(1L))
        assertEquals(CrossfadeLogic.MAX_MS, CrossfadeLogic.clampMs(60_000L))
        assertEquals(3_000L, CrossfadeLogic.clampMs(3_000L))
    }

    @Test
    fun `crossfade trigger fires only inside the window with a next`() {
        assertFalse(CrossfadeLogic.shouldTrigger(4_000L, 3_000L), "beyond window")
        assertTrue(CrossfadeLogic.shouldTrigger(3_000L, 3_000L), "window edge")
        assertTrue(CrossfadeLogic.shouldTrigger(1L, 3_000L), "last ms")
        assertFalse(CrossfadeLogic.shouldTrigger(0L, 3_000L), "already ended")
        assertFalse(CrossfadeLogic.shouldTrigger(2_000L, 0L), "disabled")
    }

    @Test
    fun `fade ramps are linear and complementary`() {
        assertEquals(1f, CrossfadeLogic.fadeOutGain(0L, 3_000L))
        assertEquals(0.5f, CrossfadeLogic.fadeOutGain(1_500L, 3_000L), 0.01f)
        assertEquals(0f, CrossfadeLogic.fadeOutGain(3_000L, 3_000L))
        assertEquals(0f, CrossfadeLogic.fadeInGain(0L, 3_000L))
        assertEquals(0.5f, CrossfadeLogic.fadeInGain(1_500L, 3_000L), 0.01f)
        assertEquals(1f, CrossfadeLogic.fadeInGain(3_000L, 3_000L))
        // مجموع الكسبين ≈ 1 في كل اللحظات — ثبات الحجم للمستمع
        for (t in 0..3000 step 250) {
            val sum = CrossfadeLogic.fadeOutGain(t.toLong(), 3_000L) +
                    CrossfadeLogic.fadeInGain(t.toLong(), 3_000L)
            assertEquals(1f, sum, 0.02f, "sum at t=$t")
        }
    }

    // ================= الكروس فيد: عقد المحرك =================

    /** قارئ مزيف زمني: يتقدم 50ms لكل كتلة وينتهي وحده عند المدة المحددة. */
    private class TimelineReader(private val totalMs: Long) : TrackReader {
        @Volatile var current = 0L
        @Volatile var closed = false
        override val durationMs = totalMs
        override val currentMs: Long get() = current
        override fun seek(ms: Long) { current = ms.coerceIn(0L, totalMs) }
        override fun pause() { }
        override fun resume() { }
        override fun applyVolume(volume: Float) { }
        override fun readChunk(): Boolean {
            current += 50L
            Thread.sleep(2)
            return current < totalMs
        }
        override fun close() { closed = true }
    }

    private fun awaitTrue(message: String, timeoutMs: Long = 6_000L, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("انقضت المهلة: $message")
    }

    @Test
    fun `auto crossfade starts near the end and hands over cleanly`(@TempDir tmp: File) {
        val a = TimelineReader(1_500L)   // ينفد سريعًا فيجيب «التالي»
        val b = TimelineReader(60_000L)
        val queue = ArrayDeque(listOf(a, b))
        val eng = MusicPlayerEngine()
        eng.trackReaderOverride = { queue.removeFirst() }
        eng.crossfadeMs = 400L
        var asked = 0
        var committed: String? = null
        eng.onCrossfadeNext = { asked++; File(tmp, "b.wav") }
        eng.onCrossfadeCommitted = { committed = it.path }
        try {
            eng.play(File(tmp, "a.wav"))
            awaitTrue("xfading began") { eng.xfading.value }
            assertEquals(1, asked, "next asked exactly once")
            assertEquals(File(tmp, "b.wav").path, committed, "committed to next file")
            awaitTrue("xfading completed") { !eng.xfading.value }
            assertTrue(a.closed, "outgoing closed after fade")
            assertFalse(b.closed, "incoming alive")
            assertTrue(eng.hasTrack.value && eng.isPlaying.value, "playback continues")
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `manual switch crossfades while playing and hard-switches when idle`(@TempDir tmp: File) {
        val a = TimelineReader(60_000L)
        val b = TimelineReader(60_000L)
        val queue = ArrayDeque(listOf(a, b))
        val eng = MusicPlayerEngine()
        eng.trackReaderOverride = { queue.removeFirst() }
        eng.crossfadeMs = 300L
        try {
            eng.play(File(tmp, "a.wav"))
            awaitTrue("track a loaded") { eng.hasTrack.value && eng.positionMs.value > 0L }
            // تبديل أثناء التشغيل → كروس فيد
            eng.play(File(tmp, "b.wav"), crossfade = true)
            awaitTrue("xfading began on switch") { eng.xfading.value }
            awaitTrue("xfading completed") { !eng.xfading.value }
            assertTrue(a.closed, "previous closed")
            assertEquals(File(tmp, "b.wav"), eng.currentFile.value, "now playing b")
            eng.stop()
            eng.awaitIdle()
            // تبديل والمحرك متوقف → قطع صلب بلا كروس فيد
            val c = TimelineReader(60_000L)
            eng.trackReaderOverride = { c }
            eng.play(File(tmp, "c.wav"), crossfade = true)
            awaitTrue("track c loaded") { eng.currentFile.value == File(tmp, "c.wav") }
            assertFalse(eng.xfading.value, "no crossfade when idle")
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `pause aborts crossfade immediately`(@TempDir tmp: File) {
        val a = TimelineReader(60_000L)
        val b = TimelineReader(60_000L)
        val queue = ArrayDeque(listOf(a, b))
        val eng = MusicPlayerEngine()
        eng.trackReaderOverride = { queue.removeFirst() }
        eng.crossfadeMs = 5_000L
        try {
            eng.play(File(tmp, "a.wav"))
            awaitTrue("a playing") { eng.positionMs.value > 0L }
            eng.play(File(tmp, "b.wav"), crossfade = true)
            awaitTrue("xfading began") { eng.xfading.value }
            eng.pause()
            awaitTrue("xfade aborted on pause") { !eng.xfading.value }
            assertTrue(a.closed, "outgoing closed on pause")
            assertTrue(eng.isPaused.value, "incoming paused")
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    // ================= تسجيل المزيج: الناقل والتحويل =================

    @Test
    fun `mix bus writes one stereo wav from mixed formats`(@TempDir tmp: File) {
        val f = File(tmp, "mix.wav")
        assertTrue(MixBus.start(f), "start")
        assertTrue(MixBus.recording.value)
        assertFalse(MixBus.start(File(tmp, "other.wav")), "second start rejected")
        // موسيقى ستيريو 48kHz: 480 إطارًا (960 عيّنة)
        val music = ShortArray(960) { i -> (((i % 60) - 30) * 800).toInt().toShort() }
        MixBus.feedMusic(music, music.size, 48_000, 2)
        // مايك أحادي 44.1kHz عبر بايتات (2205 عيّنة = 50ms)
        val micBytes = ByteArray(4410)
        for (i in 0 until 2205) {
            val v = if (i % 2 == 0) 4000 else -4000
            micBytes[2 * i] = (v and 0xFF).toByte()
            micBytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        MixBus.feedMic(micBytes, 0, micBytes.size, 44_100, 1)
        // تغذية فارغة: لا تكسر ولا تضيف
        MixBus.feedMic(ByteArray(0), 0, 0, 44_100, 1)
        MixBus.feedJingle(ByteArray(0), 0, 0, 44_100, 2)
        val dur = MixBus.stop()
        assertTrue(dur > 0L, "duration=$dur")
        assertFalse(MixBus.recording.value)
        assertEquals(0L, MixBus.stop(), "double stop safe")
        val info = WavHeader.read(f) ?: throw AssertionError("wav header missing")
        assertEquals(44_100, info.sampleRate)
        assertEquals(2, info.channels)
        assertTrue(info.dataBytes > 0L)
        assertEquals(44L + info.dataBytes, f.length(), "file size = header + data")
    }

    @Test
    fun `mix bus ignores feeds while inactive`() {
        val music = ShortArray(100) { 100 }
        MixBus.feedMusic(music, music.size, 44_100, 2) // بلا انفجار وبلا أثر
        assertEquals(0L, MixBus.stop(), "stop without start is safe")
    }

    @Test
    fun `toStereo duplicates mono and preserves stereo`() {
        val mono = shortArrayOf(100, -200, 300)
        val st = PcmMixer.toStereo(mono, 3, 1)
        assertEquals(6, st.size)
        assertEquals(100, st[0]); assertEquals(100, st[1])
        assertEquals(-200, st[2]); assertEquals(-200, st[3])
        val stereo = shortArrayOf(5, 9, 6, 10)
        val kept = PcmMixer.toStereo(stereo, 4, 2)
        assertEquals(4, kept.size)
        assertEquals(5, kept[0]); assertEquals(9, kept[1])
    }

    @Test
    fun `stereo resampling changes length by rate ratio and keeps channels`() {
        // 480 إطارًا من 48k إلى 44.1k → 441 إطارًا (882 عيّنة)
        val src = ShortArray(960) { i -> ((i % 100) * 100).toShort() }
        val out = PcmMixer.resampleStereo(src, 480, 48_000, 44_100)
        assertEquals(882, out.size, "L ratio frames")
        // نفس المعدل: نسخة مطابقة
        val same = PcmMixer.resampleStereo(src, 480, 44_100, 44_100)
        assertEquals(src.size, same.size)
    }

    // ================= مؤقت الفاصل الآلي =================

    @Test
    fun `segment timer decisions follow the threshold math`() {
        val calm = SegmentTimerLogic.decide(10_000L, 60L, 30L)
        assertFalse(calm.fire); assertFalse(calm.warn)
        assertEquals(50_000L, calm.remainingMs)

        val warn = SegmentTimerLogic.decide(35_000L, 60L, 30L)
        assertFalse(warn.fire); assertTrue(warn.warn)
        assertEquals(25_000L, warn.remainingMs)

        val fire = SegmentTimerLogic.decide(60_000L, 60L, 30L)
        assertTrue(fire.fire); assertFalse(fire.warn)

        val overdue = SegmentTimerLogic.decide(75_000L, 60L, 30L)
        assertTrue(overdue.fire)
        assertTrue(overdue.remainingMs < 0L, "negative remaining = exceeded")

        val unplanned = SegmentTimerLogic.decide(999_999L, 0L, 30L)
        assertFalse(unplanned.fire); assertFalse(unplanned.warn)
        assertEquals(0L, unplanned.remainingMs)
    }

    @Test
    fun `segment timer warn seconds clamp and labels`() {
        assertEquals(5, SegmentTimerLogic.clampWarnSeconds(0))
        assertEquals(120, SegmentTimerLogic.clampWarnSeconds(999))
        assertEquals(45, SegmentTimerLogic.clampWarnSeconds(45))
        assertTrue(
            SegmentTimerLogic.remainingLabel(SegmentTimerLogic.decide(70_000L, 60L, 30L)).startsWith("تجاوز")
        )
        val lbl = SegmentTimerLogic.remainingLabel(SegmentTimerLogic.decide(0L, 125L, 30L))
        assertTrue(lbl.startsWith("02:"), "label=$lbl")
    }

    // ================= الإعدادات: حفظ واسترجاع دائري =================

    @Test
    fun `settings store round-trips crossfade and timer with sanitizing`(@TempDir tmp: File) {
        val file = File(tmp, "settings.json")
        val st = SettingsStore(file)
        // الافتراضيات المهنية
        assertTrue(st.crossfade().enabled ?: false, "crossfade default on")
        assertEquals(3_000L, st.crossfade().ms)
        assertTrue(st.segmentTimer().autoAdvance ?: false)
        assertTrue(st.segmentTimer().sting ?: false)
        assertEquals(30, st.segmentTimer().warnSeconds)
        // حفظ واسترجاع
        st.setCrossfade(enabled = false, ms = 20L) // نافذة فاسدة تُحبس
        st.setSegmentTimer(autoAdvance = false, sting = true, warnSeconds = 999)
        val st2 = SettingsStore(file)
        assertFalse(st2.crossfade().enabled ?: true)
        assertEquals(CrossfadeLogic.MIN_MS, st2.crossfade().ms, "ms clamped on save+load")
        assertFalse(st2.segmentTimer().autoAdvance ?: true)
        assertTrue(st2.segmentTimer().sting ?: false)
        assertEquals(120, st2.segmentTimer().warnSeconds, "warn clamped")
    }
}
