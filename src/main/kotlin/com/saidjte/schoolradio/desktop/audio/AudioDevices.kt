package com.saidjte.schoolradio.desktop.audio

import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.Line
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.Mixer
import javax.sound.sampled.SourceDataLine
import javax.sound.sampled.TargetDataLine

/**
 * طبقة أجهزة الصوت — الإدخال والإخراج معًا (نسخة الحاسوب).
 *
 * الجذور التي يعالجها هذا الملف:
 * 1) «لا يمكن اختيار المخرج»: كان التعداد يخص الميكروفونات فقط، وكل التشغيل
 *    كان يذهب آليًا للسماعة الافتراضية. الآن نعدّد أجهزة التشغيل (SourceDataLine)
 *    وفتحها على الجهاز الذي يختاره المذيع.
 * 2) انهيار فتح الميكروفون برسالة إنجليزية خام: كان الطلب صيغة واحدة صارمة.
 *    الآن: نقرأ الصيغ التي **يعلنها الجهاز نفسه** من DataLine.Info (مع توسيع العناصر
 *    الناقصة)، وشبكة مرشحة موسّعة (9 معدلات × قناتين)، ومسح شامل لكل الأجهزة،
 *    ورسالة عربية واضحة تحفظ سبب جهاز المذيع المختار بدل آخر استثناء عابر.
 */
object AudioDevices {

    data class MicDevice(val mixerInfo: Mixer.Info?, val name: String)
    data class OutDevice(val mixerInfo: Mixer.Info?, val name: String)

    const val DEFAULT_INPUT_NAME = "الميكروفون الافتراضي"
    const val DEFAULT_OUTPUT_NAME = "السماعات الافتراضية (النظام)"

    /**
     * معدلات الالتقاط الشائعة بترتيب الأفضلية — 48000 أولًا (الوضع المشترك في ويندوز)
 * ثم 44100، ثم بقية المعدلات. أُضيف 11025/24000/32000 لأجهزة مقيّدة بمعدل وحيد
 * كان يرفض الشبكة القديمة كاملة («unsupported: … 8000 Hz» بعد إنهاك كل المحاولات).
 */
    val CANDIDATE_RATES: FloatArray =
        floatArrayOf(48000f, 44100f, 96000f, 22050f, 16000f, 11025f, 24000f, 32000f, 8000f)

    /** صيغ الالتقاط المرشحة: كل المعدلات المرشحة × (أحادي ثم ثنائي) — PCM موقّع 16 بت صغير النهاية. */
    val CANDIDATE_FORMATS: List<AudioFormat> = buildList {
        for (rate in CANDIDATE_RATES) {
            for (channels in intArrayOf(1, 2)) {
                add(AudioFormat(rate, 16, channels, true, false))
            }
        }
    }

    /** صيغ التشغيل الشائعة لتعداد أجهزة الإخراج. */
    private val PLAYBACK_FORMATS: List<AudioFormat> = listOf(
        AudioFormat(44100f, 16, 2, true, false),
        AudioFormat(48000f, 16, 2, true, false),
        AudioFormat(44100f, 16, 1, true, false),
        AudioFormat(48000f, 16, 1, true, false),
        AudioFormat(22050f, 16, 2, true, false),
        AudioFormat(22050f, 16, 1, true, false),
        AudioFormat(8000f, 16, 1, true, false)
    )

    private fun displayName(mi: Mixer.Info): String =
        mi.name.trim().ifEmpty { "جهاز صوت" }

    private fun supports(mixer: Mixer, info: Line.Info): Boolean =
        try { mixer.isLineSupported(info) } catch (_: Exception) { false }

    // ================= مفاتيح الصيغ والتوسيع (نصّي لأن AudioFormat لا يعرّف equals) =================

    private fun keyOf(f: AudioFormat): String =
        "${f.encoding}|${f.sampleRate}|${f.sampleSizeInBits}|${f.channels}|${f.isBigEndian}"

