package com.saidjte.schoolradio.desktop.data

import java.io.File
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * صفحة المونتاج (v0.33.0 «أضف صفحة المونتاج»): مشروع تحرير صوتي كامل يعمل
 * دون اتصال — مقاطع مرتّبة تُقصّ بدايتها ونهايتها ويوضع لمقطعٍ مستوىٌ
 * وتلاشٍ داخلي وخارجي وفاصل موحّد بين المقاطع، ثم يُصيَّر المشروع كله في
 * ملف WAV واحد 44.1k ستيريو جاهز للبث.
 *
 * v0.34.0 «الترقية الاحترافية» (البنود 1–15): كروس فيد بين المقاطع المتجاورة
 * بقوة متساوية، تلاشٍ دقيق مضاد للنقرات على كل حافة قصّ، تطبيع المستوى،
 * تقسيم عند الرأس، سجل تراجع/إعادة، حلقة A/B، ورياضيات الخط الزمني الواحد
 * (بدايات مقرّبة مع التداخل). كل القرارات هنا نقية على البيانات — تُختبر
 * وحدةً بلا صوت ولا ملفات، فحسابات المدد والقص والتلاشين هي موضع الأخطاء
 * الصامتة.
 *
 * v0.35.0 (البنود 16–17): مسار موسيقى مرافقة بمواضع مطلقة موازٍ لمسار
 * التعليق، مع خفض تلقائي نقي (عمق/هجوم/إفلات) يخرج من نطاقات النطق نفسها،
 * وعلامات مسمّاة على الخط الزمني — والقوالب الجاهزة في MontageTemplates.kt.
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

/**
 * مقطع موسيقى مرافقة (v0.35.0 — البند 16 «مسار مزدوج: تعليق + موسيقى»):
 * يعيش على مسار ثانٍ موازٍ للتعليق ويُوضع بموضع مطلق على الخط الزمني —
 * لا يُزاح بإضافة المقاطع ولا يشاركها الترتيب، وله نافذة قصّ داخل ملفه
 * ومستوى وتلاشين كأي مقطع.
 */
data class MontageMusicClip(
    val id: String,
    val path: String,
    val title: String,
    /** مدة الملف المصدر كاملة — مرجع نافذة القصّ. */
    val durationMs: Long = 0L,
    /** الموضع المطلق على الخط الزمني للمونتاج. */
    val startMs: Long = 0L,
    /** نافذة القصّ داخل الملف المصدر: [startInFileMs, endInFileMs) — الفارغة تعني الملف كاملًا. */
    val startInFileMs: Long = 0L,
    val endInFileMs: Long = 0L,
    val volumePercent: Int = MontageLogic.VOLUME_DEFAULT,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L
)

/** علامة مسمّاة على الخط الزمني (v0.35.0 — البند 17): «الفاصل»، «النشيد»… */
data class MontageMarker(
    val id: String,
    val name: String,
    val tMs: Long = 0L
)

/** مشروع المونتاج الكامل: مقاطع مرتّبة + فاصل أو كروس فيد بين المقاطع. */
data class MontageProject(
    val clips: List<MontageClip> = emptyList(),
    val gapMs: Long = 0L,
    /** كروس فيد موحّد بين كل مقطعين متجاورين — أكبر من صفر يُغني عن الفاصل. */
    val crossfadeMs: Long = 0L,
    val title: String = "مونتاج الحصة",
    /** موضع رأس التشغيل المحفوظ — يُستعاد مع الجلسة عند إعادة الفتح. */
    val playheadMs: Long = 0L,
    // ==== v0.35.0: المسار المزدوج والخفض التلقائي والعلامات ====
    /** مسار الموسيقى المرافقة — مواضع مطلقة موازية لمسار التعليق. */
    val musicClips: List<MontageMusicClip> = emptyList(),
    /** الخفض التلقائي للموسيقى حين يُسمع التعليق — سلوك المزج الإذاعي الحقيقي. */
    val duckingEnabled: Boolean = true,
    /** عمق الخفض بالديسيبل (سالب). */
    val duckDb: Int = MontageLogic.DUCK_DB_DEFAULT,
    /** زمن الهجوم (النزول إلى الخفض) والإفلات (العودة) بالميلي ثانية. */
    val duckAttackMs: Long = MontageLogic.DUCK_ATTACK_DEFAULT,
    val duckReleaseMs: Long = MontageLogic.DUCK_RELEASE_DEFAULT,
    /** العلامات المسمّاة على الخط الزمني — مرتبة بالزمن عند الحفظ. */
    val markers: List<MontageMarker> = emptyList()
)

