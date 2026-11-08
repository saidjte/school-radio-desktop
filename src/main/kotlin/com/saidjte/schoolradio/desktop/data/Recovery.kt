package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * استعادة المشاريع بعد انقطاع مفاجئ (v0.39.0 «استعادة المشاريع بعد انقطاع
 * مفاجئ») — خطان معًا:
 * 1. قفل تشغيل: ملف run.lock يُنشأ عند الإقلاع ويُحذف عند إغلاق نظيف؛ وجوده
 *    عند إقلاع جديد يعني أن الجلسة السابقة انتهت فجأة (انقطاع كهرباء، إجهاض)
 *    فيُعلَم المذيع بذلك ويُفحص المخازن بعناية أكبر.
 * 2. فحص الفساد واستعادة: كل مخزن موجود لكن لا يُقرأ JSON سليمًا يُعزل في
 *    `<dataDir>/corrupt/` (دليل لا يضيع أبدًا) وتُنسخ مكانه أحدث نسخة سليمة
 *    من النسخ المتدحرجة — فلا ملف تالف يُسقط بيانات بصمت ولا يكسر إقلاعًا.
 *
 * كل الدوال نقية على مجلدات حقيقية — تُختبَع عقدًا وتُستدعى من AppViewModel
 * قبل بناء المخازن فتحمي كل طبقات التخزين دفعة واحدة.
 */
object Recovery {

    /**
     * هل النص JSON سليم نحويًا؟ فحص صارم بقارئ Gson غير المتساهل — الوضع
     * المتساهل يقبل نصوصًا مبتورة وقيمًا فارغة فلا يصلح لكشف التلف، والصارم
     * يرفض المبتور ويطالب بقيمة واحدة تنتهي عند نهاية النص لا قبلها.
     */
    fun isParseableJsonText(text: String): Boolean {
        if (text.isBlank()) return false
        return try {
            val reader = com.google.gson.stream.JsonReader(java.io.StringReader(text))
            reader.isLenient = false
            com.google.gson.JsonParser.parseReader(reader)
            reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT
        } catch (_: Exception) {
            false
        }
    }

    /** هل الملف JSON سليم؟ الملف الغائب ليس تالفًا — إنه ببساطة غير موجود. */
    fun isParseableJson(f: File): Boolean {
        if (!f.isFile) return true
        return try {
            isParseableJsonText(f.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            false
        }
    }

    /** نتيجة جولة الفحص — الرسائل العربية التي تُعرض في شعار الاستعادة. */
    data class Report(
        val notices: List<String>,
        val recoveredFromBackup: List<String>,
        val quarantined: List<String>
    ) {
        val isEmpty: Boolean get() = notices.isEmpty()
    }

    /** مجلد العزل داخل بيانات التطبيق. */
    fun corruptDir(dataDir: File): File = File(dataDir, "corrupt")

    /**
     * جولة فحص واستعادة لكل المخازن: التالف يُعزل باسم مؤرخ ثم تُستبدل مكانه
     * أحدث نسخة سليمة إن وُجدت. فشل فردي لا يفشل الجولة، ولا شيء يُكتب فوق
     * تالف بغير محتوى سليم.
     */
    fun recoverCorruptStores(
        dataDir: File,
        names: List<String> = AutoBackup.BACKED_UP_FILES,
        nowMs: Long
    ): Report {
        val notices = ArrayList<String>()
        val recovered = ArrayList<String>()
        val quarantined = ArrayList<String>()
        val qdir = corruptDir(dataDir)
        for (name in names) {
            val store = File(dataDir, name)
            if (!store.isFile) continue
            if (isParseableJson(store)) continue
            // عزل التالف أولًا — الدليل الأصلي لا يُمحى أبدًا
            val quarantineTarget = File(qdir, "$name.$nowMs")
            try {
                qdir.mkdirs()
                java.nio.file.Files.move(
                    store.toPath(), quarantineTarget.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                quarantined.add(name)
            } catch (_: Exception) {
                notices.add("تعذّر عزل «$name» التالف — سيبدأ من جديد احتياطًا")
                continue
            }
            // استعادة أحدث نسخة سليمة إن وجدت
            val restored = AutoBackup.recoverFromBackup(store, AutoBackup.backupsDir(dataDir))
            if (restored != null) {
                try {
                    val tmp = File(dataDir, "$name.recovery.tmp")
                    tmp.writeText(restored, Charsets.UTF_8)
                    java.nio.file.Files.move(
                        tmp.toPath(), store.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE
                    )
                    recovered.add(name)
                    notices.add("«$name» كان تالفًا بعد الانقطاع — استُعيد من آخر نسخة احتياطية سليمة")
                } catch (_: Exception) {
                    notices.add("«$name» كان تالفًا ولم تتم استعادته — يبدأ من جديد، الأصل معزول في corrupt")
                }
            } else {
                notices.add("«$name» كان تالفًا وبلا نسخة سابقة — يبدأ من جديد، الأصل معزول في corrupt")
            }
        }
        return Report(notices, recovered, quarantined)
    }

    // ================= قفل التشغيل: كشف الانقطاع المفاجئ =================

    /** ملف القفل داخل بيانات التطبيق. */
    fun lockFile(dataDir: File): File = File(dataDir, "run.lock")

    /** هل كانت الجلسة السابقة مقطوعة (القفل موجود من غير إغلاق نظيف)؟ */
    fun wasInterrupted(dataDir: File): Boolean = lockFile(dataDir).isFile

    /** اقتناء القفل عند الإقلاع — محتواه توثيقي (بداية الجلسة) لا قرار. */
    fun acquireLock(dataDir: File, nowMs: Long) {
        try {
            val f = lockFile(dataDir)
            f.parentFile?.mkdirs()
            f.writeText("""{"startedAt": $nowMs}""", Charsets.UTF_8)
        } catch (_: Exception) {
            // قفل غير ممكن لا يوقف إقلاعًا — الفحص يبقى أمانًا زائدًا لا حاجة
        }
    }

    /** تحرير القفل عند إغلاق نظيف — فلا إقلاع تالٍ يُتهم ظلمًا. */
    fun releaseLock(dataDir: File) {
        try { lockFile(dataDir).delete() } catch (_: Exception) { }
    }
}
