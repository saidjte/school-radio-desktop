package com.saidjte.schoolradio.desktop.audio

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * مُورِّد المجرات — يعيد الوسيط التالي في سلسلة البث: للوسيط المباشر مجرى واحد،
 * ولقوائم HLS مقطع التهيئة (init) ثم أجزاء الوسائط بالترتيب. null = النهاية.
 */
internal fun interface StreamSupply {
    fun next(): InputStream?
}

/**
 * ديمكس MP4 مجزّأ تدفقيًا (v0.51.0 «تشغيل مباشر من يوتيوب») — نسخة المجرى من
 * [com.saidjte.schoolradio.desktop.youtube.FragmentedMp4Aac] الملفي:
 *
 * يوتيوب يسلّم صيغة 140 (mp4a) اليوم بثًّا متتابعًا — ftyp ثم moov (بجداول
 * عينات فارغة) ثم أزواج moof+mdat، سواء عبر وسيط واحد مباشر أو أجزاء HLS.
 * هذا الديمكس يقرأ الصناديق بالترتيب دون وصول عشوائي: moov يُحلَّل في البداية
 * (مسار الصوت soun ومعرف مساره ووصف AAC من esds ومقياس الزمن)، ثم كل moof
 * تُستخرج عيناتها من tfhd/trun بذات قواعد v0.50 المُثبَتة (القاعدة إما
 * base-data-offset مطلق داخل المجرى أو بداية moof مع default-base-is-moof)،
 * وكل mdat تُقصّ منه العينات المعلّقة بتحويل الموضع المطلق داخل الجزء الحالي
 * إلى إزاحة داخل جسم mdat. نهاية المجرى تنقلنا للجزء التالي من المُورِّد.
 *
 * القيود المتعمَّدة (برسائل صادقة): بنية بلا moov في الرأس، بلا مسار soun،
 * بلا esds، أجزاء بلا قاعدة مواضع، وتشفير HLS (يرفضه HttpAudioSource قبل هنا).
 */
internal class Fmp4StreamDemux(private val supply: StreamSupply) {

    /** تهيئة المسار الصوتي المستخرجة من moov. */
    class Config(
        val trackId: Int,
        /** AudioSpecificConfig — ما يأخذه فاكّ jaad مباشرة. */
        val asc: ByteArray,
        /** مقياس زمن الوسائط في mdhd (للتشخيص؛ الموضع يُحتسب من PCM المفكوك). */
        val timescale: Int
    )

    var config: Config? = null
        private set

    private var cur: InputStream? = null
    /** موضع القراءة داخل الجزء الحالي — القواعد المطلقة في tfhd/trun نسبية له. */
    private var absPos = 0L

    // عينات moof المعلّقة بانتظار mdat التالي (موضعها مطلق داخل الجزء الحالي)
    private class Pending(val offset: Long, val size: Int)
    private val pending = ArrayDeque<Pending>()
    private val ready = ArrayDeque<ByteArray>()

    // أعلام tfhd (ISO 14496-12 §8.8.5) — كما في FragmentedMp4Aac
    private val TFHD_BASE_DATA_OFFSET = 0x000001
    private val TFHD_SAMPLE_DESCRIPTION_INDEX = 0x000002
    private val TFHD_DEFAULT_SAMPLE_DURATION = 0x000008
    private val TFHD_DEFAULT_SAMPLE_SIZE = 0x000010
    private val TFHD_DEFAULT_BASE_IS_MOOF = 0x020000

    // أعلام trun (§8.8.8)
    private val TRUN_DATA_OFFSET = 0x000001
    private val TRUN_FIRST_SAMPLE_FLAGS = 0x000004
    private val TRUN_SAMPLE_DURATION = 0x000100
    private val TRUN_SAMPLE_SIZE = 0x000200
    private val TRUN_SAMPLE_FLAGS = 0x000400
    private val TRUN_SAMPLE_CTS = 0x000800

    private val head = ByteArray(16)

    // ================= التهيئة: moov في رأس البث =================