/** مغلّف عرض مقطع: ذروات دلاء النافذة بعد المستوى والتلاشين (0..1 معياري). */
class MontageEnvelope(
    val clipId: String,
    val peaks: FloatArray,
    val clipped: BooleanArray
) {
    /** ذروة الدلو عند فهرس آمن — خارجه صمت. */
    fun peakAt(bucket: Int): Float =
        if (bucket < 0 || bucket >= peaks.size) 0f else peaks[bucket]

    /** هل الدلو قرب التشبع (≥ عتبة التحذير)؟ */
    fun clippedAt(bucket: Int): Boolean =
        bucket in clipped.indices && clipped[bucket]

    /** أعلى ذروة في المغلّف — لضبط مقياس العرض. */
    fun maxPeak(): Float = peaks.maxOrNull() ?: 0f
}

object MontageLogic {
    const val GAP_MAX_MS = 10_000L
    const val FADE_MAX_MS = 10_000L
    const val VOLUME_MIN = 1
    const val VOLUME_MAX = 200
    const val VOLUME_DEFAULT = 100

    /** حد مقاطع المشروع — يحمي الواجهة والملف من العبث (قائمة طويلة لا تُدار). */
    const val MAX_CLIPS = 60

    // ==== v0.35.0: ثوابت المسار المزدوج والعلامات ====

    /** حد مقاطع الموسيقى المرافقة — سرير صوتي لا مختانٍ متعدد. */
    const val MAX_MUSIC_CLIPS = 12
    /** حد العلامات المسمّاة. */
    const val MAX_MARKERS = 40

    const val DUCK_DB_MIN = -30
    const val DUCK_DB_MAX = -3
    const val DUCK_DB_DEFAULT = -12
    const val DUCK_ATTACK_MIN = 50L
    const val DUCK_ATTACK_MAX = 2_000L
    const val DUCK_ATTACK_DEFAULT = 300L
    const val DUCK_RELEASE_MIN = 100L
    const val DUCK_RELEASE_MAX = 5_000L
    const val DUCK_RELEASE_DEFAULT = 800L

    /** تحويل الديسيبل إلى كسب خطي — نفس رياضيات Ducker في البث الحي. */
    fun dbToAmp(db: Double): Float = Math.pow(10.0, db / 20.0).toFloat()

    fun clampDuckDb(db: Int): Int = db.coerceIn(DUCK_DB_MIN, DUCK_DB_MAX)
    fun clampDuckAttack(ms: Long): Long = ms.coerceIn(DUCK_ATTACK_MIN, DUCK_ATTACK_MAX)
    fun clampDuckRelease(ms: Long): Long = ms.coerceIn(DUCK_RELEASE_MIN, DUCK_RELEASE_MAX)

    /** مدة نافذة مقطع الموسيقى على الخط الزمني — النافذة الفارغة تعني «الملف كاملًا». */
    fun musicDurationMs(m: MontageMusicClip): Long {
        val (s, e) = clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
        return (e - s).coerceAtLeast(0L)
    }

