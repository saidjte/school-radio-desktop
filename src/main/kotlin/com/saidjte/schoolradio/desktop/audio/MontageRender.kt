package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageProject
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.min

/**
 * مُصيِّر المونتاج (v0.33.0 «أضف صفحة المونتاج»): تصيير دون اتصال خالص —
 * يفكّ كل مقطع من ملفه (MP3 عبر JLayer، والباقي عبر Java Sound)، يوحّده
 * ستيريو 44.1k، يقصّ نافذته المطلوبة ويضرب مستواها والتلاشين، يفصل المقاطع
 * بصمت الفاصل، ويكتب WAV واحدًا متدفقًا بلا أي جهاز صوت ولا شبكة — فالمونتاج
 * يُبنى حتى على حاسوب بلا بطاقة صوت، والنتيجة ملف حقيقي يظهر في التسجيلات.
 */
object MontageRender {
    /** الصيغة القانونية للناتج — نفس صيغة تسجيل مزيج الحصة (v0.31.0). */
    const val RATE = 44100
    const val CHANNELS = 2

    /** نتيجة التصيير: نجاح وزمن الملف الناتج ورسالة عربية عند الفشل. */
    data class Result(val ok: Boolean, val durationMs: Long, val message: String? = null)

    /** إلغاء داخل تصيير مقطع — يُلتقط في الحلقة الكبرى كي يُغلق الملف بنظافة. */
    private class ClipCancelled : Exception()

