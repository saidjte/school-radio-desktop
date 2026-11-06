package com.saidjte.schoolradio.desktop.audio

import java.io.File

/**
 * قارئ مقطع وهمي لأتمتة الفحص البصري (v0.29.0 «--fake-track»): مقطع 10 دقائق
 * يتقدّم 250ms لكل كتلة بلا عتاد صوت ولا ملف حقيقي — يُفعَّل حصرًا عبر
 * trackReaderOverride من سطر الأوامر في Main.kt، وفي الإنتاج لا يُنشأ إطلاقًا.
 * النمط نفسه الذي بُيّن فيه FakeToneVoice لجدار الجينغل (v0.28.0).
 */
internal class FakeTrackVoice : TrackReader {
    @Volatile var current = 0L
    override val durationMs: Long = 600_000L
    override val currentMs: Long get() = current
    override fun seek(ms: Long) { current = ms.coerceIn(0L, durationMs) }
    override fun pause() { }
    override fun resume() { }
    override fun applyVolume(volume: Float) { }
    override fun readChunk(): Boolean {
        current += 250L
        Thread.sleep(2) // تنفّس كي لا يدور خيط التشغيل دورانًا محمومًا
        return true
    }
    override fun close() { }
}
