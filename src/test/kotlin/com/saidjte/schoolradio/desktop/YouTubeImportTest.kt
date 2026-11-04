package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.youtube.AudioTranscoder
import com.saidjte.schoolradio.desktop.youtube.YouTubeJson
import com.saidjte.schoolradio.desktop.youtube.YouTubeUrl
import com.saidjte.schoolradio.desktop.youtube.YtDlpLocator
import com.saidjte.schoolradio.desktop.youtube.YtDlpProcessSpec
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** اختبارات استيراد اليوتيوب (v0.25.0): الروابط والبحث وتحليل JSON والمحوّل الصوتي. */
class YouTubeImportTest {

    // ================= الروابط =================

    @Test
    fun `extract video id from known link shapes`() {
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", YouTubeUrl.extractVideoId("https://youtube.com/watch?v=dQw4w9WgXcQ&t=30s"))
        assertEquals("abc-_12345X", YouTubeUrl.extractVideoId("https://youtu.be/abc-_12345X"))
        assertEquals("abc-_12345X", YouTubeUrl.extractVideoId("https://www.youtube.com/shorts/abc-_12345X"))
        assertEquals("abc-_12345X", YouTubeUrl.extractVideoId("https://www.youtube.com/embed/abc-_12345X?start=5"))
        assertEquals("abc-_12345X", YouTubeUrl.extractVideoId("https://m.youtube.com/watch?v=abc-_12345X"))
        assertEquals("abc-_12345X", YouTubeUrl.extractVideoId("HTTPS://WWW.YOUTUBE.COM/WATCH?V=abc-_12345X"))
    }

