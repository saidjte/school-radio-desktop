package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.saidjte.schoolradio.desktop.APP_VERSION
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.OnAirDisplayLogic
import com.saidjte.schoolradio.desktop.data.OnAirModel
import com.saidjte.schoolradio.desktop.data.LiveAnnounceLogic
import com.saidjte.schoolradio.desktop.data.ScreenRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * نافذة «على الهواء» لشاشة ثانية (v0.32.0 «وضع على الهواء ملء الشاشة لشاشة
 * ثانية يتابعها الطلاب»): عرض مسرحي حيّ يفتح على الشاشة الثانية لملئها كاملة
 * بلا إطارات — الفقرة الجارية ومتبقيها بالمؤقت الآلي، الموسيقى الآن، التالية
 * في الحلقة، والساعة. على حاسوب بشاشة واحدة تُفتح نافذة عادية قابلة للسحب
 * إلى الشاشة الخارجية عند توصيلها. النافذة مستقلة غير ماسكة كجدار الجينغل —
 * الاستوديو يظل عاملاً بكامل أدواته ما دامت مفتوحة.
 */
@Composable
fun OnAirWindow(vm: AppViewModel, onClose: () -> Unit) {
    val windowIcon = remember {
        Thread.currentThread().contextClassLoader
            .getResourceAsStream("icon.png")
            ?.use { org.jetbrains.skia.Image.makeFromEncoded(it.readBytes()) }
            ?.toComposeImageBitmap()
    }
    val secondary = remember { detectSecondaryScreen() }
    Window(
        onCloseRequest = onClose,
        title = "على الهواء — عرض الطلاب · إذاعة مدرسية احترافي v$APP_VERSION",
        state = rememberWindowState(width = 1024.dp, height = 640.dp),
        // الشاشة الثانية موجودة: ملء الشاشة بلا إطار. بشاشة واحدة: نافذة سحب عادية
        undecorated = secondary != null,
        icon = windowIcon?.let { BitmapPainter(it) }
    ) {
        // التمركز على الشاشة الثانية ثم ملؤها كاملة
        LaunchedEffect(secondary) {
            val s = secondary
            if (s != null) {
                window.setLocation(s.x, s.y)
                window.setSize(s.width, s.height)
                window.extendedState = java.awt.Frame.MAXIMIZED_BOTH
            }
        }
        AppTheme {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                OnAirContent(vm = vm, onClose = onClose, onSecondary = secondary != null)
            }
        }
    }
}

/**
 * كشف الشاشة الثانية بقراءة AWT — فشل أي شيء يعود بلا شاشة ثانية فتُفتح عادية.
 * internal لتشاركها نافذة القراءة على الشاشة الثانية (v0.35.0) — مصدر واحد للحقيقة.
 */
internal fun detectSecondaryScreen(): ScreenRect? = try {
    val env = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
    val devices = env.screenDevices
    val screens = devices.map { d ->
        val b = d.defaultConfiguration.bounds
        ScreenRect(b.x, b.y, b.width, b.height)
    }
    val defIdx = devices.indices.firstOrNull { devices[it] === env.defaultScreenDevice } ?: 0
    OnAirDisplayLogic.pickSecondary(screens, defIdx)
} catch (_: Throwable) {
    null
}

