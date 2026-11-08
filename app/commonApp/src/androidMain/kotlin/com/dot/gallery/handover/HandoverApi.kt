/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Wire DTOs for the ReFra Handover HTTP API (docs/protocol.md).
 * Field names match the server's camelCase JSON exactly.
 */
@Serializable
data class HandoverInfo(
    val server: String = "",
    val version: String = "",
    val protocol: Int = 0,
    val principal: String = "",
    val capabilities: Capabilities = Capabilities(),
    val engines: List<EngineInfo> = emptyList(),
    val limits: Limits = Limits(),
    val jobsActive: Int = 0,
    val itemsPending: Int = 0,
) {
    @Serializable
    data class Capabilities(
        val searchProvider: Boolean = false,
        val embeddingItems: Boolean = false,
        val dashboard: Boolean = false,
    )

    @Serializable
    data class Limits(
        val maxBatchBytes: Int = 64 * 1024 * 1024,
        val maxBatchItems: Int = 64,
        val maxItemBytes: Int = 16 * 1024 * 1024,
        val maxJobsActive: Int = 4,
        val resultTtlSeconds: Int = 1800,
    )
}

@Serializable
data class EngineInfo(
    val id: String = "",
    val displayName: String = "",
    val engine: String = "",
    val appEngine: String = "",
    val modalities: List<String> = emptyList(),
    val embeddingDim: Int = 0,
    val indexRevisionTag: String = "",
    val modelRevision: String = "",
    val filesInstalled: Boolean = false,
    val loaded: Boolean = false,
)

@Serializable
data class SubmitResponse(
    val jobId: String,
    val accepted: Int = 0,
    val rejected: List<RejectedItem> = emptyList(),
) {
    @Serializable
    data class RejectedItem(val id: String, val error: String)
}

@Serializable
data class JobStatus(
    val jobId: String,
    val principal: String = "",
    val engineId: String = "",
    val sink: String = "",
    val state: String = "queued", // queued | running | done | failed | cancelled
    val total: Int = 0,
    val done: Int = 0,
    val failed: Int = 0,
    val indexed: Int = 0,
    val etaSeconds: Int? = null,
    val error: String? = null,
) {
    val isTerminal: Boolean get() = state in TERMINAL_STATES

    companion object {
        val TERMINAL_STATES = setOf("done", "failed", "cancelled")
    }
}

@Serializable
data class ResultsPage(
    val items: List<ResultItem> = emptyList(),
    val nextCursor: Int? = null,
) {
    @Serializable
    data class ResultItem(
        val id: String,
        val status: String, // "ok" | "indexed" | "error"
        /** base64 little-endian fp32 vector (sink=client|both only). */
        val embedding: String? = null,
        val dim: Int = 0,
        val engineId: String? = null,
        val modelRevision: String? = null,
        val error: String? = null,
    )
}

@Serializable
data class QueryRequest(
    val text: String? = null,
    /** base64 little-endian fp32 vector. */
    val embedding: String? = null,
    val itemId: String? = null,
    val engine: String? = null,
    val k: Int = 40,
    val filter: QueryFilter? = null,
)

@Serializable
data class QueryFilter(
    val kind: String? = null, // image | video
    val mimePrefix: String? = null,
    val after: Long? = null,
    val before: Long? = null,
)

@Serializable
data class QueryResponse(
    val items: List<QueryHit> = emptyList(),
    val engineId: String = "",
    val modelRevision: String = "",
    val indexRevision: String = "",
    val indexedItems: Int = 0,
) {
    @Serializable
    data class QueryHit(val id: String, val score: Float)
}

@Serializable
data class EmbedResponse(
    /** base64 little-endian fp32 vector. */
    val embedding: String,
    val dim: Int,
    val engineId: String = "",
    val modelRevision: String = "",
)

@Serializable
data class IndexStatsResponse(
    val index: Map<String, JsonObject> = emptyMap(),
)

@Serializable
data class HandoverErrorBody(
    val code: String = "error",
    val message: String = "",
    @SerialName("jobId") val jobId: String? = null,
)

/** Decodes a base64 little-endian fp32 vector into a [FloatArray]. */
fun String.decodeWireVector(expectedDim: Int = 0): FloatArray {
    val bytes = java.util.Base64.getDecoder().decode(this)
    require(bytes.size % 4 == 0) { "vector payload not fp32 aligned" }
    if (expectedDim > 0) require(bytes.size == expectedDim * 4) {
        "vector dim mismatch: ${bytes.size / 4} != $expectedDim"
    }
    return FloatArray(bytes.size / 4) { i ->
        java.lang.Float.intBitsToFloat(
            (bytes[i * 4].toInt() and 0xFF) or
                ((bytes[i * 4 + 1].toInt() and 0xFF) shl 8) or
                ((bytes[i * 4 + 2].toInt() and 0xFF) shl 16) or
                ((bytes[i * 4 + 3].toInt() and 0xFF) shl 24)
        )
    }
}
