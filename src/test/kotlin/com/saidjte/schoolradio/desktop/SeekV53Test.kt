package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.audio.MusicChannel
import com.saidjte.schoolradio.desktop.audio.MusicPlayerEngine
import com.saidjte.schoolradio.desktop.audio.Mp3TrackReader
import com.saidjte.schoolradio.desktop.audio.PcmOutLine
import java.io.File
import javax.sound.sampled.AudioFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * عقد السعي الواقعي (v0.53.0 «السعي داخل المقطع الموسيقي يتسبب بتوقف المشغل وتوقف الموسيقى»):
 *
 * الجذر المُثبَت: القفزة البايتية في Mp3TrackReader.seek تُقدَّر من مجال
 * غير موثوق (VBR بلا جدول Xing يُقدَّر بترويسة الإطار الأول الأعلى من المتوسط،
 * وdataBytes يحسب البقايا التالية — غلاف/وسوم ملاحقة — صوتًا)، فتهبط القفزة
 * بعد الإطار الأخير. حلقة العد السريع تنكسر عند أول readFrame()=null والقارئ
 * يبقى متوقفًا عند EOF وهو يزعم أنه عند الهدف — فأول readChunk() يعيد false
 * فيعاملها المحرك نهاية مقطع كاذبة: _isPlaying=false ويرتحل العامل والموسيقى
 * تصمت والمشغل يقف وهو ما زال في وسط المقطع حسب المدة المعلنة.
 *
 * العقد بعد الإصلاح (بالمستوى الواحد الصحيح — داخل القارئ نفسه):
 * 1) السعي لا يترك القارئ أبدًا بلا إطار أول: إن لم يجد Bitstream أي إطار
 *    عند موضع القفزة يتراجع بالبايت (‎×⅞ ثم ×¾ ثم ×½ ثم ×¼ ثم الصفر) ويعيد،
 *    ثم يمشي سريعًا إلى الهدف المطلوب — والموضع النهائي صادق دائمًا.
 * 2) سعي عميق (85% من المدة المعلنة) في ملفات واقعية (VBR بلا Xing،
 *    وجدول Xing مع بقايا تالية) يكمل التشغيل ويصل النهاية الطبيعية —
 *    لا صمت مبكر ولا قفزة ولا توقف.
 * 3) على مستوى المحرك: مقطع مفرد بلا تالٍ، سعي عميق ⇒ يستمر الصوت و
 *    isPlaying يبقى صادقًا والموضع يتقدم — هذا هو بلاغ المستخدم نفسه.
 */
class SeekV53Test {

    private class MemoryLine : PcmOutLine {
        var bytes = 0L
        override fun open(format: AudioFormat, bufferSize: Int) { }
        override fun start() { }
        override fun stop() { }
        override fun flush() { }
        override fun write(b: ByteArray, off: Int, len: Int) { bytes += len }
        override fun setVolume(v: Float) { }
        override fun close() { }
    }

    /** انتظار شرط بمهلة — يفشل برسالة واضحة إن انقضت. */
    private fun await(message: String, timeoutMs: Long = 8_000L, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("انقضت المهلة: $message")
    }

    private fun fixture(name: String): File {
        val bytes = javaClass.classLoader.getResourceAsStream("formats/$name")?.readBytes()
            ?: throw AssertionError("العيّنة مفقودة: $name")
        val ext = name.substringAfterLast('.')
        val f = File.createTempFile("v53-", ".$ext")
        f.writeBytes(bytes)
        f.deleteOnExit()
        return f
    }

    // ===== 1) VDR بلا Xing: سعي عميق 85% يكمل — إعادة إنتاج بلاغ التوقف =====

