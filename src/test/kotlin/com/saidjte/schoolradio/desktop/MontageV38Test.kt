package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.ClipTransforms
import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.ui.MontageShortcutAction
import com.saidjte.schoolradio.desktop.ui.resolveMontageShortcut
import androidx.compose.ui.input.key.Key
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.sin

/**
 * عقد v0.38.0 «أدوات الحفرة الاحترافية — الحفرة الحديثة»:
 * - عكس الموجة، تعديل السرعة دون تغيير النبرة (WSOLA)، وتغيير النبرة دون
 *   تغيير الطول — رياضيات نقية مُتحقق منها بالتردد والمدة على موجات معروفة.
 * - الحافظة: قص/نسخ/لصق بمعرّفات جديدة بين التعليق والمسارات الحرة.
 * - تكرار المقاطع ×N والدمج المتتالي وعقد صلاحيته.
 * - ارتداد مخزَّن ببصمة حتمية يُعاد بلا حساب، وملفات WAV قانونية.
 * - بروتوكول اختصارات المفاتيح الجديدة X/C/V/D/J.
 */
class MontageV38Test {

    @TempDir
    lateinit var tmp: File

    /** موجة جيبية ستيريو متشابكة بسعة 12000 — مادة اختبار النبرة والمدة. */
    private fun sine(hz: Double, ms: Long, rate: Int = 44100): ShortArray {
        val frames = (ms * rate / 1000L).toInt()
        val out = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val v = (sin(2.0 * PI * hz * i / rate) * 12000.0).toInt().toShort()
            out[2 * i] = v
            out[2 * i + 1] = v
        }
        return out
    }

    // ================= 1) عكس الموجة =================

    @Test
    fun `reversePcm reverses frame order keeping channel pairing`() {
        val pcm = shortArrayOf(1, 2, 3, 4, 5, 6)
        val r = ClipTransforms.reversePcm(pcm)
        assertEquals(6, r.size)
        assertEquals(5f, r[0].toFloat()); assertEquals(6f, r[1].toFloat())
        assertEquals(3f, r[2].toFloat()); assertEquals(4f, r[3].toFloat())
        assertEquals(1f, r[4].toFloat()); assertEquals(2f, r[5].toFloat())
        // الأصل لا يُمسّ
        assertEquals(1f, pcm[0].toFloat())
        // فرد العينات: إطارات كاملة فقط تُقلب
        val odd = shortArrayOf(9, 8, 7)
        assertEquals(3, ClipTransforms.reversePcm(odd).size)
    }

    // ================= 2) WSOLA: السرعة دون تغيير النبرة =================

    @Test
    fun `wsola tempo 2 halves duration and preserves pitch`() {
        val src = sine(440.0, 2000)
        val out = ClipTransforms.wsolaChangeTempo(src, 2.0)
        val inDur = src.size / 2.0 / 44100.0
        val outDur = out.size / 2.0 / 44100.0
        assertTrue(abs(outDur - inDur / 2.0) <= 0.08) { "tempo2 duration=$outDur expected=${inDur / 2}" }
        val hz = ClipTransforms.estimateDominantHz(out)
        assertTrue(abs(hz - 440.0) <= 35.0) { "tempo2 freq=$hz" }
    }

    @Test
    fun `wsola tempo half doubles duration and preserves pitch`() {
        val src = sine(330.0, 1500)
        val out = ClipTransforms.wsolaChangeTempo(src, 0.5)
        val inDur = src.size / 2.0 / 44100.0
        val outDur = out.size / 2.0 / 44100.0
        assertTrue(abs(outDur - inDur * 2.0) <= 0.10) { "tempo05 duration=$outDur expected=${inDur * 2}" }
        val hz = ClipTransforms.estimateDominantHz(out)
        assertTrue(abs(hz - 330.0) <= 30.0) { "tempo05 freq=$hz" }
    }

    @Test
    fun `wsola identity at tempo 1 and short-input guard`() {
        val src = sine(440.0, 1200)
        val same = ClipTransforms.wsolaChangeTempo(src, 1.0)
        val d1 = src.size / 2 / 44100.0
        val d2 = same.size / 2 / 44100.0
        assertTrue(abs(d1 - d2) <= 0.08) { "tempo1 duration drift=$d1 -> $d2" }
        // أقصر من إطارين: يعود كما هو حرفيًا
        val tiny = shortArrayOf(1, 2, 3, 4)
        assertTrue(ClipTransforms.wsolaChangeTempo(tiny, 2.0).contentEquals(tiny))
        // المعامل محبوس 0.25..4 داخليًا — قيم متطرفة لا تفشل
        val extreme = ClipTransforms.wsolaChangeTempo(src, 99.0)
        assertTrue(extreme.size < src.size) { "tempo99 should shrink" }
    }

    @Test
    fun `changeSpeed percent semantics clamp and frequency hold`() {
        assertEquals(50, ClipTransforms.clampSpeedPercent(10))
        assertEquals(200, ClipTransforms.clampSpeedPercent(999))
        assertEquals(150, ClipTransforms.clampSpeedPercent(150))
        val src = sine(440.0, 2000)
        val fast = ClipTransforms.changeSpeed(src, 200)
        val slow = ClipTransforms.changeSpeed(src, 50)
        val inFrames = src.size / 2
        assertTrue(abs(fast.size / 2 - inFrames / 2) <= 44100 * 8 / 100) { "speed200 frames" }
        assertTrue(slow.size / 2 > inFrames) { "speed50 must lengthen" }
        assertTrue(abs(ClipTransforms.estimateDominantHz(fast) - 440.0) <= 35.0) { "speed200 freq" }
        assertTrue(abs(ClipTransforms.estimateDominantHz(slow) - 440.0) <= 35.0) { "speed50 freq" }
    }

    // ================= 3) النبرة دون تغيير الطول =================

    @Test
    fun `pitch up an octave preserves duration and doubles frequency`() {
        val src = sine(440.0, 2000)
        val out = ClipTransforms.pitchShift(src, 12)
        val dIn = src.size / 2.0 / 44100.0
        val dOut = out.size / 2.0 / 44100.0
        assertTrue(abs(dOut - dIn) <= 0.10) { "pitch+12 duration=$dOut vs $dIn" }
        val hz = ClipTransforms.estimateDominantHz(out)
        assertTrue(abs(hz - 880.0) <= 45.0) { "pitch+12 freq=$hz" }
    }

    @Test
    fun `pitch down an octave preserves duration and halves frequency`() {
        val src = sine(440.0, 2000)
        val out = ClipTransforms.pitchShift(src, -12)
        val dIn = src.size / 2.0 / 44100.0
        val dOut = out.size / 2.0 / 44100.0
        assertTrue(abs(dOut - dIn) <= 0.10) { "pitch-12 duration=$dOut vs $dIn" }
        val hz = ClipTransforms.estimateDominantHz(out)
        assertTrue(abs(hz - 220.0) <= 25.0) { "pitch-12 freq=$hz" }
    }

    @Test
    fun `pitch zero is identity and bounds clamp and factor math`() {
        val src = sine(440.0, 800)
        assertTrue(ClipTransforms.pitchShift(src, 0).contentEquals(src))
        assertEquals(-12, ClipTransforms.clampPitchSemitones(-99))
        assertEquals(12, ClipTransforms.clampPitchSemitones(99))
        assertEquals(0, ClipTransforms.clampPitchSemitones(0))
        assertEquals(2.0, ClipTransforms.pitchFactor(12), 1e-9)
        assertEquals(0.5, ClipTransforms.pitchFactor(-12), 1e-9)
        // نغمة واحدة ≈ 1.0595 (توازن متساوي الحرارة)
        assertEquals(1.05946, ClipTransforms.pitchFactor(1), 1e-4)
    }

    // ================= 4) الارتداد المخزَّن والدمج =================

    private fun toneClip(f: File, id: String, title: String): MontageClip {
        val file = MontageDemo.tone(f, 44100, 2, 2500, 440.0)
        return MontageClip(id = id, path = file.path, title = title, durationMs = 2500, startMs = 0, endMs = 2500)
    }

    @Test
    fun `bounce cache is deterministic and reuses identical key`() {
        val clip = toneClip(File(tmp, "a.wav"), "a", "مقطع")
        val cache = File(tmp, "bounce")
        val r1 = ClipTransforms.bounce(clip, "reverse", "-", ClipTransforms::reversePcm, cache)
        val r2 = ClipTransforms.bounce(clip, "reverse", "-", ClipTransforms::reversePcm, cache)
        assertEquals(r1.file.absolutePath, r2.file.absolutePath)
        assertTrue(r1.file.isFile && r1.file.length() > 44)
        assertEquals(r1.durationMs, r2.durationMs)
        assertTrue(abs(r1.durationMs - 2500) <= 40) { "bounce duration=${r1.durationMs}" }
        val h = WavHeader.read(r1.file)
        assertNotNull(h)
        assertEquals(44100, h!!.sampleRate)
        assertEquals(2, h.channels)
        // بصمة مختلفة بتحويل مختلف — لا تصادم
        val r3 = ClipTransforms.bounce(clip, "speed", "200", { ClipTransforms.changeSpeed(it, 200) }, cache)
        assertTrue(r3.file.absolutePath != r1.file.absolutePath)
        assertTrue(abs(r3.durationMs - 1250) <= 90) { "speed bounce duration=${r3.durationMs}" }
        // البصمة تشمل النافذة: قصّ مختلف يعطي ملفًا مختلفًا
        val trimmed = clip.copy(startMs = 500)
        val r4 = ClipTransforms.bounce(trimmed, "reverse", "-", ClipTransforms::reversePcm, cache)
        assertTrue(r4.file.absolutePath != r1.file.absolutePath)
        assertTrue(abs(r4.durationMs - 2000) <= 40) { "window bounce=${r4.durationMs}" }
    }

    @Test
    fun `bounceMerge concatenates with gap through the real renderer`() {
        val a = toneClip(File(tmp, "a.wav"), "a", "أول")
        val b = toneClip(File(tmp, "b.wav"), "b", "ثانٍ")
        val cache = File(tmp, "bounce")
        val r = ClipTransforms.bounceMerge(a, b, gapMs = 500, crossfadeMs = 0, cacheDir = cache)
        // 2500 + 500 + 2500 = 5500ms
        assertTrue(abs(r.durationMs - 5500) <= 80) { "merge duration=${r.durationMs}" }
        val h = WavHeader.read(r.file)
        assertNotNull(h) { "merged wav header" }
        // العودية بنفس المدخلات تعيد نفس الملف
        val r2 = ClipTransforms.bounceMerge(a, b, gapMs = 500, crossfadeMs = 0, cacheDir = cache)
        assertEquals(r.file.absolutePath, r2.file.absolutePath)
        // عنوان الدمج معقّم بعقد التسمية
        assertEquals("أول+ثانٍ", ClipTransforms.mergedTitle(" أول ", " ثانٍ "))
        assertTrue(ClipTransforms.mergedTitle("", "").isNotEmpty())
    }

    @Test
    fun `decodeWindow normalizes rate and honors the trim window`() {
        // نصف ثانية من ملف 22050 أحادي — يعود ستيريو 44.1k بنفس المدة
        val f = MontageDemo.tone(File(tmp, "mono.wav"), 22050, 1, 2000, 550.0)
        val full = MontageClip(id = "m", path = f.path, title = "مونو", durationMs = 2000, startMs = 0, endMs = 0)
        val pcm = ClipTransforms.decodeWindow(full)
        val durMs = pcm.size / 2 * 1000L / 44100L
        assertTrue(abs(durMs - 2000) <= 40) { "mono decode=$durMs" }
        val half = full.copy(startMs = 500, endMs = 1500)
        val pcm2 = ClipTransforms.decodeWindow(half)
        val dur2 = pcm2.size / 2 * 1000L / 44100L
        assertTrue(abs(dur2 - 1000) <= 40) { "window decode=$dur2" }
        // النافذة الفارغة = الملف كاملًا (عقد المُصيِّر نفسه)
        assertTrue(pcm.size > pcm2.size)
    }

    // ================= 5) الحافظة =================

    private fun vc(id: String, dur: Long = 1000) =
        MontageClip(id = id, path = "/p/$id.wav", title = id, durationMs = dur, startMs = 0, endMs = dur)

    @Test
    fun `clipboard voice paste inserts after selection and at end with fresh ids`() {
        val clips = listOf(vc("a"), vc("b"), vc("c"))
        val copy = MontageLogic.pastedVoiceCopy(vc("b", 700))
        // بعد المحدد b
        val mid = MontageLogic.pasteVoiceAfter(clips, copy, "b")
        assertEquals(listOf("a", "b", copy.id, "c"), mid.map { it.id })
        assertEquals(700L, mid[2].endMs)
        // بلا محدد: نهاية القائمة
        val end = MontageLogic.pasteVoiceAfter(clips, copy, null)
        assertEquals(copy.id, end.last().id)
        assertEquals(4, end.size)
        // محدد مجهول: نهاية القائمة كذلك
        val unknown = MontageLogic.pasteVoiceAfter(clips, copy, "zzz")
        assertEquals(copy.id, unknown.last().id)
        // الأصل بلا مساس والمعرّفات لا تتكرر
        assertTrue(clips.none { it.id == copy.id })
        assertEquals(4, mid.map { it.id }.toSet().size)
    }

    @Test
    fun `clipboard track paste lands at requested ms and respects caps`() {
        val m = MontageMusicClip(id = "m1", path = "/p/m.wav", title = "سرير", durationMs = 4000, startMs = 100)
        val copy = MontageLogic.pastedTrackCopy(m, 9500)
        assertEquals("m1", m.id) // الأصل سليم
        assertEquals(9500L, copy.startMs)
        assertTrue(copy.id != m.id)
        val out = MontageLogic.pasteTrackAt(emptyList(), copy)
        assertEquals(1, out.size)
        // موضع سالب يُحبس صفرًا
        assertEquals(0L, MontageLogic.pastedTrackCopy(m, -50).startMs)
    }

    // ================= 6) تكرار المقاطع ×N =================

    @Test
    fun `duplicate voice inserts sequential copies behind the original`() {
        val clips = listOf(vc("a"), vc("b"))
        val out = MontageLogic.duplicateVoiceAfter(clips, "a", 3)
        // الأصل أولًا وb أخيرًا وبينهما ثلاث نسخ بمعرّفات فريدة
        assertEquals(5, out.size)
        assertEquals("a", out.first().id)
        assertEquals("b", out.last().id)
        assertEquals(5, out.map { it.id }.toSet().size) { "ids must stay unique" }
        // النسخ تحمل خصائص الأصل (النافذة والمستوى)
        assertTrue(out.subList(1, 4).all { it.path == clips[0].path && it.endMs == clips[0].endMs })
        // صفر وسالب ومجهول: بلا تغيير
        assertTrue(MontageLogic.duplicateVoiceAfter(clips, "a", 0) === clips)
        assertTrue(MontageLogic.duplicateVoiceAfter(clips, "a", -2) === clips)
        assertTrue(MontageLogic.duplicateVoiceAfter(clips, "zz", 3) === clips)
        // السقف DUPLICATE_MAX يحرس
        val many = MontageLogic.duplicateVoiceAfter(listOf(vc("a")), "a", 999)
        assertEquals(1 + MontageLogic.DUPLICATE_MAX, many.size)
    }

    @Test
    fun `duplicate track copies are contiguous on the same track`() {
        val m = MontageMusicClip(id = "m1", path = "/p/m.wav", title = "سرير", durationMs = 2000, startMs = 500)
        val copies = MontageLogic.duplicateTrackClip(m, 3)
        assertEquals(3, copies.size)
        assertEquals(listOf(2500L, 4500L, 6500L), copies.map { it.startMs })
        assertTrue(copies.all { it.trackId == m.trackId })
        assertTrue(copies.all { it.path == m.path })
        assertTrue(copies.none { it.id == m.id })
        assertEquals(0, MontageLogic.duplicateTrackClip(m, 0).size)
    }

    // ================= 7) عقد الدمج =================

    @Test
    fun `mergeable pair rules for voice and free tracks`() {
        val clips = listOf(vc("a", 1000), vc("b", 30), vc("c", 1000))
        // الأخير لا يُدمج
        assertNull(MontageLogic.mergeableVoicePair(clips, "c"))
        // التالي أقصر من حد الدمج
        assertNull(MontageLogic.mergeableVoicePair(clips, "b"))
        assertNull(MontageLogic.mergeableVoicePair(clips, "zz"))
        val ok = MontageLogic.mergeableVoicePair(listOf(vc("a", 800), vc("b", 900)), "a")
        assertNotNull(ok)
        assertEquals("a", ok!!.first.id)
        assertEquals("b", ok.second.id)
        // المسارات: متتالٍ على نفس المسار يُدمج حتى لو كانت بينهما فجوة
        val tracks = listOf(
            MontageMusicClip(id = "m1", path = "/p/1.wav", title = "١", durationMs = 2000, startMs = 0),
            MontageMusicClip(id = "m2", path = "/p/2.wav", title = "٢", durationMs = 2000, startMs = 4000),
            MontageMusicClip(id = "x1", path = "/p/3.wav", title = "آخر", durationMs = 2000, startMs = 6000, trackId = "free2")
        )
        val pair = MontageLogic.mergeableTrackPair(tracks, "m1")
        assertNotNull(pair)
        assertEquals("m1", pair!!.first.id)
        assertEquals("m2", pair.second.id)
        // آخر مساره لا يُدمج
        assertNull(MontageLogic.mergeableTrackPair(tracks, "m2"))
        assertNull(MontageLogic.mergeableTrackPair(tracks, "x1"))
        // حد أدنى مدة الدمج موثّق
        assertEquals(50L, MontageLogic.MERGE_MIN_MS)
    }

    // ================= 8) اختصارات v0.38.0 =================

    @Test
    fun `new clipboard duplicate merge shortcuts resolve plain and with ctrl`() {
        assertEquals(MontageShortcutAction.CutSelected, resolveMontageShortcut(Key.X, false, false, false, false))
        assertEquals(MontageShortcutAction.CutSelected, resolveMontageShortcut(Key.X, true, false, false, false))
        assertEquals(MontageShortcutAction.CopySelected, resolveMontageShortcut(Key.C, false, false, false, false))
        assertEquals(MontageShortcutAction.CopySelected, resolveMontageShortcut(Key.C, true, false, false, false))
        assertEquals(MontageShortcutAction.PasteClipboard, resolveMontageShortcut(Key.V, false, false, false, false))
        assertEquals(MontageShortcutAction.PasteClipboard, resolveMontageShortcut(Key.V, true, false, false, false))
        assertEquals(MontageShortcutAction.DuplicateSelected, resolveMontageShortcut(Key.D, false, false, false, false))
        assertEquals(MontageShortcutAction.MergeWithNext, resolveMontageShortcut(Key.J, false, false, false, false))
        assertEquals(MontageShortcutAction.MergeWithNext, resolveMontageShortcut(Key.J, true, false, false, false))
        // Shift يحرس الحروف الجديدة (احتياط اختصارات مستقبلية)
        assertNull(resolveMontageShortcut(Key.C, false, true, false, false))
        assertNull(resolveMontageShortcut(Key.V, true, true, false, false))
        // البروتوكول القديم بلا مساس
        assertEquals(MontageShortcutAction.SplitAtPlayhead, resolveMontageShortcut(Key.S, false, false, false, false))
        assertEquals(MontageShortcutAction.Undo, resolveMontageShortcut(Key.Z, true, false, false, false))
        assertEquals(MontageShortcutAction.DeleteRegion, resolveMontageShortcut(Key.R, false, false, false, false))
    }
}
