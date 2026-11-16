package com.saidjte.schoolradio.desktop.audio

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * فتح وسائط البث عبر HTTP — نظير ما تفعله مشغلات الويب:
 * ترويج متصفح كامل (يوتيوب يرفض عملاء مجهولين على روابط googlevideo)، مهلات
 * صريحة، وكوكيز اختيارية من ملف Netscape مُصدَّر من المتصفح (نفس ملف حوار
 * الاستيراد) تُرشَّح لنطاق الرابط فقط — بلا أي اعتماد على أدوات خارجية.
 */
internal object HttpAudioSource {

    /** مهلة الاتصال والقراءة — البث الحي حساس: أقصر من مهلات التحميل. */
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /** ترويج متصفح سطح مكتب — روابط googlevideo ترفض الترويج الافتراضي لجافا. */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    /**
     * فتح مجرى وسيط واحد. يرمي IOException برسالة عربية عند فشل الاتصال
     * أو رمز استجابة غير سليم — الرسالة تصل واجهة المشغّل كما هي.
     */
    fun open(url: String, cookiesPath: String?): InputStream {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", "https://www.youtube.com/")
            setRequestProperty("Origin", "https://www.youtube.com")
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        cookiesPath?.takeIf { it.isNotBlank() }?.let { p ->
            try {
                val f = File(p)
                if (f.isFile) {
                    val header = cookieHeaderFor(f, url)
                    if (!header.isNullOrEmpty()) conn.setRequestProperty("Cookie", header)
                }
            } catch (_: Exception) { }
        }
        try {
            conn.connect()
        } catch (e: IOException) {
            throw IOException("تعذّر الاتصال بخادم البث — تحقق من الشبكة وأعد المحاولة. (${e.message ?: "شبكة"})")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            try { conn.disconnect() } catch (_: Exception) { }
            throw IOException("رفض خادم البث الطلب (رمز $code) — رابط البث منتهٍ غالبًا؛ أعد «تشغيل مباشر» لجلب رابط جديد.")
        }
        return try {
            conn.inputStream
        } catch (e: IOException) {
            throw IOException("تعذّر بدء تدفق الصوت من الخادم (${e.message ?: "مجرى"}) — أعد المحاولة.")
        }
    }

    /**
     * ترويسة Cookie من ملف Netscape مُرشَّحة لنطاق ومسار الرابط — نقية وقابلة
     * للاختبار: الأسطر التعليقية (#HttpOnly_ فالسطر نفسه بلا # المحتوى) والمجالات
     * غير المطابقة تُسقَط، والصيغة: domain tab flag tab path tab secure tab expiry tab name tab value.
     */
    fun cookieHeaderFor(cookieFile: File, url: String): String? {
        val u = try { URI(url) } catch (_: Exception) { return null }
        val host = u.host?.lowercase() ?: return null
        val path = (u.path ?: "/").ifEmpty { "/" }
        val secure = u.scheme == "https"
        val pairs = ArrayList<String>()
        try {
            BufferedReader(InputStreamReader(cookieFile.inputStream(), Charsets.UTF_8)).useLines { lines ->
                lines.forEach { raw ->
                    var line = raw.trim()
                    if (line.isEmpty()) return@forEach
                    if (line.startsWith("#HttpOnly_")) line = line.removePrefix("#HttpOnly_")
                    if (line.startsWith("#")) return@forEach
                    val cols = line.split("\t")
                    if (cols.size < 7) return@forEach
                    val domain = cols[0].lowercase().removePrefix(".")
                    val cookiePath = cols[2].ifEmpty { "/" }
                    val isSecureOnly = cols[3].equals("TRUE", ignoreCase = true)
                    val name = cols[5].trim()
                    val value = cols[6].trim()
                    if (name.isEmpty()) return@forEach
                    // مطابقة النطاق: مساوٍ أو نطاق أب (youtube.com تغطي www.youtube.com)
                    val domainOk = host == domain || host.endsWith(".$domain")
                    val pathOk = path.startsWith(cookiePath)
                    val secureOk = !isSecureOnly || secure
                    if (domainOk && pathOk && secureOk) pairs.add("$name=$value")
                }
            }
        } catch (_: Exception) {
            return null
        }
        if (pairs.isEmpty()) return null
        return pairs.joinToString("; ")
    }

    /**
     * جدول HLS الناتج من تحليل نص القائمة: init (EXT-X-MAP) وسائط الأجزاء،
     * أو قائمة رئيسية (variant) إن كانت تجميعية.
     */
    class Playlist(
        val initUri: String?,
        val segments: List<String>,
        /** true = قائمة رئيسية تجميعية — [variantUri] هو أفضل مسار فيها. */
        val isMaster: Boolean,
        val variantUri: String?
    )

    /**
     * تحليل نص m3u8 — نقية وقابلة للاختبار:
     * - #EXT-X-MAP:URI="…" → مقطع التهيئة (init) لحاويات fMP4.
     * - #EXT-X-STREAM-INF → القائمة رئيسية: السطر النصي التالي أفضل مسار
     *   (نأخذ أول مسار — يوتيوب يرتب الجودات تنازليًا في قوائم الصوت).
     * - الأسطر النصية غير الفارغة → وسائط الأجزاء بالترتيب.
     * - تشفير غير NONE (#EXT-X-KEY) يُرفض صادقًا — لا يُفك محليًا.
     * الروابط النسبية تُحلّ على رابط القائمة نفسه.
     */
    fun parse(text: String, baseUrl: String): Playlist {
        var initUri: String? = null
        val segments = ArrayList<String>()
        var isMaster = false
        var variantUri: String? = null
        var pendingVariant = false
        var encrypted = false

        fun resolve(u: String): String = try {
            URI(baseUrl).resolve(u.trim()).toString()
        } catch (_: Exception) {
            u.trim()
        }

        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> { }
                line.startsWith("#EXT-X-KEY") -> {
                    if (!line.contains("METHOD=NONE") && !line.contains("method=NONE")) encrypted = true
                }
                line.startsWith("#EXT-X-MAP") -> {
                    Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)?.let { initUri = resolve(it) }
                }
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    isMaster = true
                    pendingVariant = true
                }
                line.startsWith("#") -> { }
                else -> {
                    if (pendingVariant && variantUri == null) {
                        variantUri = resolve(line)
                        pendingVariant = false
                    } else if (!isMaster) {
                        segments.add(resolve(line))
                    }
                }
            }
        }
        if (encrypted) {
            throw IOException("بث HLS مشفَّر (#EXT-X-KEY) — التشغيل المباشر لا يفك التشفير؛ استخدم زر التحميل.")
        }
        return Playlist(initUri, segments, isMaster, variantUri)
    }
}
