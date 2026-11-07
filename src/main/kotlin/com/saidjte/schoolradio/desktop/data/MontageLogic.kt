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
 *
 * v0.36.0 «المونتاج متعدد المسارات ومسطرة العمليات»: عدة خطوط عمل حقيقية —
 * مسارات حرّة مسمّاة بلون ومستوى وكتم/منفرد تعيش بالتوازي مع مسار التعليق،
 * ومسطرة العمليات تُسحَب عليها لتحديد منطقة زمنية تُحذف من المشروع كله
 * (حذف متموّج Ripple: كل ما بعد المنطقة يُسحب يسارًا فلا فجوة يتيمة)،
 * وتقسيم مقاطع المسارات بالنقر، والتقاط على العلامات وحواف المقاطع.
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
 * يعيش على مسار موازٍ للتعليق ويُوضع بموضع مطلق على الخط الزمني —
 * لا يُزاح بإضافة المقاطع ولا يشاركها الترتيب، وله نافذة قصّ داخل ملفه
 * ومستوى وتلاشين كأي مقطع.
 *
 * v0.36.0 «عدة خطوط عمل»: نوع المقطع نفسه يخدم كل المسارات الحرة —
 * [trackId] يحدد أي مسار يسكنه، والفارغ يعني المسار الافتراضي «موسيقى»
 * (مشاريع v0.35.0 تُحمّل كما هي وتُهاجر منطقيًا دون مساس ملفها).
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
    val fadeOutMs: Long = 0L,
    /** معرّف المسار الحر المالك (v0.36.0) — الفارغ = المسار الافتراضي «موسيقى». */
    val trackId: String = ""
)

/**
 * مسار عمل حر (v0.36.0 «عدة خطوط عمل»): خط أفقي على الخط الزمني يسكنه
 * مقاطع بمواضع مطلقة — موسيقى، مؤثرات، تعليق ثانٍ، أجواء — بمستوى موحّد
 * وكتمن/منفرد كوحدة المزج الإذاعي. مسار التعليق (المتسلسل) خارج هذه القائمة
 * فهو العمود الفقري الثابت للمشروع لا يُنشأ ولا يُحذف.
 */
data class MontageTrack(
    val id: String,
    val name: String,
    /** فهرس اللون من لوحة ألوان المسارات — تمايز بصري بين الخطوط. */
    val colorIndex: Int = 0,
    val volumePercent: Int = MontageLogic.VOLUME_DEFAULT,
    val muted: Boolean = false,
    val solo: Boolean = false
)

/**
 * أداة المؤشر على الخط الزمني (v0.36.0 — أدوات مونتاج احترافية):
 * [SELECT] التحديد والحركة والتقليم، و[SPLIT] المقصّ — النقر داخل مقطع
 * يشطره عند نقطة النقر بدل تحديده.
 */
