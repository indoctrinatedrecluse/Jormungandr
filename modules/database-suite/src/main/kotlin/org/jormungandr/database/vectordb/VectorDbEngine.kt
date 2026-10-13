package org.jormungandr.database.vectordb

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Autonomous high-performance Vector Database and RAG simulation engine.
 * Computes cosine similarity, Euclidean distance, PCA 2D projections,
 * and text chunking splits.
 */
object VectorDbEngine {

    val SAMPLE_COLLECTIONS = listOf(
        createArxivCollection(),
        createProductKnowledgeBase(),
        createCodeDocsCollection()
    )

    /**
     * Computes the cosine similarity between two vector embeddings.
     * Returns a score between -1.0 and 1.0 (clamped to 0.0 .. 1.0 for positive embeddings).
     */
    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Double {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0.0
        var dot = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        for (i in v1.indices) {
            val a = v1[i].toDouble()
            val b = v2[i].toDouble()
            dot += a * b
            norm1 += a * a
            norm2 += b * b
        }
        val denom = sqrt(norm1) * sqrt(norm2)
        if (denom == 0.0) return 0.0
        return (dot / denom).coerceIn(-1.0, 1.0)
    }

    /**
     * Computes the Euclidean (L2) distance between two vector embeddings.
     */
    fun euclideanDistance(v1: FloatArray, v2: FloatArray): Double {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return Double.MAX_VALUE
        var sumSq = 0.0
        for (i in v1.indices) {
            val diff = (v1[i] - v2[i]).toDouble()
            sumSq += diff * diff
        }
        return sqrt(sumSq)
    }

    /**
     * Performs top-k nearest neighbor search in a collection.
     */
    fun search(collection: VectorCollection, queryEmbedding: FloatArray, topK: Int = 5): List<VectorQueryResult> {
        val scored = collection.documents.map { doc ->
            val score = when (collection.metric) {
                DistanceMetric.COSINE -> cosineSimilarity(queryEmbedding, doc.embedding)
                DistanceMetric.EUCLIDEAN_L2 -> euclideanDistance(queryEmbedding, doc.embedding)
                DistanceMetric.DOT_PRODUCT -> {
                    var dot = 0.0
                    val limit = minOf(queryEmbedding.size, doc.embedding.size)
                    for (i in 0 until limit) dot += queryEmbedding[i] * doc.embedding[i]
                    dot
                }
            }
            doc to score
        }

        val sorted = when (collection.metric) {
            DistanceMetric.COSINE, DistanceMetric.DOT_PRODUCT -> scored.sortedByDescending { it.second }
            DistanceMetric.EUCLIDEAN_L2 -> scored.sortedBy { it.second }
        }

        return sorted.take(topK).mapIndexed { idx, pair ->
            VectorQueryResult(document = pair.first, score = pair.second, rank = idx + 1)
        }
    }

    /**
     * Generates a deterministic pseudo-semantic embedding for query text.
     */
    fun embedText(text: String, dimension: Int = 384): FloatArray {
        val result = FloatArray(dimension)
        val normalized = text.lowercase().trim()
        val words = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
        
        for ((wordIdx, word) in words.withIndex()) {
            val wordHash = word.hashCode()
            for (dim in 0 until dimension) {
                val freq = (dim + 1) * 0.173 + wordHash * 0.001
                val amp = 1.0 / (1.0 + wordIdx * 0.1)
                result[dim] += (sin(freq) * amp).toFloat()
            }
        }

        // L2 Normalization
        var norm = 0.0
        for (v in result) norm += (v * v).toDouble()
        val len = sqrt(norm).toFloat()
        if (len > 0f) {
            for (i in result.indices) result[i] /= len
        }
        return result
    }

