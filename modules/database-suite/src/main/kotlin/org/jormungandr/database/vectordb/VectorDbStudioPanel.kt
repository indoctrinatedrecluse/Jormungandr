package org.jormungandr.database.vectordb

import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * Flagship Vector Database & RAG Semantic Search Studio Panel.
 * Includes:
 * - Multi-collection vector index explorer (LanceDB, Chroma, FAISS, SQLite-VSS)
 * - Semantic nearest-neighbor similarity search
 * - Interactive 2D PCA cluster projection canvas
 * - Live RAG document chunking & token estimation simulator
 * - Python integration code exporter (LanceDB / LangChain / LlamaIndex)
 */
class VectorDbStudioPanel : JPanel(BorderLayout()) {

    private var currentCollection: VectorCollection = VectorDbEngine.SAMPLE_COLLECTIONS.first()
    private val canvas = VectorProjectionCanvas()

    // Header controls
    private val collectionCombo = ComboBox(VectorDbEngine.SAMPLE_COLLECTIONS.map { it.name }.toTypedArray())
    private val metricLabel = JBLabel("Metric: Cosine")
    private val dimensionBadge = JBLabel("Dim: 384")
    private val docCountBadge = JBLabel("Docs: ${currentCollection.documents.size}")

    // Search controls
    private val queryField = JBTextField("What are self-attention mechanisms in transformers?", 24)
    private val topKSpinner = JSpinner(SpinnerNumberModel(3, 1, 10, 1))
    private val searchButton = JButton("⚡ Semantic Search")

    // Results container
    private val resultsPanel = JPanel()

    // Chunking Simulator controls
    private val chunkStrategyCombo = ComboBox(ChunkingStrategy.values().map { it.displayName }.toTypedArray())
    private val chunkSizeSpinner = JSpinner(SpinnerNumberModel(200, 50, 2000, 50))
    private val chunkOverlapSpinner = JSpinner(SpinnerNumberModel(40, 0, 500, 10))
    private val chunkInputArea = JBTextArea(
        """# Retrieval-Augmented Generation (RAG) Architecture
RAG combines dense retrieval models with pre-trained seq2seq generative LLMs.
The retriever fetches top-k relevant context chunks from vector stores like LanceDB or Chroma.
The generator conditions on both the original query and the retrieved context documents to produce factual answers.
This substantially mitigates hallucination and enables dynamic knowledge updates without expensive fine-tuning.""",
        6, 30
    )
    private val chunksContainer = JPanel()

    init {
        resultsPanel.layout = BoxLayout(resultsPanel, BoxLayout.Y_AXIS)
        resultsPanel.background = Color(252, 252, 252)

        chunksContainer.layout = BoxLayout(chunksContainer, BoxLayout.Y_AXIS)
        chunksContainer.background = Color(252, 252, 252)

        setupUI()
        setupListeners()
        updateProjection()
        performSearch()
        simulateChunking()
    }

    private fun setupUI() {
        // 1. Top Control Bar
        val topPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(8, 12, 8, 12))
            background = Color(245, 245, 245)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply { isOpaque = false }
        leftHeader.add(JBLabel("<b>Vector Collection:</b>"))
        leftHeader.add(collectionCombo)
        leftHeader.add(createPill(dimensionBadge, Color(38, 139, 210)))
        leftHeader.add(createPill(docCountBadge, Color(42, 161, 152)))
        leftHeader.add(createPill(metricLabel, Color(133, 153, 0)))

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply { isOpaque = false }
        val exportButton = JButton("📋 Export Python RAG Code").apply {
            isFocusPainted = false
            addActionListener { exportPythonCode() }
        }
        rightHeader.add(exportButton)

        topPanel.add(leftHeader, BorderLayout.WEST)
        topPanel.add(rightHeader, BorderLayout.EAST)

