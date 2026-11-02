package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.EchoCanceller
import com.saidjte.schoolradio.desktop.audio.FarEndTap
import com.saidjte.schoolradio.desktop.audio.Fft
import com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS
import com.saidjte.schoolradio.desktop.data.LibraryDefaults
import com.saidjte.schoolradio.desktop.data.MusicLibraryStore
import com.saidjte.schoolradio.desktop.data.PlaylistMath
import com.saidjte.schoolradio.desktop.data.PlaylistStore
import com.saidjte.schoolradio.desktop.data.RepeatMode
import java.io.File
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * اختبارات الجيل الثاني: إلغاء الصدى PBFDAF بمرجع متصل منساب (صدى مباشر + ذيل
 * ارتداد متعدد الطرقات)، قراءة المرجع المستمرة بلا انقطاع، منطق التكرار الثلاثي
 * والعشوائية، وبوابة الصيغ في المكتبة — كلها DSP ومنطق نقي على إشارات حتمية.
 */
class AecV2AndPlayerTest {

    // ================= FFT =================

    @Test
    fun `fft roundtrip preserves signal`() {
        val n = 256
        val re = DoubleArray(n) { sin(2.0 * PI * 17 * it / n) + 0.3 * sin(2.0 * PI * 41 * it / n) }
        val im = DoubleArray(n)
        val re0 = re.copyOf()
        Fft.fft(re, im)
        Fft.ifft(re, im)
        for (i in 0 until n) {
            assertTrue(abs(re[i] - re0[i]) < 1e-9, "fft roundtrip عند $i: ${re[i]} مقابل ${re0[i]}")
            assertTrue(abs(im[i]) < 1e-9)
        }
    }

    @Test
    fun `fft linear convolution matches direct convolution`() {
        val a = DoubleArray(300) { sin(2.0 * PI * 5 * it / 300.0) }
        val b = DoubleArray(97) { 1.0 / (1.0 + it) }
        val conv = Fft.linearConvolve(a, b)
        assertEquals(a.size + b.size - 1, conv.size)
        val direct = DoubleArray(a.size + b.size - 1)
        for (i in a.indices) for (j in b.indices) direct[i + j] += a[i] * b[j]
        for (i in direct.indices) {
            assertTrue(abs(conv[i] - direct[i]) < 1e-9, "التفاف عند $i: ${conv[i]} مقابل ${direct[i]}")
        }
    }

    // ================= المرجع المتصل readContinuous =================

    @Test
    fun `continuous reference reads without gaps across rate mismatch`() {
        val refRate = 44100
        val micRate = 48000
        val tap = FarEndTap(2.5)
        tap.configure(refRate)
        val total = (2.0 * refRate).toInt()
        val signal = DoubleArray(total) { sin(2.0 * PI * 440.0 * it / refRate) }
        // دفع أولي: ثانيتان كاملتان ثم تغذية بمعدل الاستهلاك تقريبًا
        var pushed = 0
        fun pushMore(count: Int) {
            // الأساس يُلتقط مرة واحدة: القيم متتالية بلا فجوات (درس قفزات 512)
            val base = pushed
            var p = 0
            while (p < count) {
                val k = minOf(512, count - p)
                val arr = FloatArray(k) { j -> sin(2.0 * PI * 440.0 * (base + p + j) / refRate).toFloat() }
                tap.push(arr, k)
                p += k
            }
            pushed += count
        }
        pushMore(total)
        assertTrue(tap.alignContinuous(0.10), "الربط ينجح بعد تاريخ كافٍ")

        val frames = 480
        val out = FloatArray(frames)
        var crossings = 0
        var maxJump = 0.0
        var reanchors = 0
        val calls = 120
        for (c in 0 until calls) {
            pushMore((frames * refRate / micRate)) // ~441 عيّنة مرجعية لكل نداء
            val r = tap.readContinuous(micRate, frames, 0.10, out)
            if (r != FarEndTap.ReadResult.OK) reanchors++
            for (i in 0 until frames) {
                if (i > 0) maxJump = max(maxJump, abs(out[i] - out[i - 1]).toDouble())
                if (i > 0 && out[i - 1] <= 0f && out[i] > 0f) crossings++ // تقاطع صاعد فقط
                assertTrue(abs(out[i]) <= 1.05, "سعة منطقية عند نداء $c عيّنة $i: ${out[i]}")
            }
        }
        assertEquals(0, reanchors, "بلا انقطاعات في التغذية المنتظمة")
        assertTrue(maxJump < 0.2, "بلا قفزات: $maxJump")
        val expectedCrossings = 440.0 * (calls * frames.toDouble() / micRate)
        assertTrue(abs(crossings - expectedCrossings) < expectedCrossings * 0.1,
            "التواتر محفوظ: $crossings مقابل ${expectedCrossings}")
    }

