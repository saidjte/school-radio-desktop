package com.saidjte.schoolradio.desktop.data

import com.saidjte.schoolradio.desktop.APP_VERSION
import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.util.Matrix
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.Rectangle2D
import java.io.File
import java.text.AttributedString
import java.time.LocalDate

/**
 * تصدير سكريبت المذيع إلى ملف PDF (v0.30.0 «تصدير PDF»): وثيقة A4 بهوية التطبيق،
 * نص عربي منسق من اليمين بتشكيل كامل عبر AWT يُرسم أشكالًا متجهة داخل PDF
 * (جسر pdfbox-graphics2d) — فالملف حاد عند أي تكبير ولا يضمّن خطوطًا،
 * وكل صفحة لها رأس باسم المدرسة وتذييل برقمها وتاريخ التوليد.
 * التوليد كله محلي 100% دون اتصال.
 */
object ScriptPdfExporter {

    data class Result(
        val file: File,
        val pages: Int,
        val bytes: Long,
        val words: Int,
        val estimatedSeconds: Long
    )

    // ================= هندسة الصفحة (A4 بالنقاط) =================
    private const val PAGE_W = 595f
    private const val PAGE_H = 842f
    private const val MARGIN = 57f
    private const val TOP = 96f
    private const val BOTTOM = 68f

    // ================= ألوان الهوية (مطابقة ثيم التطبيق) =================
    private val C_PRIMARY = Color(0x0F4C67)
    private val C_ACCENT = Color(0x17708F)
    private val C_BODY = Color(0x1A2430)
    private val C_META = Color(0x5B6B7A)
    private val C_RULE = Color(0xD4DEE6)
    private val C_HEADING_BG = Color(0xF1F6F9)

    // ================= خط الهوية المدمج (OFL — Cairo: عربي + لاتيني وكل الترقيم) =================
    private val fontRegular: Font by lazy { loadFont("Cairo-Regular.ttf") }
    private val fontSemi: Font by lazy { loadFont("Cairo-SemiBold.ttf") }
    private val fontBold: Font by lazy { loadFont("Cairo-Bold.ttf") }

    private fun loadFont(name: String): Font =
        Font.createFont(
            Font.TRUETYPE_FONT,
            ScriptPdfExporter::class.java.classLoader.getResourceAsStream("fonts/$name")
        )

    /** المحارف التي لا يغطيها خط التصدير (تظهر مربعات) — تُستعمل للتحقق الآلي من النصوص. */
    fun missingGlyphs(text: String): List<String> {
        val f = fontRegular
        val found = sortedSetOf<String>()
        text.forEach { ch ->
            if (ch != '\n' && ch != '\r' && !f.canDisplay(ch)) found.add("U+%04X".format(ch.code))
        }
        return found.toList()
    }

    /** اسم ملف افتراضي مهذب: سكريبت المذيع 2026-11-06.pdf */
    fun suggestedFileName(date: LocalDate = LocalDate.now()): String =
        "سكريبت المذيع ${date}.pdf"

    fun export(
        sections: List<ScriptSection>,
        schoolName: String?,
        date: LocalDate,
        out: File
    ): Result {
        val words = ScriptStats.totalWords(sections)
        val estSeconds = ScriptStats.totalSeconds(sections)

        val doc = PDDocument()
        try {
            val page = Doc(doc)
            drawBody(page, sections, date)
            // المرحلة الثانية: رأس وتذييل كل صفحة (بعد معرفة العدد الكلي)
            val n = page.pages.size
            page.pages.forEachIndexed { i, p ->
                drawChrome(doc, p, schoolName, date, i + 1, n)
            }

            val info = doc.documentInformation
            info.title = "سكريبت المذيع - ${schoolName ?: "إذاعة مدرسية"}"
            info.author = schoolName ?: "إذاعة مدرسية احترافي"
            info.creator = "إذاعة مدرسية احترافي - نسخة الحاسوب v$APP_VERSION"
            info.subject = "سكريبت برنامج إذاعة مدرسية بتاريخ $date"

            out.parentFile?.mkdirs()
            doc.save(out)
            return Result(
                file = out,
                pages = page.pages.size,
                bytes = out.length(),
                words = words,
                estimatedSeconds = estSeconds
            )
        } finally {
            doc.close()
        }
    }

    // ================= مستند متعدد الصفحات: كانفس Graphics2D لكل صفحة =================

    private class Doc(val doc: PDDocument) {
        val pages = mutableListOf<PDPage>()
        private var g: PdfBoxGraphics2D = newG2(doc)
        // اصطلاح Graphics2D: المحور ص ينمو للأسفل من أعلى الصفحة (المكتبة
        // توضع الـForm على الصفحة فتطابق هذا الاصطلاح تلقائيًا)
        var y = TOP

