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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saidjte.schoolradio.desktop.APP_VERSION
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.Tab
import com.saidjte.schoolradio.desktop.audio.MicState
import com.saidjte.schoolradio.desktop.data.ScriptStats
import com.saidjte.schoolradio.desktop.data.StudioLogic

/**
 * صفحة البث المباشر — الشاشة المدمجة الوحيدة في التطبيق، ومحتواها فقط ما يلزم المذيع
 * أثناء الهواء: الميكروفون والموسيقى ولوحة القراءة الكبيرة. بقية الأقسام (الجدولة،
 * تحرير السكريبت، المكتبة) لِكلٍّ منها صفحته الخاصة — كي لا تكتظ صفحة البث
 * وتبقى السلاسة في العمل أثناء البث المباشر دون تنقّل بين الصفحات.
 */
@Composable
fun LivePage(vm: AppViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        LiveTopBar(vm)
        // شريط الموعد الأسبوعي المنطلق — يظهر لحظة إطلاق أي موعد مجدول
        WeeklyAlertBanner(
            vm = vm,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReadingPane(vm, Modifier.weight(1f))
            BroadcastColumn(vm)
        }
    }
}

// ================= الشريط العلوي: الهواء + الساعة =================

@Composable
private fun LiveTopBar(vm: AppViewModel) {
    val clock by vm.clockText.collectAsState()
    val micState by vm.mic.state.collectAsState()
    val recording by vm.mic.recording.collectAsState()
    val playing by vm.player.isPlaying.collectAsState()
    val paused by vm.player.isPaused.collectAsState()
    val onAir = StudioLogic.status(
        micLive = micState == MicState.LIVE,
        recording = recording,
        musicPlaying = playing && !paused
    ).onAir

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "البث المباشر",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // مؤشر الهواء الموحد بنبض حي
        OnAirPill(onAir = onAir)
        if (recording) {
            Text(
                "تسجيل جارٍ",
                style = MaterialTheme.typography.labelLarge,
                color = LocalStudioColors.current.recordingAccent,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.weight(1f))
        Text(
            clock,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "v$APP_VERSION",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================= لوحة القراءة الكبيرة (العمود الأعرض) =================

@Composable
private fun ReadingPane(vm: AppViewModel, modifier: Modifier = Modifier) {
    val schedule by vm.schedule.collectAsState()
    val runActive by vm.runActive.collectAsState()
    val runIndex by vm.runCurrentIndex.collectAsState()
    val script by vm.script.collectAsState()
    val selectedId by vm.selectedSectionId.collectAsState()
    val fullReading by vm.fullScriptReading.collectAsState()

    var fontSp by remember { mutableStateOf(34f) }

    val resolved = StudioLogic.resolveScriptSection(schedule, runActive, runIndex, script, selectedId)

    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "لوحة القراءة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            // مصدر النص المعروض: من الجدولة أم اختيار يدوي
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    resolved?.second?.label ?: "فارغ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Switch(checked = fullReading, onCheckedChange = { vm.fullScriptReading.value = it })
                Text("كامل", style = MaterialTheme.typography.bodySmall)
            }
            SecondaryAction(text = "أ−", onClick = { fontSp = (fontSp - 4f).coerceAtLeast(16f) })
            SecondaryAction(text = "أ+", onClick = { fontSp = (fontSp + 4f).coerceAtMost(72f) })
        }

        // سطح القراءة الكبير
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (script.isEmpty()) {
                EmptyState(
                    icon = AppIconKind.SCRIPT,
                    title = "لا يوجد سكريبت بعد",
                    hint = "أضف فقرات النص من قسم «سكريبت المذيع» وستظهر هنا بخط كبير للقراءة على الهواء."
                )
            } else if (fullReading) {
                // وضع «كامل»: السكريبت بأكمله للقراءة المتصلة دون تنقّل
                script.forEachIndexed { i, sec ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${i + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            sec.title.ifEmpty { "بدون عنوان" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        sec.body.ifEmpty { "—" },
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.5f).sp
                    )
                    Spacer(Modifier.height(14.dp))
                }
            } else {
                val shown = resolved?.first
                if (shown == null) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        shown.title.ifEmpty { "بدون عنوان" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        shown.body.ifEmpty { "—" },
                        fontSize = fontSp.sp,
                        lineHeight = (fontSp * 1.5f).sp
                    )
                }
            }
        }

        // أسفل اللوحة: معلومات + تنقّل سلس بأقل عدد من الأزرار
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (fullReading) {
                    "السكريبت كاملًا · ${script.size} فقرات"
                } else {
                    resolved?.first?.let {
                        "${ScriptStats.wordCount(it.body)} كلمة · " +
                                "زمن تقديري ${formatMs(ScriptStats.secondsFor(it.body) * 1000)}"
                    } ?: "لا نص"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            if (runActive) {
                // أثناء جريان الحلقة: زر واحد ينهي الفقرة وينتقل للسكريبت التالي آليًا
                PrimaryAction(
                    text = "إنهاء الفقرة (${runIndex + 1}/${schedule.size})",
                    onClick = { vm.finishCurrentItem() }
                )
            } else {
                SecondaryAction(
                    text = "السابقة",
                    onClick = { vm.selectAdjacentSection(-1) }
                )
                SecondaryAction(
                    text = "التالية",
                    onClick = { vm.selectAdjacentSection(+1) }
                )
            }
            SecondaryAction(text = "تحرير", onClick = { vm.selectTab(Tab.SCRIPT) })
        }
    }
}

// ================= عمود البث: الميكروفون + الموسيقى =================

@Composable
private fun BroadcastColumn(vm: AppViewModel) {
    Column(
        modifier = Modifier.width(330.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BroadcastMicCard(vm)
        BroadcastMusicCard(vm)
        Spacer(Modifier.weight(1f))
    }
}
