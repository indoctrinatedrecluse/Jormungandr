package org.jormungandr.dataframe.contract

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import org.jormungandr.dataframe.model.DataFrame
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Flagship Data Contract & Schema Expectation Studio Panel.
 * Visually authors expectations, executes live audits against the active DataFrame,
 * displays pass/fail metrics, and exports to Pandera & Great Expectations.
 */
class DataContractStudioPanel(
    private var dataFrame: DataFrame = DataFrame.empty()
) : JPanel(BorderLayout()) {

    private val expectations = mutableListOf<DataExpectation>()

    // Top summary
    private val passCountBadge = JBLabel("Passed: 0")
    private val failCountBadge = JBLabel("Failed: 0")
    private val passRateBadge = JBLabel("Pass Rate: 0.0%")

    // Table
    private val tableModel = object : DefaultTableModel(
        arrayOf("Status", "Column", "Rule Type", "Violations", "Observed Summary", "Explanation"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JBTable(tableModel)

    // Rule builder controls
    private val columnCombo = ComboBox<String>()
    private val typeCombo = ComboBox(ExpectationType.values().map { it.displayName }.toTypedArray())
    private val param1Field = JBTextField("0.0", 6)
    private val param2Field = JBTextField("1000.0", 6)

    init {
        setupDefaultExpectations()
        setupUI()
        setupListeners()
        updateColumns()
        runAudit()
    }

    fun setDataFrame(df: DataFrame) {
        this.dataFrame = df
        updateColumns()
        runAudit()
    }

    private fun setupDefaultExpectations() {
        expectations.add(DataExpectation("exp-1", "id", ExpectationType.NOT_NULL))
        expectations.add(DataExpectation("exp-2", "id", ExpectationType.UNIQUE_RATIO, minUniqueRatio = 0.95))
        expectations.add(DataExpectation("exp-3", "price", ExpectationType.VALUE_RANGE, minVal = 0.0, maxVal = 50000.0))
        expectations.add(DataExpectation("exp-4", "status", ExpectationType.ALLOWED_SET, allowedValues = listOf("active", "pending", "completed", "archived")))
    }

    private fun setupUI() {
        // 1. Top Bar
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(8, 12, 8, 12))
            background = Color(245, 245, 245)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>Data Contract Studio</b>"))
        leftHeader.add(createPill(passCountBadge, Color(133, 153, 0)))
        leftHeader.add(createPill(failCountBadge, Color(211, 54, 130)))
        leftHeader.add(createPill(passRateBadge, Color(38, 139, 210)))

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val runAuditBtn = JButton("⚡ Run Contract Audit").apply {
            isFocusPainted = false
            font = Font("Segoe UI", Font.BOLD, 12)
            addActionListener { runAudit() }
        }
        val exportPanderaBtn = JButton("📋 Export Pandera").apply {
            isFocusPainted = false
            addActionListener { exportPandera() }
        }
        val exportGxBtn = JButton("📋 Export Great Expectations").apply {
            isFocusPainted = false
            addActionListener { exportGreatExpectations() }
        }

        rightHeader.add(runAuditBtn)
        rightHeader.add(exportPanderaBtn)
        rightHeader.add(exportGxBtn)

        topPanel.add(leftHeader, BorderLayout.WEST)
        topPanel.add(rightHeader, BorderLayout.EAST)

        // 2. Rule Builder Toolbar
        val builderPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
            border = CompoundBorder(LineBorder(Color(230, 230, 230), 1), EmptyBorder(4, 10, 4, 10))
            background = Color(250, 250, 250)
        }
        builderPanel.add(JBLabel("Add Expectation:"))
        builderPanel.add(columnCombo)
        builderPanel.add(typeCombo)
        builderPanel.add(JBLabel("Param 1 / Min:"))
        builderPanel.add(param1Field)
        builderPanel.add(JBLabel("Param 2 / Max:"))
        builderPanel.add(param2Field)
        val addBtn = JButton("+ Add Rule").apply {
            isFocusPainted = false
            addActionListener { addRule() }
        }
        builderPanel.add(addBtn)

        val northContainer = JPanel(BorderLayout())
        northContainer.add(topPanel, BorderLayout.NORTH)
        northContainer.add(builderPanel, BorderLayout.SOUTH)
        add(northContainer, BorderLayout.NORTH)

        // 3. Results Table
        table.rowHeight = 28
        table.tableHeader.reorderingAllowed = false
        table.columnModel.getColumn(0).cellRenderer = StatusCellRenderer()

        val scrollPane = JBScrollPane(table).apply {
            border = LineBorder(Color(225, 225, 225), 1)
        }
        add(scrollPane, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        typeCombo.addActionListener {
            val selectedType = ExpectationType.values()[typeCombo.selectedIndex.coerceIn(0, ExpectationType.values().size - 1)]
            when (selectedType) {
                ExpectationType.VALUE_RANGE -> {
                    param1Field.text = "0.0"
                    param2Field.text = "1000.0"
                }
                ExpectationType.REGEX_MATCH -> {
                    param1Field.text = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\$"
                    param2Field.text = ""
                }
                ExpectationType.ALLOWED_SET -> {
                    param1Field.text = "active,pending,completed"
                    param2Field.text = ""
                }
                ExpectationType.UNIQUE_RATIO -> {
                    param1Field.text = "0.95"
                    param2Field.text = ""
                }
                else -> {
                    param1Field.text = ""
                    param2Field.text = ""
                }
            }
        }
    }

    private fun updateColumns() {
        columnCombo.removeAllItems()
        for (c in dataFrame.columns) {
            columnCombo.addItem(c.name)
        }
        if (columnCombo.itemCount == 0) {
            columnCombo.addItem("id")
            columnCombo.addItem("price")
            columnCombo.addItem("status")
        }
    }

    private fun addRule() {
        val col = columnCombo.selectedItem as? String ?: return
        val type = ExpectationType.values()[typeCombo.selectedIndex.coerceIn(0, ExpectationType.values().size - 1)]

        val minVal = param1Field.text.toDoubleOrNull()
        val maxVal = param2Field.text.toDoubleOrNull()
        val regex = param1Field.text.takeIf { type == ExpectationType.REGEX_MATCH }
        val allowed = if (type == ExpectationType.ALLOWED_SET) param1Field.text.split(",").map { it.trim() } else null
        val minRatio = if (type == ExpectationType.UNIQUE_RATIO) param1Field.text.toDoubleOrNull() ?: 0.9 else null

        val rule = DataExpectation(
            id = "exp-${expectations.size + 1}",
            column = col,
            type = type,
            minVal = minVal,
            maxVal = maxVal,
            regexPattern = regex,
            allowedValues = allowed,
            minUniqueRatio = minRatio
        )
        expectations.add(rule)
        runAudit()
    }

    fun runAudit() {
        val suite = DataContractSuite("ProductionDataContract", dataFrame.name.ifEmpty { "ActiveDataset" }, expectations)
        val results = DataContractEngine.evaluate(dataFrame, suite)

        tableModel.rowCount = 0
        var passed = 0
        var failed = 0

        for (res in results) {
            if (res.status == EvaluationStatus.PASSED) passed++ else failed++
            tableModel.addRow(arrayOf(
                res.status.name,
                res.expectation.column,
                res.expectation.type.displayName,
                "${res.violationCount} (${String.format("%.1f%%", res.failureRate * 100)})",
                res.observedSummary,
                res.message
            ))
        }

        val total = results.size
        val passRate = if (total > 0) (passed.toDouble() / total) * 100.0 else 0.0

        passCountBadge.text = "Passed: $passed"
        failCountBadge.text = "Failed: $failed"
        passRateBadge.text = String.format("Pass Rate: %.1f%%", passRate)
    }

    private fun exportPandera() {
        val suite = DataContractSuite("ProductionDataContract", dataFrame.name, expectations)
        val code = DataContractEngine.exportToPandera(suite)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(code), null)
        Messages.showInfoMessage("Pandera Schema definition copied to clipboard!", "Pandera Code Exported")
    }

    private fun exportGreatExpectations() {
        val suite = DataContractSuite("ProductionDataContract", dataFrame.name, expectations)
        val code = DataContractEngine.exportToGreatExpectations(suite)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(code), null)
        Messages.showInfoMessage("Great Expectations Suite definition copied to clipboard!", "Great Expectations Code Exported")
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

    private class StatusCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ): Component {
            val c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            val status = value?.toString() ?: ""
            if (status == "PASSED") {
                c.foreground = Color(133, 153, 0)
                c.text = "✓ PASSED"
                c.font = Font("Segoe UI", Font.BOLD, 11)
            } else {
                c.foreground = Color(211, 54, 130)
                c.text = "✕ FAILED"
                c.font = Font("Segoe UI", Font.BOLD, 11)
            }
            return c
        }
    }
}
