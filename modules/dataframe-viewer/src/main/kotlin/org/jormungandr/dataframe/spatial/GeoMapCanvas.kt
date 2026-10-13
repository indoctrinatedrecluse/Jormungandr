package org.jormungandr.dataframe.spatial

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.ToolTipManager
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Interactive Swing Map & GIS Vector Canvas with Pan, Zoom, Layer Rendering,
 * and Bounding Box spatial filtering.
 */
class GeoMapCanvas : JComponent() {

    var layer: GeoLayer? = null
        set(value) {
            field = value
            selectedFeature = null
            repaint()
        }

    var selectedFeature: GeoFeature? = null
        private set

    var filterBoundingBox: GeoBoundingBox? = null
        set(value) {
            field = value
            repaint()
        }

    var onFeatureSelected: ((GeoFeature?) -> Unit)? = null

    // Viewport transform state
    private var centerLat: Double = 20.0
    private var centerLon: Double = 0.0
    private var zoomScale: Double = 2.5 // Screen pixels per degree longitude

    private var dragStartPoint: Point? = null
    private var isSelectingBBox = false
    private var bboxStartCoord: GeoPoint? = null
    private var hoveredFeature: GeoFeature? = null

    init {
        background = Color(230, 240, 250) // Ocean light blue
        ToolTipManager.sharedInstance().registerComponent(this)

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isShiftDown || isSelectingBBox) {
                    bboxStartCoord = screenToGeo(e.x, e.y)
                } else {
                    dragStartPoint = e.point
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                dragStartPoint = null
                if (bboxStartCoord != null) {
                    val endCoord = screenToGeo(e.x, e.y)
                    val start = bboxStartCoord!!
                    filterBoundingBox = GeoBoundingBox(
                        minLat = minOf(start.latitude, endCoord.latitude),
                        minLon = minOf(start.longitude, endCoord.longitude),
                        maxLat = maxOf(start.latitude, endCoord.latitude),
                        maxLon = maxOf(start.longitude, endCoord.longitude)
                    )
                    bboxStartCoord = null
                    repaint()
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                val start = dragStartPoint
                if (start != null && bboxStartCoord == null) {
                    val dx = e.x - start.x
                    val dy = e.y - start.y
                    centerLon -= dx / zoomScale
                    centerLat += dy / zoomScale
                    centerLat = centerLat.coerceIn(-85.0, 85.0)
                    centerLon = ((centerLon + 180.0) % 360.0) - 180.0
                    dragStartPoint = e.point
                    repaint()
                } else if (bboxStartCoord != null) {
                    repaint()
                }
            }

            override fun mouseClicked(e: MouseEvent) {
                val feat = findFeatureAt(e.x, e.y)
                selectedFeature = feat
                onFeatureSelected?.invoke(feat)
                repaint()
            }

            override fun mouseMoved(e: MouseEvent) {
                val feat = findFeatureAt(e.x, e.y)
                if (feat != hoveredFeature) {
                    hoveredFeature = feat
                    toolTipText = if (feat != null) {
                        "<b>${feat.name}</b> (${feat.geometryType})<br/>" +
                                feat.properties.entries.joinToString("<br/>") { "${it.key}: ${it.value}" }
                    } else null
                    repaint()
                }
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                val factor = if (e.wheelRotation < 0) 1.25 else 0.8
                zoomScale = (zoomScale * factor).coerceIn(0.5, 60.0)
                repaint()
            }
        }

        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
        addMouseWheelListener(mouseHandler)
    }

    fun setBBoxSelectionMode(enabled: Boolean) {
        this.isSelectingBBox = enabled
        cursor = if (enabled) Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        else Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    fun resetView() {
        centerLat = 20.0
        centerLon = 0.0
        zoomScale = 2.5
        filterBoundingBox = null
        selectedFeature = null
        repaint()
    }

    fun zoomIn() {
        zoomScale = (zoomScale * 1.3).coerceAtMost(60.0)
        repaint()
    }

    fun zoomOut() {
        zoomScale = (zoomScale * 0.77).coerceAtLeast(0.5)
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val w = width
        val h = height

        // 1. Ocean Background
        g2.color = Color(234, 243, 250)
        g2.fillRect(0, 0, w, h)

        // 2. Continents background outline (Stylized World Reference)
        drawContinentsReference(g2)

        // 3. Coordinate Graticule (Lat/Lon grid)
        drawGraticule(g2, w, h)

        // 4. Render Active Layer Features
        layer?.let { l ->
            for (feat in l.features) {
                drawFeature(g2, feat)
            }
        }

        // 5. Draw Bounding Box Filter (if active)
        filterBoundingBox?.let { bbox ->
            val p1 = geoToScreen(GeoPoint(bbox.maxLat, bbox.minLon))
            val p2 = geoToScreen(GeoPoint(bbox.minLat, bbox.maxLon))
            val bx = minOf(p1.x, p2.x)
            val by = minOf(p1.y, p2.y)
            val bw = abs(p2.x - p1.x)
            val bh = abs(p2.y - p1.y)

            g2.color = Color(211, 54, 130, 45) // Translucent magenta
            g2.fill(Rectangle2D.Double(bx, by, bw, bh))
            g2.color = Color(211, 54, 130)
            g2.stroke = BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, floatArrayOf(6f, 4f), 0f)
            g2.draw(Rectangle2D.Double(bx, by, bw, bh))
            g2.drawString("Filter BBox", (bx + 6).toInt(), (by + 16).toInt())
        }

        // 6. HUD / Scale & Info Overlay
        drawHud(g2, w, h)

        g2.dispose()
    }

    private fun drawGraticule(g2: Graphics2D, w: Int, h: Int) {
        g2.color = Color(215, 225, 235)
        g2.stroke = BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, floatArrayOf(4f, 4f), 0f)
        g2.font = Font("Segoe UI", Font.PLAIN, 10)

        // Longitude lines every 30 deg
        for (lon in -180..180 step 30) {
            val pt = geoToScreen(GeoPoint(0.0, lon.toDouble()))
            if (pt.x in 0.0..w.toDouble()) {
                g2.drawLine(pt.x.toInt(), 0, pt.x.toInt(), h)
                g2.drawString("${lon}°", pt.x.toInt() + 2, h - 8)
            }
        }

        // Latitude lines every 30 deg
        for (lat in -60..60 step 30) {
            val pt = geoToScreen(GeoPoint(lat.toDouble(), 0.0))
            if (pt.y in 0.0..h.toDouble()) {
                g2.drawLine(0, pt.y.toInt(), w, pt.y.toInt())
                g2.drawString("${lat}°", 4, pt.y.toInt() - 2)
            }
        }

        // Equator highlight
        val eq = geoToScreen(GeoPoint(0.0, 0.0))
        if (eq.y in 0.0..h.toDouble()) {
            g2.color = Color(180, 200, 220)
            g2.stroke = BasicStroke(1.5f)
            g2.drawLine(0, eq.y.toInt(), w, eq.y.toInt())
        }
    }

    private fun drawContinentsReference(g2: Graphics2D) {
        // Approximate continent landmass shapes for visual GIS context
        g2.color = Color(245, 243, 235) // Land fill
        val continents = listOf(
            // North America
            listOf(GeoPoint(70.0, -165.0), GeoPoint(60.0, -65.0), GeoPoint(25.0, -80.0), GeoPoint(15.0, -90.0), GeoPoint(30.0, -120.0), GeoPoint(60.0, -140.0)),
            // South America
            listOf(GeoPoint(12.0, -75.0), GeoPoint(-5.0, -35.0), GeoPoint(-40.0, -60.0), GeoPoint(-55.0, -70.0), GeoPoint(-20.0, -70.0)),
            // Eurasia
            listOf(GeoPoint(70.0, 10.0), GeoPoint(75.0, 140.0), GeoPoint(35.0, 120.0), GeoPoint(20.0, 80.0), GeoPoint(30.0, 35.0), GeoPoint(45.0, -5.0), GeoPoint(60.0, 5.0)),
            // Africa
            listOf(GeoPoint(35.0, -5.0), GeoPoint(30.0, 32.0), GeoPoint(12.0, 50.0), GeoPoint(-34.0, 20.0), GeoPoint(5.0, 10.0), GeoPoint(15.0, -17.0)),
            // Australia
            listOf(GeoPoint(-12.0, 130.0), GeoPoint(-25.0, 150.0), GeoPoint(-38.0, 145.0), GeoPoint(-35.0, 115.0), GeoPoint(-20.0, 115.0))
        )
        for (poly in continents) {
            val path = Path2D.Double()
            val first = geoToScreen(poly.first())
            path.moveTo(first.x, first.y)
            for (i in 1 until poly.size) {
                val pt = geoToScreen(poly[i])
                path.lineTo(pt.x, pt.y)
            }
            path.closePath()
            g2.fill(path)
            g2.color = Color(225, 220, 210)
            g2.stroke = BasicStroke(1.0f)
            g2.draw(path)
            g2.color = Color(245, 243, 235)
        }
    }

    private fun drawFeature(g2: Graphics2D, feat: GeoFeature) {
        val isSelected = (feat == selectedFeature)
        val isHovered = (feat == hoveredFeature)
        val inFilter = filterBoundingBox?.let { bbox ->
            feat.coordinates.any { bbox.contains(it) }
        } ?: true

        val alpha = if (inFilter) 255 else 60

        when (feat.geometryType) {
            SpatialGeometryType.POINT -> {
                val coord = feat.coordinates.firstOrNull() ?: return
                val pt = geoToScreen(coord)
                val radius = if (isSelected || isHovered) 8.0 else 5.5

                val baseColor = if (isSelected) Color(211, 54, 130) else Color(38, 139, 210)
                g2.color = Color(baseColor.red, baseColor.green, baseColor.blue, alpha)
                g2.fill(Ellipse2D.Double(pt.x - radius, pt.y - radius, radius * 2, radius * 2))

                g2.color = if (isSelected) Color(40, 40, 40) else Color(255, 255, 255, alpha)
                g2.stroke = BasicStroke(if (isSelected) 2.0f else 1.2f)
                g2.draw(Ellipse2D.Double(pt.x - radius, pt.y - radius, radius * 2, radius * 2))

                if (isSelected || isHovered) {
                    g2.color = Color(30, 30, 30)
                    g2.font = Font("Segoe UI", Font.BOLD, 11)
                    g2.drawString(feat.name, (pt.x + radius + 4).toInt(), (pt.y + 4).toInt())
                }
            }
            SpatialGeometryType.LINE_STRING -> {
                if (feat.coordinates.size < 2) return
                val path = Path2D.Double()
                val first = geoToScreen(feat.coordinates.first())
                path.moveTo(first.x, first.y)
                for (i in 1 until feat.coordinates.size) {
                    val p = geoToScreen(feat.coordinates[i])
                    path.lineTo(p.x, p.y)
                }
                val lineColor = if (isSelected) Color(211, 54, 130) else Color(133, 153, 0)
                g2.color = Color(lineColor.red, lineColor.green, lineColor.blue, alpha)
                g2.stroke = BasicStroke(if (isSelected) 3.5f else 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g2.draw(path)
            }
            SpatialGeometryType.POLYGON -> {
                if (feat.coordinates.size < 3) return
                val path = Path2D.Double()
                val first = geoToScreen(feat.coordinates.first())
                path.moveTo(first.x, first.y)
                for (i in 1 until feat.coordinates.size) {
                    val p = geoToScreen(feat.coordinates[i])
                    path.lineTo(p.x, p.y)
                }
                path.closePath()

                val polyColor = if (isSelected) Color(211, 54, 130) else Color(181, 137, 0)
                g2.color = Color(polyColor.red, polyColor.green, polyColor.blue, (alpha * 0.35).toInt())
                g2.fill(path)
                g2.color = Color(polyColor.red, polyColor.green, polyColor.blue, alpha)
                g2.stroke = BasicStroke(if (isSelected) 2.5f else 1.5f)
                g2.draw(path)
            }
        }
    }

    private fun drawHud(g2: Graphics2D, w: Int, h: Int) {
        g2.color = Color(50, 50, 50, 200)
        g2.font = Font("Segoe UI", Font.PLAIN, 11)
        val infoStr = String.format("Center: %.2f°N, %.2f°E | Zoom: %.1fx | WGS84 EPSG:4326", centerLat, centerLon, zoomScale)
        g2.drawString(infoStr, 12, 20)

        // Drag instruction
        g2.color = Color(120, 120, 120)
        g2.drawString("• Drag to Pan | Scroll to Zoom | Shift+Drag or Toggle BBox to Filter", 12, h - 12)
    }

    private fun geoToScreen(point: GeoPoint): Point2D.Double {
        val cx = width / 2.0
        val cy = height / 2.0
        val sx = cx + (point.longitude - centerLon) * zoomScale
        val sy = cy - (point.latitude - centerLat) * zoomScale
        return Point2D.Double(sx, sy)
    }

    private fun screenToGeo(sx: Int, sy: Int): GeoPoint {
        val cx = width / 2.0
        val cy = height / 2.0
        val lon = (((sx - cx) / zoomScale) + centerLon).coerceIn(-180.0, 180.0)
        val lat = (centerLat - ((sy - cy) / zoomScale)).coerceIn(-85.0, 85.0)
        return GeoPoint(lat, lon)
    }

    private fun findFeatureAt(mx: Int, my: Int): GeoFeature? {
        val feats = layer?.features ?: return null
        for (feat in feats) {
            when (feat.geometryType) {
                SpatialGeometryType.POINT -> {
                    val pt = geoToScreen(feat.coordinates.first())
                    if (hypot(mx - pt.x, my - pt.y) <= 12.0) return feat
                }
                SpatialGeometryType.LINE_STRING, SpatialGeometryType.POLYGON -> {
                    for (c in feat.coordinates) {
                        val pt = geoToScreen(c)
                        if (hypot(mx - pt.x, my - pt.y) <= 10.0) return feat
                    }
                }
            }
        }
        return null
    }
}
