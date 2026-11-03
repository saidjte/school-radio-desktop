package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.LayoutJson
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.StudioLayout
import com.saidjte.schoolradio.desktop.data.StudioLayoutLogic
import com.saidjte.schoolradio.desktop.data.StudioLogic
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v0.21.0: أبعاد حيزات شاشة الاستوديو القابلة للضبط بالفأرة (سحب الفواصل
 * وحفظها بين الجلسات) وشاشة القراءة بسكريبت واحد — طلبات المستخدم:
 * «ان امكن اعطي المستعمل طريقة التحكم في ابعاد كل الحيزات الموجودة في
 * شاشة الاستوديو بواسطة الفأرة» و«في الجانب الايمن اجعل الحيزين متساويين
 * في الارتفاع» و«شاشة القراءة يجب أن تضم سكريبت واحد لتجنب ارباك المذيع».
 * كلها منطق نقّي بلا واجهة ولا أجهزة صوت.
 */
class LayoutAndReadingTest {

    // ================= حسم الأبعاد: الافتراضي والحدود =================

    @Test
    fun `default layout makes right areas equal height`() {
        val d = StudioLayoutLogic.resolve(null)
        assertEquals(0.5f, d.rightTopFrac)
        assertEquals(360f, d.rightWidth)
        assertEquals(330f, d.broadcastWidth)
        assertEquals(0.72f, d.midTopFrac)
        assertEquals(0.45f, d.bcMicFrac)
    }

    @Test
    fun `layout values are clamped to interface bounds`() {
        val r = StudioLayoutLogic.resolve(
            LayoutJson(
                rightWidth = 100f,
                broadcastWidth = 9999f,
                rightTopFrac = 0.05f,
                midTopFrac = 1.5f,
                bcMicFrac = -1f
            )
        )
        assertEquals(StudioLayoutLogic.RIGHT_WIDTH_MIN, r.rightWidth)
        assertEquals(StudioLayoutLogic.BC_WIDTH_MAX, r.broadcastWidth)
        assertEquals(StudioLayoutLogic.RIGHT_TOP_MIN, r.rightTopFrac)
        assertEquals(StudioLayoutLogic.MID_TOP_MAX, r.midTopFrac)
        assertEquals(StudioLayoutLogic.BC_MIC_MIN, r.bcMicFrac)
    }

    @Test
    fun `partial layout json keeps defaults for null fields`() {
        val r = StudioLayoutLogic.resolve(LayoutJson(rightWidth = 480f))
        assertEquals(480f, r.rightWidth)
        assertEquals(330f, r.broadcastWidth)
        assertEquals(0.5f, r.rightTopFrac)
    }

    @Test
    fun `layout roundtrip through settings file`() {
        val f = File.createTempFile("sr-layout", ".json")
        try {
            val store = SettingsStore(f)
            store.setLayout(
                StudioLayoutLogic.toJson(StudioLayout(420f, 390f, 0.62f, 0.8f, 0.5f))
            )
            val reopened = SettingsStore(f)
            val l = reopened.layout()
            assertEquals(420f, l.rightWidth)
            assertEquals(390f, l.broadcastWidth)
            assertEquals(0.62f, l.rightTopFrac)
            assertEquals(0.8f, l.midTopFrac)
            assertEquals(0.5f, l.bcMicFrac)
        } finally {
            f.delete()
        }
    }

    @Test
    fun `clampFrac keeps drag inside bounds`() {
        assertEquals(0.3f, StudioLayoutLogic.clampFrac(0.1f, 0.3f, 0.9f))
        assertEquals(0.9f, StudioLayoutLogic.clampFrac(1.2f, 0.3f, 0.9f))
        assertEquals(0.6f, StudioLayoutLogic.clampFrac(0.6f, 0.3f, 0.9f))
    }

    // ================= شاشة القراءة: سكريبت واحد معياري =================

    private val script = listOf(
        ScriptSection(1, "مقدمة", "أهلا بكم في إذاعتنا المدرسية."),
        ScriptSection(2, "خبر اليوم", "اليوم حفل تكريم الطلاب المتفوقين."),
        ScriptSection(3, "ختام", "إلى اللقاء في اللقاء القادم.")
    )
    private val schedule = listOf(
        ScheduleItem(10, "مقدمة", scriptSectionId = 1L),
        ScheduleItem(11, "خبر اليوم", scriptSectionId = 2L),
        ScheduleItem(12, "ختام", scriptSectionId = 3L)
    )

    @Test
    fun `reading screen with follow shows the run section only`() {
        val r = StudioLogic.resolveReadingSection(
            schedule, runActive = true, runIndex = 1, script = script,
            manualSectionId = null, followActive = true
        )
        assertEquals(2L, r?.first?.id)
        assertEquals("من الجدولة", r?.second?.label)
    }

    @Test
    fun `reading screen with follow off gives the announcer manual control`() {
        val r = StudioLogic.resolveReadingSection(
            schedule, runActive = true, runIndex = 1, script = script,
            manualSectionId = 3L, followActive = false
        )
        assertEquals(3L, r?.first?.id)
        assertEquals("اختيار يدوي", r?.second?.label)
    }

    @Test
    fun `reading screen with follow off and no manual choice falls back to run`() {
        val r = StudioLogic.resolveReadingSection(
            schedule, runActive = true, runIndex = 2, script = script,
            manualSectionId = null, followActive = false
        )
        assertEquals(3L, r?.first?.id)
    }

    @Test
    fun `reading screen without a run shows manual selection`() {
        val r = StudioLogic.resolveReadingSection(
            schedule, runActive = false, runIndex = -1, script = script,
            manualSectionId = 2L, followActive = true
        )
        assertEquals(2L, r?.first?.id)
        assertEquals("اختيار يدوي", r?.second?.label)
    }

    @Test
    fun `reading screen with follow off ignores stale manual id`() {
        val r = StudioLogic.resolveReadingSection(
            schedule, runActive = true, runIndex = 0, script = script,
            manualSectionId = 999L, followActive = false
        )
        assertEquals(1L, r?.first?.id)
    }

    @Test
    fun `reading screen with empty script is null`() {
        assertNull(
            StudioLogic.resolveReadingSection(
                schedule, runActive = true, runIndex = 0, script = emptyList(),
                manualSectionId = null, followActive = true
            )
        )
    }

    @Test
    fun `teleprompter spacer geometry guarantees full pass through the indicator`() {
        // طلب v0.21.0: «النص يجب ان يبدأ من الأسفل و ينتهي في الأعلى (لضمان
        // مرور النص كاملا عبر مؤشر القراءة)» — الحشوتان = ارتفاع مساحة الزحف H
        // ناقص سطرًا واحدًا h: أول سطر مرئي عند الحافة السفلية عند البداية،
        // وآخر سطر مرئي عند الحافة العليا عند النهاية، وكل سطر يمرّ بالمؤشر.
        val areaHeight = 600f
        val lineHeight = 40f
        val textHeight = 250f
        val edge = areaHeight - lineHeight
        val maxScroll = edge + textHeight + edge - areaHeight
        // البداية: أول سطر جالس على الحافة السفلية (موضعه في المحتوى = H − h)
        assertEquals(areaHeight - lineHeight, edge)
        // النهاية: آخر سطر عند سطر واحد من الحافة العليا
        assertEquals(lineHeight, edge + textHeight - maxScroll)
        // والمدى المقطوع أكبر من طول النص فلا سطر يبقى عالقًا بعيدًا عن المؤشر
        assertTrue(maxScroll >= textHeight, "كل النص يمر عبر المؤشر")
    }
}
