package com.saidjte.schoolradio.desktop.data

/**
 * وضع «على الهواء» لشاشة ثانية — طلب المستخدم v0.32.0 «عرض: وضع على الهواء
 * ملء الشاشة لشاشة ثانية يتابعها الطلاب»: منطق العرض نقي قبل الواجهة —
 * اختيار الشاشة الثانية على الحاسوب، وحساب ما يراه الطلاب لحظة بلحظة
 * (الفقرة الجارية ومتبقيها، الموسيقى الآن، التالية في الحلقة).
 */

/** مستطيل شاشة بالبكسل — تجريد شاشات AWT كي يكون الاختبار بلا رسوميات. */
data class ScreenRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** نوع البطاقة الرئيسية على شاشة الطلاب. */
enum class HeadlineKind { RUN_ITEM, MUSIC, NONE }

/**
 * نموذج ما يعرضه الطلاب — محسوب دفعة واحدة من حالة الاستوديو، فالنافذة
 * ترسم فقط ولا تحسب شيء (كل القرارات هنا نقية ومختبرة).
 */
data class OnAirModel(
    /** على الهواء فعليًا (مايك حي أو تسجيل أو موسيقى تعمل) — يضيء الشارة. */
    val onAir: Boolean,
    /** عنوان البطاقة الرئيسية: الفقرة الجارية من الحلقة وإلا مقطع الموسيقى. */
    val headline: String?,
    val headlineKind: HeadlineKind,
    /** سطر الزمن تحت العنوان: «00:12 من 00:30» للفقرة أو «01:23 / 03:45» للموسيقى. */
    val timeText: String?,
    /** تقدم البطاقة الرئيسية 0..1 (null = بلا زمن مخطط). */
    val headlineProgress: Float?,
    /** المتبقي للفاصل الآلي — يصبغه تنبيهًا الواجهة عند الحدود. */
    val remainingText: String?,
    val warn: Boolean,
    val fired: Boolean,
    /** عنوان الفقرة التالية في الحلقة (بعد الجارية مباشرة). */
    val nextTitle: String?,
    /** بطاقة الموسيقى الثانوية تحت الفقرة الجارية حين يعمل المشغل معها. */
    val musicTitle: String?,
    val musicTimeText: String?,
    val musicProgress: Float,
    val musicPaused: Boolean
)

object OnAirDisplayLogic {

    /** تنسيق mm:ss — الأزمنة على شاشة الطلاب كلها أقل من ساعة، والسالب يُقص إلى صفر. */
    fun mmss(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        return String.format(java.util.Locale.ROOT, "%02d:%02d", s / 60L, s % 60L)
    }

    /**
     * الشاشة الثانية: أول شاشة غير شاشة المذيع (الافتراضية).
     * تعيد null على حاسوب بشاشة واحدة فتُفتح نافذة سحب عادية بدل ملء الشاشة.
     */
    fun pickSecondary(screens: List<ScreenRect>, defaultIndex: Int): ScreenRect? {
        if (screens.size < 2) return null
        val d = defaultIndex.coerceIn(0, screens.size - 1)
        return screens.withIndex().firstOrNull { (i, _) -> i != d }?.value
    }

    /** نسبة تقدم محبوسة 0..1 — قاسم صفر أو سالب يعود صفرًا. */
    private fun progress(num: Long, den: Long): Float {
        if (den <= 0L) return 0f
        return (num.toFloat() / den.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * تركيب نموذج شاشة الطلاب من حالة الاستوديو الحية:
     * الفقرة الجارية في الحلقة هي العنوان الأول دائمًا (إذاعة المدرسة برامجها
     * أولًا)، والموسيقى عنوانًا حين لا حلقة، وبطاقة الموسيقى الثانوية تظهر
     * مع الفقرة إن كان المشغل يعمل أيضًا.
     */
    fun buildModel(
        runActive: Boolean,
        runIndex: Int,
        runElapsedMs: Long,
        remainingMs: Long?,
        warn: Boolean,
        scheduleTitles: List<String>,
        schedulePlannedSeconds: List<Long>,
        musicTitle: String?,
        musicPositionMs: Long,
        musicDurationMs: Long,
        musicPlaying: Boolean,
        micLive: Boolean,
        recording: Boolean
    ): OnAirModel {
        val onAir = micLive || recording || musicPlaying
        val item = if (runActive) scheduleTitles.getOrNull(runIndex) else null
        val plannedSec = if (runActive) schedulePlannedSeconds.getOrNull(runIndex) ?: 0L else 0L

        val headlineKind: HeadlineKind
        val headline: String?
        var timeText: String? = null
        var headlineProgress: Float? = null
        var nextTitle: String? = null

        if (item != null) {
            headlineKind = HeadlineKind.RUN_ITEM
            headline = item
            val plannedMs = plannedSec * 1000L
            timeText = if (plannedSec > 0L) "${mmss(runElapsedMs)} من ${mmss(plannedMs)}" else mmss(runElapsedMs)
            headlineProgress = if (plannedSec > 0L) progress(runElapsedMs, plannedMs) else null
            nextTitle = scheduleTitles.getOrNull(runIndex + 1)
        } else if (!musicTitle.isNullOrBlank() && (musicPlaying || musicPositionMs > 0L || musicDurationMs > 0L)) {
            headlineKind = HeadlineKind.MUSIC
            headline = musicTitle
            timeText = "${mmss(musicPositionMs)} / ${mmss(musicDurationMs)}"
            headlineProgress = progress(musicPositionMs, musicDurationMs)
        } else {
            headlineKind = HeadlineKind.NONE
            headline = null
        }

        // بطاقة الموسيقى الثانوية: تظهر تحت الفقرة الجارية حين يصاحبها صوت موسيقى
        val musicCard = if (headlineKind == HeadlineKind.RUN_ITEM && !musicTitle.isNullOrBlank() && musicPlaying) {
            musicTitle
        } else null

        val remainingText = remainingMs?.let { r ->
            if (r > 0L) "المتبقي ${mmss(r)}" else "تجاوز الزمن ${mmss(-r)}"
        }

        return OnAirModel(
            onAir = onAir,
            headline = headline,
            headlineKind = headlineKind,
            timeText = timeText,
            headlineProgress = headlineProgress,
            remainingText = remainingText,
            warn = warn,
            fired = remainingMs != null && remainingMs <= 0L,
            nextTitle = nextTitle,
            musicTitle = musicCard,
            musicTimeText = if (musicCard != null) "${mmss(musicPositionMs)} / ${mmss(musicDurationMs)}" else null,
            musicProgress = if (musicCard != null) progress(musicPositionMs, musicDurationMs) else 0f,
            musicPaused = !musicPlaying
        )
    }
}
