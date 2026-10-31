package com.saidjte.schoolradio.desktop.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** طبقة تخزين JSON واحدة: كتابة ذرّية (tmp ثم نقل) وتحميل متسامح. */
object Json {
    val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    inline fun <reified T> load(file: File, fallback: T): T {
        if (!file.isFile) return fallback
        return try {
            @Suppress("UNCHECKED_CAST")
            gson.fromJson(file.readText(Charsets.UTF_8), T::class.java) ?: fallback
        } catch (_: Exception) {
            fallback
        }
    }

    /** حفظ ذرّي: يُكتب ملف مؤقت ثم يُنقل فوق الهدف — لا ملفات نصف مكتوبة. */
    fun save(file: File, value: Any): Boolean = try {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(gson.toJson(value), Charsets.UTF_8)
        Files.move(
            tmp.toPath(), file.toPath(),
            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
        )
        true
    } catch (_: Exception) {
        try { File(file.parentFile, file.name + ".tmp").delete() } catch (_: Exception) { }
        false
    }
}
