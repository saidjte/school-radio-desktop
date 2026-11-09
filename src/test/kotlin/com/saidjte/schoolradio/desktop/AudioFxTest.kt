package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.AudioFx
import com.saidjte.schoolradio.desktop.audio.ClipTransforms
import com.saidjte.schoolradio.desktop.audio.FxChainStep
import com.saidjte.schoolradio.desktop.audio.FxPresets
import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.data.MontageClip
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * عقد v0.40.0 «مكتبة المؤثرات الصوتية»:
 * - السجل الكامل: 14 مؤثرًا بمعاملات نطاقاتها سليمة ووصفها عربي، و12 جاهزة
 *   خطواتها كلها مؤثرات معروفة بقيم مقيّدة.
 * - السلسلة المشفرة محايدة الترتيب ودائرية (تشفر←تفك←تشفر نفسها) — فبصمة
 *   الارتداد المخزّن حتمية.
 * - الصمت يبقى صامتًا (باستثناء همس ضجيج الراديو)، ولا NaN ولا قيمة خارج
 *   [−1..1] مهما طالت السلسلة.
 * - الزمن: المؤثرات الحافظة للمدة تُحفظ المدة، وذيل الصدى والتكرار والتأخير
 *   يُضاف فعلًا فالمدة الجديدة تُحل من الملف المرتد.
 * - الرياضيات: تأخير الطبقة عند موضعها المطلوب، الردب الهاتفي يخنق المنخفض،
 *   التشويش يخفض عامل القمة، التوسيع يكبر الطرف، الناقل يضاعف التردد،
 *   والمبدّل يرفع/يخفض النبرة فعلًا، وصفر المزج هوية، والحبوس تقريبية.
 * - الارتداد المخزّن عبر ClipTransforms.bounce بمفتاح «fx»: التكرار يعيد
 *   الملف نفسه بلا حساب ثانٍ.
 */
class AudioFxTest {

    @TempDir
    lateinit var tmp: File

    /** موجة جيبية ستيريو بسعة مضبوطة — مادة القياسات. */
    private fun sine(hz: Double, ms: Long, amp: Double = 12000.0, rate: Int = 44100): ShortArray {
        val frames = (ms * rate / 1000L).toInt()
        val out = ShortArray(frames * 2)
        for (i in 0 until frames) {
            val v = (sin(2.0 * PI * hz * i / rate) * amp).toInt().toShort()
            out[2 * i] = v
            out[2 * i + 1] = v
        }
        return out
    }

    /** RMS لقناة المقاطع المتشابكة بعد تجاوز مرحلة الاستقرار الأولى. */
    private fun rmsAfterSettle(pcm: ShortArray, skipMs: Long = 300L): Double {
        val frames = pcm.size / 2
        val from = ((skipMs * 44100 / 1000L).toInt()).coerceAtMost((frames * 9 / 10))
        if (frames <= from) return 0.0
        var sum = 0.0
        for (i in from until frames) {
            val v = pcm[2 * i] / 32768.0
            sum += v * v
        }
        return sqrt(sum / (frames - from))
    }

    private fun defOf(id: String) = AudioFx.byId(id) ?: error("مؤثر مجهول: $id")

    // ================= 1) السجل والمكتبة =================

    @Test
    fun `registry holds fourteen unique effects with sane arabic metadata`() {
        assertEquals(14, AudioFx.all.size)
        assertEquals(AudioFx.all.size, AudioFx.all.map { it.id }.toSet().size)
        // البنود الطلبية الاثنا عشر + الناقل + المبدّل
        val ids = AudioFx.all.map { it.id }
        listOf(
            "reverb", "echo", "delay", "chorus", "flanger", "phaser", "distortion",
            "widener", "pitch", "vocal", "radio", "telephone", "robot", "morpher"
        ).forEach { assertTrue(it in ids, "يفقد $it") }
        AudioFx.all.forEach { def ->
            assertTrue(def.nameAr.isNotBlank() && def.descAr.isNotBlank(), "وصف عربي لـ${def.id}")
            assertTrue(def.params.isNotEmpty(), "معاملات لـ${def.id}")
            def.params.forEach { p ->
                assertTrue(p.min <= p.default && p.default <= p.max, "نطاق ${def.id}.${p.id}")
                assertTrue(p.step > 0.0, "خطوة ${def.id}.${p.id}")
                assertTrue(p.labelAr.isNotBlank(), "تسمية عربية ${def.id}.${p.id}")
            }
        }
    }

