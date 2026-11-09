package com.saidjte.schoolradio.desktop.audio

import com.saidjte.schoolradio.desktop.data.SoundGroups
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * مكتبة الأصوات والموسيقى المدمجة (v0.42.0) — ستة وثلاثون صوتًا في خمس مجموعات
 * مولدة برمجيًا داخل المشروع (CC0 — الاستخدام التجاري مسموح، التفصيل في
 * resources/sounds/SOURCES.md). تعيش داخل موارد الجرة فتصل مع كل منصة بلا شبكة،
 * وتُستخرج مرة واحدة إلى مجلد بيانات المستخدم كي تقرأها المحركات كملفات عادية
 * وتُضاف إلى المكتبة الرئيسية بنقرة.
 *
 * المدد المذكورة مدة الخلق المنطقية (±50ms من الملف الفعلي لسقف MP3).
 */
object BundledSounds {

    data class Sound(
        val id: String,
        val fileName: String,
        val title: String,
        val group: String,
        val description: String,
        val durationMs: Long,
        val tags: List<String>
    )

    val ALL: List<Sound> = listOf(
        // ================= الموسيقى الخلفية (8) =================
        Sound("morning_bright", "morning_bright.mp3", "صباح مشرق", SoundGroups.MUSIC,
            "موسيقى صباحية مبهجة بأوتار مشطوبة وجرس خفيف — افتتاحية الطابور والإعلانات المرحة",
            39_800, listOf("موسيقى", "خلفية", "صباح", "مبهج", "طابور")),
        Sound("assembly_pulse", "assembly_pulse.mp3", "نبض الطابور", SoundGroups.MUSIC,
            "إيقاع نشيط بنبض حي يليق بالطابور الصباحي والفقرات الحماسية",
            33_400, listOf("موسيقى", "خلفية", "حماس", "إيقاع", "طابور")),
        Sound("contemplation", "contemplation.mp3", "هدوء التأمل", SoundGroups.MUSIC,
            "وسادة هادئة مع ناي تأملي — للفقرات الدينية والثقافية الهادئة",
            42_500, listOf("موسيقى", "خلفية", "تأمل", "هدوء", "دين")),
        Sound("anthem_warm", "anthem_warm.mp3", "وقار وطني", SoundGroups.MUSIC,
            "تستر دافئ مهيب بنبض خفيف — المناسبات الوطنية والأعياد",
            44_000, listOf("موسيقى", "خلفية", "وطني", "مهيب", "مناسبات")),
        Sound("joy_ansheed", "joy_ansheed.mp3", "فرحة النشيد", SoundGroups.MUSIC,
            "أجراس مرحّة بإيقاع خفيف — الأناشيد والفقرات المرحة للأطفال",
            32_100, listOf("موسيقى", "خلفية", "مرح", "أطفال", "أناشيد")),
        Sound("dream_journey", "dream_journey.mp3", "رحلة خيالية", SoundGroups.MUSIC,
            "أجواء حلمية متلألئة — الفقرات القصصية والحكايات",
            41_400, listOf("موسيقى", "خلفية", "حلم", "قصة", "خيال")),
        Sound("focus_study", "focus_study.mp3", "تركيز الدراسة", SoundGroups.MUSIC,
            "طبقة دراسية هادئة بإيقاع خفيف — فقرات المعرفة والدراسة",
            49_400, listOf("موسيقى", "خلفية", "دراسة", "تركيز", "هدوء")),
        Sound("calm_outro", "calm_outro.mp3", "ختام هادئ", SoundGroups.MUSIC,
            "خاتمة دافئة بنعومة — إنهاء الحصة الإذاعية بهدوء",
            45_700, listOf("موسيقى", "خلفية", "ختام", "هدوء", "نهاية")),

        // ================= مؤثرات جاهزة (8) =================
        Sound("handbell", "handbell.mp3", "جرس يد", SoundGroups.SFX,
            "نقرتا جرس يد صافيتان — استدعاء وإشارة بداية",
            2_200, listOf("مؤثر", "جرس", "استدعاء", "نقرة")),
        Sound("applause", "applause.mp3", "تصفيق جمهور", SoundGroups.SFX,
            "تصفيق جمهور حي — بعد الفقرات والفوز والاحتفالات",
            4_000, listOf("مؤثر", "تصفيق", "جمهور", "احتفال")),
        Sound("camera_click", "camera_click.mp3", "نقرة كاميرا", SoundGroups.SFX,
            "نقرة كاميرا حادة — لحظة الحدث والصورة",
            700, listOf("مؤثر", "كاميرا", "نقرة", "حدث")),
        Sound("clock_ticks", "clock_ticks.mp3", "دقات ساعة", SoundGroups.SFX,
            "أربع دقات ساعة — العد التنازلي والانتقال الزمني",
            3_600, listOf("مؤثر", "ساعة", "دق", "عد تنازلي")),
        Sound("drum_combo", "drum_combo.mp3", "ضربات طبل", SoundGroups.SFX,
            "جملة طبل نشطة — دخول الفقرات الحماسية",
            2_000, listOf("مؤثر", "طبل", "إيقاع", "حماس")),
        Sound("darbuka", "darbuka.mp3", "إيقاع دف", SoundGroups.SFX,
            "نمط دف وطبل عربي قصير — لون إيقاعي عربي أصيل",
            2_400, listOf("مؤثر", "دف", "إيقاع", "عربي")),
        Sound("referee_whistle", "referee_whistle.mp3", "صافرة حكم", SoundGroups.SFX,
            "صافرتان حادتان — البداية والنهاية والمسابقات",
            1_400, listOf("مؤثر", "صافرة", "مسابقة", "بداية")),
        Sound("big_boom", "big_boom.mp3", "ضربة عميقة", SoundGroups.SFX,
            "طبل عميق كثيف — التأكيد الكبير والدخول الدرامي",
            2_500, listOf("مؤثر", "طبل", "عميق", "تأكيد")),

        // ================= أصوات انتقالية (6) =================
        Sound("whoosh_up", "whoosh_up.mp3", "مرور صاعد", SoundGroups.TRANSITIONS,
            "مرور هوائي صاعد ينقي الفقرات بسرعة",
            1_800, listOf("انتقال", "هوائي", "صاعد", "سريع")),
        Sound("riser_rise", "riser_rise.mp3", "صعود متصاعد", SoundGroups.TRANSITIONS,
            "صعود توتر متصاعد يجهّز الجمهور للحدث",
            3_000, listOf("انتقال", "صعود", "تشويق", "توقع")),
        Sound("swoosh_down", "swoosh_down.mp3", "هبوط سريع", SoundGroups.TRANSITIONS,
            "هبوط انسيابي يغلق الفقرة بنعومة",
            1_500, listOf("انتقال", "هبوط", "إغلاق", "سريع")),
        Sound("sparkle", "sparkle.mp3", "وميض نجمي", SoundGroups.TRANSITIONS,
            "أجراس متلألئة صاعدة — دخول الفقرات الساحرة",
            3_000, listOf("انتقال", "وميض", "أجراس", "ساحر")),
        Sound("radio_tune", "radio_tune.mp3", "ضبط إذاعي", SoundGroups.TRANSITIONS,
            "سحبة ضبط لاسلكي — العودة للإذاعة بين الفقرات",
            2_200, listOf("انتقال", "إذاعة", "ضبط", "لاسلكي")),
        Sound("reverse_hit", "reverse_hit.mp3", "تجمّع وضربة", SoundGroups.TRANSITIONS,
            "تجمّع صاعد ينتهي بضربة عميقة — التحولات الكبيرة",
            1_500, listOf("انتقال", "ضربة", "تحول", "قوي")),

        // ================= طبيعة وبيئة (8) =================
        Sound("light_rain", "light_rain.mp3", "مطر خفيف", SoundGroups.NATURE,
            "مطر خفيف دائم — خلفية طبيعية للفقرات الهادئة",
            10_000, listOf("طبيعة", "مطر", "خلفية", "هدوء")),
        Sound("gentle_wind", "gentle_wind.mp3", "ريح معتدلة", SoundGroups.NATURE,
            "ريح معتدلة بهبّات لطيفة — أجواء خارجية حية",
            10_000, listOf("طبيعة", "ريح", "خلفية", "جو")),
        Sound("birds_chirp", "birds_chirp.mp3", "زقزنة طيور", SoundGroups.NATURE,
            "زقزنة طيور صباحية متنوعة — بدايات الفجر والحياة",
            10_000, listOf("طبيعة", "طيور", "زقزنة", "صباح")),
        Sound("stream_water", "stream_water.mp3", "جدول ماء", SoundGroups.NATURE,
            "جدول ماء يجري بفقاعات — خلفية طبيعية منعشة",
            10_000, listOf("طبيعة", "ماء", "جدول", "انتعاش")),
        Sound("ocean_waves", "ocean_waves.mp3", "أمواج البحر", SoundGroups.NATURE,
            "أمواج بحر متدحرجة — سكون واتساع للحكايات",
            12_000, listOf("طبيعة", "بحر", "أمواج", "سكون")),
        Sound("distant_thunder", "distant_thunder.mp3", "رعد بعيد", SoundGroups.NATURE,
            "رعد بعيد مجدول — لحظات الدراما الطبيعية",
            6_000, listOf("طبيعة", "رعد", "عاصفة", "دراما")),
        Sound("night_crickets", "night_crickets.mp3", "صراصير الليل", SoundGroups.NATURE,
            "همس الليل بحشرات صغيرة — فقرات الليل والسكون",
            10_000, listOf("طبيعة", "ليل", "حشرات", "سكون")),
        Sound("dawn_forest", "dawn_forest.mp3", "غابة الفجر", SoundGroups.NATURE,
            "غابة فجرًا: طيور وريح خفيفة — استيقاظ الطبيعة",
            10_000, listOf("طبيعة", "غابة", "فجر", "طيور")),

        // ================= مؤثرات سينمائية (6) =================
        Sound("epic_braam", "epic_braam.mp3", "ضربة مهيبة", SoundGroups.CINEMA,
            "ضربة سينمائية مهيبة بعَمق باص — الإعلانات الكبيرة",
            3_500, listOf("سينمائي", "ضربة", "مهيب", "إعلان")),
        Sound("cinema_riser", "cinema_riser.mp3", "تشويق سينمائي", SoundGroups.CINEMA,
            "تشويق متصاعد ينتهي بضربة — الدخول الدرامي الكبير",
            3_000, listOf("سينمائي", "تشويق", "صعود", "دراما")),
        Sound("sub_drop", "sub_drop.mp3", "هبوط عميق", SoundGroups.CINEMA,
            "هبوط باص عميق يخلق فراغًا فوريًا — لحظة الصدمة",
            3_000, listOf("سينمائي", "هبوط", "عميق", "صدمة")),
        Sound("magic_shimmer", "magic_shimmer.mp3", "تلألؤ خيالي", SoundGroups.CINEMA,
            "تلألؤ جرسية عالية متفرقة — السحر والحلم",
            3_500, listOf("سينمائي", "تلألؤ", "سحر", "حلم")),
        Sound("heartbeat", "heartbeat.mp3", "نبض درامي", SoundGroups.CINEMA,
            "نبض قلب درامي متكرر — التوتر واللحظة الحاسمة",
            4_000, listOf("سينمائي", "نبض", "توتر", "قلب")),
        Sound("tension_drone", "tension_drone.mp3", "توتر مشدود", SoundGroups.CINEMA,
            "درين توتر متشابك يرتفع — اللحظات المعلقة",
            5_000, listOf("سينمائي", "توتر", "درين", "تعليق"))
    )

