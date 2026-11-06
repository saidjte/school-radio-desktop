package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MusicChannel
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.TrackReader
import com.saidjte.schoolradio.desktop.ui.ShortcutAction
import com.saidjte.schoolradio.desktop.ui.TextEditing
import com.saidjte.schoolradio.desktop.ui.resolveShortcut
import androidx.compose.ui.input.key.Key
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * عقد الاختصارات الاحترافية والتلاشٍ الناعم (v0.29.0):
 * - الحلّ النقي: كل مفتاح يقوم بفعله، ومفاتيح التعديل تمرّ سالمة، وغير المربوط يسكت.
 * - حارس الكتابة: عدّاد ذري لا يعلق ولا ينفد مهما تقلبت أحادياث البؤرة.
 * - غلاف التلاشٍ: قيم على ساعة الحائط تبدأ من القيمة الحالية وتصل الهدف تمامًا.
 * - عقد المحرك: الإيقاف المؤقت ينتظر خروجًا ناعمًا (350ms) قبل تجميد القارئ،
 *   والاستئناف يصعد ناعمًا، والسعي أثناء التلاشي يجهضه فورًا — بلا عتاد صوت.
 */
class ShortcutsAndFadeTest {

    // ================= الحلّ النقي للاختصارات =================

    private fun r(key: Key, ctrl: Boolean = false, alt: Boolean = false, meta: Boolean = false) =
        resolveShortcut(key, ctrl, alt, meta)

    @Test
    fun `every shortcut key resolves to its action`() {
        assertEquals(ShortcutAction.PlayPauseMusic, r(Key.Spacebar))
        assertEquals(ShortcutAction.FireJingle(1), r(Key.One))
        assertEquals(ShortcutAction.FireJingle(5), r(Key.Five))
        assertEquals(ShortcutAction.FireJingle(9), r(Key.Nine))
        assertEquals(ShortcutAction.SeekBack, r(Key.DirectionLeft))
        assertEquals(ShortcutAction.SeekForward, r(Key.DirectionRight))
        assertEquals(ShortcutAction.NextTrack, r(Key.N))
        assertEquals(ShortcutAction.PreviousTrack, r(Key.P))
        assertEquals(ShortcutAction.ToggleMute, r(Key.M))
    }

    @Test
    fun `modifier combos pass through untouched`() {
        assertNull(r(Key.Spacebar, ctrl = true))
        assertNull(r(Key.M, alt = true))
        assertNull(r(Key.N, meta = true))
        assertNull(r(Key.One, ctrl = true, alt = true))
    }

    @Test
    fun `unbound keys stay silent`() {
        assertNull(r(Key.A))
        assertNull(r(Key.Escape))
        assertNull(r(Key.F5))
        assertNull(r(Key.Unknown))
    }

    // ================= حارس الكتابة =================

    @Test
    fun `text editing counter survives focus order churn`() {
        TextEditing.reset()
        assertFalse(TextEditing.active)
        TextEditing.enter()
        TextEditing.enter()
        TextEditing.exit()
        assertTrue(TextEditing.active, "حقل ما زال مركزًا — يجب أن يبقى الحارس مفعّلًا")
        TextEditing.exit()
        assertFalse(TextEditing.active)
        TextEditing.exit() // انخفاض زائد لا يجعل العدّاد سالبًا
        assertFalse(TextEditing.active)
        TextEditing.enter()
        assertTrue(TextEditing.active)
        TextEditing.reset()
        assertFalse(TextEditing.active)
    }

    // ================= غلاف التلاشٍ على القناة =================

    @Test
    fun `fade envelope ramps from current value to target on wall clock`() {
        val ch = MusicChannel()
        assertEquals(1f, ch.currentEnv(), "بلا تلاشٍ: الكسب الكامل")
        ch.beginFade(0f, 100)
        Thread.sleep(40)
        val mid = ch.currentEnv()
        assertTrue(mid in 0.01f..0.95f) { "منتصف التلاشٍ=$mid" }
        Thread.sleep(120)
        assertEquals(0f, ch.currentEnv(), "نهاية التلاشٍ: صمت تام")
        // صعود من الصفر إلى الكامل
        ch.beginFade(1f, 80)
        Thread.sleep(140)
        assertEquals(1f, ch.currentEnv(), "الصعود يكمل إلى الكامل")
        // من القيمة الحالية لا من 1 دائمًا
        ch.beginFade(0.5f, 40)
        Thread.sleep(90)
        assertEquals(0.5f, ch.currentEnv(), "الهدف الوسيط يثبت")
        ch.beginFade(1f, 0) // مدة صفرية = فوري
        assertEquals(1f, ch.currentEnv())
        ch.resetEnv()
        assertEquals(1f, ch.currentEnv())
    }

