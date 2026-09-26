/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import android.content.Context
import android.graphics.Bitmap
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.dot.gallery.BuildConfig
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.dao.DetectedFaceDao
import com.dot.gallery.cloud.data.dao.PersonDao
import com.dot.gallery.cloud.data.entity.DetectedFaceEntity
import com.dot.gallery.cloud.data.entity.FaceClusterEntity
import com.dot.gallery.cloud.data.entity.FaceLinkKind
import com.dot.gallery.cloud.data.entity.PersonEntity
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.data.data_source.SmartScanPhase
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import com.dot.gallery.feature_node.domain.util.FloatVectorCodec
import com.dot.gallery.feature_node.presentation.util.printError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Batch re-grouping phase, run after [SmartScanPhase.FACE_INDEX] in the same
 * execution branch. Delegates all grouping decisions to [FaceClusterer] over every
 * stored embedding, then applies the result in one transaction: assignments are
 * rewritten, retired persons are deleted, fresh persons are created with cover
 * thumbnails, `face_clusters` is regenerated as a pure derived cache, and
 * `people.faceCount` is refreshed.
 *
 * Because it recomputes from scratch every scan, new photos picked up by
 * FACE_INDEX simply join the next grouping — drift and split clusters are
 * cleaned up deterministically instead of patched one pair at a time.
 */
