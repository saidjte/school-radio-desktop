package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.AutoBackup
import com.saidjte.schoolradio.desktop.data.DropLogic
import com.saidjte.schoolradio.desktop.data.Recovery
import com.saidjte.schoolradio.desktop.data.SnapshotHistory
import com.saidjte.schoolradio.desktop.data.WorkspaceJson
import com.saidjte.schoolradio.desktop.data.WorkspaceLogic
import com.saidjte.schoolradio.desktop.ui.ShortcutAction
import com.saidjte.schoolradio.desktop.ui.resolveShortcut
import androidx.compose.ui.input.key.Key
import java.io.File
import java.nio.file.Files
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * عقد بيئة العمل الاحترافية (v0.39.0): تراجع/إعادة عام بمعلومات لقطات،
 * مساحة عمل قابلة للتخصيص بحبس القيم، سحب وإفلات بقرار نقّي، نسخ احتياطية
 * متدحرجة، واستعادة بعد انقطاع مفاجئ — كل ذلك نقي بلا واجهة ولا محرك.
 */
class WorkspaceReliabilityTest {

    private fun tempDir(): File = Files.createTempDirectory("ws39").toFile()

    // ================= سجل التراجع/الإعادة العام =================

    @Test
    fun `snapshot history undo redo round trip`() {
        val h = SnapshotHistory<String>()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        h.push("before-add")
        assertTrue(h.canUndo)
        // الحالة بعد التغيير: "قائمة فيها عنصر"
        assertEquals("before-add", h.undo(current = "after-add"))
        assertFalse(h.canUndo)
        assertTrue(h.canRedo)
        assertEquals("after-add", h.redo(current = "before-add"))
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
    }

    @Test
    fun `undo on empty history is a no-op`() {
        val h = SnapshotHistory<Int>()
        assertNull(h.undo(1))
        assertNull(h.redo(1))
    }

    @Test
    fun `new change after undo cuts the redo branch`() {
        val h = SnapshotHistory<String>()
        h.push("A")
        h.undo("B") // تراجع عن B نحو A
        assertTrue(h.canRedo)
        h.push("C") // تغيير جديد بعد التراجع يقطع فرع الإعادة
        assertFalse(h.canRedo)
        assertEquals("C", h.undo(current = "D"))
    }

    @Test
    fun `history cap evicts oldest and keeps bounded memory`() {
        // سقف 3: بعد عشرة تغييرات تبقى آخر ثلاث لقطات فقط
        val h = SnapshotHistory<Int>(cap = 3)
        for (i in 1..10) h.push(i)
        assertEquals(10, h.undo(current = 11))
        assertEquals(9, h.undo(current = 10))
        assertEquals(8, h.undo(current = 9))
        assertFalse(h.canUndo)
        // وذات العدّ على سجل مستقل يؤكد الحدّ المطلق
        val h2 = SnapshotHistory<Int>(cap = 3)
        for (i in 1..10) h2.push(i)
        assertEquals(3, countUndoable(h2))
    }

    private fun countUndoable(h: SnapshotHistory<Int>): Int {
        var n = 0
        var cur = 0
        while (h.canUndo) {
            cur = h.undo(cur) ?: break
            n++
        }
        return n
    }

    // ================= مساحة العمل القابلة للتخصيص =================

    private val tabs = setOf("STUDIO", "MUSIC", "MONTAGE", "SCRIPT")

    @Test
    fun `workspace defaults are professional and visible`() {
        val s = WorkspaceLogic.resolveState(null, tabs)
        assertEquals(1.0, s.fontScale)
        assertEquals(232.0, s.sidebarWidth)
        assertTrue(s.showRecordings && s.showSchedule && s.showScript && s.showBroadcast)
        assertFalse(s.rememberLastTab)
        assertNull(s.lastTab)
    }

    @Test
    fun `workspace values are clamped inside usability bounds`() {
        assertEquals(0.8, WorkspaceLogic.clampScale(0.1))
        assertEquals(1.5, WorkspaceLogic.clampScale(9.0))
        assertEquals(1.0, WorkspaceLogic.clampScale(null))
        assertEquals(1.0, WorkspaceLogic.clampScale(Double.NaN))
        assertEquals(180.0, WorkspaceLogic.clampSidebarWidth(5.0))
        assertEquals(360.0, WorkspaceLogic.clampSidebarWidth(999.0))
        assertEquals(232.0, WorkspaceLogic.clampSidebarWidth(null))
        val s = WorkspaceLogic.resolveState(
            WorkspaceJson(fontScale = 99.0, sidebarWidth = 1.0, showRecordings = false),
            tabs
        )
        assertEquals(1.5, s.fontScale)
        assertEquals(180.0, s.sidebarWidth)
        assertFalse(s.showRecordings)
        // بقية اللوحات ظاهرة افتراضيًا فلا شاشة فارغة صامتة
        assertTrue(s.showSchedule && s.showScript && s.showBroadcast)
    }

