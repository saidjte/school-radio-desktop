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

/** مشروع المونتاج الكامل: مقاطع مرتّبة + فاصل أو كروس فيد بين المقاطع. */
data class MontageProject(
    val clips: List<MontageClip> = emptyList(),
    val gapMs: Long = 0L,
    /** كروس فيد موحّد بين كل مقطعين متجاورين — أكبر من صفر يُغني عن الفاصل. */
    val crossfadeMs: Long = 0L,
    val title: String = "مونتاج الحصة",
    /** موضع رأس التشغيل المحفوظ — يُستعاد مع الجلسة عند إعادة الفتح. */
    val playheadMs: Long = 0L
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
