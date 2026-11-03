package com.saidjte.schoolradio.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.data.SeekLogic

/**
 * شريط تقدم المشغل القابل للسعي (v0.23.0 «التنقل في المقطع بالنقر والسحب»):
 * مكوّن Canvas مرسوم بيد بدل Slider المكتبة — لأن إيماءات الفأرة في Slider
 * المادّي على الحاسوب مضطربة (انقطاع السحب ومناداة نهاية التغيير مبكرًا أو
 * متكررًا) فلا يُنفَّذ السعي ولا يكتمل الزحف.
 *
 * العقد السلوكي — مضمون بحالة SeekLogic.ScrubState:
 * - النقر في أي نقطة: انتقال فوري إلى ذلك الزمن (بداية+نهاية متتاليتان).
 * - السحب: معاينة حية للموضع والزمن أثناء السحب، ثم سعي واحد بالضبط عند
 *   الإفلات بالقيمة الأخيرة.
 * - اتجاه LTR دائمًا (عرف المشغلات) داخل أي تخطيط عربي.
 * - هالة سماوية عند المرور والزحف تكبر المؤشر — نفس لغة توهج فواصل الحيّزات.
 */
@Composable
fun StudioSeekBar(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onScrub: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    val sc = LocalStudioColors.current
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = sc.meterTrack

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var scrubbing by remember { mutableStateOf(false) }
    var scrubMs by remember { mutableLongStateOf(0L) }

    val shownMs = if (scrubbing) scrubMs else positionMs
    val fraction = SeekLogic.fractionForMs(shownMs, durationMs)

    val glowColor by animateColorAsState(
        if (hovered || scrubbing) accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(durationMillis = 140),
        label = "seekGlow"
    )
    val thumbScale by animateFloatAsState(
        if (scrubbing) 1.4f else if (hovered) 1.15f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "seekThumb"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .hoverable(interactionSource = interaction, enabled = enabled)
            .pointerInput(enabled, durationMs) {
                if (!enabled || durationMs <= 0L) return@pointerInput
                awaitEachGesture {
                    // الضغط: معاينة فورية عند نقطة النقر — هذا هو «الانتقال بالنقر»
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val scrub = SeekLogic.ScrubState()
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    scrub.begin(SeekLogic.msForFraction(down.position.x / w, durationMs))
                    scrubbing = true
                    scrubMs = scrub.previewMs
                    onScrub(scrubMs)
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) {
                            // الإفلات: سعي واحد بالضبط بقيمة آخر معاينة
                            val commit = scrub.end()
                            scrubbing = false
                            onScrub(null)
                            if (commit != null) onSeek(commit)
                            break
                        }
                        val pressed = event.changes.first { it.pressed }
                        val f = (pressed.position.x / w).coerceIn(0f, 1f)
                        scrub.update(SeekLogic.msForFraction(f, durationMs))
                        scrubMs = scrub.previewMs
                        onScrub(scrubMs)
                        event.changes.forEach { if (it.pressed) it.consume() }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxWidth().height(36.dp)) {
            val w = size.width
            val cy = size.height / 2f
            val trackH = 6.dp.toPx()
            val radius = CornerRadius(trackH / 2f)

            // هالة التحويم/الزحف — تُعلن قابلية السحب كما في فواصل الحيّزات
            if (glowColor != Color.Transparent) {
                val glowH = 16.dp.toPx()
                drawRoundRect(
                    color = glowColor,
                    topLeft = Offset(0f, cy - glowH / 2f),
                    size = Size(w, glowH),
                    cornerRadius = CornerRadius(glowH / 2f)
                )
            }

            // المسار الكامل ثم الجزء المقطوع
            drawRoundRect(
                color = trackColor,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(w, trackH),
                cornerRadius = radius
            )
            val fillW = (w * fraction).coerceIn(0f, w)
            if (fillW > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(fillW, trackH),
                    cornerRadius = radius
                )
            }

            // المؤشر الدائري — يكبر عند المرور ويكبر أكثر أثناء الزحف
            val thumbR = 7.dp.toPx() * thumbScale
            drawCircle(color = accent, radius = thumbR, center = Offset(fillW, cy))
            drawCircle(
                color = sc.cardBorder,
                radius = thumbR,
                center = Offset(fillW, cy),
                style = Stroke(width = 1.5.dp.toPx())
            )
        }
    }
}
