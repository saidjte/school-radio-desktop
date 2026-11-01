package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.RecordingMeta
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.data.Backup
import com.saidjte.schoolradio.desktop.data.ExecutionsStore
import com.saidjte.schoolradio.desktop.data.FireStatus
import com.saidjte.schoolradio.desktop.data.LibraryDefaults
import com.saidjte.schoolradio.desktop.data.LibraryMath
import com.saidjte.schoolradio.desktop.data.MusicLibraryStore
import com.saidjte.schoolradio.desktop.data.PlaylistStore
import com.saidjte.schoolradio.desktop.data.Role
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.UsersStore
import com.saidjte.schoolradio.desktop.data.WeeklyMath
import com.saidjte.schoolradio.desktop.data.WeeklyStore
import java.io.File
import java.nio.file.Files
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * مواكبة قدرات نسخة الهاتف التسع — النواة الحتمية للميزات الجديدة:
 * الأدوار والبوابات، إعدادات المدرسة، الجدولة الأسبوعية وسجل التنفيذ،
 * المكتبة المصنفة، قائمة التشغيل الدائمة، النسخ الاحتياطي، توقيع المُسجِّل.
 */
class FeaturesParityTest {

    private fun tmp(): File = Files.createTempDirectory("sr-parity").toFile()

    // ================= الأدوار والمستخدمون =================

    @Test
    fun `role matrix matches phone app`() {
        assertEquals("مشرف", Role.ADMIN.labelAr)
        assertEquals("معلم", Role.TEACHER.labelAr)
        assertEquals("طالب", Role.STUDENT.labelAr)
        assertTrue(Role.canManage(Role.ADMIN))
        assertTrue(Role.canManage(Role.TEACHER))
        assertFalse(Role.canManage(Role.STUDENT))
        assertTrue(Role.canAdminister(Role.ADMIN))
        assertFalse(Role.canAdminister(Role.TEACHER))
        assertFalse(Role.canAdminister(Role.STUDENT))
    }

    @Test
    fun `unknown role falls back to student least privilege`() {
        assertEquals(Role.STUDENT, Role.from(null))
        assertEquals(Role.STUDENT, Role.from(""))
        assertEquals(Role.STUDENT, Role.from("GHOST"))
        assertEquals(Role.ADMIN, Role.from("ADMIN"))
        assertEquals(Role.TEACHER, Role.from("TEACHER"))
    }

    @Test
    fun `users store unique names sanitize and current fallback`() {
        val dir = tmp()
        val store = UsersStore(File(dir, "users.json"))
        val admin = store.add("أمين المكتبة", Role.ADMIN)!!
        val teacher = store.add("أستاذ خالد", Role.TEACHER)!!
        assertNull(store.add("أستاذ خالد", Role.STUDENT)) // الاسم فريد
        assertNull(store.add("   ", Role.STUDENT)) // الاسم الفارغ مرفوض
        assertEquals(2, store.users().size)
        assertEquals(admin.id, store.currentUser()?.id) // أول مستخدم هو الحالي

        assertTrue(store.setCurrent(teacher.id))
        assertEquals(teacher.id, store.currentUser()?.id)
        assertTrue(store.delete(teacher.id))
        assertEquals(admin.id, store.currentUser()?.id) // fallback للأول

        // دور أجنبي في الملف يُعامل طالبًا
        File(dir, "users.json").writeText(
            """{"users":[{"id":9,"name":"زائر","role":"GHOST"}],"currentUserId":9}"""
        )
        val reloaded = UsersStore(File(dir, "users.json"))
        assertEquals(Role.STUDENT, reloaded.users().first { it.id == 9L }.role)
        assertEquals(9L, reloaded.currentUser()?.id)
    }

    // ================= إعدادات المدرسة =================

    @Test
    fun `settings store school name round trip and blank nullify`() {
        val dir = tmp()
        val store = SettingsStore(File(dir, "settings.json"))
        assertNull(store.schoolName())
        store.setSchoolName("مدرسة النور")
        assertEquals("مدرسة النور", store.schoolName())
        store.setSchoolName("   ")
        assertNull(store.schoolName())
        store.setSchoolName("مدرسة السلام")
        assertEquals("مدرسة السلام", SettingsStore(File(dir, "settings.json")).schoolName())
    }

    // ================= الجدولة الأسبوعية =================

    private val zone = TimeZone.getTimeZone("UTC")

