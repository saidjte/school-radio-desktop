package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.LibraryDefaults
import com.saidjte.schoolradio.desktop.data.MusicLibraryStore
import com.saidjte.schoolradio.desktop.data.PlaylistDoc
import com.saidjte.schoolradio.desktop.data.PlaylistMath
import com.saidjte.schoolradio.desktop.data.PlaylistStore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * قوائم التشغيل المسماة والتصنيف الموسيقي — النواة الحتمية لنسخة v0.12.0:
 * عدة قوائم بأسماء تُدار (إنشاء/تسمية/حذف/تفعيل) وواحدة منها «الجارية»،
 * ترحيل ملف النسخة السابقة (قائمة وحيدة) بلا أي فقد، قيد قائمة واحدة على
 * الأقل، نقل مقاطع المكتبة بين التصنيفات، وعدّاد المقاطع لكل تصنيف.
 */
class PlaylistsAndCategoriesTest {

    private fun tmp(): File = Files.createTempDirectory("sr-playlists-test").toFile()

    // ================= القوائم المسماة =================

    @Test
    fun `fresh store has one default active playlist`() {
        val store = PlaylistStore(File(tmp(), "playlist.json"))
        assertEquals(1, store.playlists().size)
        assertEquals("القائمة الرئيسية", store.active().name)
        assertEquals(store.activeId(), store.playlists().first().id)
        assertEquals(0, store.paths().size)
        assertEquals(-1, store.currentIndex())
    }

    @Test
    fun `legacy v1 doc migrates to named playlist without loss`() {
        val dir = tmp()
        val f = File(dir, "playlist.json")
        // ملف بنسق النسخة السابقة تمامًا: مسارات + جاري + تكرار بلا قوائم مسماة
        f.writeText("""{"paths":["/a.mp3","/b.mp3","/c.mp3"],"currentIndex":2,"repeatAll":true}""")
        val store = PlaylistStore(f)
        assertEquals(1, store.playlists().size)
        assertEquals("القائمة الرئيسية", store.active().name)
        assertEquals(listOf("/a.mp3", "/b.mp3", "/c.mp3"), store.paths())
        assertEquals(2, store.currentIndex())
        assertTrue(store.repeatAll())
        // الترحيل يُكتب على القرص فورًا — إعادة الفتح تجد القائمة المسماة مباشرة
        val again = PlaylistStore(f)
        assertEquals(1, again.playlists().size)
        assertEquals("القائمة الرئيسية", again.active().name)
        assertEquals(2, again.currentIndex())
        // والمقاليد القديمة (paths/index في الجذر) لم تبقَ في الوثيقة
        val raw = f.readText()
        assertTrue(raw.contains("playlists"))
        assertTrue(raw.contains("القائمة الرئيسية"))
        assertFalse(raw.contains("\"repeatAll\": true") && raw.contains("\"paths\" : ["))
    }

    @Test
    fun `create activates new empty playlist and switch back preserves content`() {
        val store = PlaylistStore(File(tmp(), "playlist.json"))
        val main = store.activeId()
        store.setPaths(main, listOf("/a.mp3", "/b.mp3"), 1)
        val p = store.create("فقرة الصباح")
        assertTrue(p != null && p.id > 0L)
        assertEquals(p!!.id, store.activeId())
        assertEquals(0, store.paths().size) // الجارية الجديدة فارغة
        assertTrue(store.setActive(main))
        assertEquals(listOf("/a.mp3", "/b.mp3"), store.paths())
        assertEquals(1, store.currentIndex())
        // قائمة جارية ثانية بمحتواها الخاص
        assertTrue(store.setActive(p.id))
        store.setPaths(p.id, listOf("/c.mp3"), 0)
        assertTrue(store.setActive(main))
        assertEquals(listOf("/a.mp3", "/b.mp3"), store.paths())
        assertTrue(store.setActive(p.id))
        assertEquals(listOf("/c.mp3"), store.paths())
    }

