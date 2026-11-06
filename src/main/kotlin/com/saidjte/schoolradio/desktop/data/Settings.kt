package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * اختيار الأجهزة المحفوظ — طلب المستخدم v0.20.0 «حفظ اختيار الأجهزة بين الجلسات»:
 * الأسماء وحدها تُحفظ (لا مؤشرات أجهزة)، وعند الإقلاع يُطابق كل اسم مع الجهاز
 * الموجود فعليًا — جهاز غير موصول مؤقتًا لا يُمحى اختياره إذ لا نكتب إلا عند
 * اختيار صريح من المذيع.
 */
data class DevicesJson(
    val input1: String? = null,
    val input2: String? = null,
    val output1: String? = null,
    val output2: String? = null
)

/**
 * تفضيلات شاشة القراءة المحفوظة — جزء من «طور واجهة شاشة القراءة الى شاشة
 * احترافية»: حجم الخط والسرعة ومتابعة الجارية تُذكر بين الجلسات.
 */
data class ReadingJson(
    val fontSp: Float? = null,
    val speed: String? = null,
    val follow: Boolean? = null
)

/**
 * أبعاد حيزات شاشة الاستوديو المحفوظة — طلب المستخدم v0.21.0 «أعطي المستعمل
 * طريقة التحكم في ابعاد كل الحيزات الموجودة في شاشة الاستوديو بواسطة الفأرة»:
 * سحب الفواصل يغيّر الأبعاد وتُكتب في settings.json فتعود كما ضبطها المذيع
 * عند الإقلاع. القيم أرقام مطلقة (عرض بالـdp ونِسَب ارتفاع 0..1) وتُحبَس
 * عند التحميل داخل حدود الواجهة فلا يكسر ملف معدَّل يدويًا التخطيط.
 */
data class LayoutJson(
    /** عرض عمود الجانب الأيمن (التسجيلات + الجدولة) بالـdp. */
    val rightWidth: Float? = null,
    /** عرض عمود البث بالـdp. */
    val broadcastWidth: Float? = null,
    /** نصيب الحيّز العلوي في الجانب الأيمن (التسجيلات) من الارتفاع — الباقي للجدولة. */
    val rightTopFrac: Float? = null,
    /** نصيب شاشة السكريبت من ارتفاع العمود الأوسط — الباقي للمشغل المستقل. */
    val midTopFrac: Float? = null,
    /** نصيب حيّز الميكروفون من ارتفاع عمود البث — الباقي لحيّز الموسيقى. */
    val bcMicFrac: Float? = null
)

/**
 * إعداد الكروس فيد المحفوظ (v0.31.0 «كروس فيد بين المقاطع»): مفعّل افتراضيًا
 * بنافذة ثلاث ثوانٍ كما في محطات البث — التلعيب المكتوب يُقَمَّع عند القراءة.
 */
data class CrossfadeJson(
    val enabled: Boolean? = null,
    val ms: Long? = null
)

/**
 * إعدادات مؤقت الفاصل الآلي المحفوظة (v0.31.0 «مؤقت فاصل آلي»):
 * تقدم آلي للفقرة التالية عند انتهاء الزمن، وجينل فاصل عند التحول،
 * ونافذة التحذير بالثواني.
 */
data class SegmentTimerJson(
    val autoAdvance: Boolean? = null,
    val sting: Boolean? = null,
    val warnSeconds: Int? = null
)

/** إعدادات التطبيق العامة — تُحفظ في settings.json. */
data class AppSettings(
    val schoolName: String? = null,
    /** وحدة المزج: فيدرات القنوات + معالج المذيع + معادل الصوت (null = الافتراضي). */
    val mixer: MixerJson? = null,
    /** اختيار أجهزة الصوت المحفوظ (أسماء — تُطابق عند الإقلاع). */
    val devices: DevicesJson? = null,
    /** تفضيلات شاشة القراءة المحفوظة. */
    val reading: ReadingJson? = null,
    /** أبعاد حيزات شاشة الاستوديو المحفوظة (سحب الفواصل). */
    val layout: LayoutJson? = null,
    /** جدار الجينغل المحفوظ: المواضع المعيّنة والمستوى العام (v0.28.0). */
    val jingle: JingleWallJson? = null,
    /** الكروس فيد بين المقاطع (v0.31.0). */
    val crossfade: CrossfadeJson? = null,
    /** مؤقت الفاصل الآلي (v0.31.0). */
    val segmentTimer: SegmentTimerJson? = null
)

