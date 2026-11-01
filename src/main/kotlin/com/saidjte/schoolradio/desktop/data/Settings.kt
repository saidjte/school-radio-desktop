package com.saidjte.schoolradio.desktop.data

import java.io.File

/** إعدادات التطبيق العامة — تُحفظ في settings.json. */
data class AppSettings(
    val schoolName: String? = null
)

/** مخزن الإعدادات: تحميل/حفظ settings.json ذريًا مع تعقيم الحقول. */
class SettingsStore(private val file: File) {

    private var doc: AppSettings = load()

    private fun load(): AppSettings {
        val d = Json.load(file, AppSettings())
        return d.copy(schoolName = d.schoolName?.trim()?.takeIf { it.isNotEmpty() })
    }

    fun settings(): AppSettings = doc

    fun schoolName(): String? = doc.schoolName

    fun setSchoolName(name: String) {
        doc = doc.copy(schoolName = name.trim().takeIf { it.isNotEmpty() })
        persist()
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
