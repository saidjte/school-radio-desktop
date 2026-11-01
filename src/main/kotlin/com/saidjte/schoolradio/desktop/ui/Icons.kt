package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * أيقونات الواجهة المرسومة متجهيًا داخل التطبيق — بلا اعتماديات خارجية وبثبات كامل
 * على كل الأنظمة. تعمل داخل Canvas فلا تتأثر باتجاه التخطيط RTL.
 */
enum class AppIconKind { STUDIO, LIVE, SCHEDULE, SCRIPT, MUSIC, MIXER, RECORDINGS }

@Composable
fun AppIcon(
    kind: AppIconKind,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        when (kind) {
            AppIconKind.STUDIO -> drawSliders(tint)
            AppIconKind.LIVE -> drawMic(tint)
            AppIconKind.SCHEDULE -> drawCalendar(tint)
            AppIconKind.SCRIPT -> drawDocument(tint)
            AppIconKind.MUSIC -> drawNote(tint)
            AppIconKind.MIXER -> drawMixer(tint)
            AppIconKind.RECORDINGS -> drawDisc(tint)
        }
    }
}

private fun DrawScope.meterStroke(tint: Color, width: Float) =
    Stroke(width = width, cap = StrokeCap.Round)

/** مقياس التحكم (الاستوديو): ثلاثة مسارات بمقابض في مواضع مختلفة. */
private fun DrawScope.drawSliders(tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.045f
    val ys = listOf(0.25f, 0.5f, 0.75f)
    val knobs = listOf(0.66f, 0.32f, 0.8f)
    ys.forEachIndexed { i, y ->
        val yy = h * y
        drawLine(tint, Offset(w * 0.1f, yy), Offset(w * 0.9f, yy), strokeWidth = sw, cap = StrokeCap.Round)
        val kx = w * knobs[i]
        drawCircle(tint, radius = w * 0.1f, center = Offset(kx, yy), style = meterStroke(tint, sw))
    }
}

