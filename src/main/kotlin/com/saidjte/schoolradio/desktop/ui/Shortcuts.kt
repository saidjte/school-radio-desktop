package com.saidjte.schoolradio.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.key.isShiftPressed
import com.saidjte.schoolradio.desktop.AppViewModel
import com.saidjte.schoolradio.desktop.Tab
import java.util.concurrent.atomic.AtomicInteger

/**
 * اختصارات لوحة المفاتيح الاحترافية (v0.29.0): بروتوكول ثابت لكل استوديو إذاعي —
 * مسافة = تشغيل/إيقاف مؤقت، 1-9 = إطلاق/إيقاف موضع الجينل حتى والجدار مغمور،
 * الأسهم = سعي ±5 ثوانٍ، N/P = التالي/السابق، M = كتم جلسي.
 * الحلّ نقية قابلة للاختبار، والتطبيق عبر معالج مستوى النافذة يُمرَّر للأفعال هنا.
 */
sealed interface ShortcutAction {
    /** مسافة: تشغيل/إيقاف مؤقت للموسيقى. */
    data object PlayPauseMusic : ShortcutAction

    /** 1-9: إطلاق/إيقاف الجينل في الموضع (رقم المستخدم، والدليل الداخلي يبدأ من 0). */
    data class FireJingle(val slot: Int) : ShortcutAction

    /** السهم الأيسر: سعي خمس ثوانٍ إلى الخلف. */
    data object SeekBack : ShortcutAction

    /** السهم الأيمن: سعي خمس ثوانٍ إلى الأمام. */
    data object SeekForward : ShortcutAction

    /** N: المقطع التالي في القائمة. */
    data object NextTrack : ShortcutAction

    /** P: المقطع السابق في القائمة. */
    data object PreviousTrack : ShortcutAction

    /** M: كتم/إلغاء كتم الموسيقى (نفس كتم وحدة المزج). */
    data object ToggleMute : ShortcutAction

    /** Alt+1..8: التنقل بين صفحات التطبيق الثمانية بالترتيب (v0.39.0). */
    data class SwitchTab(val index: Int) : ShortcutAction

    /** F1: فتح/إغلاق دليل الاختصارات (v0.39.0). */
    data object ShowShortcuts : ShortcutAction

    /** Ctrl+Z: تراجع سياقي — القائمة في الموسيقى/الاستوديو، السكريبت في صفحته (v0.39.0). */
    data object ContextualUndo : ShortcutAction

    /** Ctrl+Y أو Ctrl+Shift+Z: إعادة سياقية بنفس التوجيه (v0.39.0). */
    data object ContextualRedo : ShortcutAction
}

/**
 * حلّ المفتاح إلى فعل: بلا مفاتيح تعديل حتى لا نسرق اختصارات
 * النظام والتطبيق — استثناءات العُرف المحرراتي (v0.39.0):
 * Alt+رقم 1..8 = تبديل الصفحات، F1 = دليل الاختصارات، Ctrl+Z/Y = تراجع/إعادة
 * سياقيان. يعيد null للمفاتيح غير المربوطة.
 * المفاتيح الحرفية (N/P/M) تعمل بتخطيط لاتيني — وتخطيطات أخرى تُنتج رموز مفاتيح
 * مختلفة فتُهمَل بهدوء: سلوك قياسي لا يفسد شيء.
 */