class FaceClusterPhaseProcessor @Inject constructor(
    repository: MediaRepository,
    database: InternalDatabase,
    private val modelManager: ModelManager,
    private val faceDao: DetectedFaceDao,
    private val personDao: PersonDao,
    private val thumbnailLoader: SmartThumbnailLoader,
    @ApplicationContext private val appContext: Context
) : MediaPhaseProcessor(repository, database) {
    override val phase = SmartScanPhase.FACE_CLUSTER
    override val revision: String
        get() = "cluster-v2:${modelManager.processorRevision(ModelGroup.FACE_DETECT)}:" +
            modelManager.processorRevision(ModelGroup.FACE_RECOGNITION)

    override suspend fun process(context: SmartScanPhaseContext): SmartScanPhaseResult {
        if (!BuildConfig.ENABLE_INDEXING) return SmartScanPhaseResult.Blocked("indexing_disabled")
        if (!modelManager.isReady(ModelGroup.FACE_DETECT) ||
            !modelManager.isReady(ModelGroup.FACE_RECOGNITION)
        ) return SmartScanPhaseResult.Blocked("face_model_unavailable")

        faceDao.deleteOrphans()
        faceDao.deleteOrphanExclusions()
        faceDao.deleteOrphanLinks()

        val allFaces = faceDao.getAll()
        val embedded = allFaces.mapNotNull { face ->
            val embedding = face.embedding
                ?.let {
                    runCatching { FloatVectorCodec.decode(it) }
                        .onFailure { e ->
                            printError("ml.faces", "face embedding decode failed; skipping item", e)
                        }
                        .getOrNull()
                }
                ?.takeIf { it.size == FACE_EMBEDDING_DIMENSION && it.all(Float::isFinite) }
            if (embedding == null || face.left >= face.right || face.top >= face.bottom) null
            else face to embedding
        }
        val persons = personDao.getByProviderOnce(ProviderType.LOCAL_PEOPLE)

        val inputs = embedded.map { (face, embedding) ->
            FaceClusterer.Face(
                id = face.id,
                mediaId = face.mediaId,
                embedding = embedding,
                left = face.left,
                top = face.top,
                right = face.right,
                bottom = face.bottom,
                confidence = face.confidence,
                personId = face.personId
            )
        }
        val seeds = persons.map {
            FaceClusterer.PersonSeed(
                id = it.id,
                curated = it.name.isNotBlank() || it.hidden,
                faceCount = it.faceCount
            )
        }
        val assertions = faceDao.getLinks().map {
            FaceClusterer.FaceAssertion(
                mediaId = it.mediaId,
                left = it.left,
                top = it.top,
                right = it.right,
                bottom = it.bottom,
                personId = it.personId,
                include = it.kind == FaceLinkKind.INCLUDE
            )
        }
        val mediaAssertions = faceDao.getExclusions().map {
            FaceClusterer.MediaAssertion(mediaId = it.mediaId, personId = it.personId)
        }

        val total = inputs.size.coerceAtLeast(1)
        progress(context, total, 0, 0, 0, 0)
        val result = FaceClusterer.cluster(inputs, seeds, assertions, mediaAssertions) { done, _ ->
            progress(context, total, done.coerceIn(0, total), done.coerceIn(0, total), 0, 0)
        }

        apply(result, allFaces, persons)
        val summary = progress(context, total, total, total, 0, 0)
        return SmartScanPhaseResult.Completed(summary)
    }

    private suspend fun apply(
        result: FaceClusterer.Result,
        allFaces: List<DetectedFaceEntity>,
        persons: List<PersonEntity>
    ) {
        val faceById = allFaces.associateBy { it.id }
        val personById = persons.associateBy { it.id }
        val now = System.currentTimeMillis()

        val assignments = LinkedHashMap<String, List<Long>>()
        val centroids = LinkedHashMap<String, FloatArray>()
        result.resolved.forEach { (personId, component) ->
            assignments[personId] = component.faceIds
            centroids[personId] = component.centroid
        }
        val freshIds = result.fresh.map { "local_${UUID.randomUUID()}" }
        result.fresh.forEachIndexed { index, component ->
            assignments[freshIds[index]] = component.faceIds
            centroids[freshIds[index]] = component.centroid
        }

        fun bestFaceOf(component: FaceClusterer.Component): DetectedFaceEntity? =
            component.faceIds
                .mapNotNull(faceById::get)
                .maxByOrNull { (it.right - it.left) * (it.bottom - it.top) * it.confidence }

        // Covers: every fresh person, plus resolved persons whose stored cover
        // file vanished. Media is loaded lazily — only when a cover is needed.
        val coverTargets = ArrayList<Pair<String, DetectedFaceEntity>>()
        result.fresh.forEachIndexed { index, component ->
            bestFaceOf(component)?.let { coverTargets += freshIds[index] to it }
        }
        result.resolved.forEach { (personId, component) ->
            val file = personById[personId]?.thumbnailUrl?.toUri()?.path?.let(::File)
            if (file == null || !file.exists()) {
                bestFaceOf(component)?.let { coverTargets += personId to it }
            }
        }
        val mediaById = if (coverTargets.isNotEmpty()) {
            media().associateBy { it.id }
        } else {
            emptyMap()
        }
        val coverUrls = coverTargets.mapNotNull { (personId, face) ->
            saveFaceThumbnail(personId, face, mediaById[face.mediaId])?.let { personId to it }
        }.toMap()

        val newPersons = result.fresh.mapIndexed { index, component ->
            val personId = freshIds[index]
            val coverFace = coverTargets.firstOrNull { it.first == personId }?.second
            PersonEntity(
                id = personId,
                name = "",
                providerType = ProviderType.LOCAL_PEOPLE,
                thumbnailMediaId = coverFace?.mediaId,
                thumbnailUrl = coverUrls[personId],
                faceCount = component.faceIds.size,
                lastUpdated = now
            )
        }
        val regeneratedCovers = coverUrls.filterKeys { it in personById }

        val retired = persons.map { it.id } - assignments.keys
        database.withTransaction {
            if (newPersons.isNotEmpty()) personDao.insertAll(newPersons)
            regeneratedCovers.forEach { (personId, url) ->
                val face = coverTargets.first { it.first == personId }.second
                personDao.updateThumbnail(personId, face.mediaId, url)
            }
            assignments.forEach { (personId, faceIds) ->
                faceIds.chunked(BATCH_SIZE).forEach { faceDao.assignFaces(it, personId) }
            }
            result.unassignedFaceIds.chunked(BATCH_SIZE).forEach { faceDao.assignFaces(it, null) }
            retired.forEach { personDao.deleteById(it) }
            faceDao.replaceClusters(
                centroids.map { (personId, centroid) ->
                    FaceClusterEntity(
                        personId = personId,
                        centroid = centroid,
                        faceCount = assignments.getValue(personId).size,
                        updatedAt = now
                    )
                }
            )
            assignments.keys.forEach { personId ->
                personDao.updateFaceCount(personId, faceDao.countForPerson(personId), now)
            }
        }

        // Retired persons' cover files are deleted outside the transaction;
        // their face_links rows are already gone via the FK cascade.
        retired.forEach { personId ->
            personById[personId]?.thumbnailUrl?.let { value ->
                runCatching { File(requireNotNull(value.toUri().path)).delete() }
            }
        }
    }

    private suspend fun saveFaceThumbnail(
        personId: String,
        face: DetectedFaceEntity,
        media: Media.UriMedia?
    ): String? = runCatching {
        val bitmap = media?.let { thumbnailLoader.load(it, THUMBNAIL_DECODE_SIZE) }
            ?: return@runCatching null
        try {
            // Square crop centered on the face with margin — same framing as the
            // face-strip crops in FaceCropLoader.
            val faceCx = (face.left + face.right) / 2f * bitmap.width
            val faceCy = (face.top + face.bottom) / 2f * bitmap.height
            val faceW = (face.right - face.left) * bitmap.width
            val faceH = (face.bottom - face.top) * bitmap.height
            val side = (maxOf(faceW, faceH) * CROP_MARGIN).toInt()
                .coerceIn(2, minOf(bitmap.width, bitmap.height))
            val left = (faceCx - side / 2f).toInt().coerceIn(0, bitmap.width - side)
            val top = (faceCy - side / 2f).toInt().coerceIn(0, bitmap.height - side)
            val crop = Bitmap.createBitmap(bitmap, left, top, side, side)
            val directory = File(appContext.filesDir, FACE_THUMBNAIL_DIRECTORY).apply { mkdirs() }
            val file = File(directory, "$personId.jpg")
            file.outputStream().use { crop.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (crop != bitmap) crop.recycle()
            file.toUri().toString()
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    companion object {
        const val FACE_THUMBNAIL_DIRECTORY = "face_thumbs"
        private const val THUMBNAIL_DECODE_SIZE = 640
        private const val CROP_MARGIN = 1.45f
        private const val BATCH_SIZE = 500
        private const val FACE_EMBEDDING_DIMENSION = 512
    }
}
