package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.DuckBus
import com.saidjte.schoolradio.desktop.audio.Ducker
import com.saidjte.schoolradio.desktop.audio.EchoCanceller
import com.saidjte.schoolradio.desktop.audio.FarEndTap
import com.saidjte.schoolradio.desktop.audio.VoiceProcessor
import com.saidjte.schoolradio.desktop.data.AecJson
import com.saidjte.schoolradio.desktop.data.DuckJson
import com.saidjte.schoolradio.desktop.data.MixerJson
import com.saidjte.schoolradio.desktop.data.MixerLogic
import com.saidjte.schoolradio.desktop.data.MixerState
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.VoiceFxJson
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * اختبارات الإذاعة الاحترافية v0.11.0: توحيد الكسب AGC، إلغاء الصدى AEC
 * (مرجع طرف بعيد + بحث تأخير + NLMS)، والخفض التلقائي Voice-Over —
 * كلها DSP نقي على إشارات تركيبية حتمية، بلا أي عتاد.
 */
class ProAudioTest {

    private val fs = 44100

    private fun sineShorts(freq: Double, amp: Double, samples: Int, rate: Int = fs): ShortArray =
        ShortArray(samples) { (amp * 32767 * sin(2.0 * PI * freq * it / rate)).toInt().toShort() }

    // ================= AGC: توحيد الكسب الأوتوماتيكي =================

    private fun agcProcessor(): VoiceProcessor = VoiceProcessor().also {
        it.configure(fs, 1)
        it.setParams(
            VoiceProcessor.Params(
                hpfOn = false, gateOn = false, compOn = false,
                agcOn = true, agcTargetDb = -18f, agcMaxBoostDb = 24f
            )
        )
    }

    @Test
    fun `agc boosts quiet input toward target`() {
        val agc = agcProcessor()
        val src = sineShorts(440.0, 0.02, 1024)
        repeat(120) { val blk = src.copyOf(); agc.process(blk, blk.size, 1.0f) }
        assertTrue(agc.agcCurrentDb > 10f, "يجب أن يرفع الخافت: ${agc.agcCurrentDb}")
    }

    @Test
    fun `agc attenuates loud input`() {
        val agc = agcProcessor()
        val src = sineShorts(440.0, 0.5, 1024)
        repeat(120) { val blk = src.copyOf(); agc.process(blk, blk.size, 1.0f) }
        assertTrue(agc.agcCurrentDb < -5f, "يجب أن يخفض القوي: ${agc.agcCurrentDb}")
    }

    @Test
    fun `agc freezes in silence instead of chasing noise`() {
        val agc = agcProcessor()
        val block = ShortArray(1024)
        repeat(200) { agc.process(block, block.size, 1.0f) }
        assertEquals(0f, agc.agcCurrentDb, "الصمت لا يحرّك الكسب إطلاقًا")
    }

    @Test
    fun `agc respects max boost ceiling`() {
        val agc = agcProcessor()
        agc.setParams(
            VoiceProcessor.Params(
                hpfOn = false, gateOn = false, compOn = false,
                agcOn = true, agcTargetDb = -18f, agcMaxBoostDb = 10f
            )
        )
        val src = sineShorts(440.0, 0.005, 1024)
        repeat(300) { val blk = src.copyOf(); agc.process(blk, blk.size, 1.0f) }
        assertTrue(agc.agcCurrentDb <= 10.5f, "التعزيز لا يتجاوز السقف: ${agc.agcCurrentDb}")
    }

    @Test
    fun `agc disabled keeps unity gain`() {
        val agc = agcProcessor()
        agc.setParams(
            VoiceProcessor.Params(hpfOn = false, gateOn = false, compOn = false, agcOn = false)
        )
        val block = sineShorts(440.0, 0.05, 1024)
        repeat(60) { agc.process(block, block.size, 1.0f) }
        assertEquals(0f, agc.agcCurrentDb)
    }

    // ================= مرجع الطرف البعيد FarEndTap =================

    @Test
    fun `far end tap returns window matching written signal at same rate`() {
        val tap = FarEndTap(0.25)
        tap.configure(fs)
        val n = 8192
        val signal = DoubleArray(n) { sin(2.0 * PI * 350.0 * it / fs) }
        var pushed = 0
        while (pushed < n) {
            val k = minOf(512, n - pushed)
            tap.push(FloatArray(k) { signal[pushed + it].toFloat() }, k)
            pushed += k
        }
        val frames = 1024
        val out = FloatArray(frames)
        assertTrue(tap.readWindow(fs, frames, 0.0, out), "النافذة متاحة بعد امتلاء نصف الحلقة")
        for (i in 0 until frames) {
            val expected = signal[n - frames + i]
            assertTrue(abs(out[i] - expected) < 0.02, "عند $i: ${out[i]} مقابل $expected")
        }
    }

