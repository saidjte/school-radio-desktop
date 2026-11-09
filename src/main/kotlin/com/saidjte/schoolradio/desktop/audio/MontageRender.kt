package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageEnvelope
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import de.sciss.jump3r.lowlevel.LameEncoder
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.min

/** صيغة تصدير المونتاج (v0.37.0): WAV بلا فقد للبث، أو MP3 مضغوط للمشاركة. */
enum class MontageExportFormat(val labelAr: String, val extension: String) {
    WAV("WAV — بلا فقد، جاهز للبث", "wav"),
    MP3("MP3 — مضغوط للمشاركة والهاتف", "mp3")
}

/**
 * مَصِبّ PCM موحّد للمُصيّر (v0.37.0): نفس عقد WavFileWriter (كتابة بايتات
 * ستيريو 16-bit little-endian ومدة المكتبوكة) — فالمزج الكتلي لا يعرف
 * هل يكتب WAV أم يُرمّز MP3، والتبديل نقطة واحدة عند الفتح.
 */
internal interface PcmSink : Closeable {
    fun write(b: ByteArray, offset: Int, length: Int)
    val durationMs: Long
}

private class WavPcmSink(file: File) : PcmSink {
    private val w = WavFileWriter(file, MontageRender.RATE, MontageRender.CHANNELS)
    override val durationMs: Long get() = w.durationMs
    override fun write(b: ByteArray, offset: Int, length: Int) = w.write(b, offset, length)
    override fun close() = w.close()
}

/**
 * مصبّ MP3 متدفق (v0.37.0): نفس كتل PCM ستيريو 44.1k تُرمَّز عبر LAME
 * (jump3r — بورت LAME الخالص على JVM، نفس محرك استيراد اليوتيوب) بجودة
 * CBR قابلة للاختيار، بلا أي أداة خارجية ولا شبكة. الكتل تُجمَع إلى حجم
 * تغذية LAME المفضّل وتُدفَع كما هي، ونهاية البِتستريم تُفرَغ بـ encodeFinish
 * فلا يُقطع ذيل الملف.
 */
private class Mp3PcmSink(file: File, bitrateKbps: Int) : PcmSink {
    private val fos = BufferedOutputStream(FileOutputStream(file), 1 shl 16)
    private val encoder = LameEncoder(
        AudioFormat(MontageRender.RATE.toFloat(), 16, MontageRender.CHANNELS, true, false),
        bitrateKbps.coerceIn(64, 320),
        LameEncoder.MPEG_VERSION_1,
        LameEncoder.CHANNEL_MODE_JOINT_STEREO,
        false
    )
    private val chunkSize = encoder.pcmBufferSize.coerceAtLeast(4096)
    private val inBuf = ByteArray(chunkSize)
    private var inLen = 0
    private val outBuf = ByteArray(chunkSize / 2 + 8192)
    private var pcmBytes = 0L

    override val durationMs: Long
        get() = WavFileWriter.durationMsOf(pcmBytes, MontageRender.RATE, MontageRender.CHANNELS)

    override fun write(b: ByteArray, offset: Int, length: Int) {
        var p = offset
        var rem = length
        while (rem > 0) {
            val n = minOf(rem, chunkSize - inLen)
            System.arraycopy(b, p, inBuf, inLen, n)
            inLen += n
            p += n
            rem -= n
            if (inLen == chunkSize) flushChunk()
        }
    }

    private fun flushChunk() {
        if (inLen == 0) return
        val n = encoder.encodeBuffer(inBuf, 0, inLen, outBuf)
        if (n > 0) fos.write(outBuf, 0, n)
        pcmBytes += inLen
        inLen = 0
    }

    override fun close() {
        try {
            flushChunk()
            val fin = encoder.encodeFinish(outBuf)
            if (fin > 0) fos.write(outBuf, 0, fin)
        } catch (_: Throwable) {
        } finally {
            try { fos.close() } catch (_: Exception) { }
            try { encoder.close() } catch (_: Throwable) { }
        }
    }
}