@Composable
private fun OnAirContent(vm: AppViewModel, onClose: () -> Unit, onSecondary: Boolean) {
    val sc = LocalStudioColors.current
    val school by vm.schoolName.collectAsState()
    val clock by vm.clockText.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val runElapsed by vm.runElapsedMs.collectAsState()
    val remaining by vm.runRemainingMs.collectAsState()
    val warn by vm.runWarn.collectAsState()
    val schedule by vm.schedule.collectAsState()
    val micState by vm.mic.state.collectAsState()
    val rec by vm.mic.recording.collectAsState()
    val mixRec by vm.mixRecording.collectAsState()
    val musicTitle by vm.player.title.collectAsState()
    val musicPos by vm.player.positionMs.collectAsState()
    val musicDur by vm.player.durationMs.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()

    // شارة إعلان البث المباشر (v0.35.0): تضيء مدة محدودة بعد نقرة الزر
    val announceAt by vm.liveAnnounceStartedAt.collectAsState()
    var announceNow by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            announceNow = System.currentTimeMillis()
            delay(500)
        }
    }
    val announceActive = LiveAnnounceLogic.isActive(announceAt, announceNow)
    val announceRemaining = LiveAnnounceLogic.remainingMs(announceAt, announceNow)

    val model = remember(
        runActive, runIndex, runElapsed, remaining, warn, schedule,
        musicTitle, musicPos, musicDur, playing,
        micState == MicState.LIVE, rec, mixRec, announceActive
    ) {
        OnAirDisplayLogic.buildModel(
            runActive = runActive,
            runIndex = runIndex,
            runElapsedMs = runElapsed,
            remainingMs = remaining,
            warn = warn,
            scheduleTitles = schedule.map { it.title },
            schedulePlannedSeconds = schedule.map { it.plannedSeconds },
            musicTitle = musicTitle,
            musicPositionMs = musicPos,
            musicDurationMs = musicDur,
            musicPlaying = playing,
            micLive = micState == MicState.LIVE,
            recording = rec || mixRec,
            announceActive = announceActive
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // الرأس: الهوية + شارة على الهواء + الساعة الكبيرة
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LogoMark(size = 52.dp)
            Column {
                Text(
                    school ?: "إذاعة مدرسية",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "عرض الطلاب — يتابع الحلقة لحظة بلحظة",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (model.onAir) sc.onAir else MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (model.onAir) {
                    PulsingDot(color = sc.onAirContent, size = 14.dp)
                } else {
                    Box(
                        Modifier
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                    )
                }
                Text(
                    if (model.onAir) "على الهواء" else "خارج البث",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (model.onAir) sc.onAirContent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                clock,
                style = MaterialTheme.typography.displaySmall.numeric(),
                fontWeight = FontWeight.Bold
            )
        }

        // شارة إعلان البث المباشر — نقرة زر الاستوديو تضيئها هنا لمدة محدودة
        if (announceActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(sc.onAir)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PulsingDot(color = sc.onAirContent, size = 14.dp)
                Text(
                    LiveAnnounceLogic.BANNER_TEXT,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = sc.onAirContent,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "الإعلان ${announceRemaining / 1000L}ث",
                    style = MaterialTheme.typography.labelLarge.numeric(),
                    color = sc.onAirContent
                )
            }
        }

        // البطاقة الرئيسية — ما يتابعه الطلاب الآن
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            when (model.headlineKind) {
                com.saidjte.schoolradio.desktop.data.HeadlineKind.RUN_ITEM ->
                    RunItemCard(model = model, index = runIndex, total = schedule.size, sc = sc)

                com.saidjte.schoolradio.desktop.data.HeadlineKind.MUSIC ->
                    MusicHeadlineCard(model = model)

                com.saidjte.schoolradio.desktop.data.HeadlineKind.NONE -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "الاستوديو جاهز",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "عند بدء الحلقة أو تشغيل الموسيقى يُعرض ما يبثّه المذيع هنا لحظة بلحظة",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // التالية في الحلقة — يعرف الطلاب ما بعد الفقرة الجارية
        if (model.nextTitle != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "التالية",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Text(
                    model.nextTitle ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // الموسيقى المصاحبة للفقرة الجارية — شريط ثانوي رقيق
        if (model.musicTitle != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppIcon(kind = AppIconKind.MUSIC, size = 22.dp, tint = MaterialTheme.colorScheme.primary)
                Text(
                    model.musicTitle ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    model.musicTimeText ?: "",
                    style = MaterialTheme.typography.labelLarge.numeric(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // التذييل: مؤشرات البث الحية + إنهاء العرض
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (micState == MicState.LIVE) {
                PulsingDot(color = sc.liveDot, size = 10.dp)
                Text(
                    "الميكروفون مباشر",
                    style = MaterialTheme.typography.labelLarge,
                    color = sc.liveDot,
                    fontWeight = FontWeight.Bold
                )
            }
            if (rec || mixRec) {
                PulsingDot(color = sc.recDot, size = 10.dp)
                Text(
                    "تسجيل جارٍ",
                    style = MaterialTheme.typography.labelLarge,
                    color = sc.recordingAccent,
                    fontWeight = FontWeight.Bold
                )
            }
            if (!model.onAir && micState != MicState.LIVE && !rec && !mixRec) {
                Text(
                    "الاستوديو في وضع الاستعداد",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "v$APP_VERSION",
                style = MaterialTheme.typography.labelSmall.brand(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SecondaryAction(text = "إنهاء العرض", onClick = onClose)
        }

        // تعليمات الشاشة الواحدة: النافذة تُسحب إلى الشاشة الخارجية عند توصيلها
        if (!onSecondary) {
            Text(
                "لا شاشة ثانية موصولة — اسحب هذه النافذة إلى شاشة العرض عند توصيلها فتملؤها كاملة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** بطاقة الفقرة الجارية: العنوان الضخم والزمن وشريط التقدم والمتبقي للفاصل. */
@Composable
private fun RunItemCard(
    model: OnAirModel,
    index: Int,
    total: Int,
    sc: com.saidjte.schoolradio.desktop.ui.StudioColors
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PulsingDot(color = sc.liveDot, size = 12.dp)
            Text(
                "الفقرة الجارية (${index + 1} من ${total.coerceAtLeast(1)})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            model.headline ?: "",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            model.timeText ?: "",
            style = MaterialTheme.typography.titleLarge.numeric(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // شريط التقدم — عرضي باتجاه القراءة العربية (يمين → يسار)
        if (model.headlineProgress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(sc.meterTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(model.headlineProgress.coerceIn(0f, 1f))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        // المتبقي للفاصل الآلي: عادي / كهرماني في نافذة التحذير / أحمر عند التجاوز
        if (model.remainingText != null) {
            val color = when {
                model.fired -> sc.meterRed
                model.warn -> sc.meterAmber
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (model.fired || model.warn) PulsingDot(color = color, size = 10.dp)
                Text(
                    model.remainingText,
                    style = MaterialTheme.typography.headlineSmall.numeric(),
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
    }
}

/** بطاقة الموسيقى كعنوان رئيسي حين لا حلقة جارية: العنوان والزمن والتقدم. */
@Composable
private fun MusicHeadlineCard(model: OnAirModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppIcon(kind = AppIconKind.MUSIC, size = 26.dp, tint = MaterialTheme.colorScheme.primary)
            Text(
                if (model.musicPaused) "موسيقى موقوفة مؤقتًا" else "الموسيقى الآن",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            model.headline ?: "",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            model.timeText ?: "",
            style = MaterialTheme.typography.titleLarge.numeric(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(model.headlineProgress?.coerceIn(0f, 1f) ?: 0f)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
