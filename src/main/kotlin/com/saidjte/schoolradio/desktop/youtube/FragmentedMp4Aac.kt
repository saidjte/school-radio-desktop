package com.saidjte.schoolradio.desktop.youtube

import java.io.File
import java.io.RandomAccessFile

/**
 * عيّنات AAC من ملف MP4 مجزّأ (fMP4/CMF) — النقي على JVM بلا أدوات خارجية.
 *
 * لماذا (v0.50.0 — عطل «لم يُفكّ أي إطار صوتي من الملف المُحمَّل»):
 * يوتيوب منذ 2025–2026 يفرض بث SABR وقوائم HLS على قنواته (tv وmweb وweb_safari
 * وبقية سلسلة v0.44.0)، فيصل ملف الصوت «MP4 مجزّأً»: جدول العينات في moov فارغ
 * (stsz/stco بلا مدخلات أو غائبان) والعينات نفسها داخل أجزاء moof+mdat.
 * حاوية JAAD تقرأ جداول العينات الكلاسيكية فقط فترى مسارًا صوتيًا بلا إطارات —
 * وهذا هو العطل المبلَّغ عنه. هذا الديمكس يقرأ الأجزاء: tfhd/trun يحددان موضع
 * كل عينة داخل mdat (نسبيًا لبداية moof مع default-base-is-moof أو مطلقًا مع
 * base-data-offset) فتُسلَّم العينات لفاكّ JAAD نفسه بلا أي تعديل عليه.
 *
 * النطاق المتعمَّد (مخرجات أداة التحميل -f bestaudio):
 * - ملف صوتي أحادي المسار؛ إن ظهرت معرفات مسارات متعددة حُدِّد مسار الصوت من
 *   hdlr (نوع soun) في moov.
 * - أحجام العينات في trun أو افتراضًا من tfhd — ما عدا ذلك يُرفض صادقًا برسالة عربية.
 * - عينة تمتد خارج الملف = نهاية البيانات (تنزيل مبتور) فتتوقف القائمة عندها.
 */
internal object FragmentedMp4Aac {

    /** عينة واحدة: موضعها المطلق في الملف وطولها بالبايتات. */
    data class Sample(val offset: Long, val size: Int)

    /** نتيجة المسح: عينات المسار الصوتي بترتيبها وعدد الأجزاء الذي جاءت منه. */
    class Scan(val samples: List<Sample>, val fragmentCount: Int, val trackId: Int)

    // أعلام tfhd (ISO 14496-12 §8.8.5)
    private const val TFHD_BASE_DATA_OFFSET = 0x000001
    private const val TFHD_DEFAULT_SAMPLE_SIZE = 0x000010
    private const val TFHD_DEFAULT_BASE_IS_MOOF = 0x020000

    // أعلام trun (ISO 14496-12 §8.8.8)
    private const val TRUN_DATA_OFFSET = 0x000001
    private const val TRUN_FIRST_SAMPLE_FLAGS = 0x000004
    private const val TRUN_SAMPLE_DURATION = 0x000100
    private const val TRUN_SAMPLE_SIZE = 0x000200
    private const val TRUN_SAMPLE_FLAGS = 0x000400
    private const val TRUN_SAMPLE_CTS = 0x000800

    /** سقف أمان: أقصى عدد عينات يُجمَع (ساعة صوت ≈ 140 ألفًا). */
    private const val MAX_SAMPLES = 4_000_000

    /** سقف قراءة جسم moof الواحد — ما فوقه بنية شاذة تُرفض صادقًا. */
    private const val MAX_MOOF_BODY = 16 shl 20