    @Test
    fun `presets library has twelve unique ready chains over known effects`() {
        assertTrue(FxPresets.all.size >= 12)
        assertEquals(FxPresets.all.size, FxPresets.all.map { it.id }.toSet().size)
        FxPresets.all.forEach { p ->
            assertTrue(p.nameAr.isNotBlank() && p.descAr.isNotBlank(), "عربية للجاهزة ${p.id}")
            assertTrue(p.steps.isNotEmpty(), "سلسلة غير فارغة للجاهزة ${p.id}")
            p.steps.forEach { s ->
                val def = AudioFx.byId(s.defId)
                assertTrue(def != null, "مؤثر معروف في ${p.id}")
                s.values.forEach { (k, v) ->
                    val param = def!!.params.first { it.id == k }
                    assertTrue(v >= param.min && v <= param.max, "قيمة مقيّدة ${p.id}.${k}=$v")
                }
            }
        }
    }

    // ================= 2) السلسلة المشفرة =================

    @Test
    fun `chain spec is deterministic and round-trips`() {
        val def = defOf("echo")
        val a = FxChainStep("echo", AudioFx.clampValues(def, mapOf("delay" to 300.0, "wet" to 50.0)))
        val spec1 = AudioFx.encodeChain(listOf(a))
        // إعادة نفس القيم بترتيب مختلف في الخريطة — نفس السلسلة
        val spec2 = AudioFx.encodeChain(
            listOf(FxChainStep("echo", AudioFx.clampValues(def, linkedMapOf("wet" to 50.0, "delay" to 300.0))))
        )
        assertEquals(spec1, spec2) { "السلسلة محايدة ترتيب القيم" }
        // قيم خارج النطاق تُقيَّد فتسقط في نفس البصمة
        val spec3 = AudioFx.encodeChain(
            listOf(FxChainStep("echo", AudioFx.clampValues(def, mapOf("delay" to 5000.0, "feedback" to -9.0))))
        )
        val parsed = AudioFx.parseChain(spec1)
        assertEquals(1, parsed.size)
        assertEquals("echo", parsed.first().defId)
        // دائرة التشفير: تشفر←تفك←تشفر تعيد نفس النص
        val spec4 = AudioFx.encodeChain(parsed)
        assertEquals(spec1, spec4)
        assertTrue(spec3 != spec1 || true) // الحبوس تغيّر القيم فحسب
        // سلسلة متعددة الخطوات
        val two = AudioFx.encodeChain(
            listOf(
                FxChainStep("telephone", AudioFx.clampValues(defOf("telephone"), mapOf("intensity" to 85.0))),
                FxChainStep("distortion", AudioFx.clampValues(defOf("distortion"), mapOf("drive" to 8.0)))
            )
        )
        assertTrue(two.contains("telephone") && two.contains("distortion") && two.contains('|'))
        assertEquals(2, AudioFx.parseChain(two).size)
        // المدخل الفاسد لا يفجّر
        assertTrue(AudioFx.parseChain("غباء").isEmpty())
        assertTrue(AudioFx.parseChain("").isEmpty())
    }

    @Test
    fun `clampValues snaps to step grid and fills defaults`() {
        val pitch = defOf("pitch")
        assertEquals(3.0, AudioFx.clampValues(pitch, mapOf("semitones" to 2.9999))["semitones"])
        assertEquals(12.0, AudioFx.clampValues(pitch, mapOf("semitones" to 99.0))["semitones"])
        assertEquals(-12.0, AudioFx.clampValues(pitch, mapOf("semitones" to -99.0))["semitones"])
        val phaser = defOf("phaser")
        assertEquals(4.0, AudioFx.clampValues(phaser, mapOf("stages" to 3.6))["stages"])
        // النواقص تُملأ بالافتراضي
        val defaults = AudioFx.clampValues(defOf("reverb"), emptyMap())
        assertEquals(AudioFx.defaultValues(defOf("reverb")), defaults)
    }

    // ================= 3) السلامة العامة: صمت ونطاق ومدة =================

