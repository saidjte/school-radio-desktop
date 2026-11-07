package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMarker
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageTool
import com.saidjte.schoolradio.desktop.data.MontageTrack
import java.io.File
import javax.sound.sampled.AudioSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * عقد v0.36.0 «المونتاج متعدد المسارات ومسطرة العمليات»:
 * - المسارات الفعّالة وهجرة مشاريع v0.35.0 القديمة دون مساس ملفها.
 * - كسب المسار: الكتم والمنفرد بوصفهما عقد وحدة المزج الإذاعي.
 * - التقسيم على مقاطع المسارات والتقليم المُحبوس.
 * - حذف منطقة المسطرة متموّجًا: التعليق والمسارات والعلامات والرأس والتحديد.
 * - التقاط على العلامات والحواف والشبكة.
 * - التصيير الفعلي بعدة مسارات: المدة أبعدها، وأعلام الكتم/المنفرد القديمة تُهرَج (منذ v0.37.0).
 */
class MontageMultiTrackTest {

    @TempDir
    lateinit var tmp: File

    private fun clip(id: String, dur: Long) = MontageClip(
        id = id, path = "/x/$id.wav", title = id, durationMs = dur, startMs = 0, endMs = dur
    )

    private fun tclip(
        id: String, dur: Long, start: Long, trackId: String = MontageLogic.DEFAULT_TRACK_ID,
        sIn: Long = 0, eIn: Long = dur, vol: Int = 100
    ) = MontageMusicClip(
        id = id, path = "/x/$id.wav", title = id, durationMs = dur,
        startMs = start, startInFileMs = sIn, endInFileMs = eIn,
        volumePercent = vol, trackId = trackId
    )

    private fun project(
        clips: List<MontageClip> = emptyList(),
        trackClips: List<MontageMusicClip> = emptyList(),
        tracks: List<MontageTrack> = emptyList(),
        markers: List<MontageMarker> = emptyList(),
        playheadMs: Long = 0L,
        gapMs: Long = 0L
    ) = MontageProject(
        clips = clips, musicClips = trackClips, tracks = tracks,
        markers = markers, playheadMs = playheadMs, gapMs = gapMs
    )

    // ================= 1) المسارات الفعّالة والهجرة =================

    @Test
    fun `legacy project with no tracks migrates to default music track`() {
        val p = project(trackClips = listOf(tclip("m1", 4000, 0L)))
        val tracks = MontageLogic.effectiveTracks(p)
        assertEquals(1, tracks.size)
        assertEquals(MontageLogic.DEFAULT_TRACK_ID, tracks[0].id)
        assertEquals(MontageLogic.DEFAULT_TRACK_NAME, tracks[0].name)
        // مقطع بلا trackId يعيش على المسار الافتراضي
        assertEquals(MontageLogic.DEFAULT_TRACK_ID, MontageLogic.effectiveTrackId(p.musicClips[0]))
        assertEquals(listOf("m1"), MontageLogic.clipsOfTrack(p, MontageLogic.DEFAULT_TRACK_ID).map { it.id })
    }

    @Test
    fun `explicit tracks are clamped and unknown ids fall back`() {
        val p = project(
            tracks = listOf(
                MontageTrack(id = "t1", name = "  ", colorIndex = 7, volumePercent = 5000),
                MontageTrack(id = "t2", name = "مؤثرات", colorIndex = -1, volumePercent = 30)
            ),
            trackClips = listOf(tclip("m1", 4000, 0L, trackId = "t1"), tclip("m2", 2000, 500L, trackId = "ghost"))
        )
        val tracks = MontageLogic.effectiveTracks(p)
        assertEquals(2, tracks.size)
        assertEquals(MontageLogic.DEFAULT_TRACK_NAME, tracks[0].name) // الاسم الفارغ يرجع للاسم الافتراضي
        assertEquals(MontageLogic.VOLUME_MAX, tracks[0].volumePercent) // 5000 تحبس إلى 200
        assertEquals(MontageLogic.TRACK_COLORS.size - 1, tracks[1].colorIndex) // -1 يلف إلى آخر لون
        // مقطع بمسار مجهول يعود للمسار الأول الفعّال
        assertEquals("t1", MontageLogic.trackById(tracks, "ghost").id)
        assertEquals(listOf("m2"), MontageLogic.clipsOfTrack(p, "ghost").map { it.id })
    }

    // ================= 2) كسب المسار المرجعي (أعلام الملفات القديمة) =================

