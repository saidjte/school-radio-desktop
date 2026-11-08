package com.saidjte.schoolradio.desktop.data

import java.io.File
import java.security.MessageDigest

/**
 * الحفظ التلقائي المحصَّن (v0.39.0 «حفظ المشاريع تلقائيًا») — طبقة نسخ متدحرجة
 * فوق الكتابة الذرّية الموجودة: كل مخزن يتغير يُلتقط منه نسخة مؤرخة في
 * `<dataDir>/backups/` مع تدوير يحفظ الأحدث ويهجر الأقدم بعد سقف معقول.
 * الكتابة الذرّية تحمي من نصف ملف؛ والنسخ المتدحرجة تحمي من ملف سليم نحويًا
 * لكن محتواه لم يكن ما يريد المذيع (حذف بالخطأ، قالب استبدل السكريبت…) —
 * وفي الحالين يستطيع «الاستعادة بعد انقطاع مفاجئ» أن يرجع لأحدث نسخة سليمة.
 *
 * كل الدوال نقية على مجلدات حقيقية قابلة للاختبار بلا واجهة ولا محرك.
 */
object AutoBackup {

    /** أسماء المخازن المشمولة — مرجع وحيد للنظامين: النسخ الدورية والاستعادة. */
    val BACKED_UP_FILES = listOf(
        "settings.json",
        "users.json",
        "script.json",
        "schedule.json",
        "weekly.json",
        "executions.json",
        "library.json",
        "playlist.json",
        "montage.json"
    )

    /** سقف النسخ لكل مخزن — الأقدم يُهجر بعدما يمتلئ. */
    const val KEEP_PER_FILE = 8

    /** مجلد النسخ داخل بيانات التطبيق. */
    fun backupsDir(dataDir: File): File = File(dataDir, "backups")

    /** بصمة المحتوى — التغيُّر يُقاس بالمحتوى لا بالتوقيت فلا نسخ بلا معنى. */
    fun contentHash(f: File): String? = try {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { input ->
            val buf = ByteArray(8192)
            var n = input.read(buf)
            while (n > 0) {
                md.update(buf, 0, n)
                n = input.read(buf)
            }
        }
        md.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    /** أحدث نسخة محفوظة لمخزن معين — بالاسم `<الجذل>.<طابع>.bak`. */
    fun newestBackup(backupsDir: File, storeName: String): File? =
        backupsDir.listFiles { f -> f.isFile && f.name.startsWith("$storeName.") && f.name.endsWith(".bak") }
            ?.maxByOrNull { it.name }
            ?.takeIf { it.isFile }

    /** لقطة أحدث نسخة محفوظة — بلا نسخ سابقة يعاد null فتُلتقط أول لقطة دائمًا. */
    private fun referenceContent(backupsDir: File, store: File): String? {
        val last = newestBackup(backupsDir, store.name) ?: return null
        return try { last.readText(Charsets.UTF_8) } catch (_: Exception) { null }
    }

    /** نتيجة جولة نسخ — للرسالة التوثيقية في selftest وسجل العمل. */
    data class Report(val copied: List<String>, val pruned: Int) {
        val isEmpty: Boolean get() = copied.isEmpty() && pruned == 0
    }

    /**
     * جولة نسخ واحدة: مخزن تغيّر محتواه عن آخر لقطة يُنسخ باسم مؤرخ، ثم
     * تدوير لكل مخزن يُبقي الأحدث [keep] ويهجر البقية. الملف الغائب يُتجاهل
     * بصمت (لا نسخ لمخزن لم يُنشأ بعد) والفشل الفردي لا يفشل الجولة.
     */
    fun snapshot(dataDir: File, names: List<String> = BACKED_UP_FILES, keep: Int = KEEP_PER_FILE, nowMs: Long): Report {
        val dir = backupsDir(dataDir)
        dir.mkdirs()
        val copied = ArrayList<String>()
        var pruned = 0
        for (name in names) {
            val store = File(dataDir, name)
            if (!store.isFile) continue
            val content = try { store.readText(Charsets.UTF_8) } catch (_: Exception) { continue }
            val ref = referenceContent(dir, store)
            if (ref != null && ref == content) continue // بلا تغيّر عن آخر لقطة — لا نسخ بلا معنى
            val target = File(dir, "$name.$nowMs.bak")
            try {
                // كتابة في ملف مؤقت ثم نقل ذرّي — لا نسخ نصف مكتوبة قط
                val tmp = File(dir, "$name.$nowMs.tmp")
                tmp.writeText(content, Charsets.UTF_8)
                java.nio.file.Files.move(
                    tmp.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                continue
            }
            if (!target.isFile) continue
            copied.add(name)
            // تدوير: النسخ الأقدم من السقف تُهجر (الأحدث تبقى)
            val copies = dir.listFiles { f -> f.isFile && f.name.startsWith("$name.") && f.name.endsWith(".bak") }
                ?.sortedByDescending { it.name } ?: continue
            for (old in copies.drop(keep)) {
                if (old.delete()) pruned++
            }
        }
        return Report(copied, pruned)
    }

    /**
     * استعادة ملف تالف من أحدث نسخة سليمة: تُعاد بيانات النسخة إن كانت تُقرأ
     * JSON سليمًا، وإلا null فلا شيء يُنسخ (المتصل يعزل الملف التالف ويبدأ
     * من جديد — قرار الاستعادة لا يكتب فسادًا فوق فساد).
     */
    fun recoverFromBackup(corrupt: File, backupsDir: File): String? {
        val backup = newestBackup(backupsDir, corrupt.name) ?: return null
        val content = try { backup.readText(Charsets.UTF_8) } catch (_: Exception) { return null }
        if (!Recovery.isParseableJsonText(content)) return null
        return content
    }
}