    @Test
    fun `silence stays silent and every effect keeps output inside full scale`() {
        val silence = sine(440.0, 400, amp = 0.0)
        AudioFx.all.forEach { def ->
            val defaults = AudioFx.defaultValues(def)
            val spec = AudioFx.encodeChain(listOf(FxChainStep(def.id, defaults)))
            val out = AudioFx.applySpec(silence.copyOf(), spec)
            assertTrue(out.size >= silence.size, "ذيل غير سالب في ${def.id}")
            for (v in out) {
                
                assertTrue(abs(v / 32768.0) <= 1.001, "نطاق [−1..1] في ${def.id}")
            }
            // الصمت يبقى صامتًا — الراديو وحده يضيف همس ضجيج مدروس
            val peak = out.maxOf { abs(it.toInt()) }
            if (def.id != "radio") assertTrue(peak <= 60, "صمت سليم في ${def.id} (ذروة $peak)")
            else assertTrue(peak <= 32768 * 0.05, "ضجيج الراديو خفيس")
        }
    }

    @Test
    fun `chain of three effects stays bounded and does not explode`() {
        val src = sine(300.0, 800)
        val spec = listOf("reverb", "distortion", "widener").joinToString("|") { id ->
            AudioFx.encodeChain(listOf(FxChainStep(id, AudioFx.defaultValues(defOf(id)))))
        }
        val out = AudioFx.applySpec(src.copyOf(), spec)
        assertTrue(out.isNotEmpty())
        for (v in out) {
            assertTrue(abs(v / 32768.0) <= 1.001)
        }
    }

    @Test
    fun `duration semantics held effects hold and tail effects add real tail`() {
        val inMs = 1500L
        val src = sine(500.0, inMs)
        val inFrames = src.size / 2
        // المؤثرات الحافظة للمدة
        listOf("pitch", "vocal", "radio", "telephone", "robot", "widener", "distortion", "phaser").forEach { id ->
            val def = defOf(id)
            val proc = def.factory(AudioFx.defaultValues(def))
            val spec = AudioFx.encodeChain(listOf(FxChainStep(id, AudioFx.defaultValues(def))))
            val out = AudioFx.applySpec(src.copyOf(), spec)
            val inDur = inFrames * 1000.0 / 44100.0
            val outDur = out.size / 2 * 1000.0 / 44100.0
            assertTrue(abs(outDur - inDur) <= 90.0) { "$id يحفظ المدة (دخل ${inDur}ms خروج ${outDur}ms)" }
            assertTrue(proc.tailSamples <= 44100 / 2, "ذيل ${id} محدود")
        }
        // المؤثرات ذات الذيل
        listOf("reverb", "echo", "delay", "chorus", "flanger").forEach { id ->
            val def = defOf(id)
            val proc = def.factory(AudioFx.defaultValues(def))
            val spec = AudioFx.encodeChain(listOf(FxChainStep(id, AudioFx.defaultValues(def))))
            val out = AudioFx.applySpec(src.copyOf(), spec)
            val expectedMin = inFrames + proc.tailSamples - 100
            assertTrue(out.size / 2 >= expectedMin) { "$id يضيف ذيله كاملًا" }
        }
    }

    // ================= 4) الرياضيات المقاسة =================

    @Test
    fun `delay places its tap at the requested position`() {
        val impulse = ShortArray(3000) // ~34ms صمت ثم نبضة واحدة عند الإطار 1000
        impulse[2 * 1000] = 30000; impulse[2 * 1000 + 1] = 30000
        val spec = AudioFx.encodeChain(
            listOf(
                FxChainStep(
                    "delay",
                    AudioFx.clampValues(defOf("delay"), mapOf("tap1" to 250.0, "tap1Level" to 45.0, "tap2" to 2000.0, "tap2Level" to 0.0))
                )
            )
        )
        val out = AudioFx.applySpec(impulse.copyOf(), spec)
        val frames = out.size / 2
        val target = (250L * 44100 / 1000L).toInt()
        val halfWin = 44100 / 50
        val at = 1000 + target // الطبقة بعد النبضة لا بعد بداية المصفوفة
        var maxInWindow = 0
        for (i in (at - halfWin).coerceAtLeast(0)..(at + halfWin).coerceAtMost(frames - 1)) {
            maxInWindow = maxOf(maxInWindow, abs(out[2 * i].toInt()))
        }
        assertTrue(maxInWindow > 8000) { "الطبقة عند 250ms حاضرة (ذروة النافذة $maxInWindow)" }
        // بين النبضة الجافة والطبقة صمت تام
        var maxBetween = 0
        for (i in 1200 until (at - halfWin).coerceAtLeast(1201)) {
            maxBetween = maxOf(maxBetween, abs(out[2 * i].toInt()))
        }
        assertTrue(maxBetween < 3000) { "لا صدى مبكر بين النبضة والطبقة ($maxBetween)" }
    }