        // 2. Query Search Toolbar
        val searchBar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 6)).apply {
            border = EmptyBorder(6, 12, 6, 12)
            background = Color(250, 250, 250)
        }
        searchBar.add(JBLabel("<b>Semantic Query:</b>"))
        searchBar.add(queryField)
        searchBar.add(JBLabel("Top-K:"))
        searchBar.add(topKSpinner)
        searchBar.add(searchButton.apply {
            background = Color(38, 139, 210)
            foreground = Color.BLACK
            isFocusPainted = false
            font = Font("Segoe UI", Font.BOLD, 12)
        })

        val northContainer = JPanel(BorderLayout())
        northContainer.add(topPanel, BorderLayout.NORTH)
        northContainer.add(searchBar, BorderLayout.SOUTH)
        add(northContainer, BorderLayout.NORTH)

        // 3. Main Center SplitPane: (Left: Chunking Simulator | Center: 2D Canvas | Right: Results)
        val centerSplit = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            resizeWeight = 0.55
            dividerSize = 4
        }

        // Center visual canvas
        val canvasContainer = JPanel(BorderLayout()).apply {
            border = LineBorder(Color(220, 220, 220), 1)
            add(canvas, BorderLayout.CENTER)
        }
        centerSplit.leftComponent = canvasContainer

        // Right side: Tabs for Search Results vs Chunking Simulator
        val tabbedPane = JTabbedPane()

        // Tab 1: Nearest Neighbor Search Results
        val searchResultsScroll = JBScrollPane(resultsPanel).apply {
            border = EmptyBorder(0, 0, 0, 0)
        }
        tabbedPane.addTab("Top-K Matches", searchResultsScroll)

        // Tab 2: Document Chunking Simulator
        val chunkingTab = createChunkingSimulatorPanel()
        tabbedPane.addTab("Chunking Simulator", chunkingTab)

        centerSplit.rightComponent = tabbedPane
        add(centerSplit, BorderLayout.CENTER)
    }

    private fun createChunkingSimulatorPanel(): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 8, 8, 8)
            background = Color(252, 252, 252)
        }

        val topControls = JPanel(GridLayout(3, 1, 4, 4)).apply { isOpaque = false }

        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        row1.add(JBLabel("Strategy:"))
        row1.add(chunkStrategyCombo)

        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        row2.add(JBLabel("Chunk Size (chars):"))
        row2.add(chunkSizeSpinner)
        row2.add(JBLabel("Overlap:"))
        row2.add(chunkOverlapSpinner)

        val row3 = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val splitBtn = JButton("✂️ Split Document").apply {
            isFocusPainted = false
            addActionListener { simulateChunking() }
        }
        row3.add(splitBtn)

        topControls.add(row1)
        topControls.add(row2)
        topControls.add(row3)

        val inputSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            resizeWeight = 0.35
            dividerSize = 4
        }

        val inputPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(4, 4, 4, 4))
            add(JBLabel("<b>Sample Document Input:</b>"), BorderLayout.NORTH)
            add(JBScrollPane(chunkInputArea), BorderLayout.CENTER)
        }
        inputSplit.topComponent = inputPanel

        val outputPanel = JPanel(BorderLayout()).apply {
            border = CompoundBorder(LineBorder(Color(220, 220, 220), 1), EmptyBorder(4, 4, 4, 4))
            add(JBLabel("<b>Generated Chunks Preview:</b>"), BorderLayout.NORTH)
            add(JBScrollPane(chunksContainer), BorderLayout.CENTER)
        }
        inputSplit.bottomComponent = outputPanel

        panel.add(topControls, BorderLayout.NORTH)
        panel.add(inputSplit, BorderLayout.CENTER)
        return panel
    }

    private fun setupListeners() {
        collectionCombo.addActionListener {
            val selectedName = collectionCombo.selectedItem as? String ?: return@addActionListener
            currentCollection = VectorDbEngine.SAMPLE_COLLECTIONS.firstOrNull { it.name == selectedName } ?: currentCollection
            dimensionBadge.text = "Dim: ${currentCollection.dimension}"
            docCountBadge.text = "Docs: ${currentCollection.documents.size}"
            metricLabel.text = "Metric: ${currentCollection.metric.displayName}"
            updateProjection()
            performSearch()
        }

        searchButton.addActionListener {
            performSearch()
        }

        queryField.addActionListener {
            performSearch()
        }

        canvas.onPointSelected = { doc ->
            queryField.text = doc.text.take(60)
            performSearch()
        }
    }

    private fun updateProjection(queryEmbedding: FloatArray? = null, topKIds: Set<String> = emptySet()) {
        val projected = VectorDbEngine.projectTo2D(currentCollection.documents, queryEmbedding)
        canvas.setProjection(projected, topKIds)
    }

    private fun performSearch() {
        val queryText = queryField.text.trim()
        val topK = (topKSpinner.value as? Int) ?: 3
        val queryEmbedding = VectorDbEngine.embedText(queryText, currentCollection.dimension)

        val results = VectorDbEngine.search(currentCollection, queryEmbedding, topK)
        val topKIds = results.map { it.document.id }.toSet()

        updateProjection(queryEmbedding, topKIds)
        displayResults(results)
    }

    private fun displayResults(results: List<VectorQueryResult>) {
        resultsPanel.removeAll()

        val header = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
            isOpaque = false
            add(JBLabel("<b>Top ${results.size} Nearest Neighbors</b> (Similarity Ranked):"))
        }
        resultsPanel.add(header)

        for (res in results) {
            val card = JPanel(BorderLayout(6, 4)).apply {
                background = Color.WHITE
                border = CompoundBorder(
                    LineBorder(Color(220, 220, 220), 1),
                    EmptyBorder(8, 10, 8, 10)
                )
                maximumSize = Dimension(Short.MAX_VALUE.toInt(), 120)
            }

            val cardTop = JPanel(BorderLayout()).apply { isOpaque = false }
            val rankTitle = JBLabel("<b>#${res.rank} [${res.document.id}]</b>")
            
            val scorePct = (res.score * 100.0).coerceIn(0.0, 100.0)
            val scoreBadge = JBLabel(String.format("%.1f%% MATCH", scorePct)).apply {
                font = Font("Segoe UI", Font.BOLD, 10)
                foreground = Color(133, 153, 0)
                border = CompoundBorder(LineBorder(Color(133, 153, 0), 1), EmptyBorder(2, 6, 2, 6))
            }
            cardTop.add(rankTitle, BorderLayout.WEST)
            cardTop.add(scoreBadge, BorderLayout.EAST)

            val textLabel = JBTextArea(res.document.text).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                font = Font("Segoe UI", Font.PLAIN, 12)
                background = Color.WHITE
            }

            val metaPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply { isOpaque = false }
            for ((k, v) in res.document.metadata) {
                metaPanel.add(JBLabel("[$k: $v]").apply {
                    font = Font("Segoe UI", Font.PLAIN, 10)
                    foreground = Color(100, 100, 100)
                })
            }

            card.add(cardTop, BorderLayout.NORTH)
            card.add(textLabel, BorderLayout.CENTER)
            card.add(metaPanel, BorderLayout.SOUTH)

            resultsPanel.add(card)
            resultsPanel.add(Box.createVerticalStrut(6))
        }

        resultsPanel.revalidate()
        resultsPanel.repaint()
    }

    private fun simulateChunking() {
        chunksContainer.removeAll()
        val text = chunkInputArea.text
        val strategy = ChunkingStrategy.values()[chunkStrategyCombo.selectedIndex.coerceIn(0, 2)]
        val chunkSize = (chunkSizeSpinner.value as? Int) ?: 200
        val chunkOverlap = (chunkOverlapSpinner.value as? Int) ?: 40

        val chunks = VectorDbEngine.splitDocument(text, strategy, chunkSize, chunkOverlap)

        for (c in chunks) {
            val card = JPanel(BorderLayout(4, 2)).apply {
                background = Color.WHITE
                border = CompoundBorder(
                    LineBorder(Color(215, 215, 215), 1),
                    EmptyBorder(6, 8, 6, 8)
                )
                maximumSize = Dimension(Short.MAX_VALUE.toInt(), 90)
            }

            val titlePanel = JPanel(BorderLayout()).apply { isOpaque = false }
            titlePanel.add(JBLabel("<b>Chunk #${c.index}</b>"), BorderLayout.WEST)
            titlePanel.add(JBLabel("${c.characterCount} chars | ~${c.tokenCount} tokens").apply {
                font = Font("Segoe UI", Font.PLAIN, 10)
                foreground = Color(100, 100, 100)
            }, BorderLayout.EAST)

            val body = JBTextArea(c.text).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                font = Font("Segoe UI", Font.PLAIN, 11)
                background = Color.WHITE
            }

            card.add(titlePanel, BorderLayout.NORTH)
            card.add(body, BorderLayout.CENTER)

            chunksContainer.add(card)
            chunksContainer.add(Box.createVerticalStrut(4))
        }

        chunksContainer.revalidate()
        chunksContainer.repaint()
    }

    private fun exportPythonCode() {
        val code = """
# ==============================================================================
# Jörmungandr Vector DB & RAG Pipeline Integration (LanceDB + LangChain)
# ==============================================================================
import lancedb
from langchain_community.vectorstores import LanceDB
from langchain_community.embeddings import HuggingFaceEmbeddings

# 1. Connect to LanceDB (embedded, zero-server)
db = lancedb.connect("./data/lancedb_lakehouse")
embeddings = HuggingFaceEmbeddings(model_name="sentence-transformers/all-MiniLM-L6-v2")

# 2. Open or create vector table
table = db.open_table("${currentCollection.name}")

# 3. Perform semantic similarity search
query = "${queryField.text.trim()}"
results = table.search(embeddings.embed_query(query)).limit(${topKSpinner.value}).to_pandas()

print(f"Top matches for: {query}")
for idx, row in results.iterrows():
    print(f"[{row['_distance']:.4f}] {row['text']}")
""".trimIndent()

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(code), null)
        Messages.showInfoMessage(
            "Python LanceDB & LangChain integration snippet has been copied to your clipboard!",
            "RAG Code Exported"
        )
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
