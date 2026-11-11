package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.BundledJingles
import com.saidjte.schoolradio.desktop.data.AnnounceJson
import com.saidjte.schoolradio.desktop.data.LiveAnnounceLogic
import com.saidjte.schoolradio.desktop.data.OnAirDisplayLogic
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.StudioLogic
import com.saidjte.schoolradio.desktop.data.StudioStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * عقود v0.45.0 الثلاثة الأخرى: شارة الإعلان تُضيء الهواء، واختيار نغمة
 * الإعلان بحفظها وتعقيمها، والاختيار اليدوي للسكربت من شاشة الاستوديو.
 */
class AnnounceBadgeScriptV45Test {

    private val ids = BundledJingles.ALL.map { it.id }

    // ================= 1) نغمة الإعلان: التعقيم والافتراضي =================

    @Test
    fun `resolveJingleId defaults on null blank unknown and honors known ids`() {
        // الغائب/الفارغ/المبلّد يعود للافتراضي «افتتاحية مبهجة»
        assertEquals(LiveAnnounceLogic.JINGLE_ID, LiveAnnounceLogic.resolveJingleId(null, ids))
        assertEquals(LiveAnnounceLogic.JINGLE_ID, LiveAnnounceLogic.resolveJingleId("", ids))
        assertEquals(LiveAnnounceLogic.JINGLE_ID, LiveAnnounceLogic.resolveJingleId("   ", ids))
        // معرف من نسخة أقدم حُذف من المدمجات يعود للافتراضي بلا مساس
        assertEquals(LiveAnnounceLogic.JINGLE_ID, LiveAnnounceLogic.resolveJingleId("removed_jingle", ids))
        // المعلوم يُعاد مهذّبًا من فراغاته
        assertEquals("school_bell", LiveAnnounceLogic.resolveJingleId(" school_bell ", ids))
        // كل جينل مدمج يمرّ كما هو
        ids.forEach { assertEquals(it, LiveAnnounceLogic.resolveJingleId(it, ids)) }
        // الافتراضي نفسه عضو في المدمجات — عهد v0.35 باقٍ
        assertTrue(ids.contains(LiveAnnounceLogic.JINGLE_ID))
    }

    @Test
    fun `announce tone round trip through settings store`() {
        val tmp = kotlin.io.path.createTempDirectory("annv45").toFile()
        val f = File(tmp, "settings.json")
        try {
            val s1 = SettingsStore(f)
            assertEquals(null, s1.liveAnnounceJingleId(), "default absent")
            s1.setLiveAnnounceJingleId("whoosh")
            assertEquals("whoosh", s1.liveAnnounceJingleId())
            // إعادة إقلاع: نفس القيمة تعود من settings.json
            val s2 = SettingsStore(f)
            assertEquals("whoosh", s2.liveAnnounceJingleId())
            // الاسم الغريب يُخزّن كما هو والتعقيم عند القراءة في resolveJingleId
            s2.setLiveAnnounceJingleId("ghost")
            val s3 = SettingsStore(f)
            assertEquals(
                LiveAnnounceLogic.JINGLE_ID,
                LiveAnnounceLogic.resolveJingleId(s3.liveAnnounceJingleId(), ids)
            , "unknown saved id falls back at read time")
        } finally {
            f.delete(); tmp.delete()
        }
    }

    @Test
    fun `announce json survives corrupted settings file`() {
        val tmp = kotlin.io.path.createTempDirectory("annv45b").toFile()
        val f = File(tmp, "settings.json")
        try {
            f.writeText("{ نصف JSON مكسور")
            val s = SettingsStore(f)
            assertEquals(null, s.liveAnnounceJingleId(), "corrupt file returns null safely")
            s.setLiveAnnounceJingleId("magic_sweep")
            assertEquals("magic_sweep", SettingsStore(f).liveAnnounceJingleId())
        } finally {
            f.delete(); tmp.delete()
        }
    }

    @Test
    fun `jingle title resolves with fallback for unknown id`() {
        fun titleOf(jid: String): String? =
            BundledJingles.ALL.firstOrNull { it.id == jid }?.title
        assertEquals("جرس المدرسة", LiveAnnounceLogic.jingleTitleOf("school_bell", ::titleOf))
        assertEquals("نغمة مخصصة", LiveAnnounceLogic.jingleTitleOf("alien", ::titleOf))
    }

    // ================= 2) شارة الإعلان تُضيء الهواء =================

    @Test
    fun `studio status onAir includes announcement badge`() {
        // كل المصادر خاملة والإعلان مضيء → على الهواء
        assertTrue(StudioStatus(false, false, false, announceActive = true).onAir)
        // بلا إعلان: العهد القديم كما هو
        assertFalse(StudioStatus(false, false, false).onAir)
        assertTrue(StudioStatus(true, false, false).onAir)
        assertTrue(StudioStatus(false, true, false).onAir)
        assertTrue(StudioStatus(false, false, true).onAir)
        // مصدر واحد مع الإعلان يبقى على الهواء بالتأكيد
        assertTrue(StudioStatus(false, true, false, announceActive = true).onAir)
    }