    /**
     * تصيير المشروع كاملًا في ملف واحد — يستدعى من خيط عمل، والتقدم 0..1
     * ينبض عبر onProgress، و cancelled يُفحص بين الكتل فالإلغاء سريع ونظيف.
     */
    fun render(
        project: MontageProject,
        out: File,
        onProgress: ((Float) -> Unit)? = null,
        cancelled: () -> Boolean = { false }
    ): Result {
        val clips = MontageLogic.sanitize(project.clips)
        if (clips.isEmpty()) return Result(false, 0L, "لا توجد مقاطع صالحة في المشروع")
        out.parentFile?.mkdirs()
        val writer = try {
            WavFileWriter(out, RATE, CHANNELS)
        } catch (t: Throwable) {
            return Result(false, 0L, "تعذّر إنشاء ملف التصدير: ${t.message ?: ""}")
        }
        try {
            // خطة التصيير: إطارات الوجهة لكل مقطع + صمت الفاصل بين كل مقطعين
            val planFrames = clips.map { framesFor(MontageLogic.clipDurationMs(it)) }
            val gapFrames = framesFor(project.gapMs)
            val totalPlanned = (planFrames.sum() + gapFrames * (clips.size - 1).coerceAtLeast(0)).coerceAtLeast(1L)
            var done = 0L
            for ((i, clip) in clips.withIndex()) {
                done += renderClip(clip, planFrames[i], writer, cancelled) { written ->
                    onProgress?.invoke(((done + written).toFloat()) / totalPlanned)
                }
                if (i < clips.size - 1) {
                    writeSilence(writer, gapFrames)
                    done += gapFrames
                }
                onProgress?.invoke(done.toFloat() / totalPlanned)
            }
        } catch (c: ClipCancelled) {
            return Result(false, writer.durationMs, "أُلغي التصيير")
        } catch (t: Throwable) {
            return Result(false, 0L, "فشل التصيير: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            try { writer.close() } catch (_: Exception) { }
        }
        return Result(true, writer.durationMs, null)
    }

    /**
     * تصيير مقطع واحد: قفز لبداية النافذة → فك وستيريو وإعادة تشكيل إلى
     * 44.1k → مستوى وتلاشٍ لكل إطار مع حبس صريح بلا لفّ أعداد صامتة →
     * كتابة متدفقة. يعيد عدد الإطارات المكتوبة فعليًا.
     */
    private fun renderClip(
        clip: MontageClip,
        targetFrames: Long,
        writer: WavFileWriter,
        cancelled: () -> Boolean,
        onChunk: (Long) -> Unit
    ): Long {
        if (targetFrames <= 0) return 0L
        MontageClipSource.open(File(clip.path)).use { src ->
            src.skipTo(clip.startMs)
            val gain = clip.volumePercent / 100f
            val visibleMs = targetFrames * 1000L / RATE
            val native = ShortArray(64 * 1024)
            var written = 0L
            while (written < targetFrames) {
                if (cancelled()) throw ClipCancelled()
                val n = src.read(native)
                if (n <= 0) break // نهاية الملف قبل نهاية النافذة (بيانات وصفية أخطأت) — نكمل بما وُجد
                val frames = n / src.channels.coerceAtLeast(1)
                val stereo = foldStereo(native, n, src.channels)
                val resampled = if (src.rate == RATE) stereo
                else PcmMixer.resampleStereo(stereo, frames, src.rate, RATE)
                val outFrames = resampled.size / 2
                if (outFrames <= 0) continue
                val take = min(outFrames.toLong(), targetFrames - written).toInt()
                for (f in 0 until take) {
                    val g = gain * MontageLogic.fadeGainAt(
                        (written + f) * 1000L / RATE, visibleMs, clip.fadeInMs, clip.fadeOutMs
                    )
                    val l = (resampled[2 * f] * g).toInt().coerceIn(-32768, 32767)
                    val r = (resampled[2 * f + 1] * g).toInt().coerceIn(-32768, 32767)
                    resampled[2 * f] = l.toShort()
                    resampled[2 * f + 1] = r.toShort()
                }
                val bytes = ByteArray(take * 4)
                var bi = 0
                for (f in 0 until take * 2) {
                    val v = resampled[f].toInt()
                    bytes[bi++] = (v and 0xFF).toByte()
                    bytes[bi++] = ((v shr 8) and 0xFF).toByte()
                }
                writer.write(bytes)
                written += take
                onChunk(written)
            }
            return written
        }
    }

    /**
     * طيّ أي عدد قنوات إلى ستيريو: أحادي يُكرَّر في القناتين، ثنائي كما هو،
     * وأكثر من قناتين تأخذ القناتين الأوليين — عقد موحّد قبل إعادة التشكيل.
     */
    private fun foldStereo(src: ShortArray, samples: Int, channels: Int): ShortArray {
        if (samples <= 0) return ShortArray(0)
        return when {
            channels == 2 -> src.copyOf(samples)
            channels == 1 -> {
                val out = ShortArray(samples * 2)
                for (f in 0 until samples) {
                    val v = src[f]
                    out[2 * f] = v
                    out[2 * f + 1] = v
                }
                out
            }
            else -> {
                val frames = samples / channels
                val out = ShortArray(frames * 2)
                for (f in 0 until frames) {
                    out[2 * f] = src[f * channels]
                    out[2 * f + 1] = src[f * channels + 1]
                }
                out
            }
        }
    }

    /** صمت الفاصل: أصفار قانونية تُكتب متدفقة. */
    private fun writeSilence(writer: WavFileWriter, frames: Long) {
        var remaining = frames
        val chunk = ByteArray(64 * 1024)
        while (remaining > 0) {
            val bytes = (min(remaining, chunk.size / 4L).toInt() * 4)
            writer.write(chunk, 0, bytes)
            remaining -= bytes / 4L
        }
    }

    private fun framesFor(ms: Long): Long = ms * RATE / 1000L
}

/**
 * مصدر فك موحّد لمقطع المونتاج: MP3 عبر JLayer إطارًا بإطار (بنفس آلية
 * المحرك)، والباقي عبر Java Sound محوّلًا إلى PCM موقّع 16-bit بمعدل الملف
 * وقنواته الأصلية — يقرأ عينات متشابكة ويقفز إلى بداية النافذة بلا فك زائد.
 */
internal class MontageClipSource private constructor(
    val rate: Int,
    val channels: Int,
    private val mp3: Mp3Pipeline?,
    private val pcm: AudioInputStream?
) : Closeable {

    private class Mp3Pipeline(val stream: BufferedInputStream, val fh: Mp3Info.FrameHeader) {
        val bitstream = Bitstream(stream)
        val decoder = Decoder()
    }

    companion object {
        fun open(file: File): MontageClipSource {
            if (file.extension.lowercase() == "mp3") {
                val info = Mp3Info.analyze(file)
                    ?: throw IllegalArgumentException("تعذّر تحليل ملف MP3: ${file.name}")
                val fh = info.first
                val stream = BufferedInputStream(FileInputStream(file), 1 shl 16)
                Mp3Info.skipId3v2(stream)
                return MontageClipSource(fh.sampleRate, fh.channels, Mp3Pipeline(stream, fh), null)
            }
            val raw = openAudioInputStream(file)
            val rf = raw.format
            val ais = if (rf.encoding == AudioFormat.Encoding.PCM_SIGNED &&
                !rf.isBigEndian && rf.sampleSizeInBits == 16
            ) raw
            else AudioSystem.getAudioInputStream(
                AudioFormat(rf.sampleRate, 16, rf.channels, true, false), raw
            )
            val f = ais.format
            return MontageClipSource(f.sampleRate.toInt(), f.channels, null, ais)
        }
    }

    /** يقرأ العينات المتشابكة التالية في المصفوفة — يعيد عدد العينات أو -1 عند النهاية. */
    fun read(dst: ShortArray): Int {
        val mp = mp3
        if (mp != null) {
            val header = try { mp.bitstream.readFrame() } catch (_: Exception) { null } ?: return -1
            return try {
                val decoded = mp.decoder.decodeFrame(header, mp.bitstream)
                if (decoded is SampleBuffer) {
                    val buf = decoded.buffer
                    val n = buf.size.coerceAtMost(dst.size)
                    System.arraycopy(buf, 0, dst, 0, n)
                    n
                } else -1
            } catch (_: Exception) {
                0 // إطار تالف: كتلة فارغة والتصيير يكمل
            } finally {
                try { mp.bitstream.closeFrame() } catch (_: Exception) { }
            }
        }
        val ais = pcm ?: return -1
        val bytes = ByteArray(dst.size * 2)
        val n = try { ais.read(bytes) } catch (_: Exception) { -1 }
        if (n < 0) return -1
        val usable = n - (n % 2)
        for (i in 0 until usable / 2) {
            dst[i] = ((bytes[2 * i].toInt() and 0xFF) or (bytes[2 * i + 1].toInt() shl 8)).toShort()
        }
        return usable / 2
    }

    /**
     * القفز إلى موضع زمني: MP3 بعدّ إطارات من دون فك (الإطار الذي يبدأ داخل
     * النافذة يُعاد للبِتستريم كي لا يُفقد)، والباقي بتجاوز بايتات مع احتياط
     * قراءة إذا لم يقدّ skip.
     */
    fun skipTo(ms: Long) {
        val mp = mp3
        if (mp != null) {
            val fh = mp.fh
            var count = 0L
            while (true) {
                val h = try { mp.bitstream.readFrame() } catch (_: Exception) { null } ?: break
                val t = count * fh.samplesPerFrame * 1000L / fh.sampleRate
                if (t >= ms) {
                    try { mp.bitstream.unreadFrame() } catch (_: Exception) {
                        try { mp.bitstream.closeFrame() } catch (_: Exception) { }
                    }
                    break
                }
                count++
                try { mp.bitstream.closeFrame() } catch (_: Exception) { }
            }
            return
        }
        val ais = pcm ?: return
        val frameSize = ais.format.frameSize.coerceAtLeast(1)
        val targetBytes = ms * ais.format.frameRate.toLong() / 1000L * frameSize
        var toSkip = targetBytes
        var failed = false
        try {
            while (toSkip > 0) {
                val k = ais.skip(toSkip)
                if (k <= 0) {
                    if (ais.read() < 0) break
                    toSkip -= 1
                } else toSkip -= k
            }
        } catch (_: Exception) {
            failed = true
        }
        if (failed) {
            var remaining = targetBytes
            val discard = ByteArray(64 * 1024)
            try {
                while (remaining > 0) {
                    val r = ais.read(discard, 0, min(remaining, discard.size.toLong()).toInt())
                    if (r < 0) break
                    remaining -= r
                }
            } catch (_: Exception) { }
        }
    }

    override fun close() {
        try { mp3?.bitstream?.close() } catch (_: Exception) { }
        try { mp3?.stream?.close() } catch (_: Exception) { }
        try { pcm?.close() } catch (_: Exception) { }
    }
}

/**
 * ملفات عرض تجريبية للفحص الآلي والبصري (v0.33.0): أصوات جيبية تُولَّد محليًا
 * في مجلد مؤقت — بلا عتاد صوت ولا شبكة، وبمعدلات وقنوات مختلفة كي يختبر
 * توحيد الصيغة في المُصيِّر فعليًا.
 */
object MontageDemo {
    fun make(dir: File): List<File> {
        dir.mkdirs()
        return listOf(
            tone(File(dir, "افتتاحية.wav"), 44100, 1, 4000, 440.0),
            tone(File(dir, "نشرة-أخبار.wav"), 44100, 2, 6000, 330.0),
            tone(File(dir, "ختام.wav"), 22050, 1, 3000, 550.0)
        )
    }

    /** موجة جيبية ثابتة السعة تُكتب متدفقة — للفتحات والفواصل التجريبية. */
    fun tone(file: File, rate: Int, channels: Int, ms: Long, hz: Double): File {
        file.parentFile?.mkdirs()
        val frames = (ms * rate / 1000L).toInt()
        val w = WavFileWriter(file, rate, channels)
        val buf = ByteArray(8192 * 2 * 2)
        var frame = 0
        while (frame < frames) {
            val n = minOf(8192, frames - frame)
            var bi = 0
            for (i in 0 until n) {
                val v = (Math.sin(2.0 * Math.PI * hz * (frame + i) / rate) * 12000.0).toInt()
                val samples = if (channels == 2) intArrayOf(v, v * 3 / 4) else intArrayOf(v)
                for (s in samples) {
                    buf[bi++] = (s and 0xFF).toByte()
                    buf[bi++] = ((s shr 8) and 0xFF).toByte()
                }
            }
            w.write(buf, 0, bi)
            frame += n
        }
        w.close()
        return file
    }
}
