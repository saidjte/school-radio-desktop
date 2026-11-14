package com.saidjte.schoolradio.desktop.youtube

import de.sciss.jump3r.lowlevel.LameEncoder
import net.sourceforge.jaad.aac.Decoder
import net.sourceforge.jaad.aac.SampleBuffer
import net.sourceforge.jaad.mp4.MP4Container
import net.sourceforge.jaad.mp4.api.AudioTrack
import net.sourceforge.jaad.mp4.api.Movie
import net.sourceforge.jaad.mp4.api.Track
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import javax.sound.sampled.AudioFormat

/**
 * محوّل صوتي نقي على JVM (بلا أدوات خارجية): M4A/MP4 بصوت AAC-LC (صيغة يوتيوب القياسية)
 * ← PCM ← MP3 192k عبر LAME (jump3r). الناتج MP3 يتشغّل في المحرك بأدق مساراته
 * (JLayer + بحث زمني Xing/متوسط معدل البت) دون أي تعديل في مسار البث الحي.
 *
 * التقدم: تقدير من نسبة البايتات المفكوكة مقابل المدة المتوقعة (إن ذُكرت).
 * سرعة تقريبية 6–10× الزمن الحقيقي على حواسيب مدارس عادية.
 */
object AudioTranscoder {

    /** امتدادات تحتاج تحويلًا إلى MP3 (كبسولة MP4/AAC). */
    val TRANSCODE_EXTENSIONS = setOf("m4a", "mp4", "aac", "mp4a", "m4b")

    /** هل الامتداد يحتاج تحويلًا قبل دخول المكتبة؟ */
    fun needsTranscode(ext: String): Boolean = ext.lowercase() in TRANSCODE_EXTENSIONS

