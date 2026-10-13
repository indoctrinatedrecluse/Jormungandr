package org.jormungandr.dataframe.spatial

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GeoSpatialTest {

    @Test
    fun `test GeoPoint validates valid coordinates and rejects out-of-bounds`() {
        val validPoint = GeoPoint(35.6762, 139.6503)
        assertEquals(35.6762, validPoint.latitude)
        assertEquals(139.6503, validPoint.longitude)

        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(95.0, 0.0) // Invalid lat > 90
        }
        assertThrows(IllegalArgumentException::class.java) {
            GeoPoint(0.0, 200.0) // Invalid lon > 180
        }
    }

    @Test
    fun `test GeoBoundingBox accurately tests spatial point containment`() {
        val bbox = GeoBoundingBox(
            minLat = 10.0,
            minLon = -80.0,
            maxLat = 50.0,
            maxLon = -50.0
        )

        val insidePt = GeoPoint(40.7128, -74.0060) // New York
        assertTrue(bbox.contains(insidePt), "New York must be inside North American bbox")

        val outsidePt = GeoPoint(35.6762, 139.6503) // Tokyo
        assertFalse(bbox.contains(outsidePt), "Tokyo must be outside North American bbox")
    }

    @Test
    fun `test GeoSpatialStudioPanel initializes layers cleanly`() {
        val panel = GeoSpatialStudioPanel()
        assertNotNull(panel)
        assertTrue(panel.componentCount >= 1)
    }
}