    @Test
    fun `duplicate and blank names rejected`() {
        val store = PlaylistStore(File(tmp(), "playlist.json"))
        assertTrue(store.create("أناشيد") != null)
        assertNull(store.create("أناشيد"))
        assertNull(store.create("  أناشيد  ".trim())) // نفس الاسم بعد التنقيح
        assertNull(store.create(""))
        assertNull(store.create("   "))
        // التسمية إلى اسم قائمة أخرى مرفوضة — أما الاسم نفسه فلا ضرر فيه
        val main = store.playlists().first { it.name == "القائمة الرئيسية" }.id
        assertFalse(store.rename(main, "أناشيد"))
        assertTrue(store.rename(main, "القائمة الرئيسية"))
        assertFalse(store.rename(999L, "غريب"))
    }

    @Test
    fun `delete keeps at least one playlist and reassigns active`() {
        val store = PlaylistStore(File(tmp(), "playlist.json"))
        val main = store.activeId()
        assertFalse(store.delete(main)) // وحيدة: لا تُحذف
        val p = store.create("ثانية")!!
        assertTrue(store.delete(p.id))
        assertEquals(main, store.activeId())
        assertEquals(1, store.playlists().size)
        // حذف الجارية يفعّل الأولى
        val q = store.create("ثالثة")!!
        assertTrue(store.delete(q.id))
        assertEquals(main, store.activeId())
        assertFalse(store.delete(4242L)) // غير موجودة
    }

    @Test
    fun `setPaths and setIndex clamp currentIndex inside bounds`() {
        val store = PlaylistStore(File(tmp(), "playlist.json"))
        val id = store.activeId()
        store.setPaths(id, listOf("/a.mp3", "/b.mp3"), 5) // خارج الحدود → يُلغى
        assertNull(store.active().currentIndex)
        assertEquals(-1, store.currentIndex())
        store.setIndex(id, 1)
        assertEquals(1, store.currentIndex())
        store.setIndex(id, 9) // خارج الحدود → يُلغى
        assertNull(store.active().currentIndex)
        store.setPaths(id, listOf("", "  ", "/c.mp3"), 2) // الفراغات تُنقّى والمؤشر يُقيّد
        assertEquals(listOf("/c.mp3"), store.paths())
        assertNull(store.active().currentIndex)
        // قائمة غير موجودة: لا شيء يحدث ولا يُرمى خطأ
        store.setPaths(777L, listOf("/x.mp3"), 0)
        store.setIndex(777L, 0)
        assertEquals(listOf("/c.mp3"), store.paths())
    }

    @Test
    fun `persisted round trip keeps named playlists and active`() {
        val dir = tmp()
        val store = PlaylistStore(File(dir, "playlist.json"))
        val main = store.activeId()
        store.setPaths(main, listOf("/a.mp3"), 0)
        val p = store.create("مسائية")!!
        store.setPaths(p.id, listOf("/b.mp3", "/c.mp3"), 1)
        store.setRepeat(true)
        val again = PlaylistStore(File(dir, "playlist.json"))
        assertEquals(2, again.playlists().size)
        assertEquals(p.id, again.activeId())
        assertEquals("مسائية", again.active().name)
        assertEquals(listOf("/b.mp3", "/c.mp3"), again.paths())
        assertEquals(1, again.currentIndex())
        assertTrue(again.repeatAll())
        assertTrue(again.setActive(main))
        assertEquals(listOf("/a.mp3"), again.paths())
    }

    @Test
    fun `playlist math resolve sanitizes broken documents`() {
        // هوية قارية تشير إلى قائمة محذوفة → الأولى تُفعّل
        val broken = PlaylistDoc(
            playlists = listOf(
                com.saidjte.schoolradio.desktop.data.NamedPlaylist(1, "أ", listOf("/a.mp3"), 0),
                com.saidjte.schoolradio.desktop.data.NamedPlaylist(2, "ب", emptyList(), null)
            ),
            activeId = 99L,
            nextId = 3L
        )
        val r = PlaylistMath.resolve(broken)
        assertEquals(1L, r.activeId)
        // قوائم بلا هويات صالحة → قائمة افتراضية تُنشأ
        val empty = PlaylistMath.resolve(PlaylistDoc())
        assertEquals(1, empty.playlists.size)
        assertEquals("القائمة الرئيسية", empty.playlists.first().name)
        // مؤشر سالب يُلغى
        val negative = PlaylistMath.resolve(
            PlaylistDoc(playlists = listOf(com.saidjte.schoolradio.desktop.data.NamedPlaylist(1, "أ", listOf("/a.mp3"), -3)))
        )
        assertNull(negative.playlists.first().currentIndex)
    }

