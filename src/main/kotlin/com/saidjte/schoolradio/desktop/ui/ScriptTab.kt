package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats

/**
 * سكريبت المذيع: تحرير فقرات النص (إضافة/تحرير/حذف/ترتيب) + لوحة قراءة
 * كبيرة الخط تصلح للقراءة على الهواء من شاشة الحاسوب.
 */
@Composable
fun ScriptTab(vm: AppViewModel) {
    val script by vm.script.collectAsState()
    val selectedId by vm.selectedSectionId.collectAsState()
    val fullReading by vm.fullScriptReading.collectAsState()

    var editorSection by remember { mutableStateOf<ScriptSection?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ScriptSection?>(null) }
    var fontSp by remember { mutableStateOf(30f) }
    var showOverlay by remember { mutableStateOf(false) }
    val readScroll = rememberScrollState()

    // عودة القراءة لقمة النص لحظة اختيار فقرة جديدة أو تبديل وضع «كامل»
    LaunchedEffect(selectedId, fullReading) {
        readScroll.animateScrollTo(0)
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ============ قائمة الفقرات + التحرير ============
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "سكريبت المذيع",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                PrimaryAction(text = "فقرة جديدة", onClick = { showCreate = true })
            }

            if (script.isEmpty()) {
                Text(
                    "المجموع: لا فقرات بعد — أضف أول فقرة لتبدأ كتابة سكريبت الحلقة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "المجموع: ${script.size} فقرة · ${ScriptStats.totalWords(script)} كلمة · " +
                            "زمن قراءة تقديري ${formatMs(ScriptStats.totalSeconds(script) * 1000)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(script) { index, section ->
                    val isSelected = section.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable { vm.selectSection(section.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                section.title.ifEmpty { "بدون عنوان" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "${ScriptStats.wordCount(section.body)} كلمة · " +
                                        formatMs(ScriptStats.secondsFor(section.body) * 1000),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SecondaryAction(
                            text = "تحرير",
                            onClick = { editorSection = section }
                        )
                        SecondaryAction(
                            text = "أعلى",
                            enabled = index > 0,
                            onClick = { vm.moveSection(section.id, -1) }
                        )
                        SecondaryAction(
                            text = "أسفل",
                            enabled = index < script.size - 1,
                            onClick = { vm.moveSection(section.id, +1) }
                        )
                        SecondaryAction(
                            text = "حذف",
                            onClick = { deleteTarget = section }
                        )
                    }
                }
            }
        }

        // ============ لوحة القراءة (تلقيم بصري كبير الخط) ============
        Column(
            modifier = Modifier.width(420.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "لوحة القراءة",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(text = "شاشة القراءة", onClick = { showOverlay = true })
                SecondaryAction(text = "أ−", onClick = { fontSp = (fontSp - 4f).coerceAtLeast(16f) })
                SecondaryAction(text = "أ+", onClick = { fontSp = (fontSp + 4f).coerceAtMost(64f) })
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Switch(
                    checked = fullReading,
                    onCheckedChange = { vm.fullScriptReading.value = it }
                )
                Text("قراءة السكريبت كاملًا", style = MaterialTheme.typography.bodyMedium)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(readScroll)
                    .padding(18.dp)
            ) {
                if (fullReading) {
                    if (script.isEmpty()) {
                        Text(
                            "لا يوجد نص للقراءة بعد.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = fontSp.sp * 0.6f
                        )
                    }
                    script.forEachIndexed { i, section ->
                        // الفقرة المحددة مميّزة بخلفية فاتحة — المذيع يعرف موقعه
                        // في القراءة الكاملة دون البحث بعينيه
                        val isCurrent = section.id == selectedId
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else Color.Transparent
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                "${i + 1}. ${section.title.ifEmpty { "بدون عنوان" }}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                section.body.ifEmpty { "—" },
                                fontSize = fontSp.sp,
                                lineHeight = (fontSp * 1.5f).sp
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                } else {
                    val current = vm.selectedSection
                    if (current == null) {
                        Text(
                            "اختر فقرة من القائمة لتظهر هنا بخط كبير للقراءة.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            current.title.ifEmpty { "بدون عنوان" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            current.body.ifEmpty { "—" },
                            fontSize = fontSp.sp,
                            lineHeight = (fontSp * 1.5f).sp
                        )
                    }
                }
            }

            val readingNow = if (fullReading) null else vm.selectedSection
            Text(
                if (fullReading) {
                    "السكريبت كاملًا · ${ScriptStats.totalWords(script)} كلمة · " +
                            formatMs(ScriptStats.totalSeconds(script) * 1000)
                } else if (readingNow != null) {
                    "${ScriptStats.wordCount(readingNow.body)} كلمة · " +
                            formatMs(ScriptStats.secondsFor(readingNow.body) * 1000)
                } else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // شاشة القراءة التلقيمية بملء النافذة (v0.19.0)
    if (showOverlay) {
        ReadingOverlay(vm) { showOverlay = false }
    }

    // ============ حوارات التحرير والحذف ============
    if (showCreate) {
        SectionEditorDialog(
            title = "فقرة جديدة",
            initialTitle = "",
            initialBody = "",
            onDismiss = { showCreate = false },
            onConfirm = { t, b ->
                vm.addSection(t, b)
                showCreate = false
            }
        )
    }
    if (editorSection != null) {
        val target = editorSection!!
        SectionEditorDialog(
            title = "تحرير الفقرة",
            initialTitle = target.title,
            initialBody = target.body,
            onDismiss = { editorSection = null },
            onConfirm = { t, b ->
                vm.updateSection(target.id, t, b)
                editorSection = null
            }
        )
    }
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل تريد حذف فقرة «${deleteTarget?.title}» نهائيًا؟") },
            confirmButton = {
                TextButton(onClick = {
                    val t = deleteTarget
                    deleteTarget = null
                    if (t != null) vm.deleteSection(t.id)
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun SectionEditorDialog(
    title: String,
    initialTitle: String,
    initialBody: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, body: String) -> Unit
) {
    var t by remember { mutableStateOf(initialTitle) }
    var b by remember { mutableStateOf(initialBody) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    label = { Text("عنوان الفقرة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().observesTextEditing()
                )
                OutlinedTextField(
                    value = b,
                    onValueChange = { b = it },
                    label = { Text("نص الفقرة") },
                    minLines = 6,
                    maxLines = 14,
                    modifier = Modifier.fillMaxWidth().observesTextEditing()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(t, b) }) { Text("حفظ") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