    /**
     * مسح الملف: يعيد null إن لم يكن فيه أي جزء moof (ملف كلاسيكي — مسار JAAD
     * يكفيه)، ويرمي IllegalArgumentException برسالة عربية عند بنية أجزاء غير مدعومة،
     * ويعيد [Scan] — بعينات ربما فارغة إن كانت الأجزاء لمسار آخر أو تالفة.
     */
    fun scan(file: File): Scan? {
        RandomAccessFile(file, "r").use { raf ->
            val len = file.length()
            val samples = ArrayList<Sample>()
            var fragmentCount = 0
            var trackId = -1
            var seenOtherTrack = false

            var pos = 0L
            while (pos + 8 <= len) {
                raf.seek(pos)
                val head = readInt(raf) ?: break
                val type = readType(raf) ?: break
                var headerLen = 8L
                var size = head.toLong() and 0xFFFFFFFFL
                if (size == 1L) {
                    val big = readLong(raf) ?: break
                    size = big
                    headerLen = 16L
                } else if (size == 0L) {
                    size = len - pos
                }
                if (size < headerLen || pos + size > len) break // ترويسة تالفة: ننتهي بهدوء

                if (type == "moof") {
                    fragmentCount++
                    parseFragment(raf, pos + headerLen, size - headerLen, len) { id, list ->
                        if (trackId == -1) trackId = id
                        if (id != trackId) seenOtherTrack = true
                        for (s in list) {
                            if (samples.size >= MAX_SAMPLES) {
                                throw IllegalArgumentException("عدد العينات في الأجزاء يتجاوز الحد المعقول (${MAX_SAMPLES}).")
                            }
                            samples.add(s)
                        }
                    }
                }
                pos += size
            }

            if (fragmentCount == 0) return null
            if (!seenOtherTrack) return Scan(samples, fragmentCount, trackId)

            // مسارات متعددة: حُدِّد مسار الصوت من hdlr في moov وأعد التجميع له وحده
            val audioId = findAudioTrackId(raf, len)
                ?: throw IllegalArgumentException("الأجزاء تخص عدة مسارات ولا يظهر مسار صوتي (soun) في moov.")
            val audio = ArrayList<Sample>()
            var fragments = 0
            var pos2 = 0L
            while (pos2 + 8 <= len) {
                raf.seek(pos2)
                val head = readInt(raf) ?: break
                val type = readType(raf) ?: break
                var headerLen = 8L
                var size = head.toLong() and 0xFFFFFFFFL
                if (size == 1L) {
                    val big = readLong(raf) ?: break
                    size = big; headerLen = 16L
                } else if (size == 0L) {
                    size = len - pos2
                }
                if (size < headerLen || pos2 + size > len) break
                if (type == "moof") {
                    fragments++
                    parseFragment(raf, pos2 + headerLen, size - headerLen, len) { id, list ->
                        if (id == audioId) {
                            for (s in list) {
                                if (audio.size >= MAX_SAMPLES) {
                                    throw IllegalArgumentException("عدد العينات في الأجزاء يتجاوز الحد المعقول (${MAX_SAMPLES}).")
                                }
                                audio.add(s)
                            }
                        }
                    }
                }
                pos2 += size
            }
            return Scan(audio, fragments, audioId)
        }
    }

    /**
     * فك جزء moof واحد: يمشي mfhd/traf ثم tfhd وtrun ويحسب موضع كل عينة مطلقًا.
     * القاعدة: القاعدة (base) إما base_data_offset مطلقًا أو بداية moof مع
     * default-base-is-moof؛ data_offset في trun يُضاف إليها، وتوالٍ truns داخل
     * traf نفسه يكمل من حيث انتهى السابق.
     */
    private fun parseFragment(
        raf: RandomAccessFile,
        bodyStart: Long,
        bodyLen: Long,
        fileLen: Long,
        onTrack: (Int, List<Sample>) -> Unit
    ) {
        if (bodyLen > MAX_MOOF_BODY) {
            throw IllegalArgumentException("جزء moof بحجم غير معقول ($bodyLen بايت) — الملف تالف.")
        }
        val buf = ByteArray(bodyLen.toInt())
        raf.seek(bodyStart)
        raf.readFully(buf)
        val moofStart = bodyStart - 8

        var off = 0
        while (off + 8 <= buf.size) {
            val size = readInt(buf, off)
            val type = readType(buf, off + 4)
            var headerLen = 8
            var boxLen = size.toLong() and 0xFFFFFFFFL
            if (boxLen == 1L) {
                boxLen = readLong(buf, off + 8)
                headerLen = 16
            } else if (boxLen == 0L) {
                boxLen = (buf.size - off).toLong()
            }
            if (boxLen < headerLen || off + boxLen > buf.size) break

            if (type == "traf") {
                parseTraf(buf, off + headerLen, (boxLen - headerLen).toInt(), moofStart, fileLen, onTrack)
            }
            off += boxLen.toInt()
        }
    }

