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
import com.saidjte.schoolradio.desktop.AppViewModel
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
}

/**
 * حلّ المفتاح إلى فعل: بلا مفاتيح تعديل (Ctrl/Alt/Meta) حتى لا نسرق اختصارات
 * النظام والتطبيق. يعيد null للمفاتيح غير المربوطة.
 * المفاتيح الحرفية (N/P/M) تعمل بتخطيط لاتيني — وتخطيطات أخرى تُنتج رموز مفاتيح
 * مختلفة فتُهمَل بهدوء: سلوك قياسي لا يفسد شيء.
 */
fun resolveShortcut(key: Key, ctrl: Boolean, alt: Boolean, meta: Boolean): ShortcutAction? {
    if (ctrl || alt || meta) return null
    return when (key) {
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
    val action = resolveShortcut(
        event.key, event.isCtrlPressed, event.isAltPressed, event.isMetaPressed
    ) ?: return false
    vm.applyShortcut(action)
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
                "M" to "كتم أو إلغاء كتم الموسيقى"
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
                "لا تعمل الاختصارات أثناء الكتابة في الحقول النصية — فبقاؤك تكتب حرًّا.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