/**
 * مُصيِّر المونتاج (v0.33.0 «أضف صفحة المونتاج»): تصيير دون اتصال خالص —
 * يفكّ كل مقطع من ملفه (MP3 عبر JLayer، والباقي عبر Java Sound)، يوحّده
 * ستيريو 44.1k، يقصّ نافذته المطلوبة ويضرب مستواها والتلاشين، ويكتب WAV
 * واحدًا متدفقًا بلا أي جهاز صوت ولا شبكة — فالمونتاج يُبنى حتى على حاسوب
 * بلا بطاقة صوت، والنتيجة ملف حقيقي يظهر في التسجيلات.
 *
 * v0.34.0 «الترقية الاحترافية»: خلاط كتل زمني واحد — المقاطع تُسحب من مصادرها
 * على مواضعها في الخط الزمني للوجهة، فتتداخل المتجاورة في منطقة الكروس فيد
 * بمزج قوة متساوية، وكل حافة قصّ تمر عبر التلاشي الدقيق المضاد للنقرات،
 * ومعه حساب مغلّفات العرض (ويڤ فورم وVU ومناطق التشبع) والذروة الخام للتطبيع —
 * كل ذلك بنفس التصيير المتدفق الذي لا يحمّل ملفًا كاملًا في الذاكرة.
 *
 * v0.35.0 (البند 16): المسار المزدوج — مقاطع موسيقى مرافقة تُمزج على مواضعها
 * المطلقة فوق التعليق بكسب الخفض التلقائي النقي (عمق/هجوم/إفلات) المستنتج
 * من نطاقات النطق نفسها، والمدة الكلية أبعدها بين المسارين.
 *
 * v0.36.0 «عدة خطوط عمل»: كل مقطع مسار يأخذ كسب مساره — مستواه المئوي ×
 * الكتم/المنفرد (وحدة المزج الإذاعي) — والخفض التلقائي يمسّ كل المسارات
 * الحرة تحت نطق المذيع، فعدة خطوط تُمزج في ملف واحد كما تُسمع في الاستوديو.
 */
object MontageRender {
    /** الصيغة القانونية للناتج — نفس صيغة تسجيل مزيج الحصة (v0.31.0). */
    const val RATE = 44100
    const val CHANNELS = 2

    /** حجم كتلة المزج بإطارات الوجهة (~371 مللي ثانية) — توازن ذاكرة/نداءات. */
    private const val MIX_BLOCK = 16_384

    /** جودات MP3 المسموحة (v0.37.0) والافتراضي — CBR ثابت لسعي دقيق في المحرك. */
    val MP3_BITRATES = listOf(128, 192, 256, 320)
    const val MP3_DEFAULT_KBPS = 192

    /** نتيجة التصيير: نجاح وزمن الملف الناتج ورسالة عربية عند الفشل. */
    data class Result(val ok: Boolean, val durationMs: Long, val message: String? = null)

    /** إلغاء داخل تصيير مقطع — يُلتقط في الحلقة الكبرى كي يُغلق الملف بنظافة. */
    private class ClipCancelled : Exception()

    /**
     * خطة تصيير بإطارات الوجهة: بدايات المقاطع على الخط الزمني وأزمنة الكروس
     * فيد لكل حدّ — تُحسب مرة واحدة فلا انحراف تجميعي في المدد.
     */
    internal class RenderPlan(
        val starts: LongArray,
        val durations: LongArray,
        val totalFrames: Long,
        /** لكل حدّ: بداية منطقة الكروس فيد وعدد إطاراتها (0 = لا كروس فيد). */
        val xfStart: LongArray,
        val xfFrames: LongArray
    )

    internal fun plan(project: MontageProject, clips: List<MontageClip>): RenderPlan {
        val n = clips.size
        val starts = LongArray(n)
        val durations = LongArray(n)
        for (i in 0 until n) durations[i] = framesFor(MontageLogic.clipDurationMs(clips[i]))
        var t = 0L
        for (i in 0 until n) {
            starts[i] = t
            if (i < n - 1) {
                val overlapMs = MontageLogic.effectiveOverlap(
                    MontageLogic.clipDurationMs(clips[i]),
                    MontageLogic.clipDurationMs(clips[i + 1]),
                    project.crossfadeMs
                )
                t = if (overlapMs > 0L) t + durations[i] - framesFor(overlapMs)
                else t + durations[i] + framesFor(project.gapMs.coerceAtLeast(0L))
            }
        }
        val total = (if (n > 0) starts[n - 1] + durations[n - 1] else 0L).coerceAtLeast(1L)
        val xfStart = LongArray((n - 1).coerceAtLeast(0))
        val xfFrames = LongArray((n - 1).coerceAtLeast(0))
        if (project.crossfadeMs > 0L) {
            for (i in 0 until n - 1) {
                val overlapMs = MontageLogic.effectiveOverlap(
                    MontageLogic.clipDurationMs(clips[i]),
                    MontageLogic.clipDurationMs(clips[i + 1]),
                    project.crossfadeMs
                )
                if (overlapMs > 0L) {
                    xfFrames[i] = framesFor(overlapMs)
                    xfStart[i] = starts[i] + durations[i] - xfFrames[i]
                }
            }
        }
        return RenderPlan(starts, durations, total, xfStart, xfFrames)
    }

