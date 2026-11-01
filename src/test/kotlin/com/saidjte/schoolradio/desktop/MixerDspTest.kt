package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.DspMeasure
import com.saidjte.schoolradio.desktop.audio.MusicEq
import com.saidjte.schoolradio.desktop.audio.Pcm16
import com.saidjte.schoolradio.desktop.audio.VoiceProcessor
import com.saidjte.schoolradio.desktop.data.MixerJson
import com.saidjte.schoolradio.desktop.data.MixerLogic
import com.saidjte.schoolradio.desktop.data.MixerState
import com.saidjte.schoolradio.desktop.data.MusicEqJson
import com.saidjte.schoolradio.desktop.data.MusicEqState
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.VoiceFxJson
import com.saidjte.schoolradio.desktop.data.VoiceFxState
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * اختبارات وحدة المزج الإذاعية: المرشحات والمعالج والمعادل على إشارات فعلية،
 * وأنماط الإذاعة الجاهزة، وحسم حالات JSON الناقصة أو المشوهة.
 * كلها بلا أي عتاد — DSP نقي قابل للتكرار حرفيًا.
 */
class MixerDspTest {

    private val fs = 44100

    private fun sineShorts(freq: Double, amp: Double, samples: Int): ShortArray =
        ShortArray(samples) { (amp * 32767 * sin(2.0 * PI * freq * it / fs)).toInt().toShort() }

    // ================= المرشحات =================

    @Test
    fun `high pass cuts rumble and passes voice`() {
        val low = DspMeasure.chainDb(40.0, fs) { it.highPass(fs.toDouble(), 85.0) }
        val high = DspMeasure.chainDb(400.0, fs) { it.highPass(fs.toDouble(), 85.0) }
        assertTrue(low < -12.0, "40هرتز يجب أن يُقص بقوة: $low")
        assertTrue(high in -1.5..1.5, "400هرتز يجب أن يمر: $high")
    }

    @Test
    fun `peaking boosts center frequency`() {
        val gain = DspMeasure.chainDb(3200.0, fs) { it.peaking(fs.toDouble(), 3200.0, 6.0, 1.0) }
        assertTrue(gain in 4.5..7.5, "قمة +6 عند 3.2ك: $gain")
        val elsewhere = DspMeasure.chainDb(300.0, fs) { it.peaking(fs.toDouble(), 3200.0, 6.0, 1.0) }
        assertTrue(elsewhere in -1.0..1.0, "خارج الشريحة شبه بلا أثر: $elsewhere")
    }

    @Test
    fun `shelves move only their side of the spectrum`() {
        val lowBoost = DspMeasure.chainDb(40.0, fs) { it.lowShelf(fs.toDouble(), 80.0, 6.0) }
        val midUntouched = DspMeasure.chainDb(1000.0, fs) { it.lowShelf(fs.toDouble(), 80.0, 6.0) }
        val highBoost = DspMeasure.chainDb(14000.0, fs) { it.highShelf(fs.toDouble(), 10000.0, 6.0) }
        assertTrue(lowBoost > 4.5, "رف منخفض: $lowBoost")
        assertTrue(midUntouched in -1.0..1.0, "وسط محفوظ: $midUntouched")
        assertTrue(highBoost > 4.0, "رف عالٍ: $highBoost")
    }

    @Test
    fun `pcm16 roundtrip is bit exact`() {
        val shorts = ShortArray(1000) { ((it % 2000) - 1000).toShort() }
        val bytes = ByteArray(shorts.size * 2)
        Pcm16.shortsToBytes(shorts, shorts.size, bytes)
        val back = ShortArray(shorts.size)
        Pcm16.bytesToShorts(bytes, bytes.size, back)
        assertTrue(shorts.contentEquals(back), "دورة البايتات تحفظ العينات حرفيًا")
    }

    // ================= معالج صوت المذيع =================

    @Test
    fun `silence stays silence through full chain`() {
        val vp = VoiceProcessor()
        vp.configure(fs, 1)
        vp.setParams(VoiceProcessor.Params(gateOn = true, compOn = true, bassDb = 3f, presenceDb = 3f))
        val silence = ShortArray(2048)
        vp.process(silence, silence.size, 1.0f)
        assertTrue(silence.all { it == 0.toShort() }, "الصمت لا يولّد ضجيجًا")
    }

    @Test
    fun `limiter never exceeds ceiling`() {
        val vp = VoiceProcessor()
        vp.configure(fs, 1)
        vp.setParams(VoiceProcessor.Params(hpfOn = false))
        val loud = sineShorts(500.0, 0.95, 2048)
        vp.process(loud, loud.size, 1.0f)
        val maxAmp = loud.maxOf { abs(it.toInt()) }
        assertTrue(maxAmp <= (0.935 * 32767 + 2).toInt(), "السقف -0.6dB: $maxAmp")
    }

