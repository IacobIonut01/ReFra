/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.handover

import android.util.Base64
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.feature.classifier.ui.search.RemoteSearchProvider
import javax.inject.Inject

/**
 * Routes interactive search to the Handover server's index via POST /api/v1/query.
 * Every method degrades to null (→ "use the local path") on any failure —
 * remote search is an overlay, never a hard dependency.
 */
@Singleton
class HandoverSearchProvider @Inject constructor(
    private val client: HandoverClient,
) : RemoteSearchProvider {

    override suspend fun search(query: String, k: Int): List<Pair<String, Float>>? =
        run(QueryRequest(text = query, k = k))

    override suspend fun similarTo(itemId: String, k: Int): List<Pair<String, Float>>? =
        run(QueryRequest(itemId = itemId, k = k))

    override suspend fun searchVector(embedding: FloatArray, k: Int): List<Pair<String, Float>>? =
        run(QueryRequest(embedding = embedding.encodeWireVector(), k = k))

    override suspend fun embedText(text: String): FloatArray? {
        val config = client.config()?.takeIf { it.preferRemoteQueries } ?: return null
        return try {
            client.embedText(config, text, config.engine.ifBlank { null })
                .embedding.decodeWireVector()
        } catch (e: HandoverException) {
            printWarn("handover.search", "remote embedText failed: ${e.code} — local path used")
            null
        }
    }

    private suspend fun run(request: QueryRequest): List<Pair<String, Float>>? {
        val config = client.config()?.takeIf { it.preferRemoteQueries } ?: return null
        return try {
            client.query(config, request.copy(engine = config.engine.ifBlank { null }))
                .items.map { it.id to it.score }
        } catch (e: HandoverException) {
            printWarn("handover.search", "remote query failed: ${e.code} — local path used")
            null
        }
    }

    private fun FloatArray.encodeWireVector(): String =
        Base64.encodeToString(HandoverEnvelope.packFloat32(this), Base64.NO_WRAP)
}
