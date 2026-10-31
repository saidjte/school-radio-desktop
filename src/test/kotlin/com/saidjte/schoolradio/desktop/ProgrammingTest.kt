package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.Json
import com.saidjte.schoolradio.desktop.data.ScheduleDoc
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.ScheduleStore
import com.saidjte.schoolradio.desktop.data.ScriptDoc
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.ScriptStore
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.SegmentStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** اختبارات سكريبت المذيع وجدولة الفقرات (v0.2.0). */
class ProgrammingTest {

    private fun tempDir(): File {
        val d = File(System.getProperty("java.io.tmpdir"), "sr-desktop-test-${System.nanoTime()}")
        d.mkdirs()
        return d
    }

    @Test
    fun `script stats counts arabic words with mixed whitespace`() {
        assertEquals(0, ScriptStats.wordCount(""))
        assertEquals(0, ScriptStats.wordCount("   \n\t  "))
        assertEquals(4, ScriptStats.wordCount("بسم الله الرحمن الرحيم"))
        assertEquals(3, ScriptStats.wordCount("واحد\nاثنان\tثلاثة"))
        assertEquals(2, ScriptStats.wordCount("  كلمة   كلمة  "))
    }

    @Test
    fun `script stats reading time rounds up`() {
        // 130 كلمة/دقيقة → 130 كلمة = 60 ثانية بالضبط
        val w130 = (1..130).joinToString(" ") { "كلمة" }
        assertEquals(60L, ScriptStats.secondsFor(w130))
        // 1 كلمة → ثانية واحدة (تقريب لأعلى)
        assertEquals(1L, ScriptStats.secondsFor("كلمة"))
        // 65 كلمة → 30 ثانية
        val w65 = (1..65).joinToString(" ") { "كلمة" }
        assertEquals(30L, ScriptStats.secondsFor(w65))
        assertEquals(0L, ScriptStats.secondsFor(""))
    }

