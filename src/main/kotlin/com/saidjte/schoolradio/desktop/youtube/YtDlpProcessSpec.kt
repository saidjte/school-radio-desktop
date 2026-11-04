package com.saidjte.schoolradio.desktop.youtube

import java.io.File

/**
 * تهيئة بيئة عملية أداة التحميل بأمان — إصلاح جذري لعطل v0.25 على ويندوز
 * (تقرير المستخدم: «ERROR: [Errno 13] Permission denied: C:\Program Files\…tmp»):
 *
 * التطبيق يُطلق من الاختصار فيصبح مجلد العمل الحالي مجلد التثبيت داخل
 * Program Files المحمي ضد الكتابة، وأداة التحميل المدمجة (تطبيق بايثون)
 * تسقط ملفاتها المؤقتة في مجلد العمل أو مجلد TEMP إن غاب/تعطّل —
 * فتفشل كل عملية استيراد برفض الإذن.
 *
 * الحل الجذري: مجلد عمل آمن قابل للكتابة دائمًا (تحت بيانات المستخدم أولًا
 * ثم TEMP النظام) يثبَّت للعملية عبر directory()، ويُمرَّر عبر متغيرات
 * TMP/TEMP/TMPDIR فتحلها بايثون أولًا قبل أي احتياط، وأي تسكين في
 * مجلد العمل يقع في مكان آمن مهما كان سياق الإطلاق (مسؤول/خدمة/اختصار).
 */
object YtDlpProcessSpec {

    /**
     * اختيار أول مجلد قابل للإنشاء والكتابة من المرشحين بالترتيب:
     * 1) مجلد بيانات المستخدم (workDir/tmp — مثل ‎~/SchoolRadio/youtube/tmp‎)
     * 2) مجلد TEMP النظام (java.io.tmpdir) تحت اسم التطبيق
     * 3) مجلد البيانات نفسه احتياطًا أخيرًا
     * المرشح الفاشل (محمي/غير موجود) يُتجاهل بصمت — الدالة تنجح دائمًا.
     */
    fun scratchDir(workDir: File, javaTmpDir: String?): File {
        val candidates = listOfNotNull(
            File(workDir, "tmp"),
            javaTmpDir?.takeIf { it.isNotBlank() }?.let { File(it, "SchoolRadioDesktop") },
            workDir
        )
        return candidates.firstOrNull { c ->
            runCatching { c.mkdirs(); c.isDirectory && c.canWrite() }.getOrDefault(false)
        } ?: workDir
    }

    /**
     * متغيرات بيئة العملية الفرعية: TMP/TEMP/TMPDIR تُثبَّت على المجلد الآمن
     * فتعتمدها بايثون (tempfile) فورًا كأول مرشح — أي تعطيل في بيئة
     * الإطلاق (حذف المتغيرات أو توجيهها لمجلد محمي) يُعالَج هنا.
     */
    fun envFor(scratch: File, base: Map<String, String>): Map<String, String> {
        val p = scratch.absolutePath
        return base + mapOf("TMP" to p, "TEMP" to p, "TMPDIR" to p)
    }
}