    /**
     * قراءة التهيئة: تمشي الصناديق (عبر الأجزاء عند الحاجة) حتى أول moov وتحلّله.
     * تُستدعى مرة واحدة قبل nextSample — أثناء البث الحي وليس بعده.
     */
    fun readInit(): Config {
        if (config != null) return config!!
        var moovBuf: ByteArray? = null
        while (true) {
            if (cur == null && !advanceSegment()) {
                throw IOException("لم تصل أي بيانات من خادم البث — تحقق من الشبكة وأعد «تشغيل مباشر».")
            }
            val (size, type, headerLen) = readBoxHead() ?: throw IOException(
                "انقطع البث قبل رأس MP4 (moov) — الاتصال ضعيف أو الرابط منتهٍ؛ أعد المحاولة."
            )
            if (type == TYPE_MOOV) {
                moovBuf = readExact((size - headerLen).toInt())
                break
            }
            skipBody(size - headerLen)
        }
        val cfg = parseMoov(moovBuf!!)
            ?: throw IOException("بث MP4 بلا مسار صوتي AAC مقروء (لا soun ولا esds) — استخدم زر التحميل بدلًا من التشغيل المباشر.")
        config = cfg
        return cfg
    }

    /**
     * العينة التالية من المسار الصوتي — null عند نهاية البث.
     * ترتيب الإطلاق: الجاهزة سلفًا، وإلا يمشي الصناديق: moof يجمّع مواضع
     * العينات وmdat يقصّها، والبقية تُتخطى، ونهاية الجزء تنقلنا للتالي.
     */
    fun nextSample(): ByteArray? {
        while (true) {
            ready.removeFirstOrNull()?.let { return it }

            val (size, type, headerLen) = readBoxHead() ?: return null // نهاية البث النظيفة
            when (type) {
                TYPE_MOOF -> {
                    val body = readExact((size - headerLen).toInt())
                    parseMoof(body, headerLen)
                }
                TYPE_MDAT -> {
                    val mdatStart = absPos
                    val body = readExact((size - headerLen).toInt())
                    drainPendingInto(body, mdatStart)
                }
                else -> skipBody(size - headerLen)
            }
        }
    }

    fun close() {
        try { cur?.close() } catch (_: Exception) { }
        cur = null
        pending.clear()
        ready.clear()
    }

    // ================= قراءة الأجزاء =================

    /** الانتقال للجزء التالي من المُورِّد — العينات المعلّقة الباقية تُنسخ (جزء تالف). */
    private fun advanceSegment(): Boolean {
        if (cur != null) {
            try { cur!!.close() } catch (_: Exception) { }
            cur = null
        }
        if (pending.isNotEmpty()) pending.clear() // moof بلا mdat خاصته = جزء ناقص
        val next = try { supply.next() } catch (t: IOException) { throw t }
        ?: return false
        cur = next
        absPos = 0L
        return true
    }

    /**
     * ترويسة صندوق من الموضع الحالي: [الحجم 4][النوع 4] وحجم 1 يعني لارغسايز
     * 64-bit. نهاية الجزء الحالي عند حد صندوق ننقلنا للجزء التالي تلقائيًا —
     * الأجزاء لا تتقاطع صناديقها، وما عدا ذلك (قطع داخل صندوق) بث مبتور.
     */
    private fun readBoxHead(): Triple<Long, Int, Long>? {
        while (true) {
            if (cur == null && !advanceSegment()) return null
            val h = readExactOrEof(8)
            if (h == null) {
                if (!advanceSegment()) return null // نهاية البث النظيفة بلا أجزاء أخرى
                continue
            }
            var size = ((h[0].toLong() and 0xFF) shl 24) or ((h[1].toLong() and 0xFF) shl 16) or
                    ((h[2].toLong() and 0xFF) shl 8) or (h[3].toLong() and 0xFF)
            val t = ((h[4].toInt() and 0xFF) shl 24) or ((h[5].toInt() and 0xFF) shl 16) or
                    ((h[6].toInt() and 0xFF) shl 8) or (h[7].toInt() and 0xFF)
            var headerLen = 8L
            if (size == 1L) {
                val ext = readExact(8)
                size = 0
                for (b in ext) size = (size shl 8) or (b.toLong() and 0xFF)
                headerLen = 16L
            }
            if (size < headerLen) throw IOException("صندوق بحجم غير معقول في البث — البيانات تالفة؛ أعد المحاولة.")
            return Triple(size, t, headerLen)
        }
    }

