package org.jormungandr.dataframe.synthetic

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel

/**
 * Flagship Synthetic Data & Differential Privacy Studio Panel.
 * Clones empirical marginal distributions with mathematically calibrated DP noise,
 * compares distribution fidelity, and exports synthetic datasets.
 */
class SyntheticDataStudioPanel(
    private var sourceDataFrame: DataFrame = DataFrame.empty(),
    private val onSyntheticDataGenerated: ((DataFrame) -> Unit)? = null
) : JPanel(BorderLayout()) {

    private var lastResult: SyntheticDatasetResult? = null

    // Controls
    private val rowCountSpinner = JSpinner(SpinnerNumberModel(100, 10, 50000, 50))
    private val epsilonSlider = JSlider(1, 100, 10) // 0.1 to 10.0 (divided by 10)
    private val epsilonLabel = JBLabel("ε = 1.0")
    private val mechanismCombo = ComboBox(PrivacyMechanism.values().map { it.displayName }.toTypedArray())

    // Metrics Badges
    private val privacyBadge = JBLabel("Privacy: 50.0%")
    private val fidelityBadge = JBLabel("Fidelity: 66.7%")

    // Tables
    private val metricsModel = object : DefaultTableModel(
        arrayOf("Column", "Type", "Orig Mean", "Synth Mean", "Orig StdDev", "Synth StdDev"), 0
    ) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val metricsTable = JBTable(metricsModel)

    private val previewModel = DefaultTableModel()
    private val previewTable = JBTable(previewModel)

    init {
        setupUI()
        setupListeners()
        if (sourceDataFrame.columns.isNotEmpty()) {
            generateSyntheticData(notifyCallback = false)
        }
    }

    fun setSourceDataFrame(df: DataFrame) {
        this.sourceDataFrame = df
        generateSyntheticData(notifyCallback = false)
    }

    private fun setupUI() {
        // 1. Top Control Bar
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>Synthetic Data &amp; DP Studio</b>"))
        leftHeader.add(createPill(privacyBadge, Color(133, 153, 0)))
        leftHeader.add(createPill(fidelityBadge, Color(38, 139, 210)))

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val generateBtn = JButton("⚡ Generate Synthetic Data").apply {
            isFocusPainted = false
            font = Font("Segoe UI", Font.BOLD, 12)
            addActionListener { generateSyntheticData() }
        }
        val exportBtn = JButton("📋 Export CSV").apply {
            isFocusPainted = false
            addActionListener { exportSyntheticCsv() }
        }

        rightHeader.add(generateBtn)
        rightHeader.add(exportBtn)

        topPanel.add(leftHeader, BorderLayout.WEST)
        topPanel.add(rightHeader, BorderLayout.EAST)

        // 2. Parameters Configuration Toolbar
        val paramsPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 6)).apply {
            border = CompoundBorder(LineBorder(Color(230, 230, 230), 1), EmptyBorder(4, 10, 4, 10))
            background = Color(250, 250, 250)
        }
        paramsPanel.add(JBLabel("Target Rows:"))
        paramsPanel.add(rowCountSpinner)
        paramsPanel.add(JBLabel("Mechanism:"))
        paramsPanel.add(mechanismCombo)
        paramsPanel.add(JBLabel("Privacy Budget (ε):"))
        paramsPanel.add(epsilonSlider.apply {
            preferredSize = Dimension(120, 24)
        })
        paramsPanel.add(epsilonLabel)

        val northContainer = JPanel(BorderLayout())
        northContainer.add(topPanel, BorderLayout.NORTH)
        northContainer.add(paramsPanel, BorderLayout.SOUTH)
        add(northContainer, BorderLayout.NORTH)

        // 3. Center Split: Left = Distribution Metrics Comparison | Right = Synthetic Preview
        val centerSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            resizeWeight = 0.45
            dividerSize = 4
        }

        val leftContainer = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(4, 4, 4, 4))
            add(JBLabel("<b>Distribution Comparison (Original vs Synthetic):</b>"), BorderLayout.NORTH)
            add(JBScrollPane(metricsTable), BorderLayout.CENTER)
        }
        centerSplit.leftComponent = leftContainer

        val rightContainer = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(4, 4, 4, 4))
            add(JBLabel("<b>Synthesized Data Preview:</b>"), BorderLayout.NORTH)
            add(JBScrollPane(previewTable), BorderLayout.CENTER)
        }
        centerSplit.rightComponent = rightContainer

        add(centerSplit, BorderLayout.CENTER)
    }

    private fun setupListeners() {
        epsilonSlider.addChangeListener {
            val eps = epsilonSlider.value / 10.0
            epsilonLabel.text = String.format("ε = %.1f", eps)
        }
    }

    fun generateSyntheticData(notifyCallback: Boolean = true) {
        if (sourceDataFrame.columns.isEmpty()) {
            // Create fallback dataset if empty
            val colA = ColumnMetadata("age", "Int", DataTypeCategory.INTEGER, totalCount = 8)
            val colB = ColumnMetadata("income", "Double", DataTypeCategory.FLOAT, totalCount = 8)
            val colC = ColumnMetadata("department", "String", DataTypeCategory.STRING, totalCount = 8)
            val rows = listOf(
                listOf(25, 52000.0, "Engineering"),
                listOf(30, 64000.0, "Sales"),
                listOf(45, 95000.0, "Engineering"),
                listOf(50, 110000.0, "Marketing"),
                listOf(35, 78000.0, "Sales"),
                listOf(28, 59000.0, "Engineering"),
                listOf(62, 125000.0, "Management"),
                listOf(41, 88000.0, "Sales")
            )
            sourceDataFrame = DataFrame("Employee_Profiles", listOf(colA, colB, colC), rows)
        }

        val rowCount = (rowCountSpinner.value as? Int) ?: 100
        val eps = epsilonSlider.value / 10.0
        val mechanism = PrivacyMechanism.values()[mechanismCombo.selectedIndex.coerceIn(0, PrivacyMechanism.values().size - 1)]

        val config = SyntheticGenerationConfig(rowCount, eps, 1e-5, mechanism)
        val result = SyntheticDataEngine.generateSyntheticDataset(sourceDataFrame, config)
        this.lastResult = result

        // Update badges
        privacyBadge.text = String.format("Privacy: %.1f%%", result.privacyScore)
        fidelityBadge.text = String.format("Fidelity: %.1f%%", result.fidelityScore)

        // Update metrics comparison table
        metricsModel.rowCount = 0
        val synthDistributions = SyntheticDataEngine.analyzeDistributions(result.syntheticDataFrame)

        for (origDist in result.columnDistributions) {
            val synthDist = synthDistributions.firstOrNull { it.columnName == origDist.columnName }
            if (origDist.isNumeric) {
                metricsModel.addRow(arrayOf(
                    origDist.columnName,
                    "Numeric",
                    String.format("%.2f", origDist.mean),
                    String.format("%.2f", synthDist?.mean ?: 0.0),
                    String.format("%.2f", origDist.stdDev),
                    String.format("%.2f", synthDist?.stdDev ?: 0.0)
                ))
            } else {
                metricsModel.addRow(arrayOf(
                    origDist.columnName,
                    "Categorical",
                    "${origDist.categoricalFrequencies.size} classes",
                    "${synthDist?.categoricalFrequencies?.size ?: 0} classes",
                    "-",
                    "-"
                ))
            }
        }

        // Update preview table
        val synthDf = result.syntheticDataFrame
        previewModel.setDataVector(
            Array(minOf(synthDf.rowCount, 100)) { r ->
                Array(synthDf.columnCount) { c ->
                    synthDf.rows.getOrNull(r)?.getOrNull(c)?.toString() ?: ""
                }
            },
            synthDf.columns.map { it.name }.toTypedArray()
        )

        if (notifyCallback) {
            onSyntheticDataGenerated?.invoke(synthDf)
        }
    }

    private fun exportSyntheticCsv() {
        val res = lastResult?.syntheticDataFrame ?: return
        val sb = StringBuilder()
        sb.append(res.columns.joinToString(",") { it.name }).append("\n")
        for (r in 0 until res.rowCount) {
            val row = res.rows.getOrNull(r) ?: emptyList()
            sb.append(row.joinToString(",") { it?.toString() ?: "" }).append("\n")
        }

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(sb.toString()), null)
        Messages.showInfoMessage("Synthesized CSV dataset (${res.rowCount} rows) copied to clipboard!", "Synthetic Data Exported")
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
}
