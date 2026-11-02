package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.data.Json
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
    val lastModified: Long,
    /** توقيع المُسجِّل من الملف الجانبي (null إن لم يوجد). */
    val recorderName: String? = null,
    val recorderRole: String? = null
)

/** توقيع من سجّل هذا الملف — يُكتب تلقائيًا لحظة إتمام التسجيل (ملف جانبي .rec.json). */
data class RecordingMeta(
    val recorderName: String? = null,
    val recorderRole: String? = null,
    val startedAtEpochMs: Long? = null,
    val durationMs: Long? = null
) {
    fun save(sidecar: File): Boolean = Json.save(sidecar, this)

    companion object {
        fun load(sidecar: File): RecordingMeta? {
            if (!sidecar.isFile) return null
            val m = Json.load(sidecar, RecordingMeta())
            val name = m.recorderName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            return m.copy(recorderName = name, recorderRole = m.recorderRole?.trim()?.takeIf { it.isNotEmpty() })
        }

        /** مسار الملف الجانبي لتوقيع تسجيل. */
        fun sidecarOf(media: File): File =
            File(media.parentFile, media.nameWithoutExtension + ".rec.json")
    }
}

/** ماسح مكتبة التسجيلات: WAV عبر ترويسته، وMP3 عبر محلّل الإطارات. */
object RecordingsScanner {

    /** كل الصيغ القابلة للتشغيل في المحرك — المرجع الوحيد لبوابات الاستيراد والمسح. */
    val AUDIO_EXTENSIONS = PLAYABLE_EXTENSIONS

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
            val meta = RecordingMeta.load(RecordingMeta.sidecarOf(f))
            RecordingItem(
                f, f.nameWithoutExtension, duration, f.length(), f.lastModified(),
                recorderName = meta?.recorderName,
                recorderRole = meta?.recorderRole
            )
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

    /** نقل التوقيع الجانبي مع إعادة تسمية التسجيل. */
    fun moveMeta(oldFile: File, newFile: File) {
        val oldSidecar = RecordingMeta.sidecarOf(oldFile)
        if (oldSidecar.isFile) {
            val target = RecordingMeta.sidecarOf(newFile)
            try { oldSidecar.renameTo(target) } catch (_: Exception) { }
        }
    }

    /** حذف التوقيع الجانبي مع حذف التسجيل. */
    fun deleteMeta(media: File) {
        val sidecar = RecordingMeta.sidecarOf(media)
        if (sidecar.isFile) {
            try { sidecar.delete() } catch (_: Exception) { }
        }
    }
}
