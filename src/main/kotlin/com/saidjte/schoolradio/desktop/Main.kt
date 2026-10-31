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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import com.saidjte.schoolradio.desktop.ui.AppTheme
import com.saidjte.schoolradio.desktop.ui.LiveTab
import com.saidjte.schoolradio.desktop.ui.MusicTab
import com.saidjte.schoolradio.desktop.ui.RecordingsTab
import com.saidjte.schoolradio.desktop.ui.ScheduleTab
import com.saidjte.schoolradio.desktop.ui.ScriptTab
import com.saidjte.schoolradio.desktop.ui.StudioScreen

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
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (tab == Tab.STUDIO) {
            // الاستوديو المدمج: الجدولة + السكريبت + البث في شاشة واحدة بلا قائمة جانبية
            StudioScreen(vm)
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                SideNav(selected = tab, onSelect = vm::selectTab)
                when (tab) {
                    Tab.STUDIO -> StudioScreen(vm)
                    Tab.LIVE -> LiveTab(vm)
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
private fun SideNav(selected: Tab, onSelect: (Tab) -> Unit) {
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
            if (t == selected) {
                Button(onClick = { onSelect(t) }, modifier = Modifier.fillMaxWidth()) {
                    Text(t.label)
                }
            } else {
                FilledTonalButton(onClick = { onSelect(t) }, modifier = Modifier.fillMaxWidth()) {
                    Text(t.label)
                }
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
