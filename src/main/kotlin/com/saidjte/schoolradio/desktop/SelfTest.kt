package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.LevelMeter
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.MeterLogic
import com.saidjte.schoolradio.desktop.data.MeterZone
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.ScheduleStore
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.SegmentStatus
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.ScriptStore
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScriptSource
import com.saidjte.schoolradio.desktop.data.StudioLogic
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
            check(lib.addFiles(listOf(mediaFile), "الأناشيد المدرسية").size == 1)
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
            val nBlocks = 28
            val delaySamples = (0.120 * refRate).toInt()
            val totalRef = delaySamples + blockN * nBlocks
            val refSignal = DoubleArray(totalRef) {
                0.6 * kotlin.math.sin(2.0 * Math.PI * 300.0 * it / refRate) *
                        kotlin.math.sin(2.0 * Math.PI * 0.9 * it / refRate)
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
                if (b >= 20) {
                    for (f in 0 until blockN) {
                        inEnergy += (pre[f].toDouble() / 32768.0).let { it * it }
                        outEnergy += (mic[f].toDouble() / 32768.0).let { it * it }
                    }
                }
            }
            val cancellationDb = 10.0 * kotlin.math.log10(inEnergy / maxOf(outEnergy, 1e-12))
            check(cancellationDb > 8.0) { "aec cancellation=$cancellationDb" }
            check(aec.locked) { "aec locked" }
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

            println("SELFTEST OK — version $APP_VERSION")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }
}
