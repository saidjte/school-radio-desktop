package com.saidjte.schoolradio.desktop.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * النسخ الاحتياطي JSON — نظير ميزة نسخة الهاتف:
 * - ملف واحد بصيغة JSON يجمع كل مخازن البيانات (الإعدادات/المستخدمون/السكريبت/
 *   الجدولة/الأحداث الأسبوعية/سجل التنفيذ/المكتبة/قائمة التشغيل).
 * - فحص إصدار وصياغة قبل الاستبدال (فحص السلامة).
 * - قبل الاستبدال: نسخة أمان من كل ملف حالي إلى <الاسم>.bak — لا فقدان بيانات
 *   حتى لو كان النسخة الاحتياطية تالفة جزئيًا.
 * ملفات التسجيلات الصوتية نفسها ملفات قرص حرة خارج هذه الحزمة.
 */
object Backup {

    const val FORMAT_VERSION = 1
    const val MIN_SUPPORTED_VERSION = 1
    const val APP_ID = "school-radio-desktop"

    /** أسماء ملفات المخازن المشمولة — مرجع وحيد تستعمله التصدير والاستعادة. */
    val STORE_FILES = listOf(
        "settings.json",
        "users.json",
        "script.json",
        "schedule.json",
        "weekly.json",
        "executions.json",
        "library.json",
        "playlist.json"
    )

    /** نتيجة عملية (تصدير/استعادة) للعرض في الواجهة. */
    data class Report(val ok: Boolean, val message: String)

    // ================= التصدير =================

    fun export(target: File, dataDir: File, appVersion: String): Report {
        return try {
            val stores = JsonObject()
            var included = 0
            for (name in STORE_FILES) {
                val f = File(dataDir, name)
                if (f.isFile) {
                    stores.addProperty(name, f.readText(Charsets.UTF_8))
                    included++
                }
            }
            if (included == 0) {
                return Report(false, "لا توجد بيانات للتصدير بعد")
            }
            val root = JsonObject().apply {
                addProperty("app", APP_ID)
                addProperty("format_version", FORMAT_VERSION)
                addProperty("app_version", appVersion)
                addProperty("exported_at", System.currentTimeMillis())
                add("stores", stores)
            }
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(Json.gson.toJson(root), Charsets.UTF_8)
            // فحص سلامة ذاتي: الناتج يجب أن يجتاز التحقق قبل اعتماده
            val self = verify(tmp)
            if (self == null) {
                tmp.delete()
                return Report(false, "فشل فحص السلامة الذاتي — لم يُعتمد الملف")
            }
            java.nio.file.Files.move(
                tmp.toPath(), target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
            Report(true, "صُدّرت $included مخازن إلى ${target.name}")
        } catch (t: Throwable) {
            Report(false, "تعذّر التصدير: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    // ================= التحقق (فحص السلامة) =================

    /** يتحقق من الصياغة والإصدار وصلاحية كل مخزن مضمّن — يعيد المخازن أو null. */
    fun verify(f: File): Map<String, String>? {
        return try {
            if (!f.isFile) return null
            val root = JsonParser.parseString(f.readText(Charsets.UTF_8)).asJsonObject
            val app = root.get("app")?.asString
            val version = root.get("format_version")?.asInt ?: -1
            if (app != APP_ID) return null
            if (version < MIN_SUPPORTED_VERSION || version > FORMAT_VERSION) return null
            val storesObj = root.getAsJsonObject("stores") ?: return null
            val out = mutableMapOf<String, String>()
            for ((name, value) in storesObj.entrySet()) {
                if (name !in STORE_FILES) return null
                val text = value.asString
                // كل مخزن مضمّن يجب أن يكون JSON سليمًا
                JsonParser.parseString(text)
                out[name] = text
            }
            if (out.isEmpty()) null else out
        } catch (_: Exception) {
            null
        }
    }

    // ================= الاستعادة =================

    /**
     * استعادة موثوقة: تحقق → نسخة أمان .bak لكل ملف حالي → كتابة ذرية →
     * قائمة الأسماء المستعادة (المتصل يعيد تحميل المخازن).
     */
    fun restore(f: File, dataDir: File): Report {
        val stores = verify(f) ?: return Report(false, "ملف النسخة غير صالح أو إصداره غير مدعوم")
        return try {
            dataDir.mkdirs()
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            var restored = 0
            for ((name, text) in stores) {
                val target = File(dataDir, name)
                if (target.isFile) {
                    val bak = File(dataDir, "$name.bak-$stamp")
                    java.nio.file.Files.copy(
                        target.toPath(), bak.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING
                    )
                }
                val tmp = File(dataDir, "$name.restore-tmp")
                tmp.writeText(text, Charsets.UTF_8)
                java.nio.file.Files.move(
                    tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                restored++
            }
            Report(true, "استُعيدت $restored مخازن — نسخ الأمان باسم *.bak-$stamp")
        } catch (t: Throwable) {
            Report(false, "تعذّرت الاستعادة: ${t.message ?: t.javaClass.simpleName}")
        }
    }
}
