package com.orchords.orchordsai.data.retrieval

enum class SemanticNamespaceKind { PROJECT, MEMORY, CONNECTOR, MANAGED_LIBRARY }

data class SemanticNamespace(
    val kind: SemanticNamespaceKind,
    val ownerId: String,
) {
    init { require(ownerId.isNotBlank()) { "Semantic namespace owner is required" } }
}

data class EmbeddingSpec(
    val providerId: String,
    val modelId: String,
    val dimension: Int,
    val version: String = "",
) {
    init {
        require(providerId.isNotBlank()) { "Embedding provider is required" }
        require(modelId.isNotBlank()) { "Embedding model is required" }
        require(dimension in 1..MAX_EMBEDDING_DIMENSION) { "Embedding dimension is out of bounds" }
    }
}

data class SemanticChunkRef(
    val namespace: SemanticNamespace,
    val sourceId: String,
    val chunkId: String,
    val sourceRevision: String,
    val ordinal: Int,
) {
    init {
        require(sourceId.isNotBlank()) { "Semantic source id is required" }
        require(chunkId.isNotBlank()) { "Semantic chunk id is required" }
        require(sourceRevision.isNotBlank()) { "Semantic source revision is required" }
        require(ordinal >= 0) { "Semantic chunk ordinal must be non-negative" }
    }
}

data class SemanticChunk(
    val ref: SemanticChunkRef,
    val text: String,
    val contentHash: String,
) {
    init {
        require(text.isNotBlank()) { "Semantic chunk text is required" }
        require(text.length <= MAX_CHUNK_CHARS) { "Semantic chunk exceeds character budget" }
        require(contentHash.isNotBlank()) { "Semantic content hash is required" }
    }
}

data class RetrievalQuery(
    val namespace: SemanticNamespace,
    val vector: FloatArray,
    val topK: Int = 8,
    val maxChars: Int = 12_000,
    val minScore: Float? = null,
) {
    init {
        require(topK in 1..MAX_TOP_K) { "Semantic topK is out of bounds" }
        require(maxChars in 1..MAX_RETRIEVAL_CHARS) { "Semantic retrieval character budget is out of bounds" }
        require(vector.isNotEmpty()) { "Semantic query vector is required" }
        require(vector.all { it.isFinite() }) { "Semantic query vector contains non-finite values" }
        require(minScore == null || minScore.isFinite()) { "Semantic minimum score must be finite" }
    }
}

data class RetrievalResult(
    val ref: SemanticChunkRef,
    val score: Float,
) {
    init { require(score.isFinite()) { "Semantic retrieval score must be finite" } }
}

enum class SemanticIndexHealth { READY, REBUILD_REQUIRED, REBUILDING, UNAVAILABLE }

interface EmbeddingProvider {
    val spec: EmbeddingSpec
    suspend fun embed(texts: List<String>): List<FloatArray>
}

interface SemanticIndex {
    suspend fun upsert(chunks: List<SemanticChunk>, embeddings: List<FloatArray>, spec: EmbeddingSpec)
    suspend fun deleteSource(namespace: SemanticNamespace, sourceId: String)
    suspend fun query(query: RetrievalQuery, spec: EmbeddingSpec): List<RetrievalResult>
    suspend fun clearNamespace(namespace: SemanticNamespace)
    suspend fun health(namespace: SemanticNamespace, spec: EmbeddingSpec): SemanticIndexHealth
}

fun validateEmbeddingBatch(
    chunks: List<SemanticChunk>,
    embeddings: List<FloatArray>,
    spec: EmbeddingSpec,
) {
    require(chunks.size == embeddings.size) { "Semantic chunk/vector counts differ" }
    require(chunks.size <= MAX_UPSERT_BATCH) { "Semantic upsert batch is too large" }
    require(chunks.map { it.ref.namespace }.distinct().size <= 1) { "Semantic upsert cannot cross namespaces" }
    embeddings.forEach { vector ->
        require(vector.size == spec.dimension) { "Semantic embedding dimension mismatch" }
        require(vector.all { it.isFinite() }) { "Semantic embedding contains non-finite values" }
    }
}

fun validateRetrievalResults(query: RetrievalQuery, results: List<RetrievalResult>): List<RetrievalResult> {
    require(results.size <= query.topK) { "Semantic backend returned more results than requested" }
    require(results.all { it.ref.namespace == query.namespace }) { "Semantic backend crossed namespace boundary" }
    return results
}

const val MAX_EMBEDDING_DIMENSION = 8_192
const val MAX_CHUNK_CHARS = 32_000
const val MAX_TOP_K = 64
const val MAX_RETRIEVAL_CHARS = 128_000
const val MAX_UPSERT_BATCH = 256
