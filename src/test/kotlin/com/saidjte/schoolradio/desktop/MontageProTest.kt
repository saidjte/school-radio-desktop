package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageHistory
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageStore
import com.saidjte.schoolradio.desktop.ui.envAndBucketAt
import java.io.File
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * عقد الترقية الاحترافية لصفحة المونتاج (v0.34.0 — البنود 1–15):
 * - رياضيات الكروس فيد وقوته المتساوية ومغلّف مضاد النقرات — نقية ومحبوسة.
 * - التقسيم عند موضع وحساب التطبيع من الذروة الخام وسجل التراجع/الإعادة.
 * - الخلاط الكتلي: تداخل حقيقي بين المتجاورة بلا هبوط صمت عند الوصل.
 * - مغلّفات العرض (ويڤ فورم/VU/تشبع) من مصدر فكٍّ حقيقي بلا عتاد.
 * - توافق المخزن مع ملفات الإصدارات السابقة بلا فقدان.
 */
class MontageProTest {

    @TempDir
    lateinit var tmp: File

    private fun clip(
        id: String, dur: Long, start: Long = 0, end: Long = dur,
        vol: Int = 100, fi: Long = 0, fo: Long = 0
    ) = MontageClip(id = id, path = "/x/$id.wav", title = id, durationMs = dur,
        startMs = start, endMs = end, volumePercent = vol, fadeInMs = fi, fadeOutMs = fo)

    // ================= 1) رياضيات الخط الزمني والكروس فيد =================

    @Test
    fun `crossfade overlaps shrink the timeline and replace the gap`() {
        val a = clip("a", 3000)
        val b = clip("b", 3000)
        // بلا كروس فيد: 3000 + فاصل 500 + 3000 = 6500
        assertEquals(6500L, MontageLogic.totalDurationMs(listOf(a, b), 500, 0))
        // كروس فيد 1000: 3000 + 3000 − 1000 = 5000 — الفاصل يُغني عنه
        assertEquals(5000L, MontageLogic.totalDurationMs(listOf(a, b), 500, 1000))
        val starts = MontageLogic.clipStartMs(listOf(a, b), 500, 1000)
        assertEquals(listOf(0L, 2000L), starts)
        // ثلاثة مقاطع: كل حدّ يخصم تداخله
        val c = clip("c", 3000)
        assertEquals(7000L, MontageLogic.totalDurationMs(listOf(a, b, c), 0, 1000))
    }

    @Test
    fun `effective overlap never exceeds the shorter neighbour`() {
        assertEquals(1000L, MontageLogic.effectiveOverlap(3000, 3000, 1000))
        assertEquals(500L, MontageLogic.effectiveOverlap(500, 3000, 1000))
        assertEquals(0L, MontageLogic.effectiveOverlap(0, 3000, 1000))
        assertEquals(0L, MontageLogic.effectiveOverlap(3000, 3000, -5))
        // المقطع الملاصق لا ينكمش لصفر: التداخل محبوس بطول كل طرف
        assertEquals(3000L, MontageLogic.effectiveOverlap(3000, 9000, 9000))
    }

    @Test
    fun `equal power crossfade keeps constant energy across the seam`() {
        assertEquals(1f, MontageLogic.crossfadeOutGain(0f), 1e-4f)
        assertEquals(0f, MontageLogic.crossfadeOutGain(1f), 1e-4f)
        assertEquals(0f, MontageLogic.crossfadeInGain(0f), 1e-4f)
        assertEquals(1f, MontageLogic.crossfadeInGain(1f), 1e-4f)
        // قوة متساوية: مجموع مربعي الكسبين ≈ 1 في كل تقدّم — بلا هبوط مسموع
        for (step in 0..10) {
            val p = step / 10f
            val e = MontageLogic.crossfadeOutGain(p).toDouble().let { it * it } +
                    MontageLogic.crossfadeInGain(p).toDouble().let { it * it }
            assertEquals(1.0, e, 0.01, "p=$p")
        }
    }

    // ================= 2) التلاشي الدقيق مضاد النقرات =================

