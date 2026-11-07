package com.saidjte.schoolradio.desktop.data

/**
 * قوالب المونتاج الجاهزة (v0.35.0 — البند 17 «علامات مسمّاة + قوالب جاهزة»):
 * هياكل فصول إذاعة مدرسية حقيقية — تطبيق القالب يولّد العلامات المسمّاة على
 * الخط الزمني ويعرض إعدادات الفاصل/الكروس فيد/الخفض التلقائي الموصى بها،
 * فيجد المذيع خطّه الزمني مهيّأً ويبقى ملء المقاطع إليه.
 * كل الأزمنة بالميلي ثانية، والتطبيق يمرّ من حبوس [MontageLogic] فلا قيمة عبثية.
 */
data class MontageTemplate(
    val id: String,
    val name: String,
    val description: String,
    /** الفاصل الموصى به بين مقاطع التعليق. */
    val gapMs: Long,
    /** الكروس فيد الموصى به بين المتجاورة (0 = فاصل صمت). */
    val crossfadeMs: Long,
    /** عمق الخفض التلقائي الموصى به للسرير الموسيقي. */
    val duckDb: Int,
    /** العلامات المسمّاة: (الاسم، الموضع بالميلي ثانية من بداية المونتاج). */
    val markers: List<Pair<String, Long>>
)

object MontageTemplateCatalog {

    val all: List<MontageTemplate> = listOf(
        MontageTemplate(
            id = "full-break",
            name = "الفاصل الإذاعي الكامل",
            description = "فصل حصة كامل: افتتاحية ثم ترحيب ثم نشرة ثم فاصل موسيقي طويل ثم ختام — نحو أربع دقائق ونصف.",
            gapMs = 400L, crossfadeMs = 0L, duckDb = -12,
            markers = listOf(
                "الافتتاحية" to 0L,
                "ترحيب المذيع" to 15_000L,
                "النشرة المدرسية" to 60_000L,
                "الفاصل الموسيقي" to 180_000L,
                "الختام" to 270_000L
            )
        ),
        MontageTemplate(
            id = "morning-anthem",
            name = "النشيد الصباحي",
            description = "تهيئة قصيرة ثم النشيد كاملًا بكروس فيد ناعم وسرير موسيقي مخفوض عميقًا تحت التعليق.",
            gapMs = 0L, crossfadeMs = 1_500L, duckDb = -18,
            markers = listOf(
                "التهيئة" to 0L,
                "النشيد" to 8_000L,
                "الختام" to 180_000L
            )
        ),
        MontageTemplate(
            id = "admin-announcement",
            name = "إعلان إداري",
            description = "جرس الفسحة ثم الإعلان ثم تكراره للصفوف الأخيرة — الأقصر والأوضح بين القوالب.",
            gapMs = 300L, crossfadeMs = 0L, duckDb = -10,
            markers = listOf(
                "جرس الفسحة" to 0L,
                "الإعلان" to 4_000L,
                "تكرار الإعلان" to 20_000L,
                "الختام" to 35_000L
            )
        ),
        MontageTemplate(
            id = "guest-interview",
            name = "مقابلة مع ضيف",
            description = "تقديم الضيف ثم الأسئلة ثم كلمته ثم الختام — خفض متوسط يُبقي الموسيقى حاضرة بأدب.",
            gapMs = 250L, crossfadeMs = 0L, duckDb = -15,
            markers = listOf(
                "تقديم الضيف" to 0L,
                "الأسئلة" to 10_000L,
                "كلمة الضيف" to 40_000L,
                "الختام" to 180_000L
            )
        )
    )

    fun byId(id: String): MontageTemplate? = all.firstOrNull { it.id == id }
}

/**
 * تطبيق قالب على مشروع — نقي قابل للاختبار: يعرض العلامات المولّدة بمعرّفات
 * فريدة مرتبة، ويعرض إعدادات الفاصل والكروس فيد والخفض الموصى بها، ولا يمسّ
 * مقاطع التعليق ولا مسار الموسيقى ولا الرأس — ما بناه المذيع يبقى.
 */
object MontageTemplatesLogic {

    fun apply(project: MontageProject, t: MontageTemplate): MontageProject = project.copy(
        gapMs = MontageLogic.clampGap(t.gapMs),
        crossfadeMs = MontageLogic.clampCrossfade(t.crossfadeMs),
        duckDb = MontageLogic.clampDuckDb(t.duckDb),
        duckingEnabled = true,
        markers = MontageLogic.sanitizeMarkers(
            t.markers.mapIndexed { i, (name, offset) ->
                MontageMarker(
                    id = MontageLogic.newMarkerId() + "-$i",
                    name = name,
                    tMs = offset.coerceAtLeast(0L)
                )
            }
        )
    )
}