    @Test
    fun `script store crud move and persistence with special characters`() {
        val dir = tempDir()
        try {
            val f = File(dir, "script.json")
            val store = ScriptStore(f)
            val s1 = store.add("افتتاحية", "السلام عليكم — \"باسم الله\" نبدأ\nسطر جديد")
            val s2 = store.add("تلاوة", "سورة الفاتحة")
            val s3 = store.add("نشرات", "أخبار اليوم")
            assertEquals(3, store.sections().size)
            assertTrue(s1.id < s2.id && s2.id < s3.id)

            // تحديث
            assertTrue(store.update(s2.id, "تلاوة جميلة", "سورة الفاتحة كاملة"))
            assertEquals("تلاوة جميلة", store.sections()[1].title)

            // نقل لأعلى ثم حدود مستحيلة
            assertTrue(store.move(s3.id, -1))
            assertEquals("نشرات", store.sections()[1].title)
            assertEquals("تلاوة جميلة", store.sections()[2].title)
            assertFalse(store.move(s1.id, -1)) // أول عنصر لا يصعد
            assertFalse(store.move(s3.id, +9))

            // حذف
            assertTrue(store.delete(s1.id))
            assertEquals(2, store.sections().size)

            // استرجاع من الملف: الحروف الخاصة والأسطر محفوظة حرفيًا
            val reloaded = ScriptStore(f)
            assertEquals(2, reloaded.sections().size)
            val original = store.sections().first { it.id == s3.id }
            val persisted = reloaded.sections().first { it.id == s3.id }
            assertEquals(original, persisted)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `script store rejects corrupt file and returns fallback`() {
        val dir = tempDir()
        try {
            val f = File(dir, "script.json")
            f.writeText("{ this is not json !!!")
            assertEquals(0, ScriptStore(f).sections().size)
            // ملف JSON بأقسام ناقصة الحقول → تعقيم بدل انهيار
            f.writeText("""{"sections":[{"id":7,"title":"فقط عنوان"}]}""")
            val store = ScriptStore(f)
            assertEquals(1, store.sections().size)
            assertEquals("", store.sections()[0].body)
            // المعرف السالب يُرفض
            f.writeText("""{"sections":[{"id":-3,"title":"x","body":"y"}]}""")
            assertEquals(0, ScriptStore(f).sections().size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `schedule math offsets clocks statuses and move`() {
        val items = listOf(
            ScheduleItem(1, "افتتاح", SegmentKind.LIVE, 300),
            ScheduleItem(2, "فاصل", SegmentKind.MUSIC, 90),
            ScheduleItem(3, "نشرات", SegmentKind.RECORDING, 0)
        )
        assertEquals(390L, ScheduleMath.totalSeconds(items))
        assertEquals(listOf(0L, 300L, 390L), ScheduleMath.offsetsSeconds(items))

        // بداية 7:55 → الفقرات: 07:55 ثم 08:00 (بعد 5 دقائق) ثم 08:01 (بعد 6.5 دقائق، تُقتطع للدقيقة)
        val clocks = ScheduleMath.plannedClocks(items, 7 * 60 + 55)
        assertEquals("07:55", clocks[0])
        assertEquals("08:00", clocks[1])
        assertEquals("08:01", clocks[2])
        // بلا بداية → كلها null
        assertEquals(listOf(null, null, null), ScheduleMath.plannedClocks(items, null))

        // لفّ الساعة: 23:50 + 20 دقيقة → 00:10
        assertEquals("00:10", ScheduleMath.clockOf(23 * 60 + 50 + 20))
        assertEquals("00:00", ScheduleMath.clockOf(24 * 60))
        assertEquals("23:59", ScheduleMath.clockOf(-1))

        // الحالات
        assertEquals(
            listOf(SegmentStatus.UPCOMING, SegmentStatus.UPCOMING, SegmentStatus.UPCOMING),
            ScheduleMath.statuses(-1, 3)
        )
        assertEquals(
            listOf(SegmentStatus.DONE, SegmentStatus.CURRENT, SegmentStatus.UPCOMING),
            ScheduleMath.statuses(1, 3)
        )
        assertEquals(
            listOf(SegmentStatus.DONE, SegmentStatus.DONE, SegmentStatus.CURRENT),
            ScheduleMath.statuses(2, 3)
        )

        // النقل
        assertEquals(listOf(2, 1, 3), ScheduleMath.moved(listOf(1, 2, 3), 1, -1))
        assertEquals(listOf(1, 3, 2), ScheduleMath.moved(listOf(1, 2, 3), 1, +1))
        assertEquals(listOf(1, 2, 3), ScheduleMath.moved(listOf(1, 2, 3), 0, -5))
        assertEquals(listOf(1, 2, 3), ScheduleMath.moved(listOf(1, 2, 3), 2, +1))
    }

    @Test
    fun `schedule store crud episode start and persistence`() {
        val dir = tempDir()
        try {
            val f = File(dir, "schedule.json")
            val store = ScheduleStore(f)
            assertNull(store.episodeStartMinutes())
            store.setEpisodeStart(495)
            assertEquals(495, store.episodeStartMinutes())
            store.setEpisodeStart(null)
            assertNull(store.episodeStartMinutes())
            store.setEpisodeStart(500)

            val a = store.add("افتتاح", SegmentKind.LIVE, 120, null, "")
            val b = store.add("نشيد", SegmentKind.RECORDING, 180, "/x/n.wav", "حادي")
            assertEquals(2, store.items().size)

            assertTrue(
                store.update(a.id, "افتتاح معدل", SegmentKind.SPEECH, 90, null, "ملاحظة")
            )
            assertEquals(SegmentKind.SPEECH, store.sections0()[0].kind)
            assertEquals(90L, store.sections0()[0].plannedSeconds)

            assertTrue(store.move(b.id, -1))
            assertEquals(b.id, store.items()[0].id)
            assertFalse(store.move(a.id, +7))
            assertTrue(store.delete(b.id))
            assertEquals(1, store.items().size)

            val reloaded = ScheduleStore(f)
            assertEquals(500, reloaded.episodeStartMinutes())
            assertEquals(1, reloaded.items().size)
            assertEquals("افتتاح معدل", reloaded.items()[0].title)
            assertEquals("ملاحظة", reloaded.items()[0].note)
            assertNotEquals(0L, reloaded.items()[0].id)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** وصول داخلي مصغّر لعناصر المخزن ضمن الاختبار. */
    private fun ScheduleStore.sections0(): List<ScheduleItem> = items()

    @Test
    fun `schedule store sanitizes foreign kind and negative duration`() {
        val dir = tempDir()
        try {
            val f = File(dir, "schedule.json")
            // enum أجنبي + مدة سالبة + معرف غير صالح
            f.writeText(
                """{"episodeStartMinutes":null,"items":[
                   {"id":1,"title":"سليمة","kind":"MUSIC","plannedSeconds":30},
                   {"id":-2,"title":"مرفوضة","kind":"SPEECH","plannedSeconds":5},
                   {"id":2,"title":"نوع مجهول","kind":"WHATEVER","plannedSeconds":-10}
                ]}""".trimIndent()
            )
            val store = ScheduleStore(f)
            assertEquals(2, store.items().size)
            assertEquals(SegmentKind.MUSIC, store.items()[0].kind)
            assertEquals(SegmentKind.SPEECH, store.items()[1].kind) // نوع مجهول → SPEECH
            assertEquals(0L, store.items()[1].plannedSeconds)        // سالبة → 0
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `json save is atomic and roundtrips arabic quotes and newlines`() {
        val dir = tempDir()
        try {
            val f = File(dir, "doc.json")
            val doc = ScriptDoc(
                sections = listOf(
                    ScriptSection(1, "عنوان", "نص فيه \"علامات\" و\nأسطر و \\ شرطات")
                )
            )
            assertTrue(Json.save(f, doc))
            assertFalse(File(dir, "doc.json.tmp").exists())
            val back = Json.load(f, ScriptDoc())
            assertEquals(doc, back)
            // تالف → fallback
            f.writeText("garbage {")
            val fallback = Json.load(f, ScriptDoc())
            assertEquals(0, fallback.sections.size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