    private fun parseTraf(
        buf: ByteArray,
        start: Int,
        len: Int,
        moofStart: Long,
        fileLen: Long,
        onTrack: (Int, List<Sample>) -> Unit
    ) {
        var trackId = -1
        var flags = 0
        var baseDataOffset = -1L
        var defaultSampleSize = 0

        var off = start
        val end = start + len
        var cursor = -1L // موضع العينة القادمة داخل هذا traf

        while (off + 8 <= end) {
            val size = readInt(buf, off)
            val type = readType(buf, off + 4)
            var headerLen = 8
            var boxLen = size.toLong() and 0xFFFFFFFFL
            if (boxLen == 1L) {
                boxLen = readLong(buf, off + 8)
                headerLen = 16
            } else if (boxLen == 0L) {
                boxLen = (end - off).toLong()
            }
            if (boxLen < headerLen || off + boxLen > end) break

            if (type == "tfhd") {
                var p = off + headerLen
                flags = readInt(buf, p)
                p += 4
                trackId = readInt(buf, p)
                p += 4
                if (flags and TFHD_BASE_DATA_OFFSET != 0) {
                    baseDataOffset = readLong(buf, p)
                    p += 8
                }
                if (flags and TFHD_DEFAULT_SAMPLE_SIZE != 0) {
                    defaultSampleSize = readInt(buf, p)
                }
            } else if (type == "trun" && trackId != -1) {
                var p = off + headerLen
                val tflags = readInt(buf, p)
                p += 4
                val count = readInt(buf, p)
                p += 4
                var dataOffset = 0L
                if (tflags and TRUN_DATA_OFFSET != 0) {
                    dataOffset = readInt(buf, p).toLong()
                    p += 4
                }
                if (tflags and TRUN_FIRST_SAMPLE_FLAGS != 0) p += 4

                val base = if (baseDataOffset >= 0) baseDataOffset
                else if (flags and TFHD_DEFAULT_BASE_IS_MOOF != 0) moofStart
                else -1L
                if (base < 0) {
                    throw IllegalArgumentException(
                        "أجزاء بلا قاعدة مواضع (لا base-data-offset ولا default-base-is-moof) — بنية غير مدعومة."
                    )
                }
                if (cursor < 0) cursor = base + dataOffset

                val out = ArrayList<Sample>(count)
                for (i in 0 until count) {
                    // ترتيب الحقول لكل عينة: مدة ثم حجم ثم أعلام ثم CTS (ISO 14496-12 §8.8.8)
                    if (tflags and TRUN_SAMPLE_DURATION != 0) p += 4
                    val sz: Int = if (tflags and TRUN_SAMPLE_SIZE != 0) {
                        val s = readInt(buf, p)
                        p += 4
                        s
                    } else {
                        defaultSampleSize
                    }
                    if (tflags and TRUN_SAMPLE_FLAGS != 0) p += 4
                    if (tflags and TRUN_SAMPLE_CTS != 0) p += 4
                    if (sz <= 0) {
                        throw IllegalArgumentException("عينة بحجم صفر في الأجزاء — الملف تالف.")
                    }
                    if (cursor + sz > fileLen) break // تنزيل مبتور: ننتهي عند آخر عينة كاملة
                    out.add(Sample(cursor, sz))
                    cursor += sz
                    if (samplesCountLimitHit(out.size)) break
                }
                if (out.isNotEmpty()) onTrack(trackId, out)
            }
            off += boxLen.toInt()
        }
    }

    private fun samplesCountLimitHit(n: Int): Boolean = n >= MAX_SAMPLES

    /** معرف مسار الصوت من moov: أول trak يحمل hdlr بنوع soun — أو null إن لم يظهر. */
    private fun findAudioTrackId(raf: RandomAccessFile, len: Long): Int? {
        var pos = 0L
        while (pos + 8 <= len) {
            raf.seek(pos)
            val head = readInt(raf) ?: return null
            val type = readType(raf) ?: return null
            var headerLen = 8L
            var size = head.toLong() and 0xFFFFFFFFL
            if (size == 1L) {
                size = readLong(raf) ?: return null
                headerLen = 16L
            } else if (size == 0L) {
                size = len - pos
            }
            if (size < headerLen || pos + size > len) return null
            if (type == "moov") {
                val id = scanMoovForAudio(raf, pos + headerLen, size - headerLen)
                if (id != null) return id
            }
            pos += size
        }
        return null
    }

