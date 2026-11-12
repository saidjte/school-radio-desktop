package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.Json
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * عقود v0.47.0 الخمسة: سقف البث (تطبيع الإخراج إلى −1dBFS بتمريرة في المكان)،
 * ترميز المقيّس WAV→MP3، قصّ صمت الأطراف، استماع المحدد بمشروع مصغر،
 * والتشغيل الواحد للمنطقة — كل القرارات نقية على ملفات مولّدة محليًا.
 */
class MontageProToolsV47Test {

    // ================= أدوات الملفات التجريبية =================

    /** موجة جيبية بسعة مضبوطة — تُبنى بنفس كاتب المشروع فالرأس قانوني. */
    private fun toneWav(file: File, ms: Long, amp: Int = 12_000): File {
        val rate = 44_100
        val frames = (ms * rate / 1000L).toInt()
        val w = WavFileWriter(file, rate, 2)
        val buf = ByteArray(4096 * 4)
        var frame = 0
        while (frame < frames) {
            val n = minOf(4096, frames - frame)
            var bi = 0
            for (i in 0 until n) {
                val v = (Math.sin(2.0 * Math.PI * 440.0 * (frame + i) / rate) * amp).toInt()
                buf[bi++] = (v and 0xFF).toByte()
                buf[bi++] = ((v shr 8) and 0xFF).toByte()
                buf[bi++] = (v and 0xFF).toByte()
                buf[bi++] = ((v shr 8) and 0xFF).toByte()
            }
            w.write(buf, 0, bi)
            frame += n
        }
        w.close()
        return file
    }

    /** ملف WAV صمت تام — لقرار «لا يُضخّم الصمت ولا يُقصّ من لا صوت فيه». */
    private fun silenceWav(file: File, ms: Long): File {
        val rate = 44_100
        val frames = (ms * rate / 1000L).toInt()
        val w = WavFileWriter(file, rate, 2)
        val zeros = ByteArray(8192 * 4)
        var written = 0
        while (written < frames * 4) {
            val n = minOf(zeros.size, frames * 4 - written)
            w.write(zeros, 0, n)
            written += n
        }
        w.close()
        return file
    }

    /** ذروة الملف بفك قانوني (MontageClipSource) لا تحليل بايتات خام — مصدر الحقيقة نفسه في المُصيّر. */
    private fun wavPeak(file: File): Int {
        val h = WavHeader.read(file) ?: return 0
        val durMs = WavFileWriter.durationMsOf(h.dataBytes, h.sampleRate, h.channels.coerceAtLeast(1))
        return MontageRender.computeRawPeak(
            com.saidjte.schoolradio.desktop.data.MontageClip(
                id = "probe", path = file.absolutePath, title = "probe", durationMs = durMs
            )
        )
    }

    // ================= 1) سقف البث: كسب التطبيع وتطبيقه في المكان =================

    @Test
    fun `masterGain honors target caps gain and spares silence`() {
        // صمت تام: لا تضخيم أبدًا
        assertEquals(1f, MontageRender.masterGain(0), "silence keeps gain 1.0")
        // مزيج حارّ (~0dBFS): تخفيض إلى −1dBFS بلا سقف يلزم
        val hot = MontageRender.masterGain(32_767)
        assertTrue(hot < 0.9f && hot > 0.88f, "hot mix scaled to -1dBFS, got $hot")
        // ذروة منخفضة جدًا: التضخيم محصور بسقف +24dB (15.85×)
        val quiet = MontageRender.masterGain(100)
        assertEquals(15.8489f, quiet, 0.01f, "gain capped at +24dB, got $quiet")
        // ذروة −6dBFS تقريبًا: رفع نظيف إلى الهدف
        val mid = MontageRender.masterGain(16_384)
        assertEquals(0.8913f / (16_384f / 32_768f), mid, 0.001f)
    }

