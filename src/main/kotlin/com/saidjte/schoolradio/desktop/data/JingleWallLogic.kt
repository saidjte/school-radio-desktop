package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * موضع في جدار الجينغل (v0.28.0 «أضف جدار الجينغل»): إما موضعًا معيَّنًا لمقطع
 * (جينل مدمج مستخرج أو ملف من اختيار المذيع) أو فارغًا ينتظر التعيين.
 * المسار وحده مصدر الحقيقة — العنوان نص عرضي يُكتب معه ويُستبدل عند التعيين.
 */
data class JinglePad(
    val index: Int,
    val path: String? = null,
    val title: String? = null
) {
    val isEmpty: Boolean get() = path.isNullOrBlank()
}

/** موضع محفوظ في settings.json — الحقول كلها اختيارية متسامحة مع الملفات اليدوية. */
data class JinglePadJson(
    val index: Int? = null,
    val path: String? = null,
    val title: String? = null
)

/** حالة جدار الجينغل المحفوظة: مستوى الصوت العام + المواضع المعيَّنة. */
data class JingleWallJson(
    val volume: Float? = null,
    val pads: List<JinglePadJson>? = null
)

/**
 * منطق جدار الجينغل النقي — اثنا عشر موضعًا (4×3 كالجدران الإذاعية المعتادة)،
 * السبعة الأولى تُملأ تلقائيًا بالجينلات المدمجة نفسها التي تعرفها نسخة الهاتف
 * (جرس المدرسة، الافتتاحية…) بعد استخراجها من موارد الجرة، والبقية فارغة
 * للمقاطع المدرسية الخاصة. نقية بالكامل: بلا موارد ولا صوت — تُختبر كما هي.
 */
object JingleWallLogic {

    /** عدد مواضع الجدار — ثابت يعرفه المحرك والمخزن والواجهة معًا. */
    const val PAD_COUNT = 12

    /** المستوى العام الافتراضي للجينلات (0..1). */
    const val DEFAULT_VOLUME = 0.9f

    /** حبس المستوى العام داخل الحدود. */
    fun clampVolume(v: Float): Float =
        if (v.isNaN()) DEFAULT_VOLUME else v.coerceIn(0f, 1f)

    /** جدار فارغ كامل. */
    fun emptyPads(): List<JinglePad> = (0 until PAD_COUNT).map { JinglePad(it) }

    /**
     * الافتراضي لأول تشغيل: المواضع الأولى تأخذ ملفات الجينلات المدمجة المستخرجة
     * بعناوينها العربية المعتمدة (نفس أسماء نسخة الهاتف)، والبقية فارغة.
     */
    fun bundledDefaults(files: List<File>, titles: List<String>): List<JinglePad> {
        val pads = emptyPads().toMutableList()
        files.take(PAD_COUNT).forEachIndexed { i, f ->
            pads[i] = JinglePad(index = i, path = f.absolutePath, title = titles.getOrNull(i) ?: titleForFile(f))
        }
        return pads
    }

    /**
     * دمج المحفوظ مع الافتراضي: مدخلات سليمة فقط (فهرس داخل الحدود ومسار غير فارغ)
     * وتكرار الفهرس يُرجح آخر مدخل — فلا يكسر ملف معدَّل يدويًا الجدار أبدًا،
     * والمواضع غير المذكورة تبقى على الافتراضي (الجينلات المدمجة).
     */
    fun resolve(saved: List<JinglePadJson>?, defaults: List<JinglePad>): List<JinglePad> {
        val pads = defaults.take(PAD_COUNT).let { d ->
            if (d.size == PAD_COUNT) d.toMutableList() else (d + emptyPads()).take(PAD_COUNT).toMutableList()
        }
        saved?.forEach { j ->
            val idx = j.index ?: return@forEach
            if (idx !in 0 until PAD_COUNT) return@forEach
            val path = j.path?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEach
            pads[idx] = JinglePad(index = idx, path = path, title = j.title?.trim()?.takeIf { it.isNotEmpty() })
        }
        return pads
    }

    /** عنوان عرضي مقبول من اسم ملف — بلا امتداد وبلا فراغات طرفية. */
    fun titleForFile(f: File): String =
        f.nameWithoutExtension.trim().ifEmpty { "مقطع" }

    /** عقد دائري: ما يكتبه encodePads يقرؤه resolve دائمًا بقيم مطابقة. */
    fun encodePads(pads: List<JinglePad>): List<JinglePadJson> =
        pads.take(PAD_COUNT).map { JinglePadJson(index = it.index, path = it.path, title = it.title) }
}