fun resolveShortcut(
    key: Key, ctrl: Boolean, alt: Boolean, meta: Boolean, shift: Boolean = false
): ShortcutAction? {
    if (meta) return null
    if (ctrl) {
        return when {
            key == Key.Z && shift -> ShortcutAction.ContextualRedo
            key == Key.Z -> ShortcutAction.ContextualUndo
            key == Key.Y -> ShortcutAction.ContextualRedo
            else -> null
        }
    }
    if (alt) {
        return when (key) {
            Key.One -> ShortcutAction.SwitchTab(1)
            Key.Two -> ShortcutAction.SwitchTab(2)
            Key.Three -> ShortcutAction.SwitchTab(3)
            Key.Four -> ShortcutAction.SwitchTab(4)
            Key.Five -> ShortcutAction.SwitchTab(5)
            Key.Six -> ShortcutAction.SwitchTab(6)
            Key.Seven -> ShortcutAction.SwitchTab(7)
            Key.Eight -> ShortcutAction.SwitchTab(8)
            else -> null
        }
    }
    return when (key) {
        Key.F1 -> ShortcutAction.ShowShortcuts
        Key.Spacebar -> ShortcutAction.PlayPauseMusic
        Key.DirectionLeft -> ShortcutAction.SeekBack
        Key.DirectionRight -> ShortcutAction.SeekForward
        Key.N -> ShortcutAction.NextTrack
        Key.P -> ShortcutAction.PreviousTrack
        Key.M -> ShortcutAction.ToggleMute
        Key.One -> ShortcutAction.FireJingle(1)
        Key.Two -> ShortcutAction.FireJingle(2)
        Key.Three -> ShortcutAction.FireJingle(3)
        Key.Four -> ShortcutAction.FireJingle(4)
        Key.Five -> ShortcutAction.FireJingle(5)
        Key.Six -> ShortcutAction.FireJingle(6)
        Key.Seven -> ShortcutAction.FireJingle(7)
        Key.Eight -> ShortcutAction.FireJingle(8)
        Key.Nine -> ShortcutAction.FireJingle(9)
        else -> null
    }
}

/**
 * حارس الكتابة: عدّاد ذري لحقول النص المركّزة. حين يكون أي حقل نصي في بؤرة
 * الإدخال تُهمَل الاختصارات كليًا — فالمسافة والأرقام تُكتب في الحقل كالمعتاد
 * ولا يُطلق جينلٌ بينما يكتب المذيع نصّ السكريبت. العدّاد (لا علمًا منطقيًا)
 * صامد أمام ترتيب أحداث فقدان/اكتساب البؤرة بين حقلين متتاليين.
 */
object TextEditing {
    private val count = AtomicInteger()
    val active: Boolean get() = count.get() > 0
    internal fun enter() { count.incrementAndGet() }
    internal fun exit() { count.updateAndGet { if (it <= 0) 0 else it - 1 } }

    /** لأغراض الاختبار فقط: تصفير العدّاد بعد حالة اختبار تركت حقلًا مركّزًا. */
    internal fun reset() { count.set(0) }
}

/**
 * مراقِب التحرير: أضِفه إلى سلسلة المعدّلات على أي حقل نصي كي يسجّل بؤرته
 * في [TextEditing]. يُركَّب على كل حقول التطبيق (18 موضعًا) عبر الالتزام v0.29.0.
 */
fun Modifier.observesTextEditing(): Modifier = composed {
    onFocusChanged { if (it.hasFocus) TextEditing.enter() else TextEditing.exit() }
}

/**
 * معالج مفاتيح مستوى النافذة: ينفّذ الاختصار على [vm] ويعيد true إن استهلكه.
 * يُمرَّر إلى onPreviewKeyEvent في النافذة الرئيسية ونافذة الجدار معًا، فتعمل
 * الاختصارات من أي نافذة مركّزة. [guardTextEditing] صحيح في النوافذ ذات الحقول
 * النصية (الرئيسية) وخاطئ في نافذة الجدار بلا حقول.
 */
fun handleGlobalShortcut(vm: AppViewModel, event: KeyEvent, guardTextEditing: Boolean): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (guardTextEditing && TextEditing.active) return false
    // صفحة المونتاج تملك بروتوكولها الخاص (v0.34.0) — فلا مسافة تُطلق الموسيقى
    // ولا أرقام تُطلق جينلات والاستوديو بعيد، والعكس صحيح.
    if (vm.tab.value == Tab.MONTAGE) return handleMontageShortcut(vm, event)
    val action = resolveShortcut(
        event.key, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed, event.isShiftPressed
    ) ?: return false
    vm.applyShortcut(action)
    return true
}

