package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.Mp3Probe
import com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS
import com.saidjte.schoolradio.desktop.audio.SUPPORTED_FORMATS_HINT
import com.saidjte.schoolradio.desktop.audio.openAudioInputStream
import com.saidjte.schoolradio.desktop.audio.StreamProbe
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * عقد الصيغ المدعومة (v0.49.0 — طلب «زيادة الصيغ المدعومة في القارئ لأنه
 * لا يتمكن من تشغيل العديد من الملفات بما فيها بعض ملفات mp3»): عينات ffmpeg
 * حقيقية مثبتة في الموارد تشمل حالات الإخفاق السابقة — MP3 بأغلفة ID3v2 ضخمة
 * (كان يسقط خارج نافذة البحث فيُرفض ملفًا سليمًا)، MPEG-2.5 بمعدل 8kHz،
 * VBR بجدول Xing، MPEG Layer 2، AAC داخل MP4 (M4A) وADTS — مع عينات OGG/FLAC/
 * WAV أساسًا، وصيغ غير مدعومة بعد (OPUS/WMA) برسالة صادقة. وفق مرجع VLC
 * (wiki.videolan.org/VLC_Features_Formats) نوسّع بما يُفك صراحة في جافا.
 */
class FormatsV49Test {

    private val tmp = Files.createTempDirectory("formats49").toFile()

