package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.data.Role
import com.saidjte.schoolradio.desktop.data.User

/**
 * الإعدادات والمستخدمون — البوابة الموحدة لثلاث مهام إدارية:
 * اسم المدرسة (معلم فأعلى)، المستخدمون والأدوار (مشرف)، النسخ الاحتياطي JSON (مشرف).
 * تفتح من أسفل القائمة الجانبية في كل الصفحات.
 */
@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val me by vm.currentUser.collectAsState()
    val users by vm.users.collectAsState()
    val schoolName by vm.schoolName.collectAsState()

    val canManage = me?.let { Role.canManage(it.role ?: Role.STUDENT) } ?: false
    val canAdminister = me?.let { Role.canAdminister(it.role ?: Role.STUDENT) } ?: false

    var schoolText by remember(schoolName) { mutableStateOf(schoolName ?: "") }
    var newUserName by remember { mutableStateOf("") }
    var newUserRole by remember { mutableStateOf(Role.STUDENT) }
    var roleMenuOpen by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الإعدادات والمستخدمون") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ============ اسم المدرسة ============
                Text(
                    "اسم المدرسة",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = schoolText,
                        onValueChange = { schoolText = it },
                        placeholder = { Text("مثال: مدرسة النور الثانوية") },
                        singleLine = true,
                        enabled = canManage,
                        modifier = Modifier.weight(1f).observesTextEditing()
                    )
                    TextButton(
                        onClick = { vm.setSchoolName(schoolText) },
                        enabled = canManage
                    ) { Text("حفظ") }
                }
                if (!canManage) {
                    Text(
                        "تعديل اسم المدرسة لمعلم فأعلى",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // ============ المستخدمون والأدوار ============
                Text(
                    "المستخدمون والأدوار",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "اضغط على مستخدم لتبديل الهوية الحالية — الدور تنظيمي لا حماية (كالنسخة الهاتفية).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (users.isEmpty()) {
                    Text(
                        "لا مستخدمين بعد — أنشئ الحساب الأول (يُنصح أن يكون مشرفًا).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(users, key = { it.id }) { u ->
                            val isMe = me?.id == u.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isMe) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { vm.switchUser(u.id) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        u.name ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        (u.role ?: Role.STUDENT).labelAr +
                                                if (isMe) " · الحالي" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (canAdminister && users.size > 1) {
                                    Text(
                                        "حذف",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { vm.deleteUser(u.id) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        placeholder = { Text("اسم المستخدم الجديد") },
                        singleLine = true,
                        enabled = canAdminister || users.isEmpty(),
                        modifier = Modifier.weight(1f).observesTextEditing()
                    )
                    Box {
                        TextButton(
                            onClick = { roleMenuOpen = true },
                            enabled = canAdminister || users.isEmpty()
                        ) { Text(newUserRole.labelAr) }
                        DropdownMenu(expanded = roleMenuOpen, onDismissRequest = { roleMenuOpen = false }) {
                            Role.entries.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r.labelAr) },
                                    onClick = {
                                        newUserRole = r
                                        roleMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            if (vm.addUser(newUserName, newUserRole)) newUserName = ""
                        },
                        enabled = (canAdminister || users.isEmpty()) && newUserName.isNotBlank()
                    ) { Text("إضافة") }
                }
                if (!canAdminister && users.isNotEmpty()) {
                    Text(
                        "إدارة المستخدمين للمشرف فقط",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider()

                // ============ النسخ الاحتياطي ============
                Text(
                    "النسخ الاحتياطي JSON",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "ملف واحد يجمع: الإعدادات والمستخدمين والسكريبت والجدولة والأحداث الأسبوعية وسجل التنفيذ والمكتبة وقائمة التشغيل — مع فحص سلامة ونسخ أمان قبل الاستبدال.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val f = FilePickers.pickJsonSave("schoolradio-backup.json")
                            if (f != null) backupMessage = vm.exportBackup(f).message
                        },
                        enabled = canAdminister
                    ) { Text("تصدير") }
                    TextButton(
                        onClick = {
                            val f = FilePickers.pickJsonOpen()
                            if (f != null) backupMessage = vm.importBackup(f).message
                        },
                        enabled = canAdminister
                    ) { Text("استيراد واستبدال") }
                }
                backupMessage?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider()

                // ============ مساحة العمل القابلة للتخصيص (v0.39.0) ============
                WorkspaceSection(vm = vm)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

/**
 * قسم «مساحة العمل» في الإعدادات (v0.39.0 «مساحة عمل قابلة للتخصيص») —
 * حجم الخط العام، عرض القائمة الجانبية، إظهار/إخفاء لوحات الاستوديو،
 * وتذكّر آخر صفحة عند الإقلاع. كل تعديل يُحفظ فورًا في settings.json.
 */
@Composable
private fun WorkspaceSection(vm: AppViewModel) {
    val ws by vm.workspace.collectAsState()

    Text(
        "مساحة العمل",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "تخصيص بيئة العمل يُحفظ فورًا ويعود بين الجلسات — للجميع بلا صلاحيات.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // حجم الخط العام: أربعة أحجام
    Text(
        "حجم الخط العام",
        style = MaterialTheme.typography.labelLarge
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        com.saidjte.schoolradio.desktop.data.WorkspaceLogic.SCALE_OPTIONS.forEach { (label, value) ->
            val selected = kotlin.math.abs(ws.fontScale - value) < 0.001
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { vm.setFontScale(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }

    // عرض القائمة الجانبية
    Text(
        "عرض القائمة الجانبية: ${ws.sidebarWidth.toInt()} dp",
        style = MaterialTheme.typography.labelLarge
    )
    androidx.compose.material3.Slider(
        value = ws.sidebarWidth.toFloat(),
        onValueChange = { vm.setSidebarWidth(it.toDouble()) },
        valueRange = com.saidjte.schoolradio.desktop.data.WorkspaceLogic.SIDEBAR_MIN_DP.toFloat()
            ..com.saidjte.schoolradio.desktop.data.WorkspaceLogic.SIDEBAR_MAX_DP.toFloat(),
        steps = 11
    )

    // لوحات الاستوديو المدمج
    Text(
        "لوحات الاستوديو المدمج",
        style = MaterialTheme.typography.labelLarge
    )
    val panels = listOf(
        "recordings" to "لوحة التسجيلات",
        "schedule" to "لوحة الجدولة",
        "script" to "شاشة السكريبت",
        "broadcast" to "حيّز البث والمزج"
    )
    panels.forEach { (key, label) ->
        val visible = when (key) {
            "recordings" -> ws.showRecordings
            "schedule" -> ws.showSchedule
            "script" -> ws.showScript
            else -> ws.showBroadcast
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(
                checked = visible,
                onCheckedChange = { vm.setStudioPanel(key, it) }
            )
        }
    }

    // تذكّر آخر صفحة
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("تذكّر آخر صفحة عند الإقلاع", style = MaterialTheme.typography.bodyMedium)
            Text(
                "يفتح التطبيق على الصفحة التي كانت مفتوحة عند الإغلاق",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Switch(
            checked = ws.rememberLastTab,
            onCheckedChange = { vm.setRememberLastTab(it) }
        )
    }
}