    @Test
    fun `track gain honors mute and solo like a mixing console`() {
        // الرياضيات المرجعية لوحدة المزج — المسارات الفعّالة تُهرج الأعلام قبلها
        // منذ v0.37.0 فتعود دائمًا المستوى (انظر MontageV37Test)
        val t = MontageTrack(id = "t1", name = "موسيقى", volumePercent = 150)
        assertEquals(150, MontageLogic.trackGainPercent(t, anySolo = false))
        assertEquals(0, MontageLogic.trackGainPercent(t.copy(muted = true), anySolo = false))
        assertEquals(0, MontageLogic.trackGainPercent(t, anySolo = true)) // غير منفرد وسط منفرد
        assertEquals(150, MontageLogic.trackGainPercent(t.copy(solo = true), anySolo = true))
        assertTrue(MontageLogic.anyTrackSolo(listOf(t.copy(solo = true))))
        assertFalse(MontageLogic.anyTrackSolo(listOf(t)))
    }

    // ================= 3) تقسيم مقاطع المسارات والتقليم =================

    @Test
    fun `track clip split yields two adjacent halves with clean fades`() {
        val m = tclip("m1", dur = 10_000, start = 1_000, sIn = 500, eIn = 9_500)
        val (a, b) = MontageLogic.splitTrackClipAt(m, 2_000)!!
        // النصف الأيسر: نافذته تنتهي عند نقطة الشطر وتحمل التلاشي الداخلي
        assertEquals(500L, a.startInFileMs)
        assertEquals(2_500L, a.endInFileMs)
        assertEquals(0f, a.fadeOutMs.toFloat())
        assertEquals(1_000L, a.startMs)
        // النصف الأيمن: يبدأ من نقطة الشطر داخل الملف وعلى الخط معًا
        assertEquals(2_500L, b.startInFileMs)
        assertEquals(9_500L, b.endInFileMs)
        assertEquals(3_000L, b.startMs)
        assertEquals(0f, b.fadeInMs.toFloat())
        assertEquals(2_000L, MontageLogic.musicDurationMs(a))
        assertEquals(7_000L, MontageLogic.musicDurationMs(b))
        // خارج النافذة لا تقسيم
        assertNull(MontageLogic.splitTrackClipAt(m, 0))
        assertNull(MontageLogic.splitTrackClipAt(m, 9_000)) // عند نهاية النافذة
        assertNull(MontageLogic.splitTrackClipAt(m, -1))
    }

    @Test
    fun `track clip trims stay inside the source file`() {
        val m = tclip("m1", dur = 6_000, start = 0, sIn = 1_000, eIn = 5_000)
        // تقليم اليسرى: الموضع والنافذة يزحفان معًا
        val l = MontageLogic.trimTrackClipLeft(m, 800)
        assertEquals(1_800L, l.startInFileMs)
        assertEquals(5_000L, l.endInFileMs)
        assertEquals(800L, l.startMs)
        // محبوس داخل بداية الملف
        assertEquals(0L, MontageLogic.trimTrackClipLeft(m, -99_999).startInFileMs)
        // تقليم اليمنى: النافذة تتمد إلى نهاية الملف لا بعدها
        val r = MontageLogic.trimTrackClipRight(m, 2_000)
        assertEquals(6_000L, r.endInFileMs)
        assertEquals(0L, r.startMs)
        // أدنى مدة حية محفوظة
        assertEquals(MontageLogic.MIN_CLIP_MS,
            MontageLogic.musicDurationMs(MontageLogic.trimTrackClipRight(m, -99_999)))
    }

    // ================= 4) حذف منطقة المسطرة متموّجًا =================

    @Test
    fun `region ripple delete removes and pulls narration`() {
        val a = clip("a", 3_000) // [0, 3000)
        val b = clip("b", 2_000) // [3000, 5000) — فاصل 0
        val c = clip("c", 4_000) // [5000, 9000)
        val np = MontageLogic.deleteRegionRipple(
            project(clips = listOf(a, b, c), gapMs = 0, playheadMs = 6_000), 3_200, 5_600
        )!!
        // «b» داخل المنطقة كليًا: قُتل، و«a» قبله سليم، و«c» بعده احتفظ بذيله —
        // والمنطقة (2400ms) أُغلقت من الخط كله: 9000 ← 6600
        assertEquals(3, np.clips.size)
        assertEquals(6_600L, MontageLogic.totalDurationMs(np.clips, np.gapMs, np.crossfadeMs))
        // الرأس بعد المنطقة سُحب بمقدار القطع (2400)
        assertEquals(3_600L, np.playheadMs)
    }

