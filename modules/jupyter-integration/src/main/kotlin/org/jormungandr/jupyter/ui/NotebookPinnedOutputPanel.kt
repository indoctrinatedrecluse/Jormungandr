package org.jormungandr.jupyter.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.NotebookCell
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.ByteArrayInputStream
import java.util.Base64
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.MatteBorder

/**
 * Pinned Output & Split View Side Panel for Reactive Notebooks.
 * Displays docked outputs from individual notebook cells alongside the main notebook editor,
 * updating in real time as cells are re-executed.
 */
class NotebookPinnedOutputPanel(
    private val project: Project,
    private val onUnpin: (cellId: String) -> Unit = {}
) : JPanel(BorderLayout()) {

    private val pinnedCells = LinkedHashMap<String, PinnedEntry>()

    private val headerLabel = JLabel("📌 Pinned Outputs")
    private val countBadge = JLabel("(0)")
    private val clearAllBtn = JButton("🧹 Clear All")

    private val cardsContainer = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
    }

    private val emptyStatePanel = JPanel(GridBagLayout()).apply {
        isOpaque = false
        val box = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.CENTER_ALIGNMENT

            val icon = JLabel("📌").apply {
                font = font.deriveFont(28f)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            val title = JLabel("No Pinned Outputs").apply {
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = Color(71, 85, 105)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            val desc = JLabel("<html><center style='color:#64748b; font-size:11px;'>" +
                    "Click <b>⋮</b> on any cell and select<br><b>📌 Pin Output to Side Panel</b><br>" +
                    "to dock live outputs here." +
                    "</center></html>").apply {
                alignmentX = Component.CENTER_ALIGNMENT
            }

            add(icon)
            add(Box.createVerticalStrut(8))
            add(title)
            add(Box.createVerticalStrut(4))
            add(desc)
        }
        add(box)
    }

    private val scrollPane = JBScrollPane(cardsContainer).apply {
        border = null
        viewport.isOpaque = false
    }

    data class PinnedEntry(
        val cellId: String,
        var cellIndex: Int,
        var cell: NotebookCell,
        var card: JPanel
    )

    init {
        background = Color(248, 250, 252)
        border = CompoundBorder(
            LineBorder(Color(226, 232, 240), 1),
            EmptyBorder(0, 0, 0, 0)
        )

        buildTopBar()

        val contentWrapper = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(emptyStatePanel, BorderLayout.CENTER)
        }

        add(contentWrapper, BorderLayout.CENTER)
    }

    private fun buildTopBar() {
        val topBar = JPanel(BorderLayout()).apply {
            background = Color(241, 245, 249)
            border = CompoundBorder(
                MatteBorder(0, 0, 1, 0, Color(226, 232, 240)),
                EmptyBorder(6, 10, 6, 10)
            )
        }

        val left = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        headerLabel.font = headerLabel.font.deriveFont(Font.BOLD, 12f)
        headerLabel.foreground = Color(15, 23, 42)

        countBadge.font = countBadge.font.deriveFont(Font.BOLD, 11f)
        countBadge.foreground = Color(100, 116, 139)

        left.add(headerLabel)
        left.add(countBadge)

        val right = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        clearAllBtn.apply {
            isFocusPainted = false
            font = font.deriveFont(Font.PLAIN, 10.5f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            toolTipText = "Unpin all cell outputs"
            addActionListener { clearAll() }
        }
        right.add(clearAllBtn)

        topBar.add(left, BorderLayout.WEST)
        topBar.add(right, BorderLayout.EAST)
        add(topBar, BorderLayout.NORTH)
    }

    fun pinCell(cell: NotebookCell, cellIndex: Int) {
        val card = buildCardForCell(cell, cellIndex)
        val entry = PinnedEntry(cell.id, cellIndex, cell, card)
        pinnedCells[cell.id] = entry
        refreshContainer()
    }

    fun isCellPinned(cellId: String): Boolean = pinnedCells.containsKey(cellId)

    fun updateCellIfPinned(cell: NotebookCell, cellIndex: Int) {
        val entry = pinnedCells[cell.id] ?: return
        entry.cell = cell
        entry.cellIndex = cellIndex
        entry.card = buildCardForCell(cell, cellIndex)
        refreshContainer()
    }

    fun unpinCell(cellId: String) {
        pinnedCells.remove(cellId)
        onUnpin(cellId)
        refreshContainer()
    }

    fun clearAll() {
        val ids = pinnedCells.keys.toList()
        pinnedCells.clear()
        ids.forEach { onUnpin(it) }
        refreshContainer()
    }

    fun getPinnedCount(): Int = pinnedCells.size

    private fun refreshContainer() {
        cardsContainer.removeAll()
        val count = pinnedCells.size
        countBadge.text = "($count)"
        clearAllBtn.isEnabled = count > 0

        removeAll()
        buildTopBar()

        if (count == 0) {
            val emptyWrapper = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(emptyStatePanel, BorderLayout.CENTER)
            }
            add(emptyWrapper, BorderLayout.CENTER)
        } else {
            for (entry in pinnedCells.values) {
                cardsContainer.add(Box.createVerticalStrut(6))
                cardsContainer.add(entry.card)
            }
            cardsContainer.add(Box.createVerticalStrut(6))
            add(scrollPane, BorderLayout.CENTER)
        }

        revalidate()
        repaint()
    }

    private fun buildCardForCell(cell: NotebookCell, cellIndex: Int): JPanel {
        val card = JPanel(BorderLayout()).apply {
            background = Color(255, 255, 255)
            border = CompoundBorder(
                LineBorder(Color(203, 213, 225), 1, true),
                EmptyBorder(6, 8, 8, 8)
            )
        }

        // Header
        val cardHeader = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = MatteBorder(0, 0, 1, 0, Color(241, 245, 249))
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
        val execPrefix = when {
            cell.isExecuting -> "In [*]:"
            cell.executionCount != null -> "In [${cell.executionCount}]:"
            else -> "In [ ]:"
        }
        val prefixLabel = JLabel(execPrefix).apply {
            font = Font("Monospaced", Font.BOLD, 11)
            foreground = if (cell.isExecuting) Color(203, 75, 22) else Color(38, 139, 210)
        }
        val titleLabel = JLabel("Cell #${cellIndex + 1} (${cell.id.take(6)})").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 41, 59)
        }

        val firstCodeLine = cell.source.lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        val snippetLabel = JLabel(if (firstCodeLine.isNotBlank()) "— ${firstCodeLine.take(30)}" else "").apply {
            font = Font("Monospaced", Font.PLAIN, 10)
            foreground = Color(148, 163, 184)
            toolTipText = cell.source.take(500)
        }

        leftHeader.add(prefixLabel)
        leftHeader.add(titleLabel)
        if (firstCodeLine.isNotBlank()) {
            leftHeader.add(snippetLabel)
        }

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 2, 0)).apply { isOpaque = false }
        val copyBtn = JButton("📋").apply {
            isFocusPainted = false
            border = EmptyBorder(2, 4, 2, 4)
            toolTipText = "Copy cell output to clipboard"
            addActionListener {
                val fullText = buildCellOutputPlainText(cell)
                val sel = StringSelection(fullText)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            }
        }
        val unpinBtn = JButton("✕").apply {
            isFocusPainted = false
            border = EmptyBorder(2, 4, 2, 4)
            toolTipText = "Unpin cell"
            addActionListener { unpinCell(cell.id) }
        }
        rightHeader.add(copyBtn)
        rightHeader.add(unpinBtn)

        cardHeader.add(leftHeader, BorderLayout.WEST)
        cardHeader.add(rightHeader, BorderLayout.EAST)
        card.add(cardHeader, BorderLayout.NORTH)

        // Body: Render Outputs
        val bodyPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = EmptyBorder(6, 2, 2, 2)
        }

        if (cell.outputs.isEmpty()) {
            val emptyMsg = JLabel(if (cell.cellType == CellType.MARKDOWN) "Markdown cell (no code outputs)" else "No outputs produced yet.").apply {
                font = font.deriveFont(Font.ITALIC, 11f)
                foreground = Color(148, 163, 184)
            }
            bodyPanel.add(emptyMsg)
        } else {
            for (output in cell.outputs) {
                when (output) {
                    is CellOutput.StreamOutput -> {
                        val txt = stripAnsi(output.text)
                        val area = JTextArea(txt).apply {
                            isEditable = false
                            font = Font("Consolas", Font.PLAIN, 11)
                            foreground = if (output.name == "stderr") Color(220, 38, 38) else Color(30, 41, 59)
                            background = Color(248, 250, 252)
                            border = CompoundBorder(LineBorder(Color(226, 232, 240), 1), EmptyBorder(4, 6, 4, 6))
                        }
                        bodyPanel.add(area)
                    }
                    is CellOutput.ExecuteResultOutput -> {
                        renderRichMimeToBody(output.data, output.executionCount, bodyPanel)
                    }
                    is CellOutput.DisplayDataOutput -> {
                        renderRichMimeToBody(output.data, null, bodyPanel)
                    }
                    is CellOutput.ErrorOutput -> {
                        val errBox = JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.Y_AXIS)
                            background = Color(254, 242, 242)
                            border = CompoundBorder(LineBorder(Color(248, 113, 113), 1), EmptyBorder(4, 6, 4, 6))
                            val title = JLabel("${output.ename}: ${output.evalue}").apply {
                                font = font.deriveFont(Font.BOLD, 11f)
                                foreground = Color(185, 28, 28)
                            }
                            add(title)
                            if (output.traceback.isNotEmpty()) {
                                val tb = JTextArea(output.traceback.joinToString("\n") { stripAnsi(it) }).apply {
                                    isEditable = false
                                    font = Font("Consolas", Font.PLAIN, 10)
                                    foreground = Color(153, 27, 27)
                                    background = Color(254, 242, 242)
                                }
                                add(tb)
                            }
                        }
                        bodyPanel.add(errBox)
                    }
                }
                bodyPanel.add(Box.createVerticalStrut(4))
            }
        }

        card.add(bodyPanel, BorderLayout.CENTER)
        return card
    }

    private fun renderRichMimeToBody(data: Map<String, Any>, execCount: Int?, container: JPanel) {
        val imgBase64 = data["image/png"]?.toString() ?: data["image/jpeg"]?.toString()
        if (imgBase64 != null) {
            runCatching {
                val clean = imgBase64.replace("\n", "").replace("\r", "").trim()
                val bytes = Base64.getDecoder().decode(clean)
                val img = ImageIO.read(ByteArrayInputStream(bytes))
                if (img != null) {
                    val imgLabel = JLabel(ImageIcon(img)).apply {
                        border = LineBorder(Color(226, 232, 240), 1)
                    }
                    container.add(imgLabel)
                    return
                }
            }
        }

        val htmlContent = data["text/html"]?.toString()
        if (htmlContent != null) {
            val parsed = HtmlTableParser.parse(htmlContent)
            if (parsed != null) {
                val tableHeader = JLabel("📊 Table [${parsed.rows.size} rows × ${parsed.headers.size} cols]").apply {
                    font = font.deriveFont(Font.BOLD, 10.5f)
                    foreground = Color(30, 64, 175)
                }
                val editorPane = JEditorPane("text/html", formatTableHtml(htmlContent)).apply {
                    isEditable = false
                    background = Color(255, 255, 255)
                    border = null
                }
                val scroll = JBScrollPane(editorPane).apply {
                    preferredSize = Dimension(editorPane.preferredSize.width, 160)
                    border = LineBorder(Color(226, 232, 240), 1)
                }
                container.add(tableHeader)
                container.add(Box.createVerticalStrut(2))
                container.add(scroll)
                return
            } else {
                val pane = JEditorPane("text/html", htmlContent).apply {
                    isEditable = false
                    background = Color(255, 255, 255)
                }
                container.add(pane)
                return
            }
        }

        val plain = data["text/plain"]?.toString() ?: data.values.firstOrNull()?.toString() ?: ""
        val area = JTextArea(plain).apply {
            isEditable = false
            font = Font("Consolas", Font.PLAIN, 11)
            foreground = Color(30, 41, 59)
            background = Color(248, 250, 252)
            border = CompoundBorder(LineBorder(Color(226, 232, 240), 1), EmptyBorder(4, 6, 4, 6))
        }
        container.add(area)
    }

    private fun formatTableHtml(html: String): String {
        return "<html><head><style>" +
                "body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; font-size: 11px; margin: 0; padding: 4px; background: #ffffff; color: #1e293b; }" +
                "table { border-collapse: collapse; width: 100%; border: 1px solid #e2e8f0; }" +
                "th { background: #f8fafc; color: #475569; font-weight: 600; padding: 4px 8px; border: 1px solid #e2e8f0; text-align: left; }" +
                "td { padding: 4px 8px; border: 1px solid #e2e8f0; }" +
                "tr:nth-child(even) { background: #f8fafc; }" +
                "</style></head><body>$html</body></html>"
    }

    private fun buildCellOutputPlainText(cell: NotebookCell): String {
        val sb = StringBuilder()
        sb.appendLine("# [In ${cell.executionCount ?: "-"}] Cell Output (${cell.id}):")
        for (out in cell.outputs) {
            when (out) {
                is CellOutput.StreamOutput -> sb.append(stripAnsi(out.text))
                is CellOutput.ExecuteResultOutput -> sb.appendLine(out.data["text/plain"]?.toString() ?: "")
                is CellOutput.DisplayDataOutput -> sb.appendLine(out.data["text/plain"]?.toString() ?: "[Media]")
                is CellOutput.ErrorOutput -> {
                    sb.appendLine("${out.ename}: ${out.evalue}")
                    out.traceback.forEach { sb.appendLine(stripAnsi(it)) }
                }
            }
        }
        return sb.toString()
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\\u001B\\[[;\\d]*[ -/]*[@-~]"), "")
    }
}
