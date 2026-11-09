package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.data.SegmentKind
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/** تنسيق المدة بالدقائق والثواني: 04:05. */
fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}

// ================= حاضنات الهوية الموحّدة (v0.22.0 «طوّر GUI التطبيق») =================

/**
 * البطاقة الموحّدة: خلفية السطح، حافة مرسومة بلون بطاقة مختبر، وزاوية
 * 16dp ثابتة عبر التطبيق — كل حيّزات الاستوديو والصفحات التفصيلية تلتقطها
 * فتبدو الواجهة عائلة واحدة بدل مستطيلات متفرقة الأشكال.
 */
@Composable
fun StudioCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val sc = LocalStudioColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, sc.cardBorder, RoundedCornerShape(16.dp))
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

/**
 * ترويسة قسم موحّدة: شريحة تمييز سماوية عمودية + عنوان ثقيل، مع مساحة
 * خلفها لأدوات القسم (أزرار تحديث/فتح…) — بدل نصوص عناوين متفاوتة الأنماط.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f, fill = false)
        )
        trailing()
    }
}

/**
 * شارة حالة موحّدة: كبسولة بلون دلالي ونص مضاد مبني على نسبة تباين مختبرة —
 * تُستخدم لـ «سكريبت» و«الجارية» و«الآن» وكل الوسوم الصغيرة.
 */
@Composable
fun StatusChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

/** مقياس مستوى رأسي مقطعي (يمتلئ من الأسفل إلى الأعلى) بمناطق MeterLogic. */
@Composable
fun LevelBar(level: Float, modifier: Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val segments = 12
        val gap = h * 0.015f
        val segH = (h - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            // المقطع 0 أسفل الشاشة؛ الترتيب من الأسفل إلى الأعلى
            val lit = level * segments >= (segments - i)
            val top = h - (i + 1) * segH - i * gap
            drawRoundRect(
                color = Color(GuiTokens.segmentColorArgb(i, lit = true)).let { if (lit) it else trackColor },
                topLeft = Offset(0f, top),
                size = Size(w, segH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 4f)
            )
        }
    }
}

/** مقياس مستوى أفقي مقطعي (يمتلئ من اليمين إلى اليسار — اتجاه القراءة العربية). */
@Composable
fun LevelBarH(level: Float, modifier: Modifier) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val segments = 12
        val gap = w * 0.008f
        val segW = (w - gap * (segments - 1)) / segments
        for (i in 0 until segments) {
            // المقطع 0 عند اليمين (بداية الامتلاء في RTL)
            val lit = level * segments >= (segments - i)
            val rightX = w - i * (segW + gap)
            drawRoundRect(
                color = Color(GuiTokens.segmentColorArgb(i, lit = true)).let { if (lit) it else trackColor },
                topLeft = Offset(rightX - segW, 0f),
                size = Size(segW, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 4f)
            )
        }
    }
}

/** لون نوع الفقرة — تمييز بصري موحّد في الجدولة والاستوديو. */
fun segmentKindColor(kind: SegmentKind): Color = when (kind) {
    SegmentKind.LIVE -> Color(0xFFEF5350)
    SegmentKind.MUSIC -> Color(0xFF4FC3F7)
    SegmentKind.RECORDING -> Color(0xFF80CBC4)
    SegmentKind.SPEECH -> Color(0xFFCCC2DC)
}

/** نقطة ملونة صغيرة (مؤشر نوع/حالة). */
@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(
        modifier = Modifier.size(size).background(color, CircleShape)
    ) { }
}

@Composable
fun PrimaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false
) {
    if (danger) {
        Button(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 36.dp),
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White
            )
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier.defaultMinSize(minHeight = 36.dp),
            enabled = enabled,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            // تسمية زر لا تلتف عموديًا مهما كبر الخط — سطر واحد دائمًا
            maxLines = 1,
            softWrap = false
        )
    }
}

/** منتقيات ملفات Swing — تعمل داخل تطبيقات Compose Desktop. */
object FilePickers {

    fun pickAudioFiles(): List<File>? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "اختر ملفات صوتية"
        chooser.isMultiSelectionEnabled = true
        chooser.fileFilter = FileNameExtensionFilter("ملفات صوتية (WAV / MP3)", "wav", "mp3")
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFiles.toList() else null
    }

    fun pickSingleAudioFile(): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "اختر ملفًا صوتيًا"
        chooser.fileFilter = FileNameExtensionFilter("ملفات صوتية (WAV / MP3)", "wav", "mp3")
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /**
     * اختيار ملفات مكتبة الأصوات الخاصة (v0.42.0) — كل الصيغ التي يفكها
     * المحرك لا WAV/MP3 وحدهما، فلا يُرفض OGG/FLAC لا سبب له.
     */
    fun pickSoundLibraryFiles(): List<File>? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "اختر ملفات مكتبتك الصوتية"
        chooser.isMultiSelectionEnabled = true
        chooser.fileFilter = FileNameExtensionFilter(
            "ملفات صوتية (MP3 / WAV / OGG / FLAC)",
            "mp3", "wav", "ogg", "oga", "flac", "aif", "aiff", "au"
        )
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFiles.toList() else null
    }

    fun pickAudioFolder(): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "اختر مجلدًا"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** حفظ نسخة احتياطية JSON. */
    fun pickJsonSave(defaultName: String): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "حفظ النسخة الاحتياطية"
        chooser.fileFilter = FileNameExtensionFilter("نسخة احتياطية (JSON)", "json")
        chooser.selectedFile = File(defaultName)
        val result = chooser.showSaveDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return null
        var f = chooser.selectedFile
        if (!f.name.lowercase().endsWith(".json")) f = File(f.parentFile, f.name + ".json")
        return f
    }

    /** حفظ سكريبت المذيع كملف PDF (v0.30.0) — الامتداد يُستكمل تلقائيًا. */
    fun pickPdfSave(defaultName: String): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "حفظ سكريبت المذيع PDF"
        chooser.fileFilter = FileNameExtensionFilter("مستند PDF", "pdf")
        chooser.selectedFile = File(defaultName)
        val result = chooser.showSaveDialog(null)
        if (result != JFileChooser.APPROVE_OPTION) return null
        var f = chooser.selectedFile
        if (!f.name.lowercase().endsWith(".pdf")) f = File(f.parentFile, f.name + ".pdf")
        return f
    }

    /** فتح نسخة احتياطية JSON. */
    fun pickJsonOpen(): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "فتح نسخة احتياطية"
        chooser.fileFilter = FileNameExtensionFilter("نسخة احتياطية (JSON)", "json")
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    /** اختيار ملف كوكيز (cookies.txt) لاستيراد اليوتيوب — أي ملف نصي مسموح (v0.25.0). */
    fun pickCookiesFile(): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "اختر ملف الكوكيز (cookies.txt)"
        chooser.fileFilter = FileNameExtensionFilter("ملف كوكيز (txt)", "txt")
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
