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

    /**
     * مصدر بث مباشر مُختار من JSON الأداة (v0.51.0 «تشغيل مباشر دون تحميل»):
     * مسار صوتي خالص يمكن فكّه محليًا حيًا — mp4a عبر fMP4 أو قائمة HLS،
     * أو MP3 جاهز. صيغ opus/vorbis/webm تُرفض صادقًا (لا فاكّ جافا لهما).
     */
    data class StreamSource(
        val url: String,
        /** https = وسيط مباشر، وأي شيء يحوي m3u8 = قائمة HLS. */
        val protocol: String,
        /** امتداد الحاوية كما تسمّيها الأداة (m4a/mp4/mp3…). */
        val ext: String,
        val abr: Double
    ) {
        val isHls: Boolean get() = protocol.lowercase().contains("m3u8")
        val isMp3: Boolean get() = ext.lowercase() == "mp3"
    }

    /**
     * اختيار أفضل مصدر بث من JSON مقطع (مخرجات -J): مرشّحات mp4a وmp3 الصوتية
     * الخالصة التي تملك رابطًا فعليًا، والأولوية للرابط المباشر (https) على HLS
     * ثم لأعلى abr. يعيد null إن لم يوجد ما يفكّه المحرك — فتُعرض رسالة صادقة
     * تقترح زر التحميل بدلًا من التشغيل المباشر.
     */
    fun pickStreamSource(json: String): StreamSource? = try {
        val root = JsonParser.parseString(json).asJsonObject
        val cands = ArrayList<StreamSource>()

        fun consider(url: String?, protocol: String?, ext: String?, abr: Double) {
            val u = url?.trim().orEmpty()
            if (u.isEmpty()) return
            val e = ext?.trim()?.lowercase().orEmpty()
            val p = protocol?.trim()?.lowercase().orEmpty()
            val codecOk = e in setOf("m4a", "mp4", "mp3", "mpga", "aac", "a4a", "m4b")
            val protoOk = p == "https" || p == "http" || p.contains("m3u8") ||
                    p.contains("dash_https") || p.isEmpty()
            if (!codecOk || !protoOk) return
            if (p == "n/a" && !u.contains("googlevideo")) return // صيغة بلا رابط فعلي
            cands.add(StreamSource(u, p.ifEmpty { "https" }, e.ifEmpty { "m4a" }, abr))
        }

        root.get("formats")?.asJsonArrayOrNull()?.forEach { f ->
            val o = f.asJsonObjectOrNull() ?: return@forEach
            val acodec = o.get("acodec")?.asStringOrNull() ?: return@forEach
            val vcodec = o.get("vcodec")?.asStringOrNull() ?: "none"
            val audioOnly = vcodec == "none" || vcodec.isEmpty()
            val playableCodec = acodec.startsWith("mp4a") || acodec.startsWith("mp3")
            if (!audioOnly || !playableCodec) return@forEach
            consider(
                o.get("url")?.asStringOrNull(),
                o.get("protocol")?.asStringOrNull(),
                o.get("ext")?.asStringOrNull(),
                o.get("abr")?.asDoubleOrNull() ?: 0.0
            )
        }
        // الصيغة المطلوبة نفسها (-f) إن كانت مرشّحة — عادةً الأفضل في القائمة
        consider(
            root.get("url")?.asStringOrNull(),
            root.get("protocol")?.asStringOrNull(),
            root.get("ext")?.asStringOrNull(),
            root.get("abr")?.asDoubleOrNull() ?: 0.0
        )
        cands.maxByOrNull { (if (!it.isHls) 1_000_000 else 0) + (it.abr * 1000).toInt() }
    } catch (_: Exception) {
        null
    }

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
                "يوتيوب يطلب تحقّقًا إضافيًا من شبكتك الحالية وتعذّر التجاوز بكل القنوات المجرَّبة تلقائيًا. " +
                        "الحل داخل هذا الحوار: أرفق ملف كوكيز من متصفحك بخيار «ملف كوكيز» (خيار متقدم أدناه) — أو جرّب شبكة أخرى."
            "video unavailable" in s ->
                "المقطع غير متاح (حُذف أو خاص أو محجوب في بلدك)."
            "is not a valid url" in s || "unable to download webpage" in s || "network" in s ->
                "تعذّر الوصول إلى الشبكة أو الرابط غير صالح — تأكد من الاتصال ومن صحة الرابط."
            "unsupported url" in s ->
                "الرابط غير مدعوم — استخدم رابط مقطع يوتيوب أو اكتب كلمات للبحث."
            "no supported format" in s || "requested format is not available" in s ->
                "لا تتوفر صيغة صوتية قابلة للاستيراد لهذا المقطع عبر كل القنوات المجرَّبة."
            "ffmpeg" in s ->
                "احتاج التحميل أداة خارجية رفضت الشبكة — أعد المحاولة."
            "permission denied" in s || "errno 13" in s ->
                "رفض النظام الكتابة في مجلد محمي أثناء التحميل — هذه النسخة تعمل في مجلد مؤقت آمن، " +
                        "حدّث التطبيق وأعد المحاولة، وإن استمر فأعد تثبيت التطبيق في مسار المستخدم الافتراضي."
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

    private fun com.google.gson.JsonElement.asJsonArrayOrNull(): com.google.gson.JsonArray? =
        if (isJsonNull || !isJsonArray) null else try { asJsonArray } catch (_: Exception) { null }

    private fun com.google.gson.JsonElement.asJsonObjectOrNull(): com.google.gson.JsonObject? =
        if (isJsonNull || !isJsonObject) null else try { asJsonObject } catch (_: Exception) { null }
}
