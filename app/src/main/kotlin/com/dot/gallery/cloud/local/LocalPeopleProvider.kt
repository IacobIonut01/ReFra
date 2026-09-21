/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.local

import com.dot.gallery.cloud.core.LOCAL_PEOPLE_CONFIG_ID
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.capabilities.PeopleCapableProvider
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.dot.gallery.cloud.data.dao.CloudMediaDao
import com.dot.gallery.cloud.data.dao.DetectedFaceDao
import com.dot.gallery.cloud.data.dao.PersonDao
import com.dot.gallery.cloud.data.entity.DetectedFaceEntity
import com.dot.gallery.cloud.data.entity.FaceExclusionEntity
import com.dot.gallery.cloud.data.entity.FaceLinkEntity
import com.dot.gallery.cloud.data.entity.FaceLinkKind
import com.dot.gallery.cloud.data.entity.PersonEntity
import com.dot.gallery.core.Resource
import com.dot.gallery.core.ml.FaceHelper
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.ModelManager
import com.dot.gallery.feature_node.data.data_source.InternalDatabase
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A pair of local people that look similar enough to be worth a manual merge review. */
data class PersonMergeSuggestion(
    val first: PersonInfo,
    val second: PersonInfo,
    val similarity: Float
)

/**
 * Local, on-device people provider backed by the [PersonDao]/[DetectedFaceDao] tables that the
 * SmartScan FACE_INDEX/FACE_CLUSTER phases populate. Available whenever the face
 * detector model is installed.
 *
 * User corrections are written as durable assertions rather than one-shot rewrites:
 * merges leave an INCLUDE link on every moved face, face-level removals leave an
 * EXCLUDE link, and media-level removals keep using `face_exclusions`. The batch
 * clusterer consumes all of them, so a later re-group can never silently undo
 * what the user decided.
 */
