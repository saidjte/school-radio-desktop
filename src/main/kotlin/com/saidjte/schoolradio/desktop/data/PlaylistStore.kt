package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * قائمة التشغيل الدائمة: مسارات مرتبة + مؤشر الجاري + تكرار القائمة.
 * تُحفظ في playlist.json وتبقى بعد إعادة التشغيل (مثل نسخة الهاتف).
 */
data class PlaylistDoc(
    val paths: List<String> = emptyList(),
    val currentIndex: Int? = null,
    val repeatAll: Boolean? = null
)

class PlaylistStore(private val file: File) {

    private var doc: PlaylistDoc = load()

    private fun load(): PlaylistDoc {
        val d = Json.load(file, PlaylistDoc())
        return d.copy(
            paths = d.paths.filter { it.isNotBlank() },
            currentIndex = d.currentIndex?.takeIf { it >= 0 },
            repeatAll = d.repeatAll ?: false
        )
    }

    fun doc(): PlaylistDoc = doc

    fun paths(): List<String> = doc.paths
    fun currentIndex(): Int = doc.currentIndex ?: -1
    fun repeatAll(): Boolean = doc.repeatAll ?: false

    fun set(paths: List<String>, currentIndex: Int) {
        doc = doc.copy(
            paths = paths.filter { it.isNotBlank() },
            currentIndex = currentIndex.takeIf { it >= 0 && it < paths.size }
        )
        persist()
    }

    fun setIndex(index: Int) {
        doc = doc.copy(currentIndex = index.takeIf { it >= 0 })
        persist()
    }

    fun setRepeat(on: Boolean) {
        doc = doc.copy(repeatAll = on)
        persist()
    }

    fun clear() {
        doc = PlaylistDoc(paths = emptyList(), currentIndex = null, repeatAll = doc.repeatAll)
        persist()
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
