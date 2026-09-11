package com.orchords.orchordsai.data.retrieval

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SemanticRetrievalContractTest {
    private val projectA = SemanticNamespace(SemanticNamespaceKind.PROJECT, "project-a")
    private val projectB = SemanticNamespace(SemanticNamespaceKind.PROJECT, "project-b")
    private val spec = EmbeddingSpec("local", "tiny", 3)
    private val chunk = SemanticChunk(
        ref = SemanticChunkRef(
            namespace = projectA,
            sourceId = "source",
            chunkId = "chunk",
            sourceRevision = "r1",
            ordinal = 0,
        ),
        text = "hello",
        contentHash = "hash",
    )

    @Test
    fun `validates bounded embedding batches and exact dimensions`() {
        validateEmbeddingBatch(
            chunks = listOf(chunk),
            embeddings = listOf(floatArrayOf(1f, 2f, 3f)),
            spec = spec,
        )

        assertThrows(IllegalArgumentException::class.java) {
            validateEmbeddingBatch(
                chunks = listOf(chunk),
                embeddings = listOf(floatArrayOf(1f, 2f)),
                spec = spec,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateEmbeddingBatch(
                chunks = listOf(chunk),
                embeddings = listOf(floatArrayOf(1f, Float.NaN, 3f)),
                spec = spec,
            )
        }
    }

    @Test
    fun `retrieval query enforces hard bounds and finite vectors`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetrievalQuery(projectA, floatArrayOf(Float.NaN), topK = 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            RetrievalQuery(projectA, floatArrayOf(1f), topK = MAX_TOP_K + 1)
        }
    }

    @Test
    fun `backend results cannot cross namespace or exceed requested count`() {
        val query = RetrievalQuery(projectA, floatArrayOf(1f, 2f, 3f), topK = 2)
        assertEquals(
            1,
            validateRetrievalResults(query, listOf(RetrievalResult(chunk.ref, 0.9f))).size,
        )

        assertThrows(IllegalArgumentException::class.java) {
            validateRetrievalResults(
                query,
                listOf(RetrievalResult(chunk.ref.copy(namespace = projectB), 0.8f)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateRetrievalResults(
                query,
                List(3) { RetrievalResult(chunk.ref, 0.7f) },
            )
        }
    }
}
