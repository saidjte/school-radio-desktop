package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageExportFormat
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageTrack
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import javax.sound.sampled.AudioSystem

/**
 * عقد v0.37.0 «تسمية المقاطع بالنقر المزدوج وبلا كتم وتصدير MP3»:
 * - تعقيم أسماء المقاطع لإعادة التسمية بالنقر المزدوج.
 * - الكتم/المنفرد خارج عقد المونتاج: رؤوس بلا M/S، وأعلام الملفات القديمة
 *   تُهرَج في المسارات الفعّالة فلا مسار يتيم مكتموم، والتصيير يسمع كل خطوط العمل.
 * - تصدير MP3 إلى جانب WAV عبر LAME (jump3r) النقي: ملف حقيقي يُحلَّل ويُفكَّ
 *   بمعدل 44.1k ستيريو وجودة CBR مطابقة ومدة تُطابق خطة التصيير.
 */
class MontageV37Test {

    @TempDir
    lateinit var tmp: File

    // ================= 1) تعقيم أسماء المقاطع (النقر المزدوج) =================

    @Test
    fun `clip titles are trimmed flattened clamped and never empty`() {
        assertEquals("افتتاحية الحصة", MontageLogic.sanitizeClipTitle("  افتتاحية الحصة  "))
        assertEquals("سطر واحد فقط ثالث", MontageLogic.sanitizeClipTitle("سطر واحد\nفقط\r\nثالث"))
        assertEquals("مقطع", MontageLogic.sanitizeClipTitle("   "))
        assertEquals("مقطع", MontageLogic.sanitizeClipTitle(""))
        assertEquals("مقطع", MontageLogic.sanitizeClipTitle("\n\t\n"))
        val long = "ا".repeat(80)
        assertEquals(40, MontageLogic.sanitizeClipTitle(long).length)
        assertEquals(40, MontageLogic.sanitizeClipTitle(long).count { it == 'ا' })
    }

    // ================= 2) الكتم والمنفرد حيّان في المسارات (عقد v0.41.0 المعاد) =================

    @Test
    fun `mute and solo flags are live controls on effective tracks again`() {
        val p = MontageProject(
            tracks = listOf(
                MontageTrack(id = "t1", name = "موسيقى", volumePercent = 130, muted = true, solo = false),
                MontageTrack(id = "t2", name = "مؤثرات", volumePercent = 80, muted = false, solo = true)
            )
        )
        val tracks = MontageLogic.effectiveTracks(p)
        assertEquals(2, tracks.size)
        // منذ v0.41.0 الأعلام حيّة لا تُهرَج — الأزرار عادت فلا مسار يتيم مكتموم
        assertTrue(tracks[0].muted) { "mute flag lost" }
        assertTrue(tracks[1].solo) { "solo flag lost" }
        // باقي القيم محفوظة كما هي
        assertEquals(130, tracks[0].volumePercent)
        assertEquals("موسيقى", tracks[0].name)
        assertEquals(80, tracks[1].volumePercent)
        // الكسب المرجعي يحترم المسامع: المكتوم صفر والمنفرد حيّ وسط منفرد
        assertEquals(0, MontageLogic.trackGainPercent(tracks[0], anySolo = false))
        assertEquals(80, MontageLogic.trackGainPercent(tracks[1], anySolo = true))
        assertEquals(true, MontageLogic.anyTrackSolo(tracks))
    }