    @Test
    fun `micro fade ramps eight ms at every window edge`() {
        val ms = MontageLogic.MICRO_FADE_MS
        assertEquals(0f, MontageLogic.microFadeGainAt(0, 4000))
        assertEquals(0.5f, MontageLogic.microFadeGainAt(ms / 2, 4000), 0.01f)
        assertEquals(1f, MontageLogic.microFadeGainAt(ms + 1, 4000))
        // النهاية: منحدر 8ms — آخر ميلي ثانية عند ثُمن الكسب
        assertEquals(0.125f, MontageLogic.microFadeGainAt(3999, 4000), 0.001f)
        assertTrue(MontageLogic.microFadeGainAt(4000 - ms + 1, 4000) < 1f)
        // نافذة أقصر من التلاشي نفسه تبقى آمنة
        assertTrue(MontageLogic.microFadeGainAt(2, 4) in 0f..1f)
        assertEquals(0f, MontageLogic.microFadeGainAt(0, 0))
    }

    // ================= 3) التقسيم عند الرأس =================

    @Test
    fun `split divides window and distributes fades`() {
        val c = clip("s", 6000, start = 1000, end = 5000, vol = 80, fi = 400, fo = 600)
        val pair = MontageLogic.splitClipAt(c, 2000)
        assertNotNull(pair)
        val (a, b) = pair!!
        assertEquals(1000L to 3000L, a.startMs to a.endMs)
        assertEquals(3000L to 5000L, b.startMs to b.endMs)
        assertEquals(400L, a.fadeInMs)
        assertEquals(0L, a.fadeOutMs)
        assertEquals(0L, b.fadeInMs)
        assertEquals(600L, b.fadeOutMs)
        assertEquals(80, a.volumePercent)
        // المعرّفات جديدة ومختلفة
        assertTrue(a.id != b.id && a.id != c.id && b.id != c.id)
        // عند الحواف لا تقسيم
        assertNull(MontageLogic.splitClipAt(c, 0))
        assertNull(MontageLogic.splitClipAt(c, 4000))
        assertNull(MontageLogic.splitClipAt(c, -1))
    }

    // ================= 4) سجل التراجع/الإعادة =================

    @Test
    fun `history undo redo and cap behave like a pro editor`() {
        val h = MontageHistory(cap = 3)
        val p0 = MontageProject(title = "0")
        val p1 = p0.copy(title = "1")
        val p2 = p1.copy(title = "2")
        h.record(p0) // قبل التغيير إلى p1
        h.record(p1) // قبل التغيير إلى p2
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals("1", h.undo(p2)!!.title)
        assertTrue(h.canRedo)
        assertEquals("0", h.undo(p1)!!.title)
        assertFalse(h.canUndo)
        assertEquals("1", h.redo(p0)!!.title)
        assertEquals("2", h.redo(p1)!!.title)
        assertFalse(h.canRedo)
        // تسجيل جديد يمحو سلسلة الإعادة
        h.record(p2)
        assertFalse(h.canRedo)
        // السقف: أقدم لقطة تُنسى أولًا
        val h2 = MontageHistory(cap = 2)
        val a = MontageProject(title = "a")
        val b = a.copy(title = "b")
        val c = b.copy(title = "c")
        val d = c.copy(title = "d")
        h2.record(a); h2.record(b); h2.record(c)
        assertEquals("c", h2.undo(d)!!.title)
        assertEquals("b", h2.undo(c)!!.title)
        assertFalse(h2.canUndo)
        // pushCurrent لقطة سحبٍ يدوية
        val h3 = MontageHistory()
        val x = MontageProject(title = "x")
        h3.pushCurrent(x)
        assertEquals("x", h3.undo(x.copy(title = "y"))!!.title)
    }

    // ================= 5) التطبيع والتشبع والحلقة =================

    @Test
    fun `normalize maps raw peak to target within bounds`() {
        assertEquals(62, MontageLogic.normalizedVolumePercent(50_000)) // 31000*100/50000
        assertEquals(200, MontageLogic.normalizedVolumePercent(12_000)) // 258 ← محبوس 200
        assertEquals(1, MontageLogic.normalizedVolumePercent(3_100_000))
        assertEquals(100, MontageLogic.normalizedVolumePercent(0)) // صمت: بلا تغيير
        assertTrue(MontageLogic.isClippedLevel(32000f / 32768f))
        assertFalse(MontageLogic.isClippedLevel(30000f / 32768f))
    }

