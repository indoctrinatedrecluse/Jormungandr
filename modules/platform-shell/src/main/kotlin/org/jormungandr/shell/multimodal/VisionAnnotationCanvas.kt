package org.jormungandr.shell.multimodal

import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.ToolTipManager

/**
 * Interactive Swing Computer Vision Bounding Box Canvas.
 * Renders normalized bounding boxes, confidence tags, hover halos, and selection handles.
 */
class VisionAnnotationCanvas : JComponent() {

    var boundingBoxes: List<BoundingBox> = emptyList()
        set(value) {
            field = value
            selectedBox = null
            repaint()
        }

    var selectedBox: BoundingBox? = null
        private set

    var onBoxSelected: ((BoundingBox?) -> Unit)? = null

    private var hoveredBox: BoundingBox? = null
    var activeClassFilter: String? = null
        set(value) {
            field = value
            repaint()
        }

    init {
        background = Color(24, 24, 28) // Deep dark canvas
        ToolTipManager.sharedInstance().registerComponent(this)

        val mouseHandler = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val box = findBoxAt(e.x, e.y)
                selectedBox = box
                onBoxSelected?.invoke(box)
                repaint()
            }

            override fun mouseMoved(e: MouseEvent) {
                val box = findBoxAt(e.x, e.y)
                if (box != hoveredBox) {
                    hoveredBox = box
                    toolTipText = if (box != null) {
                        "<b>${box.label}</b> (Conf: ${String.format("%.1f%%", box.confidence * 100)})"
                    } else null
                    repaint()
                }
            }
        }

        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val w = width
        val h = height

        // 1. Synthetic Vision Frame Background (Simulated camera sensor / aerial view)
        drawSimulatedScene(g2, w, h)

        // 2. Draw Bounding Boxes
        for (box in boundingBoxes) {
            val matchesFilter = activeClassFilter == null || box.label.equals(activeClassFilter, ignoreCase = true)
            val isSelected = (box == selectedBox)
            val isHovered = (box == hoveredBox)

            val bx = box.xMin * w
            val by = box.yMin * h
            val bw = (box.xMax - box.xMin) * w
            val bh = (box.yMax - box.yMin) * h

            val alpha = if (matchesFilter) 255 else 40
            val c = box.color

            // Box Fill Alpha
            g2.color = Color(c.red, c.green, c.blue, if (isSelected || isHovered) 60 else (alpha * 0.15).toInt())
            g2.fill(Rectangle2D.Double(bx, by, bw, bh))

            // Box Outline
            g2.color = Color(c.red, c.green, c.blue, alpha)
            g2.stroke = BasicStroke(if (isSelected || isHovered) 2.5f else 1.8f)
            g2.draw(Rectangle2D.Double(bx, by, bw, bh))

            // Tag Header Badge
            if (matchesFilter) {
                val tagText = "${box.label} ${String.format("%.2f", box.confidence)}"
                g2.font = Font("Segoe UI", Font.BOLD, 10)
                val fm = g2.fontMetrics
                val textWidth = fm.stringWidth(tagText)
                val tagHeight = 16

                val tagY = (by - tagHeight).coerceAtLeast(0.0)
                g2.color = Color(c.red, c.green, c.blue, 220)
                g2.fill(RoundRectangle2D.Double(bx, tagY, (textWidth + 8).toDouble(), tagHeight.toDouble(), 4.0, 4.0))

                g2.color = Color.WHITE
                g2.drawString(tagText, (bx + 4).toInt(), (tagY + 12).toInt())
            }
        }

        // 3. Grid / Reticle Overlay
        g2.color = Color(255, 255, 255, 30)
        g2.stroke = BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, floatArrayOf(4f, 4f), 0f)
        g2.drawLine(w / 2, 0, w / 2, h)
        g2.drawLine(0, h / 2, w, h / 2)

        g2.dispose()
    }

    private fun drawSimulatedScene(g2: Graphics2D, w: Int, h: Int) {
        // Gradient asphalt & skyline representation
        val grad = GradientPaint(0f, 0f, Color(35, 42, 54), 0f, h.toFloat(), Color(20, 24, 30))
        g2.paint = grad
        g2.fillRect(0, 0, w, h)

        // Road lane perspective markings
        g2.color = Color(60, 68, 80)
        val road = java.awt.Polygon(
            intArrayOf((w * 0.35).toInt(), (w * 0.65).toInt(), w, 0),
            intArrayOf((h * 0.4).toInt(), (h * 0.4).toInt(), h, h),
            4
        )
        g2.fill(road)

        // Lane dashed divider
        g2.color = Color(218, 165, 32, 120)
        g2.stroke = BasicStroke(2.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, floatArrayOf(12f, 8f), 0f)
        g2.drawLine(w / 2, (h * 0.4).toInt(), w / 2, h)
    }

    private fun findBoxAt(mx: Int, my: Int): BoundingBox? {
        val w = width.toDouble()
        val h = height.toDouble()
        for (box in boundingBoxes.reversed()) {
            val bx = box.xMin * w
            val by = box.yMin * h
            val bw = (box.xMax - box.xMin) * w
            val bh = (box.yMax - box.yMin) * h
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
                return box
            }
        }
        return null
    }
}