// ================= اختصارات صفحة المونتاج (v0.34.0 — البند 10) =================
// مفتاح التحرير السريع: تشتغل حين صفحة المونتاج هي الحالية فقط فلا تسرق
// مسافة/الأسهم من الاستوديو، وتنطفئ آليًا في أي حقل نصي.

sealed interface MontageShortcutAction {
    /** مسافة: تشغيل/إيقاف المعاينة من موضع الرأس. */
    data object PreviewPlayPause : MontageShortcutAction

    /** S: تقسيم المقطع الحاوي للرأس عند الرأس. */
    data object SplitAtPlayhead : MontageShortcutAction

    /** Delete/Backspace: حذف المقطع المحدد (والخط الزمني يُغلق مكانَه). */
    data object DeleteSelected : MontageShortcutAction

    /** Ctrl+Z: تراجع عن آخر تعديل. */
    data object Undo : MontageShortcutAction

    /** Ctrl+Y أو Ctrl+Shift+Z: إعادة آخر تراجع. */
    data object Redo : MontageShortcutAction

    /** ←/→: تحريك رأس التشغيل — 100ms عاديًا و10ms مع Shift (دقة نتح). */
    data class PlayheadMove(val deltaMs: Long) : MontageShortcutAction

    /** ↑/↓: تحديد المقطع السابق/التالي. */
    data object SelectPrev : MontageShortcutAction

    /** ↑/↓: تحديد المقطع السابق/التالي. */
    data object SelectNext : MontageShortcutAction

    /** R: حذف المنطقة المحددة على المسطرة متموّجًا (v0.36.0). */
    data object DeleteRegion : MontageShortcutAction

    /** G: تفعيل/تعطيل التقاط السحب (v0.36.0). */
    data object ToggleSnap : MontageShortcutAction

    /** Escape: مسح تحديد المسطرة (v0.36.0). */
    data object ClearSelection : MontageShortcutAction

    /** X أو Ctrl+X: قص المحدد إلى حافظة المونتاج (v0.38.0). */
    data object CutSelected : MontageShortcutAction

    /** C أو Ctrl+C: نسخ المحدد إلى حافظة المونتاج (v0.38.0). */
    data object CopySelected : MontageShortcutAction

    /** V أو Ctrl+V: لصق الحافظة بمعرّف جديد (v0.38.0). */
    data object PasteClipboard : MontageShortcutAction

    /** D: تكرار المحدد مرة واحدة خلفه (v0.38.0) — العدد الأكبر من زر «تكرار…». */
    data object DuplicateSelected : MontageShortcutAction

    /** J: دمج المحدد مع التالي في مقطع واحد (v0.38.0). */
    data object MergeWithNext : MontageShortcutAction
}

/**
 * حلّ مفتاح صفحة المونتاج: مفاتيح حرة (مسافة/S/Delete/الأسهم) ومركّبة
 * (Ctrl+Z/Y) — يُحتكم إليها فقط حين المونتاج هي الصفحة الحالية.
 */
fun resolveMontageShortcut(
    key: Key, ctrl: Boolean, shift: Boolean, alt: Boolean, meta: Boolean
): MontageShortcutAction? {
    if (alt || meta) return null
    return when {
        key == Key.Spacebar && !ctrl && !shift -> MontageShortcutAction.PreviewPlayPause
        key == Key.S && !ctrl && !shift -> MontageShortcutAction.SplitAtPlayhead
        (key == Key.Delete || key == Key.Backspace) && !ctrl && !shift -> MontageShortcutAction.DeleteSelected
        key == Key.Z && ctrl && shift -> MontageShortcutAction.Redo
        key == Key.Z && ctrl && !shift -> MontageShortcutAction.Undo
        key == Key.Y && ctrl -> MontageShortcutAction.Redo
        key == Key.DirectionLeft && !ctrl -> MontageShortcutAction.PlayheadMove(if (shift) -10L else -100L)
        key == Key.DirectionRight && !ctrl -> MontageShortcutAction.PlayheadMove(if (shift) 10L else 100L)
        key == Key.DirectionUp && !ctrl && !shift -> MontageShortcutAction.SelectPrev
        key == Key.DirectionDown && !ctrl && !shift -> MontageShortcutAction.SelectNext
        key == Key.R && !ctrl && !shift -> MontageShortcutAction.DeleteRegion
        key == Key.G && !ctrl && !shift -> MontageShortcutAction.ToggleSnap
        key == Key.Escape -> MontageShortcutAction.ClearSelection
        // v0.38.0: الحافظة والتكرار والدمج — حرة أو بـCtrl للعُرف المحرراتي
        key == Key.X -> MontageShortcutAction.CutSelected
        key == Key.C && !shift -> MontageShortcutAction.CopySelected
        key == Key.V && !shift -> MontageShortcutAction.PasteClipboard
        key == Key.D && !shift -> MontageShortcutAction.DuplicateSelected
        key == Key.J && !shift -> MontageShortcutAction.MergeWithNext
        else -> null
    }
}

