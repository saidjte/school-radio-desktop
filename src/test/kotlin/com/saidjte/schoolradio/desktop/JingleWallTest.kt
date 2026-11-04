package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.BundledJingles
import com.saidjte.schoolradio.desktop.audio.FakeToneVoice
import com.saidjte.schoolradio.desktop.audio.JingleWallEngine
import com.saidjte.schoolradio.desktop.data.JinglePad
import com.saidjte.schoolradio.desktop.data.JinglePadJson
import com.saidjte.schoolradio.desktop.data.JingleWallLogic
import com.saidjte.schoolradio.desktop.data.SettingsStore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * عقد جدار الجينغل (v0.28.0 «أضف جدار الجينغل» و«زر فتح الجدار في نافذة منبثقة
 * لا تعرقل العمل»): المنطق النقي للمواضع الاثني عشر مع الجينلات السبعة المدمجة،
 * العقد الدائري للمخزن، وسلوك المحرك (تشغيل/إيقاف/تزامن/مستوى حي) بصوت مزيف
 * بلا عتاد صوتي — والعقد نفسه يُفحص داخل selftest الجرة المشحونة أيضًا.
 */
class JingleWallTest {

    private fun tmp(): File = File(System.getProperty("java.io.tmpdir"), "jw-test-${System.currentTimeMillis()}-${(0..999).random()}")