    /** إزالة التكرار بالقيمة مع حفظ الترتيب الأصلي. */
    fun dedupeFormats(list: List<AudioFormat>): List<AudioFormat> {
        val byKey = LinkedHashMap<String, AudioFormat>()
        for (f in list) byKey.putIfAbsent(keyOf(f), f)
        return byKey.values.toList()
    }

    /**
     * توسيع الصيغ التي يعلنها الجهاز: بعض الأنظمة تعلن صيغًا بعناصر ناقصة
     * (معدل غير محدد/قنوات غير محددة) فنستبدلها بشبكة المعدلات الشائعة،
     * ونبقي الصيغ الملموسة كما هي (مثلًا جهاز يقبل 24000 هرتز وحده —
     * كان يُرفض في الشبكة القديمة كاملة)، ونحصر الناتج في PCM موقّع 16 بت
     * صغير النهاية (مدخل المحرك والتسجيل).
     */
    fun expandDiscoveredFormats(raw: List<AudioFormat>): List<AudioFormat> {
        val byKey = LinkedHashMap<String, AudioFormat>()
        for (base in raw) {
            // فلترة الأساس قبل التوسيع: PCM موقّع 16 بت صغير النهاية فقط
            // (بناء الصيغة الجديدة بـ signed=true إجباريًا كان يحوّل غير الموقّعة إلى موقّعة فيتنكّر الفلتر)
            val bits = if (base.sampleSizeInBits == AudioSystem.NOT_SPECIFIED) 16 else base.sampleSizeInBits
            if (base.encoding != AudioFormat.Encoding.PCM_SIGNED || bits != 16 || base.isBigEndian) continue
            val rates = if (base.sampleRate == AudioSystem.NOT_SPECIFIED.toFloat()) {
                CANDIDATE_RATES
            } else floatArrayOf(base.sampleRate)
            val channels = if (base.channels == AudioSystem.NOT_SPECIFIED) intArrayOf(1, 2) else intArrayOf(base.channels)
            for (r in rates) for (c in channels) {
                val f = AudioFormat(r, bits, c, true, false)
                byKey.putIfAbsent(keyOf(f), f)
            }
        }
        return byKey.values.toList()
    }

    /** ترتيب صيغ الالتقاط: المعدلات بترتيب الأفضلية ثم أحادي قبل ثنائي (مستقر لما بعدها). */
    fun rankInputFormats(list: List<AudioFormat>): List<AudioFormat> {
        fun rateRank(f: AudioFormat): Int {
            for (i in CANDIDATE_RATES.indices) if (CANDIDATE_RATES[i] == f.sampleRate) return i
            return CANDIDATE_RATES.size
        }
        return list.sortedWith(compareBy<AudioFormat> { rateRank(it) }.thenBy { it.channels })
    }

