package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.BundledSounds
import com.saidjte.schoolradio.desktop.audio.Mp3Info
import com.saidjte.schoolradio.desktop.data.BundledSound
import com.saidjte.schoolradio.desktop.data.LibraryDefaults
import com.saidjte.schoolradio.desktop.data.SoundGroups
import com.saidjte.schoolradio.desktop.data.SoundLicense
import com.saidjte.schoolradio.desktop.data.SoundLibDoc
import com.saidjte.schoolradio.desktop.data.SoundLibraryMath
import com.saidjte.schoolradio.desktop.data.SoundLibraryStore
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * عقد مكتبة الأصوات والموسيقى (v0.42.0) — بنود الطلب العشرة:
 * المجموعات المدمجة الخمس بمالكها من الأصوات، والبحث والتصفية،
 * والمعاينة قبل الإضافة، والاستيراد الخاص، والتراخيص القاطعة.
 */
class SoundsLibraryTest {

    private fun tmpDir(name: String): File =
        File(System.getProperty("java.io.tmpdir"), "sounds-test-$name-${System.nanoTime()}").apply { mkdirs() }

    // ================= العقد الأساسي: 36 صوتًا في خمس مجموعات =================

    @Test
    fun `manifest carries thirty six sounds in five groups`() {
        assertEquals(36, BundledSounds.ALL.size, "الطلب يطلب مكتبة كاملة لا قائمة رمزية")
        val counts = BundledSounds.ALL.groupingBy { it.group }.eachCount()
        assertEquals(8, counts[SoundGroups.MUSIC], "مكتبة موسيقى خلفية")
        assertEquals(8, counts[SoundGroups.SFX], "مؤثرات جاهزة")
        assertEquals(6, counts[SoundGroups.TRANSITIONS], "أصوات انتقالية")
        assertEquals(8, counts[SoundGroups.NATURE], "أصوات طبيعية وبيئية")
        assertEquals(6, counts[SoundGroups.CINEMA], "مؤثرات سينمائية")
        assertEquals(
            SoundGroups.BUNDLED, counts.keys.toList().sortedBy { SoundGroups.BUNDLED.indexOf(it) },
            "لا مجموعة غريبة تسرّب إلى القائمة"
        )
    }

    @Test
    fun `every sound has arabic identity tags and duration`() {
        BundledSounds.ALL.forEach { s ->
            assertTrue(s.title.isNotBlank(), "عنوان ${s.id}")
            assertTrue(s.description.length >= 15, "وصف ${s.id} قصير جدًا فلا يشرح للمذيع")
            assertTrue(s.tags.size >= 3, "وسوم ${s.id} — البحث يحتاج وسومًا كافية")
            assertTrue(s.durationMs > 0, "مدة ${s.id}")
            assertTrue(s.fileName.endsWith(".mp3"), "الصيغة MP3 ${s.id}")
        }
        assertEquals(36, BundledSounds.ALL.map { it.id }.toSet().size, "المعرفات وحيدة")
        assertEquals(36, BundledSounds.ALL.map { it.fileName }.toSet().size, "أسماء الملفات وحيدة")
    }

    /** كل مورد MP3 موجود فعلاً داخل الجرة/مسار الاختبار — لا بطاقة وهمية تُعرض للمذيع. */
    @Test
    fun `every sound resource exists inside the jar classpath`() {
        BundledSounds.ALL.forEach { s ->
            val ins = BundledSounds::class.java.classLoader.getResourceAsStream(BundledSounds.resourcePath(s))
            assertTrue(ins != null, "المورد مفقود: ${BundledSounds.resourcePath(s)}")
            ins!!.close()
        }
    }

    // ================= الاستخراج الآمن =================

