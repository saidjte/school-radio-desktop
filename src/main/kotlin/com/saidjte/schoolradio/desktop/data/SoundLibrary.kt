package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * مكتبة الأصوات والموسيقى (v0.42.0) — طبقة المنطق الصافية:
 * المجموعات الخمس المدمجة + «مكتبتي الخاصة» المستوردة، والبحث،
 * والإضافة المقترحة إلى المكتبة الرئيسية، والتراخيص.
 */

/** مجموعات مكتبة الأصوات — الثوابت مرجعٌ وحيد للواجهة والمحرّك والاختبارات. */
object SoundGroups {
    const val MUSIC = "الموسيقى الخلفية"
    const val SFX = "مؤثرات جاهزة"
    const val TRANSITIONS = "أصوات انتقالية"
    const val NATURE = "طبيعة وبيئة"
    const val CINEMA = "مؤثرات سينمائية"
    const val CUSTOM = "مكتبتي الخاصة"

    /** المجموعات المدمجة بالترتيب المعتمد في الواجهة. */
    val BUNDLED = listOf(MUSIC, SFX, TRANSITIONS, NATURE, CINEMA)

    /** مجلد المورد داخل الجرة لكل مجموعة. */
    val dirOf: Map<String, String> = mapOf(
        MUSIC to "bgmusic",
        SFX to "sfx",
        TRANSITIONS to "transitions",
        NATURE to "nature",
        CINEMA to "cinema"
    )
}

/** صوت مدمج داخل الجرة — بياناته الوصفية كلها عربية لتظهر في البحث والبطاقات. */
data class BundledSound(
    val id: String,
    val fileName: String,
    val title: String,
    val group: String,
    val description: String,
    val durationMs: Long,
    val tags: List<String>
)

/** صوت من مكتبة المستخدم الخاصة — مسار مطلق على قرصه يبقى بين الجلسات. */
data class CustomSound(
    val id: Long,
    val path: String,
    val title: String,
    val addedAtEpochMs: Long = 0L
)

data class SoundLibDoc(val custom: List<CustomSound> = emptyList())

/**
 * البحث والتصفية — خالص وحتمي: مطابقة العنوان والوصف والوسوم
 * والمجموعة بلا حساسية لحالة الأحرف.
 */
object SoundLibraryMath {

    /** هل يطابق الصوت استعلام البحث؟ (الاستعلام الفارغ يطابق كل شيء). */
    fun matches(title: String, description: String, tags: List<String>, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return title.contains(q, ignoreCase = true) ||
                description.contains(q, ignoreCase = true) ||
                tags.any { it.contains(q, ignoreCase = true) }
    }

    /** تصنيف المكتبة الرئيسية المقترح عند الإضافة بحسب المجموعة — كلاهما موجود افتراضيًا. */
    fun suggestedCategory(group: String): String = when (group) {
        SoundGroups.MUSIC -> LibraryDefaults.FALLBACK_CATEGORY // «فقرات متنوعة»
        else -> "المؤثرات الصوتية"
    }

    /** نص المدة العربية المختصرة — «39.8 ث» أو «1:22 د». */
    fun durationText(durationMs: Long): String {
        val totalSec = durationMs / 1000.0
        return if (totalSec < 90) {
            val one = (totalSec * 10).toLong() % 10
            val whole = totalSec.toLong()
            if (one == 0L) "$whole ث" else "$whole.${one} ث"
        } else {
            val m = totalSec.toLong() / 60
            val s = Math.round(totalSec - m * 60)
            "$m:${if (s < 10) "0$s" else "$s"} د"
        }
    }
}

/** مخزن «مكتبتي الخاصة» — ملفات المستخدم المستوردة تُفهرَس هنا بين الجلسات. */
class SoundLibraryStore(private val file: File) {

    private var nextId: Long = 1L
    private var doc: SoundLibDoc = load()

    private fun load(): SoundLibDoc {
        val d = Json.load(file, SoundLibDoc())
        val safe = d.copy(
            custom = d.custom.mapNotNull { c ->
                val p = c.path?.trim().orEmpty()
                val t = c.title?.trim().orEmpty()
                if (c.id <= 0L || p.isEmpty() || t.isEmpty()) null
                else c.copy(path = p, title = t, addedAtEpochMs = c.addedAtEpochMs ?: 0L)
            }
        )
        nextId = (safe.custom.maxOfOrNull { it.id } ?: 0L) + 1L
        return safe
    }

