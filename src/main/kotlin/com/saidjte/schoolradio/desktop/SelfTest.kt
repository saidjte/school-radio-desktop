package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.AudioDevices
import com.saidjte.schoolradio.desktop.audio.JingleWallEngine
import com.saidjte.schoolradio.desktop.audio.LevelMeter
import com.saidjte.schoolradio.desktop.audio.MixBus
import com.saidjte.schoolradio.desktop.audio.MixMath
import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageExportFormat
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.FakeToneVoice
import com.saidjte.schoolradio.desktop.audio.FakeTrackVoice
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.RecordingsScanner
import com.saidjte.schoolradio.desktop.audio.RecordingStudio
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
import com.saidjte.schoolradio.desktop.data.MontageMarker
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageStore
import com.saidjte.schoolradio.desktop.data.MontageTemplateCatalog
import com.saidjte.schoolradio.desktop.data.MontageTemplatesLogic
import com.saidjte.schoolradio.desktop.data.MontageTrack
import com.saidjte.schoolradio.desktop.data.LiveAnnounceLogic
import com.saidjte.schoolradio.desktop.audio.BundledJingles
import com.saidjte.schoolradio.desktop.data.ReadingJson
import com.saidjte.schoolradio.desktop.data.Json
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
import com.saidjte.schoolradio.desktop.youtube.YouTubeClientChain
import com.saidjte.schoolradio.desktop.youtube.YouTubeImportService
import com.saidjte.schoolradio.desktop.youtube.YouTubeJson
import com.saidjte.schoolradio.desktop.youtube.YouTubeUrl
import com.saidjte.schoolradio.desktop.youtube.YtDlpLocator
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
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

            // ================= v0.35.0: المسار المزدوج والخفض والعلامات والقوالب وإعلان البث =================
            run {
                // 1) نطاقات النطق ومنحنى الخفض النقي
                val da = MontageClip(id = "da", path = "/x/a.wav", title = "a", durationMs = 3000, startMs = 0, endMs = 3000)
                val db2 = MontageClip(id = "db", path = "/x/b.wav", title = "b", durationMs = 2000, startMs = 0, endMs = 2000)
                check(MontageLogic.narrationIntervals(listOf(da, db2), 0, 0) == listOf(0L to 5000L)) { "intervals merged" }
                check(MontageLogic.narrationIntervals(listOf(da, db2), 500, 0).size == 2) { "intervals split" }
                val iv = listOf(1000L to 3000L)
                val duck = MontageLogic.dbToAmp(-12.0)
                check(MontageLogic.duckGainAt(500, iv, -12, 300, 800) == 1f) { "duck before attack" }
                check(Math.abs(MontageLogic.duckGainAt(2000, iv, -12, 300, 800) - duck) < 1e-4f) { "duck inside" }
                check(MontageLogic.duckGainAt(3801, iv, -12, 300, 800) == 1f) { "duck after release" }
                check(MontageLogic.duckGainAt(500, iv, -12, 300, 800, enabled = false) == 1f) { "duck disabled" }
                check(MontageLogic.clampDuckDb(-99) == -30 && MontageLogic.clampDuckDb(0) == -3) { "duck clamp" }

                // 2) القوالب والعلامات
                val tpl = MontageTemplateCatalog.byId("full-break")!!
                val applied = MontageTemplatesLogic.apply(MontageProject(), tpl)
                check(applied.markers.size == 5 && applied.gapMs == 400L && applied.duckDb == -12) { "template apply" }
                check(MontageLogic.sanitizeMarkers(listOf(MontageMarker("x", "  ", 5))).isEmpty()) { "marker empty name" }

                // 3) تصيير سرير موسيقي مخفوض تحت التعليق — عينات خام
                // (تعليق خافت 4000 وسرير عالٍ 12000 كي يُعزل أثر الخفض على السرير)
                val ddir = File(tmp, "dual")
                ddir.mkdirs()
                val voice = File(ddir, "voice.wav")
                run {
                    val frames = 3 * 44100
                    val w = WavFileWriter(voice, 44100, 2)
                    val vbuf = ByteArray(8192 * 4)
                    var vf = 0
                    while (vf < frames) {
                        val n = minOf(8192, frames - vf)
                        var bi = 0
                        for (i in 0 until n) {
                            val v = (sin(2.0 * PI * 440.0 * (vf + i) / 44100.0) * 4000).toInt()
                            vbuf[bi++] = (v and 0xFF).toByte(); vbuf[bi++] = ((v shr 8) and 0xFF).toByte()
                            vbuf[bi++] = (v and 0xFF).toByte(); vbuf[bi++] = ((v shr 8) and 0xFF).toByte()
                        }
                        w.write(vbuf, 0, bi)
                        vf += n
                    }
                    w.close()
                }
                val bed = MontageDemo.tone(File(ddir, "bed.wav"), 44100, 2, 6000, 330.0)
                val dual = MontageProject(
                    clips = listOf(MontageClip(id = "v", path = voice.path, title = "v", durationMs = 3000)),
                    musicClips = listOf(
                        MontageMusicClip(id = "b", path = bed.path, title = "b", durationMs = 6000, startMs = 0)
                    ),
                    duckingEnabled = true, duckDb = -12, duckAttackMs = 200, duckReleaseMs = 400
                )
                val dout = File(ddir, "out.wav")
                val dr = MontageRender.render(dual, dout)
                check(dr.ok && Math.abs(dr.durationMs - 6000) <= 40) { "dual duration=${dr.durationMs}" }
                val draw = java.nio.file.Files.readAllBytes(dout.toPath())
                fun dsampleAt(raw: ByteArray, frame: Int): Int {
                    val off = 44 + frame * 4
                    if (off + 1 >= raw.size) return 0
                    val lo = raw[off].toInt() and 0xFF
                    val hi = raw[off + 1].toInt()
                    return (hi shl 8) or lo
                }
                var dmax = 0
                for (f in (600 * 44100 / 1000) until (2600 * 44100 / 1000)) dmax = maxOf(dmax, Math.abs(dsampleAt(draw, f)))
                var amax = 0
                for (f in (3600 * 44100 / 1000) until (5900 * 44100 / 1000)) amax = maxOf(amax, Math.abs(dsampleAt(draw, f)))
                check(dmax > 2000 && dmax < 8000) { "duck bed ducked=$dmax" }
                check(amax > 11000) { "duck bed recovered=$amax" }
                // بلا خفض: السرير كامل القوة أثناء النطق
                val offOut = File(ddir, "off.wav")
                check(MontageRender.render(dual.copy(duckingEnabled = false), offOut).ok) { "duck off render" }
                val offRaw = java.nio.file.Files.readAllBytes(offOut.toPath())
                var offMax = 0
                for (f in (600 * 44100 / 1000) until (2600 * 44100 / 1000)) offMax = maxOf(offMax, Math.abs(dsampleAt(offRaw, f)))
                check(offMax > dmax + 5000) { "duck off full=$offMax during=$dmax" }

                // 4) إعلان البث المباشر: حدود الشارة والثوابت
                check(!LiveAnnounceLogic.isActive(null, 1000L)) { "announce null" }
                check(LiveAnnounceLogic.isActive(1000L, 1000L)) { "announce start inclusive" }
                check(!LiveAnnounceLogic.isActive(1000L, 1000L + LiveAnnounceLogic.DURATION_MS)) { "announce end exclusive" }
                check(LiveAnnounceLogic.remainingMs(1000L, 99999L) == 0L) { "announce remaining floor" }
                check(BundledJingles.ALL.any { it.id == LiveAnnounceLogic.JINGLE_ID }) { "announce jingle exists" }
            }


            // ================= v0.37.0: تسمية المقاطع وبلا كتم وتصدير MP3 =================
            run {
                // 1) تعقيم أسماء المقاطع للنقر المزدوج
                check(MontageLogic.sanitizeClipTitle("  افتتاحية الحصة  ") == "افتتاحية الحصة") { "rename trim" }
                check(MontageLogic.sanitizeClipTitle("سطر\nثاني") == "سطر ثاني") { "rename newline" }
                check(MontageLogic.sanitizeClipTitle("   ") == "مقطع") { "rename empty" }
                check(MontageLogic.sanitizeClipTitle("ا".repeat(80)).length == 40) { "rename clamp" }

                // 2) الكتم/المنفرد حيّان في المسارات منذ v0.41.0 — الأعلام القديمة تُحفظ
                // وتحترم في المزج (الأزرار عادت فلا مسار يتيم مكتموم بلا زر يعيده)
                val legacy = listOf(
                    MontageTrack(id = "t1", name = "موسيقى", volumePercent = 130, muted = true),
                    MontageTrack(id = "t2", name = "مؤثرات", volumePercent = 80, solo = true)
                )
                val eff = MontageLogic.effectiveTracks(MontageProject(tracks = legacy))
                check(eff.size == 2 && eff[0].muted && eff[1].solo) { "legacy flags live" }
                check(eff[0].volumePercent == 130 && eff[1].volumePercent == 80) { "legacy volumes kept" }
                check(MontageLogic.anyTrackSolo(eff)) { "solo honored" }
                check(MontageLogic.trackGainPercent(eff[0], anySolo = false) == 0) { "muted gain zero" }
                check(MontageLogic.trackGainPercent(eff[1], anySolo = true) == 80) { "solo gain kept" }

                // 3) تصدير MP3 إلى جانب WAV — LAME نقي بنفس خطة التصيير
                val m37 = File(tmp, "m37").apply { mkdirs() }
                val a37 = MontageDemo.tone(File(m37, "a.wav"), 44100, 2, 2500, 440.0)
                val b37 = MontageDemo.tone(File(m37, "b.wav"), 22050, 1, 2000, 330.0)
                val p37 = MontageProject(
                    clips = listOf(
                        MontageClip(id = "a", path = a37.path, title = "أول", durationMs = 2500, startMs = 0, endMs = 2500),
                        MontageClip(id = "b", path = b37.path, title = "ثانٍ", durationMs = 2000, startMs = 0, endMs = 2000)
                    ),
                    gapMs = 500
                )
                val plan37 = MontageLogic.totalDurationMs(p37.clips, p37.gapMs, p37.crossfadeMs)
                val mp3out = File(m37, "out.mp3")
                val r37 = MontageRender.render(p37, mp3out, format = MontageExportFormat.MP3, bitrateKbps = 192)
                check(r37.ok) { "mp3 render: " + (r37.message ?: "") }
                check(mp3out.length() > 5_000) { "mp3 small: " + mp3out.length() }
                check(Math.abs(r37.durationMs - plan37) <= 60) { "mp3 sink duration=" + r37.durationMs }
                val info37 = Mp3Info.analyze(mp3out) ?: error("mp3 header parse")
                check(info37.first.sampleRate == 44100 && info37.first.channels == 2) { "mp3 format" }
                check(info37.first.bitRateKbps == 192 && !info37.isVbr) { "mp3 cbr 192" }
                check(Math.abs(info37.durationMs - plan37) <= 260) { "mp3 duration=" + info37.durationMs }
                // الجودة تغيّر الحجم بنفس المدة: 320 أكبر من 128
                val lo37 = File(m37, "lo.mp3")
                val hi37 = File(m37, "hi.mp3")
                check(MontageRender.render(p37, lo37, format = MontageExportFormat.MP3, bitrateKbps = 128).ok) { "mp3 128" }
                check(MontageRender.render(p37, hi37, format = MontageExportFormat.MP3, bitrateKbps = 320).ok) { "mp3 320" }
                check(hi37.length() > lo37.length()) { "bitrate size order" }
                check(Mp3Info.analyze(lo37)!!.first.bitRateKbps == 128) { "mp3 128 header" }
                check(Mp3Info.analyze(hi37)!!.first.bitRateKbps == 320) { "mp3 320 header" }
                // WAV الافتراضي بلا مساس
                val wav37 = File(m37, "out.wav")
                check(MontageRender.render(p37, wav37).ok) { "wav default" }
                val wh37 = WavHeader.read(wav37) ?: error("wav header")
                check(wh37.sampleRate == 44100 && wh37.channels == 2) { "wav format" }
                // ثوابت الصيغة والجودة
                check(MontageExportFormat.entries.map { it.name }.toSet() == setOf("WAV", "MP3")) { "format enum" }
                check(MontageRender.MP3_BITRATES == listOf(128, 192, 256, 320)) { "bitrate ladder" }
                check(MontageRender.MP3_DEFAULT_KBPS == 192) { "bitrate default" }
            }

            // ================= v0.38.0: أدوات الحفرة — الحافظة والعكس والسرعة والنبرة =================
            run {
                // 1) عكس الموجة — قلب إطارات بلا مساس الاقتران
                val rev38 = com.saidjte.schoolradio.desktop.audio.ClipTransforms.reversePcm(
                    shortArrayOf(1, 2, 3, 4, 5, 6)
                )
                check(rev38.toList() == listOf(5, 6, 3, 4, 1, 2).map { it.toShort() }) { "reverse order" }

                // 2) شد WSOLA: مدة تُقسَم بالمعامل والنبرة ثابتة — على موجة معروفة
                fun sine38(hz: Double, ms: Long): ShortArray {
                    val n = (ms * 44100 / 1000L).toInt()
                    return ShortArray(n * 2) { i ->
                        val f = i / 2
                        (Math.sin(2.0 * Math.PI * hz * f / 44100.0) * 12000.0).toInt().toShort()
                    }
                }
                val t38 = com.saidjte.schoolradio.desktop.audio.ClipTransforms
                val fast38 = t38.wsolaChangeTempo(sine38(440.0, 2000), 2.0)
                val fastDur = fast38.size / 2.0 / 44100.0
                check(Math.abs(fastDur - 1.0) <= 0.08) { "wsola tempo2 dur=$fastDur" }
                val fastHz = t38.estimateDominantHz(fast38)
                check(Math.abs(fastHz - 440.0) <= 35.0) { "wsola tempo2 hz=$fastHz" }
                val slow38 = t38.wsolaChangeTempo(sine38(440.0, 1500), 0.5)
                val slowDur = slow38.size / 2.0 / 44100.0
                check(Math.abs(slowDur - 3.0) <= 0.12) { "wsola tempo05 dur=$slowDur" }
                check(Math.abs(t38.estimateDominantHz(slow38) - 440.0) <= 35.0) { "wsola tempo05 hz" }

                // 3) النبرة: ±12 ثُمانية كاملة بطولة محفوظة
                val up38 = t38.pitchShift(sine38(440.0, 2000), 12)
                check(Math.abs(up38.size / 2.0 / 44100.0 - 2.0) <= 0.10) { "pitch+12 dur=${up38.size / 2.0 / 44100.0}" }
                val upHz = t38.estimateDominantHz(up38)
                check(Math.abs(upHz - 880.0) <= 45.0) { "pitch+12 hz=$upHz" }
                val down38 = t38.pitchShift(sine38(440.0, 2000), -12)
                val downHz = t38.estimateDominantHz(down38)
                check(Math.abs(downHz - 220.0) <= 25.0) { "pitch-12 hz=$downHz" }
                check(Math.abs(down38.size / 2.0 / 44100.0 - 2.0) <= 0.10) { "pitch-12 dur" }
                // الحبوس وعقد المعامل
                check(t38.clampSpeedPercent(1) == 50 && t38.clampSpeedPercent(5000) == 200) { "speed clamp" }
                check(t38.clampPitchSemitones(-99) == -12 && t38.clampPitchSemitones(99) == 12) { "pitch clamp" }
                check(Math.abs(t38.pitchFactor(12) - 2.0) < 1e-9) { "pitch factor octave" }

                // 4) الارتداد المخزَّن على ملف حقيقي: إعادة الاستخدام والصلاحية
                val m38 = File(tmp, "m38").apply { mkdirs() }
                val tone38 = MontageDemo.tone(File(m38, "tone.wav"), 44100, 2, 2500, 440.0)
                val clip38 = MontageClip(id = "a", path = tone38.path, title = "مقطع", durationMs = 2500, startMs = 0, endMs = 2500)
                val cache38 = File(m38, "bounce")
                val b1 = t38.bounce(clip38, "reverse", "-", t38::reversePcm, cache38)
                val b2 = t38.bounce(clip38, "reverse", "-", t38::reversePcm, cache38)
                check(b1.file.absolutePath == b2.file.absolutePath && b1.file.isFile) { "bounce reuse" }
                check(Math.abs(b1.durationMs - 2500) <= 40) { "bounce dur=${b1.durationMs}" }
                val wh38 = WavHeader.read(b1.file) ?: error("bounce wav header")
                check(wh38.sampleRate == 44100 && wh38.channels == 2) { "bounce wav format" }
                // الدمج عبر المُصيِّر نفسه: 2500 + 500 + 2500 = 5500
                val toneB38 = MontageDemo.tone(File(m38, "toneB.wav"), 22050, 1, 2500, 330.0)
                val clipB38 = MontageClip(id = "b", path = toneB38.path, title = "ثانٍ", durationMs = 2500, startMs = 0, endMs = 2500)
                val mg38 = t38.bounceMerge(clip38, clipB38, gapMs = 500, crossfadeMs = 0, cacheDir = cache38)
                check(Math.abs(mg38.durationMs - 5500) <= 80) { "merge dur=${mg38.durationMs}" }
                check(WavHeader.read(mg38.file) != null) { "merge wav header" }

                // 5) الحافظة والتكرار والدمج — الرياضيات النقية
                fun vc38(id: String, dur: Long = 1000L) = MontageClip(id = id, path = "/p/$id.wav", title = id, durationMs = dur, startMs = 0, endMs = dur)
                val base38 = listOf(vc38("a"), vc38("b"), vc38("c"))
                val copy38 = MontageLogic.pastedVoiceCopy(vc38("b"))
                check(copy38.id != "b" && copy38.path == "/p/b.wav") { "paste fresh id" }
                val mid38 = MontageLogic.pasteVoiceAfter(base38, copy38, "b")
                check(mid38.size == 4 && mid38[2].id == copy38.id) { "paste after selection" }
                check(MontageLogic.pasteVoiceAfter(base38, copy38, null).last().id == copy38.id) { "paste at end" }
                val dup38 = MontageLogic.duplicateVoiceAfter(base38, "a", 3)
                check(dup38.size == 6 && dup38.first().id == "a" && dup38.last().id == "c") { "duplicate voice x3" }
                check(dup38.map { it.id }.toSet().size == 6) { "duplicate unique ids" }
                check(MontageLogic.duplicateVoiceAfter(base38, "a", 0) === base38) { "duplicate zero guard" }
                val srcMu = MontageMusicClip(id = "m1", path = "/p/m.wav", title = "سرير", durationMs = 2000, startMs = 500)
                val dmu38 = MontageLogic.duplicateTrackClip(srcMu, 3)
                check(dmu38.map { it.startMs } == listOf(2500L, 4500L, 6500L)) { "duplicate track contiguous" }
                // عقد الدمج: الأخير لا يُدمج والقصير مستبعد
                check(MontageLogic.mergeableVoicePair(base38, "c") == null) { "merge last rejected" }
                check(MontageLogic.mergeableVoicePair(listOf(vc38("a"), vc38("b", 30)), "a") == null) { "merge short rejected" }
                check(MontageLogic.mergeableVoicePair(listOf(vc38("a", 800), vc38("b", 900)), "a") != null) { "merge valid pair" }
                check(t38.mergedTitle(" أول ", " ثانٍ ") == "أول+ثانٍ") { "merged title" }

                // 6) اختصارات الحفرة الحديثة
                fun r38(key: Key) = com.saidjte.schoolradio.desktop.ui.resolveMontageShortcut(key, false, false, false, false)
                check(
                    r38(Key.X) == com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.CutSelected &&
                        r38(Key.V) == com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.PasteClipboard &&
                        r38(Key.J) == com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.MergeWithNext &&
                        r38(Key.D) == com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.DuplicateSelected &&
                        r38(Key.C) == com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.CopySelected &&
                        com.saidjte.schoolradio.desktop.ui.resolveMontageShortcut(Key.X, true, false, false, false) == com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.CutSelected &&
                        com.saidjte.schoolradio.desktop.ui.resolveMontageShortcut(Key.S, false, false, false, false) == com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.SplitAtPlayhead
                ) { "v38 shortcuts" }
            }

            // ================= v0.39.0: بيئة العمل — تخصيص وتراجع وسحب وموثوقية =================
            run {
                val d39 = File(tmp, "ws39").apply { mkdirs() }

                // 1) مساحة العمل: حبس القيم وتعقيم آخر صفحة
                val st39 = com.saidjte.schoolradio.desktop.data.WorkspaceLogic.resolveState(
                    com.saidjte.schoolradio.desktop.data.WorkspaceJson(
                        fontScale = 99.0, sidebarWidth = 5.0, lastTab = "GHOST", rememberLastTab = true
                    ),
                    setOf("STUDIO", "MUSIC", "MONTAGE", "SCRIPT")
                )
                check(st39.fontScale == 1.5 && st39.sidebarWidth == 180.0) { "ws clamp" }
                check(st39.rememberLastTab && st39.lastTab == null) { "ws lastTab sanitize" }
                val def39 = com.saidjte.schoolradio.desktop.data.WorkspaceLogic.resolveState(null, emptySet())
                check(def39.fontScale == 1.0 && def39.sidebarWidth == 232.0 && def39.showScript) { "ws defaults" }

                // 2) التراجع/الإعادة العام: دفع وتراجع وإعادة وقطع فرع الإعادة
                val h39 = com.saidjte.schoolradio.desktop.data.SnapshotHistory<String>()
                check(!h39.canUndo && !h39.canRedo) { "history empty" }
                h39.push("A")
                check(h39.undo("B") == "A" && !h39.canUndo && h39.canRedo) { "undo39" }
                check(h39.redo("A") == "B" && !h39.canRedo) { "redo39" }
                h39.push("X")
                check(h39.undo("Y") == "X") { "undo second" }
                h39.push("Z")
                check(!h39.canRedo) { "redo branch cut" }
                check(h39.undo("W") == "Z") { "undo after branch cut" }

                // 3) السحب والإفلات: القرار النقي (الصوت يمر والباقي يُعدّ للرسالة)
                val wav39 = File(d39, "t.wav").apply { writeBytes(ByteArray(4)) }
                val txt39 = File(d39, "t.txt").apply { writeBytes(ByteArray(4)) }
                val dec39 = com.saidjte.schoolradio.desktop.data.DropLogic.decide(listOf(wav39, txt39))
                check(dec39.audioFiles == listOf(wav39) && dec39.skippedUnsupported == 1) { "drop decide" }
                check(com.saidjte.schoolradio.desktop.data.DropLogic.destinationFor("MONTAGE")
                    == com.saidjte.schoolradio.desktop.data.DropLogic.Destination.MONTAGE) { "drop dest montage" }
                check(com.saidjte.schoolradio.desktop.data.DropLogic.destinationFor("MUSIC")
                    == com.saidjte.schoolradio.desktop.data.DropLogic.Destination.LIBRARY) { "drop dest library" }
                check(com.saidjte.schoolradio.desktop.data.DropLogic.decide(emptyList()).message("المكتبة") == null) { "drop silent" }
                check(com.saidjte.schoolradio.desktop.data.DropLogic.DEFAULT_IMPORT_CATEGORY.isNotEmpty()) { "drop category" }

                // 4) النسخ الاحتياطية المتدحرجة: أول لقطة ثم تخطي الثابت ثم التدوير
                val store39 = File(d39, "script.json").apply { writeText("""{"sections":[]}""") }
                val rep1 = com.saidjte.schoolradio.desktop.data.AutoBackup.snapshot(d39, names = listOf("script.json"), nowMs = 1)
                check(rep1.copied == listOf("script.json")) { "backup first snapshot" }
                val rep2 = com.saidjte.schoolradio.desktop.data.AutoBackup.snapshot(d39, names = listOf("script.json"), nowMs = 2)
                check(rep2.isEmpty) { "backup skips unchanged" }
                for (i in 3..9) {
                    store39.writeText("""{"n":$i}""")
                    com.saidjte.schoolradio.desktop.data.AutoBackup.snapshot(d39, names = listOf("script.json"), keep = 3, nowMs = i.toLong() * 10)
                }
                val copies39 = com.saidjte.schoolradio.desktop.data.AutoBackup.backupsDir(d39).listFiles { f -> f.isFile } ?: arrayOf()
                check(copies39.size == 3) { "backup rotation kept=${copies39.size}" }
                check(com.saidjte.schoolradio.desktop.data.AutoBackup.newestBackup(
                    com.saidjte.schoolradio.desktop.data.AutoBackup.backupsDir(d39), "script.json"
                )?.name == "script.json.90.bak") { "backup newest" }

                // 5) الاستعادة بعد انقطاع مفاجئ: عزل التالف واستعادة أحدث نسخة سليمة + قفل التشغيل
                store39.writeText("""{"sections":[{"id":2,"tit""")
                val rec39 = com.saidjte.schoolradio.desktop.data.Recovery.recoverCorruptStores(d39, names = listOf("script.json"), nowMs = 999)
                check(rec39.recoveredFromBackup == listOf("script.json") && rec39.quarantined == listOf("script.json")) { "recover39" }
                check(com.saidjte.schoolradio.desktop.data.Recovery.isParseableJson(store39)) { "recovered parses" }
                check(store39.readText().contains("\"n\":9")) { "recovered is newest snapshot" }
                check(com.saidjte.schoolradio.desktop.data.Recovery.corruptDir(d39).listFiles()?.size == 1) { "quarantine kept" }
                check(!com.saidjte.schoolradio.desktop.data.Recovery.wasInterrupted(d39)) { "lock absent" }
                com.saidjte.schoolradio.desktop.data.Recovery.acquireLock(d39, 1)
                check(com.saidjte.schoolradio.desktop.data.Recovery.wasInterrupted(d39)) { "lock acquired" }
                com.saidjte.schoolradio.desktop.data.Recovery.releaseLock(d39)
                check(!com.saidjte.schoolradio.desktop.data.Recovery.wasInterrupted(d39)) { "lock released" }

                // 6) اختصارات v0.39.0: F1 وAlt+أرقام وCtrl+Z/Y وحرس المفاتيح
                fun rs39(k: Key, c: Boolean = false, a: Boolean = false) =
                    com.saidjte.schoolradio.desktop.ui.resolveShortcut(k, c, a, false)
                check(rs39(Key.F1) == com.saidjte.schoolradio.desktop.ui.ShortcutAction.ShowShortcuts) { "f1" }
                check(rs39(Key.One, a = true) == com.saidjte.schoolradio.desktop.ui.ShortcutAction.SwitchTab(1)) { "alt1" }
                check(rs39(Key.Eight, a = true) == com.saidjte.schoolradio.desktop.ui.ShortcutAction.SwitchTab(8)) { "alt8" }
                // v0.42.0: Alt+9 فتح الصفحة التاسعة «مكتبة الأصوات» — العقد المحدث
                check(rs39(Key.Nine, a = true) == com.saidjte.schoolradio.desktop.ui.ShortcutAction.SwitchTab(9)) { "alt9 sounds page" }
                check(rs39(Key.Z, c = true) == com.saidjte.schoolradio.desktop.ui.ShortcutAction.ContextualUndo) { "ctrlz" }
                check(rs39(Key.Y, c = true) == com.saidjte.schoolradio.desktop.ui.ShortcutAction.ContextualRedo) { "ctrly" }
                check(com.saidjte.schoolradio.desktop.ui.resolveShortcut(Key.Z, true, false, false, true)
                    == com.saidjte.schoolradio.desktop.ui.ShortcutAction.ContextualRedo) { "ctrlshiftz" }
                check(rs39(Key.Spacebar, a = true) == null && rs39(Key.One, c = true, a = true) == null) { "modifier guards" }
            }

            // ================= v0.40.0: مكتبة المؤثرات الصوتية =================
            run {
                val fx40 = com.saidjte.schoolradio.desktop.audio.AudioFx
                val ps40 = com.saidjte.schoolradio.desktop.audio.FxPresets

                fun sine40(hz: Double, ms: Long, amp: Double = 12000.0): ShortArray {
                    val n = (ms * 44100 / 1000L).toInt()
                    return ShortArray(n * 2) { i ->
                        val f = i / 2
                        (Math.sin(2.0 * Math.PI * hz * f / 44100.0) * amp).toInt().toShort()
                    }
                }

                // 1) السجل: 14 مؤثرًا بمعاملات سليمة و12 جاهزة بمؤثرات معروفة
                check(fx40.all.size == 14 && fx40.all.map { it.id }.toSet().size == 14) { "fx registry" }
                fx40.all.forEach { def ->
                    def.params.forEach { p ->
                        check(p.min <= p.default && p.default <= p.max && p.step > 0) { "fx param ${def.id}.${p.id}" }
                    }
                }
                check(ps40.all.size >= 12) { "presets count" }
                ps40.all.forEach { p ->
                    p.steps.forEach { s -> check(fx40.byId(s.defId) != null) { "preset ${p.id} def" } }
                }

                // 2) السلسلة المشفرة: محايدة ترتيب القيم ودائرية التشفير
                fun def40(id: String) = fx40.byId(id) ?: error("no def $id")
                val specA = fx40.encodeChain(listOf(
                    com.saidjte.schoolradio.desktop.audio.FxChainStep("echo", fx40.clampValues(def40("echo"), mapOf("delay" to 300.0, "wet" to 50.0)))
                ))
                val specB = fx40.encodeChain(listOf(
                    com.saidjte.schoolradio.desktop.audio.FxChainStep("echo", fx40.clampValues(def40("echo"), linkedMapOf("wet" to 50.0, "delay" to 300.0)))
                ))
                check(specA == specB) { "fx spec deterministic" }
                check(fx40.encodeChain(fx40.parseChain(specA)) == specA) { "fx spec round trip" }

                // 3) الحبوس والخطوة: نصف نغمة عائمة تقرب لعدد صحيح
                val cl40 = fx40.clampValues(def40("pitch"), mapOf("semitones" to 2.9999))
                check(cl40["semitones"] == 3.0) { "fx snap" }
                check(fx40.clampValues(def40("pitch"), mapOf("semitones" to 99.0))["semitones"] == 12.0) { "fx clamp" }

                // 4) السلامة: الصمت يبقى صامتًا والنطاق سليم والمدة محفوظة للناقل
                val silence40 = sine40(440.0, 300, amp = 0.0)
                fx40.all.forEach { def ->
                    val spec = fx40.encodeChain(listOf(
                        com.saidjte.schoolradio.desktop.audio.FxChainStep(def.id, fx40.defaultValues(def))
                    ))
                    val out = fx40.applySpec(silence40.copyOf(), spec)
                    check(out.size >= silence40.size) { "fx ${def.id} tail" }
                    out.forEach { v ->
                        check(Math.abs(v / 32768.0) <= 1.001) { "fx ${def.id} bounds" }
                    }
                }
                val pitchSpec40 = fx40.encodeChain(listOf(
                    com.saidjte.schoolradio.desktop.audio.FxChainStep("pitch", fx40.clampValues(def40("pitch"), mapOf("semitones" to 12.0)))
                ))
                val up40 = fx40.applySpec(sine40(440.0, 2000).copyOf(), pitchSpec40)
                check(Math.abs(up40.size / 2.0 / 44100.0 - 2.0) <= 0.10) { "fx pitch dur" }
                val upHz40 = com.saidjte.schoolradio.desktop.audio.ClipTransforms.estimateDominantHz(up40)
                check(Math.abs(upHz40 - 880.0) <= 60.0) { "fx pitch hz=$upHz40" }

                // 5) الذيل الزمني: التأخير يضيف طبقة عند موضعها المطلوب
                val impulse40 = ShortArray(2000)
                impulse40[2 * 900] = 30000; impulse40[2 * 900 + 1] = 30000
                val delaySpec = fx40.encodeChain(listOf(
                    com.saidjte.schoolradio.desktop.audio.FxChainStep(
                        "delay",
                        fx40.clampValues(def40("delay"), mapOf("tap1" to 250.0, "tap1Level" to 45.0, "tap2Level" to 0.0))
                    )
                ))
                val dOut = fx40.applySpec(impulse40.copyOf(), delaySpec)
                val dTarget = 250 * 44100 / 1000
                var dPeak = 0
                val at40 = 900 + dTarget // الطبقة بعد النبضة لا بعد بداية المصفوفة
                for (i in (at40 - 44100 / 50).coerceAtLeast(0)..(at40 + 44100 / 50).coerceAtMost(dOut.size / 2 - 1)) {
                    dPeak = Math.max(dPeak, Math.abs(dOut[2 * i].toInt()))
                }
                check(dPeak > 8000) { "fx delay tap=$dPeak" }

                // 6) المبدّل: أنثوي يرفع النبرة ورجولي يخفضها فعليًا
                val src220 = sine40(220.0, 2200)
                val morphF = fx40.encodeChain(listOf(
                    com.saidjte.schoolradio.desktop.audio.FxChainStep("morpher", fx40.clampValues(def40("morpher"), mapOf("mode" to 0.0, "intensity" to 75.0)))
                ))
                val morphD = fx40.encodeChain(listOf(
                    com.saidjte.schoolradio.desktop.audio.FxChainStep("morpher", fx40.clampValues(def40("morpher"), mapOf("mode" to 1.0, "intensity" to 75.0)))
                ))
                val hzF = com.saidjte.schoolradio.desktop.audio.ClipTransforms.estimateDominantHz(fx40.applySpec(src220.copyOf(), morphF))
                val hzD = com.saidjte.schoolradio.desktop.audio.ClipTransforms.estimateDominantHz(fx40.applySpec(src220.copyOf(), morphD))
                check(hzF > 250.0 && hzF < 330.0) { "morph female hz=$hzF" }
                check(hzD > 150.0 && hzD < 195.0 && hzF > hzD + 40) { "morph deep hz=$hzD" }

                // 7) كل الجاهزات تُطبَّق على صوت حقيقي بلا فجّ ولا تشبع
                val tone330 = sine40(330.0, 800)
                ps40.all.forEach { p ->
                    val out = fx40.applySpec(tone330.copyOf(), ps40.encode(p))
                    check(out.size >= tone330.size) { "preset ${p.id} length" }
                    out.forEach { v ->
                        check(Math.abs(v / 32768.0) <= 1.001) { "preset ${p.id} bounds" }
                    }
                }

                // 8) الارتداد المخزّن بمفتاح fx: التكرار يعيد الملف نفسه بلا حساب
                val m40 = File(tmp, "fx40").apply { mkdirs() }
                val tone40 = MontageDemo.tone(File(m40, "tone.wav"), 44100, 2, 1200, 440.0)
                val clip40 = MontageClip(id = "f", path = tone40.path, title = "مقطع", durationMs = 1200, startMs = 0, endMs = 1200)
                val cache40 = File(m40, "bounce")
                val echoSpec = fx40.encodeChain(listOf(
                    com.saidjte.schoolradio.desktop.audio.FxChainStep("echo", fx40.clampValues(def40("echo"), mapOf("delay" to 200.0, "feedback" to 40.0, "wet" to 40.0)))
                ))
                val bF1 = com.saidjte.schoolradio.desktop.audio.ClipTransforms.bounce(
                    clip40, "fx", echoSpec, { pcm -> fx40.applySpec(pcm, echoSpec) }, cache40
                )
                val bF2 = com.saidjte.schoolradio.desktop.audio.ClipTransforms.bounce(
                    clip40, "fx", echoSpec, { pcm -> fx40.applySpec(pcm, echoSpec) }, cache40
                )
                check(bF1.file.absolutePath == bF2.file.absolutePath && bF1.file.isFile) { "fx bounce reuse" }
                check(bF1.durationMs > 1200L) { "fx bounce tail dur=${bF1.durationMs}" }

                // 9) اختصار E يفتح المكتبة والتعديلات تحرسه
                fun r40(key: Key, c: Boolean = false) =
                    com.saidjte.schoolradio.desktop.ui.resolveMontageShortcut(key, c, false, false, false)
                check(r40(Key.E) == com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.OpenEffects) { "fx shortcut E" }
                check(r40(Key.E, c = true) == null) { "fx shortcut E guarded" }
            }

            // ================= v0.41.0: نظام المونتاج متعدد المسارات الاحترافي =================
            run {
                val ml41 = com.saidjte.schoolradio.desktop.data.MontageLogic

                // 1) الأنواع والهجرة الصامتة والتحكم في التداخل بحسب النوع
                check(ml41.trackType(com.saidjte.schoolradio.desktop.data.MontageTrack(id = "a", name = "x")) == ml41.TYPE_MUSIC) { "v41 type null->music" }
                check(ml41.trackType(com.saidjte.schoolradio.desktop.data.MontageTrack(id = "a", name = "x", type = "sfx")) == ml41.TYPE_SFX) { "v41 type sfx" }
                check(ml41.trackType(com.saidjte.schoolradio.desktop.data.MontageTrack(id = "a", name = "x", type = "dialogue")) == ml41.TYPE_DIALOGUE) { "v41 type dialogue" }
                check(ml41.trackType(com.saidjte.schoolradio.desktop.data.MontageTrack(id = "a", name = "x", type = "bad")) == ml41.TYPE_MUSIC) { "v41 type unknown->music" }
                check(ml41.ducksUnderVoice(com.saidjte.schoolradio.desktop.data.MontageTrack(id = "a", name = "x"))) { "v41 music ducks" }
                check(!ml41.ducksUnderVoice(com.saidjte.schoolradio.desktop.data.MontageTrack(id = "a", name = "x", type = ml41.TYPE_SFX))) { "v41 sfx no duck" }
                check(!ml41.ducksUnderVoice(com.saidjte.schoolradio.desktop.data.MontageTrack(id = "a", name = "x", type = ml41.TYPE_DIALOGUE))) { "v41 dialogue no duck" }
                val trio = ml41.effectiveTracks(com.saidjte.schoolradio.desktop.data.MontageProject())
                check(trio.size == 3 && trio[0].id == ml41.DEFAULT_TRACK_ID && trio[1].id == ml41.DEFAULT_SFX_ID && trio[2].id == ml41.DEFAULT_DIALOGUE_ID) { "v41 default trio" }
                check(ml41.MAX_TRACKS == 16 && ml41.MAX_GROUPS == 8 && ml41.MAX_AUTO_POINTS == 64) { "v41 caps" }

                // 2) عدد كبير من المسارات والترتيب المستقل
                var tracks41 = ml41.defaultTrackTrio()
                repeat(13) { tracks41 = tracks41 + ml41.newTrackOfType(tracks41, ml41.TYPE_SFX) }
                check(tracks41.size == 16) { "v41 sixteen tracks" }
                check(tracks41.filter { ml41.trackType(it) == ml41.TYPE_SFX }.size == 14) { "v41 sfx count" } // الثلاثي فيه مؤثر + 13
                check(tracks41.map { it.name }.distinct().size == tracks41.size) { "v41 unique names" }
                // عشرة ألوان فقط في اللوحة فالتكرار مشروع عند 16 مسارًا — المطلوب استنفادها كلها
                check(tracks41.map { it.colorIndex }.distinct().size == ml41.TRACK_COLORS.size) { "v41 colors used" }
                val moved41 = ml41.moveTrack(tracks41, tracks41[0].id, +1)!!
                check(moved41[0].id == tracks41[1].id && moved41[1].id == tracks41[0].id) { "v41 reorder" }
                check(ml41.moveTrack(tracks41, tracks41[0].id, -1) == null) { "v41 reorder bounds" }

                // 3) الكتم/المنفرد والمجموعات: عقد وحدة المزج الكامل
                val t41 = com.saidjte.schoolradio.desktop.data.MontageTrack(id = "t", name = "t", volumePercent = 80, groupId = "g")
                val g41 = com.saidjte.schoolradio.desktop.data.MontageGroup(id = "g", name = "أجواء", volumePercent = 50)
                check(Math.abs(ml41.trackAmp(t41, g41, false, false) - 0.4f) < 1e-6f) { "v41 group amp" }
                check(ml41.trackAmp(t41, g41.copy(muted = true), false, false) == 0f) { "v41 group mute" }
                check(ml41.trackAmp(t41, g41, false, true) == 0f) { "v41 other group solo silences" }
                check(ml41.trackAmp(t41, g41.copy(solo = true), false, true) == 0.4f) { "v41 member group solo audible" }
                check(ml41.trackAmp(t41.copy(muted = true), g41, false, false) == 0f) { "v41 track mute wins" }
                check(ml41.trackAmp(t41.copy(solo = true), g41, true, false) == 0.4f) { "v41 track solo audible" }

                // 4) التوازن خطي
                check(ml41.panGainLeft(0) == 1f && ml41.panGainRight(0) == 1f) { "v41 pan center" }
                check(ml41.panGainLeft(-100) == 1f && ml41.panGainRight(-100) == 0f) { "v41 pan left" }
                check(ml41.panGainLeft(100) == 0f && ml41.panGainRight(100) == 1f) { "v41 pan right" }
                check(ml41.clampPan(999) == 100 && ml41.clampPan(-999) == -100) { "v41 pan clamp" }

                // 5) الأتمتة: قبل وبين وبعد والحبوس والسقف
                val ap = listOf(
                    com.saidjte.schoolradio.desktop.data.MontageAutoPoint(id = "1", tMs = 0, percent = 100),
                    com.saidjte.schoolradio.desktop.data.MontageAutoPoint(id = "2", tMs = 1000, percent = 200),
                    com.saidjte.schoolradio.desktop.data.MontageAutoPoint(id = "3", tMs = 2000, percent = 50)
                )
                check(ml41.autoGainPercentAt(ap, 500) == 150) { "v41 auto mid" }
                check(ml41.autoGainPercentAt(ap, 1500) == 125) { "v41 auto mid2" }
                check(ml41.autoGainPercentAt(ap, 9999) == 50) { "v41 auto after last" }
                check(ml41.autoGainPercentAt(emptyList(), 5) == 100) { "v41 auto empty neutral" }
                val sane41 = ml41.sanitizeAutoPoints(listOf(
                    com.saidjte.schoolradio.desktop.data.MontageAutoPoint(id = "b", tMs = -5, percent = 500),
                    com.saidjte.schoolradio.desktop.data.MontageAutoPoint(id = "a", tMs = 10, percent = 80)
                ))
                // b حُبس زمنها إلى 0 فتصدر القائمة، وpercent الحدسى 200، وa تبقى 10/80
                check(sane41.map { it.id } == listOf("b", "a") && sane41[0].tMs == 0L && sane41[0].percent == 200 && sane41[1].percent == 80) { "v41 auto sanitize" }

                // 6) المزج الفعلي: مؤثرات Pan يسار بأتمتة تنتهي صفرًا + حوار Pan يمين
                val dir41 = File(tmp, "v41mix").apply { mkdirs() }
                val sfx41 = com.saidjte.schoolradio.desktop.audio.MontageDemo.tone(File(dir41, "s.wav"), 44_100, 2, 1_000, 880.0)
                val p41 = com.saidjte.schoolradio.desktop.data.MontageProject(
                    musicClips = listOf(
                        com.saidjte.schoolradio.desktop.data.MontageMusicClip(id = "s1", path = sfx41.path, title = "مؤثر", durationMs = 1_000, startMs = 0, trackId = "tf"),
                        com.saidjte.schoolradio.desktop.data.MontageMusicClip(id = "s2", path = sfx41.path, title = "حوار", durationMs = 1_000, startMs = 1_000, trackId = "td")
                    ),
                    tracks = listOf(
                        com.saidjte.schoolradio.desktop.data.MontageTrack(id = "tf", name = "مؤثرات", type = ml41.TYPE_SFX, pan = -100),
                        com.saidjte.schoolradio.desktop.data.MontageTrack(id = "td", name = "حوار", type = ml41.TYPE_DIALOGUE, pan = 100)
                    ),
                    automation = listOf(
                        com.saidjte.schoolradio.desktop.data.MontageAutomationLane(
                            trackId = "tf",
                            points = listOf(
                                com.saidjte.schoolradio.desktop.data.MontageAutoPoint(id = "1", tMs = 0, percent = 100),
                                com.saidjte.schoolradio.desktop.data.MontageAutoPoint(id = "2", tMs = 1000, percent = 0)
                            )
                        )
                    )
                )
                val out41 = File(dir41, "mix.wav")
                val r41 = com.saidjte.schoolradio.desktop.audio.MontageRender.render(p41, out41)
                check(r41.ok) { "v41 mix render: ${r41.message}" }
                check(Math.abs(r41.durationMs - 2_000L) <= 40L) { "v41 mix duration=${r41.durationMs}" }
                AudioSystem.getAudioInputStream(out41).use { ais41 ->
                    // بداية المؤثرات: Pan يسار فاليسرى تسود واليمنى صمت تقريبًا
                    val buf1 = ByteArray(400 * 4)
                    ais41.skip(100 * 4L)
                    var rd = 0
                    while (rd < buf1.size) { val n = ais41.read(buf1, rd, buf1.size - rd); if (n < 0) break; rd += n }
                    var lPeak = 0; var rPeak = 0
                    var i = 0
                    while (i + 1 < rd / 2) {
                        val v = Math.abs(((buf1[2 * i + 1].toInt() shl 8) or (buf1[2 * i].toInt() and 0xFF)).toShort().toInt())
                        if (i % 2 == 0) { if (v > lPeak) lPeak = v } else { if (v > rPeak) rPeak = v }
                        i++
                    }
                    check(lPeak > 6_000) { "v41 sfx left peak=$lPeak" }
                    check(rPeak < lPeak / 8) { "v41 sfx right muted by pan r=$rPeak l=$lPeak" }
                    // بعد 1600ms: الأتمتة بلغت 0٪ فالمؤثرات صامتة، والحوار Pan يمين مسموع
                    val buf2 = ByteArray(400 * 4)
                    ais41.skip(1400L * 44_100L / 1000L * 4L) // من 200ms إلى 1600ms
                    rd = 0
                    while (rd < buf2.size) { val n = ais41.read(buf2, rd, buf2.size - rd); if (n < 0) break; rd += n }
                    lPeak = 0; rPeak = 0
                    i = 0
                    while (i + 1 < rd / 2) {
                        val v = Math.abs(((buf2[2 * i + 1].toInt() shl 8) or (buf2[2 * i].toInt() and 0xFF)).toShort().toInt())
                        if (i % 2 == 0) { if (v > lPeak) lPeak = v } else { if (v > rPeak) rPeak = v }
                        i++
                    }
                    check(rPeak > 6_000 && lPeak < rPeak / 8) { "v41 dialogue right and automation killed sfx r=$rPeak l=$lPeak" }
                }

                // 7) هجرة JSON قديمة بلا الحقول الجديدة عبر Gson الحقيقي
                val legacy41 = """
                {"tracks":[{"id":"t1","name":"موسيقى","volumePercent":110}]}
                """.trimIndent()
                val lp41 = Json.gson.fromJson(legacy41, com.saidjte.schoolradio.desktop.data.MontageProject::class.java)
                val lt41 = ml41.effectiveTracks(lp41)
                check(lt41.size == 1 && ml41.trackType(lt41[0]) == ml41.TYPE_MUSIC && lt41[0].pan == 0) { "v41 legacy json migration" }
                check(ml41.groupsOf(lp41).isEmpty() && ml41.automationLanesOf(lp41).isEmpty()) { "v41 legacy null lists" }
            }

            // =================== v0.42.0: مكتبة الأصوات والموسيقى ===================
            run {
                val groups42 = com.saidjte.schoolradio.desktop.data.SoundGroups
                val bs42 = com.saidjte.schoolradio.desktop.audio.BundledSounds
                val slm42 = com.saidjte.schoolradio.desktop.data.SoundLibraryMath

                // 1) العقد: 36 صوتًا في خمس مجموعات بعددها الصحيح، وسوم كافية ومدد موجبة
                val counts42 = bs42.ALL.groupingBy { it.group }.eachCount()
                check(bs42.ALL.size == 36) { "v42 manifest size=${bs42.ALL.size}" }
                check(counts42[groups42.MUSIC] == 8 && counts42[groups42.SFX] == 8 &&
                        counts42[groups42.TRANSITIONS] == 6 && counts42[groups42.NATURE] == 8 &&
                        counts42[groups42.CINEMA] == 6) { "v42 group counts $counts42" }
                check(bs42.ALL.all { it.tags.size >= 3 && it.durationMs > 0 && it.title.isNotBlank() }) { "v42 identities" }
                check(bs42.ALL.map { it.id }.toSet().size == 36) { "v42 unique ids" }

                // 2) الموارد موجودة داخل الجرة فعلاً — لا بطاقة وهمية
                val loader42 = bs42::class.java.classLoader
                check(bs42.ALL.all { loader42.getResourceAsStream(bs42.resourcePath(it)) != null }) { "v42 resources in jar" }

                // 3) الاستخراج الذري الاستئثاري: 36 ملفًا فعلًا ثم استدعاء ثانٍ مطابق
                val sd42 = File(tmp, "v42sounds").apply { mkdirs() }
                val extracted42 = bs42.ensureExtracted(sd42)
                check(extracted42.size == 36 && extracted42.values.all { it.isFile && it.length() > 0 }) { "v42 extraction" }
                val sizes42 = extracted42.values.associate { it.name to it.length() }
                check(extracted42.values.all { (sizes42[it.name] ?: 0L) == it.length() }) { "v42 sizes sane" }
                check(bs42.ensureExtracted(sd42) == extracted42) { "v42 idempotent" }
                check(sd42.listFiles()?.none { it.name.endsWith(".tmp") } == true) { "v42 no tmp leftovers" }

                // 4) عينات من كل مجموعة تُفك MP3 فعلاً وتطابق المدة ±400ms
                listOf("morning_bright", "handbell", "whoosh_up", "birds_chirp", "epic_braam").forEach { id42 ->
                    val s42 = bs42.ALL.first { it.id == id42 }
                    val f42 = extracted42[id42] ?: throw AssertionError("v42 missing $id42")
                    val d42 = Mp3Info.analyze(f42)?.durationMs ?: -1L
                    check(Math.abs(d42 - s42.durationMs) <= 400L) { "v42 duration $id42 manifest=${s42.durationMs} real=$d42" }
                }

                // 5) البحث: بالعنوان والوصف والوسوم، والخاوي تطابق الكل
                check(slm42.matches("مطر خفيف", "وصف", listOf("طبيعة"), "مطر")) { "v42 search title" }
                check(slm42.matches("مطر", "مطر هادئ دائم", emptyList(), "هادئ")) { "v42 search description" }
                check(slm42.matches("مطر", "وصف", listOf("طبيعة", "مطر"), "طبيعة")) { "v42 search tag" }
                check(!slm42.matches("مطر", "وصف", listOf("طبيعة"), "تصفيق")) { "v42 search miss" }
                check(slm42.matches("أي شيء", "وصف", emptyList(), "")) { "v42 empty query" }

                // 6) التصنيف المقترح دائمًا موجود في الافتراضات — لا حوار إضافة فاشل
                check(slm42.suggestedCategory(groups42.MUSIC) == "فقرات متنوعة") { "v42 music suggestion" }
                check(groups42.BUNDLED.all { slm42.suggestedCategory(it) in com.saidjte.schoolradio.desktop.data.LibraryDefaults.CATEGORIES }) { "v42 suggestions exist" }

                // 7) نص المدة العربي
                check(slm42.durationText(700) == "0.7 ث" && slm42.durationText(122_000) == "2:02 د") { "v42 duration text" }

                // 8) مكتبتي الخاصة: استيراد بتجاوز معلن وتكرار مكبوح ودورة حفظ/قراءة حقيقية
                val cd42 = File(tmp, "v42custom").apply { mkdirs() }
                val w42a = File(cd42, "mine_a.wav").apply { writeBytes(ByteArray(96)) }
                val w42b = File(cd42, "mine_b.mp3").apply { writeBytes(ByteArray(96)) }
                val junk42 = File(cd42, "skip.txt").apply { writeBytes(ByteArray(8)) }
                val st42 = com.saidjte.schoolradio.desktop.data.SoundLibraryStore(File(cd42, "soundlib.json"))
                val imp42 = st42.addFiles(listOf(w42a, junk42, w42b))
                check(imp42.added.size == 2 && imp42.skippedUnsupported == 1) { "v42 import ${imp42.added.size}/${imp42.skippedUnsupported}" }
                check(st42.addFiles(listOf(w42a)).added.isEmpty()) { "v42 dedupe" }
                check(com.saidjte.schoolradio.desktop.data.SoundLibraryStore(File(cd42, "soundlib.json")).custom().size == 2) { "v42 persisted" }
                val corrupt42 = File(cd42, "corrupt.json").apply { writeText("{ تالف ") }
                check(com.saidjte.schoolradio.desktop.data.SoundLibraryStore(corrupt42).custom().isEmpty()) { "v42 corrupt tolerated" }

                // 9) التراخيص: صف المولد + صفوف الجينلات السبعة متطابقة العناوين، والتجاري قاطع
                val lic42 = com.saidjte.schoolradio.desktop.data.SoundLicense
                check(lic42.rows().size == 8) { "v42 license rows=${lic42.rows().size}" }
                check(lic42.rows().drop(1).map { it.item } == com.saidjte.schoolradio.desktop.audio.BundledJingles.ALL.map { it.title }) { "v42 jingle license parity" }
                check(lic42.BUNDLED_ROW.commercial.contains("مسموح") && lic42.commercialSummary().contains("CC0")) { "v42 commercial explicit" }
                check(lic42.BUNDLED_ROW.source.contains("داخل المشروع")) { "v42 local generation declared" }

                // 10) محرك المعاينة: صوت واحد، النقر الثاني يوقف، والتبديل يوقف السابق
                val pe42 = com.saidjte.schoolradio.desktop.audio.SoundPreviewEngine()
                pe42.voiceFactoryOverride = { com.saidjte.schoolradio.desktop.audio.FakePreviewVoice() }
                val pf42a = File("/tmp/v42-a.mp3")
                val pf42b = File("/tmp/v42-b.mp3")
                pe42.toggle(pf42a)
                check(pe42.playingFile.value == pf42a.absolutePath) { "v42 preview starts" }
                pe42.toggle(pf42a)
                check(pe42.playingFile.value == null) { "v42 preview stops" }
                pe42.toggle(pf42b)
                check(pe42.playingFile.value == pf42b.absolutePath) { "v42 preview switches" }
                pe42.shutdown()
                check(pe42.playingFile.value == null) { "v42 preview shutdown" }
            }

            // ================= v0.43.0: استوديو التسجيل الاحترافي =================
            run {

                // 1) الجودة: 16/24/32 بت × 44.1/48/96/192 — حبس بلا استثناء
                check(RecordingStudio.ALLOWED_BITS == listOf(16, 24, 32)) { "v43 bits" }
                check(RecordingStudio.ALLOWED_RATES == listOf(44_100, 48_000, 96_000, 192_000)) { "v43 rates" }
                val q43 = RecordingStudio.Quality(24, 96_000).clamp()
                check(q43.bits == 24 && q43.rateHz == 96_000 && q43.bytesPerSample == 3) { "v43 quality id" }
                val cq = RecordingStudio.Quality(18, 50_000).clamp()
                check(cq.bits == 16 && cq.rateHz == 48_000) { "v43 clamp ${cq.bits}/${cq.rateHz}" }
                check(RecordingStudio.Quality(24, 44_100).labelAr().contains("44.1")) { "v43 label" }

                // 2) تفاوض الالتقاط: مطابقة تامة ثم أقرب بت ثم أقرب معدل أدنى
                val target43 = RecordingStudio.Quality(24, 96_000)
                val capExact = RecordingStudio.pickCapture(target43, listOf(RecordingStudio.CaptureFormat(44_100, 16, 1), RecordingStudio.CaptureFormat(96_000, 24, 1)))
                check(capExact.rateHz == 96_000 && capExact.bits == 24) { "v43 capture exact" }
                val capSame = RecordingStudio.pickCapture(target43, listOf(RecordingStudio.CaptureFormat(96_000, 16, 1), RecordingStudio.CaptureFormat(96_000, 32, 1)))
                check(capSame.bits == 32) { "v43 capture same-rate tie prefers higher" }
                val capNear = RecordingStudio.pickCapture(target43, listOf(RecordingStudio.CaptureFormat(192_000, 16, 1), RecordingStudio.CaptureFormat(48_000, 16, 1)))
                check(capNear.rateHz == 48_000) { "v43 capture nearest below" }
                val capEmpty = RecordingStudio.pickCapture(target43, emptyList())
                check(capEmpty.rateHz == 96_000 && capEmpty.bits == 16) { "v43 capture empty fallback" }
                check(RecordingStudio.conversionLabel(RecordingStudio.CaptureFormat(44_100, 16, 1), target43) != null) { "v43 conversion label" }
                check(RecordingStudio.conversionLabel(RecordingStudio.CaptureFormat(96_000, 24, 1), target43) == null) { "v43 conversion none" }

                // 3) ترميز 24 بت: دورة كاملة وقصّ قبل الالتفاف
                val src24 = floatArrayOf(0f, 0.25f, -0.5f, 1f, -1f)
                val b24 = ByteArray(src24.size * 3)
                RecordingStudio.PcmWide.floatsToBytes24(src24, src24.size, b24)
                val out24 = FloatArray(src24.size)
                check(RecordingStudio.PcmWide.bytes24ToFloats(b24, b24.size, out24) == src24.size) { "v43 pcm24 count" }
                check(kotlin.math.abs(out24[4] - (-1f)) < 0.0001f && kotlin.math.abs(out24[1] - 0.25f) < 0.0001f) { "v43 pcm24 values" }

                // 4) المقياس والتشبع: ذروة كاملة تشبع، والقفل يعدّ الأحداث لا الإطارات
                val full43 = FloatArray(100) { if (it % 2 == 0) 1f else -1f }
                check(RecordingStudio.analyzeFrame(full43, 100).clipped) { "v43 clip flag" }
                val latch43 = RecordingStudio.ClipLatch(holdMs = 800)
                latch43.onFrame(true, 100)
                latch43.onFrame(true, 300)
                check(latch43.count == 1 && latch43.active(700)) { "v43 latch hold" }
                check(!latch43.active(1500)) { "v43 latch release" }
                latch43.onFrame(true, 1600)
                check(latch43.count == 2) { "v43 latch new event" }

                // 5) آلة الحالات: الدورة الكاملة والانتقالات المخالفة تُردّ
                val sm43 = RecordingStudio.StateMachine()
                check(sm43.start() && !sm43.start() && !sm43.resume()) { "v43 sm start phase" }
                check(sm43.pause() && sm43.resume() && sm43.pause() && sm43.stop()) { "v43 sm lifecycle" }
                check(sm43.state == RecordingStudio.RecState.IDLE && !sm43.stop()) { "v43 sm idle" }

                // 6) ملف WAV بعمق 24 بت قانوني ومقروء بالترويسته
                val wav24 = File(tmp, "v43-rec.wav")
                val frames43 = 9600
                val data24 = ByteArray(frames43 * 3)
                val s24 = FloatArray(frames43) { (0.3 * kotlin.math.sin(2 * Math.PI * it * 880.0 / 48000.0)).toFloat() }
                com.saidjte.schoolradio.desktop.audio.WavFileWriter(wav24, 48_000, 1, 24).use {
                    RecordingStudio.PcmWide.floatsToBytes24(s24, frames43, data24)
                    it.write(data24)
                }
                val h24 = com.saidjte.schoolradio.desktop.audio.WavHeader.read(wav24)
                check(h24 != null && h24.bitsPerSample == 24 && h24.sampleRate == 48_000 && h24.channels == 1) { "v43 wav24 header" }
                check(com.saidjte.schoolradio.desktop.audio.WavFileWriter.durationMsOf(h24!!.dataBytes, h24.sampleRate, h24.channels, h24.bitsPerSample) == 200L) { "v43 wav24 duration" }

                // 7) الجدولة: مستحق/بانتظار/فائت/منفّذ/معطّل مع نافذة اليدوي دقيقتين
                val zone = java.time.ZoneId.systemDefault()
                fun at43(h: Int, m: Int = 0): Long =
                    java.time.LocalDate.now().atTime(h, m).atZone(zone).toInstant().toEpochMilli()
                val sched = RecordingStudio.ScheduledItem(id = "a", title = "فاصل", startMinutes = 8 * 60 + 30, durationMinutes = 5, repeat = RecordingStudio.REPEAT_DAILY)
                check(RecordingStudio.decide(sched, at43(8, 0)).status == RecordingStudio.SchedStatus.WAITING) { "v43 sched waiting" }
                check(RecordingStudio.decide(sched, at43(8, 34)).status == RecordingStudio.SchedStatus.DUE) { "v43 sched due" }
                check(RecordingStudio.decide(sched, at43(8, 40)).status == RecordingStudio.SchedStatus.MISSED) { "v43 sched missed" }
                check(RecordingStudio.decide(sched.copy(lastFiredDateKey = RecordingStudio.dateKeyOf(at43(9)) ), at43(9)).status == RecordingStudio.SchedStatus.FIRED_TODAY) { "v43 sched fired" }
                check(RecordingStudio.decide(sched.copy(enabled = false), at43(8, 31)).status == RecordingStudio.SchedStatus.DISABLED) { "v43 sched disabled" }
                val manual43 = sched.copy(durationMinutes = null)
                check(RecordingStudio.decide(manual43, at43(8, 31)).status == RecordingStudio.SchedStatus.DUE && RecordingStudio.decide(manual43, at43(8, 34)).status == RecordingStudio.SchedStatus.MISSED) { "v43 manual window" }
                val weekly43 = sched.copy(repeat = RecordingStudio.REPEAT_WEEKLY, weeklyDays = listOf(java.time.LocalDate.now().atStartOfDay(zone).dayOfWeek.value), startMinutes = 9 * 60 + 55)
                check(RecordingStudio.decide(weekly43, at43(9, 57)).status == RecordingStudio.SchedStatus.DUE) { "v43 weekly iso day" }
                check(RecordingStudio.decide(weekly43.copy(weeklyDays = listOf((weekly43.weeklyDays.first() % 7) + 1)), at43(9, 57)).status == RecordingStudio.SchedStatus.WAITING) { "v43 weekly other day" }
                val onceDone = sched.copy(repeat = RecordingStudio.REPEAT_ONCE, lastFiredDateKey = "2026-01-01")
                check(RecordingStudio.decide(onceDone, at43(8, 31)).status == RecordingStudio.SchedStatus.FIRED_TODAY) { "v43 once never again" }
                val san43 = RecordingStudio.sanitize(sched.copy(startMinutes = 2000, durationMinutes = 9999, repeat = "bogus", weeklyDays = listOf(9, 1, 1, 3)))
                check(san43.startMinutes == 1439 && san43.durationMinutes == 600 && san43.repeat == RecordingStudio.REPEAT_ONCE && san43.weeklyDays.isEmpty()) { "v43 sanitize" }

                // 8) الإعدادات: دورة حفظ/قراءة كاملة + مفتاح آخر تنفيذ
                val st43 = com.saidjte.schoolradio.desktop.data.SettingsStore(File(tmp, "v43-settings.json"))
                st43.setRecordingStudio(
                    com.saidjte.schoolradio.desktop.data.RecordingJson(
                        bits = 32, rateHz = 96_000, monitor = true,
                        deviceNames = listOf("ميكروفون خارجي"),
                        schedules = listOf(com.saidjte.schoolradio.desktop.data.SchedRecJson(id = "s1", title = "صباحي", startMinutes = 500, durationMinutes = 10, repeat = "daily"))
                    )
                )
                val rt43 = com.saidjte.schoolradio.desktop.data.SettingsStore(File(tmp, "v43-settings.json"))
                val cfg43 = rt43.recordingStudio()
                check(cfg43.bits == 32 && cfg43.rateHz == 96_000 && cfg43.monitor == true) { "v43 settings quality" }
                check(rt43.recordingSchedules().size == 1 && rt43.recordingSchedules()[0].durationMinutes == 10) { "v43 settings schedules" }
                rt43.setScheduleFired("s1", "2026-11-11")
                check(com.saidjte.schoolradio.desktop.data.SettingsStore(File(tmp, "v43-settings.json")).recordingSchedules()[0].lastFiredDateKey == "2026-11-11") { "v43 fired mark" }

                // 9) الفوقية: ضمان المسار (حوار أولًا) والمقطع بنافذته الكاملة
                val (p43, tid43) = com.saidjte.schoolradio.desktop.data.MontageLogic.ensureOverdubTrack(com.saidjte.schoolradio.desktop.data.MontageProject())
                check(p43.tracks.size == 1 && tid43 == p43.tracks.first().id &&
                        p43.tracks.first().type == com.saidjte.schoolradio.desktop.data.MontageLogic.TYPE_DIALOGUE &&
                        p43.tracks.first().name == com.saidjte.schoolradio.desktop.data.MontageLogic.OVERDUB_TRACK_NAME) { "v43 overdub track empty" }
                val pWith = com.saidjte.schoolradio.desktop.data.MontageProject(
                    tracks = listOf(
                        com.saidjte.schoolradio.desktop.data.MontageTrack(id = "music", name = "م", type = com.saidjte.schoolradio.desktop.data.MontageLogic.TYPE_MUSIC),
                        com.saidjte.schoolradio.desktop.data.MontageTrack(id = "dlg", name = "ح", type = com.saidjte.schoolradio.desktop.data.MontageLogic.TYPE_DIALOGUE)
                    )
                )
                val (p43b, tid43b) = com.saidjte.schoolradio.desktop.data.MontageLogic.ensureOverdubTrack(pWith)
                check(tid43b == "dlg" && p43b.tracks.size == 2) { "v43 overdub dialogue preferred" }
                val clip43 = com.saidjte.schoolradio.desktop.data.MontageLogic.overdubClip("/tmp/r.wav", " تعليق ", 7_500L, 12_000L, tid43)
                check(clip43.startMs == 12_000L && clip43.durationMs == 7_500L && clip43.startInFileMs == 0L && clip43.endInFileMs == 7_500L) { "v43 overdub clip window" }

                // 10) بوابة الجلسة الخاوية على المحرك: لا بدء بلا أجهزة والإنهاء آمن
                val eng43 = com.saidjte.schoolradio.desktop.audio.RecordingEngine()
                check(!eng43.start(emptyList(), RecordingStudio.Quality(16, 48_000), tmp, false)) { "v43 engine no devices" }
                check(eng43.state.value == RecordingStudio.RecState.IDLE && eng43.stop().isEmpty() && !eng43.pause() && !eng43.resume()) { "v43 engine guards" }
            }

            // ================= v0.44.0: استيراد اليوتيوب — سلسلة القنوات والمعاينة وفصل التبويبين =================
            run {
                // 1) سلسلة القنوات: ثماني محاولات والافتراضية أولًا بلا وسائط قناة
                check(YouTubeClientChain.size == 8 && YouTubeClientChain.CHAIN.first().client == null) { "v44 chain size/first" }
                check(YouTubeClientChain.extractorArgs(YouTubeClientChain.CHAIN.first()).isEmpty()) { "v44 default no args" }
                check(YouTubeClientChain.extractorArgs(YouTubeClientChain.attempt(1)) ==
                        listOf("--extractor-args", "youtube:player_client=tv")) { "v44 tv args" }
                check(YouTubeClientChain.extractorArgs(YouTubeClientChain.attempt(3)) ==
                        listOf("--extractor-args", "youtube:player_client=android_vr")) { "v44 vr args" }
                check(YouTubeClientChain.attempt(99) == YouTubeClientChain.CHAIN.last()) { "v44 clamp" }
                check(YouTubeClientChain.CHAIN.map { it.client }.toSet().size == YouTubeClientChain.size) { "v44 unique clients" }
                check(YouTubeClientChain.CHAIN.all { it.label.isNotBlank() }) { "v44 labels" }

                // 2) التصنيف: تحقق البوت/لا صيغة/صفحة تُعاد على قناة تالية، والحاسم يتوقف
                check(YouTubeClientChain.retryableError("ERROR: Sign in to confirm you're not a bot")) { "v44 retry bot" }
                check(YouTubeClientChain.retryableError("ERROR: Requested format is not available")) { "v44 retry format" }
                check(YouTubeClientChain.retryableError("ERROR: unable to download webpage")) { "v44 retry webpage" }
                check(!YouTubeClientChain.retryableError("ERROR: [youtube] x: Video unavailable")) { "v44 stop unavailable" }
                check(!YouTubeClientChain.retryableError("ERROR: Unsupported URL")) { "v44 stop unsupported" }
                check(!YouTubeClientChain.retryableError("ERROR: [Errno 13] Permission denied")) { "v44 stop permission" }
                check(YouTubeClientChain.botCheck("sign in to confirm") && !YouTubeClientChain.botCheck("video unavailable")) { "v44 botcheck" }

                // 3) رسائل التقدم تحمل رقم المحاولة والقناة العربية
                val pm0 = YouTubeClientChain.progressMessage(0, "جلب المعلومات من يوتيوب")
                val pm2 = YouTubeClientChain.progressMessage(2, "تحميل الصوت")
                check(pm0.contains("1 من 8") && !pm0.contains("عبر")) { "v44 progress default" }
                check(pm2.contains("3 من 8") && pm2.contains("التلفاز المبسطة")) { "v44 progress channel" }

                // 4) فصل التبويبين: شبّاه الرابط يعرف الروابط وينحّي كلمات البحث
                check(YouTubeUrl.looksLikeUrl("https://youtu.be/abc-_12345X")) { "v44 url https" }
                check(YouTubeUrl.looksLikeUrl("WWW.YOUTUBE.COM/watch?v=x")) { "v44 url www" }
                check(YouTubeUrl.looksLikeUrl("youtube.com/watch?v=x")) { "v44 url bare host" }
                check(YouTubeUrl.looksLikeUrl("m.youtube.com/shorts/abc")) { "v44 url mobile" }
                check(!YouTubeUrl.looksLikeUrl("نشيد العلم الوطني")) { "v44 search words" }
                check(!YouTubeUrl.looksLikeUrl("مدرسة.النور")) { "v44 search dotted" }
                check(!YouTubeUrl.looksLikeUrl("")) { "v44 search empty" }
                check(YouTubeUrl.containsUrlSignal("انظر https://youtu.be/abc-_12345X للتفاصيل")) { "v44 signal embedded" }
                check(!YouTubeUrl.containsUrlSignal("نشيد العلم الوطني")) { "v44 signal words" }

                // 5) رسالة الجدار الكلي: كوكيز وشبكة أخرى — بلا تعليمات خارجية
                val wall44 = YouTubeJson.describeError("Sign in to confirm you're not a bot")
                check(wall44 != null && wall44.contains("القنوات") && wall44.contains("كوكيز")) { "v44 wall message" }

                // 6) دورة المعاينة: الخدمة الخاوية بلا معلَّق والتجاهل آمنة ومتكررة
                val svc44 = YouTubeImportService(YtDlpLocator(tmp), File(tmp, "v44-yt"))
                check(svc44.pendingDownload() == null && !svc44.pendingUsableFor("abc-_12345X")) { "v44 pending empty" }
                svc44.discardPreview()
                svc44.discardPreview()
                check(svc44.state.value.phase == YouTubeImportService.Phase.IDLE) { "v44 discard idle" }

                // 7) قرار التسليم من المعلَّق منطق نقي مُختبر على ملفات حقيقية
                val pendDir = File(tmp, "v44-yt/tmp/preview").apply { mkdirs() }
                val ready = File(pendDir, "ready.mp3").apply { writeBytes(ByteArray(4_096)) }
                fun usable(file: File, vid: String, want: String) =
                    file.isFile && file.length() > 1_000L && vid == want
                check(usable(ready, "abc-_12345X", "abc-_12345X")) { "v44 usable same id" }
                check(!usable(ready, "abc-_12345X", "other1234567")) { "v44 usable other id" }
                val small = File(pendDir, "small.mp3").apply { writeBytes(ByteArray(100)) }
                check(!usable(small, "abc-_12345X", "abc-_12345X")) { "v44 usable too small" }
                val gone = File(pendDir, "gone.mp3")
                check(!usable(gone, "abc-_12345X", "abc-_12345X")) { "v44 usable missing file" }
            }

            // ================= v0.45.0: خلط المزيج الحقيقي وشارة الإعلان ونغمته واختيار السكربت =================
            run {
                // 1) الإشباع الناعم: تعريف داخل الركبة وانحناء فوقها بلا موجة مربعة
                check(MixMath.saturate(0.5f) == 0.5f) { "v45 sat identity" }
                check(MixMath.saturate(-0.5f) == -0.5f) { "v45 sat identity neg" }
                val s = MixMath.saturate(1.5f)
                check(s > MixMath.KNEE && s < 1.0f) { "v45 sat bent=$s" }
                check(MixMath.saturate(1.5f) > MixMath.saturate(1.0f)) { "v45 sat monotone" }
                check(MixMath.saturate(-1.5f) == -s) { "v45 sat symmetry" }

                // 2) خلط مسارين متزامنين: المدة أقصى المدد والذروة مجموعهما —
                //    درس «أي تشغيل متوازٍ يشوّش التسجيل»
                val mixF = File(tmp, "v45-mix.wav")
                check(MixBus.start(mixF)) { "v45 mix start" }
                val music = ShortArray(22_050 * 2) { (0.4f * 32767).toInt().toShort() }
                MixBus.feedMusic(music, music.size, 44_100, 2)
                val micBytes = ByteArray(22_050 * 2) { i ->
                    if (i % 2 == 0) (0.4f * 32767).toInt().toByte() else ((0.4f * 32767).toInt() shr 8).toByte()
                }
                MixBus.feedMic(micBytes, 0, micBytes.size, 44_100, 1)
                val mixDur = MixBus.stop()
                check(mixDur > 0L) { "v45 mix duration=$mixDur" }
                val mixInfo = WavHeader.read(mixF)
                check(mixInfo != null && mixInfo.sampleRate == 44_100 && mixInfo.channels == 2) { "v45 mix header" }
                // المدة قريبة من نصف ثانية (أقصى المدد) لا ثانية كاملة (مجموعها)
                val mixFrames = mixInfo!!.dataBytes / 4
                check(mixFrames in 18_000..30_000) { "v45 mix frames=$mixFrames expect ~22050" }
                // الذروة ≈ 0.8 (جمع متزامن) — التناوب القديم كان يتركها 0.4
                val bytes = mixF.readBytes()
                val dataOff = (bytes.size - mixInfo.dataBytes).toInt()
                var peak = 0
                for (i in 0 until (mixInfo.dataBytes / 2).toInt()) {
                    val v = (((bytes[dataOff + i * 2 + 1].toInt()) shl 8) or (bytes[dataOff + i * 2].toInt() and 0xFF)).toShort().toInt()
                    val a = maxOf(v, -v)
                    if (a > peak) peak = a
                }
                check(peak in (0.75f * 32767).toInt()..(0.83f * 32767).toInt()) { "v45 mix peak=$peak expect sum ~0.8" }
                check(MixBus.stop() == 0L) { "v45 mix double stop" }

                // 3) نغمة الإعلان: تعقيم الاختيار مع الافتراضي
                val ids = BundledJingles.ALL.map { it.id }
                check(LiveAnnounceLogic.resolveJingleId(null, ids) == LiveAnnounceLogic.JINGLE_ID) { "v45 tone null" }
                check(LiveAnnounceLogic.resolveJingleId("ghost_tone", ids) == LiveAnnounceLogic.JINGLE_ID) { "v45 tone unknown" }
                check(LiveAnnounceLogic.resolveJingleId(" school_bell ", ids) == "school_bell") { "v45 tone trim" }

                // 4) شارة الإعلان تُضيء الهواء في المنطقين
                check(!StudioLogic.status(false, false, false).onAir) { "v45 badge off baseline" }
                check(StudioLogic.status(false, false, false, announceActive = true).onAir) { "v45 badge lights studio" }
                check(
                    OnAirDisplayLogic.buildModel(
                        false, -1, 0L, null, false, emptyList(), emptyList(),
                        null, 0L, 0L, false, false, false, announceActive = true
                    ).onAir
                ) { "v45 badge lights display" }

                // 5) تسمية منتقي السكربت اليدوي
                check(StudioLogic.pickerLabel(0, "المقدمة") == "1. المقدمة") { "v45 picker label" }
                check(StudioLogic.pickerLabel(1, "") == "2. بدون عنوان") { "v45 picker empty" }
                val pl = StudioLogic.pickerLabel(0, "فترة طويلة جدًا في الإذاعة المدرسية تحتاج قصًّا محكمًا هنا")
                check(pl.endsWith("…") && pl.length <= 42) { "v45 picker truncate=$pl" }

                // ================= v0.46.0: إنهاء البث + نغمة من ملف + لوحة الهواء =================

                // 1) إنهاء البث: الوجه المغلق — الجلسة تضيء الهواء والإنهاء يطفئها
                check(!StudioLogic.status(false, false, false).broadcastLive) { "v46 session default off" }
                check(StudioLogic.status(false, false, false, broadcastLive = true).onAir) { "v46 session lights onAir" }
                check(!StudioLogic.status(false, false, false, broadcastLive = false).onAir) { "v46 end turns badge off" }
                check(StudioLogic.status(micLive = true, recording = false, musicPlaying = false, broadcastLive = false).onAir) { "v46 live mic keeps onAir after end" }
                check(LiveAnnounceLogic.endMessage(false) == "تم إنهاء البث المباشر") { "v46 end message clean" }
                check(LiveAnnounceLogic.endMessage(true).contains("ما زالت تعمل")) { "v46 end message honest" }

                // 2) نغمة الإعلان من ملف المستخدم: الحسم لحظة النقر
                val src = LiveAnnounceLogic.resolveAnnounceSource(
                    "/nonexistent/tone.mp3", "magic_sweep", ids
                ) { false }
                check(src == LiveAnnounceLogic.AnnounceSource.BuiltIn("magic_sweep")) { "v46 missing custom falls back" }
                val src2 = LiveAnnounceLogic.resolveAnnounceSource(
                    " /x/nashid.mp3 ", "school_bell", ids
                ) { true }
                check(src2 == LiveAnnounceLogic.AnnounceSource.CustomFile("/x/nashid.mp3")) { "v46 existing custom wins (trimmed)" }
                check(LiveAnnounceLogic.customLabel("C:\\m\\طويلة جدا جدا جدا جدا في الاسم.mp3").endsWith("…")) { "v46 custom label cut" }

                // 3) الإعدادات: مسار النغمة المخصصة يجتمع مع معرف المدمجة في وثيقة واحدة
                val st = File(tmp, "settings-v46.json")
                val store = SettingsStore(st)
                store.setLiveAnnounceCustomPath("/x/nashid.mp3")
                store.setLiveAnnounceJingleId("school_bell")
                val re = SettingsStore(st)
                check(re.liveAnnounceCustomPath() == "/x/nashid.mp3") { "v46 custom path round trip" }
                check(re.liveAnnounceJingleId() == "school_bell") { "v46 jingle id survives alongside" }
                re.setLiveAnnounceCustomPath(null)
                check(SettingsStore(st).liveAnnounceCustomPath() == null) { "v46 clear persists" }

                // 4) لوحة شاشة على الهواء: عقد التباين المقيس كله صائم
                check(GuiTokens.validateOnAirContrast()) { "v46 on-air contrast contract" }
                check(GuiTokens.validateThemeLayering()) { "v46 theme layering intact" }

                // 5) شاشة الطلاب: الجلسة المعلنة تضيء النموذج والإنهاء يعيده مطفأً
                check(
                    OnAirDisplayLogic.buildModel(
                        false, -1, 0L, null, false, emptyList(), emptyList(),
                        null, 0L, 0L, false, false, false, broadcastLive = true
                    ).onAir
                ) { "v46 display lights with session" }
            }

            println("SELFTEST OK — version $APP_VERSION")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }
}
