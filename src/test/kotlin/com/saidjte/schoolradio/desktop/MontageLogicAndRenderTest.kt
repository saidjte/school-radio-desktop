package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageStore
import java.io.File
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * عقد صفحة المونتاج (v0.33.0 «أضف صفحة المونتاج»):
 * - المدد والفواصل ونوافذ القص والتلاشين نقية ومحبوسة.
 * - التعقيم يسقط المفقود ويحبس العبث قبل التصيير.
 * - المخزن دورة حفظ/استرجاع ذرّية متسامحة مع التلف.
 * - التصيير: مصادر مختلفة المعدل والقنوات تصير WAV واحد 44.1k ستيريو
 *   بقصّ وتلاشٍ وفواصل حقيقية — بلا عتاد صوت ولا شبكة.
 * - الإلغاء والفشل برسالة عربية واضحة، والتقدم يصل القمة.
 */
class MontageLogicAndRenderTest {

    @TempDir
    lateinit var tmp: File

    // ================= المنطق النقي =================

    @Test
    fun `clip duration and total duration math`() {
        val a = MontageClip(id = "a", path = "/x/a.wav", title = "a", durationMs = 4000, startMs = 1000, endMs = 3000)
        val b = MontageClip(id = "b", path = "/x/b.wav", title = "b", durationMs = 6000, startMs = 0, endMs = 2000)
        assertEquals(2000L, MontageLogic.clipDurationMs(a))
        assertEquals(0L, MontageLogic.totalDurationMs(emptyList(), 500))
        assertEquals(2000L, MontageLogic.totalDurationMs(listOf(a), 500))
        assertEquals(4500L, MontageLogic.totalDurationMs(listOf(a, b), 500))
        assertEquals(4000L, MontageLogic.totalDurationMs(listOf(a, b), 0))
        assertEquals(500L, MontageLogic.gapAfter(0, 2, 500))
        assertEquals(0L, MontageLogic.gapAfter(1, 2, 500))
        assertEquals(0L, MontageLogic.gapAfter(0, 1, 500))
        assertEquals(0L, MontageLogic.gapAfter(0, 0, 500))
    }

    @Test
    fun `window clamping keeps start before end within file`() {
        // النافذة الفارغة تعني «الملف كاملًا» — المقطع الجديد يضاف كاملًا
        assertEquals(0L to 4000L, MontageLogic.clampWindow(0, 0, 4000))
        assertEquals(0L to 4000L, MontageLogic.clampWindow(-5, 99_999, 4000))
        assertEquals(1000L to 2000L, MontageLogic.clampWindow(1000, 2000, 4000))
        // مدة صفريّة: نافذة فارغة آمنة
        assertEquals(0L to 0L, MontageLogic.clampWindow(0, 0, 0))
    }

    @Test
    fun `fade envelope ramps linearly and peaks at one`() {
        assertEquals(0f, MontageLogic.fadeGainAt(0, 2000, 500, 500))
        assertTrue(abs(MontageLogic.fadeGainAt(250, 2000, 500, 500) - 0.5f) < 0.01f)
        assertEquals(1f, MontageLogic.fadeGainAt(1000, 2000, 500, 500))
        assertTrue(abs(MontageLogic.fadeGainAt(1750, 2000, 500, 500) - 0.5f) < 0.01f)
        // بدون تلاشٍ المنصف كاملًا، والنافذة الصفرية صامتة
        assertEquals(1f, MontageLogic.fadeGainAt(1000, 2000, 0, 0))
        assertEquals(0f, MontageLogic.fadeGainAt(0, 0, 500, 500))
        // تلاشٍ أطول من النافذة يُحبس داخلها
        assertEquals(0.5f, MontageLogic.fadeGainAt(500, 1000, 5000, 0), 0.01f)
    }