    @Test
    fun `disabled processor is bit-exact identity`() {
        val vp = VoiceProcessor()
        vp.configure(fs, 1)
        vp.setParams(VoiceProcessor.Params(enabled = false))
        val x = sineShorts(440.0, 0.3, 2048)
        val original = x.copyOf()
        vp.process(x, x.size, 1.0f)
        assertTrue(x.contentEquals(original), "المعالج المعطل = هوية تامة")
    }

    @Test
    fun `gate closes on signal below threshold`() {
        val vp = VoiceProcessor()
        vp.configure(fs, 1)
        vp.setParams(VoiceProcessor.Params(gateOn = true, gateThresholdDb = -45f, hpfOn = false, compOn = false))
        val faint = sineShorts(440.0, 0.002, 2048)
        repeat(30) { vp.process(faint, faint.size, 1.0f) }
        assertTrue(vp.gateClosed, "البوابة تُغلق تحت العتبة")
        assertTrue(faint.maxOf { abs(it.toInt()) } < 0.05 * 0.002 * 32767, "الإشارة الخافتة تُقفل")
    }

    @Test
    fun `gate stays open on signal above threshold`() {
        val vp = VoiceProcessor()
        vp.configure(fs, 1)
        vp.setParams(VoiceProcessor.Params(gateOn = true, gateThresholdDb = -45f, hpfOn = false, compOn = false))
        val normal = sineShorts(440.0, 0.2, 2048)
        val originalMax = normal.maxOf { abs(it.toInt()) }
        repeat(10) { vp.process(normal, normal.size, 1.0f) }
        assertFalse(vp.gateClosed, "البوابة تبقى مفتوحة فوق العتبة")
        val maxAfter = normal.maxOf { abs(it.toInt()) }
        assertTrue(maxAfter > originalMax * 0.9, "الإشارة الطبيعية تمر: $maxAfter مقابل $originalMax")
    }

    @Test
    fun `compressor reduces level above threshold`() {
        val vp = VoiceProcessor()
        vp.configure(fs, 1)
        vp.setParams(
            VoiceProcessor.Params(
                compOn = true, compThresholdDb = -20f, compRatio = 3f, compMakeupDb = 0f,
                hpfOn = false, gateOn = false
            )
        )
        // بث حي حقيقي: كتلة جديدة كل مرة (كما يفعل المحرك) —
        // إعادة معالجة الكتلة نفسها 40 مرة تصنع تلاشيًا اصطناعيًا لا علاقة له بالضاغط.
        val blocks = 60
        val n = 2048
        var inRms = 0.0
        var outRms = 0.0
        for (b in 0 until blocks) {
            val block = ShortArray(n) { j ->
                (0.3 * 32767 * sin(2.0 * PI * 440.0 * (b * n + j) / fs)).toInt().toShort()
            }
            if (b == blocks - 1) inRms = Pcm16.rms(block, n)
            vp.process(block, n, 1.0f)
            if (b == blocks - 1) outRms = Pcm16.rms(block, n)
        }
        val reductionDb = Pcm16.ampToDb(outRms / inRms)
        // مكتشف 10ms = شبه-RMS: env ≈ 0.637×الذرة → الخفض المستقر ≈ -3.75dB
        assertTrue(reductionDb in -6.0..-1.5, "خفض مستقر متوقع ≈ -3.75dB فعلي: $reductionDb")
    }

    // ================= معادل الصوت =================

    @Test
    fun `eq with flat bands is bit-exact identity`() {
        val eq = MusicEq()
        eq.configure(fs, 1)
        eq.setEnabled(true)
        eq.setBands(floatArrayOf(0f, 0f, 0f, 0f, 0f))
        val x = sineShorts(1000.0, 0.4, 4096)
        val original = x.copyOf()
        eq.process(x, x.size, 1.0f)
        assertTrue(x.contentEquals(original), "شرائح صفرية = هوية تامة")
    }

    @Test
    fun `eq bass boost actually lifts low end`() {
        val eq = MusicEq()
        eq.configure(fs, 1)
        eq.setEnabled(true)
        eq.setBands(floatArrayOf(12f, 0f, 0f, 0f, 0f))
        val bass = sineShorts(60.0, 0.3, 4096)
        val inMax = bass.maxOf { abs(it.toInt()) }.toDouble()
        eq.process(bass, bass.size, 1.0f)
        val outMax = bass.maxOf { abs(it.toInt()) }.toDouble()
        assertTrue(outMax / inMax > 1.8, "باص +12 يرفع فعليًا: ${outMax / inMax}")
    }

    @Test
    fun `eq bypass when disabled`() {
        val eq = MusicEq()
        eq.configure(fs, 1)
        eq.setEnabled(false)
        eq.setBands(floatArrayOf(12f, 12f, 12f, 12f, 12f))
        val x = sineShorts(1000.0, 0.4, 4096)
        val original = x.copyOf()
        eq.process(x, x.size, 1.0f)
        assertTrue(x.contentEquals(original), "المعادل المطفي = هوية حتى بشرائح مرتفعة")
    }

    @Test
    fun `mute gain silences channel gradually`() {
        val eq = MusicEq()
        eq.configure(fs, 1)
        val x = sineShorts(1000.0, 0.4, 4096)
        repeat(10) { eq.process(x, x.size, 0f) }
        assertTrue(x.all { it == 0.toShort() }, "الكتم الجلسي يصمت القناة")
    }