    @Test
    fun `iso day and minutes computed from epoch`() {
        // 2026-01-05 هو اثنين ISO
        val monday = java.util.Calendar.getInstance(zone).apply {
            set(2026, 0, 5, 10, 30, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        assertEquals(1, WeeklyMath.isoDayOfWeek(monday, zone))
        assertEquals(10 * 60 + 30, WeeklyMath.minutesOfDay(monday, zone))
        assertEquals("2026-01-05", WeeklyMath.dateKey(monday, zone))
        assertEquals("الثلاثاء", WeeklyMath.dayName(2))
        assertEquals("الأحد", WeeklyMath.dayName(7))
        assertEquals("08:15", WeeklyMath.clockOf(8 * 60 + 15))
    }

    @Test
    fun `decide fires once within grace and never duplicates same date`() {
        val monday = java.util.Calendar.getInstance(zone).apply {
            set(2026, 0, 5, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val events = listOf(
            com.saidjte.schoolradio.desktop.data.WeeklyEvent(
                id = 1, title = "إذاعة الصباح", dayOfWeek = 1,
                startMinutes = 8 * 60 + 15, enabled = true, autoStartRun = true
            )
        )
        // قبل الموعد بعشر دقائق: لا قرار
        val at805 = monday + (8 * 60 + 5) * 60_000L
        assertTrue(WeeklyMath.decide(events, emptySet(), at805, zone).isEmpty())
        // بعد الموعد بدقيقتين: إطلاق
        val at817 = monday + (8 * 60 + 17) * 60_000L
        val d = WeeklyMath.decide(events, emptySet(), at817, zone)
        assertEquals(1, d.size)
        assertEquals(FireStatus.FIRED, d[0].status)
        assertEquals("2026-01-05", d[0].dateKey)
        // بعد القيد: لا تكرار في نفس التاريخ
        assertTrue(WeeklyMath.decide(events, setOf("1@2026-01-05"), at817, zone).isEmpty())
        // نافذة السماح تجاوزت: فائت
        val at845 = monday + (8 * 60 + 45) * 60_000L
        val missed = WeeklyMath.decide(events, emptySet(), at845, zone)
        assertEquals(1, missed.size)
        assertEquals(FireStatus.MISSED, missed[0].status)
        // يوم آخر: لا قرار
        val tuesday = monday + 86_400_000L
        assertTrue(WeeklyMath.decide(events, emptySet(), tuesday, zone).isEmpty())
        // حدث معطّل: لا قرار
        val disabled = listOf(events[0].copy(enabled = false))
        assertTrue(WeeklyMath.decide(disabled, emptySet(), at817, zone).isEmpty())
    }

    @Test
    fun `missedBetween detects events while app was closed capped at 31 days`() {
        val monday = java.util.Calendar.getInstance(zone).apply {
            set(2026, 0, 5, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val events = listOf(
            com.saidjte.schoolradio.desktop.data.WeeklyEvent(
                id = 1, title = "إذاعة الصباح", dayOfWeek = 1,
                startMinutes = 8 * 60, enabled = true, autoStartRun = false
            )
        )
        val from = monday // الاثنين منتصف الليل
        val to = monday + 3 * 86_400_000L // الخميس منتصف الليل
        val missed = WeeklyMath.missedBetween(events, emptySet(), from, to, zone)
        assertEquals(1, missed.size) // إذاعة الاثنين 08:00 فائتة فقط
        assertEquals(FireStatus.MISSED, missed[0].status)
        // المقيّد مسبقًا لا يُعلن فائتًا
        assertTrue(WeeklyMath.missedBetween(events, setOf("1@2026-01-05"), from, to, zone).isEmpty())
        // ترتيب زمني معكوس: لا شيء
        assertTrue(WeeklyMath.missedBetween(events, emptySet(), to, from, zone).isEmpty())
    }

    @Test
    fun `weekly store persists sanitize and toggle`() {
        val dir = tmp()
        val store = WeeklyStore(File(dir, "weekly.json"))
        val e = store.add("إذاعة الصباح", 1, 8 * 60 + 15, true)!!
        assertNull(store.add("", 1, 600, true)) // عنوان فارغ مرفوض
        assertNull(store.add("x", 9, 600, true)) // يوم خارج المدى
        assertNull(store.add("x", 1, 2000, true)) // دقائق خارج المدى
        assertTrue(store.setEnabled(e.id, false))
        assertFalse(WeeklyStore(File(dir, "weekly.json")).events().first().enabled!!)
        assertNull(store.lastRunAt())
        store.touch(123L)
        assertEquals(123L, WeeklyStore(File(dir, "weekly.json")).lastRunAt())
    }

    @Test
    fun `executions store unique event date and capacity trim`() {
        val dir = tmp()
        val store = ExecutionsStore(File(dir, "executions.json"), capacity = 3)
        assertTrue(store.log(1, "صباح", "2026-01-05", 100, FireStatus.FIRED))
        assertFalse(store.log(1, "صباح", "2026-01-05", 200, FireStatus.FIRED)) // قيد فريد حدث+تاريخ
        assertTrue(store.log(1, "صباح", "2026-01-06", 300, FireStatus.FIRED)) // تاريخ آخر مقبول
        assertTrue(store.log(2, "ظهر", "2026-01-05", 150, FireStatus.MISSED))
        assertTrue(store.log(2, "ظهر", "2026-01-06", 350, FireStatus.MISSED))
        assertTrue(store.log(3, "عصر", "2026-01-06", 400, FireStatus.FIRED))
        assertEquals(3, store.entries().size) // القصص على السعة
        assertTrue(store.has(3, "2026-01-06"))
        assertFalse(store.has(3, "2026-01-07"))
        // إعادة تحميل تعيد آخر 3
        val reloaded = ExecutionsStore(File(dir, "executions.json"), capacity = 3)
        assertEquals(3, reloaded.entries().size)
        assertTrue(reloaded.has(3, "2026-01-06"))
    }

    // ================= المكتبة المصنفة =================

    @Test
    fun `library store defaults seven categories custom and search`() {
        val dir = tmp()
        val store = MusicLibraryStore(File(dir, "library.json"))
        assertEquals(7, store.categories().size)
        assertEquals(LibraryDefaults.CATEGORIES, store.categories())
        // تصنيف مخصص + منع التكرار
        assertTrue(store.addCategory("جينغلات المدرسة"))
        assertFalse(store.addCategory("جينغلات المدرسة"))
        assertFalse(store.addCategory("  "))
        assertEquals(8, store.categories().size)
        // تصنيفات افتراضية لا تُحذف
        assertFalse(store.deleteCategory("القرآن الكريم"))
        assertTrue(store.deleteCategory("جينغلات المدرسة"))
        assertEquals(7, store.categories().size)

        // إضافة ملفات
        val f1 = File(dir, "nashid1.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val f2 = File(dir, "quran2.wav").apply { writeBytes(byteArrayOf(4, 5)) }
        val added = store.addFiles(listOf(f1, f2), "الأناشيد المدرسية")
        assertEquals(2, added.size)
        assertEquals("الأناشيد المدرسية", added[0].category)
        // تصنيف غريب يعود للافتراضي — ملف جديد لم يُدرج من قبل
        val f1b = File(dir, "nashid1b.mp3").apply { writeBytes(byteArrayOf(7, 8)) }
        val added2 = store.addFiles(listOf(f1b), "تصنيف شبح")
        assertEquals("فقرات متنوعة", added2.first().category)
        // منع التكرار
        assertTrue(store.addFiles(listOf(f1), "الأناشيد المدرسية").isEmpty())

        // البحث الخالص
        assertTrue(LibraryMath.matches("نشيد الصباح", "الأناشيد", "صباح"))
        assertTrue(LibraryMath.matches("نشيد الصباح", "الأناشيد", "أناشيد"))
        assertFalse(LibraryMath.matches("نشيد الصباح", "الأناشيد", "رياضة"))
        assertTrue(LibraryMath.matches("نشيد", "الأناشيد", ""))

        // الحذف يعدّل العناصر — حذف تصنيف عنده عناصر يعيدها للافتراضي
        val custom = store.categories().last()
        assertTrue(store.deleteCategory(custom) == false) // افتراضي محمي

        // إعادة الربط
        val f3 = File(dir, "moved.mp3").apply { writeBytes(byteArrayOf(9)) }
        assertTrue(store.relink(added[0].id, f3))
        assertFalse(store.relink(added[0].id, File(dir, "ghost.mp3")))
        assertEquals(f3.path, store.entries().first { it.id == added[0].id }.path)
    }

    @Test
    fun `library missing paths detection`() {
        val dir = tmp()
        val store = MusicLibraryStore(File(dir, "library.json"))
        val f1 = File(dir, "a.mp3").apply { writeBytes(byteArrayOf(1)) }
        store.addFiles(listOf(f1), "فقرات متنوعة")
        assertTrue(store.missingPaths().isEmpty())
        f1.delete()
        assertEquals(setOf(f1.path), store.missingPaths())
    }

    // ================= قائمة التشغيل الدائمة (مسماة) =================

    @Test
    fun `playlist store persists paths index repeat`() {
        val dir = tmp()
        val store = PlaylistStore(File(dir, "playlist.json"))
        assertFalse(store.repeatAll())
        store.setPaths(store.activeId(), listOf("/a.mp3", "/b.mp3"), 1)
        store.setRepeat(true)
        val reloaded = PlaylistStore(File(dir, "playlist.json"))
        assertEquals(listOf("/a.mp3", "/b.mp3"), reloaded.paths())
        assertEquals(1, reloaded.currentIndex())
        assertTrue(reloaded.repeatAll())
        reloaded.setIndex(reloaded.activeId(), -1)
        assertEquals(-1, reloaded.currentIndex())
        reloaded.clear(reloaded.activeId())
        assertEquals(0, reloaded.paths().size)
        assertTrue(reloaded.repeatAll()) // التكرار يبقى بعد المسح
    }

    // ================= النسخ الاحتياطي =================

    @Test
    fun `backup export verify restore with safety copies`() {
        val dir = tmp()
        // بيانات حقيقية في مخازن
        val users = UsersStore(File(dir, "users.json"))
        users.add("أمين", Role.ADMIN)
        val settings = SettingsStore(File(dir, "settings.json"))
        settings.setSchoolName("مدرسة النور")
        val script = com.saidjte.schoolradio.desktop.data.ScriptStore(File(dir, "script.json"))
        script.add("مقدمة", "بسم الله")
        val target = File(dir, "backup.schoolradio.json")

        val report = Backup.export(target, dir, "0.9.0")
        assertTrue(report.ok, report.message)
        assertTrue(target.isFile)

        // التحقق يمر ويعيد المخازن
        val stores = Backup.verify(target)
        assertTrue(stores != null && stores.containsKey("users.json"))
        // ملف تالف يُرفض
        target.writeText("{ not json")
        assertNull(Backup.verify(target))
        // إصدار مستقبلي يُرفض
        target.writeText("""{"app":"school-radio-desktop","format_version":99,"stores":{}}""")
        assertNull(Backup.verify(target))
        // معرّف تطبيق آخر يُرفض
        target.writeText("""{"app":"other","format_version":1,"stores":{"users.json":"{}"}}""")
        assertNull(Backup.verify(target))

        // استعادة بعد تعديل البيانات الحالية — مع نسخ أمان
        assertTrue(Backup.export(target, dir, "0.9.0").ok) // نعيد النسخة السليمة بعد فشل التحقق أعلاه
        settings.setSchoolName("مدرسة متغيرة")
        val restore = Backup.restore(target, dir)
        assertTrue(restore.ok, restore.message)
        assertEquals("مدرسة النور", SettingsStore(File(dir, "settings.json")).schoolName())
        // نسخ الأمان موجودة
        assertTrue(dir.listFiles { f -> f.name.startsWith("settings.json.bak-") }!!.isNotEmpty())
    }

    // ================= توقيع المُسجِّل =================

    @Test
    fun `recording meta sidecar write load move delete`() {
        val dir = tmp()
        val wav = File(dir, "بث-مباشر_20260105_080000.wav").apply { writeBytes(byteArrayOf(1, 2)) }
        val sidecar = RecordingMeta.sidecarOf(wav)
        assertEquals(File(dir, "بث-مباشر_20260105_080000.rec.json"), sidecar)
        assertNull(RecordingMeta.load(sidecar)) // لا توقيع بعد

        val meta = RecordingMeta("أحمد", "طالب", 1000L, 5000L)
        assertTrue(meta.save(sidecar))
        val loaded = RecordingMeta.load(sidecar)
        assertEquals("أحمد", loaded?.recorderName)
        assertEquals("طالب", loaded?.recorderRole)
        assertEquals(5000L, loaded?.durationMs)

        // الماسح يقرأ التوقيع (قبل أي استبدال لاحق)
        val items = RecordingsScanner.scan(dir)
        assertEquals("أحمد", items.first().recorderName)
        assertEquals("طالب", items.first().recorderRole)

        // توقيع بلا اسم يُرفض عند التحميل
        RecordingMeta("  ", null, null, null).save(sidecar)
        assertNull(RecordingMeta.load(sidecar))

        // النقل مع إعادة التسمية والحذف
        val renamed = File(dir, "افتتاحية.wav")
        assertTrue(wav.renameTo(renamed))
        RecordingsScanner.moveMeta(wav, renamed)
        assertTrue(RecordingMeta.sidecarOf(renamed).isFile)
        assertTrue(RecordingMeta.sidecarOf(wav).exists().not())
        RecordingsScanner.deleteMeta(renamed)
        assertFalse(RecordingMeta.sidecarOf(renamed).isFile)
    }
}
