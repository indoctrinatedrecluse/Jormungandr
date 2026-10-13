package org.jormungandr.dataframe.spatial

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Flagship Spatial & GeoData Studio Panel for Jörmungandr.
 * Inspects geospatial vector layers (Points, Lines, Polygons), performs bounding-box
 * spatial queries, computes centroids, and exports GeoPandas / GeoJSON code.
 */
class GeoSpatialStudioPanel : JPanel(BorderLayout()) {

    private val mapCanvas = GeoMapCanvas()

    // Sample layers
    private val layers = listOf(
        createMegacitiesLayer(),
        createShippingRoutesLayer(),
        createEconomicZonesLayer()
    )

    private val layerCombo = ComboBox(layers.map { it.name }.toTypedArray())
    private val featureCountLabel = JBLabel("Features: 0")
    private val activeLayerBadge = JBLabel("WGS84 EPSG:4326")

    // Property inspector
    private val inspectorTitle = JBLabel("<b>Feature Details</b>")
    private val inspectorText = JBTextArea(8, 22).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font("Segoe UI", Font.PLAIN, 12)
        text = "Click a feature on the map to inspect its spatial properties."
    }

    private val bboxToggleBtn = JToggleButton("⬚ Draw BBox Filter")

    init {
        setupUI()
        setupListeners()
        selectLayer(layers.first())
    }

    private fun setupUI() {
        // 1. Top Control Toolbar
        val topToolbar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftTools.add(JBLabel("<b>Layer:</b>"))
        leftTools.add(layerCombo)
        leftTools.add(createPill(featureCountLabel, Color(38, 139, 210)))
        leftTools.add(createPill(activeLayerBadge, Color(133, 153, 0)))

        val rightTools = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val zoomInBtn = JButton("+").apply {
            isFocusPainted = false
            toolTipText = "Zoom In"
            addActionListener { mapCanvas.zoomIn() }
        }
        val zoomOutBtn = JButton("-").apply {
            isFocusPainted = false
            toolTipText = "Zoom Out"
            addActionListener { mapCanvas.zoomOut() }
        }
        val resetBtn = JButton("Reset").apply {
            isFocusPainted = false
            toolTipText = "Reset Center & Zoom"
            addActionListener {
                mapCanvas.resetView()
                bboxToggleBtn.isSelected = false
            }
        }
        val exportBtn = JButton("📋 Export GeoPandas").apply {
            isFocusPainted = false
            addActionListener { exportGeoPandasCode() }
        }

        rightTools.add(bboxToggleBtn)
        rightTools.add(zoomInBtn)
        rightTools.add(zoomOutBtn)
        rightTools.add(resetBtn)
        rightTools.add(exportBtn)

        topToolbar.add(leftTools, BorderLayout.WEST)
        topToolbar.add(rightTools, BorderLayout.EAST)
        add(topToolbar, BorderLayout.NORTH)

        // 2. Main Center Area with Map & Right Sidebar Inspector
        val centerSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            resizeWeight = 0.75
            dividerSize = 4
        }

        val mapContainer = JPanel(BorderLayout()).apply {
            border = LineBorder(Color(220, 220, 220), 1)
            add(mapCanvas, BorderLayout.CENTER)
        }
        centerSplit.leftComponent = mapContainer

        // Right Inspector Sidebar
        val sidebar = JPanel(BorderLayout(8, 8)).apply {
            border = EmptyBorder(8, 8, 8, 8)
            background = Color(250, 250, 250)
            preferredSize = Dimension(280, 400)
        }

        val sidebarTop = JPanel(BorderLayout(4, 4)).apply { isOpaque = false }
        sidebarTop.add(inspectorTitle, BorderLayout.NORTH)
        val centroidBtn = JButton("📍 Compute Layer Centroid").apply {
            isFocusPainted = false
            addActionListener { computeCentroid() }
        }
        sidebarTop.add(centroidBtn, BorderLayout.SOUTH)

        sidebar.add(sidebarTop, BorderLayout.NORTH)
        sidebar.add(JBScrollPane(inspectorText).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(4, 4, 4, 4))
        }, BorderLayout.CENTER)

        centerSplit.rightComponent = sidebar
        add(centerSplit, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        layerCombo.addActionListener {
            val selectedName = layerCombo.selectedItem as? String ?: return@addActionListener
            val l = layers.firstOrNull { it.name == selectedName } ?: return@addActionListener
            selectLayer(l)
        }

        bboxToggleBtn.addActionListener {
            mapCanvas.setBBoxSelectionMode(bboxToggleBtn.isSelected)
            if (!bboxToggleBtn.isSelected) {
                mapCanvas.filterBoundingBox = null
            }
        }

        mapCanvas.onFeatureSelected = { feat ->
            if (feat != null) {
                val sb = StringBuilder()
                sb.append("ID: ${feat.id}\n")
                sb.append("Name: ${feat.name}\n")
                sb.append("Type: ${feat.geometryType}\n")
                sb.append("Vertices: ${feat.coordinates.size}\n\n")
                sb.append("Properties:\n")
                for ((k, v) in feat.properties) {
                    sb.append(" • $k: $v\n")
                }
                sb.append("\nCoordinates:\n")
                for ((idx, c) in feat.coordinates.withIndex()) {
                    sb.append(String.format(" [%d] %.4f°N, %.4f°E\n", idx + 1, c.latitude, c.longitude))
                }
                inspectorText.text = sb.toString()
                inspectorText.caretPosition = 0
            } else {
                inspectorText.text = "Click a feature on the map to inspect its spatial properties."
            }
        }
    }

    private fun selectLayer(l: GeoLayer) {
        mapCanvas.layer = l
        featureCountLabel.text = "Features: ${l.features.size}"
        inspectorText.text = "Active Layer: ${l.name}\n${l.description}\n\nTotal Geometries: ${l.features.size}"
    }

    private fun computeCentroid() {
        val l = mapCanvas.layer ?: return
        val allPoints = l.features.flatMap { it.coordinates }
        if (allPoints.isEmpty()) return

        val avgLat = allPoints.map { it.latitude }.average()
        val avgLon = allPoints.map { it.longitude }.average()

        Messages.showInfoMessage(
            String.format("Calculated Mean Centroid for '%s':\n\nLatitude: %.4f° N\nLongitude: %.4f° E\n(Point Count: %d)",
                l.name, avgLat, avgLon, allPoints.size),
            "Centroid Computed"
        )
    }

    private fun exportGeoPandasCode() {
        val l = mapCanvas.layer ?: return
        val code = """
# ==============================================================================
# Jörmungandr Spatial & GeoData Studio - GeoPandas Pipeline
# ==============================================================================
import geopandas as gpd
from shapely.geometry import Point, LineString, Polygon

# 1. Reconstruct layer features
data = {
    "name": [${l.features.joinToString(", ") { "\"${it.name}\"" }}],
    "geometry": [
${l.features.joinToString(",\n") { feat ->
    when (feat.geometryType) {
        SpatialGeometryType.POINT -> "        Point(${feat.coordinates.first().longitude}, ${feat.coordinates.first().latitude})"
        SpatialGeometryType.LINE_STRING -> "        LineString([${feat.coordinates.joinToString(", ") { "(${it.longitude}, ${it.latitude})" }}])"
        SpatialGeometryType.POLYGON -> "        Polygon([${feat.coordinates.joinToString(", ") { "(${it.longitude}, ${it.latitude})" }}])"
    }
}}
    ]
}

# 2. Build GeoDataFrame in EPSG:4326 (WGS84)
gdf = gpd.GeoDataFrame(data, crs="EPSG:4326")
print(f"Loaded {len(gdf)} spatial features into GeoDataFrame:")
print(gdf.head())

# 3. Export to GeoJSON & Spatial Bounding Box Filter
# gdf.to_file("./output/layer.geojson", driver="GeoJSON")
""".trimIndent()

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(code), null)
        Messages.showInfoMessage(
            "GeoPandas & Shapely spatial pipeline snippet has been copied to your clipboard!",
            "GeoPandas Code Exported"
        )
    }

    private fun createPill(label: JBLabel, color: Color): JPanel {
        return JPanel(FlowLayout(FlowLayout.CENTER, 4, 1)).apply {
            background = Color(color.red, color.green, color.blue, 30)
            border = CompoundBorder(LineBorder(color, 1), EmptyBorder(1, 6, 1, 6))
            label.foreground = Color(30, 30, 30)
            label.font = Font("Segoe UI", Font.BOLD, 10)
            add(label)
        }
    }

    private fun createMegacitiesLayer(): GeoLayer {
        val feats = listOf(
            GeoFeature("city-tokyo", "Tokyo", SpatialGeometryType.POINT, listOf(GeoPoint(35.6762, 139.6503)), mapOf("population" to "37.4M", "country" to "Japan")),
            GeoFeature("city-ny", "New York", SpatialGeometryType.POINT, listOf(GeoPoint(40.7128, -74.0060)), mapOf("population" to "19.5M", "country" to "USA")),
            GeoFeature("city-london", "London", SpatialGeometryType.POINT, listOf(GeoPoint(51.5074, -0.1278)), mapOf("population" to "9.5M", "country" to "UK")),
            GeoFeature("city-paris", "Paris", SpatialGeometryType.POINT, listOf(GeoPoint(48.8566, 2.3522)), mapOf("population" to "11.1M", "country" to "France")),
            GeoFeature("city-mumbai", "Mumbai", SpatialGeometryType.POINT, listOf(GeoPoint(19.0760, 72.8777)), mapOf("population" to "21.0M", "country" to "India")),
            GeoFeature("city-sydney", "Sydney", SpatialGeometryType.POINT, listOf(GeoPoint(-33.8688, 151.2093)), mapOf("population" to "5.3M", "country" to "Australia")),
            GeoFeature("city-cairo", "Cairo", SpatialGeometryType.POINT, listOf(GeoPoint(30.0444, 31.2357)), mapOf("population" to "20.9M", "country" to "Egypt")),
            GeoFeature("city-sp", "São Paulo", SpatialGeometryType.POINT, listOf(GeoPoint(-23.5505, -46.6333)), mapOf("population" to "22.4M", "country" to "Brazil"))
        )
        return GeoLayer("megacities", "Global Megacity Hubs", feats, "Global high-density metropolitan centroids")
    }

    private fun createShippingRoutesLayer(): GeoLayer {
        val feats = listOf(
            GeoFeature(
                "route-transpacific",
                "Trans-Pacific Route (Tokyo -> San Francisco)",
                SpatialGeometryType.LINE_STRING,
                listOf(GeoPoint(35.6762, 139.6503), GeoPoint(42.0, 175.0), GeoPoint(37.7749, -122.4194)),
                mapOf("mode" to "Maritime", "traffic" to "Very High")
            ),
            GeoFeature(
                "route-transatlantic",
                "North Atlantic Corridor (London -> New York)",
                SpatialGeometryType.LINE_STRING,
                listOf(GeoPoint(51.5074, -0.1278), GeoPoint(48.0, -35.0), GeoPoint(40.7128, -74.0060)),
                mapOf("mode" to "Aviation/Maritime", "traffic" to "High")
            ),
            GeoFeature(
                "route-suez",
                "Suez Maritime Highway (Mumbai -> Cairo -> London)",
                SpatialGeometryType.LINE_STRING,
                listOf(GeoPoint(19.0760, 72.8777), GeoPoint(12.5, 45.0), GeoPoint(29.9, 32.5), GeoPoint(36.0, -5.3), GeoPoint(51.5074, -0.1278)),
                mapOf("mode" to "Maritime", "traffic" to "Critical")
            )
        )
        return GeoLayer("shipping_routes", "Trans-Continental Shipping Lanes", feats, "Major global logistics corridors")
    }

    private fun createEconomicZonesLayer(): GeoLayer {
        val feats = listOf(
            GeoFeature(
                "zone-euro",
                "European Core Economic Corridor",
                SpatialGeometryType.POLYGON,
                listOf(GeoPoint(52.5, 4.0), GeoPoint(52.5, 13.5), GeoPoint(45.5, 10.0), GeoPoint(46.0, 2.0), GeoPoint(52.5, 4.0)),
                mapOf("type" to "Trade Zone", "gdp" to "$12T")
            ),
            GeoFeature(
                "zone-silicon",
                "Northern California Tech Corridor",
                SpatialGeometryType.POLYGON,
                listOf(GeoPoint(38.0, -122.8), GeoPoint(38.0, -121.5), GeoPoint(37.0, -121.5), GeoPoint(37.0, -122.8), GeoPoint(38.0, -122.8)),
                mapOf("type" to "Innovation Zone", "gdp" to "$1.2T")
            )
        )
        return GeoLayer("economic_zones", "Special Economic Zones", feats, "Major regional economic bounding areas")
    }
}
