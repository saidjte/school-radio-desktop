package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScheduleStore
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptSource
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.StudioLogic
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** اختبارات الاستوديو المدمج (v0.3.0): أولوية عرض السكريبت، حالة الهواء، الساعة، الربط المحفوظ. */
class StudioTest {

    private val secs = listOf(
        ScriptSection(1, "مقدمة", "السلام عليكم"),
        ScriptSection(2, "نشرات", "أخبار المدرسة"),
        ScriptSection(3, "ختام", "إلى اللقاء")
    )

    private val items = listOf(
        ScheduleItem(10, "افتتاح", SegmentKind.LIVE, 60, null, "", 2L),
        ScheduleItem(11, "نشيد", SegmentKind.RECORDING, 90),
        ScheduleItem(12, "ختام", SegmentKind.SPEECH, 60, null, "", 3L)
    )

    @Test
    fun `linked script of running segment wins over manual selection`() {
        val r = StudioLogic.resolveScriptSection(items, runActive = true, runIndex = 0, script = secs, manualSectionId = 1L)
        assertEquals(ScriptSource.FROM_SCHEDULE, r?.second)
        assertEquals(2L, r?.first?.id)
    }

    @Test
    fun `running segment without link falls back to manual selection`() {
        val r = StudioLogic.resolveScriptSection(items, runActive = true, runIndex = 1, script = secs, manualSectionId = 1L)
        assertEquals(ScriptSource.MANUAL, r?.second)
        assertEquals(1L, r?.first?.id)
    }

    @Test
    fun `idle run with no manual selection shows first section`() {
        val r = StudioLogic.resolveScriptSection(items, runActive = false, runIndex = -1, script = secs, manualSectionId = null)
        assertEquals(1L, r?.first?.id)
        assertEquals(ScriptSource.MANUAL, r?.second)
    }

    @Test
    fun `stale link to deleted script section degrades safely to manual`() {
        val stale = listOf(ScheduleItem(10, "افتتاح", SegmentKind.LIVE, 60, null, "", 99L))
        val r = StudioLogic.resolveScriptSection(stale, runActive = true, runIndex = 0, script = secs, manualSectionId = 3L)
        assertEquals(ScriptSource.MANUAL, r?.second)
        assertEquals(3L, r?.first?.id)
    }

    @Test
    fun `invalid link ids are ignored`() {
        // صفر وسالب لا يُعتبران ربطًا صالحًا
        val bad = listOf(ScheduleItem(1, "x", SegmentKind.SPEECH, 60, null, "", 0L))
        val r = StudioLogic.resolveScriptSection(bad, runActive = true, runIndex = 0, script = secs, manualSectionId = null)
        assertEquals(1L, r?.first?.id)
        val manualIgnored = StudioLogic.resolveScriptSection(items, false, -1, secs, -4L)
        assertEquals(1L, manualIgnored?.first?.id)
    }

    @Test
    fun `empty script resolves to null`() {
        assertNull(StudioLogic.resolveScriptSection(items, true, 0, emptyList(), 1L))
    }

    @Test
    fun `run index out of bounds is safe`() {
        val r = StudioLogic.resolveScriptSection(items, runActive = true, runIndex = 99, script = secs, manualSectionId = 2L)
        assertEquals(ScriptSource.MANUAL, r?.second)
        assertEquals(2L, r?.first?.id)
    }

    @Test
    fun `on air aggregates mic recording and music`() {
        assertTrue(StudioLogic.status(micLive = true, recording = false, musicPlaying = false).onAir)
        assertTrue(StudioLogic.status(micLive = false, recording = true, musicPlaying = false).onAir)
        assertTrue(StudioLogic.status(micLive = false, recording = false, musicPlaying = true).onAir)
        assertTrue(StudioLogic.status(micLive = true, recording = true, musicPlaying = true).onAir)
        assertFalse(StudioLogic.status(micLive = false, recording = false, musicPlaying = false).onAir)
    }

    @Test
    fun `clock text formats hh mm ss in fixed zone`() {
        val utc = java.util.TimeZone.getTimeZone("UTC")
        assertEquals("00:00:00", StudioLogic.clockText(0L, utc))
        assertEquals("01:01:01", StudioLogic.clockText(3_661_000L, utc))
        assertEquals("23:59:59", StudioLogic.clockText(86_399_000L, utc))
        assertEquals("00:00:00", StudioLogic.clockText(86_400_000L, utc))
    }

    @Test
    fun `script link survives persistence roundtrip and sanitization`() {
        val dir = File(System.getProperty("java.io.tmpdir"), "sr-studio-test-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val f = File(dir, "schedule.json")
            val store = ScheduleStore(f)
            val a = store.add("افتتاح", SegmentKind.LIVE, 60, null, "", 2L)
            store.add("نشيد", SegmentKind.RECORDING, 60, null, "", null)
            store.add("تالفة", SegmentKind.SPEECH, 60, null, "", -7L)

            val reloaded = ScheduleStore(f)
            assertEquals(3, reloaded.items().size)
            assertEquals(2L, reloaded.items().first { it.id == a.id }.scriptSectionId)
            assertNull(reloaded.items()[1].scriptSectionId)
            assertNull(reloaded.items()[2].scriptSectionId) // سالب → null
        } finally {
            dir.deleteRecursively()
        }
    }
}