    fun custom(): List<CustomSound> = doc.custom

    /** نتيجة الاستيراد — المتجاوز بصيغة غير مدعومة يُعرض للمستخدم لا صمتًا. */
    data class ImportResult(val added: List<CustomSound>, val skippedUnsupported: Int)

    /** استيراد ملفات (ملفات مفردة أو محتوى مجلد ممسوح) إلى «مكتبتي الخاصة». */
    fun addFiles(files: List<File>): ImportResult {
        val existing = doc.custom.map { it.path }.toSet()
        val usable = files.filter {
            it.isFile && it.path !in existing &&
                    it.extension.lowercase() in com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS
        }
        val skipped = files.count { it.isFile && it !in usable }
        val added = usable.map {
            CustomSound(nextId++, it.path, it.nameWithoutExtension, System.currentTimeMillis())
        }
        if (added.isNotEmpty()) {
            doc = doc.copy(custom = doc.custom + added)
            persist()
        }
        return ImportResult(added, skipped)
    }

    fun remove(id: Long): Boolean {
        if (doc.custom.none { it.id == id }) return false
        doc = doc.copy(custom = doc.custom.filter { it.id != id })
        persist()
        return true
    }

    private fun persist() {
        Json.save(file, doc)
    }
}

/**
 * التراخيص (بند «توضيح تراخيص الاستخدام التجاري») — مصدر وحيد يقرؤه
 * حوار التراخيص في الواجهة واختبارات العقد، مطابق لـSOURCES.md.
 */
object SoundLicense {

    data class Row(val item: String, val source: String, val license: String, val commercial: String)

    /** أصوات مكتبة الأصوات المدمجة — كلها مولدة محليًا داخل المشروع. */
    val BUNDLED_ROW = Row(
        "أصوات مكتبة الأصوات (36 صوتًا في خمس مجموعات)",
        "مُولَّدة برمجيًا داخل المشروع — صفر حقوق خارجية",
        "CC0 / ملكية عامة",
        "مسموح — بلا قيود ولا إسناد ولا رسوم"
    )

    /** الجينلات المدمجة (v0.28.0) رخصها خارجية — توثيق مطابق لـjingles/SOURCES.md. */
    val JINGLES = listOf(
        Row("جرس المدرسة", "orangefreesounds.com", "استعمال مجاني", "مسموح"),
        Row("افتتاحية مبهجة", "mixkit.co", "Mixkit License", "مسموح في المنتجات النهائية"),
        Row("مقدمة فقرة", "mixkit.co", "Mixkit License", "مسموح في المنتجات النهائية"),
        Row("انتقال ساحر", "mixkit.co", "Mixkit License", "مسموح في المنتجات النهائية"),
        Row("علامة سريعة", "mixkit.co", "Mixkit License", "مسموح في المنتجات النهائية"),
        Row("مرور هوائي", "mixkit.co", "Mixkit License", "مسموح في المنتجات النهائية"),
        Row("ختام هادئ", "mixkit.co", "Mixkit License", "مسموح في المنتجات النهائية")
    )

    /** ملاحظة المكتبة المستوردة — مسؤولية المستخدم عن رخص ملفاته. */
    const val CUSTOM_NOTICE =
        "أي ملفات تستوردها إلى «مكتبتي الخاصة» تبقى برخصة صاحبها أنت المسؤول عنها — " +
                "تأكد من رخصة كل ملف تستورده للبث المدرسي؛ التطبيق لا يفحصها ولا يغيّرها."

    /** الملخص القاطع للسؤال: هل أستطيع الاستخدام التجاري؟ */
    fun commercialSummary(): String =
        "الاستخدام التجاري: أصوات المكتبة المولدة (36 صوتًا) CC0 — مسموح بلا قيود؛ " +
                "الجينلات السبع المدمجة Mixkit/OrangeFreeSounds — مسموح في المنتجات النهائية بلا إسناد، " +
                "ولا يُسمح بإعادة بيع الملفات نفسها كمحتوى مستقل؛ والمستورد خاص برخصة صاحبه."

    fun rows(): List<Row> = listOf(BUNDLED_ROW) + JINGLES
}
