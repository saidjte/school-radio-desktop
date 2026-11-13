package com.saidjte.schoolradio.desktop.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * معلومات FLAC الخالصة (v0.49.0 «الصيغ المدعومة»): قراءة كتلة STREAMINFO
 * من أول الملف — يقرأها jflac نفسه خطأً فيصيّر طول الإطارات بحجم الملف
 * بالبايتات فتظهر مدة كل ملف FLAC ناقصة في القوائم (عقد الاختبار كشفها:
 * ملف 3 ثوان أُبلغ عنه بمدة ~1 ثانية). STREAMINFO يمدّنا بمعدل العينات
 * وعددها الكلي بدقة: المدة = الكلي*1000/المعدل.
 */
internal object FlacInfo {

    data class Info(val sampleRate: Int, val channels: Int, val totalSamples: Long)

    fun analyze(file: File): Info? = try {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(4 + 4 + 34) // وسم fLaC + ترويسة الكتلة + STREAMINFO
            if (raf.read(head) < head.size) return null
            if (head[0] != 'f'.code.toByte() || head[1] != 'L'.code.toByte() ||
                head[2] != 'a'.code.toByte() || head[3] != 'C'.code.toByte()
            ) return null
            val type = head[4].toInt() and 0x7F
            if (type != 0) return null // STREAMINFO يجب أن تكون أول كتلة
            val b = head.copyOfRange(8, 8 + 34)
            fun u(n: Int, bitOff: Int): Long {
                var v = 0L
                for (i in 0 until n) {
                    val bit = bitOff + i
                    val byte = b[bit / 8].toInt() and 0xFF
                    v = (v shl 1) or (((byte shr (7 - bit % 8)) and 0x01).toLong())
                }
                return v
            }
            val sampleRate = u(20, 80).toInt()
            val channels = (u(3, 101).toInt() + 1)
            val totalSamples = u(36, 108)
            if (sampleRate <= 0) null else Info(sampleRate, channels, totalSamples)
        }
    } catch (_: Throwable) {
        null
    }

    fun durationMsOf(file: File): Long {
        val i = analyze(file) ?: return -1L
        return if (i.totalSamples > 0) i.totalSamples * 1000L / i.sampleRate else -1L
    }
}

/**
 * مدد إضافية خارج AudioSystem frameLength — لصيغ يقرؤها القارئ صراحةً
 * وتفشل المكتبات في إبلاغ طولها (FLAC بالبايتات، AAC بلا طول إطاري، OGG
 * بلا طول أصلًا فمعدله من حزمة التعريف وعدد عيناته من حبيبة آخر صفحة).
 */
internal object ExtraDuration {
    fun durationMsOf(file: File): Long {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".flac") -> FlacInfo.durationMsOf(file)
            name.endsWith(".m4a") || name.endsWith(".m4b") || name.endsWith(".mp4") ->
                AacSupport.mp4DurationMs(file)
            name.endsWith(".aac") -> AacSupport.adtsDurationMs(file)
            name.endsWith(".ogg") || name.endsWith(".oga") -> OggInfo.durationMsOf(file)
            else -> -1L
        }
    }
}

/**
 * مدة OGG Vorbis الخالصة (v0.49.0): vorbisspi لا يبلّغ طولًا إطاريًا فمكثت
 * مدة كل ملف OGG صفرًا في القوائم. المنهج الصحيح: معدل العينات من حزمة
 * التعريف الأولى (0x01vorbis + version + channels + rate)، وعدد العينات
 * الكلي من حبيبة آخر صفحة OggS (granule position) — المدة = الحبيبة/المعدل.
 */
internal object OggInfo {

    private fun indexOf(hay: ByteArray, needle: ByteArray, from: Int): Int {
        var i = from
        while (i <= hay.size - needle.size) {
            var j = 0
            while (j < needle.size && hay[i + j] == needle[j]) j++
            if (j == needle.size) return i
            i++
        }
        return -1
    }

    fun durationMsOf(file: File): Long = try {
        val len = file.length()
        if (len < 64) -1L else {
            RandomAccessFile(file, "r").use { raf ->
                // معدل العينات من أول 64KB (حزمة التعريف في أول صفحة)
                val head = ByteArray(minOf(64L * 1024, len).toInt())
                raf.readFully(head)
                val vi = indexOf(head, byteArrayOf(0x01, 0x76, 0x6F, 0x72, 0x62, 0x69, 0x73), 0)
                if (vi < 0 || vi + 16 > head.size) -1L else {
                    val rate = ((head[vi + 12].toInt() and 0xFF)) or
                            ((head[vi + 13].toInt() and 0xFF) shl 8) or
                            ((head[vi + 14].toInt() and 0xFF) shl 16) or
                            ((head[vi + 15].toInt() and 0xFF) shl 24)
                    if (rate <= 0) -1L else {
                        // حبيبة آخر صفحة من آخر 128KB
                        val tailLen = minOf(128L * 1024, len).toInt()
                        raf.seek(len - tailLen)
                        val tail = ByteArray(tailLen)
                        raf.readFully(tail)
                        var granule = -1L
                        var i = tailLen - 4
                        while (i >= 0) {
                            if (tail[i] == 'O'.code.toByte() && tail[i + 1] == 'g'.code.toByte() &&
                                tail[i + 2] == 'g'.code.toByte() && tail[i + 3] == 'S'.code.toByte()
                            ) {
                                var g = 0L
                                for (k in 0 until 8) g = g or ((tail[i + 6 + k].toLong() and 0xFF) shl (8 * k))
                                if (g >= 0) { granule = g; break }
                            }
                            i--
                        }
                        if (granule > 0) granule * 1000L / rate else -1L
                    }
                }
            }
        }
    } catch (_: Throwable) {
        -1L
    }
}