    @Test
    fun `region ripple delete trims straddling clips and splits through-clippers`() {
        val a = clip("a", 5_000) // [0, 5000)
        val b = clip("b", 5_000) // [5000, 10000)
        val np = MontageLogic.deleteRegionRipple(
            project(clips = listOf(a, b), gapMs = 0), 2_000, 7_000
        )!!
        // «a» عابر للحد الأول: يُقص إلى [0, 2000) والخارجي يصفَر
        val a2 = np.clips.first { it.id.startsWith("c") && it.endMs == 2_000L }
        assertEquals(0L, a2.startMs)
        assertEquals(0L, a2.fadeOutMs)
        // «b» عابر للحد الثاني: يحتفظ بذيله [7000, 10000) فيصير نافذته [2000, 5000)
        val b2 = np.clips.first { it.startMs == 2_000L && it.id != a2.id }
        assertEquals(2_000L, b2.startMs)
        assertEquals(5_000L, b2.endMs)
        assertEquals(0L, b2.fadeInMs)
        assertEquals(2, np.clips.size)
    }

    @Test
    fun `region ripple delete handles free track clips with absolute positions`() {
        // المنطقة [1500, 4500) بطول 3000
        val before = tclip("before", 1_000, start = 0)                    // [0,1000) قبلها — يبقى
        val inside = tclip("inside", 1_000, start = 2_000)                // [2000,3000) داخلها — يُقتل
        val straddle = tclip("straddle", 5_000, start = 1_000, sIn = 0, eIn = 5_000) // [1000,6000) عابرة — تنشطر
        val after = tclip("after", 2_000, start = 5_000)                  // [5000,7000) بعدها — تُسحب 3000
        val np = MontageLogic.deleteRegionRipple(
            project(trackClips = listOf(before, inside, straddle, after), tracks = listOf(
                MontageTrack(id = MontageLogic.DEFAULT_TRACK_ID, name = "موسيقى")
            )), 1_500, 4_500
        )!!
        val byId = { id: String -> np.musicClips.filter { it.title == id }.sortedBy { it.startMs } }
        // المقتول لا يعود، والسابق بقى، واللاحق انسحب 3000
        assertTrue(byId("inside").isEmpty())
        assertEquals(1, byId("before").size)
        assertEquals(0L, byId("before")[0].startMs)
        assertEquals(2_000L, byId("after")[0].startMs)
        assertEquals(0L, byId("after")[0].startInFileMs)
        // العابر ينشطر: يسار يبقى مكانه بنافذة مقصوصة ويمين يهبط على بداية المنطقة بذيله في الملف
        val halves = byId("straddle")
        assertEquals(2, halves.size)
        assertEquals(1_000L, halves[0].startMs)
        assertEquals(500L, halves[0].endInFileMs)
        assertEquals(0f, halves[0].fadeOutMs.toFloat())
        assertEquals(1_500L, halves[1].startMs)
        assertEquals(3_500L, halves[1].startInFileMs)
        assertEquals(5_000L, halves[1].endInFileMs)
        assertEquals(0f, halves[1].fadeInMs.toFloat())
    }

    @Test
    fun `region ripple delete shifts markers playhead and selection`() {
        val p = project(
            clips = listOf(clip("a", 4_000), clip("b", 4_000)),
            markers = listOf(
                MontageMarker("mk1", "قبل", 500),
                MontageMarker("mk2", "داخل", 5_000),
                MontageMarker("mk3", "بعد", 7_000)
            ),
            playheadMs = 5_000
        ).copy(selectionStartMs = 6_000, selectionEndMs = 8_000)
        val np = MontageLogic.deleteRegionRipple(p, 4_000, 6_500)!!
        assertEquals(listOf("قبل", "بعد"), np.markers.map { it.name })
        assertEquals(4_500L, np.markers[1].tMs) // 7000 - 2500
        assertEquals(4_000L, np.playheadMs) // الرأس كان داخل المنطقة فقفز لبدايتها
        // تحديد ممتد على المنطقة: يُحدّث إلى ما تبقى منه بعد الحذف
        assertEquals(4_000L, np.selectionStartMs)
        assertEquals(5_500L, np.selectionEndMs)
    }

    @Test
    fun `tiny or empty regions are rejected`() {
        val p = project(clips = listOf(clip("a", 5_000)))
        assertNull(MontageLogic.deleteRegionRipple(p, 1_000, 1_020)) // تحت الحد الأدنى
        assertNull(MontageLogic.deleteRegionRipple(p, 3_000, 3_000)) // معدومة
        assertNull(MontageLogic.deleteRegionRipple(p, 9_000, 20_000)) // خارج المشروع كله
    }