    /**
     * نطاقات النطق الفعلية على الخط الزمني — وقود الخفض التلقائي: نوافذ
     * مقاطع التعليق مدموجة حيث تتلاصق أو تتداخل (بكروس فيد) فالموسيقى لا
     * تتهادى بين مقطعين متلاصقين.
     */
    fun narrationIntervals(clips: List<MontageClip>, gapMs: Long, crossfadeMs: Long): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>()
        if (clips.isEmpty()) return out
        val starts = clipStartMs(clips, gapMs, crossfadeMs)
        var i = 0
        while (i < clips.size) {
            var s = starts[i]
            var e = s + clipDurationMs(clips[i])
            var j = i + 1
            while (j < clips.size) {
                val ns = starts[j]
                if (ns <= e) { // تتلاصق أو تتداخل — نطق متصل
                    e = maxOf(e, ns + clipDurationMs(clips[j]))
                    j++
                } else break
            }
            out.add(s to e)
            i = j
        }
        return out
    }

    /**
     * كسب الموسيقى عند لحظة زمنية تحت الخفض التلقائي (v0.35.0 — البند 16):
     * داخل نطاق النطق = عمق الخفض، وقبله هجوم خطي بزمن [duckAttackMs]،
     * وبعده إفلات خطي بزمن [duckReleaseMs] — ونطاقات متقاربة تأخذ الأعمق
     * (الأصغر) فلا تتقافز الموسيقى بين فقرة وفترة قصيرة. نقي وقابل للاختبار
     * بدقة: منحنى خاطئ هنا يعني موسيقى تقفز مسموعًا على الهواء.
     */
    fun duckGainAt(
        tMs: Long,
        intervals: List<Pair<Long, Long>>,
        duckDb: Int,
        attackMs: Long,
        releaseMs: Long,
        enabled: Boolean = true
    ): Float {
        if (!enabled || intervals.isEmpty()) return 1f
        val duck = dbToAmp(duckDb.toDouble())
        val atk = attackMs.coerceAtLeast(1L)
        val rel = releaseMs.coerceAtLeast(1L)
        var g = 1f
        for ((s, e) in intervals) {
            when {
                tMs >= s && tMs < e -> g = minOf(g, duck)
                tMs < s && tMs >= s - atk -> {
                    val p = (s - tMs).toFloat() / atk
                    g = minOf(g, duck + (1f - duck) * p)
                }
                tMs >= e && tMs < e + rel -> {
                    val p = (tMs - e).toFloat() / rel
                    g = minOf(g, duck + (1f - duck) * p)
                }
            }
        }
        return g.coerceIn(0f, 1f)
    }

    /**
     * الزمن الكلي للمشروع بالمسار المزدوج: أبعدها بين نهاية التعليق
     * ونهاية الموسيقى — فمقطع موسيقى يمدّ الخط الزمني إن طال بعد الختام.
     */
    fun totalDurationFull(project: MontageProject): Long {
        var t = totalDurationMs(project.clips, project.gapMs, project.crossfadeMs)
        for (m in project.musicClips) {
            t = maxOf(t, m.startMs.coerceAtLeast(0L) + musicDurationMs(m))
        }
        return t
    }

    // ==== v0.34.0: ثوابت الاحتراف ====

    /** تلاشٍ دقيق مضاد للنقرات على كل حافة قصّ — غير مسموع ويقتل الطقطقة. */
    const val MICRO_FADE_MS = 8L

    /** هدف التطبيع: ذروة بعد الكسب ≈ 95٪ من المدى الكامل. */
    const val NORMALIZE_TARGET = 31_000

    /** عتبة التحذير من التشبع: ذروة بعد الكسب ≥ 32000/32768. */
    const val CLIP_WARN_LEVEL = 32_000f / 32768f

    /** زمن المقطع المقطوع فعليًا في المونتاج. */
    fun clipDurationMs(c: MontageClip): Long = (c.endMs - c.startMs).coerceAtLeast(0L)

    /** الزمن الكلي للمونتاج: مجموع المقاطع + فاصل بين كل مقطعين متجاورين. */
    fun totalDurationMs(clips: List<MontageClip>, gapMs: Long): Long =
        totalDurationMs(clips, gapMs, 0L)

    /**
     * الزمن الكلي مع الكروس فيد: كل حدّ بين مقطعين هو تداخلٌ يُخصم من المجموع
     * (كروس فيد) أو فاصلٌ يُضاف إليه — الكروس فيد إن فعُل يُغني عن الفاصل.
     */
    fun totalDurationMs(clips: List<MontageClip>, gapMs: Long, crossfadeMs: Long): Long {
        if (clips.isEmpty()) return 0L
        val starts = clipStartMs(clips, gapMs, crossfadeMs)
        return starts.last() + clipDurationMs(clips.last())
    }

    /**
     * التداخل الفعّال على الحدّ بين المقطعين المتجاورين: الكروس فيد إن فعُل —
     * محبوسًا ألّا يتجاوز زمن أيٍّ من المقطعين فلا تنهار مدة أحدهما.
     */
    fun effectiveOverlap(prevDurMs: Long, nextDurMs: Long, crossfadeMs: Long): Long =
        crossfadeMs.coerceAtLeast(0L).coerceAtMost(prevDurMs).coerceAtMost(nextDurMs)

    /** بدايات المقاطع على الخط الزمني للوجهة (مللي ثانية) — عقد العرض والمدخلات. */
    fun clipStartMs(clips: List<MontageClip>, gapMs: Long, crossfadeMs: Long): List<Long> {
        val out = ArrayList<Long>(clips.size)
        var t = 0L
        for (i in clips.indices) {
            out.add(t)
            val d = clipDurationMs(clips[i])
            if (i < clips.size - 1) {
                val xf = effectiveOverlap(d, clipDurationMs(clips[i + 1]), crossfadeMs)
                t += if (xf > 0L) d - xf else d + gapMs.coerceAtLeast(0L)
            }
        }
        return out
    }

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
    fun clampCrossfade(ms: Long): Long = ms.coerceIn(0L, FADE_MAX_MS)

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
     * كسب مضاد النقرات (v0.34.0): منحدر خطي دقيق [MICRO_FADE_MS] على كل حافة
     * نافذة — يُضرب دائمًا في الكسوب الكلي فكل قصّة تخرج بلا طقطقة حتى بلا
     * تلاشٍ من المستخدم.
     */
    fun microFadeGainAt(offsetMs: Long, visibleMs: Long): Float {
        if (visibleMs <= 0) return 0f
        var g = 1f
        if (offsetMs < MICRO_FADE_MS) g = offsetMs.toFloat() / MICRO_FADE_MS
        val remaining = visibleMs - offsetMs
        if (remaining < MICRO_FADE_MS) g = minOf(g, remaining.toFloat() / MICRO_FADE_MS)
        return g.coerceIn(0f, 1f)
    }

    /**
     * كسب الكروس فيد بقوة متساوية (v0.34.0): المقطع المغادر يهبط بـ cos والمقيم
     * يصعد بـ sin على نفس التقدّم p∈[0,1] — مجموع الطاقة ثابت فلا هبوط مسموع
     * عند الوصل، العرف الاحترافي للمواد الموسيقية.
     */
    fun crossfadeOutGain(p: Float): Float {
        val q = p.coerceIn(0f, 1f)
        return cos(q * (Math.PI / 2.0).toFloat())
    }

    fun crossfadeInGain(p: Float): Float {
        val q = p.coerceIn(0f, 1f)
        return sin(q * (Math.PI / 2.0).toFloat())
    }

    /**
     * تقسيم مقطع عند موضع داخل نافذته (v0.34.0 — مفتاح S): مقطعان متجاوران،
     * الأول يحمل التلاشي الداخلي بلا خارجي والثاني يحمل الخارجي بلا داخلي —
     * فالمونتاج يبقى سليم الصوت عند نقطة الفصل. يعيد null خارج النافذة.
     */
    fun splitClipAt(clip: MontageClip, offsetInWindowMs: Long): Pair<MontageClip, MontageClip>? {
        val dur = clipDurationMs(clip)
        if (offsetInWindowMs <= 0L || offsetInWindowMs >= dur) return null
        val cut = clip.startMs + offsetInWindowMs
        val a = clip.copy(endMs = cut, fadeOutMs = 0L, id = newClipId())
        val b = clip.copy(startMs = cut, fadeInMs = 0L, id = newClipId())
        return a to b
    }

    /**
     * مستوى التطبيع (v0.34.0): من الذروة الخام للنافذة يُحسب المستوى الذي يجعل
     * الذروة بعد الكسب عند هدف التطبيع — محبوسًا داخل 1..200٪.
     */
    fun normalizedVolumePercent(rawPeak: Int): Int {
        if (rawPeak <= 0) return VOLUME_DEFAULT
        return clampVolume(Math.round(NORMALIZE_TARGET * 100.0 / rawPeak).toInt())
    }

    /** هل مستوى بعد الكسب قرب التشبع (تحذير VU)؟ */
    fun isClippedLevel(postGainPeak: Float): Boolean = postGainPeak >= CLIP_WARN_LEVEL

    /**
     * نبضة الحلقة A/B (v0.34.0): إذا تجاوز موضع التشغيل نهاية الحلقة الصحيحة
     * يُعاد إلى بدياتها — وإلا لا شيء. حلقة مبتورة لا تُنفَّذ.
     */
    fun loopSeekMs(playMs: Long, loopStartMs: Long, loopEndMs: Long): Long? =
        if (loopEndMs > loopStartMs && playMs >= loopEndMs) loopStartMs else null

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

    /** تعقيم مسار الموسيقى: ملف موجود، موضع غير سالب، نافذة ومستوى وتلاشين محبوسة. */
    fun sanitizeMusic(clips: List<MontageMusicClip>): List<MontageMusicClip> = clips.mapNotNull { m ->
        if (!File(m.path).isFile) return@mapNotNull null
        val (s, e) = clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
        m.copy(
            startMs = m.startMs.coerceAtLeast(0L),
            startInFileMs = s, endInFileMs = e,
            volumePercent = clampVolume(m.volumePercent),
            fadeInMs = clampFade(m.fadeInMs),
            fadeOutMs = clampFade(m.fadeOutMs)
        )
    }.take(MAX_MUSIC_CLIPS)

    /** تعقيم العلامات: اسم غير فارغ محدود، موضع غير سالب، وسقف العدد. */
    fun sanitizeMarkers(markers: List<MontageMarker>): List<MontageMarker> = markers.mapNotNull { mk ->
        val n = mk.name.trim()
        if (n.isEmpty()) return@mapNotNull null
        mk.copy(name = n.take(24), tMs = mk.tMs.coerceAtLeast(0L))
    }.take(MAX_MARKERS)

    /** معرّف مقطع موسيقى جديد فريد. */
    fun newMusicClipId(): String = "mu${System.nanoTime()}"

    /** معرّف علامة جديد فريد. */
    fun newMarkerId(): String = "mk${System.nanoTime()}"

    /** معرّف مقطع جديد فريد داخل المشروع. */
    fun newClipId(): String = "c${System.nanoTime()}"
}

