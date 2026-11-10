package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.RecordingEngine
import com.saidjte.schoolradio.desktop.audio.RecordingStudio
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageTrack
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.SchedRecJson
import com.saidjte.schoolradio.desktop.data.RecordingJson
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * اختبارات عقد استوديو التسجيل الاحترافي (v0.43.0) — بنود الطلب الأحد عشر
 * على المنطق الخالص قبل أي عتاد: الجودة والترميز العريض والمقياس والتشبع
 * وآلة الحالات وتفاوض الصيغ والجدولة والإعدادات ومساعدو الفوقية —
 * وحد الجلسة الخاوية على المحرك بلا عتاد.
 */
class RecordingStudioV43Test {

    private fun tmpDir(): File = Files.createTempDirectory("v43test").toFile()

    // ================= (6)(7) الجودة: 16/24/32 بت × 44.1/48/96/192 =================

    @Test
    fun quality_clamp_and_labels() {
        val q = RecordingStudio.Quality(24, 96_000).clamp()
        assertEquals(24, q.bits)
        assertEquals(96_000, q.rateHz)
        assertEquals(3, q.bytesPerSample)

        // القيم خارج المسموح تعود لأقربها القانونية — لا استثناء أبدًا
        assertEquals(16, RecordingStudio.Quality(18, 50_000).clamp().bits)
        assertEquals(48_000, RecordingStudio.Quality(18, 50_000).clamp().rateHz)
        assertEquals(32, RecordingStudio.Quality(64, 500_000).clamp().bits)
        assertEquals(192_000, RecordingStudio.Quality(16, 500_000).clamp().rateHz)

        assertEquals(listOf(16, 24, 32), RecordingStudio.ALLOWED_BITS)
        assertEquals(listOf(44_100, 48_000, 96_000, 192_000), RecordingStudio.ALLOWED_RATES)

        val label = RecordingStudio.Quality(24, 44_100).labelAr()
        assertTrue(label.contains("44.1") && label.contains("كيلوهرتز") && label.contains("24"), label)
    }

    // ================= (6) ترميز PCM العريض =================

    @Test
    fun pcm24_roundtrip() {
        val src = floatArrayOf(0f, 0.25f, -0.5f, 0.9f, -1f, 1f)
        val bytes = ByteArray(src.size * 3)
        val written = RecordingStudio.PcmWide.floatsToBytes24(src, src.size, bytes)
        assertEquals(src.size * 3, written)
        val out = FloatArray(src.size)
        val n = RecordingStudio.PcmWide.bytes24ToFloats(bytes, bytes.size, out)
        assertEquals(src.size, n)
        // قصّ التشفير يمنع الالتفاف: ±1 تعود ±1 تقريبًا (تسامح خطوة الترميز)
        assertEquals(-1f, out[4], 0.0001f)
        for (i in src.indices) {
            assertTrue(kotlin.math.abs(src[i] - out[i]) < 0.0001f, "i=$i src=${src[i]} out=${out[i]}")
        }
    }

    @Test
    fun pcm32_roundtrip_and_sign_extension() {
        val src = floatArrayOf(0f, 0.5f, -0.5f, -1f)
        val bytes = ByteArray(src.size * 4)
        RecordingStudio.PcmWide.floatsToBytes32(src, src.size, bytes)
        val out = FloatArray(src.size)
        val n = RecordingStudio.PcmWide.bytes32ToFloats(bytes, bytes.size, out)
        assertEquals(src.size, n)
        // أعلى قيمة سالبة تُعار قرب −1.0 بدقة (قسمة على 2³¹) — مع تسامح ترميز
        assertEquals(-1f, out[3], 0.0001f)
        assertEquals(0.5f, out[1], 0.0001f)
    }

    @Test
    fun pcm16_float_helpers_roundtrip() {
        val shorts = shortArrayOf(0, 16384, -16384, 32767, -32768)
        val f = FloatArray(shorts.size)
        RecordingStudio.PcmWide.shortsToFloats(shorts, shorts.size, f)
        val back = ShortArray(shorts.size)
        RecordingStudio.PcmWide.floatsToShorts(f, f.size, back)
        // دورة كاملة: 16→عائم→16 بخطأ أقل من خطوة ترميز واحدة
        assertTrue(kotlin.math.abs(back[1] - shorts[1]) <= 1, "back=${back[1]}")
        assertTrue(kotlin.math.abs(back[2] - shorts[2]) <= 1, "back=${back[2]}")
        assertTrue(kotlin.math.abs(f[1] - 0.5f) < 0.001f)
    }

