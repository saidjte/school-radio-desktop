package com.saidjte.schoolradio.desktop.data

import com.saidjte.schoolradio.desktop.audio.MusicEq

/**
 * نماذج وحدة المزج الإذاعية — الحالة المحفوظة + منطقها الخالص.
 *
 * درس صلب من نسخة الهاتف (v0.16.0) محفوظ هنا حرفيًا:
 * - الكتم جلسيّ لا يُكتب في المحفوظ إطلاقًا (إلغاؤه يعيد المستوى نفسه).
 * - الفيدرات لا تُضاعَف داخل بعضها، والقيم المحفوظة لا تتلوث أبدًا.
 * - النموذج المحفوظ بحقول تقبل Null: ملف JSON ناقص أو مكتوب جزئيًا
 *   لا يمكن أن ينتج فيدرًا صامتًا (Gson يترك الحقل الغائب null ثم
 *   يُستبدل بالقيمة الافتراضية، بدل 0.0f الذي كان سيصمت القناة).
 */

// ================= الحالة المحفوظة (JSON) =================

/** حالة معالج صوت المذيع المحفوظة — كل الأرقام تقبل Null (غياب = الافتراضي). */
data class VoiceFxJson(
    val enabled: Boolean? = null,
    val hpfOn: Boolean? = null,
    val gateOn: Boolean? = null,
    val gateThresholdDb: Float? = null,
    val gateReleaseMs: Float? = null,
    val agcOn: Boolean? = null,
    val agcTargetDb: Float? = null,
    val agcMaxBoostDb: Float? = null,
    val compOn: Boolean? = null,
    val compThresholdDb: Float? = null,
    val compRatio: Float? = null,
    val compMakeupDb: Float? = null,
    val bassDb: Float? = null,
    val presenceDb: Float? = null,
    val airDb: Float? = null,
    val preset: String? = null
)

/** حالة معادل الصوت المحفوظة (5 شرائح). */
data class MusicEqJson(
    val enabled: Boolean? = null,
    val bands: List<Float>? = null,
    val preset: String? = null
)

/** حالة الخفض التلقائي المحفوظة (Voice-Over). */
data class DuckJson(
    val enabled: Boolean? = null,
    val duckDb: Float? = null,
    val holdMs: Float? = null
)

/** حالة إلغاء الصدى المحفوظة (AEC). */
data class AecJson(
    val enabled: Boolean? = null
)

/** حالة وحدة المزج المحفوظة كاملة. */
data class MixerJson(
    val micFader: Float? = null,
    val musicFader: Float? = null,
    val masterFader: Float? = null,
    val voice: VoiceFxJson? = null,
    val musicEq: MusicEqJson? = null,
    val duck: DuckJson? = null,
    val aec: AecJson? = null
)

// ================= الحالة الجارية (محسومة) =================

data class VoiceFxState(
    val enabled: Boolean = true,
    val hpfOn: Boolean = true,
    val gateOn: Boolean = false,
    val gateThresholdDb: Float = -45f,
    val gateReleaseMs: Float = 250f,
    val agcOn: Boolean = true,
    val agcTargetDb: Float = -18f,
    val agcMaxBoostDb: Float = 18f,
    val compOn: Boolean = false,
    val compThresholdDb: Float = -20f,
    val compRatio: Float = 3f,
    val compMakeupDb: Float = 3f,
    val bassDb: Float = 0f,
    val presenceDb: Float = 0f,
    val airDb: Float = 0f,
    val preset: String? = null
)

data class MusicEqState(
    val enabled: Boolean = false,
    val bands: List<Float> = listOf(0f, 0f, 0f, 0f, 0f),
    val preset: String? = null
)

/** الخفض التلقائي: على/مقدار الخفض/زمن التعليق. */
data class DuckState(
    val enabled: Boolean = true,
    val duckDb: Float = -12f,
    val holdMs: Float = 1200f
)

/** إلغاء الصدى: مفعّل افتراضيًا (مرور نظيف حين لا موسيقى). */
data class AecState(
    val enabled: Boolean = true
)

data class MixerState(
    val micFader: Float = 1.0f,
    val musicFader: Float = 1.0f,
    val masterFader: Float = 1.0f,
    val voice: VoiceFxState = VoiceFxState(),
    val musicEq: MusicEqState = MusicEqState(),
    val duck: DuckState = DuckState(),
    val aec: AecState = AecState()
)

// ================= المنطق الخالص =================

/**
 * منطق وحدة المزج: حدود الفيدرات، أنماط الإذاعة الجاهزة، تحويلات العرض،
 * وحسم أي حالة محفوظة مهما كانت ناقصة أو مشوهة — لا قيمة خارج مداها أبدًا.
 */
object MixerLogic {

    const val FADER_MAX = 1.5f

