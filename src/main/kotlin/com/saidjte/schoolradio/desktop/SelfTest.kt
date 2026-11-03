package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.LevelMeter
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.DevicesJson
import com.saidjte.schoolradio.desktop.data.MeterLogic
import com.saidjte.schoolradio.desktop.data.MeterZone
import com.saidjte.schoolradio.desktop.data.ReadingJson
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.ScheduleStore
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.SegmentStatus
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.ScriptStore
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScriptSource
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.StudioLogic
import com.saidjte.schoolradio.desktop.data.TeleprompterNames
import com.saidjte.schoolradio.desktop.ui.GuiTokens
import java.io.File
import javax.sound.sampled.AudioFormat
import kotlin.math.PI
import kotlin.math.sin

/**
 * فحص ذاتي بدون واجهة رسومية: يتحقق من النواة الصوتية كاملة (كتابة/قراءة WAV،
 * مقياس المستوى، محلّل MP3، ماسح التسجيلات) ثم يطبع SELFTEST OK.
 * يُشغَّل بـ: java -jar SchoolRadioDesktop.jar selftest
 */
object SelfTest {

    fun run() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "sr-desktop-selftest-${System.currentTimeMillis()}")
        tmp.mkdirs()
        try {
            // 1) كتابة/قراءة WAV كاملة الدورة
            val wav = File(tmp, "t1.wav")
            val sr = 8000
            val samples = 8000 // ثانية واحدة
            val writer = WavFileWriter(wav, sr, 1)
            val data = ByteArray(samples * 2)
            for (i in 0 until samples) {
                val v = (sin(2.0 * PI * 220.0 * i / sr) * 12000).toInt()
                data[2 * i] = (v and 0xFF).toByte()
                data[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
            }
            writer.write(data)
            writer.close()
            val info = WavHeader.read(wav) ?: error("WavHeader.read فشل")
            check(info.sampleRate == sr) { "sampleRate=${info.sampleRate}" }
            check(info.channels == 1) { "channels=${info.channels}" }
            check(info.dataBytes == samples * 2L) { "dataBytes=${info.dataBytes}" }
            check(
                WavFileWriter.durationMsOf(info.dataBytes, info.sampleRate, info.channels, info.bitsPerSample) == 1000L
            ) { "durationMs" }

            // 2) مقياس المستوى
            check(LevelMeter.normalized(0.0) == 0.0) { "normalized(0)" }
            check(LevelMeter.rms(data, data.size) > 0.2) { "rms(sine)" }
            check(LevelMeter.normalized(LevelMeter.rms(data, data.size)) > 0.1) { "normalized(sine)" }

            // 3) محلّل MP3 (ترويسة MPEG1 Layer III: 128kbps / 44100Hz / ستيريو)
            val fh = Mp3Info.parseFrame(
                byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte()), 0
            ) ?: error("parseFrame فشل")
            check(fh.sampleRate == 44100) { "mp3 sampleRate=${fh.sampleRate}" }
            check(fh.bitRateKbps == 128) { "mp3 bitrate=${fh.bitRateKbps}" }
            check(fh.samplesPerFrame == 1152) { "mp3 spf=${fh.samplesPerFrame}" }
            check(fh.frameSize == 417) { "mp3 frameSize=${fh.frameSize}" }

            // 4) ماسح التسجيلات
            val items = RecordingsScanner.scan(tmp)
            check(items.size == 1 && items[0].displayName == "t1") { "scanner=$items" }

            // 5) سكريبت المذيع: مخزن + حسابات + دورة حفظ/استرجاع
            val scriptStore = ScriptStore(File(tmp, "script.json"))
            val secA = scriptStore.add("مقدمة", "السلام عليكم ورحمة الله وبركاته")
            scriptStore.add("نشرات", "أخبار المدرسة اليوم\nبسم الله نبدأ")
            check(scriptStore.sections().size == 2) { "script add" }
            check(ScriptStats.wordCount("أخبار المدرسة اليوم\nبسم الله نبدأ") == 6) { "wordCount" }
            check(ScriptStats.secondsFor("x".repeat(1)) == 1L) { "ceil 1 word" }
            check(scriptStore.move(secA.id, +1)) { "script move" }
            check(scriptStore.sections()[0].body.contains("أخبار")) { "script order" }
            scriptStore.update(secA.id, "مقدمة معدلة", "نص جديد")
            val reloadedScript = ScriptStore(File(tmp, "script.json"))
            check(reloadedScript.sections().size == 2) { "script reload" }
            check(reloadedScript.sections().any { it.title == "مقدمة معدلة" }) { "script update persisted" }

            // 6) جدولة الفقرات: مخزن + رياضيات + دورة حفظ/استرجاع
            val schStore = ScheduleStore(File(tmp, "schedule.json"))
            schStore.setEpisodeStart(8 * 60 + 15)
            val it1 = schStore.add("افتتاح", SegmentKind.LIVE, 120, null, "")
            schStore.add("نشيد", SegmentKind.RECORDING, 180, "/x/nashid.wav", "بطيء")
            check(schStore.items().size == 2) { "schedule add" }
            val offs = ScheduleMath.offsetsSeconds(schStore.items())
            check(offs[0] == 0L && offs[1] == 120L) { "offsets=$offs" }
            val clocks = ScheduleMath.plannedClocks(schStore.items(), schStore.episodeStartMinutes())
            check(clocks[0] == "08:15" && clocks[1] == "08:17") { "clocks=$clocks" }
            check(ScheduleMath.clockOf(25 * 60) == "01:00") { "clock wrap" }
            check(
                ScheduleMath.statuses(1, 3) ==
                        listOf(SegmentStatus.DONE, SegmentStatus.CURRENT, SegmentStatus.UPCOMING)
            ) { "statuses" }
            check(
                ScheduleMath.statuses(-1, 2) ==
                        listOf(SegmentStatus.UPCOMING, SegmentStatus.UPCOMING)
            ) { "statuses idle" }
            val reloadedSch = ScheduleStore(File(tmp, "schedule.json"))
            check(reloadedSch.episodeStartMinutes() == 495) { "episode start persisted" }
            check(reloadedSch.items()[1].kind == SegmentKind.RECORDING) { "kind persisted" }
            check(reloadedSch.items()[1].mediaPath == "/x/nashid.wav") { "mediaPath persisted" }
            check(reloadedSch.move(it1.id, +5) == false) { "move bounds" }

            // 7) الاستوديو المدمج: أولوية عرض السكريبت + حالة الهواء + الساعة
            val secs = listOf(ScriptSection(1, "مقدمة", "نص المقدمة"), ScriptSection(2, "نشرات", "نص النشرات"))
            val studioItems = listOf(
                ScheduleItem(10, "افتتاح", SegmentKind.LIVE, 60, null, "", 2L),
                ScheduleItem(11, "نشيد", SegmentKind.RECORDING, 60)
            )
            val r1 = StudioLogic.resolveScriptSection(studioItems, true, 0, secs, 1L)
                ?: error("resolve r1 null")
            check(r1.second == ScriptSource.FROM_SCHEDULE && r1.first.id == 2L) { "r1=$r1" }
            // فقرة بلا ارتباط → تراجع للاختيار اليدوي
            val r2 = StudioLogic.resolveScriptSection(studioItems, true, 1, secs, 1L)
                ?: error("resolve r2 null")
            check(r2.second == ScriptSource.MANUAL && r2.first.id == 1L) { "r2=$r2" }
            // بلا تشغيل وبلا اختيار → أول فقرة
            val r3 = StudioLogic.resolveScriptSection(studioItems, false, -1, secs, null)
                ?: error("resolve r3 null")
            check(r3.first.id == 1L && r3.second == ScriptSource.MANUAL) { "r3=$r3" }
            // رابط قديم (فقرة سكريبت محذوفة) → تراجع آمن
            val stale = listOf(ScheduleItem(10, "افتتاح", SegmentKind.LIVE, 60, null, "", 99L))
            val r4 = StudioLogic.resolveScriptSection(stale, true, 0, secs, 2L)
                ?: error("resolve r4 null")
            check(r4.second == ScriptSource.MANUAL && r4.first.id == 2L) { "r4=$r4" }
            // سكريبت فارغ → null
            check(StudioLogic.resolveScriptSection(studioItems, true, 0, emptyList(), 1L) == null) { "empty script" }
            // حالة الهواء
            check(StudioLogic.status(true, false, false).onAir) { "onAir mic" }
            check(StudioLogic.status(false, true, false).onAir) { "onAir rec" }
            check(StudioLogic.status(false, false, true).onAir) { "onAir music" }
            check(!StudioLogic.status(false, false, false).onAir) { "off air" }
            // الساعة
            val utc = java.util.TimeZone.getTimeZone("UTC")
            check(StudioLogic.clockText(0L, utc) == "00:00:00") { "clock zero=${StudioLogic.clockText(0L, utc)}" }
            check(StudioLogic.clockText(3_661_000L, utc) == "01:01:01") { "clock 1h=${StudioLogic.clockText(3_661_000L, utc)}" }
            // معرف ارتباط سالب يُنقّى من المخزن
            schStore.add("مربوطة", SegmentKind.LIVE, 30, null, "", -5L)
            check(schStore.items().last().scriptSectionId == null) { "sanitize negative link" }

            // 8) طبقة أجهزة الصوت — الإدخال والإخراج: تعداد آمن + صيغ + رسائل عربية
            check(AudioDevices.listMics().isNotEmpty()) { "listMics empty" }
            val outs = AudioDevices.listOutputs()
            check(outs.isNotEmpty()) { "listOutputs empty" }
            check(outs.first().mixerInfo == null && outs.first().name == AudioDevices.DEFAULT_OUTPUT_NAME) {
                "outputs default first"
            }
            check(AudioDevices.CANDIDATE_FORMATS.size >= 12) { "candidates=${AudioDevices.CANDIDATE_FORMATS.size}" }
            check(
                AudioDevices.CANDIDATE_FORMATS.all {
                    it.sampleSizeInBits == 16 && !it.isBigEndian && it.encoding == AudioFormat.Encoding.PCM_SIGNED
                }
            ) { "candidate formats not pcm16le" }
            check(AudioDevices.CANDIDATE_FORMATS.any { it.sampleRate == 48000f && it.channels == 1 }) { "48k mono" }
            check(AudioDevices.pickFormat(null).sampleSizeInBits == 16) { "pickFormat" }
            val inMsg = AudioDevices.describeInputFailure(
                RuntimeException("No line matching interface TargetDataLine supporting format is supported.")
            )
            check(inMsg.contains("ميكروفون")) { "input arabic: $inMsg" }
            val outMsg = AudioDevices.describeOutputFailure(
                RuntimeException("No line matching interface SourceDataLine supporting format is supported.")
            )
            check(outMsg.contains("سماعة")) { "output arabic: $outMsg" }

            // 9) مقياس البث المقطعي: المقاطع والمناطق والذروة
            check(MeterLogic.SEGMENTS == 24) { "segments" }
            check(MeterLogic.litSegments(0f) == 0) { "lit zero" }
            check(MeterLogic.litSegments(1f) == 24) { "lit full" }
            check(MeterLogic.litSegments(0.5f) == 12) { "lit half" }
            check(MeterLogic.litSegments(-0.7f) == 0) { "lit negative" }
            check(MeterLogic.litSegments(2f) == 24) { "lit over" }
            check(MeterLogic.litSegments(Float.NaN) == 0) { "lit nan" }
            check(MeterLogic.zoneOf(0) == MeterZone.GREEN) { "zone green start" }
            check(MeterLogic.zoneOf(13) == MeterZone.GREEN) { "zone green end" }
            check(MeterLogic.zoneOf(14) == MeterZone.AMBER) { "zone amber start" }
            check(MeterLogic.zoneOf(19) == MeterZone.AMBER) { "zone amber end" }
            check(MeterLogic.zoneOf(20) == MeterZone.RED) { "zone red start" }
            check(MeterLogic.zoneOf(23) == MeterZone.RED) { "zone red end" }
            check(kotlin.math.abs(MeterLogic.peakIndex(0.5f, 0.2f) - 0.48f) < 1e-4f) { "peak decay" }
            check(MeterLogic.peakIndex(0.2f, 0.5f) == 0.5f) { "peak rise" }
            check(kotlin.math.abs(MeterLogic.peakIndex(0.1f, 0.05f) - 0.08f) < 1e-4f) { "peak floor" }
            check(MeterLogic.peakSegment(0f) == -1) { "peak segment silent" }
            check(MeterLogic.peakSegment(0.9f) == 21) { "peak segment 0.9=${MeterLogic.peakSegment(0.9f)}" }
            check(MeterLogic.peakSegment(1f) == 23) { "peak segment full" }

            // 10) الأدوار والمستخدمون — مصفوفة النسخة الهاتفية
            val usersStore = com.saidjte.schoolradio.desktop.data.UsersStore(File(tmp, "users.json"))
            check(com.saidjte.schoolradio.desktop.data.Role.from("GHOST") == com.saidjte.schoolradio.desktop.data.Role.STUDENT)
            check(com.saidjte.schoolradio.desktop.data.Role.canManage(com.saidjte.schoolradio.desktop.data.Role.TEACHER))
            check(!com.saidjte.schoolradio.desktop.data.Role.canManage(com.saidjte.schoolradio.desktop.data.Role.STUDENT))
            check(com.saidjte.schoolradio.desktop.data.Role.canAdminister(com.saidjte.schoolradio.desktop.data.Role.ADMIN))
            check(!com.saidjte.schoolradio.desktop.data.Role.canAdminister(com.saidjte.schoolradio.desktop.data.Role.TEACHER))
            val u1 = usersStore.add("أمين", com.saidjte.schoolradio.desktop.data.Role.ADMIN)
            check(u1 != null && usersStore.currentUser()?.id == u1.id)
            check(usersStore.add("أمين", com.saidjte.schoolradio.desktop.data.Role.STUDENT) == null) // فريد
            check(usersStore.setCurrent(usersStore.add("معلمة", com.saidjte.schoolradio.desktop.data.Role.TEACHER)!!.id))
            check(usersStore.currentUser()?.role == com.saidjte.schoolradio.desktop.data.Role.TEACHER)
            check(com.saidjte.schoolradio.desktop.data.UsersStore(File(tmp, "users.json")).users().size == 2)

            // 11) الجدولة الأسبوعية + سجل التنفيذ — قيد فريد حدث+تاريخ
            val zoneUtc = java.util.TimeZone.getTimeZone("UTC")
            val cal = java.util.Calendar.getInstance(zoneUtc).apply {
                set(2026, 0, 5, 8, 17, 0); set(java.util.Calendar.MILLISECOND, 0)
            }
            val monday817 = cal.timeInMillis
            val wEvents = listOf(
                com.saidjte.schoolradio.desktop.data.WeeklyEvent(
                    1, "إذاعة الصباح", 1, 8 * 60 + 15, true, false
                )
            )
            val d = com.saidjte.schoolradio.desktop.data.WeeklyMath.decide(wEvents, emptySet(), monday817, zoneUtc)
            check(d.size == 1 && d[0].status == com.saidjte.schoolradio.desktop.data.FireStatus.FIRED) { "decide=$d" }
            check(com.saidjte.schoolradio.desktop.data.WeeklyMath.decide(
                wEvents, setOf("1@2026-01-05"), monday817, zoneUtc
            ).isEmpty()) { "no duplicate same date" }
            val exec = com.saidjte.schoolradio.desktop.data.ExecutionsStore(File(tmp, "executions.json"))
            check(exec.log(1, "إذاعة الصباح", "2026-01-05", monday817, com.saidjte.schoolradio.desktop.data.FireStatus.FIRED))
            check(!exec.log(1, "إذاعة الصباح", "2026-01-05", monday817, com.saidjte.schoolradio.desktop.data.FireStatus.FIRED)) { "unique" }
            check(exec.has(1, "2026-01-05"))

            // 12) المكتبة المصنفة + النسخ الاحتياطي + قائمة التشغيل الدائمة
            val lib = com.saidjte.schoolradio.desktop.data.MusicLibraryStore(File(tmp, "library.json"))
            check(lib.categories().size == 7) { "seven defaults=${lib.categories()}" }
            val mediaFile = File(tmp, "media1.wav").apply {
                writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0)) // ملف غير صالح — للمسارات فقط
            }
            check(lib.addFiles(listOf(mediaFile), "الأناشيد المدرسية").added.size == 1)
            check(lib.addCategory("جينغلات") && lib.categories().size == 8)
            check(!lib.deleteCategory("القرآن الكريم")) // الافتراضية محمية
            check(mediaFile.delete())
            check(lib.missingPaths().size == 1) { "missing detection" }
            val bak = File(tmp, "backup.schoolradio.json")
            val exp = com.saidjte.schoolradio.desktop.data.Backup.export(bak, tmp, APP_VERSION)
            check(exp.ok) { "backup export: ${exp.message}" }
            check(com.saidjte.schoolradio.desktop.data.Backup.verify(bak)?.containsKey("library.json") == true)
            val pl = com.saidjte.schoolradio.desktop.data.PlaylistStore(File(tmp, "playlist.json"))
            pl.setPaths(pl.activeId(), listOf("/a.mp3", "/b.mp3"), 1)
            pl.setRepeat(true)
            pl.create("فقرة الصباح")
            check(pl.playlists().size == 2) { "playlist create" }
            check(pl.active().name == "فقرة الصباح") { "playlist create activates" }
            check(pl.create("فقرة الصباح") == null) { "playlist duplicate rejected" }
            check(pl.setActive(pl.doc().playlists[0].id)) { "playlist setActive" }
            check(pl.paths() == listOf("/a.mp3", "/b.mp3") && pl.currentIndex() == 1) { "playlist active content" }
            val plReload = com.saidjte.schoolradio.desktop.data.PlaylistStore(File(tmp, "playlist.json"))
            check(plReload.playlists().size == 2) { "playlists persisted" }
            check(plReload.paths() == listOf("/a.mp3", "/b.mp3") && plReload.currentIndex() == 1 && plReload.repeatAll()) { "playlist content persisted" }
            check(plReload.delete(plReload.activeId()) && plReload.playlists().size == 1) { "playlist delete" }
            check(!plReload.delete(plReload.activeId())) { "playlist min-one invariant" }
            // ترحيل النسخة السابقة (قائمة وحيدة بلا أسماء) إلى قائمة مسماة
            val legacy = File(tmp, "legacy-playlist.json")
            legacy.writeText("""{"paths": ["/x.mp3", "/y.mp3"], "currentIndex": 1, "repeatAll": false}""")
            val migrated = com.saidjte.schoolradio.desktop.data.PlaylistStore(legacy)
            check(migrated.playlists().size == 1 && migrated.active().name == "القائمة الرئيسية") { "playlist migration name" }
            check(migrated.paths() == listOf("/x.mp3", "/y.mp3") && migrated.currentIndex() == 1) { "playlist migration content" }
            val meta = com.saidjte.schoolradio.desktop.audio.RecordingMeta("سارة", "معلمة", 5L, 10L)
            check(meta.save(com.saidjte.schoolradio.desktop.audio.RecordingMeta.sidecarOf(File(tmp, "x.wav"))))
            check(com.saidjte.schoolradio.desktop.audio.RecordingMeta.load(
                com.saidjte.schoolradio.desktop.audio.RecordingMeta.sidecarOf(File(tmp, "x.wav"))
            )?.recorderName == "سارة")

            // 13) وحدة المزج: DSP نقي (معالج المذيع + المعادل) + نماذج الحفظ
            val fs = 44100
            // تمرير عالٍ: 40هرتز مقصوص بقوة و400هرتز يمر
            val hpLow = com.saidjte.schoolradio.desktop.audio.DspMeasure.chainDb(40.0, fs) {
                it.highPass(fs.toDouble(), 85.0)
            }
            val hpHigh = com.saidjte.schoolradio.desktop.audio.DspMeasure.chainDb(400.0, fs) {
                it.highPass(fs.toDouble(), 85.0)
            }
            check(hpLow < -12.0) { "hpf low=$hpLow" }
            check(hpHigh > -1.5 && hpHigh < 1.5) { "hpf high=$hpHigh" }
            // قمة حضور +6 عند 3.2ك
            val presence = com.saidjte.schoolradio.desktop.audio.DspMeasure.chainDb(3200.0, fs) {
                it.peaking(fs.toDouble(), 3200.0, 6.0, 1.0)
            }
            check(presence > 4.5 && presence < 7.5) { "peaking=$presence" }
            // رفّ منخفض +6 عند 80: باس يرتفع والوسط يبقى
            val shelfLow = com.saidjte.schoolradio.desktop.audio.DspMeasure.chainDb(40.0, fs) {
                it.lowShelf(fs.toDouble(), 80.0, 6.0)
            }
            val shelfMid = com.saidjte.schoolradio.desktop.audio.DspMeasure.chainDb(1000.0, fs) {
                it.lowShelf(fs.toDouble(), 80.0, 6.0)
            }
            check(shelfLow > 4.5) { "lowShelf low=$shelfLow" }
            check(shelfMid > -1.0 && shelfMid < 1.0) { "lowShelf mid=$shelfMid" }

            // معالج المذيع: الصمت يبقى صمتًا، والمحدّد لا يتجاوز السقف أبدًا
            val vp = com.saidjte.schoolradio.desktop.audio.VoiceProcessor()
            vp.configure(fs, 1)
            val silence = ShortArray(2048)
            vp.process(silence, silence.size, 1.0f)
            check(silence.all { it == 0.toShort() }) { "silence out" }
            val loud = ShortArray(2048) { (0.95 * 32767 * kotlin.math.sin(2.0 * Math.PI * 500.0 * it / fs)).toInt().toShort() }
            vp.process(loud, loud.size, 1.0f)
            check(loud.maxOf { kotlin.math.abs(it.toInt()) } <= (0.935 * 32767 + 2).toInt()) { "limiter ceiling" }
            // معطل = هوية (بلا تلوين)
            val vp2 = com.saidjte.schoolradio.desktop.audio.VoiceProcessor()
            vp2.configure(fs, 1)
            vp2.setParams(com.saidjte.schoolradio.desktop.audio.VoiceProcessor.Params(enabled = false))
            val clean = ShortArray(2048) { ((0.3 * 32767) * kotlin.math.sin(2.0 * Math.PI * 440.0 * it / fs)).toInt().toShort() }
            val cleanIn = clean.copyOf()
            vp2.process(clean, clean.size, 1.0f)
            check(clean.contentEquals(cleanIn)) { "bypass identity" }
            // البوابة: نغمة أعمق من العتبة تُقفل تدريجيًا
            val vp3 = com.saidjte.schoolradio.desktop.audio.VoiceProcessor()
            vp3.configure(fs, 1)
            vp3.setParams(
                com.saidjte.schoolradio.desktop.audio.VoiceProcessor.Params(
                    gateOn = true, gateThresholdDb = -45f,
                    hpfOn = false, compOn = false
                )
            )
            val faint = ShortArray(2048) { ((0.002 * 32767) * kotlin.math.sin(2.0 * Math.PI * 440.0 * it / fs)).toInt().toShort() }
            repeat(30) { vp3.process(faint, faint.size, 1.0f) }
            check(vp3.gateClosed) { "gate closed flag" }
            check(faint.maxOf { kotlin.math.abs(it.toInt()) } < 0.05 * 0.002 * 32767) { "gate attenuated" }

            // المعادل: شرائح صفرية = هوية، وباص +12 يرفع فعليًا
            val eq = com.saidjte.schoolradio.desktop.audio.MusicEq()
            eq.configure(fs, 1)
            eq.setEnabled(true)
            eq.setBands(floatArrayOf(0f, 0f, 0f, 0f, 0f))
            val tone = ShortArray(4096) { ((0.4 * 32767) * kotlin.math.sin(2.0 * Math.PI * 1000.0 * it / fs)).toInt().toShort() }
            val toneIn = tone.copyOf()
            eq.process(tone, tone.size, 1.0f)
            check(tone.contentEquals(toneIn)) { "eq flat identity" }
            eq.setBands(floatArrayOf(12f, 0f, 0f, 0f, 0f))
            val bassy = ShortArray(4096) { ((0.3 * 32767) * kotlin.math.sin(2.0 * Math.PI * 60.0 * it / fs)).toInt().toShort() }
            val bassyIn = bassy.copyOf()
            eq.process(bassy, bassy.size, 1.0f)
            val boost = bassy.maxOf { kotlin.math.abs(it.toInt()) }.toDouble() / bassyIn.maxOf { kotlin.math.abs(it.toInt()) }
            check(boost > 1.8) { "eq bass boost=$boost" }

            // نماذج المزج: JSON ناقص لا يصمت القناة + القيم المشوهة تُحسم
            val m = com.saidjte.schoolradio.desktop.data.MixerLogic
            check(m.resolve(null).micFader == 1.0f) { "resolve null" }
            val partial = m.resolve(com.saidjte.schoolradio.desktop.data.MixerJson(musicFader = -3f))
            check(partial.micFader == 1.0f && partial.masterFader == 1.0f) { "partial defaults" }
            check(partial.musicFader == 0f) { "wild fader coerced" }
            val partialEq = m.resolve(com.saidjte.schoolradio.desktop.data.MixerJson(
                musicEq = com.saidjte.schoolradio.desktop.data.MusicEqJson(enabled = true, bands = listOf(4f, 2f))
            ))
            check(partialEq.musicEq.bands.size == 5 && partialEq.musicEq.bands[4] == 0f) { "eq padded" }
            val ghostPreset = m.resolveVoice(com.saidjte.schoolradio.desktop.data.VoiceFxJson(preset = "غير موجود"))
            check(ghostPreset.preset == null) { "ghost preset" }
            val announcer = m.applyVoicePreset(com.saidjte.schoolradio.desktop.data.VoiceFxState(), "صوت المذيع")
            check(announcer.presenceDb == 4.0f && announcer.compOn && announcer.gateOn) { "voice preset" }
            // v0.18.0: حزمة البث الكاملة تفرض كل الحلقات، وأعماق الخفض تُحسم بالتسامح
            val kit = m.applyVoicePreset(com.saidjte.schoolradio.desktop.data.VoiceFxState(hpfOn = false, agcOn = false), "حزمة البث")
            check(kit.hpfOn && kit.agcOn && kit.gateOn && kit.compOn) { "broadcast kit full chain" }
            check(m.duckPresetName(-12f) == "متوسط" && m.duckPresetName(-9f) == null) { "duck depth presets" }
            val warm = m.applyMusicPreset(com.saidjte.schoolradio.desktop.data.MusicEqState(), "باص دافئ")
            check(warm.bands[0] == 5f && warm.bands.size == 5) { "music preset" }

            // دورة حفظ/استرجاع كاملة عبر SettingsStore
            val st = com.saidjte.schoolradio.desktop.data.SettingsStore(File(tmp, "settings-mix.json"))
            st.setSchoolName("مدرسة الاختبار")
            st.setMixer(com.saidjte.schoolradio.desktop.data.MixerState(
                micFader = 1.2f, musicFader = 0.8f, masterFader = 1.1f,
                voice = announcer, musicEq = warm
            ))
            val stReload = com.saidjte.schoolradio.desktop.data.SettingsStore(File(tmp, "settings-mix.json"))
            val restored = stReload.mixer()
            check(restored.micFader == 1.2f && restored.musicFader == 0.8f && restored.masterFader == 1.1f) { "mixer roundtrip faders" }
            check(restored.voice.presenceDb == 4.0f && restored.voice.preset == "صوت المذيع") { "mixer roundtrip voice" }
            check(restored.musicEq.bands[0] == 5f && restored.musicEq.preset == "باص دافئ") { "mixer roundtrip eq" }
            check(stReload.schoolName() == "مدرسة الاختبار") { "mixer save keeps school name" }
            check(restored.voice.agcOn && restored.duck.enabled && restored.aec.enabled) { "mixer defaults new modules" }

            // 14) الإذاعة الاحترافية: AGC + إلغاء الصدى AEC + الخفض التلقائي
            val fs2 = 44100
            // AGC: إشارة خافتة يرفعها الكسب نحو الهدف، وقوية يخفضها
            val agc = com.saidjte.schoolradio.desktop.audio.VoiceProcessor()
            agc.configure(fs2, 1)
            agc.setParams(
                com.saidjte.schoolradio.desktop.audio.VoiceProcessor.Params(
                    hpfOn = false, gateOn = false, compOn = false,
                    agcOn = true, agcTargetDb = -18f, agcMaxBoostDb = 24f
                )
            )
            val quietSrc = ShortArray(1024) { ((0.02 * 32767) * kotlin.math.sin(2.0 * Math.PI * 440.0 * it / fs2)).toInt().toShort() }
            repeat(120) { val blk = quietSrc.copyOf(); agc.process(blk, blk.size, 1.0f) }
            check(agc.agcCurrentDb > 10f) { "agc boost quiet=${agc.agcCurrentDb}" }
            val agcLoud = com.saidjte.schoolradio.desktop.audio.VoiceProcessor()
            agcLoud.configure(fs2, 1)
            agcLoud.setParams(
                com.saidjte.schoolradio.desktop.audio.VoiceProcessor.Params(
                    hpfOn = false, gateOn = false, compOn = false,
                    agcOn = true, agcTargetDb = -18f, agcMaxBoostDb = 24f
                )
            )
            val loudSrc = ShortArray(1024) { ((0.5 * 32767) * kotlin.math.sin(2.0 * Math.PI * 440.0 * it / fs2)).toInt().toShort() }
            repeat(120) { val blk = loudSrc.copyOf(); agcLoud.process(blk, blk.size, 1.0f) }
            check(agcLoud.agcCurrentDb < -5f) { "agc attenuate loud=${agcLoud.agcCurrentDb}" }
            // تجميد عند الصمت: لا مطاردة ضجيج
            val agcFrozen = com.saidjte.schoolradio.desktop.audio.VoiceProcessor()
            agcFrozen.configure(fs2, 1)
            val silenceBlock = ShortArray(1024)
            repeat(200) { agcFrozen.process(silenceBlock, silenceBlock.size, 1.0f) }
            check(kotlin.math.abs(agcFrozen.agcCurrentDb) < 0.01f) { "agc frozen in silence=${agcFrozen.agcCurrentDb}" }

            // AEC: محاكاة زمن حقيقية — المرجع يُدفع أثناء معالجة كل كتلة ميكروفون،
            // والصدى = مرجع مؤخَّر 120ms بكسب 0.5 (كما يسمعه الميكروفون من السماعة)
            val refRate = 44100
            val blockN = 4096
            val nBlocks = 40
            val delaySamples = (0.120 * refRate).toInt()
            val totalRef = delaySamples + blockN * nBlocks
            // زحزحة واسعة النطاق (موسيقى واقعية) بدل نغمة واحدة غير قابلة لتحديد المسار
            val refSignal = DoubleArray(totalRef) {
                val t = it.toDouble() / refRate
                val freq = 200.0 + 2600.0 * ((t % 2.0) / 2.0)
                0.6 * kotlin.math.sin(2.0 * Math.PI * freq * t)
            }
            val tap = com.saidjte.schoolradio.desktop.audio.FarEndTap(2.5)
            tap.configure(refRate)
            val aec = com.saidjte.schoolradio.desktop.audio.EchoCanceller()
            aec.configure(refRate, 1)
            var inEnergy = 0.0
            var outEnergy = 0.0
            val mic = ShortArray(blockN)
            var pushed = 0
            for (b in 0 until nBlocks) {
                // دفع المرجع حتى لحظة «الآن» (المرجع والميكروفون على الحائط نفسه)
                val target = (b + 1) * blockN
                while (pushed < target) {
                    val n = minOf(512, target - pushed)
                    tap.push(FloatArray(n) { refSignal[pushed + it].toFloat() }, n)
                    pushed += n
                }
                for (f in 0 until blockN) {
                    val idx = b * blockN + f - delaySamples
                    val echo = if (idx >= 0) 0.5 * refSignal[idx] else 0.0
                    mic[f] = (echo * 32767.0).toInt().toShort()
                }
                val pre = mic.copyOf()
                aec.process(mic, mic.size, tap)
                if (b >= 32) {
                    for (f in 0 until blockN) {
                        inEnergy += (pre[f].toDouble() / 32768.0).let { it * it }
                        outEnergy += (mic[f].toDouble() / 32768.0).let { it * it }
                    }
                }
            }
            val cancellationDb = 10.0 * kotlin.math.log10(inEnergy / maxOf(outEnergy, 1e-12))
            check(cancellationDb > 8.0) { "aec cancellation=$cancellationDb" }
            check(aec.locked) { "aec locked" }
            check(aec.erleDb > 6f) { "aec erle=${aec.erleDb}" }
            // AEC المراقبة (الجيل الثالث — تكافؤ الهاتف): صوت المذيع يعود من سماعة
            // المراقبة نسخة مؤخّرة 90ms بكسب 0.35 — المانع الثاني بلا كشف حديث مزدوج
            // (المرجع نسخة من الصوت نفسه فجيلجل يخطئ فيه بالضرورة) يقفل ويكبح العائد
            run {
                val fsM = 48000
                val blockM = 2048
                val nBlocksM = 64 // ~2.7 ثانية — تكفي لبحث التأخير الأول (كتل 46) وإعادة التقارب
                val dMon = (0.090 * fsM).toInt()
                val sigM = DoubleArray(dMon + blockM * nBlocksM) {
                    val t = it.toDouble() / fsM
                    val freq = 250.0 + 1900.0 * ((t % 1.6) / 1.6)
                    0.5 * kotlin.math.sin(2.0 * Math.PI * freq * t)
                }
                val tapM = com.saidjte.schoolradio.desktop.audio.FarEndTap(2.5)
                tapM.configure(fsM)
                val aecM = com.saidjte.schoolradio.desktop.audio.EchoCanceller()
                aecM.doubleTalkGuard = false
                aecM.configure(fsM, 1)
                val micM = ShortArray(blockM)
                var pushedM = 0
                var inEM = 0.0
                var outEM = 0.0
                for (b in 0 until nBlocksM) {
                    val targetM = (b + 1) * blockM
                    while (pushedM < targetM) {
                        val k = minOf(512, targetM - pushedM)
                        tapM.push(FloatArray(k) { sigM[pushedM + it].toFloat() }, k)
                        pushedM += k
                    }
                    for (f in 0 until blockM) {
                        val idx = b * blockM + f - dMon
                        val v = if (idx >= 0) 0.35 * sigM[idx] else 0.0
                        micM[f] = (v * 32767.0).toInt().toShort()
                    }
                    val preM = micM.copyOf()
                    aecM.process(micM, micM.size, tapM)
                    if (b >= nBlocksM - 10) {
                        for (f in 0 until blockM) {
                            inEM += (preM[f].toDouble() / 32768.0).let { it * it }
                            outEM += (micM[f].toDouble() / 32768.0).let { it * it }
                        }
                    }
                }
                val cancM = 10.0 * kotlin.math.log10(inEM / maxOf(outEM, 1e-12))
                check(cancM > 8.0) { "monitor aec cancellation=$cancM" }
                check(aecM.locked) { "monitor aec locked" }
            }
            // FFT نقي: دورة كاملة + التفاف خطي يطابق المباشر
            run {
                val n = 64
                val re = DoubleArray(n) { kotlin.math.sin(2.0 * Math.PI * 3 * it / n) }
                val im = DoubleArray(n)
                val re0 = re.copyOf()
                com.saidjte.schoolradio.desktop.audio.Fft.fft(re, im)
                com.saidjte.schoolradio.desktop.audio.Fft.ifft(re, im)
                var maxErr = 0.0
                for (i in 0 until n) maxErr = maxOf(maxErr, kotlin.math.abs(re[i] - re0[i]) + kotlin.math.abs(im[i]))
                check(maxErr < 1e-9) { "fft roundtrip=$maxErr" }
                val a = doubleArrayOf(1.0, 2.0, -1.0, 3.0)
                val b = doubleArrayOf(0.5, -1.0, 2.0)
                val conv = com.saidjte.schoolradio.desktop.audio.Fft.linearConvolve(a, b)
                val direct = DoubleArray(a.size + b.size - 1)
                for (i in a.indices) for (j in b.indices) direct[i + j] += a[i] * b[j]
                var convErr = 0.0
                for (i in direct.indices) convErr = maxOf(convErr, kotlin.math.abs(conv[i] - direct[i]))
                check(convErr < 1e-9) { "fft convolve=$convErr" }
            }
            // بوابة الصيغ: الملفات غير القابلة للفك لا تدخل المكتبة — تُعدّ وتُعرض
            run {
                val libDir = File(tmp, "libguard").apply { mkdirs() }
                val goodWav = File(libDir, "g.wav")
                val gw = WavFileWriter(goodWav, 8000, 1)
                gw.write(ByteArray(1600))
                gw.close()
                val fakeM4a = File(libDir, "bad.m4a").apply { writeBytes(ByteArray(64)) }
                val store = com.saidjte.schoolradio.desktop.data.MusicLibraryStore(File(tmp, "libguard.json"))
                val r = store.addFiles(listOf(goodWav, fakeM4a), "فقرات متنوعة")
                check(r.added.size == 1 && r.skippedUnsupported == 1) {
                    "library guard ${r.added.size}/${r.skippedUnsupported}"
                }
                check("ogg" in com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS) { "ogg playable" }
                check("flac" in com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS) { "flac playable" }
                // منطق التكرار الثلاثي والعشوائية (منطق خالص ببذرة مثبّتة)
                val rng = kotlin.random.Random(7)
                val pm = com.saidjte.schoolradio.desktop.data.PlaylistMath
                val mOFF = com.saidjte.schoolradio.desktop.data.RepeatMode.OFF
                val mALL = com.saidjte.schoolradio.desktop.data.RepeatMode.ALL
                val mONE = com.saidjte.schoolradio.desktop.data.RepeatMode.ONE
                check(pm.nextIndex(5, 4, mOFF, false, true, rng) == -1) { "repeat off stops at end" }
                check(pm.nextIndex(5, 4, mOFF, false, false, rng) == 0) { "repeat off manual wraps" }
                check(pm.nextIndex(5, 4, mALL, false, true, rng) == 0) { "repeat all wraps" }
                check(pm.nextIndex(5, 2, mONE, false, true, rng) == 2) { "repeat one replays" }
                check(pm.nextIndex(1, 0, mOFF, true, true, rng) == 0) { "shuffle single track" }
                repeat(20) { check(pm.nextIndex(5, 3, mOFF, true, false, rng) != 3) { "shuffle skips current" } }
            }
            // مرجع راكد (لا موسيقى الآن) = مرور نظيف تام بلا أي مساس
            val aecIdle = com.saidjte.schoolradio.desktop.audio.EchoCanceller()
            aecIdle.configure(refRate, 1)
            val passthrough = ShortArray(1024) { ((0.3 * 32767) * kotlin.math.sin(2.0 * Math.PI * 500.0 * it / refRate)).toInt().toShort() }
            val passthroughIn = passthrough.copyOf()
            Thread.sleep(450) // آخر دفع مرجع صار أقدم من 400ms
            aecIdle.process(passthrough, passthrough.size, tap)
            check(passthrough.contentEquals(passthroughIn)) { "aec stale passthrough" }

            // الخفض التلقائي: هبوط عند الكلام وعودة بعد التعليق
            val dk = com.saidjte.schoolradio.desktop.audio.Ducker()
            var g: Float = dk.process(21.0, micLive = true, voiceActive = true, duckDb = -12.0, holdMs = 1200.0)
            repeat(69) { g = dk.process(21.0, true, true, -12.0, 1200.0) }
            check(g in 0.24f..0.27f) { "duck engaged=$g" } // هدف -12dB ≈ 0.251
            check(dk.active) { "duck active flag" }
            // سكوت: خلال التعليق يبقى منخفضًا ثم يعود تدريجيًا إلى الوحدة
            repeat(20) { g = dk.process(21.0, true, false, -12.0, 1200.0) }
            check(g < 0.4f) { "duck holds=$g" }
            repeat(250) { g = dk.process(21.0, true, false, -12.0, 1200.0) }
            check(g > 0.99f) { "duck released=$g" }
            // بلا بث مباشر: لا خفض أبدًا
            dk.reset()
            repeat(50) { g = dk.process(21.0, micLive = false, voiceActive = false, duckDb = -12.0, holdMs = 1200.0) }
            check(g > 0.99f && !dk.active) { "duck idle=$g" }
            check(g <= 1.0f) { "duck gain bounded" }

            // ============ v0.19.0: المخرجان والمدخلان ============
            // خلاط المدخل الثاني: طيّ القنوات + إعادة التشكيل + الجمع المحبوس
            val pm2 = com.saidjte.schoolradio.desktop.audio.PcmMixer
            val monoDst = FloatArray(8)
            check(pm2.foldToMono(shortArrayOf(200, 100, -100, 100), 4, 2, monoDst) == 2) { "fold stereo frames" }
            check(kotlin.math.abs(monoDst[0] - 150f / 32768f) < 1e-4f) { "fold avg=${monoDst[0]}" }
            val rsDst = FloatArray(512)
            check(pm2.resample(FloatArray(100) { it / 100f }, 100, 48000, 24000, rsDst) == 50) { "resample 48->24" }
            check(pm2.resample(FloatArray(40) { it / 40f }, 40, 8000, 16000, rsDst) in 79..81) { "resample 8->16" }
            val mixBase = shortArrayOf(100, 100, 100, 100)
            check(pm2.addClamped(mixBase, mixBase.size, 2, floatArrayOf(0.5f, 0.5f), 2) == 2) { "addClamped frames" }
            check(mixBase[0] == 16484.toShort() && mixBase[1] == 16484.toShort()) { "addClamped sum=${mixBase[0]}" }
            val hot = shortArrayOf(32000, 32000, 32000, 32000)
            pm2.addClamped(hot, hot.size, 2, floatArrayOf(0.5f, 0.5f), 2)
            check(hot[0] == Short.MAX_VALUE && hot[1] == Short.MAX_VALUE) { "addClamped max" }
            val srBase = shortArrayOf(100, 200, 100, 200)
            pm2.addSameRate(srBase, srBase.size, 2, shortArrayOf(10, 20, 30, 40), 4, 2)
            check(srBase[0] == 110.toShort() && srBase[1] == 220.toShort()) { "addSameRate stereo" }
            check(!pm2.needsConversion(48000, 48000) && pm2.needsConversion(48000, 44100)) { "needsConversion" }

            // الإخراج المزدوج: نفس الكتلة في الجهازين (خطوط وهمية بلا أجهزة)
            class FakeOutLine : com.saidjte.schoolradio.desktop.audio.PcmOutLine {
                val chunks = mutableListOf<ByteArray>()
                var lastVolume = 0f
                var closed = 0
                override fun open(format: AudioFormat, bufferSize: Int) { }
                override fun start() { }
                override fun stop() { }
                override fun flush() { }
                override fun write(b: ByteArray, off: Int, len: Int) { chunks.add(b.copyOfRange(off, off + len)) }
                override fun setVolume(v: Float) { lastVolume = v }
                override fun close() { closed++ }
            }
            val fo1 = FakeOutLine()
            val fo2 = FakeOutLine()
            val twin = com.saidjte.schoolradio.desktop.audio.TwinPcmOut(fo1, fo2)
            val twinData = byteArrayOf(9, 8, 7, 6)
            twin.write(twinData, 0, twinData.size)
            twin.setVolume(0.8f)
            twin.close()
            check(fo1.chunks.size == 1 && fo2.chunks.size == 1) { "twin write both" }
            check(fo1.chunks[0].contentEquals(twinData) && fo2.chunks[0].contentEquals(twinData)) { "twin same bytes" }
            check(kotlin.math.abs(fo1.lastVolume - 0.8f) < 1e-4f && kotlin.math.abs(fo2.lastVolume - 0.8f) < 1e-4f) { "twin volume" }
            check(fo1.closed == 1 && fo2.closed == 1) { "twin close both" }

            // v0.20.0: السرعة الرابعة — أربع سرعات متصاعدة وتسميات مطابقة لعقد الحفظ
            val speeds = com.saidjte.schoolradio.desktop.ui.TeleprompterSpeed.entries
            check(speeds.size == 4) { "teleprompter fourth speed: ${speeds.size}" }
            check(speeds.zipWithNext().all { (a, b) -> a.pxPerSecond < b.pxPerSecond }) { "speeds ascend" }
            check(speeds.map { it.label } == TeleprompterNames.ALL) { "speed labels contract" }

            // v0.20.0: حفظ اختيار الأجهزة وتفضيلات القراءة بين الجلسات
            val devFile = File(tmp, "selftest-settings.json")
            val st1 = SettingsStore(devFile)
            st1.setDevices(DevicesJson(input1 = "ميكروفون فحص", input2 = null, output1 = "سماعات فحص", output2 = "قاعة فحص"))
            st1.setReading(ReadingJson(fontSp = 72f, speed = "سريعة جدًا", follow = false))
            val st2 = SettingsStore(devFile)
            val sd = st2.devices()
            check(sd.input1 == "ميكروفون فحص" && sd.input2 == null && sd.output1 == "سماعات فحص" && sd.output2 == "قاعة فحص") { "devices persist" }
            val sr2 = st2.reading()
            check(sr2.fontSp == 72f && sr2.speed == "سريعة جدًا" && sr2.follow == false) { "reading prefs persist" }
            st1.setReading(ReadingJson(fontSp = 300f, speed = "مجهولة", follow = true))
            check(st1.reading().fontSp == 96f && st1.reading().speed == null) { "reading prefs sanitize" }
            check(StudioLogic.restorePrimary(listOf("أ", "ب"), { it }, "ب") == "ب") { "restorePrimary hit" }
            check(StudioLogic.restorePrimary(listOf("أ", "ب"), { it }, "غائب") == "أ") { "restorePrimary fallback" }
            check(StudioLogic.restoreOptional(listOf("أ", "ب"), { it }, "غائب") == null) { "restoreOptional missing" }
            devFile.delete()

            // v0.21.0: أبعاد حيزات الاستوديو بالفأرة — حسم محبوس وحفظ بين الجلسات
            val dl = com.saidjte.schoolradio.desktop.data.StudioLayoutLogic.resolve(null)
            check(dl.rightTopFrac == 0.5f) { "right areas equal height default" }
            val clamped = com.saidjte.schoolradio.desktop.data.StudioLayoutLogic.resolve(
                com.saidjte.schoolradio.desktop.data.LayoutJson(
                    rightWidth = 1f, broadcastWidth = 99999f,
                    rightTopFrac = -2f, midTopFrac = 5f, bcMicFrac = 0f
                )
            )
            check(clamped.rightWidth == com.saidjte.schoolradio.desktop.data.StudioLayoutLogic.RIGHT_WIDTH_MIN &&
                    clamped.broadcastWidth == com.saidjte.schoolradio.desktop.data.StudioLayoutLogic.BC_WIDTH_MAX &&
                    clamped.rightTopFrac == com.saidjte.schoolradio.desktop.data.StudioLayoutLogic.RIGHT_TOP_MIN &&
                    clamped.midTopFrac == com.saidjte.schoolradio.desktop.data.StudioLayoutLogic.MID_TOP_MAX &&
                    clamped.bcMicFrac == com.saidjte.schoolradio.desktop.data.StudioLayoutLogic.BC_MIC_MIN) { "layout clamp" }
            val layFile = File(tmp, "selftest-layout.json")
            val stl1 = SettingsStore(layFile)
            stl1.setLayout(
                com.saidjte.schoolradio.desktop.data.StudioLayoutLogic.toJson(
                    com.saidjte.schoolradio.desktop.data.StudioLayout(500f, 400f, 0.7f, 0.85f, 0.6f)
                )
            )
            val stl2 = SettingsStore(layFile)
            val sl = stl2.layout()
            check(sl.rightWidth == 500f && sl.broadcastWidth == 400f && sl.rightTopFrac == 0.7f &&
                    sl.midTopFrac == 0.85f && sl.bcMicFrac == 0.6f) { "layout persist" }
            layFile.delete()

            // v0.21.0: شاشة القراءة بسكريبت واحد — متابعة تعرض الجارية ووقفها
            // يعطي المذيع الاختيار اليدوي أثناء الحلقة
            val ss = listOf(
                com.saidjte.schoolradio.desktop.data.ScriptSection(1, "مقدمة", "أ"),
                com.saidjte.schoolradio.desktop.data.ScriptSection(2, "خبر", "ب")
            )
            val sch = listOf(
                com.saidjte.schoolradio.desktop.data.ScheduleItem(10, "مقدمة", scriptSectionId = 1L),
                com.saidjte.schoolradio.desktop.data.ScheduleItem(11, "خبر", scriptSectionId = 2L)
            )
            check(StudioLogic.resolveReadingSection(sch, true, 1, ss, null, true)?.first?.id == 2L) { "reading follows run" }
            check(StudioLogic.resolveReadingSection(sch, true, 1, ss, 1L, false)?.first?.id == 1L) { "reading manual takeover" }
            check(StudioLogic.resolveReadingSection(sch, true, 0, ss, 999L, false)?.first?.id == 1L) { "reading stale manual falls back" }

            // v0.22.0: تطوير الواجهة — هوية مختبرة رياضيًا وموارد مدمجة
            // 1) تباين WCAG للثنائيات الحرجة: نص شارة الهواء على خلفيتها ≥ 4.5:1
            check(GuiTokens.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()) > 20.0) { "contrast black/white=21" }
            check(GuiTokens.validateCriticalContrast()) { "onAir pill contrast >= 4.5" }
            // 2) طبقات الثيم متمايزة: خلفية/سطح/سطح متغاير/حافة بطاقة
            check(GuiTokens.validateThemeLayering()) { "theme layering distinct" }
            // 3) خريطة مقاطع المقياس تحترم حدود MeterLogic (0-13 أخضر، 14-19 كهرماني، 20-23 أحمر)
            check(GuiTokens.segmentColorArgb(0, lit = true) == GuiTokens.zoneGreen) { "segment 0 green" }
            check(GuiTokens.segmentColorArgb(13, lit = true) == GuiTokens.zoneGreen) { "segment 13 green" }
            check(GuiTokens.segmentColorArgb(14, lit = true) == GuiTokens.zoneAmber) { "segment 14 amber" }
            check(GuiTokens.segmentColorArgb(20, lit = true) == GuiTokens.zoneRed) { "segment 20 red" }
            check(GuiTokens.segmentColorArgb(0, lit = false) != GuiTokens.zoneGreen) { "dim differs from lit" }
            // 4) الخط العربي المدمج: أوزان أربعة بمورد الجرة وسحرة TTF سليمة
            for (w in listOf("Regular", "Medium", "SemiBold", "Bold")) {
                val bytes = GuiTokens::class.java.classLoader.getResourceAsStream("fonts/NotoSansArabic-$w.ttf")?.readBytes()
                check(bytes != null && bytes.size > 100_000) { "font $w packaged" }
                check(bytes[0].toInt() == 0 && bytes[1].toInt() == 1 && bytes[2].toInt() == 0 && bytes[3].toInt() == 0) { "font $w ttf magic" }
            }
            // 5) أيقونة النافذة مدمجة بسحر PNG
            val iconBytes = GuiTokens::class.java.classLoader.getResourceAsStream("icon.png")?.readBytes()
            check(iconBytes != null && iconBytes.size > 1_000) { "icon packaged" }
            check((iconBytes[0].toInt() and 0xFF) == 0x89 && (iconBytes[1].toInt() and 0xFF) == 0x50) { "icon png magic" }

            println("SELFTEST OK — version $APP_VERSION")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }
}
