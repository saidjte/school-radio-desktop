package com.saidjte.schoolradio.desktop.audio

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
val PLAYABLE_EXTENSIONS = setOf("wav", "mp3", "ogg", "oga", "flac", "aif", "aiff", "au")

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
    private var monoBuf = FloatArray(4096)
    private val shorts = ShortArray(1 shl 15)

    /** مستوى الكتلة الأخير (منمّط 0..1) — يُستدعى من خيط التشغيل. */
    var onLevel: ((Float) -> Unit)? = null

    internal fun configure(sampleRate: Int, channels: Int) {
        eq.configure(sampleRate, channels)
        this.channels = channels.coerceIn(1, 2)
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
        // الخفض التلقائي: كسب الموسيقى يقرأ من ناقل الخفض قبل الفيدر والماستر
        val duck = if (DuckBus.enabled) DuckBus.musicGain.coerceIn(0f, 1f) else 1f
        val gain = if (muted) 0f else fader * master * duck
        val level = eq.process(target, count, gain)
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
    private val fh = info?.first
    private val format: AudioFormat
    private val out: PcmOutLine
    private var stream: BufferedInputStream
    private var bitstream: Bitstream
    private val decoder = Decoder()
    private var framesDone = 0L
    private var currentMsValue = 0L

    override val durationMs: Long = info?.durationMs ?: 0L
    override val currentMs: Long get() = currentMsValue

    init {
        val i = info ?: throw IllegalArgumentException("تعذّر تحليل ملف MP3: ${file.name}")
        val f = fh!!
        stream = BufferedInputStream(FileInputStream(file), 1 shl 16)
        Mp3Info.skipId3v2(stream)
        bitstream = Bitstream(stream)
        format = AudioFormat(f.sampleRate.toFloat(), 16, f.channels, true, false)
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
        val i = info ?: return
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
        // - معدل الإطار الأول (CBR): التحويل الخطي مباشر
        val jumpMs: Long
        val toc = i.toc
        val dataBytes = i.dataBytes.coerceAtLeast(0L)
        if (toc != null && toc.size >= 100 && durationMs > 0 && dataBytes > 0) {
            val frac = ((target - 500L).coerceAtLeast(0L)).toFloat() / durationMs.toFloat()
            val idx = (frac * 100).toInt().coerceIn(0, 99)
            val byteFrac = (toc[idx].toInt() and 0xFF) / 255f
            jumpMs = idx.toLong() * durationMs / 100L
            skipBytes(s, (byteFrac * dataBytes).toLong())
        } else {
            val avgKbps = if (i.isVbr && durationMs > 0 && dataBytes > 0)
                (dataBytes * 8L / durationMs).toInt().coerceAtLeast(1)
            else f.bitRateKbps
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
        val raw = openAudioInputStream(file)
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
        durationMs = when {
            fl > 0 && pcmFormat.frameRate > 0 -> (fl * 1000L / pcmFormat.frameRate.toLong())
            wavInfo != null -> WavFileWriter.durationMsOf(
                wavInfo.dataBytes, wavInfo.sampleRate, wavInfo.channels, wavInfo.bitsPerSample
            )
            else -> 0L
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
 * OGG عبر قارئ Vorbis الصريح، FLAC عبر قارئ Flac الصريح، والباقي عبر AudioSystem.
 */
internal fun openAudioInputStream(file: File): AudioInputStream {
    val name = file.name.lowercase()
    return when {
        name.endsWith(".ogg") || name.endsWith(".oga") ->
            VorbisAudioFileReader().getAudioInputStream(file)
        name.endsWith(".flac") ->
            FlacAudioFileReader().getAudioInputStream(file)
        else -> AudioSystem.getAudioInputStream(file)
    }
}

/**
 * تقدير مدة ملف صوتي بفتح المجرى وقراءة طوله الإطاري —
 * للأصوات غير MP3/WAV (OGG/FLAC/AIFF/AU) في قوائم العرض. يعيد 0 إن تعذّر.
 */
internal object StreamProbe {
    fun durationMsOf(file: File): Long = try {
        openAudioInputStream(file).use { ais ->
            val fl = ais.frameLength
            val fr = ais.format.frameRate
            if (fl > 0 && fr > 0) (fl * 1000L / fr.toLong()) else 0L
        }
    } catch (_: Exception) { 0L }
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
    @Volatile private var seekRequest = -1L
    @Volatile private var volumeDirty = false

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

    fun play(file: File) {
        switchTo = file
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
        var wasPaused = false
        try {
            while (!stopped) {
                val sw = switchTo
                if (sw != null) {
                    switchTo = null
                    try { reader?.close() } catch (_: Exception) { }
                    reader = null
                    wasPaused = false
                    try {
                        reader = openReader(sw)
                        _currentFile.value = sw
                        _title.value = sw.nameWithoutExtension
                        _hasTrack.value = true
                        _durationMs.value = reader.durationMs
                        _positionMs.value = 0L
                        _isPlaying.value = true
                        _isPaused.value = false
                        reopenOutput = false // فُتح للتو بالمخرج الحالي — لا حاجة لإعادة
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
                // تبديل حي للمخرج: إعادة فتح سلسة تحفظ الموضع الحالي
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
                    if (!wasPaused) {
                        try { r.pause() } catch (_: Exception) { }
                        wasPaused = true
                    }
                    Thread.sleep(40)
                    continue
                }
                if (wasPaused) {
                    try { r.resume() } catch (_: Exception) { }
                    wasPaused = false
                }

                val more = try {
                    r.readChunk()
                } catch (t: Throwable) {
                    _error.value = t.message
                    false
                }
                if (more) {
                    _positionMs.value = r.currentMs
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
            if (stopped) {
                _isPlaying.value = false
                _isPaused.value = false
                _hasTrack.value = false
                _currentFile.value = null
                _title.value = null
                _positionMs.value = 0L
                _durationMs.value = 0L
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

    private fun openReader(file: File): TrackReader {
        trackReaderOverride?.let { return it(file) }
        val name = file.name.lowercase()
        val onSecondFail: (Throwable) -> Unit = { t: Throwable ->
            _error.value = "المخرج الثاني: " + AudioDevices.describeOutputFailure(t)
        }
        return if (name.endsWith(".mp3")) {
            Mp3TrackReader(file, outputMain, outputSecond, { _volume.value }, onSecondFail, channel)
        } else {
            PcmTrackReader(file, outputMain, outputSecond, { _volume.value }, onSecondFail, channel)
        }
    }
}
