package com.saidjte.schoolradio.desktop.audio

import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder as JLayerDecoder
import javazoom.jl.decoder.SampleBuffer as JLayerSampleBuffer
import net.sourceforge.jaad.aac.Decoder
import net.sourceforge.jaad.aac.SampleBuffer
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat

/**
 * مصنع خطوط البث الحي (v0.51.0) — يفتح مواصفة بث على خط فكّ مناسب:
 * - AAC داخل fMP4 (صيغة يوتيوب 140 وما بعدها): [Fmp4StreamDemux] تدفقيًا + فاكّ jaad.
 * - MPEG صوتي (MP3/Layer2): JLayer إطارًا بإطار فوق مجرى HTTP.
 * قوائم HLS تُفكّ هنا (init ثم الأجزاء) قبل وصولها للديمكس.
 * كل الأخطاء برسائل عربية صادقة تصل واجهة المشغّل كما هي.
 */
internal object StreamPipelineFactory {

    fun open(spec: StreamSpec): StreamPipeline =
        if (spec.mp3) Mp3StreamPipeline(spec) else AacStreamPipeline(spec)
}

/**
 * خط AAC: ديمكس MP4 مجزّأ تدفقي + فاكّ jaad — الكتلة الواحدة = إطار AAC واحد
 * مفكوك إلى PCM 16-bit لين-إندَيان (خيار صريح كما في AudioTranscoder).
 */
internal class AacStreamPipeline(private val spec: StreamSpec) : StreamPipeline {

    private val demux: Fmp4StreamDemux
    private val decoder: Decoder
    private val sb = SampleBuffer()

    private var pcmRate = 0
    private var pcmChannels = 0
    private var pcmFrames = 0L
    @Volatile private var error: String? = null

    init {
        demux = Fmp4StreamDemux(buildSupply(spec))
        val cfg = try {
            demux.readInit()
        } catch (e: EOFException) {
            throw IllegalArgumentException("انقطع البث قبل اكتمال رأس MP4 — الرابط منتهٍ أو الشبكة ضعيفة؛ أعد «تشغيل مباشر».")
        } catch (e: IOException) {
            throw IllegalArgumentException(e.message ?: "تعذّر قراءة رأس البث من يوتيوب.")
        }
        decoder = try {
            Decoder(cfg.asc)
        } catch (e: Exception) {
            try { demux.close() } catch (_: Exception) { }
            val aot = com.saidjte.schoolradio.desktop.youtube.FragmentedMp4Aac.audioObjectType(cfg.asc)
            throw IllegalArgumentException(
                "فاكّ AAC رفض وصف صوت هذا البث (${com.saidjte.schoolradio.desktop.youtube.FragmentedMp4Aac.audioProfileName(aot)}) " +
                        "— الصيغة غير مدعومة للتشغيل المباشر؛ استخدم زر التحميل."
            )
        }
    }

    override val format: AudioFormat?
        get() = if (pcmRate > 0) AudioFormat(pcmRate.toFloat(), 16, pcmChannels.coerceIn(1, 2), true, false) else null

    override val currentMs: Long
        get() = if (pcmRate > 0) pcmFrames * 1000L / pcmRate else 0L

    override val lastError: String? get() = error

    override fun nextBlock(): ShortArray? {
        var skipped = 0
        while (true) {
            val sample = try {
                demux.nextSample()
            } catch (e: EOFException) {
                return null // بث مبتور: يُعامل كنهاية بيانات (سلوك v0.50)
            } catch (e: IOException) {
                error = e.message ?: "انقطع بث يوتيوب — أعد «تشغيل مباشر»."
                return null
            } ?: return null // نهاية البث النظيفة
            try {
                sb.setBigEndian(false)
                decoder.decodeFrame(sample, sb)
                val data = sb.getData()
                if (data.isEmpty()) continue
                if (pcmRate == 0) {
                    pcmRate = sb.getSampleRate().coerceAtLeast(8000)
                    pcmChannels = sb.getChannels().coerceIn(1, 2)
                }
                pcmFrames += data.size / 2 / pcmChannels
                val shorts = ShortArray(data.size / 2)
                ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                return shorts
            } catch (_: Exception) {
                // إطار تالف: نتجاوزه — وركبة متتالية طويلة تعني صيغة غير مدعومة
                if (++skipped > 128) {
                    error = "أطوال عينات AAC رفضها الفاكّ متتابعة — الصيغة غير مدعومة للتشغيل المباشر؛ استخدم زر التحميل."
                    return null
                }
            }
        }
    }

    override fun close() {
        try { demux.close() } catch (_: Exception) { }
    }
}

/**
 * خط MPEG صوتي: JLayer فوق مجرى HTTP مباشر — الكتلة الواحدة = إطار مفكوك.
 * الموضع من عدّ عينات الخرج (معدل القارئ الفعلي) فلا يعتمد على ترويسات مدة.
 */
