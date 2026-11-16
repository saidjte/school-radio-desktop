package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.Fmp4StreamDemux
import com.saidjte.schoolradio.desktop.audio.HttpAudioSource
import com.saidjte.schoolradio.desktop.audio.MusicChannel
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.PcmOutLine
import com.saidjte.schoolradio.desktop.audio.StreamPipelineFactory
import com.saidjte.schoolradio.desktop.audio.StreamRef
import com.saidjte.schoolradio.desktop.audio.StreamSpec
import com.saidjte.schoolradio.desktop.audio.TrackReader
import com.saidjte.schoolradio.desktop.youtube.FragmentedMp4Aac
import com.saidjte.schoolradio.desktop.youtube.YouTubeJson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import net.sourceforge.jaad.aac.Decoder as JaadDecoder
import net.sourceforge.jaad.aac.SampleBuffer as JaadSampleBuffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * عقد البث المباشر من يوتيوب (v0.51.0 «تشغيل مباشر دون تحميل» — نظير نسخة الهاتف):
 *
 * الشبكة الحقيقية تعذّر من بيئة الاختبار (IP مركز بيانات يفرض حاجز تحقق البوت —
 * مُثبَت في worklog v0.50.0)، فكل عقد البث يُبنى على خادم HTTP محلي يخدم عيّنات
 * حقيقية: نغمة fMP4 المجزّأة (v0.50.0) مقسّمة قسّمَي تهيئة وأجزاء كقوائم HLS،
 * وMP3 حقيقي. العقد الأربعة الكبرى:
 * 1) اختيار مصدر البث من JSON الأداة — يرفض opus/webm صادقًا ويختار الأفضل.
 * 2) الدِيمكس التدفقي يعطي عينات مطابقة بايتًا ببايت للديمكس الملفي (v0.50.0).
 * 3) خط البث عبر HTTP المباشر وقوائم HLS يعطي PCM مطابقًا للفك الملفي المرجعي.
 * 4) المحرك يشغّل البث: شارة حية، عنوان ومدة، انتهاء بلا انتقال آلي، والتبديل
 *    إلى ملف يطفئ الشارة — وقارئ البث الحقيقي يسقي ويبحث وينتهي عبر خادم محلي.
 */
class YouTubeStreamV51Test {

    // ================= أدوات العيّنات =================

