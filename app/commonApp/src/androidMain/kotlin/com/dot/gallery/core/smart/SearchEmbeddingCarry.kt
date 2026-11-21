/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.dao.CloudLocalCopyLink
import kotlinx.coroutines.CancellationException

/**
 * When a backed-up local file is deleted, its `media` row disappears while the
 * cloud copy stays. Orphan cleanup then removes the `image_embeddings` row
 * keyed by the local MediaStore id, so the surviving cloud item loses its
 * search vector. [carrySearchEmbeddingsToCloudCopies] copies those vectors onto
 * the linked cloud rows first — call it right before every `deleteOrphans`.
 */
suspend fun carrySearchEmbeddingsToCloudCopies(database: InternalDatabase): Int {
    return try {
        val embeddingDao = database.getImageEmbeddingDao()
        val missingLocalIds = embeddingDao.getMissingLocalIds()
        if (missingLocalIds.isEmpty()) return 0
        val links = database.getCloudMediaDao().getLocalCopyLinks() +
            database.getCloudMediaDao().getBackupRevisionLinks()
        val plan = searchEmbeddingCarryPlan(
            missingLocalIds = missingLocalIds,
            links = links,
            embeddedIds = embeddingDao.getIds().toHashSet()
        )
        if (plan.isEmpty()) return 0
        var carried = 0
        for ((localId, targets) in plan) {
            val record = embeddingDao.getRecord(localId) ?: continue
            // Empty vectors are remote-handover markers, not real embeddings.
            if (record.embedding.isEmpty()) continue
            for (target in targets) {
                embeddingDao.addImageEmbedding(
                    record.copy(
                        id = target.cloudMediaId,
                        date = target.cloudTimestampMillis / 1000L
                    )
                )
                carried++
            }
        }
        if (carried > 0) {
            printInfo("ml.search", "carried $carried search embedding(s) to cloud copies")
        }
        carried
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        printWarn(
            "ml.search",
            "search embedding carry to cloud copies failed",
            ctx = mapOf("error" to e.toString())
        )
        0
    }
}

internal fun mediaStoreIdOf(uri: String): Long? {
    if (!uri.startsWith("content://media/")) return null
    // Backup revisions store "$uri\u001f$remoteId" — keep only the MediaStore part.
    val id = uri.substringBefore('\u001f').substringBefore('?').substringAfterLast('/')
    return id.toLongOrNull()?.takeIf { it >= 0 }
}

internal fun searchEmbeddingCarryPlan(
    missingLocalIds: Collection<Long>,
    links: List<CloudLocalCopyLink>,
    embeddedIds: Set<Long>
): Map<Long, List<CloudLocalCopyLink>> {
    val missing = missingLocalIds.toHashSet()
    return links.mapNotNull { link ->
        mediaStoreIdOf(link.localUri)?.takeIf { it in missing }?.let { it to link }
    }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, targets) ->
            targets
                .distinctBy { it.cloudMediaId }
                .filterNot { it.cloudMediaId in embeddedIds }
        }
        .filterValues { it.isNotEmpty() }
}
