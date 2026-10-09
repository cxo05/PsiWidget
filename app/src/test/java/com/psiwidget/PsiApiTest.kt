package com.psiwidget

import com.psiwidget.data.Band
import com.psiwidget.data.Metric
import com.psiwidget.data.PsiApi
import com.psiwidget.data.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class PsiApiTest {
    private val fixture = javaClass.classLoader!!.getResource("psi.json")!!.readText()

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
}
