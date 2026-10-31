package com.saidjte.schoolradio.desktop.audio

import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
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
 * 2) انهيار فتح الميكروفون برسالة إنجليزية خام («No line matching interface
 *    TargetDataLine…»): كان الطلب صيغة واحدة صارمة. الآن مفاوضة تلقائية على
 *    48000/44100/96000/22050/16000/8000 هرتز (أحادي ثم ثنائي)، ومسح شامل
 *    لكل أجهزة النظام عند تعذر الجهاز المختار، ورسالة عربية واضحة عند الفشل.
 */
object AudioDevices {

    data class MicDevice(val mixerInfo: Mixer.Info?, val name: String)
    data class OutDevice(val mixerInfo: Mixer.Info?, val name: String)

    const val DEFAULT_INPUT_NAME = "الميكروفون الافتراضي"
    const val DEFAULT_OUTPUT_NAME = "السماعات الافتراضية (النظام)"

    /**
     * صيغ الالتقاط المرشحة: 48000 أولًا (الوضع المشترك في ويندوز) ثم 44100،
     * ثم بقية المعدلات الشائعة — أحادي ثم ثنائي لكل معدل. كلها PCM 16 بت.
     */
    val CANDIDATE_FORMATS: List<AudioFormat> = buildList {
        for (rate in floatArrayOf(48000f, 44100f, 96000f, 22050f, 16000f, 8000f)) {
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

    private fun supports(mixer: Mixer, info: DataLine.Info): Boolean =
        try { mixer.isLineSupported(info) } catch (_: Exception) { false }

    // ================= الإدخال: الميكروفونات =================

    /** أجهزة الإدخال — تعيد دائمًا قائمة غير فارغة (بند الافتراضي عند العدم). */
    fun listMics(): List<MicDevice> {
        val out = mutableListOf<MicDevice>()
        for (mi in AudioSystem.getMixerInfo()) {
            try {
                val mixer = AudioSystem.getMixer(mi)
                val supportsAny = CANDIDATE_FORMATS.any { f ->
                    supports(mixer, DataLine.Info(TargetDataLine::class.java, f))
                }
                if (supportsAny) out += MicDevice(mi, displayName(mi))
            } catch (_: Exception) {
            }
        }
        if (out.isEmpty()) out += MicDevice(null, DEFAULT_INPUT_NAME)
        return out
    }

    /** اختيار أول صيغة يدعمها جهاز الإدخال من قائمة الصيغ المرشحة. */
    fun pickFormat(mixerInfo: Mixer.Info?): AudioFormat {
        for (f in CANDIDATE_FORMATS) {
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
     * الجهاز المختار (بكل الصيغ) ← المسار الافتراضي ← مسح شامل لكل أجهزة النظام.
     * عند تعذر كل ذلك: IOException برسالة عربية واضحة سببها الجذر.
     */
    fun openTargetLine(mixerInfo: Mixer.Info?, format: AudioFormat): TargetDataLine {
        var lastCause: Throwable? = null

        if (mixerInfo != null) {
            try {
                return rawTarget(mixerInfo, format)
            } catch (t: Throwable) { lastCause = t }
            // نفس الجهاز ببقية الصيغ المرشحة (تفاوض تلقائي)
            for (f in CANDIDATE_FORMATS) {
                if (f == format) continue
                try { return rawTarget(mixerInfo, f) } catch (t: Throwable) { lastCause = t }
            }
        }

        // المسار الافتراضي للنظام بكل الصيغ
        for (f in CANDIDATE_FORMATS) {
            try { return AudioSystem.getTargetDataLine(f) } catch (t: Throwable) { lastCause = t }
        }

        // مسح شامل: كل جهاز إدخال يعرضه النظام × كل صيغة
        for (mi in AudioSystem.getMixerInfo()) {
            for (f in CANDIDATE_FORMATS) {
                try {
                    val l = rawTarget(mi, f)
                    return l
                } catch (t: Throwable) { lastCause = t }
            }
        }

        throw IOException(describeInputFailure(lastCause), lastCause)
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
            msg.contains("No line matching") || msg.contains("is supported") ->
                "لا يوجد ميكروفون متاح في النظام (أو لا يدعم الصيغ المطلوبة). " +
                        "تأكد من توصيل الميكروفون وتفعيله في إعدادات صوت النظام، ثم اضغط «تحديث» وأعد المحاولة."
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
