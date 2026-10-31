package com.saidjte.schoolradio.desktop.audio

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * كاتب ملفات WAV متدفّق: يكتب ترويسة مؤقتة عند البدء ثم يحدّث أحجام الأجزاء
 * عند الإغلاق. مناسب لتسجيل مباشر طويل دون معرفة المدة مسبقًا.
 */
class WavFileWriter(
    file: File,
    private val sampleRate: Int,
    private val channels: Int,
    private val bitsPerSample: Int = 16
) : Closeable {

    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var closed = false

    init {
        raf.setLength(0)
        raf.write(header(0L))
    }

    val bytesWritten: Long get() = dataBytes

    /** المدة الحالية بالمللي ثانية وفق البايتات المكتوبة حتى الآن. */
    val durationMs: Long
        get() = durationMsOf(dataBytes, sampleRate, channels, bitsPerSample)

    fun write(pcm: ByteArray, offset: Int = 0, length: Int = pcm.size) {
        if (closed) return
        raf.write(pcm, offset, length)
        dataBytes += length
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            raf.seek(0)
            raf.write(header(dataBytes))
        } finally {
            raf.close()
        }
    }

    private fun header(dataLen: Long): ByteArray {
        val blockAlign = channels * bitsPerSample / 8
        val byteRate = sampleRate * blockAlign
        val bb = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray(Charsets.US_ASCII))
        bb.putInt((36 + dataLen).toInt())
        bb.put("WAVE".toByteArray(Charsets.US_ASCII))
        bb.put("fmt ".toByteArray(Charsets.US_ASCII))
        bb.putInt(16)
        bb.putShort(1) // PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort(blockAlign.toShort())
        bb.putShort(bitsPerSample.toShort())
        bb.put("data".toByteArray(Charsets.US_ASCII))
        bb.putInt(dataLen.toInt())
        return bb.array()
    }

    companion object {
        fun durationMsOf(dataBytes: Long, sampleRate: Int, channels: Int, bitsPerSample: Int = 16): Long {
            val bytesPerSecond = sampleRate.toLong() * channels * (bitsPerSample / 8)
            return if (bytesPerSecond <= 0) 0L else dataBytes * 1000 / bytesPerSecond
        }
    }
}
