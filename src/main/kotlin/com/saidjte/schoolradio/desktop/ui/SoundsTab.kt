package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.audio.BundledSounds
import com.saidjte.schoolradio.desktop.data.CustomSound
import com.saidjte.schoolradio.desktop.data.SoundGroups
import com.saidjte.schoolradio.desktop.data.SoundLicense
import com.saidjte.schoolradio.desktop.data.SoundLibraryMath
import java.io.File

/**
 * صفحة مكتبة الأصوات والموسيقى (v0.42.0) — بنود الطلب العشرة كاملة:
 * موسيقى خلفية ومؤثرات جاهزة وأصوات انتقالية وطبيعة وبيئة وسينمائية
 * (مدمجة داخل التطبيق بلا شبكة)، تصنيف حسب النوع برقائق، بحث في العنوان
 * والوصف والوسوم، معاينة قبل الإضافة، إضافة إلى المكتبة الرئيسية بتصنيف
 * يصل منها للقوائم والمونتاج والجدولة، استيراد مكتبة خاصة (ملفات أو مجلد)،
 * وحوار تراخيص يوضح الاستخدام التجاري صراحة.
 */

/** صف بطاقة موحد — يغلف المدمج والمستورد في قائمة واحدة. */
private data class SoundRow(
    val title: String,
    val description: String,
    val tags: List<String>,
    val group: String,
    val durationMs: Long,
    val file: File?,
    val isCustom: Boolean,
    val customId: Long? = null
)