internal class Mp3StreamPipeline(private val spec: StreamSpec) : StreamPipeline {

    private val stream: InputStream = HttpAudioSource.open(spec.url, spec.cookiesPath)
    private val bitstream = Bitstream(BufferedInputStream(stream, 1 shl 16))
    private val decoder = JLayerDecoder()

    private var outRate = 0
    private var outChannels = 0
    private var pcmFrames = 0L
    @Volatile private var error: String? = null

    override val format: AudioFormat?
        get() = if (outRate > 0) AudioFormat(outRate.toFloat(), 16, outChannels.coerceIn(1, 2), true, false) else null

    override val currentMs: Long
        get() = if (outRate > 0) pcmFrames * 1000L / outRate else 0L

    override val lastError: String? get() = error

    override fun nextBlock(): ShortArray? {
        val header = try {
            bitstream.readFrame()
        } catch (e: Exception) {
            error = "انقطع بث MPEG من الخادم — أعد «تشغيل مباشر»."
            return null
        } ?: return null
        try {
            // JLayer يبني مخزن الخرج بنفسه ويعيده مع كل إطار — والمخزن يُستهلك
            // فورًا قبل استدعاء الإطار التالي (عقد المشغّل نفسه: كتلة واحدة كل مرة)
            val decoded = decoder.decodeFrame(header, bitstream) as? JLayerSampleBuffer
            val shorts = decoded?.buffer
            if (shorts == null || shorts.isEmpty()) return ShortArray(0) // إطار صامت: يُكتب كما هو ولا يُحسب
            if (outRate == 0) {
                outRate = decoded.getSampleFrequency().coerceAtLeast(8000)
                outChannels = decoded.getChannelCount().coerceIn(1, 2)
            }
            pcmFrames += shorts.size / outChannels
            return shorts
        } catch (_: Exception) {
            return ShortArray(0) // إطار تالف: تجاوزه واصل
        } finally {
            try { bitstream.closeFrame() } catch (_: Exception) { }
        }
    }

    override fun close() {
        try { bitstream.close() } catch (_: Exception) { }
        try { stream.close() } catch (_: Exception) { }
    }
}

// ================= مُورِّدات المجرات =================

/** وسيط واحد مباشر (fMP4 كامل أو MP3) — مجرى واحد ثم النهاية. */
internal class SingleSupply(private val url: String, private val cookiesPath: String?) : StreamSupply {
    private var opened = false
    override fun next(): InputStream? {
        if (opened) return null
        opened = true
        return HttpAudioSource.open(url, cookiesPath)
    }
}

/**
 * قائمة HLS: رأس القائمة يُحلَّل — قائمة رئيسية تُتابع إلى أفضل مسار فيها،
 * ومقطع التهيئة (EXT-X-MAP) يفتح أولًا ثم أجزاء الوسائط بالترتيب.
 * أجزاء TS تُرفض صادقًا (فاكّ MPEG-TS خارج نطاق التشغيل المباشر).
 */
internal class HlsSupply(url: String, private val cookiesPath: String?) : StreamSupply {

    private var initStream: InputStream? = null
    private val segments: Iterator<String>

    init {
        val pl = resolvePlaylist(url)
        pl.initUri?.let { initStream = HttpAudioSource.open(it, cookiesPath) }
        segments = pl.segments.iterator()
    }

    /** فتح القائمة واتباع المسار الأفضل مرة واحدة إن كانت رئيسية تجميعية. */
    private fun resolvePlaylist(url: String): HttpAudioSource.Playlist {
        var pl = fetchAndParse(url)
        if (pl.isMaster) {
            val variant = pl.variantUri ?: throw IOException("قائمة HLS رئيسية بلا مسار — بنية غير مدعومة.")
            pl = fetchAndParse(variant)
            if (pl.isMaster) throw IOException("قائمتا HLS رئيسيتان متتاليتان — بنية غير مدعومة.")
        }
        return pl
    }

    private fun fetchAndParse(url: String): HttpAudioSource.Playlist {
        val text = HttpAudioSource.open(url, cookiesPath).use { s ->
            s.readBytes().toString(Charsets.UTF_8)
        }
        return HttpAudioSource.parse(text, url)
    }

    override fun next(): InputStream? {
        initStream?.let { initStream = null; return it }
        if (!segments.hasNext()) return null
        val seg = segments.next()
        val path = seg.substringBefore('?').lowercase()
        if (path.endsWith(".ts") || path.contains(".ts?")) {
            throw IOException("بث HLS بصيغة TS غير مدعوم للتشغيل المباشر — استخدم زر التحميل بدلًا منه.")
        }
        return HttpAudioSource.open(seg, cookiesPath)
    }
}

/** بناء المُورِّد المناسب لمواصفة البث. */
internal fun buildSupply(spec: StreamSpec): StreamSupply =
    if (spec.hls) HlsSupply(spec.url, spec.cookiesPath)
    else SingleSupply(spec.url, spec.cookiesPath)
