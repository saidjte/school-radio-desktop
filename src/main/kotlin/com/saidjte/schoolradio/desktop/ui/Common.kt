package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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

/** مقياس مستوى رأسي (يمتلئ من الأسفل إلى الأعلى). */
@Composable
fun LevelBar(level: Float, modifier: Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(w / 3f))
        val lv = level.coerceIn(0f, 1f)
        if (lv > 0.01f) {
            val barHeight = h * lv
            drawRoundRect(
                color = barColor,
                topLeft = Offset(0f, h - barHeight),
                size = Size(w, barHeight),
                cornerRadius = CornerRadius(w / 3f)
            )
        }
    }
}

/** مقياس مستوى أفقي (يمتلئ من اليمين إلى اليسار — اتجاه القراءة العربية). */
@Composable
fun LevelBarH(level: Float, modifier: Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(h / 2f))
        val lv = level.coerceIn(0f, 1f)
        if (lv > 0.01f) {
            val barWidth = w * lv
            drawRoundRect(
                color = barColor,
                topLeft = Offset(w - barWidth, 0f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(h / 2f)
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
            modifier = modifier,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White
            )
        ) { Text(text) }
    } else {
        Button(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
    }
}

@Composable
fun SecondaryAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled) { Text(text) }
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

    /** فتح نسخة احتياطية JSON. */
    fun pickJsonOpen(): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "فتح نسخة احتياطية"
        chooser.fileFilter = FileNameExtensionFilter("نسخة احتياطية (JSON)", "json")
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
