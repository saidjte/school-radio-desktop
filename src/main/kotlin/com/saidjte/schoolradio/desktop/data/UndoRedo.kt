package com.saidjte.schoolradio.desktop.data

/**
 * سجل تراجع/إعادة عام (v0.39.0 «التراجع والإعادة Undo / Redo») — نقي مستقل عن
 * أي مجال: يدفع لقطة قبل كل تغيير بشري فيعود إليها التراجع، والإعادة تعيد
 * لقطة التراجع عنها. صمم على صورة سجل المونتاج (v0.34.0) ليكون سلوك الاستوديو
 * كله موحّدًا: التراجع عن الإضافة يعيدها، وعن الحذف يستعيدها، وعن الترتيب يرجعه.
 *
 * الاختيار الهندسي: لقطات كاملة لا أوامر معكوسة — كل مجال (قائمة التشغيل،
 * السكريبت) يحفظ لقطة خفيفة (مسارات وفهرس / معرفات وعناوين) فلا يعيد أحد
 * تنفيذ فعل معكوس على حالة تغيّرت بعده، ولا فروع غامضة: تغيير جديد بعد تراجع
 * يقطع فرع الإعادة كما في كل محررات الاحتراف.
 */
class SnapshotHistory<T>(private val cap: Int = DEFAULT_CAP) {

    /** سقف السجل الافتراضي — مئة لقطة كما في سجل المونتاج. */
    companion object {
        const val DEFAULT_CAP = 100
    }

    /** لقطة قبل تغيير لم يُنفَّذ بعد — التراجع يستعيدها. */
    private val past = ArrayDeque<T>()

    /** لقطة أُلغي تغييرها بتراجع — الإعادة تعيد تنفيذها. */
    private val future = ArrayDeque<T>()

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    /**
     * دفع لقطة ما قبل التغيير: يقطع فرع الإعادة (سلوك المحررات القياسي)
     * ويقيّد الحجم بأقدم لقطة تُهجر — الفيضان لا يكبر الذاكرة بلا حد.
     */
    fun push(before: T) {
        future.clear()
        past.addLast(before)
        while (past.size > cap) past.removeFirst()
    }

    /**
     * تراجع: يعيد لقطة «ما قبل آخر تغيير» ويدفع الحالة الحالية إلى الإعادة.
     * @param current الحالة الفعلية الآن (تُدفع للإعادة كما هي — لا تفترض هنا)
     * @return لقطة الاستعادة أو null إن لم يوجد تراجع
     */
    fun undo(current: T): T? {
        val before = past.removeLastOrNull() ?: return null
        future.addLast(current)
        return before
    }

    /**
     * إعادة: تعيد آخر لقطة أُلغيت، وتدفع الحالة الحالية إلى الماضي فبقيا قابل
     * للتراجع مجددًا.
     */
    fun redo(current: T): T? {
        val next = future.removeLastOrNull() ?: return null
        past.addLast(current)
        return next
    }

    /** تصفير كامل — يُستدعى عند تغيّر سياق التاريخ كليًا (تبديل قائمة نشطة). */
    fun clear() {
        past.clear()
        future.clear()
    }
}
