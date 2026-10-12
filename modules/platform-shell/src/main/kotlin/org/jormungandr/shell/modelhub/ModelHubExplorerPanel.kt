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

package org.jormungandr.shell.modelhub

import com.intellij.openapi.project.Project
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.*
import org.jormungandr.shell.copilot.local.LocalLlmModelInfo
import org.jormungandr.shell.copilot.local.OllamaService
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.net.URI
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableModel
import kotlin.math.roundToInt

/**
 * Metadata descriptor for open-source AI models on Hugging Face & Ollama.
 */
data class HuggingFaceModelCard(
    val modelId: String,
    val name: String,
    val category: String, // "Code Specialist", "Reasoning & Math", "Edge / Fast", "Embeddings"
    val parameterCount: String,
    val contextWindow: String,
    val license: String,
    val description: String,
    val ollamaTag: String? = null
)

/**
 * Interactive Studio Panel allowing data scientists to browse, benchmark,
 * and integrate open models from Ollama and Hugging Face directly within Jörmungandr.
 */
class ModelHubExplorerPanel(
    val project: Project? = null
) : JPanel(BorderLayout()) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ollamaService = com.intellij.openapi.application.ApplicationManager.getApplication().getService(OllamaService::class.java)

    // Ollama Tab components
    private val ollamaStatusLabel = JBLabel("Checking daemon...")
    private val localModelsTableModel = DefaultTableModel(arrayOf("Model Name", "Family", "Param Size", "Size (GB)", "Status"), 0)
    private val localModelsTable = JBTable(localModelsTableModel)
    private val pullModelField = JBTextField("qwen2.5-coder:7b", 16)
    private val pullStatusLabel = JBLabel("")

    // Hugging Face Tab components
    private val hfCategoryCombo = JComboBox(arrayOf("All Categories", "Code Specialist", "Reasoning & Math", "Edge / Fast", "Embeddings"))
    private val hfSearchField = JBTextField(16)
    private val hfCardsContainer = JPanel()

    private val curatedHfModels = listOf(
        HuggingFaceModelCard(
            modelId = "Qwen/Qwen2.5-Coder-7B-Instruct",
            name = "Qwen 2.5 Coder 7B",
            category = "Code Specialist",
            parameterCount = "7.61B",
            contextWindow = "128k tokens",
            license = "Apache 2.0",
            description = "Top-tier code generation model matching 33B+ class models. Supports 92 programming languages.",
            ollamaTag = "qwen2.5-coder:7b"
        ),
        HuggingFaceModelCard(
            modelId = "deepseek-ai/deepseek-coder-6.7b-instruct",
            name = "DeepSeek Coder 6.7B",
            category = "Code Specialist",
            parameterCount = "6.7B",
            contextWindow = "16k tokens",
            license = "DeepSeek Open License",
            description = "Trained from scratch on 2T code & math tokens. Highly capable at complex Python/Data Science tasks.",
            ollamaTag = "deepseek-coder:6.7b"
        ),
        HuggingFaceModelCard(
            modelId = "meta-llama/Llama-3.3-70B-Instruct",
            name = "Llama 3.3 70B Instruct",
            category = "Reasoning & Math",
            parameterCount = "70.6B",
            contextWindow = "128k tokens",
            license = "Llama 3.3 Community",
            description = "State-of-the-art open reasoning model matching previous 405B frontier performance.",
            ollamaTag = "llama3.3:70b"
        ),
        HuggingFaceModelCard(
            modelId = "microsoft/phi-4",
            name = "Phi-4 (14B)",
            category = "Reasoning & Math",
            parameterCount = "14B",
            contextWindow = "16k tokens",
            license = "MIT",
            description = "Microsoft's state-of-the-art synthetic reasoning model excelling at algorithmic programming & math.",
            ollamaTag = "phi4:14b"
        ),
        HuggingFaceModelCard(
            modelId = "meta-llama/Llama-3.2-3B-Instruct",
            name = "Llama 3.2 3B (Edge / Fast)",
            category = "Edge / Fast",
            parameterCount = "3.21B",
            contextWindow = "128k tokens",
            license = "Llama 3.2 Community",
            description = "Ultra-lightweight edge model designed for local laptop CPU/VRAM inference with near-instant TTFT.",
            ollamaTag = "llama3.2:3b"
        ),
        HuggingFaceModelCard(
            modelId = "BAAI/bge-m3",
            name = "BGE-M3 Multilingual Embedding",
            category = "Embeddings",
            parameterCount = "567M",
            contextWindow = "8,192 tokens",
            license = "MIT",
            description = "Industry standard multi-function dense & sparse retrieval embedding model for RAG & vector databases.",
            ollamaTag = "bge-m3"
        )
    )

    init {
        buildUi()
        refreshOllamaModels()
    }

    private fun buildUi() {
        val tabbedPane = JBTabbedPane()

        // --- Tab 1: Local Ollama Model Manager ---
        val ollamaPanel = JPanel(BorderLayout(0, 10)).apply {
            border = EmptyBorder(12, 12, 12, 12)
        }

        val ollamaHeader = JPanel(BorderLayout()).apply {
            border = CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color(220, 220, 220)),
                EmptyBorder(0, 0, 8, 0)
            )
        }
        val statusBox = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        statusBox.add(ollamaStatusLabel)
        val refreshBtn = JButton("🔄 Refresh Models").apply {
            isFocusable = false
            addActionListener { refreshOllamaModels() }
        }
        statusBox.add(refreshBtn)
        ollamaHeader.add(statusBox, BorderLayout.WEST)

        val pullBox = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
        pullBox.add(JBLabel("Pull Model:"))
        pullBox.add(pullModelField)
        val pullBtn = JButton("📥 Pull (ollama pull)").apply {
            isFocusable = false
            addActionListener {
                val modelName = pullModelField.text.trim()
                if (modelName.isNotEmpty()) {
                    triggerModelPull(modelName)
                }
            }
        }
        pullBox.add(pullBtn)
        ollamaHeader.add(pullBox, BorderLayout.EAST)
        ollamaPanel.add(ollamaHeader, BorderLayout.NORTH)

        localModelsTable.rowHeight = 24
        ollamaPanel.add(JBScrollPane(localModelsTable), BorderLayout.CENTER)

        val bottomToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 4)).apply {
            val benchmarkBtn = JButton("⚡ Benchmark Selected Model").apply {
                isFocusable = false
                addActionListener { benchmarkSelectedModel() }
            }
            add(benchmarkBtn)
            add(pullStatusLabel)
        }
        ollamaPanel.add(bottomToolbar, BorderLayout.SOUTH)
        tabbedPane.addTab("🦙 Ollama Local Daemon", ollamaPanel)

        // --- Tab 2: Hugging Face Hub Explorer ---
        val hfPanel = JPanel(BorderLayout(0, 10)).apply {
            border = EmptyBorder(12, 12, 12, 12)
        }
        val hfToolbar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 4)).apply {
            add(JBLabel("Filter Category:"))
            add(hfCategoryCombo)
            add(JBLabel("Search:"))
            add(hfSearchField)
            val filterAction = { updateHfCards() }
            hfCategoryCombo.addActionListener { filterAction() }
            hfSearchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = filterAction()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = filterAction()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = filterAction()
            })
        }
        hfPanel.add(hfToolbar, BorderLayout.NORTH)

        hfCardsContainer.layout = BoxLayout(hfCardsContainer, BoxLayout.Y_AXIS)
        hfPanel.add(JBScrollPane(hfCardsContainer), BorderLayout.CENTER)
        tabbedPane.addTab("🤗 Hugging Face Model Hub", hfPanel)

        add(tabbedPane, BorderLayout.CENTER)
        updateHfCards()
    }

    private fun refreshOllamaModels() {
        val reachable = ollamaService?.isServerReachable() ?: false
        if (reachable) {
            ollamaStatusLabel.text = "🟢 Ollama Daemon Active (${ollamaService?.endpointUrl})"
            ollamaStatusLabel.foreground = Color(40, 167, 69)
        } else {
            ollamaStatusLabel.text = "🟡 Ollama Offline (Using built-in offline fallbacks)"
            ollamaStatusLabel.foreground = Color(217, 119, 6)
        }

        localModelsTableModel.rowCount = 0
        val models = ollamaService?.listLocalModels() ?: emptyList()
        for (m in models) {
            localModelsTableModel.addRow(arrayOf(
                m.name,
                m.family,
                m.parameterSize,
                "%.1f GB".format(m.sizeGb),
                if (reachable) "Installed / Ready" else "Cached Preset"
            ))
        }
    }

    private fun triggerModelPull(modelName: String) {
        pullStatusLabel.text = "Starting pull for $modelName..."
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val process = ProcessBuilder("ollama", "pull", modelName)
                        .redirectErrorStream(true)
                        .start()
                    val exited = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
                    if (exited && process.exitValue() == 0) {
                        withContext(Dispatchers.Main) {
                            pullStatusLabel.text = "✅ Successfully pulled $modelName!"
                            refreshOllamaModels()
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            pullStatusLabel.text = "⚠️ Pull finished or backgrounded: $modelName"
                            refreshOllamaModels()
                        }
                    }
                }
            } catch (e: Exception) {
                pullStatusLabel.text = "❌ Pull error: ${e.message}"
            }
        }
    }

    private fun benchmarkSelectedModel() {
        val selRow = localModelsTable.selectedRow
        if (selRow < 0) {
            JOptionPane.showMessageDialog(this, "Please select a model from the table to benchmark.", "Benchmark", JOptionPane.INFORMATION_MESSAGE)
            return
        }
        val modelName = localModelsTableModel.getValueAt(selRow, 0) as String
        val service = ollamaService ?: return
        pullStatusLabel.text = "Benchmarking $modelName..."

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                service.generate(
                    model = modelName,
                    prompt = "def quicksort(arr):",
                    systemPrompt = "You are a concise Python code generator. Complete the function in 20 tokens."
                )
            }
            withContext(Dispatchers.Main) {
                if (result != null) {
                    pullStatusLabel.text = "⚡ $modelName: ${result.tokensPerSecond.roundToInt()} tok/s (Latency: ${result.latencyMs}ms)"
                    JOptionPane.showMessageDialog(
                        this@ModelHubExplorerPanel,
                        """
                        Benchmark Results for $modelName:
                        ------------------------------------
                        Speed:       ${"%.1f".format(result.tokensPerSecond)} tokens/second
                        Latency:     ${result.latencyMs} ms
                        Tokens:      ${result.tokenCount} tokens
                        Model Used:  ${result.modelUsed}
                        """.trimIndent(),
                        "Model Benchmark Complete",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                } else {
                    pullStatusLabel.text = "❌ Benchmark failed for $modelName"
                }
            }
        }
    }

    private fun updateHfCards() {
        hfCardsContainer.removeAll()
        val catFilter = hfCategoryCombo.selectedItem as? String ?: "All Categories"
        val query = hfSearchField.text.trim().lowercase()

        val filtered = curatedHfModels.filter { model ->
            (catFilter == "All Categories" || model.category == catFilter) &&
            (query.isEmpty() || model.name.lowercase().contains(query) || model.modelId.lowercase().contains(query) || model.description.lowercase().contains(query))
        }

        for (m in filtered) {
            val card = createHfModelCard(m)
            hfCardsContainer.add(card)
            hfCardsContainer.add(Box.createVerticalStrut(8))
        }

        hfCardsContainer.revalidate()
        hfCardsContainer.repaint()
    }

    private fun createHfModelCard(m: HuggingFaceModelCard): JComponent {
        val panel = JPanel(BorderLayout(10, 8)).apply {
            border = CompoundBorder(
                BorderFactory.createLineBorder(Color(220, 226, 235), 1, true),
                EmptyBorder(10, 14, 10, 14)
            )
            background = Color(250, 252, 255)
            maximumSize = Dimension(Int.MAX_VALUE, 125)
        }

        val topRow = JPanel(BorderLayout())
        topRow.isOpaque = false
        val titleLabel = JBLabel(m.name).apply {
            font = font.deriveFont(Font.BOLD, 13f)
            foreground = Color(15, 23, 42)
        }
        val metaLabel = JBLabel("${m.parameterCount} | Context: ${m.contextWindow} | License: ${m.license}").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            foreground = Color(100, 116, 139)
        }
        topRow.add(titleLabel, BorderLayout.WEST)
        topRow.add(metaLabel, BorderLayout.EAST)
        panel.add(topRow, BorderLayout.NORTH)

        val descLabel = JBLabel("<html>${m.description}</html>").apply {
            font = font.deriveFont(Font.PLAIN, 11.5f)
            foreground = Color(51, 65, 85)
        }
        panel.add(descLabel, BorderLayout.CENTER)

        val actionsRow = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0)).apply { isOpaque = false }
        val copySnippetBtn = JButton("🐍 Copy Transformers").apply {
            isFocusable = false
            font = font.deriveFont(11f)
            toolTipText = "Copy python code to load this model with HuggingFace Transformers"
            addActionListener {
                val code = """
from transformers import AutoModelForCausalLM, AutoTokenizer

model_id = "${m.modelId}"
tokenizer = AutoTokenizer.from_pretrained(model_id)
model = AutoModelForCausalLM.from_pretrained(model_id, device_map="auto", torch_dtype="auto")

inputs = tokenizer("def predict():", return_tensors="pt").to(model.device)
outputs = model.generate(**inputs, max_new_tokens=100)
print(tokenizer.decode(outputs[0], skip_special_tokens=True))
""".trimIndent()
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(code), null)
                JOptionPane.showMessageDialog(this@ModelHubExplorerPanel, "Copied Transformers code snippet for ${m.name}!", "Model Hub", JOptionPane.INFORMATION_MESSAGE)
            }
        }
        actionsRow.add(copySnippetBtn)

        if (m.ollamaTag != null) {
            val pullOllamaBtn = JButton("🦙 Pull in Ollama").apply {
                isFocusable = false
                font = font.deriveFont(11f)
                addActionListener {
                    pullModelField.text = m.ollamaTag
                    triggerModelPull(m.ollamaTag)
                }
            }
            actionsRow.add(pullOllamaBtn)
        }

        val openHfBtn = JButton("🌐 Open Model Card").apply {
            isFocusable = false
            font = font.deriveFont(11f)
            addActionListener {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().browse(URI("https://huggingface.co/${m.modelId}"))
                }
            }
        }
        actionsRow.add(openHfBtn)

        panel.add(actionsRow, BorderLayout.SOUTH)
        return panel
    }
}
