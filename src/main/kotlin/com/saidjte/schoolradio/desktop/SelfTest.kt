package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.JingleWallEngine
import com.saidjte.schoolradio.desktop.audio.LevelMeter
import com.saidjte.schoolradio.desktop.audio.MixBus
import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.FakeToneVoice
import com.saidjte.schoolradio.desktop.audio.FakeTrackVoice
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.TrackReader
import com.saidjte.schoolradio.desktop.audio.WavFileWriter
import com.saidjte.schoolradio.desktop.audio.WavHeader
import com.saidjte.schoolradio.desktop.data.CrossfadeLogic
import com.saidjte.schoolradio.desktop.data.DevicesJson
import com.saidjte.schoolradio.desktop.data.MeterLogic
import com.saidjte.schoolradio.desktop.data.MeterZone
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageHistory
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageStore
import com.saidjte.schoolradio.desktop.data.ReadingJson
import com.saidjte.schoolradio.desktop.data.ScheduleMath
import com.saidjte.schoolradio.desktop.data.ScheduleStore
import com.saidjte.schoolradio.desktop.data.SeekLogic
import com.saidjte.schoolradio.desktop.data.SegmentKind
import com.saidjte.schoolradio.desktop.data.SegmentTimerLogic
import com.saidjte.schoolradio.desktop.data.SegmentStatus
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.ScriptStore
import com.saidjte.schoolradio.desktop.data.ScriptTemplateCatalog
import com.saidjte.schoolradio.desktop.data.ScriptTemplatesLogic
import com.saidjte.schoolradio.desktop.data.ScriptPdfExporter
import com.saidjte.schoolradio.desktop.data.ScheduleItem
import com.saidjte.schoolradio.desktop.data.ScriptSource
import com.saidjte.schoolradio.desktop.data.SettingsStore
import com.saidjte.schoolradio.desktop.data.SessionJson
import com.saidjte.schoolradio.desktop.data.SessionRestore
import com.saidjte.schoolradio.desktop.data.SessionStore
import com.saidjte.schoolradio.desktop.data.OnAirDisplayLogic
import com.saidjte.schoolradio.desktop.data.ScreenRect
import com.saidjte.schoolradio.desktop.data.StudioLogic
import com.saidjte.schoolradio.desktop.data.TeleprompterNames
import androidx.compose.ui.input.key.Key
import com.saidjte.schoolradio.desktop.ui.GuiTokens
import com.saidjte.schoolradio.desktop.ui.ShortcutAction
import com.saidjte.schoolradio.desktop.ui.TextEditing
import com.saidjte.schoolradio.desktop.ui.resolveShortcut
import com.saidjte.schoolradio.desktop.ui.typographyWithFamilies
import com.saidjte.schoolradio.desktop.youtube.AudioTranscoder
import com.saidjte.schoolradio.desktop.youtube.YtDlpLocator
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

            // 3ب) شريط التقدم القابل للسعي (v0.23.0): تحويلات + زحف + جدول Xing
            check(SeekLogic.msForFraction(0.5f, 5999L) == 2999L) { "msForFraction" }
            check(SeekLogic.fractionForMs(-5L, 1000L) == 0f) { "fractionForMs clamp" }
            check(SeekLogic.msForFraction(2f, 10_000L) == 10_000L) { "msForFraction clamp" }
            val scrub = SeekLogic.ScrubState()
            scrub.begin(1200L); scrub.update(3400L)
            check(scrub.end() == 3400L && scrub.end() == null) { "scrub commit-once" }
            val tocRamp = ByteArray(100) { i -> (i * 255 / 99).toByte() }
            check(
                SeekLogic.tocByteFraction(tocRamp, 0f) == 0f &&
                        SeekLogic.tocByteFraction(tocRamp, 1f) == 1f
            ) { "toc bounds" }
            // في مجلد فرعي كي لا يلتقطه ماسح التسجيلات في الفحص 4)
            val probeDir = File(tmp, "seekprobe").apply { mkdirs() }
            val xmp3 = File(probeDir, "xing.mp3")
            run {
                val header = byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte(), 0x00.toByte())
                val out = ByteArray(417 * 2)
                header.copyInto(out, 0); header.copyInto(out, 417)
                var p = 36
                "Xing".toByteArray(Charsets.US_ASCII).copyInto(out, p); p += 4
                for (b in intArrayOf(0, 0, 0, 0x07)) out[p++] = b.toByte()
                val frames = 86
                for (b in intArrayOf((frames shr 24) and 0xFF, (frames shr 16) and 0xFF, (frames shr 8) and 0xFF, frames and 0xFF)) out[p++] = b.toByte()
                for (b in intArrayOf(0, 0, 3, 66)) out[p++] = b.toByte() // 834 باية
                for (i in 0 until 100) out[p++] = (i * 255 / 99).toByte()
                xmp3.writeBytes(out)
            }
            val xinfo = Mp3Info.analyze(xmp3)
            check(xinfo != null && xinfo.isVbr && xinfo.toc != null && xinfo.toc!!.size == 100) { "xing toc parse" }
            check(xinfo!!.durationMs == 86L * 1152L * 1000L / 44100L) { "xing duration" }

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

            // v0.26.0: السرعة الخامسة «بطيئة جدًا» والتحكم الحر — خمس سرعات متصاعدة
            // وتسميات مطابقة لعقد الحفظ + عقد resolve/encode الدائري للقيمة الحرة
            val speeds = com.saidjte.schoolradio.desktop.ui.TeleprompterSpeed.entries
            check(speeds.size == 5) { "teleprompter fifth speed: ${speeds.size}" }
            check(speeds.zipWithNext().all { (a, b) -> a.pxPerSecond < b.pxPerSecond }) { "speeds ascend" }
            check(speeds.map { it.label } == TeleprompterNames.ALL) { "speed labels contract" }
            val tsp = com.saidjte.schoolradio.desktop.data.TeleprompterSpeedLogic
            check(tsp.resolve("بطيئة جدًا") == 12f) { "very slow preset" }
            check(tsp.resolve("83.5") == 83.5f) { "free speed numeric" }
            check(tsp.encode(83.5f) == "83.5") { "free speed encode" }
            check(tsp.resolve(tsp.encode(47.5f)) == 47.5f) { "speed roundtrip" }
            check(tsp.resolve("فاسد") == 60f && tsp.resolve(null) == 60f) { "speed fallback" }
            check(tsp.displayName(83.5f) == "مخصّصة" && tsp.displayName(60f) == "متوسطة") { "speed display" }

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
            val fontResources = listOf(
                "Cairo-Regular.ttf", "Cairo-Medium.ttf", "Cairo-SemiBold.ttf", "Cairo-Bold.ttf",
                "Changa-Medium.ttf", "Changa-SemiBold.ttf", "Changa-Bold.ttf", "Changa-ExtraBold.ttf",
                "Orbitron-Medium.ttf", "Orbitron-SemiBold.ttf", "Orbitron-Bold.ttf", "Orbitron-ExtraBold.ttf",
                "SpicySale-Regular.otf"
            )
            for (f in fontResources) {
                val bytes = GuiTokens::class.java.classLoader.getResourceAsStream("fonts/$f")?.readBytes()
                check(bytes != null && bytes.size > 15_000) { "font $f packaged" }
                val ttfMagic = bytes[0].toInt() == 0 && bytes[1].toInt() == 1 && bytes[2].toInt() == 0 && bytes[3].toInt() == 0
                val ottoMagic = bytes[0].toInt() == 0x4F && bytes[1].toInt() == 0x54 && bytes[2].toInt() == 0x54 && bytes[3].toInt() == 0x4F
                check(ttfMagic || ottoMagic) { "font $f sfnt magic" }
            }
            // 4-ب) إسناد العائلات الطباعي: العناوين للعائلة العنوانية والمتون للأساس — بمنادِلين حارسين بلا موارد رسومية
            run {
                val mono = androidx.compose.ui.text.font.FontFamily.Monospace
                val serif = androidx.compose.ui.text.font.FontFamily.Serif
                val mapped = typographyWithFamilies(androidx.compose.material3.Typography(), mono, serif)
                check(mapped.displaySmall.fontFamily == serif) { "display uses heading family" }
                check(mapped.headlineSmall.fontFamily == serif) { "headline uses heading family" }
                check(mapped.titleLarge.fontFamily == serif) { "title uses heading family" }
                check(mapped.bodyMedium.fontFamily == mono) { "body uses body family" }
                check(mapped.labelSmall.fontFamily == mono) { "label uses body family" }
            }
            // 5) أيقونة النافذة مدمجة بسحر PNG
            val iconBytes = GuiTokens::class.java.classLoader.getResourceAsStream("icon.png")?.readBytes()
            check(iconBytes != null && iconBytes.size > 1_000) { "icon packaged" }
            check((iconBytes[0].toInt() and 0xFF) == 0x89 && (iconBytes[1].toInt() and 0xFF) == 0x50) { "icon png magic" }

            // 6) استيراد اليوتيوب (v0.25.0): أداة التحميل مدمجة لمنصة التشغيل
            //    والمحوّل M4A→MP3 يعمل من موارد الجرة نفسها (نغمة 3 ثوانٍ صناعية)
            run {
                val res = YtDlpLocator.bundledResourceName()
                val bin = GuiTokens::class.java.classLoader.getResourceAsStream(res)?.readBytes()
                check(bin != null && bin.size > 5_000_000) { "yt-dlp bundled ($res): ${bin?.size ?: -1}" }
                val m4aBytes = GuiTokens::class.java.classLoader.getResourceAsStream("selftest/test_tone.m4a")?.readBytes()
                check(m4aBytes != null && m4aBytes.size > 10_000) { "selftest m4a packaged" }
                val inp = File(tmp, "tone.m4a")
                inp.writeBytes(m4aBytes!!)
                val outMp3 = File(tmp, "tone.mp3")
                AudioTranscoder.m4aToMp3(inp, outMp3, durationSec = 3)
                check(outMp3.length() > 10_000) { "transcoded size=${outMp3.length()}" }
                val mi = Mp3Info.analyze(outMp3)
                check(mi != null && mi.durationMs in 2_700..3_400) { "transcoded duration=${mi?.durationMs}" }
            }

            // v0.27.0: عقد شريط التقدم القابل للسعي — العرض الفوري للموضع الجديد
            //     عند النقر/الإفلات وثباته أثناء الإيقاف المؤقت (كان يرتد للقديم)
            run {
                class SeekProbe : TrackReader {
                    val seekCalls = mutableListOf<Long>()
                    @Volatile var current = 0L
                    override val durationMs = 600_000L
                    override val currentMs get() = current
                    override fun seek(ms: Long) { seekCalls += ms; current = ms }
                    override fun pause() { }
                    override fun resume() { }
                    override fun applyVolume(volume: Float) { }
                    override fun readChunk(): Boolean { current += 250L; Thread.sleep(2); return true }
                    override fun close() { }
                }
                fun awaitS(cond: () -> Boolean, msg: String) {
                    val dl = System.currentTimeMillis() + 5_000
                    while (System.currentTimeMillis() < dl) { if (cond()) return; Thread.sleep(10) }
                    check(false) { msg }
                }
                val probe = SeekProbe()
                val eng = MusicPlayerEngine()
                eng.trackReaderOverride = { probe }
                try {
                    // بلا مقطع: السعي لا يلمس العرض إطلاقًا
                    check(eng.positionMs.value == 0L) { "idle position" }
                    eng.seek(30_000)
                    check(eng.positionMs.value == 0L) { "seek without track is no-op" }
                    // تشغيل بقارئ مزيف ثم إيقاف مؤقت والسعي — يجب أن يثبت العرض عند الهدف
                    eng.play(File("selftest-seek/probe.mp3"))
                    awaitS({ eng.hasTrack.value }, "hasTrack")
                    awaitS({ eng.positionMs.value > 0L }, "ticking")
                    eng.pause()
                    awaitS({ eng.isPaused.value }, "paused")
                    Thread.sleep(120)
                    eng.seek(45_000)
                    check(eng.positionMs.value == 45_000L) { "seek display immediate=${eng.positionMs.value}" }
                    repeat(10) { Thread.sleep(20); check(eng.positionMs.value == 45_000L) { "seek display stable@$it=${eng.positionMs.value}" } }
                    awaitS({ probe.seekCalls.contains(45_000L) }, "worker executed paused seek")
                } finally {
                    eng.trackReaderOverride = null
                    eng.stop()
                    eng.awaitIdle()
                }
            }

            // v0.28.0: عقد جدار الجينغل — السبعة المدمجة من موارد الجرة،
            //     منطق المواضع نقية مع عقد دائري، والمحرك يعمل بصوت مزيف بلا عتاد
            run {
                // 1) الموارد مدمجة بالجرة واستخراجها idempotent يعمل
                check(com.saidjte.schoolradio.desktop.audio.BundledJingles.ALL.size == 7) { "bundled count" }
                check(com.saidjte.schoolradio.desktop.audio.BundledJingles.ALL.map { it.id }.toSet().size == 7) { "bundled unique ids" }
                val titles = com.saidjte.schoolradio.desktop.audio.BundledJingles.ALL.map { it.title }
                check(titles.toSet().size == 7) { "bundled unique titles" }
                for (j in com.saidjte.schoolradio.desktop.audio.BundledJingles.ALL) {
                    val b = GuiTokens::class.java.classLoader
                        .getResourceAsStream(com.saidjte.schoolradio.desktop.audio.BundledJingles.resourcePath(j))?.readBytes()
                    check(b != null && b.size > 10_000) { "jingle packaged: ${j.fileName} ${b?.size ?: -1}" }
                }
                val jdir = File(tmp, "jingles-extract")
                val extracted = com.saidjte.schoolradio.desktop.audio.BundledJingles.ensureExtracted(jdir)
                check(extracted.size == 7 && extracted.all { it.isFile && it.length() > 10_000 }) { "jingles extracted" }
                val sizes1 = extracted.map { it.length() }
                com.saidjte.schoolradio.desktop.audio.BundledJingles.ensureExtracted(jdir)
                check(extracted.map { it.length() } == sizes1) { "extraction idempotent" }

                // 2) المنطق النقي: الافتراضي والدمج والعقد الدائري والحبس
                val logic = com.saidjte.schoolradio.desktop.data.JingleWallLogic
                check(logic.PAD_COUNT == 12) { "pad count" }
                val defaults = logic.bundledDefaults(extracted, titles)
                check(defaults.size == 12) { "defaults size" }
                check(defaults.take(7).all { !it.isEmpty && File(it.path!!).isFile }) { "first 7 assigned" }
                check(defaults.drop(7).all { it.isEmpty }) { "rest empty" }
                check(defaults.map { it.index } == (0..11).toList()) { "indices ordered" }
                // الدمج: فهرس فاسد يسقط، تكرار يرجّح الأخير، مسار فارغ يسقط، والبقية تظل افتراضيًا
                val merged = logic.resolve(
                    listOf(
                        com.saidjte.schoolradio.desktop.data.JinglePadJson(index = 3, path = "/x/a.mp3", title = "خ"),
                        com.saidjte.schoolradio.desktop.data.JinglePadJson(index = 99, path = "/bad.mp3"),
                        com.saidjte.schoolradio.desktop.data.JinglePadJson(index = 5, path = "  "),
                        com.saidjte.schoolradio.desktop.data.JinglePadJson(index = 3, path = "/x/b.wav", title = "ز")
                    ),
                    defaults
                )
                check(merged[3].path == "/x/b.wav" && merged[3].title == "ز") { "dup last wins" }
                check(merged[5] == defaults[5]) { "blank path keeps default" }
                check(merged.size == 12) { "merge size" }
                // العقد الدائري: encode ثم resolve يعيد المسارات نفسها
                val rt = logic.resolve(logic.encodePads(defaults), logic.emptyPads())
                check(rt == defaults) { "round trip" }
                check(logic.clampVolume(5f) == 1f && logic.clampVolume(-1f) == 0f) { "clamp volume" }

                // 3) المخزن: حفظ واسترجاع دائري في settings.json
                val sfile = File(tmp, "settings-jingle.json")
                val s1 = SettingsStore(sfile)
                s1.setJingleWall(0.55f, logic.encodePads(defaults))
                val s2 = SettingsStore(sfile)
                check(kotlin.math.abs(s2.jingleVolume() - 0.55f) < 1e-4f) { "jingle volume persisted" }
                check(s2.jinglePads() == logic.encodePads(defaults)) { "jingle pads persisted" }

                // 4) المحرك بصوت مزيف: تبديل/تزامن/إيقاف شامل/مستوى حي/خطأ ملف مفقود
                fun awaitJ(cond: () -> Boolean, msg: String) {
                    val dl = System.currentTimeMillis() + 5_000
                    while (System.currentTimeMillis() < dl) { if (cond()) return; Thread.sleep(10) }
                    check(false) { msg }
                }
                val eng = JingleWallEngine()
                val probes = mutableListOf<com.saidjte.schoolradio.desktop.audio.FakeToneVoice>()
                eng.voiceFactoryOverride = {
                    com.saidjte.schoolradio.desktop.audio.FakeToneVoice(chunks = 600, chunkMs = 5).also { probes += it }
                }
                try {
                    eng.toggle(0, File("selftest-jingle/a.mp3"))
                    awaitJ({ 0 in eng.playing.value }, "playing after toggle")
                    // نقرة ثانية توقف الجاري فورًا
                    eng.toggle(0, File("selftest-jingle/a.mp3"))
                    awaitJ({ 0 !in eng.playing.value }, "second click stops")
                    // ثلاثة مواضع متزامنة
                    eng.toggle(1, File("selftest-jingle/b.mp3"))
                    eng.toggle(2, File("selftest-jingle/c.wav"))
                    eng.toggle(3, File("selftest-jingle/d.ogg"))
                    awaitJ({ eng.playing.value == setOf(1, 2, 3) }, "three concurrent: ${eng.playing.value}")
                    // المستوى الحي: تغييره أثناء التشغيل يصل للأصوات
                    eng.setVolume(0.42f)
                    awaitJ({ probes.filter { it.remaining in 1..599 }.all { it.volumes.contains(0.42f) } }, "live volume applied")
                    // ملف مفقود: خطأ على الموضع بلا انهيار — بوضع الإنتاج (بلا حقن)
                    // لأن الحقن يقفز فوق حارس الملفات عمدًا كما في عقد مشغل الموسيقى
                    eng.voiceFactoryOverride = null
                    eng.toggle(4, File("selftest-jingle/missing.mp3"))
                    awaitJ({ eng.errors.value[4]?.contains("غير موجود") == true }, "missing file error")
                    eng.voiceFactoryOverride = {
                        // صوت قصير (6 كتل ≈ 60ms) ليكمل الإنهاء الطبيعي سريعًا
                        com.saidjte.schoolradio.desktop.audio.FakeToneVoice(chunks = 6, chunkMs = 5)
                    }
                    // الإنهاء الطبيعي للصوت القصير يزيله من الجاري
                    eng.toggle(5, File("selftest-jingle/short.mp3"))
                    awaitJ({ 5 !in eng.playing.value }, "natural finish removes slot")
                    // إيقاف شامل
                    eng.stopAll()
                    awaitJ({ eng.playing.value.isEmpty() }, "stop all")
                    eng.awaitIdle()
                } finally {
                    eng.voiceFactoryOverride = null
                    eng.shutdown()
                }
            }

            // v0.29.0: اختصارات لوحة المفاتيح — الحلّ النقي وحارس الكتابة
            run {
                fun r(k: Key, ctrl: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
                    resolveShortcut(k, ctrl, alt, meta)
                check(r(Key.Spacebar) == ShortcutAction.PlayPauseMusic) { "space action" }
                check(r(Key.One) == ShortcutAction.FireJingle(1)) { "slot 1" }
                check(r(Key.Nine) == ShortcutAction.FireJingle(9)) { "slot 9" }
                check(r(Key.DirectionLeft) == ShortcutAction.SeekBack) { "seek back" }
                check(r(Key.DirectionRight) == ShortcutAction.SeekForward) { "seek fwd" }
                check(r(Key.N) == ShortcutAction.NextTrack) { "next" }
                check(r(Key.P) == ShortcutAction.PreviousTrack) { "prev" }
                check(r(Key.M) == ShortcutAction.ToggleMute) { "mute" }
                check(r(Key.Spacebar, ctrl = true) == null) { "ctrl passes through" }
                check(r(Key.M, alt = true) == null) { "alt passes through" }
                check(r(Key.A) == null) { "unbound key silent" }
                // حارس الكتابة: عدّاد ذري صامد أمام ترتيب أحداث البؤرة المتقلب
                val te = TextEditing
                te.reset()
                check(!te.active) { "editing idle" }
                te.enter(); te.enter(); te.exit()
                check(te.active) { "guard holds while one field focused" }
                te.exit()
                check(!te.active) { "guard released" }
                te.exit()
                check(!te.active) { "counter clamped at zero" }
                te.reset()
            }

            // v0.29.0: التلاشي الناعم — غلاف ساعة الحائط وعقد الإيقاف/الاستئناف
            run {
                val eng = MusicPlayerEngine()
                class AudioProbe : TrackReader {
                    @Volatile var current = 0L
                    @Volatile var pauseCalls = 0
                    @Volatile var resumeCalls = 0
                    override val durationMs = 600_000L
                    override val currentMs get() = current
                    override fun seek(ms: Long) { current = ms.coerceIn(0L, durationMs) }
                    override fun pause() { pauseCalls++ }
                    override fun resume() { resumeCalls++ }
                    override fun applyVolume(volume: Float) { }
                    override fun readChunk(): Boolean {
                        val n = 2048
                        val s = ShortArray(n)
                        for (i in 0 until n) {
                            s[i] = (sin(2.0 * PI * 440.0 * (current * 24L + i) / 24_000.0) * 12000).toInt().toShort()
                        }
                        eng.channel.processShorts(s, n)
                        current += 21L
                        Thread.sleep(1)
                        return true
                    }
                    override fun close() { }
                }
                fun awaitF(cond: () -> Boolean, msg: String) {
                    val dl = System.currentTimeMillis() + 5_000
                    while (System.currentTimeMillis() < dl) { if (cond()) return; Thread.sleep(10) }
                    check(false) { msg }
                }
                // 1) رياضيات الغلاف على القناة مباشرة
                val ch = eng.channel
                check(ch.currentEnv() == 1f) { "env idle full" }
                ch.beginFade(0f, 100)
                Thread.sleep(40)
                val mid = ch.currentEnv()
                check(mid in 0.01f..0.95f) { "fade mid=$mid" }
                Thread.sleep(120)
                check(ch.currentEnv() == 0f) { "fade reaches silence" }
                ch.beginFade(1f, 80)
                Thread.sleep(140)
                check(ch.currentEnv() == 1f) { "fade back full" }
                ch.beginFade(1f, 0)
                check(ch.currentEnv() == 1f) { "instant fade" }
                ch.resetEnv()
                check(ch.currentEnv() == 1f) { "reset env" }
                // 2) عقد المحرك: الإيقاف ينتظر الخروج الناعم ثم يجمد، والاستئناف يصعد ناعمًا
                val probe = AudioProbe()
                eng.trackReaderOverride = { probe }
                try {
                    eng.play(File("selftest-fade/probe.wav"))
                    awaitF({ eng.hasTrack.value && eng.level.value > 0.03f }, "audible playing")
                    val t0 = System.currentTimeMillis()
                    eng.pause()
                    awaitF({ probe.pauseCalls == 1 }, "worker paused after fade")
                    val elapsed = System.currentTimeMillis() - t0
                    check(elapsed >= 250L) { "pause waits for fade: $elapsed" }
                    check(eng.channel.currentEnv() == 0f) { "env silent after pause" }
                    Thread.sleep(120)
                    val frozen = eng.positionMs.value
                    Thread.sleep(120)
                    check(eng.positionMs.value == frozen) { "frozen after fade" }
                    eng.resume()
                    awaitF({ probe.resumeCalls == 1 }, "resumed")
                    awaitF({ eng.level.value > 0.03f }, "audible after fade-in")
                    Thread.sleep(400)
                    check(eng.channel.currentEnv() == 1f) { "env full after fade-in" }
                } finally {
                    eng.trackReaderOverride = null
                    eng.stop()
                    eng.awaitIdle()
                }
            }

            // v0.30.0: قوالب السكريبت — كتالوج سليم ومسودات مرتبة ومخزن دفعة واحدة
            run {
                check(ScriptTemplateCatalog.all.size >= 6) { "catalog size=${ScriptTemplateCatalog.all.size}" }
                val errs = ScriptTemplatesLogic.catalogErrors()
                check(errs.isEmpty()) { "catalog errors=$errs" }
                check(ScriptTemplateCatalog.all.map { it.id }.toSet().size == ScriptTemplateCatalog.all.size) { "dup ids" }
                val t = ScriptTemplateCatalog.byId("morning-bulletin")!!
                val drafts = ScriptTemplatesLogic.drafts(t)
                check(drafts.size == t.sections.size && drafts.all { it.first.isNotBlank() && it.second.isNotBlank() })
                check(t.estimatedSeconds() > 0 && t.totalWords() > 0) { "template stats" }
                check(ScriptTemplatesLogic.pdfUnsafeChars().isEmpty()) { "unsafe pdf chars" }

                val store = ScriptStore(File(tmp, "st-script.json"))
                val batch = store.addAll(drafts.take(3))
                check(store.sections().size == 3 && batch.map { it.id } == listOf(1L, 2L, 3L)) { "addAll ids" }
                store.clear()
                check(store.sections().isEmpty()) { "clear" }
                val again = store.addAll(drafts.take(2))
                check(again.first().id == 4L) { "nextId monotonic after clear: ${again.first().id}" }
                check(store.addAll(emptyList()).isEmpty() && store.sections().size == 2) { "addAll empty" }

                // v0.30.0: تصدير PDF — وثيقة صالحة متعددة الصفحات تُقرأ وتُرسم
                val pdfFile = File(tmp, "st-script.pdf")
                val res = ScriptPdfExporter.export(store.sections(), "مدرسة الفحص الذاتي", java.time.LocalDate.of(2026, 11, 6), pdfFile)
                check(res.pages >= 1 && res.bytes > 10_000) { "pdf result pages=${res.pages} bytes=${res.bytes}" }
                check(res.words == store.sections().sumOf { ScriptStats.wordCount(it.body) }) { "pdf words" }
                org.apache.pdfbox.pdmodel.PDDocument.load(pdfFile).use { pd ->
                    check(pd.numberOfPages == res.pages) { "pdf pages=${pd.numberOfPages}" }
                    // رسم صفحة كاملة يتطلب تحليل كل الأشكال المتجهة — أي عطل تشكيل يقع هنا
                    org.apache.pdfbox.rendering.PDFRenderer(pd).renderImage(0)
                }
                val suggested = ScriptPdfExporter.suggestedFileName(java.time.LocalDate.of(2026, 11, 6))
                check(suggested.startsWith("سكريبت المذيع ") && suggested.endsWith("2026-11-06.pdf")) { "suggested=$suggested" }
                // سكريبت فارغ: صفحة واحدة برسالة مهذبة لا انهيار
                val emptyPdf = File(tmp, "st-empty.pdf")
                val emptyRes = ScriptPdfExporter.export(emptyList(), null, java.time.LocalDate.of(2026, 11, 6), emptyPdf)
                check(emptyRes.pages == 1 && emptyRes.bytes > 3_000) { "empty pdf pages=${emptyRes.pages}" }
                // سكريبت طويل: انسياب صفحات حقيقي (12 فقرة نصية)
                val longSections = (1..12).map { k ->
                    ScriptSection(k.toLong(), "فقرة اختبار الطول $k",
                        "نص تجريبي طويل للتحقق من لف الأسطر العربية وعبور حدود الصفحة. ".repeat(14))
                }
                val longPdf = File(tmp, "st-long.pdf")
                val longRes = ScriptPdfExporter.export(longSections, null, java.time.LocalDate.of(2026, 11, 6), longPdf)
                check(longRes.pages >= 2) { "long pdf pages=${longRes.pages}" }
                org.apache.pdfbox.pdmodel.PDDocument.load(longPdf).use { pd ->
                    org.apache.pdfbox.rendering.PDFRenderer(pd).renderImage(pd.numberOfPages - 1)
                }
            }

            // ================= v0.31.0: الكروس فيد وتسجيل المزيج والمؤقت الآلي =================
            run {
                // 1) عقد CrossfadeLogic النقي
                check(CrossfadeLogic.clampMs(0L) == 0L) { "cf disabled preserved" }
                check(CrossfadeLogic.clampMs(100L) == CrossfadeLogic.MIN_MS) { "cf clamp low" }
                check(CrossfadeLogic.clampMs(99_000L) == CrossfadeLogic.MAX_MS) { "cf clamp high" }
                check(CrossfadeLogic.clampMs(3_000L) == 3_000L) { "cf pass-through" }
                check(!CrossfadeLogic.shouldTrigger(4_000L, 3_000L)) { "no trigger beyond window" }
                check(CrossfadeLogic.shouldTrigger(3_000L, 3_000L)) { "trigger at window edge" }
                check(!CrossfadeLogic.shouldTrigger(0L, 3_000L)) { "no trigger at exact end" }
                check(!CrossfadeLogic.shouldTrigger(2_000L, 0L)) { "disabled never triggers" }
                check(CrossfadeLogic.fadeOutGain(0L, 3_000L) == 1f) { "out gain start" }
                check(CrossfadeLogic.fadeOutGain(1_500L, 3_000L) in 0.49f..0.51f) { "out gain mid" }
                check(CrossfadeLogic.fadeOutGain(4_000L, 3_000L) == 0f) { "out gain end" }
                check(CrossfadeLogic.fadeInGain(0L, 3_000L) == 0f) { "in gain start" }
                check(CrossfadeLogic.fadeInGain(1_500L, 3_000L) in 0.49f..0.51f) { "in gain mid" }
                check(CrossfadeLogic.fadeInGain(4_000L, 3_000L) == 1f) { "in gain end" }

                // 2) كروس فيد ذاتي في المحرك: مقطع قصير ينتهي والقائمة تمدّده بلا قطع
                run {
                    class XfProbe(val tag: String, val totalMs: Long) : TrackReader {
                        @Volatile var current = 0L
                        @Volatile var closed = false
                        override val durationMs = totalMs
                        override val currentMs get() = current
                        override fun seek(ms: Long) { current = ms.coerceIn(0L, totalMs) }
                        override fun pause() { }
                        override fun resume() { }
                        override fun applyVolume(volume: Float) { }
                        override fun readChunk(): Boolean {
                            current += 50L
                            Thread.sleep(2)
                            return current < totalMs
                        }
                        override fun close() { closed = true }
                    }
                    fun awaitX(cond: () -> Boolean, msg: String) {
                        val dl = System.currentTimeMillis() + 6_000
                        while (System.currentTimeMillis() < dl) { if (cond()) return; Thread.sleep(10) }
                        check(false) { msg }
                    }
                    val a = XfProbe("a", 2_500L) // ينفد بعد ~50 كتلة (~100ms حقيقية)
                    val b = XfProbe("b", 60_000L)
                    val queue = ArrayDeque(listOf(a, b))
                    val eng = MusicPlayerEngine()
                    eng.trackReaderOverride = { queue.removeFirst() }
                    eng.crossfadeMs = 400L
                    var nextAsked = 0
                    var committedTo: String? = null
                    eng.onCrossfadeNext = { nextAsked++; java.io.File("selftest-xf/b.wav") }
                    eng.onCrossfadeCommitted = { committedTo = it.path }
                    try {
                        eng.play(java.io.File("selftest-xf/a.wav"))
                        awaitX({ eng.hasTrack.value }, "xf track loaded")
                        awaitX({ eng.xfading.value }, "xfading started") // التمديد الآلي بدأ
                        check(nextAsked == 1) { "next asked once, got $nextAsked" }
                        check(committedTo == "selftest-xf/b.wav") { "committed file=$committedTo" }
                        awaitX({ !eng.xfading.value }, "xfading completed")
                        check(a.closed) { "outgoing reader closed" }
                        check(eng.hasTrack.value && eng.isPlaying.value) { "still playing after crossfade" }
                        check(!b.closed) { "incoming alive" }
                    } finally {
                        eng.trackReaderOverride = null
                        eng.stop()
                        eng.awaitIdle()
                    }
                }

                // 3) MixBus: مصدران بصيغتين مختلفتين في ملف WAV واحد 44.1k ستيريو
                run {
                    val f = File(tmp, "mix.wav")
                    check(MixBus.start(f)) { "mix start" }
                    check(MixBus.recording.value) { "mix active" }
                    check(!MixBus.start(File(tmp, "mix2.wav"))) { "double start rejected" }
                    // موسيقى ستيريو 48kHz — 100 إطار يُعاد تشكيلها إلى 44.1k
                    val music = ShortArray(200) { i -> (((i % 40) - 20) * 900).toInt().toShort() }
                    MixBus.feedMusic(music, music.size, 48_000, 2)
                    // مايك أحادي 44.1kHz عبر بايتات
                    val micBytes = ByteArray(4410) { 0 } // 2205 عينة صمت أحادية
                    MixBus.feedMic(micBytes, 0, micBytes.size, 44_100, 1)
                    // جينل بمستوى برمحي عبر بايتات بإزاحة
                    val jinBytes = ByteArray(100) { i -> if (i % 2 == 0) 0x10 else 0x00 }
                    MixBus.feedJingle(jinBytes, 0, jinBytes.size, 44_100, 2)
                    // تغذية فارغة لا تكسر ولا تُضيف
                    MixBus.feedMic(ByteArray(0), 0, 0, 44_100, 1)
                    val dur = MixBus.stop()
                    check(dur > 0L) { "mix duration=$dur" }
                    val info = WavHeader.read(f) ?: error("mix wav header missing")
                    check(info.sampleRate == 44_100) { "mix rate=${info.sampleRate}" }
                    check(info.channels == 2) { "mix channels=${info.channels}" }
                    check(info.dataBytes > 0L) { "mix dataBytes=${info.dataBytes}" }
                    check(!MixBus.recording.value) { "mix stopped" }
                    check(MixBus.stop() == 0L) { "double stop safe" }
                    check(f.length() == 44L + info.dataBytes) { "mix file size ${f.length()}" }
                }

                // 4) SegmentTimerLogic: قرار حتمي — لا إطلاق بلا زمن مخطط
                run {
                    val d0 = SegmentTimerLogic.decide(0L, 60L, 30L)
                    check(!d0.fire && !d0.warn && d0.remainingMs == 60_000L) { "timer start" }
                    val dW = SegmentTimerLogic.decide(45_000L, 60L, 30L)
                    check(!dW.fire && dW.warn) { "timer warn window" }
                    val dOut = SegmentTimerLogic.decide(20_000L, 60L, 30L)
                    check(!dOut.fire && !dOut.warn) { "timer calm" }
                    val dF = SegmentTimerLogic.decide(60_000L, 60L, 30L)
                    check(dF.fire) { "timer fires at threshold" }
                    val dNone = SegmentTimerLogic.decide(60_000L, 0L, 30L)
                    check(!dNone.fire && !dNone.warn && dNone.remainingMs == 0L) { "unplanned never fires" }
                    check(SegmentTimerLogic.clampWarnSeconds(1) == 5) { "warn clamp low" }
                    check(SegmentTimerLogic.clampWarnSeconds(500) == 120) { "warn clamp high" }
                    check(
                        SegmentTimerLogic.remainingLabel(SegmentTimerLogic.decide(90_000L, 60L, 30L)).startsWith("تجاوز")
                    ) { "overdue label" }
                }

                // 5) جينل الفاصل الآلي: موضع خارج الشبكة يُطلق ويُوقف مع الكل
                run {
                    val eng = JingleWallEngine()
                    eng.voiceFactoryOverride = { FakeToneVoice(chunks = 30, chunkMs = 10) }
                    try {
                        eng.sting(File("selftest-sting/none.mp3"))
                        check(eng.playing.value.contains(JingleWallEngine.STING_SLOT)) { "sting playing" }
                        check(!eng.playing.value.any { it < 12 }) { "sting outside grid" }
                        eng.stopAll()
                        check(eng.playing.value.isEmpty()) { "sting stopped with all" }
                    } finally {
                        eng.voiceFactoryOverride = null
                        eng.shutdown()
                    }
                }
            }

            // ================= v0.32.0: استعادة الجلسة الأخيرة ووضع «على الهواء» =================
            run {
                // 1) SessionRestore: التعقيم والقرار
                val r1 = SessionRestore.resolve(
                    SessionJson(trackPath = "/pl/b.mp3", positionMs = 45_000L, selectedSectionId = 7L),
                    listOf("/pl/a.mp3", "/pl/b.mp3"), setOf(7L, 8L)
                )
                check(r1 != null && r1.playlistIndex == 1 && r1.positionMs == 45_000L) { "restore playlist match" }
                check(SessionRestore.clampPosition(-1000L, 30_000L) == 0L) { "clamp negative" }
                check(SessionRestore.clampPosition(99_000L, 30_000L) == 30_000L) { "clamp over-duration" }
                check(
                    SessionRestore.resolve(SessionJson(trackPath = "/nowhere/x.mp3"), listOf("/pl/a"), emptySet()) == null
                ) { "missing track and no section → nothing to restore" }
                val r2 = SessionRestore.resolve(
                    SessionJson(trackPath = "/nowhere/x.mp3", selectedSectionId = 3L),
                    listOf("/pl/a"), setOf(3L)
                )
                check(r2 != null && r2.playlistIndex == -1 && r2.selectedSectionId == 3L) { "script-only restore" }
                val r3 = SessionRestore.resolve(
                    SessionJson(trackPath = " /pl/a.mp3 ", selectedSectionId = 99L),
                    listOf("/pl/a.mp3"), setOf(1L)
                )
                check(r3 != null && r3.playlistIndex == 0 && r3.selectedSectionId == null) {
                    "deleted section dropped, trimmed path matched"
                }

                // 2) SessionStore: دورة حفظية على القرص
                run {
                    val f = File(tmp, "session.json")
                    val st = SessionStore(f)
                    st.save(SessionJson(trackPath = "/pl/a.mp3", positionMs = 77_000L, savedAt = 1L))
                    check(SessionStore(f).session().positionMs == 77_000L) { "session round trip" }
                    f.writeText("{broken")
                    check(SessionStore(f).session().trackPath == null) { "broken session tolerated" }
                }

                // 3) المحرك: فتح موقوفًا عند الموضع المحفوظ ثم الاستئناف يكمل
                // (مقطع طويل كي لا ينفد أثناء فحص التقدم — سرعة القارئ الوهمي عالية)
                run {
                    val eng = MusicPlayerEngine()
                    eng.trackReaderOverride = { FakeTrackVoice(totalMs = 120_000L, chunkSleepMs = 2L) }
                    try {
                        val f = File(tmp, "st-restore.mp3")
                        f.writeBytes(ByteArray(0))
                        check(eng.preparePaused(f, 5_000L)) { "prepare accepts" }
                        var tries = 0
                        while (!eng.hasTrack.value && tries < 100) { Thread.sleep(20); tries++ }
                        check(eng.hasTrack.value && eng.isPaused.value && eng.isPlaying.value) { "restored paused (isPlaying kept)" }
                        tries = 0
                        while (eng.positionMs.value != 5_000L && tries < 100) { Thread.sleep(20); tries++ }
                        check(eng.positionMs.value == 5_000L) { "restored position=${eng.positionMs.value}" }
                        eng.resume()
                        Thread.sleep(500)
                        check(eng.isPlaying.value && eng.positionMs.value > 5_000L) { "resume continues" }
                        check(!eng.preparePaused(File(tmp, "missing.mp3"), 1_000L)) { "missing rejected" }
                    } finally {
                        eng.trackReaderOverride = null
                        eng.stop()
                        eng.awaitIdle()
                    }
                }

                // 4) الشاشة الثانية: الاختيار النقي
                run {
                    val a = ScreenRect(0, 0, 1920, 1080)
                    val b = ScreenRect(1920, 0, 1280, 1024)
                    check(OnAirDisplayLogic.pickSecondary(listOf(a, b), 0) == b) { "secondary picked" }
                    check(OnAirDisplayLogic.pickSecondary(listOf(a, b), 1) == a) { "default second → first" }
                    check(OnAirDisplayLogic.pickSecondary(listOf(a), 0) == null) { "single screen null" }
                }

                // 5) نموذج شاشة الطلاب: الفقرة والمتبقي والتالية والموسيقى
                run {
                    val m = OnAirDisplayLogic.buildModel(
                        runActive = true, runIndex = 0, runElapsedMs = 15_000L,
                        remainingMs = 5_000L, warn = true,
                        scheduleTitles = listOf("افتتاح الحلقة", "نشرة الأخبار"),
                        schedulePlannedSeconds = listOf(20L, 60L),
                        musicTitle = "نشيد", musicPositionMs = 10_000L, musicDurationMs = 100_000L,
                        musicPlaying = true, micLive = true, recording = false
                    )
                    check(m.onAir && m.headline == "افتتاح الحلقة") { "model run item" }
                    check(m.timeText == "00:15 من 00:20") { "model time text=${m.timeText}" }
                    check(m.remainingText == "المتبقي 00:05" && m.warn && !m.fired) { "model remaining" }
                    check(m.nextTitle == "نشرة الأخبار") { "model next" }
                    check(m.musicTitle == "نشيد") { "model music strip" }
                    check(OnAirDisplayLogic.mmss(75_000L) == "01:15") { "mmss" }
                }
            }

            // ================= v0.33.0: صفحة المونتاج =================
            run {
                // 1) المنطق النقي: المدد والفواصل ونوافذ القص والتلاشين والتعقيم
                val ca = MontageClip(id = "a", path = "/x/a.wav", title = "a", durationMs = 4000, startMs = 1000, endMs = 3000)
                val cb = MontageClip(id = "b", path = "/x/b.wav", title = "b", durationMs = 6000, startMs = 0, endMs = 2000)
                check(MontageLogic.clipDurationMs(ca) == 2000L) { "clip duration" }
                check(MontageLogic.totalDurationMs(emptyList(), 500) == 0L) { "empty total" }
                check(MontageLogic.totalDurationMs(listOf(ca), 500) == 2000L) { "single total" }
                check(MontageLogic.totalDurationMs(listOf(ca, cb), 500) == 4500L) { "two+gap total" }
                check(MontageLogic.gapAfter(0, 2, 500) == 500L && MontageLogic.gapAfter(1, 2, 500) == 0L) { "gapAfter" }
                check(MontageLogic.clampWindow(0, 0, 4000) == (0L to 4000L)) { "empty window → full" }
                check(MontageLogic.clampWindow(-5, 99_999, 4000) == (0L to 4000L)) { "window clamp" }
                check(MontageLogic.clampWindow(1000, 2000, 4000) == (1000L to 2000L)) { "window intact" }
                check(MontageLogic.fadeGainAt(0, 2000, 500, 500) == 0f) { "fade at start" }
                check(Math.abs(MontageLogic.fadeGainAt(250, 2000, 500, 500) - 0.5f) < 0.01f) { "fade mid ramp" }
                check(MontageLogic.fadeGainAt(1000, 2000, 500, 500) == 1f) { "fade plateau" }
                check(Math.abs(MontageLogic.fadeGainAt(1750, 2000, 500, 500) - 0.5f) < 0.01f) { "fade out ramp" }
                check(MontageLogic.fadeGainAt(1000, 2000, 0, 0) == 1f) { "no fades" }
                check(MontageLogic.fadeGainAt(0, 0, 500, 500) == 0f) { "zero window" }
                val san = MontageLogic.sanitize(
                    listOf(
                        ca.copy(path = "missing.wav"),
                        cb.copy(path = File(tmp, "sanitize-real.wav").apply { writeBytes(ByteArray(10)) }.path,
                                volumePercent = 9999, fadeInMs = -10, fadeOutMs = 99_999)
                    )
                )
                check(san.size == 1 && san[0].volumePercent == 200 && san[0].fadeInMs == 0L && san[0].fadeOutMs == 10_000L) { "sanitize" }

                // 2) المخزن: دورة حفظ/استرجاع ذرّية
                run {
                    val msf = File(tmp, "montage.json")
                    MontageStore(msf).save(MontageProject(clips = listOf(ca), gapMs = 750, title = "ت"))
                    val back = MontageStore(msf).project()
                    check(back.gapMs == 750L && back.clips.first().id == "a") { "montage store roundtrip" }
                }

                // 3) التصيير: قص وتلاشٍ وفاصل من مصادر مختلفة المعدل والقنوات إلى WAV واحد 44.1k ستيريو
                run {
                    val mdir = File(tmp, "montage")
                    val demo = MontageDemo.make(mdir)
                    val proj = MontageProject(
                        clips = listOf(
                            MontageClip(id = "a", path = demo[0].path, title = "a", durationMs = 4000, startMs = 1000, endMs = 3000, fadeInMs = 500, fadeOutMs = 500),
                            MontageClip(id = "c", path = demo[2].path, title = "c", durationMs = 3000, startMs = 500, endMs = 2500)
                        ),
                        gapMs = 500
                    )
                    val mout = File(mdir, "out.wav")
                    val pvals = mutableListOf<Float>()
                    val mr = MontageRender.render(proj, mout, onProgress = { pvals.add(it) })
                    check(mr.ok) { mr.message ?: "render failed" }
                    val mh = WavHeader.read(mout) ?: error("montage header")
                    check(mh.sampleRate == 44100 && mh.channels == 2 && mh.bitsPerSample == 16) { "montage format" }
                    check(Math.abs(mr.durationMs - 4500) <= 40) { "montage duration=${mr.durationMs}" }
                    check(pvals.isNotEmpty() && pvals.last() >= 0.99f) { "progress end=${pvals.lastOrNull()}" }
                    // عينات خام: تلاشٍ داخلي يبدأ قرب الصفر، صمت الفاصل، ذروة المقطع الثاني
                    val raw = java.nio.file.Files.readAllBytes(mout.toPath())
                    fun sampleAt(frame: Int): Int {
                        val off = 44 + frame * 4
                        if (off + 1 >= raw.size) return 0
                        val lo = raw[off].toInt() and 0xFF
                        val hi = raw[off + 1].toInt()
                        return (hi shl 8) or lo
                    }
                    var headMax = 0
                    for (f in 0 until 20) headMax = maxOf(headMax, Math.abs(sampleAt(f)))
                    check(headMax < 400) { "fade-in head=$headMax" }
                    var gapMax = 0
                    for (f in 88_400 until 110_000) gapMax = maxOf(gapMax, Math.abs(sampleAt(f)))
                    check(gapMax <= 4) { "gap silence=$gapMax" }
                    var lateMax = 0
                    for (f in 112_000 until 150_000) lateMax = maxOf(lateMax, Math.abs(sampleAt(f)))
                    check(lateMax > 3000) { "late clip amplitude=$lateMax" }

                    // 4) الإلغاء والفشل برسالة عربية واضحة
                    val pc = MontageRender.render(proj, File(mdir, "cancel.wav"), cancelled = { true })
                    check(!pc.ok && pc.message!!.contains("أُلغي")) { "cancel message" }
                    val pf = MontageRender.render(MontageProject(), File(mdir, "empty.wav"))
                    check(!pf.ok && pf.message != null) { "empty project fails" }
                }
            }

            // ================= v0.34.0: الترقية الاحترافية للمونتاج =================
            run {
                // 1) رياضيات الكروس فيد والخط الزمني وقوة متساوية
                val xa = MontageClip(id = "xa", path = "/x/a.wav", title = "a", durationMs = 3000, startMs = 0, endMs = 3000)
                val xb = MontageClip(id = "xb", path = "/x/b.wav", title = "b", durationMs = 3000, startMs = 0, endMs = 3000)
                check(MontageLogic.totalDurationMs(listOf(xa, xb), 500, 0L) == 6500L) { "total gap" }
                check(MontageLogic.totalDurationMs(listOf(xa, xb), 500, 1000L) == 5000L) { "total xf" }
                check(MontageLogic.clipStartMs(listOf(xa, xb), 500, 1000L) == listOf(0L, 2000L)) { "starts xf" }
                check(MontageLogic.effectiveOverlap(500, 3000, 1000) == 500L) { "overlap clamp" }
                check(Math.abs(MontageLogic.crossfadeOutGain(0f) - 1f) < 1e-4f) { "xf out start" }
                check(Math.abs(MontageLogic.crossfadeInGain(1f) - 1f) < 1e-4f) { "xf in end" }
                val eMid = MontageLogic.crossfadeOutGain(0.5f).toDouble().let { it * it } +
                        MontageLogic.crossfadeInGain(0.5f).toDouble().let { it * it }
                check(Math.abs(eMid - 1.0) < 0.01) { "xf equal power" }
                val mf = MontageLogic.MICRO_FADE_MS
                check(MontageLogic.microFadeGainAt(0, 4000) == 0f) { "micro start" }
                check(Math.abs(MontageLogic.microFadeGainAt(mf / 2, 4000) - 0.5f) < 0.01f) { "micro mid" }
                check(MontageLogic.microFadeGainAt(mf + 1, 4000) == 1f) { "micro plateau" }

                // 2) التقسيم والتطبيع والحلقة والتراجع
                val sc = MontageClip(id = "sc", path = "/x/s.wav", title = "s", durationMs = 6000, startMs = 1000, endMs = 5000, volumePercent = 80, fadeInMs = 400, fadeOutMs = 600)
                val pair = MontageLogic.splitClipAt(sc, 2000)!!
                check(pair.first.endMs == 3000L && pair.second.startMs == 3000L) { "split windows" }
                check(pair.first.fadeInMs == 400L && pair.second.fadeOutMs == 600L) { "split fades" }
                check(MontageLogic.splitClipAt(sc, 0) == null) { "split edge" }
                check(MontageLogic.normalizedVolumePercent(50_000) == 62) { "normalize map" }
                check(MontageLogic.normalizedVolumePercent(12_000) == 200) { "normalize cap" }
                check(MontageLogic.normalizedVolumePercent(0) == 100) { "normalize silence" }
                check(MontageLogic.loopSeekMs(2000, 1000, 2000) == 1000L) { "loop wrap" }
                check(MontageLogic.loopSeekMs(1999, 1000, 2000) == null) { "loop before end" }
                check(MontageLogic.loopSeekMs(5000, 2000, 2000) == null) { "loop invalid" }
                run {
                    val h = MontageHistory(cap = 2)
                    val a = MontageProject(title = "a"); val b = a.copy(title = "b"); val c = b.copy(title = "c"); val d = c.copy(title = "d")
                    h.record(a); h.record(b); h.record(c)
                    check(h.undo(d)!!.title == "c" && h.undo(c)!!.title == "b" && !h.canUndo) { "history cap" }
                    h.record(b)
                    check(!h.canRedo) { "record clears redo" }
                }

                // 3) التصيير: كروس فيد حقيقي + تلاشي دقيق + مغلّفات وذروة خام
                run {
                    val pdir = File(tmp, "pro")
                    val t1 = MontageDemo.tone(File(pdir, "a.wav"), 44100, 2, 3000, 440.0)
                    val t2 = MontageDemo.tone(File(pdir, "b.wav"), 44100, 2, 3000, 330.0)
                    val xfProj = MontageProject(
                        clips = listOf(
                            MontageClip(id = "a", path = t1.path, title = "a", durationMs = 3000),
                            MontageClip(id = "b", path = t2.path, title = "b", durationMs = 3000)
                        ),
                        gapMs = 500, crossfadeMs = 1000
                    )
                    val xout = File(pdir, "xf.wav")
                    val xr = MontageRender.render(xfProj, xout)
                    check(xr.ok && Math.abs(xr.durationMs - 5000) <= 40) { "xf render duration=${xr.durationMs}" }
                    val xraw = java.nio.file.Files.readAllBytes(xout.toPath())
                    fun sampleAt(frame: Int): Int {
                        val off = 44 + frame * 4
                        if (off + 1 >= xraw.size) return 0
                        val lo = xraw[off].toInt() and 0xFF
                        val hi = xraw[off + 1].toInt()
                        return (hi shl 8) or lo
                    }
                    var overlapMax = 0
                    for (f in (2000 * 44100 / 1000) until (3000 * 44100 / 1000)) overlapMax = maxOf(overlapMax, Math.abs(sampleAt(f)))
                    check(overlapMax > 8000) { "xf overlap amplitude=$overlapMax" }
                    var silentRun = 0; var worst = 0
                    for (f in (2000 * 44100 / 1000) until (3000 * 44100 / 1000)) {
                        if (Math.abs(sampleAt(f)) < 500) silentRun++ else { worst = maxOf(worst, silentRun); silentRun = 0 }
                    }
                    check(maxOf(worst, silentRun) < 441) { "xf silent run=${maxOf(worst, silentRun)}" }
                    // تلاشي دقيق: قصّ صلب يبدأ من الصفر
                    val single = MontageProject(clips = listOf(MontageClip(id = "s", path = t1.path, title = "s", durationMs = 3000)))
                    val sout = File(pdir, "single.wav")
                    check(MontageRender.render(single, sout).ok) { "single render" }
                    val sraw = java.nio.file.Files.readAllBytes(sout.toPath())
                    var headMax = 0
                    for (f in 0 until 4) {
                        val off = 44 + f * 4
                        val lo = sraw[off].toInt() and 0xFF
                        val hi = sraw[off + 1].toInt()
                        headMax = maxOf(headMax, Math.abs((hi shl 8) or lo))
                    }
                    check(headMax < 500) { "micro head=$headMax" }
                    // مغلّف العرض: دلاء وذروة خام
                    val env = MontageRender.computeEnvelope(MontageClip(id = "a", path = t1.path, title = "a", durationMs = 3000), 80)
                    check(env.peaks.size == 80 && env.maxPeak() > 0.25f) { "envelope peak=${env.maxPeak()}" }
                    val raw = MontageRender.computeRawPeak(MontageClip(id = "a", path = t1.path, title = "a", durationMs = 3000))
                    check(raw in 11_000..12_500) { "raw peak=$raw" }
                }

                // 4) المخزن: حقول جديدة وتوافق الملفات القديمة
                run {
                    val mf = File(tmp, "pro-montage.json")
                    MontageStore(mf).save(MontageProject(clips = listOf(xa), gapMs = 250, crossfadeMs = 750, title = "ت", playheadMs = 1234))
                    val back = MontageStore(mf).project()
                    check(back.crossfadeMs == 750L && back.playheadMs == 1234L) { "store new fields" }
                    val old = File(tmp, "pro-old.json")
                    old.writeText("{\"clips\":[{\"id\":\"a\",\"path\":\"/x/a.wav\",\"title\":\"a\",\"durationMs\":3000,\"startMs\":0,\"endMs\":3000}],\"gapMs\":500,\"title\":\"قديم\"}")
                    val legacy = MontageStore(old).project()
                    check(legacy.crossfadeMs == 0L && legacy.playheadMs == 0L && legacy.clips.size == 1) { "legacy compat" }
                }
            }

            println("SELFTEST OK — version $APP_VERSION")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }
}
