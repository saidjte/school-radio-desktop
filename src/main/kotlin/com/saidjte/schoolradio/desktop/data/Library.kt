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

    /** إضافة ملفات ضمن تصنيف (التصنيف الغريب يُرجع للتصنيف الافتراضي). */
    fun addFiles(files: List<File>, category: String): List<LibraryEntry> {
        val cat = if (category in doc.categories) category else LibraryDefaults.FALLBACK_CATEGORY
        val existing = doc.entries.mapNotNull { it.path }.toSet()
        val added = files
            .filter { it.isFile && it.path !in existing }
            .map { LibraryEntry(nextId++, it.path, it.nameWithoutExtension, cat, System.currentTimeMillis()) }
        if (added.isNotEmpty()) {
            doc = doc.copy(entries = doc.entries + added)
            persist()
        }
        return added
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

    private fun persist() {
        Json.save(file, doc)
    }
}