    // ================= التصنيف الموسيقي =================

    private fun fakeAudio(dir: File, name: String): File =
        File(dir, name).apply { writeBytes(byteArrayOf(1, 2, 3)) }

    @Test
    fun `move entry to another category persists`() {
        val dir = tmp()
        val store = MusicLibraryStore(File(dir, "library.json"))
        val f1 = fakeAudio(dir, "nashid1.mp3")
        val f2 = fakeAudio(dir, "nashid2.mp3")
        store.addFiles(listOf(f1), "الأناشيد المدرسية")
        store.addFiles(listOf(f2), "القرآن الكريم")
        val e1 = store.entries().first { it.title == "nashid1" }
        assertEquals("الأناشيد المدرسية", e1.category)

        assertTrue(store.moveEntry(e1.id, "القرآن الكريم"))
        assertEquals("القرآن الكريم", store.entries().first { it.id == e1.id }.category)
        val reloaded = MusicLibraryStore(File(dir, "library.json"))
        assertEquals("القرآن الكريم", reloaded.entries().first { it.id == e1.id }.category)
        // نقل إلى تصنيف غير موجود أو مقطع غير موجود يُرفض
        assertFalse(store.moveEntry(e1.id, "تصنيف غير موجود"))
        assertFalse(store.moveEntry(999L, "القرآن الكريم"))
    }

    @Test
    fun `category counts reflect entries and moves`() {
        val dir = tmp()
        val store = MusicLibraryStore(File(dir, "library.json"))
        val files = (1..4).map { fakeAudio(dir, "f$it.mp3") }
        store.addFiles(files.take(3), "الأناشيد المدرسية")
        store.addFiles(files.drop(3), "المؤثرات الصوتية")
        var counts = store.countByCategory()
        assertEquals(3, counts["الأناشيد المدرسية"])
        assertEquals(1, counts["المؤثرات الصوتية"])
        assertNull(counts["القرآن الكريم"]) // تصنيف بلا مقاطع لا يظهر في العدّاد
        val moved = store.entries().first()
        store.moveEntry(moved.id, "القرآن الكريم")
        counts = store.countByCategory()
        assertEquals(2, counts["الأناشيد المدرسية"])
        assertEquals(1, counts["القرآن الكريم"])
    }

    @Test
    fun `deleting custom category returns its entries to fallback`() {
        val dir = tmp()
        val store = MusicLibraryStore(File(dir, "library.json"))
        store.addCategory("فقرات الطلاب")
        val f = fakeAudio(dir, "x.mp3")
        store.addFiles(listOf(f), "فقرات الطلاب")
        val e = store.entries().first()
        assertTrue(store.deleteCategory("فقرات الطلاب"))
        assertEquals(LibraryDefaults.FALLBACK_CATEGORY, store.entries().first { it.id == e.id }.category)
        assertFalse(store.deleteCategory("الأناشيد المدرسية")) // الافتراضية محمية
    }

    @Test
    fun `import duplicate files not added twice`() {
        val dir = tmp()
        val store = MusicLibraryStore(File(dir, "library.json"))
        val f = fakeAudio(dir, "same.mp3")
        assertTrue(store.addFiles(listOf(f), "فقرات متنوعة").added.size == 1)
        assertTrue(store.addFiles(listOf(f), "فقرات متنوعة").added.isEmpty())
        assertEquals(1, store.entries().size)
    }

    @Test
    fun `named playlist ids never collide with nextId`() {
        val store = PlaylistStore(File(tmp(), "playlist.json"))
        val a = store.create("أ")!!
        val b = store.create("ب")!!
        assertNotEquals(a.id, b.id)
        assertTrue(b.id > a.id)
        // بعد الحذف والإنشاء لا تُعاد الهويات (عدّاد تصاعدي دائم)
        store.delete(b.id)
        val c = store.create("ج")!!
        assertTrue(c.id > b.id)
    }
}
