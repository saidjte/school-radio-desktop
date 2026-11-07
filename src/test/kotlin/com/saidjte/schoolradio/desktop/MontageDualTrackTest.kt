package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMarker
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageStore
import com.saidjte.schoolradio.desktop.data.MontageTemplateCatalog
import com.saidjte.schoolradio.desktop.data.MontageTemplatesLogic
import java.io.File
import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * عقد المسار المزدوج والخفض التلقائي والعلامات والقوالب (v0.35.0 — البنود 16–17):
 * - منحنى الخفض النقي: عمق داخل النطق وهجوم/إفلات خطيان وأعمق النطاقات يفوز.
 * - نطاقات النطق تندمج حيث تتلاصق المقاطع (بلا فاصل أو بكروس فيد).
 * - الخلاط الكتلي: سرير موسيقي حقيقي يُمزج على موضعه المطلق ويُخفض تحت التعليق
 *   بعينات خام — والتعطيل يعيد الوحدة.
 * - العلامات المسمّاة وقوالبها الجاهزة وتوافق المخزن مع ملفات الإصدارات السابقة.
 */
class MontageDualTrackTest {

    @TempDir
    lateinit var tmp: File

    private fun clip(id: String, dur: Long) = MontageClip(
        id = id, path = "/x/$id.wav", title = id, durationMs = dur, startMs = 0, endMs = dur
    )

    private fun music(
        id: String, dur: Long, start: Long,
        sIn: Long = 0, eIn: Long = dur, vol: Int = 100
    ) = MontageMusicClip(
        id = id, path = "/x/$id.wav", title = id, durationMs = dur,
        startMs = start, startInFileMs = sIn, endInFileMs = eIn, volumePercent = vol
    )

    // ================= 1) نطاقات النطق =================

    @Test
    fun `narration intervals merge touching clips and split on gaps`() {
        val a = clip("a", 3000)
        val b = clip("b", 2000)
        // فاصل صامت 500 → نطاقان
        assertEquals(listOf(0L to 3000L, 3500L to 5500L),
            MontageLogic.narrationIntervals(listOf(a, b), 500, 0))
        // متلاصقان (بلا فاصل) → نطاق واحد متصل
        assertEquals(listOf(0L to 5000L),
            MontageLogic.narrationIntervals(listOf(a, b), 0, 0))
        // كروس فيد يدمجهما متداخلين → نطاق واحد
        assertEquals(listOf(0L to 4000L),
            MontageLogic.narrationIntervals(listOf(a, b), 500, 1000))
        // القائمة الفارغة بلا نطاقات
        assertTrue(MontageLogic.narrationIntervals(emptyList(), 0, 0).isEmpty())
    }

    // ================= 2) منحنى الخفض النقي =================

    @Test
    fun `duck gain follows narration with linear attack and release`() {
        val intervals = listOf(1000L to 3000L)
        val duck = MontageLogic.dbToAmp(-12.0)
        // قبل الهجوم: وحدة كاملة
        assertEquals(1f, MontageLogic.duckGainAt(500, intervals, -12, 300, 800))
        // وسط الهجوم: منتصف المنحدر بين الوحدة والعمق
        val midAttack = MontageLogic.duckGainAt(850, intervals, -12, 300, 800)
        assertTrue(abs(midAttack - (duck + (1f - duck) * 0.5f)) < 0.01f) { "midAttack=$midAttack" }
        // داخل النطق: عمق الخفض
        assertEquals(duck, MontageLogic.duckGainAt(2000, intervals, -12, 300, 800), 1e-4f)
        // وسط الإفلات
        val midRelease = MontageLogic.duckGainAt(3400, intervals, -12, 300, 800)
        assertTrue(abs(midRelease - (duck + (1f - duck) * 0.5f)) < 0.01f) { "midRelease=$midRelease" }
        // بعد الإفلات: وحدة كاملة
        assertEquals(1f, MontageLogic.duckGainAt(3801, intervals, -12, 300, 800))
        // الحدود: لحظة البداية والنهاية داخل النطاق
        assertEquals(duck, MontageLogic.duckGainAt(1000, intervals, -12, 300, 800), 1e-4f)
        assertEquals(duck, MontageLogic.duckGainAt(2999, intervals, -12, 300, 800), 1e-4f)
    }

