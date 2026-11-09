package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.data.JingleWallLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.Mixer
import kotlin.concurrent.thread

/**
 * جدار الجينغل (v0.28.0 «أضف جدار الجينغل»): اثنا عشر موضعًا يقفز كل واحد
 * بنقرة — صوتٌ قصير يُطلق من الصفر ويتوقف بالنقر الثانية، ويمكن لمواضع
 * عدة أن تتعادل في نفس اللحظة (كالجدران الإذاعية الحقيقية) دون أن يمس بعضها.
 *
 * النمط نفسه المجرَّب في MusicPlayerEngine: خيط لكل صوت يملك قارئه وخطه،
 * وأوامر متطايرة (إيقاف/مستوى) — بلا أقفال في المسار الساخن وبلا سباقات.
 * الجينلات لا تمرّ بقناة المزج (معادل/دكّة/مرجع صدى) عمدًا: هي إشارة برنامج
 * مستقلة بمستوى عام واحد يُحفظ بين الجلسات.
 */
class JingleWallEngine(
    /** لقطة مخرجَي التشغيل المختارين — الجينلات تشارك سماعات الموسيقى نفسها. */
    private val outputs: () -> Pair<Mixer.Info?, Mixer.Info?> = { null to null }
) {

    /** صوت جينل واحد: فك من البداية حتى النهاية بلا سعي ولا إيقاف مؤقت. */
    internal interface Voice : AutoCloseable {
        /** يفكّ الكتلة التالية ويكتبها في خط السماع. يعيد false عند النهاية. */
        fun readChunk(): Boolean

        /** تحديث الكسب حيًّا أثناء التشغيل. */
        fun applyVolume(v: Float)
    }

    private class Run internal constructor(
        val slot: Int,
        val voice: Voice,
        val engine: JingleWallEngine
    ) {
        @Volatile var stopRequested = false
        @Volatile var lastSeenVolume = Long.MIN_VALUE
        val thread: Thread = thread(name = "jingle-$slot", isDaemon = true) { engine.voiceLoop(this) }
    }

    private val runs = ConcurrentHashMap<Int, Run>()

    private val _playing = MutableStateFlow<Set<Int>>(emptySet())
    val playing: StateFlow<Set<Int>> = _playing.asStateFlow()

    /** خطأ آخر موضع (ملف مفقود/فاشل الفتح) — يظهر على البطاقة نفسها. */
    private val _errors = MutableStateFlow<Map<Int, String>>(emptyMap())
    val errors: StateFlow<Map<Int, String>> = _errors.asStateFlow()

    private val _volume = MutableStateFlow(JingleWallLogic.DEFAULT_VOLUME)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    /** إصدار المستوى: كل تغيير يرفع العداد فتعرف الأصوات الحية أن تعيد ضبط الكسب. */
    @Volatile private var volumeVersion = 0L

    /**
     * خط حقن الاختبارات والفحص الذاتي والفحص البصري: صوت بديل بلا عتاد صوتي.
     * في الإنتاج يبقى null دائمًا فلا يسري أثره إطلاقًا.
     */
    @Volatile internal var voiceFactoryOverride: ((File) -> Voice)? = null

    /**
     * النقر على موضع: قيد التشغيل → يتوقف، متوقف → يُطلق من الصفر.
     * نقرة سريعة متكررة تعيد الإطلاق نظيفًا (الأصوات القديمة تُغلق فورًا).
     */
    fun toggle(slot: Int, file: File) {
        if (slot !in 0 until JingleWallLogic.PAD_COUNT) return
        val existing = runs[slot]
        if (existing != null) {
            stopRun(existing)
            return
        }
        if (voiceFactoryOverride == null && !file.isFile) {
            // حارس رسالة المستخدم في الإنتاج فقط — وضع الحقن (الاختبارات والفحص)
            // يمرر مسارات وهمية كما في عقد مشغل الموسيقى تمامًا
            _errors.value = _errors.value + (slot to "الملف غير موجود")
            return
        }
        start(slot, file)
    }

    /** إيقاف موضع بعينه (عند مسحه أو تعيين مقطع جديد فوقه). */
    fun stop(slot: Int) {
        runs[slot]?.let { stopRun(it) }
    }

    /** إيقاف كل الأصوات الجارية دفعة واحدة. */
    fun stopAll() {
        runs.values.forEach { stopRun(it) }
        _playing.value = emptySet()
    }

    /**
     * جينل الفاصل الآلي (v0.31.0 «مؤقت فاصل آلي»): إطلاق صوت تنبيه خارج
     * شبكة المواضع الاثني عشر — مؤقت الفقرات يطلقه لحظة بلوغ الزمن المخطط.
     * إطلاق جديد يوقف السابق فلا تتكدس الفواصل، ويُلغى مع «إيقاف الكل».
     */
    fun sting(file: File) {
        if (voiceFactoryOverride == null && !file.isFile) return
        runs[STING_SLOT]?.let { stopRun(it) }
        start(STING_SLOT, file)
    }

    /** موضع الفاصل الآلي خارج شبكة المواضع الظاهرة. */
    companion object {
        const val STING_SLOT = 100
    }

    /** المستوى العام للجينلات — يسري على الأصوات الجارية في الكتلة التالية. */
    fun setVolume(v: Float) {
        _volume.value = JingleWallLogic.clampVolume(v)
        volumeVersion++
    }

    /** انتظار انقضاء كل الأصوات (الاختبارات وإغلاق التطبيق). */
    fun awaitIdle(timeoutMs: Long = 3000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        for (r in runs.values.toList()) {
            val remain = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
            try { r.thread.join(remain) } catch (_: InterruptedException) { }
        }
    }

    /** إغلاق نهائي عند خروج التطبيق. */
    fun shutdown() {
        stopAll()
        awaitIdle()
    }

    // ================= الداخلية =================

    private fun start(slot: Int, file: File) {
        // احتياط: موضع عالق قديم يُوقف قبل الإطلاق الجديد
        runs[slot]?.let { stopRun(it) }
        try {
            val (main, second) = outputs()
            val voice = voiceFactoryOverride?.invoke(file)
                ?: JingleVoice(file, main, second, _volume.value)
            val run = Run(slot, voice, this)
            runs[slot] = run
            _errors.value = _errors.value - slot
            _playing.value = runs.keys.toSet()
        } catch (t: Throwable) {
            _errors.value = _errors.value + (slot to (t.message ?: "تعذّر تشغيل الجينل"))
        }
    }

    private fun stopRun(run: Run) {
        run.stopRequested = true
        try { run.voice.close() } catch (_: Exception) { }
        _playing.value = _playing.value - run.slot
    }

    /** حلقة الصوت — تعمل على خيط الـRun وحده. */
    private fun voiceLoop(run: Run) {
        try {
            while (!run.stopRequested) {
                val ver = volumeVersion
                if (ver != run.lastSeenVolume) {
                    run.lastSeenVolume = ver
                    try { run.voice.applyVolume(_volume.value) } catch (_: Exception) { }
                }
                val more = try {
                    run.voice.readChunk()
                } catch (t: Throwable) {
                    _errors.value = _errors.value + (run.slot to (t.message ?: "انقطع تشغيل الجينل"))
                    false
                }
                if (!more) break
            }
        } finally {
            try { run.voice.close() } catch (_: Exception) { }
            runs.remove(run.slot, run)
            _playing.value = runs.keys.toSet()
        }
    }
}