    @Test
    fun `sanitize drops missing files and clamps values`() {
        val real = File(tmp, "real.wav").apply { writeBytes(ByteArray(10)) }
        val a = MontageClip(id = "a", path = "/x/a.wav", title = "a", durationMs = 4000, startMs = 1000, endMs = 3000)
        val gone = a.copy(id = "g", path = "missing.wav")
        val wild = a.copy(
            id = "w", path = real.path,
            volumePercent = 9999, fadeInMs = -10, fadeOutMs = 99_999,
            startMs = -7, endMs = 50_000
        )
        val out = MontageLogic.sanitize(listOf(gone, wild))
        assertEquals(1, out.size)
        assertEquals(200, out[0].volumePercent)
        assertEquals(0L, out[0].fadeInMs)
        assertEquals(10_000L, out[0].fadeOutMs)
        // النافذة تُحبس داخل مدة الملف المعلنة
        assertEquals(0L, out[0].startMs)
        assertEquals(4000L, out[0].endMs)
    }

    @Test
    fun `montage store roundtrip tolerates corruption`() {
        val f = File(tmp, "montage.json")
        val p = MontageProject(
            clips = listOf(
                MontageClip(id = "a", path = "/x/a.wav", title = "افتتاحية", durationMs = 4000, startMs = 500, endMs = 3500, volumePercent = 80, fadeInMs = 300)
            ),
            gapMs = 750, title = "مونتاج الحصة"
        )
        assertTrue(MontageStore(f).save(p))
        val back = MontageStore(f).project()
        assertEquals(750L, back.gapMs)
        assertEquals("افتتاحية", back.clips.first().title)
        assertEquals(80, back.clips.first().volumePercent)
        assertEquals(300L, back.clips.first().fadeInMs)
        // تلف الملف يعود بالمشروع الفارغ بلا انهيار
        f.writeText("{broken")
        assertEquals(0L, MontageStore(f).project().gapMs)
        assertTrue(MontageStore(f).project().clips.isEmpty())
    }

    // ================= التصيير =================

    /** قراءة عينة يسارية خام من WAV قانوني 44 بايت ترويسة. */
    private fun leftSampleAt(raw: ByteArray, frame: Int): Int {
        val off = 44 + frame * 4
        if (off + 1 >= raw.size) return 0
        val lo = raw[off].toInt() and 0xFF
        val hi = raw[off + 1].toInt()
        return (hi shl 8) or lo
    }

    private fun maxAbs(raw: ByteArray, fromFrame: Int, toFrame: Int): Int {
        var m = 0
        for (f in fromFrame until toFrame) m = maxOf(m, abs(leftSampleAt(raw, f)))
        return m
    }

    @Test
    fun `render trims windows with fades and gap into one canonical wav`() {
        val dir = File(tmp, "m1")
        val demo = MontageDemo.make(dir)
        val proj = MontageProject(
            clips = listOf(
                MontageClip(id = "a", path = demo[0].path, title = "a", durationMs = 4000, startMs = 1000, endMs = 3000, fadeInMs = 500, fadeOutMs = 500),
                MontageClip(id = "c", path = demo[2].path, title = "c", durationMs = 3000, startMs = 500, endMs = 2500)
            ),
            gapMs = 500
        )
        val out = File(dir, "out.wav")
        val progress = mutableListOf<Float>()
        val r = MontageRender.render(proj, out, onProgress = { progress.add(it) })
        assertTrue(r.ok) { r.message ?: "render failed" }
        val h = WavHeader.read(out) ?: error("montage header missing")
        assertEquals(44100, h.sampleRate)
        assertEquals(2, h.channels)
        assertEquals(16, h.bitsPerSample)
        // 2000 + 500 فاصل + 2000 = 4500 مللي ثانية (تسامح يدّي ضيق)
        assertTrue(abs(r.durationMs - 4500) <= 40) { "duration=${r.durationMs}" }
        assertTrue(progress.isNotEmpty() && progress.last() >= 0.99f) { "progress=${progress.lastOrNull()}" }
        val raw = out.readBytes()
        // تلاشٍ داخلي: أول 20 إطارًا قرب الصفر
        assertTrue(maxAbs(raw, 0, 20) < 400) { "head=${maxAbs(raw, 0, 20)}" }
        // الفاصل بين المقطعين (88200..110250) صمت رقمي
        assertTrue(maxAbs(raw, 88_400, 110_000) <= 4) { "gap=${maxAbs(raw, 88_400, 110_000)}" }
        // المقطع الثاني (22050 أحادي → ستيريو 44.1k) بكامل سعته تقريبًا
        assertTrue(maxAbs(raw, 112_000, 150_000) > 3000) { "late=${maxAbs(raw, 112_000, 150_000)}" }
    }

