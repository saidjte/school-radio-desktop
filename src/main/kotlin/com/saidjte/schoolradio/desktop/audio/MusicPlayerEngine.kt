package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.data.CrossfadeLogic
import com.saidjte.schoolradio.desktop.data.MusicEqState
import javazoom.jl.decoder.Bitstream
import javazoom.jl.decoder.Decoder
import javazoom.jl.decoder.SampleBuffer
import javazoom.spi.vorbis.sampled.file.VorbisAudioFileReader
import org.jflac.sound.spi.FlacAudioFileReader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Mixer
import kotlin.concurrent.thread
import kotlin.math.min

/** الصيغ القابلة للتشغيل فعليًا في المحرك — بوابة واحدة للاستيراد والمسح والمكتبة. */
/** الصيغ التي يفكها القارئ — فلاتر الاختيار والاستيراد تتبعها (v0.49.0 موسّعة). */
val PLAYABLE_EXTENSIONS = setOf(
    "wav", "mp3", "ogg", "oga", "flac", "aif", "aiff", "au",
    "aac", "m4a", "m4b", "mp4", "mp2", "mpga", "mpeg", "mpa"
)

/** رسالة الصيغ المدعومة — تظهر في أخطاء الفتح الصادقة (وفق قائمة VLC wiki.videolan.org). */
const val SUPPORTED_FORMATS_HINT =
    "الصيغ المدعومة: MP3 (MPEG-1/2/2.5 وLayer 2 وVBR) وWAV وFLAC وOGG وAAC/M4A وAIFF/AU"

/**
 * قارئ مقطع صوتي يفكّ ويكتب في خط السماع مباشرة.
 * واجهة موحّدة فوق MP3 (JLayer) و PCM (Java Sound + OGG + FLAC).
 */
internal interface TrackReader : Closeable {
    val durationMs: Long
    val currentMs: Long
    fun seek(ms: Long)
    fun pause()
    fun resume()
    fun applyVolume(volume: Float)

    /** يفكّ الإطار/الكتلة التالية ويكتبها في خط السماع. يعيد false عند النهاية. */
    fun readChunk(): Boolean
}

/**
 * قناة الموسيقى في وحدة المزج: معادل الصوت خمس الشرائح + فيدر + كتم جلسي
 * + مقياس حي + الخفض التلقائي (Voice-Over) + إعطاء مرجع إلغاء الصدى.
 * حيّة لحظيًا: كل التغييرات تسري على الكتلة التالية (~26ms)
 * دون قطع التشغيل ولا إعادة فتح الخط.
 */
class MusicChannel internal constructor() {

    internal val eq = MusicEq()

    @Volatile internal var fader = 1.0f
    @Volatile internal var master = 1.0f
    @Volatile internal var muted = false

    private var channels = 2
    /** معدل القناة الحالي — يُمرّر لناقل تسجيل المزيج مع كل كتلة (v0.31.0). */
    @Volatile private var rateHz = 44100
    private var monoBuf = FloatArray(4096)
    private val shorts = ShortArray(1 shl 15)

    // غلاف التلاشٍ الناعم (v0.29.0): يُقيّم على ساعة الحائط لكل كتلة فمستقل
    // عن حجم الكتلة ومعدل العينات، ويسري في مسار process فقط (القرّاء الحقيقيون)
    @Volatile private var fadeFrom = 1f
    @Volatile private var fadeTo = 1f
    @Volatile private var fadeStartNanos = 0L
    @Volatile private var fadeMs = 0L

    /** بدء تلاشٍ من القيمة الحالية إلى [to] خلال [ms] ميلي ثانية (0 = فوري). */
    internal fun beginFade(to: Float, ms: Long) {
        fadeFrom = currentEnv()
        fadeTo = to.coerceIn(0f, 1f)
        fadeStartNanos = System.nanoTime()
        fadeMs = ms.coerceAtLeast(0L)
    }

    /** إلغاء أي تلاشٍ والعودة الفورية للكسب الكامل — عند فتح مقطع جديد أو إعادة فتح المخرج. */
    internal fun resetEnv() {
        fadeFrom = 1f
        fadeTo = 1f
        fadeMs = 0L
        fadeStartNanos = 0L
    }

    /** قيمة الغلاف الآن: 1 بلا تلاشٍ، وإلا استيفاء خطي من البداية إلى الهدف. */
    internal fun currentEnv(): Float {
        val ms = fadeMs
        if (ms <= 0L) return fadeTo
        val t = (System.nanoTime() - fadeStartNanos) / 1_000_000.0 / ms
        return if (t >= 1.0) fadeTo else fadeFrom + (fadeTo - fadeFrom) * t.toFloat()
    }

    /** مستوى الكتلة الأخير (منمّط 0..1) — يُستدعى من خيط التشغيل. */
    var onLevel: ((Float) -> Unit)? = null

    internal fun configure(sampleRate: Int, channels: Int) {
        eq.configure(sampleRate, channels)
        this.channels = channels.coerceIn(1, 2)
        rateHz = sampleRate.coerceAtLeast(8000)
        MusicFarEnd.tap.configure(sampleRate)
    }

    /** معالجة بايتات PCM 16-bit في مكانها قبل كتابة الخط. */
    internal fun process(bytes: ByteArray, n: Int) {
        val count = Pcm16.bytesToShorts(bytes, n, shorts)
        apply(shorts, count)
        Pcm16.shortsToBytes(shorts, count, bytes)
    }

    /** معالجة عينات مفكوكة (MP3) في مكانها قبل التحويل للبايتات. */
    internal fun processShorts(s: ShortArray, count: Int) {
        apply(s, count)
    }