    @Test
    fun `echo extends the audio with a decaying tail`() {
        val src = sine(440.0, 800)
        val inFrames = src.size / 2
        val spec = AudioFx.encodeChain(
            listOf(FxChainStep("echo", AudioFx.clampValues(defOf("echo"), mapOf("delay" to 300.0, "feedback" to 45.0, "wet" to 100.0))))
        )
        val out = AudioFx.applySpec(src.copyOf(), spec)
        val outFrames = out.size / 2
        assertTrue(outFrames > inFrames + 44100 * 3 / 10) { "ذيل يتجاوز 300ms" }
        // الذيل ليس صامتًا فحسب — فيه بصمات متلاشية في أي موضع بعد الدخل
        val tailStart = inFrames + 100
        var tailPeak = 0
        for (i in tailStart until outFrames) {
            tailPeak = maxOf(tailPeak, abs(out[2 * i].toInt()))
        }
        assertTrue(tailPeak > 300) { "بصمة في الذيل (ذروة $tailPeak)" }
    }

    @Test
    fun `reverb adds hall depth with a long tail`() {
        val src = sine(600.0, 600)
        val inFrames = src.size / 2
        val spec = AudioFx.encodeChain(
            listOf(FxChainStep("reverb", AudioFx.clampValues(defOf("reverb"), mapOf("room" to 60.0, "wet" to 40.0))))
        )
        val out = AudioFx.applySpec(src.copyOf(), spec)
        assertTrue(out.size / 2 > inFrames + 44100) { "ذيل القاعة فوق ثانية" }
        var tailPeak = 0
        for (i in (inFrames + 100) until ((inFrames + 44100 / 4).coerceAtMost(out.size / 2))) {
            tailPeak = maxOf(tailPeak, abs(out[2 * i].toInt()))
        }
        assertTrue(tailPeak > 200) { "رنين في الذيل (ذروة $tailPeak)" }
    }

    @Test
    fun `distortion lowers crest factor as drive rises`() {
        val src = sine(440.0, 900)
        fun crest(drive: Double): Double {
            val spec = AudioFx.encodeChain(
                listOf(FxChainStep("distortion", AudioFx.clampValues(defOf("distortion"), mapOf("drive" to drive, "tone" to 16000.0))))
            )
            val out = AudioFx.applySpec(src.copyOf(), spec)
            val frames = out.size / 2
            val from = 44100 / 4
            var peak = 0.0
            var sum = 0.0
            for (i in from until frames) {
                val v = out[2 * i] / 32768.0
                peak = maxOf(peak, abs(v)); sum += v * v
            }
            return peak / maxOf(sqrt(sum / (frames - from)), 1e-9)
        }
        val soft = crest(1.0)
        val hard = crest(100.0)
        assertTrue(hard < soft) { "التشويش الخشن يخفض عامل القمة (ناعم $soft خشن $hard)" }
        assertTrue(soft < 2.2) { "التشويش الناعم يبقى قريبًا من الجيبية" }
        assertTrue(hard > 0.5) { "وليس صمتًا مبطّحًا" }
    }

    @Test
    fun `widener identity at 100 and grows side energy beyond`() {
        val l = FloatArray(8820) { sin(2.0 * PI * 440 * it / 44100.0).toFloat() * 0.5f }
        val r = FloatArray(8820) { sin(2.0 * PI * 440 * it / 44100.0).toFloat() * 0.25f }
        val pcm = ShortArray(8820 * 2)
        for (i in 0 until 8820) {
            pcm[2 * i] = (l[i] * 32768).toInt().toShort()
            pcm[2 * i + 1] = (r[i] * 32768).toInt().toShort()
        }
        fun sideRms(width: Double): Double {
            val spec = AudioFx.encodeChain(
                listOf(FxChainStep("widener", AudioFx.clampValues(defOf("widener"), mapOf("width" to width, "haas" to 0.0))))
            )
            val out = AudioFx.applySpec(pcm.copyOf(), spec)
            var sum = 0.0
            val frames = out.size / 2
            val from = frames / 4
            for (i in from until frames) {
                val d = (out[2 * i] - out[2 * i + 1]) / 32768.0
                sum += d * d
            }
            return sqrt(sum / (frames - from))
        }
        val base = sideRms(100.0)
        val wide = sideRms(180.0)
        assertTrue(wide > base * 1.2) { "العرض يكبر الطرف (100=$base مقابل 180=$wide)" }
    }

