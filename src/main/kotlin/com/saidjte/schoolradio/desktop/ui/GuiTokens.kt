package com.saidjte.schoolradio.desktop.ui

import com.saidjte.schoolradio.desktop.data.MeterLogic
import com.saidjte.schoolradio.desktop.data.MeterZone

/**
 * نواة هوية الواجهة البصرية (v0.22.0 «طوّر GUI التطبيق»): رياضيات نقيّة
 * بلا اعتماد على Compose حتى تُختبَر في JUnit مباشرةً.
 *
 * - حساب التباين بمقياس WCAG 2.1 (النسبة بين إضاءة لونين) ليُفرض على كل
 *   ثنائيات لون حرجة (شارة الهواء، النص على الحاويات) حدٌّ أدنى 4.5:1.
 * - خريطة لون مقاطع المقياس: لكل مقطع من مقاطع MeterLogic الأربعة والعشرين
 *   لون منطقته (أخضر/كهرماني/أحمر) — مصدر واحد تقرأه كل المقاييس.
 */
object GuiTokens {

    /** إضاءة لون قياسي sRGB بمقياس WCAG (0..1). */
    fun luminance(argb: Int): Double {
        val r = (argb shr 16 and 0xFF) / 255.0
        val g = (argb shr 8 and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun channel(c: Double): Double =
            if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** نسبة التباين WCAG بين لونين (1..21) — الحد الموصى به للنص 4.5. */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** ألوان المناطق الثلاث لكل مقطع (ARGB) — مناطق MeterLogic نفسها. */
    val zoneGreen = 0xFF43D17C.toInt()
    val zoneAmber = 0xFFFFB300.toInt()
    val zoneRed = 0xFFE53935.toInt()

    /** لون مقطع بحسب ترتيبه (0..23) — ألوان مهدأة داكنة للمنطقة المطفأة. */
    fun segmentColorArgb(index: Int, lit: Boolean): Int {
        val zone = MeterLogic.zoneOf(index.coerceIn(0, 23))
        val base = when (zone) {
            MeterZone.GREEN -> zoneGreen
            MeterZone.AMBER -> zoneAmber
            MeterZone.RED -> zoneRed
        }
        return if (lit) base else dimForZone(base)
    }

    /** إطفاء لون المنطقة إلى نسخة داكنة للخلفية (يحفظ هوية المنطقة). */
    fun dimForZone(base: Int): Int {
        val r = (base shr 16 and 0xFF)
        val g = (base shr 8 and 0xFF)
        val b = (base and 0xFF)
        fun dim(c: Int) = (c * 0.22).toInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or (dim(r) shl 16) or (dim(g) shl 8) or dim(b)
    }

    // ============== ثوابت ثيم 0.22.0 ==============
    // مُعلنة هنا بنقاوة حتى تُختبر نسب التباين والتفاوتات في JUnit
    // وتُلتقط في Theme.kt كألوان Compose فلا تنفصل الواجهة عن المصدر.

    const val ON_AIR_BG = 0xFFC62828.toInt()
    const val ON_AIR_CONTENT = 0xFFFFFFFF.toInt()

    const val BG = 0xFF0B0E12.toInt()
    const val SURFACE = 0xFF141A21.toInt()
    const val SURFACE_VARIANT = 0xFF1C242E.toInt()
    const val CARD_BORDER = 0xFF232D3A.toInt()

    /**
     * توكنز شاشة «على الهواء» الموحدة (v0.46.0 — طلب «الالوان في شاشة على
     * الهواء غير متناسقة وبعض النصوص غير واضحة»): عائلة حمراء واحدة لحالتي
     * الحية والتسجيل — النقطة بالأحمر الصلب (zoneRed) والنص بتدرجه الفاتح
     * المضبوط بقياس WCAG — بدل ثلاثة أحمر متفرقة كانت تشتت العين.
     */
    const val RECORDING_TEXT = 0xFFFF8A80.toInt()

    /** نسب التباين المقاسة مسبقًا بالحساب المطبوع (scripts/onair_contrast_v46.py). */
    const val RATIO_RECORDING_TEXT_VARIANT = 6.86
    const val RATIO_ON_AIR_CONTENT_ON_AIR_BG = 5.62
    const val RATIO_ZONE_RED_VARIANT_DOT = 3.70

    /** ثيم سليم: كل طبقة تختلف عن طبقتها العلوية بوضوح. */
    fun validateThemeLayering(): Boolean =
        BG != SURFACE && SURFACE != SURFACE_VARIANT &&
                SURFACE != CARD_BORDER && BG != SURFACE_VARIANT

    /** حُبيبات النص الحرجة تتجاوز 4.5:1. */
    fun validateCriticalContrast(): Boolean =
        contrastRatio(ON_AIR_CONTENT, ON_AIR_BG) >= 4.5

    /**
     * عقد التباين لشاشة على الهواء (v0.46.0): كل ثنائية نص/خلفية في النافذة
     * فوق 4.5:1 والنقاط الحية فوق 3:1 — لوحة موحدة مقيسة لا ذوق متغير.
     */
    fun validateOnAirContrast(): Boolean =
        contrastRatio(ON_AIR_CONTENT, ON_AIR_BG) >= 4.5 &&
                contrastRatio(RECORDING_TEXT, SURFACE_VARIANT) >= 4.5 &&
                contrastRatio(RECORDING_TEXT, SURFACE) >= 4.5 &&
                contrastRatio(RECORDING_TEXT, BG) >= 4.5 &&
                contrastRatio(0xFFB9C0CB.toInt(), BG) >= 4.5 &&
                contrastRatio(0xFFE4E7EB.toInt(), SURFACE_VARIANT) >= 4.5 &&
                contrastRatio(zoneRed, SURFACE_VARIANT) >= 3.0 &&
                contrastRatio(zoneRed, BG) >= 3.0
}
