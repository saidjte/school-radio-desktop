package com.saidjte.schoolradio.desktop.youtube

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicBoolean

/**
 * خدمة استيراد المقاطع من اليوتيوب — كل التفاصيل محلية والأداة مدمجة:
 * 1) جلب بيانات المقطع أو البحث (yt-dlp -J) — عنوان ومدة وقناة.
 * 2) تحميل أفضل صوت mp4a (AAC) مع تقدم حي.
 * 3) تحويل M4A→MP3 محليًا (JAAD+LAME) فتدخل المكتبة بأضمن صيغة للمحرك.
 * الدوال مُعطِّلة (تُستدعى من خيط عمل في AppViewModel) والحالة تنبض عبر StateFlow.
 */
class YouTubeImportService(
    private val locator: YtDlpLocator,
    /** مجلد العمل والمخرجات النهائية (MP3) — عادةً SchoolRadio/youtube. */
    private val workDir: File
) {

    enum class Phase { IDLE, RESOLVING, RESOLVED, RESULTS, DOWNLOADING, CONVERTING, DONE, FAILED }

    data class State(
        val phase: Phase = Phase.IDLE,
        val percent: Float = 0f,
        val title: String = "",
        val message: String = "",
        val resolved: YouTubeJson.VideoInfo? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<YouTubeJson.VideoInfo>>(emptyList())
    val results: StateFlow<List<YouTubeJson.VideoInfo>> = _results.asStateFlow()

    /** ملف كوكيز اختياري (يُصدَّر من المتصفح) لشبكات يطلب يوتيوب فيها تحققًا إضافيًا. */
    @Volatile var cookiesFile: File? = null

    private val cancelFlag = AtomicBoolean(false)
    @Volatile private var activeProcess: Process? = null

    /** طلب إلغاء: يقطع العملية الجارية ويُنظّف الملفات المؤقتة. */
    fun cancel() {
        cancelFlag.set(true)
        try { activeProcess?.destroy() } catch (_: Exception) { }
    }

    fun reset() {
        _state.value = State()
    }

    /** ضبط حالة فاشلة من الخارج (حاجز أمان في AppViewModel). */
    fun fail(message: String) {
        _state.value = State(Phase.FAILED, message = message)
    }

    fun clearResults() {
        _results.value = emptyList()
    }

    // ================= جلب البيانات: رابط واحد أو بحث =================

    /**
     * جلب بيانات: رابط مقطع → معلوماته كاملة (RESOLVED)، أو نص → نتائج بحث (RESULTS).
     * يرمي IllegalStateException برسالة عربية عند الفشل.
     */
    fun resolve(input: String) {
        val q = input.trim()
        if (q.isEmpty()) throw IllegalStateException("اكتب رابط مقطع أو كلمات للبحث أولًا.")
        cancelFlag.set(false)
        setState(Phase.RESOLVING, message = "جلب المعلومات من يوتيوب…")

        val bin = locator.resolve()
        val json = try {
            val args = mutableListOf(bin.binary.path, "-J", "--no-warnings", "--no-playlist")
            cookiesFile?.takeIf { it.isFile }?.let { args.addAll(listOf("--cookies", it.absolutePath)) }
            when (YouTubeUrl.classify(q)) {
                YouTubeUrl.Kind.VIDEO_URL -> {
                    val id = YouTubeUrl.extractVideoId(q)
                    args.add(if (id != null) YouTubeUrl.watchUrl(id) else q)
                }
                YouTubeUrl.Kind.SEARCH -> args.add("ytsearch8:$q")
            }
            runTool(args, onProgress = null)
        } catch (t: Throwable) {
            _results.value = emptyList()
            setState(Phase.FAILED, message = friendlyMessage(t))
            return
        }

        if (cancelFlag.get()) { setState(Phase.IDLE, message = "أُلغي الطلب"); return }

        if (YouTubeUrl.classify(q) == YouTubeUrl.Kind.VIDEO_URL) {
            val info = YouTubeJson.parseVideo(json)?.copy(
                sourceUrl = YouTubeUrl.extractVideoId(q)?.let { YouTubeUrl.watchUrl(it) } ?: q
            )
            if (info == null) {
                setState(Phase.FAILED, message = "تعذّر قراءة بيانات المقطع — تأكد أن الرابط مقطع صالح.")
            } else {
                _results.value = emptyList()
                setState(Phase.RESOLVED, title = info.title, resolved = info)
            }
        } else {
            val list = YouTubeJson.parseSearchResults(json)
            if (list.isEmpty()) {
                _results.value = emptyList()
                setState(Phase.FAILED, message = "لا نتائج للبحث — جرّب كلمات أخرى.")
            } else {
                _results.value = list
                setState(Phase.RESULTS, message = "نتائج البحث (${list.size}) — اختر مقطعًا للتحميل")
            }
        }
    }

    // ================= استيراد مقطع: تحميل ثم تحويل =================

    /**
     * استيراد كامل لمقطع: تحميل الصوت ثم (إن لزم) تحويله MP3 ثم تسليمه
     * عبر onAdded لإضافته إلى المكتبة (يُستدعى في خيط العمل نفسه).
     */
    fun import(
        video: YouTubeJson.VideoInfo,
        onAdded: (File, YouTubeJson.VideoInfo) -> Unit
    ) {
        cancelFlag.set(false)
        val tmpDir = File(workDir, "tmp").apply { mkdirs() }
        try {
            val bin = locator.resolve()

            // —— التحميل ——
            setState(Phase.DOWNLOADING, percent = 0f, title = video.title, message = "تحميل الصوت…")
            val outTemplate = File(tmpDir, "src.%(ext)s").absolutePath
            // صيغة الصوت: itag 140 (AAC 128k) القياسي في يوتيوب ثم احتياطات m4a/أفضل صوت
            val args = mutableListOf(
                bin.binary.path, "--no-warnings", "--no-playlist", "--newline", "--no-part",
                "-f", YouTubeJson.FALLBACK_FORMAT, "-o", outTemplate
            )
            cookiesFile?.takeIf { it.isFile }?.let { args.addAll(listOf("--cookies", it.absolutePath)) }
            args.add(video.sourceUrl.ifEmpty { YouTubeUrl.watchUrl(video.videoId) })

            try {
                runTool(args) { line ->
                    YouTubeJson.parseProgressLine(line)?.let { p ->
                        setState(Phase.DOWNLOADING, percent = p, title = video.title, message = "تحميل الصوت…")
                    }
                }
            } catch (t: Throwable) {
                if (cancelFlag.get()) { setState(Phase.IDLE, message = "أُلغي الاستيراد"); return }
                setState(Phase.FAILED, title = video.title, message = friendlyMessage(t))
                return
            }
            if (cancelFlag.get()) { setState(Phase.IDLE, message = "أُلغي الاستيراد"); return }

            // —— الملف المُحمَّل ——
            val downloaded = tmpDir.listFiles { f -> f.isFile && f.name.startsWith("src.") }
                ?.maxByOrNull { it.length() }
            if (downloaded == null || downloaded.length() < 1_000L) {
                setState(Phase.FAILED, title = video.title, message = "لم يصل أي صوت من التحميل — أعد المحاولة.")
                return
            }

            // —— التحويل أو النقل المباشر ——
            val ext = downloaded.extension.lowercase()
            val finalName = YouTubeUrl.sanitizeFilename(video.title)
            val final = uniqueOutput(File(workDir, "$finalName.mp3"))
            if (AudioTranscoder.needsTranscode(ext)) {
                setState(Phase.CONVERTING, percent = 0f, title = video.title, message = "تحويل إلى MP3…")
                val converted = File(tmpDir, "converted.mp3")
                try {
                    AudioTranscoder.m4aToMp3(
                        downloaded, converted,
                        durationSec = video.durationSec,
                        onProgress = { p ->
                            setState(Phase.CONVERTING, percent = p * 100f, title = video.title, message = "تحويل إلى MP3…")
                        }
                    )
                } catch (t: Throwable) {
                    if (cancelFlag.get()) { setState(Phase.IDLE, message = "أُلغي الاستيراد"); return }
                    setState(Phase.FAILED, title = video.title, message = friendlyMessage(t))
                    return
                }
                if (cancelFlag.get()) { setState(Phase.IDLE, message = "أُلغي الاستيراد"); return }
                if (!converted.renameTo(final)) converted.copyTo(final, overwrite = true)
                converted.delete()
            } else if (ext == "mp3" || ext in com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS) {
                // صيغة يفكّها المحرك مباشرة (مواقع أخرى أو MP3 جاهز) — نقل بأمان
                val target = if (ext == "mp3") final else uniqueOutput(File(workDir, "$finalName.$ext"))
                if (!downloaded.renameTo(target)) downloaded.copyTo(target, overwrite = true)
                deliver(target, video, onAdded)
                return
            } else {
                setState(
                    Phase.FAILED, title = video.title,
                    message = "وصل المقطع بصيغة غير مدعومة ($ext) — جرّب مقطعًا آخر."
                )
                return
            }

            deliver(final, video, onAdded)
        } finally {
            // تنظيف المؤقتات دائمًا
            try { tmpDir.listFiles()?.forEach { it.delete() } } catch (_: Exception) { }
            try { tmpDir.delete() } catch (_: Exception) { }
        }
    }

    private fun deliver(file: File, video: YouTubeJson.VideoInfo, onAdded: (File, YouTubeJson.VideoInfo) -> Unit) {
        onAdded(file, video)
        setState(
            Phase.DONE, percent = 100f, title = video.title,
            message = "جاهز: «${file.name}» أُضيف إلى المكتبة"
        )
    }

    /** اسم ملف غير مُستخدم: النهاية (2) (3)… بدل الكتابة فوق عمل سابق. */
    private fun uniqueOutput(target: File): File {
        if (!target.exists()) return target
        val parent = target.parentFile ?: return target
        val base = target.nameWithoutExtension
        val ext = target.extension
        var i = 2
        while (true) {
            val cand = File(parent, "$base ($i).$ext")
            if (!cand.exists()) return cand
            i++
            if (i > 999) return File(parent, "$base-${System.currentTimeMillis()}.$ext")
        }
    }

    // ================= تشغيل الأداة =================

    /** تشغيل yt-dlp وجمع stdout كاملًا مع مراقبة stderr في خيط جانبي (لا انسداد أنابيب). */
    private fun runTool(args: List<String>, onProgress: ((String) -> Unit)?): String {
        val bin = locator.resolve()
        if (!bin.fromBundled && !bin.binary.isFile) {
            throw IllegalStateException(
                "أداة تحميل اليوتيوب غير مدمجة في هذه النسخة ولا مثبتة في النظام — أعد تنزيل النسخة الرسمية."
            )
        }
        // إصلاح جذري لعطل Program Files (تقرير المستخدم v0.25): مجلد عمل آمن
        // قابل للكتابة + TMP/TEMP/TMPDIR مثبتة عليه — فلا تسقط ملفات الأداة
        // المؤقتة في مجلد التثبيت المحمي مهما كان سياق إطلاق التطبيق
        val scratch = YtDlpProcessSpec.scratchDir(workDir, System.getProperty("java.io.tmpdir"))
        val pb = ProcessBuilder(args)
            .directory(scratch)
            .redirectErrorStream(false)
        val env = pb.environment()
        YtDlpProcessSpec.envFor(scratch, env).forEach { (k, v) -> env[k] = v }
        val proc = try {
            pb.start()
        } catch (e: Exception) {
            throw IllegalStateException(
                "تعذّر تشغيل أداة التحميل المدمجة (${e.javaClass.simpleName}) — أعد تشغيل التطبيق مرة."
            )
        }
        activeProcess = proc

        val stderrTail = ArrayDeque<String>()
        val errThread = Thread {
            try {
                proc.errorStream.bufferedReader().useLines { lines ->
                    lines.forEach { l ->
                        synchronized(stderrTail) {
                            if (stderrTail.size > 24) stderrTail.removeFirst()
                            stderrTail.addLast(l)
                        }
                    }
                }
            } catch (_: Exception) { }
        }
        errThread.isDaemon = true
        errThread.start()

        val stdout = StringBuilder()
        try {
            BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { l ->
                    stdout.appendLine(l)
                    onProgress?.invoke(l)
                }
            }
        } catch (_: Exception) { }

        val code = proc.waitFor()
        activeProcess = null
        if (cancelFlag.get()) throw IllegalStateException("cancel")
        if (code != 0) {
            val raw = synchronized(stderrTail) { stderrTail.joinToString("\n") }
            val known = YouTubeJson.describeError(raw)
            throw IllegalStateException(known ?: (raw.lineSequence().lastOrNull { it.isNotBlank() }?.take(220)
                ?: "فشل التحميل (رمز $code)"))
        }
        return stdout.toString()
    }

    private fun friendlyMessage(t: Throwable): String {
        val m = t.message.orEmpty()
        if (m == "cancel") return "أُلغي الاستيراد"
        return YouTubeJson.describeError(m) ?: m.ifBlank { "تعذّر الاستيراد — أعد المحاولة." }
    }

    private fun setState(phase: Phase, percent: Float = _state.value.percent, title: String = _state.value.title, message: String = "", resolved: YouTubeJson.VideoInfo? = _state.value.resolved) {
        _state.value = State(phase, percent, title, message, resolved)
    }
}