    // ================= AEC v2: صدى غرفة بذيل ارتدادي =================

    @Test
    fun `aec cancels multi-tap room echo with reverb tail at 48k`() {
        val fs = 48000
        val blockN = 1024
        val seconds = 4.0
        val nSamples = (seconds * fs).toInt()
        val rng = Random(42)
        val ref = DoubleArray(nSamples) {
            val t = it.toDouble() / fs
            val am = 0.55 + 0.4 * sin(2.0 * PI * 0.7 * t)
            (0.5 * sin(2.0 * PI * 300 * it / fs) + 0.25 * sin(2.0 * PI * 900 * it / fs) +
                    0.18 * sin(2.0 * PI * 1500 * it / fs)) * am +
                    0.02 * (rng.nextDouble() - 0.5)
        }
        // مسار غرفة: صدى مباشر 87ms بكسب 0.45 + ارتدادان متأخران
        val d0 = (0.087 * fs).toInt()
        val d1 = d0 + (0.023 * fs).toInt()
        val d2 = d0 + (0.047 * fs).toInt()
        val micRef = DoubleArray(nSamples) {
            var v = 0.0
            if (it >= d0) v += 0.45 * ref[it - d0]
            if (it >= d1) v += 0.15 * ref[it - d1]
            if (it >= d2) v += 0.08 * ref[it - d2]
            v
        }

        val tap = FarEndTap(2.5)
        tap.configure(fs)
        val aec = EchoCanceller()
        aec.configure(fs, 1)
        val mic = ShortArray(blockN)
        var pushed = 0
        var inEnergy = 0.0
        var outEnergy = 0.0
        val nBlocks = nSamples / blockN
        for (b in 0 until nBlocks) {
            val target = (b + 1) * blockN
            while (pushed < target) {
                val k = minOf(1152, target - pushed)
                tap.push(FloatArray(k) { ref[pushed + it].toFloat() }, k)
                pushed += k
            }
            for (f in 0 until blockN) {
                mic[f] = (micRef[b * blockN + f] * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            }
            val pre = mic.copyOf()
            aec.process(mic, mic.size, tap)
            if (b >= nBlocks - 48) { // آخر 1.2 ثانية — بعد أي بحث وإعادة تقارب
                for (f in 0 until blockN) {
                    inEnergy += (pre[f].toDouble() / 32768.0).let { it * it }
                    outEnergy += (mic[f].toDouble() / 32768.0).let { it * it }
                }
            }
        }
        val erle = 10.0 * log10(inEnergy / max(outEnergy, 1e-12))
        assertTrue(erle > 10.0, "كبح الصدى متعدد الطرقات غير كافٍ: $erle dB")
        assertTrue(aec.locked, "المسار مقفل")
        // حلقة الميكروفون وساعة المرجع متطابقتان لحظة البحث: المؤشر = تأخير المسار المباشر
        assertTrue(abs(aec.delayMs - 87.0f) < 35.0f,
            "التأخير المكتشف ${aec.delayMs} يقارب 87")
        assertTrue(aec.erleDb > 8f, "مؤشر ERLE الحي يعكس الكبح: ${aec.erleDb}")
    }

    @Test
    fun `aec freezes learning during double talk and never diverges`() {
        val fs = 48000
        val blockN = 1024
        val nSamples = 2 * fs
        val rng = Random(9)
        val ref = DoubleArray(nSamples) {
            0.5 * sin(2.0 * PI * 350 * it / fs) + 0.02 * (rng.nextDouble() - 0.5)
        }
        val d0 = (0.09 * fs).toInt()
        val micRef = DoubleArray(nSamples) { if (it >= d0) 0.4 * ref[it - d0] else 0.0 }
        val tap = FarEndTap(2.5)
        tap.configure(fs)
        val aec = EchoCanceller()
        aec.configure(fs, 1)
        val mic = ShortArray(blockN)
        var pushed = 0
        var worstGain = 1.0
        val nBlocks = nSamples / blockN
        for (b in 0 until nBlocks) {
            val target = (b + 1) * blockN
            while (pushed < target) {
                val k = minOf(1152, target - pushed)
                tap.push(FloatArray(k) { ref[pushed + it].toFloat() }, k)
                pushed += k
            }
            for (f in 0 until blockN) {
                val n = b * blockN + f
                // كلام متقطع فوق الصدى — التكيّف يجب أن يتجمّد لا يتبع الكلام
                val speech = if ((n / 1600) % 2 == 0) 0.3 * sin(2.0 * PI * 620 * n / fs) else 0.0
                mic[f] = ((micRef[n] + speech) * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
            }
            val pre = mic.copyOf()
            aec.process(mic, mic.size, tap)
            // لا انحراف: الخرج لا يتضخم عن الدخل أبدًا بمعنى طاقة الكتلة
            var inE = 0.0; var outE = 0.0
            for (f in 0 until blockN) {
                inE += (pre[f].toDouble() / 32768.0).let { it * it }
                outE += (mic[f].toDouble() / 32768.0).let { it * it }
            }
            val gain = outE / max(inE, 1e-12)
            worstGain = max(worstGain, gain)
        }
        assertTrue(worstGain < 4.0, "حارس الانحراف يمنع تضخم الخرج: أسوأ كسب $worstGain")
    }

    // ================= التكرار الثلاثي والعشوائية =================

    @Test
    fun `repeat modes advance deterministically`() {
        val rng = Random(11)
        // لا تكرار: يتقدم ثم يتوقف آليًا عند النهاية، ويعيد الأول يدويًا
        assertEquals(3, PlaylistMath.nextIndex(5, 2, RepeatMode.OFF, false, true, rng))
        assertEquals(-1, PlaylistMath.nextIndex(5, 4, RepeatMode.OFF, false, true, rng))
        assertEquals(0, PlaylistMath.nextIndex(5, 4, RepeatMode.OFF, false, false, rng))
        // الكل: يلفّ دائمًا
        assertEquals(0, PlaylistMath.nextIndex(5, 4, RepeatMode.ALL, false, true, rng))
        assertEquals(4, PlaylistMath.nextIndex(5, 3, RepeatMode.ALL, false, false, rng))
        // المقطع: يعيد الجاري (آليًا ويدويًا)
        assertEquals(2, PlaylistMath.nextIndex(5, 2, RepeatMode.ONE, false, true, rng))
        assertEquals(2, PlaylistMath.nextIndex(5, 2, RepeatMode.ONE, false, false, rng))
        // السابق يلفّ عكسيًا
        assertEquals(4, PlaylistMath.prevIndex(5, 0, false, rng))
        assertEquals(1, PlaylistMath.prevIndex(5, 2, false, rng))
    }

    @Test
    fun `shuffle never repeats the current track`() {
        val rng = Random(3)
        repeat(50) {
            val n = PlaylistMath.nextIndex(8, 5, RepeatMode.OFF, true, true, rng)
            assertTrue(n in 0..7 && n != 5, "العشوائي يخير غير الجاري: $n")
            val p = PlaylistMath.prevIndex(8, 5, true, rng)
            assertTrue(p in 0..7 && p != 5, "العشوائي للسابق يخير غير الجاري: $p")
        }
        assertEquals(0, PlaylistMath.nextIndex(1, 0, RepeatMode.OFF, true, true, rng), "مقطع وحيد")
    }

    @Test
    fun `repeat mode cycle order is off all one`() {
        assertEquals(RepeatMode.ALL, RepeatMode.OFF.next())
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.OFF, RepeatMode.ONE.next())
        assertEquals(RepeatMode.ALL, RepeatMode.fromCode(1))
        assertEquals(RepeatMode.ONE, RepeatMode.fromCode(2))
        assertEquals(RepeatMode.OFF, RepeatMode.fromCode(null))
        // الملفات القديمة: repeatAll=true ترث «الكل»
        assertEquals(RepeatMode.ALL, PlaylistMath.resolveRepeat(null, true))
        assertEquals(RepeatMode.OFF, PlaylistMath.resolveRepeat(null, false))
        assertEquals(RepeatMode.ONE, PlaylistMath.resolveRepeat(2, true), "الرمز المحفوظ أقوى من القديم")
    }

    @Test
    fun `playlist store persists repeat mode and shuffle`() {
        val dir = Files.createTempDirectory("sr-aecv2-test").toFile()
        try {
            val f = File(dir, "playlist.json")
            // ترحيل ملف نسخة قديمة: repeatAll فقط
            f.writeText("""{"paths": ["/a.mp3", "/b.mp3"], "currentIndex": 1, "repeatAll": true}""")
            val store = PlaylistStore(f)
            assertEquals(RepeatMode.ALL, store.repeatMode(), "القديم يرث «الكل»")
            store.setRepeatMode(RepeatMode.ONE)
            store.setShuffle(true)
            val reloaded = PlaylistStore(f)
            assertEquals(RepeatMode.ONE, reloaded.repeatMode())
            assertTrue(reloaded.shuffle())
            assertTrue(reloaded.repeatAll() == false, "حقل التوافق يتحدث مع النظام الجديد")
            reloaded.setRepeatMode(RepeatMode.ALL)
            assertTrue(PlaylistStore(f).repeatAll(), "تكرار الكل يكتب حقل التوافق أيضًا")
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    // ================= بوابة الصيغ =================

    @Test
    fun `library import rejects undecodable formats and reports them`() {
        val dir = Files.createTempDirectory("sr-libguard-test").toFile()
        try {
            val good = File(dir, "song.mp3").apply { writeBytes(ByteArray(128)) }
            val bad = File(dir, "voice.m4a").apply { writeBytes(ByteArray(128)) }
            val bad2 = File(dir, "clip.wma").apply { writeBytes(ByteArray(128)) }
            val store = MusicLibraryStore(File(dir, "library.json"))
            val r = store.addFiles(listOf(good, bad, bad2), LibraryDefaults.FALLBACK_CATEGORY)
            assertEquals(1, r.added.size, "القابل للفك فقط يدخل")
            assertEquals(2, r.skippedUnsupported, "المرفوض يُعدّ ويُعرض")
            assertEquals(setOf("mp3"), r.added.mapNotNull { it.path?.substringAfterLast('.') }.toSet())
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `playable extensions cover modern formats`() {
        assertTrue("ogg" in PLAYABLE_EXTENSIONS)
        assertTrue("oga" in PLAYABLE_EXTENSIONS)
        assertTrue("flac" in PLAYABLE_EXTENSIONS)
        assertTrue("mp3" in PLAYABLE_EXTENSIONS)
        assertTrue("wav" in PLAYABLE_EXTENSIONS)
        assertFalse("m4a" in PLAYABLE_EXTENSIONS, "Aac غير مدعوم — يُرفض في البوابة صراحة")
    }
}