@Composable
fun SoundsTab(vm: AppViewModel) {
    val query by vm.soundQuery.collectAsState()
    val groupFilter by vm.soundGroup.collectAsState()
    val bundledFiles by vm.bundledSoundFiles.collectAsState()
    val customSounds by vm.customSounds.collectAsState()
    val pending by vm.pendingAdd.collectAsState()
    val licenseOpen by vm.licenseOpen.collectAsState()
    val message by vm.lastLibraryMessage.collectAsState()
    val categories by vm.libraryCategories.collectAsState()
    val previewPlaying by vm.soundPreview.playingFile.collectAsState()
    val previewError by vm.soundPreview.lastError.collectAsState()

    val rows = remember(query, groupFilter, bundledFiles, customSounds) {
        buildList {
            if (groupFilter == null || groupFilter != SoundGroups.CUSTOM) {
                BundledSounds.ALL
                    .filter { groupFilter == null || it.group == groupFilter }
                    .forEach { s ->
                        add(
                            SoundRow(
                                title = s.title,
                                description = s.description,
                                tags = s.tags,
                                group = s.group,
                                durationMs = s.durationMs,
                                file = bundledFiles[s.id],
                                isCustom = false
                            )
                        )
                    }
            }
            if (groupFilter == null || groupFilter == SoundGroups.CUSTOM) {
                customSounds.forEach { c ->
                    add(
                        SoundRow(
                            title = c.title,
                            description = "ملف خاص مستورد — ${c.path.substringAfterLast('/')}",
                            tags = emptyList(),
                            group = SoundGroups.CUSTOM,
                            durationMs = 0L,
                            file = File(c.path).takeIf { it.isFile },
                            isCustom = true,
                            customId = c.id
                        )
                    )
                }
            }
        }.filter { SoundLibraryMath.matches(it.title, it.description, it.tags, query) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ============ الترويسة ============
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "مكتبة الأصوات والموسيقى",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${BundledSounds.ALL.size} صوتًا مدمجًا · ${SoundGroups.BUNDLED.size} مجموعات · بلا شبكة",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ============ أزرار الاستيراد والتراخيص ============
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SecondaryAction(
                text = "استيراد ملفات",
                onClick = {
                    val files = FilePickers.pickSoundLibraryFiles()
                    if (!files.isNullOrEmpty()) vm.importCustomLibrary(files)
                }
            )
            SecondaryAction(
                text = "استيراد مجلد",
                onClick = {
                    val dir = FilePickers.pickAudioFolder()
                    if (dir != null) {
                        val files = dir.walkTopDown()
                            .filter { it.isFile && it.extension.lowercase() in com.saidjte.schoolradio.desktop.audio.PLAYABLE_EXTENSIONS }
                            .toList()
                        if (files.isNotEmpty()) vm.importCustomLibrary(files)
                    }
                }
            )
            SecondaryAction(text = "التراخيص…", onClick = { vm.openLicenseDialog() })
            Spacer(Modifier.weight(1f))
            Text(
                "${rows.size} نتيجة",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ============ البحث ============
        OutlinedTextField(
            value = query,
            onValueChange = { vm.setSoundQuery(it) },
            placeholder = { Text("ابحث عن مؤثر أو موسيقى (عنوان، وصف، وسم)…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().observesTextEditing()
        )

        // ============ رقائق التصنيف حسب النوع ============
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = groupFilter == null,
                onClick = { vm.setSoundGroupFilter(null) },
                label = { Text("الكل (${BundledSounds.ALL.size + customSounds.size})") }
            )
            SoundGroups.BUNDLED.forEach { g ->
                FilterChip(
                    selected = groupFilter == g,
                    onClick = { vm.setSoundGroupFilter(if (groupFilter == g) null else g) },
                    label = {
                        Text("$g (${BundledSounds.ALL.count { it.group == g }})")
                    }
                )
            }
            FilterChip(
                selected = groupFilter == SoundGroups.CUSTOM,
                onClick = {
                    vm.setSoundGroupFilter(if (groupFilter == SoundGroups.CUSTOM) null else SoundGroups.CUSTOM)
                },
                label = { Text("${SoundGroups.CUSTOM} (${customSounds.size})") }
            )
        }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        previewError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // ============ القائمة ============
        if (rows.isEmpty()) {
            EmptyState(
                icon = AppIconKind.SOUNDS,
                title = if (groupFilter == SoundGroups.CUSTOM && customSounds.isEmpty()) "مكتبتك الخاصة فارغة"
                else "لا نتائج مطابقة",
                hint = if (groupFilter == SoundGroups.CUSTOM && customSounds.isEmpty())
                    "استورد ملفاتك الصوتية بـ«استيراد ملفات» أو «استيراد مجلد» — تُفهرس هنا وتُضاف إلى المكتبة بنقرة."
                else "جرّب وسمًا أقصر مثل «مطر» أو «جرس» أو امسح البحث — والتصنيفات فوق تختصر الطريق."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(rows) { _, row ->
                    val bundledId = BundledSounds.ALL
                        .firstOrNull { it.title == row.title && it.group == row.group }?.id
                    SoundCardRow(
                        row = row,
                        playing = row.file != null && previewPlaying == row.file.absolutePath,
                        onPreview = {
                            if (row.isCustom) row.customId?.let(vm::togglePreviewCustom)
                            else bundledId?.let(vm::togglePreviewBundled)
                        },
                        onAdd = {
                            if (row.isCustom) row.customId?.let(vm::openCustomAddDialog)
                            else bundledId?.let(vm::openBundledAddDialog)
                        },
                        onDelete = { row.customId?.let(vm::removeCustomSound) }
                    )
                }
            }
        }
    }

    // ============ حوار الإضافة إلى المكتبة ============
    pending?.let { p ->
        AddSoundDialog(
            title = p.title,
            group = p.group,
            categories = categories,
            onConfirm = { vm.confirmAddSound(it) },
            onDismiss = { vm.dismissAddDialog() }
        )
    }

    // ============ حوار التراخيص والاستخدام التجاري ============
    if (licenseOpen) {
        LicenseDialog(onDismiss = { vm.closeLicenseDialog() })
    }
}

@Composable
private fun SoundCardRow(
    row: SoundRow,
    playing: Boolean,
    onPreview: () -> Unit,
    onAdd: () -> Unit,
    onDelete: () -> Unit
) {
    val missing = row.file == null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (missing) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    row.group + if (row.durationMs > 0) " · ${SoundLibraryMath.durationText(row.durationMs)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                (row.description + if (missing) " · الملف مفقود من القرص" else "")
                        + if (row.tags.isNotEmpty()) " — الوسوم: ${row.tags.joinToString("، ")}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (missing) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (missing && row.isCustom) {
            SecondaryAction(text = "حذف من الفهرس", onClick = onDelete)
        } else {
            TextButton(
                onClick = onPreview,
                modifier = Modifier.defaultMinSize(minHeight = 36.dp)
            ) {
                Text(
                    if (playing) "■ إيقاف" else "▶ استماع",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (playing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )
            }
            SecondaryAction(text = "إضافة إلى المكتبة", onClick = onAdd)
        }
    }
}

/** حوار الإضافة: تصنيف المكتبة الرئيسية — المقترح حسب المجموعة مختار أولًا. */
@Composable
private fun AddSoundDialog(
    title: String,
    group: String,
    categories: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var chosen by remember {
        mutableStateOf(
            SoundLibraryMath.suggestedCategory(group)
                .takeIf { it in categories } ?: categories.firstOrNull() ?: "فقرات متنوعة"
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة «$title» إلى المكتبة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "اختر تصنيف المكتبة الرئيسية — الصوت يصل منها للقوائم والمونتاج والجدولة:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (chosen == c) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { chosen = c }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                c + if (c == SoundLibraryMath.suggestedCategory(group)) " · مقترح" else "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (chosen == c) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen) }) { Text("إضافة") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

/** حوار التراخيص — بنود توضيح الاستخدام التجاري بالكامل. */
@Composable
private fun LicenseDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("التراخيص والاستخدام التجاري") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoundLicense.commercialSummary().let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                // رأس جدول التراخيص
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("العنصر", Modifier.weight(1.4f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("المصدر", Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("الترخيص", Modifier.weight(1.2f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("تجاريًا", Modifier.weight(1.1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                SoundLicense.rows().forEach { r ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(r.item, Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
                        Text(r.source, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
                        Text(r.license, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
                        Text(r.commercial, Modifier.weight(1.1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    SoundLicense.CUSTOM_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "التفصيل الكامل في ملف sounds/SOURCES.md داخل التطبيق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}
