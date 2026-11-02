package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * المكتبة الصوتية المصنفة — نفس التصنيفات الافتراضية السبع في نسخة الهاتف
 * مع إضافة تصنيفات مخصصة، وفهرس دائم للمسارات يبقى بعد إعادة التشغيل،
 * وكشف الملفات المفقودة وإعادة ربطها (مكافئ ميزة «إعادة توصيل» في الهاتف).
 */
object LibraryDefaults {
    val CATEGORIES = listOf(
        "القرآن الكريم",
        "الحديث النبوي",
        "النشيد الوطني",
        "الأناشيد المدرسية",
        "المؤثرات الصوتية",
        "الفقرات الثقافية",
        "فقرات متنوعة"
    )
    const val FALLBACK_CATEGORY = "فقرات متنوعة"
}

data class LibraryEntry(
    val id: Long,
    val path: String? = null,
    val title: String? = null,
    val category: String? = null,
    val addedAtEpochMs: Long? = null
)

data class LibraryDoc(
    val categories: List<String> = emptyList(),
    val entries: List<LibraryEntry> = emptyList()
)

/** بحث خالص حتمي — مطابقة أسماء مستقلة عن حالة الأحرف. */
object LibraryMath {
    fun matches(title: String?, category: String?, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val t = title.orEmpty()
        val c = category.orEmpty()
        return t.contains(q, ignoreCase = true) || c.contains(q, ignoreCase = true)
    }
}

class MusicLibraryStore(private val file: File) {

    private var nextId: Long = 1L
    private var doc: LibraryDoc = load()

    private fun load(): LibraryDoc {
        val d = Json.load(file, LibraryDoc())
        val cats = (d.categories.ifEmpty { LibraryDefaults.CATEGORIES })
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        // ضمان وجود التصنيفات الافتراضية السبع أولًا (نفس سلوك نسخة الهاتف)
        val merged = (LibraryDefaults.CATEGORIES.filter { it !in cats }) + cats
        val safe = d.copy(
            categories = merged,
            entries = d.entries.mapNotNull { e ->
                val path = e.path?.trim().orEmpty()
                val title = e.title?.trim().orEmpty()
                if (e.id <= 0L || path.isEmpty() || title.isEmpty()) null
                else e.copy(
                    path = path,
                    title = title,
                    category = e.category?.trim()?.takeIf { it.isNotEmpty() && it in merged }
                        ?: LibraryDefaults.FALLBACK_CATEGORY,
                    addedAtEpochMs = e.addedAtEpochMs ?: 0L
                )
            }
        )
        nextId = (safe.entries.maxOfOrNull { it.id } ?: 0L) + 1L
        return safe
    }

    fun categories(): List<String> = doc.categories
    fun entries(): List<LibraryEntry> = doc.entries

    /** المسارات المفقودة (الملف غير موجود على القرص الآن). */
    fun missingPaths(): Set<String> =
        doc.entries.mapNotNull { e ->
            val p = e.path ?: return@mapNotNull null
            if (!File(p).isFile) p else null
        }.toSet()

    /** نتيجة استيراد: المقبول + عدد المتجاوز لصيغة غير مدعومة (يُعرض للمستخدم لا صمتًا). */
    data class ImportResult(val added: List<LibraryEntry>, val skippedUnsupported: Int)

    /**
     * إضافة ملفات ضمن تصنيف (التصنيف الغريب يُرجع للتصنيف الافتراضي).
     * الملفات بصيغة لا يفكّها المحرك تُرفض هنا — لا تدخل المكتبة أبدًا مقاطع
     * لا يمكن تشغيلها (درس خلل «المكتبة تظهر ولا تشتغل»).
     */
    fun addFiles(files: List<File>, category: String): ImportResult {
        val cat = if (category in doc.categories) category else LibraryDefaults.FALLBACK_CATEGORY
        val existing = doc.entries.mapNotNull { it.path }.toSet()
        val usable = files.filter {
            it.isFile && it.path !in existing &&
                    it.extension.lowercase() in com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS
        }
        val skipped = files.count { it.isFile && it !in usable }
        val added = usable.map {
            LibraryEntry(nextId++, it.path, it.nameWithoutExtension, cat, System.currentTimeMillis())
        }
        if (added.isNotEmpty()) {
            doc = doc.copy(entries = doc.entries + added)
            persist()
        }
        return ImportResult(added, skipped)
    }

    /** إعادة ربط ملف مفقود بمسار جديد (نفس اسم المكتبة يتغير موقعه). */
    fun relink(id: Long, newPath: File): Boolean {
        if (!newPath.isFile) return false
        val i = doc.entries.indexOfFirst { it.id == id }
        if (i < 0) return false
        doc = doc.copy(
            entries = doc.entries.mapIndexed { k, e ->
                if (k == i) e.copy(path = newPath.path) else e
            }
        )
        persist()
        return true
    }

    fun remove(id: Long): Boolean {
        if (doc.entries.none { it.id == id }) return false
        doc = doc.copy(entries = doc.entries.filter { it.id != id })
        persist()
        return true
    }

    /** تصنيف مخصص جديد — المكرر أو الفارغ يُرفض. */
    fun addCategory(name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty() || clean in doc.categories) return false
        doc = doc.copy(categories = doc.categories + clean)
        persist()
        return true
    }

    /** حذف تصنيف: عناصره تعود للتصنيف الافتراضي — التصنيفات الافتراضية السبع لا تُحذف. */
    fun deleteCategory(name: String): Boolean {
        if (name in LibraryDefaults.CATEGORIES) return false
        if (name !in doc.categories) return false
        doc = doc.copy(
            categories = doc.categories.filter { it != name },
            entries = doc.entries.map {
                if (it.category == name) it.copy(category = LibraryDefaults.FALLBACK_CATEGORY) else it
            }
        )
        persist()
        return true
    }

    /** نقل مقطع إلى تصنيف آخر — التصنيف يجب أن يكون موجودًا. */
    fun moveEntry(id: Long, category: String): Boolean {
        if (category !in doc.categories) return false
        val i = doc.entries.indexOfFirst { it.id == id }
        if (i < 0) return false
        if (doc.entries[i].category == category) return true
        doc = doc.copy(
            entries = doc.entries.mapIndexed { k, e ->
                if (k == i) e.copy(category = category) else e
            }
        )
        persist()
        return true
    }

    /** عدد المقاطع في كل تصنيف — لعرض الشارات على رقائق التصفية. */
    fun countByCategory(): Map<String, Int> =
        doc.entries.groupingBy { it.category ?: LibraryDefaults.FALLBACK_CATEGORY }.eachCount()

    private fun persist() {
        Json.save(file, doc)
    }
}