    /** الصيغ التي يعلنها جهاز الإدخال نفسه من معلومات خطوط الالتقاط — فارغة إن لم يتاح الإعلان. */
    private fun discoveredInputFormats(mixerInfo: Mixer.Info?): List<AudioFormat> {
        if (mixerInfo == null) return emptyList()
        return try {
            val mixer = AudioSystem.getMixer(mixerInfo)
            val infos = mixer.getTargetLineInfo(Line.Info(TargetDataLine::class.java))
            expandDiscoveredFormats(
                infos.flatMap { (it as? DataLine.Info)?.formats?.toList() ?: emptyList() }
            )
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** قائمة المحاولة لجهاز إدخال: الصيغة المطلوبة أولًا ثم المعلنة من الجهاز ثم الشبكة المرشحة. */
    private fun orderedInputFormats(mixerInfo: Mixer.Info?, requested: AudioFormat?): List<AudioFormat> {
        val head = if (requested != null) listOf(requested) else emptyList()
        return dedupeFormats(head + rankInputFormats(dedupeFormats(discoveredInputFormats(mixerInfo) + CANDIDATE_FORMATS)))
    }

    // ================= الإدخال: الميكروفونات =================

    /** أجهزة الإدخال — تعيد دائمًا قائمة غير فارغة (بند الافتراضي عند العدم). */
    fun listMics(): List<MicDevice> {
        val out = mutableListOf<MicDevice>()
        for (mi in AudioSystem.getMixerInfo()) {
            try {
                val mixer = AudioSystem.getMixer(mi)
                // وجود أي خط التقاط بلا قيد صيغة: فلا يختفي ميكروفون مقيد بنسق غير مرشّح من القائمة
                val anyTarget = supports(mixer, Line.Info(TargetDataLine::class.java))
                val supportsAny = anyTarget || CANDIDATE_FORMATS.any { f ->
                    supports(mixer, DataLine.Info(TargetDataLine::class.java, f))
                }
                if (supportsAny) out += MicDevice(mi, displayName(mi))
            } catch (_: Exception) {
            }
        }
        if (out.isEmpty()) out += MicDevice(null, DEFAULT_INPUT_NAME)
        return out
    }

    /** اختيار أول صيغة يدعمها جهاز الإدخال من صيغه المعلنة ثم الشبكة المرشحة. */
    fun pickFormat(mixerInfo: Mixer.Info?): AudioFormat {
        for (f in orderedInputFormats(mixerInfo, null)) {
            try {
                val supported = if (mixerInfo != null) {
                    supports(AudioSystem.getMixer(mixerInfo), DataLine.Info(TargetDataLine::class.java, f))
                } else {
                    try {
                        AudioSystem.isLineSupported(DataLine.Info(TargetDataLine::class.java, f))
                    } catch (_: Exception) { false }
                }
                if (supported) return f
            } catch (_: Exception) {
            }
        }
        return CANDIDATE_FORMATS.first()
    }

    /**
     * فتح خط التقاط على الجهاز المحدد مع دروع احتياطية متدرجة:
     * الجهاز المختار (صيغته المعلنة + الشبكة) ← المسار الافتراضي ← مسح شامل لكل أجهزة النظام.
     * سبب جهاز المذيع المختار يُحفظ ويُعرض في الرسالة النهائية —
     * فلا يُستبدل بخطأ عابر من آخر جهاز في المسح.
     */
    fun openTargetLine(mixerInfo: Mixer.Info?, format: AudioFormat): TargetDataLine {
        var selectedCause: Throwable? = null
        var lastCause: Throwable? = null

        if (mixerInfo != null) {
            for (f in orderedInputFormats(mixerInfo, format)) {
                try {
                    return rawTarget(mixerInfo, f)
                } catch (t: Throwable) {
                    lastCause = t
                    if (selectedCause == null) selectedCause = t
                }
            }
        }

        // المسار الافتراضي للنظام بكل الصيغ
        for (f in CANDIDATE_FORMATS) {
            try { return AudioSystem.getTargetDataLine(f) } catch (t: Throwable) { lastCause = t }
        }

        // مسح شامل: كل جهاز إدخال يعرضه النظام × كل صيغة (المعلنة + المرشحة)
        for (mi in AudioSystem.getMixerInfo()) {
            for (f in orderedInputFormats(mi, null)) {
                try {
                    return rawTarget(mi, f)
                } catch (t: Throwable) { lastCause = t }
            }
        }

        throw IOException(describeInputFailure(selectedCause ?: lastCause), lastCause)
    }

    private fun rawTarget(mixerInfo: Mixer.Info?, format: AudioFormat): TargetDataLine {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        return if (mixerInfo != null) {
            AudioSystem.getMixer(mixerInfo).getLine(info) as TargetDataLine
        } else {
            AudioSystem.getTargetDataLine(format)
        }
    }

    // ================= الإخراج: السماعات =================

    /**
     * أجهزة التشغيل (السماعات) — أول بند دائمًا هو الافتراضي للنظام،
     * ثم كل جهاز يعرضه النظام يدعم إحدى صيغ التشغيل الشائعة.
     */
    fun listOutputs(): List<OutDevice> {
        val out = mutableListOf<OutDevice>()
        for (mi in AudioSystem.getMixerInfo()) {
            try {
                val mixer = AudioSystem.getMixer(mi)
                val supportsAny = PLAYBACK_FORMATS.any { f ->
                    supports(mixer, DataLine.Info(SourceDataLine::class.java, f))
                }
                if (supportsAny) out += OutDevice(mi, displayName(mi))
            } catch (_: Exception) {
            }
        }
        out.add(0, OutDevice(null, DEFAULT_OUTPUT_NAME))
        return out
    }

    /**
     * فتح خط تشغيل على المخرج الذي اختاره المذيع.
     * الجهاز المختار ← احتياطًا السماعة الافتراضية للنظام —
     * فلا يصمت التشغيل أبدًا لخلل في جهاز بعينه.
     */
    fun openSourceLine(mixerInfo: Mixer.Info?, format: AudioFormat): SourceDataLine {
        if (mixerInfo != null) {
            try {
                val info = DataLine.Info(SourceDataLine::class.java, format)
                val mixer = AudioSystem.getMixer(mixerInfo)
                if (supports(mixer, info)) {
                    return mixer.getLine(info) as SourceDataLine
                }
            } catch (_: Exception) {
            }
        }
        try {
            return AudioSystem.getSourceDataLine(format)
        } catch (t: Throwable) {
            throw IOException(describeOutputFailure(t), t)
        }
    }

    // ================= رسائل عربية لأسباب الجذر =================

    /** ترجمة أسباب فتح الإدخال إلى رسالة عربية مفهومة بدل الرسالة الإنجليزية الخام. */
    fun describeInputFailure(cause: Throwable?): String {
        val msg = cause?.message ?: ""
        return when {
            // عائلة «النسق غير مدعوم»: «No line matching…»، «unsupported: interface TargetDataLine supporting format…»
            msg.contains("No line matching") || msg.contains("is supported") ||
                    msg.contains("unsupported") || msg.contains("supporting format") ->
                "تعذّر فتح الميكروفون: الجهاز لا يقبل أي نسق صوتي مجرَّب. " +
                        "جرّب: (1) الإعدادات ← الخصوصية والأمان ← الميكروفون: فعّل الوصول لتطبيقات سطح المكتب، " +
                        "(2) أغلق التطبيقات التي تستخدم الميكروفون، " +
                        "(3) اضغط «تحديث» ثم اختر الميكروفون من جديد."
            cause is LineUnavailableException ->
                "جهاز الصوت مشغول من تطبيق آخر أو غير متاح حاليًا. أغلق التطبيقات التي تستخدم الميكروفون ثم أعد المحاولة."
            cause is SecurityException ->
                "النظام منع الوصول إلى الميكروفون (إعدادات الخصوصية). اسمح بالوصول ثم أعد المحاولة."
            else -> "تعذّر فتح الميكروفون: ${cause?.message ?: "خطأ غير معروف"}"
        }
    }

    /** ترجمة أسباب فتح الإخراج إلى رسالة عربية مفهومة. */
    fun describeOutputFailure(cause: Throwable?): String {
        val msg = cause?.message ?: ""
        return when {
            msg.contains("No line matching") || msg.contains("is supported") ->
                "لا توجد سماعة متاحة في النظام لتشغيل الصوت. تأكد من توصيل السماعات وعدم تعطيلها في إعدادات صوت النظام."
            cause is LineUnavailableException ->
                "مخرج الصوت مشغول من تطبيق آخر أو غير متاح حاليًا. أغلق التطبيقات التي تستخدم السماعات ثم أعد المحاولة."
            else -> "تعذّر فتح مخرج الصوت: ${cause?.message ?: "خطأ غير معروف"}"
        }
    }
}