    // ================= (8)(9) المقياس اللحظي ومؤشر التشبع =================

    @Test
    fun analyzeFrame_rms_peak_and_clip() {
        // جيب 0.5 سعة: RMS ≈ 0.5/√2
        val n = 480
        val sine = FloatArray(n) { (0.5 * sin(2 * PI * it * 440.0 / 48000.0)).toFloat() }
        val m = RecordingStudio.analyzeFrame(sine, n)
        assertTrue(m.rmsAmp > 0.3 && m.rmsAmp < 0.4, "rms=${m.rmsAmp}")
        assertTrue(m.peakAmp <= 0.5 + 0.02 && m.peakAmp > 0.4, "peak=${m.peakAmp}")
        assertFalse(m.clipped)

        // ذروة كاملة → تشبع صريح
        val full = FloatArray(100) { if (it % 2 == 0) 1f else -1f }
        assertTrue(RecordingStudio.analyzeFrame(full, 100).clipped)

        // الصمت والخاوية: صمت صريح لا استثناء
        assertEquals(0.0, RecordingStudio.analyzeFrame(FloatArray(64), 64).rmsAmp)
        val empty = RecordingStudio.analyzeFrame(FloatArray(64), 0)
        assertEquals(0.0, empty.rmsAmp)
        assertFalse(empty.clipped)
    }

    @Test
    fun clipLatch_countsEvents_notFrames_andHolds() {
        val latch = RecordingStudio.ClipLatch(holdMs = 800)
        latch.onFrame(true, 100)
        latch.onFrame(true, 300)   // داخل القفل نفسه — لا عدّ جديد
        latch.onFrame(true, 600)   // ما زال داخل القفل
        assertEquals(1, latch.count)
        assertTrue(latch.active(700))
        assertFalse(latch.active(1500)) // انحسر القفل: آخر حدث عند 600 + 800
        latch.onFrame(true, 1600)  // حدث جديد
        assertEquals(2, latch.count)
        latch.reset()
        assertEquals(0, latch.count)
        assertFalse(latch.active(1700))
    }

    // ================= (5) آلة حالات الإيقاف والاستئناف =================

    @Test
    fun stateMachine_fullLifecycle() {
        val s = RecordingStudio.StateMachine()
        assertEquals(RecordingStudio.RecState.IDLE, s.state)
        assertTrue(s.start())
        assertEquals(RecordingStudio.RecState.RECORDING, s.state)
        assertTrue(s.pause())
        assertEquals(RecordingStudio.RecState.PAUSED, s.state)
        assertTrue(s.resume())
        assertEquals(RecordingStudio.RecState.RECORDING, s.state)
        assertTrue(s.pause())
        assertTrue(s.stop()) // الإنهاء مسموح من التوقف المؤقت أيضًا
        assertEquals(RecordingStudio.RecState.IDLE, s.state)
    }

    @Test
    fun stateMachine_rejectsIllegalTransitions() {
        val s = RecordingStudio.StateMachine()
        assertFalse(s.pause())  // إيقاف مؤقت من الخمول
        assertFalse(s.resume()) // استئناف بلا توقف
        assertFalse(s.stop())   // إنهاء بلا جلسة
        assertTrue(s.start())
        assertFalse(s.start())  // بدء مزدوج
        assertFalse(s.resume()) // استئناف من التسجيل لا من التوقف
        assertTrue(s.stop())
        assertFalse(s.stop())   // إنهاء مزدوج
    }

    // ================= تفاوض صيغة الالتقاط وخطة التحويل =================

    @Test
    fun pickCapture_prefersExactThenSameRateThenNearest() {
        val target = RecordingStudio.Quality(24, 96_000)
        // 1) المطابقة التامة
        val device1 = listOf(
            RecordingStudio.CaptureFormat(44_100, 16, 1),
            RecordingStudio.CaptureFormat(96_000, 24, 1),
            RecordingStudio.CaptureFormat(48_000, 16, 2)
        )
        assertEquals(96_000, RecordingStudio.pickCapture(target, device1).rateHz)
        assertEquals(24, RecordingStudio.pickCapture(target, device1).bits)

        // 2) نفس المعدل وأقرب بت (32 أقرب إلى 24 من 16 — والتعادل يفضّل الأعلى)
        val device2 = listOf(
            RecordingStudio.CaptureFormat(96_000, 16, 1),
            RecordingStudio.CaptureFormat(96_000, 32, 1)
        )
        assertEquals(32, RecordingStudio.pickCapture(target, device2).bits)

        // 3) لا معدل مطابق: أقرب معدل لا يتجاوز المطلوب
        val device3 = listOf(
            RecordingStudio.CaptureFormat(192_000, 16, 1),
            RecordingStudio.CaptureFormat(48_000, 16, 1)
        )
        val cap3 = RecordingStudio.pickCapture(target, device3)
        assertEquals(48_000, cap3.rateHz)

        // 4) قائمة خاوية: 16 بت على المعدل المطلوب (التحويل يكمل الطريق)
        val cap4 = RecordingStudio.pickCapture(target, emptyList())
        assertEquals(96_000, cap4.rateHz)
        assertEquals(16, cap4.bits)
    }