enum class MontageTool { SELECT, SPLIT }

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
    val markers: List<MontageMarker> = emptyList(),
    // ==== v0.36.0: عدة خطوط عمل ومسطرة العمليات ====
    /** المسارات الحرة (عدا مسار التعليق الثابت) — قائمة فارغة تعني مشروعًا قديمًا يُهاجر منطقيًا إلى «موسيقى». */
    val tracks: List<MontageTrack> = emptyList(),
    /** المنطقة المحددة على المسطرة [selectionStartMs, selectionEndMs) — null = لا تحديد. */
    val selectionStartMs: Long = -1L,
    val selectionEndMs: Long = -1L
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

    // ==== v0.36.0: ثوابت المسارات المتعددة ومسطرة العمليات ====

    /** معرّف مسار التعليق الثابت — خارج قائمة المسارات الحرة ولا يُنشأ ولا يُحذف. */
    const val VOICE_TRACK_ID = "voice"

    /** معرّف المسار الحر الافتراضي «موسيقى» — مقاطع بلا trackId تعيش عليه. */
    const val DEFAULT_TRACK_ID = "music"

    /** سقف المسارات الحرة — عدة خطوط عمل حقيقية بلا فوضى عمودية. */
    const val MAX_TRACKS = 5

    /** سقف مقاطع المسارات الحرة مجتمعة. */
    const val MAX_TRACK_CLIPS = 60

    /** لوحة ألوان المسارات — تمايز بصري بين خطوط العمل. */
    val TRACK_COLORS = intArrayOf(
        0xFF7E57C2.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(),
        0xFF5C6BC0.toInt(), 0xFFD81B60.toInt()
    )

    /** الحد الأدنى لعرض المنطقة المحددة على المسطرة (مللي ثانية) — أصغر منها يُهمَل. */
    const val SELECTION_MIN_MS = 30L

    /** حد عتبة التقاط السحب (مللي ثانية) — الأقرب ضمنه يلتقط. */
    const val SNAP_THRESHOLD_MS = 120L

    /** شبكة التقاط عامة نصف ثانية — أساس احترافي حين لا علامة ولا حافة قريبة. */
    const val SNAP_GRID_MS = 500L

    /** اسم المسار الحر الافتراضي. */
    const val DEFAULT_TRACK_NAME = "موسيقى"

    /** معرّف مسار حر جديد فريد. */
    fun newTrackId(): String = "tr${System.nanoTime()}"

    /**
     * المسارات الفعّالة للمشروع: القائمة المحفوظة إن وُجدت، وإلا مسار «موسيقى»
     * افتراضي واحد — هجرة منطقية صامتة لمشاريع v0.35.0 دون مساس ملفها.
     */
    fun effectiveTracks(p: MontageProject): List<MontageTrack> {
        val t = p.tracks.map {
            it.copy(
                name = it.name.trim().ifEmpty { DEFAULT_TRACK_NAME }.take(24),
                colorIndex = Math.floorMod(it.colorIndex, TRACK_COLORS.size),
                volumePercent = clampVolume(it.volumePercent)
            )
        }
        return if (t.isEmpty()) listOf(MontageTrack(id = DEFAULT_TRACK_ID, name = DEFAULT_TRACK_NAME)) else t
    }

    /** معرّف المسار الفعلي لمقطع مسار — الفارغ يعني المسار الافتراضي. */
    fun effectiveTrackId(c: MontageMusicClip): String =
        c.trackId.trim().ifEmpty { DEFAULT_TRACK_ID }

    /** المسار بمعرّفه من قائمة فعّالة — المجهول يعود للمسار الافتراضي. */
    fun trackById(tracks: List<MontageTrack>, id: String): MontageTrack =
        tracks.firstOrNull { it.id == id }
            ?: tracks.firstOrNull()
            ?: MontageTrack(id = DEFAULT_TRACK_ID, name = DEFAULT_TRACK_NAME)

    /** مقاطع مسار محدد بترتيب الموضع. */
    fun clipsOfTrack(p: MontageProject, trackId: String): List<MontageMusicClip> =
        p.musicClips.filter { effectiveTrackId(it) == trackId }.sortedBy { it.startMs }

    /**
     * كسب المسار عند لحظة المزج: الكتم أو عزلة مسارٍ آخر تُسقطه صفريًا،
     * وإلا مستواه المئوي. مسار التعليق خارج عقد الكتم/المنفرد — هو البرنامج.
     */
    fun trackGainPercent(track: MontageTrack, anySolo: Boolean): Int =
        if (track.muted || (anySolo && !track.solo)) 0 else clampVolume(track.volumePercent)

    /** هل من مسار حر منفرد؟ */
    fun anyTrackSolo(tracks: List<MontageTrack>): Boolean = tracks.any { it.solo }

    /**
     * التقاط موضع زمني (v0.36.0): أقرب مرشح ضمن العتبة يفوز — العلامات
     * وحواف المقاطع والشبكة نصف الثانية. النقي قابل للاختبار بدقة.
     */
    fun snapMs(targetMs: Long, candidates: List<Long>, thresholdMs: Long = SNAP_THRESHOLD_MS): Long {
        val t = targetMs.coerceAtLeast(0L)
        var best = t
        var bestD = thresholdMs.coerceAtLeast(0L)
        for (c in candidates) {
            val d = Math.abs(c - t)
            if (d <= bestD) { bestD = d; best = c }
        }
        return best
    }

    /** مرشحات التقاط مشروع: العلامات + حواف مقاطع التعليق على الخط الزمني + حواف مقاطع المسارات. */
    fun snapCandidates(p: MontageProject): List<Long> {
        val out = ArrayList<Long>()
        out.addAll(p.markers.map { it.tMs })
        val starts = clipStartMs(p.clips, p.gapMs, p.crossfadeMs)
        for (i in p.clips.indices) {
            out.add(starts[i])
            out.add(starts[i] + clipDurationMs(p.clips[i]))
        }
        for (m in p.musicClips) {
            out.add(m.startMs)
            out.add(m.startMs + musicDurationMs(m))
        }
        return out
    }

    /**
     * تقسيم مقطع مسار عند موضع داخل نافذته (v0.36.0 — أداة التقسيم على مقاطع
     * المسارات): نصفان على نفس المسار بمواضع مطلقة متلاصقة، الأول يحمل التلاشي
     * الداخلي والثاني الخارجي — فلا طقطقة عند نقطة الفصل. يعيد null خارج النافذة.
     */
    fun splitTrackClipAt(m: MontageMusicClip, offsetInWindowMs: Long): Pair<MontageMusicClip, MontageMusicClip>? {
        val dur = musicDurationMs(m)
        if (offsetInWindowMs <= 0L || offsetInWindowMs >= dur) return null
        val (ws, we) = clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
        val cut = ws + offsetInWindowMs
        val a = m.copy(endInFileMs = cut, fadeOutMs = 0L, id = newMusicClipId())
        val b = m.copy(startInFileMs = cut, startMs = m.startMs + offsetInWindowMs, fadeInMs = 0L, id = newMusicClipId())
        return a to b
    }

    /** أدنى مدة حية لمقطع بعد التقليم (مللي ثانية) — نفس حد مقابض التعليق. */
    const val MIN_CLIP_MS = 50L

    /**
     * تقليم الحافة اليسرى لمقطع مسار بالسحب (v0.36.0 — الضغط والسحب): الموضع
     * المطلق ونافذة الملف يزحفان معًا فنهاية المقطع تبقى ثابتة في ملفه —
     * محبوسًا ألّا يتجاوز بداية الملف ولا يبتلع النافذة كلها.
     */
    fun trimTrackClipLeft(m: MontageMusicClip, deltaMs: Long): MontageMusicClip {
        val (ws, we) = clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
        val dur = we - ws
        val dx = deltaMs.coerceIn(-ws, dur - MIN_CLIP_MS)
        return m.copy(startMs = (m.startMs + dx).coerceAtLeast(0L), startInFileMs = ws + dx)
    }

    /**
     * تقليم الحافة اليمنى: النافذة تتمد أو تنكمش من نهايتها — محبوسة داخل
     * مدة الملف وفوق أدنى مدة حية. الموضع المطلق لا يمسّ.
     */
    fun trimTrackClipRight(m: MontageMusicClip, deltaMs: Long): MontageMusicClip {
        val (ws, we) = clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
        val dx = deltaMs.coerceIn(-(we - ws - MIN_CLIP_MS), m.durationMs.coerceAtLeast(0L) - we)
        return m.copy(endInFileMs = we + dx)
    }

    /**
     * حذف منطقة زمنية متموّجًا (v0.36.0 — عملية المسطرة الجوهرية «حدّد الجزء
     * الذي تريد حذفه»): كل ما في [s, e) يُقتل من المشروع كله وكل ما بعد e
     * يُسحب يسارًا بطول المنطقة — التعليق بنوافذه، ومقاطع المسارات بمواضعها،
     * والعلامات والرأس. المقاطع العابرة للحدود تُقصّ، والعابرة للمنطقة كلها
     * تنشطر نصفين يبقى النصفان خارجها. يعيد null إن كانت المنطقة معدومة.
     */
    fun deleteRegionRipple(p: MontageProject, s: Long, e: Long): MontageProject? {
        val start = s.coerceAtLeast(0L)
        val end = e.coerceAtLeast(start)
        val len = end - start
        if (len < SELECTION_MIN_MS) return null
        val total = totalDurationFull(p)
        val st = start.coerceAtMost(total)
        val en = end.coerceAtMost(total)
        val cut = en - st
        if (cut < SELECTION_MIN_MS) return null

        // مسار التعليق: مقاطع متسلسلة تُقصّ/تُقتل ويُعاد حساب البدايات تلقائيًا
        val starts = clipStartMs(p.clips, p.gapMs, p.crossfadeMs)
        val newClips = ArrayList<MontageClip>()
        for (i in p.clips.indices) {
            val c = p.clips[i]
            val cs = starts[i]
            val ce = cs + clipDurationMs(c)
            when {
                cs >= st && ce <= en -> { /* داخل المنطقة: يُقتل */ }
                cs < st && ce > en -> {
                    // عابر للمنطقة كلها: ينشطر — النصف الأيسر يبقى والأيمن يُلحق به
                    val leftEnd = c.startMs + (st - cs)
                    val rightStart = c.startMs + (en - cs)
                    newClips.add(c.copy(endMs = leftEnd, fadeOutMs = 0L, id = newClipId()))
                    newClips.add(c.copy(startMs = rightStart, fadeInMs = 0L, id = newClipId()))
                }
                cs < st && ce > st ->
                    newClips.add(c.copy(endMs = c.startMs + (st - cs), fadeOutMs = 0L, id = newClipId()))
                cs < en && ce >= en ->
                    newClips.add(c.copy(startMs = c.startMs + (en - cs), fadeInMs = 0L, id = newClipId()))
                else -> newClips.add(c)
            }
        }

        // مقاطع المسارات الحرة: مواضع مطلقة تُقصّ وتُقتل وتُسحب
        val newTrackClips = ArrayList<MontageMusicClip>()
        for (m in p.musicClips) {
            val cs = m.startMs.coerceAtLeast(0L)
            val ce = cs + musicDurationMs(m)
            when {
                ce <= st || cs >= en -> {
                    val shifted = if (cs >= en) m.copy(startMs = cs - cut) else m
                    newTrackClips.add(shifted)
                }
                cs >= st && ce <= en -> { /* داخل المنطقة: يُقتل */ }
                cs < st && ce > en -> {
                    // ينشطر نصفين: الأيسر يبقى مكانه والأيمن يهبط على بداية المنطقة
                    val (ws, _) = clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
                    val leftEndInFile = ws + (st - cs)
                    val rightStartInFile = ws + (en - cs)
                    newTrackClips.add(m.copy(endInFileMs = leftEndInFile, fadeOutMs = 0L, id = newMusicClipId()))
                    newTrackClips.add(
                        m.copy(
                            startInFileMs = rightStartInFile,
                            startMs = st,
                            fadeInMs = 0L,
                            id = newMusicClipId()
                        )
                    )
                }
                cs < st && ce > st -> {
                    val (ws, _) = clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
                    newTrackClips.add(
                        m.copy(endInFileMs = ws + (st - cs), fadeOutMs = 0L, id = newMusicClipId())
                    )
                }
                else -> {
                    val (_, we) = clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
                    newTrackClips.add(
                        m.copy(
                            startMs = st,
                            startInFileMs = we - (ce - en),
                            fadeInMs = 0L,
                            id = newMusicClipId()
                        )
                    )
                }
            }
        }

        // العلامات: داخل المنطقة تُقتل وما بعدها يُسحب
        val newMarkers = p.markers.mapNotNull { mk ->
            when {
                mk.tMs >= st && mk.tMs < en -> null
                mk.tMs >= en -> mk.copy(tMs = mk.tMs - cut)
                else -> mk
            }
        }

        // الرأس: داخل المنطقة يقفز لبدايتها وما بعدها يُسحب
        val head = p.playheadMs
        val newHead = when {
            head in st until en -> st
            head >= en -> head - cut
            else -> head
        }

        // المنطقة المحددة نفسها تُحدَّث: ما بقي منها بعد الحذف
        val sel0 = p.selectionStartMs
        val sel1 = p.selectionEndMs
        val newSel: Pair<Long, Long> = if (sel0 >= 0L && sel1 > sel0) {
            val ns = when {
                sel0 >= en -> sel0 - cut
                sel0 >= st -> st
                else -> sel0
            }
            val ne = when {
                sel1 >= en -> sel1 - cut
                sel1 >= st -> st
                else -> sel1
            }
            if (ne - ns >= SELECTION_MIN_MS) ns to ne else -1L to -1L
        } else -1L to -1L

        return p.copy(
            clips = newClips.take(MAX_CLIPS),
            // لا تعقيم قرصي هنا — دالة نموذجية نقية؛ المُصيّر يعقّم عند التصدير
            musicClips = newTrackClips.take(MAX_TRACK_CLIPS),
            markers = sanitizeMarkers(newMarkers),
            playheadMs = newHead.coerceAtLeast(0L),
            selectionStartMs = newSel.first,
            selectionEndMs = newSel.second
        )
    }

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
