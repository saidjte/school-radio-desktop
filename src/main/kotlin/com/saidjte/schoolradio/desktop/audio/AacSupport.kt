package com.saidjte.schoolradio.desktop.audio

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import net.sourceforge.jaad.adts.ADTSDemultiplexer
import net.sourceforge.jaad.aac.Decoder
import net.sourceforge.jaad.aac.SampleBuffer

/**
 * دعم AAC/M4A الحقيقي (v0.49.0 «الصيغ المدعومة» — طلب «زيادة الصيغ المدعومة
 * في القارئ»): فاكّ SPI مكتبة jaad غير علني وفتحها الملفي محدود (moov في
 * النهاية يطلب وصولًا عشوائيًا، ومسح ADTS يفشل mark/reset، ومفسّر صناديقها
 * يتعطل مع ملفات ffmpeg الشائعة)، فنقرأ بنية MP4 بفاكّنا الخاص
 * (Mp4AacDemux) وADTS عبر ADTSDemultiplexer، ونبقي jaad لوظيفته السليمة
 * وحدها: فك إطار AAC إلى PCM. الإخراج PCM signed 16-bit لين-إندَيان يمر بمسار
 * PcmTrackReader المعروف (تحويل/مزج/مستوى) بلا مساس.
 * المدة: M4A من عدد العينات × 1024 ÷ مقياس المسار، وADTS بمسح ترويسات
 * الإطارات الخالص — كلاهما خالص قابل للاختبار الوحدوي.
 */
internal object AacSupport {

    /** فتح M4A/M4B/MP4 (AAC داخل حاوية MP4) — يرمي برسالة صادقة إن لم يوجد مسار AAC. */
    fun openMp4(file: File): AudioInputStream {
        val demux = Mp4AacDemux.parse(file)
            ?: throw IllegalArgumentException(
                "تعذّر قراءة مسار AAC في «${file.name}» — الملف تالف أو ليس M4A صوتيًا. $SUPPORTED_FORMATS_HINT"
            )
        try {
            val decoder = Decoder(demux.config)
            val sb = SampleBuffer()
            val firstFrame = demux.nextFrame()
                ?: throw IllegalArgumentException("M4A بلا عينات صوتية: ${file.name}")
            decoder.decodeFrame(firstFrame, sb)
            if (sb.getData().isEmpty()) throw IllegalArgumentException("فك أول عينة AAC أعطى صمتًا: ${file.name}")
            val fmt = AudioFormat(sb.getSampleRate().toFloat(), 16, sb.getChannels(), true, false)
            val source = FrameSource(
                fmt,
                sb.getData(),
                { demux.nextFrame() },
                decoder,
                { demux.close() }
            )
            return AudioInputStream(source, fmt, AudioSystem.NOT_SPECIFIED.toLong())
        } catch (t: Throwable) {
            demux.close()
            if (t is IllegalArgumentException) throw t
            throw IllegalArgumentException(
                "تعذّر فك «${file.name}» كـM4A/AAC — ترميز غير مدعوم أو ملف محميًا. $SUPPORTED_FORMATS_HINT", t
            )
        }
    }

    /** فتح AAC خام ADTS (.aac) — بثّ البث الإذاعي الرقمي الخام. */
    fun openAdts(file: File): AudioInputStream {
        val stream = BufferedInputStream(FileInputStream(file), 1 shl 16)
        try {
            val demux = ADTSDemultiplexer(stream)
            val decoder = Decoder(demux.getDecoderSpecificInfo())
            val sb = SampleBuffer()
            val firstFrame = demux.readNextFrame()
                ?: throw IllegalArgumentException("ADTS بلا إطارات: ${file.name}")
            decoder.decodeFrame(firstFrame, sb)
            if (sb.getData().isEmpty()) throw IllegalArgumentException("فك أول إطار AAC أعطى صمتًا: ${file.name}")
            val fmt = AudioFormat(sb.getSampleRate().toFloat(), 16, sb.getChannels(), true, false)
            val source = FrameSource(
                fmt,
                sb.getData(),
                {
                    try { demux.readNextFrame() } catch (_: java.io.IOException) { null }
                },
                decoder,
                { try { stream.close() } catch (_: Exception) { } }
            )
            return AudioInputStream(source, fmt, AudioSystem.NOT_SPECIFIED.toLong())
        } catch (t: Throwable) {
            try { stream.close() } catch (_: Exception) { }
            if (t is IllegalArgumentException) throw t
            throw IllegalArgumentException(
                "تعذّر فتح «${file.name}» كـAAC/ADTS — الملف تالف أو ليست صيغته AAC. $SUPPORTED_FORMATS_HINT", t
            )
        }
    }

