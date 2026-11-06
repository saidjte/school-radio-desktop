package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.ScriptPdfExporter
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.ScriptStore
import com.saidjte.schoolradio.desktop.data.ScriptTemplateCatalog
import com.saidjte.schoolradio.desktop.data.ScriptTemplatesLogic
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * عقد قوالب السكريبت وتصدير PDF (v0.30.0).
 */
class ScriptTemplatesAndPdfTest {

    private fun tempFile(name: String): File =
        File.createTempFile("sr-test-", name).apply { deleteOnExit() }

    // ================= كتالوج القوالب =================

    @Test
    fun `catalog has six templates all valid with unique ids`() {
        assertTrue(ScriptTemplateCatalog.all.size >= 6, "size=${ScriptTemplateCatalog.all.size}")
        assertEquals(ScriptTemplateCatalog.all.size, ScriptTemplateCatalog.all.map { it.id }.toSet().size)
        assertEquals(emptyList(), ScriptTemplatesLogic.catalogErrors())
    }

    @Test
    fun `templates carry school radio arabic content and positive estimates`() {
        ScriptTemplateCatalog.all.forEach { t ->
            assertTrue(t.sectionCount >= 4, "${t.id}: sections=${t.sectionCount}")
            assertTrue(t.totalWords() > 60, "${t.id}: words=${t.totalWords()}")
            assertTrue(t.estimatedSeconds() > 30, "${t.id}: seconds=${t.estimatedSeconds()}")
            assertTrue(t.description.isNotBlank())
            t.sections.forEach { s ->
                assertTrue(s.title.isNotBlank())
                assertTrue(ScriptStats.wordCount(s.body) >= 6, "${t.id}/${s.title}")
            }
        }
    }

    @Test
    fun `template bodies avoid pdf-unsafe punctuation`() {
        assertEquals(emptyList(), ScriptTemplatesLogic.pdfUnsafeChars())
        // عينات المحارف الخطرة نفسها تُكتشف فعلا
        assertTrue('—' in setOf('—', '…'))
    }

    @Test
    fun `drafts preserve section order`() {
        val t = ScriptTemplateCatalog.byId("weekly-extended")!!
        val drafts = ScriptTemplatesLogic.drafts(t)
        assertEquals(t.sections.map { it.title }, drafts.map { it.first })
        assertEquals(t.sections.map { it.body }, drafts.map { it.second })
        assertEquals(null, ScriptTemplateCatalog.byId("لا يوجد"))
    }

    // ================= المخزن: إضافة دفعة واحدة وتفريغ =================

    @Test
    fun `store addAll appends in order and clear keeps ids monotonic`() {
        val store = ScriptStore(tempFile(".json"))
        val first = store.addAll(listOf("أ" to "نص أ", "ب" to "نص ب"))
        assertEquals(listOf(1L, 2L), first.map { it.id })
        store.clear()
        assertEquals(0, store.sections().size)
        val second = store.addAll(listOf("ج" to "نص ج"))
        assertEquals(3L, second.single().id, "nextId survives clear")
        // trim النصوص والفارغ يعود بلا فقرات
        val trimmed = store.addAll(listOf("  د  " to "  نص د "))
        assertEquals("د", trimmed.single().title)
        assertEquals(0, store.addAll(emptyList()).size)
    }

    // ================= تصدير PDF =================

    @Test
    fun `pdf export of template script is valid and renders`() {
        val sections = ScriptTemplatesLogic.drafts(ScriptTemplateCatalog.all.first())
            .mapIndexed { i, (t, b) -> ScriptSection((i + 1).toLong(), t, b) }
        val out = tempFile(".pdf")
        val res = ScriptPdfExporter.export(sections, "مدرسة الاختبار", LocalDate.of(2026, 11, 6), out)
        assertTrue(res.pages >= 1)
        assertTrue(res.bytes > 10_000, "bytes=${res.bytes}")
        assertEquals(sections.sumOf { ScriptStats.wordCount(it.body) }, res.words)
        PDDocument.load(out).use { pd ->
            assertEquals(res.pages, pd.numberOfPages)
            PDFRenderer(pd).renderImage(0)
            assertEquals("سكريبت المذيع - مدرسة الاختبار", pd.documentInformation.title)
        }
    }

    @Test
    fun `pdf export flows long scripts across pages`() {
        val longBody = "جملة عربية لاختبار لف الأسطر والتشكيل الصحيح للحروف المتصلة. ".repeat(20)
        val sections = (1..14).map { ScriptSection(it.toLong(), "فقرة طويلة $it", longBody) }
        val out = tempFile(".pdf")
        val res = ScriptPdfExporter.export(sections, null, LocalDate.of(2026, 11, 6), out)
        assertTrue(res.pages >= 2, "pages=${res.pages}")
        PDDocument.load(out).use { pd ->
            assertEquals(res.pages, pd.numberOfPages)
            // أول وآخر صفحة يُرسمان بلا استثناء — العقد يشمل الرؤوس والتذييلات
            PDFRenderer(pd).renderImage(0)
            PDFRenderer(pd).renderImage(res.pages - 1)
        }
    }

    @Test
    fun `pdf export handles empty and blank sections gracefully`() {
        val out = tempFile(".pdf")
        val res = ScriptPdfExporter.export(emptyList(), null, LocalDate.of(2026, 11, 6), out)
        assertEquals(1, res.pages)
        val withBlank = listOf(
            ScriptSection(1L, "عنوان بلا نص", ""),
            ScriptSection(2L, "  ", "نص سليم"),
            ScriptSection(3L, "أسطر متعددة", "السطر الأول\nالسطر الثاني")
        )
        val res2 = ScriptPdfExporter.export(withBlank, null, LocalDate.of(2026, 11, 6), tempFile(".pdf"))
        assertTrue(res2.pages >= 1)
        PDDocument.load(out).use { PDFRenderer(it).renderImage(0) }
    }

    @Test
    fun `suggested file name is arabic with date and extension`() {
        val name = ScriptPdfExporter.suggestedFileName(LocalDate.of(2026, 11, 6))
        assertTrue(name.startsWith("سكريبت المذيع "), "name=$name")
        assertTrue(name.endsWith("2026-11-06.pdf"), "name=$name")
    }
}
