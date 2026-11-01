package com.saidjte.schoolradio.desktop.data

import java.io.File

/**
 * أدوار المستخدمين في استوديو الإذاعة المدرسي — نفس مصفوفة الصلاحيات الموثقة
 * في نسخة الهاتف (قرار تصميمي موثق):
 *  - طالب (STUDENT) : بث مباشر، تسجيل، تشغيل، تحضير الفقرات (سكريبت/مكتبة/قوائم).
 *  - معلم (TEACHER) : كل ما سبق + إدارة الجدولة + حذف/تسمية التسجيلات (رقابة المحتوى).
 *  - مشرف (ADMIN)   : كل ما سبق + إدارة المستخدمين أنفسهم + النسخ الاحتياطي.
 * لا كلمات مرور عمدًا — الجهاز جهاز الاستوديو المحلي، والدور للتنظيم لا للحماية.
 */
enum class Role(val labelAr: String) {
    ADMIN("مشرف"),
    TEACHER("معلم"),
    STUDENT("طالب");

    companion object {
        /** تحويل آمن لاسم الدور المخزن نصيًا — أي قيمة غير معروفة تُعامل كطالب (الأقل صلاحية). */
        fun from(name: String?): Role =
            entries.firstOrNull { it.name == name } ?: STUDENT

        /** الصلاحية الإدارية على المحتوى (جدولة/تسجيلات) = معلم فأعلى. */
        fun canManage(role: Role): Boolean = role == ADMIN || role == TEACHER

        /** إدارة المستخدمين أنفسهم والنسخ الاحتياطي = المشرف فقط. */
        fun canAdminister(role: Role): Boolean = role == ADMIN
    }
}

/** مستخدم التطبيق: طالب/معلم/مشرف — الاسم فريد حتى لا يلتبس التوقيع على التسجيلات. */
data class User(
    val id: Long,
    val name: String? = null,
    val role: Role? = null
)

data class UsersDoc(
    val users: List<User> = emptyList(),
    val currentUserId: Long? = null
)

/**
 * مخزن المستخدمين: تحميل/حفظ users.json ذريًا مع تعقيم الأدوار غير المعروفة
 * (قيمة أجنبية → طالب) وتنقية الأسماء الفارغة — كل كائن يخرج من هنا سليم.
 */
class UsersStore(private val file: File) {

    private var nextId: Long = 1L
    private var doc: UsersDoc = load()

    private fun load(): UsersDoc {
        val d = Json.load(file, UsersDoc())
        val safe = d.copy(
            users = d.users
                .mapNotNull { u ->
                    val name = u.name?.trim().orEmpty()
                    if (u.id <= 0L || name.isEmpty()) null
                    else u.copy(name = name, role = Role.from(u.role?.name))
                }
                .distinctBy { it.name },
            currentUserId = d.currentUserId?.takeIf { id -> id > 0L }
        )
        nextId = (safe.users.maxOfOrNull { it.id } ?: 0L) + 1L
        return safe
    }

    fun users(): List<User> = doc.users

    /** المستخدم الحالي: المحدد إن وُجد وإلا الأول — وnull لجهاز بلا مستخدمين بعد. */
    fun currentUser(): User? =
        doc.users.firstOrNull { it.id == doc.currentUserId } ?: doc.users.firstOrNull()

    /** إضافة مستخدم باسم فريد — الاسم المكرر أو الفارغ يُرفض (يعيد null). */
    fun add(name: String, role: Role): User? {
        val clean = name.trim()
        if (clean.isEmpty() || doc.users.any { it.name == clean }) return null
        val u = User(nextId++, clean, role)
        doc = doc.copy(
            users = doc.users + u,
            currentUserId = doc.currentUserId ?: u.id
        )
        persist()
        return u
    }

    fun update(id: Long, name: String, role: Role): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        if (doc.users.any { it.id != id && it.name == clean }) return false
        val i = doc.users.indexOfFirst { it.id == id }
        if (i < 0) return false
        doc = doc.copy(
            users = doc.users.mapIndexed { k, u ->
                if (k == i) u.copy(name = clean, role = role) else u
            }
        )
        persist()
        return true
    }

    fun delete(id: Long): Boolean {
        if (doc.users.none { it.id == id }) return false
        doc = doc.copy(users = doc.users.filter { it.id != id })
        if (doc.currentUserId == id) doc = doc.copy(currentUserId = doc.users.firstOrNull()?.id)
        persist()
        return true
    }

    fun setCurrent(id: Long): Boolean {
        if (doc.users.none { it.id == id }) return false
        doc = doc.copy(currentUserId = id)
        persist()
        return true
    }

    private fun persist() {
        Json.save(file, doc)
    }
}