    /** مدة M4A: عدد العينات × 1024 عينة/إطار ÷ مقياس المسار (معدل الصوت) — -1 عند التعذر. */
    fun mp4DurationMs(file: File): Long = try {
        val d = Mp4AacDemux.parse(file) ?: return -1L
        d.close()
        if (d.mediaTimescale > 0 && d.sampleCount > 0)
            d.sampleCount * 1024L * 1000L / d.mediaTimescale else -1L
    } catch (_: Throwable) {
        -1L
    }

    /**
     * مدة ADTS بمسح ترويسات الإطارات الخالص (دون فك): كل إطار 1024 عينة،
     * ترويسة 7 بايتات (وأقيدها 2 عند وجود CRC) وحجمها من الحقول المعبأة.
     */
    fun adtsDurationMs(file: File): Long = try {
        BufferedInputStream(FileInputStream(file), 1 shl 16).use { s ->
            var frames = 0L
            var rate = 0
            val header = ByteArray(7)
            while (true) {
                var read = 0
                while (read < 7) {
                    val r = s.read(header, read, 7 - read)
                    if (r < 0) break
                    read += r
                }
                if (read < 7) break
                val b1 = header[1].toInt() and 0xFF
                if (header[0].toInt() != 0xFF || (b1 and 0xF6) != 0xF0) {
                    // مزامنة مفقودة: انزلاق بايت واحد فتكتمل الترويسة من التالي
                    val next = s.read()
                    if (next < 0) break
                    System.arraycopy(header, 1, header, 0, 6)
                    header[6] = next.toByte()
                    continue
                }
                val sampleRateIndex = (header[2].toInt() shr 2) and 0x0F
                val sr = ADTS_RATES.getOrElse(sampleRateIndex) { 44100 }
                if (rate == 0) rate = sr
                val protectionAbsent = header[1].toInt() and 0x01
                val frameSize = ((header[3].toInt() and 0x03) shl 11) or
                        ((header[4].toInt() and 0xFF) shl 3) or
                        ((header[5].toInt() and 0xE0) shr 5)
                val total = frameSize + (if (protectionAbsent == 0) 2 else 0)
                if (total < 7) break
                var toSkip = (total - 7).toLong()
                while (toSkip > 0) {
                    val r = s.skip(toSkip)
                    if (r <= 0) {
                        if (s.read() < 0) break
                        toSkip -= 1
                    } else toSkip -= r
                }
                frames++
            }
            if (frames > 0 && rate > 0) frames * 1024 * 1000L / rate else -1L
        }
    } catch (_: Throwable) {
        -1L
    }

    private val ADTS_RATES = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000,
        22050, 16000, 12000, 11025, 8000, 7350
    )

    /**
     * مجرى PCM مولّد بفك إطارًا بإطار — يقرأ الخيط العامل منه بكتل كبيرة،
     * فكل read تملأ من العينة المفكوكة وتسحب إطارًا جديدًا عند الفراغ.
     * الإطار الأول مفكوك سلفًا (مصدر الصيغة الحقيقية) ويُسلَّم عيناته جاهزًا.
     */
    private class FrameSource(
        @Suppress("unused") private val format: AudioFormat,
        initialPcm: ByteArray,
        private val nextFrame: () -> ByteArray?,
        private val decoder: Decoder,
        private val onClose: () -> Unit
    ) : InputStream() {

        private var pcm: ByteArray = initialPcm
        private var pcmPos = 0
        private val sb = SampleBuffer()
        private var closed = false

        private fun pullNext(): Boolean {
            val frame = nextFrame() ?: return false
            try {
                decoder.decodeFrame(frame, sb)
                pcm = sb.getData()
            } catch (_: Exception) {
                pcm = ByteArray(0) // إطار تالف: تجاوزه واصل
            }
            pcmPos = 0
            return pcm.isNotEmpty()
        }

        override fun read(): Int {
            while (pcmPos >= pcm.size) {
                if (!pullNext()) return -1
            }
            val b = pcm[pcmPos].toInt() and 0xFF
            pcmPos++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (closed) throw java.io.IOException("مجرى مغلق")
            var filled = 0
            while (filled < len) {
                if (pcmPos >= pcm.size) {
                    if (!pullNext()) break
                }
                val n = minOf(len - filled, pcm.size - pcmPos)
                System.arraycopy(pcm, pcmPos, b, off + filled, n)
                pcmPos += n
                filled += n
            }
            return if (filled == 0) -1 else filled
        }

        override fun available(): Int = (pcm.size - pcmPos).coerceAtLeast(0)

        override fun close() {
            closed = true
            onClose()
        }
    }
}
