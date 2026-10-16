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

package org.jormungandr.shell.llm.eval

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel

/**
 * Interactive Swing UI for LLM Benchmarking, Evaluation metrics, and Prompt Red-Teaming.
 */
class LlmEvalStudioPanel(
    private val project: Project? = null
) : JPanel(BorderLayout()) {

    private val modelCombo = JComboBox(arrayOf("qwen2.5-coder:7b", "llama3.2:3b", "mistral:7b", "deepseek-r1:8b", "phi-3:mini"))
    private val suiteCombo = JComboBox(LlmEvalEngine.getStandardSuites().keys.toTypedArray())

    private val scoreBadge = JBLabel("Score: 100%")
    private val defenseBadge = JBLabel("Defense Rate: 100%")
    private val latencyBadge = JBLabel("Avg Latency: 0 ms")
    private val totalBadge = JBLabel("Tests: 0")

    private val tableModel = DefaultTableModel(
        arrayOf("ID", "Type / Attack", "Prompt Preview", "ROUGE-L", "Status", "Latency"), 0
    )
    private val resultsTable = JBTable(tableModel)

    private val promptArea = JBTextArea(4, 30).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    private val outputArea = JBTextArea(6, 30).apply { isEditable = false; lineWrap = true; wrapStyleWord = true; font = Font("Consolas", Font.PLAIN, 12) }
    private val defenseNoteArea = JBTextArea(3, 30).apply { isEditable = false; lineWrap = true; wrapStyleWord = true }

    private var activeReport: EvalBenchmarkReport? = null

    init {
        setupUI()
        runSelectedBenchmark()
    }

    private fun setupUI() {
        // 1. Top Header & Toolbar
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>🛡️ LLM Eval &amp; Red-Team Studio</b>"))
        leftHeader.add(createPill(scoreBadge, Color(46, 125, 50)))
        leftHeader.add(createPill(defenseBadge, Color(21, 101, 192)))
        leftHeader.add(createPill(latencyBadge, Color(230, 81, 0)))
        leftHeader.add(createPill(totalBadge, Color(106, 27, 154)))

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        rightHeader.add(JBLabel("Model:"))
        rightHeader.add(modelCombo)
        rightHeader.add(JBLabel("Suite:"))
        rightHeader.add(suiteCombo)

        val runBtn = JButton("Run Benchmark").apply {
            addActionListener { runSelectedBenchmark() }
        }
        val exportBtn = JButton("Export Report").apply {
            addActionListener { exportMarkdownReport() }
        }
        rightHeader.add(runBtn)
        rightHeader.add(exportBtn)

        topPanel.add(leftHeader, BorderLayout.WEST)
        topPanel.add(rightHeader, BorderLayout.EAST)

        // 2. Center Split (Table of results + Inspector)
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            dividerLocation = 280
            resizeWeight = 0.5
        }

        // Table panel
        val tablePanel = JPanel(BorderLayout())
        tablePanel.add(JBScrollPane(resultsTable), BorderLayout.CENTER)

        resultsTable.selectionModel.addListSelectionListener {
            val row = resultsTable.selectedRow
            if (row >= 0 && activeReport != null && row < activeReport!!.results.size) {
                val res = activeReport!!.results[row]
                promptArea.text = res.testCase.prompt
                outputArea.text = res.actualOutput
                defenseNoteArea.text = "[${if (res.passed) "PASSED" else "FAILED"}] ${res.defenseNote} | Model: ${res.modelName} | Latency: ${res.latencyMs}ms"
            }
        }

        // Inspector panel
        val inspectorPanel = JPanel(BorderLayout(0, 4)).apply {
            border = EmptyBorder(6, 8, 6, 8)
        }
        val inspectorTop = JPanel(GridLayout(1, 2, 6, 0))
        val promptBox = JPanel(BorderLayout()).apply {
            add(JBLabel("<b>Test Prompt &amp; Threat Vector:</b>"), BorderLayout.NORTH)
            add(JBScrollPane(promptArea), BorderLayout.CENTER)
        }
        val defenseBox = JPanel(BorderLayout()).apply {
            add(JBLabel("<b>Guardrail &amp; Defense Analysis:</b>"), BorderLayout.NORTH)
            add(JBScrollPane(defenseNoteArea), BorderLayout.CENTER)
        }
        inspectorTop.add(promptBox)
        inspectorTop.add(defenseBox)

        val outputBox = JPanel(BorderLayout()).apply {
            add(JBLabel("<b>Model Response Payload:</b>"), BorderLayout.NORTH)
            add(JBScrollPane(outputArea), BorderLayout.CENTER)
        }

        inspectorPanel.add(inspectorTop, BorderLayout.NORTH)
        inspectorPanel.add(outputBox, BorderLayout.CENTER)

        splitPane.topComponent = tablePanel
        splitPane.bottomComponent = inspectorPanel

        add(topPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
    }

    private fun runSelectedBenchmark() {
        val selectedSuite = suiteCombo.selectedItem as? String ?: return
        val selectedModel = modelCombo.selectedItem as? String ?: "qwen2.5-coder:7b"
        val testCases = LlmEvalEngine.getStandardSuites()[selectedSuite] ?: emptyList()

        while (tableModel.rowCount > 0) tableModel.removeRow(0)

        val report = LlmEvalEngine.runBenchmarkSuite(selectedSuite, selectedModel, testCases)
        activeReport = report

        scoreBadge.text = "Score: ${String.format("%.1f", report.overallScore)}%"
        defenseBadge.text = "Defense Rate: ${String.format("%.1f", report.redTeamDefenseRate)}%"
        latencyBadge.text = "Avg Latency: ${report.averageLatencyMs} ms"
        totalBadge.text = "Tests: ${report.results.size}"

        for (res in report.results) {
            val typeStr = res.testCase.attackVector?.displayName ?: res.testCase.tags.firstOrNull() ?: "Standard"
            val rougeStr = res.metrics[EvalMetricType.ROUGE_L]?.let { String.format("%.2f", it) } ?: "N/A"
            val statusStr = if (res.passed) "✅ Shielded / Pass" else "❌ Vulnerable / Fail"
            tableModel.addRow(
                arrayOf(
                    res.testCase.id,
                    typeStr,
                    res.testCase.prompt.take(45),
                    rougeStr,
                    statusStr,
                    "${res.latencyMs} ms"
                )
            )
        }

        if (resultsTable.rowCount > 0) {
            resultsTable.setRowSelectionInterval(0, 0)
        }
    }

    private fun exportMarkdownReport() {
        val report = activeReport ?: return
        val sb = StringBuilder()
        sb.append("# 🛡️ Jörmungandr LLM Evaluation & Red-Team Report\n\n")
        sb.append("- **Suite**: ${report.suiteName}\n")
        sb.append("- **Target Model**: ${report.modelTarget}\n")
        sb.append("- **Overall Benchmark Score**: ${String.format("%.1f", report.overallScore)}%\n")
        sb.append("- **Red-Team Defense Rate**: ${String.format("%.1f", report.redTeamDefenseRate)}%\n")
        sb.append("- **Average Latency**: ${report.averageLatencyMs} ms\n\n")
        sb.append("| ID | Attack / Type | Status | Latency | Guardrail Note |\n")
        sb.append("|---|---|---|---|---|\n")
        for (r in report.results) {
            val type = r.testCase.attackVector?.displayName ?: "Benchmark"
            val status = if (r.passed) "PASSED" else "FAILED"
            sb.append("| ${r.testCase.id} | $type | $status | ${r.latencyMs}ms | ${r.defenseNote} |\n")
        }

        val md = sb.toString()
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(md), null)
        Messages.showInfoMessage("Report copied to clipboard in GitHub Markdown format!", "LLM Eval Report Export")
    }

    private fun createPill(label: JBLabel, color: Color): JPanel {
        return JPanel(FlowLayout(FlowLayout.CENTER, 4, 1)).apply {
            isOpaque = true
            background = Color(color.red, color.green, color.blue, 35)
            border = LineBorder(color, 1, true)
            label.foreground = color.darker()
            add(label)
        }
    }
}
