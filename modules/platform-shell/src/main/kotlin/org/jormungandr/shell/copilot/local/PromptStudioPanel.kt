package org.jormungandr.shell.copilot.local

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

/**
 * Interactive Prompt Engineering & Local LLM Evaluation Studio.
 * Allows selecting local Ollama/vLLM models, testing system prompts, tuning hyperparameters,
 * viewing live token throughput, and injecting results directly into active editors or notebook cells.
 */
class PromptStudioPanel(private val project: Project) : JPanel(BorderLayout(0, 10)) {

    private val ollamaService = OllamaService.getInstance()

    // Model selection
    private val modelComboBox = JComboBox<String>()
    private val refreshModelsBtn = JButton("🔄 Refresh")
    private val serverStatusLabel = JBLabel("Checking...")

    // Prompt areas
    private val systemPromptArea = JBTextArea(3, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        text = "You are an expert Data Science and Python Software Engineer. Write clean, vectorized, production-grade code with strict typing."
    }
    private val userPromptArea = JBTextArea(4, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        text = "Write a high-performance function to calculate rolling 30-day volatility for a stock price DataFrame."
    }

    // Hyperparameters
    private val tempSlider = JSlider(0, 100, 20) // 0.0 to 1.0 (step 0.01)
    private val tempValueLabel = JBLabel("0.20")

    // Output area & telemetry
    private val outputArea = JBTextArea(10, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        isEditable = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val metricsLabel = JBLabel("⚡ Ready. Select a model and click 'Run Inference'.")

    private val executeBtn = JButton("⚡ Run Local Inference").apply {
        font = font.deriveFont(Font.BOLD, 12f)
    }
    private val insertBtn = JButton("📋 Insert into Active Editor").apply {
        isEnabled = false
    }

    init {
        border = JBUI.Borders.empty(12)
        buildUi()
        refreshModelList()
    }

    private val pullModelBtn = JButton("⬇️ Pull Model").apply {
        font = font.deriveFont(Font.BOLD, 11f)
        toolTipText = "Download selected/typed model directly into local Ollama"
    }
    private val pullProgressPanel = JPanel(BorderLayout(6, 2)).apply {
        isVisible = false
        border = JBUI.Borders.empty(4, 2)
    }
    private val pullProgressBar = JProgressBar(0, 100).apply {
        isStringPainted = true
    }
    private val pullStatusLabel = JBLabel("Ready")

    private fun buildUi() {
        // 1. Top Configuration Bar
        val topConfigPanel = JPanel(BorderLayout(8, 4)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(8, 10)
            )

            modelComboBox.isEditable = true
            pullModelBtn.addActionListener { pullSelectedModel() }

            val modelRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
                add(JBLabel("🤖 Local Model:"))
                add(modelComboBox)
                add(pullModelBtn)
                add(refreshModelsBtn)
                add(serverStatusLabel)
                val badge = org.jormungandr.shell.license.LicenseBadgeFactory.createBadgeComponent(
                    org.jormungandr.core.license.LicenseService.getInstance().currentLicense.value,
                    large = false
                )
                add(badge)
            }

            pullProgressPanel.add(pullStatusLabel, BorderLayout.NORTH)
            pullProgressPanel.add(pullProgressBar, BorderLayout.CENTER)

            val presetsRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                add(JBLabel("🎯 Presets:"))
                val presets = listOf(
                    "Pandas Refactor" to "Refactor this Pandas transformation to use vectorized method chaining and avoid SettingWithCopyWarning.",
                    "PyTest Fixtures" to "Generate comprehensive pytest unit tests with synthetic data mock fixtures and edge cases.",
                    "Docstrings" to "Generate Google-style Python docstring detailing types, parameters, returns, and raises.",
                    "DuckDB SQL" to "Convert this Pandas query into an in-memory DuckDB zero-copy SQL query over Parquet files."
                )
                presets.forEach { (name, promptText) ->
                    val b = JButton(name).apply {
                        font = font.deriveFont(11f)
                        addActionListener {
                            userPromptArea.text = promptText
                        }
                    }
                    add(b)
                }
            }

