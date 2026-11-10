/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ocr.repository

import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.core.logging.printWarn
import com.dot.gallery.core.ocr.domain.OcrDocument
import com.dot.gallery.core.ocr.text.OcrTextNormalizer
import com.dot.gallery.data.dao.OcrResultDao
import com.dot.gallery.data.entity.OcrResultEntity
import com.dot.gallery.data.entity.OcrSearchEntity
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.domain.util.isEncrypted
import kotlinx.serialization.json.Json

/**
 * The text index of library photos. Vault media can never be stored: [persist] refuses it, so no code
 * path leads from a vault photo to the database.
 */
class OcrRepository(private val dao: OcrResultDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** The stored text of [media] when it is for this file version and these packs, else null. */
    suspend fun current(media: Media, resultRevision: String): OcrDocument? {
        if (media.isEncrypted) return null
        val row = dao.get(media.id) ?: return null
        if (row.sourceRevision != sourceRevision(media) || row.resultRevision != resultRevision) return null
        return decode(row)
    }

    /** Whether [mediaId] has a row for [sourceRevision] and [resultRevision] (the indexer's output check). */
    suspend fun isCurrent(mediaId: Long, sourceRevision: String, resultRevision: String): Boolean =
        dao.get(mediaId)?.let { it.sourceRevision == sourceRevision && it.resultRevision == resultRevision } == true

    suspend fun persist(
        media: Media,
        resultRevision: String,
        packIds: Collection<String>,
        document: OcrDocument,
    ) {
        require(!media.isEncrypted) { "Vault media is never indexed" }
        val cloud = CloudUri.parse(media.getUri().toString())
        val normalized = OcrTextNormalizer.normalizeForSearch(document.fullText)
        dao.upsertResult(
            OcrResultEntity(
                mediaId = media.id,
                sourceType = if (cloud != null) OcrResultEntity.SOURCE_CLOUD else OcrResultEntity.SOURCE_LOCAL,
                providerType = cloud?.providerType?.name,
                serverConfigId = cloud?.configId,
                remoteId = cloud?.remoteId,
                sourceRevision = sourceRevision(media),
                resultRevision = resultRevision,
                packIds = packIds.sorted().joinToString(","),
                fullText = document.fullText,
                normalizedText = normalized,
                documentJson = json.encodeToString(OcrDocument.serializer(), document),
                averageConfidence = document.lines.map { it.confidence }.average().takeIf { it.isFinite() }?.toFloat() ?: 0f,
                updatedAt = System.currentTimeMillis(),
            )
        )
        if (normalized.isBlank()) {
            dao.deleteSearch(media.id)
        } else {
            dao.upsertSearch(OcrSearchEntity(media.id, normalized, OcrTextNormalizer.indexTokens(normalized).joinToString(" ")))
        }
    }

    /** Media ids whose text matches [query]: FTS prefix match, then a substring fallback for CJK and Thai. */
    suspend fun search(query: String, limit: Int = 500): List<Long> {
        val normalized = OcrTextNormalizer.sanitizeQuery(query)
        if (normalized.isEmpty()) return emptyList()
        val expression = OcrTextNormalizer.quotedPrefixExpression(normalized)
        val fts = if (expression.isEmpty()) emptyList() else runCatching { dao.matchIds(expression, limit) }
            .onFailure { printWarn(TAG, "Text index match failed; using the substring fallback", mapOf("error" to it.toString())) }
            .getOrDefault(emptyList())
        return (fts + dao.containingIds(normalized, limit)).distinct().take(limit)
    }

    /** The stored documents of [ids], for drawing matches; vault media is never among them. */
    suspend fun documents(ids: List<Long>): Map<Long, OcrDocument> =
        ids.chunked(SQL_BATCH).flatMap { dao.results(it) }.mapNotNull { row -> decode(row)?.let { row.mediaId to it } }.toMap()

    /** For each of [ids] with stored text, where [query] appears: the best line and its matching words. */
    suspend fun matchRegions(query: String, ids: List<Long>): Map<Long, OcrMatchRegion> {
        val tokens = OcrTextNormalizer.normalizeForSearch(query).split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) return emptyMap()
        return documents(ids).mapNotNull { (id, document) -> matchRegion(document, tokens)?.let { id to it } }.toMap()
    }

    suspend fun delete(mediaIds: Collection<Long>) {
        mediaIds.forEach { id ->
            dao.deleteSearch(id)
            dao.deleteResult(id)
        }
    }

    suspend fun deleteAll() {
        dao.deleteAllSearch()
        dao.deleteAllResults()
    }

    fun observeCount() = dao.observeCount()

    fun observeBytes() = dao.observeBytes()

    private fun decode(row: OcrResultEntity): OcrDocument? =
        runCatching { json.decodeFromString(OcrDocument.serializer(), row.documentJson) }
            .onFailure { printWarn(TAG, "Stored text could not be read", mapOf("media" to row.mediaId.toString(), "error" to it.toString())) }
            .getOrNull()

    companion object {
        private const val TAG = "ml.ocr"
        private const val SQL_BATCH = 500

        /** The line of [document] containing most of [tokens] (normalized), with the words that match. */
        fun matchRegion(document: OcrDocument, tokens: List<String>): OcrMatchRegion? {
            val best = document.lines
                .map { line -> line to OcrTextNormalizer.normalizeForSearch(line.text).let { text -> tokens.count { it in text } } }
                .filter { it.second > 0 }
                .maxByOrNull { it.second }
                ?.first ?: return null
            val words = best.words.filter { word ->
                val text = OcrTextNormalizer.normalizeForSearch(word.text)
                text.isNotEmpty() && tokens.any { token -> token in text || text in token }
            }.map { it.quad }
            return OcrMatchRegion(best.quad, words, document.sourceWidth.toFloat() / document.sourceHeight)
        }

        /** The file version a stored result belongs to: changes whenever the photo is edited or replaced. */
        fun sourceRevision(media: Media): String = "${media.timestamp}:${media.size}:${media.mimeType}:${media.path}"
    }
}