    @Test
    fun `stereo processing keeps channels independent`() {
        val eq = MusicEq()
        eq.configure(fs, 2)
        eq.setEnabled(true)
        eq.setBands(floatArrayOf(0f, 0f, 0f, 0f, 0f))
        val stereo = ShortArray(4096) { if (it % 2 == 0) 5000 else -5000 }
        val original = stereo.copyOf()
        eq.process(stereo, stereo.size, 1.0f)
        assertTrue(stereo.contentEquals(original), "الستيريو بشرائح صفرية = هوية")
    }

    // ================= أنماط الإذاعة والنماذج المحفوظة =================

    @Test
    fun `voice presets carry broadcast curves`() {
        val base = VoiceFxState()
        val announcer = MixerLogic.applyVoicePreset(base, "صوت المذيع")
        assertTrue(announcer.presenceDb == 4.0f && announcer.compOn && announcer.gateOn)
        val clarity = MixerLogic.applyVoicePreset(base, "وضوح السرد")
        assertTrue(clarity.presenceDb > announcer.presenceDb && clarity.bassDb < 0f)
        val warm = MixerLogic.applyVoicePreset(base, "دفء الاستوديو")
        assertTrue(warm.bassDb > 0f && !warm.gateOn)
        val natural = MixerLogic.applyVoicePreset(base, "طبيعي")
        assertTrue(!natural.compOn && !natural.gateOn && natural.presenceDb == 0f)
        assertEquals(base, MixerLogic.applyVoicePreset(base, "نمط غير موجود"))
    }

    @Test
    fun `music presets are five in-range bands`() {
        MixerLogic.MUSIC_PRESETS.forEach { (name, bands) ->
            assertEquals(MusicEq.BANDS, bands.size, "نمط $name")
            bands.forEach { assertTrue(it in -MusicEq.MAX_DB..MusicEq.MAX_DB, "نمط $name شريحة $it") }
        }
        val state = MixerLogic.applyMusicPreset(MusicEqState(), "باص دافئ")
        assertEquals(5f, state.bands[0])
        assertEquals("باص دافئ", state.preset)
    }

    @Test
    fun `partial json cannot silence a channel`() {
        val resolved = MixerLogic.resolve(MixerJson(musicFader = -3f))
        assertEquals(1.0f, resolved.micFader)
        assertEquals(1.0f, resolved.masterFader)
        assertEquals(0f, resolved.musicFader, "قيمة خارج الحدود تُحسم")
    }

    @Test
    fun `eq json padded to five bands`() {
        val resolved = MixerLogic.resolve(
            MixerJson(musicEq = MusicEqJson(enabled = true, bands = listOf(4f, 2f)))
        )
        assertTrue(resolved.musicEq.bands.size == 5)
        assertEquals(4f, resolved.musicEq.bands[0])
        assertEquals(0f, resolved.musicEq.bands[4])
    }

    @Test
    fun `ghost preset names are dropped`() {
        val v = MixerLogic.resolveVoice(VoiceFxJson(preset = "غير موجود"))
        assertNull(v.preset)
        val e = MixerLogic.resolveEq(MusicEqJson(preset = "كذبة"))
        assertNull(e.preset)
    }

    @Test
    fun `fader helpers format correctly`() {
        assertEquals(0, MixerLogic.faderPercent(0f))
        assertEquals(100, MixerLogic.faderPercent(1f))
        assertEquals(150, MixerLogic.faderPercent(1.5f))
        assertEquals("-∞", MixerLogic.faderDb(0f))
        assertEquals("0.0", MixerLogic.faderDb(1f))
        assertEquals(1.5f, MixerLogic.cleanFader(9f))
        assertEquals(0f, MixerLogic.cleanFader(-1f))
    }

    @Test
    fun `settings store roundtrip keeps mixer and school name`() {
        val dir = Files.createTempDirectory("sr-mixer-test").toFile()
        try {
            val f = File(dir, "settings.json")
            val st = SettingsStore(f)
            st.setSchoolName("مدرسة الوحدة")
            st.setMixer(
                MixerState(
                    micFader = 1.2f, musicFader = 0.8f, masterFader = 1.1f,
                    voice = MixerLogic.applyVoicePreset(VoiceFxState(), "صوت المذيع"),
                    musicEq = MixerLogic.applyMusicPreset(MusicEqState(), "باص دافئ")
                )
            )
            val reloaded = SettingsStore(f)
            val m = reloaded.mixer()
            assertEquals(1.2f, m.micFader)
            assertEquals(0.8f, m.musicFader)
            assertEquals(1.1f, m.masterFader)
            assertEquals("صوت المذيع", m.voice.preset)
            assertEquals(4.0f, m.voice.presenceDb)
            assertTrue(m.voice.compOn)
            assertEquals("باص دافئ", m.musicEq.preset)
            assertEquals("مدرسة الوحدة", reloaded.schoolName())
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }
}