    // ================= 5) الالتقاط =================

    @Test
    fun `snapping picks nearest candidate within threshold else keeps target`() {
        val cands = listOf(1_000L, 2_500L, 4_000L)
        assertEquals(1_000L, MontageLogic.snapMs(1_040, cands))
        assertEquals(2_500L, MontageLogic.snapMs(2_450, cands))
        assertEquals(3_000L, MontageLogic.snapMs(3_000, cands)) // بلا مرشح قريب يبقى
        assertEquals(500L, MontageLogic.snapMs(500, emptyList())) // بلا مرشحات أصلًا يبقى
    }

    @Test
    fun `snap candidates include markers and all clip edges`() {
        val p = project(
            clips = listOf(clip("a", 2_000)),
            trackClips = listOf(tclip("m", 3_000, start = 4_000)),
            markers = listOf(MontageMarker("mk", "الفاصل", 1_500))
        )
        val c = MontageLogic.snapCandidates(p)
        assertTrue(1_500L in c)
        assertTrue(0L in c && 2_000L in c) // حافتا مقطع التعليق
        assertTrue(4_000L in c && 7_000L in c) // حافتا مقطع المسار
    }

    // ================= 6) التصيير الفعلي بعدة مسارات =================

    @Test
    fun `render mixes multiple tracks and strips legacy mute and solo flags`() {
        val dir = File(tmp, "tones")
        val voice = MontageDemo.tone(File(dir, "voice.wav"), 44_100, 1, 2_000, 440.0)
        val bed1 = MontageDemo.tone(File(dir, "bed1.wav"), 44_100, 1, 4_000, 330.0)
        val bed2 = MontageDemo.tone(File(dir, "bed2.wav"), 44_100, 1, 4_000, 220.0)
        val vDur = 2_000L

        fun mk(path: String, dur: Long, vol: Int, trackId: String) = MontageMusicClip(
            id = "mu$trackId", path = path, title = trackId, durationMs = dur,
            startMs = 0L, volumePercent = vol, trackId = trackId
        )

        val tracks = listOf(
            MontageTrack(id = "ta", name = "موسيقى", volumePercent = 100),
            MontageTrack(id = "tb", name = "مؤثرات", volumePercent = 100)
        )

        // الثلاثة مسموعون: المدة أبعدها (4s من المسارات)
        val p1 = project(
            clips = listOf(MontageClip("v", voice.path, "v", vDur, 0, vDur)),
            trackClips = listOf(mk(bed1.path, 4_000, 100, "ta"), mk(bed2.path, 4_000, 100, "tb")),
            tracks = tracks
        )
        val out1 = File(tmp, "mix1.wav")
        val r1 = MontageRender.render(p1, out1)
        assertTrue(r1.ok) { r1.message ?: "" }
        assertTrue(Math.abs(r1.durationMs - 4_000L) <= 40L) { "durationMs=${r1.durationMs}" }
        val f1 = AudioSystem.getAudioInputStream(out1).format
        assertEquals(44_100f, f1.sampleRate)

        // أعلام كتم قديمة من مشاريع v0.36.0: تُهرَج فالمدة والصوت كما هي
        val p2 = p1.copy(tracks = tracks.map { if (it.id == "tb") it.copy(muted = true) else it })
        val out2 = File(tmp, "mix2.wav")
        val r2 = MontageRender.render(p2, out2)
        assertTrue(r2.ok) { r2.message ?: "" }
        assertTrue(Math.abs(r2.durationMs - 4_000L) <= 40L) { "durationMs=${r2.durationMs}" }

        // أعلام منفرد قديمة: تُهرَج أيضًا فلا عزل صامت
        val p3 = p1.copy(tracks = tracks.map { if (it.id == "tb") it.copy(solo = true) else it })
        val out3 = File(tmp, "mix3.wav")
        val r3 = MontageRender.render(p3, out3)
        assertTrue(r3.ok) { r3.message ?: "" }
        assertNotNull(out3)
        assertTrue(out3.length() > 44L)
    }

    // ================= 7) الأداة والثوابت =================

    @Test
    fun `montage tool enum and constants are wired`() {
        assertEquals(setOf("SELECT", "SPLIT"), MontageTool.entries.map { it.name }.toSet())
        assertTrue(MontageLogic.MAX_TRACKS >= 2)
        assertTrue(MontageLogic.SELECTION_MIN_MS > 0)
        assertTrue(MontageLogic.SNAP_THRESHOLD_MS > 0)
        assertEquals(5, MontageLogic.TRACK_COLORS.size)
    }
}