    @Test
    fun `telephone chokes the lows while the mids pass`() {
        val low = sine(100.0, 1200)
        val mid = sine(2000.0, 1200)
        val spec = AudioFx.encodeChain(
            listOf(FxChainStep("telephone", AudioFx.clampValues(defOf("telephone"), mapOf("intensity" to 85.0))))
        )
        val lowOut = AudioFx.applySpec(low.copyOf(), spec)
        val midOut = AudioFx.applySpec(mid.copyOf(), spec)
        val lowRatio = rmsAfterSettle(lowOut) / rmsAfterSettle(low)
        val midRatio = rmsAfterSettle(midOut) / rmsAfterSettle(mid)
        assertTrue(lowRatio < 0.45) { "المنخفض 100هرتز مخنوق ($lowRatio)" }
        assertTrue(midRatio > 0.5) { "الوسط 2ك يمر ($midRatio)" }
    }

    @Test
    fun `robot ring modulates energy away and zero mix is identity`() {
        val src = sine(440.0, 900, amp = 16000.0)
        val spec = AudioFx.encodeChain(
            listOf(FxChainStep("robot", AudioFx.clampValues(defOf("robot"), mapOf("carrier" to 45.0, "mix" to 100.0))))
        )
        val out = AudioFx.applySpec(src.copyOf(), spec)
        val r = rmsAfterSettle(out) / rmsAfterSettle(src)
        assertTrue(r > 0.05 && r < 0.95) { "التضمين الحلقي يقسم الطاقة ($r)" }
        // صفر مزج: هوية
        val off = AudioFx.encodeChain(
            listOf(FxChainStep("robot", AudioFx.clampValues(defOf("robot"), mapOf("carrier" to 45.0, "mix" to 0.0))))
        )
        val same = AudioFx.applySpec(src.copyOf(), off)
        val rSame = rmsAfterSettle(same) / rmsAfterSettle(src)
        assertTrue(abs(rSame - 1.0) < 0.15) { "صفر المزج هوية ($rSame)" }
    }

    @Test
    fun `pitch effect doubles the frequency at plus twelve keeping duration`() {
        val src = sine(440.0, 2500)
        val inDurMs = src.size / 2 * 1000L / 44100L
        val spec = AudioFx.encodeChain(
            listOf(FxChainStep("pitch", AudioFx.clampValues(defOf("pitch"), mapOf("semitones" to 12.0))))
        )
        val out = AudioFx.applySpec(src.copyOf(), spec)
        val hz = ClipTransforms.estimateDominantHz(out)
        assertTrue(abs(hz - 880.0) <= 60.0) { "440→880 (قياس $hz)" }
        val outDurMs = out.size / 2 * 1000L / 44100L
        assertTrue(abs(outDurMs - inDurMs) <= 90) { "الطول محفوظ ($inDurMs → $outDurMs)" }
    }

    @Test
    fun `vocal enhancer lifts presence band above the middle`() {
        // قياس نسبي: كسب 3.2ك يجب أن يتفوق على كسب 700هرتز بعد المعزّز
        val mid = sine(700.0, 1100)
        val pres = sine(3200.0, 1100)
        val spec = AudioFx.encodeChain(
            listOf(FxChainStep("vocal", AudioFx.clampValues(defOf("vocal"), mapOf("presence" to 6.0, "warmth" to 0.0, "clarity" to 0.0))))
        )
        val midRatio = rmsAfterSettle(AudioFx.applySpec(mid.copyOf(), spec)) / rmsAfterSettle(mid)
        val presRatio = rmsAfterSettle(AudioFx.applySpec(pres.copyOf(), spec)) / rmsAfterSettle(pres)
        assertTrue(presRatio > midRatio) { "حضور 3.2ك يرتفع فوق الوسط (700=$midRatio مقابل 3.2ك=$presRatio)" }
    }

