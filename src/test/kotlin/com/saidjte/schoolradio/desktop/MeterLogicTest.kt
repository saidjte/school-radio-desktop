package com.saidjte.schoolradio.desktop

import com.saidjte.schoolradio.desktop.data.MeterLogic
import com.saidjte.schoolradio.desktop.data.MeterZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** اختبارات مقياس البث المقطعي — منطق الرسم الحتمي قبل عرضه على الشاشة. */
class MeterLogicTest {

    @Test
    fun segments_constant() {
        assertEquals(24, MeterLogic.SEGMENTS)
    }

    @Test
    fun litSegments_boundaries() {
        assertEquals(0, MeterLogic.litSegments(0f))
        assertEquals(24, MeterLogic.litSegments(1f))
        assertEquals(12, MeterLogic.litSegments(0.5f))
        assertEquals(6, MeterLogic.litSegments(0.25f))
        assertEquals(18, MeterLogic.litSegments(0.75f))
    }

    @Test
    fun litSegments_clampsAbnormalValues() {
        assertEquals(0, MeterLogic.litSegments(-1f))
        assertEquals(0, MeterLogic.litSegments(-0.001f))
        assertEquals(0, MeterLogic.litSegments(Float.NaN))
        assertEquals(24, MeterLogic.litSegments(1.5f))
        assertEquals(24, MeterLogic.litSegments(Float.POSITIVE_INFINITY))
    }

    @Test
    fun zones_boundaries() {
        assertEquals(MeterZone.GREEN, MeterLogic.zoneOf(0))
        assertEquals(MeterZone.GREEN, MeterLogic.zoneOf(13))
        assertEquals(MeterZone.AMBER, MeterLogic.zoneOf(14))
        assertEquals(MeterZone.AMBER, MeterLogic.zoneOf(19))
        assertEquals(MeterZone.RED, MeterLogic.zoneOf(20))
        assertEquals(MeterZone.RED, MeterLogic.zoneOf(23))
    }

    @Test
    fun zones_coverAllSegmentsInOrder() {
        val zones = (0 until MeterLogic.SEGMENTS).map { MeterLogic.zoneOf(it) }
        assertEquals(14, zones.count { it == MeterZone.GREEN })
        assertEquals(6, zones.count { it == MeterZone.AMBER })
        assertEquals(4, zones.count { it == MeterZone.RED })
        // ترتيب تصاعدي: أخضر ثم كهرماني ثم أحمر
        val firstAmber = zones.indexOf(MeterZone.AMBER)
        val firstRed = zones.indexOf(MeterZone.RED)
        assertTrue(firstAmber in 1 until firstRed)
        assertEquals(zones.last(), MeterZone.RED)
    }

    @Test
    fun peak_risesInstantlyToLevel() {
        assertEquals(0.5f, MeterLogic.peakIndex(0.2f, 0.5f))
        assertEquals(1f, MeterLogic.peakIndex(0.9f, 1f))
    }

    @Test
    fun peak_decaysSlowlyButNeverBelowLevel() {
        val decayed = MeterLogic.peakIndex(0.5f, 0.2f)
        assertTrue(abs(decayed - 0.48f) < 1e-4f, "decayed=$decayed")
        // الانحسار لا ينزل تحت المستوى الحالي مهما تكرر
        var peak = 0.5f
        repeat(100) { peak = MeterLogic.peakIndex(peak, 0.2f) }
        assertTrue(peak >= 0.2f - 1e-6f, "peak=$peak")
    }

    @Test
    fun peak_handlesAbnormalInputs() {
        assertEquals(0.5f, MeterLogic.peakIndex(Float.NaN, 0.5f))
        assertEquals(0f, MeterLogic.peakIndex(Float.NaN, Float.NaN))
        // قيم شاذة تُقيَّد أولاً: ذروة سابقة فوق 1 تنحسر انحسارًا طبيعيًا
        assertTrue(abs(MeterLogic.peakIndex(2f, -1f) - 0.98f) < 1e-4f)
    }

    @Test
    fun peakSegment_mapsAndSuppressesSilence() {
        assertEquals(-1, MeterLogic.peakSegment(0f))
        assertEquals(-1, MeterLogic.peakSegment(0.01f))
        assertEquals(21, MeterLogic.peakSegment(0.9f))
        assertEquals(23, MeterLogic.peakSegment(1f))
        assertEquals(11, MeterLogic.peakSegment(0.5f))
    }
}