/**
 * صوت جينل حقيقي فوق عتاد النظام: MP3 بفاكّ JLayer إطارًا بإطار،
 * وغيره (WAV/OGG/FLAC/AIFF/AU) بمجرى PCM موحَّد 16-bit —
 * والكتابة في PcmOutLine على مخرجَي التطبيق المختارين.
 */
internal class JingleVoice(
    file: File,
    outMain: Mixer.Info?,
    outSecond: Mixer.Info?,
    initialVolume: Float
) : JingleWallEngine.Voice {

    private val out: PcmOutLine
    private val mp3: Mp3OneShot?
    private val pcm: PcmOneShot?

    /** المستوى العام برمحيًا (v0.31.0) — يطابق ما يُسجّل في مزيج الحصة. */
    @Volatile private var gain = initialVolume.coerceIn(0f, 1f)

    init {
        val f: AudioFormat
        if (file.name.lowercase().endsWith(".mp3")) {
            val one = Mp3OneShot(file)
            mp3 = one
            pcm = null
            f = one.format
            val raw = AudioDevices.openOutLine(outMain, outSecond, f, null)
            raw.open(f, 16 * 1024)
            raw.start()
            out = TappingOut(raw, f.sampleRate.toInt(), f.channels) { gain }
        } else {
            val one = PcmOneShot(file)
            mp3 = null
            pcm = one
            f = one.format
            val raw = AudioDevices.openOutLine(outMain, outSecond, f, null)
            raw.open(f, 16 * 1024)
            raw.start()
            out = TappingOut(raw, f.sampleRate.toInt(), f.channels) { gain }
        }
    }

    override fun readChunk(): Boolean =
        if (mp3 != null) mp3.readChunkInto(out) else pcm!!.readChunkInto(out)

    override fun applyVolume(v: Float) {
        gain = v.coerceIn(0f, 1f)
    }

    override fun close() {
        try { mp3?.close() } catch (_: Exception) { }
        try { pcm?.close() } catch (_: Exception) { }
        try { out.close() } catch (_: Exception) { }
    }
}

