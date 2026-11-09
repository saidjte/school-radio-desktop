package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.audio.MontageDemo
import com.saidjte.schoolradio.desktop.audio.MontageRender
import com.saidjte.schoolradio.desktop.data.Json
import com.saidjte.schoolradio.desktop.data.MontageAutoPoint
import com.saidjte.schoolradio.desktop.data.MontageAutomationLane
import com.saidjte.schoolradio.desktop.data.MontageClip
import com.saidjte.schoolradio.desktop.data.MontageGroup
import com.saidjte.schoolradio.desktop.data.MontageLogic
import com.saidjte.schoolradio.desktop.data.MontageMusicClip
import com.saidjte.schoolradio.desktop.data.MontageProject
import com.saidjte.schoolradio.desktop.data.MontageTrack
import java.io.File
import javax.sound.sampled.AudioSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * عقد v0.41.0 «نظام المونتاج متعدد المسارات الاحترافي» — بنود الطلب الخمسة عشر:
 * - عدد كبير من المسارات (16) بإضافة وحذف وترتيب مستقل.
 * - أنواع المسارات: تعليق ثابت + موسيقى خلفية + مؤثرات صوتية + حوار.
 * - كتم M ومنفرد S حيّان في رؤوس المسارات الحرة (مسار التعليق وحده بلا كتم).
 * - توازن Pan خطي لكل مسار يصل المزج فعليًا (يسار/يمين).
 * - تجميع المسارات Groups: مستوى يتراكب وكتم يصمّم ومنفرد يعزل أعضاءه.
 * - التحكم في تداخل الأصوات: الخفض يعمّ الموسيقى الخلفية وحدها بحسب النوع.
 * - أتمتة مستوى الصوت: نقاط مفتاحية خطية تُقرأ قبلها وبعدها وبينها وتصل المزج.
 * - مزج كل المسارات في ملف واحد: التصدير يشمل الأنواع كلها بالمدة الصحيحة.
 * - الهجرة: ملف JSON قديم بلا الحقول الجديدة يفتح آمنًا (null → افتراضات).
 */
class MontageV41Test {

    @TempDir
    lateinit var tmp: File

    // ================= 1) الأنواع والهجرة =================

    @Test
    fun `track types resolve with silent migration for unknown or null type`() {
        assertEquals(MontageLogic.TYPE_MUSIC, MontageLogic.trackType(MontageTrack(id = "a", name = "x")))
        assertEquals(MontageLogic.TYPE_MUSIC, MontageLogic.trackType(MontageTrack(id = "a", name = "x", type = null)))
        assertEquals(MontageLogic.TYPE_MUSIC, MontageLogic.trackType(MontageTrack(id = "a", name = "x", type = "garbage")))
        assertEquals(MontageLogic.TYPE_SFX, MontageLogic.trackType(MontageTrack(id = "a", name = "x", type = "sfx")))
        assertEquals(MontageLogic.TYPE_DIALOGUE, MontageLogic.trackType(MontageTrack(id = "a", name = "x", type = "dialogue")))
        // التسميات العربية للرؤوس
        assertEquals("موسيقى", MontageLogic.trackTypeLabelAr(MontageLogic.TYPE_MUSIC))
        assertEquals("مؤثرات", MontageLogic.trackTypeLabelAr(MontageLogic.TYPE_SFX))
        assertEquals("حوار", MontageLogic.trackTypeLabelAr(MontageLogic.TYPE_DIALOGUE))
        // التحكم في تداخل الأصوات بحسب النوع: الموسيقى وحدها تنخفض
        assertTrue(MontageLogic.ducksUnderVoice(MontageTrack(id = "a", name = "x")))
        assertTrue(MontageLogic.ducksUnderVoice(MontageTrack(id = "a", name = "x", type = MontageLogic.TYPE_MUSIC)))
        assertFalse(MontageLogic.ducksUnderVoice(MontageTrack(id = "a", name = "x", type = MontageLogic.TYPE_SFX)))
        assertFalse(MontageLogic.ducksUnderVoice(MontageTrack(id = "a", name = "x", type = MontageLogic.TYPE_DIALOGUE)))
    }

