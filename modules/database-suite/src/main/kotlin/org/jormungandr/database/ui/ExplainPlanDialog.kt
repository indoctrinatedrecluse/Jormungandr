/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.database.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import org.jormungandr.database.engine.ExplainPlanNode
import org.jormungandr.database.engine.ExplainPlanResult
import java.awt.*
import java.awt.geom.CubicCurve2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import kotlin.math.max

/**
 * Visual Query Execution Plan & EXPLAIN ANALYZE visualizer dialog.
 * Renders interactive operator flow DAGs, hierarchical execution trees, and cost/scan warnings.
 */
class ExplainPlanDialog(
    project: Project? = null,
    private var result: ExplainPlanResult,
    private val onReAnalyze: ((Boolean) -> ExplainPlanResult)? = null
) : DialogWrapper(project, true) {

    private val headerLabel = JBLabel("")
    private val tabbedPane = JBTabbedPane()
    private val dagCanvas = ExecutionPlanDagCanvas(result.rootNodes)
    private val rawArea = JBTextArea(result.rawText)
    private val sqlArea = JBTextArea(result.sql)
    private val treeRoot = DefaultMutableTreeNode("Query Plan")
    private val treeModel = DefaultTreeModel(treeRoot)
    private val planTree = Tree(treeModel)

    init {
        title = "Visual Execution Plan Analysis (${result.dialect})"
        init()
        refreshViews()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(8, 8)).apply {
            preferredSize = Dimension(820, 560)
            border = EmptyBorder(8, 8, 8, 8)
        }

        // Header info bar
        val header = JPanel(BorderLayout()).apply {
            background = JBColor(Color(248, 250, 252), Color(30, 41, 59))
            border = CompoundBorder(
                LineBorder(JBColor(Color(226, 232, 240), Color(51, 65, 85)), 1),
                EmptyBorder(8, 12, 8, 12)
            )
        }

        header.add(headerLabel, BorderLayout.WEST)

        if (onReAnalyze != null) {
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
            val reAnalyzeBtn = JButton("⚡ Run EXPLAIN ANALYZE").apply {
                font = font.deriveFont(Font.BOLD, 11f)
                toolTipText = "Execute query with engine profiling and actual runtime timings"
                addActionListener {
                    val updated = onReAnalyze.invoke(true)
                    result = updated
                    refreshViews()
                }
            }
            actions.add(reAnalyzeBtn)
            header.add(actions, BorderLayout.EAST)
        }

        root.add(header, BorderLayout.NORTH)

        // 1. Interactive DAG Flow Canvas
        tabbedPane.addTab("🗺 Visual DAG Flow", JBScrollPane(dagCanvas).apply {
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBar.unitIncrement = 16
        })

        // 2. Visual Execution Tree
        planTree.apply {
            isRootVisible = false
            showsRootHandles = true
            cellRenderer = object : DefaultTreeCellRenderer() {
                override fun getTreeCellRendererComponent(
                    tree: JTree?,
                    value: Any?,
                    sel: Boolean,
                    expanded: Boolean,
                    leaf: Boolean,
                    row: Int,
                    hasFocus: Boolean
                ): Component {
                    super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
                    val uo = (value as? DefaultMutableTreeNode)?.userObject as? ExplainPlanNode
                    if (uo != null) {
                        text = "${uo.title}: ${uo.detail}"
                        if (uo.isScanWarning) {
                            foreground = Color(194, 65, 12)
                            font = font.deriveFont(Font.BOLD)
                        } else {
                            foreground = if (sel) textSelectionColor else textNonSelectionColor
                        }
                    }
                    return this
                }
            }
        }
        tabbedPane.addTab("🌳 Plan Tree", JBScrollPane(planTree))

        // 3. Raw Text Output
        rawArea.apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 12)
            background = JBColor(Color(248, 250, 252), Color(15, 23, 42))
        }
        tabbedPane.addTab("📄 Raw Engine Output", JBScrollPane(rawArea))

        // 4. SQL Query
        sqlArea.apply {
            isEditable = false
            font = Font("Monospaced", Font.PLAIN, 12)
            background = JBColor(Color(248, 250, 252), Color(15, 23, 42))
        }
        tabbedPane.addTab("🔍 Query Text", JBScrollPane(sqlArea))

        root.add(tabbedPane, BorderLayout.CENTER)
        return root
    }

    private fun refreshViews() {
        val scanCount = countWarnings(result.rootNodes)
        val warningBadge = if (scanCount > 0) " | ⚠️ $scanCount Full Table Scans detected" else " | ✓ Optimized"
        headerLabel.text = "⏱ Analysis Duration: ${result.durationMs} ms$warningBadge"
        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 12f)
        headerLabel.foreground = if (scanCount > 0) Color(194, 65, 12) else Color(22, 101, 52)

        rawArea.text = result.rawText
        sqlArea.text = result.sql
        dagCanvas.setNodes(result.rootNodes)

        treeRoot.removeAllChildren()
        for (node in result.rootNodes) {
            treeRoot.add(buildTreeNode(node))
        }
        treeModel.reload()
        for (i in 0 until planTree.rowCount) {
            planTree.expandRow(i)
        }
    }

    private fun buildTreeNode(node: ExplainPlanNode): DefaultMutableTreeNode {
        val tn = DefaultMutableTreeNode(node)
        for (child in node.children) {
            tn.add(buildTreeNode(child))
        }
        return tn
    }

    private fun countWarnings(nodes: List<ExplainPlanNode>): Int {
        var count = 0
        for (n in nodes) {
            if (n.isScanWarning) count++
            count += countWarnings(n.children)
        }
        return count
    }
}