    private fun apply(target: ShortArray, count: Int) {
        if (count <= 0) return
        // الخفض التلقائي: كسب الموسيقى يقرأ من ناقل الخفض قبل الفيدر والماستر،
        // وغلاف التلاشٍ الناعم يُضرب في الكل فتغطي النهاية الناعمة كل المسار
        val duck = if (DuckBus.enabled) DuckBus.musicGain.coerceIn(0f, 1f) else 1f
        val env = currentEnv()
        val gain = env * if (muted) 0f else fader * master * duck
        val level = eq.process(target, count, gain)
        // تغذية تسجيل المزيج (v0.31.0): ما بعد كل الكسب — نفس ما يُسمع في السماعة
        if (MixBus.recording.value) {
            MixBus.feedMusic(target, count, rateHz, channels)
        }
        pushFarEnd(target, count)
        onLevel?.invoke(level)
    }

    /** إعطاء مرجع إلغاء الصدى: ما سيُكتب فعليًا في السماعة (أحادي متوسط القنوات). */
    private fun pushFarEnd(target: ShortArray, count: Int) {
        val frames = count / channels
        if (frames <= 0) return
        if (monoBuf.size < frames) monoBuf = FloatArray(frames)
        if (channels == 1) {
            for (f in 0 until frames) monoBuf[f] = target[f] / 32768.0f
        } else {
            var j = 0
            for (f in 0 until frames) {
                monoBuf[f] = (target[j] + target[j + 1]) / 65536.0f
                j += 2
            }
        }
        MusicFarEnd.tap.push(monoBuf, frames)
    }
}

/**
 * قارئ MP3 عبر JLayer: فك إطارًا بإطار مع بحث زمني بإعادة الفتح والعدّ السريع.
 * الكتابة في PcmOutLine — وحيد أو مزدوج (مخرجان معًا) دون أن يعرف القارئ الفرق.
 */
private class Mp3TrackReader(
    private val file: File,
    outMain: Mixer.Info?,
    outSecond: Mixer.Info?,
    volume: () -> Float,
    onSecondOutFailed: ((Throwable) -> Unit)? = null,
    private val channel: MusicChannel
) : TrackReader {

    private val info = Mp3Info.analyze(file)
    private var fh: Mp3Info.FrameHeader? = info?.first
    // v0.49.0 «الصيغ المدعومة»: ملفات يفشل تحليل ترويساتها (وسوم ضخمة/رؤوس
    // شاذة) لم تكن تعمل إطلاقًا — الآن يُفك أول إطار فعليًا لمصدر الحقيقة
    // وتُحتسب المدة بعدّ الإطارات كاملًا (قراءة سريعة بلا فك)
    private val probed: Mp3Probe.FirstFrame? = if (info == null) Mp3Probe.decodeFirstFrame(file) else null
    private val fallbackDurationMs: Long = if (info == null) {
        probed?.let { Mp3Probe.countFramesMs(file, it) } ?: 0L
    } else 0L
    private val format: AudioFormat
    private val out: PcmOutLine
    private var stream: BufferedInputStream
    private var bitstream: Bitstream
    private val decoder = Decoder()
    private var framesDone = 0L
    private var currentMsValue = 0L

    override val durationMs: Long
        get() = info?.durationMs ?: fallbackDurationMs
    override val currentMs: Long get() = currentMsValue

    init {
        val p = probed
        if (info == null && p == null) {
            throw IllegalArgumentException(
                "تعذّر فك «${file.name}» كـMP3 — الملف تالف أو ليست صيغته MP3. " + SUPPORTED_FORMATS_HINT
            )
        }
        val f: Mp3Info.FrameHeader
        if (info != null) {
            f = fh!!
            format = AudioFormat(f.sampleRate.toFloat(), 16, f.channels, true, false)
        } else {
            // ترويسة مبنية من فك الإطار الأول: قنوات ومعدل الخرج الحقيقيان —
            // وMPEG2/2.5 تُصحَّح هنا كذلك لأن JLayer يفكها بمعدل خرج الحقيقة
            f = Mp3Info.FrameHeader(
                version = if (p!!.sampleRate >= 32000) 1 else 2,
                layer = 3,
                sampleRate = p.sampleRate,
                bitRateKbps = 0,
                channels = p.channels,
                samplesPerFrame = p.samplesPerFrame,
                frameSize = 0
            )
            fh = f
            format = AudioFormat(p.sampleRate.toFloat(), 16, p.channels, true, false)
        }
        stream = BufferedInputStream(FileInputStream(file), 1 shl 16)
        Mp3Info.skipId3v2(stream)
        bitstream = Bitstream(stream)
        out = AudioDevices.openOutLine(outMain, outSecond, format, onSecondOutFailed)
        out.open(format, 16 * 1024)
        out.start()
        out.setVolume(volume())
        channel.configure(f.sampleRate, f.channels)
    }

    override fun readChunk(): Boolean {
        val header = try { bitstream.readFrame() } catch (_: Exception) { null } ?: return false
        try {
            val decoded = decoder.decodeFrame(header, bitstream)
            if (decoded is SampleBuffer) {
                val shorts = decoded.buffer
                if (shorts.isNotEmpty()) {
                    // قناة المزج: المعادل + الفيدر + المقياس قبل كتابة الخط
                    channel.processShorts(shorts, shorts.size)
                    val lineBytes = ByteArray(shorts.size * 2)
                    var bi = 0
                    for (s in shorts) {
                        val v = s.toInt()
                        lineBytes[bi++] = (v and 0xFF).toByte()
                        lineBytes[bi++] = ((v shr 8) and 0xFF).toByte()
                    }
                    out.write(lineBytes, 0, lineBytes.size)
                }
            }
            framesDone++
            val f = fh!!
            currentMsValue = framesDone * f.samplesPerFrame * 1000L / f.sampleRate
        } catch (_: Exception) {
            // إطار تالف: نتجاوزه ونكمل
        } finally {
            try { bitstream.closeFrame() } catch (_: Exception) { }
        }
        return true
    }

    override fun seek(ms: Long) {
        val i = info
        val f = fh ?: return
        val target = ms.coerceIn(0L, durationMs)
        try { bitstream.close() } catch (_: Exception) { }
        try { stream.close() } catch (_: Exception) { }

        val s = BufferedInputStream(FileInputStream(file), 1 shl 16)
        stream = s
        Mp3Info.skipId3v2(s)

        // موضع البايت وزمن المرساة قبل العدّ الدقيق للإطارات:
        // - جدول Xing المئوي (VBR موسوم): مواضع بايتات لكل 1% زمني — أدق المسارات
        // - متوسط معدل البت الفعلي (VBR دون جدول): من حجم البيانات والمدة
        // - معدل الإطار الأول (CBR): التحويل الخطي مباشرةً
        // - بلا ترويسة تحليلية (v0.49.0): متوسط فعلي من حجم الملف والمدة المعدودة
        val jumpMs: Long
        val toc = i?.toc
        val dataBytes = i?.dataBytes?.coerceAtLeast(0L) ?: 0L
        if (toc != null && toc.size >= 100 && durationMs > 0 && dataBytes > 0) {
            val frac = ((target - 500L).coerceAtLeast(0L)).toFloat() / durationMs.toFloat()
            val idx = (frac * 100).toInt().coerceIn(0, 99)
            val byteFrac = (toc[idx].toInt() and 0xFF) / 255f
            jumpMs = idx.toLong() * durationMs / 100L
            skipBytes(s, (byteFrac * dataBytes).toLong())
        } else {
            val avgKbps = when {
                i != null && i.isVbr && durationMs > 0 && dataBytes > 0 ->
                    (dataBytes * 8L / durationMs).toInt().coerceAtLeast(1)
                i != null -> f.bitRateKbps
                else -> ((file.length() * 8L) / durationMs.coerceAtLeast(1L)).toInt().coerceAtLeast(1)
            }
            jumpMs = (target - 1500L).coerceAtLeast(0L)
            skipBytes(s, jumpMs * avgKbps / 8L)
        }
        bitstream = Bitstream(s)

        // عدّ سريع للإطارات (دون فك) حتى الموضع المطلوب
        var count = 0L
        while (true) {
            val h = try { bitstream.readFrame() } catch (_: Exception) { null } ?: break
            val t = jumpMs + count * f.samplesPerFrame * 1000L / f.sampleRate
            if (t >= target) break
            count++
            try { bitstream.closeFrame() } catch (_: Exception) { }
        }
        framesDone = jumpMs * f.sampleRate / (f.samplesPerFrame * 1000L) + count
        currentMsValue = jumpMs + count * f.samplesPerFrame * 1000L / f.sampleRate
        out.flush()
    }

    /** تجاوز عدد بايتات من المجرى بالقراءة — متحمل للقراءات الجزئية. */
    private fun skipBytes(s: BufferedInputStream, bytes: Long) {
        var toSkip = bytes.coerceAtLeast(0L)
        val buf = ByteArray(8192)
        while (toSkip > 0) {
            val r = s.read(buf, 0, min(toSkip, buf.size.toLong()).toInt())
            if (r < 0) break
            toSkip -= r
        }
    }

    override fun pause() { try { out.stop() } catch (_: Exception) { } }
    override fun resume() { try { out.start() } catch (_: Exception) { } }
    override fun applyVolume(volume: Float) { out.setVolume(volume) }

    override fun close() {
        try { bitstream.close() } catch (_: Exception) { }
        try { stream.close() } catch (_: Exception) { }
        try { out.close() } catch (_: Exception) { }
    }
}

