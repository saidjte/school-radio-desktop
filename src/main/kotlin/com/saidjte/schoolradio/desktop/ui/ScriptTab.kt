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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import com.saidjte.schoolradio.desktop.data.ScriptPdfExporter
import com.saidjte.schoolradio.desktop.data.ScriptSection
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.ScriptTemplate
import com.saidjte.schoolradio.desktop.data.ScriptTemplateCatalog
import java.awt.Desktop
import kotlin.concurrent.thread

/**
 * سكريبت المذيع: تحرير فقرات النص (إضافة/تحرير/حذف/ترتيب) + لوحة قراءة
 * كبيرة الخط تصلح للقراءة على الهواء من شاشة الحاسوب.
 */
@Composable
fun ScriptTab(vm: AppViewModel) {
    val script by vm.script.collectAsState()
    val selectedId by vm.selectedSectionId.collectAsState()
    val fullReading by vm.fullScriptReading.collectAsState()
    // تراجع/إعادة السكريبت (v0.39.0) — أزرار الشريط وحارسا Ctrl+Z/Ctrl+Y
    val scriptCanUndo by vm.scriptCanUndo.collectAsState()
    val scriptCanRedo by vm.scriptCanRedo.collectAsState()

    var editorSection by remember { mutableStateOf<ScriptSection?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ScriptSection?>(null) }
    var fontSp by remember { mutableStateOf(30f) }
    var showOverlay by remember { mutableStateOf(false) }
    val readScroll = rememberScrollState()

    // قوالب السكريبت وتصدير PDF (v0.30.0)
    var showTemplates by remember { mutableStateOf(false) }
    var replaceTarget by remember { mutableStateOf<ScriptTemplate?>(null) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportDone by remember { mutableStateOf<ScriptPdfExporter.Result?>(null) }
    var exportError by remember { mutableStateOf<String?>(null) }

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
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                // عند حجم خط كبير تبقى كل الأزرار في متناول سحب أفقي بدل انحشارها
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                Text(
                    "سكريبت المذيع",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    // في صف يُسحب أفقيًا لا وزن للعناوين — العرض الجوهري ثم تمرّ للأزرار
                    maxLines = 1,
                    softWrap = false
                )
                PrimaryAction(text = "فقرة جديدة", onClick = { showCreate = true })
                // تراجع/إعادة السكريبت (v0.39.0): الإضافة والتعديل والحذف والترتيب
                // واستبدال القالب كلها ترتد — الفقرات تعود بمعرفاتها كما كانت
                SecondaryAction(text = "تراجع", onClick = { vm.undoScript() }, enabled = scriptCanUndo)
                SecondaryAction(text = "إعادة", onClick = { vm.redoScript() }, enabled = scriptCanRedo)
                // قوالب السكريبت (v0.30.0): هياكل برامج جاهزة تُولّد فقراتها بضغطة
                SecondaryAction(text = "قوالب جاهزة", onClick = { showTemplates = true })
                // تصدير PDF (v0.30.0): وثيقة A4 عربية جاهزة للطباعة والاعتماد الإداري
                SecondaryAction(
                    text = "تصدير PDF",
                    enabled = script.isNotEmpty() && !exportBusy,
                    onClick = {
                        val f = FilePickers.pickPdfSave(ScriptPdfExporter.suggestedFileName()) ?: return@SecondaryAction
                        exportBusy = true
                        thread(name = "script-pdf-export") {
                            try {
                                exportDone = vm.exportScriptPdf(f)
                            } catch (e: Exception) {
                                exportError = e.message ?: "فشل إنشاء ملف PDF"
                            } finally {
                                exportBusy = false
                            }
                        }
                    }
                )
            }

            if (script.isEmpty()) {
                Text(
                    "المجموع: لا فقرات بعد — أضف أول فقرة، أو ابدأ من «قوالب جاهزة» لتوليد سكريبت حلقة كامل بضغطة.",
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
                // شاشة القراءة على الشاشة الثانية (v0.35.0) — على طريقة «على الهواء»
                SecondaryAction(
                    text = "على الشاشة الثانية",
                    onClick = { vm.readingDisplayOpen.value = true }
                )
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
                Text(
                    "قراءة السكريبت كاملًا",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
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

    // ============ حوار قوالب السكريبت (v0.30.0) ============
    if (showTemplates) {
        AlertDialog(
            onDismissRequest = { showTemplates = false },
            title = { Text("قوالب السكريبت الجاهزة") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ScriptTemplateCatalog.all, key = { it.id }) { t ->
                        TemplateCard(
                            template = t,
                            onAppend = {
                                vm.applyScriptTemplate(t, replaceExisting = false)
                                showTemplates = false
                            },
                            onReplace = {
                                showTemplates = false
                                if (script.isEmpty()) {
                                    vm.applyScriptTemplate(t, replaceExisting = true)
                                } else {
                                    replaceTarget = t
                                }
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplates = false }) { Text("إغلاق") }
            }
        )
    }

    // تأكيد الاستبدال: الفقرات الحالية كلها ستُحذف ولا تراجع بعدها
    if (replaceTarget != null) {
        val t = replaceTarget!!
        AlertDialog(
            onDismissRequest = { replaceTarget = null },
            title = { Text("استبدال السكريبت الحالي؟") },
            text = {
                Text(
                    "سيتم حذف الفقرات الحالية (${script.size}) وتوليد فقرات قالب «${t.name}» " +
                            "(${t.sectionCount} فقرة) مكانها. هذا الإجراء لا تراجع بعده — " +
                            "وإن أردت الاحتفاظ بالحالي فاختر «إلحاق» بدل الاستبدال."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.applyScriptTemplate(t, replaceExisting = true)
                    replaceTarget = null
                }) { Text("استبدال", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { replaceTarget = null }) { Text("إلغاء") }
            }
        )
    }

    // ============ حوارات تصدير PDF (v0.30.0) ============
    if (exportBusy) {
        AlertDialog(
            onDismissRequest = { /* التوليد سريع ولا يُجهض — يكتمل ويُظهر النتيجة */ },
            title = { Text("جارٍ إنشاء ملف PDF") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text("تُنسّق الصفحات العربية الآن...")
                }
            },
            confirmButton = { }
        )
    }
    exportDone?.let { r ->
        AlertDialog(
            onDismissRequest = { exportDone = null },
            title = { Text("تم إنشاء ملف PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${r.pages} صفحة · ${r.words} كلمة · ${formatMs(r.estimatedSeconds * 1000)} زمن قراءة")
                    Text(
                        r.file.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { Desktop.getDesktop().open(r.file) }
                    exportDone = null
                }) { Text("فتح الملف") }
            },
            dismissButton = {
                TextButton(onClick = { exportDone = null }) { Text("إغلاق") }
            }
        )
    }
    exportError?.let { msg ->
        AlertDialog(
            onDismissRequest = { exportError = null },
            title = { Text("فشل تصدير PDF") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { exportError = null }) { Text("حسنًا") }
            }
        )
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

/**
 * بطاقة قالب سكريبت (v0.30.0): اسم ووصف وعدد فقرات وزمن تقديري،
 * وفعلان: إلحاق بالنهاية (آمن دائمًا) أو استبدال الكل (مع تأكيد).
 */
@Composable
private fun TemplateCard(
    template: ScriptTemplate,
    onAppend: () -> Unit,
    onReplace: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            template.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            template.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${template.sectionCount} فقرات · زمن تقديري " +
                    formatMs(template.estimatedSeconds() * 1000) +
                    " · ${template.totalWords()} كلمة",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onReplace) {
                Text("استبدال الكل", style = MaterialTheme.typography.labelLarge)
            }
            TextButton(onClick = onAppend) {
                Text(
                    "إلحاق بالنهاية",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