@Singleton
class LocalPeopleProvider @Inject constructor(
    private val personDao: PersonDao,
    private val faceDao: DetectedFaceDao,
    private val cloudMediaDao: CloudMediaDao,
    private val mediaRepository: MediaRepository,
    private val modelManager: ModelManager,
    private val database: InternalDatabase
) : LocalCapabilityProvider(), PeopleCapableProvider {

    override val providerType: ProviderType = ProviderType.LOCAL_PEOPLE
    override val displayName: String = ProviderType.LOCAL_PEOPLE.displayName
    override val capabilities: Set<ProviderCapability> = setOf(ProviderCapability.PEOPLE)

    override suspend fun initialize() { /* No eager model load; sessions are created lazily. */ }

    override fun release() { }

    override val isAvailable: Boolean
        get() = modelManager.isReady(ModelGroup.FACE_DETECT)

    private fun PersonEntity.toInfo(photoCount: Int) = PersonInfo(
        id = id,
        name = name,
        providerType = ProviderType.LOCAL_PEOPLE,
        serverConfigId = LOCAL_PEOPLE_CONFIG_ID,
        thumbnailUrl = thumbnailUrl,
        assetCount = photoCount
    )

    override fun getPeople(): Flow<Resource<List<PersonInfo>>> = combine(
        personDao.getVisibleByProvider(ProviderType.LOCAL_PEOPLE),
        faceDao.observePersonPhotoCounts()
    ) { people, counts ->
        val byId = counts.associate { it.personId to it.photoCount }
        Resource.Success(people.map { it.toInfo(byId[it.id] ?: 0) })
    }

    /** Distinct photos that contain at least one assigned face — "M photos" in the header. */
    fun observeIndexedPhotoCount(): Flow<Int> = faceDao.observeIndexedPhotoCount()

    /** Hidden local people — surfaced behind the list's "show hidden" toggle. */
    fun observeHiddenPeople(): Flow<List<PersonInfo>> = combine(
        personDao.getByProvider(ProviderType.LOCAL_PEOPLE),
        faceDao.observePersonPhotoCounts()
    ) { people, counts ->
        val byId = counts.associate { it.personId to it.photoCount }
        people.filter { it.hidden }.map { it.toInfo(byId[it.id] ?: 0) }
    }

    /** Every face currently assigned to [personId], best-confidence first — the face strip. */
    fun observePersonFaces(personId: String): Flow<List<DetectedFaceEntity>> =
        faceDao.getByPerson(personId)

    /**
     * Local person pairs whose centroids sit in the uncertain similarity band — close
     * enough to plausibly be one person, not close enough for the clusterer to merge.
     * Pairs the user already rejected (EXCLUDE links between them) are filtered out.
     */
    fun observeMergeSuggestions(): Flow<List<PersonMergeSuggestion>> = combine(
        personDao.getVisibleByProvider(ProviderType.LOCAL_PEOPLE),
        faceDao.observeClusters(),
        faceDao.observeLinks()
    ) { people, clusters, links -> Triple(people, clusters, links) }
        .map { (people, clusters, links) ->
            val centroidById = clusters.associate {
                it.personId to FaceHelper.l2Normalize(it.centroid)
            }
            class Candidate(val a: PersonEntity, val b: PersonEntity, val sim: Float)
            val candidates = ArrayList<Candidate>()
            for (i in people.indices) {
                val ca = centroidById[people[i].id] ?: continue
                for (j in i + 1 until people.size) {
                    val cb = centroidById[people[j].id] ?: continue
                    val sim = FaceHelper.cosine(ca, cb)
                    if (sim >= SUGGEST_MIN_SIMILARITY) {
                        candidates += Candidate(people[i], people[j], sim)
                    }
                }
            }
            candidates.sortByDescending { it.sim }
            val excludes = links.filter { it.kind == FaceLinkKind.EXCLUDE }
            val faceCache = HashMap<String, List<DetectedFaceEntity>>()
            val photoCounts = faceDao.observePersonPhotoCounts().first()
                .associate { it.personId to it.photoCount }
            candidates.take(MAX_CANDIDATES_SCANNED).mapNotNull { candidate ->
                val facesA = faceCache.getOrPut(candidate.a.id) {
                    faceDao.getByPersonOnce(candidate.a.id)
                }
                val facesB = faceCache.getOrPut(candidate.b.id) {
                    faceDao.getByPersonOnce(candidate.b.id)
                }
                val rejected = excludes.any { link ->
                    (link.personId == candidate.a.id && link.matchesAny(facesB)) ||
                        (link.personId == candidate.b.id && link.matchesAny(facesA))
                }
                if (rejected) null else PersonMergeSuggestion(
                    first = candidate.a.toInfo(photoCounts[candidate.a.id] ?: 0),
                    second = candidate.b.toInfo(photoCounts[candidate.b.id] ?: 0),
                    similarity = candidate.sim
                )
            }.take(MAX_SUGGESTIONS)
        }

    override fun getPersonMedia(personId: String): Flow<Resource<List<Media>>> =
        faceDao.observeMediaIdsForPerson(personId).map { ids ->
            if (ids.isEmpty()) {
                return@map Resource.Success(emptyList())
            }
            val idSet = ids.toHashSet()
            val local = mediaRepository.getCompleteMedia().first().data.orEmpty()
            val cloud = cloudMediaDao.getAllCachedAsync().map { it.toUriMedia() }
            Resource.Success((local + cloud).filter { it.id in idSet })
        }

    /**
     * Persons whose faces were detected in [mediaId], emitted live as assignments change —
     * used by the media viewer's "remove from person" action to review which people a
     * media is counted as.
     */
    fun getMediaPeople(mediaId: Long): Flow<List<PersonInfo>> =
        faceDao.observePersonIdsForMedia(mediaId).map { ids ->
            ids.mapNotNull { personDao.getById(it) }.map { p ->
                p.toInfo(faceDao.countMediaForPerson(p.id))
            }
        }

    override fun getPersonThumbnailUrl(personId: String): String? = null

    override suspend fun updatePersonName(personId: String, name: String): Result<Unit> =
        runCatching { personDao.updateName(personId, name) }

    override suspend fun updatePersonBirthDate(personId: String, birthDate: String): Result<Unit> =
        Result.failure(UnsupportedOperationException("Local people provider does not track birth dates"))

    /**
     * Reassign every face of [sourceId] to [targetId] and delete the now-empty source
     * person. Records an INCLUDE link on every moved face so the batch clusterer treats
     * the merge as a user decision it must keep, retracts any prior "different person"
     * links between the pair, and carries the source's other assertions onto the target.
     */
    suspend fun mergePeople(sourceId: String, targetId: String) {
        if (sourceId == targetId) return
        val now = System.currentTimeMillis()
        val source = database.withTransaction {
            val source = personDao.getById(sourceId) ?: return@withTransaction null
            val sourceFaces = faceDao.getByPersonOnce(sourceId)
            val targetFaces = faceDao.getByPersonOnce(targetId)

            // Retract pairwise EXCLUDE assertions — merging says they are the same person.
            val staleIds = faceDao.getLinks()
                .filter { it.kind == FaceLinkKind.EXCLUDE }
                .filter { link ->
                    (link.personId == sourceId && link.matchesAny(targetFaces)) ||
                        (link.personId == targetId && link.matchesAny(sourceFaces))
                }
                .map { it.id }
            if (staleIds.isNotEmpty()) faceDao.deleteLinksByIds(staleIds)

            // The source's remaining assertions now describe the merged person.
            faceDao.reassignLinks(sourceId, targetId, FaceLinkKind.INCLUDE)
            faceDao.reassignLinks(sourceId, targetId, FaceLinkKind.EXCLUDE)

            // The merge itself is a durable include assertion for every moved face.
            if (sourceFaces.isNotEmpty()) {
                faceDao.upsertLinks(sourceFaces.map {
                    FaceLinkEntity(
                        mediaId = it.mediaId,
                        left = it.left,
                        top = it.top,
                        right = it.right,
                        bottom = it.bottom,
                        personId = targetId,
                        kind = FaceLinkKind.INCLUDE,
                        createdAt = now
                    )
                })
            }

            faceDao.reassignPerson(sourceId, targetId)
            personDao.deleteById(sourceId)
            personDao.updateFaceCount(targetId, faceDao.countForPerson(targetId), now)
            source
        } ?: return
        // Cover files are deleted outside the transaction — the person's
        // face_links rows are already gone via the FK cascade.
        deleteThumbnailFile(source)
    }

    /**
     * Remove [mediaIds] from [personId]'s aggregation without deleting the media: face rows
     * are un-assigned and a durable exclusion is recorded so future re-scans don't cluster
     * the same media back onto this person.
     *
     * @return true if the person still exists afterwards (false when the last face was
     *         removed and the empty person was deleted).
     */
    suspend fun removeMediaFromPerson(personId: String, mediaIds: List<Long>): Boolean {
        if (mediaIds.isEmpty()) return personDao.getById(personId) != null
        val person = personDao.getById(personId) ?: return false
        val now = System.currentTimeMillis()
        faceDao.insertExclusions(mediaIds.map { FaceExclusionEntity(it, personId, now) })
        faceDao.unassignPersonMedia(personId, mediaIds)
        val remaining = faceDao.countForPerson(personId)
        if (remaining <= 0) {
            deleteThumbnailFile(person)
            personDao.deleteById(personId)
            return false
        }
        personDao.updateFaceCount(personId, remaining, now)
        // Drop the persisted centroid — the next cluster run rebuilds it without the
        // removed faces.
        faceDao.deleteClusters(listOf(personId))
        if (person.thumbnailMediaId?.let { it in mediaIds } == true) {
            deleteThumbnailFile(person)
            personDao.updateThumbnail(personId, null, null)
        }
        return true
    }

    /**
     * Remove individual faces from [personId] ("this face is not this person"): an EXCLUDE
     * link is recorded per face — which the clusterer honours as a cannot-link — and any
     * prior INCLUDE pin for the same face is retracted.
     *
     * @return true if the person still exists afterwards.
     */
    suspend fun removeFacesFromPerson(personId: String, faceIds: List<Long>): Boolean {
        if (faceIds.isEmpty()) return personDao.getById(personId) != null
        val person = personDao.getById(personId) ?: return false
        val idSet = faceIds.toHashSet()
        val faces = faceDao.getByPersonOnce(personId).filter { it.id in idSet }
        if (faces.isEmpty()) return true
        val now = System.currentTimeMillis()
        faceDao.upsertLinks(faces.map {
            FaceLinkEntity(
                mediaId = it.mediaId,
                left = it.left,
                top = it.top,
                right = it.right,
                bottom = it.bottom,
                personId = personId,
                kind = FaceLinkKind.EXCLUDE,
                createdAt = now
            )
        })
        val pinnedIds = faceDao.getLinks()
            .filter { it.kind == FaceLinkKind.INCLUDE && it.personId == personId }
            .filter { it.matchesAny(faces) }
            .map { it.id }
        if (pinnedIds.isNotEmpty()) faceDao.deleteLinksByIds(pinnedIds)
        faceDao.assignFaces(faceIds, null)
        val remaining = faceDao.countForPerson(personId)
        if (remaining <= 0) {
            deleteThumbnailFile(person)
            personDao.deleteById(personId)
            return false
        }
        personDao.updateFaceCount(personId, remaining, now)
        faceDao.deleteClusters(listOf(personId))
        return true
    }

    /**
     * The user reviewed [personId] vs [otherId] and said "different people". Writes an
     * EXCLUDE link for every face on both sides so the pair can never be merged by the
     * clusterer and stops surfacing in merge suggestions.
     */
    suspend fun rejectPersonPair(personId: String, otherId: String) {
        val now = System.currentTimeMillis()
        val links = faceDao.getByPersonOnce(personId).map {
            FaceLinkEntity(
                mediaId = it.mediaId,
                left = it.left,
                top = it.top,
                right = it.right,
                bottom = it.bottom,
                personId = otherId,
                kind = FaceLinkKind.EXCLUDE,
                createdAt = now
            )
        } + faceDao.getByPersonOnce(otherId).map {
            FaceLinkEntity(
                mediaId = it.mediaId,
                left = it.left,
                top = it.top,
                right = it.right,
                bottom = it.bottom,
                personId = personId,
                kind = FaceLinkKind.EXCLUDE,
                createdAt = now
            )
        }
        if (links.isNotEmpty()) faceDao.upsertLinks(links)
    }

    /** True when [this] link's box overlaps any of [faces] on the same media. */
    private fun FaceLinkEntity.matchesAny(faces: List<DetectedFaceEntity>): Boolean =
        faces.any { face ->
            face.mediaId == mediaId && boxIoU(
                left, top, right, bottom,
                face.left, face.top, face.right, face.bottom
            ) >= LINK_MATCH_IOU
        }

    private fun boxIoU(
        l1: Float, t1: Float, r1: Float, b1: Float,
        l2: Float, t2: Float, r2: Float, b2: Float
    ): Float {
        val ix = maxOf(l1, l2)
        val iy = maxOf(t1, t2)
        val ax = minOf(r1, r2)
        val ay = minOf(b1, b2)
        val inter = (ax - ix).coerceAtLeast(0f) * (ay - iy).coerceAtLeast(0f)
        if (inter <= 0f) return 0f
        val union = (r1 - l1) * (b1 - t1) + (r2 - l2) * (b2 - t2) - inter
        return if (union <= 0f) 0f else inter / union
    }

    private fun deleteThumbnailFile(person: PersonEntity) {
        person.thumbnailUrl?.let { url ->
            runCatching { File(requireNotNull(url.toUri().path)).delete() }
        }
    }

    suspend fun setHidden(personId: String, hidden: Boolean) = personDao.setHidden(personId, hidden)

    suspend fun setCover(personId: String, mediaId: Long, thumbnailUrl: String?) =
        personDao.updateThumbnail(personId, mediaId, thumbnailUrl)

    private companion object {
        const val SUGGEST_MIN_SIMILARITY = 0.45f
        const val MAX_CANDIDATES_SCANNED = 50
        const val MAX_SUGGESTIONS = 20
        const val LINK_MATCH_IOU = 0.5f
    }
}
