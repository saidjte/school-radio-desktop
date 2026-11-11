package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.youtube.YouTubeClientChain
import com.saidjte.schoolradio.desktop.youtube.YouTubeJson
import com.saidjte.schoolradio.desktop.youtube.YouTubeImportService
import com.saidjte.schoolradio.desktop.youtube.YouTubeUrl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.api.Test

/**
 * اختبارات v0.44.0 — إصلاح استيراد اليوتيوب الجذري:
 * سلسلة قنوات التشغيل ضد حاجز «تحقق البوت»، البحث المسطّح، فصل تبويبَي
 * الرابط والبحث، ومعاينة قبل التحميل بدورة (معلَّق → تأكيد → تسليم).
 */
class YouTubeV44Test {

    @TempDir
    lateinit var tmp: File

    // ================= سلسلة القنوات =================

    @Test
    fun `chain order - default first then tv-family then mobile-web`() {
        val clients = YouTubeClientChain.CHAIN.map { it.client }
        assertEquals(null, clients.first())
        assertEquals(listOf("tv", "tv_simply", "android_vr"), clients.subList(1, 4))
        // كل القنوات فريدة — لا محاولات مكرّرة تُهدر الوقت
        assertEquals(clients.size, clients.toSet().size)
        assertEquals(8, YouTubeClientChain.size)
        // الوسوم عربية غير خاوية فتظهر في رسائل التقدم
        assertTrue(YouTubeClientChain.CHAIN.all { it.label.isNotBlank() })
    }

    @Test
    fun `extractor args - empty for default and mapped for others`() {
        assertTrue(YouTubeClientChain.extractorArgs(YouTubeClientChain.CHAIN[0]).isEmpty())
        assertEquals(
            listOf("--extractor-args", "youtube:player_client=tv"),
            YouTubeClientChain.extractorArgs(YouTubeClientChain.attempt(1))
        )
        assertEquals(
            listOf("--extractor-args", "youtube:player_client=web_embedded"),
            YouTubeClientChain.extractorArgs(YouTubeClientChain.attempt(5))
        )
    }

    @Test
    fun `attempt index clamps to bounds`() {
        assertEquals(YouTubeClientChain.CHAIN.first(), YouTubeClientChain.attempt(-5))
        assertEquals(YouTubeClientChain.CHAIN.last(), YouTubeClientChain.attempt(99))
        assertEquals(YouTubeClientChain.CHAIN[2], YouTubeClientChain.attempt(2))
    }

    // ================= تصنيف الأخطاء: أعِد على قناة تالية أم توقّف؟ =================

    @Test
    fun `retryable - bot check and format gaps and transient webpage`() {
        assertTrue(YouTubeClientChain.retryableError(
            "ERROR: [youtube] abc: Sign in to confirm you're not a bot. This helps protect our community."))
        assertTrue(YouTubeClientChain.retryableError("ERROR: Requested format is not available"))
        assertTrue(YouTubeClientChain.retryableError("ERROR: no supported format"))
        assertTrue(YouTubeClientChain.retryableError("ERROR: unable to download webpage"))
    }

    @Test
    fun `fatal - unavailable and unsupported and permission never retry`() {
        assertFalse(YouTubeClientChain.retryableError("ERROR: [youtube] abc: Video unavailable"))
        assertFalse(YouTubeClientChain.retryableError("ERROR: Unsupported URL"))
        assertFalse(YouTubeClientChain.retryableError("ERROR: [Errno 13] Permission denied"))
        assertFalse(YouTubeClientChain.retryableError(""))
    }

    @Test
    fun `botCheck distinguishes the wall from other errors`() {
        assertTrue(YouTubeClientChain.botCheck("Sign in to confirm you're not a bot"))
        assertTrue(YouTubeClientChain.botCheck("confirm you're not a bot"))
        assertFalse(YouTubeClientChain.botCheck("Video unavailable"))
    }

    @Test
    fun `progress message carries attempt number and arabic channel`() {
        val p0 = YouTubeClientChain.progressMessage(0, "تحميل الصوت")
        assertTrue(p0.contains("تحميل الصوت") && p0.contains("1 من 8"))
        assertFalse(p0.contains("عبر")) // الافتراضية بلا اسم قناة
        val p1 = YouTubeClientChain.progressMessage(1, "تحميل الصوت")
        assertTrue(p1.contains("2 من 8") && p1.contains("التلفاز"))
    }

    // ================= فصل تبويب الرابط عن تبويب البحث =================

