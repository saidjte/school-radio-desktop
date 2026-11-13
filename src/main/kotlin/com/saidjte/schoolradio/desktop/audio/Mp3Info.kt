package com.saidjte.schoolradio.desktop.audio

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer

/**
 * محلّل ترويسات MP3 خالص (دون فك ترميز): يجد أول إطار صالح، ويحسب حجم الإطار،
 * ويقدّر المدة (دقيقة عبر وسم Xing/Info، وإلا بتقدير CBR). قابل للاختبار الوحدوي.
 */
object Mp3Info {

    data class FrameHeader(
        val version: Int,        // 1 = MPEG1, 2 = MPEG2, 25 = MPEG2.5
        val layer: Int,          // 1..3
        val sampleRate: Int,
        val bitRateKbps: Int,
        val channels: Int,
        val samplesPerFrame: Int,
        val frameSize: Int       // بالبايتات شاملة الحشو
    )

    data class Info(
        val dataOffset: Long,    // إزاحة أول إطار صالح في الملف
        val first: FrameHeader,
        val durationMs: Long,
        val isVbr: Boolean,
        val dataBytes: Long = 0L,     // حجم البيانات الصوتية (بعد ID3v2 وقبل ID3v1) — للسعي بالبايتات (v0.23.0)
        val toc: ByteArray? = null    // جدول Xing المئوي (100 بايت: موضع بايت لكل 1% زمني) — سعي دقيق للـVBR (v0.23.0)
    )