/**
 * خط إخراج جينل ملتقط (v0.31.0): يضرب الكتلة بالمستوى البرمحي ثم يغذي
 * ناقل تسجيل المزيج بنفس ما يُكتب في السماعة — فتُسجّل الجينلات
 * بمستواها المسموع فعلاً لا بكسب كامل.
 */
private class TappingOut(
    private val inner: PcmOutLine,
    private val rateHz: Int,
    private val ch: Int,
    private val gain: () -> Float
) : PcmOutLine by inner {

    override fun write(b: ByteArray, off: Int, len: Int) {
        val scaled = applySoftwareGain(b, off, len)
        if (MixBus.recording.value) {
            MixBus.feedJingle(scaled, 0, scaled.size, rateHz, ch)
        }
        inner.write(scaled, 0, scaled.size)
    }

    override fun setVolume(v: Float) {
        // المستوى برمحي عبر gain — كسب الجهاز يبقى محايدًا ليطابق التسجيل السماع
    }

    private fun applySoftwareGain(b: ByteArray, off: Int, len: Int): ByteArray {
        val g = gain()
        val src = if (off == 0) b else ByteArray(len).also { System.arraycopy(b, off, it, 0, len) }
        if (g >= 0.999f) return src
        val shorts = ShortArray(len / 2)
        Pcm16.bytesToShorts(src, shorts.size * 2, shorts)
        for (i in shorts.indices) {
            shorts[i] = (shorts[i] * g).toInt().coerceIn(-32768, 32767).toShort()
        }
        val outB = ByteArray(len)
        Pcm16.shortsToBytes(shorts, shorts.size, outB)
        return outB
    }
}

/** فاكّ MP3 بلا سعي و مجرى PCM موحّد — تعريفهما المشترك في OneShots.kt (v0.42.0). */

/**
 * صوت اختباري بلا عتاد: كتل زمنية خالصة تنتهي وحدها — للاختبارات الوحدوية
 * والفحص الذاتي والفحص البصري تحت Xvfb حيث لا توجد سماعات أصلًا.
 */
internal class FakeToneVoice(private val chunks: Int = 80, private val chunkMs: Long = 25) : JingleWallEngine.Voice {
    @Volatile private var closed = false
    @Volatile var remaining = chunks
        private set
    val volumes = mutableListOf<Float>()
    var chunkHook: (() -> Unit)? = null

    override fun readChunk(): Boolean {
        if (closed || remaining <= 0) return false
        remaining--
        chunkHook?.invoke()
        Thread.sleep(chunkMs)
        return remaining > 0
    }

    override fun applyVolume(v: Float) {
        volumes += v
    }

    override fun close() {
        closed = true
    }
}
