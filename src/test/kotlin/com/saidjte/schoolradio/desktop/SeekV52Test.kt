package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.MusicChannel
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.PcmOutLine
import com.saidjte.schoolradio.desktop.audio.TrackReader
import java.io.File
import javax.sound.sampled.AudioFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * عقد التنقل اليدوي في مشغل الموسيقى (v0.52.0 «التنقل في المقطع الموسيقي
 * يتسبب في الانتقال للمقطع الموالي»):
 *
 * الجذر المُثبَت: التمشيط الآلي (كروس فيد) مفعّل افتراضيًا بنافذة ثلاث ثوانٍ،
 * وكان يُقيَّم بعد كل كتلة على الموضع الحالي — فأي سعي يدوي يقع داخل الثواني
 * الأخيرة يجعل المتبقي ≤ النافذة ويبدأ الانتقال للمقطع الموالي لحظة السعي
 * نفسها، فيبدو أن التنقل «ينتقل للمقطع الموالي».
 *
 * العقد بعد الإصلاح:
 * 1) سعي داخل النافذة → لا تمشيط إطلاقًا؛ المقطع يكمل إلى نهايته فعليًا
 *    ثم العقد الطبيعي (onEnded) ينتقل للموالي بلا قفزة مبكرة.
 * 2) سعي رجوعي خارج النافذة → يعيد تسليح التمشيط (كان يتوقف للأبد).
 * 3) والقرّاء الحقيقيون (MP3 CBR وVBR بجدول Xing وM4A) يسعون وسط المقطع
 *    ويكملون التشغيل بعده بلا انقطاع مبكر — بخط ذاكرة مُحقَن بلا عتاد صوت.
 */
class SeekV52Test {

    /** خط ذاكرة يحصي البايتات — بلا عتاد صوت. */
    private class MemoryLine : PcmOutLine {
        var bytes = 0L
        override fun open(format: AudioFormat, bufferSize: Int) { }
        override fun start() { }
        override fun stop() { }
        override fun flush() { }
        override fun write(b: ByteArray, off: Int, len: Int) { bytes += len }
        override fun setVolume(v: Float) { }
        override fun close() { }
    }

    /**
     * قارئ ملف مزيف بأزمنة مسيطر عليها: يشغّل عدد كتل محدًّا بالضبط حين يُطلب
     * منه (chunksToRun) ثم يتجمّد — كي يُرصد سلوك النافذة قبل بلوغ النهاية،
     * وينتهي طبيعيًا عند بلوغ مدته متى أُذن له بالاستمرار.
     */
    private class ScriptedFileReader(
        override val durationMs: Long,
        private val chunkMs: Long = 250L
    ) : TrackReader {
        @Volatile var chunksToRun: Long = 0L
        @Volatile var current = 0L
        val seeks = mutableListOf<Long>()
        var closed = false
        override val currentMs: Long get() = current
        override fun seek(ms: Long) { seeks += ms; current = ms.coerceIn(0, durationMs) }
        override fun pause() { }
        override fun resume() { }
        override fun applyVolume(volume: Float) { }
        override fun readChunk(): Boolean {
            if (chunksToRun <= 0L) { Thread.sleep(3); return true }
            chunksToRun--
            current += chunkMs
            return current < durationMs
        }
        override fun close() { closed = true }
    }

    /** انتظار شرط بمهلة — يفشل برسالة واضحة إن انقضت. */
    private fun await(message: String, timeoutMs: Long = 8_000L, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("انقضت المهلة: $message")
    }

    private fun fixture(name: String): File {
        val bytes = javaClass.classLoader.getResourceAsStream("formats/$name")?.readBytes()
            ?: throw AssertionError("العيّنة مفقودة: $name")
        val ext = name.substringAfterLast('.')
        // النقطة قبل الامتداد ضرورية — مُوجّه openAudioInputStream يصنّف بالامتداد
        val f = File.createTempFile("v52-", ".$ext")
        f.writeBytes(bytes)
        f.deleteOnExit()
        return f
    }

