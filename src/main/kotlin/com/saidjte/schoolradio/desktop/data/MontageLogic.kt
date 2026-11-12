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
 *
 * v0.41.0 «نظام المونتاج متعدد المسارات الاحترافي»: المسارات الحرة تصير
 * نظامًا كاملًا — أنواع (موسيقى خلفية/مؤثرات صوتية/حوار) بعدد كبير حتى 16
 * مسارًا تُرتبّ ويُحذف منها ويُضاف إليها، وكتم/منفرد حيّان في رؤوسها (الطلب
 * الجديد يعيدهما بعقد المزج الإذاعي — ومسار التعليق وحده بلا كتم ولا
 * منفرد كما في v0.37.0)، وتوازن استريو Pan لكل مسار، وتجميع المسارات
 * في مجموعات بمستوى وكتم/منفرد خاص بها تتراكب قيمها مع قيم المسار،
 * وأتمتة مستوى الصوت بنقاط مفتاحية خطية على الخط الزمني تُطبّق في المزج
 * والمعاينة والتصدير معًا، والخفض التلقائي يتحدد بحسب النوع: الموسيقى
 * الخلفية تنخفض تحت التعليق والمؤثرات والحوار لا (هي ليست سريرًا)،
 * ومزج كل المسارات في ملف واحد يبقى تصدير WAV/MP3 القائم.
 *
 * v0.37.0 «تسمية المقاطع بالنقر المزدوج وبلا كتم وتصدير MP3»: إعادة تسمية
 * أي مقطع بنقرة مزدوجة على الخط الزمني، وإزالة الكتم/المنفرد من رؤوس
 * المسارات كلها — مسار التعليق هو البرنامج لا يُكتم أبدًا، والمسارات الحرة
 * تعيش بعقد نفسه فلا يصمت أي خط عمل بمفتاح واحد (الأعلام القديمة من
 * مشاريع v0.36.0 تُهاجر صامتة إلى false في المسارات الفعّالة)، وتصدير
 * المونتاج MP3 بجودة قابلة للاختيار إلى جانب WAV بلا فقد.
 *
 * v0.38.0 «أدوات الحفرة الاحترافية — الحفرة الحديثة»: حافظة قص/نسخ/لصق
 * (Ctrl+X/C/V) تعمل بين مسار التعليق والمسارات الحرة، وتكرار المقاطع ×N،
 * ودمج متتاليتين في مقطع واحد مرتَدًا عبر المُصيِّر نفسه، وعكس الموجة،
 * وتعديل السرعة دون تغيير النبرة، وتغيير النبرة دون تغيير الطول — كل
 * التحويلات الصوتية في ClipTransforms.kt (WSOLA خالص على JVM) وهنا
 * رياضيات الحافظة والإدراج والتكرار النقية القابلة للاختبار.
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
 * مقاطع بمواضع مطلقة — موسيقى، مؤثرات، تعليق ثانٍ، أجواء — بمستوى موحّد.
 *
 * v0.41.0 «المونتاج متعدد المسارات الاحترافي»: المسار يكتمل خصائصه —
 * [type] نوعه (null/مجهول = موسيقى خلفية هجرةً للمشاريع القديمة)، و[pan]
 * توازنه الاستريو −100..+100 (0 = وسط)، و[groupId] مجموعته، والكتم/المنفرد
 * عادا حيّين في رؤوس المسارات بطلب v0.41.0 (مسار التعليق وحده بلا كتم ولا
 * منفرد كما قرّر v0.37.0). الحقول الجديدة محايدة عند الغياب في ملفات JSON
 * القديمة: Gson يتركها null/0 فتعود قيمها الافتراضية الآمنة دون مساس.
 */
data class MontageTrack(
    val id: String,
    val name: String,
    /** فهرس اللون من لوحة ألوان المسارات — تمايز بصري بين الخطوط. */
    val colorIndex: Int = 0,
    val volumePercent: Int = MontageLogic.VOLUME_DEFAULT,
    val muted: Boolean = false,
    val solo: Boolean = false,
    /** نوع المسار (v0.41.0): music/sfx/dialogue — الفارغ والمجهول = موسيقى خلفية. */
    val type: String? = null,
    /** التوازن الاستريو (v0.41.0): −100 يسار كامل، 0 وسط، +100 يمين كامل. */
    val pan: Int = 0,
    /** معرّف المجموعة المالكة (v0.41.0) — null/الفارغ = بلا مجموعة. */
    val groupId: String? = null
)

