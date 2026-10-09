package com.psiwidget

import com.psiwidget.data.Region
import com.psiwidget.location.RegionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionResolverTest {
    @Test
    fun picksNearestRegion() {
        assertEquals(Region.WEST, RegionResolver.nearest(1.3329, 103.7436)) // Jurong East
        assertEquals(Region.EAST, RegionResolver.nearest(1.3526, 103.9447)) // Tampines
        assertEquals(Region.NORTH, RegionResolver.nearest(1.4360, 103.7860)) // Woodlands
        assertEquals(Region.SOUTH, RegionResolver.nearest(1.2644, 103.8222)) // HarbourFront
        assertEquals(Region.CENTRAL, RegionResolver.nearest(1.3508, 103.8485)) // Bishan
    }

    @Test
    fun noRegionOutsideSingapore() {
        assertNull(RegionResolver.nearest(3.1390, 101.6869)) // Kuala Lumpur
    }
}