    @Test
    fun `last tab is accepted only if it is a known tab name`() {
        val ok = WorkspaceLogic.resolveState(WorkspaceJson(lastTab = "MUSIC", rememberLastTab = true), tabs)
        assertEquals("MUSIC", ok.lastTab)
        assertTrue(ok.rememberLastTab)
        val gone = WorkspaceLogic.resolveState(WorkspaceJson(lastTab = "GHOST"), tabs)
        assertNull(gone.lastTab)
        assertFalse(gone.rememberLastTab)
    }

    // ================= سحب وإفلات الملفات الصوتية =================

    @Test
    fun `drop filters audio files and counts skipped`() {
        val dir = tempDir()
        val mp3 = File(dir, "a.mp3").apply { writeBytes(byteArrayOf(1)) }
        val wav = File(dir, "b.wav").apply { writeBytes(byteArrayOf(1)) }
        val txt = File(dir, "c.txt").apply { writeBytes(byteArrayOf(1)) }
        val ghost = File(dir, "missing.mp3") // غير موجود على القرص
        val d = DropLogic.decide(listOf(mp3, txt, wav, ghost))
        assertEquals(listOf(mp3, wav), d.audioFiles)
        assertEquals(2, d.skippedUnsupported)
        assertTrue(d.hasAudio)
        assertTrue(d.message("المكتبة")!!.contains("أُضيف 2"))
    }

    @Test
    fun `drop with no audio gives an explanatory message`() {
        val dir = tempDir()
        val txt = File(dir, "c.txt").apply { writeBytes(byteArrayOf(1)) }
        val d = DropLogic.decide(listOf(txt))
        assertFalse(d.hasAudio)
        assertTrue(d.message("المكتبة")!!.contains("لا ملفات صوتية"))
        assertNull(DropLogic.decide(emptyList()).message("المكتبة"))
    }

    @Test
    fun `drop destination follows the current tab`() {
        assertEquals(DropLogic.Destination.MONTAGE, DropLogic.destinationFor("MONTAGE"))
        assertEquals(DropLogic.Destination.LIBRARY, DropLogic.destinationFor("MUSIC"))
        assertEquals(DropLogic.Destination.LIBRARY, DropLogic.destinationFor("STUDIO"))
        assertEquals(DropLogic.Destination.LIBRARY, DropLogic.destinationFor(null))
    }

    // ================= النسخ الاحتياطية المتدحرجة =================

    @Test
    fun `autosnap copies changed stores and skips unchanged ones`() {
        val dir = tempDir()
        val script = File(dir, "script.json")
        script.writeText("""{"sections":[]}""")
        val r1 = AutoBackup.snapshot(dir, names = listOf("script.json"), nowMs = 1000)
        assertEquals(listOf("script.json"), r1.copied)
        // لا تغيير → لا نسخ جديدة
        val r2 = AutoBackup.snapshot(dir, names = listOf("script.json"), nowMs = 2000)
        assertTrue(r2.isEmpty)
        script.writeText("""{"sections":[{"id":1}]}""")
        val r3 = AutoBackup.snapshot(dir, names = listOf("script.json"), nowMs = 3000)
        assertEquals(listOf("script.json"), r3.copied)
        assertEquals(2, AutoBackup.backupsDir(dir).listFiles()!!.size)
    }

    @Test
    fun `autosnap rotation keeps only the newest copies`() {
        val dir = tempDir()
        val f = File(dir, "playlist.json")
        for (i in 1..6) {
            f.writeText("""{"n":$i}""")
            AutoBackup.snapshot(dir, names = listOf("playlist.json"), keep = 3, nowMs = 1000L * i)
        }
        val copies = AutoBackup.backupsDir(dir).listFiles()!!.filter { it.isFile }
        assertEquals(3, copies.size)
        // الأحدث تبقى: آخر طابع 6000
        assertEquals("playlist.json.6000.bak", copies.maxByOrNull { it.name }!!.name)
        assertEquals("playlist.json.6000.bak", AutoBackup.newestBackup(AutoBackup.backupsDir(dir), "playlist.json")!!.name)
    }

