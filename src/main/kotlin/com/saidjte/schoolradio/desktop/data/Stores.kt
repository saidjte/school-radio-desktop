package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * مخزن سكريبت المذيع: تحميل/حفظ script.json ذريًا مع تعقيم الحقول
 * بعد القراءة (حماية من ملف تالف أو ناقص).
 */
class ScriptStore(private val file: File) {

    private var nextId: Long = 1L
    private var doc: ScriptDoc = load()

    private fun load(): ScriptDoc {
        val d = Json.load(file, ScriptDoc())
        val safe = d.copy(
            sections = d.sections.mapNotNull { s ->
                if (s.id <= 0L) null else s.copy(title = s.title ?: "", body = s.body ?: "")
            }
        )
        nextId = (safe.sections.maxOfOrNull { it.id } ?: 0L) + 1L
        return safe
    }

    fun sections(): List<ScriptSection> = doc.sections

    fun add(title: String, body: String): ScriptSection {
        val s = ScriptSection(nextId++, title.trim(), body.trim())
        doc = doc.copy(sections = doc.sections + s)
        persist()
        return s
    }

    fun update(id: Long, title: String, body: String): Boolean {
        val i = doc.sections.indexOfFirst { it.id == id }
        if (i < 0) return false
        doc = doc.copy(
            sections = doc.sections.mapIndexed { k, s ->
                if (k == i) s.copy(title = title.trim(), body = body.trim()) else s
            }
        )
        persist()
        return true
    }

    fun delete(id: Long): Boolean {
        if (doc.sections.none { it.id == id }) return false
        doc = doc.copy(sections = doc.sections.filter { it.id != id })
        persist()
        return true
    }

    fun move(id: Long, delta: Int): Boolean {
        val i = doc.sections.indexOfFirst { it.id == id }
        if (i < 0) return false
        val moved = ScheduleMath.moved(doc.sections, i, delta)
        if (moved === doc.sections) return false
        doc = doc.copy(sections = moved)
        persist()
        return true
    }

    private fun persist() {
        Json.save(file, doc)
    }
}

/**
 * مخزن جدولة الفقرات: تحميل/حفظ schedule.json ذريًا مع تعقيم الأنواع
 * غير المعروفة (enum أجنبي → SPEECH).
 */
class ScheduleStore(private val file: File) {

    private var nextId: Long = 1L
    private var doc: ScheduleDoc = load()

    private fun load(): ScheduleDoc {
        val d = Json.load(file, ScheduleDoc())
        val safe = d.copy(
            items = d.items.mapNotNull { it ->
                if (it.id <= 0L) null
                else it.copy(
                    title = it.title ?: "",
                    kind = it.kind ?: SegmentKind.SPEECH,
                    plannedSeconds = (it.plannedSeconds ?: 0L).coerceAtLeast(0L),
                    mediaPath = it.mediaPath,
                    note = it.note ?: "",
                    scriptSectionId = it.scriptSectionId?.takeIf { id -> id > 0L }
                )
            }
        )
        nextId = (safe.items.maxOfOrNull { it.id } ?: 0L) + 1L
        return safe
    }

    fun items(): List<ScheduleItem> = doc.items
    fun episodeStartMinutes(): Int? = doc.episodeStartMinutes

    fun setEpisodeStart(minutes: Int?) {
        doc = doc.copy(episodeStartMinutes = minutes)
        persist()
    }

    fun add(
        title: String,
        kind: SegmentKind,
        plannedSeconds: Long,
        mediaPath: String?,
        note: String,
        scriptSectionId: Long? = null
    ): ScheduleItem {
        val item = ScheduleItem(
            nextId++, title.trim(), kind,
            plannedSeconds.coerceAtLeast(0L), mediaPath,
            note.trim(), scriptSectionId?.takeIf { it > 0L }
        )
        doc = doc.copy(items = doc.items + item)
        persist()
        return item
    }

    fun update(
        id: Long,
        title: String,
        kind: SegmentKind,
        plannedSeconds: Long,
        mediaPath: String?,
        note: String,
        scriptSectionId: Long? = null
    ): Boolean {
        val i = doc.items.indexOfFirst { it.id == id }
        if (i < 0) return false
        doc = doc.copy(
            items = doc.items.mapIndexed { k, s ->
                if (k == i) s.copy(
                    title = title.trim(),
                    kind = kind,
                    plannedSeconds = plannedSeconds.coerceAtLeast(0L),
                    mediaPath = mediaPath,
                    note = note.trim(),
                    scriptSectionId = scriptSectionId?.takeIf { it > 0L }
                ) else s
            }
        )
        persist()
        return true
    }

    fun delete(id: Long): Boolean {
        if (doc.items.none { it.id == id }) return false
        doc = doc.copy(items = doc.items.filter { it.id != id })
        persist()
        return true
    }

    fun move(id: Long, delta: Int): Boolean {
        val i = doc.items.indexOfFirst { it.id == id }
        if (i < 0) return false
        val moved = ScheduleMath.moved(doc.items, i, delta)
        if (moved === doc.items) return false
        doc = doc.copy(items = moved)
        persist()
        return true
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
