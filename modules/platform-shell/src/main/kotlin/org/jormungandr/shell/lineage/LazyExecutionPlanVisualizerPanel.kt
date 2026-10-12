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

package org.jormungandr.shell.lineage

import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder

enum class PlanOpType(val displayName: String, val nodeColor: Color, val badgeText: String) {
    SCAN("DATA SCAN", Color(46, 125, 50), "SCAN"),
    FILTER("PREDICATE FILTER", Color(198, 40, 40), "FILTER"),
    PROJECT("PROJECTION", Color(21, 101, 192), "PROJECT"),
    AGGREGATE("AGGREGATE / GROUPBY", Color(230, 81, 0), "AGG"),
    JOIN("RELATIONAL JOIN", Color(106, 27, 154), "JOIN"),
    SORT("SORT / ORDERBY", Color(0, 131, 143), "SORT"),
    UNION("UNION / CONCAT", Color(156, 39, 176), "UNION"),
    OTHER("OPERATOR", Color(69, 90, 100), "OP")
}

data class PlanNode(
    val id: String,
    val type: PlanOpType,
    val title: String,
    val details: String,
    val children: MutableList<PlanNode> = mutableListOf(),
    var x: Int = 0,
    var y: Int = 0
)

/**
 * Interactive execution plan visualizer for Polars, PyArrow, and DuckDB lazy queries.
 */
