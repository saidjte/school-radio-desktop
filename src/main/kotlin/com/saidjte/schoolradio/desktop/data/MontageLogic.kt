package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * صفحة المونتاج (v0.33.0 «أضف صفحة المونتاج»): مشروع تحرير صوتي كامل يعمل
 * دون اتصال — مقاطع مرتّبة تُقصّ بدايتها ونهايتها ويوضع لمقطعٍ مستوىٌ
 * وتلاشٍ داخلي وخارجي وفاصل موحّد بين المقاطع، ثم يُصيَّر المشروع كله في
 * ملف WAV واحد 44.1k ستيريو جاهز للبث.
 * كل القرارات هنا نقية على البيانات — تُختبر وحدةً بلا صوت ولا ملفات،
 * فحسابات المدد والقص والتلاشين هي موضع الأخطاء الصامتة.
 */

/** مقطع داخل مشروع المونتاج. */
data class MontageClip(
    val id: String,
    val path: String,
    val title: String,
    /** مدة الملف المصدر كاملة — معلوماتية وحدود لنافذة القص. */
    val durationMs: Long = 0L,
    /** نافذة القص داخل الملف المصدر: [startMs, endMs). */
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    /** المستوى بالمئة: 100 = كما هو، 200 = الضعف، 50 = النصف. */
    val volumePercent: Int = MontageLogic.VOLUME_DEFAULT,
    /** تلاشٍ داخل وخارج بالنافذة المقطوعة (مللي ثانية). */
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

/** مشروع المونتاج الكامل: مقاطع مرتّبة + فاصل موحّد بين المقاطع. */
data class MontageProject(
    val clips: List<MontageClip> = emptyList(),
    val gapMs: Long = 0L,
    val title: String = "مونتاج الحصة"
)

object MontageLogic {
    const val GAP_MAX_MS = 10_000L
    const val FADE_MAX_MS = 10_000L
    const val VOLUME_MIN = 1
    const val VOLUME_MAX = 200
    const val VOLUME_DEFAULT = 100

    /** حد مقاطع المشروع — يحمي الواجهة والملف من العبث (قائمة طويلة لا تُدار). */
    const val MAX_CLIPS = 60

    /** زمن المقطع المقطوع فعليًا في المونتاج. */
    fun clipDurationMs(c: MontageClip): Long = (c.endMs - c.startMs).coerceAtLeast(0L)

    /** الزمن الكلي للمونتاج: مجموع المقاطع + فاصل بين كل مقطعين متجاورين. */
    fun totalDurationMs(clips: List<MontageClip>, gapMs: Long): Long =
        clips.sumOf { clipDurationMs(it) } + gapMs * (clips.size - 1).coerceAtLeast(0)

    /** الفاصل الفعلي بعد الموضع i — لا فاصل بعد الأخير ولا قبل الأول. */
    fun gapAfter(index: Int, count: Int, gapMs: Long): Long =
        if (index in 0 until (count - 1).coerceAtLeast(0)) gapMs else 0L

    /**
     * حبس نافذة القص داخل مدة الملف: البداية ≥ 0، النهاية بعد البداية وإلى
     * نهاية الملف. نافذة فارغة (نهاية ≤ بداية) تعني «الملف كاملًا» كي يضاف
     * المقطع حديثًا كاملًا قبل أن يضبطه المذيع.
     */
    fun clampWindow(startMs: Long, endMs: Long, fileDurationMs: Long): Pair<Long, Long> {
        val dur = fileDurationMs.coerceAtLeast(0L)
        val s = startMs.coerceIn(0L, dur)
        var e = endMs.coerceIn(0L, dur)
        if (e <= s) e = dur
        if (e <= s) e = s // ملف صفري المدة — نافذة فارغة
        return s to e
    }

    fun clampGap(ms: Long): Long = ms.coerceIn(0L, GAP_MAX_MS)
    fun clampFade(ms: Long): Long = ms.coerceIn(0L, FADE_MAX_MS)
    fun clampVolume(p: Int): Int = p.coerceIn(VOLUME_MIN, VOLUME_MAX)

    /**
     * كسب نقطة زمنية داخل النافذة المقطوعة (0..1، يُضرب في مستوى المقطع):
     * منحدر خطي صاعد داخل التلاشي الداخلي وخطي هابط في الخارجي، ومنتصف
     * النافذة كاملًا. الدالة نقية تُختبر بدقة — منحنى خاطئ يعني طقطقة
     * مسموعة على الهواء عند لحظة الوصل.
     */
    fun fadeGainAt(offsetMs: Long, visibleMs: Long, fadeInMs: Long, fadeOutMs: Long): Float {
        if (visibleMs <= 0) return 0f
        var g = 1f
        val fi = fadeInMs.coerceIn(0L, visibleMs)
        if (fi > 0 && offsetMs < fi) g *= offsetMs.toFloat() / fi
        val fo = fadeOutMs.coerceIn(0L, visibleMs)
        if (fo > 0) {
            val remaining = visibleMs - offsetMs
            if (remaining < fo) g *= remaining.toFloat() / fo
        }
        return g.coerceIn(0f, 1f)
    }

    /**
     * تعقيم المشروع قبل التصيير: المقاطع الموجودة على القرص فقط، والنوافذ
     * والمستويات والتلاشين محبوسة — فلا ملف مفقود يكسر التصيير ولا قيمة
     * عبثية من ملف محرَّر يدويًا.
     */
    fun sanitize(clips: List<MontageClip>): List<MontageClip> = clips.mapNotNull { c ->
        if (!File(c.path).isFile) return@mapNotNull null
        val (s, e) = clampWindow(c.startMs, c.endMs, c.durationMs)
        c.copy(
            startMs = s, endMs = e,
            volumePercent = clampVolume(c.volumePercent),
            fadeInMs = clampFade(c.fadeInMs),
            fadeOutMs = clampFade(c.fadeOutMs)
        )
    }.take(MAX_CLIPS)

    /** معرّف مقطع جديد فريد داخل المشروع. */
    fun newClipId(): String = "c${System.nanoTime()}"
}

/** مخزن مشروع المونتاج — montage.json بجانب بيانات التطبيق، كتابة ذرّية. */
class MontageStore(private val file: File) {
    fun project(): MontageProject = Json.load(file, MontageProject())
    fun save(p: MontageProject): Boolean = Json.save(file, p)
}
