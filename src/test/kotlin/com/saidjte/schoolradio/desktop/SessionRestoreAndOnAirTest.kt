package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.FakeTrackVoice
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.data.OnAirDisplayLogic
import com.saidjte.schoolradio.desktop.data.OnAirModel
import com.saidjte.schoolradio.desktop.data.ScreenRect
import com.saidjte.schoolradio.desktop.data.SessionJson
import com.saidjte.schoolradio.desktop.data.SessionRestore
import com.saidjte.schoolradio.desktop.data.SessionStore
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir

/**
 * عقد v0.32.0: استعادة الجلسة الأخيرة كاملة عند إعادة الفتح (القائمة والسكريبت
 * والموضع) + وضع «على الهواء» ملء الشاشة لشاشة ثانية يتابعها الطلاب.
 * الاستعادة: المنطق النقي وحدةً، والمحرك بقارئ حقن حقيقي على حلقة التشغيل —
 * الفتح موقوفًا عند الموضع المحفوظ ثم الاستئناف يكمل لا يعيد من الصفر.
 * على الهواء: اختيار الشاشة الثانية وتركيب ما يراه الطلاب — كله نقي.
 */
class SessionRestoreAndOnAirTest {

    // ================= استعادة الجلسة: المنطق النقي =================

    @Test
    fun `resolve restores playlist track with clamped position`() {
        val s = SessionJson(trackPath = "/pl/b.mp3", positionMs = 45_000L, selectedSectionId = 7L)
        val r = SessionRestore.resolve(
            s, listOf("/pl/a.mp3", "/pl/b.mp3", "/pl/c.mp3"), setOf(7L, 8L)
        )
        assertTrue(r != null)
        assertEquals(1, r!!.playlistIndex)
        assertEquals(45_000L, r.positionMs)
        assertEquals(7L, r.selectedSectionId)
        assertFalse(r.fullReading)
    }