/**
 * Visual canvas rendering execution plan operators as a directed DAG diagram.
 */
class ExecutionPlanDagCanvas(
    private var rootNodes: List<ExplainPlanNode>
) : JPanel() {

    private val cardWidth = 220
    private val cardHeight = 72
    private val horizSpacing = 36
    private val vertSpacing = 48

    init {
        background = JBColor(Color(248, 250, 252), Color(15, 23, 42))
        isOpaque = true
    }

    fun setNodes(nodes: List<ExplainPlanNode>) {
        this.rootNodes = nodes
        revalidate()
        repaint()
    }

    override fun getPreferredSize(): Dimension {
        val layers = buildLayers()
        val maxInLayer = layers.maxOfOrNull { it.size } ?: 1
        val w = max(800, maxInLayer * (cardWidth + horizSpacing) + 80)
        val h = max(480, layers.size * (cardHeight + vertSpacing) + 80)
        return Dimension(w, h)
    }

    private fun buildLayers(): List<List<ExplainPlanNode>> {
        val layers = mutableListOf<MutableList<ExplainPlanNode>>()
        fun traverse(nodes: List<ExplainPlanNode>, depth: Int) {
            if (nodes.isEmpty()) return
            while (layers.size <= depth) {
                layers.add(mutableListOf())
            }
            for (node in nodes) {
                layers[depth].add(node)
                traverse(node.children, depth + 1)
            }
        }
        traverse(rootNodes, 0)
        return if (layers.isEmpty()) listOf(emptyList()) else layers
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as? Graphics2D ?: return
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val layers = buildLayers()
        if (layers.isEmpty() || layers.all { it.isEmpty() }) {
            g2.color = JBColor.GRAY
            g2.font = font.deriveFont(Font.ITALIC, 13f)
            g2.drawString("No execution nodes in plan", width / 2 - 80, height / 2)
            return
        }

        val nodePositions = mutableMapOf<ExplainPlanNode, Point>()
        var y = 40

        for (layer in layers) {
            val totalLayerWidth = layer.size * cardWidth + (layer.size - 1) * horizSpacing
            var x = max(30, (width - totalLayerWidth) / 2)

            for (node in layer) {
                nodePositions[node] = Point(x, y)
                x += cardWidth + horizSpacing
            }
            y += cardHeight + vertSpacing
        }

        // Draw connection arrows from parent to children
        for ((parent, pt) in nodePositions) {
            val parentCenter = Point(pt.x + cardWidth / 2, pt.y + cardHeight)
            for (child in parent.children) {
                val childPt = nodePositions[child] ?: continue
                val childCenter = Point(childPt.x + cardWidth / 2, childPt.y)

                g2.stroke = BasicStroke(2f)
                g2.color = JBColor(Color(148, 163, 184), Color(71, 85, 105))

                val curve = CubicCurve2D.Float(
                    parentCenter.x.toFloat(), parentCenter.y.toFloat(),
                    parentCenter.x.toFloat(), (parentCenter.y + vertSpacing / 2).toFloat(),
                    childCenter.x.toFloat(), (childCenter.y - vertSpacing / 2).toFloat(),
                    childCenter.x.toFloat(), childCenter.y.toFloat()
                )
                g2.draw(curve)

                // Arrow head at child
                val arrowSize = 6
                val poly = Polygon().apply {
                    addPoint(childCenter.x, childCenter.y)
                    addPoint(childCenter.x - arrowSize, childCenter.y - arrowSize)
                    addPoint(childCenter.x + arrowSize, childCenter.y - arrowSize)
                }
                g2.fill(poly)
            }
        }

        // Draw operator cards
        for ((node, pt) in nodePositions) {
            drawOperatorCard(g2, node, pt.x, pt.y)
        }
    }

    private fun drawOperatorCard(g2: Graphics2D, node: ExplainPlanNode, x: Int, y: Int) {
        val cardRect = RoundRectangle2D.Float(x.toFloat(), y.toFloat(), cardWidth.toFloat(), cardHeight.toFloat(), 10f, 10f)

        // Card background
        g2.color = JBColor(Color(255, 255, 255), Color(30, 41, 59))
        g2.fill(cardRect)

        // Card border
        val borderColor = when {
            node.isScanWarning -> Color(220, 38, 38)
            node.operatorType == "JOIN" -> Color(37, 99, 235)
            node.operatorType == "SCAN" -> Color(5, 150, 105)
            node.operatorType == "FILTER" -> Color(147, 51, 234)
            node.operatorType == "AGGREGATE" -> Color(217, 119, 6)
            else -> JBColor(Color(203, 213, 225), Color(71, 85, 105))
        }
        g2.color = borderColor
        g2.stroke = BasicStroke(if (node.isScanWarning) 2f else 1.2f)
        g2.draw(cardRect)

        // Operator Type Pill
        val pillBg = when {
            node.isScanWarning -> Color(254, 226, 226)
            node.operatorType == "JOIN" -> Color(219, 234, 254)
            node.operatorType == "SCAN" -> Color(209, 250, 229)
            node.operatorType == "FILTER" -> Color(243, 232, 255)
            else -> Color(241, 245, 249)
        }
        val pillFg = when {
            node.isScanWarning -> Color(185, 28, 28)
            node.operatorType == "JOIN" -> Color(29, 78, 216)
            node.operatorType == "SCAN" -> Color(4, 120, 87)
            node.operatorType == "FILTER" -> Color(109, 40, 217)
            else -> Color(71, 85, 105)
        }
        g2.color = pillBg
        g2.fillRoundRect(x + 8, y + 8, 68, 16, 6, 6)
        g2.color = pillFg
        g2.font = font.deriveFont(Font.BOLD, 9.5f)
        g2.drawString(node.operatorType, x + 12, y + 20)

        // Title
        g2.color = JBColor(Color(15, 23, 42), Color(241, 245, 249))
        g2.font = font.deriveFont(Font.BOLD, 11f)
        val title = if (node.title.length > 20) node.title.take(19) + "…" else node.title
        g2.drawString(title, x + 82, y + 20)

        // Metrics (Cost / Rows)
        g2.color = JBColor(Color(100, 116, 139), Color(148, 163, 184))
        g2.font = font.deriveFont(Font.PLAIN, 10f)
        val costStr = if (node.estimatedCost != null) "Cost: ${node.estimatedCost}" else null
        val rowsStr = if (node.estimatedRows != null) "${String.format("%,d", node.estimatedRows)} rows" else null
        val metrics = listOfNotNull(costStr, rowsStr).joinToString(" • ").ifEmpty {
            node.detail.take(28)
        }
        g2.drawString(metrics, x + 10, y + 42)

        // Filter pushdown indicator
        if (node.filterPushdown != null) {
            g2.color = Color(124, 58, 237)
            g2.font = font.deriveFont(Font.ITALIC, 9.5f)
            val filterSnippet = "⚡ Filter: " + node.filterPushdown.take(24)
            g2.drawString(filterSnippet, x + 10, y + 58)
        } else if (node.isScanWarning) {
            g2.color = Color(220, 38, 38)
            g2.font = font.deriveFont(Font.BOLD, 9.5f)
            g2.drawString("⚠️ Full Table Scan", x + 10, y + 58)
        }
    }
}