    private fun skipBody(bytes: Long) {
        var toSkip = bytes.coerceAtLeast(0L)
        val buf = ByteArray(16 * 1024)
        while (toSkip > 0) {
            val n = cur!!.read(buf, 0, minOf(toSkip, buf.size.toLong()).toInt())
            if (n < 0) throw EOFException("انقطع البث داخل صندوق — الرابط منتهٍ أو الشبكة ضعيفة.")
            absPos += n
            toSkip -= n
        }
    }

    /** قراءة قاطعة لعدد بايتات من الجزء الحالي — EOF وسط الصندوق يعني بثًا مبتورًا. */
    private fun readExact(n: Int): ByteArray {
        val out = ByteArray(n)
        var filled = 0
        while (filled < n) {
            val r = cur!!.read(out, filled, n - filled)
            if (r < 0) throw EOFException("انقطع البث داخل الصندوق — تنزيل مبتور أو شبكة ضعيفة.")
            filled += r
            absPos += r
        }
        return out
    }

    /** مثل readExact لكن نهاية المجرى عند الترويسة نفسها تعيد null (نهاية البث)، والإعطاب الشبكي يُرمى. */
    private fun readExactOrEof(n: Int): ByteArray? {
        val out = ByteArray(n)
        var filled = 0
        while (filled < n) {
            val r = cur!!.read(out, filled, n - filled)
            if (r < 0) return null // نهاية البث النظيفة
            filled += r
            absPos += r
        }
        return out
    }

    // ================= moof: تجميع مواضع العينات =================

    /**
     * تحليل جسم moof — ذوات قواعد FragmentedMp4Aac نفسها لكن المواضع مطلقة
     * داخل الجزء الحالي (نفس مجرى mdat القادم) فلا حاجة لتحويل ملفي.
     */
    private fun parseMoof(body: ByteArray, headerLen: Long) {
        if (body.size > (16 shl 20)) {
            throw IOException("جزء moof بحجم غير معقول (${body.size} بايت) — البيانات تالفة.")
        }
        val moofStart = absPos - body.size - headerLen
        walkBoxes(body) { b ->
            if (b.type == TYPE_TRAF) parseTraf(body, b.bodyStart, b.bodyEnd, moofStart)
        }
    }

    private fun parseTraf(buf: ByteArray, start: Int, end: Int, moofStart: Long) {
        val trackId = config?.trackId ?: return
        var tfhdFlags = 0
        var trafTrackId = -1
        var baseDataOffset = -1L
        var defaultSampleSize = 0
        var cursor = -1L

        walkBoxes(buf, start, end) { b ->
            when (b.type) {
                TYPE_TFHD -> {
                    var p = b.bodyStart
                    tfhdFlags = be32(buf, p); p += 4
                    trafTrackId = be32(buf, p); p += 4
                    if (tfhdFlags and TFHD_BASE_DATA_OFFSET != 0) {
                        baseDataOffset = be64(buf, p); p += 8
                    }
                    if (tfhdFlags and TFHD_SAMPLE_DESCRIPTION_INDEX != 0) p += 4 // sample_description_index
                    if (tfhdFlags and TFHD_DEFAULT_SAMPLE_DURATION != 0) p += 4 // default_sample_duration
                    if (tfhdFlags and TFHD_DEFAULT_SAMPLE_SIZE != 0) {
                        defaultSampleSize = be32(buf, p); p += 4
                    }
                    // default_sample_flags يُتجاوز صمتًا — لا يلزمنا
                }
                TYPE_TRUN -> {
                    if (trafTrackId != trackId) return@walkBoxes
                    var p = b.bodyStart
                    val tflags = be32(buf, p); p += 4
                    val count = be32(buf, p); p += 4
                    var dataOffset = 0L
                    if (tflags and TRUN_DATA_OFFSET != 0) {
                        dataOffset = be32(buf, p).toLong(); p += 4
                    }
                    if (tflags and TRUN_FIRST_SAMPLE_FLAGS != 0) p += 4

                    val base = when {
                        baseDataOffset >= 0 -> baseDataOffset
                        tfhdFlags and TFHD_DEFAULT_BASE_IS_MOOF != 0 -> moofStart
                        else -> -1L
                    }
                    if (base < 0) {
                        throw IOException(
                            "أجزاء بث بلا قاعدة مواضع (لا base-data-offset ولا default-base-is-moof) — بنية غير مدعومة."
                        )
                    }
                    if (cursor < 0) cursor = base + dataOffset

                    for (i in 0 until count) {
                        // ترتيب حقول العينة مقدس: مدة ثم حجم ثم أعلام ثم CTS (§8.8.8)
                        if (tflags and TRUN_SAMPLE_DURATION != 0) p += 4
                        val sz = if (tflags and TRUN_SAMPLE_SIZE != 0) {
                            val s = be32(buf, p); p += 4; s
                        } else defaultSampleSize
                        if (tflags and TRUN_SAMPLE_FLAGS != 0) p += 4
                        if (tflags and TRUN_SAMPLE_CTS != 0) p += 4
                        if (sz <= 0) throw IOException("عينة بحجم صفر في أجزاء البث — البيانات تالفة.")
                        if (pending.size >= 200_000) {
                            throw IOException("عدد عينات الأجزاء يتجاوز الحد المعقول — بث شاذّ.")
                        }
                        pending.addLast(Pending(cursor, sz))
                        cursor += sz
                    }
                }
            }
        }
    }

