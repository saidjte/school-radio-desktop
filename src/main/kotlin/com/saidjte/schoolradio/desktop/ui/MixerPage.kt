package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.audio.MusicEq
import com.saidjte.schoolradio.desktop.data.MixerLogic
import com.saidjte.schoolradio.desktop.data.VoiceFxState

/**
 * وحدة المزج الإذاعية — غرفة التحكم الصوتي كما في الإذاعة الاحترافية:
 *
 * - قنوات: الميكروفون والموسيقى والماستر، بفيدرات وكتم جلسي ومقاييس حية.
 * - معالج صوت المذيع: تمرير عالٍ + بوابة ضجيج + منحنى إذاعي + ضاغط + محدّد أمان،
 *   بأنماط جاهزة (طبيعي / صوت المذيع / وضوح السرد / دفء الاستوديو).
 * - معادل الصوت: خمس شرائح (80/250/1ك/3.5ك/10ك) بأنماط إذاعية جاهزة.
 *
 * كل التغييرات حيّة على القنوات أثناء البث، وتُحفظ تلقائيًا في الإعدادات.
 */
@Composable
fun MixerPage(vm: AppViewModel) {
    val mixer by vm.mixer.collectAsState()
    val micLevel by vm.mic.level.collectAsState()
    val musicLevel by vm.player.level.collectAsState()
    val micMuted by vm.micMuted.collectAsState()
    val musicMuted by vm.musicMuted.collectAsState()
    val gateClosed by vm.mic.gateClosed.collectAsState()
    val micLive by vm.mic.state.collectAsState()
    val duckActive by vm.mic.duckActive.collectAsState()
    val aecLocked by vm.mic.aecLocked.collectAsState()
    val aecDelayMs by vm.mic.aecDelayMs.collectAsState()
    val aecErleDb by vm.mic.aecErleDb.collectAsState()
    val aecMonitorLocked by vm.mic.aecMonitorLocked.collectAsState()
    val aecMonitorErleDb by vm.mic.aecMonitorErleDb.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ================= الترويسة =================
        Column {
            Text(
                "وحدة المزج",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "غرفة تحكم صوتية إذاعية: فيدرات القنوات، معالج صوت المذيع، ومعادل الصوت — كل التغييرات حيّة أثناء البث",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ================= شريط القنوات الثلاثة =================
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChannelStrip(
                title = "قناة الميكروفون",
                subtitle = if (micLive == com.saidjte.schoolradio.desktop.audio.MicState.LIVE) "بث مباشر" else "غير نشطة",
                fader = mixer.micFader,
                onFader = { vm.setMicFader(it) },
                muted = micMuted,
                showMute = true,
                onMuted = { vm.setMicMuted(!micMuted) },
                level = micLevel,
                modifier = Modifier.weight(1f),
                extra = {
                    if (mixer.voice.enabled && mixer.voice.gateOn) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Dot(color = if (gateClosed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, size = 8.dp)
                            Text(
                                if (gateClosed) "البوابة مغلقة — صمت المقصلة" else "البوابة مفتوحة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        "المعالج: " + if (mixer.voice.enabled) {
                            listOfNotNull(
                                if (mixer.voice.hpfOn) "قناع رعد" else null,
                                if (mixer.voice.gateOn) "بوابة" else null,
                                if (mixer.voice.compOn) "ضاغط" else null
                            ).ifEmpty { listOf("مطفي") }.joinToString(" · ")
                        } else "مطفي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            ChannelStrip(
                title = "قناة الموسيقى",
                subtitle = "معادل الصوت خمس الشرائح",
                fader = mixer.musicFader,
                onFader = { vm.setMusicFader(it) },
                muted = musicMuted,
                showMute = true,
                onMuted = { vm.setMusicMuted(!musicMuted) },
                level = musicLevel,
                modifier = Modifier.weight(1f),
                extra = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Dot(
                            color = when {
                                duckActive && mixer.duck.enabled -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            size = 8.dp
                        )
                        Text(
                            "الخفض التلقائي: " + when {
                                !mixer.duck.enabled -> "مطفي"
                                duckActive -> "الموسيقى منخفضة لحديث المذيع"
                                else -> "جاهز"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "المعادل: " + if (mixer.musicEq.enabled)
                            (mixer.musicEq.preset ?: "مخصص")
                        else "مطفي",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
            ChannelStrip(
                title = "الماستر",
                subtitle = "مخرج الهواء الموحد",
                fader = mixer.masterFader,
                onFader = { vm.setMasterFader(it) },
                muted = false,
                showMute = false,
                onMuted = { },
                level = maxOf(micLevel, musicLevel),
                modifier = Modifier.width(240.dp),
                extra = {
                    Text(
                        "يضرب القناتين جميعًا",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }

        // ================= معالج صوت المذيع =================
        VoiceProcessorCard(vm, mixer.voice)

        // ================= معادل الصوت =================
        MusicEqCard(vm, mixer.musicEq)

        // ================= الخفض التلقائي (Voice-Over) =================
        DuckCard(vm, mixer.duck, duckActive)

        // ================= إلغاء صدى الميكروفون (AEC) =================
        AecCard(
            vm, mixer.aec, aecLocked, aecDelayMs, aecErleDb,
            aecMonitorLocked, aecMonitorErleDb
        )

        Spacer(Modifier.height(4.dp))
    }
}

// ================= شريط قناة =================

@Composable
private fun ChannelStrip(
    title: String,
    subtitle: String,
    fader: Float,
    onFader: (Float) -> Unit,
    muted: Boolean,
    showMute: Boolean,
    onMuted: () -> Unit,
    level: Float,
    modifier: Modifier = Modifier,
    extra: @Composable () -> Unit = {}
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showMute) {
                MuteChip(muted = muted, onClick = onMuted)
            }
        }

        BroadcastMeter(level = level, modifier = Modifier.fillMaxWidth().height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${MixerLogic.faderPercent(fader)}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (fader <= 0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(46.dp)
            )
            Slider(
                value = fader,
                onValueChange = onFader,
                valueRange = 0f..MixerLogic.FADER_MAX,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${MixerLogic.faderDb(fader)} dB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(70.dp)
            )
        }
        extra()
    }
}

/** زر الكتم الجلسي M — يصمت مؤقتًا ولا يلمس القيم المحفوظة إطلاقًا. */
@Composable
internal fun MuteChip(muted: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
    ) {
        Text(
            "M",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (muted) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ================= شريحة نمط جاهز =================

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

// ================= معالج صوت المذيع =================

@Composable
private fun VoiceProcessorCard(vm: AppViewModel, v: VoiceFxState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "معالج صوت المذيع",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = v.enabled, onCheckedChange = { on -> vm.updateVoiceFx { it.copy(enabled = on) } })
        }
        Text(
            "سلسلة إذاعية كاملة: قناع الرعد (تمرير عالٍ 85هرتز) ← بوابة الضجيج ← توحيد الكسب AGC ← منحنى الصوت ← الضاغط ← محدّد الأمان",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (v.enabled) {
            // الأنماط الجاهزة
            Text("أنماط الإذاعة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MixerLogic.VOICE_PRESET_NAMES.forEach { name ->
                    PresetChip(
                        label = name,
                        selected = v.preset == name,
                        onClick = { vm.applyVoicePreset(name) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // قناع الرعد
            LabeledSwitch("قناع الرعد — قصّ الرمبل والاحتكاك (85هرتز)", v.hpfOn) {
                vm.updateVoiceFx { it.copy(hpfOn = it.hpfOn.not()) }
            }

            // بوابة الضجيج
            LabeledSwitch("بوابة الضجيج — صمت المقصلة بين الجمل", v.gateOn) {
                vm.updateVoiceFx { it.copy(gateOn = it.gateOn.not()) }
            }
            if (v.gateOn) {
                DbSlider(
                    label = "عتبة البوابة",
                    value = v.gateThresholdDb,
                    range = -60f..-20f,
                    onValue = { vm.updateVoiceFx { s -> s.copy(gateThresholdDb = it, preset = null) } }
                )
            }

            // توحيد الكسب الأوتوماتيكي AGC
            LabeledSwitch("توحيد الكسب الأوتوماتيكي AGC — مستوى نطق ثابت مهما بعدتَ عن الميكروفون", v.agcOn) {
                vm.updateVoiceFx { it.copy(agcOn = it.agcOn.not()) }
            }
            if (v.agcOn) {
                DbSlider(
                    label = "مستوى الهدف",
                    value = v.agcTargetDb,
                    range = -30f..-6f,
                    onValue = { vm.updateVoiceFx { s -> s.copy(agcTargetDb = MixerLogic.cleanAgcTarget(it)) } }
                )
                DbSlider(
                    label = "أقصى تعزيز",
                    value = v.agcMaxBoostDb,
                    range = 6f..30f,
                    onValue = { vm.updateVoiceFx { s -> s.copy(agcMaxBoostDb = MixerLogic.cleanAgcBoost(it)) } }
                )
                Text(
                    "الكسب يتحرك ببطء فلا مضخّ ولا قفزات، ويجمّد نفسه عند الصمت كي لا يضخّم الضجيج",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // الضاغط
            LabeledSwitch("الضاغط الديناميكي — توحيد مستوى النطق", v.compOn) {
                vm.updateVoiceFx { it.copy(compOn = it.compOn.not()) }
            }
            if (v.compOn) {
                DbSlider(
                    label = "عتبة الضاغط",
                    value = v.compThresholdDb,
                    range = -40f..0f,
                    onValue = { vm.updateVoiceFx { s -> s.copy(compThresholdDb = it, preset = null) } }
                )
                ParamSlider(
                    label = "نسبة الضغط ${"%.1f".format(v.compRatio)}:1",
                    value = v.compRatio,
                    range = 1f..10f,
                    onValue = { vm.updateVoiceFx { s -> s.copy(compRatio = MixerLogic.cleanRatio(it), preset = null) } }
                )
                DbSlider(
                    label = "كسب التعويض",
                    value = v.compMakeupDb,
                    range = 0f..12f,
                    onValue = { vm.updateVoiceFx { s -> s.copy(compMakeupDb = it, preset = null) } }
                )
            }

            // منحنى الصوت
            Text("منحنى الصوت", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            DbSlider(
                label = "باس 180 هرتز",
                value = v.bassDb,
                range = -12f..12f,
                onValue = { vm.updateVoiceFx { s -> s.copy(bassDb = it, preset = null) } }
            )
            DbSlider(
                label = "حضور 3.2ك — قصّ النطق",
                value = v.presenceDb,
                range = -12f..12f,
                onValue = { vm.updateVoiceFx { s -> s.copy(presenceDb = it, preset = null) } }
            )
            DbSlider(
                label = "هواء 8ك — لمعان",
                value = v.airDb,
                range = -12f..12f,
                onValue = { vm.updateVoiceFx { s -> s.copy(airDb = it, preset = null) } }
            )
            Text(
                "محدّد الأمان يعمل دائمًا: الخروج لا يتجاوز −0.6dB أبدًا",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ================= معادل الصوت =================

@Composable
private fun MusicEqCard(vm: AppViewModel, eq: com.saidjte.schoolradio.desktop.data.MusicEqState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "معادل الصوت — قناة الموسيقى",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = eq.enabled, onCheckedChange = { on -> vm.updateMusicEq { it.copy(enabled = on) } })
        }
        Text(
            "خمس شرائح إذاعية ±12dB تعمل على الموسيقى والتسجيلات أثناء التشغيل لحظيًا",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (eq.enabled) {
            Text("أنماط الإذاعة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MixerLogic.MUSIC_PRESET_NAMES.forEach { name ->
                    PresetChip(
                        label = name,
                        selected = eq.preset == name,
                        onClick = { vm.applyMusicEqPreset(name) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            for (b in 0 until MusicEq.BANDS) {
                DbSlider(
                    label = MusicEq.BAND_LABELS[b],
                    value = eq.bands.getOrElse(b) { 0f },
                    range = -MusicEq.MAX_DB..MusicEq.MAX_DB,
                    onValue = { g ->
                        vm.updateMusicEq { s ->
                            s.copy(
                                bands = s.bands.mapIndexed { i, old -> if (i == b) MusicEq.clampBand(g) else old },
                                preset = null
                            )
                        }
                    }
                )
            }
        }
    }
}

// ================= الخفض التلقائي (Voice-Over) =================

@Composable
private fun DuckCard(vm: AppViewModel, d: com.saidjte.schoolradio.desktop.data.DuckState, duckActive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "الخفض التلقائي للموسيقى (Voice-Over)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.enabled) {
                    Dot(
                        color = if (duckActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        size = 8.dp
                    )
                    Text(
                        if (duckActive) "منخفضة الآن" else "جاهز",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = d.enabled, onCheckedChange = { on -> vm.updateDuck { it.copy(enabled = on) } })
            }
        }
        Text(
            "سلوك الاستوديو الإذاعي الحقيقي: حين تتكلم على الميكروفون تنخفض الموسيقى آليًا بالمقدار الذي تختاره، " +
                    "وعند سكوتك تعود تدريجيًا بعد زمن التعليق — بلا أن تلمس أي فيدر",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (d.enabled) {
            DbSlider(
                label = "نسبة الخفض",
                value = d.duckDb,
                range = -30f..-3f,
                onValue = { vm.updateDuck { s -> s.copy(duckDb = MixerLogic.cleanDuckDb(it)) } }
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "زمن التعليق",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(150.dp)
                )
                Slider(
                    value = d.holdMs,
                    onValueChange = { vm.updateDuck { s -> s.copy(holdMs = MixerLogic.cleanHoldMs(it)) } },
                    valueRange = 100f..5000f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "%.1f".format(d.holdMs / 1000f) + " ث",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(62.dp)
                )
            }
            Text(
                "انتقالات ناعمة: هجوم 150ms كي لا تتقدم الموسيقى على كلمتك، وإفلات ناعم بعد التعليق فلا قفزات",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ================= إلغاء صدى الميكروفون (AEC) =================

@Composable
private fun AecCard(
    vm: AppViewModel,
    a: com.saidjte.schoolradio.desktop.data.AecState,
    locked: Boolean,
    delayMs: Float,
    erleDb: Float,
    monitorLocked: Boolean,
    monitorErleDb: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "إلغاء صدى الميكروفون (AEC)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (a.enabled) {
                    Dot(
                        color = if (locked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        size = 8.dp
                    )
                    Text(
                        if (locked) "مسار مقفل · تأخير %.0f ملّي · كبح %.0f دب".format(delayMs, erleDb)
                        else "يبحث عن مسار الصدى…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = a.enabled, onCheckedChange = { on -> vm.updateAec { it.copy(enabled = on) } })
            }
        }
        // مانع صدى المراقبة — مكافئ مانع النظام في الهاتف لصوت المذيع العائد من السماعة
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dot(
                color = if (a.enabled && monitorLocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                size = 8.dp
            )
            Text(
                if (!a.enabled) "المانعان معطّلان — صدى السماعة يصل الهواء كما هو"
                else if (monitorLocked) "صدى مراقبتك مقفل وكُبح %.0f دب — الميكروفون قرب السماعة آمن كال هاتف".format(monitorErleDb)
                else "مانع صدى المراقبة جاهز — يشتغل تلقائيًا مع سماع المراقبة فيُلغي عودة صوتك من السماعة",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "مانعان تكيّفيان متتاليان يرآن مسار الصوت من السماعة إلى الميكروفون ويطرحانه من صوتك: " +
                    "الأول لصدى الموسيقى والثاني لعودة صوتك من سماعة المراقبة — وهذا ما يفعله النظام " +
                    "في الهاتف تلقائيًا، لذلك لا صدى هناك مهما قرب الميكروفون من مكبر الصوت. مع كشف " +
                    "لحظي للحديث المزدوج يحمي صوتك أثناء كلامك، وكابح بقايا يخفي ما تبقّى من صدى " +
                    "بعد الطرح. لا أثر لهما حين لا موسيقى ولا سماع مراقبة تعملان",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================= أدوات مشتركة =================

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = checked, onCheckedChange = { onToggle() })
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DbSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(150.dp)
        )
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${if (value > 0) "+" else ""}%.1f".format(value) + " dB",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(62.dp)
        )
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(150.dp)
        )
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(62.dp))
    }
}

// ================= بطاقة المزج المختصرة (للاستوديو المدمج) =================

/**
 * بطاقة مزج مختصرة للعمود الأيسر في الاستوديو المدمج: سطر واحد —
 * فيدر الماستر + نسبته + بوابة إلى وحدة المزج الكاملة — دون إثقال الاستوديو.
 */
@Composable
fun MixerLinkCard(vm: AppViewModel) {
    val mixer by vm.mixer.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "المزج",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = mixer.masterFader,
            onValueChange = { vm.setMasterFader(it) },
            valueRange = 0f..MixerLogic.FADER_MAX,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${MixerLogic.faderPercent(mixer.masterFader)}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        SecondaryAction(
            text = "الوحدة الكاملة",
            onClick = { vm.selectTab(com.saidjte.schoolradio.desktop.Tab.MIXER) }
        )
    }
}