/**
 * قارئ PCM (WAV/AIFF/AU وOGG وFLAC عبر قرّاء صريحين) مع تحويل إلى PCM 16-bit عند الحاجة.
 * الكتابة في PcmOutLine — وحيد أو مزدوج (مخرجان معًا).
 */
private class PcmTrackReader(
    private val file: File,
    outMain: Mixer.Info?,
    outSecond: Mixer.Info?,
    volume: () -> Float,
    onSecondOutFailed: ((Throwable) -> Unit)? = null,
    private val channel: MusicChannel
) : TrackReader {

    private var ais: AudioInputStream
    private val pcmFormat: AudioFormat
    private val out: PcmOutLine
    private val frameSize: Int
    private val buf = ByteArray(64 * 1024)
    private var framesDone = 0L
    private var currentMsValue = 0L

    override val durationMs: Long
    override val currentMs: Long get() = currentMsValue

    init {
        // v0.49.0: رسالة فتح صادقة تسمّي الملف والصيغ المدعومة بدل استثناء مجرد
        val raw = try {
            openAudioInputStream(file)
        } catch (t: Throwable) {
            throw IllegalArgumentException(
                "تعذّر فتح «${file.name}» — الملف تالف أو صيغته غير مدعومة بعد. $SUPPORTED_FORMATS_HINT", t
            )
        }
        val rf = raw.format
        val isPlainPcm = rf.encoding == AudioFormat.Encoding.PCM_SIGNED &&
                !rf.isBigEndian && rf.sampleSizeInBits == 16
        ais = if (isPlainPcm) raw
        else AudioSystem.getAudioInputStream(
            AudioFormat(rf.sampleRate, 16, rf.channels, true, false), raw
        )
        pcmFormat = ais.format
        frameSize = pcmFormat.frameSize.coerceAtLeast(1)

        val fl = ais.frameLength
        val wavInfo = WavHeader.read(file)
        // v0.49.0: FLAC أولًا من STREAMINFO — jflac يبلّغ frameLength بحجم
        // الملف بالبايتات فيضيّر المدة، وAAC بلا طول إطاري فمدتها من بنية
        // الحاوية (mdhd) أو مسح ADTS
        val flacMs = if (file.name.lowercase().endsWith(".flac")) FlacInfo.durationMsOf(file) else -1L
        durationMs = when {
            flacMs > 0 -> flacMs
            fl > 0 && pcmFormat.frameRate > 0 -> (fl * 1000L / pcmFormat.frameRate.toLong())
            wavInfo != null -> WavFileWriter.durationMsOf(
                wavInfo.dataBytes, wavInfo.sampleRate, wavInfo.channels, wavInfo.bitsPerSample
            )
            else -> ExtraDuration.durationMsOf(file).coerceAtLeast(0L)
        }

        out = AudioDevices.openOutLine(outMain, outSecond, pcmFormat, onSecondOutFailed)
        out.open(pcmFormat, 16 * 1024)
        out.start()
        out.setVolume(volume())
        channel.configure(pcmFormat.sampleRate.toInt(), pcmFormat.channels)
    }

    override fun readChunk(): Boolean {
        val n = try { ais.read(buf) } catch (_: Exception) { -1 }
        if (n < 0) return false
        if (n == 0) return true
        val usable = n - (n % frameSize)
        if (usable > 0) {
            // قناة المزج: المعادل + الفيدر + المقياس قبل كتابة الخط
            channel.process(buf, usable)
            out.write(buf, 0, usable)
            framesDone += usable / frameSize
            if (pcmFormat.frameRate > 0) {
                currentMsValue = framesDone * 1000L / pcmFormat.frameRate.toLong()
            }
        }
        return true
    }

    override fun seek(ms: Long) {
        val target = ms.coerceIn(0L, durationMs)
        try { ais.close() } catch (_: Exception) { }
        val raw = openAudioInputStream(file)
        val rf = raw.format
        ais = if (rf.encoding == AudioFormat.Encoding.PCM_SIGNED && !rf.isBigEndian && rf.sampleSizeInBits == 16) raw
        else AudioSystem.getAudioInputStream(AudioFormat(rf.sampleRate, 16, rf.channels, true, false), raw)

        var toSkip = target * pcmFormat.frameRate.toLong() / 1000L * frameSize
        var failed = false
        try {
            while (toSkip > 0) {
                val k = ais.skip(toSkip)
                if (k <= 0) {
                    if (ais.read() < 0) break
                    toSkip -= 1
                } else {
                    toSkip -= k
                }
            }
        } catch (_: Exception) {
            failed = true
        }
        if (failed) {
            // احتياط: تجاهل بالقراءة السريعة
            var remaining = target * pcmFormat.frameRate.toLong() / 1000L * frameSize
            val discard = ByteArray(64 * 1024)
            try {
                while (remaining > 0) {
                    val r = ais.read(discard, 0, min(remaining, discard.size.toLong()).toInt())
                    if (r < 0) break
                    remaining -= r
                }
            } catch (_: Exception) { }
        }
        framesDone = target * pcmFormat.frameRate.toLong() / 1000L
        currentMsValue = target
        out.flush()
    }

    override fun pause() { try { out.stop() } catch (_: Exception) { } }
    override fun resume() { try { out.start() } catch (_: Exception) { } }
    override fun applyVolume(volume: Float) { out.setVolume(volume) }

    override fun close() {
        try { ais.close() } catch (_: Exception) { }
        try { out.close() } catch (_: Exception) { }
    }
}

