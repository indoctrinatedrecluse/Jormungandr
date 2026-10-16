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

package org.jormungandr.shell.julia

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.table.DefaultTableModel

/**
 * First-Class Julia Language REPL & Scientific Package Studio Swing UI.
 */
class JuliaStudioPanel(
    private val project: Project? = null,
    private val engine: JuliaReplEngine = JuliaReplEngine()
) : JPanel(BorderLayout()) {

    private val consoleArea = JBTextArea().apply {
        isEditable = false
        font = Font("Consolas", Font.PLAIN, 13)
        background = Color(24, 24, 24)
        foreground = Color(230, 230, 230)
        margin = Insets(8, 8, 8, 8)
    }

    private val inputField = JBTextField().apply {
        font = Font("Consolas", Font.PLAIN, 13)
    }

    private val promptLabel = JBLabel("julia> ").apply {
        font = Font("Consolas", Font.BOLD, 13)
        foreground = Color(56, 142, 60)
    }

    private val versionBadge = JBLabel(engine.installationInfo.version)
    private val projectBadge = JBLabel("Env: ${engine.installationInfo.activeProject}")

    private val packageTableModel = DefaultTableModel(arrayOf("Package", "Category", "Status"), 0)
    private val packageTable = JBTable(packageTableModel)

    private val inputHistory = mutableListOf<String>()
    private var historyIndex = -1
    private var currentMode = JuliaReplMode.JULIA

    init {
        setupUI()
        printWelcomeBanner()
        populatePackageList()
    }

    private fun setupUI() {
        // 1. Top Control Bar
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(6, 12, 6, 12))
            background = Color(245, 245, 245)
        }

        val leftControls = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftControls.add(JBLabel("<b>🔴🟢🟣 Julia Studio</b>"))
        leftControls.add(createPill(versionBadge, Color(156, 39, 176)))
        leftControls.add(createPill(projectBadge, Color(33, 150, 243)))

        val rightControls = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val modeCombo = JComboBox(arrayOf("julia> (Code)", "pkg> (Package)", "shell> (System)", "help?> (Docs)")).apply {
            addActionListener {
                currentMode = when (selectedIndex) {
                    1 -> JuliaReplMode.PKG
                    2 -> JuliaReplMode.SHELL
                    3 -> JuliaReplMode.HELP
                    else -> JuliaReplMode.JULIA
                }
                updatePromptDisplay()
            }
        }
        rightControls.add(JBLabel("Mode:"))
        rightControls.add(modeCombo)

        val clearBtn = JButton("Clear").apply {
            addActionListener {
                consoleArea.text = ""
                printWelcomeBanner()
            }
        }
        rightControls.add(clearBtn)

        topPanel.add(leftControls, BorderLayout.WEST)
        topPanel.add(rightControls, BorderLayout.EAST)

        // 2. Center Split (REPL Console + Package Studio)
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            dividerLocation = 600
            resizeWeight = 0.7
        }

        // Left Console Panel
        val consolePanel = JPanel(BorderLayout())
        consolePanel.add(JBScrollPane(consoleArea), BorderLayout.CENTER)

        val inputPanel = JPanel(BorderLayout(6, 0)).apply {
            border = EmptyBorder(6, 8, 6, 8)
        }
        inputPanel.add(promptLabel, BorderLayout.WEST)
        inputPanel.add(inputField, BorderLayout.CENTER)

        val runBtn = JButton("Run").apply {
            addActionListener { executeInput() }
        }
        inputPanel.add(runBtn, BorderLayout.EAST)
        consolePanel.add(inputPanel, BorderLayout.SOUTH)

        // Setup Key Listener for History Navigation and Enter
        inputField.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ENTER -> executeInput()
                    KeyEvent.VK_UP -> navigateHistory(-1)
                    KeyEvent.VK_DOWN -> navigateHistory(1)
                }
            }
        })

        // Right Package & Snippet Studio Panel
        val rightStudio = JPanel(BorderLayout(0, 8)).apply {
            border = EmptyBorder(8, 8, 8, 8)
        }

        val rightHeader = JPanel(BorderLayout()).apply {
            val title = JBLabel("<b>Scientific Package Manager & Snippets</b>")
            add(title, BorderLayout.NORTH)
        }

        // Snippet Selector
        val snippetPanel = JPanel(BorderLayout(4, 0))
        val snippetCombo = JComboBox(JuliaReplEngine.getScientificSnippets().keys.toTypedArray())
        val insertSnippetBtn = JButton("Insert").apply {
            addActionListener {
                val selected = snippetCombo.selectedItem as? String ?: return@addActionListener
                val code = JuliaReplEngine.getScientificSnippets()[selected] ?: ""
                inputField.text = code.replace("\n", " ")
                inputField.requestFocusInWindow()
            }
        }
        snippetPanel.add(JBLabel("Snippets:"), BorderLayout.WEST)
        snippetPanel.add(snippetCombo, BorderLayout.CENTER)
        snippetPanel.add(insertSnippetBtn, BorderLayout.EAST)
        rightHeader.add(snippetPanel, BorderLayout.SOUTH)

        rightStudio.add(rightHeader, BorderLayout.NORTH)
        rightStudio.add(JBScrollPane(packageTable), BorderLayout.CENTER)

        // Package install bar
        val pkgInstallBar = JPanel(BorderLayout(4, 0))
        val pkgField = JBTextField(12)
        val addPkgBtn = JButton("Pkg.add()").apply {
            addActionListener {
                val pkgName = pkgField.text.trim()
                if (pkgName.isNotEmpty()) {
                    val res = engine.evaluate("] add $pkgName")
                    appendToConsole("pkg> add $pkgName\n${res.output}\n\n", Color(76, 175, 80))
                    packageTableModel.addRow(arrayOf(pkgName, "Scientific", "Installed"))
                    pkgField.text = ""
                }
            }
        }
        pkgInstallBar.add(JBLabel("Add:"), BorderLayout.WEST)
        pkgInstallBar.add(pkgField, BorderLayout.CENTER)
        pkgInstallBar.add(addPkgBtn, BorderLayout.EAST)
        rightStudio.add(pkgInstallBar, BorderLayout.SOUTH)

        splitPane.leftComponent = consolePanel
        splitPane.rightComponent = rightStudio

        add(topPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
    }

    private fun updatePromptDisplay() {
        when (currentMode) {
            JuliaReplMode.JULIA -> {
                promptLabel.text = "julia> "
                promptLabel.foreground = Color(56, 142, 60)
            }
            JuliaReplMode.PKG -> {
                promptLabel.text = "pkg> "
                promptLabel.foreground = Color(33, 150, 243)
            }
            JuliaReplMode.SHELL -> {
                promptLabel.text = "shell> "
                promptLabel.foreground = Color(255, 152, 0)
            }
            JuliaReplMode.HELP -> {
                promptLabel.text = "help?> "
                promptLabel.foreground = Color(156, 39, 176)
            }
        }
    }

    private fun executeInput() {
        val text = inputField.text.trim()
        if (text.isEmpty()) return

        inputHistory.add(text)
        historyIndex = inputHistory.size

        val fullCommand = when {
            text.startsWith("]") || text.startsWith(";") || text.startsWith("?") -> text
            currentMode == JuliaReplMode.PKG -> "] $text"
            currentMode == JuliaReplMode.SHELL -> "; $text"
            currentMode == JuliaReplMode.HELP -> "? $text"
            else -> text
        }

        val prompt = promptLabel.text
        appendToConsole("$prompt$text\n", Color(255, 255, 255))
        inputField.text = ""

        val result = engine.evaluate(fullCommand)
        if (result.isError) {
            appendToConsole("${result.output}\n\n", Color(244, 67, 54))
        } else {
            appendToConsole("${result.output}\n\n", Color(129, 199, 132))
        }
    }

    private fun navigateHistory(direction: Int) {
        if (inputHistory.isEmpty()) return
        historyIndex = (historyIndex + direction).coerceIn(0, inputHistory.size)
        inputField.text = if (historyIndex in inputHistory.indices) inputHistory[historyIndex] else ""
    }

    private fun printWelcomeBanner() {
        val banner = """
               _
   _       _ _(_)_     |  Documentation: https://docs.julialang.org
  (_)     | (_) (_)    |
   _ _   _| |_  __ _   |  Type "?" for help, "]" for Pkg mode.
  | | | | | | |/ _` |  |
  | | |_| | | | (_| |  |  Jörmungandr Julia Scientific REPL Studio
 _/ |\__'_|_|_|\__'_|  |  ${engine.installationInfo.version}
|__/                   |

        """.trimIndent()
        appendToConsole(banner + "\n\n", Color(100, 181, 246))
    }

    private fun populatePackageList() {
        val packages = listOf(
            Triple("DataFrames.jl", "Data Processing", "Ready"),
            Triple("Plots.jl", "Visualization", "Ready"),
            Triple("DifferentialEquations.jl", "Numerics & ODE", "Ready"),
            Triple("Flux.jl", "Deep Learning", "Ready"),
            Triple("CSV.jl", "I/O", "Ready"),
            Triple("LinearAlgebra", "Base Math", "Ready"),
            Triple("BenchmarkTools.jl", "Performance", "Ready")
        )
        for (pkg in packages) {
            packageTableModel.addRow(arrayOf(pkg.first, pkg.second, pkg.third))
        }
    }

    private fun appendToConsole(text: String, color: Color) {
        consoleArea.append(text)
        consoleArea.caretPosition = consoleArea.document.length
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
