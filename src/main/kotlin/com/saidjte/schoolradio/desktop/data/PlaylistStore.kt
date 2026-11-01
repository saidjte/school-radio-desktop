package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * قوائم التشغيل الدائمة المسماة (v2): عدة قوائم باسم يختارها المستخدم،
 * كل قائمة مسارات مرتبة + مؤشر الجاري، وقائمة واحدة «الجارية» في المشغل،
 * وتكرار القائمة الجارية عند انتهائها. تُحفظ في playlist.json وتبقى بعد
 * إعادة التشغيل — وملف النسخة السابقة (قائمة وحيدة بلا أسماء) يُستقبل
 * آليًا كقائمة «القائمة الرئيسية» دون أي فقد.
 */
data class NamedPlaylist(
    val id: Long = 0L,
    val name: String = "",
    val paths: List<String> = emptyList(),
    val currentIndex: Int? = null
)

data class PlaylistDoc(
    // حقول النسخة v1 — تبقى للتوافق عند القراءة، ثم تهاجر إلى «playlists»
    val paths: List<String> = emptyList(),
    val currentIndex: Int? = null,
    val repeatAll: Boolean? = null,
    // حقول v2: القوائم المسماة + الهوية القارية + عدّاد الهويات
    val playlists: List<NamedPlaylist> = emptyList(),
    val activeId: Long? = null,
    val nextId: Long? = null
)

/** منطق خالص حتمي — ترحيل النسخة القديمة وضمان تماسك الوثيقة (قابل للاختبار بلا ملفات). */
object PlaylistMath {
    const val DEFAULT_NAME = "القائمة الرئيسية"

    /**
     * تطهير الوثيقة المحمّلة: ترحيل قائمة v1 الوحيدة إلى قائمة مسماة،
     * ضمان وجود قائمة واحدة على الأقل، وهوية قارية صحيحة، ومؤشرات داخل الحدود.
     */
    fun resolve(d: PlaylistDoc): PlaylistDoc {
        var playlists = d.playlists.map { p ->
            p.copy(
                id = p.id,
                name = p.name.trim().ifEmpty { DEFAULT_NAME },
                paths = p.paths.filter { it.isNotBlank() },
                currentIndex = p.currentIndex?.takeIf { it >= 0 && it < p.paths.size }
            )
        }.filter { it.id > 0L }

        if (playlists.isEmpty()) {
            // ترحيل v1 → v2: القائمة الوحيدة القديمة تصبح «القائمة الرئيسية»
            playlists = listOf(
                NamedPlaylist(
                    id = 1L,
                    name = DEFAULT_NAME,
                    paths = d.paths.filter { it.isNotBlank() },
                    currentIndex = d.currentIndex?.takeIf { it >= 0 && it < d.paths.size }
                )
            )
        }

        var next = maxOf(d.nextId ?: 0L, playlists.maxOfOrNull { it.id } ?: 0L) + 1L
        // هوية قارية صحيحة: تشير إلى قائمة موجودة، وإلا الأولى
        val active = d.activeId?.takeIf { id -> playlists.any { it.id == id } }
            ?: playlists.first().id

        return PlaylistDoc(
            paths = emptyList(), // تم ترحيلها — لا تُكتب مجددًا
            currentIndex = null,
            repeatAll = d.repeatAll ?: false,
            playlists = playlists,
            activeId = active,
            nextId = next
        )
    }

    fun indexOfId(d: PlaylistDoc, id: Long): Int = d.playlists.indexOfFirst { it.id == id }

    fun activeIndex(d: PlaylistDoc): Int = indexOfId(d, d.activeId ?: -1L)

    fun active(d: PlaylistDoc): NamedPlaylist =
        d.playlists.firstOrNull { it.id == d.activeId } ?: d.playlists.first()

    /** اسم غير مكرر يُقبل، والمكرر/الفارغ يُرفض. */
    fun nameAcceptable(d: PlaylistDoc, name: String, exceptId: Long = -1L): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        return d.playlists.none { it.id != exceptId && it.name == clean }
    }
}

class PlaylistStore(private val file: File) {

    private var doc: PlaylistDoc