    @Test
    fun conversion_plan_and_labels() {
        val target = RecordingStudio.Quality(24, 48_000)
        val capture = RecordingStudio.CaptureFormat(44_100, 16, 1)
        assertTrue(RecordingStudio.needsResample(capture, target))
        assertTrue(RecordingStudio.needsReencode(capture, target))
        val label = RecordingStudio.conversionLabel(capture, target)
        assertNotNull(label)
        assertTrue(label.contains("44.1") && label.contains("24"), label)

        // مطابقة تامة: لا خطة تحويل
        val exact = RecordingStudio.CaptureFormat(48_000, 24, 1)
        assertFalse(RecordingStudio.needsResample(exact, target))
        assertFalse(RecordingStudio.needsReencode(exact, target))
        assertNull(RecordingStudio.conversionLabel(exact, target))
    }

    // ================= (10) التسجيل المجدول =================

    /** لحظة اليوم عند ساعة محددة (بتوقيت الجهاز نفسه الذي يستخدمه القرار). */
    private fun at(hour: Int, minute: Int = 0): Long =
        LocalDate.now().atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun scheduled_due_waiting_missed_lifecycle() {
        val item = RecordingStudio.ScheduledItem(
            id = "a", title = "فاصل", startMinutes = 8 * 60 + 30, // 08:30
            durationMinutes = 5, repeat = RecordingStudio.REPEAT_DAILY
        )
        assertEquals(RecordingStudio.SchedStatus.WAITING, RecordingStudio.decide(item, at(8, 0)).status)
        assertEquals(RecordingStudio.SchedStatus.DUE, RecordingStudio.decide(item, at(8, 30)).status)
        assertEquals(RecordingStudio.SchedStatus.DUE, RecordingStudio.decide(item, at(8, 34)).status)
        assertEquals(RecordingStudio.SchedStatus.MISSED, RecordingStudio.decide(item, at(8, 40)).status)
        assertEquals(RecordingStudio.SchedStatus.WAITING, RecordingStudio.decide(item, at(9, 0)).status)
        // نافذة البند اليدوي (بلا مدة): دقيقتان
        val manual = item.copy(durationMinutes = null)
        assertEquals(RecordingStudio.SchedStatus.DUE, RecordingStudio.decide(manual, at(8, 31)).status)
        assertEquals(RecordingStudio.SchedStatus.MISSED, RecordingStudio.decide(manual, at(8, 34)).status)
    }

    @Test
    fun scheduled_firedToday_disabled_and_once_never_again() {
        val todayKey = RecordingStudio.dateKeyOf(at(9, 0))
        val item = RecordingStudio.ScheduledItem(
            id = "b", title = "صباحي", startMinutes = 8 * 60, durationMinutes = 10,
            repeat = RecordingStudio.REPEAT_DAILY, lastFiredDateKey = todayKey
        )
        assertEquals(RecordingStudio.SchedStatus.FIRED_TODAY, RecordingStudio.decide(item, at(9, 0)).status)

        val off = item.copy(enabled = false, lastFiredDateKey = null)
        assertEquals(RecordingStudio.SchedStatus.DISABLED, RecordingStudio.decide(off, at(8, 30)).status)

        // «مرة واحدة» نفّذت يومًا ما: لا عودة إليها أبدًا
        val onceDone = RecordingStudio.ScheduledItem(
            id = "c", title = "خاص", startMinutes = 480, durationMinutes = 5,
            repeat = RecordingStudio.REPEAT_ONCE, lastFiredDateKey = "2026-01-01"
        )
        assertEquals(RecordingStudio.SchedStatus.FIRED_TODAY, RecordingStudio.decide(onceDone, at(8, 31)).status)

        // «مرة واحدة» لم تنفّذ بعد: مستحقة داخل نافذتها (08:00–08:05)
        val onceFresh = onceDone.copy(lastFiredDateKey = null)
        assertEquals(RecordingStudio.SchedStatus.DUE, RecordingStudio.decide(onceFresh, at(8, 2)).status)
    }

