package com.saidjte.schoolradio.desktop.audio

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

/**
 * فاكّات القراءة الواحدة المشتركة (v0.42.0) — كانت خاصة بجدار الجينل
 * واستُخرجت ليتشاركهما محرك المعاينة في مكتبة الأصوات: MP3 بفاكّ JLayer
 * إطارًا بإطار، وغيره (WAV/OGG/FLAC/AIFF/AU) بمجرى PCM موحَّد 16-bit.
 * سلوكهما المطابق تمامًا يحميه اختبار جدار الجينل القائم.
 */

/** فاكّ MP3 بلا سعي: من أول إطار حتى النهاية. */
internal class Mp3OneShot(file: File) : Closeable {
    val format: AudioFormat
    private var stream: BufferedInputStream
    private var bitstream: Bitstream
    private val decoder = Decoder()

    init {
        val info = Mp3Info.analyze(file)
            ?: throw IllegalArgumentException("تعذّر تحليل ملف MP3: ${file.name}")
        val fh = info.first
        stream = BufferedInputStream(FileInputStream(file), 1 shl 16)
        Mp3Info.skipId3v2(stream)
        bitstream = Bitstream(stream)
        format = AudioFormat(fh.sampleRate.toFloat(), 16, fh.channels, true, false)
    }

    fun readChunkInto(out: PcmOutLine): Boolean {
        val header = try { bitstream.readFrame() } catch (_: Exception) { null } ?: return false
        try {
            val decoded = decoder.decodeFrame(header, bitstream)
            if (decoded is SampleBuffer && decoded.buffer.isNotEmpty()) {
                val shorts = decoded.buffer
                val bytes = ByteArray(shorts.size * 2)
                var bi = 0
                for (s in shorts) {
                    val v = s.toInt()
                    bytes[bi++] = (v and 0xFF).toByte()
                    bytes[bi++] = ((v shr 8) and 0xFF).toByte()
                }
                out.write(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            // إطار تالف: نتجاوزه ونكمل
        } finally {
            try { bitstream.closeFrame() } catch (_: Exception) { }
        }
        return true
    }

    override fun close() {
        try { bitstream.close() } catch (_: Exception) { }
        try { stream.close() } catch (_: Exception) { }
    }
}

/** مجرى PCM موحَّد 16-bit مبهمي الترتيب لأي صيغة يدعمها النظام (OGG/FLAC/AIFF/AU). */
internal class PcmOneShot(file: File) : Closeable {
    val format: AudioFormat
    private val ais: AudioInputStream
    private val frameSize: Int
    private val buf = ByteArray(64 * 1024)

    init {
        val raw = openAudioInputStream(file)
        val rf = raw.format
        ais = if (rf.encoding == AudioFormat.Encoding.PCM_SIGNED && !rf.isBigEndian && rf.sampleSizeInBits == 16) {
            raw
        } else {
            AudioSystem.getAudioInputStream(AudioFormat(rf.sampleRate, 16, rf.channels, true, false), raw)
        }
        format = ais.format
        frameSize = format.frameSize.coerceAtLeast(1)
    }

    fun readChunkInto(out: PcmOutLine): Boolean {
        val n = try { ais.read(buf) } catch (_: Exception) { -1 }
        if (n < 0) return false
        if (n == 0) return true
        val usable = n - (n % frameSize)
        if (usable > 0) out.write(buf, 0, usable)
        return true
    }

    override fun close() {
        try { ais.close() } catch (_: Exception) { }
    }
}
/* openAudioInputStream موجودة مسبقًا في MusicPlayerEngine.kt (internal) وتشارك هنا. */