    @Test
    fun `recovery restores corrupt store from newest valid backup`() {
        val dir = tempDir()
        File(dir, "script.json").writeText("""{"sections":[{"id":1,"title":"أ"}]}""")
        AutoBackup.snapshot(dir, names = listOf("script.json"), nowMs = 1000)
        // تلف بعد اللقطة: JSON مبتور
        File(dir, "script.json").writeText("""{"sections":[{"id":2,"tit""")
        val report = Recovery.recoverCorruptStores(dir, names = listOf("script.json"), nowMs = 2000)
        assertEquals(listOf("script.json"), report.recoveredFromBackup)
        assertEquals(listOf("script.json"), report.quarantined)
        assertTrue(report.notices.first().contains("استُعيد"))
        // المحتوى عاد للقطة الأخيرة السليمة
        val text = File(dir, "script.json").readText()
        assertTrue(text.contains("\"id\":1"))
        // الأصل التالف معزول لا محو
        val qdir = Recovery.corruptDir(dir)
        assertEquals(1, qdir.listFiles()!!.size)
    }

    @Test
    fun `recovery quarantines corrupt store with no backup`() {
        val dir = tempDir()
        File(dir, "users.json").writeText("not-json{{{")
        val report = Recovery.recoverCorruptStores(dir, names = listOf("users.json"), nowMs = 5000)
        assertTrue(report.recoveredFromBackup.isEmpty())
        assertEquals(listOf("users.json"), report.quarantined)
        assertFalse(File(dir, "users.json").exists())
        assertTrue(Recovery.corruptDir(dir).listFiles()!!.size == 1)
    }

    @Test
    fun `parse check accepts valid json and rejects broken text`() {
        assertTrue(Recovery.isParseableJsonText("""{"a":1,"b":[2,3]}"""))
        assertFalse(Recovery.isParseableJsonText("""{"a":1,,}"""))
        assertFalse(Recovery.isParseableJsonText(""))
        val missing = tempDir().resolve("x.json")
        assertTrue(Recovery.isParseableJson(missing)) // الغائب ليس تالفًا
    }

    // ================= قفل التشغيل: كشف الانقطاع المفاجئ =================

    @Test
    fun `run lock detects interrupted previous session`() {
        val dir = tempDir()
        assertFalse(Recovery.wasInterrupted(dir))
        Recovery.acquireLock(dir, nowMs = 111)
        assertTrue(Recovery.wasInterrupted(dir))
        Recovery.releaseLock(dir)
        assertFalse(Recovery.wasInterrupted(dir))
    }

    // ================= الاختصارات الجديدة (v0.39.0) =================

    @Test
    fun `alt plus digits switches pages one to nine`() {
        assertEquals(ShortcutAction.SwitchTab(1), resolveShortcut(Key.One, ctrl = false, alt = true, meta = false))
        assertEquals(ShortcutAction.SwitchTab(8), resolveShortcut(Key.Eight, ctrl = false, alt = true, meta = false))
        // v0.42.0: الصفحة التاسعة «مكتبة الأصوات» تُفتح بـAlt+9
        assertEquals(ShortcutAction.SwitchTab(9), resolveShortcut(Key.Nine, ctrl = false, alt = true, meta = false))
        assertNull(resolveShortcut(Key.One, ctrl = true, alt = true, meta = false))
    }

    @Test
    fun `f1 opens shortcuts help and ctrl z y are contextual undo redo`() {
        assertEquals(ShortcutAction.ShowShortcuts, resolveShortcut(Key.F1, ctrl = false, alt = false, meta = false))
        assertEquals(ShortcutAction.ContextualUndo, resolveShortcut(Key.Z, ctrl = true, alt = false, meta = false))
        assertEquals(ShortcutAction.ContextualRedo, resolveShortcut(Key.Y, ctrl = true, alt = false, meta = false))
        assertEquals(ShortcutAction.ContextualRedo, resolveShortcut(Key.Z, ctrl = true, alt = false, meta = false, shift = true))
        // بلا Ctrl يبقى Z حرًّا غير مربوط في البروتوكول العام
        assertNull(resolveShortcut(Key.Z, ctrl = false, alt = false, meta = false))
    }
}