    @Test
    fun `loop tick returns to start only past a valid end`() {
        assertNull(MontageLogic.loopSeekMs(0, 1000, 2000))
        assertNull(MontageLogic.loopSeekMs(1999, 1000, 2000))
        assertEquals(1000L, MontageLogic.loopSeekMs(2000, 1000, 2000))
        assertEquals(1000L, MontageLogic.loopSeekMs(5000, 1000, 2000))
        // حلقة مبتورة لا تُنفَّذ
        assertNull(MontageLogic.loopSeekMs(5000, 2000, 2000))
        assertNull(MontageLogic.loopSeekMs(5000, 3000, 2000))
    }

    // ================= 6) التصيير: تداخل حقيقي وتلاشي دقيق =================

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
    fun `render mixes a real crossfade between neighbours`() {
        val dir = File(tmp, "xf")
        val t1 = MontageDemo.tone(File(dir, "a.wav"), 44100, 2, 3000, 440.0)
        val t2 = MontageDemo.tone(File(dir, "b.wav"), 44100, 2, 3000, 330.0)
        val proj = MontageProject(
            clips = listOf(
                MontageClip(id = "a", path = t1.path, title = "a", durationMs = 3000),
                MontageClip(id = "b", path = t2.path, title = "b", durationMs = 3000)
            ),
            gapMs = 500, crossfadeMs = 1000
        )
        val out = File(dir, "out.wav")
        val r = MontageRender.render(proj, out)
        assertTrue(r.ok) { r.message ?: "render" }
        // 3000 + 3000 − 1000 = 5000 بلا فاصل صمت رغم gapMs
        assertTrue(abs(r.durationMs - 5000) <= 40) { "duration=${r.durationMs}" }
        val raw = out.readBytes()
        val totalFrames = (r.durationMs * 44100L / 1000L).toInt()
        // منطقة التداخل 2000..3000: كلا المقطعين حاضران بقوة متساوية
        val overlapMax = maxAbs(raw, 2000 * 44100 / 1000, 3000 * 44100 / 1000)
        assertTrue(overlapMax > 8000) { "overlapMax=$overlapMax" }
        // لا صمت طولي عند الوصل: القوة لا تنهار تحت نصف الذروة لكتلة كاملة
        var silentRun = 0
        var worst = 0
        for (f in 2000 * 44100 / 1000 until 3000 * 44100 / 1000) {
            if (abs(leftSampleAt(raw, f)) < 500) silentRun++ else { worst = maxOf(worst, silentRun); silentRun = 0 }
        }
        worst = maxOf(worst, silentRun)
        assertTrue(worst < 441) { "silent run frames=$worst" } // أقل من 10ms تواليًا
        // النهاية تخص المقطع الثاني وحده
        assertTrue(maxAbs(raw, totalFrames - 44100, totalFrames - 10) > 8000)
    }

    @Test
    fun `render applies micro fades to hard cuts removing clicks`() {
        val dir = File(tmp, "micro")
        val t = MontageDemo.tone(File(dir, "a.wav"), 44100, 2, 4000, 440.0)
        val proj = MontageProject(
            clips = listOf(MontageClip(id = "a", path = t.path, title = "a", durationMs = 4000)),
            gapMs = 0
        )
        val out = File(dir, "out.wav")
        val r = MontageRender.render(proj, out)
        assertTrue(r.ok) { r.message ?: "render" }
        val raw = out.readBytes()
        val totalFrames = 4L * 44100L
        // أول إطارات النافذة الصلبة تصعد من الصفر خلال 8ms (352 إطارًا)
        assertTrue(maxAbs(raw, 0, 4) < 500) { "head=${maxAbs(raw, 0, 4)}" }
        assertTrue(maxAbs(raw, 0, 100) < 6000) { "ramp=${maxAbs(raw, 0, 100)}" }
        // والنهاية تهبط بلا قفزة مفاجئة
        assertTrue(maxAbs(raw, (totalFrames - 2).toInt(), totalFrames.toInt()) < 2500)
        // والوسط بكامل قوته
        assertTrue(maxAbs(raw, 44100, 88200) > 11000)
    }

    // ================= 7) مغلّفات العرض والذروة الخام =================