            val header = JPanel(BorderLayout()).apply {
                add(modelRow, BorderLayout.NORTH)
                add(pullProgressPanel, BorderLayout.CENTER)
                add(presetsRow, BorderLayout.SOUTH)
            }
            add(header, BorderLayout.CENTER)
        }

        // 2. Middle Editor Split (System + User Prompt + Hyperparameters)
        val promptControls = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // System prompt card
        promptControls.add(createLabeledCard("🛠️ System Prompt (Instruction & Personality):", JBScrollPane(systemPromptArea)))
        promptControls.add(Box.createVerticalStrut(6))

        // User prompt card
        promptControls.add(createLabeledCard("💬 User Request / Code Input:", JBScrollPane(userPromptArea)))
        promptControls.add(Box.createVerticalStrut(6))

        // Hyperparameters card
        val hyperPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(6, 10)
            )
            add(JBLabel("🌡️ Temperature:"))
            add(tempSlider)
            add(tempValueLabel)
        }
        tempSlider.addChangeListener {
            tempValueLabel.text = String.format("%.2f", tempSlider.value / 100.0)
        }
        promptControls.add(hyperPanel)
        promptControls.add(Box.createVerticalStrut(6))

        // Actions row
        val btnRow = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
            add(executeBtn)
            add(insertBtn)
            add(metricsLabel)
        }
        promptControls.add(btnRow)

        // 3. Output Canvas
        val outputPanel = JPanel(BorderLayout(0, 4)).apply {
            border = JBUI.Borders.emptyTop(6)
            val outHeader = JBLabel("📄 Model Response & Code Generation:").apply {
                font = font.deriveFont(Font.BOLD, 12f)
            }
            add(outHeader, BorderLayout.NORTH)
            add(JBScrollPane(outputArea), BorderLayout.CENTER)
        }

        // Split Pane
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, promptControls, outputPanel).apply {
            resizeWeight = 0.45
            dividerSize = 4
        }

        add(topConfigPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)

        // Listeners
        refreshModelsBtn.addActionListener { refreshModelList() }

        executeBtn.addActionListener { runInference() }

        insertBtn.addActionListener { insertCodeIntoActiveEditor() }
    }

    private fun createLabeledCard(title: String, component: JComponent): JPanel {
        return JPanel(BorderLayout(0, 4)).apply {
            val lbl = JBLabel(title).apply { font = font.deriveFont(Font.BOLD, 11f) }
            add(lbl, BorderLayout.NORTH)
            add(component, BorderLayout.CENTER)
        }
    }

    private fun refreshModelList() {
        modelComboBox.removeAllItems()
        val reachable = ollamaService.isServerReachable()
        if (reachable) {
            serverStatusLabel.text = "🟢 Ollama Daemon Active"
            serverStatusLabel.foreground = JBColor(Color(40, 167, 69), Color(60, 185, 90))
        } else {
            serverStatusLabel.text = "🟡 Ollama Offline (Using Local Heuristic Engine)"
            serverStatusLabel.foreground = JBColor(Color(230, 140, 0), Color(255, 180, 50))
        }

        val models = ollamaService.listLocalModels()
        models.forEach { modelComboBox.addItem(it.name) }
    }

    private fun runInference() {
        val selectedModel = modelComboBox.selectedItem as? String ?: "jormungandr-offline-heuristic"
        val prompt = userPromptArea.text.trim()
        val system = systemPromptArea.text.trim()
        val temp = tempSlider.value / 100.0

        if (!org.jormungandr.core.license.LicenseService.getInstance().isLicensed()) {
            val choice = JOptionPane.showConfirmDialog(
                this,
                "Prompt Studio is locked in Trial Mode.\nA valid User, Developer, or Admin commercial license is required to execute AI completions.\n\nWould you like to open the License Manager to activate now?",
                "Commercial License Required",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )
            if (choice == JOptionPane.YES_OPTION) {
                org.jormungandr.shell.license.LicenseDialog(project).show()
            }
            return
        }

        if (prompt.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a user prompt.", "Empty Prompt", JOptionPane.WARNING_MESSAGE)
            return
        }

        executeBtn.isEnabled = false
        executeBtn.text = "⏳ Generating..."
        metricsLabel.text = "⚡ Generating tokens via $selectedModel..."

        SwingWorker_run {
            val result = ollamaService.generate(selectedModel, prompt, system, temp)
            SwingUtilities.invokeLater {
                outputArea.text = result.responseText
                outputArea.caretPosition = 0
                metricsLabel.text = "⚡ Completed in ${result.latencyMs}ms | ${String.format("%.1f", result.tokensPerSecond)} tok/s (${result.tokenCount} tokens) • [${result.modelUsed}]"
                executeBtn.isEnabled = true
                executeBtn.text = "⚡ Run Local Inference"
                insertBtn.isEnabled = result.responseText.isNotBlank()
            }
        }
    }

    private fun insertCodeIntoActiveEditor() {
        val textToInsert = outputArea.text
        if (textToInsert.isBlank()) return

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            JOptionPane.showMessageDialog(this, "No active code editor or notebook open.", "Insert Failed", JOptionPane.INFORMATION_MESSAGE)
            return
        }

        val document = editor.document
        val primaryCaret = editor.caretModel.primaryCaret

        WriteCommandAction.runWriteCommandAction(project) {
            val offset = primaryCaret.offset
            document.insertString(offset, "\n$textToInsert\n")
        }
    }

    private fun pullSelectedModel() {
        if (!org.jormungandr.core.license.LicenseService.getInstance().isLicensed()) {
            JOptionPane.showMessageDialog(
                this,
                "Model Downloading & Local AI Agent deployment is locked in Trial Mode.\nA valid User, Developer, or Admin license is required.",
                "Commercial License Required",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        val rawName = (modelComboBox.editor.item ?: modelComboBox.selectedItem)?.toString()?.trim() ?: ""
        if (rawName.isBlank() || rawName.contains("jormungandr-offline-heuristic")) {
            JOptionPane.showMessageDialog(this, "Please enter or select a valid model name (e.g. 'llama3:8b', 'deepseek-coder:6.7b').", "Invalid Model Name", JOptionPane.WARNING_MESSAGE)
            return
        }

        pullModelBtn.isEnabled = false
        pullProgressPanel.isVisible = true
        pullProgressBar.value = 0
        pullStatusLabel.text = "Initiating download for '$rawName'..."

        SwingWorker_run {
            ollamaService.pullModel(rawName) { progress ->
                SwingUtilities.invokeLater {
                    if (progress.error != null) {
                        pullStatusLabel.text = "❌ Failed: ${progress.error}"
                        pullModelBtn.isEnabled = true
                    } else if (progress.isDone) {
                        pullProgressBar.value = 100
                        pullStatusLabel.text = "✅ Model '$rawName' installed successfully!"
                        pullModelBtn.isEnabled = true
                        refreshModelList()
                        modelComboBox.selectedItem = rawName
                    } else {
                        pullProgressBar.value = progress.percent.toInt()
                        val mbCompleted = progress.completedBytes / (1024 * 1024)
                        val mbTotal = progress.totalBytes / (1024 * 1024)
                        val sizeStr = if (mbTotal > 0) " (${mbCompleted}MB / ${mbTotal}MB)" else ""
                        pullStatusLabel.text = "⬇️ ${progress.status}: ${String.format("%.1f%%", progress.percent)}$sizeStr"
                    }
                }
            }
        }
    }

    private fun SwingWorker_run(block: () -> Unit) {
        val worker = object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                block()
            }
        }
        worker.execute()
    }
}