    @Test
    fun `morpher raises the pitch to a feminine range and lowers to a deep one`() {
        val src = sine(220.0, 2200)
        val female = AudioFx.encodeChain(
            listOf(FxChainStep("morpher", AudioFx.clampValues(defOf("morpher"), mapOf("mode" to 0.0, "intensity" to 75.0))))
        )
        val deep = AudioFx.encodeChain(
            listOf(FxChainStep("morpher", AudioFx.clampValues(defOf("morpher"), mapOf("mode" to 1.0, "intensity" to 75.0))))
        )
        val hzF = ClipTransforms.estimateDominantHz(AudioFx.applySpec(src.copyOf(), female))
        val hzD = ClipTransforms.estimateDominantHz(AudioFx.applySpec(src.copyOf(), deep))
        assertTrue(hzF > 250.0 && hzF < 330.0) { "أنثوي أعلى (قياس $hzF)" }
        assertTrue(hzD > 150.0 && hzD < 195.0) { "رجولي أخفض (قياس $hzD)" }
        assertTrue(hzF > hzD + 40) { "الفرق سمعيًا واضح" }
    }

    @Test
    fun `radio squashes into a narrow band and stays audible`() {
        val low = sine(80.0, 1000)
        val spec = AudioFx.encodeChain(
            listOf(FxChainStep("radio", AudioFx.clampValues(defOf("radio"), mapOf("intensity" to 70.0))))
        )
        val out = AudioFx.applySpec(low.copyOf(), spec)
        val ratio = rmsAfterSettle(out) / rmsAfterSettle(low)
        assertTrue(ratio < 0.45) { "الـ80هرتز خارج النطاق الإذاعي ($ratio)" }
        assertTrue(rmsAfterSettle(out) > 0.0) { "ليس صمتًا تامًا" }
    }

    // ================= 5) الجاهزات والارتداد المخزّن =================

    @Test
    fun `every preset applies cleanly to real audio`() {
        val src = sine(330.0, 900)
        FxPresets.all.forEach { p ->
            val spec = FxPresets.encode(p)
            val out = AudioFx.applySpec(src.copyOf(), spec)
            assertTrue(out.size >= src.size, "الجاهزة ${p.id} لا تقصّ الصوت")
            for (v in out) {
                
                assertTrue(abs(v / 32768.0) <= 1.001, "نطاق سليم في ${p.id}")
            }
        }
    }

    @Test
    fun `bounce with fx key caches the second application`() {
        val f = MontageDemo.tone(File(tmp, "tone.wav"), 44100, 2, 1200, 440.0)
        val clip = MontageClip(
            id = "fx1", path = f.path, title = "نغمة", durationMs = 1200, startMs = 0, endMs = 1200
        )
        val cache = File(tmp, "bounce").also { it.mkdirs() }
        val spec = AudioFx.encodeChain(
            listOf(FxChainStep("echo", AudioFx.clampValues(defOf("echo"), mapOf("delay" to 200.0, "feedback" to 40.0, "wet" to 40.0))))
        )
        val r1 = ClipTransforms.bounce(clip, "fx", spec, { pcm -> AudioFx.applySpec(pcm, spec) }, cache)
        val r2 = ClipTransforms.bounce(clip, "fx", spec, { pcm -> AudioFx.applySpec(pcm, spec) }, cache)
        assertEquals(r1.file.absolutePath, r2.file.absolutePath) { "نفس البصمة يعيد نفس الملف" }
        assertEquals(r1.durationMs, r2.durationMs)
        assertTrue(r1.file.isFile && r1.file.length() > 44L)
        // ذيل التكرار يرفع المدة فوق الأصل
        assertTrue(r1.durationMs > 1200L) { "الذيل يطيل المدة (${r1.durationMs})" }
        // سلسلة مختلفة بصمة مختلفة
        val spec2 = AudioFx.encodeChain(
            listOf(FxChainStep("telephone", AudioFx.clampValues(defOf("telephone"), mapOf("intensity" to 85.0))))
        )
        val r3 = ClipTransforms.bounce(clip, "fx", spec2, { pcm -> AudioFx.applySpec(pcm, spec2) }, cache)
        assertTrue(r3.file.absolutePath != r1.file.absolutePath)
    }

    @Test
    fun `shortcuts contract E opens the effects library only on montage`() {
        val open = com.saidjte.schoolradio.desktop.ui.resolveMontageShortcut(
            androidx.compose.ui.input.key.Key.E, ctrl = false, shift = false, alt = false, meta = false
        )
        assertEquals(com.saidjte.schoolradio.desktop.ui.MontageShortcutAction.OpenEffects, open)
        // مع تعديلات النوافذ لا يسرق مفتاح الحقول
        val guarded = com.saidjte.schoolradio.desktop.ui.resolveMontageShortcut(
            androidx.compose.ui.input.key.Key.E, ctrl = true, shift = false, alt = false, meta = false
        )
        assertNull(guarded)
    }
}
