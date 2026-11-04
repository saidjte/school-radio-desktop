package com.saidjte.schoolradio.desktop.audio

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * الجينلات المدمجة (v0.28.0 «أضف جدار الجينغل») — نفس سبع جينلات نسخة الهاتف
 * المعالجة محليًا (-16 LUFS، قص الصمت، فويدات ضد النقرات) ومرخّصة للاستعمال
 * المجاني (Mixkit / OrangeFreeSounds — التفصيل في resources/jingles/SOURCES.md).
 * تعيش داخل موارد الجرة فتصل مع كل منصة بلا شبكة، وتُستخرج مرة واحدة إلى مجلد
 * بيانات المستخدم كي يقرأها المحرك كملفات عادية ويحفظ الجدار مساراتها.
 */
object BundledJingles {

    data class Jingle(
        val id: String,
        val fileName: String,
        val title: String,
        val purpose: String
    )

    /** الترتيب والتسميات مطابقان لـBundledJingles في نسخة الهاتف — اختبار يمنع الانفصال. */
    val ALL: List<Jingle> = listOf(
        Jingle("school_bell", "school_bell.mp3", "جرس المدرسة", "جرس الفسحة الكلاسيكي — أقوى إشارة انتقال يفهمها التلاميذ فورًا"),
        Jingle("opening_jingle", "opening_jingle.mp3", "افتتاحية مبهجة", "افتتاح موسيقي إذاعي مرح لطابور الصباح"),
        Jingle("intro_sting", "intro_sting.mp3", "مقدمة فقرة", "نغمة قصيرة تفتتح كل فقرة بلمسة إذاعية"),
        Jingle("magic_sweep", "magic_sweep.mp3", "انتقال ساحر", "انتقال انسيابي بين الفقرات بجو حديث"),
        Jingle("quick_marker", "quick_marker.mp3", "علامة سريعة", "نقطة انتقال خاطفة (أقل من ثانية) بين الإعلانات القصيرة"),
        Jingle("whoosh", "whoosh.mp3", "مرور هوائي", "whoosh إذاعي كلاسيكي للانتقالات السريعة"),
        Jingle("closing_sting", "closing_sting.mp3", "ختام هادئ", "نغمة ختام لطيفة لإنهاء الإذاعة بنعومة")
    )

    /** مسار المورد داخل الجرة. */
    fun resourcePath(j: Jingle): String = "jingles/${j.fileName}"

    /**
     * استخراج الجينلات إلى مجلد بيانات المستخدم (idempotent): الموجود سليم يُترك،
     * والناقص يُكتب في ملف مؤقت ثم يُنقل ذريًّا فلا يبقى نصف جينل إن قُطع التشغيل.
     * يعيد ملفات السبعة بالترتيب المعتمد؛ من تعذّر استخراجه يُرجع ملفه غير الموجود
     * فتظهر حالته «مفقود» في الواجهة بدل انهيار التهيئة.
     */
    fun ensureExtracted(dir: File): List<File> {
        dir.mkdirs()
        val loader = BundledJingles::class.java.classLoader
        return ALL.map { j ->
            val f = File(dir, j.fileName)
            if (!f.isFile || f.length() == 0L) {
                try {
                    loader.getResourceAsStream(resourcePath(j))?.use { ins ->
                        val tmp = File(dir, j.fileName + ".tmp")
                        BufferedOutputStream(FileOutputStream(tmp)).use { outs -> ins.copyTo(outs) }
                        if (!tmp.renameTo(f)) {
                            tmp.copyTo(f, overwrite = true)
                            tmp.delete()
                        }
                    }
                } catch (_: Exception) {
                    try { File(dir, j.fileName + ".tmp").delete() } catch (_: Exception) { }
                }
            }
            f
        }
    }
}