    @Test
    fun `closer intervals keep the deepest duck without bouncing`() {
        val a = 1000L to 2000L
        val b = 2600L to 3600L // الإفلات الأول لم يكتمل قبل الهجوم الثاني
        val duck = MontageLogic.dbToAmp(-12.0)
        // بين النطاقين (تداخل إفلات/هجوم): الأعمق يفوز — لا قفزة عودة وهمية
        val between = MontageLogic.duckGainAt(2300, listOf(a, b), -12, 300, 800)
        assertTrue(between < duck + (1f - duck) * 0.5f) { "between=$between" }
        assertTrue(between >= duck) { "between=$between" }
    }

    @Test
    fun `disabled ducking or empty narration yields unity gain`() {
        val intervals = listOf(0L to 1000L)
        assertEquals(1f, MontageLogic.duckGainAt(500, intervals, -12, 300, 800, enabled = false))
        assertEquals(1f, MontageLogic.duckGainAt(500, emptyList(), -12, 300, 800))
        // حبس القيم
        assertEquals(-30, MontageLogic.clampDuckDb(-99))
        assertEquals(-3, MontageLogic.clampDuckDb(0))
        assertEquals(50L, MontageLogic.clampDuckAttack(1))
        assertEquals(5000L, MontageLogic.clampDuckRelease(99_999))
    }

    // ================= 3) مدد المسار المزدوج =================

    @Test
    fun `music bed can extend the timeline beyond narration`() {
        val proj = MontageProject(
            clips = listOf(clip("a", 3000)),
            musicClips = listOf(music("m", 10_000, start = 0))
        )
        assertEquals(10_000L, MontageLogic.totalDurationFull(proj))
        // موسيقى تبدأ متأخرة تمتد أكثر
        val late = proj.copy(musicClips = listOf(music("m", 10_000, start = 8_000)))
        assertEquals(18_000L, MontageLogic.totalDurationFull(late))
        // بلا موسيقى: زمن التعليق وحده
        assertEquals(3000L, MontageLogic.totalDurationFull(proj.copy(musicClips = emptyList())))
        // النافذة الفارغة تعني الملف كاملًا
        assertEquals(10_000L, MontageLogic.musicDurationMs(music("m", 10_000, start = 0)))
        assertEquals(4_000L, MontageLogic.musicDurationMs(music("m", 10_000, start = 0, sIn = 2000, eIn = 6000)))
    }

    @Test
    fun `music source clip maps the window like the renderer`() {
        val m = music("m", 10_000, start = 500, sIn = 2000, eIn = 6000, vol = 80)
        val src = MontageRender.musicAsSourceClip(m)
        assertEquals("m", src.id)
        assertEquals(2000L to 6000L, src.startMs to src.endMs)
        assertEquals(80, src.volumePercent)
        // النافذة الفارغة → الملف كاملًا
        val full = MontageRender.musicAsSourceClip(music("n", 4000, start = 0))
        assertEquals(0L to 4000L, full.startMs to full.endMs)
    }

    // ================= 4) التعقيم =================

    @Test
    fun `sanitize music drops missing files and clamps values`() {
        val realFile = tmp.resolve("real.wav")
        realFile.writeBytes(ByteArray(4)) // موجود على القرص كي ينجو من التعقيم
        val ok = music("m", 10_000, start = -500, sIn = 100, eIn = 99_000, vol = 500)
            .copy(path = realFile.absolutePath)
        val gone = music("g", 1000, start = 0)
        val out = MontageLogic.sanitizeMusic(listOf(ok, gone))
        assertEquals(1, out.size)
        val m = out.first()
        assertEquals(0L, m.startMs) // السالب يُقص
        assertEquals(100L to 10_000L, m.startInFileMs to m.endInFileMs)
        assertEquals(200, m.volumePercent)
        // سقف المسار الثاني
        val many = (1..20).map { music("m$it", 1000, start = 0).copy(path = ok.path) }
        assertEquals(MontageLogic.MAX_MUSIC_CLIPS, MontageLogic.sanitizeMusic(many).size)
    }

