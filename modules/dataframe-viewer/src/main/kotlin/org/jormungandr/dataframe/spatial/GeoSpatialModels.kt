package org.jormungandr.dataframe.spatial

/**
 * Geometric primitives supported by the Spatial & GeoData Studio.
 */
enum class SpatialGeometryType {
    POINT,
    LINE_STRING,
    POLYGON
}

/**
 * Geographic coordinate pair in standard WGS84 (EPSG:4326).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90, got: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180, got: $longitude" }
    }
}

/**
 * Geographic bounding box defining a spatial query region.
 */
data class GeoBoundingBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double
) {
    fun contains(point: GeoPoint): Boolean {
        return point.latitude in minLat..maxLat && point.longitude in minLon..maxLon
    }
}

/**
 * A vector spatial feature with geometries and attribute metadata.
 */
data class GeoFeature(
    val id: String,
    val name: String,
    val geometryType: SpatialGeometryType,
    val coordinates: List<GeoPoint>,
    val properties: Map<String, Any> = emptyMap()
)

/**
 * A named geospatial layer containing multiple features.
 */
data class GeoLayer(
    val id: String,
    val name: String,
    val features: List<GeoFeature>,
    val description: String = ""
)
