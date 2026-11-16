package com.saidjte.schoolradio.desktop.audio

import java.io.IOException
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.Mixer

/**
 * قارئ مقطع بثّ حي من يوتيوب (v0.51.0 «تشغيل مباشر دون تحميل») — يحقق عقد
 * [TrackReader] نفسه الذي تتحقق به قرّاء الملفات، فيمتزج البث في المحرك بلا
 * أي استثناء: المعادل والفيدر والخفض والمقياس وتسجيل المزج والكروس فيد تعمل.
 *
 * البنية: [StreamPipeline] يفكّ الوسيط الحي كتلة تلو أخرى، وكل كتلة تمر بقناة
 * المزج ثم تُكتب في خط السماع. السعي يعيد فتح البث من الصفر ويُسكِت الفك حتى
 * الموضع المطلوب (روابط يوتيوب لا تتيح قفزة بايتات مُسماة)، وانقطاع الشبكة
 * يجرب إعادة اتصال واحدة عند الموضع نفسه قبل أن يُسلّم خطأً صادقًا.
 */
internal class StreamTrackReader(
    private val spec: StreamSpec,
    private val displayTitle: String,
    private val knownDurationMs: Long,
    outMain: Mixer.Info?,
    outSecond: Mixer.Info?,
    volume: () -> Float,
    onSecondOutFailed: ((Throwable) -> Unit)?,
    private val channel: MusicChannel,
    /** فتح الخط المُحقَن (للاختبارات والفحص الذاتي) — null = مخرجات النظام كالملفات. */
    private val outLineFactory: ((AudioFormat) -> PcmOutLine)? = null
) : TrackReader {

    private var pipeline: StreamPipeline = StreamPipelineFactory.open(spec)
    private var pendingFirst: ShortArray? = null
    private val format: AudioFormat
    private val out: PcmOutLine

    override val durationMs: Long get() = knownDurationMs.coerceAtLeast(0L)
    override val currentMs: Long get() = pipeline.currentMs

    init {
        val first = try {
            pipeline.nextBlock()
        } catch (t: Throwable) {
            try { pipeline.close() } catch (_: Exception) { }
            throw IllegalArgumentException(t.message ?: "تعذّر بدء البث المباشر من يوتيوب.")
        }
        if (first == null) {
            val err = pipeline.lastError
            try { pipeline.close() } catch (_: Exception) { }
            throw IllegalArgumentException(
                err ?: "لم يصل أي صوت من يوتيوب للبث المباشر — تحقق من الشبكة وأعد «تشغيل مباشر»."
            )
        }
        format = pipeline.format ?: AudioFormat(44100f, 16, 2, true, false)
        out = outLineFactory?.invoke(format)
            ?: AudioDevices.openOutLine(outMain, outSecond, format, onSecondOutFailed)
        out.open(format, 16 * 1024)
        out.start()
        out.setVolume(volume())
        channel.configure(format.sampleRate.toInt(), format.channels)
        pendingFirst = first
    }

    override fun readChunk(): Boolean {
        var block = pendingFirst
        pendingFirst = null
        if (block == null) block = pullOrReconnect() ?: return false
        writeBlock(block)
        return true
    }

    /**
     * كتلة تالية مع حماية الانقطاع: خطأ شبكي حقيقي يجرب إعادة اتصال واحدة عند
     * الموضع نفسه؛ فشلت → يُرمى بخطأ عربي واضح (يصل لواجهة المشغّل وينهي البث).
     */
    private fun pullOrReconnect(): ShortArray? {
        val b = try {
            pipeline.nextBlock()
        } catch (_: Throwable) { null }
        if (b != null) return b
        if (pipeline.lastError == null) return null // نهاية بث نظيفة — لا إعادة اتصال
        val at = pipeline.currentMs
        try { pipeline.close() } catch (_: Exception) { }
        pipeline = try {
            StreamPipelineFactory.open(spec)
        } catch (_: Throwable) {
            throw IOException(pipeline.lastError ?: "انقطع بث يوتيوب وتعذّرت إعادة الاتصال — أعد «تشغيل مباشر».")
        }
        if (!skipTo(at)) {
            throw IOException(pipeline.lastError ?: "انقطع بث يوتيوب وتعذّر استكماله من الموضع نفسه.")
        }
        val next = try { pipeline.nextBlock() } catch (_: Throwable) { null }
        if (next == null) {
            throw IOException(pipeline.lastError ?: "انقطع بث يوتيوب بعد إعادة الاتصال — أعد «تشغيل مباشر».")
        }
        return next
    }

    /** تفقيد صامت حتى موضع معين — دون كتابة في الخط ولا قناة المزج. */
    private fun skipTo(targetMs: Long): Boolean {
        var guard = 0
        while (pipeline.currentMs < targetMs) {
            val b = try {
                pipeline.nextBlock()
            } catch (_: Throwable) { return false }
            if (b == null) return pipeline.currentMs >= targetMs && pipeline.lastError == null
            // سقف أمان: ساعتا بث عند 44.1kHz ≈ 317 مليون عينة — ما بعد ذلك بث شاذّ
            if (++guard > 20_000_000) return false
        }
        return true
    }

    private fun writeBlock(block: ShortArray) {
        if (block.isEmpty()) return
        // قناة المزج: المعادل + الفيدر + الخفض + المقياس قبل كتابة الخط
        channel.processShorts(block, block.size)
        val bytes = ByteArray(block.size * 2)
        var bi = 0
        for (s in block) {
            val v = s.toInt()
            bytes[bi++] = (v and 0xFF).toByte()
            bytes[bi++] = ((v shr 8) and 0xFF).toByte()
        }
        out.write(bytes, 0, bytes.size)
    }

    override fun seek(ms: Long) {
        val target = ms.coerceIn(0L, durationMs)
        try { pipeline.close() } catch (_: Exception) { }
        pipeline = StreamPipelineFactory.open(spec)
        pendingFirst = null
        out.flush()
        if (target > 0) {
            if (!skipTo(target)) {
                throw IOException("تعذّر الانتقال في بث يوتيوب إلى الموضع المطلوب — البث يُسعى بإعادة الفتح.")
            }
        }
    }

    override fun pause() { try { out.stop() } catch (_: Exception) { } }
    override fun resume() { try { out.start() } catch (_: Exception) { } }
    override fun applyVolume(volume: Float) { out.setVolume(volume) }

    override fun close() {
        try { pipeline.close() } catch (_: Exception) { }
        try { out.close() } catch (_: Exception) { }
    }
}