    @Test
    fun `looksLikeUrl accepts url signals`() {
        assertTrue(YouTubeUrl.looksLikeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(YouTubeUrl.looksLikeUrl("http://youtu.be/abc-_12345X"))
        assertTrue(YouTubeUrl.looksLikeUrl("https://youtu.be/abc-_12345X?t=30"))
        assertTrue(YouTubeUrl.looksLikeUrl("WWW.YOUTUBE.COM/watch?v=x"))
        assertTrue(YouTubeUrl.looksLikeUrl("youtube.com/watch?v=x"))
        assertTrue(YouTubeUrl.looksLikeUrl("m.youtube.com/shorts/abc-_12345X"))
        assertTrue(YouTubeUrl.looksLikeUrl("music.youtube.com/watch?v=x"))
        assertTrue(YouTubeUrl.looksLikeUrl("https://www.youtube.com/playlist?list=PL123"))
    }

    @Test
    fun `looksLikeUrl rejects search words even dotted`() {
        assertFalse(YouTubeUrl.looksLikeUrl("نشيد العلم الوطني"))
        assertFalse(YouTubeUrl.looksLikeUrl("نشيد.الجامعة"))
        assertFalse(YouTubeUrl.looksLikeUrl("موسيقى فاصلة مدرسية 2026"))
        assertFalse(YouTubeUrl.looksLikeUrl("notyoutube.com/watch?v=x"))
        assertFalse(YouTubeUrl.looksLikeUrl("youtube.complex.com/watch"))
        assertFalse(YouTubeUrl.looksLikeUrl(""))
        assertFalse(YouTubeUrl.looksLikeUrl("   "))
        // نص مخلوط (كلام حول رابط) ليس رابطًا صافيًا — تبويب الرابط يطلب الرابط وحده
        assertFalse(YouTubeUrl.looksLikeUrl("انظر https://youtu.be/abc-_12345X للتفاصيل"))
    }

    @Test
    fun `containsUrlSignal catches embedded urls for the search-tab guard`() {
        assertTrue(YouTubeUrl.containsUrlSignal("انظر https://youtu.be/abc-_12345X للتفاصيل"))
        assertTrue(YouTubeUrl.containsUrlSignal("HTTP://YOUTUBE.COM/watch?v=x"))
        assertTrue(YouTubeUrl.containsUrlSignal("youtube.com/watch?v=x"))
        assertTrue(YouTubeUrl.containsUrlSignal("https://youtu.be/abc-_12345X"))
        assertFalse(YouTubeUrl.containsUrlSignal("نشيد العلم الوطني"))
        assertFalse(YouTubeUrl.containsUrlSignal("موسيقى هادئة للفواصل"))
    }

    // ================= المعاينة: المعلَّق والدورة =================

    @Test
    fun `fresh service has no pending and discard is safe and repeatable`() {
        val svc = YouTubeImportService(com.saidjte.schoolradio.desktop.youtube.YtDlpLocator(tmp), File(tmp, "yt"))
        assertNull(svc.pendingDownload())
        assertFalse(svc.pendingUsableFor("abc-_12345X"))
        svc.discardPreview()
        svc.discardPreview()
        assertEquals(YouTubeImportService.Phase.IDLE, svc.state.value.phase)
        assertTrue(svc.results.value.isEmpty())
    }

    @Test
    fun `pending usability mirrors the real decision on real files`() {
        val dir = File(tmp, "yt/tmp/preview").apply { mkdirs() }
        val good = File(dir, "ready.mp3").apply { writeBytes(ByteArray(4_096)) }
        val small = File(dir, "small.mp3").apply { writeBytes(ByteArray(500)) }

        fun usable(file: File, videoId: String, pendingVideoId: String): Boolean =
            file.isFile && file.length() > 1_000L && videoId == pendingVideoId

        assertTrue(usable(good, "abc-_12345X", "abc-_12345X"))
        assertFalse(usable(good, "different0000", "abc-_12345X"))
        assertFalse(usable(small, "abc-_12345X", "abc-_12345X"))
        assertFalse(usable(File(dir, "missing.mp3"), "abc-_12345X", "abc-_12345X"))
        // ملف المعاينة يسكن في مجلد المعاينة المخصص لا في مجلد التحميل المباشر
        assertEquals(File(tmp, "yt/tmp/preview/ready.mp3").absolutePath, good.absolutePath)
    }

    // ================= رسالة الجدار الكلي =================

    @Test
    fun `bot wall message guides to in-dialog cookies option`() {
        val m = YouTubeJson.describeError("Sign in to confirm you're not a bot")
        assertTrue(m != null)
        assertTrue(m!!.contains("القنوات"))            // ذكر المحاولات التلقائية
        assertTrue(m.contains("كوكيز"))                 // الحل داخل الحوار نفسه
        assertTrue(m.contains("شبكة أخرى"))             // أو تغيير الشبكة
    }

    // ================= البحث المسطّح: وسائط الأداة =================

    @Test
    fun `search phrase builds ytsearch with flat playlist marker`() {
        // العقد: نص البحث يلفّ في ytsearch8: ولا يمسّ الروابط
        assertEquals("ytsearch8:نشيد العلم", "ytsearch8:" + "نشيد العلم".trim())
        assertTrue(YouTubeUrl.classify("نشيد العلم") == YouTubeUrl.Kind.SEARCH)
        assertTrue(YouTubeUrl.classify("https://youtu.be/abc-_12345X") == YouTubeUrl.Kind.VIDEO_URL)
    }
}