    @Test
    fun `markers sanitize drops empty names caps count and clamps time`() {
        val ok = MontageMarker("1", "  الفاصل ", -5)
        val empty = MontageMarker("2", "   ", 100)
        val out = MontageLogic.sanitizeMarkers(listOf(ok, empty))
        assertEquals(1, out.size)
        assertEquals("الفاصل", out.first().name)
        assertEquals(0L, out.first().tMs)
        val many = (1..50).map { MontageMarker("$it", "ع$it", it.toLong()) }
        assertEquals(MontageLogic.MAX_MARKERS, MontageLogic.sanitizeMarkers(many).size)
    }

    // ================= 5) القوالب الجاهزة =================

    @Test
    fun `template apply generates markers and recommended settings keeping clips`() {
        val proj = MontageProject(
            clips = listOf(clip("a", 3000)),
            gapMs = 1000, crossfadeMs = 0,
            markers = listOf(MontageMarker("old", "قديم", 5))
        )
        val t = MontageTemplateCatalog.byId("full-break")!!
        val out = MontageTemplatesLogic.apply(proj, t)
        assertEquals(t.markers.size, out.markers.size)
        assertEquals(t.markers.map { it.first }, out.markers.map { it.name })
        assertEquals(listOf(0L, 15_000L, 60_000L, 180_000L, 270_000L), out.markers.map { it.tMs })
        assertEquals(400L, out.gapMs)
        assertEquals(0L, out.crossfadeMs)
        assertEquals(-12, out.duckDb)
        // المقاطع والمعرّفات القديمة للعلامات لم تُنسخ — معرّفات جديدة فريدة
        assertEquals(1, out.clips.size)
        assertTrue(out.markers.map { it.id }.toSet().size == out.markers.size)
        // القوالب الأربعة سليمة التعريف
        assertEquals(4, MontageTemplateCatalog.all.size)
        assertTrue(MontageTemplateCatalog.all.all { it.markers.isNotEmpty() && it.name.isNotBlank() })
    }

    // ================= 6) التصيير: سرير موسيقي حقيقي بخفض فعلي =================

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
    fun `render mixes music bed ducked under narration and silent beyond it`() {
        val dir = File(tmp, "dual")
        // تعليق خافت (4000) وسرير عالٍ (12000) كي نعزل أثر الخفض على السرير:
        // أثناء النطق = تعليق 4000 + سرير مخفوض 3000، وبعد الإفلات = سرير 12000 وحده
        val voice = tone(File(dir, "voice.wav"), 44100, 2, 3000, 440.0, 4000)
        val bed = MontageDemo.tone(File(dir, "bed.wav"), 44100, 2, 8000, 330.0)
        val proj = MontageProject(
            clips = listOf(
                MontageClip(id = "v", path = voice.path, title = "v", durationMs = 3000)
            ),
            musicClips = listOf(
                MontageMusicClip(
                    id = "b", path = bed.path, title = "b", durationMs = 8000,
                    startMs = 0, volumePercent = 100
                )
            ),
            duckingEnabled = true, duckDb = -12,
            duckAttackMs = 200, duckReleaseMs = 400
        )
        val out = File(dir, "out.wav")
        val r = MontageRender.render(proj, out)
        assertTrue(r.ok) { r.message ?: "render" }
        // المدة = أبعدها بين المسارين: 8 ثوانٍ من السرير
        assertTrue(abs(r.durationMs - 8000) <= 40) { "duration=${r.durationMs}" }
        val raw = out.readBytes()
        val fs = 44100L
        // داخل النطق: السرير حاضر لكن مخفوض تحت التعليق
        val during = maxAbs(raw, (600 * fs / 1000).toInt(), (2800 * fs / 1000).toInt())
        // بعد الإفلات: السرير وحده بكامل قوته
        val after = maxAbs(raw, (3600 * fs / 1000).toInt(), (7900 * fs / 1000).toInt())
        assertTrue(during > 2000) { "during=$during" } // السرير حاضر لا مكتوم تمامًا
        assertTrue(during < 8000) { "during=$during" } // مخفوض بعمق −12dB
        assertTrue(after > 11000) { "after=$after" } // عاد بكامل سعته
        // التعطيل يعيد الوحدة: السرير كامل القوة حتى أثناء النطق
        val off = File(dir, "off.wav")
        val r2 = MontageRender.render(proj.copy(duckingEnabled = false), off)
        assertTrue(r2.ok && abs(r2.durationMs - 8000) <= 40)
        val raw2 = off.readBytes()
        val duringOff = maxAbs(raw2, (600 * fs / 1000).toInt(), (2800 * fs / 1000).toInt())
        assertTrue(duringOff > during + 5000) { "duringOff=$duringOff during=$during" }
    }