    @Test
    fun scheduled_weekly_respects_iso_days() {
        val now = at(10, 0)
        val iso = RecordingStudio.isoDayOfWeek(now)
        val on = RecordingStudio.ScheduledItem(
            id = "d", title = "أسبوعي", startMinutes = 9 * 60 + 55, durationMinutes = 10,
            repeat = RecordingStudio.REPEAT_WEEKLY, weeklyDays = listOf(iso)
        )
        assertEquals(RecordingStudio.SchedStatus.DUE, RecordingStudio.decide(on, now).status)
        val otherDay = (iso % 7) + 1
        val off = on.copy(weeklyDays = listOf(otherDay))
        assertEquals(RecordingStudio.SchedStatus.WAITING, RecordingStudio.decide(off, now).status)
    }

    @Test
    fun scheduled_sanitize_clamps_and_validates() {
        val s = RecordingStudio.sanitize(
            RecordingStudio.ScheduledItem(
                id = "e", title = "  عنوان طويل ".repeat(8), startMinutes = 2000,
                durationMinutes = 9999, repeat = "weekly-bogus", weeklyDays = listOf(9, 1, 1, 3), enabled = true
            )
        )
        assertTrue(s.title.startsWith("عنوان طويل"), s.title) // معقّم بلا أطراف بيضاء
        assertTrue(s.title.length <= 60)
        assertEquals(1439, s.startMinutes)
        assertEquals(600, s.durationMinutes)
        assertEquals(RecordingStudio.REPEAT_ONCE, s.repeat) // التكرار المجهول يعود مرة واحدة
        assertTrue(s.weeklyDays.isEmpty()) // بلا تكرار أسبوعي فلا أيام
        val w = RecordingStudio.sanitize(
            RecordingStudio.ScheduledItem(
                id = "f", title = "أسبوعي", startMinutes = 600, durationMinutes = 5,
                repeat = RecordingStudio.REPEAT_WEEKLY, weeklyDays = listOf(9, 1, 1, 3)
            )
        )
        assertEquals(listOf(1, 3), w.weeklyDays) // ترشيح وتفريد وترتيب
    }

    @Test
    fun scheduled_nextLabel_variants() {
        val now = at(8, 0)
        val future = RecordingStudio.ScheduledItem(
            id = "g", title = "قادم", startMinutes = 8 * 60 + 30, durationMinutes = 5,
            repeat = RecordingStudio.REPEAT_DAILY, enabled = true
        )
        assertTrue(RecordingStudio.nextLabel(future, now).contains("اليوم"))
        assertTrue(RecordingStudio.nextLabel(future, now).contains("08:30"))
        val past = future.copy(startMinutes = 7 * 60)
        assertTrue(RecordingStudio.nextLabel(past, now).contains("غدًا"))
        assertTrue(RecordingStudio.nextLabel(future.copy(enabled = false), now).contains("معطّل"))
        val once = RecordingStudio.ScheduledItem(
            id = "h", title = "مرة", startMinutes = 600, durationMinutes = 5,
            repeat = RecordingStudio.REPEAT_ONCE, lastFiredDateKey = RecordingStudio.dateKeyOf(now)
        )
        assertTrue(RecordingStudio.nextLabel(once, now).contains("نُفّذ سابقًا"))
        // الساعة العربية صفرية الأرقام
        assertEquals("08:30", RecordingStudio.clockOf(8 * 60 + 30))
    }

    // ================= الملف: WAV 24 بت قانوني =================

    @Test
    fun wav_writer_24bit_header_roundtrip() {
        val dir = tmpDir()
        val f = File(dir, "t24.wav")
        val frames = 9600 // 200ms على 48 كيلوهرتز
        val bytes = ByteArray(frames * 3)
        val samples = FloatArray(frames) { 0.3f * sin(2 * PI * 880.0 * it / 48000.0).toFloat() }
        RecordingStudio.PcmWide.floatsToBytes24(samples, frames, bytes)
        WavFileWriter(f, 48_000, 1, 24).use { it.write(bytes) }
        val h = WavHeader.read(f)
        assertNotNull(h)
        assertEquals(48_000, h.sampleRate)
        assertEquals(1, h.channels)
        assertEquals(24, h.bitsPerSample)
        assertEquals(frames * 3L, h.dataBytes)
        assertEquals(200L, WavFileWriter.durationMsOf(h.dataBytes, h.sampleRate, h.channels, h.bitsPerSample))
        dir.deleteRecursively()
    }

    // ================= الفوقية: مساعدو الإدراج في المونتاج =================