    @Test
    fun `resolve accepts library track outside playlist when file exists`() {
        // ملف موجود على القرص لكنه ليس في القائمة — يُستعاد في المحرك دون مساس القائمة
        val tmp = File(System.getProperty("java.io.tmpdir"), "sr-lib-track.mp3")
        tmp.writeBytes(byteArrayOf(0x00))
        try {
            val r = SessionRestore.resolve(
                SessionJson(trackPath = tmp.absolutePath, positionMs = 10_000L),
                listOf("/pl/other.mp3"), emptySet()
            )
            assertTrue(r != null)
            assertEquals(-1, r!!.playlistIndex, "outside playlist")
            assertEquals(10_000L, r.positionMs)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `resolve rejects missing track unless script section survives`() {
        // مسار غير موجود ولا فقرة — لا شيء قابل للاستعادة
        assertNull(SessionRestore.resolve(SessionJson(), listOf("x"), emptySet()))
        assertNull(
            SessionRestore.resolve(
                SessionJson(trackPath = "/nowhere/gone.mp3", positionMs = 5_000L),
                listOf("/pl/a.mp3"), emptySet()
            )
        )
        // المقطع مفقود لكن الفقرة موجودة — الجلسة تُستعاد للسكريبت دون الموضع
        val r = SessionRestore.resolve(
            SessionJson(trackPath = "/nowhere/gone.mp3", selectedSectionId = 3L),
            listOf("/pl/a.mp3"), setOf(3L, 4L)
        )
        assertTrue(r != null)
        assertEquals(-1, r!!.playlistIndex)
        assertEquals(3L, r.selectedSectionId)
    }

    @Test
    fun `resolve drops deleted script section and keeps blank paths out`() {
        // فقرة محذوفة بعد الحفظ تُسقط ولا تحمل وضع القراءة معها
        val r = SessionRestore.resolve(
            SessionJson(trackPath = " /pl/a.mp3 ", selectedSectionId = 99L, fullScriptReading = true),
            listOf("/pl/a.mp3"), setOf(1L)
        )
        assertTrue(r != null)
        assertEquals(0, r!!.playlistIndex, "trimmed path matched")
        assertNull(r.selectedSectionId)
        assertFalse(r.fullReading, "reading mode without its section")
    }

    @Test
    fun `position clamp handles negative and over-duration`() {
        assertEquals(0L, SessionRestore.clampPosition(-1_000L, 30_000L))
        assertEquals(30_000L, SessionRestore.clampPosition(99_000L, 30_000L))
        assertEquals(12_000L, SessionRestore.clampPosition(12_000L, 30_000L))
        assertEquals(5_000L, SessionRestore.clampPosition(5_000L, 0L), "unknown duration passes")
        assertEquals(0L, SessionRestore.clampPosition(null, 30_000L))
    }

    @Test
    fun `session store round trip is atomic and tolerant`() {
        val f = File.createTempFile("session", ".json")
        try {
            val store = SessionStore(f)
            store.save(SessionJson(trackPath = "/pl/a.mp3", positionMs = 77_000L, selectedSectionId = 2L, fullScriptReading = true, savedAt = 42L))
            val re = SessionStore(f).session()
            assertEquals("/pl/a.mp3", re.trackPath)
            assertEquals(77_000L, re.positionMs)
            assertEquals(2L, re.selectedSectionId)
            assertTrue(re.fullScriptReading == true)
            // ملف تالف: يعود بالافتراضي فلا يكسر الإقلاع
            f.writeText("{broken")
            val broken = SessionStore(f).session()
            assertNull(broken.trackPath)
            assertNull(broken.positionMs)
        } finally {
            f.delete()
        }
    }

    // ================= استعادة الجلسة: المحرك الحقيقي بقارئ حقن =================

    @Test
    fun `engine preparePaused loads track paused at restored position and resume continues`() {
        val eng = MusicPlayerEngine()
        eng.trackReaderOverride = { FakeTrackVoice(totalMs = 15_000L, chunkSleepMs = 2L) }
        try {
            val f = File("automation/sr-fake.mp3")
            f.parentFile?.mkdirs()
            f.writeBytes(ByteArray(0))
            assertTrue(eng.preparePaused(f, 6_000L), "prepare accepts existing file")
            // انتظار العامل حتى يفتح ويسقي ويجمّد
            var tries = 0
            while (!eng.hasTrack.value && tries < 100) { Thread.sleep(20); tries++ }
            assertTrue(eng.hasTrack.value, "track opened")
            assertTrue(eng.isPaused.value, "restored paused")
            // الصمت إشارة isPaused — عقد المحرك يُبقي isPlaying true
            // الموضع يُرسي على الموضع المستعاد لا الصفر
            tries = 0
            while (eng.positionMs.value != 6_000L && tries < 100) { Thread.sleep(20); tries++ }
            assertEquals(6_000L, eng.positionMs.value, "restored position")
            // عقد المحرك: isPlaying يبقى true والإيقاف المؤقت هو إشارة الصمت —
            // الاستئناف يزيله فتُكمل الحالة موسيقى «تشغيل»
            assertTrue(eng.isPlaying.value, "playing contract kept while paused")
            eng.resume()
            assertFalse(eng.isPaused.value)
            Thread.sleep(600)
            assertTrue(eng.positionMs.value > 6_000L, "resumed advances, was ${eng.positionMs.value}")
            assertFalse(eng.isPaused.value)

            // ملف مفقود: رفض صامت بلا مساس الحالة
            assertFalse(eng.preparePaused(File("automation/missing-x.mp3"), 1_000L))
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
            File("automation/sr-fake.mp3").delete()
        }
    }

    @Test
    fun `engine preparePaused at zero position starts paused at start`() {
        val eng = MusicPlayerEngine()
        eng.trackReaderOverride = { FakeTrackVoice(totalMs = 10_000L, chunkSleepMs = 2L) }
        try {
            val f = File("automation/sr-zero.mp3")
            f.parentFile?.mkdirs()
            f.writeBytes(ByteArray(0))
            assertTrue(eng.preparePaused(f, 0L))
            var tries = 0
            while (!eng.hasTrack.value && tries < 100) { Thread.sleep(20); tries++ }
            assertTrue(eng.hasTrack.value && eng.isPaused.value)
            assertEquals(0L, eng.positionMs.value)
            assertTrue(eng.isPlaying.value, "playing contract kept while paused")
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
            File("automation/sr-zero.mp3").delete()
        }
    }

    // ================= وضع «على الهواء»: الشاشة الثانية والنموذج =================

    @Test
    fun `secondary screen picked first non-default or null on single screen`() {
        val a = ScreenRect(0, 0, 1920, 1080)
        val b = ScreenRect(1920, 0, 1280, 1024)
        val c = ScreenRect(3200, 0, 1920, 1080)
        assertEquals(b, OnAirDisplayLogic.pickSecondary(listOf(a, b), 0), "default first → second")
        assertEquals(a, OnAirDisplayLogic.pickSecondary(listOf(a, b), 1), "default second → first")
        assertEquals(a, OnAirDisplayLogic.pickSecondary(listOf(a, b, c), 2), "default last → first non-default")
        assertEquals(a, OnAirDisplayLogic.pickSecondary(listOf(a, b, c), 1), "first non-default wins")
        assertNull(OnAirDisplayLogic.pickSecondary(listOf(a), 0), "single screen")
        assertNull(OnAirDisplayLogic.pickSecondary(emptyList(), 0))
        // الافتراضي خارج الحدود يُحبس للآخر فلا انهيار — يبقى «أول غير الافتراضي»
        assertEquals(a, OnAirDisplayLogic.pickSecondary(listOf(a, b), 9))
    }

    @Test
    fun `on air model shows run item with remaining and next`() {
        val m = OnAirDisplayLogic.buildModel(
            runActive = true, runIndex = 1, runElapsedMs = 45_000L,
            remainingMs = 15_000L, warn = true,
            scheduleTitles = listOf("افتتاح الحلقة", "نشرة الأخبار", "فاصل الإذاعة"),
            schedulePlannedSeconds = listOf(30L, 60L, 45L),
            musicTitle = "نشيد الصباح", musicPositionMs = 20_000L, musicDurationMs = 100_000L,
            musicPlaying = true, micLive = true, recording = false
        )
        assertTrue(m.onAir)
        assertEquals(com.saidjte.schoolradio.desktop.data.HeadlineKind.RUN_ITEM, m.headlineKind)
        assertEquals("نشرة الأخبار", m.headline)
        assertEquals("00:45 من 01:00", m.timeText)
        assertEquals("المتبقي 00:15", m.remainingText)
        assertTrue(m.warn && !m.fired)
        assertEquals("فاصل الإذاعة", m.nextTitle)
        assertEquals("نشيد الصباح", m.musicTitle, "music strip while run item")
        assertTrue(m.musicProgress in 0.19f..0.21f)
        assertFalse(m.musicPaused)
    }
    @Test
    fun `on air model fires overdue and hides next after last item`() {
        val m = OnAirDisplayLogic.buildModel(
            runActive = true, runIndex = 2, runElapsedMs = 70_000L,
            remainingMs = -10_000L, warn = false,
            scheduleTitles = listOf("أ", "ب", "ج"),
            schedulePlannedSeconds = listOf(30L, 30L, 60L),
            musicTitle = null, musicPositionMs = 0L, musicDurationMs = 0L,
            musicPlaying = false, micLive = false, recording = true
        )
        assertTrue(m.onAir, "recording counts on air")
        assertEquals("تجاوز الزمن 00:10", m.remainingText)
        assertTrue(m.fired && !m.warn)
        assertNull(m.nextTitle, "last item has no next")
        // mmss يُقص السالب إلى صفر — نص التجاوز نفسه يُبنى من قيمة موجبة mmss(-r)
        assertEquals("00:00", OnAirDisplayLogic.mmss(-10_000L))
        assertEquals("00:10", OnAirDisplayLogic.mmss(10_000L))
    }

    @Test
    fun `on air model falls back to music headline then idle`() {
        // لا حلقة: الموسيقى تصير العنوان الرئيسي
        val m1 = OnAirDisplayLogic.buildModel(
            runActive = false, runIndex = -1, runElapsedMs = 0L, remainingMs = null, warn = false,
            scheduleTitles = listOf("أ"), schedulePlannedSeconds = listOf(30L),
            musicTitle = "نشيد الختام", musicPositionMs = 50_000L, musicDurationMs = 120_000L,
            musicPlaying = true, micLive = false, recording = false
        )
        assertEquals(com.saidjte.schoolradio.desktop.data.HeadlineKind.MUSIC, m1.headlineKind)
        assertEquals("نشيد الختام", m1.headline)
        assertEquals("00:50 / 02:00", m1.timeText)
        assertTrue(m1.onAir)
        assertNull(m1.nextTitle)
        assertNull(m1.musicTitle, "no secondary strip without run item")

        // لا شيء يعمل: شاشة استعداد
        val m2 = OnAirDisplayLogic.buildModel(
            runActive = false, runIndex = -1, runElapsedMs = 0L, remainingMs = null, warn = false,
            scheduleTitles = listOf("أ"), schedulePlannedSeconds = listOf(30L),
            musicTitle = null, musicPositionMs = 0L, musicDurationMs = 0L,
            musicPlaying = false, micLive = false, recording = false
        )
        assertEquals(com.saidjte.schoolradio.desktop.data.HeadlineKind.NONE, m2.headlineKind)
        assertFalse(m2.onAir)
        assertNull(m2.headline)
    }
}
