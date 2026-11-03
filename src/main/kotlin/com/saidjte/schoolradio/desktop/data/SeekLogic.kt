package com.saidjte.schoolradio.desktop.data

/**
 * رياضيات شريط التقدم القابل للسعي (v0.23.0 «التنقل في المقطع بالنقر والسحب»):
 * نقيّة بلا اعتماد على Compose حتى تُختبَر في JUnit مباشرةً — نمط MeterLogic/GuiTokens.
 *
 * - تحويلات الموضع: النقر في أي نقطة من الشريط يعني «انتقل إلى هذا الزمن»،
 *   والسحب يعني «معاينة حية حتى الإفلات» — فالتحويل بين البايتات/النسب/المللي
 *   ثانية يجب أن يكون مطابقًا بين الرسم والإيماءة والمحرك.
 * - حالة الزحف (ScrubState): تضمن تنفيذ السعي مرة واحدة بالضبط عند الإفلات —
 *   النقر = بداية ونهاية متتاليتان، والسحب = بداية فتحايت فنهاية.
 * - جدول Xing المئوي: مواضع بايتات لكل 1% زمني في مقاطع VBR.
 */
object SeekLogic {

    /** نسبة موضع 0..1 من مدة كاملة — آمنة على المدة صفر. */
    fun fractionForMs(ms: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f
        else (ms.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

    /** موضع بالمللي ثانية لنسبة 0..1 — يقرّب للأسفل ويثبّت داخل المدة. */
    fun msForFraction(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val f = fraction.coerceIn(0f, 1f)
        return (f * durationMs).toLong().coerceIn(0L, durationMs)
    }

    /**
     * بايت نسبي 0..1 من جدول Xing المئوي (100 بايت، كل بايت = موضع 1% زمني
     * مقيسًا 0..255) لنسبة زمنية — جدول فارغ يعيد النسبة نفسها (سلوك خطي).
     */
    fun tocByteFraction(toc: ByteArray, fraction: Float): Float {
        if (toc.isEmpty()) return fraction.coerceIn(0f, 1f)
        val idx = (fraction.coerceIn(0f, 1f) * (toc.size - 1)).toInt().coerceIn(0, toc.size - 1)
        return (toc[idx].toInt() and 0xFF) / 255f
    }

    /**
     * حالة الزحف: تُغذّى من إيماءات المؤشر في StudioSeekBar وتضمن أن السعي
     * ينفَّذ مرة واحدة بالضبط عند الإفلات بقيمة آخر معاينة.
     */
    class ScrubState {
        var active: Boolean = false
            private set
        var previewMs: Long = 0L
            private set

        /** بداية زحف/نقر: معاينة فورية عند نقطة الضغط. */
        fun begin(ms: Long) {
            active = true
            previewMs = ms.coerceAtLeast(0L)
        }

        /** تحديث المعاينة أثناء السحب — يُتجاهل إن لم يبدأ الزحف أو انتهى. */
        fun update(ms: Long) {
            if (active) previewMs = ms.coerceAtLeast(0L)
        }

        /** نهاية الزحف: يعيد موضع الالتزام مرة واحدة ثم يصفّر الحالة، وإلا null. */
        fun end(): Long? {
            if (!active) return null
            val value = previewMs
            active = false
            previewMs = 0L
            return value
        }
    }
}