/**
 * فتح مجرى صوتي بقرّاء صريحين حسب الامتداد — بلا اعتماد على ServiceLoader
 * (دمج ملفات الخدمات في الجرة الموحّدة غير مضمون):
 * OGG عبر قارئ Vorbis الصريح، FLAC عبر قارئ Flac الصريح، AAC/M4A عبر قارئ
 * jaad الصريح (v0.49.0 «الصيغ المدعومة» — تسجيلات آيفون M4A وADTS)، والباقي
 * عبر AudioSystem (WAV/AIFF/AU مدمجة).
 */
internal fun openAudioInputStream(file: File): AudioInputStream {
    val name = file.name.lowercase()
    return when {
        name.endsWith(".ogg") || name.endsWith(".oga") ->
            VorbisAudioFileReader().getAudioInputStream(file)
        name.endsWith(".flac") ->
            FlacAudioFileReader().getAudioInputStream(file)
        name.endsWith(".aac") ->
            AacSupport.openAdts(file)
        name.endsWith(".m4a") || name.endsWith(".m4b") || name.endsWith(".mp4") ->
            AacSupport.openMp4(file)
        else -> AudioSystem.getAudioInputStream(file)
    }
}

/**
 * تقدير مدة ملف صوتي بفتح المجرى وقراءة طوله الإطاري —
 * للأصوات غير MP3/WAV (OGG/FLAC/AAC/M4A/AIFF/AU) في قوائم العرض. يعيد 0 إن تعذّر.
 * v0.49.0: AAC/M4A من بنية الحاوية وFLAC من STREAMINFO — frameLength وحده
 * يخدع في هذين (jaad لا يعطي طولًا وjflac يعطي حجم الملف بالبايتات).
 */
internal object StreamProbe {
    fun durationMsOf(file: File): Long {
        return try {
            val extra = ExtraDuration.durationMsOf(file)
            if (extra > 0) return extra
            openAudioInputStream(file).use { ais ->
                val fl = ais.frameLength
                val fr = ais.format.frameRate
                if (fl > 0 && fr > 0) (fl * 1000L / fr.toLong()) else 0L
            }
        } catch (_: Exception) { 0L }
    }
}

/**
 * محرّك تشغيل الموسيقى (نسخة الحاسوب): خيط عامل واحد يملك القارئ والخط،
 * أوامر متطايرة (تبديل/بحث/إيقاف مؤقت) — نمط مجرّب لا سباقات فيه.
 */