    @Test
    fun overdub_ensuresTrack_onEmptyProject() {
        val (p, id) = MontageLogic.ensureOverdubTrack(MontageProject())
        assertEquals(1, p.tracks.size)
        val track = p.tracks.first()
        assertEquals(id, track.id)
        assertEquals(MontageLogic.OVERDUB_TRACK_NAME, track.name)
        assertEquals(MontageLogic.TYPE_DIALOGUE, track.type) // صوت المذيع لا يخفض تحت التعليق
    }

    @Test
    fun overdub_prefersDialogueTrack_whenExists() {
        val p = MontageProject(
            tracks = listOf(
                MontageTrack(id = "music", name = "موسيقى خلفية", type = MontageLogic.TYPE_MUSIC),
                MontageTrack(id = "dlg", name = "حوار", type = MontageLogic.TYPE_DIALOGUE)
            )
        )
        val (out, id) = MontageLogic.ensureOverdubTrack(p)
        assertEquals("dlg", id)
        assertEquals(2, out.tracks.size) // لا مسار جديد أُنشئ
    }

    @Test
    fun overdub_clipPlacement_fullWindow() {
        val clip = MontageLogic.overdubClip(
            path = "/tmp/rec.wav", title = "  تعليق الفاصل  ",
            durationMs = 7_500L, startMs = 12_000L, trackId = "tr1"
        )
        assertEquals(12_000L, clip.startMs)
        assertEquals(7_500L, clip.durationMs)
        assertEquals(0L, clip.startInFileMs)
        assertEquals(7_500L, clip.endInFileMs)
        assertEquals("tr1", clip.trackId)
        assertEquals("تعليق الفاصل", clip.title)
        // المدة الصفرية (فشل الالتقاط): نافذة مغلقة آمنة
        val zero = MontageLogic.overdubClip("/tmp/x.wav", "x", 0L, 0L, "tr1")
        assertEquals(0L, zero.endInFileMs)
    }

    // ================= الإعدادات: دورة كاملة بين الجلسات =================

    @Test
    fun settings_recording_roundtrip_and_firedMark() {
        val dir = tmpDir()
        val store = SettingsStore(File(dir, "settings.json"))
        store.setRecordingStudio(
            RecordingJson(
                bits = 32, rateHz = 96_000, monitor = true,
                deviceNames = listOf("ميكروفون خارجي"),
                schedules = listOf(
                    SchedRecJson(id = "s1", title = "صباحي", startMinutes = 500, durationMinutes = 10, repeat = "daily", enabled = true),
                    SchedRecJson(id = "", title = "بلا معرّف يُسقَط", startMinutes = 500, repeat = "daily")
                )
            )
        )
        val reloaded = SettingsStore(File(dir, "settings.json"))
        val cfg = reloaded.recordingStudio()
        assertEquals(32, cfg.bits)
        assertEquals(96_000, cfg.rateHz)
        assertEquals(true, cfg.monitor)
        assertEquals(listOf("ميكروفون خارجي"), cfg.deviceNames)
        val items = reloaded.recordingSchedules()
        assertEquals(1, items.size) // البند بلا معرّف سُقط عند القراءة
        assertEquals("s1", items[0].id)
        assertEquals(10, items[0].durationMinutes)

        // تحديث مفتاح آخر تنفيذ يكتب فعليًا ويقرأ من جديد
        reloaded.setScheduleFired("s1", "2026-11-11")
        assertEquals("2026-11-11", SettingsStore(File(dir, "settings.json")).recordingSchedules()[0].lastFiredDateKey)
        dir.deleteRecursively()
    }

    // ================= (1)(2)(3) بوابة الجلسة الخاوية على المحرك =================

    @Test
    fun engine_rejectsStart_withoutDevices_andGuardsDoubleStart() {
        val engine = RecordingEngine()
        val dir = tmpDir()
        // قائمة خاوية: لا بدء والحالة تبقى خاولة
        assertFalse(engine.start(emptyList(), RecordingStudio.Quality(16, 48_000), dir, false))
        assertEquals(RecordingStudio.RecState.IDLE, engine.state.value)
        // آلة الحالات على المحرك نفسه: إيقاف بلا جلسة يُردّ بلا ملفات
        assertTrue(engine.stop().isEmpty())
        assertEquals(RecordingStudio.RecState.IDLE, engine.state.value)
        dir.deleteRecursively()
    }

    @Test
    fun engine_pause_resume_stop_guarded_whenIdle() {
        val engine = RecordingEngine()
        assertFalse(engine.pause())
        assertFalse(engine.resume())
        assertTrue(engine.stop().isEmpty())
    }
}
