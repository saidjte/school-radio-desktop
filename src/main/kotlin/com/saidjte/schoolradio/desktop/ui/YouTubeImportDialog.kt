package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import com.saidjte.schoolradio.desktop.APP_VERSION
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.youtube.YouTubeImportService
import com.saidjte.schoolradio.desktop.youtube.YouTubeUrl
import java.io.File

/** نمط الحوار: تبويب الرابط المباشر أو تبويب البحث بالكلمات — منفصلان تمامًا. */
private enum class YouTubeMode { LINK, SEARCH }

/**
 * حوار استيراد المقاطع من اليوتيوب (v0.25.0 — مطوَّر v0.44.0):
 * تبويبان منفصلان كما طلب المستخدم — «رابط مباشر» و«بحث بالكلمات» —
 * فحقل واحد كان يفهم الاثنين فيتلخبط. منتقي التصنيف أعلى المحتوى في كل
 * الأطوار فيُختار قبل أي تحميل، ومعاينة «استمع قبل أن تحمّل» على بطاقة
 * الرابط وكل نتيجة بحث: تحميل مؤقت وتحويل محلي ثم تشغيل، والتأكيد يسلّم
 * الملف نفسه للمكتبة بلا تحميل ثانٍ. وشارة النسخة من المركز لا نص قديم ثابت.
 */
