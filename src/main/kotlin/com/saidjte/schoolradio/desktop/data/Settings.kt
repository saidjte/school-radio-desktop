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
    val layout: LayoutJson? = null
)

/**
 * أسماء سرعات التلقيم المعروفة — أربع سرعات بعد طلب v0.20.0 «إضافة سرعة رابعة».
 * تعيش في طبقة البيانات كي يتعقّم المخزن الاسم المحفوظ دون اعتماد على الواجهة؛
 * تسميات enum الواجهة يجب أن تطابقها (اختبار يمنع الانفصال).
 */
object TeleprompterNames {
    val ALL = listOf("هادئة", "متوسطة", "سريعة", "سريعة جدًا")
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

    /** تفضيلات القراءة المحسومة: خط داخل الحدود وسرعة من الأسماء المعروفة فقط. */
    fun reading(): ReadingJson {
        val r = doc.reading ?: ReadingJson()
        return ReadingJson(
            fontSp = clampFontSp(r.fontSp),
            speed = r.speed?.takeIf { s -> TeleprompterNames.ALL.any { it == s } },
            follow = r.follow
        )
    }

    /** حفظ تفضيلات القراءة. */
    fun setReading(r: ReadingJson) {
        doc = doc.copy(
            reading = ReadingJson(
                fontSp = clampFontSp(r.fontSp),
                speed = r.speed?.takeIf { s -> TeleprompterNames.ALL.any { it == s } },
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
