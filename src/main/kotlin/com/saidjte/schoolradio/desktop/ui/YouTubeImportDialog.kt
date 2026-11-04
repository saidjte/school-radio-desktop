package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.youtube.YouTubeImportService
import com.saidjte.schoolradio.desktop.youtube.YouTubeUrl
import java.io.File

/**
 * حوار استيراد المقاطع من اليوتيوب (v0.25.0):
 * حقل واحد يفهم الرابط والبحث معًا — رابط يوتيوب يعرض بطاقة المقطع
 * (العنوان والقناة والمدة) والكلمات تعرض نتائج بحث للاختيار.
 * ثم اختيار تصنيف المكتبة وتحميل حي بالنسبة ثم تحويل MP3 محليًا،
 * وملف كوكيز اختياري لشبكات يطلب فيها يوتيوب تحققًا إضافيًا.
 */
@Composable
fun YouTubeImportDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    val st by vm.youtubeImportState.collectAsState()
    val results by vm.youtubeResults.collectAsState()
    val categories by vm.libraryCategories.collectAsState()

    var input by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(categories.firstOrNull() ?: "فقرات متنوعة") }
    var categoryMenu by remember { mutableStateOf(false) }
    var showCookiesRow by remember { mutableStateOf(false) }

    val busy = st.phase == YouTubeImportService.Phase.RESOLVING ||
            st.phase == YouTubeImportService.Phase.DOWNLOADING ||
            st.phase == YouTubeImportService.Phase.CONVERTING

    fun closeIfIdle(): () -> Unit = {
        if (!busy) {
            vm.youtubeReset()
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) { vm.youtubeReset(); onDismiss() } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("استيراد من يوتيوب", fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "v0.25",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // —— حقل الرابط/البحث + زر التنفيذ ——
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("رابط مقطع يوتيوب أو كلمات للبحث…") },
                        singleLine = true,
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    )
                    if (st.phase == YouTubeImportService.Phase.RESOLVING) {
                        SecondaryAction(text = "…", enabled = false, onClick = { })
                    } else {
                        PrimaryAction(
                            text = "جلب",
                            enabled = input.isNotBlank(),
                            onClick = { vm.youtubeSearch(input) }
                        )
                    }
                }

                // —— الحالة: تقدم التحميل/التحويل ——
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
                            VideoCard(v) {
                                // اختيار تصنيف ثم استيراد
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PrimaryAction(
                                        text = "تحميل إلى «${shortenName(category, 12)}»",
                                        enabled = !busy,
                                        onClick = { vm.youtubeImport(v, category) }
                                    )
                                }
                            }
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
                                        .clickable(enabled = !busy) {
                                            category = category
                                            vm.youtubeImport(v, category)
                                        }
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
                                    SecondaryAction(text = "تحميل", enabled = !busy, onClick = { vm.youtubeImport(v, category) })
                                }
                            }
                        }
                        // تصنيف الاستيراد
                        CategoryPicker(
                            categories = categories,
                            category = category,
                            expanded = categoryMenu,
                            onExpanded = { categoryMenu = it },
                            onPick = { category = it; categoryMenu = false }
                        )
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
                        "يطلب يوتيوب تحققًا من شبكتك؟ أضف ملف كوكيز (خيار متقدم)",
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

/** بطاقة المقطع المجلوب من رابط: العنوان والقناة والمدة + المحتوى الإضافي (زر التحميل). */
@Composable
private fun VideoCard(
    v: com.saidjte.schoolradio.desktop.youtube.YouTubeJson.VideoInfo,
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
        footer()
    }
}

@Composable
private fun CategoryPicker(
    categories: List<String>,
    category: String,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onPick: (String) -> Unit
) {
    androidx.compose.foundation.layout.Box {
        SecondaryAction(text = "التصنيف: $category ▾", onClick = { onExpanded(true) })
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
