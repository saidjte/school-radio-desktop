package com.saidjte.schoolradio.desktop.data

/**
 * سكريبت المذيع: وثيقة من فقرات مرتبة (عنوان + نص).
 * تُحفظ في script.json داخل مجلد بيانات التطبيق.
 */
data class ScriptSection(
    val id: Long,
    val title: String,
    val body: String
)

data class ScriptDoc(val sections: List<ScriptSection> = emptyList())

/** حسابات نصية خالصة (قابلة للاختبار): عدد الكلمات وزمن القراءة التقديري. */
object ScriptStats {

    /** متوسط سرعة قراءة مذيّع بالعربية على الهواء (كلمة/دقيقة). */
    const val WORDS_PER_MINUTE = 130

    fun wordCount(text: String): Int =
        text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    /** الزمن التقديري لقراءة النص بالثواني (تقريب لأعلى). */
    fun secondsFor(text: String): Long {
        val words = wordCount(text)
        if (words == 0) return 0L
        return (words * 60L + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE
    }

    fun totalSeconds(sections: List<ScriptSection>): Long =
        sections.sumOf { secondsFor(it.body) }

    fun totalWords(sections: List<ScriptSection>): Int =
        sections.sumOf { wordCount(it.body) }
}
