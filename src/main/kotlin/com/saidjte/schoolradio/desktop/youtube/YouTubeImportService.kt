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
 * 1) جلب بيانات المقطع (yt-dlp -J بسلسلة قنوات تلقائية) أو البحث (بـ --flat-playlist:
 *    لا يفحص كل نتيجة فيستبق حاجز «تحقق البوت» ويكون أسرع بثمانية أضعاف).
 * 2) معاينة قبل التحميل (v0.44.0): تحميل مؤقت إلى مجلد المعاينة ثم تحويله MP3
 *    محليًا فيبقى جاهزًا — التشغيل عبر محرك المعاينة المشترك من الحوار،
 *    والتأكيد يسلّم الملف نفسه للمكتبة بلا تحميل ثانٍ ولا تحويل ثانٍ.
 * 3) تحميل أفضل صوت mp4a (AAC) مع تقدم حي وتحويل M4A→MP3 محليًا (JAAD+LAME).
 * الدوال مُعطِّلة (تُستدعى من خيط عمل في AppViewModel) والحالة تنبض عبر StateFlow.
 */
class YouTubeImportService(
    private val locator: YtDlpLocator,
    /** مجلد العمل والمخرجات النهائية (MP3) — عادةً SchoolRadio/youtube. */
    private val workDir: File
) {

    enum class Phase { IDLE, RESOLVING, RESOLVED, RESULTS, PREVIEW_READY, DOWNLOADING, CONVERTING, DONE, FAILED }

    data class State(
        val phase: Phase = Phase.IDLE,
        val percent: Float = 0f,
        val title: String = "",
        val message: String = "",
        val resolved: YouTubeJson.VideoInfo? = null
    )

    /** تحميل مؤقت جاهز للتسليم: ملف MP3 (أو صيغة قابلة للتشغيل) بانتظار تأكيد المستخدم. */
    data class PendingDownload(
        val video: YouTubeJson.VideoInfo,
        val file: File
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<YouTubeJson.VideoInfo>>(emptyList())
    val results: StateFlow<List<YouTubeJson.VideoInfo>> = _results.asStateFlow()

    /** ملف كوكيز اختياري (يُصدَّر من المتصفح) لشبكات يطلب يوتيوب فيها تحققًا إضافيًا. */
    @Volatile var cookiesFile: File? = null

    /** التحميل الجاهز للتأكيد (من مسار المعاينة) — يعاد استخدامه عند الاستيراد بلا إعادة تحميل. */
    @Volatile private var pending: PendingDownload? = null
    fun pendingDownload(): PendingDownload? = pending

    /** مجلد المعاينة المؤقتة — يبقى حتى التأكيد أو التجاهل أو إغلاق الحوار. */
    private val previewDir: File get() = File(workDir, "tmp/preview")

    private val cancelFlag = AtomicBoolean(false)
    @Volatile private var activeProcess: Process? = null

    /**
     * خطأ أداة يحمل الاثنين: النص الخام للتصنيف (سلسلة القنوات تصنّف به)
     * والرسالة الودّية للعرض (describeError تترجم أو آخر سطر خام).
     */
    internal class ToolError(val raw: String, friendly: String) : IllegalStateException(friendly)

    /** طلب إلغاء: يقطع العملية الجارية ويُنظّف الملفات المؤقتة. */
    fun cancel() {
        cancelFlag.set(true)
        try { activeProcess?.destroy() } catch (_: Exception) { }
    }

    fun reset() {
        discardPreview()
        _state.value = State()
    }

    /** ضبط حالة فاشلة من الخارج (حاجز أمان في AppViewModel). */
    fun fail(message: String) {
        _state.value = State(Phase.FAILED, message = message)
    }

    fun clearResults() {
        _results.value = emptyList()
    }

    /**
     * هل التحميل المعلَّق صالح لهذا المقطع؟ منطق نقي مُختبر: نفس المقطع والملف
     * موجود فعليًا وله حجم منطقي — عندها الاستيراد يتخطى التحميل والتحويل معًا.
     */
    fun pendingUsableFor(videoId: String): Boolean {
        val p = pending ?: return false
        return p.video.videoId == videoId &&
                p.file.isFile &&
                p.file.length() > 1_000L
    }

    /** تجاهل المعاينة: إزالة الملفات المؤقتة وتصفير المعلَّق وتهدئة الحالة إن كانت مرحلة معاينة. */
    fun discardPreview() {
        pending = null
        try {
            previewDir.listFiles()?.forEach { it.delete() }
            previewDir.delete()
        } catch (_: Exception) { }
        if (_state.value.phase == Phase.PREVIEW_READY) {
            _state.value = State()
        }
    }

    // ================= جلب البيانات: رابط واحد أو بحث =================

    /**
     * جلب بيانات: رابط مقطع → معلوماته كاملة (RESOLVED)، أو نص → نتائج بحث (RESULTS).
     * رابط المقطع يُجرَّب بسلسلة قنوات التشغيل تلقائيًا (v0.44.0) — أول قناة تنجح تُكمل.
     * البحث يعمل بـ --flat-playlist فلا يفحص كل نتيجة — أسرع ويتفادى حاجز التحقق.
     * يرمي IllegalStateException برسالة عربية عند الفشل.
     */
    fun resolve(input: String) {
        val q = input.trim()
        if (q.isEmpty()) throw IllegalStateException("اكتب رابط مقطع أو كلمات للبحث أولًا.")
        cancelFlag.set(false)
        setState(Phase.RESOLVING, message = "جلب المعلومات من يوتيوب…")

        val isSearch = YouTubeUrl.classify(q) == YouTubeUrl.Kind.SEARCH
        val json: String = try {
            if (isSearch) {
                // البحث: مسطّح — لا فحص لكل نتيجة (الإصلاح المُتحقَّق منه ضد حاجز البوت)
                val args = mutableListOf(
                    locator.resolve().binary.path, "-J", "--no-warnings", "--no-playlist",
                    "--flat-playlist", "--sleep-requests", "1"
                )
                cookiesFile?.takeIf { it.isFile }?.let { args.addAll(listOf("--cookies", it.absolutePath)) }
                args.add("ytsearch8:$q")
                runTool(args, onProgress = null)
            } else {
                // رابط مباشر: سلسلة قنوات — كل محاولة بـ -f صريح فلا يفشل اختيار الصيغ
                // على أنظمة بلا ffmpeg مع القنوات ذات الصيغ المنفصلة فقط
                resolveViaChain(q)
            }
        } catch (t: Throwable) {
            _results.value = emptyList()
            setState(Phase.FAILED, message = friendlyMessage(t))
            return
        }

        if (cancelFlag.get()) { setState(Phase.IDLE, message = "أُلغي الطلب"); return }

        if (!isSearch) {
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
                setState(Phase.RESULTS, message = "نتائج البحث (${list.size}) — استمع بالمعاينة ثم اختر مقطعًا للتحميل")
            }
        }
    }

    /**
     * جلب بيانات رابط مباشر عبر سلسلة القنوات — يرمي آخر خطأ إن فشلت كلها،
     * وتحقق البوت الكلي يعطي رسالة الكوكيز الواضحة.
     */
    private fun resolveViaChain(q: String): String {
        val bin = locator.resolve()
        var lastErr: IllegalStateException? = null
        for (i in 0 until YouTubeClientChain.size) {
            if (cancelFlag.get()) throw IllegalStateException("cancel")
            val attempt = YouTubeClientChain.attempt(i)
            setState(Phase.RESOLVING, message = YouTubeClientChain.progressMessage(i, "جلب المعلومات من يوتيوب"))
            val args = mutableListOf(
                bin.binary.path, "-J", "--no-warnings", "--no-playlist",
                "--sleep-requests", "1", "-f", YouTubeJson.FALLBACK_FORMAT
            )
            cookiesFile?.takeIf { it.isFile }?.let { args.addAll(listOf("--cookies", it.absolutePath)) }
            args.addAll(YouTubeClientChain.extractorArgs(attempt))
            val id = YouTubeUrl.extractVideoId(q)
            args.add(if (id != null) YouTubeUrl.watchUrl(id) else q)
            try {
                return runTool(args, onProgress = null)
            } catch (t: Throwable) {
                if (t.message == "cancel") throw t
                val raw = (t as? ToolError)?.raw ?: t.message.orEmpty()
                lastErr = t as? IllegalStateException ?: IllegalStateException(raw)
                if (!YouTubeClientChain.retryableError(raw)) throw lastErr
            }
        }
        val m = lastErr?.message.orEmpty()
        throw IllegalStateException(
            if (YouTubeClientChain.botCheck(m)) friendlyBotWall() else m
        )
    }

    private fun friendlyBotWall(): String =
        YouTubeJson.describeError("sign in to confirm you're not a bot")
            ?: "يوتيوب يطلب تحقّقًا إضافيًا من شبكتك — أرفق ملف كوكيز أو جرّب شبكة أخرى."

    // ================= المعاينة قبل التحميل (v0.44.0) =================

    /**
     * معاينة مقطع: تحميل مؤقت في مجلد المعاينة ثم تحويله MP3 محليًا فجاهزًا
     * للتشغيل من الحوار (محرك المعاينة المشترك) وللتأكيد بلا إعادة تحميل.
     */
    fun preview(video: YouTubeJson.VideoInfo) {
        cancelFlag.set(false)
        previewDir.mkdirs()
        try {
            // معاينة سابقة لنفس المقطع؟ لا تحميل ولا تحويل — أعد إظهار الجاهزية
            if (pendingUsableFor(video.videoId)) {
                setState(Phase.PREVIEW_READY, percent = 100f, title = video.title,
                    message = "المعاينة جاهزة — شغّلها من الزر، وبعد الاستماع أكّد التحميل أو تجاهلها.",
                    resolved = video)
                return
            }
            discardPreview()
            previewDir.mkdirs()

            val downloaded = downloadViaChain(video, previewDir)
            val ext = downloaded.extension.lowercase()
            val ready: File = when {
                AudioTranscoder.needsTranscode(ext) -> {
                    setState(Phase.CONVERTING, percent = 0f, title = video.title, message = "تحويل المعاينة إلى MP3…")
                    val ready = File(previewDir, "ready.mp3")
                    AudioTranscoder.m4aToMp3(
                        downloaded, ready,
                        durationSec = video.durationSec,
                        onProgress = { p ->
                            setState(Phase.CONVERTING, percent = p * 100f, title = video.title, message = "تحويل المعاينة إلى MP3…")
                        }
                    )
                    downloaded.delete()
                    ready
                }
                ext == "mp3" || ext in com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS -> downloaded
                else -> throw IllegalStateException("وصل المقطع بصيغة غير مدعومة ($ext) — جرّب مقطعًا آخر.")
            }
            pending = PendingDownload(video, ready)
            setState(Phase.PREVIEW_READY, percent = 100f, title = video.title,
                message = "المعاينة جاهزة — شغّلها من الزر، وبعد الاستماع أكّد التحميل أو تجاهلها.",
                resolved = video)
        } catch (t: Throwable) {
            if (t.message == "cancel" || cancelFlag.get()) {
                setState(Phase.IDLE, message = "أُلغيت المعاينة")
            } else {
                setState(Phase.FAILED, title = video.title, message = friendlyMessage(t))
            }
        }
    }

    // ================= استيراد مقطع: تحميل ثم تحويل =================

    /**
     * استيراد كامل لمقطع وتسليمه عبر onAdded لإضافته إلى المكتبة.
     * إن وُجد تحميل معاينة جاهز لنفس المقطع فسُلِّم هو — بلا تحميل ثانٍ ولا تحويل ثانٍ.
     */
    fun import(
        video: YouTubeJson.VideoInfo,
        onAdded: (File, YouTubeJson.VideoInfo) -> Unit
    ) {
        cancelFlag.set(false)

        // —— مسار المعاينة الجاهزة: تسليم مباشر ——
        if (pendingUsableFor(video.videoId)) {
            val p = pending!!
            val finalName = YouTubeUrl.sanitizeFilename(video.title)
            val final = uniqueOutput(File(workDir, "${finalName}.mp3"))
            try {
                if (!p.file.renameTo(final)) p.file.copyTo(final, overwrite = true)
            } catch (t: Throwable) {
                setState(Phase.FAILED, title = video.title, message = "تعذّر تجهيز الملف النهائي: ${t.message ?: ""}")
                return
            }
            discardPreview()
            deliver(final, video, onAdded)
            return
        }

        val dlDir = File(workDir, "tmp/dl").apply { mkdirs() }
        try {
            val downloaded = downloadViaChain(video, dlDir)

            // —— التحويل أو النقل المباشر ——
            val ext = downloaded.extension.lowercase()
            val finalName = YouTubeUrl.sanitizeFilename(video.title)
            val final = uniqueOutput(File(workDir, "$finalName.mp3"))
            if (AudioTranscoder.needsTranscode(ext)) {
                setState(Phase.CONVERTING, percent = 0f, title = video.title, message = "تحويل إلى MP3…")
                val converted = File(dlDir, "converted.mp3")
                AudioTranscoder.m4aToMp3(
                    downloaded, converted,
                    durationSec = video.durationSec,
                    onProgress = { p ->
                        setState(Phase.CONVERTING, percent = p * 100f, title = video.title, message = "تحويل إلى MP3…")
                    }
                )
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
        } catch (t: Throwable) {
            if (t.message == "cancel" || cancelFlag.get()) {
                setState(Phase.IDLE, message = "أُلغي الاستيراد")
            } else {
                setState(Phase.FAILED, title = video.title, message = friendlyMessage(t))
            }
        } finally {
            // تنظيف مجلد التحميل المباشر دائمًا — مجلد المعاينة شأنه شأنه
            try { dlDir.listFiles()?.forEach { it.delete() } } catch (_: Exception) { }
            try { dlDir.delete() } catch (_: Exception) { }
        }
    }

    /**
     * تحميل الصوت عبر سلسلة القنوات إلى مجلد معطى — يعيد الملف المُحمَّل.
     * كل محاولة فاشلة قابلة للإعادة تنتقل للقناة التالية برسالة تقدم حية.
     */
    private fun downloadViaChain(video: YouTubeJson.VideoInfo, into: File): File {
        val bin = locator.resolve()
        var lastErr: IllegalStateException? = null
        for (i in 0 until YouTubeClientChain.size) {
            if (cancelFlag.get()) throw IllegalStateException("cancel")
            val attempt = YouTubeClientChain.attempt(i)
            setState(Phase.DOWNLOADING, percent = 0f, title = video.title,
                message = YouTubeClientChain.progressMessage(i, "تحميل الصوت"))
            val outTemplate = File(into, "src.%(ext)s").absolutePath
            val args = mutableListOf(
                bin.binary.path, "--no-warnings", "--no-playlist", "--newline", "--no-part",
                "--sleep-requests", "1",
                "-f", YouTubeJson.FALLBACK_FORMAT, "-o", outTemplate
            )
            cookiesFile?.takeIf { it.isFile }?.let { args.addAll(listOf("--cookies", it.absolutePath)) }
            args.addAll(YouTubeClientChain.extractorArgs(attempt))
            args.add(video.sourceUrl.ifEmpty { YouTubeUrl.watchUrl(video.videoId) })

            try {
                runTool(args) { line ->
                    YouTubeJson.parseProgressLine(line)?.let { p ->
                        setState(Phase.DOWNLOADING, percent = p, title = video.title,
                            message = YouTubeClientChain.progressMessage(i, "تحميل الصوت"))
                    }
                }
            } catch (t: Throwable) {
                if (t.message == "cancel") throw t
                lastErr = t as? IllegalStateException ?: IllegalStateException(t.message.orEmpty())
                if (!YouTubeClientChain.retryableError((lastErr as? ToolError)?.raw ?: lastErr.message.orEmpty())) throw lastErr
                continue
            }

            val downloaded = into.listFiles { f -> f.isFile && f.name.startsWith("src.") }
                ?.maxByOrNull { it.length() }
            if (downloaded != null && downloaded.length() >= 1_000L) return downloaded
            // وصل لا شيء: اعتبرها محاولة فاشلة وانتقل للقناة التالية
            lastErr = IllegalStateException("لم يصل أي صوت من التحميل — أعد المحاولة.")
        }
        val m = lastErr?.message.orEmpty()
        throw IllegalStateException(
            if (YouTubeClientChain.botCheck(m)) friendlyBotWall() else m
        )
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
            val friendly = known ?: (raw.lineSequence().lastOrNull { it.isNotBlank() }?.take(220)
                ?: "فشل التحميل (رمز $code)")
            throw ToolError(raw, friendly)
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