@Composable
fun YouTubeImportDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    val st by vm.youtubeImportState.collectAsState()
    val results by vm.youtubeResults.collectAsState()
    val categories by vm.libraryCategories.collectAsState()
    val previewPlayingPath by vm.soundPreview.playingFile.collectAsState()

    var mode by remember { mutableStateOf(YouTubeMode.SEARCH) }
    var linkInput by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf(categories.firstOrNull() ?: "فقرات متنوعة") }
    var categoryMenu by remember { mutableStateOf(false) }
    var showCookiesRow by remember { mutableStateOf(false) }

    val busy = st.phase == YouTubeImportService.Phase.RESOLVING ||
            st.phase == YouTubeImportService.Phase.DOWNLOADING ||
            st.phase == YouTubeImportService.Phase.CONVERTING

    fun clearError() { if (inputError != null) inputError = null }

    fun closeIfIdle(): () -> Unit = {
        if (!busy) {
            vm.youtubeReset()
            onDismiss()
        }
    }

    fun doFetchLink() {
        val q = linkInput.trim()
        when {
            q.isEmpty() -> inputError = "الصق رابط المقطع أولًا."
            !YouTubeUrl.looksLikeUrl(q) -> {
                inputError = if (YouTubeUrl.containsUrlSignal(q))
                    "الصق الرابط وحده بلا كلام حوله."
                else "هذا لا يشبه رابطًا — استخدم تبويب «بحث بالكلمات»."
            }
            else -> {
                clearError()
                vm.youtubeSearch(q)
            }
        }
    }

    fun doSearch() {
        val q = searchInput.trim()
        when {
            q.isEmpty() -> inputError = "اكتب كلمات البحث أولًا."
            YouTubeUrl.containsUrlSignal(q) -> inputError = "يبدو أنك لصقت رابطًا — استخدم تبويب «رابط مباشر»."
            else -> {
                clearError()
                vm.youtubeSearch(q)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) { vm.youtubeReset(); onDismiss() } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("استيراد من يوتيوب", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "v$APP_VERSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // —— التبويبان: الرابط المباشر والبحث منفصلان تمامًا (طلب v0.44.0) ——
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModeChip(
                        label = "رابط مباشر",
                        selected = mode == YouTubeMode.LINK,
                        enabled = !busy
                    ) { mode = YouTubeMode.LINK; clearError() }
                    ModeChip(
                        label = "بحث بالكلمات",
                        selected = mode == YouTubeMode.SEARCH,
                        enabled = !busy
                    ) { mode = YouTubeMode.SEARCH; clearError() }
                }

                // —— حقل التبويب النشط فقط + زره ——
                when (mode) {
                    YouTubeMode.LINK -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = linkInput,
                                onValueChange = { linkInput = it; clearError() },
                                placeholder = { Text("الصق رابط مقطع يوتيوب هنا…") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.weight(1f).observesTextEditing()
                            )
                            if (st.phase == YouTubeImportService.Phase.RESOLVING) {
                                SecondaryAction(text = "…", enabled = false, onClick = { })
                            } else {
                                PrimaryAction(
                                    text = "جلب المعلومات",
                                    enabled = linkInput.isNotBlank(),
                                    onClick = { doFetchLink() }
                                )
                            }
                        }
                    }
                    YouTubeMode.SEARCH -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = searchInput,
                                onValueChange = { searchInput = it; clearError() },
                                placeholder = { Text("اكتب كلمات البحث — مثال: نشيد العلم الوطني") },
                                singleLine = true,
                                enabled = !busy,
                                modifier = Modifier.weight(1f).observesTextEditing()
                            )
                            if (st.phase == YouTubeImportService.Phase.RESOLVING) {
                                SecondaryAction(text = "…", enabled = false, onClick = { })
                            } else {
                                PrimaryAction(
                                    text = "بحث",
                                    enabled = searchInput.isNotBlank(),
                                    onClick = { doSearch() }
                                )
                            }
                        }
                    }
                }

                // —— خطأ إدخال محلي (فصل الرابط عن البحث) ——
                inputError?.let { e ->
                    Text(
                        e,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // —— التصنيف قبل التحميل: أعلى المحتوى في كل الأطوار (طلب v0.44.0) ——
                CategoryPicker(
                    categories = categories,
                    category = category,
                    expanded = categoryMenu,
                    enabled = !busy,
                    onExpanded = { categoryMenu = it },
                    onPick = { category = it; categoryMenu = false }
                )

                // —— الحالة: تقدم التحميل/التحويل/المعاينة/النتائج ——
                when (st.phase) {
                    YouTubeImportService.Phase.DOWNLOADING,
                    YouTubeImportService.Phase.CONVERTING -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                st.title.ifEmpty { "المقطع" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LinearProgressIndicator(
                                    progress = { (st.percent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${st.percent.toInt()}%",
                                    style = MaterialTheme.typography.labelLarge.numeric(),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(48.dp)
                                )
                            }
                            Text(
                                st.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            PrimaryAction(
                                text = "إلغاء الاستيراد",
                                danger = true,
                                onClick = { vm.youtubeCancel() }
                            )
                        }
                    }

                    YouTubeImportService.Phase.RESOLVING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            st.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    YouTubeImportService.Phase.RESOLVED -> {
                        st.resolved?.let { v ->
                            VideoCard(v, previewPlayingPath == null, busy,
                                onPreview = { vm.youtubePreview(v) },
                                footer = {
                                    PrimaryAction(
                                        text = "تحميل إلى «${shortenName(category, 12)}»",
                                        enabled = !busy,
                                        onClick = { vm.youtubeImport(v, category) }
                                    )
                                }
                            )
                        }
                    }

                    YouTubeImportService.Phase.RESULTS -> {
                        Text(
                            st.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(results, key = { it.videoId }) { v ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            v.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            listOfNotNull(
                                                v.uploader,
                                                YouTubeUrl.formatDuration(v.durationSec).takeIf { it.isNotEmpty() }
                                            ).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    SecondaryAction(
                                        text = "معاينة",
                                        enabled = !busy,
                                        onClick = { vm.youtubePreview(v) }
                                    )
                                    SecondaryAction(
                                        text = "تحميل",
                                        enabled = !busy,
                                        onClick = { vm.youtubeImport(v, category) }
                                    )
                                }
                            }
                        }
                    }

                    YouTubeImportService.Phase.PREVIEW_READY -> {
                        st.resolved?.let { v ->
                            val pending = vm.youtubePending()
                            val playing = pending != null && previewPlayingPath == pending.file.absolutePath
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                VideoCard(v, previewPlayingPath == null, busy,
                                    onPreview = null, // جاهزة بالفعل — زر التشغيل أدناه يتكفل
                                    footer = { }
                                )
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SecondaryAction(
                                        text = if (playing) "إيقاف المعاينة" else "تشغيل المعاينة",
                                        enabled = pending != null && !busy,
                                        onClick = {
                                            val f = pending?.file
                                            if (f != null && f.isFile) vm.soundPreview.toggle(f)
                                        }
                                    )
                                    PrimaryAction(
                                        text = "تأكيد التحميل إلى «${shortenName(category, 12)}»",
                                        enabled = pending != null && !busy,
                                        onClick = { vm.youtubeImport(v, category) }
                                    )
                                }
                                Text(
                                    "المعاينة تحميل مؤقت — لن يدخل شيئٌ إلى المكتبة إلا بعد التأكيد، والتأكيد لا يعيد التحميل.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                SecondaryAction(
                                    text = "تجاهل المعاينة",
                                    enabled = !busy,
                                    onClick = { vm.youtubeDiscardPreview() }
                                )
                            }
                        }
                    }

                    YouTubeImportService.Phase.DONE -> {
                        Text(
                            st.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        PrimaryAction(text = "تم — إغلاق", onClick = { vm.youtubeReset(); onDismiss() })
                    }

                    YouTubeImportService.Phase.FAILED -> {
                        Text(
                            st.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        PrimaryAction(text = "حسنًا", onClick = { vm.youtubeReset() })
                    }

                    else -> { /* IDLE: لا شيء إضافي */ }
                }

                // —— خيار متقدم: ملف كوكيز (لشبكات يطلب يوتيوب فيها تحققًا) ——
                if (showCookiesRow) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "ملف كوكيز (اختياري):",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SecondaryAction(
                            text = if (vm.youtubeCookiesFile()?.isFile == true) "مُرفق ✓" else "اختيار ملف…",
                            enabled = !busy,
                            onClick = {
                                val f = FilePickers.pickCookiesFile()
                                if (f != null) vm.setYouTubeCookies(f)
                            }
                        )
                        if (vm.youtubeCookiesFile()?.isFile == true) {
                            Text(
                                "إزالة",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = !busy) { vm.setYouTubeCookies(null) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        "يطلب يوتيوب تحققًا من شبكتك رغم كل القنوات؟ أضف ملف كوكيز (خيار متقدم)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCookiesRow = true }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = closeIfIdle(), enabled = !busy) { Text("إغلاق") }
        },
        dismissButton = {
            if (busy) {
                TextButton(onClick = { vm.youtubeCancel() }) { Text("إلغاء") }
            }
        }
    )
}

/** تبويب الحوار: رقاقة اختيار نمط — رابط مباشر أو بحث بالكلمات. */
@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** بطاقة المقطع المجلوب من رابط: العنوان والقناة والمدة + زرا المعاينة والتحميل. */
@Composable
private fun VideoCard(
    v: com.saidjte.schoolradio.desktop.youtube.YouTubeJson.VideoInfo,
    previewIdle: Boolean,
    busy: Boolean,
    onPreview: (() -> Unit)?,
    footer: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            v.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(
                v.uploader,
                YouTubeUrl.formatDuration(v.durationSec).takeIf { it.isNotEmpty() }
            ).joinToString(" · ").ifEmpty { "مقطع يوتيوب" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onPreview != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(text = "معاينة", enabled = previewIdle && !busy, onClick = onPreview)
                footer()
            }
        } else {
            footer()
        }
    }
}

@Composable
private fun CategoryPicker(
    categories: List<String>,
    category: String,
    expanded: Boolean,
    enabled: Boolean,
    onExpanded: (Boolean) -> Unit,
    onPick: (String) -> Unit
) {
    Box {
        SecondaryAction(text = "التصنيف قبل التحميل: $category ▾", enabled = enabled, onClick = { onExpanded(true) })
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            categories.forEach { c ->
                DropdownMenuItem(
                    text = { Text(c, maxLines = 1) },
                    onClick = { onPick(c) }
                )
            }
        }
    }
}