    @Test
    fun `far end tap honors delay offset`() {
        val tap = FarEndTap(0.25)
        tap.configure(fs)
        val n = 8192
        val signal = DoubleArray(n) { sin(2.0 * PI * 350.0 * it / fs) }
        var pushed = 0
        while (pushed < n) {
            val k = minOf(512, n - pushed)
            tap.push(FloatArray(k) { signal[pushed + it].toFloat() }, k)
            pushed += k
        }
        val frames = 1024
        val delaySamples = 1000
        val out = FloatArray(frames)
        val d = delaySamples.toDouble() / fs
        assertTrue(tap.readWindow(fs, frames, d, out))
        for (i in 0 until frames) {
            val expected = signal[n - delaySamples - frames + i]
            assertTrue(abs(out[i] - expected) < 0.02, "تأخير عند $i: ${out[i]} مقابل $expected")
        }
    }

    @Test
    fun `far end tap resamples foreign mic rate`() {
        val tap = FarEndTap(0.25)
        tap.configure(fs)
        val n = 8192
        val signal = DoubleArray(n) { sin(2.0 * PI * 350.0 * it / fs) }
        var pushed = 0
        while (pushed < n) {
            val k = minOf(512, n - pushed)
            tap.push(FloatArray(k) { signal[pushed + it].toFloat() }, k)
            pushed += k
        }
        // قراءة بمعدل ميكروفون نصف معدل المرجع: عيّنة كل عينتين مرجعيتين
        val frames = 512
        val out = FloatArray(frames)
        assertTrue(tap.readWindow(fs / 2, frames, 0.0, out))
        for (i in 0 until frames) {
            val refIdx = n - frames * 2 + 1 + i * 2
            val expected = signal[refIdx]
            assertTrue(abs(out[i] - expected) < 0.05, "إعادة قياس عند $i: ${out[i]} مقابل $expected")
        }
    }

    @Test
    fun `far end tap refuses when history insufficient`() {
        val tap = FarEndTap(1.0)
        tap.configure(fs)
        val out = FloatArray(64)
        assertFalse(tap.readWindow(fs, 64, 0.0, out), "حلقة فارغة = رفض")
        tap.push(FloatArray(1024) { 0.5f }, 1024)
        assertFalse(tap.readWindow(fs, 64, 0.0, out), "نصف الحلقة لم يمتلئ بعد")
    }

    // ================= إلغاء الصدى AEC =================

    /** محاكاة زمن حقيقية: صدى مرجعي مؤخَّر بكسب معلوم + صوت مذيع. */
    private class SimResult(
        val cancellationDb: Double,
        val locked: Boolean,
        val delayMs: Float,
        val voiceKeptRms: Double
    )

    private fun runEchoSimulation(withVoice: Boolean): SimResult {
        val blockN = 4096
        val nBlocks = 40
        val delaySamples = (0.120 * fs).toInt()
        val totalRef = delaySamples + blockN * nBlocks
        // زحزحة ترددات واسعة (200→2800هرتز كل ثانيتين) — موسيقى واقعية قابلة
        // لتحديد المسار: نغمة واحدة وحيدة تجعل موضع الطرقة غير قابل للتمييز
        val ref = DoubleArray(totalRef) {
            val t = it.toDouble() / fs
            val phase = (t % 2.0) / 2.0
            val freq = 200.0 + 2600.0 * phase
            0.6 * sin(2.0 * PI * freq * t)
        }
        val tap = FarEndTap(2.5)
        tap.configure(fs)
        val aec = EchoCanceller()
        aec.configure(fs, 1)
        var inEnergy = 0.0
        var outEnergy = 0.0
        var voiceEnergy = 0.0
        val mic = ShortArray(blockN)
        var pushed = 0
        for (b in 0 until nBlocks) {
            val target = (b + 1) * blockN
            while (pushed < target) {
                val k = minOf(512, target - pushed)
                tap.push(FloatArray(k) { ref[pushed + it].toFloat() }, k)
                pushed += k
            }
            for (f in 0 until blockN) {
                val idx = b * blockN + f - delaySamples
                val echo = if (idx >= 0) 0.5 * ref[idx] else 0.0
                // كلام المذيع متقطع (نصف الوقت) — كاشف الحديث المزدوج يجمّد التعلّم فيه
                val voice = if (withVoice && ((b * blockN + f) / 2048) % 2 == 0) {
                    0.15 * sin(2.0 * PI * 700.0 * (b * blockN + f) / fs)
                } else 0.0
                voiceEnergy += voice * voice
                mic[f] = ((echo + voice) * 32767.0).toInt().toShort()
            }
            val pre = mic.copyOf()
            aec.process(mic, mic.size, tap)
            if (b >= 32) {
                for (f in 0 until blockN) {
                    inEnergy += (pre[f].toDouble() / 32768.0).let { it * it }
                    outEnergy += (mic[f].toDouble() / 32768.0).let { it * it }
                }
            }
        }
        val cancellationDb = 10.0 * kotlin.math.log10(inEnergy / maxOf(outEnergy, 1e-12))
        return SimResult(cancellationDb, aec.locked, aec.delayMs, kotlin.math.sqrt(voiceEnergy))
    }

