package com.saidjte.schoolradio.desktop.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * فاكّ MP4 خالص خاص بنا (v0.49.0 «الصيغ المدعومة»): مكتبة jaad تتعطل في
 * صندوق meta الذي يكتبه ffmpeg في أكثر ملفات M4A شيوعًا («box too large
 * for parent»)، فنقرأ بنية الصناديق بأنفسنا — moov→trak→mdia→stbl — ونستخرج
 * جداول العينات (stsz/stco/co64/stsc) ووصف فاكّ AAC من esds، ونبقي jaad
 * لوظيفته الوحيدة السليمة: فك إطار AAC إلى PCM. كل القراءة بـRandomAccessFile
 * فmoov في أول الملف أو آخره تُقبل سواسية.
 */
internal object Mp4AacDemux {

    class Demux(
        val raf: RandomAccessFile,
        val config: ByteArray,          // AudioSpecificConfig (DecSpecificInfo من esds)
        val offsets: LongArray,         // موضع كل عينة AAC (كل عنصر = إطار فك)
        val sizes: IntArray,
        val sampleCount: Int,
        val mediaTimescale: Int         // للصوت = معدل العينات (مدة = عددها×1024/هذا)
    ) {
        private var idx = 0
        fun hasMore(): Boolean = idx < sampleCount
        fun nextFrame(): ByteArray? {
            if (idx >= sampleCount) return null
            val i = idx++
            raf.seek(offsets[i])
            val b = ByteArray(sizes[i])
            raf.readFully(b)
            return b
        }
        fun close() { try { raf.close() } catch (_: Exception) { } }
    }

    private const val MOOV = 0x6D6F6F76
    private const val TRAK = 0x7472616B
    private const val MDIA = 0x6D646961
    private const val MINF = 0x6D696E66
    private const val STBL = 0x7374626C
    private const val MDHD = 0x6D646864
    private const val STSD = 0x73747364
    private const val MP4A = 0x6D703461
    private const val ESDS = 0x65736473
    private const val STSZ = 0x7374737A
    private const val STCO = 0x7374636F
    private const val CO64 = 0x636F3634
    private const val STSC = 0x73747363

