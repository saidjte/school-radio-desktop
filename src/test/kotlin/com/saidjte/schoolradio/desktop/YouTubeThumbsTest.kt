package com.saidjte.schoolradio.desktop

import androidx.compose.ui.graphics.toComposeImageBitmap
import com.saidjte.schoolradio.desktop.youtube.YouTubeJson
import com.saidjte.schoolradio.desktop.youtube.YouTubeThumbs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * عقد أغلفة يوتيوب — نتائج البحث (v0.54.0 — طلب: «في نتائج البحث في الاستيراد
 * من اليوتيوب أضف صورة غلاف الفيديو») وبطاقة الرابط المباشر (v0.55.0 — طلب:
 * «اضفها في نتائج الرابط المباشر الى اليوتيوب»):
 *
 * 1) الرابط مشتق قياسيًا من videoId (i.ytimg.com mqdefault — 16:9 صغير سريع)،
 *    والمعرّف يُتحقق حرفيًا قبل الإدراج في الرابط (حارس حقن URL).
 * 2) خط التحميل الكامل بجالب محقون وفكك skia حقيقي: شبكة → صورة → ذاكرة،
 *    وإلغاء تكرار الطيران (طلبات متزامنة للمعرّف نفسه جلب واحد)، والفشل
 *    (بايتات تالفة/شبكة معطوبة) يعيد null بلا تخزين ولا توقف — وذاكرة
 *    مشتركة بين صفوف البحث وبطاقة الرابط المباشر في الحوار كله.
 * 3) ذاكرة بسقف 256 صفًا، وكل نتائج البحث والمقاطع المحلولة من روابط
 *    مباشرة تحمل معرّفًا قابلًا للاشتقاق.
 */
class YouTubeThumbsTest {

    // ===== 1) الاشتقاق القياسي من videoId =====

    @Test
    fun `thumbnail url derived from video id`() {
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", YouTubeThumbs.thumbUrl("dQw4w9WgXcQ"))
        assertEquals("https://i.ytimg.com/vi/ABC_-123xyz/mqdefault.jpg", YouTubeThumbs.thumbUrl("ABC_-123xyz"))
        assertEquals("https://i.ytimg.com/vi/abcdefgh1/mqdefault.jpg", YouTubeThumbs.thumbUrl(" abcdefgh1 "))
    }

    // ===== 2) حارس حقن URL: معرفات شاذة ترفض صادقًا =====

    @Test
    fun `invalid ids rejected`() {
        val bad = listOf(
            "", "  ", "bad/id", "../etc", "id with space", "id;drop", "id%20x",
            "short", "x".repeat(20), "id\nline", "عربي12345"
        )
        bad.forEach { id ->
            assertNull(YouTubeThumbs.thumbUrl(id), "يجب رفض المعرّف: '$id'")
        }
    }

    // ===== 3) فكّ skia الحقيقي لعيّنة JPEG مدمجة =====

    @Test
    fun `real skia decoder turns jpeg bytes into 8x8 bitmap`() {
        val bytes = javaClass.classLoader.getResourceAsStream("formats/tiny_thumb.jpg")!!.readBytes()
        val bmp = YouTubeThumbs.decoder(bytes)
        assertTrue(bmp != null && bmp.width == 8 && bmp.height == 8,
            "فكّ skia الحقيقي: ${bmp?.width}x${bmp?.height}")
    }

    // ===== 4) خط التحميل الكامل: شبكة محقونة → فكّ → ذاكرة → جلب واحد =====

    @Test
    fun `load pipeline fetches once then serves from cache`() = runBlocking {
        val bytes = javaClass.classLoader.getResourceAsStream("formats/tiny_thumb.jpg")!!.readBytes()
        var fetches = 0
        val savedF = YouTubeThumbs.fetcher
        val savedD = YouTubeThumbs.decoder
        try {
            YouTubeThumbs.fetcher = { url ->
                assertTrue(url == "https://i.ytimg.com/vi/v54pipeline1/mqdefault.jpg", "الرابط المشتق هو المجلوب: $url")
                fetches++
                delay(60) // نافذة تزامن لإلغاء التكرار
                bytes
            }
            val id = "v54pipeline1"
            val first = YouTubeThumbs.load(id)
            assertTrue(first != null && first.width == 8 && first.height == 8, "أول تحميل فكّ وصورة")
            assertTrue(YouTubeThumbs.cached(id) === first, "الصورة مخزنة بالذاكرة")
            val second = YouTubeThumbs.load(id)
            assertTrue(second === first, "التحميل الثاني من الذاكرة بنفس المثيل")
            assertEquals(1, fetches, "جلب شبكة واحد فقط للمعرّف نفسه")
        } finally {
            YouTubeThumbs.fetcher = savedF
            YouTubeThumbs.decoder = savedD
        }
    }

