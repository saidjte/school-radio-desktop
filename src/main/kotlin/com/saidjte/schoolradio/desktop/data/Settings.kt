package com.saidjte.schoolradio.desktop.data

import java.io.File

/** إعدادات التطبيق العامة — تُحفظ في settings.json. */
data class AppSettings(
    val schoolName: String? = null,
    /** وحدة المزج: فيدرات القنوات + معالج المذيع + معادل الصوت (null = الافتراضي). */
    val mixer: MixerJson? = null
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

    /** حالة وحدة المزج المحسومة من المحفوظ. */
    fun mixer(): MixerState = MixerLogic.resolve(doc.mixer)

    fun setMixer(state: MixerState) {
        doc = doc.copy(
            mixer = MixerJson(
                micFader = state.micFader,
                musicFader = state.musicFader,
                masterFader = state.masterFader,
                voice = VoiceFxJson(
                    enabled = state.voice.enabled,
                    hpfOn = state.voice.hpfOn,
                    gateOn = state.voice.gateOn,
                    gateThresholdDb = state.voice.gateThresholdDb,
                    gateReleaseMs = state.voice.gateReleaseMs,
                    compOn = state.voice.compOn,
                    compThresholdDb = state.voice.compThresholdDb,
                    compRatio = state.voice.compRatio,
                    compMakeupDb = state.voice.compMakeupDb,
                    bassDb = state.voice.bassDb,
                    presenceDb = state.voice.presenceDb,
                    airDb = state.voice.airDb,
                    preset = state.voice.preset
                ),
                musicEq = MusicEqJson(
                    enabled = state.musicEq.enabled,
                    bands = state.musicEq.bands,
                    preset = state.musicEq.preset
                )
            )
        )
        persist()
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
