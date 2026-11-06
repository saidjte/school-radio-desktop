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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