    /**
     * تصيير المشروع كاملًا في ملف واحد — يستدعى من خيط عمل، والتقدم 0..1
     * ينبض عبر onProgress، و cancelled يُفحص بين الكتل فالإلغاء سريع ونظيف.
     * الخلاط الكتلي: كل كتلة زمنية تُمزج من مصادرها النشطة (اثنان كحد أقصى
     * عند حدود الكروس فيد، وأكثر من مسار الموسيقى) أو تُكتب صمتًا إن كانت في فاصل.
     *
     * v0.37.0: [format] يختار الوجهة — WAV قانوني كما هو، أو MP3 عبر LAME
     * بجودة [bitrateKbps] (CBR) بنفس كتل المزج نفسها فلا مسار مزج ثانٍ.
     */
    fun render(
        project: MontageProject,
        out: File,
        format: MontageExportFormat = MontageExportFormat.WAV,
        bitrateKbps: Int = MP3_DEFAULT_KBPS,
        onProgress: ((Float) -> Unit)? = null,
        cancelled: () -> Boolean = { false }
    ): Result {
        val clips = MontageLogic.sanitize(project.clips)
        val music = MontageLogic.sanitizeMusic(project.musicClips)
        if (clips.isEmpty() && music.isEmpty()) return Result(false, 0L, "لا توجد مقاطع صالحة في المشروع")
        out.parentFile?.mkdirs()
        runCatching { if (out.isFile) out.delete() }
        val sink = try {
            when (format) {
                MontageExportFormat.WAV -> WavPcmSink(out)
                MontageExportFormat.MP3 -> Mp3PcmSink(out, bitrateKbps)
            }
        } catch (t: Throwable) {
            return Result(false, 0L, "تعذّر إنشاء ملف التصدير: ${t.message ?: ""}")
        }
        try {
            val plan = plan(project, clips)
            // المسارات الحرة (v0.36.0): كسب كل مقطع من مستوى مساره —
            // v0.41.0: الكتم/المنفرد حيّان في المسارات الحرة، ومستوى المجموعة
            // يتراكب مع مستوى المسار، والتوازن والأتمتة تُحسب لكل مسار،
            // والخفض يعمّ مسارات الموسيقى الخلفية وحدها (التحكم في تداخل
            // الأصوات بحسب النوع).
            val tracks = MontageLogic.effectiveTracks(project)
            val groups = MontageLogic.sanitizeGroups(project.groups)
            val anyTrackSolo = MontageLogic.anyTrackSolo(tracks)
            val anyGroupSolo = MontageLogic.anyGroupSolo(groups)
            val musicStarts = LongArray(music.size)
            val musicDurFrames = LongArray(music.size)
            val musicTrackAmp = FloatArray(music.size)
            val musicPanL = FloatArray(music.size)
            val musicPanR = FloatArray(music.size)
            val musicDucks = BooleanArray(music.size)
            val musicAuto = arrayOfNulls<List<com.saidjte.schoolradio.desktop.data.MontageAutoPoint>>(music.size)
            val musicSources = arrayOfNulls<MontageClip>(music.size)
            for (mi in music.indices) {
                musicSources[mi] = musicAsSourceClip(music[mi])
                musicStarts[mi] = framesFor(music[mi].startMs.coerceAtLeast(0L))
                musicDurFrames[mi] = framesFor(MontageLogic.musicDurationMs(music[mi]))
                val tr = MontageLogic.trackById(tracks, MontageLogic.effectiveTrackId(music[mi]))
                val gr = MontageLogic.groupById(groups, tr.groupId)
                musicTrackAmp[mi] = MontageLogic.trackAmp(tr, gr, anyTrackSolo, anyGroupSolo)
                musicPanL[mi] = MontageLogic.panGainLeft(tr.pan)
                musicPanR[mi] = MontageLogic.panGainRight(tr.pan)
                musicDucks[mi] = MontageLogic.ducksUnderVoice(tr)
                musicAuto[mi] = MontageLogic.sanitizeAutoPoints(
                    MontageLogic.automationLaneOf(project, tr.id)?.points ?: emptyList()
                )
            }
            val musicEnd = music.indices.maxOfOrNull { musicStarts[it] + musicDurFrames[it] } ?: 0L
            val totalFrames = maxOf(plan.totalFrames, musicEnd)
            // الخفض التلقائي: نطاقات النطق تُحسب مرة واحدة فلا حساب متكرر لكل إطار
            val duckIntervals = MontageLogic.narrationIntervals(clips, project.gapMs, project.crossfadeMs)
            val duckEnabled = project.duckingEnabled && music.isNotEmpty()
            val streams = arrayOfNulls<ClipStream>(clips.size)
            val musicStreams = arrayOfNulls<ClipStream>(music.size)
            val mix = IntArray(MIX_BLOCK * 2)
            val segBuf = ShortArray(MIX_BLOCK * 2)
            var f = 0L
            var first = 0 // أول مقطع لم ينتهِ بعد — المصادر تُفتح بكسل وتُغلق تِباعًا
            try {
                while (f < totalFrames) {
                    if (cancelled()) throw ClipCancelled()
                    val blockEnd = min(f + MIX_BLOCK, totalFrames)
                    val blockFrames = (blockEnd - f).toInt()
                    java.util.Arrays.fill(mix, 0, blockFrames * 2, 0)
                    // إغلاق ما انتهى وتخطي ما لم يبدأ بعد
                    while (first < clips.size &&
                        plan.starts[first] + plan.durations[first] <= f
                    ) {
                        streams[first]?.close(); streams[first] = null
                        first++
                    }
                    var any = false
                    var j = first
                    while (j < clips.size && plan.starts[j] < blockEnd) {
                        val s = plan.starts[j]
                        val e = s + plan.durations[j]
                        val from = maxOf(f, s)
                        val to = min(blockEnd, e)
                        if (to > from) {
                            if (streams[j] == null) streams[j] = ClipStream(clips[j], plan.durations[j])
                            val need = (to - from).toInt()
                            val got = streams[j]!!.read(segBuf, need)
                            if (got > 0) {
                                mixSegment(
                                    segBuf, got, from, s, plan, j, clips[j],
                                    if (j > 0) plan.xfStart[j - 1] else -1L,
                                    if (j > 0) plan.xfFrames[j - 1] else 0L,
                                    if (j < clips.size - 1) plan.xfStart[j] else -1L,
                                    if (j < clips.size - 1) plan.xfFrames[j] else 0L,
                                    mix, (from - f).toInt(), blockFrames
                                )
                                any = true
                            }
                        }
                        j++
                    }
                    // مسار الموسيقى المرافقة: مزج على المواضع المطلقة بكسب الخفض
                    for (mi in music.indices) {
                        val ms = musicStarts[mi]
                        val me = ms + musicDurFrames[mi]
                        if (me <= f) {
                            musicStreams[mi]?.close(); musicStreams[mi] = null
                            continue
                        }
                        if (ms >= blockEnd) continue
                        val from = maxOf(f, ms)
                        val to = min(blockEnd, me)
                        if (to > from) {
                            if (musicStreams[mi] == null) {
                                musicStreams[mi] = ClipStream(musicSources[mi]!!, musicDurFrames[mi])
                            }
                            val need = (to - from).toInt()
                            val got = musicStreams[mi]!!.read(segBuf, need)
                            if (got > 0) {
                                mixMusic(
                                    segBuf, got, from, ms, musicDurFrames[mi], music[mi],
                                    musicTrackAmp[mi], musicPanL[mi], musicPanR[mi],
                                    musicAuto[mi] ?: emptyList(),
                                    if (musicDucks[mi]) duckIntervals else emptyList(),
                                    duckEnabled,
                                    project.duckDb, project.duckAttackMs, project.duckReleaseMs,
                                    mix, (from - f).toInt(), blockFrames
                                )
                                any = true
                            }
                        }
                    }
                    if (any) {
                        val bytes = ByteArray(blockFrames * 4)
                        var bi = 0
                        for (k in 0 until blockFrames * 2) {
                            val v = mix[k].coerceIn(-32768, 32767)
                            bytes[bi++] = (v and 0xFF).toByte()
                            bytes[bi++] = ((v shr 8) and 0xFF).toByte()
                        }
                        sink.write(bytes, 0, bytes.size)
                    } else {
                        writeSilence(sink, blockFrames.toLong())
                    }
                    f = blockEnd
                    onProgress?.invoke(f.toFloat() / totalFrames)
                }
            } finally {
                for (st in streams) st?.close()
                for (st in musicStreams) st?.close()
            }
        } catch (c: ClipCancelled) {
            return Result(false, sink.durationMs, "أُلغي التصيير")
        } catch (t: Throwable) {
            return Result(false, 0L, "فشل التصيير: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            try { sink.close() } catch (_: Exception) { }
        }
        return Result(true, sink.durationMs, null)
    }

    /**
     * مزج إطارات مقطع في الكتلة: كسب المقطع × تلاشيه × التلاشي الدقيق × كسب
     * الكروس فيد على الحدّين المحيطين — تجميع صحيح بلا لفّ ثم حبس عند الكتابة.
     */
    private fun mixSegment(
        segBuf: ShortArray, got: Int, globalFrom: Long, clipStart: Long,
        plan: RenderPlan, clipIndex: Int, clip: MontageClip,
        prevXfStart: Long, prevXfFrames: Long,
        nextXfStart: Long, nextXfFrames: Long,
        mix: IntArray, mixOffsetFrames: Int, blockFrames: Int
    ) {
        val durMs = plan.durations[clipIndex] * 1000L / RATE
        val vol = clip.volumePercent / 100f
        for (k in 0 until got) {
            val g = globalFrom + k
            val offMs = (g - clipStart) * 1000L / RATE
            var gain = vol * MontageLogic.fadeGainAt(offMs, durMs, clip.fadeInMs, clip.fadeOutMs)
            gain *= MontageLogic.microFadeGainAt(offMs, durMs)
            if (prevXfFrames > 0L && g >= prevXfStart && g < prevXfStart + prevXfFrames) {
                val p = (g - prevXfStart).toFloat() / prevXfFrames
                gain *= MontageLogic.crossfadeInGain(p)
            }
            if (nextXfFrames > 0L && g >= nextXfStart && g < nextXfStart + nextXfFrames) {
                val p = (g - nextXfStart).toFloat() / nextXfFrames
                gain *= MontageLogic.crossfadeOutGain(p)
            }
            val mBase = (mixOffsetFrames + k) * 2
            if (mBase + 1 >= blockFrames * 2) break
            mix[mBase] += (segBuf[2 * k] * gain).toInt()
            mix[mBase + 1] += (segBuf[2 * k + 1] * gain).toInt()
        }
    }

    /** صمت الفاصل: أصفار قانونية تُكتب متدفقة (وفي MP3 تُرمَّز صمتًا حقيقيًا). */
    private fun writeSilence(sink: PcmSink, frames: Long) {
        var remaining = frames
        val chunk = ByteArray(64 * 1024)
        while (remaining > 0) {
            val bytes = (min(remaining, chunk.size / 4L).toInt() * 4)
            sink.write(chunk, 0, bytes)
            remaining -= bytes / 4L
        }
    }

    /**
     * مزج إطارات مقطع مسار حر (v0.35.0 البند 16 — معمّم v0.36.0 لعدة خطوط،
     * واكتمل في v0.41.0 بنظام المونتاج متعدد المسارات الاحترافي): كسب المسار
     * × كسب مجموعته × كسب المقطع × تلاشيه × التلاشي الدقيق × أتمتة المستوى
     * عند اللحظة العالمية × الخفض التلقائي (مسارات الموسيقى الخلفية وحدها)،
     * والتوازن Pan يوزّع كل إطار بين القناتين خطيًا — الكتم أو المنفرد
     * (للمسار أو لمجموعته) يصمت كليًا بصفر الكسب قبل أي حساب.
     */
    private fun mixMusic(
        segBuf: ShortArray, got: Int, globalFrom: Long, musicStart: Long, musicDurFrames: Long,
        clip: MontageMusicClip,
        trackAmp: Float,
        panL: Float, panR: Float,
        autoPoints: List<com.saidjte.schoolradio.desktop.data.MontageAutoPoint>,
        duckIntervals: List<Pair<Long, Long>>,
        duckEnabled: Boolean,
        duckDb: Int, duckAttackMs: Long, duckReleaseMs: Long,
        mix: IntArray, mixOffsetFrames: Int, blockFrames: Int
    ) {
        val durMs = musicDurFrames * 1000L / RATE
        val vol = clip.volumePercent / 100f * trackAmp
        val db = duckDb
        val atk = duckAttackMs
        val rel = duckReleaseMs
        for (k in 0 until got) {
            val g = globalFrom + k
            val offMs = (g - musicStart) * 1000L / RATE
            var gain = vol * MontageLogic.fadeGainAt(offMs, durMs, clip.fadeInMs, clip.fadeOutMs)
            gain *= MontageLogic.microFadeGainAt(offMs, durMs)
            if (autoPoints.isNotEmpty()) {
                gain *= MontageLogic.autoGainPercentAt(autoPoints, g * 1000L / RATE) / 100f
            }
            gain *= MontageLogic.duckGainAt(g * 1000L / RATE, duckIntervals, db, atk, rel, duckEnabled)
            val mBase = (mixOffsetFrames + k) * 2
            if (mBase + 1 >= blockFrames * 2) break
            mix[mBase] += (segBuf[2 * k] * gain * panL).toInt()
            mix[mBase + 1] += (segBuf[2 * k + 1] * gain * panR).toInt()
        }
    }

    private fun framesFor(ms: Long): Long = ms * RATE / 1000L

    /**
     * تحويل مقطع موسيقى إلى مقطع مصدر بشباكه داخل ملفه — يخدم المُصيّر
     * ومغلّفات عرض مسار الموسيقى معًا بعقد النافذة الفارغة = الملف كاملًا.
     */
    fun musicAsSourceClip(m: MontageMusicClip): MontageClip {
        val (ws, we) = MontageLogic.clampWindow(m.startInFileMs, m.endInFileMs, m.durationMs)
        return MontageClip(
            id = m.id, path = m.path, title = m.title,
            durationMs = m.durationMs, startMs = ws, endMs = we,
            volumePercent = m.volumePercent,
            fadeInMs = m.fadeInMs, fadeOutMs = m.fadeOutMs
        )
    }

    /**
     * مغلّف عرض مقطع (v0.34.0 — البند 1 و8): ذروات دلاء النافذة بعد المستوى
     * والتلاشين والتلاشي الدقيق — وقود الويڤ فورم ومؤشر VU ومناطق التشبع.
     * يعمل على مصدر فكٍّ حقيقي بلا جهاز صوت، والفشل يعيد مغلّفًا صامتًا.
     */
    fun computeEnvelope(clip: MontageClip, buckets: Int): MontageEnvelope {
        // نفس عقد المُصيِّر: النافذة الفارغة تعني «الملف كاملًا»
        val (ws, we) = MontageLogic.clampWindow(clip.startMs, clip.endMs, clip.durationMs)
        val eff = clip.copy(startMs = ws, endMs = we)
        val durMs = MontageLogic.clipDurationMs(eff)
        val n = buckets.coerceIn(1, 2000)
        val peaks = FloatArray(n)
        val clipped = BooleanArray(n)
        if (durMs <= 0L) return MontageEnvelope(clip.id, peaks, clipped)
        val totalFrames = framesFor(durMs).coerceAtLeast(1L)
        runCatching {
            ClipStream(eff, totalFrames).use { st ->
                val dst = ShortArray(8192 * 2)
                val vol = eff.volumePercent / 100f
                var produced = 0L
                while (true) {
                    val r = st.read(dst, 8192)
                    if (r <= 0) break
                    for (f in 0 until r) {
                        val offMs = (produced + f) * 1000L / RATE
                        val g = vol *
                                MontageLogic.fadeGainAt(offMs, durMs, eff.fadeInMs, eff.fadeOutMs) *
                                MontageLogic.microFadeGainAt(offMs, durMs)
                        val bi = (((produced + f) * n) / totalFrames).toInt().coerceIn(0, n - 1)
                        for (c in 0 until 2) {
                            val v = Math.abs(dst[2 * f + c] * g) / 32768f
                            if (v > peaks[bi]) peaks[bi] = v
                            if (MontageLogic.isClippedLevel(v)) clipped[bi] = true
                        }
                    }
                    produced += r
                }
            }
        }
        return MontageEnvelope(clip.id, peaks, clipped)
    }

    /**
     * الذروة الخام للنافذة بلا كسب (v0.34.0 — البند 7): مرجع التطبيع — تُقرأ
     * العيّنات من المصدر مباشرة حتى نهاية النافذة بمعدل الملف نفسه.
     */
    fun computeRawPeak(clip: MontageClip): Int {
        // نفس عقد المُصيِّر: النافذة الفارغة تعني «الملف كاملًا»
        val (ws, we) = MontageLogic.clampWindow(clip.startMs, clip.endMs, clip.durationMs)
        val durMs = (we - ws).coerceAtLeast(0L)
        if (durMs <= 0L) return 0
        return runCatching {
            MontageClipSource.open(File(clip.path)).use { src ->
                src.skipTo(ws)
                val ch = src.channels.coerceAtLeast(1)
                val needFrames = framesFor(durMs) * src.rate / RATE + 1
                val native = ShortArray(64 * 1024)
                var peak = 0
                var frames = 0L
                while (frames < needFrames) {
                    val n = src.read(native)
                    if (n <= 0) break
                    val lim = min(n.toLong(), (needFrames - frames) * ch).toInt()
                    for (i in 0 until lim) peak = maxOf(peak, Math.abs(native[i].toInt()))
                    frames += lim / ch
                }
                peak
            }
        }.getOrDefault(0)
    }
}

/**
 * طيّ أي عدد قنوات إلى ستيريو: أحادي يُكرَّر في القناتين، ثنائي كما هو،
 * وأكثر من قناتين تأخذ القناتين الأوليين — عقد موحّد قبل إعادة التشكيل.
 */
internal fun foldStereo(src: ShortArray, samples: Int, channels: Int): ShortArray {
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

/**
 * نافذة إنتاج مقطع على معدل الوجهة: تفتح المصدر وتقفز لبداية النافذة ثم تنتج
 * إطارات ستيريو 44.1k بالتسلسل (فكّ → طيّ ستيريو → إعادة تشكيل) — الخلاط
 * الكتلي يسحب منها بالضبط ما تحتاجه كتلة الزمن، فلا ذاكرة زائدة ولا فكّ مكرر.
 */
private class ClipStream(clip: MontageClip, private val targetFrames: Long) : Closeable {
    private val src = MontageClipSource.open(File(clip.path))
    private val native = ShortArray(64 * 1024)
    private var buf = ShortArray(0)
    private var bufFrames = 0
    private var bufPos = 0
    private var produced = 0L

    /** ينتج حتى maxFrames إطارات وجهة — أو -1 عند استهلاك النافذة كاملة. */
    fun read(dst: ShortArray, maxFrames: Int): Int {
        if (maxFrames <= 0 || produced >= targetFrames) return -1
        val want = min(maxFrames, (targetFrames - produced).toInt().coerceAtLeast(0))
        var filled = 0
        while (filled < want) {
            if (bufPos < bufFrames) {
                val take = min(bufFrames - bufPos, want - filled)
                System.arraycopy(buf, bufPos * 2, dst, filled * 2, take * 2)
                bufPos += take
                filled += take
                produced += take
            } else if (!refill()) break
        }
        return if (filled == 0) -1 else filled
    }

    private fun refill(): Boolean {
        while (true) {
            val n = src.read(native)
            if (n <= 0) return false
            val frames = n / src.channels.coerceAtLeast(1)
            val stereo = foldStereo(native, n, src.channels)
            val resampled = if (src.rate == MontageRender.RATE) stereo
            else PcmMixer.resampleStereo(stereo, frames, src.rate, MontageRender.RATE)
            val outFrames = resampled.size / 2
            if (outFrames > 0) {
                buf = resampled
                bufPos = 0
                bufFrames = outFrames
                return true
            }
        }
    }

    override fun close() {
        try { src.close() } catch (_: Exception) { }
    }
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
