package com.saidjte.schoolradio.desktop.youtube

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * أغلفة نتائج البحث من يوتيوب (v0.54.0 «صورة غلاف الفيديو في نتائج البحث» —
 * طلب: «في نتائج البحث في الاستيراد من اليوتيوب أضف صورة غلاف الفيديو»):
 *
 * الرابط مشتق قياسيًا من videoId عبر CDN صور يوتيوب i.ytimg.com —
 * mqdefault.jpg (320×180 بنسبة 16:9) حجمه صغير وتحميله سريع ويناسب صفوف
 * القائمة، وهو متاح لكل مقطع عام دون كوكيز ولا عناوين خاصة. المعرّف
 * يُتحقق حرفيًا قبل الإدراج في الرابط (حارس حقن URL).
 *
 * التحميل لامتركيًا على المرسل IO مع ذاكرة بكل videoId (سقف 256 صفًا)،
 * وإلغاء تكرار الطيران — طلبات متزامنة للمعرّف نفسه تنتظر جوابًا واحدًا —
 * وفشل الشبكة أو الفك يبقي البديل النصي في الصف دون أي توقف للواجهة،
 * ولا ذاكرة سلبية: إعادة دخول الصف تعيد المحاولة مرة واحدة فتشفى أعطال
 * الشبكة العابرة تلقائيًا.
 */
object YouTubeThumbs {

    private val ID_OK = Regex("[A-Za-z0-9_-]+")

    /** رابط غلاف معرّف صالح — null لمعرّف شاذ (حارس حقن URL). */
    fun thumbUrl(videoId: String): String? {
        val id = videoId.trim()
        if (id.length !in 8..16 || !ID_OK.matches(id)) return null
        return "https://i.ytimg.com/vi/$id/mqdefault.jpg"
    }

    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val inflight = ConcurrentHashMap<String, Mutex>()

    /** قابل للحقن بالاختبارات: جلب بايتات الغلاف عبر الشبكة. */
    internal var fetcher: suspend (String) -> ByteArray? = { url -> fetchBytes(url) }

    /** قابل للحقن بالاختبارات: فك البايتات إلى صورة كومبوز (skia). */
    internal var decoder: (ByteArray) -> ImageBitmap? = { bytes -> decodeBmp(bytes) }

    /** الصورة المخزنة إن وجدت — تُعرض فورًا بلا انتظار شبكة. */
    fun cached(videoId: String): ImageBitmap? = cache[videoId]

    internal fun putCache(videoId: String, bmp: ImageBitmap) {
        cache[videoId] = bmp
        if (cache.size > 256) {
            cache.keys.take(cache.size - 256).forEach { cache.remove(it) }
        }
    }

    /** عدد الصور المخزنة (فحوصات السقف). */
    internal val cacheSize: Int get() = cache.size

    /**
     * تحميل غلاف المعرّف: الذاكرة أولًا، ثم شبكة على المرسل IO بإلغاء تكرار
     * الطيران، والفشل يعيد null ويترك الصف ببديله دون تخزين.
     */
    suspend fun load(videoId: String): ImageBitmap? {
        cache[videoId]?.let { return it }
        val url = thumbUrl(videoId) ?: return null
        val m = inflight.computeIfAbsent(videoId) { Mutex() }
        return try {
            m.withLock {
                cache[videoId]?.let { return it }
                val bmp = fetcher(url)?.let { decoder(it) }
                if (bmp != null) putCache(videoId, bmp)
                bmp
            }
        } finally {
            inflight.remove(videoId)
        }
    }

    private suspend fun fetchBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 5_000
            c.readTimeout = 8_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            c.connect()
            if (c.responseCode !in 200..299) {
                c.disconnect()
                null
            } else {
                c.inputStream.use { it.readBytes() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** فك الصورة بskia — نفس عُرف النافذة الرئيسة (لا إغلاق؛ ImageBitmap يحمل البيكسلات). */
    private fun decodeBmp(bytes: ByteArray): ImageBitmap? = try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