    /** استخراج عينة من موارد الاختبار إلى ملف حقيقي (المحلل يحتاج RandomAccessFile). */
    private fun fixture(name: String): File {
        val out = File(tmp, name)
        javaClass.getResourceAsStream("/formats/$name")!!.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        }
        return out
    }

    private fun probeFile(name: String): File {
        val f = fixture(name)
        val raw = openAudioInputStream(f)
        val rf = raw.format
        // مسار PcmTrackReader نفسه: تحويل إلى PCM 16-bit وقراءة أجزاء حية
        val pcm = AudioSystem.getAudioInputStream(
            AudioFormat(rf.sampleRate, 16, rf.channels, true, false), raw
        )
        val buf = ByteArray(8192)
        var total = 0
        for (i in 0 until 8) {
            val n = pcm.read(buf)
            if (n < 0) break
            total += n
        }
        assertTrue(total > 0, "$name: لا يُفك إلى PCM إطلاقًا")
        return f
    }

    // ================= MP3: أساس وعيوب الوضع القديم =================

    @Test
    fun `mp3 cbr 44k baseline analyze`() {
        val i = Mp3Info.analyze(fixture("mp3_cbr44100.mp3"))
        assertNotNull(i, "MP3 CBR سليم يجب أن يُحلل")
        assertTrue(i.durationMs in 2700..3300, "مدة ≈3ث: ${i.durationMs}")
        assertEquals(44100, i.first.sampleRate)
        assertFalse(i.isVbr)
    }

    @Test
    fun `mp3 with big album-cover ID3v2 tag now plays (was failing)`() {
        val f = fixture("mp3_big_cover.mp3")
        assertTrue(f.length() > 700_000, "العينة تحمل غلافًا ضخمًا: ${f.length()}")
        val i = Mp3Info.analyze(f)
        assertNotNull(i, "MP3 بأغلفة كبيرة كان يفشل التحليل — يجب أن يُحلل الآن")
        assertTrue(i.durationMs in 2700..3300, "مدة ≈3ث: ${i.durationMs}")
        assertTrue(i.dataOffset > 300_000, "أول إطار بعد الوسم الضخم: ${i.dataOffset}")
        // والقارئ نفسه يستطيع فك أول إطار منها (مسار السقوط المحكم)
        val p = Mp3Probe.decodeFirstFrame(f)
        assertNotNull(p, "فك أول إطار من ملف الغلاف الضخم")
        assertEquals(44100, p.sampleRate)
    }

    @Test
    fun `mp3 mpeg2-5 8khz decodes (low-rate recordings)`() {
        val f = fixture("mp3_mpeg25_8k.mp3")
        val i = Mp3Info.analyze(f)
        assertNotNull(i)
        assertEquals(25, i.first.version, "يجب أن تُعرَّف الترويسة MPEG-2.5")
        assertEquals(8000, i.first.sampleRate)
        val p = Mp3Probe.decodeFirstFrame(f)
        assertNotNull(p, "JLayer يفك MPEG-2.5")
        assertEquals(8000, p.sampleRate)
        assertEquals(1, p.channels)
        assertEquals(576, p.samplesPerFrame)
    }

    @Test
    fun `mp3 vbr with xing toc`() {
        val i = Mp3Info.analyze(fixture("mp3_vbr_xing.mp3"))
        assertNotNull(i)
        assertTrue(i.isVbr, "VBR -q:a يجب أن يُكتشف بوسم Xing")
        assertNotNull(i.toc, "جدول السعي المئوي موجود")
        assertTrue(i.durationMs in 2700..3300, "المدة من عدّ الوسم: ${i.durationMs}")
    }

    @Test
    fun `mp2 layer-2 file plays via mp3 family`() {
        val f = fixture("layer2.mp2")
        val i = Mp3Info.analyze(f)
        assertNotNull(i, "MPEG Layer 2 ترويسته تُحلل")
        assertEquals(2, i.first.layer)
        val p = Mp3Probe.decodeFirstFrame(f)
        assertNotNull(p, "JLayer يفك Layer 2")
        assertEquals(44100, p.sampleRate)
    }

    @Test
    fun `renamed garbage mp3 fails honestly with supported list`() {
        // ملف WMA بإسم mp3: فك أول إطار يفشل → رسالة صادقة تسمّي المدعوم
        val f = fixture("old.wma")
        val renamed = File(tmp, "renamed_garbage.mp3")
        f.copyTo(renamed, overwrite = true)
        val p = Mp3Probe.decodeFirstFrame(renamed)
        assertEquals(null, p, "لا يجب فك WMA مُعاد تسميته كـMP3")
        assertTrue(SUPPORTED_FORMATS_HINT.contains("MPEG"))
    }

    // ================= AAC/M4A: قارئ jaad الصريح =================

    @Test
    fun `m4a iphone-style opens and decodes to pcm`() {
        val f = probeFile("apple.m4a")
        val d = StreamProbe.durationMsOf(f)
        assertTrue(d in 2500..3500, "مدة M4A من المجرى: $d")
    }

    @Test
    fun `adts aac raw stream opens and decodes`() {
        val f = probeFile("raw.adts.aac")
        val d = StreamProbe.durationMsOf(f)
        assertTrue(d >= 0L, "ADTS: المدة تُفحص بلا استثناء")
    }

    // ================= الأساس: OGG/FLAC/WAV + غير المدعوم =================

    @Test
    fun `ogg and flac still decode`() {
        assertTrue(StreamProbe.durationMsOf(fixture("vorbis.ogg")) in 2500..3500)
        assertTrue(StreamProbe.durationMsOf(fixture("lossless.flac")) in 2500..3500)
        assertTrue(StreamProbe.durationMsOf(fixture("plain.wav")) in 2500..3500)
    }

    @Test
    fun `opus and wma fail honestly (not silently corrupt)`() {
        assertFailsWith<Exception>("OPUS غير مدعوم بعد — يجب أن يُرفض صراحة") {
            openAudioInputStream(fixture("whatsapp.opus"))
        }
        assertFailsWith<Exception>("WMA غير مدعوم بعد — يجب أن يُرفض صراحة") {
            openAudioInputStream(fixture("old.wma"))
        }
    }

    // ================= الامتدادات والفلاتر =================

    @Test
    fun `playable extensions include the new families`() {
        for (ext in listOf("aac", "m4a", "m4b", "mp4", "mp2", "mpga", "mpeg", "mpa")) {
            assertTrue(ext in PLAYABLE_EXTENSIONS, "امتداد $ext في قائمة القابل للتشغيل")
        }
        // أساس: القديم لم يُفقد
        for (ext in listOf("wav", "mp3", "ogg", "oga", "flac", "aif", "aiff", "au")) {
            assertTrue(ext in PLAYABLE_EXTENSIONS, "امتداد $ext ما يزال في القائمة")
        }
    }
}