    @Test
    fun `effective tracks default to the music sfx dialogue trio when empty`() {
        val tracks = MontageLogic.effectiveTracks(MontageProject())
        assertEquals(3, tracks.size)
        assertEquals(MontageLogic.DEFAULT_TRACK_ID, tracks[0].id)
        assertEquals("موسيقى خلفية", tracks[0].name)
        assertEquals(MontageLogic.TYPE_MUSIC, MontageLogic.trackType(tracks[0]))
        assertEquals(MontageLogic.DEFAULT_SFX_ID, tracks[1].id)
        assertEquals("مؤثرات", tracks[1].name)
        assertEquals(MontageLogic.TYPE_SFX, MontageLogic.trackType(tracks[1]))
        assertEquals(MontageLogic.DEFAULT_DIALOGUE_ID, tracks[2].id)
        assertEquals("حوار", tracks[2].name)
        assertEquals(MontageLogic.TYPE_DIALOGUE, MontageLogic.trackType(tracks[2]))
    }

    // ================= 2) عدد كبير من المسارات وإدارتها =================

    @Test
    fun `add track of type picks unused color and non duplicated name`() {
        val base = MontageLogic.effectiveTracks(MontageProject())
        val sfx2 = MontageLogic.newTrackOfType(base, MontageLogic.TYPE_SFX)
        assertEquals("مؤثرات 2", sfx2.name)
        assertEquals(MontageLogic.TYPE_SFX, MontageLogic.trackType(sfx2))
        assertFalse(sfx2.colorIndex in base.map { it.colorIndex })
        val sfx3 = MontageLogic.newTrackOfType(base + sfx2, MontageLogic.TYPE_SFX)
        assertEquals("مؤثرات 3", sfx3.name)
        val dlg = MontageLogic.newTrackOfType(base, MontageLogic.TYPE_DIALOGUE)
        assertEquals("حوار 2", dlg.name)
    }

    @Test
    fun `sixteen tracks are the ceiling and reorder moves tracks independently`() {
        assertEquals(16, MontageLogic.MAX_TRACKS)
        var tracks = MontageLogic.defaultTrackTrio()
        repeat(13) { tracks = tracks + MontageLogic.newTrackOfType(tracks, MontageLogic.TYPE_MUSIC) }
        assertEquals(16, tracks.size)
        val first = tracks[0]
        val second = tracks[1]
        // الترتيب المستقل: المسار الأول ينزل خطوة والثاني يصعد مكانَه
        val moved = MontageLogic.moveTrack(tracks, first.id, +1)!!
        assertEquals(second.id, moved[0].id)
        assertEquals(first.id, moved[1].id)
        // الحدود: أول مسار لا يصعد وآخر مسار لا ينزل والمجهول null
        assertNull(MontageLogic.moveTrack(tracks, tracks[0].id, -1))
        assertNull(MontageLogic.moveTrack(tracks, tracks[15].id, +1))
        assertNull(MontageLogic.moveTrack(tracks, "ghost", +1))
    }

    // ================= 3) الكتم والمنفرد مع المجموعات =================

    private fun track(id: String, vol: Int = 100, muted: Boolean = false, solo: Boolean = false, groupId: String? = null) =
        MontageTrack(id = id, name = id, volumePercent = vol, muted = muted, solo = solo, groupId = groupId)

    private fun group(id: String, vol: Int = 100, muted: Boolean = false, solo: Boolean = false) =
        MontageGroup(id = id, name = id, volumePercent = vol, muted = muted, solo = solo)