class LazyExecutionPlanVisualizerPanel(
    val project: Project? = null
) : JPanel(BorderLayout()) {

    private val planInputArea = JBTextArea(6, 40)
    private val canvas = PlanCanvas()
    private val selectedDetailsLabel = JBLabel("Select a node on the canvas to inspect operator details.")

    private val samplePlans = mapOf(
        "Polars Filter & Aggregation" to """
            AGGREGATE
              [col("total_spend").sum(), col("orders").count()] BY [col("user_id"), col("country")]
              FILTER [(col("status")) == (String("COMPLETED"))]
                PARQUET SCAN [user_id, country, status, total_spend, orders] FROM 'data/transactions.parquet'
                  PROJECT */5 COLUMNS
        """.trimIndent(),
        "PyArrow Parquet Pushdown" to """
            SORT [col("timestamp") ASC]
              PROJECT [device_id, temp_celsius, timestamp]
                FILTER [(col("temp_celsius")) > (45.0)]
                  ARROW DATASET SCAN [device_id, temp_celsius, humidity, timestamp]
        """.trimIndent(),
        "Polars Relational Hash Join" to """
            PROJECTION [customer_id, customer_name, order_id, order_total]
              HASH JOIN ON [customer_id == customer_id]
                LEFT: SCAN customers.parquet [customer_id, customer_name]
                RIGHT: FILTER [(col("order_total")) > (100.0)]
                  SCAN orders.parquet [order_id, customer_id, order_total]
        """.trimIndent()
    )

    init {
        buildUi()
        loadPlanText(samplePlans["Polars Filter & Aggregation"]!!)
    }

    private fun buildUi() {
        val topToolbar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(220, 220, 220)),
                EmptyBorder(6, 10, 6, 10)
            )
            background = Color(248, 249, 250)
        }

        val leftTools = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val sampleCombo = JComboBox(samplePlans.keys.toTypedArray()).apply {
            isFocusable = false
            addActionListener {
                val selected = selectedItem as? String ?: return@addActionListener
                samplePlans[selected]?.let { loadPlanText(it) }
            }
        }
        leftTools.add(JBLabel("Sample Plan:"))
        leftTools.add(sampleCombo)

        val parseBtn = JButton("▶ Visualize Plan").apply {
            isFocusable = false
            addActionListener { parseAndRender() }
        }
        leftTools.add(parseBtn)

        val copyCodeBtn = JButton("🐍 Copy Polars Code").apply {
            isFocusable = false
            addActionListener {
                val code = """
import polars as pl

# Construct lazy execution graph
q = (
    pl.scan_parquet("data/transactions.parquet")
    .filter(pl.col("status") == "COMPLETED")
    .group_by(["user_id", "country"])
    .agg([
        pl.col("total_spend").sum(),
        pl.col("orders").count()
    ])
)

# Inspect the query plan
print(q.explain(optimized=True))

# Collect results
df = q.collect()
print(df.head())
""".trimIndent()
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                JOptionPane.showMessageDialog(this@LazyExecutionPlanVisualizerPanel, "Copied Polars explain() snippet to clipboard!", "Polars Plan", JOptionPane.INFORMATION_MESSAGE)
            }
        }
        leftTools.add(copyCodeBtn)
        topToolbar.add(leftTools, BorderLayout.WEST)
        add(topToolbar, BorderLayout.NORTH)

        // Split between input editor and canvas
        planInputArea.font = Font("Consolas", Font.PLAIN, 12)
        val editorPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Execution Plan Text (paste from df.lazy().explain())")
            preferredSize = Dimension(320, 400)
            add(JBScrollPane(planInputArea), BorderLayout.CENTER)
        }

        val canvasPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("Interactive 2D Operator DAG")
            add(JBScrollPane(canvas), BorderLayout.CENTER)
        }

        val centerSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorPanel, canvasPanel).apply {
            resizeWeight = 0.28
            dividerSize = 4
        }
        add(centerSplit, BorderLayout.CENTER)

        val statusBar = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Color(220, 220, 220)),
                EmptyBorder(4, 10, 4, 10)
            )
            background = Color(248, 249, 250)
        }
        statusBar.add(selectedDetailsLabel, BorderLayout.WEST)
        add(statusBar, BorderLayout.SOUTH)
    }

    private fun loadPlanText(text: String) {
        planInputArea.text = text
        parseAndRender()
    }

    private fun parseAndRender() {
        val rawText = planInputArea.text.trim()
        val lines = rawText.lines().filter { it.isNotBlank() }
        val rootNode = parseTreeFromLines(lines)
        canvas.setRootNode(rootNode)
    }

    private fun parseTreeFromLines(lines: List<String>): PlanNode? {
        if (lines.isEmpty()) return null
        val nodes = mutableListOf<Pair<Int, PlanNode>>() // (indent, node)

        var idCounter = 1
        for (line in lines) {
            val indent = line.takeWhile { it == ' ' }.length
            val trimmed = line.trim()
            val opType = when {
                trimmed.contains("SCAN", true) -> PlanOpType.SCAN
                trimmed.contains("FILTER", true) -> PlanOpType.FILTER
                trimmed.contains("PROJECT", true) -> PlanOpType.PROJECT
                trimmed.contains("AGGREGATE", true) || trimmed.contains("GROUP", true) -> PlanOpType.AGGREGATE
                trimmed.contains("JOIN", true) -> PlanOpType.JOIN
                trimmed.contains("SORT", true) -> PlanOpType.SORT
                trimmed.contains("UNION", true) -> PlanOpType.UNION
                else -> PlanOpType.OTHER
            }
            val title = trimmed.substringBefore('[').trim().ifBlank { opType.displayName }
            val details = if (trimmed.contains('[')) "[" + trimmed.substringAfter('[') else trimmed
            val node = PlanNode(
                id = "node_${idCounter++}",
                type = opType,
                title = title,
                details = details
            )
            nodes.add(indent to node)
        }

        // Build hierarchy based on indentation
        for (i in nodes.indices) {
            val (currentIndent, currentNode) = nodes[i]
            for (j in i + 1 until nodes.size) {
                val (childIndent, childNode) = nodes[j]
                if (childIndent > currentIndent) {
                    if (currentNode.children.none { it.id == childNode.id }) {
                        // Check if it's direct descendant
                        val intermediates = nodes.subList(i + 1, j)
                        if (intermediates.all { it.first >= childIndent }) {
                            currentNode.children.add(childNode)
                        }
                    }
                } else {
                    break
                }
            }
        }
        return nodes.firstOrNull()?.second
    }

    private inner class PlanCanvas : JPanel() {
        private var rootNode: PlanNode? = null
        private var selectedNode: PlanNode? = null

        init {
            background = Color(248, 250, 252)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    findNodeAt(rootNode, e.x, e.y)?.let { clicked ->
                        selectedNode = clicked
                        selectedDetailsLabel.text = "Selected [${clicked.type.name}]: ${clicked.title} -> ${clicked.details}"
                        repaint()
                    }
                }
            })
        }

        fun setRootNode(root: PlanNode?) {
            this.rootNode = root
            layoutNodes(root, 280, 40, 240)
            repaint()
        }

        private fun layoutNodes(node: PlanNode?, x: Int, y: Int, spread: Int) {
            if (node == null) return
            node.x = x
            node.y = y
            val childCount = node.children.size
            if (childCount == 0) return

            val startX = x - ((childCount - 1) * spread / 2)
            for ((idx, child) in node.children.withIndex()) {
                val childX = startX + (idx * spread)
                val childY = y + 100
                layoutNodes(child, childX, childY, spread / 2.coerceAtLeast(140))
            }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as? Graphics2D ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            rootNode?.let { renderNode(g2, it) }
        }

        private fun renderNode(g2: Graphics2D, node: PlanNode) {
            val nodeWidth = 200
            val nodeHeight = 52

            // Draw connection lines to children
            for (child in node.children) {
                g2.color = Color(148, 163, 184)
                g2.stroke = BasicStroke(2f)
                val p = Path2D.Float()
                p.moveTo(node.x.toFloat(), (node.y + nodeHeight / 2).toFloat())
                p.curveTo(
                    node.x.toFloat(), (node.y + 70).toFloat(),
                    child.x.toFloat(), (child.y - 30).toFloat(),
                    child.x.toFloat(), (child.y - nodeHeight / 2).toFloat()
                )
                g2.draw(p)
                renderNode(g2, child)
            }

            // Draw Node Box
            val rx = (node.x - nodeWidth / 2).toDouble()
            val ry = (node.y - nodeHeight / 2).toDouble()
            val roundRect = RoundRectangle2D.Double(rx, ry, nodeWidth.toDouble(), nodeHeight.toDouble(), 10.0, 10.0)

            g2.color = Color.WHITE
            g2.fill(roundRect)

            g2.color = if (node == selectedNode) Color(37, 99, 235) else node.type.nodeColor
            g2.stroke = BasicStroke(if (node == selectedNode) 3f else 1.5f)
            g2.draw(roundRect)

            // Header stripe
            val headerRect = RoundRectangle2D.Double(rx, ry, nodeWidth.toDouble(), 18.0, 10.0, 10.0)
            g2.color = node.type.nodeColor
            g2.fill(headerRect)

            // Text
            g2.color = Color.WHITE
            g2.font = Font("Segoe UI", Font.BOLD, 10)
            g2.drawString(node.type.badgeText, (rx + 8).toFloat(), (ry + 13).toFloat())

            g2.color = Color(15, 23, 42)
            g2.font = Font("Segoe UI", Font.BOLD, 11)
            val titleTrimmed = if (node.title.length > 24) node.title.take(22) + "..." else node.title
            g2.drawString(titleTrimmed, (rx + 8).toFloat(), (ry + 32).toFloat())

            g2.color = Color(100, 116, 139)
            g2.font = Font("Segoe UI", Font.PLAIN, 10)
            val detailsTrimmed = if (node.details.length > 28) node.details.take(26) + "..." else node.details
            g2.drawString(detailsTrimmed, (rx + 8).toFloat(), (ry + 46).toFloat())
        }

        private fun findNodeAt(node: PlanNode?, x: Int, y: Int): PlanNode? {
            if (node == null) return null
            val halfW = 100
            val halfH = 26
            if (x in (node.x - halfW)..(node.x + halfW) && y in (node.y - halfH)..(node.y + halfH)) {
                return node
            }
            for (child in node.children) {
                val found = findNodeAt(child, x, y)
                if (found != null) return found
            }
            return null
        }
    }
}