    private val BITRATES_V1_L3 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val BITRATES_V1_L2 = intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384)
    private val BITRATES_V1_L1 = intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448)
    private val BITRATES_LSF = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val SAMPLE_RATES = mapOf(
        1 to intArrayOf(44100, 48000, 32000),
        2 to intArrayOf(22050, 24000, 16000),
        25 to intArrayOf(11025, 12000, 8000)
    )

    /** قراءة حجم وسم ID3v2 من ترويسة 10 بايتات (synchsafe)، أو -1 إن لم توجد. */
    fun id3v2Size(head: ByteArray): Int {
        if (head.size < 10) return -1
        val isId3 = head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()
        if (!isId3) return -1
        val size = ((head[6].toInt() and 0x7F) shl 21) or
                ((head[7].toInt() and 0x7F) shl 14) or
                ((head[8].toInt() and 0x7F) shl 7) or
                (head[9].toInt() and 0x7F)
        return size + 10
    }

    /** تجاوز وسم ID3v2 من بداية مجرى. يعيد true إن وُجد وسم وتُجُوز. */
    fun skipId3v2(input: InputStream): Boolean {
        input.mark(10)
        val head = ByteArray(10)
        var read = 0
        while (read < 10) {
            val r = input.read(head, read, 10 - read)
            if (r < 0) {
                input.reset()
                return false
            }
            read += r
        }
        val size = id3v2Size(head)
        if (size < 0) {
            input.reset()
            return false
        }
        var remaining = size - 10
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val r = input.read(buf, 0, minOf(remaining, buf.size))
            if (r < 0) break
            remaining -= r
        }
        return true
    }

    /** تحليل ترويسة إطار MP3 (4 بايتات تبدأ بمزامنة 0xFFEx). */
    fun parseFrame(b: ByteArray, off: Int): FrameHeader? {
        if (off + 4 > b.size) return null
        val b0 = b[off].toInt() and 0xFF
        val b1 = b[off + 1].toInt() and 0xFF
        val b2 = b[off + 2].toInt() and 0xFF
        val b3 = b[off + 3].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null
        val versionBits = (b1 shr 3) and 0x03   // 0=MPEG2.5, 1=محجوز, 2=MPEG2, 3=MPEG1
        val layerBits = (b1 shr 1) and 0x03     // 0=محجوز, 1=III, 2=II, 3=I
        if (versionBits == 1 || layerBits == 0) return null
        val version = when (versionBits) { 3 -> 1; 2 -> 2; else -> 25 }
        val layer = when (layerBits) { 1 -> 3; 2 -> 2; else -> 1 }
        val bitrateIndex = (b2 shr 4) and 0x0F
        val sampleRateIndex = (b2 shr 2) and 0x03
        if (bitrateIndex == 0 || bitrateIndex == 15) return null
        if (sampleRateIndex == 3) return null
        val padding = (b2 shr 1) and 0x01
        val modeBits = (b3 shr 6) and 0x03
        val channels = if (modeBits == 3) 1 else 2

        val bitRateKbps = if (version == 1) when (layer) {
            3 -> BITRATES_V1_L3[bitrateIndex]
            2 -> BITRATES_V1_L2[bitrateIndex]
            else -> BITRATES_V1_L1[bitrateIndex]
        } else BITRATES_LSF[bitrateIndex]
        val sampleRate = SAMPLE_RATES.getValue(version)[sampleRateIndex]
        val samplesPerFrame = when (layer) {
            1 -> 384
            2 -> 1152
            else -> if (version == 1) 1152 else 576
        }
        val paddingBytes = if (layer == 1) 4 else 1
        val frameSize = (samplesPerFrame.toLong() * bitRateKbps * 1000L / (sampleRate.toLong() * 8L)).toInt() +
                padding * paddingBytes
        return FrameHeader(version, layer, sampleRate, bitRateKbps, channels, samplesPerFrame, frameSize)
    }

    /** تحليل ملف MP3 كامل: أول إطار + مدة تقديرية. يعيد null عند الفشل. */
    fun analyze(file: File): Info? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val fileLen = raf.length()
                if (fileLen < 128) return null
                // v0.49.0 «الصيغ المدعومة»: رأس 1MB بدل 64KB — ملفات MP3 بأغلفة
                // ألبوم مضمنة (APIC) يصل وسم ID3v2 فيها إلى مئات الكيلوبايتات
                // كانت تسقط خارج نافذة البحث فيفشل التحليل ويرفض القارئ ملفًا
                // سليمًا («بعض ملفات mp3 لا تعمل») — الآن تُقرأ كاملة وتُفحص كلها
                val head = ByteArray(1 shl 20)
                val headLen = raf.read(head).coerceAtLeast(0)

                var offset = id3v2Size(head)
                if (offset < 0) offset = 0

                var first: FrameHeader? = null
                var frameOffset = offset
                val searchLimit = headLen - 4
                while (frameOffset < searchLimit) {
                    val h = parseFrame(head, frameOffset)
                    if (h != null) {
                        // تحقق: الإطار التالي المتوقع يجب أن يبدأ بمزامنة إن توفرت البايتات
                        val next = frameOffset + h.frameSize
                        if (next + 1 < headLen) {
                            val n1 = head[next].toInt() and 0xFF
                            val n2 = head[next + 1].toInt() and 0xFF
                            if (n1 == 0xFF && (n2 and 0xE0) == 0xE0) {
                                first = h
                                offset = frameOffset
                                break
                            }
                        } else {
                            first = h
                            offset = frameOffset
                            break
                        }
                    }
                    frameOffset++
                }
                val fh = first ?: return null

                // ID3v1 في نهاية الملف؟
                raf.seek(fileLen - 128)
                val tail = ByteArray(128)
                raf.readFully(tail)
                val hasId3v1 = String(tail, 0, 3, Charsets.US_ASCII) == "TAG"
                val dataEnd = if (hasId3v1) fileLen - 128 else fileLen
                val dataBytes = dataEnd - offset

                // وسم Xing/Info لإحصاء دقيق للإطارات (VBR)
                val sideInfo = when {
                    fh.version == 1 && fh.channels == 2 -> 32
                    fh.version == 1 && fh.channels == 1 -> 17
                    fh.channels == 2 -> 17
                    else -> 9
                }
                val xingPos = (offset + 4 + sideInfo).toInt()
                var frames = -1L
                var isVbr = false
                var toc: ByteArray? = null
                if (xingPos + 16 <= headLen) {
                    val tag = String(head, xingPos, 4, Charsets.US_ASCII)
                    if (tag == "Xing" || tag == "Info") {
                        val flags = ((head[xingPos + 4].toInt() and 0xFF) shl 24) or
                                ((head[xingPos + 5].toInt() and 0xFF) shl 16) or
                                ((head[xingPos + 6].toInt() and 0xFF) shl 8) or
                                (head[xingPos + 7].toInt() and 0xFF)
                        // حقول الوسم تتالي حسب الأعلام: الإطارات ثم البايتات ثم الجدول
                        var fieldPos = xingPos + 8
                        if (flags and 0x01 != 0 && fieldPos + 4 <= headLen) {
                            frames = ((head[fieldPos].toInt() and 0xFF).toLong() shl 24) or
                                    ((head[fieldPos + 1].toInt() and 0xFF).toLong() shl 16) or
                                    ((head[fieldPos + 2].toInt() and 0xFF).toLong() shl 8) or
                                    (head[fieldPos + 3].toInt() and 0xFF).toLong()
                            fieldPos += 4
                        }
                        if (flags and 0x02 != 0) fieldPos += 4 // حقل حجم البايتات — تجاوزه
                        if (flags and 0x04 != 0 && fieldPos + 100 <= headLen) {
                            toc = head.copyOfRange(fieldPos, fieldPos + 100)
                        }
                        isVbr = tag == "Xing"
                    }
                }

                val durationMs = if (frames > 0) {
                    frames * fh.samplesPerFrame * 1000L / fh.sampleRate
                } else {
                    dataBytes * 8000L / (fh.bitRateKbps * 1000L)
                }
                Info(offset.toLong(), fh, durationMs, isVbr, dataBytes, toc)
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * تحسّس الإطار الأول بفكّه الفعلي عبر JLayer (v0.49.0 «الصيغ المدعومة») —
 * مصدر الحقيقة لقنوات الخرج ومعدلها حتى لو فشل تحليل الترويسات كله: Bitstream
 * يقفز فوق أي مخلفات قبل المزامنة بنفسه، فالملفات ذات الرؤوس الشاذة (ولو
 * أعيدت تسميتها) تُفكّ إن كانت MP3 حقيقية وتُرفض برسالة صادقة إن لم تكن.
 * خالص بلا عتاد صوتي — يُختبر وحدويًا على كل العينات.
 */