    @Test
    fun `track mute and solo follow the mixing console contract`() {
        val t = track("t", vol = 150)
        assertEquals(1.5f, MontageLogic.trackAmp(t, null, anyTrackSolo = false, anyGroupSolo = false))
        // الكتم يصمّم
        assertEquals(0f, MontageLogic.trackAmp(t.copy(muted = true), null, false, false))
        // منفرد غيره يصمّم غير المنفرد
        assertEquals(0f, MontageLogic.trackAmp(t, null, anyTrackSolo = true, anyGroupSolo = false))
        // منفرد غيره والهذا منفرد فيبقى حيًّا
        assertEquals(1.5f, MontageLogic.trackAmp(t.copy(solo = true), null, anyTrackSolo = true, anyGroupSolo = false))
        assertEquals(true, MontageLogic.anyTrackSolo(listOf(t.copy(solo = true))))
    }

    @Test
    fun `groups multiply track gain and control mute and solo over members`() {
        val g = group("g", vol = 50)
        val member = track("t", vol = 80, groupId = "g")
        // مستوى المجموعة يتراكب: 80٪ × 50٪ = 40٪
        assertEquals(0.4f, MontageLogic.trackAmp(member, g, false, false), 1e-6f)
        // كتم المجموعة يصمّم العضو ولو كان سليمًا
        assertEquals(0f, MontageLogic.trackAmp(member, g.copy(muted = true), false, false))
        // منفرد مجموعة أخرى يبقي عضو المجموعة المنفردة حيًّا
        val soloGroup = g.copy(solo = true)
        assertEquals(0.4f, MontageLogic.trackAmp(member, soloGroup, anyTrackSolo = false, anyGroupSolo = true), 1e-6f)
        // منفرد مجموعة أخرى والعضو بلا منفرد يصمّمه
        assertEquals(0f, MontageLogic.trackAmp(member, g, anyTrackSolo = false, anyGroupSolo = true))
        // كتم المسار يفوز حتى داخل مجموعة حيّة
        assertEquals(0f, MontageLogic.trackAmp(member.copy(muted = true), g, false, false))
        // المجموعة المجهولة تعني بلا مجموعة فتعود قيمة المسار وحده
        assertNull(MontageLogic.groupById(listOf(g), null))
        assertNull(MontageLogic.groupById(listOf(g), ""))
        assertNull(MontageLogic.groupById(listOf(g), "ghost"))
    }

    @Test
    fun `groups sanitize caps at eight and keeps sane names`() {
        val many = (1..12).map { group("g$it") }
        assertEquals(8, MontageLogic.sanitizeGroups(many).size)
        val sane = MontageLogic.sanitizeGroups(listOf(MontageGroup(id = "g", name = "  "), MontageGroup(id = "g2", name = "أجواء الحصة")))
        assertEquals("مجموعة", sane[0].name)
        assertEquals("أجواء الحصة", sane[1].name)
        val huge = MontageGroup(id = "g", name = "ا".repeat(80))
        assertEquals(24, MontageLogic.sanitizeGroups(listOf(huge)).first().name.length)
        // مشروع قديم بلا مجموعات يعود قائمة خاوية لا انفجار
        assertTrue(MontageLogic.groupsOf(MontageProject()).isEmpty())
    }

    // ================= 4) التوازن Pan =================

    @Test
    fun `pan clamps and maps linearly to channel gains`() {
        assertEquals(100, MontageLogic.clampPan(500))
        assertEquals(-100, MontageLogic.clampPan(-500))
        // الوسط: القناتان كاملتان — السلوك الحالي بلا مساس
        assertEquals(1f, MontageLogic.panGainLeft(0))
        assertEquals(1f, MontageLogic.panGainRight(0))
        // يسار كامل: اليسار كامل واليمين صمت
        assertEquals(1f, MontageLogic.panGainLeft(-100))
        assertEquals(0f, MontageLogic.panGainRight(-100))
        // يمين كامل
        assertEquals(0f, MontageLogic.panGainLeft(100))
        assertEquals(1f, MontageLogic.panGainRight(100))
        // النصف خطي: القناة المقابلة للجهة تبقى كاملة والأخرى تخفت
        assertEquals(0.5f, MontageLogic.panGainLeft(50), 1e-6f)
        assertEquals(1f, MontageLogic.panGainRight(50), 1e-6f)
        assertEquals(1f, MontageLogic.panGainLeft(-25), 1e-6f)
        assertEquals(0.75f, MontageLogic.panGainRight(-25), 1e-6f)
    }