    val VOICE_PRESET_NAMES = listOf("حزمة البث", "طبيعي", "صوت المذيع", "وضوح السرد", "دفء الاستوديو")
    val MUSIC_PRESET_NAMES = listOf("مسطّح", "موسيقى هادئة", "باص دافئ", "صباحي منعش", "وضوح الكلام")

    // ---- أنماط معالج المذيع: (باس، حضور، هواء، بوابة، ضاغط، نسبة، تعويض) ----

    data class VoiceRecipe(
        val bassDb: Float, val presenceDb: Float, val airDb: Float,
        val gateOn: Boolean, val compOn: Boolean,
        val compRatio: Float, val compMakeupDb: Float,
        val gateThresholdDb: Float = -45f,
        /** null = لا يُمسّ مفتاح قناع الرعد (للأنماط القديمة)؛ true/false = يُفرض. */
        val hpfOn: Boolean? = null,
        /** null = لا يُمسّ مفتاح AGC؛ true = تُفرض التسوية ضمن الحزمة. */
        val agcOn: Boolean? = null
    )

    val VOICE_PRESETS: Map<String, VoiceRecipe> = mapOf(
        // حزمة البث الكاملة: كل حلقات السلسلة الإذاعية تُسلّح بنقرة واحدة —
        // قناع الرعد + بوابة الضجيج + تسوية AGC + الضاغط + منحنى البث
        "حزمة البث" to VoiceRecipe(
            1.5f, 3.5f, 1.0f,
            gateOn = true, compOn = true, 3f, 3f, -42f,
            hpfOn = true, agcOn = true
        ),
        "طبيعي" to VoiceRecipe(0f, 0f, 0f, gateOn = false, compOn = false, 1f, 0f),
        "صوت المذيع" to VoiceRecipe(1.5f, 4.0f, 1.5f, gateOn = true, compOn = true, 3f, 3f, -42f),
        "وضوح السرد" to VoiceRecipe(-2.0f, 6.0f, 2.0f, gateOn = true, compOn = true, 4f, 4f, -40f),
        "دفء الاستوديو" to VoiceRecipe(3.0f, 1.5f, 0f, gateOn = false, compOn = true, 2f, 2f)
    )

    // ---- أعماق الخفض التلقائي الجاهزة: (الاسم، مقدار الخفض بالديسيبل) ----

    val DUCK_DEPTHS: List<Pair<String, Float>> = listOf(
        "خفيف" to -6f,   // الموسيقى تبقى مسموعة بوضوح تحت الصوت
        "متوسط" to -12f, // توازن الاستوديو الكلاسيكي
        "قوي" to -20f    // الموسيقى تكاد تختفي أثناء الحديث
    )

    /** اسم عمق الخفض الجاهز المطابق للقيمة (تسامح نصف ديسيبل) أو null للقيمة المخصصة. */
    fun duckPresetName(db: Float): String? =
        DUCK_DEPTHS.firstOrNull { kotlin.math.abs(it.second - db) <= 0.5f }?.first

    // ---- أنماط معادل الموسيقى: 5 شرائح (80/250/1ك/3.5ك/10ك) ----

    val MUSIC_PRESETS: Map<String, List<Float>> = mapOf(
        "مسطّح" to listOf(0f, 0f, 0f, 0f, 0f),
        "موسيقى هادئة" to listOf(-1f, -0.5f, 0f, 1f, 1.5f),
        "باص دافئ" to listOf(5f, 2f, -1f, 0f, 0f),
        "صباحي منعش" to listOf(-1f, 0f, 0f, 2f, 3f),
        "وضوح الكلام" to listOf(-4f, 1f, 3f, 3f, 1f)
    )

    // ---- حدود وتنظيف ----

    fun cleanFader(v: Float): Float = v.coerceIn(0f, FADER_MAX)

    fun cleanBand(v: Float): Float = v.coerceIn(-MusicEq.MAX_DB, MusicEq.MAX_DB)

    fun cleanRatio(v: Float): Float = v.coerceIn(1f, 10f)

    fun cleanThreshold(v: Float, minDb: Float, maxDb: Float): Float = v.coerceIn(minDb, maxDb)

    /** حدود AGC: مستوى الهدف بين -30 و -6dBFS، والتعزيز الأقصى بين 6 و 30dB. */
    fun cleanAgcTarget(v: Float): Float = v.coerceIn(-30f, -6f)

    fun cleanAgcBoost(v: Float): Float = v.coerceIn(6f, 30f)

    /** حدود الخفض التلقائي: -30dB كحد أدنى، -3dB كأخف خفض، والتعليق 100..5000ms. */
    fun cleanDuckDb(v: Float): Float = v.coerceIn(-30f, -3f)

    fun cleanHoldMs(v: Float): Float = v.coerceIn(100f, 5000f)