/**
 * أسماء سرعات التلقيم المعروفة — خمس سرعات بعد طلب المستخدم v0.26.0
 * «أضف سرعة بطئ جدا» (بطيئة جدًا قبل الهادئة)، مع التحكم الحر بالقيمة المتصلة.
 * تعيش في طبقة البيانات كي يتعقّم المخزن الاسم المحفوظ دون اعتماد على الواجهة؛
 * تسميات enum الواجهة يجب أن تطابقها (اختبار يمنع الانفصال).
 */
object TeleprompterNames {
    val ALL = listOf("بطيئة جدًا", "هادئة", "متوسطة", "سريعة", "سريعة جدًا")
}

/**
 * التحكم الحر في سرعة التلقيم — طلب المستخدم v0.26.0 «أضف امكانية التحكم الحر
 * في سرعة التلقيم في شاشة القراءة. وأضف سرعة بطئ جدا»: القيمة متصلة بالبكسل/ثانية
 * (منزلق حر بين الحدين) إضافة إلى السرعات الجاهزة الخمس، والمحفوظ يقبل
 * اسم سرعة جاهزة (توافق مع إعدادات v0.20–v0.25) أو قيمة رقمية داخل الحدود.
 * نقية قابلة للاختبار بلا فتح موارد رسومية.
 */
object TeleprompterSpeedLogic {
    /** أدنى وأعلى سرعة زحف بالبكسل/ثانية — بطيئة جدًا للاستهلال البطيء وسريعة للإعلانات. */
    const val MIN_PX = 6f
    const val MAX_PX = 260f

    /** السرعات الجاهزة بالترتيب التصاعدي — يجب أن تطابق enum الواجهة وTeleprompterNames. */
    val PRESETS: List<Pair<String, Float>> = listOf(
        "بطيئة جدًا" to 12f,
        "هادئة" to 30f,
        "متوسطة" to 60f,
        "سريعة" to 110f,
        "سريعة جدًا" to 170f
    )

    private const val DEFAULT_PX = 60f

    /** حبس القيمة داخل حدود المنزلق. */
    fun clamp(v: Float): Float = v.coerceIn(MIN_PX, MAX_PX)

    /**
     * استرجاع السرعة من المحفوظ: اسم جاهز → قيمته، رقم داخل الحدود → القيمة،
     * غائب/فاسد → الافتراضية (متوسطة). لا يرمي أبدًا.
     */
    fun resolve(saved: String?): Float {
        if (saved == null) return DEFAULT_PX
        val t = saved.trim()
        PRESETS.firstOrNull { it.first == t }?.let { return it.second }
        t.toFloatOrNull()?.let { return clamp(it) }
        return DEFAULT_PX
    }

    /** اسم السرعة الجاهزة المطابقة تمامًا — null إن كانت قيمة حرة مخصّصة. */
    fun presetLabelFor(v: Float): String? =
        PRESETS.firstOrNull { it.second == v }?.first

    /**
     * التخزين: اسم الجاهزة إن طابقت تمامًا، وإلا القيمة الرقمية بفاصلة واحدة —
     * ما يكتبه encode يقرأه resolve دائمًا (عقد دائري يغطيه اختبار).
     */
    fun encode(v: Float): String {
        val c = clamp(v)
        presetLabelFor(c)?.let { return it }
        return java.util.Locale.ROOT.let { String.format(it, "%.1f", c) }
    }

    /** اسم العرض في الشريط العلوي: اسم الجاهزة أو «مخصّصة» للقيم الحرة. */
    fun displayName(v: Float): String = presetLabelFor(v) ?: "مخصّصة"
}

/** مخزن الإعدادات: تحميل/حفظ settings.json ذريًا مع تعقيم الحقول. */
class SettingsStore(private val file: File) {

    private var doc: AppSettings = load()