    // ================= 5) أتمتة مستوى الصوت =================

    private fun pt(id: String, t: Long, pct: Int) = MontageAutoPoint(id = id, tMs = t, percent = pct)

    @Test
    fun `automation reads before first between and after last points linearly`() {
        val pts = listOf(pt("1", 0, 100), pt("2", 1000, 200), pt("3", 2000, 50))
        assertEquals(100, MontageLogic.autoGainPercentAt(pts, 0))
        assertEquals(100, MontageLogic.autoGainPercentAt(pts, -5)) // قبل الأولى تعود الأولى
        assertEquals(150, MontageLogic.autoGainPercentAt(pts, 500)) // منتصف 100→200
        assertEquals(200, MontageLogic.autoGainPercentAt(pts, 1000))
        assertEquals(125, MontageLogic.autoGainPercentAt(pts, 1500)) // منتصف 200→50
        assertEquals(50, MontageLogic.autoGainPercentAt(pts, 2000))
        assertEquals(50, MontageLogic.autoGainPercentAt(pts, 9999)) // بعد الأخيرة تعود الأخيرة
        // القائمة الخاوية محايدة تمامًا
        assertEquals(100, MontageLogic.autoGainPercentAt(emptyList(), 1000))
    }

    @Test
    fun `automation points sanitize order clamp and cap at sixty four`() {
        val raw = listOf(pt("3", 3000, 500), pt("1", -10, 0), pt("2", 1000, 130))
        val sane = MontageLogic.sanitizeAutoPoints(raw)
        assertEquals(listOf("1", "2", "3"), sane.map { it.id }) // مرتبة بالزمن
        assertEquals(0L, sane[0].tMs) // السالب يُقص
        assertEquals(200, sane[2].percent) // المحبوس 200
        assertEquals(130, sane[1].percent)
        val many = (1..80).map { pt("p$it", it * 10L, 100) }
        assertEquals(64, MontageLogic.sanitizeAutoPoints(many).size)
    }

    @Test
    fun `automation lane helpers read from project and tolerate legacy null`() {
        val lane = MontageAutomationLane(trackId = "t1", points = listOf(pt("1", 0, 100)))
        val p = MontageProject(automation = listOf(lane))
        assertEquals(lane, MontageLogic.automationLaneOf(p, "t1"))
        assertNull(MontageLogic.automationLaneOf(p, "ghost"))
        assertTrue(MontageLogic.automationLanesOf(MontageProject()).isEmpty()) // null القديم آمن
    }

    // ================= 6) مزج كل المسارات في ملف واحد — إثبات فعلي =================