    /** مسار المورد داخل الجرة — المجلد مشتق من المجموعة. */
    fun resourcePath(s: Sound): String =
        "sounds/${SoundGroups.dirOf[s.group]}/${s.fileName}"

    /**
     * استخراج الأصوات إلى مجلد بيانات المستخدم (idempotent): الموجود سليم يُترك،
     * والناقص يُكتب في ملف مؤقت ثم يُنقل ذريًّا فلا يبقى نصف ملف إن قُطع التشغيل.
     * يعيد خريطة الملفات بمعرفاتها؛ من تعذّر استخراجه لا يدخل الخريطة فتظهر
     * بطاقته «مفقود» في الواجهة بدل انهيار التهيئة.
     */
    fun ensureExtracted(dir: File): Map<String, File> {
        dir.mkdirs()
        val loader = BundledSounds::class.java.classLoader
        val out = HashMap<String, File>()
        for (s in ALL) {
            val f = File(dir, s.fileName)
            if (!f.isFile || f.length() == 0L) {
                try {
                    loader.getResourceAsStream(resourcePath(s))?.use { ins ->
                        val tmp = File(dir, s.fileName + ".tmp")
                        BufferedOutputStream(FileOutputStream(tmp)).use { outs -> ins.copyTo(outs) }
                        if (!tmp.renameTo(f)) {
                            tmp.copyTo(f, overwrite = true)
                            tmp.delete()
                        }
                    }
                } catch (_: Exception) {
                    try { File(dir, s.fileName + ".tmp").delete() } catch (_: Exception) { }
                }
            }
            if (f.isFile && f.length() > 0L) out[s.id] = f
        }
        return out
    }
}