    /**
     * تحويل M4A/MP4 (AAC) إلى MP3. يرمي IllegalStateException برسالة عربية
     * إن لم يكن الملف مقروءًا أو بلا مسار صوتي AAC.
     * @param durationSec المدة المتوقعة بالثواني (من بيانات اليوتيوب) لشريط التقدم — اختيارية.
     * @param onProgress تقدم 0..1 — اختياري.
     * @return حجم الملف الناتج بالبايتات.
     *
     * مساران للعينات (v0.50.0): أولاً جداول العينات الكلاسيكية عبر JAAD (سلوك
     * v0.25 المحفوظ)، وإن رأت الحاوية مسارًا بلا إطارات جرّب ديمكس الأجزاء
     * [FragmentedMp4Aac] — ملفات MP4 المجزّأة التي يسلّمها يوتيوب عبر SABR/HLS
     * (moov بجداول فارغة وعينات في moof+mdat) كانت تفشل هنا بـ«لم يُفكّ أي إطار».
     * الرسالة النهائية عند الصفر تسمّي ما وُجد فعلاً بدل نص واحد غامض.
     */
    fun m4aToMp3(
        input: File,
        output: File,
        bitrateKbps: Int = 192,
        durationSec: Long = 0L,
        onProgress: ((Float) -> Unit)? = null
    ): Long {
        val fis = FileInputStream(input)
        val container = try {
            MP4Container(fis)
        } catch (e: Exception) {
            try { fis.close() } catch (_: Exception) { }
            throw IllegalStateException("تعذّر قراءة الملف المُحمَّل كصوت MP4/AAC: ${e.message ?: e.javaClass.simpleName}")
        }

        val movie: Movie = try {
            container.movie
        } catch (e: Exception) {
            try { fis.close() } catch (_: Exception) { }
            throw IllegalStateException("تعذّر تحليل بنية الملف المُحمَّل (ليس MP4 صوتيًا؟)")
        }

        val audio: AudioTrack = movie.tracks.filterIsInstance<AudioTrack>().firstOrNull()
            ?: run {
                try { fis.close() } catch (_: Exception) { }
                throw IllegalStateException("الملف المُحمَّل لا يحتوي مسارًا صوتيًا مدعومًا (المطلوب AAC/M4A).")
            }

        val sampleRate = audio.sampleRate.coerceAtLeast(8000)
        val channels = audio.channelCount.coerceIn(1, 2)
        val asc = audio.decoderSpecificInfo
        val decoder = try {
            Decoder(asc)
        } catch (e: Exception) {
            try { fis.close() } catch (_: Exception) { }
            throw IllegalStateException("تعذّر تهيئة فاكّ AAC لهذا المقطع: ${e.message ?: ""}")
        }

        val buf = SampleBuffer()
        buf.setBigEndian(false)

        val srcFmt = AudioFormat(sampleRate.toFloat(), 16, channels, true, false)
        val version = when {
            sampleRate >= 32000 -> LameEncoder.MPEG_VERSION_1
            sampleRate >= 16000 -> LameEncoder.MPEG_VERSION_2
            else -> LameEncoder.MPEG_VERSION_2DOT5
        }
        val chMode = if (channels == 1) LameEncoder.CHANNEL_MODE_MONO else LameEncoder.CHANNEL_MODE_JOINT_STEREO
        val encoder = LameEncoder(srcFmt, bitrateKbps, version, chMode, false)
        val pcmBufSize = encoder.pcmBufferSize.coerceAtLeast(4096)
        val encBuf = ByteArray(pcmBufSize + 4096)

        // تقدير البايتات المفكوكة الكلية للتقدم (PCM 16bit)
        val totalPcmBytes = if (durationSec > 0) durationSec * sampleRate * channels * 2L else 0L
        val progress = ProgressTracker(totalPcmBytes, onProgress)

        // مسار العيّنات الموحّد: مصدر يعيد عينات AAC تلو الأخرى (null = نهاية)
        val classicProvider = SampleProvider {
            while (audio.hasMoreFrames()) {
                val frame = try {
                    audio.readNextFrame()
                } catch (_: Exception) {
                    return@SampleProvider null // نهاية البيانات أو إطار ختامي — كسلوك v0.25
                }
                val data = frame.data
                if (data != null) return@SampleProvider data // إطار بلا بيانات يُتخطى وتُكمل القراءة
            }
            null
        }

        var decoded = 0L
        var fragScan: FragmentedMp4Aac.Scan? = null
        try {
            BufferedOutputStream(FileOutputStream(output), 1 shl 16).use { bos ->
                decoded = encodeLoop(bos, classicProvider, decoder, buf, encoder, pcmBufSize, encBuf, progress)

                // مسار MP4 المجزّأ: الجداول الكلاسيكية رأت صفرًا — جرّب أجزاء moof/mdat
                if (decoded == 0L) {
                    fragScan = try {
                        FragmentedMp4Aac.scan(input)
                    } catch (_: IllegalArgumentException) {
                        null // بنية غير مدعومة: تبقى رسالة الصفر الصادقة تحتكم
                    }
                    val frag = fragScan
                    if (frag != null && frag.samples.isNotEmpty()) {
                        RandomAccessFile(input, "r").use { raf ->
                            val iter = frag.samples.iterator()
                            val fragProvider = SampleProvider {
                                if (!iter.hasNext()) return@SampleProvider null
                                val s = iter.next()
                                try {
                                    val a = ByteArray(s.size)
                                    raf.seek(s.offset)
                                    raf.readFully(a)
                                    a
                                } catch (_: Exception) {
                                    null // عينة قُطعت: كسلوك نهاية البيانات
                                }
                            }
                            decoded = encodeLoop(bos, fragProvider, decoder, buf, encoder, pcmBufSize, encBuf, progress)
                        }
                    }
                }
                encoder.close()
            }
        } finally {
            try { fis.close() } catch (_: Exception) { }
        }

        if (decoded == 0L || output.length() < 1_000L) {
            output.delete()
            throw IllegalStateException(zeroFramesMessage(fragScan, decoded, output.length(), asc))
        }
        return output.length()
    }

    /** واجهة مصدر العينات: يعيد عينة AAC أو null عند النهاية. */
    private fun interface SampleProvider {
        fun next(): ByteArray?
    }