    init {
        val d = Json.load(file, PlaylistDoc())
        doc = PlaylistMath.resolve(d)
        // ترحيل قائمة النسخة السابقة (أو ولادة الافتراضية) يُكتب فورًا كي لا يتكرر
        if (d.playlists.isEmpty()) persist()
    }

    fun doc(): PlaylistDoc = doc

    // ================= قراءة =================

    fun playlists(): List<NamedPlaylist> = doc.playlists
    fun activeId(): Long = doc.activeId ?: doc.playlists.first().id
    fun active(): NamedPlaylist = PlaylistMath.active(doc)
    fun paths(): List<String> = active().paths
    fun currentIndex(): Int = active().currentIndex ?: -1
    fun repeatAll(): Boolean = doc.repeatAll ?: false

    // ================= إدارة القوائم المسماة =================

    /** إنشاء قائمة جديدة فارغة وتفعيلها — الاسم المكرر/الفارغ يُرفض. */
    fun create(name: String): NamedPlaylist? {
        if (!PlaylistMath.nameAcceptable(doc, name)) return null
        val p = NamedPlaylist(
            id = doc.nextId ?: (doc.playlists.maxOfOrNull { it.id } ?: 0L) + 1L,
            name = name.trim()
        )
        doc = doc.copy(
            playlists = doc.playlists + p,
            activeId = p.id,
            nextId = (doc.nextId ?: p.id) + 1L
        )
        persist()
        return p
    }

    fun rename(id: Long, name: String): Boolean {
        if (!PlaylistMath.nameAcceptable(doc, name, exceptId = id)) return false
        val i = PlaylistMath.indexOfId(doc, id)
        if (i < 0) return false
        doc = doc.copy(playlists = doc.playlists.mapIndexed { k, p ->
            if (k == i) p.copy(name = name.trim()) else p
        })
        persist()
        return true
    }

    /** حذف قائمة — تبقى قائمة واحدة على الأقل؛ وحذف الجارية يفعّل الأولى. */
    fun delete(id: Long): Boolean {
        if (doc.playlists.size <= 1) return false
        if (doc.playlists.none { it.id == id }) return false
        val rest = doc.playlists.filter { it.id != id }
        doc = doc.copy(
            playlists = rest,
            activeId = if (doc.activeId == id) rest.first().id else doc.activeId
        )
        persist()
        return true
    }

    fun setActive(id: Long): Boolean {
        if (doc.playlists.none { it.id == id }) return false
        doc = doc.copy(activeId = id)
        persist()
        return true
    }

    // ================= محتوى القائمة الجارية =================

    /** استبدال مسارات قائمة محددة مع مؤشر الجاري (يُقيّد داخل الحدود). */
    fun setPaths(id: Long, paths: List<String>, currentIndex: Int) {
        val i = PlaylistMath.indexOfId(doc, id)
        if (i < 0) return
        val clean = paths.filter { it.isNotBlank() }
        doc = doc.copy(playlists = doc.playlists.mapIndexed { k, p ->
            if (k == i) p.copy(
                paths = clean,
                currentIndex = currentIndex.takeIf { it >= 0 && it < clean.size }
            ) else p
        })
        persist()
    }

    fun setIndex(id: Long, index: Int) {
        val i = PlaylistMath.indexOfId(doc, id)
        if (i < 0) return
        doc = doc.copy(playlists = doc.playlists.mapIndexed { k, p ->
            if (k == i) p.copy(currentIndex = index.takeIf { it >= 0 && it < p.paths.size }) else p
        })
        persist()
    }

    fun setRepeat(on: Boolean) {
        doc = doc.copy(repeatAll = on)
        persist()
    }

    /** تفريغ قائمة محددة (القائمة الجارية افتراضيًا) مع الحفاظ على التكرار. */
    fun clear(id: Long = activeId()) {
        val i = PlaylistMath.indexOfId(doc, id)
        if (i < 0) return
        doc = doc.copy(playlists = doc.playlists.mapIndexed { k, p ->
            if (k == i) p.copy(paths = emptyList(), currentIndex = null) else p
        })
        persist()
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
