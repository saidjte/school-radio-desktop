package com.saidjte.schoolradio.desktop.audio

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** عنصر في مكتبة التسجيلات. */
data class RecordingItem(
    val file: File,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val lastModified: Long
)

/** ماسح مكتبة التسجيلات: WAV عبر ترويسته، وMP3 عبر محلّل الإطارات. */
object RecordingsScanner {

    val AUDIO_EXTENSIONS = setOf("wav", "mp3")

    fun scan(dir: File): List<RecordingItem> {
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in AUDIO_EXTENSIONS }
            ?: return emptyList()
        return files.map { f ->
            val ext = f.extension.lowercase()
            val duration = when (ext) {
                "wav" -> WavHeader.read(f)?.let {
                    WavFileWriter.durationMsOf(it.dataBytes, it.sampleRate, it.channels, it.bitsPerSample)
                }
                "mp3" -> Mp3Info.analyze(f)?.durationMs
                else -> null
            } ?: 0L
            RecordingItem(f, f.nameWithoutExtension, duration, f.length(), f.lastModified())
        }.sortedByDescending { it.lastModified }
    }

    /** ملف تسجيل جديد باسم زمني فريد داخل المجلد المحدد. */
    fun newRecordingFile(dir: File, prefix: String = "تسجيل", extension: String = "wav"): File {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        var f = File(dir, "${prefix}_$stamp.$extension")
        var n = 1
        while (f.exists()) {
            f = File(dir, "${prefix}_${stamp}_$n.$extension")
            n++
        }
        return f
    }
}
