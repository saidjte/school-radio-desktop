package com.saidjte.schoolradio.desktop.data

/**
 * مساحة عمل قابلة للتخصيص (v0.39.0 «مساحة عمل قابلة للتخصيص») — تفضيلات شكل
 * بيئة العمل المحفوظة في settings.json فتعود بين الجلسات كما ضبطها المذيع:
 * حجم الخط العام، عرض القائمة الجانبية، إظهار/إخفاء لوحات الاستوديو المدمج،
 * وتذكّر آخر صفحة عند الإقلاع. الأرقام تُحبَس داخل حدود الواجهة فملف معدَّل
 * يدويًا لا يكسر التخطيط — نفس عهد طبقة البيانات في كل الإصدارات.
 */
data class WorkspaceJson(
    /** مقياس الخط العام (مضاعف على كثافة النظام) — null = الافتراضي 1.0. */
    val fontScale: Double? = null,
    /** عرض القائمة الجانبية بالـdp — null = الافتراضي 232. */
    val sidebarWidth: Double? = null,
    /** لوحات الاستوديو المدمج — null = ظاهرة (السلوك التاريخي). */
    val showRecordings: Boolean? = null,
    val showSchedule: Boolean? = null,
    val showScript: Boolean? = null,
    val showBroadcast: Boolean? = null,
    /** تذكّر آخر صفحة عند الإقلاع — null = لا (يبقى الاستوديو المدمج). */
    val rememberLastTab: Boolean? = null,
    /** آخر صفحة كانت مفتوحة (اسم enum) — تُعقَّم ضد الأسماء المعروفة قبل الاستخدام. */
    val lastTab: String? = null
)

/**
 * مساحة العمل المحسومة للواجهة — كل الحقول غير قابلة للـnull بعد الحسم:
 * الواجهة تقرأ قيمًا نهائية بلا فروع غائبة، والتخزين يبقى بالحقول الخيارية.
 */
data class WorkspaceState(
    val fontScale: Double,
    val sidebarWidth: Double,
    val showRecordings: Boolean,
    val showSchedule: Boolean,
    val showScript: Boolean,
    val showBroadcast: Boolean,
    val rememberLastTab: Boolean,
    val lastTab: String?
)

/** الحدود والافتراضيات — مرجع وحيد للواجهة والمخزن والاختبارات. */
object WorkspaceLogic {

    /** خيارات مقياس الخط المعروضة — أربعة أحجام من مريح القراءة عن بُعد. */
    val SCALE_OPTIONS: List<Pair<String, Double>> = listOf(
        "صغير" to 0.85,
        "افتراضي" to 1.0,
        "كبير" to 1.15,
        "أكبر" to 1.3
    )

    const val SIDEBAR_MIN_DP = 180.0
    const val SIDEBAR_MAX_DP = 360.0
    const val SIDEBAR_DEFAULT_DP = 232.0

    const val SCALE_MIN = 0.8
    const val SCALE_MAX = 1.5
    const val SCALE_DEFAULT = 1.0

    /** حبس المقياس داخل الحدود — القيمة الغريبة تعود للافتراضي. */
    fun clampScale(v: Double?): Double =
        if (v == null || v.isNaN()) SCALE_DEFAULT else v.coerceIn(SCALE_MIN, SCALE_MAX)

    /** حبس عرض القائمة الجانبية داخل حدود قابلية الاستخدام. */
    fun clampSidebarWidth(v: Double?): Double =
        if (v == null || v.isNaN()) SIDEBAR_DEFAULT_DP
        else v.coerceIn(SIDEBAR_MIN_DP, SIDEBAR_MAX_DP)

    /** علم بولي مع افتراضي صريح — null يُقرأ كقيمته الافتراضية. */
    fun flag(v: Boolean?, default: Boolean = true): Boolean = v ?: default

    /**
     * مساحة العمل المحسومة: كل حقل داخل حده ومعافى من الفساد —
     * آخر صفحة تُقبَل فقط إن كانت اسمًا معروفًا من أسماء الصفحات الحالية.
     */
    fun resolve(saved: WorkspaceJson?, validTabNames: Set<String>): WorkspaceJson {
        val w = saved ?: WorkspaceJson()
        return WorkspaceJson(
            fontScale = clampScale(w.fontScale),
            sidebarWidth = clampSidebarWidth(w.sidebarWidth),
            showRecordings = flag(w.showRecordings),
            showSchedule = flag(w.showSchedule),
            showScript = flag(w.showScript),
            showBroadcast = flag(w.showBroadcast),
            rememberLastTab = w.rememberLastTab == true,
            lastTab = w.lastTab?.trim()?.takeIf { it.isNotEmpty() && it in validTabNames }
        )
    }

    /** الحسم النهائي للواجهة — من JSON الخام إلى حالة غير قابلة للـnull. */
    fun resolveState(saved: WorkspaceJson?, validTabNames: Set<String>): WorkspaceState {
        val r = resolve(saved, validTabNames)
        return WorkspaceState(
            fontScale = r.fontScale ?: SCALE_DEFAULT,
            sidebarWidth = r.sidebarWidth ?: SIDEBAR_DEFAULT_DP,
            showRecordings = r.showRecordings == true,
            showSchedule = r.showSchedule == true,
            showScript = r.showScript == true,
            showBroadcast = r.showBroadcast == true,
            rememberLastTab = r.rememberLastTab == true,
            lastTab = r.lastTab
        )
    }
}
