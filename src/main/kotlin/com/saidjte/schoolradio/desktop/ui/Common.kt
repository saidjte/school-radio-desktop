package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.Canvas
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

    fun pickAudioFolder(): File? {
        val chooser = JFileChooser()
        chooser.dialogTitle = "اختر مجلدًا"
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }
}
