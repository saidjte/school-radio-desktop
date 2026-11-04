package com.saidjte.schoolradio.desktop.youtube

/**
 * منطق نقي للروابط والبحث في اليوتيوب — بلا شبكة ولا موارد (اختبار وحدوي مباشر).
 * يدعم: watch?v= وyoutu.be وshorts وembed وlive — وأي نص آخر يُعامل كبحث.
 */
object YouTubeUrl {

    enum class Kind { VIDEO_URL, SEARCH }

    private val HOSTS = listOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

    /** هل النص رابط HTTP(S) صريح؟ */
    fun isHttpUrl(input: String): Boolean {
        val s = input.trim().lowercase()
        return s.startsWith("http://") || s.startsWith("https://")
    }

    /** هل الرابط يوتيوب فعلًا (مضيف معروف)؟ */
    fun isYouTubeUrl(url: String): Boolean {
        val s = url.trim().lowercase()
        if (!isHttpUrl(s)) return false
        val rest = s.removePrefix("https://").removePrefix("http://")
        return HOSTS.any { h -> rest.startsWith("$h/") || rest.startsWith("$h?") }
    }

    /** استخراج معرّف المقطع (11 حرفًا) من أشكال الروابط المعروفة — بنية الرابط غير حساسة لحالة الأحرف، والمعرّف كذلك يبقى بحروفه الأصلية. */
    fun extractVideoId(input: String): String? {
        val s = input.trim()
        if (!isHttpUrl(s)) return null
        if (!isYouTubeUrl(s)) return null
        val noScheme = s.substringAfter("://")
        val host = noScheme.substringBefore('/').lowercase()
        val pathRaw = noScheme.substringAfter('/', "")
        val path = pathRaw.lowercase()
        return when {
            // youtu.be/ID
            host.endsWith("youtu.be") ->
                pathRaw.substringBefore('?').substringBefore('#').takeIf { isVideoId(it) }
            // youtube.com/watch?v=ID
            path.startsWith("watch") -> {
                val query = pathRaw.substringAfter('?', "")
                query.split('&')
                    .firstOrNull { it.lowercase().startsWith("v=") }
                    ?.substring(2)
                    ?.takeIf { isVideoId(it) }
            }
            // youtube.com/shorts/ID أو embed/ID أو live/ID أو v/ID
            path.startsWith("shorts/") || path.startsWith("embed/") ||
                    path.startsWith("live/") || path.startsWith("v/") ->
                pathRaw.substringAfter('/').substringBefore('?').substringBefore('#')
                    .takeIf { isVideoId(it) }
            else -> null
        }
    }

    /** معرّف يوتيوب: 11 حرفًا من [A-Za-z0-9_-]. */
    fun isVideoId(id: String): Boolean =
        id.length == 11 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    /** تصنيف المُدخل: أي رابط http(s) يُمرّر للأداة كما هو (يوتيوب وأخواتها)، والنص بحث. */
    fun classify(input: String): Kind =
        if (isHttpUrl(input)) Kind.VIDEO_URL else Kind.SEARCH

    /** رابط مشاهدة قياسي من معرّف المقطع. */
    fun watchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

    /**
     * اسم ملف آمن من عنوان المقطع: يزيل محارف المسارات المحظورة في ويندوز ولينكس وماك،
     * ويحبس الطول، ويمنع الفراغ (يعود لاسم افتراضي عربي).
     */
    fun sanitizeFilename(title: String, maxLen: Int = 80): String {
        val bad = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
        val cleaned = buildString {
            for (c in title) {
                when {
                    c in bad -> append(' ')
                    c == '\n' || c == '\r' || c == '\t' -> append(' ')
                    else -> append(c)
                }
            }
        }
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ' ')
        if (cleaned.isEmpty()) return "مقطع يوتيوب"
        return if (cleaned.length <= maxLen) cleaned else cleaned.take(maxLen).trimEnd('.', ' ')
    }

    /** تنسيق مدة بالثواني: m:ss أو h:mm:ss — فارغ إن غابت. */
    fun formatDuration(seconds: Long?): String {
        if (seconds == null || seconds <= 0L) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }
}