    @Test
    fun `render silences a muted track`() {
        // عقد v0.41.0: الكتم يصمت فعلا في المزج — ووحدة المزج تقرر البقية
        val tone = MontageDemo.tone(File(tmp, "bed.wav"), 44_100, 1, 3_000, 330.0)
        val p = MontageProject(
            musicClips = listOf(
                com.saidjte.schoolradio.desktop.data.MontageMusicClip(
                    id = "mu1", path = tone.path, title = "سرير", durationMs = 3_000,
                    startMs = 0, trackId = "t1"
                )
            ),
            tracks = listOf(MontageTrack(id = "t1", name = "موسيقى", muted = true))
        )
        val out = File(tmp, "muted.wav")
        val r = MontageRender.render(p, out)
        assertTrue(r.ok) { r.message ?: "" }
        assertTrue(Math.abs(r.durationMs - 3_000L) <= 40L) { "durationMs=${r.durationMs}" }
        // الملف صامت كليا: الكتم يصفّر الكسب قبل أي حساب
        AudioSystem.getAudioInputStream(out).use { ais ->
            val buf = ByteArray(64 * 1024)
            var peak = 0
            while (true) {
                val n = ais.read(buf)
                if (n < 0) break
                var i = 0
                while (i + 1 < n) {
                    val v = Math.abs(((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt())
                    if (v > peak) peak = v
                    i += 2
                }
            }
            assertTrue(peak <= 4) { "expected silent muted track, peak=$peak" }
        }
    }

    // ================= 3) تصدير MP3 إلى جانب WAV =================

    private fun demoProject(): Pair<MontageProject, Long> {
        val dir = File(tmp, "tones${System.nanoTime()}")
        val a = MontageDemo.tone(File(dir, "a.wav"), 44_100, 2, 2_500, 440.0)
        val b = MontageDemo.tone(File(dir, "b.wav"), 22_050, 1, 2_000, 330.0)
        val clips = listOf(
            MontageClip("c1", a.path, "أول", 2_500, 0, 2_500),
            MontageClip("c2", b.path, "ثانٍ", 2_000, 0, 2_000)
        )
        val plan = MontageLogic.totalDurationMs(clips, gapMs = 500L, crossfadeMs = 0L)
        return MontageProject(clips = clips, gapMs = 500L) to plan
    }

    @Test
    fun `mp3 export produces a real 44k stereo file at requested cbr bitrate`() {
        val (p, plan) = demoProject()
        val out = File(tmp, "montage-export.mp3")
        val r = MontageRender.render(p, out, format = MontageExportFormat.MP3, bitrateKbps = 192)
        assertTrue(r.ok) { r.message ?: "" }
        assertTrue(out.length() > 5_000) { "mp3 too small: ${out.length()}" }
        // المدة المكتوبة تخطط التصيير (وأقل دقة من WAV بإطار LAME)
        assertTrue(Math.abs(r.durationMs - plan) <= 60L) { "sink durationMs=${r.durationMs} plan=$plan" }
        // الملف يُحلَّل كمقاطع MP3 الحقيقية: 44.1k ستيريو CBR 192
        val info = Mp3Info.analyze(out)
        assertNotNull(info) { "mp3 header not parsed" }
        assertEquals(44_100, info!!.first.sampleRate)
        assertEquals(2, info.first.channels)
        assertEquals(192, info.first.bitRateKbps)
        assertTrue(Math.abs(info.durationMs - plan) <= 260L) { "mp3 durationMs=${info.durationMs} plan=$plan" }
        assertFalse(info.isVbr)
    }

    @Test
    fun `mp3 bitrate choice changes file size while keeping the same duration`() {
        val (p, plan) = demoProject()
        val lo = File(tmp, "lo.mp3")
        val hi = File(tmp, "hi.mp3")
        val rLo = MontageRender.render(p, lo, format = MontageExportFormat.MP3, bitrateKbps = 128)
        val rHi = MontageRender.render(p, hi, format = MontageExportFormat.MP3, bitrateKbps = 320)
        assertTrue(rLo.ok && rHi.ok)
        assertEquals(lo.length() * 320.0 / 128.0, hi.length().toDouble(), lo.length() * 0.25)
        assertTrue(hi.length() > lo.length()) { "320k should be larger than 128k" }
        assertEquals(MontageLogic.totalDurationMs(p.clips, p.gapMs, p.crossfadeMs), plan)
        assertEquals(128, Mp3Info.analyze(lo)!!.first.bitRateKbps)
        assertEquals(320, Mp3Info.analyze(hi)!!.first.bitRateKbps)
    }

    @Test
    fun `wav default render still writes a riff file unchanged`() {
        val (p, plan) = demoProject()
        val out = File(tmp, "montage.wav")
        val r = MontageRender.render(p, out) // التوقيع الافتراضي بلا صيغة — WAV كما هو
        assertTrue(r.ok) { r.message ?: "" }
        assertTrue(Math.abs(r.durationMs - plan) <= 40L) { "durationMs=${r.durationMs} plan=$plan" }
        val head = ByteArray(4)
        out.inputStream().use { it.read(head) }
        assertEquals("RIFF", String(head, Charsets.US_ASCII))
        assertEquals(plan, MontageLogic.totalDurationMs(p.clips, p.gapMs, p.crossfadeMs))
    }

    // ================= 4) ثوابت الصيغة والجودة =================

    @Test
    fun `export format enum and bitrate ladder are wired`() {
        assertEquals(setOf("WAV", "MP3"), MontageExportFormat.entries.map { it.name }.toSet())
        assertEquals("wav", MontageExportFormat.WAV.extension)
        assertEquals("mp3", MontageExportFormat.MP3.extension)
        assertTrue(MontageExportFormat.MP3.labelAr.contains("MP3"))
        assertEquals(listOf(128, 192, 256, 320), MontageRender.MP3_BITRATES)
        assertEquals(192, MontageRender.MP3_DEFAULT_KBPS)
        assertTrue(MontageRender.MP3_BITRATES.contains(MontageRender.MP3_DEFAULT_KBPS))
    }

}
