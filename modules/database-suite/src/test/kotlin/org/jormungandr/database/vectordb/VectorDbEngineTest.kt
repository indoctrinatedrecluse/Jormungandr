package org.jormungandr.database.vectordb

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VectorDbEngineTest {

    @Test
    fun `test cosineSimilarity calculates exact normalized dot product`() {
        val v1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val v2 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val simIdentical = VectorDbEngine.cosineSimilarity(v1, v2)
        assertEquals(1.0, simIdentical, 1e-4, "Identical vectors must have cosine similarity 1.0")

        val vOrthogonal = floatArrayOf(0.0f, 1.0f, 0.0f)
        val simOrtho = VectorDbEngine.cosineSimilarity(v1, vOrthogonal)
        assertEquals(0.0, simOrtho, 1e-4, "Orthogonal vectors must have cosine similarity 0.0")

        val vOpposite = floatArrayOf(-1.0f, 0.0f, 0.0f)
        val simOpposite = VectorDbEngine.cosineSimilarity(v1, vOpposite)
        assertEquals(-1.0, simOpposite, 1e-4, "Opposite vectors must have cosine similarity -1.0")
    }

    @Test
    fun `test euclideanDistance calculates geometric distance correctly`() {
        val v1 = floatArrayOf(0.0f, 0.0f)
        val v2 = floatArrayOf(3.0f, 4.0f)
        val dist = VectorDbEngine.euclideanDistance(v1, v2)
        assertEquals(5.0, dist, 1e-4, "3-4-5 triangle distance must equal 5.0")
    }

    @Test
    fun `test search returns top-k ranked nearest neighbors`() {
        val collection = VectorDbEngine.SAMPLE_COLLECTIONS.first()
        val queryEmbedding = VectorDbEngine.embedText("Transformer self-attention language models", collection.dimension)

        val results = VectorDbEngine.search(collection, queryEmbedding, topK = 3)
        assertEquals(3, results.size, "Must return exactly top-3 matches")
        assertTrue(results[0].score >= results[1].score, "Results must be sorted descending by cosine similarity")
        assertEquals(1, results[0].rank)
        assertEquals(2, results[1].rank)
        assertEquals(3, results[2].rank)
    }

    @Test
    fun `test projectTo2D normalizes coordinates within bounds`() {
        val collection = VectorDbEngine.SAMPLE_COLLECTIONS.first()
        val queryEmbedding = VectorDbEngine.embedText("Computer vision ResNet", collection.dimension)

        val points = VectorDbEngine.projectTo2D(collection.documents, queryEmbedding)
        assertFalse(points.isEmpty())
        assertTrue(points.any { it.isQuery }, "Projected points must include the query point")

        for (pt in points) {
            assertTrue(pt.x in -1.0..1.0, "Projected X coordinate must be normalized")
            assertTrue(pt.y in -1.0..1.0, "Projected Y coordinate must be normalized")
        }
    }

    @Test
    fun `test splitDocument produces chunks across chunking strategies`() {
        val text = """
# Introduction to Vector Databases
Vector databases index high-dimensional embeddings.
They utilize approximate nearest neighbor algorithms like HNSW and IVF-PQ.
This allows sub-millisecond retrieval across millions of items.

# Implementation Details
LanceDB uses Apache Arrow and Parquet under the hood.
ChromaDB utilizes SQLite and ClickHouse for persistence.
        """.trimIndent()

        val recChunks = VectorDbEngine.splitDocument(text, ChunkingStrategy.RECURSIVE_CHARACTER, chunkSize = 100, chunkOverlap = 20)
        assertTrue(recChunks.isNotEmpty(), "Recursive character splitter must produce chunks")
        assertTrue(recChunks.all { it.text.isNotBlank() })

        val mdChunks = VectorDbEngine.splitDocument(text, ChunkingStrategy.MARKDOWN_HEADER)
        assertTrue(mdChunks.size >= 2, "Markdown header splitter must partition by headers")
        assertTrue(mdChunks[0].text.startsWith("# Introduction"))
    }
}