    /** موجة جيبية بسعة مضبوطة — لاختبار الخفض بعزل مسار عن آخر. */
    private fun tone(file: File, rate: Int, channels: Int, ms: Long, hz: Double, amp: Int): File {
        file.parentFile?.mkdirs()
        val frames = (ms * rate / 1000L).toInt()
        val w = com.saidjte.schoolradio.desktop.audio.WavFileWriter(file, rate, channels)
        val buf = ByteArray(8192 * 2 * 2)
        var frame = 0
        while (frame < frames) {
            val n = minOf(8192, frames - frame)
            var bi = 0
            for (i in 0 until n) {
                val v = (Math.sin(2.0 * Math.PI * hz * (frame + i) / rate) * amp).toInt()
                val samples = if (channels == 2) intArrayOf(v, v) else intArrayOf(v)
                for (s in samples) {
                    buf[bi++] = (s and 0xFF).toByte()
                    buf[bi++] = ((s shr 8) and 0xFF).toByte()
                }
            }
            w.write(buf, 0, bi)
            frame += n
        }
        w.close()
        return file
    }

    @Test
    fun `render accepts a music-only project`() {
        val dir = File(tmp, "musiconly")
        val bed = MontageDemo.tone(File(dir, "bed.wav"), 22050, 1, 2000, 440.0)
        val proj = MontageProject(
            musicClips = listOf(
                MontageMusicClip(id = "b", path = bed.path, title = "b", durationMs = 2000)
            )
        )
        val out = File(dir, "out.wav")
        val r = MontageRender.render(proj, out)
        assertTrue(r.ok) { r.message ?: "render" }
        assertTrue(abs(r.durationMs - 2000) <= 40) { "duration=${r.durationMs}" }
        assertTrue(maxAbs(out.readBytes(), 0, 1000) > 3000)
        // مشروع فارغ تمامًا يفشل برسالة عربية
        val rf = MontageRender.render(MontageProject(), File(dir, "empty.wav"))
        assertFalse(rf.ok)
    }

    // ================= 7) توافق المخزن =================

    @Test
    fun `store roundtrip keeps dual track fields and tolerates legacy files`() {
        val f = File(tmp, "m.json")
        val p = MontageProject(
            clips = listOf(clip("a", 3000)),
            musicClips = listOf(music("m", 8000, start = 500)),
            duckingEnabled = false, duckDb = -15,
            duckAttackMs = 200, duckReleaseMs = 900,
            markers = listOf(MontageMarker("k1", "النشيد", 12_000))
        )
        assertTrue(MontageStore(f).save(p))
        val back = MontageStore(f).project()
        assertEquals(1, back.musicClips.size)
        assertEquals(500L, back.musicClips.first().startMs)
        assertFalse(back.duckingEnabled)
        assertEquals(-15, back.duckDb)
        assertEquals(900L, back.duckReleaseMs)
        assertEquals("النشيد", back.markers.first().name)
        // ملف إصدار سابق بلا حقول المسار المزدوج: افتراضيات آمنة
        val old = File(tmp, "old.json")
        old.writeText("""{"clips":[{"id":"a","path":"/x/a.wav","title":"a","durationMs":3000,"startMs":0,"endMs":3000}],"gapMs":500,"title":"قديم"}""")
        val legacy = MontageStore(old).project()
        assertTrue(legacy.musicClips.isEmpty() && legacy.markers.isEmpty())
        assertTrue(legacy.duckingEnabled && legacy.duckDb == MontageLogic.DUCK_DB_DEFAULT)
    }
}