/**
 * معالج مفاتيح صفحة المونتاج — يُنفّذ على [vm] ويعيد true عند الاستهلاك.
 * يحرس التحرير النصي كالبروتوكول العام تمامًا.
 */
fun handleMontageShortcut(vm: AppViewModel, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val action = resolveMontageShortcut(
        event.key, event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, event.isMetaPressed
    ) ?: return false
    vm.applyMontageShortcut(action)
    return true
}

/**
 * دليل الاختصارات: حوار معلوماتي من زر «الاختصارات» في الشريط العلوي للاستوديو
 * المدمج — يعرض البروتوكول كاملًا بلا بحث ولا تجريب.
 */
@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "اختصارات لوحة المفاتيح",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "بروتوكول الاستوديو السريع — تعمل من أي نافذة في التطبيق حتى والجدار مغمور.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val rows = listOf(
                "مسافة" to "تشغيل / إيقاف مؤقت للموسيقى",
                "1 – 9" to "إطلاق أو إيقاف جينل الموضع (1 هو أول موضع في الجدار)",
                "→ / ←" to "سعي خمس ثوانٍ إلى الأمام / الخلف",
                "N / P" to "المقطع التالي / السابق في القائمة",
                "M" to "كتم أو إلغاء كتم الموسيقى",
                "Alt + 1 – 8" to "التنقل بين الصفحات الثمانية بالترتيب (v0.39.0)",
                "F1" to "فتح / إغلاق دليل الاختصارات هذا",
                "Ctrl+Z / Ctrl+Y" to "تراجع / إعادة سياقيان: القائمة في الموسيقى والاستوديو، السكريبت في صفحته"
            )
            rows.forEach { (keys, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        keys,
                        modifier = Modifier
                            .width(88.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge.numeric(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                "اختصارات صفحة المونتاج — تعمل حين تكون صفحة المونتاج مفتوحة:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            val montageRows = listOf(
                "مسافة" to "تشغيل / إيقاف المعاينة من موضع الرأس",
                "S" to "تقسيم المقطع عند موضع رأس التشغيل",
                "Delete" to "حذف المقطع المحدد ويُغلق الخط مكانه",
                "R" to "حذف المنطقة المحددة على المسطرة من كل المسارات متموّجًا",
                "G" to "تفعيل / تعطيل الالتقاط على العلامات والحواف والشبكة",
                "X / C / V" to "قص / نسخ / لصق المقطع المحدد عبر حافظة المونتاج",
                "D" to "تكرار المقطع المحدد مرة خلفه",
                "J" to "دمج المحدد مع التالي في مقطع واحد",
                "Esc" to "مسح تحديد المسطرة",
                "← / →" to "تحريك رأس التشغيل 100ms (مع Shift: 10ms بدقة)",
                "↑ / ↓" to "تحديد المقطع السابق / التالي",
                "Ctrl+Z / Ctrl+Y" to "تراجع / إعادة آخر تعديل"
            )
            montageRows.forEach { (keys, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        keys,
                        modifier = Modifier
                            .width(120.dp)
                            .background(
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge.numeric(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Text(
                "لا تعمل الاختصارات أثناء الكتابة في الحقول النصية — فبقاؤك تكتب حرًّا.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