    /** قصّ العينات المعلّقة من جسم mdat وفق مواضعها المطلقة داخل الجزء. */
    private fun drainPendingInto(body: ByteArray, mdatStart: Long) {
        while (pending.isNotEmpty()) {
            val p = pending.first()
            val rel = p.offset - mdatStart
            when {
                rel >= 0 && rel + p.size <= body.size.toLong() -> {
                    val s = body.copyOfRange(rel.toInt(), (rel + p.size).toInt())
                    pending.removeFirst()
                    if (ready.size >= 200_000) {
                        throw IOException("تراكم عينات فوق الحد المعقول — المستهلك متوقف؟")
                    }
                    ready.addLast(s)
                }
                rel >= body.size.toLong() -> return // تبقى لأجزاء mdat قادمة (بنية متعددة)
                else -> {
                    // موضع يسبق هذا mdat: عينة فُقد mdat خاصتها — تُسقَط صادقًا
                    pending.removeFirst()
                }
            }
        }
    }

    // ================= moov: تهيئة المسار الصوتي =================

    /**
     * تحليل moov كاملًا (مخزَّن بذاكرة): بنية MP4 المعيارية — trak يحتوي tkhd
     * (معرف المسار) وmdia التي تحتوي hdlr (نوع soun) وmdhd (مقياس الزمن)
     * وminf→stbl→stsd (وصف فاكّ AAC من esds). أول trak يحمل hdlr بنوع soun
     * هو مسار الصوت. بديل المخزَّن لـMp4AacDemux الملفي (هناك RandomAccessFile
     * وهنا بايتات حية).
     */
    private fun parseMoov(moov: ByteArray): Config? {
        var chosenTrackId = -1
        var chosenTimescale = -1
        var chosenAsc: ByteArray? = null

        walkBoxes(moov) { trak ->
            if (trak.type != TYPE_TRAK || chosenAsc != null) return@walkBoxes
            var trackId = -1
            var handler: String? = null
            var timescale = -1
            var asc: ByteArray? = null

            walkBoxes(moov, trak.bodyStart, trak.bodyEnd) { child ->
                when (child.type) {
                    TYPE_TKHD -> {
                        val v = moov[child.bodyStart].toInt() and 0xFF
                        val idPos = child.bodyStart + 4 + if (v == 1) 8 + 8 else 4 + 4
                        if (idPos + 4 <= child.bodyEnd) trackId = be32(moov, idPos)
                    }
                    TYPE_MDIA -> walkBoxes(moov, child.bodyStart, child.bodyEnd) { md ->
                        when (md.type) {
                            TYPE_HDLR -> {
                                // pre_defined(4) ثم handler_type(4) — كما في FragmentedMp4Aac
                                if (md.bodyStart + 8 + 4 <= md.bodyEnd) {
                                    handler = readTypeAt(moov, md.bodyStart + 8)
                                }
                            }
                            TYPE_MDHD -> {
                                val v = moov[md.bodyStart].toInt() and 0xFF
                                val skip = if (v == 1) 16 else 8
                                val tsPos = md.bodyStart + 4 + skip
                                if (tsPos + 4 <= md.bodyEnd) timescale = be32(moov, tsPos)
                            }
                            TYPE_MINF -> walkBoxes(moov, md.bodyStart, md.bodyEnd) { mi ->
                                if (mi.type == TYPE_STBL) {
                                    walkBoxes(moov, mi.bodyStart, mi.bodyEnd) { st ->
                                        if (st.type == TYPE_STSD && asc == null) {
                                            asc = parseStsd(moov, st.bodyStart, st.bodyEnd)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (handler == "soun" && trackId > 0 && asc != null) {
                chosenTrackId = trackId
                chosenTimescale = timescale
                chosenAsc = asc
            }
        }

        // احتياط: بلا hdlr واضح — أول مسار يحمل esds صالحًا (سلوك Mp4AacDemux)
        if (chosenAsc == null) {
            walkBoxes(moov) { trak ->
                if (trak.type != TYPE_TRAK || chosenAsc != null) return@walkBoxes
                var trackId = -1
                var asc: ByteArray? = null
                walkBoxes(moov, trak.bodyStart, trak.bodyEnd) { child ->
                    if (child.type == TYPE_TKHD && trackId < 0) {
                        val v = moov[child.bodyStart].toInt() and 0xFF
                        val idPos = child.bodyStart + 4 + if (v == 1) 8 + 8 else 4 + 4
                        if (idPos + 4 <= child.bodyEnd) trackId = be32(moov, idPos)
                    }
                    if (child.type == TYPE_MDIA && asc == null) {
                        walkBoxes(moov, child.bodyStart, child.bodyEnd) { md ->
                            if (md.type == TYPE_MINF && asc == null) {
                                walkBoxes(moov, md.bodyStart, md.bodyEnd) { mi ->
                                    if (mi.type == TYPE_STBL && asc == null) {
                                        walkBoxes(moov, mi.bodyStart, mi.bodyEnd) { st ->
                                            if (st.type == TYPE_STSD && asc == null) {
                                                asc = parseStsd(moov, st.bodyStart, st.bodyEnd)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (asc != null && trackId > 0) {
                    chosenTrackId = trackId
                    chosenAsc = asc
                }
            }
        }

        val a = chosenAsc ?: return null
        return Config(chosenTrackId.coerceAtLeast(1), a, chosenTimescale)
    }

    /**
     * stsd: نسخة وأعلام ثم عدد مداخل ثم مداخل ذاتية الحجم — مدخل mp4a يحمل
     * esds وبداخله AudioSpecificConfig (عقد v0.49.0 المُثبَت).
     */
    private fun parseStsd(buf: ByteArray, start: Int, end: Int): ByteArray? {
        var p = start + 8 // نسخة وأعلام + عدد المداخل
        while (p + 8 <= end) {
            val entry = firstBox(buf, p, end) ?: break
            if (entry.type == TYPE_MP4A) {
                // الحقول الثابتة لمدخل mp4a حتى الأبناء: reserved(6)+dataRefIndex(2)
                // +version(2)+revision(2)+vendor(4)+channels(2)+sampleSize(2)+
                // preDefined(2)+reserved(2)+rate(4) = 28 بايتة بعد بدء الجسم
                var q = entry.bodyStart + 28
                while (q + 8 <= entry.bodyEnd) {
                    val sub = firstBox(buf, q, entry.bodyEnd) ?: break
                    if (sub.type == TYPE_ESDS) {
                        // esds صندوق كامل: نسخة وأعلام (4 بايتات) ثم الواصفات — كما في Mp4AacDemux
                        return parseEsds(buf, sub.bodyStart + 4, sub.bodyEnd)
                    }
                    q = sub.end
                }
            }
            p = entry.end
        }
        return null
    }

    /**
     * esds: ES_Descriptor(0x03) → DecoderConfigDescriptor(0x04) →
     * DecSpecificInfo(0x05) — نفس منطق Mp4AacDemux.parseEsds على مخزن.
     */
    private fun parseEsds(buf: ByteArray, start: Int, end: Int): ByteArray? {
        var p = start
        fun varLen(): Int {
            var v = 0
            for (i in 0 until 4) {
                if (p >= end) return -1
                val b = buf[p].toInt() and 0xFF
                p++
                v = (v shl 7) or (b and 0x7F)
                if (b and 0x80 == 0) break
            }
            return v
        }
        if (p >= end || (buf[p].toInt() and 0xFF) != 0x03) return null
        p++
        varLen()
        p += 2 // ES_ID
        if (p >= end) return null
        val f = buf[p].toInt() and 0xFF
        p++
        if (f and 0x80 != 0) p += 2
        if (f and 0x40 != 0) {
            if (p >= end) return null
            val l = buf[p].toInt() and 0xFF
            p++
            p += l
        }
        if (f and 0x20 != 0) p += 2
        if (p >= end || (buf[p].toInt() and 0xFF) != 0x04) return null
        p++
        varLen()
        p += 13 // objectType+streamType+bufferSize+max+avg
        if (p >= end || (buf[p].toInt() and 0xFF) != 0x05) return null
        p++
        val n = varLen()
        if (n <= 0 || n > 64 || p + n > end) return null
        return buf.copyOfRange(p, p + n)
    }

    // ================= أدوات المشي في الصناديق =================

    private class Box(val type: Int, val bodyStart: Int, val bodyEnd: Int, val end: Int)

    private fun firstBox(buf: ByteArray, start: Int, end: Int): Box? {
        if (start + 8 > end) return null
        var size = be32(buf, start).toLong() and 0xFFFFFFFFL
        val type = be32(buf, start + 4)
        var headerLen = 8
        if (size == 1L) {
            if (start + 16 > end) return null
            size = be64(buf, start + 8)
            headerLen = 16
        } else if (size == 0L) {
            size = (end - start).toLong()
        }
        if (size < headerLen || start + size > end) return null
        val bodyStart = start + headerLen
        val boxEnd = start + size.toInt()
        return Box(type, bodyStart, boxEnd, boxEnd)
    }

    private inline fun walkBoxes(buf: ByteArray, start: Int, end: Int, visit: (Box) -> Unit) {
        var p = start
        while (p + 8 <= end) {
            val b = firstBox(buf, p, end) ?: break
            visit(b)
            p = b.end
        }
    }

    private inline fun walkBoxes(buf: ByteArray, visit: (Box) -> Unit) = walkBoxes(buf, 0, buf.size, visit)

    private fun readTypeAt(buf: ByteArray, off: Int): String =
        String(buf, off, 4, Charsets.ISO_8859_1)

    private fun be32(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun be64(b: ByteArray, off: Int): Long {
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[off + i].toLong() and 0xFF)
        return v
    }

    companion object {
        private const val TYPE_MOOV = 0x6D6F6F76 // moov
        private const val TYPE_MOOF = 0x6D6F6F66 // moof
        private const val TYPE_MDAT = 0x6D646174 // mdat
        private const val TYPE_TRAF = 0x74726166 // traf
        private const val TYPE_TFHD = 0x74666864 // tfhd
        private const val TYPE_TRUN = 0x7472756E // trun
        private const val TYPE_TRAK = 0x7472616B // trak
        private const val TYPE_HDLR = 0x68646C72 // hdlr
        private const val TYPE_TKHD = 0x746B6864 // tkhd
        private const val TYPE_MDIA = 0x6D646961 // mdia
        private const val TYPE_MDHD = 0x6D646864 // mdhd
        private const val TYPE_MINF = 0x6D696E66 // minf
        private const val TYPE_STBL = 0x7374626C // stbl
        private const val TYPE_STSD = 0x73747364 // stsd
        private const val TYPE_MP4A = 0x6D703461 // mp4a
        private const val TYPE_ESDS = 0x65736473 // esds
    }
}