internal object Mp3Probe {

    data class FirstFrame(
        val sampleRate: Int,     // معدل خرج فك JLayer الفعلي (MPEG2/2.5 أنصف منه في الترويسة)
        val channels: Int,
        val samplesPerFrame: Int // 1152 لـ MPEG-1 و576 لـ MPEG-2/2.5 (Layer 3)
    )

    /** فك أول إطار صالح من الملف — null عند عدم وجود صوت MP3 يُفك أصلًا. */
    fun decodeFirstFrame(file: File): FirstFrame? {
        var bs: Bitstream? = null
        return try {
            val s = BufferedInputStream(FileInputStream(file), 1 shl 16)
            Mp3Info.skipId3v2(s)
            val b = Bitstream(s)
            bs = b
            val dec = Decoder()
            var guard = 0
            while (guard++ < 4096) {
                val h = try { b.readFrame() } catch (_: Exception) { null } ?: break
                try {
                    val d = dec.decodeFrame(h, b)
                    if (d is SampleBuffer && d.buffer.isNotEmpty()) {
                        val freq = dec.outputFrequency
                        return FirstFrame(
                            freq,
                            dec.outputChannels,
                            if (freq >= 32000) 1152 else 576
                        )
                    }
                } catch (_: Exception) {
                    // إطار شاذ: جرب التالي
                } finally {
                    try { b.closeFrame() } catch (_: Exception) { }
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            try { bs?.close() } catch (_: Exception) { }
        }
    }

    /** عدّ إطارات الملف كاملًا دون فك — مدة مضبوطة للملفات بلا ترويسة تحليلية. */
    fun countFramesMs(file: File, first: FirstFrame): Long {
        var bs: Bitstream? = null
        return try {
            val s = BufferedInputStream(FileInputStream(file), 1 shl 16)
            Mp3Info.skipId3v2(s)
            val b = Bitstream(s)
            bs = b
            var n = 0L
            while (true) {
                val h = try { b.readFrame() } catch (_: Exception) { null } ?: break
                n++
                try { b.closeFrame() } catch (_: Exception) { }
            }
            n * first.samplesPerFrame * 1000L / first.sampleRate
        } catch (_: Exception) {
            0L
        } finally {
            try { bs?.close() } catch (_: Exception) { }
        }
    }
}
