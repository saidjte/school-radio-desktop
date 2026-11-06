package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.TrackReader
import java.io.File
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * عقد السعي في مشغل الموسيقى (v0.27.0 «شريط التقدم بالنقر والسحب لا يعمل جيدًا»):
 * - النقر/السحب على شريط التقدم يستدعي seek(mil) — فيجب أن يعرض الشريط والزمن
 *   الموضع الجديد من لحظة الإفلات (تحديث متفائل)، لا الموضع القديم.
 * - السعي أثناء الإيقاف المؤقت يجب أن يُنفَّذ في القارئ ويبقى العرض على الموضع
 *   الجديد (كان الارتداد للقديم هو العطل الجذري: readChunk لا يعمل أثناء الإيقاف).
 * - السعي بلا مقطع محمّل لا يفعل شيئًا.
 * كل ذلك بقارئ مزيف عبر خط الحقن — بلا عتاد صوت، والعامل الحقيقي يعمل فعلًا.
 */
class MusicPlayerEngineSeekTest {

    /** قارئ مزيف: يسجّل السعي، يتقدّم زمنه مع كل كتلة، ويكتب في الذاكرة فقط. */
    private class FakeReader : TrackReader {
        val seekCalls = mutableListOf<Long>()
        @Volatile var current = 0L
        @Volatile var pauseCalls = 0
        @Volatile var resumeCalls = 0
        override val durationMs: Long = 600_000L
        override val currentMs: Long get() = current
        override fun seek(ms: Long) {
            seekCalls += ms
            current = ms.coerceIn(0L, durationMs)
        }
        override fun pause() { pauseCalls++ }
        override fun resume() { resumeCalls++ }
        override fun applyVolume(volume: Float) { }
        override fun readChunk(): Boolean {
            current += 250L // 250ms زمنية لكل كتلة
            Thread.sleep(2) // تنفّس للخيط كي لا يدور دورانًا محمومًا
            return true
        }
        override fun close() { }
    }

    /** انتظار شرط بمهلة — يفشل برسالة واضحة إن انقضت. */
    private fun await(message: String, timeoutMs: Long = 5_000L, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("انقضت المهلة: $message")
    }

    /** محرك بقارئ مزيف محقون — والملف وهمي لأن openReader يُتجاوَز كليًا. */
    private fun engineWithFake(fake: FakeReader): MusicPlayerEngine {
        val eng = MusicPlayerEngine()
        eng.trackReaderOverride = { fake }
        eng.play(File("selftest/fake-track.mp3"))
        await("hasTrack") { eng.hasTrack.value }
        return eng
    }

    @Test
    fun `seek without a loaded track is a no-op`() {
        val eng = MusicPlayerEngine()
        eng.seek(30_000L)
        assertEquals(0L, eng.positionMs.value, "no track: position untouched")
    }

    @Test
    fun `seek while playing displays the target immediately then grounds to reader`() {
        val fake = FakeReader()
        val eng = engineWithFake(fake)
        try {
            await("position ticking") { eng.positionMs.value > 0L }
            eng.seek(30_000L)
            // التحديث المتفائل: العرض عند الهدف فورًا من نفس اللحظة —
            // قبل الإصلاح بقي الموضع القديم حتى يستهلك العامل الطلب (وميض ارتدادي)
            val shown = eng.positionMs.value
            assertTrue(shown in 30_000L..31_500L) { "immediate display=$shown" }
            // ثم الترسيخ: العامل نفّذ السعي فعليًا والعرض متطابق مع القارئ
            await("worker executed seek") { fake.seekCalls.contains(30_000L) }
            await("display grounded") { eng.positionMs.value >= 30_000L }
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `seek while paused moves displayed position and keeps it (root fix)`() {
        val fake = FakeReader()
        val eng = engineWithFake(fake)
        try {
            await("position ticking") { eng.positionMs.value > 0L }
            val posBefore = eng.positionMs.value
            eng.pause()
            await("paused state") { eng.isPaused.value }
            // نبقى قليلًا في الإيقاف حتى يستقر العامل على فرع الإيقاف
            Thread.sleep(120)
            eng.seek(45_000L)
            // العرض الفوري عند الهدف
            assertEquals(45_000L, eng.positionMs.value, "immediate display while paused")
            // الارتداد القديم: العرض كان يعود للموضع قبل السعي لأن readChunk
            // لا يعمل أثناء الإيقاف — الآن يجب أن يثبت عند الهدف
            repeat(25) {
                Thread.sleep(20)
                assertEquals(45_000L, eng.positionMs.value, "stays at target (tick $it)")
            }
            // والعامل نفّذ السعي في القارئ فعلًا حتى مع الإيقاف
            await("worker executed paused seek") { fake.seekCalls.contains(45_000L) }
            assertTrue(posBefore < 45_000L) { "sanity posBefore=$posBefore" }
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `rapid seeks keep the last target displayed`() {
        val fake = FakeReader()
        val eng = engineWithFake(fake)
        try {
            await("position ticking") { eng.positionMs.value > 0L }
            eng.seek(1_000L)
            eng.seek(5_000L)
            await("last seek executed") { fake.seekCalls.lastOrNull() == 5_000L }
            assertTrue(eng.positionMs.value >= 5_000L) { "display=${eng.positionMs.value}" }
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    @Test
    fun `pause and resume still work with the seek display contract`() {
        val fake = FakeReader()
        val eng = engineWithFake(fake)
        try {
            await("position ticking") { eng.positionMs.value > 0L }
            eng.pause()
            await("paused") { eng.isPaused.value }
            // v0.29.0: الخروج الناعم يؤخر تجميد القارئ ~350ms — ننتظر اكتماله
            // قبل قياس الثبات (عقد الثبات نفسه: الموضع يتجمد في الإيقاف المؤقت)
            await("worker paused after fade") { fake.pauseCalls > 0 }
            // نُمه قليلًا كتهبط الكتلة قيد التنفيذ قبل تجميد القيمة المرجعية
            Thread.sleep(150)
            val frozen = eng.positionMs.value
            Thread.sleep(120)
            assertEquals(frozen, eng.positionMs.value, "position frozen while paused")
            eng.resume()
            await("resumed ticking") { eng.positionMs.value > frozen }
        } finally {
            eng.trackReaderOverride = null
            eng.stop()
            eng.awaitIdle()
        }
    }
}