    /** حسم حالة معالج المذيع المحفوظة — كل حقل غائب يعود لقيمته الافتراضية. */
    fun resolveVoice(j: VoiceFxJson?): VoiceFxState {
        j ?: return VoiceFxState()
        val base = VoiceFxState()
        return VoiceFxState(
            enabled = j.enabled ?: base.enabled,
            hpfOn = j.hpfOn ?: base.hpfOn,
            gateOn = j.gateOn ?: base.gateOn,
            gateThresholdDb = cleanThreshold(j.gateThresholdDb ?: base.gateThresholdDb, -60f, -20f),
            gateReleaseMs = (j.gateReleaseMs ?: base.gateReleaseMs).coerceIn(50f, 1000f),
            agcOn = j.agcOn ?: base.agcOn,
            agcTargetDb = cleanAgcTarget(j.agcTargetDb ?: base.agcTargetDb),
            agcMaxBoostDb = cleanAgcBoost(j.agcMaxBoostDb ?: base.agcMaxBoostDb),
            compOn = j.compOn ?: base.compOn,
            compThresholdDb = cleanThreshold(j.compThresholdDb ?: base.compThresholdDb, -40f, 0f),
            compRatio = cleanRatio(j.compRatio ?: base.compRatio),
            compMakeupDb = (j.compMakeupDb ?: base.compMakeupDb).coerceIn(0f, 12f),
            bassDb = cleanBand(j.bassDb ?: base.bassDb),
            presenceDb = cleanBand(j.presenceDb ?: base.presenceDb),
            airDb = cleanBand(j.airDb ?: base.airDb),
            preset = j.preset?.takeIf { it in VOICE_PRESETS.keys }
        )
    }

    /** حسم حالة معادل الموسيقى: 5 شرائح دائمًا، أي عدد آخر يُكمّل بالصفر ويُقصّ. */
    fun resolveEq(j: MusicEqJson?): MusicEqState {
        j ?: return MusicEqState()
        val raw = j.bands ?: emptyList()
        val bands = List(MusicEq.BANDS) { i -> cleanBand(raw.getOrElse(i) { 0f }) }
        return MusicEqState(
            enabled = j.enabled ?: false,
            bands = bands,
            preset = j.preset?.takeIf { it in MUSIC_PRESETS.keys }
        )
    }

    /** حسم حالة الخفض التلقائي — أي قيمة ناقصة أو مشوهة تعود للافتراضي. */
    fun resolveDuck(j: DuckJson?): DuckState {
        j ?: return DuckState()
        val base = DuckState()
        return DuckState(
            enabled = j.enabled ?: base.enabled,
            duckDb = cleanDuckDb(j.duckDb ?: base.duckDb),
            holdMs = cleanHoldMs(j.holdMs ?: base.holdMs)
        )
    }

    /** حسم حالة إلغاء الصدى. */
    fun resolveAec(j: AecJson?): AecState {
        j ?: return AecState()
        return AecState(enabled = j.enabled ?: true)
    }

    /** حسم حالة الوحدة كاملة من JSON المحفوظ. */
    fun resolve(j: MixerJson?): MixerState {
        j ?: return MixerState()
        return MixerState(
            micFader = cleanFader(j.micFader ?: 1.0f),
            musicFader = cleanFader(j.musicFader ?: 1.0f),
            masterFader = cleanFader(j.masterFader ?: 1.0f),
            voice = resolveVoice(j.voice),
            musicEq = resolveEq(j.musicEq),
            duck = resolveDuck(j.duck),
            aec = resolveAec(j.aec)
        )
    }

    /** تطبيق نمط معالج مذيع على حالة حية — حلقات الحزمة الكاملة تُفرض عند تحديدها. */
    fun applyVoicePreset(state: VoiceFxState, name: String): VoiceFxState {
        val r = VOICE_PRESETS[name] ?: return state
        return state.copy(
            bassDb = r.bassDb, presenceDb = r.presenceDb, airDb = r.airDb,
            gateOn = r.gateOn, compOn = r.compOn,
            compRatio = r.compRatio, compMakeupDb = r.compMakeupDb,
            gateThresholdDb = r.gateThresholdDb,
            hpfOn = r.hpfOn ?: state.hpfOn,
            agcOn = r.agcOn ?: state.agcOn,
            preset = name
        )
    }

    /** تطبيق نمط معادل موسيقى على حالة حية. */
    fun applyMusicPreset(state: MusicEqState, name: String): MusicEqState {
        val b = MUSIC_PRESETS[name] ?: return state
        return state.copy(bands = b.map { cleanBand(it) }, preset = name)
    }

    // ---- عرض ----

    /** نص الفيدر: 0% صامت، 100% هوية، 150% تعزيز. */
    fun faderPercent(v: Float): Int = (v * 100).toInt()

    /** ديسيبل الفيدر للعرض: 0% = −∞. */
    fun faderDb(v: Float): String =
        if (v <= 0.005f) "-\u221E" else {
            val db = 20.0 * kotlin.math.log10(v.toDouble())
            String.format("%.1f", db)
        }
}
