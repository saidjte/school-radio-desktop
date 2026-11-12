package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.BundledJingles
import com.saidjte.schoolradio.desktop.data.AnnounceJson
import com.saidjte.schoolradio.desktop.data.LiveAnnounceLogic
import com.saidjte.schoolradio.desktop.data.OnAirDisplayLogic
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.StudioLogic
import com.saidjte.schoolradio.desktop.data.StudioStatus
import com.saidjte.schoolradio.desktop.ui.GuiTokens
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * عقود v0.46.0 الثلاثة: زر «إنهاء البث» يغلق دورة الإعلان، ونغمة الإعلان
 * من ملف المستخدم بحفظها وحسمها لحظة النقر، ولوحة شاشة «على الهواء» الموحدة
 * بعقد تباين مقيس.
 */
class AnnounceEndCustomToneV46Test {

    private val ids = BundledJingles.ALL.map { it.id }

    // ================= 1) إنهاء البث: الوجه المغلق لدورة الإعلان =================

    @Test
    fun `broadcastLive alone lights onAir and status defaults off`() {
        // جلسة معلنة وحدها حالة هواء كاملة — شارة الهواء مضاءة حتى الإنهاء
        assertTrue(
            StudioLogic.status(false, false, false, broadcastLive = true).onAir,
            "broadcast session alone lights onAir"
        )
        // الافتراضي مطفأ — الإقلاع الجديد يفتح الجلسة مطفأة (عهد صدق الحالة)
        assertFalse(StudioLogic.status(false, false, false).broadcastLive)
        assertFalse(StudioLogic.status(false, false, false).onAir)
        // الإعلان الخاطف (شارة 15 ثانية) يضيء كما في v0.45 بلا مساس
        assertTrue(
            StudioLogic.status(false, false, false, announceActive = true).onAir,
            "announce banner still lights onAir (v0.45 contract intact)"
        )
    }

    @Test
    fun `ending the session extinguishes its badge contribution but honors live sources`() {
        // الإنهاء يطفئ مساهمة الجلسة وحدها
        val during = StudioStatus(micLive = false, recording = false, musicPlaying = false, broadcastLive = true)
        assertTrue(during.onAir)
        val after = during.copy(broadcastLive = false)
        assertFalse(after.onAir, "ended session turns the badge off")
        // لكن المصادر الحية الحقيقية تبقي الهواء مضاءً بحقها بعد الإنهاء
        val micStill = StudioStatus(micLive = true, recording = false, musicPlaying = false, broadcastLive = false)
        assertTrue(micStill.onAir, "live mic keeps onAir after ending the announcement")
        val musicStill = StudioStatus(micLive = false, recording = false, musicPlaying = true, broadcastLive = false)
        assertTrue(musicStill.onAir, "playing music keeps onAir after ending the announcement")
    }

    @Test
    fun `endMessage is honest about remaining live sources`() {
        assertEquals(
            "تم إنهاء البث المباشر",
            LiveAnnounceLogic.endMessage(false),
            "clean end says so plainly"
        )
        val withSources = LiveAnnounceLogic.endMessage(true)
        assertTrue(withSources.startsWith("تم إنهاء البث المباشر"), "end message leads with the fact")
        assertTrue("ما زالت تعمل" in withSources, "end message discloses still-live sources")
    }

    // ================= 2) نغمة الإعلان من ملف المستخدم =================

    @Test
    fun `resolveAnnounceSource prefers an existing custom file`() {
        var asked: String? = null
        val src = LiveAnnounceLogic.resolveAnnounceSource(
            customPath = " /home/teacher/nashid.mp3 ",
            selectedJingleId = "school_bell",
            knownIds = ids
        ) { p -> asked = p; true }
        assertTrue(src is LiveAnnounceLogic.AnnounceSource.CustomFile, "existing file wins")
        assertEquals("/home/teacher/nashid.mp3", (src as LiveAnnounceLogic.AnnounceSource.CustomFile).path, "path trimmed")
        assertEquals("/home/teacher/nashid.mp3", asked, "existence asked on the trimmed path")
    }

    @Test
    fun `missing or blank custom file falls back to the sanitized built-in`() {
        // ملف غائب: عودة للمدمجة المختارة بدل أن يصمت الإعلان
        val missing = LiveAnnounceLogic.resolveAnnounceSource(
            customPath = "/gone/tone.mp3", selectedJingleId = "magic_sweep", knownIds = ids
        ) { false }
        assertEquals(LiveAnnounceLogic.AnnounceSource.BuiltIn("magic_sweep"), missing)
        // غائب/فارغ/فراغات: المدمجة المختارة مباشرة
        assertEquals(
            LiveAnnounceLogic.AnnounceSource.BuiltIn("opening_jingle"),
            LiveAnnounceLogic.resolveAnnounceSource(null, "opening_jingle", ids) { true }
        )
        assertEquals(
            LiveAnnounceLogic.AnnounceSource.BuiltIn("quick_marker"),
            LiveAnnounceLogic.resolveAnnounceSource("   ", "quick_marker", ids) { true }
        )
        // ومعرف فارغ يهذّبه resolveJingleId للافتراضي «افتتاحية مبهجة»
        assertEquals(
            LiveAnnounceLogic.AnnounceSource.BuiltIn(LiveAnnounceLogic.JINGLE_ID),
            LiveAnnounceLogic.resolveAnnounceSource("", "", ids) { true }
        )
        // ومسار مخصص بلا حسم يعود للافتراضي «افتتاحية مبهجة» إن كان المختار غريبة
        assertEquals(
            LiveAnnounceLogic.AnnounceSource.BuiltIn(LiveAnnounceLogic.JINGLE_ID),
            LiveAnnounceLogic.resolveAnnounceSource(null, "ghost_id", ids) { true }
        )
    }