/** الميكروفون (البث المباشر): كبسولة + قوس + عمود وقاعدة. */
private fun DrawScope.drawMic(tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.06f
    val bodyW = w * 0.34f
    val bodyH = h * 0.42f
    drawRoundRect(
        color = tint,
        topLeft = Offset((w - bodyW) / 2f, h * 0.08f),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(bodyW / 2f)
    )
    val arcR = w * 0.26f
    drawArc(
        color = tint,
        startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w / 2f - arcR, h * 0.24f),
        size = Size(arcR * 2f, arcR * 2f),
        style = meterStroke(tint, sw)
    )
    drawLine(tint, Offset(w / 2f, h * 0.5f + arcR), Offset(w / 2f, h * 0.84f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(tint, Offset(w * 0.32f, h * 0.88f), Offset(w * 0.68f, h * 0.88f), strokeWidth = sw, cap = StrokeCap.Round)
}

/** التقويم (الجدولة): إطار + دبابيس + صفوف نقاط. */
private fun DrawScope.drawCalendar(tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.06f
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.1f, h * 0.16f),
        size = Size(w * 0.8f, h * 0.74f),
        cornerRadius = CornerRadius(w * 0.14f),
        style = meterStroke(tint, sw)
    )
    drawLine(tint, Offset(w * 0.32f, h * 0.08f), Offset(w * 0.32f, h * 0.26f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(tint, Offset(w * 0.68f, h * 0.08f), Offset(w * 0.68f, h * 0.26f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(tint, Offset(w * 0.1f, h * 0.4f), Offset(w * 0.9f, h * 0.4f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
    val dotR = w * 0.045f
    val rows = listOf(0.56f, 0.74f)
    val cols = listOf(0.3f, 0.5f, 0.7f)
    rows.forEach { ry ->
        cols.forEach { cx ->
            drawCircle(tint, radius = dotR, center = Offset(w * cx, h * ry))
        }
    }
}

/** الوثيقة (السكريبت): صفحة بأسطر. */
private fun DrawScope.drawDocument(tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.06f
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.2f, h * 0.08f),
        size = Size(w * 0.6f, h * 0.84f),
        cornerRadius = CornerRadius(w * 0.1f),
        style = meterStroke(tint, sw)
    )
    val lineX1 = w * 0.32f
    val lineX2 = w * 0.68f
    drawLine(tint, Offset(lineX1, h * 0.32f), Offset(lineX2, h * 0.32f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
    drawLine(tint, Offset(lineX1, h * 0.5f), Offset(lineX2, h * 0.5f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
    drawLine(tint, Offset(lineX1, h * 0.68f), Offset(w * 0.56f, h * 0.68f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
}

/** النوتة (الموسيقى): رأس + عمود + راية. */
private fun DrawScope.drawNote(tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.07f
    val stemX = w * 0.62f
    drawLine(tint, Offset(stemX, h * 0.16f), Offset(stemX, h * 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(
        tint, Offset(stemX, h * 0.16f), Offset(w * 0.82f, h * 0.3f),
        strokeWidth = sw * 0.9f, cap = StrokeCap.Round
    )
    drawOval(
        color = tint,
        topLeft = Offset(w * 0.3f, h * 0.62f),
        size = Size(w * 0.32f, h * 0.26f)
    )
}

/** وحدة المزج: فيدران رأسيان بمقابض + شريحة مستوى بينهما. */
private fun DrawScope.drawMixer(tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.055f
    // فيدران رأسيان (يمين: ميكروفون أعلى، يسار: موسيقى أدنى)
    val x1 = w * 0.30f
    val x2 = w * 0.70f
    val top = h * 0.14f
    val bottom = h * 0.86f
    drawLine(tint, Offset(x1, top), Offset(x1, bottom), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(tint, Offset(x2, top), Offset(x2, bottom), strokeWidth = sw, cap = StrokeCap.Round)
    // مقبضان: مستطيلان أفقيان بمواضع مختلفة
    drawLine(tint, Offset(x1 - w * 0.14f, h * 0.34f), Offset(x1 + w * 0.14f, h * 0.34f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
    drawLine(tint, Offset(x2 - w * 0.14f, h * 0.62f), Offset(x2 + w * 0.14f, h * 0.62f), strokeWidth = sw * 1.4f, cap = StrokeCap.Round)
    // شريحة مستوى صغيرة بين الفيدرين
    drawLine(tint.copy(alpha = 0.55f), Offset(w * 0.5f, h * 0.30f), Offset(w * 0.5f, h * 0.46f), strokeWidth = sw * 0.8f, cap = StrokeCap.Round)
}

/** القرص (التسجيلات): قرص مدمج بثقوب. */
private fun DrawScope.drawDisc(tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.055f
    val c = Offset(w / 2f, h / 2f)
    drawCircle(tint, radius = w * 0.4f, center = c, style = meterStroke(tint, sw))
    drawCircle(tint, radius = w * 0.16f, center = c, style = meterStroke(tint, sw))
    drawCircle(tint, radius = w * 0.05f, center = c)
}

/** شعار البث: نقطة مركزية وموجات تنطلق من الجهتين داخل مربع متدرج. */
@Composable
fun LogoMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val sc = LocalStudioColors.current
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.verticalGradient(listOf(sc.logoTop, sc.logoBottom)))
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val cx = this.size.width / 2f
            val cy = this.size.height * 0.56f
            val ink = Color.White
            drawCircle(ink, radius = this.size.width * 0.08f, center = Offset(cx, cy))
            val r1 = this.size.width * 0.26f
            val r2 = this.size.width * 0.44f
            drawArc(
                ink.copy(alpha = 0.95f), startAngle = -50f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(cx - r1, cy - r1), size = Size(r1 * 2f, r1 * 2f),
                style = Stroke(width = this.size.width * 0.05f, cap = StrokeCap.Round)
            )
            drawArc(
                ink.copy(alpha = 0.55f), startAngle = -50f, sweepAngle = 100f, useCenter = false,
                topLeft = Offset(cx - r2, cy - r2), size = Size(r2 * 2f, r2 * 2f),
                style = Stroke(width = this.size.width * 0.05f, cap = StrokeCap.Round)
            )
        }
    }
}