    @Test
    fun `applyWavMaster scales the file in place to the target peak`() {
        val tmp = createTempDir()
        try {
            val f = toneWav(File(tmp, "mix.wav"), 800)
            val before = wavPeak(f)
            assertTrue(before in 9_000..14_000, "fixture peak sanity, got $before")
            val gainDb = MontageRender.applyWavMaster(f)
            assertNotNull(gainDb, "valid wav returns applied gain")
            assertTrue(gainDb > 7.0 && gainDb < 8.0, "gain ~+7.7dB for 12000 peak, got $gainDb")
            val after = wavPeak(f)
            // الذروة بعد التطبيع عند −1dBFS ≈ 0.8913×32768 ≈ 29206 (تسامح التقريب)
            assertTrue(after in 29_190..29_215, "peak at -1dBFS, got $after")
            // إعادة التطبيق على ملف مطبَّع: لا فرج — 0.0 بلا مساس
            val again = MontageRender.applyWavMaster(f)
            assertEquals(0.0, again, "second pass is a no-op")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    @Test
    fun `applyWavMaster refuses silence and garbage without touching them`() {
        val tmp = createTempDir()
        try {
            val silent = silenceWav(File(tmp, "silent.wav"), 300)
            assertNull(MontageRender.applyWavMaster(silent), "total silence is never amplified")
            assertEquals(0, wavPeak(silent), "silence file untouched")
            val junk = File(tmp, "junk.bin")
            junk.writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
            assertNull(MontageRender.applyWavMaster(junk), "non-wav refused")
            // ملف WAV ناقص الرأس يُردّ أيضًا
            val short = File(tmp, "short.wav")
            short.writeBytes(ByteArray(20))
            assertNull(MontageRender.applyWavMaster(short), "truncated header refused")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    // ================= 2) ترميز المقيّس: WAV مطبَّع → MP3 =================

    @Test
    fun `encodeWavToMp3 produces a real mp3 with matching duration`() {
        val tmp = createTempDir()
        try {
            val wav = toneWav(File(tmp, "m.wav"), 1200)
            val out = File(tmp, "m.mp3")
            val r = MontageRender.encodeWavToMp3(wav, out, 128)
            assertTrue(r.ok, "encode ok: ${r.message}")
            assertTrue(out.isFile && out.length() > 1000, "real file written (${out.length()}B)")
            assertTrue(r.durationMs in 1_150..1_250, "duration from wav data, got ${r.durationMs}")
            val head = out.inputStream().use { it.readNBytes(4) }
            val isMpeg = (head[0].toInt() and 0xFF) == 0xFF && ((head[1].toInt() and 0xE0) == 0xE0)
            val isId3 = String(head, 0, 3) == "ID3"
            assertTrue(isMpeg || isId3, "mp3 frame sync or ID3 header, got ${head.toList()}")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    // ================= 3) قصّ صمت الأطراف: حدود أول وآخر صوت =================

    @Test
    fun `silenceBounds finds the spoken span with safety margins`() {
        val tmp = createTempDir()
        try {
            // 500ms صمت + 500ms صوت + 500ms صمت — جلسة كتابة واحدة فالرأس قانوني
            val f = File(tmp, "speech.wav")
            segmentedWav(f, listOf(false to 500L, true to 500L, false to 500L))
            val b = MontageRender.silenceBounds(f)
            assertNotNull(b, "voice span found")
            // البداية ≈ 500 − هامش 40 = 460 (±30 لتقريب النوافذ)
            assertTrue(b.first in 430..490, "start ≈ 460ms, got ${b.first}")
            // النهاية ≈ 1000 + هامش 40 = 1040 (±30)
            assertTrue(b.second in 1010..1070, "end ≈ 1040ms, got ${b.second}")
            // الحدود داخل الملف دائمًا
            assertTrue(b.first >= 0 && b.second <= 1500 + 60)
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    @Test
    fun `silenceBounds returns null for total silence`() {
        val tmp = createTempDir()
        try {
            val f = silenceWav(File(tmp, "quiet.wav"), 400)
            assertNull(MontageRender.silenceBounds(f), "no voice span in pure silence")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    /** ملف WAV بمقاطع متتالية في جلسة كتابة واحدة: صمت (false) وصوت (true). */
    private fun segmentedWav(file: File, segments: List<Pair<Boolean, Long>>) {
        val rate = 44_100
        val w = WavFileWriter(file, rate, 2)
        val buf = ByteArray(4096 * 4)
        segments.forEach { (voice, ms) ->
            val frames = (ms * rate / 1000L).toInt()
            var frame = 0
            while (frame < frames) {
                val n = minOf(4096, frames - frame)
                var bi = 0
                for (i in 0 until n) {
                    val v = if (voice)
                        (Math.sin(2.0 * Math.PI * 440.0 * (frame + i) / rate) * 12_000).toInt()
                    else 0
                    buf[bi++] = (v and 0xFF).toByte()
                    buf[bi++] = ((v shr 8) and 0xFF).toByte()
                    buf[bi++] = (v and 0xFF).toByte()
                    buf[bi++] = ((v shr 8) and 0xFF).toByte()
                }
                w.write(buf, 0, bi)
                frame += n
            }
        }
        w.close()
    }

    // ================= 4) استماع المحدد: مشروع مصغر بمقطع واحد =================

    @Test
    fun `auditionProject isolates the selected clip alone`() {
        val voice = MontageClip(id = "v1", path = "/x/a.wav", title = "المقدمة", startMs = 500, endMs = 4_500, durationMs = 10_000)
        val music = MontageMusicClip(id = "m1", path = "/x/b.mp3", title = "خلفية", startMs = 2_000)
        val p = MontageProject(clips = listOf(voice), musicClips = listOf(music))

        // المحدد تعليقًا: نسخة بمقطعه وحده بنافذته كما هي
        val a = MontageLogic.auditionProject(p, "v1", null)
        assertNotNull(a)
        assertEquals(listOf(voice), a.clips)
        assertTrue(a.musicClips.isEmpty(), "audition has no music bed")
        assertTrue(a.crossfadeMs == 0L && a.gapMs == 0L, "no junction effects in audition")

        // المحدد مسارًا حرًا: يُسحب إلى صفر كي لا يُسبق بصمت
        val b = MontageLogic.auditionProject(p, null, "m1")
        assertNotNull(b)
        assertEquals(1, b.musicClips.size)
        assertEquals(0L, b.musicClips[0].startMs, "free clip pulled to zero")
        assertTrue(b.clips.isEmpty())

        // الاثنان محددان: التعليق يفوز (هو البرنامج)
        val c = MontageLogic.auditionProject(p, "v1", "m1")
        assertNotNull(c)
        assertEquals("v1", c.clips[0].id)

        // لا محدد صالح: null برسالة الواجهة
        assertNull(MontageLogic.auditionProject(p, null, null))
        assertNull(MontageLogic.auditionProject(p, "ghost", "ghost"))
    }

    // ================= 5) التشغيل الواحد وسقف البث: قرارات النبضة والمفتاح =================

    @Test
    fun `oneShotReached stops exactly past the end`() {
        assertFalse(MontageLogic.oneShotReached(1_000, null), "no end set")
        assertFalse(MontageLogic.oneShotReached(999, 1_000), "before end keeps playing")
        assertTrue(MontageLogic.oneShotReached(1_000, 1_000), "at end stops")
        assertTrue(MontageLogic.oneShotReached(1_500, 1_000), "past end stops")
        assertFalse(MontageLogic.oneShotReached(10_000, 0L), "degenerate end never triggers")
        assertFalse(MontageLogic.oneShotReached(10_000, -5L), "negative end never triggers")
    }

    @Test
    fun `masterNormalize defaults on and honors explicit values`() {
        // الحقل الغائب (ملف JSON قديم) = مفعّل افتراضيًا
        assertTrue(MontageLogic.masterNormalizeEffective(MontageProject()), "absent field means on")
        assertTrue(MontageLogic.masterNormalizeEffective(MontageProject(masterNormalize = null)))
        assertTrue(MontageLogic.masterNormalizeEffective(MontageProject(masterNormalize = true)))
        assertFalse(MontageLogic.masterNormalizeEffective(MontageProject(masterNormalize = false)), "explicit off honored")
    }

    @Test
    fun `master normalize flag survives json round trip`() {
        val on = Json.gson.fromJson("""{"title":"t"}""", MontageProject::class.java)
        assertEquals(null, on.masterNormalize, "old json loads with null")
        assertTrue(MontageLogic.masterNormalizeEffective(on))
        val off = Json.gson.fromJson("""{"title":"t","masterNormalize":false}""", MontageProject::class.java)
        assertEquals(false, off.masterNormalize)
        assertFalse(MontageLogic.masterNormalizeEffective(off))
        val saved = File.createTempFile("proj", ".json")
        try {
            assertTrue(Json.save(saved, MontageProject(masterNormalize = false)))
            val re = Json.gson.fromJson(saved.readText(), MontageProject::class.java)
            assertFalse(MontageLogic.masterNormalizeEffective(re), "saved off stays off")
        } finally {
            saved.delete()
        }
    }

    // ================= 6) عقد مدمجة: أدوات المونتاج القائمة سليمة =================

    @Test
    fun `montage demo files still render through the unchanged mixer`() {
        val tmp = createTempDir()
        try {
            val files = MontageDemo.make(tmp)
            val clips = files.mapIndexed { i, f ->
                MontageClip(id = "d$i", path = f.absolutePath, title = f.nameWithoutExtension, durationMs = if (i == 2) 3_000L else 6_000L)
            }
            val p = MontageProject(clips = clips, gapMs = 250)
            val out = File(tmp, "out.wav")
            val r = MontageRender.render(p, out)
            assertTrue(r.ok, "plain render still works: ${r.message}")
            assertTrue(out.isFile && out.length() > 10_000)
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }
}