        init { newPage() }

        fun canvas(): PdfBoxGraphics2D = g

        fun newPage() {
            if (pages.isNotEmpty()) {
                g.dispose()
                flushForm(g, doc, pages.last(), append = false)
            }
            val page = PDPage(PDRectangle(PAGE_W, PAGE_H))
            doc.addPage(page)
            pages += page
            g = newG2(doc)
            y = TOP
        }

        fun finish() {
            g.dispose()
            flushForm(g, doc, pages.last(), append = false)
        }

        private fun newG2(doc: PDDocument): PdfBoxGraphics2D {
            val gr = PdfBoxGraphics2D(doc, PAGE_W, PAGE_H)
            gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            gr.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            gr.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            gr.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            return gr
        }
    }

    /** يطبع Form XObject داخل الصفحة — إحداثيات الرسم من أعلى اليسار. */
    private fun flushForm(g: PdfBoxGraphics2D, doc: PDDocument, page: PDPage, append: Boolean) {
        val cs = if (append) {
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
        } else {
            PDPageContentStream(doc, page)
        }
        cs.transform(Matrix(1f, 0f, 0f, 1f, 0f, 0f))
        cs.drawForm(g.getXFormObject())
        cs.close()
    }

    // ================= المرحلة الأولى: محتوى السكريبت =================

    private fun drawBody(page: Doc, sections: List<ScriptSection>, date: LocalDate) {
        val contentW = PAGE_W - 2 * MARGIN
        try {
            if (sections.isEmpty()) {
                val gr = page.canvas()
                gr.setFont(fontRegular.deriveFont(13f))
                gr.setColor(C_META)
                page.y += 24f
                page.y = drawRtlLine(gr, "لا توجد فقرات في السكريبت بعد.", MARGIN, contentW, page.y)
                page.y += 22f
            } else {
                // كتلة العنوان (أول صفحة فقط)
                var gr = page.canvas()
                gr.setFont(fontBold.deriveFont(22f))
                gr.setColor(C_PRIMARY)
                page.y += 16f
                page.y = drawRtlLine(gr, "سكريبت المذيع", MARGIN, contentW, page.y, ALIGN_CENTER) + 10f

                gr.setFont(fontRegular.deriveFont(10.5f))
                gr.setColor(C_META)
                val meta = date.toString() + "  ·  " +
                        "${sections.size} فقرات  ·  " +
                        "${ScriptStats.totalWords(sections)} كلمة  ·  " +
                        "زمن القراءة التقديري ${durationAr(ScriptStats.totalSeconds(sections))}"
                page.y = drawRtlLine(gr, meta, MARGIN, contentW, page.y, ALIGN_CENTER) + 6f

                gr.setColor(C_ACCENT)
                gr.fill(Rectangle2D.Float(MARGIN, page.y, contentW, 1.6f))
                page.y += 30f

                // فقرات السكريبت
                sections.forEachIndexed { i, s ->
                    // تحكم الأرامل: العنوان مع أول سطرَي النص لا ينفصلان
                    if (page.y + 64f > PAGE_H - BOTTOM) page.newPage()
                    gr = page.canvas()

                    // شريط العنوان: خلفية فاتحة + رقم وعنوان يمينًا + مدة يسارًا
                    gr.setFont(fontSemi.deriveFont(14f))
                    val head = singleLayout(gr, "${i + 1}.  ${s.title.ifBlank { "بدون عنوان" }}")
                    val headH = head.ascent + head.descent
                    gr.setColor(C_HEADING_BG)
                    gr.fill(Rectangle2D.Float(MARGIN, page.y - 5f, contentW, headH + 10f))
                    gr.setColor(C_PRIMARY)
                    head.draw(gr, MARGIN + contentW - head.advance, page.y + head.ascent)
                    if (s.body.isNotBlank()) {
                        gr.setFont(fontRegular.deriveFont(9f))
                        gr.setColor(C_META)
                        gr.drawString(durationAr(ScriptStats.secondsFor(s.body)), MARGIN + 6f, page.y + head.ascent - 1f)
                    }
                    page.y += headH + 14f

                    // نص الفقرة
                    gr = page.canvas()
                    if (s.body.isBlank()) {
                        gr.setFont(fontRegular.deriveFont(11.8f))
                        gr.setColor(C_META)
                        drawRtlLine(gr, "(فقرة فارغة)", MARGIN, contentW, page.y)
                        page.y += 20f
                    } else {
                        flowRtlParagraphs(s.body, MARGIN, contentW, page)
                        page.y += 16f
                    }
                }
            }
        } finally {
            page.finish()
        }
    }