    // ================= 1) سعي داخل النافذة: لا انتقال للموالي =================

    @Test
    fun `seek inside the crossfade window does not jump to the next track`() {
        val eng = MusicPlayerEngine()
        eng.crossfadeMs = 3_000L
        val a = File("selftest/track-a.mp3")
        val b = File("selftest/track-b.mp3")
        val fa = ScriptedFileReader(600_000L)
        val fb = ScriptedFileReader(600_000L)
        eng.trackReaderOverride = { if (it == a) fa else fb }
        eng.onCrossfadeNext = { b }
        var endedCount = 0
        eng.onEnded = { endedCount++; eng.play(b) } // نظير next(auto=true) بلا تمشيط
        try {
            eng.play(a)
            await("track A loaded") { eng.hasTrack.value && eng.title.value == "track-a" }

            // السعي المُشكِل: داخل الثواني الثلاث الأخيرة — كان يولّد الانتقال فورًا
            eng.seek(599_000L)
            await("seek grounded") { fa.seeks.contains(599_000L) }
            fa.chunksToRun = 2 // كتلتان فقط: كانت الأولى قبل الإصلاح تُطلق النافذة

            // قبل الإصلاح: أول كتلة تُطلق التمشيط (العنوان يتبدل والنافذة تشتعل) —
            // بعد الإصلاح: يبقى على المقطع نفسه بلا نافذة بلا تبديل
            Thread.sleep(300)
            assertEquals("track-a", eng.title.value, "العنوان لم يتبدل بالسعي")
            assertEquals(a, eng.currentFile.value, "الملف الحالي لم يتبدل بالسعي")
            assertFalse(eng.xfading.value, "لا نافذة تمشيط انطلقت من السعي")
            assertEquals(0, endedCount, "لم ينتهِ المقطع قبل نهايته الفعلية")

            // النهاية الطبيعية: عقد الانتهاء العادي ينتقل للموالي (بلا تمشيط)
            fa.chunksToRun = 50
            await("natural end then next") { eng.title.value == "track-b" }
            assertFalse(eng.xfading.value, "الانتقال بعد النهاية الطبيعية صلب بلا نافذة")
            assertTrue(fa.closed, "قارئ A أُغلق عند النهاية")
        } finally {
            eng.trackReaderOverride = null
            eng.onCrossfadeNext = null
            eng.onEnded = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    // ================= 2) سعي رجوعي خارج النافذة: يعيد تسليح التمشيط =================

    @Test
    fun `seeking back out of the window re-arms the auto crossfade`() {
        val eng = MusicPlayerEngine()
        eng.crossfadeMs = 3_000L
        val a = File("selftest/track-a.mp3")
        val b = File("selftest/track-b.mp3")
        val fa = ScriptedFileReader(600_000L)
        val fb = ScriptedFileReader(600_000L)
        eng.trackReaderOverride = { if (it == a) fa else fb }
        eng.onCrossfadeNext = { b }
        eng.onEnded = { eng.play(b) }
        try {
            eng.play(a)
            await("track A loaded") { eng.hasTrack.value }

            // سعي داخل النافذة يُسليح المنع (سلوك الإصلاح)…
            eng.seek(599_000L)
            await("first seek grounded") { fa.seeks.contains(599_000L) }
            fa.chunksToRun = 2
            Thread.sleep(120)
            // …ثم سعي رجوعي بعيد خارج النافذة يعيد التسليح (كان يتعطل للأبد)
            eng.seek(100_000L)
            await("second seek grounded") { fa.seeks.contains(100_000L) }
            fa.chunksToRun = 2_000 // يتقدم طبيعيًا حتى يدخل النافذة وصولًا طبيعيًا

            await("crossfade fires organically") { eng.xfading.value && eng.title.value == "track-b" }
            assertTrue(fa.current >= 597_000L, "النافذة انطلقت قرب النهاية لا من موضع السعي: ${fa.current}")
        } finally {
            eng.trackReaderOverride = null
            eng.onCrossfadeNext = null
            eng.onEnded = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    // ================= 3) القرّاء الحقيقيون: سعي وسط المقطع ثم إكمال =================

    @Test
    fun `real mp3 cbr seek to middle keeps playing without premature end`() {
        val f = fixture("mp3_cbr44100.mp3")
        val info = Mp3Info.analyze(f)
        assertTrue(info != null && info.durationMs > 1_000, "مدة العيّنة: ${info?.durationMs}")
        val r = com.saidjte.schoolradio.desktop.audio.Mp3TrackReader(
            f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() }
        )
        try {
            val target = r.durationMs / 2
            r.seek(target)
            val at = r.currentMs
            assertTrue(at >= target - 100 && at <= target + 1_600, "السعي وصل الهدف: $at مقابل $target")
            assertTrue(r.readChunk(), "التشغيل يكمل بعد السعي (لا نهاية مبكرة)")
            val after = r.currentMs
            assertTrue(after > at, "الموضع يتقدم بعد السعي: $after مقابل $at")
            // الإكمال حتى النهاية: لا انقطاع شاذ قبل بلوغ المدة
            var guard = 0
            while (r.readChunk() && guard < 200_000) guard++
            assertTrue(r.currentMs >= r.durationMs - 2_000, "وصل النهاية الطبيعية: ${r.currentMs} من ${r.durationMs}")
        } finally {
            r.close()
        }
    }

    @Test
    fun `real mp3 vbr xing seek via toc table keeps playing`() {
        val f = fixture("mp3_vbr_xing.mp3")
        val info = Mp3Info.analyze(f)
        assertTrue(info != null && info.durationMs > 1_000, "مدة العيّنة: ${info?.durationMs}")
        val r = com.saidjte.schoolradio.desktop.audio.Mp3TrackReader(
            f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() }
        )
        try {
            val target = r.durationMs / 2
            r.seek(target)
            val at = r.currentMs
            assertTrue(at >= target - 100 && at <= target + 1_600, "سعي TOC وصل الهدف: $at مقابل $target")
            assertTrue(r.readChunk(), "التشغيل يكمل بعد سعي VBR")
        } finally {
            r.close()
        }
    }

    @Test
    fun `real m4a aac seek to middle keeps playing and ends naturally`() {
        val f = fixture("apple.m4a")
        val r = com.saidjte.schoolradio.desktop.audio.PcmTrackReader(
            f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() }
        )
        try {
            assertTrue(r.durationMs > 1_000, "مدة M4A: ${r.durationMs}")
            val target = r.durationMs / 2
            r.seek(target)
            assertTrue(r.readChunk(), "التشغيل يكمل بعد سعي M4A (لا نهاية مبكرة)")
            val at = r.currentMs
            assertTrue(at >= target - 100, "الموضع بعد السعي: $at مقابل $target")
            var guard = 0
            while (r.readChunk() && guard < 500_000) guard++
            assertTrue(r.currentMs >= r.durationMs - 2_000, "نهاية طبيعية بعد السعي: ${r.currentMs}")
        } finally {
            r.close()
        }
    }

    @Test
    fun `seek to near end ends cleanly without exception`() {
        val f = fixture("mp3_cbr44100.mp3")
        val r = com.saidjte.schoolradio.desktop.audio.Mp3TrackReader(
            f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() }
        )
        try {
            r.seek(r.durationMs - 300L)
            // النهاية القريبة: كتل قليلة ثم false — ولا استثناء ولا تعليق
            var guard = 0
            while (r.readChunk() && guard < 100_000) guard++
            assertTrue(guard < 100_000, "النهاية بلُغت نظيفة")
        } finally {
            r.close()
        }
    }
}
