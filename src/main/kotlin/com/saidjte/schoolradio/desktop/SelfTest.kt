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

            println("SELFTEST OK — version $APP_VERSION")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }
}