    private fun resourceBytes(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(name)?.readBytes()
            ?: throw AssertionError("العيّنة مفقودة: $name")

    private fun tmpFile(prefix: String, bytes: ByteArray): File {
        val f = File.createTempFile(prefix, ".bin")
        f.writeBytes(bytes)
        f.deleteOnExit()
        return f
    }

    private fun shortsToLeBytes(s: ShortArray): ByteArray {
        val bb = ByteBuffer.allocate(s.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bb.asShortBuffer().put(s)
        return bb.array()
    }

    /** خادم HTTP محلي يخدم خرائط مسارات ثابتة — للبث الحي دون شبكة خارجية. */
    private class TestServer(private val files: Map<String, ByteArray>) {
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port: Int

        init {
            server.createContext("/") { ex: HttpExchange ->
                val path = ex.requestURI.path ?: "/"
                val body = files[path]
                if (body == null) {
                    ex.sendResponseHeaders(404, 3)
                    ex.responseBody.use { it.write("404".toByteArray()) }
                } else {
                    ex.responseHeaders.add("Content-Type", "application/octet-stream")
                    ex.sendResponseHeaders(200, body.size.toLong())
                    ex.responseBody.use { it.write(body) }
                }
            }
            server.start()
            port = server.address.port
        }

        fun url(path: String): String = "http://127.0.0.1:$port$path"
        fun stop() { server.stop(0) }
    }

    /**
     * تقسيم fMP4 إلى (تهيئة، أجزاء) بمشي الصناديق العلوية: التهيئة كل ما قبل
     * أول moof، وكل جزء من moof حتى ما قبل moof التالي — نفس ما يسلّمه HLS.
     */
    private fun splitFmp4(b: ByteArray): Pair<ByteArray, List<ByteArray>> {
        data class Box(val type: String, val start: Int, val end: Int)
        val boxes = ArrayList<Box>()
        var pos = 0
        while (pos + 8 <= b.size) {
            val size = ((b[pos].toInt() and 0xFF) shl 24) or ((b[pos + 1].toInt() and 0xFF) shl 16) or
                    ((b[pos + 2].toInt() and 0xFF) shl 8) or (b[pos + 3].toInt() and 0xFF)
            val type = String(b, pos + 4, 4, Charsets.ISO_8859_1)
            var headerLen = 8
            var sz = size.toLong() and 0xFFFFFFFFL
            if (sz == 1L) {
                sz = 0
                for (k in 0 until 8) sz = (sz shl 8) or (b[pos + 8 + k].toLong() and 0xFF)
                headerLen = 16
            }
            if (sz < headerLen || pos + sz > b.size) break
            boxes.add(Box(type, pos, pos + sz.toInt()))
            pos += sz.toInt()
        }
        val firstMoof = boxes.firstOrNull { it.type == "moof" } ?: throw AssertionError("لا moof في العيّنة")
        val init = b.copyOfRange(0, firstMoof.start)
        val segments = ArrayList<ByteArray>()
        var i = 0
        while (i < boxes.size) {
            if (boxes[i].type == "moof") {
                var j = i + 1
                while (j < boxes.size && boxes[j].type != "moof") j++
                segments.add(b.copyOfRange(boxes[i].start, boxes[j - 1].end))
                i = j
            } else i++
        }
        return init to segments
    }

    /** مُورِّد مجرى واحد: يعيد المجرى مرة واحدة ثم null (نهاية) — لا حلقة لا نهائية. */
    private fun singleSupply(bytes: ByteArray): Fmp4StreamDemux {
        var used = false
        return Fmp4StreamDemux {
            if (used) null else { used = true; ByteArrayInputStream(bytes) }
        }
    }

    /** انتظار شرط بمهلة — يفشل برسالة واضحة إن انقضت. */
    private fun await(message: String, timeoutMs: Long = 8_000L, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("انقضت المهلة: $message")
    }

    // ================= 1) اختيار مصدر البث =================

    @Test
    fun `pickStreamSource prefers direct https mp4a and refuses opus`() {
        val json = """
        {"id":"abc","duration":180,"formats":[
          {"format_id":"249","acodec":"opus","vcodec":"none","ext":"webm","abr":50.0,"protocol":"https","url":"https://g/249"},
          {"format_id":"140","acodec":"mp4a.40.2","vcodec":"none","ext":"m4a","abr":129.5,"protocol":"https","url":"https://g/140"},
          {"format_id":"140h","acodec":"mp4a.40.2","vcodec":"none","ext":"m4a","abr":129.5,"protocol":"m3u8_native","url":"https://g/hls.m3u8"}
        ],
        "url":"https://g/140","protocol":"https","ext":"m4a","abr":129.5}
        """.trimIndent()
        val src = YouTubeJson.pickStreamSource(json)
        assertNotNull(src, "يجب اختيار مسار mp4a المباشر")
        assertEquals("https://g/140", src!!.url)
        assertFalse(src.isHls)
        assertFalse(src.isMp3)

        // opus وحده → null صادق
        val opusOnly = """{"id":"x","duration":10,"formats":[
          {"format_id":"249","acodec":"opus","vcodec":"none","ext":"webm","abr":50.0,"protocol":"https","url":"https://g/249"}]}"""
        assertNull(YouTubeJson.pickStreamSource(opusOnly), "لا يجب اختيار opus للتشغيل المباشر")
    }

    @Test
    fun `pickStreamSource falls back to hls and accepts mp3`() {
        val hlsOnly = """{"id":"y","duration":60,"formats":[
          {"format_id":"140h","acodec":"mp4a.40.2","vcodec":"none","ext":"m4a","abr":129.0,"protocol":"m3u8_native","url":"https://g/audio.m3u8"}]}"""
        val hls = YouTubeJson.pickStreamSource(hlsOnly)
        assertNotNull(hls)
        assertTrue(hls!!.isHls, "يجب أن يُعلَّم المصدر HLS")

        val mp3Only = """{"id":"z","duration":60,"formats":[
          {"format_id":"mp3","acodec":"mp3","vcodec":"none","ext":"mp3","abr":128.0,"protocol":"https","url":"https://x/a.mp3"}]}"""
        val mp3 = YouTubeJson.pickStreamSource(mp3Only)
        assertNotNull(mp3)
        assertTrue(mp3!!.isMp3)
        assertFalse(mp3.isHls)

        assertNull(YouTubeJson.pickStreamSource("not json"), "JSON تالف → null صادق")
    }

    // ================= 2) تحليل HLS =================

    @Test
    fun `hls media playlist maps init and segments with base resolution`() {
        val text = """
        #EXTM3U
        #EXT-X-VERSION:6
        #EXT-X-TARGETDURATION:11
        #EXT-X-MAP:URI="init.mp4"
        #EXTINF:10.006,
        seg0.m4s
        #EXTINF:10.006,
        seg1.m4s
        #EXT-X-ENDLIST
        """.trimIndent()
        val pl = HttpAudioSource.parse(text, "http://h/stream/playlist.m3u8")
        assertFalse(pl.isMaster)
        assertEquals("http://h/stream/init.mp4", pl.initUri)
        assertEquals(listOf("http://h/stream/seg0.m4s", "http://h/stream/seg1.m4s"), pl.segments)
    }

    @Test
    fun `hls master playlist follows best variant and rejects encryption`() {
        val master = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=128000,CLOSED-CAPTIONS=NONE
        audio_128.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=64000,CLOSED-CAPTIONS=NONE
        audio_64.m3u8
        """.trimIndent()
        val pl = HttpAudioSource.parse(master, "http://h/master.m3u8")
        assertTrue(pl.isMaster)
        assertEquals("http://h/audio_128.m3u8", pl.variantUri)

        val encrypted = """
        #EXTM3U
        #EXT-X-KEY:METHOD=AES-128,URI="keys"
        #EXTINF:10.0,
        seg.m4s
        """.trimIndent()
        assertThrows<IOException> { HttpAudioSource.parse(encrypted, "http://h/p.m3u8") }
    }

    // ================= 3) الدِيمكس التدفقي = الديمكس الملفي بايتًا ببايت =================

    @Test
    fun `stream demux samples equal file-path scan byte for byte`() {
        val bytes = resourceBytes("selftest/test_tone_fmp4.m4a")
        val file = tmpFile("v51-fmp4", bytes)
        val fileScan = FragmentedMp4Aac.scan(file)
        assertNotNull(fileScan)
        assertTrue(fileScan!!.samples.isNotEmpty())

        val demux = singleSupply(bytes)
        val cfg = demux.readInit()
        assertTrue(cfg.asc.isNotEmpty(), "ASC من esds")
        assertEquals(fileScan.trackId, cfg.trackId, "مسار الصوت نفسه")

        val streamed = ArrayList<ByteArray>()
        while (true) {
            val s = demux.nextSample() ?: break
            streamed.add(s)
        }
        demux.close()
        assertEquals(fileScan.samples.size, streamed.size, "عدد العينات متطابق")

        for (i in streamed.indices) {
            val fs = fileScan.samples[i]
            val expected = bytes.copyOfRange(fs.offset.toInt(), fs.offset.toInt() + fs.size)
            assertTrue(
                expected.contentEquals(streamed[i]),
                "العينة $i تختلف عن الملفي (حجم ${fs.size} مقابل ${streamed[i].size})"
            )
        }
    }

    @Test
    fun `stream demux works across split segments like hls`() {
        val bytes = resourceBytes("selftest/test_tone_fmp4.m4a")
        val (init, segments) = splitFmp4(bytes)
        assertTrue(segments.size >= 3, "العيّنة فيها أجزاء متعددة: ${segments.size}")

        val streams = ArrayList<ByteArrayInputStream>()
        streams.add(ByteArrayInputStream(init))
        segments.forEach { streams.add(ByteArrayInputStream(it)) }
        val demux = Fmp4StreamDemux { streams.removeFirstOrNull() }

        demux.readInit()
        val count = ArrayList<ByteArray>()
        while (true) {
            val s = demux.nextSample() ?: break
            count.add(s)
        }
        demux.close()

        val file = tmpFile("v51-fmp4b", bytes)
        val fileScan = FragmentedMp4Aac.scan(file)!!
        assertEquals(fileScan.samples.size, count.size, "التقسيم لأجزاء لا يغيّر العينات")
        for (i in count.indices) {
            val fs = fileScan.samples[i]
            assertTrue(
                bytes.copyOfRange(fs.offset.toInt(), fs.offset.toInt() + fs.size).contentEquals(count[i]),
                "عينة الجزء $i مطابقة"
            )
        }
    }

    // ================= 4) خط البث عبر HTTP: PCM = المرجع الملفي =================

    /** المرجع: عينات الديمكس الملفي تُفكّ بjaad مباشرة — PCM 16-bit LE بايتًا ببايت. */
    private fun referencePcmBytes(bytes: ByteArray): ByteArray {
        val demux = singleSupply(bytes)
        val cfg = demux.readInit()
        val dec = JaadDecoder(cfg.asc)
        val sb = JaadSampleBuffer()
        sb.setBigEndian(false)
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val s = demux.nextSample() ?: break
            try {
                dec.decodeFrame(s, sb)
                val data = sb.getData()
                if (data.isNotEmpty()) out.write(data)
            } catch (_: Exception) { }
        }
        demux.close()
        return out.toByteArray()
    }

    private fun pipelinePcmBytes(pipeline: com.saidjte.schoolradio.desktop.audio.StreamPipeline): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val b = try { pipeline.nextBlock() } catch (_: Throwable) { null } ?: break
            if (b.isNotEmpty()) out.write(shortsToLeBytes(b))
        }
        pipeline.close()
        return out.toByteArray()
    }

    @Test
    fun `aac pipeline over direct http yields pcm identical to file-path decode`() {
        val bytes = resourceBytes("selftest/test_tone_fmp4.m4a")
        val server = TestServer(mapOf("/audio.m4a" to bytes))
        try {
            val pipeline = StreamPipelineFactory.open(StreamSpec(url = server.url("/audio.m4a"), hls = false, mp3 = false))
            val got = pipelinePcmBytes(pipeline)
            val want = referencePcmBytes(bytes)
            assertTrue(want.size > 100_000, "مرجع PCM منطقي: ${want.size}")
            assertTrue(want.contentEquals(got), "PCM التدفق مطابق للفك الملفي (${got.size} مقابل ${want.size})")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `aac pipeline over hls playlist yields pcm identical to direct stream`() {
        val bytes = resourceBytes("selftest/test_tone_fmp4.m4a")
        val (init, segments) = splitFmp4(bytes)
        val files = HashMap<String, ByteArray>()
        files["/init.mp4"] = init
        segments.forEachIndexed { i, s -> files["/seg$i.m4s"] = s }
        val pl = buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-VERSION:6")
            appendLine("#EXT-X-MAP:URI=\"init.mp4\"")
            segments.forEachIndexed { i, _ ->
                appendLine("#EXTINF:10.006,")
                appendLine("seg$i.m4s")
            }
            appendLine("#EXT-X-ENDLIST")
        }
        files["/playlist.m3u8"] = pl.toByteArray(Charsets.UTF_8)
        val server = TestServer(files)
        try {
            val pipeline = StreamPipelineFactory.open(
                StreamSpec(url = server.url("/playlist.m3u8"), hls = true, mp3 = false)
            )
            val got = pipelinePcmBytes(pipeline)
            val direct = referencePcmBytes(bytes)
            assertTrue(direct.contentEquals(got), "PCM قائمة HLS مطابق للبث المباشر (${got.size} مقابل ${direct.size})")
        } finally {
            server.stop()
        }
    }

    @Test
    fun `mp3 pipeline over http decodes real frames with position and format`() {
        val bytes = resourceBytes("formats/mp3_cbr44100.mp3")
        val server = TestServer(mapOf("/tone.mp3" to bytes))
        try {
            val pipeline = StreamPipelineFactory.open(StreamSpec(url = server.url("/tone.mp3"), hls = false, mp3 = true))
            var blocks = 0
            var firstNonSilent = false
            while (true) {
                val b = try { pipeline.nextBlock() } catch (_: Throwable) { null } ?: break
                if (b.isNotEmpty()) {
                    blocks++
                    if (!firstNonSilent && b.any { it.toInt() != 0 }) firstNonSilent = true
                }
            }
            pipeline.close()
            assertTrue(blocks > 50, "عدد كتل MP3 المفكوكة: $blocks")
            assertTrue(firstNonSilent, "النغمة غير صامتة")
            val f = pipeline.format
            assertNotNull(f)
            assertEquals(44100f, f!!.sampleRate)
            assertTrue(pipeline.currentMs > 1_000L, "الموضع يتقدم: ${pipeline.currentMs}ms")
        } finally {
            server.stop()
        }
    }

    // ================= 5) قارئ البث الحقيقي: سقي وسعي ونهاية =================

    /** خط ذاكرة يحصي البايتات — بلا عتاد صوت. */
    private class MemoryLine : PcmOutLine {
        var bytes = 0L
        override fun open(format: AudioFormat, bufferSize: Int) { }
        override fun start() { }
        override fun stop() { }
        override fun flush() { }
        override fun write(b: ByteArray, off: Int, len: Int) { bytes += len }
        override fun setVolume(v: Float) { }
        override fun close() { }
    }

    @Test
    fun `stream track reader plays seeks and ends over local http`() {
        val bytes = resourceBytes("selftest/test_tone_fmp4.m4a")
        val server = TestServer(mapOf("/audio.m4a" to bytes))
        try {
            val spec = StreamSpec(url = server.url("/audio.m4a"), hls = false, mp3 = false)
            val reader = com.saidjte.schoolradio.desktop.audio.StreamTrackReader(
                spec, "نغمة بث", 3000L, null, null, { 1f }, null, MusicChannel(),
                outLineFactory = { MemoryLine() }
            )
            try {
                assertTrue(reader.durationMs == 3000L)
                // سقي أولي: عدة كتل والموضع يتقدم
                var chunks = 0
                while (chunks < 20 && reader.readChunk()) chunks++
                val at = reader.currentMs
                assertTrue(at > 0L, "الموضع تقدم: $at")

                // السعي إلى نقطة أبعد: إعادة فتح وتفقيد صامت حتى الهدف
                reader.seek(at + 900L)
                assertTrue(reader.currentMs >= at + 900L, "السعي وصل الهدف: ${reader.currentMs}")

                // التسريب حتى النهاية: readChunk يُنهي البث نظيفًا
                var drained = 0
                while (reader.readChunk() && drained < 200_000) drained++
                assertTrue(reader.currentMs > at, "وصل نهاية البث")
            } finally {
                reader.close()
            }
        } finally {
            server.stop()
        }
    }

    // ================= 6) الكوكيز: ترشيح النطاق والمسار =================

    @Test
    fun `netscape cookies filtered by domain path and secure`() {
        val f = File.createTempFile("v51-cookies", ".txt")
        f.deleteOnExit()
        f.writeText(
            listOf(
                "# Netscape HTTP Cookie File",
                ".youtube.com\tTRUE\t/\tTRUE\t1899999999\tYS\ty=1",
                "#HttpOnly_.youtube.com\tTRUE\t/\tTRUE\t1899999999\tHSO\th=2",
                ".example.com\tFALSE\t/\tFALSE\t1899999999\tOTHER\to=3",
                ".youtube.com\tTRUE\t/video\tTRUE\t1899999999\tPATHED\tp=4"
            ).joinToString("\n")
        )
        val got = HttpAudioSource.cookieHeaderFor(f, "https://www.youtube.com/watch?v=x")
        assertNotNull(got)
        assertTrue(got!!.contains("YS=y=1"), "كوكي يوتيوب العادي: $got")
        assertTrue(got.contains("HSO=h=2"), "سطر #HttpOnly_ يُفهم: $got")
        assertFalse(got.contains("OTHER"), "كوكي نطاق آخر يُسقط: $got")
        assertFalse(got.contains("PATHED"), "كوكي مسار آخر يُسقط: $got")

        assertNull(HttpAudioSource.cookieHeaderFor(f, "http://localhost:1/x"), "بلا مطابقة → null")
    }

    // ================= 7) المحرك يشغّل البث =================

    /** قارئ بث مزيف — يسجل السعي ويتقدم وينتهي بعد عدد كتل معلوم (يُضبط لاحقًا). */
    private class FakeStreamReader(
        private val chunkMs: Long = 250L
    ) : TrackReader {
        val seeks = mutableListOf<Long>()
        var closed = false
        /** بعد كم كتلة ينتهي البث — MAX يعني لا نهاية؛ يُضبط من الاختبار عند الحاجة. */
        @Volatile var stopAfter: Long = Long.MAX_VALUE
        private var chunks = 0L
        @Volatile var current = 0L
        override val durationMs: Long = 600_000L
        override val currentMs: Long get() = current
        override fun seek(ms: Long) { seeks += ms; current = ms.coerceIn(0, durationMs) }
        override fun pause() { }
        override fun resume() { }
        override fun applyVolume(volume: Float) { }
        override fun readChunk(): Boolean {
            chunks++
            current += chunkMs
            return chunks <= stopAfter
        }
        override fun close() { closed = true }
    }

    @Test
    fun `engine playStream shows badge title and ends without auto next`() {
        val eng = MusicPlayerEngine()
        var endedCount = 0
        var autoNextCount = 0
        eng.onStreamEnded = { endedCount++ }
        eng.onEnded = { autoNextCount++ }
        val fake = FakeStreamReader()
        eng.streamReaderOverride = { fake }
        try {
            eng.playStream(
                StreamRef("بث يوتيوب تجريبي", "vid123", 600_000L,
                    StreamSpec("http://127.0.0.1:1/nothing", false, false))
            )
            await("stream opened") { eng.hasTrack.value && eng.streamActive.value }
            assertEquals("بث يوتيوب تجريبي", eng.title.value)
            assertEquals(600_000L, eng.durationMs.value)
            assertNull(eng.currentFile.value, "بلا ملف محلي للبث")
            await("position ticks") { eng.positionMs.value > 0L }

            // سعي يمر للقارئ
            eng.seek(30_000L)
            await("seek executed") { fake.seeks.contains(30_000L) }

            // ثم نطلب النهاية: شارة تنطفئ وonStreamEnded يُستدعى ولا انتقال آلي للقائمة
            fake.stopAfter = 1L
            await("stream ended") { endedCount > 0 }
            assertEquals(0, autoNextCount, "نهاية البث لا تنتقل آليًا لمكتبة الموسيقى")
            assertFalse(eng.streamActive.value)
            assertTrue(fake.closed, "قارئ البث أُغلق عند النهاية")
        } finally {
            eng.streamReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `playing a file while streaming switches and clears the badge`() {
        val eng = MusicPlayerEngine()
        val fake = FakeStreamReader()
        eng.streamReaderOverride = { fake }
        val fileReader = FakeStreamReader()
        eng.trackReaderOverride = { fileReader }
        try {
            eng.playStream(
                StreamRef("بث", "vid", 600_000L, StreamSpec("http://127.0.0.1:1/x", false, false))
            )
            await("stream active") { eng.streamActive.value }
            eng.play(File("selftest/fake-track.mp3"))
            await("file takes over") { !eng.streamActive.value && eng.currentFile.value != null }
            assertTrue(fake.closed, "قارئ البث أُغلق عند التبديل للملف")
        } finally {
            eng.streamReaderOverride = null
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `second playStream replaces the first stream`() {
        val eng = MusicPlayerEngine()
        val first = FakeStreamReader()
        val second = FakeStreamReader()
        val handed = AtomicInteger(0)
        eng.streamReaderOverride = { ref: StreamRef ->
            if (ref.videoId == "one") first else second
        }
        try {
            eng.playStream(StreamRef("الأول", "one", 10_000L, StreamSpec("http://x", false, false)))
            await("first open") { eng.streamActive.value && eng.title.value == "الأول" }
            eng.playStream(StreamRef("الثاني", "two", 20_000L, StreamSpec("http://x", false, false)))
            await("second takes over") { eng.title.value == "الثاني" }
            assertTrue(first.closed, "الأول أُغلق")
            handed.incrementAndGet()
        } finally {
            eng.streamReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `failing stream open surfaces an honest error`() {
        val eng = MusicPlayerEngine()
        eng.streamReaderOverride = {
            throw IllegalArgumentException("لا يوجد مسار صوتي يفكّه التشغيل المباشر")
        }
        try {
            eng.playStream(StreamRef("فاشل", "err", 0L, StreamSpec("http://x", false, false)))
            await("error surfaced") { eng.error.value != null }
            assertTrue(eng.error.value!!.contains("لا يوجد مسار صوتي"), "رسالة صادقة: ${eng.error.value}")
            assertFalse(eng.hasTrack.value)
            assertFalse(eng.streamActive.value)
        } finally {
            eng.streamReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }
}