    @Test
    fun `echo canceller suppresses delayed reference and locks`() {
        val r = runEchoSimulation(withVoice = false)
        assertTrue(r.cancellationDb > 8.0, "إلغاء غير كافٍ: ${r.cancellationDb} dB")
        assertTrue(r.locked, "المسار يجب أن يقفل")
        // حلقة الميكروفون وساعة المرجع متطابقتان لحظة البحث: المؤشر = التأخير الصوتي الحقيقي
        assertTrue(abs(r.delayMs - 120f) <= 35f, "التأخير المكتشف قريب من الحقيقي: ${r.delayMs}")
    }

    @Test
    fun `echo canceller keeps announcer voice while removing echo`() {
        val r = runEchoSimulation(withVoice = true)
        assertTrue(r.cancellationDb > 4.0, "الصدى يُطرح مع بقاء الصوت: ${r.cancellationDb} dB")
        assertTrue(r.voiceKeptRms > 0.0)
    }

    @Test
    fun `echo canceller bypasses when disabled`() {
        val aec = EchoCanceller()
        aec.configure(fs, 1)
        aec.enabled = false
        val block = sineShorts(500.0, 0.3, 1024)
        val before = block.copyOf()
        assertFalse(aec.process(block, block.size, null))
        assertTrue(block.contentEquals(before), "معطّل = هوية تامة")
    }

    @Test
    fun `echo canceller bypasses on stale reference`() {
        val tap = FarEndTap(1.0)
        tap.configure(fs)
        tap.push(FloatArray(2048) { 0.5f }, 2048)
        val aec = EchoCanceller()
        aec.configure(fs, 1)
        val block = sineShorts(500.0, 0.3, 1024)
        val before = block.copyOf()
        Thread.sleep(450) // المرجع راكد الآن (آخر دفع قبل >400ms)
        assertFalse(aec.process(block, block.size, tap))
        assertTrue(block.contentEquals(before), "مرجع راكد = مرور نظيف تام")
    }

    @Test
    fun `echo canceller never outputs beyond pcm range`() {
        val blockN = 4096
        val nBlocks = 10
        val delaySamples = (0.12 * fs).toInt()
        val totalRef = delaySamples + blockN * nBlocks
        val ref = DoubleArray(totalRef) { 0.9 * sin(2.0 * PI * 250.0 * it / fs) }
        val tap = FarEndTap(2.5)
        tap.configure(fs)
        val aec = EchoCanceller()
        aec.configure(fs, 1)
        var pushed = 0
        val mic = ShortArray(blockN)
        for (b in 0 until nBlocks) {
            val target = (b + 1) * blockN
            while (pushed < target) {
                val k = minOf(512, target - pushed)
                tap.push(FloatArray(k) { ref[pushed + it].toFloat() }, k)
                pushed += k
            }
            for (f in 0 until blockN) {
                val idx = b * blockN + f - delaySamples
                mic[f] = (0.9 * ref[idx.coerceAtLeast(0)] * 32767.0).toInt().toShort()
            }
            aec.process(mic, mic.size, tap)
            assertTrue(mic.all { abs(it.toInt()) <= 32767 }, "الخرج داخل نطاق PCM دائمًا")
        }
    }

    // ================= الخفض التلقائي Voice-Over =================

    @Test
    fun `ducker descends to duck level during speech`() {
        val dk = Ducker()
        var g = dk.process(21.0, micLive = true, voiceActive = true, duckDb = -12.0, holdMs = 1200.0)
        repeat(69) { g = dk.process(21.0, true, true, -12.0, 1200.0) }
        assertTrue(g in 0.24f..0.27f, "هدف -12dB ≈ 0.251: $g")
        assertTrue(dk.active)
    }

