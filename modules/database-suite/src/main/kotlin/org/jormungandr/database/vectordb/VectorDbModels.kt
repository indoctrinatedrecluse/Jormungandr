package org.jormungandr.database.vectordb

/**
 * Supported distance metrics for vector similarity calculations.
 */
enum class DistanceMetric(val displayName: String) {
    COSINE("Cosine Similarity"),
    EUCLIDEAN_L2("Euclidean (L2) Distance"),
    DOT_PRODUCT("Dot Product")
}

/**
 * Supported chunking algorithms for document preprocessing.
 */
enum class ChunkingStrategy(val displayName: String) {
    RECURSIVE_CHARACTER("Recursive Character Splitter"),
    TOKEN_BASED("Token-Based Splitter"),
    MARKDOWN_HEADER("Markdown Header Splitter")
}

/**
 * A document chunk with its text content, token estimation, and vector embedding.
 */
data class VectorDocument(
    val id: String,
    val text: String,
    val metadata: Map<String, String> = emptyMap(),
    val embedding: FloatArray = FloatArray(0),
    val clusterId: Int = 0
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VectorDocument) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * Metadata and documents belonging to a vector collection.
 */
data class VectorCollection(
    val name: String,
    val dimension: Int,
    val metric: DistanceMetric,
    val documents: List<VectorDocument>,
    val description: String = ""
)

/**
 * Result of a nearest-neighbor similarity search query.
 */
data class VectorQueryResult(
    val document: VectorDocument,
    val score: Double, // Cosine similarity: [0.0 .. 1.0], or Distance
    val rank: Int
)

/**
 * 2D projected coordinates for dimensionality reduction visualization.
 */
data class ProjectedPoint2D(
    val document: VectorDocument,
    val x: Double,
    val y: Double,
    val clusterId: Int,
    val isQuery: Boolean = false
)

/**
 * Result of a document splitting / chunking simulation.
 */
data class ChunkResult(
    val index: Int,
    val text: String,
    val tokenCount: Int,
    val characterCount: Int
)