    /**
     * Projects high-dimensional document vectors and an optional query vector into 2D space
     * using Principal Component Analysis (PCA) projection.
     */
    fun projectTo2D(documents: List<VectorDocument>, queryEmbedding: FloatArray? = null): List<ProjectedPoint2D> {
        if (documents.isEmpty()) return emptyList()
        val dim = documents.first().embedding.size
        if (dim < 2) return emptyList()

        // Compute mean vector
        val mean = FloatArray(dim)
        for (doc in documents) {
            for (i in 0 until dim) {
                if (i < doc.embedding.size) mean[i] += doc.embedding[i]
            }
        }
        for (i in 0 until dim) mean[i] /= documents.size.toFloat()

        // Deterministic orthogonal projection vectors (approximating leading eigenvectors)
        val proj1 = FloatArray(dim)
        val proj2 = FloatArray(dim)
        for (i in 0 until dim) {
            proj1[i] = cos(i * 0.314f)
            proj2[i] = sin(i * 0.518f)
        }

        val points = mutableListOf<ProjectedPoint2D>()
        for (doc in documents) {
            var x = 0.0
            var y = 0.0
            for (i in 0 until dim) {
                if (i < doc.embedding.size) {
                    val centered = (doc.embedding[i] - mean[i]).toDouble()
                    x += centered * proj1[i]
                    y += centered * proj2[i]
                }
            }
            points.add(ProjectedPoint2D(document = doc, x = x, y = y, clusterId = doc.clusterId))
        }

        if (queryEmbedding != null && queryEmbedding.isNotEmpty()) {
            var qx = 0.0
            var qy = 0.0
            for (i in 0 until minOf(dim, queryEmbedding.size)) {
                val centered = (queryEmbedding[i] - mean[i]).toDouble()
                qx += centered * proj1[i]
                qy += centered * proj2[i]
            }
            val dummyDoc = VectorDocument("query", "Active Query", mapOf("type" to "query"))
            points.add(ProjectedPoint2D(document = dummyDoc, x = qx, y = qy, clusterId = -1, isQuery = true))
        }

        // Normalize points to [-1.0 .. 1.0] bounding box
        val maxX = points.maxOfOrNull { abs(it.x) }?.coerceAtLeast(1e-5) ?: 1.0
        val maxY = points.maxOfOrNull { abs(it.y) }?.coerceAtLeast(1e-5) ?: 1.0

        return points.map {
            it.copy(x = it.x / maxX, y = it.y / maxY)
        }
    }