/**
 * مجموعة مسارات (v0.41.0 «تجميع المسارات»): وعاء يضم مسارات متعددة بمستوى
 * وكتم/منفرد خاص بها — كسب المجموعة يتراكب مع كسب المسار، وكتمها يصمّت
 * أعضاءها، ومنفردها يبقيهم حيّين وسط منفردَين غيرهم. قيمها قابلة للحبس
 * مثل المسارات تمامًا.
 */
data class MontageGroup(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val volumePercent: Int = MontageLogic.VOLUME_DEFAULT,
    val muted: Boolean = false,
    val solo: Boolean = false
)

/**
 * نقطة أتمتة مستوى (v0.41.0 «أتمتة مستوى الصوت»): عند زمن معين على المسار
 * يأخذ المستوى قيمة بالمئة — النقاط ترتب بالزمن وتُقرأ خطيًا بينها.
 * [id] ثابت للسحب في الواجهة بلا التباس فهرس.
 */
data class MontageAutoPoint(
    val id: String,
    val tMs: Long = 0L,
    val percent: Int = MontageLogic.VOLUME_DEFAULT
)

/** خط أتمتة مسار واحد: نقاطه المفتاحية مرتبة بالزمن عند الحفظ. */
data class MontageAutomationLane(
    val trackId: String,
    val points: List<MontageAutoPoint> = emptyList()
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
    // ==== v0.41.0: المونتاج متعدد المسارات الاحترافي ====
    /** مجموعات المسارات — null في الملفات القديمة تعود قائمة فارغة. */
    val groups: List<MontageGroup>? = null,
    /** خطوط الأتمتة لكل مسار — null في الملفات القديمة تعود قائمة فارغة. */
    val automation: List<MontageAutomationLane>? = null,
    /** المنطقة المحددة على المسطرة [selectionStartMs, selectionEndMs) — null = لا تحديد. */
    val selectionStartMs: Long = -1L,
    val selectionEndMs: Long = -1L,
    // ==== v0.47.0: سقف البث ====
    /**
     * تطبيع الإخراج النهائي إلى ذروة −1 dBFS — null في ملفات JSON القديمة
     * يعني الافتراضي مفعّل (قرار masterNormalizeEffective)، والقيمة الصريحة
     * تُحترم: true مفعّل وfalse معطّل بمطلع المذيع.
     */
    val masterNormalize: Boolean? = null
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

    /** حد مقاطع الموسيقى المرافقة — منذ v0.41.0 سقف مقاطع كل المسارات الحرة معًا (نظام متعدد المسارات لا سرير واحد). */
    const val MAX_MUSIC_CLIPS = 120
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

    /** سقف المسارات الحرة — منذ v0.41.0 عدد كبير من المسارات حتى 16 خط عمل. */
    const val MAX_TRACKS = 16

    /** سقف مقاطع المسارات الحرة مجتمعة — رُفع مع توسّع النظام متعدد المسارات. */
    const val MAX_TRACK_CLIPS = 120

    // ==== v0.41.0: ثوابت نظام المونتاج متعدد المسارات الاحترافي ====

    /** نوع المسار: موسيقى خلفية — ينخفض تحت التعليق حين يفعّل المذيع الخفض. */
    const val TYPE_MUSIC = "music"
    /** نوع المسار: مؤثرات صوتية — لا تنخفض تحت التعليق (نبضة قصيرة لا سرير). */
    const val TYPE_SFX = "sfx"
    /** نوع المسار: حوار — كلام بحد ذاته فلا ينخفض تحت التعليق. */
    const val TYPE_DIALOGUE = "dialogue"

    /** سقف المجموعات في المشروع الواحد. */
    const val MAX_GROUPS = 8
    /** سقف نقاط الأتمتة على المسار الواحد — منحنى قابل للقراءة لا شجرة أرقام. */
    const val MAX_AUTO_POINTS = 64

    /** أدنى وأعلى توازن استريو لمسار — 0 وسط. */
    const val PAN_MIN = -100
    const val PAN_MAX = 100

    /** لوحة ألوان المسارات — منذ v0.41.0 عشرة ألوان لمسارات أكثر. */
    val TRACK_COLORS = intArrayOf(
        0xFF7E57C2.toInt(), 0xFF26A69A.toInt(), 0xFFEF6C00.toInt(),
        0xFF5C6BC0.toInt(), 0xFFD81B60.toInt(), 0xFF00897B.toInt(),
        0xFFF4511E.toInt(), 0xFF3949AB.toInt(), 0xFFC0CA33.toInt(),
        0xFF6D4C41.toInt()
    )

    /** الحد الأدنى لعرض المنطقة المحددة على المسطرة (مللي ثانية) — أصغر منها يُهمَل. */
    const val SELECTION_MIN_MS = 30L

    /** حد عتبة التقاط السحب (مللي ثانية) — الأقرب ضمنه يلتقط. */
    const val SNAP_THRESHOLD_MS = 120L

    /** شبكة التقاط عامة نصف ثانية — أساس احترافي حين لا علامة ولا حافة قريبة. */
    const val SNAP_GRID_MS = 500L

    /** اسم المسار الحر الافتراضي. */
    const val DEFAULT_TRACK_NAME = "موسيقى"

    // ==== v0.41.0: أسماء وأنواع المسارات الافتراضية ====

    /** معرّفات المسارات الافتراضية الثلاثة للمشاريع الجديدة والقديمة الخاوية. */
    const val DEFAULT_MUSIC_NAME = "موسيقى خلفية"
    const val DEFAULT_SFX_ID = "sfx"
    const val DEFAULT_SFX_NAME = "مؤثرات"
    const val DEFAULT_DIALOGUE_ID = "dialogue"
    const val DEFAULT_DIALOGUE_NAME = "حوار"

    /** معرّف مسار حر جديد فريد. */
    fun newTrackId(): String = "tr${System.nanoTime()}"

    /** معرّف مجموعة جديد فريد. */
    fun newGroupId(): String = "gr${System.nanoTime()}"

    /** معرّف نقطة أتمتة جديد فريد. */
    fun newAutoPointId(): String = "ap${System.nanoTime()}"

    /**
     * المسارات الفعّالة للمشروع: القائمة المحفوظة إن وُجدت، وإلا الثلاثي
     * الافتراضي (موسيقى خلفية + مؤثرات + حوار) — هجرة منطقية صامتة
     * لمشاريع v0.35.0 الخاوية دون مساس ملفها.
     *
     * v0.41.0: الكتم/المنفرد عادا حيّين في رؤوس المسارات الحرة (طلب نظام
     * المونتاج متعدد المسارات) فلا تُهرَج أعلامهما إلى false بعد الآن —
     * والأزرار موجودة فلا مسار يتيم مكتموم، ومسار التعليق وحده (خارج هذه
     * القائمة) بلا كتم ولا منفرد كما قرّر v0.37.0. النوع المجهول يُفسَّر
     * موسيقى خلفية، والتوازن محبوس، والقيم محبوسة كما هي.
     */
    fun effectiveTracks(p: MontageProject): List<MontageTrack> {
        val t = p.tracks.map {
            it.copy(
                name = it.name.trim().ifEmpty { DEFAULT_TRACK_NAME }.take(24),
                colorIndex = Math.floorMod(it.colorIndex, TRACK_COLORS.size),
                volumePercent = clampVolume(it.volumePercent),
                pan = clampPan(it.pan)
            )
        }
        return if (t.isEmpty()) defaultTrackTrio() else t
    }

    /**
     * الثلاثي الافتراضي للمسارات الحرة (v0.41.0): موسيقى خلفية + مؤثرات
     * + حوار — يشغّل المشاريع الجديدة والمشاريع القديمة الخاوية بأرضية
     * نظام متعدد المسارات جاهز، بمعرّفات ثابتة تُعرف بالمقارنة النصية.
     */
    fun defaultTrackTrio(): List<MontageTrack> = listOf(
        MontageTrack(id = DEFAULT_TRACK_ID, name = DEFAULT_MUSIC_NAME, colorIndex = 1, type = TYPE_MUSIC),
        MontageTrack(id = DEFAULT_SFX_ID, name = DEFAULT_SFX_NAME, colorIndex = 2, type = TYPE_SFX),
        MontageTrack(id = DEFAULT_DIALOGUE_ID, name = DEFAULT_DIALOGUE_NAME, colorIndex = 3, type = TYPE_DIALOGUE)
    )

    /**
     * تعقيم اسم مقطع للإعادة التسمية بالنقر المزدوج (v0.37.0): بلا أطراف
     * بيضاء وبلا سطر جديد وغير فارغ (الفارغ يعود «مقطع») ومحبوس 40 حرفًا
     * كي يبقى مقروءًا على الموجة وفي القائمة.
     */
    fun sanitizeClipTitle(name: String): String {
        val flat = name.replace(Regex("[\\r\\n]+"), " ").trim()
        return flat.ifEmpty { "مقطع" }.take(40)
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
     * وإلا مستواه المئوي. منذ v0.37.0 المسارات الفعّالة بلا كتم ولا منفرد
     * (هجران الأعلام القديمة في effectiveTracks) فتعود دائمًا المستوى —
     * تبقى رياضياتها لعقود الملفات القديمة ووحدة المزج المرجعية.
     */
    fun trackGainPercent(track: MontageTrack, anySolo: Boolean): Int =
        if (track.muted || (anySolo && !track.solo)) 0 else clampVolume(track.volumePercent)

    /** هل من مسار حر منفرد؟ */
    fun anyTrackSolo(tracks: List<MontageTrack>): Boolean = tracks.any { it.solo }

    // ==== v0.43.0: التسجيل الفوقي على المونتاج — مساعدو الإدراج ====

    /** اسم المسار المخصّص للتسجيل الفوقي — يُنشأ حين المشروع خاوي من المسارات. */
    const val OVERDUB_TRACK_NAME = "مسار التسجيل"

    /** معرّف مقطع تسجيل فوقي جديد فريد. */
    fun newOverdubClipId(): String = "od${System.nanoTime()}"

    /**
     * ضمان مسار الوجهة للتسجيل الفوقي: أول مسار حوار فعّال إن وُجد —
     * صوت المذيع لا يخفض تحت التعليق — وإلا أول مسار حر، وإلا أُنشئ
     * مسار «مسار التسجيل» بنوع حوار للمشاريع الخاوية. يعيد المشروع
     * المعدَّل ومعرّف المسار الوجهة — قرار موحّد يختبره العقد.
     */
    fun ensureOverdubTrack(p: MontageProject): Pair<MontageProject, String> {
        val eff = effectiveTracks(p)
        if (p.tracks.isNotEmpty() && eff.isNotEmpty()) {
            val dest = eff.firstOrNull { it.type == TYPE_DIALOGUE } ?: eff.first()
            return p to dest.id
        }
        val track = MontageTrack(
            id = newTrackId(), name = OVERDUB_TRACK_NAME,
            colorIndex = 4, type = TYPE_DIALOGUE
        )
        return p.copy(tracks = listOf(track)) to track.id
    }

    /**
     * مقطع التسجيل الفوقي الجاهز للإدراج: موضع مطلق عند رأس التشغيل لحظة
     * بدء التسجيل، ونافذة الملف المسجَّل كاملةً، وعلى المسار الوجهة —
     * والمدة الصفرية (فشل الالتقاط) تعطي نافذة مغلقة آمنة لا تكسر المزج.
     */
    fun overdubClip(path: String, title: String, durationMs: Long, startMs: Long, trackId: String): MontageMusicClip {
        val d = durationMs.coerceAtLeast(0L)
        val (s, e) = clampWindow(0L, 0L, d)
        return MontageMusicClip(
            id = newOverdubClipId(),
            path = path,
            title = sanitizeClipTitle(title),
            durationMs = d,
            startMs = startMs.coerceAtLeast(0L),
            startInFileMs = s,
            endInFileMs = e,
            trackId = trackId
        )
    }

    // ==== v0.41.0: نظام المونتاج متعدد المسارات الاحترافي — رياضيات نقية ====

    /**
     * نوع المسار الفعّال: القيمة المعروفة تعود كما هي، والفارغ/المجهول
     * (ملفات JSON القديمة عبر Gson تترك الحقل null) يعود موسيقى خلفية —
     * هجرة صامتة تحفظ سلوك المشاريع القديمة (كل المسارات كانت تنخفض).
     */
    fun trackType(t: MontageTrack): String = when (t.type?.trim()) {
        TYPE_MUSIC, TYPE_SFX, TYPE_DIALOGUE -> t.type!!.trim()
        else -> TYPE_MUSIC
    }

    /** الاسم العربي لعرض نوع المسار على الرأس والشارات. */
    fun trackTypeLabelAr(type: String): String = when (type) {
        TYPE_SFX -> "مؤثرات"
        TYPE_DIALOGUE -> "حوار"
        else -> "موسيقى"
    }

    /**
     * هل ينخفض هذا المسار تحت التعليق عند تفعيل الخفض التلقائي؟ التحكم في
     * تداخل الأصوات بحسب النوع: الموسيقى الخلفية سرير ينخفض، والمؤثرات
     * نبضة قصيرة لا يُلحق بها الخفض، والحوار كلام بحد ذاته لا يُقابَل
     * كالموسيقى. مشاريع ما قبل v0.41.0 تُفسَّر موسيقى فسلوكها بلا مساس.
     */
    fun ducksUnderVoice(t: MontageTrack): Boolean = trackType(t) == TYPE_MUSIC

    /** حبس قيمة التوازن الاستريو إلى نطاقها القانوني. */
    fun clampPan(p: Int): Int = p.coerceIn(PAN_MIN, PAN_MAX)

    /** كسب القناة اليسرى عند التوازن المعطى — خطي بلا انكسار عند الوسط. */
    fun panGainLeft(pan: Int): Float =
        1f - (clampPan(pan).toFloat() / PAN_MAX).coerceAtLeast(0f)

    /** كسب القناة اليمنى عند التوازن المعطى — خطي بلا انكسار عند الوسط. */
    fun panGainRight(pan: Int): Float =
        1f + (clampPan(pan).toFloat() / PAN_MAX).coerceAtMost(0f)

    /** مجموعات المشروع الفعّالة — null في الملفات القديمة تعود قائمة فارغة. */
    fun groupsOf(p: MontageProject): List<MontageGroup> = p.groups ?: emptyList()

    /** تعقيم قائمة المجموعات: أسماء غير خاوية محبوسة وقيم محبوسة وسقف العدد. */
    fun sanitizeGroups(groups: List<MontageGroup>?): List<MontageGroup> =
        (groups ?: emptyList()).take(MAX_GROUPS).map {
            it.copy(
                name = it.name.trim().ifEmpty { "مجموعة" }.take(24),
                colorIndex = Math.floorMod(it.colorIndex, TRACK_COLORS.size),
                volumePercent = clampVolume(it.volumePercent)
            )
        }

    /** المجموعة بمعرّفها — المجهول/الفارغ يعود null فالمسار يعيش بلا مجموعة. */
    fun groupById(groups: List<MontageGroup>, groupId: String?): MontageGroup? {
        val id = groupId?.trim().orEmpty()
        if (id.isEmpty()) return null
        return groups.firstOrNull { it.id == id }
    }

    /** هل من مجموعة منفردة؟ */
    fun anyGroupSolo(groups: List<MontageGroup>): Boolean = groups.any { it.solo }

    /**
     * هل يُسمع هذا المسار في المزج؟ عقد وحدة المزج الإذاعي الكامل (v0.41.0):
     * كتم المسار أو كتم مجموعته يصمّمه، ومنفرد المسار أو منفرد مجموعته
     * يبقيه حيًّا وسط أي منفردَين آخرين — ولا منفرد على الإطلاق يعني الجميع
     * حيّ (عدا المكتوم). مسار التعليق خارج هذا العقد: هو البرنامج دائمًا.
     */
    fun trackAudible(
        track: MontageTrack,
        group: MontageGroup?,
        anyTrackSolo: Boolean,
        anyGroupSolo: Boolean
    ): Boolean {
        if (track.muted) return false
        if (group?.muted == true) return false
        val anySolo = anyTrackSolo || anyGroupSolo
        if (!anySolo) return true
        return track.solo || group?.solo == true
    }

    /**
     * كسب المسار النهائي عند المزج (v0.41.0): مستوى المسار × مستوى مجموعته
     * (مجموعة بلا وجود تعني 100٪)، والمسامع (كتم/منفرد) تصفّره كليًا —
     * يُستدعى من المُصيّر لكل مقطع مسار بدل كسب المسار الأحادي القديم.
     */
    fun trackAmp(
        track: MontageTrack,
        group: MontageGroup?,
        anyTrackSolo: Boolean,
        anyGroupSolo: Boolean
    ): Float {
        if (!trackAudible(track, group, anyTrackSolo, anyGroupSolo)) return 0f
        val gAmp = (group?.volumePercent ?: VOLUME_DEFAULT) / 100f
        return track.volumePercent / 100f * gAmp
    }

    /** اسم مسار جديد غير مكرر لنوع معين — «مؤثرات» ف«مؤثرات 2» ف«مؤثرات 3»… */
    fun nextTrackName(tracks: List<MontageTrack>, type: String): String {
        val base = trackTypeLabelAr(type)
        val sameType = tracks.filter { trackType(it) == type }.size
        if (tracks.none { it.name.trim() == base }) return base
        var n = sameType + 1
        while (tracks.any { it.name.trim() == "$base $n" }) n++
        return "$base $n"
    }

    /** مسار جديد بنوع معين بلون غير مستخدم من اللوحة — قرارات الإضافة النقية. */
    fun newTrackOfType(tracks: List<MontageTrack>, type: String): MontageTrack {
        val used = tracks.map { it.colorIndex }.toSet()
        var ci = 0
        while (ci < TRACK_COLORS.size && ci in used) ci++
        return MontageTrack(
            id = newTrackId(),
            name = nextTrackName(tracks, type),
            colorIndex = ci % TRACK_COLORS.size,
            type = type
        )
    }

    /**
     * ترتيب المسارات بتحريك مسار واحد خطوة لأعلى (−1) أو لأسفل (+1) —
     * «تحريك المسارات بشكل مستقل»: العائد null إن كان المسار مجهولًا أو
     * الخطوة تخرجه من القائمة، وإلا القائمة الجديدة بلا مساس البقية.
     */
    fun moveTrack(tracks: List<MontageTrack>, trackId: String, delta: Int): List<MontageTrack>? {
        val i = tracks.indexOfFirst { it.id == trackId }
        if (i < 0) return null
        val j = i + delta
        if (j < 0 || j >= tracks.size) return null
        val out = tracks.toMutableList()
        val t = out.removeAt(i)
        out.add(j, t)
        return out
    }

    /** خطوط الأتمتة الفعّالة — null في الملفات القديمة تعود قائمة فارغة. */
    fun automationLanesOf(p: MontageProject): List<MontageAutomationLane> =
        p.automation ?: emptyList()

    /** خط أتمتة مسار معين — الغائب يعود null فالمسار بلا أتمتة بعد. */
    fun automationLaneOf(p: MontageProject, trackId: String): MontageAutomationLane? =
        automationLanesOf(p).firstOrNull { it.trackId == trackId }

    /**
     * تعقيم نقاط أتمتة: زمن غير سالب ومستوى محبوس وترتيب زمني وسقف العدد
     * — فلا ملف مُحرَّر يدويًا يكسر المنحنى ولا نقطة خارجة عن النطاق.
     */
    fun sanitizeAutoPoints(points: List<MontageAutoPoint>): List<MontageAutoPoint> =
        points.map { it.copy(tMs = it.tMs.coerceAtLeast(0L), percent = clampVolume(it.percent)) }
            .sortedBy { it.tMs }
            .take(MAX_AUTO_POINTS)

    /**
     * قراءة المنحنى عند لحظة: قبل أول نقطة تعود قيمتها، وبعد آخر نقطة
     * تعود قيمتها، وبين نقطتين استقراء خطي — والقائمة الخاوية تعود 100٪
     * فالأتمتة الغائبة محايدة تمامًا على المزج.
     */
    fun autoGainPercentAt(points: List<MontageAutoPoint>, tMs: Long): Int {
        if (points.isEmpty()) return VOLUME_DEFAULT
        val t = tMs.coerceAtLeast(0L)
        if (t <= points.first().tMs) return points.first().percent
        if (t >= points.last().tMs) return points.last().percent
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (t >= a.tMs && t <= b.tMs) {
                if (b.tMs <= a.tMs) return b.percent
                val f = (t - a.tMs).toFloat() / (b.tMs - a.tMs).toFloat()
                return (a.percent + (b.percent - a.percent) * f).toInt()
            }
        }
        return points.last().percent
    }

    /**
     * نقاط أتمتة افتراضية لمسار يفعّل الأتمتة أول مرة: نقطة واحدة عند
     * الصفر بمستوى المسار الحيّ — المنحنى يبدأ مساويًا للوضع بلا أتمتة
     * فالتشغيل لا يتغير بلمسة التفعيل، والنقاط تضاف فوقه بعدها.
     */
    fun defaultAutoPoints(track: MontageTrack): List<MontageAutoPoint> = listOf(
        MontageAutoPoint(id = newAutoPointId(), tMs = 0L, percent = clampVolume(track.volumePercent))
    )

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
     * نبضة التشغيل الواحد للمنطقة المحددة (v0.47.0): نهاية صالحة وموضع بلغها
     * أو تجاوزها = حان وقف التشغيل. الحلقة A/B باقية كما هي — وهذان نمطان
     * مستقلان: الحلقة تعود للبداية، والتشغيل الواحد يقف عند النهاية.
     */
    fun oneShotReached(playMs: Long, oneShotEndMs: Long?): Boolean =
        oneShotEndMs != null && oneShotEndMs > 0L && playMs >= oneShotEndMs

    /**
     * مشروع استماع لمقطع واحد (v0.47.0 — «استماع للمحدد»): نسخة مصغرة من
     * المشروع تحوي المقطع المحدد وحده بنافذته ومستواه وتلاشيه وتحويلاته
     * (المحوّلات تعيش في ملف المصدر فتُسمع كما هي)، والتعليق يُسمع نافذته
     * كما هي والمقطع الحر يُسحب إلى موضع الصفر كي لا يُسبق بصمت، بلا كروس
     * فيد ولا خفض ولا تطبيع إخراج — سماع المقطع وحده لا المزيج. التعليق
     * المحدد يفوز حين يكون الاثنان محددين (التعليق هو البرنامج). يعيد null
     * إن لم يوجد محدد صالح.
     */
    fun auditionProject(p: MontageProject, voiceId: String?, musicId: String?): MontageProject? {
        val voice = voiceId?.takeIf { it.isNotEmpty() }?.let { id -> p.clips.firstOrNull { it.id == id } }
        if (voice != null) {
            val (s, e) = clampWindow(voice.startMs, voice.endMs, voice.durationMs)
            return MontageProject(clips = listOf(voice.copy(startMs = s, endMs = e)))
        }
        val music = musicId?.takeIf { it.isNotEmpty() }?.let { id -> p.musicClips.firstOrNull { it.id == id } }
        if (music != null) {
            return MontageProject(musicClips = listOf(music.copy(startMs = 0L)))
        }
        return null
    }

    /**
     * قرار تطبيع الإخراج الفعلي (v0.47.0 — «سقف البث»): null في المشروع
     * يعني الافتراضي مفعّل — ملفات JSON القديمة التي لا تحمل الحقل تُعدّ
     * مفعّلة، والقيم الصريحة true/false تُحترم كما هي.
     */
    fun masterNormalizeEffective(p: MontageProject): Boolean = p.masterNormalize != false

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

    // ==== v0.38.0: الحافظة والتكرار والدمج — رياضيات نقية ====

    /** حد التقليم قبل الدمج: متتاليتان أقصر من هذا لا تستحقان الدمج. */
    const val MERGE_MIN_MS = 50L

    /** سقف تكرار المقطع الواحد بنقرة — الحافظة ليست مولّد سبام. */
    const val DUPLICATE_MAX = 20

    /**
     * خانة الحافظة: مقطع تعليق أو مقطع مسار حر — القص والنسخ يملآنانها
     * واللصق يقرؤها بمعرّفات جديدة في كل مرة فاللصق المتكرر يضيف نسخًا.
     */
    sealed interface MontageClipboardEntry {
        /** مقطع تعليق مُنسوخ — يُلصق بعد المحدد أو في نهاية الترتيب. */
        data class Voice(val clip: MontageClip) : MontageClipboardEntry

        /** مقطع مسار حر مُنسوخ — يُلصق عند الرأس على مساره. */
        data class Free(val clip: MontageMusicClip) : MontageClipboardEntry
    }

    /** نسخة لصق بمعرّف جديد — الأصل يبقى كما هو تمامًا. */
    fun pastedVoiceCopy(c: MontageClip): MontageClip = c.copy(id = newClipId())

    /** نسخة لصق لمقطع مسار بموضع جديد ومعرّف جديد. */
    fun pastedTrackCopy(m: MontageMusicClip, atMs: Long): MontageMusicClip =
        m.copy(id = newMusicClipId(), startMs = atMs.coerceAtLeast(0L))

    /**
     * لصق مقطع تعليق بعد مقطع محدد: منتصف الترتيب يدسّ خلفه مباشرة،
     * وبلا محدد يُضاف في النهاية — محبوسًا بسقف [MAX_CLIPS].
     */
    fun pasteVoiceAfter(clips: List<MontageClip>, copy: MontageClip, afterId: String?): List<MontageClip> {
        val i = if (afterId == null) -1 else clips.indexOfFirst { it.id == afterId }
        val out = if (i < 0) clips + copy
        else ArrayList(clips).apply { add(i + 1, copy) }
        return out.take(MAX_CLIPS)
    }

    /** لصق مقطع مسار عند موضع زمني — محبوسًا بسقف [MAX_TRACK_CLIPS]. */
    fun pasteTrackAt(clips: List<MontageMusicClip>, copy: MontageMusicClip): List<MontageMusicClip> =
        (clips + copy).take(MAX_TRACK_CLIPS)

    /**
     * تكرار مقطع تعليق ×عدد: النسخ تُدرَس خلف الأصل بترتيبها فالخط الزمني
     * المتسلسل يشغلها تباعًا — 0 يعيد القائمة كما هي، وسقف [MAX_CLIPS] يحمي.
     */
    fun duplicateVoiceAfter(clips: List<MontageClip>, clipId: String, times: Int): List<MontageClip> {
        val i = clips.indexOfFirst { it.id == clipId }
        if (i < 0 || times <= 0) return clips
        val copies = (1..times.coerceAtMost(DUPLICATE_MAX)).map {
            clips[i].copy(id = newClipId())
        }
        val out = ArrayList<MontageClip>(clips.size + copies.size)
        out.addAll(clips)
        out.addAll(i + 1, copies)
        return out.take(MAX_CLIPS)
    }

    /**
     * تكرار مقطع مسار ×عدد على مساره: كل نسخة خلف سابقتها متلاصقة —
     * «تكرار المقاطع» على الخطوط الحرة بمواضع مطلقة محسوبة.
     */
    fun duplicateTrackClip(source: MontageMusicClip, times: Int): List<MontageMusicClip> {
        if (times <= 0) return emptyList()
        val dur = musicDurationMs(source)
        return (1..times.coerceAtMost(DUPLICATE_MAX)).map { k ->
            source.copy(id = newMusicClipId(), startMs = source.startMs + k * dur)
        }
    }

    /**
     * هل يصح دمج متتاليتين على التعليق؟ ليست الأخيرة (لا يوجد بعدها تالٍ)،
     * وكلتاهما حية النافذة.
     */
    fun mergeableVoicePair(clips: List<MontageClip>, clipId: String): Pair<MontageClip, MontageClip>? {
        val i = clips.indexOfFirst { it.id == clipId }
        if (i < 0 || i >= clips.size - 1) return null
        val a = clips[i]
        val b = clips[i + 1]
        if (clipDurationMs(a) < MERGE_MIN_MS || clipDurationMs(b) < MERGE_MIN_MS) return null
        return a to b
    }

    /**
     * هل يصح دمج مقطعي مسار؟ متتاليان على نفس المسار — والدمج يلصقهما
     * متلاصقين عند بداية الأول حتى لو كانت بينهما فجوة على الخط.
     */
    fun mergeableTrackPair(clips: List<MontageMusicClip>, clipId: String): Pair<MontageMusicClip, MontageMusicClip>? {
        val sorted = clipsOfTrack(
            MontageProject(musicClips = clips),
            effectiveTrackId(clips.firstOrNull { it.id == clipId } ?: return null)
        )
        val i = sorted.indexOfFirst { it.id == clipId }
        if (i < 0 || i >= sorted.size - 1) return null
        val a = sorted[i]
        val b = sorted[i + 1]
        if (musicDurationMs(a) < MERGE_MIN_MS || musicDurationMs(b) < MERGE_MIN_MS) return null
        return a to b
    }
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