    @Test
    fun `extraction is idempotent atomic and leaves no temp files`() {
        val dir = tmpDir("extract")
        try {
            val first = BundledSounds.ensureExtracted(dir)
            assertEquals(36, first.size, "الكل يستخرج من أول مرة")
            first.values.forEach { assertTrue(it.isFile && it.length() > 0) }
            val sizes = first.values.associate { it.name to it.length() }
            val second = BundledSounds.ensureExtracted(dir)
            assertEquals(first, second, "الاستدعاء الثاني لا يعيد الكتابة")
            second.values.forEach { assertEquals(sizes[it.name], it.length(), "محتوى $it لم يتغير") }
            assertTrue(dir.listFiles()?.none { it.name.endsWith(".tmp") } == true, "بلا ملفات مؤقتة متخلفة")
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    /** عينة حية من كل مجموعة تُفك فعلاً بفاكّ MP3 وتطابق مدتها القائمة ±400ms. */
    @Test
    fun `sampled sounds decode as real mp3 matching manifest duration`() {
        val dir = tmpDir("decode")
        try {
            val files = BundledSounds.ensureExtracted(dir)
            val samples = listOf("morning_bright", "handbell", "whoosh_up", "birds_chirp", "epic_braam")
            samples.forEach { id ->
                val s = BundledSounds.ALL.first { it.id == id }
                val f = files[id] ?: throw AssertionError("ملف $id غائب")
                val info = Mp3Info.analyze(f)
                assertTrue(info != null, "تحليل MP3 لـ$id")
                val real = info!!.durationMs
                assertTrue(Math.abs(real - s.durationMs) <= 400L, "مدة $id: قائمة=${s.durationMs} فعلي=$real")
            }
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    // ================= البحث والتصفية (تصنيف حسب النوع + البحث) =================

    @Test
    fun `search matches title description and tags`() {
        // بالعنوان
        assertTrue(SoundLibraryMath.matches("مطر خفيف", "وصف", listOf("طبيعة"), "مطر"))
        // بالوصف
        assertTrue(SoundLibraryMath.matches("مطر خفيف", "مطر خفيف دائم للفقرات الهادئة", listOf(), "هادئة"))
        // بالوسم
        assertTrue(SoundLibraryMath.matches("مطر خفيف", "وصف", listOf("طبيعة", "مطر"), "طبيعة"))
        // لا مطابقة
        assertFalse(SoundLibraryMath.matches("مطر خفيف", "وصف", listOf("طبيعة"), "تصفيق"))
        // الاستعلام الخاوي يطابق الكل
        assertTrue(SoundLibraryMath.matches("أي شيء", "وصف", listOf(), ""))
        assertTrue(SoundLibraryMath.matches("أي شيء", "وصف", listOf(), "   "))
    }

    @Test
    fun `suggested category maps group to existing default categories`() {
        assertEquals(LibraryDefaults.FALLBACK_CATEGORY, SoundLibraryMath.suggestedCategory(SoundGroups.MUSIC))
        listOf(SoundGroups.SFX, SoundGroups.TRANSITIONS, SoundGroups.NATURE, SoundGroups.CINEMA)
            .forEach { assertEquals("المؤثرات الصوتية", SoundLibraryMath.suggestedCategory(it)) }
        // المقترحات دائمًا ضمن التصنيفات الافتراضية الموجودة فلا إضافة حوارية فاشلة
        SoundGroups.BUNDLED.forEach {
            assertTrue(SoundLibraryMath.suggestedCategory(it) in LibraryDefaults.CATEGORIES, "مقترح $it")
        }
    }

    @Test
    fun `duration text renders arabic compact form`() {
        assertEquals("0.7 ث", SoundLibraryMath.durationText(700))
        assertEquals("39.8 ث", SoundLibraryMath.durationText(39_800))
        assertEquals("40 ث", SoundLibraryMath.durationText(40_000))
        assertEquals("2:02 د", SoundLibraryMath.durationText(122_000))
    }

    // ================= مكتبتي الخاصة (استيراد مكتبة خاصة) =================

    @Test
    fun `custom store imports playable dedupes and persists between sessions`() {
        val dir = tmpDir("custom")
        try {
            val f = File(dir, "store.json")
            val wav1 = File(dir, "my_sound.wav").apply { writeBytes(ByteArray(128)) }
            val wav2 = File(dir, "my_tone.wav").apply { writeBytes(ByteArray(128)) }
            val junk = File(dir, "not_audio.txt").apply { writeBytes(ByteArray(16)) }

            val st = SoundLibraryStore(f)
            val r = st.addFiles(listOf(wav1, junk, wav2))
            assertEquals(2, r.added.size)
            assertEquals(1, r.skippedUnsupported, "الملف غير الصوتي يتجاوز معلنًا لا صامتًا")

            // تكرار نفس الملفات لا يضاعف
            val r2 = st.addFiles(listOf(wav1, wav2))
            assertTrue(r2.added.isEmpty())

            // دورة بين الجلسات: مخزن جديد على نفس الملف يقرأ الفهرس نفسه
            val st2 = SoundLibraryStore(f)
            assertEquals(st.custom().size, st2.custom().size)
            assertEquals(wav1.path, st2.custom().first { it.title == "my_sound" }.path)
            assertTrue(st2.remove(st2.custom().first().id))
            assertEquals(st2.custom().size, st.custom().size - 1)
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test
    fun `custom store survives corrupt json by falling back to empty`() {
        val dir = tmpDir("corrupt")
        try {
            val f = File(dir, "store.json")
            f.writeText("{ هذا JSON تالف تمامًا ")
            val st = SoundLibraryStore(f)
            assertTrue(st.custom().isEmpty(), "التالف يعود فارغًا ولا يهدم الإقلاع")
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    // ================= التراخيص (توضيح الاستخدام التجاري) =================

    @Test
    fun `license table covers bundled sounds and all seven jingles`() {
        val rows = SoundLicense.rows()
        assertEquals(8, rows.size, "صف الأصوات المولدة + صفوف الجينلات السبعة")
        assertEquals(SoundLicense.BUNDLED_ROW, rows.first())
        // توافق قاطع مع عناوين الجينلات الفعلية — أي جينل جديد بلا صف رخصة يُكسر الاختبار
        val jingleTitles = com.saidjte.schoolradio.desktop.audio.BundledJingles.ALL.map { it.title }
        assertEquals(jingleTitles, rows.drop(1).map { it.item })
        rows.forEach { r ->
            assertTrue(r.license.isNotBlank() && r.commercial.isNotBlank(), "صف رخصة ناقص: ${r.item}")
        }
    }

    @Test
    fun `commercial use is stated explicitly`() {
        val s = SoundLicense.commercialSummary()
        assertTrue(s.contains("CC0"), "الأصوات المولدة CC0")
        assertTrue(s.contains("مسموح"), "الجواب قاطع: مسموح")
        assertTrue(SoundLicense.BUNDLED_ROW.commercial.contains("مسموح"))
        assertTrue(SoundLicense.CUSTOM_NOTICE.contains("المسؤول"), "تحذير المستورد واضح")
        // المصدر يوضح التوليد المحلي — لا حقوق خارجية في الأصوات الـ36
        assertTrue(SoundLicense.BUNDLED_ROW.source.contains("داخل المشروع"))
    }

    // ================= محرك المعاينة (الاستماع قبل الإضافة) =================

    private class ScriptedVoice(
        private val chunks: Int = 30,
        private val chunkMs: Long = 15
    ) : com.saidjte.schoolradio.desktop.audio.SoundPreviewEngine.Voice {
        private var left = chunks
        override fun readChunk(): Boolean {
            Thread.sleep(chunkMs)
            left--
            return left > 0
        }
        override fun close() { left = 0 }
    }

    @Test
    fun `preview engine plays one sound and switching stops the previous`() {
        val engine = com.saidjte.schoolradio.desktop.audio.SoundPreviewEngine()
        engine.voiceFactoryOverride = { ScriptedVoice() }
        val f1 = File("/tmp/preview-a.mp3")
        val f2 = File("/tmp/preview-b.mp3")
        try {
            assertNull(engine.playingFile.value)
            engine.toggle(f1)
            assertEquals(f1.absolutePath, engine.playingFile.value, "المعاينة تعمل قبل الإضافة")
            // نفس الملف ثانية = إيقاف
            engine.toggle(f1)
            assertNull(engine.playingFile.value, "النقر الثانية يوقف")
            // ملف آخر يبدأ مباشرة
            engine.toggle(f2)
            assertEquals(f2.absolutePath, engine.playingFile.value)
            engine.shutdown()
            assertNull(engine.playingFile.value, "الإغلاق النظيف يوقف كل شيء")
        } finally {
            engine.shutdown()
        }
    }
}
