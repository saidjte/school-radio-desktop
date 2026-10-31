package com.saidjte.schoolradio.desktop.audio

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * قارئ ترويسة WAV لاستخراج معدل العيّنات وعدد القنوات وعمق البت وطول البيانات.
 * يتجاوز الأجزاء الإضافية (LIST وغيرها) بشكل صحيح.
 */
object WavHeader {

    data class Info(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataBytes: Long
    )

    fun read(file: File): Info? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                if (len < 44) return null

                val magic = ByteArray(4)
                raf.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "RIFF") return null
                raf.skipBytes(4) // حجم RIFF الكلي
                val wave = ByteArray(4)
                raf.readFully(wave)
                if (String(wave, Charsets.US_ASCII) != "WAVE") return null

                var sampleRate = 0
                var channels = 0
                var bits = 16
                var dataBytes = -1L

                while (raf.filePointer + 8 <= len) {
                    val chunkHeader = ByteArray(8)
                    if (raf.read(chunkHeader) < 8) break
                    val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                    val size = ByteBuffer.wrap(chunkHeader, 4, 4)
                        .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

                    when (id) {
                        "fmt " -> {
                            val fmtSize = size.toInt().coerceAtLeast(16).coerceAtMost(64)
                            val fmt = ByteArray(fmtSize)
                            raf.readFully(fmt)
                            val f = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                            f.short // صيغة الصوت (1 = PCM)
                            channels = f.short.toInt()
                            sampleRate = f.int
                            f.int // معدل البايتات
                            f.short // محاذاة الكتلة
                            bits = f.short.toInt()
                            val leftover = size - 16L
                            if (leftover > 0) raf.seek(raf.filePointer + leftover + (size % 2))
                        }
                        "data" -> {
                            val remaining = len - raf.filePointer
                            dataBytes = if (size <= 0 || size > remaining) remaining else size
                            break
                        }
                        else -> {
                            val skip = size + (size % 2)
                            raf.seek(raf.filePointer + skip)
                        }
                    }
                }

                if (sampleRate <= 0 || channels <= 0 || dataBytes < 0) null
                else Info(sampleRate, channels, bits, dataBytes)
            }
        } catch (_: Exception) {
            null
        }
    }
}