    @Test
    fun `envelope buckets expose peaks and clipping zones`() {
        val dir = File(tmp, "env")
        // نغمة عالية السعة مع مستوى 200٪ تكسر عتبة التشبع
        val loud = File(dir, "loud.wav")
        loud.parentFile?.mkdirs()
        val frames = 2 * 44100
        val w = WavFileWriter(loud, 44100, 2)
        val buf = ByteArray(8192 * 4)
        var frame = 0
        while (frame < frames) {
            val n = minOf(8192, frames - frame)
            var bi = 0
            for (i in 0 until n) {
                val v = (Math.sin(2.0 * Math.PI * 440.0 * (frame + i) / 44100.0) * 20000.0).toInt()
                buf[bi++] = (v and 0xFF).toByte(); buf[bi++] = ((v shr 8) and 0xFF).toByte()
                buf[bi++] = (v and 0xFF).toByte(); buf[bi++] = ((v shr 8) and 0xFF).toByte()
            }
            w.write(buf, 0, bi)
            frame += n
        }
        w.close()
        val clip = MontageClip(id = "e", path = loud.path, title = "e", durationMs = 2000, volumePercent = 200)
        val env = MontageRender.computeEnvelope(clip, 100)
        assertEquals(100, env.peaks.size)
        val maxP = env.maxPeak()
        // 20000 × 2.0 = 40000 > 32768 — التشبع محسوس في المغلّف
        assertTrue(maxP > 0.97f) { "maxPeak=$maxP" }
        assertTrue(env.clippedAt(50)) { "expected clipping zone mid-bucket" }
        // الدلاء خارج النطاق صامتة بأمان
        assertEquals(0f, env.peakAt(-1))
        assertEquals(0f, env.peakAt(999))
        // الذروة الخام بلا كسب: ≈ 20000
        val raw = MontageRender.computeRawPeak(clip)
        assertTrue(raw in 19_000..20_500) { "rawPeak=$raw" }
        // مغلّف مقطع مفقود: صامت بلا انهيار
        val gone = MontageRender.computeEnvelope(clip.copy(path = "/x/missing.wav"), 50)
        assertEquals(0f, gone.maxPeak())
    }

    @Test
    fun `envelope lookup maps playhead to the owning clip bucket`() {
        val a = clip("a", 3000)
        val b = clip("b", 2000)
        val proj = MontageProject(clips = listOf(a, b), gapMs = 0, crossfadeMs = 0)
        val envA = com.saidjte.schoolradio.desktop.data.MontageEnvelope("a", FloatArray(100) { 0.25f }, BooleanArray(100))
        val envB = com.saidjte.schoolradio.desktop.data.MontageEnvelope("b", FloatArray(50) { 0.5f }, BooleanArray(50))
        val map = mapOf("a" to envA, "b" to envB)
        val hitA = envAndBucketAt(proj, 1500, map)!!
        assertEquals("a", hitA.first.clipId)
        assertEquals(50, hitA.second)
        val hitB = envAndBucketAt(proj, 4000, map)!! // 3000 داخل b → 1000ms من 2000
        assertEquals("b", hitB.first.clipId)
        assertEquals(25, hitB.second)
        assertNull(envAndBucketAt(proj, 999_999, map))
        assertNull(envAndBucketAt(proj, 500, mapOf())) // بلا مغلّف بعد
    }

    // ================= 8) توافق المخزن =================

    @Test
    fun `store roundtrip keeps new fields and tolerates old files`() {
        val f = File(tmp, "m.json")
        val p = MontageProject(
            clips = listOf(clip("a", 4000, vol = 150)),
            gapMs = 250, crossfadeMs = 750, title = "ت", playheadMs = 1234
        )
        assertTrue(MontageStore(f).save(p))
        val back = MontageStore(f).project()
        assertEquals(750L, back.crossfadeMs)
        assertEquals(1234L, back.playheadMs)
        assertEquals(250L, back.gapMs)
        // ملف إصدار سابق بلا الحقول الجديدة: قيم افتراضية آمنة
        val old = File(tmp, "old.json")
        old.writeText("""{"clips":[{"id":"a","path":"/x/a.wav","title":"a","durationMs":4000,"startMs":0,"endMs":4000}],"gapMs":500,"title":"قديم"}""")
        val legacy = MontageStore(old).project()
        assertEquals(0L, legacy.crossfadeMs)
        assertEquals(0L, legacy.playheadMs)
        assertEquals(500L, legacy.gapMs)
        assertEquals(1, legacy.clips.size)
    }
}