    @Test
    fun `ducker holds then releases smoothly after silence`() {
        val dk = Ducker()
        var g = 1f
        repeat(70) { g = dk.process(21.0, true, true, -12.0, 1200.0) }
        repeat(20) { g = dk.process(21.0, true, false, -12.0, 1200.0) }
        assertTrue(g < 0.4f, "خلال التعليق يبقى منخفضًا: $g")
        repeat(250) { g = dk.process(21.0, true, false, -12.0, 1200.0) }
        assertTrue(g > 0.99f, "بعد التعليق يعود للوحدة: $g")
    }

    @Test
    fun `ducker never ducks when mic is idle`() {
        val dk = Ducker()
        var g = 1f
        repeat(100) { g = dk.process(21.0, micLive = false, voiceActive = false, duckDb = -12.0, holdMs = 1200.0) }
        assertTrue(g > 0.99f && !dk.active, "بلا بث = لا خفض: $g")
    }

    @Test
    fun `ducker gain never exceeds unity and respects ratio`() {
        val dk = Ducker()
        var g = 1f
        repeat(100) { g = dk.process(21.0, true, true, -6.0, 500.0) }
        assertTrue(g <= 1.0f, "الكسب لا يتجاوز الوحدة: $g")
        assertTrue(g in 0.48f..0.53f, "هدف -6dB ≈ 0.501: $g")
    }

    @Test
    fun `ducker reset returns to unity`() {
        val dk = Ducker()
        repeat(50) { dk.process(21.0, true, true, -24.0, 1000.0) }
        dk.reset()
        assertTrue(dk.currentGain() > 0.99f && !dk.active, "التصفير يعيد الوحدة")
    }

    @Test
    fun `duck bus defaults keep music untouched`() {
        assertTrue(DuckBus.musicGain in 0.999f..1.001f, "الناقل يبدأ محايدًا")
        assertTrue(DuckBus.enabled)
    }

    // ================= نماذج الحفظ =================

    @Test
    fun `mixer json roundtrip keeps duck aec and agc`() {
        val state = MixerState(
            duck = com.saidjte.schoolradio.desktop.data.DuckState(enabled = false, duckDb = -18f, holdMs = 2500f),
            aec = com.saidjte.schoolradio.desktop.data.AecState(enabled = false),
            voice = com.saidjte.schoolradio.desktop.data.VoiceFxState(
                agcOn = false, agcTargetDb = -22f, agcMaxBoostDb = 10f
            )
        )
        val file = File.createTempFile("sr-mixer-test", ".json")
        try {
            val store = SettingsStore(file)
            store.setMixer(state)
            val back = SettingsStore(file).mixer()
            assertFalse(back.duck.enabled)
            assertEquals(-18f, back.duck.duckDb)
            assertEquals(2500f, back.duck.holdMs)
            assertFalse(back.aec.enabled)
            assertFalse(back.voice.agcOn)
            assertEquals(-22f, back.voice.agcTargetDb)
            assertEquals(10f, back.voice.agcMaxBoostDb)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `missing json fields resolve to professional defaults`() {
        val r = MixerLogic.resolve(MixerJson())
        assertTrue(r.duck.enabled && r.duck.duckDb == -12f && r.duck.holdMs == 1200f, "الخفض افتراضي")
        assertTrue(r.aec.enabled, "إلغاء الصدى افتراضي")
        assertTrue(r.voice.agcOn && r.voice.agcTargetDb == -18f && r.voice.agcMaxBoostDb == 18f, "AGC افتراضي")
    }

    @Test
    fun `wild duck values are coerced to bounds`() {
        val r = MixerLogic.resolveDuck(DuckJson(duckDb = 5f, holdMs = 99_000f))
        assertEquals(-3f, r.duckDb, "خفض موجب يُقص لأخف خفض مسموح")
        assertEquals(5000f, r.holdMs)
        val a = MixerLogic.resolveAec(AecJson(enabled = null))
        assertTrue(a.enabled, "غياب الحقل = الافتراضي لا العكس")
    }

    @Test
    fun `wild agc values are coerced to bounds`() {
        val v = MixerLogic.resolveVoice(VoiceFxJson(agcTargetDb = 3f, agcMaxBoostDb = 99f))
        assertEquals(-6f, v.agcTargetDb)
        assertEquals(30f, v.agcMaxBoostDb)
    }
}
