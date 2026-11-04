package com.saidjte.schoolradio.desktop.youtube

import java.io.File

/**
 * تحديد أداة تحميل اليوتيوب وتجهيزها — بلا أي تثبيت خارجي للمستخدم:
 * 1) الأداة مدمجة في موارد الجرة (/bin/yt-dlp أو /bin/yt-dlp.exe حسب منصة البناء)
 *    فتُستخرج مرّة واحدة إلى مجلد بيانات التطبيق وتُمنح صلاحية التنفيذ.
 * 2) إن غاب المورد (نسخة مطوّر مثلًا) نُجرّب أداة yt-dlp المثبتة في النظام (PATH).
 * إعادة الاستخراج تحدث فقط إذا تغيّر رقم إصدار الأداة المدمجة (ytdlp_version.txt).
 */
class YtDlpLocator(private val baseDir: File) {

    /** نتيجة التجهيز: مسار الأداة الجاهز للتنفيذ. */
    data class Resolved(val binary: File, val fromBundled: Boolean)

    fun resolve(): Resolved {
        val exeName = if (hostOs() == "windows") "yt-dlp.exe" else "yt-dlp"
        val bundled = YtDlpLocator::class.java.classLoader.getResourceAsStream("bin/$exeName")?.readBytes()
        if (bundled != null && bundled.size > 1_000_000) {
            val dir = File(baseDir, "bin").apply { mkdirs() }
            val out = File(dir, exeName)
            val markerRes = YtDlpLocator::class.java.classLoader.getResourceAsStream("bin/ytdlp_version.txt")
                ?.readBytes()?.toString(Charsets.UTF_8)?.trim()
            val markerFile = File(dir, "ytdlp_version.txt")
            val needExtract = !out.isFile || out.length() != bundled.size.toLong() ||
                    markerFile.readText().trim() != markerRes
            if (needExtract) {
                // اكتب في ملف مؤقت ثم انقل — لا يترك أداة نصفية لو انقطع التشغيل
                val tmp = File(dir, "$exeName.tmp")
                tmp.writeBytes(bundled)
                if (out.exists()) out.delete()
                if (!tmp.renameTo(out)) {
                    tmp.copyTo(out, overwrite = true)
                    tmp.delete()
                }
                if (markerRes != null) markerFile.writeText(markerRes)
            }
            if (hostOs() != "windows") {
                runCatching { out.setExecutable(true, false) }
            }
            if (out.isFile && out.canExecute() || hostOs() == "windows") return Resolved(out, true)
            return Resolved(out, true) // التنفيذ يُجرّب لاحقًا ويظهر خطأ واضح إن فشل
        }
        // احتياط: أداة النظام
        return Resolved(File("yt-dlp"), false)
    }

    companion object {
        /** منصة التشغيل الحالية بنفس تصنيف البناء: windows / macos / linux. */
        fun hostOs(): String {
            val n = System.getProperty("os.name").lowercase()
            return when {
                n.contains("win") -> "windows"
                n.contains("mac") || n.contains("darwin") -> "macos"
                else -> "linux"
            }
        }

        /** اسم المورد المدمج لمنصة معطاة — للاختبارات وفحص selftest. */
        fun bundledResourceName(os: String = hostOs()): String =
            if (os == "windows") "bin/yt-dlp.exe" else "bin/yt-dlp"
    }
}
