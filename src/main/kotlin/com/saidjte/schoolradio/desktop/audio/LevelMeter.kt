package com.saidjte.schoolradio.desktop.audio

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * حساب مستوى الصوت من عيّنات PCM 16-bit صغيرة الطرفية (Little Endian).
 * منطق خالص قابل للاختبار الوحدوي دون أي عتاد.
 */
object LevelMeter {

    /** الجذر التربيعي لمتوسط المربعات (RMS) لقيم في النطاق [0.0, 1.0]. */
    fun rms(pcm: ByteArray, length: Int): Double {
        if (length < 2) return 0.0
        var sum = 0.0
        var count = 0
        var i = 0
        val limit = length - 1
        while (i < limit) {
            val low = pcm[i].toInt() and 0xFF
            val high = pcm[i + 1].toInt()
            val sample = (high shl 8) or low
            val v = sample / 32768.0
            sum += v * v
            count++
            i += 2
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    /**
     * تحويل RMS إلى قيمة معيارية [0.0, 1.0] للعرض على المقياس،
     * حيث floorDb هو أدنى مستوى يُعرض (كل ما دونه يُعد صمتًا).
     */
    fun normalized(rmsAmp: Double, floorDb: Double = -54.0): Double {
        if (rmsAmp <= 0.0) return 0.0
        val db = 20.0 * log10(rmsAmp)
        return ((db - floorDb) / -floorDb).coerceIn(0.0, 1.0)
    }
}
