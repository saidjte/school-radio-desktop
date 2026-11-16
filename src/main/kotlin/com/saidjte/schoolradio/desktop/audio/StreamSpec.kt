package com.saidjte.schoolradio.desktop.audio

import java.io.Closeable
import javax.sound.sampled.AudioFormat

/**
 * مواصفة بث مباشر — بيانات نقية تُمرَّر من خدمة اليوتيوب إلى محرك الموسيقى
 * (v0.51.0 «تشغيل مباشر من يوتيوب دون تحميل» — نظير ما تفعله نسخة الهاتف:
 * ExoPlayer يبثّ الصوت من الرابط فورًا؛ هنا نفس الفكرة بقرّاء JVM الخالصة).
 */
data class StreamSpec(
    /** رابط الوسيط: وسيط واحد مباشر (fMP4/MP3) أو رابط قائمة تشغيل HLS. */
    val url: String,
    /** true = الرابط قائمة HLS تُفكّ إلى init + أجزاء، false = وسيط واحد مباشر. */
    val hls: Boolean,
    /** true = MPEG صوتي (MP3/Layer2) عبر JLayer، false = AAC داخل fMP4 عبر jaad. */
    val mp3: Boolean,
    /** ملف كوكيز اختياري (Netscape) لشبكات يطلب يوتيوب فيها تحققًا — مسار مطلق. */
    val cookiesPath: String? = null
)

/**
 * خط بثّ PCM: يفكّ الوسيط الحي كتلة تلو الأخرى — عقد موحّد فوق حاويتي البث
 * (fMP4/AAC عبر jaad وMPEG عبر JLayer). القارئ [StreamTrackReader] يستهلكه
 * ويكتب في خط السماع عبر قناة المزج نفسها التي تمر بها ملفات المكتبة، فتعمل
 * المعادل والفيدر والخفض والتسجيل والمقياس على البث المباشر بلا أي استثناء.
 */
internal interface StreamPipeline : Closeable {

    /** صيغة الخرج الفعلية — ثابتة بعد أول كتلة مفكوكة، null قبلها. */
    val format: AudioFormat?

    /** الكتلة التالية من PCM 16-bit لين-إندَيان، أو null عند النهاية/العطل. */
    fun nextBlock(): ShortArray?

    /** موضع التشغيل المفكوك حتى الآن بالمللي ثانية. */
    val currentMs: Long

    /** رسالة عربية لسبب التوقف إن كان عطلًا لا نهاية طبيعية (null عند النهاية السليمة). */
    val lastError: String?
}
