package org.jormungandr.database.vectordb

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import javax.swing.JComponent
import javax.swing.ToolTipManager
import kotlin.math.abs

/**
 * Interactive Swing 2D Canvas for High-Dimensional Vector Projections (PCA / UMAP).
 * Visualizes embedding clusters, query points, nearest-neighbor links, and similarity scores.
 */
class VectorProjectionCanvas : JComponent() {

    private var points: List<ProjectedPoint2D> = emptyList()
    private var highlightedPoints: Set<String> = emptySet()
    private var hoveredPoint: ProjectedPoint2D? = null

    var onPointSelected: ((VectorDocument) -> Unit)? = null

    // Cluster color palette (Solarized & Cyberpunk friendly)
    private val clusterColors = arrayOf(
        Color(38, 139, 210),   // Blue
        Color(42, 161, 152),   // Cyan
        Color(133, 153, 0),    // Green
        Color(181, 137, 0),    // Yellow
        Color(203, 75, 22),    // Orange
        Color(211, 54, 130),   // Magenta
        Color(108, 113, 196)   // Violet
    )

    init {
        background = Color(250, 248, 240) // Solarized Base3
        ToolTipManager.sharedInstance().registerComponent(this)

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val pt = findPointAt(e.x, e.y)
                if (pt != null && !pt.isQuery) {
                    onPointSelected?.invoke(pt.document)
                }
            }
        })

        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val pt = findPointAt(e.x, e.y)
                if (pt != hoveredPoint) {
                    hoveredPoint = pt
                    toolTipText = if (pt != null) {
                        if (pt.isQuery) "Query Vector"
                        else "<b>${pt.document.id}</b>: ${pt.document.text.take(80)}...<br/>Cluster: ${pt.clusterId}"
                    } else null
                    repaint()
                }
            }
        })
    }

    fun setProjection(newPoints: List<ProjectedPoint2D>, topKDocIds: Set<String> = emptySet()) {
        this.points = newPoints
        this.highlightedPoints = topKDocIds
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val w = width
        val h = height

        // Background
        g2.color = Color(248, 246, 238)
        g2.fillRect(0, 0, w, h)

        // Grid lines & Axes
        g2.color = Color(225, 220, 205)
        g2.stroke = BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, floatArrayOf(4f, 4f), 0f)
        val cx = w / 2
        val cy = h / 2
        g2.drawLine(cx, 0, cx, h)
        g2.drawLine(0, cy, w, cy)

        val padding = 40
        val scaleX = (w - padding * 2) / 2.0
        val scaleY = (h - padding * 2) / 2.0

        val queryPt = points.firstOrNull { it.isQuery }

        // Draw connections from query vector to nearest neighbors
        if (queryPt != null) {
            val qScreenX = cx + (queryPt.x * scaleX)
            val qScreenY = cy - (queryPt.y * scaleY)

            g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, floatArrayOf(6f, 4f), 0f)
            g2.color = Color(211, 54, 130, 180) // Violet connector

            for (p in points) {
                if (!p.isQuery && highlightedPoints.contains(p.document.id)) {
                    val px = cx + (p.x * scaleX)
                    val py = cy - (p.y * scaleY)
                    g2.draw(Line2D.Double(qScreenX, qScreenY, px, py))
                }
            }
        }

        // Draw document points
        for (p in points) {
            val screenX = cx + (p.x * scaleX)
            val screenY = cy - (p.y * scaleY)
            val isHovered = (p == hoveredPoint)
            val isHighlighted = highlightedPoints.contains(p.document.id)

            if (p.isQuery) {
                // Draw Query Vector (Gold Star / Crosshair)
                g2.color = Color(218, 165, 32) // Goldenrod
                val starRadius = if (isHovered) 12.0 else 9.0
                drawStar(g2, screenX, screenY, starRadius, starRadius / 2.2)

                g2.color = Color(80, 50, 0)
                g2.font = Font("Segoe UI", Font.BOLD, 11)
                g2.drawString("Query", (screenX + 12).toInt(), (screenY + 4).toInt())
            } else {
                val radius = when {
                    isHovered -> 10.0
                    isHighlighted -> 8.0
                    else -> 6.0
                }
                val color = clusterColors[abs(p.clusterId) % clusterColors.size]

                // Halo glow for highlighted top-k nearest neighbors
                if (isHighlighted) {
                    g2.color = Color(color.red, color.green, color.blue, 60)
                    g2.fill(Ellipse2D.Double(screenX - radius - 5, screenY - radius - 5, (radius + 5) * 2, (radius + 5) * 2))
                }

                // Point body
                g2.color = if (isHighlighted) color else Color(color.red, color.green, color.blue, 210)
                g2.fill(Ellipse2D.Double(screenX - radius, screenY - radius, radius * 2, radius * 2))

                // Outline
                g2.color = if (isHighlighted || isHovered) Color(30, 30, 30) else Color(100, 100, 100, 150)
                g2.stroke = BasicStroke(if (isHighlighted || isHovered) 2.0f else 1.0f)
                g2.draw(Ellipse2D.Double(screenX - radius, screenY - radius, radius * 2, radius * 2))

                if (isHighlighted || isHovered) {
                    g2.color = Color(40, 40, 40)
                    g2.font = Font("Segoe UI", Font.PLAIN, 10)
                    g2.drawString(p.document.id, (screenX + radius + 4).toInt(), (screenY + 3).toInt())
                }
            }
        }

        // Legend
        drawLegend(g2, w, h)

        g2.dispose()
    }

    private fun drawLegend(g2: Graphics2D, w: Int, h: Int) {
        val lx = 16
        val ly = h - 28
        g2.font = Font("Segoe UI", Font.PLAIN, 11)
        g2.color = Color(100, 100, 100)
        g2.drawString("• PCA 2D Projection  |  ⭐ Query Vector  |  ⚪ Highlighted: Top-K Matches", lx, ly)
    }

    private fun drawStar(g2: Graphics2D, cx: Double, cy: Double, outerR: Double, innerR: Double) {
        val path = Path2D.Double()
        val points = 5
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = i * Math.PI / points - Math.PI / 2
            val x = cx + r * kotlin.math.cos(angle)
            val y = cy + r * kotlin.math.sin(angle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.closePath()
        g2.fill(path)
        g2.color = Color(140, 100, 0)
        g2.stroke = BasicStroke(1.5f)
        g2.draw(path)
    }

    private fun findPointAt(mx: Int, my: Int): ProjectedPoint2D? {
        val w = width
        val h = height
        val cx = w / 2
        val cy = h / 2
        val padding = 40
        val scaleX = (w - padding * 2) / 2.0
        val scaleY = (h - padding * 2) / 2.0

        for (p in points) {
            val screenX = cx + (p.x * scaleX)
            val screenY = cy - (p.y * scaleY)
            val dist = kotlin.math.hypot(mx - screenX, my - screenY)
            if (dist <= 12.0) {
                return p
            }
        }
        return null
    }
}