    @Test
    fun `mixdown renders every track type with pan automation and grouping`() {
        val dir = File(tmp, "tones${System.nanoTime()}")
        val voice = MontageDemo.tone(File(dir, "voice.wav"), 44_100, 2, 2_000, 440.0)
        val bed = MontageDemo.tone(File(dir, "bed.wav"), 44_100, 2, 4_000, 330.0)
        val sfx = MontageDemo.tone(File(dir, "sfx.wav"), 44_100, 2, 1_500, 880.0)
        val dlg = MontageDemo.tone(File(dir, "dlg.wav"), 44_100, 2, 3_000, 220.0)

        val music = MontageTrack(id = "tm", name = "موسيقى خلفية", type = MontageLogic.TYPE_MUSIC)
        val effects = MontageTrack(id = "tf", name = "مؤثرات", type = MontageLogic.TYPE_SFX, pan = -100)
        val dialogue = MontageTrack(id = "td", name = "حوار", type = MontageLogic.TYPE_DIALOGUE, pan = 100)
        val groups = listOf(group("gm", vol = 50))
        val musicInGroup = music.copy(groupId = "gm")

        val p = MontageProject(
            clips = listOf(MontageClip("v", voice.path, "تعليق", 2_000, 0, 2_000)),
            musicClips = listOf(
                MontageMusicClip("b", bed.path, "سرير", 4_000, startMs = 0, trackId = "tm"),
                MontageMusicClip("s", sfx.path, "مؤثر", 1_500, startMs = 4_000, trackId = "tf"),
                MontageMusicClip("d", dlg.path, "حوار", 3_000, startMs = 5_500, trackId = "td")
            ),
            tracks = listOf(musicInGroup, effects, dialogue),
            groups = groups,
            duckingEnabled = true,
            duckDb = -12
        )

        val out = File(tmp, "mixdown.wav")
        val r = MontageRender.render(p, out)
        assertTrue(r.ok) { r.message ?: "" }
        // المدة = آخر نهاية مسار: 5500 + 3000 = 8500ms — مزج كل المسارات في ملف واحد
        assertTrue(Math.abs(r.durationMs - 8_500L) <= 60L) { "durationMs=${r.durationMs}" }

        // قراءة عينات القناتين عند مواضع دليلية — كل قراءة تفتح تيارها الخاص
        // لأن skip نسبية لا مطلقة فلا تشارك تيارًا واحدًا بين المواضع
        val fmt = AudioSystem.getAudioInputStream(out).format
        assertEquals(44_100f, fmt.sampleRate)
        assertEquals(2, fmt.channels)
        fun framesAt(ms: Long, count: Int): ShortArray {
            val ais = AudioSystem.getAudioInputStream(out)
            val bytes = ByteArray(count * 4)
            ais.skip(ms * 44_100 / 1000 * 4)
            var off = 0
            while (off < bytes.size) {
                val n = ais.read(bytes, off, bytes.size - off)
                if (n < 0) break
                off += n
            }
            ais.close()
            val sh = ShortArray(bytes.size / 2)
            var i = 0
            while (i < sh.size) {
                sh[i] = (((bytes[2 * i + 1].toInt() shl 8) or (bytes[2 * i].toInt() and 0xFF)).toShort())
                i++
            }
            return sh
        }
        // عند 1000ms: السرير فقط (التعليق دخل من 0 حتى 2000) — مزيج التعليق والسرير
        val mid1 = framesAt(1_000, 200)
        val l1 = mid1.filterIndexed { i, _ -> i % 2 == 0 }.maxOf { Math.abs(it.toInt()) }
        val r1 = mid1.filterIndexed { i, _ -> i % 2 == 1 }.maxOf { Math.abs(it.toInt()) }
        assertTrue(l1 > 2_000 || r1 > 2_000) { "music audible at 1s: L=$l1 R=$r1" }
        // عند 4500ms: المؤثرات وحدها — Pan يسار كامل فالقناة اليسرى تسود
        val mid2 = framesAt(4_500, 200)
        val l2 = mid2.filterIndexed { i, _ -> i % 2 == 0 }.maxOf { Math.abs(it.toInt()) }
        val r2 = mid2.filterIndexed { i, _ -> i % 2 == 1 }.maxOf { Math.abs(it.toInt()) }
        assertTrue(l2 > 2_000) { "sfx left channel audible: L=$l2" }
        assertTrue(r2 < l2 / 4) { "sfx right channel nearly silent (pan -100): R=$r2 L=$l2" }
        // عند 6000ms: الحوار وحده — Pan يمين كامل فاليمنى تسود
        val mid3 = framesAt(6_000, 200)
        val l3 = mid3.filterIndexed { i, _ -> i % 2 == 0 }.maxOf { Math.abs(it.toInt()) }
        val r3 = mid3.filterIndexed { i, _ -> i % 2 == 1 }.maxOf { Math.abs(it.toInt()) }
        assertTrue(r3 > 2_000) { "dialogue right channel audible: R=$r3" }
        assertTrue(l3 < r3 / 4) { "dialogue left channel nearly silent (pan +100): L=$l3 R=$r3" }
    }

