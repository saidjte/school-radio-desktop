package com.saidjte.schoolradio.desktop.audio

import java.io.File

/**
 * قارئ مقطع وهمي لأتمتة الفحص البصري (v0.29.0 «--fake-track»): مقطع 10 دقائق
 * يتقدّم 250ms لكل كتلة بلا عتاد صوت ولا ملف حقيقي — يُفعَّل حصرًا عبر
 * trackReaderOverride من سطر الأوامر في Main.kt، وفي الإنتاج لا يُنشأ إطلاقًا.
 * النمط نفسه الذي بُيّن فيه FakeToneVoice لجدار الجينغل (v0.28.0).
 */
internal class FakeTrackVoice(
    private val totalMs: Long = 600_000L,
    private val chunkSleepMs: Long = 2L
) : TrackReader {
    @Volatile var current = 0L
    override val durationMs: Long get() = totalMs
    override val currentMs: Long get() = current
    override fun seek(ms: Long) { current = ms.coerceIn(0L, totalMs) }
    override fun pause() { }
    override fun resume() { }
    override fun applyVolume(volume: Float) { }
    override fun readChunk(): Boolean {
        current += 250L
        Thread.sleep(chunkSleepMs) // تنفّس كي لا يدور خيط التشغيل دورانًا محمومًا
        return current < totalMs
    }
    override fun close() { }
}
