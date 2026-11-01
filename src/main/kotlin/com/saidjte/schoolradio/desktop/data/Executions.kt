package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * سجل تنفيذ الجدولة الأسبوعية — كل إطلاق أو فوات يُقيد بسطر دائم.
 * القيد الفريد (eventId + dateKey) يمنع التكرار اليومي بنفس آلية نسخة الهاتف.
 */
data class ExecutionEntry(
    val id: Long,
    val eventId: Long? = null,
    val title: String? = null,
    /** تاريخ اللحظة "yyyy-MM-dd" — نصف القيد الفريد. */
    val dateKey: String? = null,
    val atEpochMs: Long? = null,
    val status: FireStatus? = null
)

data class ExecutionsDoc(
    val entries: List<ExecutionEntry> = emptyList()
)

class ExecutionsStore(private val file: File, private val capacity: Int = 500) {

    private var nextId: Long = 1L
    private var doc: ExecutionsDoc = load()

    private fun load(): ExecutionsDoc {
        val d = Json.load(file, ExecutionsDoc())
        val safe = d.copy(
            entries = d.entries.mapNotNull { e ->
                if (e.id <= 0L || e.dateKey == null) null
                else e.copy(
                    title = e.title ?: "",
                    atEpochMs = e.atEpochMs ?: 0L,
                    status = e.status ?: FireStatus.FIRED
                )
            }.takeLast(capacity)
        )
        nextId = (safe.entries.maxOfOrNull { it.id } ?: 0L) + 1L
        return safe
    }

    fun entries(): List<ExecutionEntry> = doc.entries.sortedByDescending { it.atEpochMs ?: 0L }

    /** هل قُيّد هذا الحدث في هذا التاريخ من قبل؟ (قيد فريد حدث+تاريخ) */
    fun has(eventId: Long, dateKey: String): Boolean =
        doc.entries.any { it.eventId == eventId && it.dateKey == dateKey }

    /**
     * قيد إطلاق — يعيد false إن كان القيد موجودًا (يمنع التكرار اليومي)
     * أو البيانات ناقصة. السجل مقصوص على آخر [capacity] سطر.
     */
    fun log(eventId: Long, title: String, dateKey: String, atEpochMs: Long, status: FireStatus): Boolean {
        if (eventId <= 0L || dateKey.isEmpty()) return false
        if (has(eventId, dateKey)) return false
        val e = ExecutionEntry(nextId++, eventId, title.trim(), dateKey, atEpochMs, status)
        val trimmed = (doc.entries + e).takeLast(capacity)
        doc = doc.copy(entries = trimmed)
        persist()
        return true
    }

    fun clear() {
        doc = doc.copy(entries = emptyList())
        persist()
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