    private fun type(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun be32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    /**
     * قراءة ترويسة صندوق من الموضع الحالي: [الحجم 4][النوع 4]، وحجم 1 يعني
     * 64-bit لارغسايز. يعيد (الحجم الكلي شامل الترويسة، النوع).
     */
    private fun readBoxHeader(raf: RandomAccessFile): Pair<Long, Int> {
        val h = ByteArray(8)
        raf.readFully(h)
        var size = ((h[0].toLong() and 0xFF) shl 24) or ((h[1].toLong() and 0xFF) shl 16) or
                ((h[2].toLong() and 0xFF) shl 8) or (h[3].toLong() and 0xFF)
        val t = type(h, 4)
        if (size == 1L) {
            val ext = ByteArray(8)
            raf.readFully(ext)
            size = 0
            for (b in ext) size = (size shl 8) or (b.toLong() and 0xFF)
        }
        return size to t
    }

    /**
     * الجولة العودية داخل الصناديق المستهدفة حتى عمق stbl، وجمع الحقول الخمسة:
     * config من esds، وstsz (عدد وأحجام العينات)، وstco/co64 (مواضع الكتل)،
     * وstsc (كثافة العينات بالكتل)، وmdhd (مقياس زمن المسار).
     */
    private class Collected {
        var config: ByteArray? = null
        var sampleCount = 0
        var uniformSize = 0L
        var sizes: IntArray? = null
        var chunks: LongArray? = null
        var stscFirst: IntArray? = null
        var stscPer: IntArray? = null
        var timescale = -1
    }

    private fun descend(raf: RandomAccessFile, endPos: Long, len: Long, c: Collected): Boolean {
        var found = false
        var pos = raf.filePointer
        while (pos + 8 <= endPos) {
            raf.seek(pos)
            val (size, t) = readBoxHeader(raf)
            if (size < 8L || pos + size > endPos + 8 || pos + size > len) break
            when (t) {
                MOOV, TRAK, MDIA, MINF, STBL -> {
                    found = descend(raf, pos + size, len, c) || found
                }
                MDHD -> {
                    val v = raf.read()
                    raf.skipBytes(3)
                    if (v == 1) {
                        raf.skipBytes(16) // creation + modification (64-bit)
                        val d = ByteArray(12)
                        raf.readFully(d)
                        c.timescale = be32(d, 0)
                    } else {
                        raf.skipBytes(8) // creation + modification (32-bit)
                        val d = ByteArray(8)
                        raf.readFully(d)
                        c.timescale = be32(d, 0)
                    }
                }
                STSD -> {
                    raf.skipBytes(4) // نسخة وأعلام
                    raf.skipBytes(4) // عدد المداخل — المداخل ذاتية الحجم حتى نهاية stsd
                    // مداخل stsd صناديق بحجمها الذاتي حتى نهاية stsd
                    val stsdEnd = pos + size
                    var guard = 0
                    while (raf.filePointer + 8 <= stsdEnd && guard++ < 16) {
                        val eHere = raf.filePointer
                        val (eSize, eType) = readBoxHeader(raf)
                        if (eType == MP4A) {
                            // reserved(6)+dataRefIndex(2)+version(2)+revision(2)+vendor(4)
                            // +channels(2)+sampleSize(2)+preDefined(2)+reserved(2)+rate(4)
                            var p = eHere + 8 + 6 + 2 + 8 + 2 + 2 + 2 + 2 + 4
                            while (p + 8 <= eHere + eSize) {
                                raf.seek(p)
                                val (s2, t2) = readBoxHeader(raf)
                                if (s2 < 8L || p + s2 > eHere + eSize) break
                                if (t2 == ESDS) {
                                    raf.skipBytes(4) // نسخة وأعلام esds
                                    c.config = parseEsds(raf)
                                }
                                p += s2
                            }
                        }
                        raf.seek(eHere + eSize)
                    }
                    raf.seek(stsdEnd)
                }
                STSZ -> {
                    raf.skipBytes(4)
                    val b = ByteArray(8); raf.readFully(b)
                    c.uniformSize = be32(b, 0).toLong()
                    c.sampleCount = be32(b, 4)
                    if (c.uniformSize == 0L && c.sampleCount in 1..1_000_000) {
                        val tb = ByteArray(c.sampleCount * 4)
                        raf.readFully(tb)
                        val arr = IntArray(c.sampleCount)
                        for (i in 0 until c.sampleCount) arr[i] = be32(tb, i * 4)
                        c.sizes = arr
                    }
                }
                STCO -> {
                    raf.skipBytes(4)
                    val n = ByteArray(4); raf.readFully(n)
                    val cnt = be32(n, 0)
                    if (cnt in 1..1_000_000) {
                        val b = ByteArray(cnt * 4); raf.readFully(b)
                        c.chunks = LongArray(cnt) { i -> be32(b, i * 4).toLong() and 0xFFFFFFFFL }
                    }
                }
                CO64 -> {
                    raf.skipBytes(4)
                    val n = ByteArray(4); raf.readFully(n)
                    val cnt = be32(n, 0)
                    if (cnt in 1..1_000_000) {
                        val b = ByteArray(cnt * 8); raf.readFully(b)
                        val arr = LongArray(cnt)
                        for (i in 0 until cnt) {
                            var v = 0L
                            for (k in 0 until 8) v = (v shl 8) or (b[i * 8 + k].toLong() and 0xFF)
                            arr[i] = v
                        }
                        c.chunks = arr
                    }
                }
                STSC -> {
                    raf.skipBytes(4)
                    val n = ByteArray(4); raf.readFully(n)
                    val cnt = be32(n, 0)
                    if (cnt in 1..1_000_000) {
                        val b = ByteArray(cnt * 12); raf.readFully(b)
                        c.stscFirst = IntArray(cnt) { i -> be32(b, i * 12) }
                        c.stscPer = IntArray(cnt) { i -> be32(b, i * 12 + 4) }
                    }
                }
            }
            pos += size
        }
        return found
    }

    /**
     * esds: ES_Descriptor(0x03) → DecoderConfigDescriptor(0x04) →
     * DecSpecificInfo(0x05) — بايتات AudioSpecificConfig هي ما يأخذه فاكّ jaad.
     */
    private fun parseEsds(raf: RandomAccessFile): ByteArray? {
        fun varLen(): Int {
            var v = 0
            for (i in 0 until 4) {
                val b = raf.read()
                if (b < 0) return -1
                v = (v shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) break
            }
            return v
        }
        if (raf.read() != 0x03) return null
        varLen()
        raf.skipBytes(2) // ES_ID
        // علم الواصف بايت واحد (ISO 14496-1 كما تقرؤه jaad نفسها): البتات
        // 0x80 streamDependency و0x40 URL و0x20 OCR — ffmpeg/آيفون يكتبان 0
        val f = raf.read()
        if (f and 0x80 != 0) raf.skipBytes(2) // streamDependency
        if (f and 0x40 != 0) { // URL
            val l = raf.read()
            if (l > 0) raf.skipBytes(l)
        }
        if (f and 0x20 != 0) raf.skipBytes(2) // OCR
        if (raf.read() != 0x04) return null
        varLen()
        raf.skipBytes(13) // objectType(1)+streamType(1)+bufferSize(3)+max(4)+avg(4)
        if (raf.read() != 0x05) return null
        val n = varLen()
        if (n <= 0 || n > 64) return null
        val b = ByteArray(n)
        raf.readFully(b)
        return b
    }

    /** تحليل الملف كاملًا: يعيد Demux جاهزًا للفك، أو null إن لم يوجد مسار AAC كامل. */
    fun parse(file: File): Demux? {
        val raf = RandomAccessFile(file, "r")
        try {
            val len = raf.length()
            val c = Collected()
            raf.seek(0)
            descend(raf, len, len, c)

            val cfg = c.config ?: return null
            val ch = c.chunks ?: return null
            val f = c.stscFirst ?: return null
            if (c.sampleCount <= 0 || ch.isEmpty()) return null

            val p = c.stscPer ?: return null

            // توزيع العينات على الكتل وفق stsc (firstChunk من 1) ومواضعها وفق stco
            val offsets = LongArray(c.sampleCount)
            val szs = IntArray(c.sampleCount)
            var si = 0
            var ptr = 0L
            var chunkNumber = 0 // من 1 كما في stsc
            var entryIdx = 0
            var samplesLeft = 0
            while (si < c.sampleCount) {
                if (samplesLeft == 0) {
                    if (chunkNumber >= ch.size) break
                    chunkNumber++
                    while (entryIdx + 1 < f.size && f[entryIdx + 1] <= chunkNumber) entryIdx++
                    samplesLeft = p[entryIdx]
                    ptr = ch[chunkNumber - 1]
                }
                val size = if (c.uniformSize > 0) c.uniformSize.toInt() else c.sizes?.get(si) ?: return null
                offsets[si] = ptr
                szs[si] = size
                ptr += size
                si++
                samplesLeft--
            }
            return Demux(raf, cfg, offsets, szs, c.sampleCount, c.timescale)
        } catch (_: Throwable) {
            try { raf.close() } catch (_: Exception) { }
            return null
        }
    }
}
