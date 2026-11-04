package com.saidjte.schoolradio.desktop.youtube

import com.google.gson.JsonParser

/**
 * تحليل مخرجات yt-dlp JSON نقية — بلا عمليات (اختبار وحدوي مباشر):
 * بيانات مقطع واحد، نتائج بحث (flat-playlist)، اختيار صيغة صوتية مدعومة،
 * سطور تقدم التحميل، وتصنيف الأخطاء الشائعة برسائل عربية واضحة.
 */
object YouTubeJson {

    /** معلومات مقطع يوتيوب كما تظهر في الواجهة. sourceUrl يُحفظ لتحميل أي موقع تدعمه الأداة. */
    data class VideoInfo(
        val videoId: String,
        val title: String,
        val durationSec: Long,
        val uploader: String? = null,
        val sourceUrl: String = ""
    )

    /** صيغة الصوت المدعومة: أفضل مسار mp4a (AAC) — يفكّه المحوّل المحلي. */
    fun pickAudioFormatId(formatsJson: String?): String? {
        if (formatsJson.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(formatsJson).asJsonObject
            val formats = root.getAsJsonArray("formats") ?: return null
            data class Cand(val id: String, val abr: Double, val isAudioOnly: Boolean)
            val cands = ArrayList<Cand>()
            for (f in formats) {
                val o = f.asJsonObject
                val acodec = o.get("acodec")?.asStringOrNull() ?: continue
                if (!acodec.startsWith("mp4a")) continue
                val abr = o.get("abr")?.asDoubleOrNull() ?: 0.0
                val vcodec = o.get("vcodec")?.asStringOrNull() ?: "none"
                val audioOnly = vcodec == "none" || vcodec.isEmpty()
                cands.add(Cand(o.get("format_id")?.asStringOrNull() ?: continue, abr, audioOnly))
            }
            // الأولوية: مسار صوتي خالص أعلى جودة، ثم أي مسار mp4a
            (cands.filter { it.isAudioOnly }.maxByOrNull { it.abr } ?: cands.maxByOrNull { it.abr })
                ?.id
        } catch (_: Exception) {
            null
        }
    }

    /**
     * صيغة احتياطية إن غابت قائمة الصيغ: itag 140 (AAC) القياسي في يوتيوب ثم m4a
     * ثم أي صوت خالص ثم أي صيغة (للمواقع التي تقدّم مسارًا واحدًا مدمجًا —
     * يُفكّ الصوت منها محليًا في المحوّل).
     */
    const val FALLBACK_FORMAT = "140/bestaudio[ext=m4a]/bestaudio/best"

    /** تحليل JSON مقطع واحد (yt-dlp -J). يعيد null إن لم يكن مقطعًا صالحًا. */
    fun parseVideo(json: String): VideoInfo? = try {
        val root = JsonParser.parseString(json).asJsonObject
        if (root.get("_type")?.asStringOrNull() == "playlist") {
            // قد يعود قائمة بألبوم واحد — نأخذ أول مدخل صالح
            val entries = root.getAsJsonArray("entries") ?: return null
            if (entries.count() == 0) null else entryToInfo(entries[0].asJsonObject)
        } else {
            entryToInfo(root)
        }
    } catch (_: Exception) {
        null
    }

    /** تحليل نتائج بحث (yt-dlp -J --flat-playlist "ytsearchN:…"). */
    fun parseSearchResults(json: String): List<VideoInfo> = try {
        val root = JsonParser.parseString(json).asJsonObject
        val entries = root.getAsJsonArray("entries") ?: return emptyList()
        entries.mapNotNull { e ->
            runCatching { entryToInfo(e.asJsonObject) }.getOrNull()
        }.filter { it.videoId.isNotEmpty() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun entryToInfo(o: com.google.gson.JsonObject): VideoInfo? {
        val id = o.get("id")?.asStringOrNull()?.trim().orEmpty()
        val title = o.get("title")?.asStringOrNull()?.trim().orEmpty()
        if (id.isEmpty() || title.isEmpty()) return null
        val dur = o.get("duration")?.asLongOrNull() ?: 0L
        val url = o.get("webpage_url")?.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: o.get("url")?.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            ?: YouTubeUrl.watchUrl(id)
        return VideoInfo(
            videoId = id,
            title = title,
            durationSec = dur.coerceAtLeast(0L),
            uploader = o.get("uploader")?.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?: o.get("channel")?.asStringOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            sourceUrl = url
        )
    }

    /**
     * نسبة التقدم من سطر yt-dlp --newline — مثل:
     * «[download]  45.3% of  3.42MiB at …» → 45.3f. يعيد null لسطر غير تقدم.
     */
    fun parseProgressLine(line: String): Float? {
        val s = line.trim()
        if (!s.startsWith("[download]")) return null
        val rest = s.removePrefix("[download]").trim()
        if (!rest.endsWith("%") && !rest.contains("% of")) {
            // «[download] 100% of 1.9MiB in 00:00:03» — النسبة داخلها قبل "%"
        }
        val m = Regex("^([0-9]+(?:\\.[0-9]+)?)%").find(rest) ?: return null
        val v = m.groupValues[1].toFloatOrNull() ?: return null
        return v.coerceIn(0f, 100f)
    }

    /**
     * تصنيف أخطاء yt-dlp الشائعة برسائل عربية عملية — يعيد null إن لم يتعرف عليها
     * (فتعرض رسالة الخطأ الخام مع ملاحظة عامة).
     */
    fun describeError(stderr: String): String? {
        val s = stderr.lowercase()
        return when {
            "sign in to confirm" in s || "not a bot" in s ->
                "يوتيوب يطلب تحقّقًا إضافيًا من شبكتك الحالية. الحل: صدّر ملف كوكيز من متصفحك " +
                        "(امتداد «Get cookies.txt») ثم أرفقه في الحوار بخيار «ملف كوكيز» — أو جرّب شبكة أخرى."
            "video unavailable" in s ->
                "المقطع غير متاح (حُذف أو خاص أو محجوب في بلدك)."
            "is not a valid url" in s || "unable to download webpage" in s || "network" in s ->
                "تعذّر الوصول إلى الشبكة أو الرابط غير صالح — تأكد من الاتصال ومن صحة الرابط."
            "unsupported url" in s ->
                "الرابط غير مدعوم — استخدم رابط مقطع يوتيوب أو اكتب كلمات للبحث."
            "no supported format" in s || "requested format is not available" in s ->
                "لا تتوفر صيغة صوتية قابلة للاستيراد لهذا المقطع."
            "ffmpeg" in s ->
                "احتاج التحميل أداة خارجية رفضت الشبكة — أعد المحاولة."
            else -> null
        }
    }

    // —— محوّلات Gson المتسامحة ——

    private fun com.google.gson.JsonElement.asStringOrNull(): String? =
        if (isJsonNull) null else try { asString } catch (_: Exception) { null }

    private fun com.google.gson.JsonElement.asDoubleOrNull(): Double? =
        if (isJsonNull) null else try { asDouble } catch (_: Exception) { null }

    private fun com.google.gson.JsonElement.asLongOrNull(): Long? =
        if (isJsonNull) null else try { asLong } catch (_: Exception) {
            try { asDouble.toLong() } catch (_: Exception) { null }
        }
}
