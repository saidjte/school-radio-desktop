package com.saidjte.schoolradio.desktop.audio

import kotlin.math.exp
import kotlin.math.max

/**
 * الخفض التلقائي للصوت (Voice-Over Ducker) — سلوك المزج الإذاعي الحقيقي:
 *
 * حين يتكلم المذيع على الميكروفون تنخفض الموسيقى آليًا بمقدار محدد قابل للضبط،
 * وحين يسكت تعود الموسيقى تدريجيًا بعد زمن تعليق — بلا أن يلمس المذيع أي فيدر.
 * الانتقالات ناعمة (هجوم وإفلات بزمن أُسّي) فلا طقطقة ولا قفزات.
 *
 * المنطق خالص ودون ساعة حائط: كل كتلة تمرر زمنها وحالة الميكروفون فيستنتج الكسب.
 * [DuckBus] هو الناقل المشترك الوحيد بين قناة الميكروفون (القيادة) وقناة الموسيقى (التنفيذ).
 */

// ================= المنطق الخالص =================

class Ducker {

    /** زمن الهجوم (النزول إلى الخفض) — قصير كي لا «تسبق» الموسيقى كلام المذيع. */
    var attackMs: Double = 150.0

    /** زمن الإفلات (العودة بعد انتهاء التعليق) — طويل كي لا تتقافز الموسيقى. */
    var releaseMs: Double = 700.0

    private var gain = 1.0                 // الكسب الحالي الخطي
    private var clockMs = 0.0              // ساعة داخلية تتراكم بالكتل
    private var lastVoiceMs = -1.0e9       // آخر لحظة سُمع فيها كلام

    /** هل الخفض نشط الآن؟ (للمؤشر الحي). */
    @Volatile var active = false
        private set

    /** الكسب الخطي الحالي [duckAmp..1] — للعرض أو للاختبار. */
    fun currentGain(): Float = gain.toFloat()

    /** تصفير الحالة (عند بدء البث). */
    fun reset() {
        gain = 1.0
        clockMs = 0.0
        lastVoiceMs = -1.0e9
        active = false
    }

    /**
     * خطوة واحدة لكل كتلة موسيقية/ميكروفونية.
     * @param blockMs زمن الكتلة بالمللي ثانية
     * @param micLive هل البث المباشر يعمل؟
     * @param voiceActive هل يُسمع كلام الآن؟ (بوابة مفتوحة، أو مستوى فوق العتبة)
     * @param duckDb مقدار الخفض بالديسيبل (سالب)
     * @param holdMs زمن التعليق: كم يبقى الخفض بعد آخر كلمة
     * @return كسب الموسيقى الخطي للكتلة الحالية
     */
    fun process(
        blockMs: Double,
        micLive: Boolean,
        voiceActive: Boolean,
        duckDb: Double,
        holdMs: Double
    ): Float {
        val step = max(blockMs, 1.0)
        clockMs += step
        if (micLive && voiceActive) lastVoiceMs = clockMs
        val withinHold = (clockMs - lastVoiceMs) < holdMs
        val wantDuck = micLive && (voiceActive || withinHold)
        active = wantDuck
        val target = if (wantDuck) Pcm16.dbToAmp(duckDb) else 1.0
        val tau = if (target < gain) max(attackMs, 10.0) else max(releaseMs, 10.0)
        val alpha = 1.0 - exp(-step / tau)
        gain += (target - gain) * alpha
        // الأمان: الكسب لا يتجاوز الوحدة أبدًا
        if (gain > 1.0) gain = 1.0
        return gain.toFloat()
    }
}

// ================= الناقل المشترك =================

/**
 * الناقل الوحيد بين قناتي المزج: قناة الميكروفون تقود (تحدّث الكسب والحالة كل كتلة)،
 * وقناة الموسيقى تنفّذ (تقرأ الكسب قبل كتابة الخط). حقول Volatile فلا سباقات،
 * والمعاملات تُحدَّث من الواجهة لحظيًا وتسري على الكتلة التالية.
 */
object DuckBus {

    /** هل الخفض التلقائي مفعّل إطلاقًا؟ */
    @Volatile var enabled: Boolean = true

    /** مقدار الخفض بالديسيبل (سالب، مثالي: -12). */
    @Volatile var duckDb: Float = -12f

    /** زمن التعليق بعد آخر كلمة بالمللي ثانية. */
    @Volatile var holdMs: Float = 1200f

    /** عتبة «يُسمع كلام» عندما تكون بوابة الضجيج معطلة (مستوى معياري 0..1). */
    @Volatile var thresholdLevel: Float = 0.15f

    /** الكسب الجاري — تكتبه قناة الميكروفون وتقرأه قناة الموسيقى. */
    @Volatile var musicGain: Float = 1f

    /** هل الخفض يعمل الآن (للمؤشر الحي في الواجهة). */
    @Volatile var active: Boolean = false
}
