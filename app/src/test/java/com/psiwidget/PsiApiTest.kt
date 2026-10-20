package com.psiwidget

import com.psiwidget.data.AppJson
import com.psiwidget.data.Band
import com.psiwidget.data.Metric
import com.psiwidget.data.PsiApi
import com.psiwidget.data.PsiSnapshot
import com.psiwidget.data.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PsiApiTest {
    private val fixture = javaClass.classLoader!!.getResource("psi.json")!!.readText()
    private val hourlyFixture = javaClass.classLoader!!.getResource("pm25.json")!!.readText()

    @Test
    fun parsesAllMetricsAndRegions() {
        val s = PsiApi.parse(fixture)
        assertEquals("2026-10-09T14:15:52+08:00", s.updated)
        assertEquals(186, s.value(Metric.PSI, Region.CENTRAL))
        assertEquals(128, s.value(Metric.PM25, Region.WEST))
        assertEquals(103, s.value(Metric.PM10, Region.NORTH))
        assertEquals(5, s.regionLocations.size)
        assertEquals(103.94, s.regionLocations.getValue("east").longitude, 1e-9)
    }

    @Test
    fun bandsPmBySubIndexNotConcentration() {
        val s = PsiApi.parse(fixture)
        // PM10 north is 103 µg/m³ but its sub-index is 77, so it is Moderate, not Unhealthy.
        assertEquals(Band.MODERATE, s.band(Metric.PM10, Region.NORTH))
        assertEquals(Band.UNHEALTHY, s.band(Metric.PSI, Region.NORTH))
    }

    @Test
    fun highestRegionPerMetric() {
        val s = PsiApi.parse(fixture)
        assertEquals(Region.CENTRAL, s.highest(Metric.PSI))
        assertEquals(Region.CENTRAL, s.highest(Metric.PM25))
    }

    @Test
    fun rejectsEmptyResponse() {
        assertThrows(IOException::class.java) {
            PsiApi.parse("""{"code":1,"data":{"items":[]},"errorMsg":"Data not found"}""")
        }
    }

    @Test
    fun bandBoundaries() {
        assertEquals(Band.GOOD, Band.of(50))
        assertEquals(Band.MODERATE, Band.of(51))
        assertEquals(Band.UNHEALTHY, Band.of(200))
        assertEquals(Band.VERY_UNHEALTHY, Band.of(300))
        assertEquals(Band.HAZARDOUS, Band.of(301))
    }

    @Test
    fun parsesHourlyPm25() {
        val (updated, values) = PsiApi.parseHourlyPm25(hourlyFixture)
        assertEquals("2026-10-20T12:30:42+08:00", updated)
        assertEquals(101, values["west"])
        val s = PsiApi.parse(fixture).copy(pm25Hourly = values, pm25HourlyUpdated = updated)
        assertEquals(44, s.value(Metric.PM25_1H, Region.NORTH))
        assertEquals(Region.WEST, s.highest(Metric.PM25_1H))
        assertEquals(updated, s.updated(Metric.PM25_1H))
        assertEquals("2026-10-09T14:15:52+08:00", s.updated(Metric.PSI))
    }

    @Test
    fun bandsHourlyPm25OnItsOwnScale() {
        val s = PsiApi.parse(fixture).copy(pm25Hourly = mapOf("north" to 55, "south" to 56, "west" to 251))
        assertEquals(Band.GOOD, s.band(Metric.PM25_1H, Region.NORTH))
        assertEquals(Band.MODERATE, s.band(Metric.PM25_1H, Region.SOUTH))
        assertEquals(Band.VERY_UNHEALTHY, s.band(Metric.PM25_1H, Region.WEST))
        assertEquals("Elevated", Metric.PM25_1H.bandLabel(Band.MODERATE))
        assertEquals("Moderate", Metric.PM25.bandLabel(Band.MODERATE))
    }

    @Test
    fun oldCachedSnapshotWithoutHourlyStillDecodes() {
        val s = PsiApi.parse(fixture)
        val json = AppJson.encodeToString(PsiSnapshot.serializer(), s)
            .replace(Regex(""","pm25Hourly":\{[^}]*\}"""), "")
        val decoded = AppJson.decodeFromString<PsiSnapshot>(json)
        assertEquals(null, decoded.value(Metric.PM25_1H, Region.NORTH))
    }
}