    @Test
    fun `vbr without xing deep seek keeps playing instead of stopping`() {
        val f = fixture("mp3_vbr_noxing.mp3")
        val inf = Mp3Info.analyze(f) ?: throw AssertionError("فشل تحليل VDR بلا Xing")
        assertTrue(inf.durationMs > 10_000, "المدة المعلنة: ${inf.durationMs} isVbr=${inf.isVbr} toc=${inf.toc != null}")
        val r = Mp3TrackReader(f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() })
        try {
            val target = inf.durationMs * 85L / 100L
            r.seek(target)
            val first = r.readChunk()
            assertTrue(first, "أول كتلة بعد السعي العميق في VDR بلا Xing — كانت تنتهي كاذبة فتوقف المشغل (الموضع=${r.currentMs} من ${inf.durationMs})")
            assertTrue(r.currentMs >= target - 2_500 && r.currentMs <= target + 2_500,
                "الموضع قرب الهدف: ${r.currentMs} مقابل $target")
            // يتقدم ويصل النهاية الطبيعية — لا صمت مبكر
            var guard = 0
            while (r.readChunk() && guard < 500_000) guard++
            assertTrue(r.currentMs >= inf.durationMs - 3_000,
                "وصل النهاية الطبيعية: ${r.currentMs} من ${inf.durationMs}")
        } finally {
            r.close()
        }
    }

    // ===== 2) جدول Xing مع بقايا تالية: القفزة تهبط في الفخامة ويكمل =====

    @Test
    fun `xing table with trailing junk deep seek keeps playing`() {
        val f = fixture("mp3_xing_trailing.mp3")
        val inf = Mp3Info.analyze(f) ?: throw AssertionError("فشل تحليل Xing بالبقايا")
        assertTrue(inf.durationMs > 10_000, "المدة: ${inf.durationMs} toc=${inf.toc != null} dataBytes=${inf.dataBytes} واقعي=${f.length()}")
        assertTrue(inf.toc != null, "يجب أن يُكتشف جدول Xing في هذه العيّنة")
        val r = Mp3TrackReader(f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() })
        try {
            val target = inf.durationMs * 85L / 100L
            r.seek(target)
            val first = r.readChunk()
            assertTrue(first, "أول كتلة بعد السعي العميق مع بقايا تالية — كانت تهبط في الفخامة فتنتهي كاذبة (الموضع=${r.currentMs})")
            assertTrue(r.currentMs >= target - 2_500 && r.currentMs <= target + 2_500,
                "الموضع قرب الهدف: ${r.currentMs} مقابل $target")
            var guard = 0
            while (r.readChunk() && guard < 500_000) guard++
            assertTrue(r.currentMs >= inf.durationMs - 3_000,
                "وصل النهاية الطبيعية: ${r.currentMs} من ${inf.durationMs}")
        } finally {
            r.close()
        }
    }

    // ===== 3) المحرك: مقطع مفرد، سعي عميق ⇒ الصوت يستمر — بلاغ المستخدم نفسه =====

    @Test
    fun `engine single track deep seek keeps music playing`() {
        val eng = MusicPlayerEngine()
        eng.crossfadeMs = 3_000L
        val f = fixture("mp3_xing_trailing.mp3") // امتداد منقط — المُوجّه يصنف بالامتداد
        // القارئ الحقيقي نفسه (مسار السعي الفعلي) بخط ذاكرة — بيئات بلا بطاقة صوت
        eng.trackReaderOverride = { file ->
            Mp3TrackReader(file, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() })
        }
        var ended = false
        eng.onEnded = { ended = true }
        try {
            eng.play(f)
            await("المقطع حمل") { eng.hasTrack.value && eng.isPlaying.value }
            val dur = eng.durationMs.value
            assertTrue(dur > 10_000, "المدة المعلنة بالمحرك: $dur")
            val target = dur * 85L / 100L
            eng.seek(target)
            await("السعي أُرضي") { eng.positionMs.value >= target - 2_500 }
            // بعد السعي مباشرة الكتل تُستهلك والموضع يتجاوز الهدف — لا صمت فوري
            // (خط الذاكرة بلا ساعة حائطة فاستهلاك الباقي كله أسرع من الزمن الحقيقي)
            await("التقدم بعد السعي") { eng.positionMs.value > target + 200 }
            await("الانتهاء") { !eng.isPlaying.value }
            assertTrue(ended, "انتهى بعقد الانتهاء الطبيعي")
            assertTrue(eng.positionMs.value >= dur - 1_000,
                "النهاية عند المدة الكاملة لا توقف مبكر وسط المقطع (بلاغ «توقف المشغل وتوقف الموسيقى»): pos=${eng.positionMs.value} من $dur")
        } finally {
            eng.trackReaderOverride = null
            eng.onEnded = null
            eng.stop()
            eng.awaitIdle()
        }
    }

    // ===== 4) السعي العميق قرب النهاية المعلنة ينتهي نظيفًا (لا استثناء ولا تعليق) =====

    @Test
    fun `deep seek near reported end ends cleanly`() {
        val f = fixture("mp3_vbr_noxing.mp3")
        val info = Mp3Info.analyze(f)!!
        val r = Mp3TrackReader(f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() })
        try {
            r.seek(info.durationMs - 500L)
            var guard = 0
            while (r.readChunk() && guard < 500_000) guard++
            assertTrue(guard < 500_000, "النهاية بلُغت نظيفة بعد سعي قرب المعلن")
        } finally {
            r.close()
        }
    }

    // ===== 5) سعي وسط المقطع على VDR بلا Xing يظل دقيقًا (لا تراجع زائد) =====

    @Test
    fun `vbr noxing middle seek stays accurate`() {
        val f = fixture("mp3_vbr_noxing.mp3")
        val info = Mp3Info.analyze(f)!!
        val r = Mp3TrackReader(f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() })
        try {
            val target = info.durationMs / 2
            r.seek(target)
            assertTrue(r.readChunk(), "التشغيل يكمل بعد سعي المنتصف")
            assertTrue(r.currentMs >= target - 2_500 && r.currentMs <= target + 2_500,
                "دقة المنتصف: ${r.currentMs} مقابل $target")
        } finally {
            r.close()
        }
    }

    // ===== 6) مسار المستخدم الفعلي: شغّل ~650ms ثم سعي عميق ثم فكّ متواصل للنهاية =====
    // (ما لم تغطه اختبارات v0.52: كانت تسعٍ من فتح بارد — والمستخدم يسحب الشريط
    // وهو يستمع، فالمفكك حامل لحالته عند السعي. المسبار أثبت: 35 من أول 50 كتلة
    // ما بعد السعي تفشل صامتًا ثم يتجمّد الموضع ثم نهاية كاذبة عند +430ms.)

    @Test
    fun `decode then deep seek keeps decoding to the natural end`() {
        val f = fixture("mp3_xing_trailing.mp3")
        val inf = Mp3Info.analyze(f) ?: throw AssertionError("فشل التحليل")
        val r = Mp3TrackReader(f, null, null, { 1f }, null, MusicChannel(), outLineFactory = { MemoryLine() })
        try {
            var n = 0
            while (n < 25 && r.readChunk()) n++ // 653ms فك فعلي قبل السعي — كما يفعل المستخدم
            assertEquals(25, n, "الافتتاح فكّ كتل حقيقية")
            r.seek(inf.durationMs * 85L / 100L)
            // بعد الإصلاح كل كتلة تفكّ فعلًا والموضع يتقدم — لا تجميد ولا فشل صامت
            var stalls = 0
            var prev = -1L
            var guard = 0
            while (r.readChunk() && guard < 500_000) {
                guard++
                val p = r.currentMs
                stalls = if (p == prev) stalls + 1 else 0
                prev = p
                assertTrue(stalls < 10, "الفك تجمّد عند $p من ${inf.durationMs} — فكّ مكسور بعد السعي")
            }
            assertTrue(r.currentMs >= inf.durationMs - 3_000,
                "النهاية الطبيعية بعد فكّ متواصل: ${r.currentMs} من ${inf.durationMs}")
        } finally {
            r.close()
        }
    }
}