    private fun scanMoovForAudio(raf: RandomAccessFile, start: Long, len: Long): Int? {
        if (len > MAX_MOOF_BODY) return null
        val buf = ByteArray(len.toInt())
        raf.seek(start)
        raf.readFully(buf)
        // trak واحد في كل مرة
        var off = 0
        while (off + 8 <= buf.size) {
            val size = readInt(buf, off)
            val type = readType(buf, off + 4)
            var boxLen = size.toLong() and 0xFFFFFFFFL
            var headerLen = 8
            if (boxLen == 1L) {
                boxLen = readLong(buf, off + 8); headerLen = 16
            } else if (boxLen == 0L) {
                boxLen = (buf.size - off).toLong()
            }
            if (boxLen < headerLen || off + boxLen > buf.size) break
            if (type == "trak") {
                val id = trakAudioId(buf, off + headerLen, (boxLen - headerLen).toInt())
                if (id != null) return id
            }
            off += boxLen.toInt()
        }
        return null
    }

    /** داخل trak واحد: hdlr يجب أن يكون soun، وtkhd يعطي معرف المسار. */
    private fun trakAudioId(buf: ByteArray, start: Int, len: Int): Int? {
        var handler: String? = null
        var trackId: Int? = null
        var off = start
        val end = start + len
        while (off + 8 <= end) {
            val size = readInt(buf, off)
            val type = readType(buf, off + 4)
            var boxLen = size.toLong() and 0xFFFFFFFFL
            var headerLen = 8
            if (boxLen == 1L) {
                boxLen = readLong(buf, off + 8); headerLen = 16
            } else if (boxLen == 0L) {
                boxLen = (end - off).toLong()
            }
            if (boxLen < headerLen || off + boxLen > end) break
            val bodyStart = off + headerLen
            when (type) {
                "hdlr" -> if (bodyStart + 8 + 4 <= off + boxLen) {
                    handler = readType(buf, bodyStart + 8) // pre_defined(4) ثم handler_type(4)
                }
                "tkhd" -> {
                    val version = buf[bodyStart].toInt() and 0xFF
                    val idPos = bodyStart + 4 + if (version == 1) 8 + 8 else 4 + 4
                    if (idPos + 4 <= off + boxLen) trackId = readInt(buf, idPos)
                }
                "trak" -> { /* متداخل غير متوقع */ }
            }
            off += boxLen.toInt()
        }
        return if (handler == "soun") trackId else null
    }

    /**
     * نوع الصوت من AudioSpecificConfig — لتسمية الصيغة في الرسائل الصادقة:
     * 2 = AAC-LC، 5 = HE-AAC (SBR)، 29 = HE-AAC v2 (PS).
     */
    fun audioObjectType(asc: ByteArray): Int {
        if (asc.isEmpty()) return 0
        val b0 = asc[0].toInt() and 0xFF
        val aot = b0 shr 3
        if (aot != 31 || asc.size < 2) return aot
        val b1 = asc[1].toInt() and 0xFF
        return 32 + ((b0 and 0x07) shl 3) + (b1 shr 5)
    }

    /** اسم عربي مختصر لمعرف نوع الصوت — للرسائل الصادقة. */
    fun audioProfileName(aot: Int): String = when (aot) {
        2 -> "AAC-LC"
        5 -> "HE-AAC (SBR)"
        29 -> "HE-AAC v2 (PS)"
        0 -> "غير معروف"
        else -> "AAC نوع $aot"
    }

    // ================= قراءة صغيرة من RandomAccessFile أو مصفوفة =================

    private fun readInt(raf: RandomAccessFile): Int? = try {
        raf.readInt()
    } catch (_: Exception) {
        null
    }

    private fun readLong(raf: RandomAccessFile): Long? = try {
        raf.readLong()
    } catch (_: Exception) {
        null
    }

    private fun readType(raf: RandomAccessFile): String? = try {
        val b = ByteArray(4)
        raf.readFully(b)
        String(b, Charsets.ISO_8859_1)
    } catch (_: Exception) {
        null
    }

    private fun readInt(buf: ByteArray, off: Int): Int =
        ((buf[off].toInt() and 0xFF) shl 24) or ((buf[off + 1].toInt() and 0xFF) shl 16) or
                ((buf[off + 2].toInt() and 0xFF) shl 8) or (buf[off + 3].toInt() and 0xFF)

    private fun readLong(buf: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (buf[off + i].toLong() and 0xFF)
        return v
    }

    private fun readType(buf: ByteArray, off: Int): String =
        String(buf, off, 4, Charsets.ISO_8859_1)
}
