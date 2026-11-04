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
        var decodedPcmBytes = 0L
        var frames = 0L
        var lastReport = -1f

        try {
            BufferedOutputStream(FileOutputStream(output), 1 shl 16).use { bos ->
                while (audio.hasMoreFrames()) {
                    val frame = try {
                        audio.readNextFrame()
                    } catch (e: Exception) {
                        break // نهاية البيانات أو إطار ختامي — لا نُفشل التحويل كله
                    }
                    val data = frame.data ?: continue
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
                        decodedPcmBytes += pcm.size
                    }
                    frames++
                    if (onProgress != null && totalPcmBytes > 0) {
                        val p = (decodedPcmBytes.toFloat() / totalPcmBytes).coerceIn(0f, 1f)
                        if (p - lastReport >= 0.02f || p >= 1f) {
                            lastReport = p
                            onProgress(p)
                        }
                    }
                }
                encoder.close()
            }
        } finally {
            try { fis.close() } catch (_: Exception) { }
        }

        if (frames == 0L || output.length() < 1_000L) {
            output.delete()
            throw IllegalStateException("لم يُفكّ أي إطار صوتي من الملف المُحمَّل — الصيغة غير مدعومة.")
        }
        return output.length()
    }
}