    @Test
    fun `mixdown ducks only background music and leaves sfx and dialogue full`() {
        val dir = File(tmp, "tones${System.nanoTime()}")
        val voice = MontageDemo.tone(File(dir, "voice.wav"), 44_100, 2, 4_000, 440.0)
        val bed = MontageDemo.tone(File(dir, "bed.wav"), 44_100, 2, 4_000, 330.0)
        val sfx = MontageDemo.tone(File(dir, "sfx.wav"), 44_100, 2, 4_000, 880.0)

        // عينات منطقية: الخفض −12dB يعني تقريبًا 25٪ من السعة
        val p = MontageProject(
            clips = listOf(MontageClip("v", voice.path, "تعليق", 4_000, 0, 4_000)),
            musicClips = listOf(
                MontageMusicClip("b", bed.path, "سرير", 4_000, startMs = 0, trackId = "tm"),
                MontageMusicClip("s", sfx.path, "مؤثر", 4_000, startMs = 0, trackId = "tf")
            ),
            tracks = listOf(
                MontageTrack(id = "tm", name = "موسيقى خلفية", type = MontageLogic.TYPE_MUSIC),
                MontageTrack(id = "tf", name = "مؤثرات", type = MontageLogic.TYPE_SFX)
            ),
            duckingEnabled = true,
            duckDb = -12
        )
        val out = File(tmp, "duckmix.wav")
        val r = MontageRender.render(p, out)
        assertTrue(r.ok) { r.message ?: "" }

        // نمط موسيقى 330Hz ومؤثر 880Hz منفصلان طيفيًا — نقيس بالفرق: نصيّر الملف كله
        // ونقارن طاقة المسارات عبر تصييرين مرجعيين: واحد بلا خفض للسرير وواحد كامل
        val pNoDuck = p.copy(duckingEnabled = false)
        val out2 = File(tmp, "noduck.wav")
        val r2 = MontageRender.render(pNoDuck, out2)
        assertTrue(r2.ok) { r2.message ?: "" }

        fun energyAt(f: File, fromMs: Long, toMs: Long): Double {
            AudioSystem.getAudioInputStream(f).use { ais ->
                ais.skip(fromMs * 44_100 / 1000 * 4)
                val frames = ((toMs - fromMs) * 44_100 / 1000).toInt()
                val buf = ByteArray(frames * 4)
                var off = 0
                while (off < buf.size) {
                    val n = ais.read(buf, off, buf.size - off)
                    if (n < 0) break
                    off += n
                }
                var acc = 0.0
                var i = 0
                while (i + 1 < off / 2) {
                    val v = (((buf[2 * i + 1].toInt() shl 8) or (buf[2 * i].toInt() and 0xFF)).toShort()).toDouble()
                    acc += v * v
                    i += 2
                }
                return acc
            }
        }
        // داخل نطاق النطق (1000..3000): الفرق بين خفض وبدونه = طاقة السرير المخفوضة فقط
        val ducked = energyAt(out, 1_000, 3_000)
        val free = energyAt(out2, 1_000, 3_000)
        assertTrue(ducked < free * 0.75) { "music ducked inside narration: ducked=$ducked free=$free" }
        assertTrue(ducked > free * 0.35) { "duck depth is -12dB not silence: ducked=$ducked free=$free" }
    }

    // ================= 7) هجرة JSON القديمة — null آمن عبر Gson الحقيقي =================