    @Test
    fun `reject non video inputs`() {
        assertNull(YouTubeUrl.extractVideoId("نشيد العلم الوطني"))
        assertNull(YouTubeUrl.extractVideoId("https://example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(YouTubeUrl.extractVideoId("https://www.youtube.com/playlist?list=PL1234567890"))
        assertNull(YouTubeUrl.extractVideoId(""))
        assertNull(YouTubeUrl.extractVideoId("https://youtu.be/short"))
        assertNull(YouTubeUrl.extractVideoId("https://youtu.be/toolongid12345"))
    }

    @Test
    fun `classify url versus search`() {
        assertEquals(YouTubeUrl.Kind.VIDEO_URL, YouTubeUrl.classify("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(YouTubeUrl.Kind.VIDEO_URL, YouTubeUrl.classify("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        // أي رابط http يُمرّر للأداة كما هو (تدعم آلاف المواقع) — النص الصريح بحث
        assertEquals(YouTubeUrl.Kind.VIDEO_URL, YouTubeUrl.classify("https://archive.org/details/testmp3testfile"))
        assertEquals(YouTubeUrl.Kind.VIDEO_URL, YouTubeUrl.classify("https://example.com/x"))
        assertEquals(YouTubeUrl.Kind.SEARCH, YouTubeUrl.classify("نشيد العلم السوري"))
        assertEquals(YouTubeUrl.Kind.SEARCH, YouTubeUrl.classify("example.com without scheme"))
    }

    @Test
    fun `search entries keep their source url`() {
        val json = """
            {"_type":"playlist","entries":[
               {"id":"AAA111bbb22","title":"نشيد أول","duration":182,"url":"https://www.youtube.com/watch?v=AAA111bbb22"}
            ]}
        """.trimIndent()
        val list = YouTubeJson.parseSearchResults(json)
        assertEquals(1, list.size)
        assertEquals("https://www.youtube.com/watch?v=AAA111bbb22", list[0].sourceUrl)
    }

    @Test
    fun `sanitize filename strips illegal chars`() {
        assertEquals("a b c", YouTubeUrl.sanitizeFilename("a/b\\c"))
        // «؟» العربية حرف شرعي في أسماء الملفات تبقى — المحظور (ويندوز) هو «?» اللاتينية
        assertEquals("نشيد الوطن ؟", YouTubeUrl.sanitizeFilename("نشيد: الوطن*؟"))
        // محارف ويندوز المحظورة تُستبدل بمسافة وتُطوى
        assertEquals("x z", YouTubeUrl.sanitizeFilename("x<>|?z"))
        assertEquals("مقطع يوتيوب", YouTubeUrl.sanitizeFilename("   "))
        val long = YouTubeUrl.sanitizeFilename("ع".repeat(120))
        assertTrue(long.length <= 80, "length=${long.length}")
        assertFalse(YouTubeUrl.sanitizeFilename("abc.").endsWith("."))
    }

    @Test
    fun `format duration variants`() {
        assertEquals("", YouTubeUrl.formatDuration(null))
        assertEquals("", YouTubeUrl.formatDuration(0L))
        assertEquals("0:05", YouTubeUrl.formatDuration(5L))
        assertEquals("1:05", YouTubeUrl.formatDuration(65L))
        assertEquals("1:02:05", YouTubeUrl.formatDuration(3725L))
    }

    // ================= تحليل JSON =================

    @Test
    fun `parse single video json`() {
        val json = """
            {"id":"AySEttDorbI","title":"National Anthem of Syria - حماة الديار","duration":182,
             "uploader":"Archive Channel","_type":"video",
             "formats":[{"format_id":"140","ext":"m4a","acodec":"mp4a.40.2","abr":128.0,"vcodec":"none"},
                        {"format_id":"251","ext":"webm","acodec":"opus","abr":160.0,"vcodec":"none"}]}
        """.trimIndent()
        val v = YouTubeJson.parseVideo(json)
        assertTrue(v != null)
        assertEquals("AySEttDorbI", v!!.videoId)
        assertEquals("National Anthem of Syria - حماة الديار", v.title)
        assertEquals(182L, v.durationSec)
        assertEquals("Archive Channel", v.uploader)
    }

    @Test
    fun `parse search results flat playlist`() {
        val json = """
            {"_type":"playlist","entries":[
               {"id":"AAA111bbb22","title":"نشيد أول","duration":182,"uploader":"قناة"},
               {"id":"CCC333ddd44","title":"نشيد ثانٍ","duration":95},
               {"id":"", "title":"بدون معرف"},
               {"id":"EEE555fff66","title":"","duration":10}
            ]}
        """.trimIndent()
        val list = YouTubeJson.parseSearchResults(json)
        assertEquals(2, list.size)
        assertEquals("AAA111bbb22", list[0].videoId)
        assertEquals("نشيد أول", list[0].title)
        assertEquals("قناة", list[0].uploader)
    }

    @Test
    fun `pick best mp4a audio format`() {
        val formats = """
            {"formats":[
               {"format_id":"251","ext":"webm","acodec":"opus","abr":160.0,"vcodec":"none"},
               {"format_id":"140","ext":"m4a","acodec":"mp4a.40.2","abr":128.0,"vcodec":"none"},
               {"format_id":"137","ext":"mp4","acodec":"mp4a.40.2","abr":128.0,"vcodec":"avc1.640028"},
               {"format_id":"139","ext":"m4a","acodec":"mp4a.40.5","abr":49.0,"vcodec":"none"}
            ]}
        """.trimIndent()
        // الأفضل: الصوت الخالص mp4a الأعلى جودة = 140 (الـopus لا يفكّه المحوّل)
        assertEquals("140", YouTubeJson.pickAudioFormatId(formats))
        assertNull(YouTubeJson.pickAudioFormatId("""{"formats":[{"format_id":"251","ext":"webm","acodec":"opus","vcodec":"none"}]}"""))
        assertNull(YouTubeJson.pickAudioFormatId(null))
        assertNull(YouTubeJson.pickAudioFormatId("not json"))
    }

    @Test
    fun `parse progress lines`() {
        assertEquals(45.3f, YouTubeJson.parseProgressLine("[download]  45.3% of  3.42MiB at 1.2MiB/s ETA 00:12"))
        assertEquals(100f, YouTubeJson.parseProgressLine("[download] 100.0% of  194.00KiB"))
        assertEquals(0f, YouTubeJson.parseProgressLine("[download]   0.0% of 1MiB"))
        assertNull(YouTubeJson.parseProgressLine("[download] Destination: src.m4a"))
        assertNull(YouTubeJson.parseProgressLine("[youtube] AySEttDorbI: Downloading webpage"))
        assertNull(YouTubeJson.parseProgressLine(""))
    }

    @Test
    fun `describe common errors in arabic`() {
        val bot = YouTubeJson.describeError("ERROR: Sign in to confirm you're not a bot")
        assertTrue(bot != null && bot.contains("كوكيز"))
        assertTrue(YouTubeJson.describeError("ERROR: Video unavailable")!!.contains("غير متاح"))
        assertTrue(YouTubeJson.describeError("unsupported URL")!!.contains("مدعوم"))
        assertNull(YouTubeJson.describeError("some unknown failure"))
        // v0.26.0: رفض الكتابة (عطل Program Files المُبلَّغ عنه بالصورة) له رسالة عربية عملية
        val denied = YouTubeJson.describeError(
            "ERROR: [Errno 13] Permission denied: 'C:\\Program Files\\SchoolRadioDesktop\\tmp876lh9vg.tmp'"
        )
        assertTrue(denied != null && denied.contains("مجلد محمي"), "Errno 13 must be classified: $denied")
        assertTrue(
            YouTubeJson.describeError("OSError: Permission denied: /usr/local/bin/tool")!!.contains("مجلد محمي")
        )
    }

    // ================= v0.26.0: مجلد العمل الآمن لعملية الأداة =================

    @Test
    fun `scratch dir prefers user data tmp and is always writable`() {
        val work = File(System.getProperty("java.io.tmpdir"), "sr-scratch-ok-${System.nanoTime()}")
        try {
            val scratch = YtDlpProcessSpec.scratchDir(work, System.getProperty("java.io.tmpdir"))
            assertEquals(File(work, "tmp").absolutePath, scratch.absolutePath, "مجلد بيانات المستخدم أولًا")
            assertTrue(scratch.isDirectory && scratch.canWrite())
            val env = YtDlpProcessSpec.envFor(scratch, mapOf("PATH" to "/usr/bin"))
            assertEquals(scratch.absolutePath, env["TMP"], "TMP مثبت على المجلد الآمن")
            assertEquals(scratch.absolutePath, env["TEMP"], "TEMP مثبت على المجلد الآمن")
            assertEquals(scratch.absolutePath, env["TMPDIR"], "TMPDIR مثبت على المجلد الآمن")
            assertEquals("/usr/bin", env["PATH"], "بقية البيانات تُحفظ")
        } finally {
            work.deleteRecursively()
        }
    }

    @Test
    fun `scratch dir falls back to system temp when user dir is blocked`() {
        // مسار workDir يشير إلى ملف عادي لا مجلد — mkdirs عليه مستحيل فيرجع
        // المرشح الثاني: TEMP النظام تحت اسم التطبيق (إصلاح عطل Program Files)
        val blocker = File(System.getProperty("java.io.tmpdir"), "sr-scratch-blocked-${System.nanoTime()}.txt")
        blocker.writeText("not a dir")
        try {
            val scratch = YtDlpProcessSpec.scratchDir(blocker, System.getProperty("java.io.tmpdir"))
            assertTrue(scratch.isDirectory && scratch.canWrite(), "احتياط TEMP النظام قابل للكتابة")
            assertEquals(
                File(System.getProperty("java.io.tmpdir"), "SchoolRadioDesktop").absolutePath,
                scratch.absolutePath
            )
        } finally {
            blocker.delete()
        }
    }

    @Test
    fun `scratch dir never throws and always returns something usable`() {
        // مرشحات مستحيلة كله ما عدا الأخير — تعيد الأخير بلا رمي
        val phantom = File("/proc/school-radio-impossible-${System.nanoTime()}")
        val scratch = YtDlpProcessSpec.scratchDir(phantom, "/proc/impossible-tmp-${System.nanoTime()}")
        assertTrue(scratch.absolutePath.isNotBlank())
    }

    // ================= المحوّل الصوتي =================

    @Test
    fun `transcode needs map`() {
        assertTrue(AudioTranscoder.needsTranscode("m4a"))
        assertTrue(AudioTranscoder.needsTranscode("MP4"))
        assertFalse(AudioTranscoder.needsTranscode("mp3"))
        assertFalse(AudioTranscoder.needsTranscode("wav"))
        assertFalse(AudioTranscoder.needsTranscode("ogg"))
        assertFalse(AudioTranscoder.needsTranscode("flac"))
    }

    @Test
    fun `transcode bundled tone m4a to valid mp3`() {
        val m4aBytes = javaClass.classLoader.getResourceAsStream("selftest/test_tone.m4a")?.readBytes()
        assertTrue(m4aBytes != null && m4aBytes.size > 10_000, "test resource missing")
        val tmp = File(System.getProperty("java.io.tmpdir"), "yt-import-test-${System.currentTimeMillis()}")
        tmp.mkdirs()
        try {
            val inp = File(tmp, "tone.m4a")
            inp.writeBytes(m4aBytes!!)
            val out = File(tmp, "tone.mp3")
            var lastProgress = -1f
            val size = AudioTranscoder.m4aToMp3(inp, out, durationSec = 3) { lastProgress = it }
            assertTrue(size > 10_000, "size=$size")
            assertTrue(lastProgress in 0.9f..1.01f, "progress=$lastProgress")
            val info = Mp3Info.analyze(out)
            assertTrue(info != null, "Mp3Info failed")
            assertTrue(info!!.durationMs in 2_700..3_400, "duration=${info.durationMs}")
            assertEquals(44100, info.first.sampleRate)
            assertEquals(2, info.first.channels)
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    @Test
    fun `transcode rejects non mp4 input with arabic message`() {
        val tmp = File(System.getProperty("java.io.tmpdir"), "yt-import-bad-${System.currentTimeMillis()}")
        tmp.mkdirs()
        try {
            val inp = File(tmp, "garbage.m4a")
            inp.writeBytes(ByteArray(5_000) { (it % 251).toByte() })
            val out = File(tmp, "garbage.mp3")
            var message = ""
            try {
                AudioTranscoder.m4aToMp3(inp, out)
            } catch (t: IllegalStateException) {
                message = t.message ?: ""
            }
            assertTrue(message.isNotEmpty(), "expected failure message")
            assertFalse(out.exists(), "no output on failure")
        } finally {
            tmp.listFiles()?.forEach { it.delete() }
            tmp.delete()
        }
    }

    // ================= محدّد الأداة =================

    @Test
    fun `bundled resource name per os`() {
        assertEquals("bin/yt-dlp.exe", YtDlpLocator.bundledResourceName("windows"))
        assertEquals("bin/yt-dlp", YtDlpLocator.bundledResourceName("linux"))
        assertEquals("bin/yt-dlp", YtDlpLocator.bundledResourceName("macos"))
    }

    @Test
    fun `locator resolves bundled binary in this build`() {
        // في بيئة الاختبار يكون مورد المنصة الحالية متاحًا (processResources ينسخه)
        val locator = YtDlpLocator(File(System.getProperty("java.io.tmpdir"), "yt-locator-test"))
        val r = locator.resolve()
        assertTrue(r.fromBundled, "expected bundled binary for host os ${YtDlpLocator.hostOs()}")
        assertTrue(r.binary.isFile && r.binary.length() > 1_000_000, "binary=${r.binary}")
        assertTrue(
            r.binary.name == "yt-dlp.exe" || r.binary.canExecute(),
            "executable bit missing for ${r.binary}"
        )
    }
}