    // ================= المرحلة الثانية: رأس وتذييل الصفحات =================

    private fun drawChrome(
        doc: PDDocument,
        page: PDPage,
        schoolName: String?,
        date: LocalDate,
        pageNo: Int,
        total: Int
    ) {
        val contentW = PAGE_W - 2 * MARGIN
        val gr = PdfBoxGraphics2D(doc, PAGE_W, PAGE_H)
        gr.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        gr.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
        try {
            gr.setFont(fontBold.deriveFont(10.5f))
            gr.setColor(C_PRIMARY)
            drawRtlLine(gr, schoolName ?: "إذاعة مدرسية احترافي", MARGIN, contentW, 38f, ALIGN_RIGHT)
            gr.setFont(fontRegular.deriveFont(9f))
            gr.setColor(C_META)
            gr.drawString("سكريبت المذيع - ${date}", MARGIN, 44f)
            gr.setColor(C_RULE)
            gr.fill(Rectangle2D.Float(MARGIN, 58f, contentW, 0.9f))

            gr.setFont(fontRegular.deriveFont(9f))
            gr.setColor(C_META)
            val foot = "صفحة $pageNo من $total"
            val fw = gr.fontMetrics.stringWidth(foot)
            gr.drawString(foot, (PAGE_W - fw) / 2f, PAGE_H - 34f)
        } finally {
            gr.dispose()
            flushForm(gr, doc, page, append = true)
        }
    }

    // ================= محرّك النص العربي (تشكيل + لف أسطر + انسياب صفحات) =================

    private const val ALIGN_CENTER = "center"
    private const val ALIGN_RIGHT = "right"

    private fun rtlMeasurer(g: PdfBoxGraphics2D, text: String): LineBreakMeasurer {
        val attrs = HashMap<TextAttribute, Any>()
        attrs[TextAttribute.FONT] = g.font
        attrs[TextAttribute.RUN_DIRECTION] = TextAttribute.RUN_DIRECTION_RTL
        val asd = AttributedString(text, attrs)
        return LineBreakMeasurer(asd.iterator, g.fontRenderContext)
    }

    private fun singleLayout(g: PdfBoxGraphics2D, text: String): TextLayout =
        rtlMeasurer(g, text).nextLayout(Float.MAX_VALUE)

    /**
     * سطر واحد من اليمين: align=right ملاصق للهامش الأيمن، align=center موسّط.
     * يعيد موضع y بعد نهاية السطر (يشمل النزول والفراغ) ليتسلسل الإيقاع الرأسي.
     */
    private fun drawRtlLine(
        g: PdfBoxGraphics2D,
        text: String,
        margin: Float,
        contentW: Float,
        y: Float,
        align: String = ALIGN_RIGHT
    ): Float {
        if (text.isEmpty()) return y
        val lm = rtlMeasurer(g, text)
        var baseY = y
        while (lm.position < text.length) {
            val tl = lm.nextLayout(contentW) ?: break
            baseY += tl.ascent
            val dx = when (align) {
                ALIGN_CENTER -> margin + (contentW - tl.advance) / 2f
                else -> margin + (contentW - tl.advance)
            }
            tl.draw(g, dx, baseY)
            baseY += tl.descent + tl.leading
        }
        return baseY
    }

    /**
     * يلف فقرات النص (الفصل حسب \\n) أسطرًا عربية من اليمين، صفحةً بعد صفحة:
     * إن ضاق ارتفاع الصفحة أُنشئت جديدة واستمر السطر التالي فيها.
     */
    private fun flowRtlParagraphs(
        text: String,
        margin: Float,
        contentW: Float,
        page: Doc
    ) {
        text.split('\n').forEach { raw ->
            val para = raw.trim()
            if (para.isEmpty()) {
                page.y += 10f
                return@forEach
            }
            // قلب الصفحة يستبدل الكانفس — الخط واللون يُعادان للكانفس الجديد
            fun style(c: PdfBoxGraphics2D) {
                c.setFont(fontRegular.deriveFont(12f))
                c.setColor(C_BODY)
            }
            var canvas = page.canvas()
            style(canvas)
            val lm = rtlMeasurer(canvas, para)
            while (lm.position < para.length) {
                val tl = lm.nextLayout(contentW) ?: break
                if (page.y + tl.ascent + tl.descent + 4f > PAGE_H - BOTTOM) {
                    page.newPage()
                    canvas = page.canvas()
                    style(canvas)
                }
                page.y += tl.ascent
                val dx = margin + (contentW - tl.advance)
                tl.draw(canvas, dx, page.y)
                page.y += tl.descent + tl.leading + 3f
            }
        }
    }

    /** مدة مختصرة للقراءة: 05:20. */
    private fun durationAr(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