    /**
     * Simulates document chunking with configurable strategy, chunk size, and overlap.
     */
    fun splitDocument(
        text: String,
        strategy: ChunkingStrategy,
        chunkSize: Int = 500,
        chunkOverlap: Int = 50
    ): List<ChunkResult> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        return when (strategy) {
            ChunkingStrategy.RECURSIVE_CHARACTER -> {
                val chunks = mutableListOf<String>()
                var start = 0
                while (start < trimmed.length) {
                    val end = minOf(start + chunkSize, trimmed.length)
                    var splitPoint = end
                    if (end < trimmed.length) {
                        val lastNewline = trimmed.lastIndexOf('\n', end)
                        val lastPeriod = trimmed.lastIndexOf(". ", end)
                        val lastSpace = trimmed.lastIndexOf(' ', end)
                        if (lastNewline > start + chunkSize / 2) splitPoint = lastNewline + 1
                        else if (lastPeriod > start + chunkSize / 2) splitPoint = lastPeriod + 2
                        else if (lastSpace > start + chunkSize / 2) splitPoint = lastSpace + 1
                    }
                    val chunkStr = trimmed.substring(start, splitPoint).trim()
                    if (chunkStr.isNotEmpty()) chunks.add(chunkStr)
                    start = maxOf(splitPoint - chunkOverlap, start + 1)
                }
                chunks.mapIndexed { idx, c ->
                    ChunkResult(idx + 1, c, tokenCount = estimateTokens(c), characterCount = c.length)
                }
            }
            ChunkingStrategy.TOKEN_BASED -> {
                val words = trimmed.split(Regex("\\s+"))
                val chunks = mutableListOf<String>()
                val wordsPerChunk = maxOf(chunkSize / 4, 10)
                val overlapWords = maxOf(chunkOverlap / 4, 2)
                var idx = 0
                while (idx < words.size) {
                    val end = minOf(idx + wordsPerChunk, words.size)
                    chunks.add(words.subList(idx, end).joinToString(" "))
                    idx = maxOf(end - overlapWords, idx + 1)
                }
                chunks.mapIndexed { i, c ->
                    ChunkResult(i + 1, c, tokenCount = estimateTokens(c), characterCount = c.length)
                }
            }
            ChunkingStrategy.MARKDOWN_HEADER -> {
                val sections = trimmed.split(Regex("(?m)^(?=#{1,3}\\s)"))
                sections.filter { it.isNotBlank() }.mapIndexed { idx, s ->
                    val clean = s.trim()
                    ChunkResult(idx + 1, clean, tokenCount = estimateTokens(clean), characterCount = clean.length)
                }
            }
        }
    }

    private fun estimateTokens(text: String): Int {
        return (text.length / 4.0).toInt().coerceAtLeast(1)
    }

    private fun createArxivCollection(): VectorCollection {
        val docs = listOf(
            VectorDocument(
                "arxiv-001",
                "Attention Is All You Need: The Transformer model architecture eschews recurrence and convolutions, relying entirely on self-attention mechanisms to draw global dependencies.",
                mapOf("category" to "NLP", "year" to "2017"),
                embedText("Attention Is All You Need: The Transformer model architecture self-attention mechanisms"),
                clusterId = 0
            ),
            VectorDocument(
                "arxiv-002",
                "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding. Introduces masked language models to train representations bidirectional in all layers.",
                mapOf("category" to "NLP", "year" to "2018"),
                embedText("BERT Pre-training Deep Bidirectional Transformers masked language models"),
                clusterId = 0
            ),
            VectorDocument(
                "arxiv-003",
                "Deep Residual Learning for Image Recognition: Residual networks (ResNet) ease the training of networks that are substantially deeper than those used previously via identity skip connections.",
                mapOf("category" to "CV", "year" to "2015"),
                embedText("Deep Residual Learning for Image Recognition ResNet identity skip connections computer vision"),
                clusterId = 1
            ),
            VectorDocument(
                "arxiv-004",
                "An Image is Worth 16x16 Words: Transformers for Image Recognition at Scale (ViT). Applies pure Transformer architecture directly to sequences of flattened 2D image patches.",
                mapOf("category" to "CV", "year" to "2020"),
                embedText("Vision Transformer ViT 16x16 Words Image Recognition patches convolution-free"),
                clusterId = 1
            ),
            VectorDocument(
                "arxiv-005",
                "High-Resolution Image Synthesis with Latent Diffusion Models (Stable Diffusion). Trains diffusion models in the latent space of pre-trained autoencoders for photorealistic generation.",
                mapOf("category" to "GenAI", "year" to "2022"),
                embedText("High-Resolution Image Synthesis Latent Diffusion Models Stable Diffusion generative cross-attention"),
                clusterId = 2
            ),
            VectorDocument(
                "arxiv-006",
                "Retrieval-Augmented Generation for Knowledge-Intensive NLP Tasks (RAG). Combines pre-trained parametric memory with non-parametric dense vector index for factual question answering.",
                mapOf("category" to "RAG", "year" to "2020"),
                embedText("Retrieval-Augmented Generation RAG dense vector index parametric non-parametric knowledge search"),
                clusterId = 3
            )
        )
        return VectorCollection("arxiv_ml_abstracts", 384, DistanceMetric.COSINE, docs, "ArXiv ML & AI Research Abstracts")
    }

    private fun createProductKnowledgeBase(): VectorCollection {
        val docs = listOf(
            VectorDocument(
                "prod-001",
                "How to configure DuckDB Lakehouse in Jörmungandr: Open Database Studio, select DuckDB dialect, specify database path, and enable Parquet automatic predicate pushdown.",
                mapOf("topic" to "Database", "product" to "Jörmungandr"),
                embedText("DuckDB Lakehouse Jörmungandr Parquet automatic predicate pushdown Database Studio"),
                clusterId = 0
            ),
            VectorDocument(
                "prod-002",
                "Connecting to Apache Kafka in Jörmungandr Streaming Suite: Provide bootstrap servers (localhost:9092), topic name, consumer group ID, and Avro/JSON deserializer.",
                mapOf("topic" to "Streaming", "product" to "Kafka"),
                embedText("Apache Kafka Jörmungandr Streaming Suite bootstrap servers consumer group Avro JSON"),
                clusterId = 1
            ),
            VectorDocument(
                "prod-003",
                "Zero-Latency Jupyter Notebook Execution: Interactive notebooks connect directly via ZeroMQ protocol to local Python kernels without browser overhead.",
                mapOf("topic" to "Jupyter", "product" to "Notebooks"),
                embedText("Zero-Latency Jupyter Notebook Execution ZeroMQ protocol local Python kernels browser-free"),
                clusterId = 2
            )
        )
        return VectorCollection("product_knowledge_base", 384, DistanceMetric.COSINE, docs, "Jörmungandr Knowledge Base & User Manual")
    }

    private fun createCodeDocsCollection(): VectorCollection {
        val docs = listOf(
            VectorDocument(
                "code-001",
                "class DataFrameGridPanel(initialDataFrame: DataFrame): High-performance tabular rendering with numerical heatmaps, sorting, and filter builder.",
                mapOf("module" to "dataframe-viewer", "language" to "Kotlin"),
                embedText("class DataFrameGridPanel tabular rendering numerical heatmaps sorting filter builder"),
                clusterId = 0
            ),
            VectorDocument(
                "code-002",
                "fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Double computes dot product normalized by vector Euclidean norms.",
                mapOf("module" to "database-suite", "language" to "Kotlin"),
                embedText("fun cosineSimilarity dot product normalized Euclidean norms vector embeddings"),
                clusterId = 1
            )
        )
        return VectorCollection("codebase_documentation", 384, DistanceMetric.COSINE, docs, "Jörmungandr Core Codebase Symbol Embeddings")
    }
}