    // ===== 5) إلغاء تكرار الطيران: طلبان متزامنان ⇒ جلب واحد =====

    @Test
    fun `concurrent loads for same id dedupe into one fetch`() = runBlocking {
        val bytes = javaClass.classLoader.getResourceAsStream("formats/tiny_thumb.jpg")!!.readBytes()
        var fetches = 0
        val savedF = YouTubeThumbs.fetcher
        try {
            YouTubeThumbs.fetcher = {
                fetches++
                delay(150)
                bytes
            }
            val id = "v54dedupe01"
            val (a, b) = coroutineScope {
                val x = async { YouTubeThumbs.load(id) }
                val y = async { YouTubeThumbs.load(id) }
                x.await() to y.await()
            }
            assertTrue(a != null && a === b, "كلا الطلبين أخذا الصورة نفسها")
            assertEquals(1, fetches, "الطلبات المتزامنة تنتظر جوابًا واحدًا")
        } finally {
            YouTubeThumbs.fetcher = savedF
        }
    }

    // ===== 6) الفشل الصادق: بايتات تالفة ⇒ null بلا تخزين؛ شبكة معطوبة ⇒ null =====

    @Test
    fun `garbage bytes and dead network yield null without caching`() = runBlocking {
        val realFetcher = YouTubeThumbs.fetcher // الجالب الحقيقي قبل أي حقن
        val savedF = YouTubeThumbs.fetcher
        try {
            YouTubeThumbs.fetcher = { "هذا ليس ملف صورة".toByteArray() }
            val id = "v54garbage1"
            assertNull(YouTubeThumbs.load(id), "فكّ التالفة يعيد null")
            assertNull(YouTubeThumbs.cached(id), "الفاشل لا يُخزَّن")
            // الجالب الحقيقي على مضيف غير موجود ⇒ null سريع (فشل DNS)
            YouTubeThumbs.fetcher = realFetcher
            assertNull(YouTubeThumbs.load("v54deadnet1"), "الشبكة المعطوبة تعيد null")
        } finally {
            YouTubeThumbs.fetcher = savedF
        }
    }

    // ===== 7) سقف الذاكرة =====

    @Test
    fun `cache capped at 256 entries`() {
        val savedF = YouTubeThumbs.fetcher
        val savedD = YouTubeThumbs.decoder
        try {
            val bytes = javaClass.classLoader.getResourceAsStream("formats/tiny_thumb.jpg")!!.readBytes()
            val bmp = Image.makeFromEncoded(bytes).toComposeImageBitmap()
            repeat(300) { i -> YouTubeThumbs.putCache("v54cap%03d".format(i), bmp) }
            assertTrue(YouTubeThumbs.cacheSize <= 256, "السقف مُحترم: ${YouTubeThumbs.cacheSize}")
        } finally {
            YouTubeThumbs.fetcher = savedF
            YouTubeThumbs.decoder = savedD
        }
    }

    // ===== 8) نتائج بحث محلولة تحمل معرّفًا قابلًا للاشتقاق =====

    @Test
    fun `parsed search results all yield derivable thumbnail urls`() {
        val json = """
            {"_type":"playlist","entries":[
              {"id":"aaaaaaaaaaa","title":"مقطع أول","duration":120},
              {"id":"bbbbbbbbbbb","title":"مقطع ثان","duration":95,"channel":"قناة"}
            ]}
        """.trimIndent()
        val results = YouTubeJson.parseSearchResults(json)
        assertEquals(2, results.size, "نتيجتان محلولتان")
        results.forEach { v ->
            assertEquals("https://i.ytimg.com/vi/${v.videoId}/mqdefault.jpg", YouTubeThumbs.thumbUrl(v.videoId),
                "كل نتيجة تُشتق غلافًا")
        }
    }

    // ===== 9) مقطع محلول من رابط مباشر يحمل معرّفًا قابلًا للاشتقاق (v0.55.0) =====

    @Test
    fun `video resolved from direct url yields derivable thumbnail`() {
        val json = """
            {"id":"ccccccccccc","title":"من رابط مباشر","duration":180,"uploader":"قناة",
             "webpage_url":"https://www.youtube.com/watch?v=ccccccccccc"}
        """.trimIndent()
        val v = YouTubeJson.parseVideo(json)
        assertTrue(v != null && v.videoId == "ccccccccccc", "المقطع محلول من الرابط المباشر")
        assertEquals("https://i.ytimg.com/vi/ccccccccccc/mqdefault.jpg", YouTubeThumbs.thumbUrl(v!!.videoId),
            "بطاقة الرابط المباشر تُشتق الغلاف من المعرّف نفسه")
        assertNull(YouTubeThumbs.thumbUrl("dQw4w9WgXcQ/extra"), "معرّف ملوث من رابط شاذ يرفض")
    }
}