    @Test
    fun `render applies volume percent linearly`() {
        val dir = File(tmp, "m2")
        val full = MontageDemo.tone(File(dir, "full.wav"), 44100, 2, 4000, 440.0)
        val p100 = MontageProject(clips = listOf(MontageClip(id = "a", path = full.path, title = "a", durationMs = 4000)), gapMs = 0)
        val p50 = p100.copy(clips = listOf(MontageClip(id = "a", path = full.path, title = "a", durationMs = 4000, volumePercent = 50)))
        val o100 = File(dir, "o100.wav"); val o50 = File(dir, "o50.wav")
        assertTrue(MontageRender.render(p100, o100).ok)
        assertTrue(MontageRender.render(p50, o50).ok)
        val m100 = maxAbs(o100.readBytes(), 44_100, 88_200)
        val m50 = maxAbs(o50.readBytes(), 44_100, 88_200)
        assertTrue(m100 in 11_500..12_100) { "m100=$m100" }
        assertTrue(m50 in 5_700..6_100) { "m50=$m50" }
    }

    @Test
    fun `render resamples lower rate mono source to canonical stereo`() {
        val dir = File(tmp, "m3")
        val src = MontageDemo.tone(File(dir, "low.wav"), 22050, 1, 3000, 330.0)
        val proj = MontageProject(
            clips = listOf(MontageClip(id = "a", path = src.path, title = "a", durationMs = 3000)),
            gapMs = 0
        )
        val out = File(dir, "out.wav")
        val r = MontageRender.render(proj, out)
        assertTrue(r.ok) { r.message ?: "render" }
        val h = WavHeader.read(out) ?: error("resample header missing")
        assertEquals(44100, h.sampleRate)
        assertEquals(2, h.channels)
        assertTrue(abs(r.durationMs - 3000) <= 40) { "duration=${r.durationMs}" }
    }

    @Test
    fun `render skips missing clips and still exports the valid rest`() {
        val dir = File(tmp, "m4")
        val src = MontageDemo.tone(File(dir, "ok.wav"), 44100, 1, 3000, 440.0)
        val proj = MontageProject(
            clips = listOf(
                MontageClip(id = "gone", path = File(dir, "gone.wav").path, title = "g", durationMs = 1000),
                MontageClip(id = "ok", path = src.path, title = "o", durationMs = 3000, startMs = 0, endMs = 2000)
            ),
            gapMs = 250
        )
        val out = File(dir, "out.wav")
        val r = MontageRender.render(proj, out)
        assertTrue(r.ok) { r.message ?: "render" }
        // المفقود سقط: 2000 فقط بلا فواصل (فاصل واحد بين مقطعين لكن المفقود لم يصيَّر)
        assertTrue(abs(r.durationMs - 2000) <= 40) { "duration=${r.durationMs}" }
    }

    @Test
    fun `cancel and empty projects fail with clear arabic messages`() {
        val dir = File(tmp, "m5")
        val src = MontageDemo.tone(File(dir, "a.wav"), 44100, 1, 4000, 440.0)
        val proj = MontageProject(clips = listOf(MontageClip(id = "a", path = src.path, title = "a", durationMs = 4000)))
        val canceled = MontageRender.render(proj, File(dir, "c.wav"), cancelled = { true })
        assertTrue(!canceled.ok)
        assertTrue(canceled.message!!.contains("أُلغي"))
        val empty = MontageRender.render(MontageProject(), File(dir, "e.wav"))
        assertTrue(!empty.ok && empty.message != null)
        // المجلد غير الموجود يفشل برسالة عربية لا بانهيار (mkdirs قد ينجح إنشاءه)
        val bad = MontageRender.render(proj, File(dir, "bad-file/nonexistent/x.wav"))
        assertTrue(bad.ok || bad.message != null)
    }
}