/**
 * سجل تراجع/إعادة للمونتاج (v0.34.0 — البند 3): لقطات مشروع كاملة صغيرة
 * قابلة للنسخ تُدفع قبل كل تغيير، والتراجع يسحب آخر لقطة ويدفع الحالية
 * لإعادة التطبيق. السقف يمنع تضخم الذاكرة في جلسة طويلة.
 */
class MontageHistory(private val cap: Int = 100) {
    private val undoStack = ArrayDeque<MontageProject>()
    private val redoStack = ArrayDeque<MontageProject>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** يُستدعى قبل كل تغيير: الحالة قبلية تُدفع للتراجع وتُفرَّغ سلسلة الإعادة. */
    fun record(before: MontageProject) {
        undoStack.addLast(before)
        while (undoStack.size > cap) undoStack.removeFirst()
        redoStack.clear()
    }

    /** يُسجَّل يدويًا عند بدء سحب متعدد التعديلات — لقطة واحدة لعملية كاملة. */
    fun pushCurrent(current: MontageProject) {
        undoStack.addLast(current)
        while (undoStack.size > cap) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo(current: MontageProject): MontageProject? {
        val prev = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        return prev
    }

    fun redo(current: MontageProject): MontageProject? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        return next
    }
}

/** مخزن مشروع المونتاج — montage.json بجانب بيانات التطبيق، كتابة ذرّية. */
class MontageStore(private val file: File) {
    fun project(): MontageProject = Json.load(file, MontageProject())
    fun save(p: MontageProject): Boolean = Json.save(file, p)
}
