package com.saidjte.schoolradio.desktop.data

import com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS
import java.io.File

/**
 * منطق سحب وإفلات الملفات الصوتية (v0.39.0 «سحب وإفلات الملفات الصوتية») —
 * قرار نقّي مستقل عن AWT: أي قائمة ملفات من نظام التشغيل تُصفَّى هنا قبل أن
 * يمسّ التطبيق قرصه، فلا يدخل المكتبة ولا المونتاج إلا ما يستطيع المحرك
 * تشغيله فعلًا (نفس عهد «المكتبة تظهر ولا تشتغل» — ممنوع أن ينكسر بالإفلات).
 */
object DropLogic {

    /** نتيجة الإفلات المعقمة — ما ستفعله الواجهة فعليًا. */
    data class Decision(
        /** الملفات الصوتية القابلة للتشغيل ضمن الإفلات. */
        val audioFiles: List<File>,
        /** عدد الملفات المتجاهَلة بصيغة غير مدعومة (يُعرض في رسالة عربية). */
        val skippedUnsupported: Int
    ) {
        val hasAudio: Boolean get() = audioFiles.isNotEmpty()

        /** الرسالة العربية الموحدة بعد الإفلات — null إن لم يذكر شيء. */
        fun message(targetAr: String): String? = when {
            audioFiles.isEmpty() && skippedUnsupported == 0 -> null
            audioFiles.isEmpty() ->
                "لا ملفات صوتية في ما أفلته — تجاوز $skippedUnsupported بصيغة غير مدعومة " +
                        "(المدعوم: MP3 وWAV وOGG وFLAC)"
            skippedUnsupported > 0 -> "أُضيف ${audioFiles.size} إلى $targetAr · تجاوز $skippedUnsupported بصيغة غير مدعومة"
            else -> "أُضيف ${audioFiles.size} إلى $targetAr"
        }
    }

    /** هل اسم الملف بامتداد يستطيع المحرك تشغيله؟ */
    fun isPlayable(f: File): Boolean =
        f.isFile && f.extension.lowercase() in PLAYABLE_EXTENSIONS

    /**
     * تصفية قائمة إفلات (من نظام التشغيل أو الأتمتة): الموجود على القرص فقط
     * يُقبل، التكرار يُكبح بالترتيب، والامتدادات الغريبة تُعدّ لتظهر في الرسالة.
     */
    fun decide(files: List<File>): Decision {
        val seen = HashSet<File>()
        val audio = ArrayList<File>()
        var skipped = 0
        for (f in files) {
            when {
                isPlayable(f) && seen.add(f) -> audio.add(f)
                else -> skipped++
            }
        }
        return Decision(audio, skipped)
    }

    /**
     * وجهة الإفلات حسب الصفحة الحالية — المونتاج يضيفها مقاطع تعليق، والموسيقى
     * تستوردها للمكتبة، وبقية الصفحات تستورد إلى المكتبة كذلك (فلا إفلات يضيع).
     */
    enum class Destination { MONTAGE, LIBRARY }

    fun destinationFor(tabName: String?): Destination =
        if (tabName == "MONTAGE") Destination.MONTAGE else Destination.LIBRARY

    /**
     * تصنيف الاستيراد الافتراضي للإفلات: «فقرات متنوعة» — وجهة محددة ومعلنة
     * في الرسالة بعد الإفلات، والمذيع ينقل المقطع إلى تصنيفه بنقرة إن رغب.
     */
    const val DEFAULT_IMPORT_CATEGORY = LibraryDefaults.FALLBACK_CATEGORY

    /** اسم علام الأتمتة للفحص البصري — يُمرَّر ملفات تجريبية عبر المسار ذاته. */
    const val AUTOMATION_FLAG = "--drop-demo"
}