class MusicPlayerEngine {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _hasTrack = MutableStateFlow(false)
    val hasTrack: StateFlow<Boolean> = _hasTrack.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(0.9f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _currentFile = MutableStateFlow<File?>(null)
    val currentFile: StateFlow<File?> = _currentFile.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** الإبلاغ بخطأ تشغيل من الخارج (مقطع مفقود مثلًا) — يظهر في واجهة المشغل. */
    fun fail(message: String) {
        _error.value = message
    }

    /** مسح الخطأ المعروض (زر إخفاء في الواجهة). */
    fun clearError() {
        _error.value = null
    }

    /** مقياس قناة الموسيقى الحي (منمّط 0..1) — بعد المعادل والفيدر. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** قناة الموسيقى في وحدة المزج. */
    val channel = MusicChannel()

    /** يُستدعى من خيط التشغيل عند انتهاء المقطع (للانتقال التلقائي). */
    var onEnded: (() -> Unit)? = null

    /**
     * يُستدعى عندما يفشل فتح المقطع (ملف تالف/غير مقروء) — بوابة تخطي الملفات
     * التالفة آليًا كما في نسخة الهاتف. لا يُستدعى عند الإيقاف اليدوي.
     */
    var onPlayFailed: ((File) -> Unit)? = null

    @Volatile private var paused = false
    @Volatile private var stopped = true
    @Volatile private var switchTo: File? = null
    @Volatile private var switchCrossfade = false
    @Volatile private var seekRequest = -1L
    @Volatile private var volumeDirty = false

    // ================= الكروس فيد بين المقاطع (v0.31.0) =================

    /**
     * نافذة الكروس فيد بالمللي ثانية (0 = معطّل) — تُدفع من الإعدادات حيًّا.
     * التلاشي يُنفَّذ على كسب خطَّي الإخراج (MASTER_GAIN) فتبقى قناة المزج
     * المشتركة (معادل/فيدر/خفض/مرجع صدى) محايدة كاملًا خلال الانتقال.
     */
    @Volatile var crossfadeMs: Long = 0L

    private val _xfading = MutableStateFlow(false)
    /** شارة انتقال جارٍ — تضيء في المشغل لحظة التمديد وتنطفئ عند اكتماله. */
    val xfading: StateFlow<Boolean> = _xfading.asStateFlow()

    /** طلب المقطع التالي للتمديد الآلي عند اقتراب نهاية الجاري — null = لا تمديد. */
    var onCrossfadeNext: (() -> File?)? = null

    /** تأكيد بدء الكروس فيد إلى ملف — يحدّث مؤشر القائمة في ViewModel. */
    var onCrossfadeCommitted: ((File) -> Unit)? = null

    init {
        channel.onLevel = { _level.value = it }
    }

    // ================= وحدة المزج: قناة الموسيقى =================

    /** فيدر قناة الموسيقى (0..1.5) — يسري أثناء التشغيل فورًا. */
    fun setMusicFader(v: Float) { channel.fader = v.coerceIn(0f, 1.5f) }

    /** فيدر الماستر — يسري أثناء التشغيل فورًا. */
    fun setMasterFader(v: Float) { channel.master = v.coerceIn(0f, 1.5f) }

    /** كتم جلسي: لا يُكتب في المحفوظ، وإلغاؤه يعيد المستوى نفسه. */
    fun setMusicMuted(m: Boolean) { channel.muted = m }

    /** تحديث معادل الصوت دفعة واحدة (تشغيل/شرائح). */
    fun setMusicEq(state: MusicEqState) {
        channel.eq.setEnabled(state.enabled)
        channel.eq.setBands(state.bands.toFloatArray())
    }

    /** مخرجا التشغيل المختاران (null = السماعة الافتراضية للنظام) — طلب المستخدم v0.19.0. */
    @Volatile private var outputMain: Mixer.Info? = null
    @Volatile private var outputSecond: Mixer.Info? = null
    @Volatile private var reopenOutput = false
    private var worker: Thread? = null
    private val workerLock = Any()

    /**
     * تغيير مخرجي التشغيل (السماعات) أثناء العمل: يُطبّق فورًا على المقطع الجاري
     * بإعادة فتح سلسة تحفظ الموضع، وعلى كل مقطع لاحق. الثاني اختياري (null = وحيد).
     */
    fun setOutput(main: Mixer.Info?, second: Mixer.Info?) {
        if (outputMain === main && outputSecond === second && !reopenOutput) return
        outputMain = main
        outputSecond = second
        if (_hasTrack.value) reopenOutput = true
    }

    /**
     * لقطة المخرجَين الحاليين — جدار الجينغل (v0.28.0) يشارك الموسيقى سماعاتها
     * نفسها: كل صوت جينل جديد يُفتح على ما هو مختار لحظة إطلاقه.
     */
    fun outputsSnapshot(): Pair<Mixer.Info?, Mixer.Info?> = outputMain to outputSecond

    fun play(file: File) { play(file, crossfade = false) }

    /**
     * تشغيل ملف مع طلب كروس فيد اختياري (v0.31.0): عند `crossfade=true` ووجود
     * مقطع جارٍ بنافذة مفعّلة يُبدأ انتقال مزدوج (الخارج يخفت والداخل يصعد)
     * بدل القطع الصلب — وإلا يُنفَّذ التبديل القديم حرفيًا.
     */
    fun play(file: File, crossfade: Boolean) {
        switchTo = file
        switchCrossfade = crossfade
        // سعي معلّق موجّه للمقطع القديم يُبطَل هنا — فلا يتسرب إلى المقطع
        // الجديد إن سبق العامل إلى فتحه (عقد السعي v0.27.0)
        seekRequest = -1L
        paused = false
        stopped = false
        _isPaused.value = false
        _error.value = null
        ensureWorker()
    }

    fun toggle() { if (paused) resume() else pause() }

    fun pause() {
        if (_hasTrack.value && !_isPaused.value) {
            paused = true
            _isPaused.value = true
        }
    }

    fun resume() {
        if (paused) {
            paused = false
            _isPaused.value = false
        }
    }

    fun stop() {
        stopped = true
        paused = false
        _isPaused.value = false
    }

    // ================= استعادة الجلسة الأخيرة (v0.32.0) =================

    /**
     * الموضع المحفوظ لاستعادة الجلسة — يستهلكه العامل فور فتح المقطع ثم يُصفّر.
     * (−1 = لا استعادة — تشغيل عادي من الصفر).
     */
    @Volatile private var restoreAtMs = -1L

    /**
     * تحميل مقطع موقوفًا عند موضع محفوظ (v0.32.0 «استعادة الجلسة الأخيرة
     * كاملة… والموضع»): يفتح القارئ ويسقي إلى الموضع ويجمّد الخط دون أي صوت
     * — الضغطة الأولى على التشغيل تكمل من حيث توقف المذيع لا من الصفر.
     * يُستدعى عند الإقلاع فقط قبل أي تشغيل، ولا يبدأ صوتًا إطلاقًا.
     */
    fun preparePaused(file: File, positionMs: Long): Boolean {
        if (!file.isFile) return false
        restoreAtMs = positionMs.coerceAtLeast(0L)
        switchTo = file
        switchCrossfade = false
        seekRequest = -1L
        paused = true
        stopped = false
        _isPaused.value = true
        _error.value = null
        ensureWorker()
        return true
    }

    /**
     * السعي (v0.27.0 «شريط التقدم لا يعمل جيدًا»): مع طلب السعي للعامل،
     * يُحدّث العرض المتفائل فورًا — فالشريط والزمن يبقيان حيث أفلت المستخدم
     * إصبعه من لحظة الإفلات، بدل الارتداد للموضع القديم حتى يستهلك العامل
     * الطلب (كان يظهر وميضًا خلفيًا أثناء التشغيل، وارتدادًا كاملًا أثناء
     * الإيقاف المؤقت لأن readChunk لا يعمل وقتها). العامل يُرسي العرض لاحقًا
     * على موضع القارئ الفعلي بعد تنفيذ السعي.
     */
    fun seek(ms: Long) {
        if (_hasTrack.value) {
            val v = ms.coerceAtLeast(0L)
            seekRequest = v
            _positionMs.value = v
        }
    }

    fun setVolume(v: Float) {
        _volume.value = v.coerceIn(0f, 1f)
        volumeDirty = true
    }

    /** انتظار توقف الخيط العامل (لحذف/إعادة تسمية ملف قيد التشغيل). */
    fun awaitIdle(timeoutMs: Long = 1500) {
        val w = worker
        w?.join(timeoutMs)
    }

    private fun ensureWorker() {
        synchronized(workerLock) {
            if (worker?.isAlive == true) return
            worker = thread(name = "music-player", isDaemon = true) { run() }
        }
    }

    private fun run() {
        var reader: TrackReader? = null
        // كروس فيد (v0.31.0): القارئ الخارج يبقى حيًّا خلال النافذة يخفت على
        // كسب خطه بينما القارئ الجديد (reader) يصعد على كسب خطه — قناة المزج
        // المشتركة تبقى كسبًا كاملًا فلا يُلمس المعادل ولا الخفض ولا مرجع الصدى
        var xOut: TrackReader? = null
        var xStartNanos = 0L
        var xMs = 0L
        var xAsked = false // سألنا عن «التالي» لهذا المقطع؟ — سؤال واحد لكل مقطع
        var wasPaused = false
        try {
            while (!stopped) {
                val sw = switchTo
                if (sw != null) {
                    switchTo = null
                    val wantXf = switchCrossfade && crossfadeMs > 0L && reader != null && !paused && xOut == null
                    switchCrossfade = false
                    if (wantXf) {
                        val old = reader!!
                        try {
                            val nr = openReader(sw)
                            xAsked = false
                            _currentFile.value = sw
                            _title.value = sw.nameWithoutExtension
                            _hasTrack.value = true
                            _durationMs.value = nr.durationMs
                            _positionMs.value = 0L
                            _isPlaying.value = true
                            _isPaused.value = false
                            reopenOutput = false // فُتح للتو بالمخرج الحالي — لا حاجة لإعادة
                            channel.resetEnv()
                            xOut = old
                            xStartNanos = System.nanoTime()
                            xMs = crossfadeMs
                            _xfading.value = true
                            reader = nr
                            val committed = onCrossfadeCommitted
                            if (committed != null) committed(sw)
                        } catch (t: Throwable) {
                            // فشل فتح الجديد: القديم يواجه تشغيله بكسبه الكامل
                            _error.value = t.message ?: "تعذّر تشغيل الملف"
                            try { reader?.applyVolume(_volume.value) } catch (_: Exception) { }
                            val failedCb = onPlayFailed
                            if (failedCb != null) failedCb(sw)
                        }
                    } else {
                        try { reader?.close() } catch (_: Exception) { }
                        // التبديل الصلب يلغي أي كروس فيد جارٍ فورًا
                        if (xOut != null) {
                            try { xOut?.close() } catch (_: Exception) { }
                            xOut = null
                            _xfading.value = false
                        }
                        reader = null
                        wasPaused = false
                        try {
                            reader = openReader(sw)
                            _currentFile.value = sw
                            _title.value = sw.nameWithoutExtension
                            _hasTrack.value = true
                            _durationMs.value = reader.durationMs
                            // استعادة الجلسة (v0.32.0): فتح موقوفًا عند الموضع المحفوظ —
                            // القارئ يسقي إلى الموضع ويجمّد، وwasPaused=true فلا مسار
                            // تلاشٍ ولا صوت يخرج حتى أول استئناف من المذيع
                            val restoreTo = restoreAtMs
                            restoreAtMs = -1L
                            if (restoreTo >= 0L) {
                                if (restoreTo > 0L) {
                                    try { reader.seek(restoreTo) } catch (_: Exception) { }
                                }
                                _positionMs.value = reader.currentMs
                                try { reader.pause() } catch (_: Exception) { }
                                paused = true
                                wasPaused = true
                                // عقد المحرك: isPlaying يبقى true حتى نهاية المقطع —
                                // حالة «الآن» تُحسب بـ playing && !paused فالصمت إيقاف مؤقت لا توقف كامل
                                _isPlaying.value = true
                                _isPaused.value = true
                            } else {
                                _positionMs.value = 0L
                                _isPlaying.value = true
                                _isPaused.value = false
                            }
                            xAsked = false
                            reopenOutput = false
                            channel.resetEnv()
                        } catch (t: Throwable) {
                            reader = null
                            _hasTrack.value = false
                            _isPlaying.value = false
                            _error.value = t.message ?: "تعذّر تشغيل الملف"
                            val failedCb = onPlayFailed
                            if (failedCb != null) failedCb(sw)
                            break
                        }
                    }
                }
                // صيانة الكروس فيد الجاري: كسب الخطوط كل دورة + إغلاق الخارج عند اكتمال النافذة
                val xo = xOut
                if (xo != null) {
                    val elapsed = (System.nanoTime() - xStartNanos) / 1_000_000L
                    val outG = CrossfadeLogic.fadeOutGain(elapsed, xMs)
                    val inG = CrossfadeLogic.fadeInGain(elapsed, xMs)
                    try { xo.applyVolume(_volume.value * outG) } catch (_: Exception) { }
                    try { reader?.applyVolume(_volume.value * inG) } catch (_: Exception) { }
                    val moreX = try {
                        xo.readChunk()
                    } catch (_: Exception) { false }
                    if (!moreX || outG <= 0f) {
                        try { xo.close() } catch (_: Exception) { }
                        xOut = null
                        _xfading.value = false
                        // الداخل يواجه كسبًا كاملًا فور انقضاء النافذة
                        try { reader?.applyVolume(_volume.value) } catch (_: Exception) { }
                        channel.resetEnv()
                    }
                }
                // تبديل حي للمخرج: إعادة فتح سلسة تحفظ الموضع الحالي —
                // ويُجهض أي كروس فيد جارٍ أولًا فلا يبقى خارج معلّق على خط قديم
                if (xOut != null && reopenOutput) {
                    try { xOut?.close() } catch (_: Exception) { }
                    xOut = null
                    _xfading.value = false
                    try { reader?.applyVolume(_volume.value) } catch (_: Exception) { }
                    channel.resetEnv()
                }
                if (reopenOutput) {
                    reopenOutput = false
                    val cf = _currentFile.value
                    val r0 = reader
                    if (r0 != null && cf != null) {
                        val pos = r0.currentMs
                        val wasPaused = paused
                        try { r0.close() } catch (_: Exception) { }
                        reader = try {
                            val nr = openReader(cf)
                            if (pos > 0) {
                                try { nr.seek(pos) } catch (_: Exception) { }
                            }
                            if (wasPaused) nr.pause()
                            channel.resetEnv()
                            xAsked = false
                            nr
                        } catch (t: Throwable) {
                            // الجهاز الجديد فاشل: نعود للسماعة الافتراضية
                            outputMain = null
                            outputSecond = null
                            try {
                                val nr = openReader(cf)
                                if (pos > 0) {
                                    try { nr.seek(pos) } catch (_: Exception) { }
                                }
                                if (wasPaused) nr.pause()
                                channel.resetEnv()
                                xAsked = false
                                nr
                            } catch (t2: Throwable) {
                                _error.value = t2.message
                                break
                            }
                        }
                    }
                }
                val r = reader ?: break

                val sq = seekRequest
                if (sq >= 0) {
                    seekRequest = -1L
                    try { r.seek(sq) } catch (_: Exception) { }
                    // ترسيخ العرض على موضع القارئ الفعلي بعد تنفيذ السعي —
                    // وهذا ما يجعل السعي أثناء الإيقاف المؤقت يُظهر الموضع الجديد
                    // فورًا بدل الارتداد للقديم. وإن ورد سعي أحدث قبل الاكتمال
                    // فالقيمة المتفائلة له تبقى (آخر نقرة تفوز).
                    if (seekRequest < 0L) _positionMs.value = r.currentMs
                }
                if (volumeDirty) {
                    volumeDirty = false
                    try { r.applyVolume(_volume.value) } catch (_: Exception) { }
                }

                if (paused) {
                    // الإيقاف أثناء كروس فيد: يُجهض الانتقال فورًا فلا يبقى
                    // خارج يسمع وحده بعد تجميد الداخل — ثم مسار الإيقاف الناعم كالمعتاد
                    if (xOut != null) {
                        try { xOut?.close() } catch (_: Exception) { }
                        xOut = null
                        _xfading.value = false
                        try { r.applyVolume(_volume.value) } catch (_: Exception) { }
                        channel.resetEnv()
                    }
                    if (!wasPaused) {
                        // تلاشٍ ناعم للخروج قبل التجميد (v0.29.0): نكمل التفريغ على
                        // ساعة الحائط مع نزول الغلاف في مسار process، وأي أمر أحدث
                        // (سعي/تبديل/إيقاف) يجهض التلاشٍ فورًا فلا يتأخر الوبّ لأحد
                        var endedDuringFade = false
                        channel.beginFade(0f, FADE_OUT_MS)
                        val deadline = System.nanoTime() + FADE_OUT_MS * 1_000_000L
                        while (System.nanoTime() < deadline && !stopped && switchTo == null && seekRequest < 0) {
                            val more = try {
                                r.readChunk()
                            } catch (t: Throwable) {
                                _error.value = t.message
                                false
                            }
                            if (more) {
                                // حماية عقد السعي (v0.29.0): لا كتلة قيد التنفيذ تطغى
                                // على العرض المتفائل حين طلب السعي حديثًا
                                if (seekRequest < 0L) _positionMs.value = r.currentMs
                            } else {
                                endedDuringFade = true
                                break
                            }
                        }
                        try { r.pause() } catch (_: Exception) { }
                        wasPaused = true
                        if (endedDuringFade) {
                            // انتهى المقطع أثناء التلاشٍ: عقد الانتهاء نفسه في المسار الرئيسي
                            _isPlaying.value = false
                            val cb = onEnded
                            if (cb != null) cb()
                            if (switchTo == null || stopped) break
                            continue
                        }
                    }
                    Thread.sleep(40)
                    continue
                }
                if (wasPaused) {
                    try { r.resume() } catch (_: Exception) { }
                    // صعود ناعم عند الاستئناف (v0.29.0): الغلاف من حيث توقف إلى الكامل
                    channel.beginFade(1f, FADE_IN_MS)
                    wasPaused = false
                }

                val more = try {
                    r.readChunk()
                } catch (t: Throwable) {
                    _error.value = t.message
                    false
                }
                if (more) {
                    // الحماية نفسها في المسار الرئيسي: طلب سعي حديث يفوز على أي تحديث كتلة
                    if (seekRequest < 0L) _positionMs.value = r.currentMs
                    // التمديد الآلي (v0.31.0): المتبقي داخل النافذة → اسأل عن التالي
                    // مرة واحدة لهذا المقطع، وإن وُجد يبدأ انتقال كروس فيد حقيقي:
                    // القارئ الحالي يصير خارجًا يخفت والجديد يصير reader يصعد
                    if (xOut == null && crossfadeMs > 0L && !xAsked && r.durationMs > 0L) {
                        val remaining = r.durationMs - r.currentMs
                        if (CrossfadeLogic.shouldTrigger(remaining, crossfadeMs)) {
                            xAsked = true
                            val nf = try { onCrossfadeNext?.invoke() } catch (_: Exception) { null }
                            if (nf != null && nf != _currentFile.value) {
                                try {
                                    val nr = openReader(nf)
                                    xAsked = false
                                    _currentFile.value = nf
                                    _title.value = nf.nameWithoutExtension
                                    _durationMs.value = nr.durationMs
                                    _positionMs.value = 0L
                                    reopenOutput = false
                                    channel.resetEnv()
                                    xOut = r
                                    xStartNanos = System.nanoTime()
                                    xMs = crossfadeMs
                                    _xfading.value = true
                                    reader = nr
                                    val committed = onCrossfadeCommitted
                                    if (committed != null) committed(nf)
                                    continue // الدورة القادمة تقرأ الداخل وتصوني الخارج
                                } catch (t: Throwable) {
                                    _error.value = t.message
                                    val failedCb = onPlayFailed
                                    if (failedCb != null) failedCb(nf)
                                    // القديم يكمل؛ المحاولة تعاد عند اقتراب النهاية من جديد
                                    xAsked = false
                                }
                            }
                        }
                    }
                } else {
                    _isPlaying.value = false
                    val cb = onEnded
                    if (cb != null) cb()
                    if (switchTo == null || stopped) break
                }
            }
        } catch (t: Throwable) {
            _error.value = t.message ?: "خطأ غير متوقع في المشغّل"
        } finally {
            try { reader?.close() } catch (_: Exception) { }
            try { xOut?.close() } catch (_: Exception) { }
            if (stopped) {
                _isPlaying.value = false
                _isPaused.value = false
                _hasTrack.value = false
                _currentFile.value = null
                _title.value = null
                _positionMs.value = 0L
                _durationMs.value = 0L
                _xfading.value = false
            }
            synchronized(workerLock) { worker = null }
        }
    }

    /**
     * خط حقن الاختبارات والفحص الذاتي: قارئ بديل بلا عتاد صوتي — يُستدعى مرة
     * عند كل فتح مقطع ما دام غير صفري، ويُصفَّر بعد الاختبار. في الإنتاج يبقى
     * null دائمًا فلا يسري أثره إطلاقًا.
     */
    @Volatile internal var trackReaderOverride: ((File) -> TrackReader)? = null

    /** مدتا التلاشٍ الناعم (v0.29.0): خروج 350ms عند الإيقاف المؤقت وصعود 300ms عند الاستئناف. */
    private val FADE_OUT_MS = 350L
    private val FADE_IN_MS = 300L

    private fun openReader(file: File): TrackReader {
        trackReaderOverride?.let { return it(file) }
        val name = file.name.lowercase()
        val onSecondFail: (Throwable) -> Unit = { t: Throwable ->
            _error.value = "المخرج الثاني: " + AudioDevices.describeOutputFailure(t)
        }
        // v0.49.0 «الصيغ المدعومة»: عائلة MPEG كلها (1/2/2.5 — Layer 1/2/3 وVBR)
        // عبر قارئ MP3 القوي بعائله المحكم، وعائلة AAC (M4A/ADTS) عبر قارئ PCM
        // مع القارئ الصريح لـjaad، وصيغ غير مدعومة بعد برسالة صادقة تسمّي المدعوم
        val mp3Family = name.endsWith(".mp3") || name.endsWith(".mp2") ||
            name.endsWith(".mpga") || name.endsWith(".mpeg") || name.endsWith(".mpa")
        val aacFamily = name.endsWith(".aac") || name.endsWith(".m4a") ||
            name.endsWith(".m4b") || name.endsWith(".mp4")
        val notYetSupported = name.endsWith(".opus") || name.endsWith(".wma") ||
            name.endsWith(".amr") || name.endsWith(".ac3") || name.endsWith(".dts") ||
            name.endsWith(".wv") || name.endsWith(".ape") || name.endsWith(".mka")
        return when {
            mp3Family -> Mp3TrackReader(file, outputMain, outputSecond, { _volume.value }, onSecondFail, channel)
            aacFamily -> PcmTrackReader(file, outputMain, outputSecond, { _volume.value }, onSecondFail, channel)
            notYetSupported -> throw IllegalArgumentException(
                "صيغة «${file.extension.lowercase()}» غير مدعومة بعد في هذه النسخة. $SUPPORTED_FORMATS_HINT"
            )
            else -> PcmTrackReader(file, outputMain, outputSecond, { _volume.value }, onSecondFail, channel)
        }
    }
}
