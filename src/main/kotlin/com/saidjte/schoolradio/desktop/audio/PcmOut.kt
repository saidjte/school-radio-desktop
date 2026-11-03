package com.saidjte.schoolradio.desktop.audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

/**
 * خط إخراج PCM موحّد فوق SourceDataLine — الواجهة الدنيا التي يحتاجها
 * مشغل الموسيقى والسماع المراقب: فتح/تشغيل/كتابة/كسب/إغلاق.
 *
 * وجود هذه الواجهة هو ما يجعل المخرجين المزدوجين ممكنين بلا تفريخ خيوط:
 * TwinPcmOut يكتب نفس الكتلة في الجهازين معًا، والكتابة في SourceDataLine
 * محظورة حتى يفرغ جزء من مخزن الجهاز — فيتبع الإخراجان إيقاع الأبطأ
 * تلقائيًا وتبقى القناتان متطابقتين.
 */
interface PcmOutLine : AutoCloseable {
    fun open(format: AudioFormat, bufferSize: Int)
    fun start()
    fun stop()
    fun flush()
    fun write(b: ByteArray, off: Int, len: Int)

    /** كسب 0..1 يُترجم إلى MASTER_GAIN على كل جهاز (حيثما يتوفر المتحكم). */
    fun setVolume(v: Float)

    override fun close()
}

/** إخراج وحيد فوق SourceDataLine واحد — سلوك الإصدارات السابقة حرفيًا. */
class SinglePcmOut(private val line: SourceDataLine) : PcmOutLine {
    override fun open(format: AudioFormat, bufferSize: Int) { line.open(format, bufferSize) }
    override fun start() { line.start() }
    override fun stop() { try { line.stop() } catch (_: Exception) { } }
    override fun flush() { try { line.flush() } catch (_: Exception) { } }
    override fun write(b: ByteArray, off: Int, len: Int) { line.write(b, off, len) }
    override fun setVolume(v: Float) { applyGain(line, v) }
    override fun close() { try { line.stop(); line.close() } catch (_: Exception) { } }
}

/**
 * إخراج مزدوج: نفس الكتلة تُكتب في الجهازين (رئيسي + ثانٍ) — السماعة
 * والقاعة معًا، أو قاعتان معًا. فشل الجهاز الثاني لا يمس الأول أبدًا:
 * يُفتح ما أمكن ويُكتب فيه، وما تعذّر يُهمل بصمت الواجهة مع رسالة
 * يرفعها المتصل. الكسب يُطبق على الجهازين معًا فتبقى مستوياتهما نسخة واحدة.
 */
class TwinPcmOut(private val a: PcmOutLine, private val b: PcmOutLine) : PcmOutLine {
    override fun open(format: AudioFormat, bufferSize: Int) {
        a.open(format, bufferSize)
        b.open(format, bufferSize)
    }
    override fun start() { a.start(); b.start() }
    override fun stop() { a.stop(); b.stop() }
    override fun flush() { a.flush(); b.flush() }
    override fun write(bArr: ByteArray, off: Int, len: Int) {
        a.write(bArr, off, len)
        b.write(bArr, off, len)
    }
    override fun setVolume(v: Float) { a.setVolume(v); b.setVolume(v) }
    override fun close() {
        try { a.close() } catch (_: Exception) { }
        try { b.close() } catch (_: Exception) { }
    }
}

private fun gainDb(volume: Float): Float =
    if (volume <= 0.01f) -60f else 20f * log10(volume).toFloat()

internal fun applyGain(line: SourceDataLine, volume: Float) {
    try {
        val c = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        c.value = gainDb(volume).coerceIn(c.minimum, c.maximum)
    } catch (_: Exception) { }
}