    @Test
    fun `studio logic status keeps legacy signature and adds badge`() {
        assertFalse(StudioLogic.status(false, false, false).onAir, "legacy default unchanged")
        assertTrue(StudioLogic.status(false, false, false, announceActive = true).onAir)
    }

    @Test
    fun `on air display model lights badge on announcement`() {
        // الشاشة الثانية: لا مايك ولا تسجيل ولا موسيقى — الإعلان وحده يضيء الشارة
        val m = OnAirDisplayLogic.buildModel(
            runActive = false, runIndex = -1, runElapsedMs = 0L,
            remainingMs = null, warn = false,
            scheduleTitles = emptyList(), schedulePlannedSeconds = emptyList(),
            musicTitle = null, musicPositionMs = 0L, musicDurationMs = 0L,
            musicPlaying = false, micLive = false, recording = false,
            announceActive = true
        )
        assertTrue(m.onAir, "announce alone lights on-air")
        // الافتراضي بلا إعلان — العهد القديم
        val m0 = OnAirDisplayLogic.buildModel(
            runActive = false, runIndex = -1, runElapsedMs = 0L,
            remainingMs = null, warn = false,
            scheduleTitles = emptyList(), schedulePlannedSeconds = emptyList(),
            musicTitle = null, musicPositionMs = 0L, musicDurationMs = 0L,
            musicPlaying = false, micLive = false, recording = false
        )
        assertFalse(m0.onAir)
    }

    @Test
    fun `announce banner window contracts unchanged`() {
        val now = 1_000_000L
        assertFalse(LiveAnnounceLogic.isActive(null, now))
        assertTrue(LiveAnnounceLogic.isActive(now, now), "start inclusive")
        assertFalse(LiveAnnounceLogic.isActive(now, now + LiveAnnounceLogic.DURATION_MS), "end exclusive")
        assertEquals(0L, LiveAnnounceLogic.remainingMs(now, now + LiveAnnounceLogic.DURATION_MS + 1))
        assertEquals(
            LiveAnnounceLogic.DURATION_MS,
            LiveAnnounceLogic.remainingMs(now, now)
        )
    }

    // ================= 3) الاختيار اليدوي للسكربت من الاستوديو =================

    @Test
    fun `picker labels number trim and truncate long titles`() {
        assertEquals("1. المقدمة", StudioLogic.pickerLabel(0, "المقدمة"))
        assertEquals("3. قرآن", StudioLogic.pickerLabel(2, "قرآن"))
        // الفارغ والمبلّد: «بدون عنوان»
        assertEquals("2. بدون عنوان", StudioLogic.pickerLabel(1, ""))
        assertEquals("2. بدون عنوان", StudioLogic.pickerLabel(1, "   "))
        // الطويل يُقصّ عند 38 حرفًا بعلامة الحذف
        val long = "فترة طويلة جدًا ".repeat(6)
        val label = StudioLogic.pickerLabel(0, long)
        assertTrue(label.startsWith("1. ") && label.endsWith("…"), "label=$label")
        assertTrue(label.length <= 3 + 38 + 1, "length=${label.length}")
        // الحد المخصص يُحترم — والقصّ يضع علامة الحذف دائمًا فوق العتبة
        assertEquals("1. اختبار…", StudioLogic.pickerLabel(0, "اختبار طويل للغاية", maxChars = 6))
    }

    @Test
    fun `manual selection priority contracts unchanged for studio picker`() {
        // نفس عقود resolveScriptSection التي يعتمدها المنتقي الجديد
        val s1 = com.saidjte.schoolradio.desktop.data.ScriptSection(1, "أ", "نص")
        val s2 = com.saidjte.schoolradio.desktop.data.ScriptSection(2, "ب", "نص")
        val script = listOf(s1, s2)
        // بلا حلقة حية: الاختيار اليدوي الصالح يُعرض ويعلَّم «اختيار يدوي»
        val r = StudioLogic.resolveScriptSection(emptyList(), false, -1, script, 2L)
        assertEquals(2L, r?.first?.id)
        assertEquals(com.saidjte.schoolradio.desktop.data.ScriptSource.MANUAL, r?.second)
        // معرف محذوف يقع بأمان على الأول
        assertEquals(1L, StudioLogic.resolveScriptSection(emptyList(), false, -1, script, 99L)?.first?.id)
        // أثناء حلقة حية: فقرة الجدولة أولوية ما لم يُمسك القيادة يدويًا
        val item = com.saidjte.schoolradio.desktop.data.ScheduleItem(
            id = 1, title = "فقرة", scriptSectionId = 2L, plannedSeconds = 30L
        )
        val r2 = StudioLogic.resolveScriptSection(listOf(item), true, 0, script, null)
        assertEquals(2L, r2?.first?.id)
        assertEquals(com.saidjte.schoolradio.desktop.data.ScriptSource.FROM_SCHEDULE, r2?.second)
    }

    @Test
    fun `announce json data class defaults safely`() {
        val j = AnnounceJson()
        assertEquals(null, j.jingleId)
        assertEquals("whoosh", AnnounceJson(jingleId = "whoosh").jingleId)
    }
}