    private fun load(): AppSettings {
        val d = Json.load(file, AppSettings())
        return d.copy(schoolName = d.schoolName?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun settings(): AppSettings = doc

    fun schoolName(): String? = doc.schoolName

    fun setSchoolName(name: String) {
        doc = doc.copy(schoolName = name.trim().takeIf { it.isNotEmpty() })
        persist()
    }

    // ================= الأجهزة: حفظ الاختيار بين الجلسات =================

    /** تعقيم اسم جهاز محفوظ: قص الفراغات وحذف الفارغ. */
    private fun cleanDeviceName(n: String?): String? =
        n?.trim()?.takeIf { it.isNotEmpty() }

    /** اختيار الأجهزة المحفوظ (بأسمائه) — أسماء فارغة تُعاد null. */
    fun devices(): DevicesJson {
        val d = doc.devices ?: DevicesJson()
        return DevicesJson(
            input1 = cleanDeviceName(d.input1),
            input2 = cleanDeviceName(d.input2),
            output1 = cleanDeviceName(d.output1),
            output2 = cleanDeviceName(d.output2)
        )
    }

    /** حفظ اختيار الأجهزة — يُستدعى عند اختيار صريح من المذيع فقط. */
    fun setDevices(d: DevicesJson) {
        doc = doc.copy(
            devices = DevicesJson(
                input1 = cleanDeviceName(d.input1),
                input2 = cleanDeviceName(d.input2),
                output1 = cleanDeviceName(d.output1),
                output2 = cleanDeviceName(d.output2)
            )
        )
        persist()
    }

    // ================= شاشة القراءة: تفضيلات محفوظة =================

    /** حدود حجم خط شاشة القراءة — نفس حدود الواجهة 16..96. */
    private fun clampFontSp(v: Float?): Float? = v?.coerceIn(16f, 96f)

    /**
     * تعقيم السرعة المحفوظة: اسم جاهز معروف أو قيمة رقمية داخل حدود المنزلق —
     * ما عدا ذلك (فاسد/خارج الحدود/اسم محذوف) يُسقط فيصير الافتراضي (v0.26.0).
     */
    private fun cleanSpeed(s: String?): String? {
        val t = s?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (TeleprompterNames.ALL.any { it == t }) return t
        val v = t.toFloatOrNull() ?: return null
        if (v < TeleprompterSpeedLogic.MIN_PX || v > TeleprompterSpeedLogic.MAX_PX) return null
        return TeleprompterSpeedLogic.encode(v)
    }

    /** تفضيلات القراءة المحسومة: خط داخل الحدود وسرعة جاهزة أو حرة داخل الحدود. */
    fun reading(): ReadingJson {
        val r = doc.reading ?: ReadingJson()
        return ReadingJson(
            fontSp = clampFontSp(r.fontSp),
            speed = cleanSpeed(r.speed),
            follow = r.follow
        )
    }

    /** حفظ تفضيلات القراءة. */
    fun setReading(r: ReadingJson) {
        doc = doc.copy(
            reading = ReadingJson(
                fontSp = clampFontSp(r.fontSp),
                speed = cleanSpeed(r.speed),
                follow = r.follow
            )
        )
        persist()
    }

    // ================= أبعاد حيزات الاستوديو: حفظ سحب الفواصل (v0.21.0) =================

    /** الأبعاد المحسومة بحبس كل قيمة داخل حدود الواجهة — الافتراضي من StudioLayoutLogic. */
    fun layout(): StudioLayout = StudioLayoutLogic.resolve(doc.layout)

    /** حفظ أبعاد الحيزات — يُستدعى عند إفلات الفاصل بعد السحب. */
    fun setLayout(l: LayoutJson) {
        doc = doc.copy(layout = l)
        persist()
    }

    // ================= الكروس فيد: مفعّل ونافذة (v0.31.0) =================

    /** حالة الكروس فيد المحسومة: مفعّل بنافذة محبوسة، أو معطّل. */
    fun crossfade(): CrossfadeJson {
        val c = doc.crossfade ?: CrossfadeJson()
        return CrossfadeJson(
            enabled = c.enabled ?: true,
            ms = CrossfadeLogic.clampMs(c.ms ?: CrossfadeLogic.DEFAULT_MS)
        )
    }

    /** حفظ إعداد الكروس فيد — يُستدعى من مفتاحه في صفحة الموسيقى. */
    fun setCrossfade(enabled: Boolean, ms: Long) {
        doc = doc.copy(crossfade = CrossfadeJson(enabled = enabled, ms = CrossfadeLogic.clampMs(ms)))
        persist()
    }

    // ================= مؤقت الفاصل الآلي (v0.31.0) =================

    /** إعدادات المؤقت المحسومة بالافتراضيات المهنية عند غياب الحقول. */
    fun segmentTimer(): SegmentTimerJson {
        val t = doc.segmentTimer ?: SegmentTimerJson()
        return SegmentTimerJson(
            autoAdvance = t.autoAdvance ?: true,
            sting = t.sting ?: true,
            warnSeconds = SegmentTimerLogic.clampWarnSeconds(t.warnSeconds ?: SegmentTimerLogic.DEFAULT_WARN_SECONDS.toInt())
        )
    }

    /** حفظ إعدادات المؤقت دفعة واحدة. */
    fun setSegmentTimer(autoAdvance: Boolean, sting: Boolean, warnSeconds: Int) {
        doc = doc.copy(
            segmentTimer = SegmentTimerJson(
                autoAdvance = autoAdvance,
                sting = sting,
                warnSeconds = SegmentTimerLogic.clampWarnSeconds(warnSeconds)
            )
        )
        persist()
    }

    // ================= جدار الجينغل: المواضع والمستوى (v0.28.0) =================

    /** المواضع المحفوظة كما هي — التعقيم مع الافتراضي في JingleWallLogic.resolve. */
    fun jinglePads(): List<JinglePadJson>? = doc.jingle?.pads

    /** المستوى العام المحبوس داخل الحدود — الافتراضي عند غياب الحقل. */
    fun jingleVolume(): Float = JingleWallLogic.clampVolume(doc.jingle?.volume ?: JingleWallLogic.DEFAULT_VOLUME)

    /** حفظ الجدار دفعة واحدة — يُستدعى عند كل تعيين/مسح/تغيير مستوى. */
    fun setJingleWall(volume: Float, pads: List<JinglePadJson>) {
        doc = doc.copy(
            jingle = JingleWallJson(
                volume = JingleWallLogic.clampVolume(volume),
                pads = pads
            )
        )
        persist()
    }

    /** حالة وحدة المزج المحسومة من المحفوظ. */
    fun mixer(): MixerState = MixerLogic.resolve(doc.mixer)

    fun setMixer(state: MixerState) {
        doc = doc.copy(
            mixer = MixerJson(
                micFader = state.micFader,
                musicFader = state.musicFader,
                masterFader = state.masterFader,
                voice = VoiceFxJson(
                    enabled = state.voice.enabled,
                    hpfOn = state.voice.hpfOn,
                    gateOn = state.voice.gateOn,
                    gateThresholdDb = state.voice.gateThresholdDb,
                    gateReleaseMs = state.voice.gateReleaseMs,
                    agcOn = state.voice.agcOn,
                    agcTargetDb = state.voice.agcTargetDb,
                    agcMaxBoostDb = state.voice.agcMaxBoostDb,
                    compOn = state.voice.compOn,
                    compThresholdDb = state.voice.compThresholdDb,
                    compRatio = state.voice.compRatio,
                    compMakeupDb = state.voice.compMakeupDb,
                    bassDb = state.voice.bassDb,
                    presenceDb = state.voice.presenceDb,
                    airDb = state.voice.airDb,
                    preset = state.voice.preset
                ),
                musicEq = MusicEqJson(
                    enabled = state.musicEq.enabled,
                    bands = state.musicEq.bands,
                    preset = state.musicEq.preset
                ),
                duck = DuckJson(
                    enabled = state.duck.enabled,
                    duckDb = state.duck.duckDb,
                    holdMs = state.duck.holdMs
                ),
                aec = AecJson(enabled = state.aec.enabled)
            )
        )
        persist()
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