    private fun await(msg: String, timeoutMs: Long = 5_000L, cond: () -> Boolean) {
        val dl = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < dl) {
            if (cond()) return
            Thread.sleep(10)
        }
        throw AssertionError("انقضت المهلة: $msg")
    }

    // ================= المنطق النقي =================

    @Test
    fun `bundled jingles match the phone app titles and files`() {
        val all = BundledJingles.ALL
        assertEquals(7, all.size)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals(all.size, all.map { it.title }.toSet().size)
        // الترتيب نفسه المعتمد في نسخة الهاتف
        assertEquals("جرس المدرسة", all[0].title)
        assertEquals("ختام هادئ", all.last().title)
        assertEquals("school_bell.mp3", all[0].fileName)
    }

    @Test
    fun `extraction is idempotent and fills defaults`() {
        val dir = tmp()
        try {
            val files = BundledJingles.ensureExtracted(dir)
            assertEquals(7, files.size)
            assertTrue(files.all { it.isFile && it.length() > 10_000 })
            val sizes = files.map { it.length() }
            BundledJingles.ensureExtracted(dir)
            assertEquals(sizes, files.map { it.length() })

            val defaults = JingleWallLogic.bundledDefaults(files, BundledJingles.ALL.map { it.title })
            assertEquals(12, defaults.size)
            assertTrue(defaults.take(7).all { !it.isEmpty && File(it.path!!).isFile })
            assertTrue(defaults.drop(7).all { it.isEmpty })
            assertEquals((0..11).toList(), defaults.map { it.index })
            assertEquals("جرس المدرسة", defaults[0].title)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `resolve sanitizes corrupt saved pads and keeps defaults elsewhere`() {
        val defaults = JingleWallLogic.bundledDefaults(
            (1..7).map { File("/bundled/$it.mp3") },
            BundledJingles.ALL.map { it.title }
        )
        val merged = JingleWallLogic.resolve(
            listOf(
                JinglePadJson(index = 0, path = "/x/ replaced.mp3", title = "جديد"),
                JinglePadJson(index = 42, path = "/out.mp3"),          // فهرس خارج الحدود: يسقط
                JinglePadJson(index = 8, path = "   "),                 // مسار فارغ: يسقط
                JinglePadJson(index = 0, path = "/x/second.wav"),       // تكرار: الأخير يفوز
                JinglePadJson(path = "/no-index.mp3")                   // بلا فهرس: يسقط
            ),
            defaults
        )
        assertEquals(12, merged.size)
        assertEquals("/x/second.wav", merged[0].path)
        assertEquals(defaults[1], merged[1])
        assertEquals(defaults[8], merged[8])
        assertEquals(defaults[11], merged[11])
    }

    @Test
    fun `encode and resolve round trip preserves pads exactly`() {
        val pads = (0 until 12).map { JinglePad(it, if (it % 3 == 0) "/p/$it.mp3" else null, if (it % 3 == 0) "عنوان $it" else null) }
        val restored = JingleWallLogic.resolve(JingleWallLogic.encodePads(pads), JingleWallLogic.emptyPads())
        assertEquals(pads, restored)
    }

    @Test
    fun `volume clamps and empty pads are safe`() {
        assertEquals(1f, JingleWallLogic.clampVolume(5f))
        assertEquals(0f, JingleWallLogic.clampVolume(-0.5f))
        assertEquals(0.7f, JingleWallLogic.clampVolume(0.7f))
        assertEquals(JingleWallLogic.DEFAULT_VOLUME, JingleWallLogic.clampVolume(Float.NaN))
        val empties = JingleWallLogic.emptyPads()
        assertEquals(12, empties.size)
        assertTrue(empties.all { it.isEmpty })
    }

    // ================= المخزن =================

    @Test
    fun `settings store round trips pads and volume`() {
        val dir = tmp()
        try {
            val f = File(dir, "settings.json")
            val defaults = JingleWallLogic.bundledDefaults(
                (1..7).map { File(dir, "j$it.mp3") },
                BundledJingles.ALL.map { it.title }
            )
            val s1 = SettingsStore(f)
            s1.setJingleWall(0.33f, JingleWallLogic.encodePads(defaults))
            val s2 = SettingsStore(f)
            assertEquals(0.33f, s2.jingleVolume())
            assertEquals(JingleWallLogic.encodePads(defaults), s2.jinglePads())
            // غياب الحقل كليًا: مستوى افتراضي ومواضع null
            val s3store = File(dir, "fresh.json")
            val s3 = SettingsStore(s3store)
            assertEquals(JingleWallLogic.DEFAULT_VOLUME, s3.jingleVolume())
            assertEquals(null, s3.jinglePads())
        } finally {
            dir.deleteRecursively()
        }
    }

    // ================= المحرك بصوت مزيف =================

    @Test
    fun `engine toggle plays then second click stops`() {
        val eng = JingleWallEngine()
        eng.voiceFactoryOverride = { FakeToneVoice(chunks = 600, chunkMs = 5) }
        try {
            eng.toggle(0, File("fake/a.mp3"))
            await("slot 0 playing") { 0 in eng.playing.value }
            eng.toggle(0, File("fake/a.mp3"))
            await("slot 0 stopped") { 0 !in eng.playing.value }
            eng.awaitIdle()
            assertEquals(emptySet(), eng.playing.value)
        } finally {
            eng.voiceFactoryOverride = null
            eng.shutdown()
        }
    }

    @Test
    fun `engine plays several slots simultaneously like a real cart wall`() {
        val eng = JingleWallEngine()
        eng.voiceFactoryOverride = { FakeToneVoice(chunks = 600, chunkMs = 5) }
        try {
            eng.toggle(2, File("fake/b.mp3"))
            eng.toggle(5, File("fake/c.wav"))
            eng.toggle(9, File("fake/d.ogg"))
            await("three slots") { eng.playing.value == setOf(2, 5, 9) }
            // إيقاف واحد لا يمس البقية
            eng.stop(5)
            await("slot 5 alone stopped") { eng.playing.value == setOf(2, 9) }
        } finally {
            eng.voiceFactoryOverride = null
            eng.shutdown()
        }
    }

    @Test
    fun `engine applies live volume changes to running voices`() {
        val eng = JingleWallEngine()
        val created = mutableListOf<FakeToneVoice>()
        eng.voiceFactoryOverride = { FakeToneVoice(chunks = 600, chunkMs = 5).also { created += it } }
        try {
            eng.toggle(1, File("fake/v.mp3"))
            await("slot 1 playing") { 1 in eng.playing.value }
            eng.setVolume(0.25f)
            await("volume applied") { created.first().volumes.contains(0.25f) }
            assertEquals(0.25f, eng.volume.value)
            eng.setVolume(9f)
            assertEquals(1f, eng.volume.value, "clamped to 1.0")
        } finally {
            eng.voiceFactoryOverride = null
            eng.shutdown()
        }
    }

    @Test
    fun `engine reports missing file as slot error without crashing`() {
        val eng = JingleWallEngine()
        try {
            eng.toggle(7, File("fake/does-not-exist.mp3"))
            await("error surfaced") { eng.errors.value[7]?.contains("غير موجود") == true }
            assertEquals(emptySet(), eng.playing.value)
        } finally {
            eng.shutdown()
        }
    }

    @Test
    fun `engine natural finish removes the slot from playing`() {
        val eng = JingleWallEngine()
        eng.voiceFactoryOverride = { FakeToneVoice(chunks = 6, chunkMs = 10) } // ~60ms فقط
        try {
            eng.toggle(3, File("fake/short.mp3"))
            await("started") { 3 in eng.playing.value }
            await("finished naturally") { 3 !in eng.playing.value }
            eng.awaitIdle()
        } finally {
            eng.voiceFactoryOverride = null
            eng.shutdown()
        }
    }

    @Test
    fun `engine ignores out of range slots`() {
        val eng = JingleWallEngine()
        try {
            eng.toggle(-1, File("fake/a.mp3"))
            eng.toggle(12, File("fake/a.mp3"))
            assertEquals(emptySet(), eng.playing.value)
            assertEquals(emptyMap(), eng.errors.value)
        } finally {
            eng.shutdown()
        }
    }
}
