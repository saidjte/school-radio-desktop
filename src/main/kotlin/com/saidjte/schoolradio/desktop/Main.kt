package com.saidjte.schoolradio.desktop

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.saidjte.schoolradio.desktop.ui.AppTheme
import com.saidjte.schoolradio.desktop.ui.Dot
import com.saidjte.schoolradio.desktop.ui.LivePage
import com.saidjte.schoolradio.desktop.ui.MusicTab
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
    application {
        Window(
            onCloseRequest = { exitApplication() },
            title = "إذاعة مدرسية احترافي — نسخة الحاسوب v$APP_VERSION",
            state = rememberWindowState(placement = WindowPlacement.Maximized, width = 1280.dp, height = 800.dp)
        ) {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
fun AppRoot(vm: AppViewModel = remember { AppViewModel() }) {
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
        if (tab == Tab.STUDIO) {
            // الاستوديو المدمج: الجدولة + السكريبت + البث في شاشة واحدة بلا قائمة جانبية
            StudioScreen(vm)
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                SideNav(selected = tab, onSelect = vm::selectTab, onAir = onAir)
                when (tab) {
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

@Composable
private fun SideNav(selected: Tab, onSelect: (Tab) -> Unit, onAir: Boolean) {
    Column(
        modifier = Modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("إذاعة مدرسية", style = MaterialTheme.typography.headlineSmall)
        Text(
            "نسخة الحاسوب",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Tab.entries.forEach { t ->
            val isSelected = t == selected
            val content: @Composable RowScope.() -> Unit = {
                Text(t.label, modifier = Modifier.weight(1f, fill = false))
                if ((t == Tab.LIVE || t == Tab.STUDIO) && onAir) {
                    Dot(color = Color(0xFFFF6E6E), size = 9.dp)
                }
            }
            if (isSelected) {
                Button(onClick = { onSelect(t) }, modifier = Modifier.fillMaxWidth(), content = content)
            } else {
                FilledTonalButton(onClick = { onSelect(t) }, modifier = Modifier.fillMaxWidth(), content = content)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "v$APP_VERSION",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