    @Test
    fun `custom label shows the file name truncated`() {
        assertEquals("nashid.mp3", LiveAnnounceLogic.customLabel("/home/t/nashid.mp3"))
        assertEquals("nashid.mp3", LiveAnnounceLogic.customLabel("C:\\music\\nashid.mp3"), "windows separators")
        assertEquals("نغمة مخصصة", LiveAnnounceLogic.customLabel("   "))
        val long = "نغمة الافتتاح المدرسية الطويلة جدا لطابور الصباح.mp3"
        val cut = LiveAnnounceLogic.customLabel("/x/$long")
        assertTrue(cut.endsWith("…") && cut.length <= 25, "long names are cut at 24 + ellipsis")
        assertEquals(long.take(24) + "…", cut)
    }

    @Test
    fun `custom tone path survives settings json round trip and clears with built ins`() {
        val tmp = createTempDir()
        val f = File(tmp, "settings.json")
        try {
            val s1 = SettingsStore(f)
            assertEquals(null, s1.liveAnnounceCustomPath(), "default absent")
            s1.setLiveAnnounceCustomPath("/home/teacher/nashid.mp3")
            assertEquals("/home/teacher/nashid.mp3", s1.liveAnnounceCustomPath())
            // إعادة إقلاع: المسار يعود من settings.json
            assertEquals("/home/teacher/nashid.mp3", SettingsStore(f).liveAnnounceCustomPath())
            // التهذيب: فراغات تُقص والفارغ يعود null لا سلسلة بيضاء
            s1.setLiveAnnounceCustomPath("   ")
            assertEquals(null, SettingsStore(f).liveAnnounceCustomPath(), "blank path stored as absent")
            s1.setLiveAnnounceCustomPath("/x/a.wav")
            s1.setLiveAnnounceCustomPath(null)
            assertEquals(null, SettingsStore(f).liveAnnounceCustomPath(), "explicit clear persists")
            // ومعرف المدمجة يجتمعان في وثيقة واحدة دون أن يقتل أحدهما الآخر
            s1.setLiveAnnounceCustomPath("/x/a.wav")
            s1.setLiveAnnounceJingleId("school_bell")
            val s2 = SettingsStore(f)
            assertEquals("/x/a.wav", s2.liveAnnounceCustomPath())
            assertEquals("school_bell", s2.liveAnnounceJingleId())
            assertEquals(AnnounceJson(jingleId = "school_bell", customPath = "/x/a.wav").customPath, s2.liveAnnounceCustomPath())
        } finally {
            f.delete(); tmp.delete()
        }
    }

    // ================= 3) لوحة شاشة على الهواء الموحدة بعقد تباين مقيس =================

    @Test
    fun `on air palette contrast contract holds`() {
        assertTrue(GuiTokens.validateOnAirContrast(), "every on-air text/dot pair above WCAG")
        // والقيم المسجلة تطابق الحساب الحي — فلا يتسرب توكن باهت لاحقًا
        assertEquals(
            GuiTokens.RATIO_ON_AIR_CONTENT_ON_AIR_BG,
            GuiTokens.contrastRatio(GuiTokens.ON_AIR_CONTENT, GuiTokens.ON_AIR_BG),
            0.01,
            "recorded banner ratio matches live computation"
        )
        assertEquals(
            GuiTokens.RATIO_RECORDING_TEXT_VARIANT,
            GuiTokens.contrastRatio(GuiTokens.RECORDING_TEXT, GuiTokens.SURFACE_VARIANT),
            0.01,
            "recorded recording-text ratio matches live computation"
        )
        assertTrue(
            GuiTokens.contrastRatio(GuiTokens.RECORDING_TEXT, GuiTokens.BG) >= 4.5,
            "recording text readable on the page background too"
        )
        // عائلة حمراء واحدة: نقطة الحياة ونقطة التسجيل من الصلب نفسه
        assertEquals(GuiTokens.zoneRed, 0xFFE53935.toInt())
    }

    @Test
    fun `theme layering contract stays intact`() {
        assertTrue(GuiTokens.validateThemeLayering(), "three-layer background separation intact")
    }

    // ================= 4) شاشة الطلاب: الجلسة تضيء النموذج =================

    @Test
    fun `on air display model honors broadcastLive`() {
        val m = OnAirDisplayLogic.buildModel(
            runActive = false, runIndex = 0, runElapsedMs = 0L, remainingMs = null, warn = false,
            scheduleTitles = emptyList(), schedulePlannedSeconds = emptyList(),
            musicTitle = null, musicPositionMs = 0L, musicDurationMs = 0L, musicPlaying = false,
            micLive = false, recording = false, announceActive = false, broadcastLive = true
        )
        assertTrue(m.onAir, "open announcement session lights the student display badge")
        val mEnded = m.copy() // النموذج يُبنى من الحالة — الإنهاء يعيد البناء مطفأً
        assertFalse(
            OnAirDisplayLogic.buildModel(
                runActive = false, runIndex = 0, runElapsedMs = 0L, remainingMs = null, warn = false,
                scheduleTitles = emptyList(), schedulePlannedSeconds = emptyList(),
                musicTitle = null, musicPositionMs = 0L, musicDurationMs = 0L, musicPlaying = false,
                micLive = false, recording = false, announceActive = false, broadcastLive = false
            ).onAir,
            "ended session rebuilds the model with the badge off"
        )
        assertTrue(mEnded.onAir)
    }
}