    /** حالة تقدم مشتركة بين مساري العينات — تقدير من PCM المفكوك مقابل المتوقع. */
    private class ProgressTracker(
        private val totalPcmBytes: Long,
        private val onProgress: ((Float) -> Unit)?
    ) {
        var decodedPcmBytes = 0L
        private var lastReport = -1f

        fun add(pcmLen: Int) {
            decodedPcmBytes += pcmLen
            if (onProgress != null && totalPcmBytes > 0) {
                val p = (decodedPcmBytes.toFloat() / totalPcmBytes).coerceIn(0f, 1f)
                if (p - lastReport >= 0.02f || p >= 1f) {
                    lastReport = p
                    onProgress(p)
                }
            }
        }
    }

    /**
     * حلقة الفك والترميز — جسم v0.25 الأصلي كما هو مع مصدر عينات مُجرَّد.
     * تعيد عدد الإطارات التي فُكّت فعلاً (فك ناجح حتى وإن كان PCM فارغًا).
     */
    private fun encodeLoop(
        bos: BufferedOutputStream,
        provider: SampleProvider,
        decoder: Decoder,
        buf: SampleBuffer,
        encoder: LameEncoder,
        pcmBufSize: Int,
        encBuf: ByteArray,
        progress: ProgressTracker
    ): Long {
        var decodedFrames = 0L
        while (true) {
            val data = provider.next() ?: break
            try {
                decoder.decodeFrame(data, buf)
            } catch (_: Exception) {
                continue // إطار تالف: نتجاوزه
            }
            val pcm = buf.data
            if (pcm.isNotEmpty()) {
                var off = 0
                while (off < pcm.size) {
                    val n = minOf(pcmBufSize, pcm.size - off)
                    val written = encoder.encodeBuffer(pcm, off, n, encBuf)
                    if (written > 0) bos.write(encBuf, 0, written)
                    off += n
                }
                progress.add(pcm.size)
            }
            decodedFrames++
        }
        return decodedFrames
    }

    /**
     * رسالة الصفر الصادقة — تسمّي ما وُجد في الملف فعلاً بدل نص غامض واحد:
     * MP4 كلاسيكي بلا إطارات، أو مجزّأ بلا عينات مقروءة، أو عينات رفضها الفاكّ
     * (تُسمّى صيغة AAC من AudioSpecificConfig مثل HE-AAC).
     */
    private fun zeroFramesMessage(
        fragScan: FragmentedMp4Aac.Scan?,
        decoded: Long,
        outputLen: Long,
        asc: ByteArray
    ): String {
        val frag = fragScan
        return when {
            frag != null && frag.samples.isEmpty() ->
                "الملف المُحمَّل MP4 مجزّأ (${frag.fragmentCount} جزءًا) لكنه بلا عينات صوتية مقروءة — التنزيل تالف غالبًا؛ أعد المحاولة أو أعد الاستيراد."
            frag != null && decoded == 0L -> {
                val aot = FragmentedMp4Aac.audioObjectType(asc)
                if (aot == 5 || aot == 29)
                    "وُجدت ${frag.samples.size} عينة بصيغة ${FragmentedMp4Aac.audioProfileName(aot)} لكن الفاكّ المحلي لا يدعمها — جرّب مقطعًا آخر أو أعد الاستيراد."
                else
                    "وُجدت ${frag.samples.size} عينة AAC لكن الفاكّ رفض فكّها كلها — الصيغة (${FragmentedMp4Aac.audioProfileName(aot)}) غير مدعومة محليًا؛ أعد الاستيراد أو جرّب مقطعًا آخر."
            }
            frag == null && decoded == 0L ->
                "الملف المُحمَّل بنيته MP4 سليمة لكنه بلا إطارات صوتية مقروءة — التنزيل تالف غالبًا؛ أعد المحاولة أو أعد الاستيراد."
            decoded > 0L && outputLen < 1_000L ->
                "فُكّت $decoded إطارًا لكن الترميز لم ينتج ملف MP3 صالحًا — أعد المحاولة."
            else ->
                "لم يُفكّ أي إطار صوتي من الملف المُحمَّل — الصيغة غير مدعومة."
        }
    }
}
