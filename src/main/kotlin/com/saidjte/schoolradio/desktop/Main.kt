package com.saidjte.schoolradio.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.saidjte.schoolradio.desktop.ui.AppIcon
import com.saidjte.schoolradio.desktop.ui.AppIconKind
import com.saidjte.schoolradio.desktop.ui.AppTheme
import com.saidjte.schoolradio.desktop.ui.LocalStudioColors
import com.saidjte.schoolradio.desktop.ui.LogoMark
import com.saidjte.schoolradio.desktop.ui.LivePage
import com.saidjte.schoolradio.desktop.ui.MusicTab
import com.saidjte.schoolradio.desktop.ui.PulsingDot
import com.saidjte.schoolradio.desktop.ui.RecordingsTab
import com.saidjte.schoolradio.desktop.ui.ScheduleTab
import com.saidjte.schoolradio.desktop.ui.ScriptTab
import com.saidjte.schoolradio.desktop.ui.StudioScreen
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.StudioLogic

fun main(args: Array<String>) {
    // وضع الفحص الذاتي: تشغيل النواة دون واجهة رسومية (للأتمتة والتحقق)
    if (args.contains("selftest")) {
        SelfTest.run()
        return
    }
    // صفحة بداية للأتمتة والفحص البصري: --tab=LIVE | SCHEDULE | SCRIPT | MUSIC | RECORDINGS
    val startTab = args.firstOrNull { it.startsWith("--tab=") }
        ?.substringAfter("=")
        ?.let { name -> Tab.entries.firstOrNull { it.name == name } }
    application {
        Window(
            onCloseRequest = { exitApplication() },
            title = "إذاعة مدرسية احترافي — نسخة الحاسوب v$APP_VERSION",
            state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1280.dp, height = 800.dp)
        ) {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(startTab = startTab)
                }
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = remember { AppViewModel() }, startTab: Tab? = null) {
    // الانتقال إلى صفحة البداية إن حُددت من سطر الأوامر (للأتمتة) — غير ذلك تبقى «الاستوديو المدمج»
    LaunchedEffect(startTab) { startTab?.let(vm::selectTab) }
    val tab by vm.tab.collectAsState()
    val micState by vm.mic.state.collectAsState()
    val recording by vm.mic.recording.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val paused by vm.player.isPaused.collectAsState()
    val onAir = StudioLogic.status(
        micLive = micState == MicState.LIVE,
        recording = recording,
        musicPlaying = playing && !paused
    ).onAir

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        // انتقال ناعم بين الصفحات يخدم السلاسة دون إبطاء التنقّل
        Crossfade(targetState = tab, animationSpec = tween(160), label = "pages") { t ->
            if (t == Tab.STUDIO) {
                // الاستوديو المدمج: الجدولة + السكريبت + البث في شاشة واحدة بلا قائمة جانبية
                StudioScreen(vm)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    SideNav(selected = t, onSelect = vm::selectTab, onAir = onAir)
                    when (t) {
                        Tab.STUDIO -> StudioScreen(vm)
                        Tab.LIVE -> LivePage(vm)
                        Tab.SCHEDULE -> ScheduleTab(vm)
                        Tab.SCRIPT -> ScriptTab(vm)
                        Tab.MUSIC -> MusicTab(vm)
                        Tab.RECORDINGS -> RecordingsTab(vm)
                    }
                }
            }
        }
    }
}

/** أيقونة كل صفحة — ترميز بصري ثابت بين القائمة وأي موضع آخر. */
private fun tabIcon(t: Tab): AppIconKind = when (t) {
    Tab.STUDIO -> AppIconKind.STUDIO
    Tab.LIVE -> AppIconKind.LIVE
    Tab.SCHEDULE -> AppIconKind.SCHEDULE
    Tab.SCRIPT -> AppIconKind.SCRIPT
    Tab.MUSIC -> AppIconKind.MUSIC
    Tab.RECORDINGS -> AppIconKind.RECORDINGS
}

@Composable
private fun SideNav(selected: Tab, onSelect: (Tab) -> Unit, onAir: Boolean) {
    val sc = LocalStudioColors.current
    Column(
        modifier = Modifier
            .width(232.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // رأس الهوية: شعار البث + الاسم
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            LogoMark(size = 42.dp)
            Column {
                Text(
                    "إذاعة مدرسية",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "نسخة الحاسوب",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Tab.entries.forEach { t ->
            NavItem(
                label = t.label,
                icon = tabIcon(t),
                selected = t == selected,
                showLiveDot = (t == Tab.LIVE || t == Tab.STUDIO) && onAir,
                liveDot = sc.liveDot,
                onClick = { onSelect(t) }
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            "v$APP_VERSION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** عنصر قائمة احترافي: أيقونة + كapsula تحديد + تظليل عند المرور بالفأرة. */
@Composable
private fun NavItem(
    label: String,
    icon: AppIconKind,
    selected: Boolean,
    showLiveDot: Boolean,
    liveDot: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        hovered -> MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        else -> Color.Transparent
    }
    val contentTint =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(
                if (selected) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .hoverable(interaction)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppIcon(kind = icon, size = 20.dp, tint = contentTint)
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (showLiveDot) {
            PulsingDot(color = liveDot, size = 8.dp)
        }
    }
}