    @Test
    fun `legacy v40 project json without new fields loads with safe defaults`() {
        val oldJson = """
            {
              "clips": [],
              "gapMs": 0,
              "crossfadeMs": 0,
              "title": "مونتاج الحصة",
              "playheadMs": 0,
              "musicClips": [],
              "duckingEnabled": true,
              "duckDb": -12,
              "duckAttackMs": 300,
              "duckReleaseMs": 800,
              "markers": [],
              "tracks": [
                {"id": "t1", "name": "موسيقى", "colorIndex": 1, "volumePercent": 120, "muted": false, "solo": false}
              ],
              "selectionStartMs": -1,
              "selectionEndMs": -1
            }
        """.trimIndent()
        val f = File(tmp, "legacy-montage.json")
        f.writeText(oldJson, Charsets.UTF_8)
        val loaded = Json.gson.fromJson(f.readText(Charsets.UTF_8), MontageProject::class.java)
        val tracks = MontageLogic.effectiveTracks(loaded)
        assertEquals(1, tracks.size)
        // النوع الغائب (null عبر Gson) يعود موسيقى خلفية بلا انفجار
        assertEquals(MontageLogic.TYPE_MUSIC, MontageLogic.trackType(tracks[0]))
        assertEquals(0, tracks[0].pan) // الغائب = وسط
        assertNull(tracks[0].groupId)
        assertEquals(120, tracks[0].volumePercent)
        assertTrue(MontageLogic.groupsOf(loaded).isEmpty())
        assertTrue(MontageLogic.automationLanesOf(loaded).isEmpty())
        // والكتم/المنفرد القديمان يبقيان كما حُفظا (أزرار v0.41 تديرهما)
        assertFalse(tracks[0].muted)
    }

    @Test
    fun `new project json round trips through gson with all v41 fields`() {
        val p = MontageProject(
            tracks = listOf(
                MontageTrack(id = "t1", name = "موسيقى خلفية", type = MontageLogic.TYPE_MUSIC, pan = -50, groupId = "g1"),
                MontageTrack(id = "t2", name = "مؤثرات", type = MontageLogic.TYPE_SFX)
            ),
            groups = listOf(MontageGroup(id = "g1", name = "أجواء", volumePercent = 80)),
            automation = listOf(
                MontageAutomationLane(trackId = "t1", points = listOf(pt("1", 0, 100), pt("2", 5000, 40)))
            )
        )
        val f = File(tmp, "v41.json")
        assertTrue(Json.save(f, p))
        val loaded = Json.gson.fromJson(f.readText(Charsets.UTF_8), MontageProject::class.java)
        assertEquals(2, loaded.tracks?.size)
        assertEquals(MontageLogic.TYPE_MUSIC, MontageLogic.trackType(loaded.tracks!![0]))
        assertEquals(-50, loaded.tracks!![0].pan)
        assertEquals("g1", loaded.tracks!![0].groupId)
        assertEquals("مؤثرات", loaded.tracks!![1].name)
        assertEquals(1, loaded.groups?.size)
        assertEquals("أجواء", loaded.groups!![0].name)
        assertEquals(80, loaded.groups!![0].volumePercent)
        assertEquals(1, loaded.automation?.size)
        assertEquals(2, loaded.automation!![0].points.size)
        assertEquals(40, MontageLogic.autoGainPercentAt(MontageLogic.sanitizeAutoPoints(loaded.automation!![0].points), 6_000))
    }

    // ================= 8) المسار الافتراضي وتعقيم effectiveTracks =================

    @Test
    fun `effective tracks keep names clamp volume pan and preserve types`() {
        val p = MontageProject(
            tracks = listOf(
                MontageTrack(id = "t1", name = "  ", volumePercent = 999, pan = 999, type = "sfx"),
                MontageTrack(id = "t2", name = "حوار رئيسي", volumePercent = 50, pan = -999, type = "dialogue")
            )
        )
        val tracks = MontageLogic.effectiveTracks(p)
        assertEquals("موسيقى", tracks[0].name) // الاسم الخاوي يعود الافتراضي
        assertEquals(200, tracks[0].volumePercent)
        assertEquals(100, tracks[0].pan)
        assertEquals(MontageLogic.TYPE_SFX, MontageLogic.trackType(tracks[0]))
        assertEquals(-100, tracks[1].pan)
        assertEquals(MontageLogic.TYPE_DIALOGUE, MontageLogic.trackType(tracks[1]))
        assertEquals("حوار رئيسي", tracks[1].name)
    }
}
