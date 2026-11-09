package com.saidjte.schoolradio.desktop.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.Mixer
import kotlin.concurrent.thread

/**
 * محرك المعاينة (v0.42.0 «الاستماع قبل الإضافة») — صوت واحد في كل لحظة:
 * نقر زر التشغيل على بطاقة تُطلقها، والنقر الثاني أو نقر بطاقة أخرى يوقف
 * الجارية ويطلق الجديدة — فلا تتكدس المعاينات ولا تتنافس مع القائمة.
 *
 * النمط نفسه المجرَّب في JingleWallEngine: خيط واحد يملك قارئه وخطه،
 * وأوامر متطايرة — بلا أقفال في المسار الساخن وبلا سباقات. المعاينة
 * لا تمر بقناة المزج عمدًا: إنها سماع سريع قبل القرار لا عنصر بث.
 */
class SoundPreviewEngine(
    /** لقطة مخرجَي التشغيل المختارين — المعاينة تشارك سماعات الموسيقى نفسها. */
    private val outputs: () -> Pair<Mixer.Info?, Mixer.Info?> = { null to null }
) {

    internal interface Voice : AutoCloseable {
        fun readChunk(): Boolean
    }

    private class Run internal constructor(
        val file: File,
        val voice: Voice,
        val engine: SoundPreviewEngine
    ) {
        @Volatile var stopRequested = false
        val thread: Thread = thread(name = "sound-preview", isDaemon = true) { engine.voiceLoop(this) }
    }

    @Volatile private var run: Run? = null

    /** الملف الجاري معاينته الآن (null = صمت). */
    private val _playingFile = MutableStateFlow<String?>(null)
    val playingFile: StateFlow<String?> = _playingFile.asStateFlow()

    /** خطأ آخر معاينة (ملف مفقود/فاشل الفتح) — يظهر على البطاقة نفسها. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * خط حقن الاختبارات والفحص الذاتي والفحص البصري: صوت بديل بلا عتاد صوتي.
     * في الإنتاج يبقى null دائمًا فلا يسري أثره إطلاقًا.
     */
    @Volatile internal var voiceFactoryOverride: ((File) -> Voice)? = null

    /**
     * نقر بطاقة: متوقف → إطلاق، والجارية ملفًا آخر → إيقاف ثم إطلاق،
     * والجارية نفس الملف → إيقاف فحسب.
     */
    fun toggle(file: File) {
        val cur = run
        if (cur != null) {
            val same = cur.file.absolutePath == file.absolutePath
            stopInternal()
            if (same) return
        }
        start(file)
    }

    fun stop() {
        stopInternal()
    }

    /** انتظار انقضاء المعاينة (الاختبارات وإغلاق التطبيق). */
    fun awaitIdle(timeoutMs: Long = 3000) {
        val r = run ?: return
        val deadline = System.currentTimeMillis() + timeoutMs
        val remain = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
        try { r.thread.join(remain) } catch (_: InterruptedException) { }
    }

    fun shutdown() {
        stopInternal()
        awaitIdle()
    }

    // ================= الداخلية =================

    private fun stopInternal() {
        run?.let {
            it.stopRequested = true
            try { it.voice.close() } catch (_: Exception) { }
        }
        run = null
        _playingFile.value = null
    }

    private fun start(file: File) {
        try {
            val voice = voiceFactoryOverride?.invoke(file) ?: run {
                val (main, second) = outputs()
                if (file.name.lowercase().endsWith(".mp3")) {
                    val one = Mp3OneShot(file)
                    openAndWrite(one, one.format, main, second)
                } else {
                    val one = PcmOneShot(file)
                    openAndWrite(one, one.format, main, second)
                }
            }
            val r = Run(file, voice, this)
            run = r
            _lastError.value = null
            _playingFile.value = file.absolutePath
        } catch (t: Throwable) {
            _lastError.value = "تعذّر تشغيل المعاينة: ${t.message ?: "خطأ غير معروف"}"
        }
    }

    /**
     * يفتح خط السماع على صيغة الملف ويعيد صوتًا يكتب فيه الكتل — الفصل
     * بين الفتح (فاشل مبكرًا بخط واضح) والحلقة (خيط المعاينة).
     */
    private fun openAndWrite(
        src: Closeable,
        f: AudioFormat,
        main: Mixer.Info?,
        second: Mixer.Info?
    ): Voice {
        val line = AudioDevices.openOutLine(main, second, f, null)
        line.open(f, 16 * 1024)
        line.start()
        return object : Voice {
            override fun readChunk(): Boolean =
                if (src is Mp3OneShot) src.readChunkInto(line) else (src as PcmOneShot).readChunkInto(line)
            override fun close() {
                try { src.close() } catch (_: Exception) { }
                try { line.close() } catch (_: Exception) { }
            }
        }
    }

    private fun voiceLoop(r: Run) {
        try {
            while (!r.stopRequested) {
                val more = try {
                    r.voice.readChunk()
                } catch (t: Throwable) {
                    _lastError.value = "انقطعت المعاينة: ${t.message ?: "خطأ غير معروف"}"
                    false
                }
                if (!more) break
            }
        } finally {
            try { r.voice.close() } catch (_: Exception) { }
            if (run === r) {
                run = null
                _playingFile.value = null
            }
        }
    }
}

/**
 * صوت معاينة اختباري بلا عتاد: كتل زمنية خالصة تنتهي وحدها — للفحص الذاتي
 * والفحص البصري تحت Xvfb حيث لا توجد سماعات أصلًا (عُرف FakeToneVoice).
 */
internal class FakePreviewVoice(
    private val chunks: Int = 40,
    private val chunkMs: Long = 20
) : SoundPreviewEngine.Voice {
    @Volatile private var closed = false
    @Volatile var remaining = chunks
        private set

    override fun readChunk(): Boolean {
        if (closed || remaining <= 0) return false
        remaining--
        Thread.sleep(chunkMs)
        return remaining > 0
    }

    override fun close() {
        closed = true
    }
}
