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

/** حالة وحدة المزج المحفوظة كاملة. */
data class MixerJson(
    val micFader: Float? = null,
    val musicFader: Float? = null,
    val masterFader: Float? = null,
    val voice: VoiceFxJson? = null,
    val musicEq: MusicEqJson? = null
)

// ================= الحالة الجارية (محسومة) =================

data class VoiceFxState(
    val enabled: Boolean = true,
    val hpfOn: Boolean = true,
    val gateOn: Boolean = false,
    val gateThresholdDb: Float = -45f,
    val gateReleaseMs: Float = 250f,
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

data class MixerState(
    val micFader: Float = 1.0f,
    val musicFader: Float = 1.0f,
    val masterFader: Float = 1.0f,
    val voice: VoiceFxState = VoiceFxState(),
    val musicEq: MusicEqState = MusicEqState()
)

// ================= المنطق الخالص =================

/**
 * منطق وحدة المزج: حدود الفيدرات، أنماط الإذاعة الجاهزة، تحويلات العرض،
 * وحسم أي حالة محفوظة مهما كانت ناقصة أو مشوهة — لا قيمة خارج مداها أبدًا.
 */
object MixerLogic {

    const val FADER_MAX = 1.5f

    val VOICE_PRESET_NAMES = listOf("طبيعي", "صوت المذيع", "وضوح السرد", "دفء الاستوديو")
    val MUSIC_PRESET_NAMES = listOf("مسطّح", "موسيقى هادئة", "باص دافئ", "صباحي منعش", "وضوح الكلام")

    // ---- أنماط معالج المذيع: (باس، حضور، هواء، بوابة، ضاغط، نسبة، تعويض) ----

    data class VoiceRecipe(
        val bassDb: Float, val presenceDb: Float, val airDb: Float,
        val gateOn: Boolean, val compOn: Boolean,
        val compRatio: Float, val compMakeupDb: Float,
        val gateThresholdDb: Float = -45f
    )

    val VOICE_PRESETS: Map<String, VoiceRecipe> = mapOf(
        "طبيعي" to VoiceRecipe(0f, 0f, 0f, gateOn = false, compOn = false, 1f, 0f),
        "صوت المذيع" to VoiceRecipe(1.5f, 4.0f, 1.5f, gateOn = true, compOn = true, 3f, 3f, -42f),
        "وضوح السرد" to VoiceRecipe(-2.0f, 6.0f, 2.0f, gateOn = true, compOn = true, 4f, 4f, -40f),
        "دفء الاستوديو" to VoiceRecipe(3.0f, 1.5f, 0f, gateOn = false, compOn = true, 2f, 2f)
    )

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

    /** حسم حالة الوحدة كاملة من JSON المحفوظ. */
    fun resolve(j: MixerJson?): MixerState {
        j ?: return MixerState()
        return MixerState(
            micFader = cleanFader(j.micFader ?: 1.0f),
            musicFader = cleanFader(j.musicFader ?: 1.0f),
            masterFader = cleanFader(j.masterFader ?: 1.0f),
            voice = resolveVoice(j.voice),
            musicEq = resolveEq(j.musicEq)
        )
    }

    /** تطبيق نمط معالج مذيع على حالة حية. */
    fun applyVoicePreset(state: VoiceFxState, name: String): VoiceFxState {
        val r = VOICE_PRESETS[name] ?: return state
        return state.copy(
            bassDb = r.bassDb, presenceDb = r.presenceDb, airDb = r.airDb,
            gateOn = r.gateOn, compOn = r.compOn,
            compRatio = r.compRatio, compMakeupDb = r.compMakeupDb,
            gateThresholdDb = r.gateThresholdDb,
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