    // ================= عقد المحرك مع التلاشٍ =================

    /** قارئ مزيف يمرّ صوتًا حقيقي الصيغة عبر قناة المزج كي يسري غلاف التلاشٍ فعلًا. */
    private class AudioProbe(private val ch: MusicChannel) : TrackReader {
        @Volatile var current = 0L
        @Volatile var pauseCalls = 0
        @Volatile var resumeCalls = 0
        @Volatile var seekCount = 0
        override val durationMs: Long = 600_000L
        override val currentMs: Long get() = current
        override fun seek(ms: Long) { seekCount++; current = ms.coerceIn(0L, durationMs) }
        override fun pause() { pauseCalls++ }
        override fun resume() { resumeCalls++ }
        override fun applyVolume(volume: Float) { }
        override fun readChunk(): Boolean {
            val n = 2048
            val s = ShortArray(n)
            for (i in 0 until n) {
                s[i] = (sin(2.0 * PI * 440.0 * (current * 24L + i) / 24_000.0) * 12000).toInt().toShort()
            }
            ch.processShorts(s, n)
            current += 21L // ≈2048/96000 إطارًا بزمن 24kHz مزدوج القناة
            Thread.sleep(1)
            return true
        }
        override fun close() { }
    }

    private fun await(message: String, timeoutMs: Long = 5_000L, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("انقضت المهلة: $message")
    }

    @Test
    fun `pause fades out before freezing the reader and resume fades back in`() {
        val eng = MusicPlayerEngine()
        val probe = AudioProbe(eng.channel)
        eng.trackReaderOverride = { probe }
        try {
            eng.play(File("selftest/fade-track.wav"))
            await("hasTrack") { eng.hasTrack.value }
            await("audible") { eng.level.value > 0.03f }
            val t0 = System.currentTimeMillis()
            eng.pause()
            await("paused state") { eng.isPaused.value }
            // الإيقاف يُعلن فورًا في الواجهة لكن القارئ يتجمد بعد الخروج الناعم فقط
            await("worker paused after fade") { probe.pauseCalls == 1 }
            val elapsed = System.currentTimeMillis() - t0
            assertTrue(elapsed >= 250L) { "الإيقاف يجب أن ينتظر التلاشٍ: $elapsed" }
            assertEquals(0f, eng.channel.currentEnv(), "الغلاف صامت بعد الخروج الناعم")
            val frozen = eng.positionMs.value
            Thread.sleep(150)
            assertEquals(frozen, eng.positionMs.value, "الموضع متجمّد بعد اكتمال التلاشٍ")
            // الاستئناف: صعود ناعم ثم صوت كامل
            eng.resume()
            await("resume called") { probe.resumeCalls == 1 }
            await("audible again") { eng.level.value > 0.03f }
            Thread.sleep(400)
            assertEquals(1f, eng.channel.currentEnv(), "الغلاف كامل بعد الصعود الناعم")
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `seek during fade aborts it immediately and the reader pauses fast`() {
        val eng = MusicPlayerEngine()
        val probe = AudioProbe(eng.channel)
        eng.trackReaderOverride = { probe }
        try {
            eng.play(File("selftest/fade-track2.wav"))
            await("hasTrack") { eng.hasTrack.value }
            await("audible") { eng.level.value > 0.03f }
            val t0 = System.currentTimeMillis()
            eng.pause()
            Thread.sleep(30) // التلاشٍ في منتصفه تقريبًا
            eng.seek(120_000L)
            assertEquals(120_000L, eng.positionMs.value, "العرض المتفائل للسعي لا يتأثر بالتلاشٍ")
            await("worker paused") { probe.pauseCalls == 1 }
            val elapsed = System.currentTimeMillis() - t0
            assertTrue(elapsed < 330L) { "السعي يجهض التلاشٍ فورًا بدل انتظاره: $elapsed" }
            await("seek executed") { probe.seekCount > 0 }
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }
}
