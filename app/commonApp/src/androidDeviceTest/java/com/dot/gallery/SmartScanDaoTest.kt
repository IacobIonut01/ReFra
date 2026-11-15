/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery

import androidx.room3.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.data.entity.DetectedFaceEntity
import com.dot.gallery.data.entity.FaceClusterEntity
import com.dot.gallery.data.entity.PersonEntity
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.data.entity.MediaFeature
import com.dot.gallery.data.entity.MediaFeatureStateEntity
import com.dot.gallery.data.entity.MediaFeatureStatus
import com.dot.gallery.data.dao.SmartScanDao
import com.dot.gallery.data.entity.SmartScanFeature
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.data.entity.SmartScanPhaseEntity
import com.dot.gallery.data.entity.SmartScanRunEntity
import com.dot.gallery.data.entity.SmartScanStatus
import com.dot.gallery.data.entity.SmartScanTrigger
import com.dot.gallery.domain.model.ImageEmbedding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class SmartScanDaoTest {
    private lateinit var db: InternalDatabase
    private lateinit var dao: SmartScanDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.getSmartScanDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun activeAndLatestFlowsTrackRunLifecycle() = runBlocking {
        dao.upsertRun(SmartScanRunEntity("old", SmartScanTrigger.AUTOMATIC, requestedAt = 10L))
        dao.upsertRun(SmartScanRunEntity("new", SmartScanTrigger.MANUAL, requestedAt = 20L))
        dao.upsertPhase(
            SmartScanPhaseEntity(
                runId = "new",
                phase = SmartScanPhase.METADATA,
                updatedAt = 20L
            )
        )

        assertEquals("new", dao.observeActiveRun().first()?.runId)
        assertEquals("new", dao.observeLatestRun().first()?.runId)
        assertEquals(2, dao.observeActiveRuns().first().size)

        assertEquals(1, dao.claimRunLease("new", "worker-a", 30L, 40L))
        assertEquals(1, dao.claimRunLease("new", "worker-a", 35L, 45L))
        assertEquals(0, dao.claimRunLease("new", "worker-b", 35L, 45L))
        assertEquals(1, dao.claimPhaseLease("new", SmartScanPhase.METADATA, "worker-a", 30L, 40L, "v1"))
        assertEquals(1, dao.claimPhaseLease("new", SmartScanPhase.METADATA, "worker-a", 35L, 45L, "v1"))
        assertEquals(35L, dao.getPhase("new", SmartScanPhase.METADATA)?.startedAt)
        assertEquals(0, dao.claimPhaseLease("new", SmartScanPhase.METADATA, "worker-b", 35L, 45L, "v1"))
        assertEquals(1, dao.recoverExpiredPhaseLeases(46L))
        assertEquals(1, dao.recoverExpiredRunLeases(46L))
        assertEquals(1, dao.claimRunLease("new", "worker-b", 46L, 56L))
        assertEquals(1, dao.finishRun("new", SmartScanStatus.SUCCEEDED, 57L))

        assertEquals("old", dao.observeActiveRun().first()?.runId)
        assertEquals("new", dao.observeLatestRun().first()?.runId)
    }

    @Test
    fun latestTerminalPhaseTracksProcessorRevisionAcrossRuns() = runBlocking {
        dao.upsertRun(SmartScanRunEntity("old", SmartScanTrigger.AUTOMATIC, requestedAt = 10L))
        dao.upsertRun(SmartScanRunEntity("new", SmartScanTrigger.AUTOMATIC, requestedAt = 20L))
        dao.upsertRun(SmartScanRunEntity("failed", SmartScanTrigger.AUTOMATIC, requestedAt = 30L))
        dao.upsertPhase(
            SmartScanPhaseEntity(
                runId = "old",
                phase = SmartScanPhase.SEARCH_INDEX,
                status = SmartScanStatus.SUCCEEDED,
                finishedAt = 11L,
                updatedAt = 11L,
                processorRevision = "clip-v1"
            )
        )
        dao.upsertPhase(
            SmartScanPhaseEntity(
                runId = "new",
                phase = SmartScanPhase.SEARCH_INDEX,
                status = SmartScanStatus.BLOCKED,
                finishedAt = 21L,
                updatedAt = 21L,
                processorRevision = "clip-v2"
            )
        )
        dao.upsertPhase(
            SmartScanPhaseEntity(
                runId = "failed",
                phase = SmartScanPhase.SEARCH_INDEX,
                status = SmartScanStatus.FAILED,
                finishedAt = 31L,
                updatedAt = 31L,
                processorRevision = "clip-v3"
            )
        )

        assertEquals("clip-v2", dao.getLatestCurrentPhase(SmartScanPhase.SEARCH_INDEX)?.processorRevision)
        assertEquals("clip-v1", dao.getLatestSuccessfulPhase(SmartScanPhase.SEARCH_INDEX)?.processorRevision)
    }

    @Test
    fun existingEmbeddingRevisionCanBeAdoptedWithoutReplacingVector() = runBlocking {
        val embedding = ImageEmbedding(7L, 100L, floatArrayOf(0.6f, 0.8f))
        db.getImageEmbeddingDao().addImageEmbedding(embedding)

        assertEquals(1, db.getImageEmbeddingDao().updateResultRevision(7L, "clip-v2"))

        val adopted = db.getImageEmbeddingDao().getRecord(7L)
        assertEquals("clip-v2", adopted?.resultRevision)
        assertEquals(true, adopted?.embedding?.contentEquals(embedding.embedding))
        assertEquals(listOf(7L), db.getImageEmbeddingDao().getIds())
        assertEquals("clip-v2", db.getImageEmbeddingDao().getHeaders().single().resultRevision)
    }

    @Test
    fun embeddingPreparationReadsAndUpdatesRecordsInBatches() = runBlocking {
        val embeddingDao = db.getImageEmbeddingDao()
        embeddingDao.addImageEmbedding(ImageEmbedding(7L, 100L, floatArrayOf(0.6f, 0.8f)))
        embeddingDao.addImageEmbedding(ImageEmbedding(8L, 100L, floatArrayOf(0.8f, 0.6f)))

        assertEquals(setOf(7L, 8L), embeddingDao.getRecords(listOf(7L, 8L)).mapTo(hashSetOf()) { it.id })
        assertEquals(2, embeddingDao.updateResultRevisions(listOf(7L, 8L), "clip-v2"))
        assertEquals(setOf("clip-v2"), embeddingDao.getRecords(listOf(7L, 8L)).mapTo(hashSetOf()) { it.resultRevision })
    }

    @Test
    fun existingFaceRevisionsCanBeAdoptedWithoutReplacingResults() = runBlocking {
        val faceDao = db.getDetectedFaceDao()
        faceDao.insert(
            DetectedFaceEntity(
                mediaId = 7L,
                embedding = byteArrayOf(1, 2, 3, 4),
                left = 0.1f,
                top = 0.1f,
                right = 0.8f,
                bottom = 0.8f,
                confidence = 0.9f,
                timestamp = 100L
            )
        )

        assertEquals(1, faceDao.updateResultRevision(7L, "face-v2"))

        val adopted = faceDao.getByMedia(7L).single()
        assertEquals("face-v2", adopted.resultRevision)
        assertEquals(0.9f, adopted.confidence)
        assertEquals("face-v2", faceDao.getHeaders().single().resultRevision)

        val centroid = FloatArray(512) { 1f / kotlin.math.sqrt(512f) }
        db.getPersonDao().insert(PersonEntity("person", "", ProviderType.LOCAL_PEOPLE))
        faceDao.upsertClusters(listOf(FaceClusterEntity("person", centroid, 1, 10L)))
        assertEquals(true, faceDao.getClusters().single().centroid.contentEquals(centroid))
    }

    @Test
    fun updatingPersonPreservesFacesAndCluster() = runBlocking {
        val personDao = db.getPersonDao()
        val faceDao = db.getDetectedFaceDao()
        val centroid = FloatArray(512) { 1f / kotlin.math.sqrt(512f) }
        val person = PersonEntity("person", "", ProviderType.LOCAL_PEOPLE)
        personDao.insert(person)
        faceDao.insert(DetectedFaceEntity(mediaId = 7L, personId = person.id, embedding = byteArrayOf(1)))
        faceDao.upsertClusters(listOf(FaceClusterEntity(person.id, centroid, 1, 10L)))

        personDao.insert(person.copy(name = "Updated"))

        assertEquals(person.id, faceDao.getByMedia(7L).single().personId)
        assertEquals(person.id, faceDao.getClusters().single().personId)
    }

    @Test
    fun featureLeaseIsExclusiveAndRecoverable() = runBlocking {
        dao.upsertFeatureState(
            MediaFeatureStateEntity(
                mediaId = 7L,
                feature = MediaFeature.SEARCH_EMBEDDING,
                sourceRevision = "source-1",
                updatedAt = 10L
            )
        )

        assertEquals(1, dao.claimFeatureLease(7L, MediaFeature.SEARCH_EMBEDDING, "run", "a", 11L, 20L))
        assertEquals(0, dao.claimFeatureLease(7L, MediaFeature.SEARCH_EMBEDDING, "run", "b", 12L, 21L))
        assertEquals(1, dao.recoverExpiredFeatureLeases(20L))
        assertEquals(1, dao.claimFeatureLease(7L, MediaFeature.SEARCH_EMBEDDING, "run", "b", 21L, 30L))
        assertEquals(
            1,
            dao.finishFeature(
                7L,
                MediaFeature.SEARCH_EMBEDDING,
                "b",
                MediaFeatureStatus.SUCCEEDED,
                "model-v1",
                22L
            )
        )

        val state = dao.getFeatureState(7L, MediaFeature.SEARCH_EMBEDDING)
        assertEquals(MediaFeatureStatus.SUCCEEDED, state?.status)
        assertEquals("model-v1", state?.resultRevision)
        assertNull(state?.leaseOwner)
        assertEquals(listOf(7L), dao.getFeatureStates(MediaFeature.SEARCH_EMBEDDING).map { it.mediaId })
    }

    @Test
    fun interruptedAttemptReleasesOnlyItsFeatureLeases() = runBlocking {
        dao.upsertFeatureState(
            MediaFeatureStateEntity(
                mediaId = 7L,
                feature = MediaFeature.METADATA,
                sourceRevision = "source",
                updatedAt = 10L
            )
        )
        assertEquals(1, dao.claimFeatureLease(7L, MediaFeature.METADATA, "run", "attempt:metadata:7", 11L, 100L))

        assertEquals(0, dao.releaseFeatureLeases("run", "other", 12L))
        assertEquals(1, dao.releaseFeatureLeases("run", "attempt", 13L))
        assertEquals(MediaFeatureStatus.PENDING, dao.getFeatureState(7L, MediaFeature.METADATA)?.status)
    }

    @Test
    fun cancellingRunReleasesProcessingFeatureState() = runBlocking {
        dao.upsertRun(SmartScanRunEntity("cancel", SmartScanTrigger.MANUAL, requestedAt = 10L))
        dao.upsertPhase(SmartScanPhaseEntity("cancel", SmartScanPhase.METADATA, updatedAt = 10L))
        dao.upsertFeatureState(
            MediaFeatureStateEntity(
                mediaId = 7L,
                feature = MediaFeature.METADATA,
                sourceRevision = "source",
                updatedAt = 10L
            )
        )
        dao.claimFeatureLease(7L, MediaFeature.METADATA, "cancel", "owner", 11L, 100L)

        dao.cancelRun("cancel", 12L)

        val state = dao.getFeatureState(7L, MediaFeature.METADATA)
        assertEquals(MediaFeatureStatus.PENDING, state?.status)
        assertNull(state?.leaseOwner)
        assertNull(state?.runId)
    }

    @Test
    fun coalescingAnIdenticalRequestDoesNotRequeueCompletedPhases() = runBlocking {
        dao.upsertRun(
            SmartScanRunEntity(
                runId = "active",
                trigger = SmartScanTrigger.AUTOMATIC,
                requestedFeatures = SmartScanFeature.METADATA.bit,
                workId = "existing-work",
                requestedAt = 10L
            )
        )
        dao.upsertPhase(
            SmartScanPhaseEntity(
                runId = "active",
                phase = SmartScanPhase.METADATA,
                status = SmartScanStatus.SUCCEEDED,
                finishedAt = 20L,
                updatedAt = 20L,
                totalMedia = 100,
                processedMedia = 100,
                succeededMedia = 100
            )
        )

        val result = dao.coalesceOrCreate(
            run = SmartScanRunEntity(
                runId = "incoming",
                trigger = SmartScanTrigger.AUTOMATIC,
                requestedFeatures = SmartScanFeature.METADATA.bit,
                workId = "incoming-work",
                requestedAt = 30L
            ),
            phases = listOf(
                SmartScanPhaseEntity(
                    runId = "incoming",
                    phase = SmartScanPhase.METADATA,
                    updatedAt = 30L
                )
            )
        )

        assertEquals("active", result.runId)
        assertEquals(false, result.created)
        val phase = dao.getPhase("active", SmartScanPhase.METADATA)
        assertEquals(SmartScanStatus.SUCCEEDED, phase?.status)
        assertEquals(100, phase?.processedMedia)
    }

    @Test
    fun fullRefreshRequeuesCompletedPhases() = runBlocking {
        dao.upsertRun(
            SmartScanRunEntity(
                runId = "active",
                trigger = SmartScanTrigger.AUTOMATIC,
                requestedFeatures = SmartScanFeature.METADATA.bit,
                workId = "existing-work",
                requestedAt = 10L
            )
        )
        dao.upsertPhase(
            SmartScanPhaseEntity(
                runId = "active",
                phase = SmartScanPhase.METADATA,
                status = SmartScanStatus.SUCCEEDED,
                finishedAt = 20L,
                updatedAt = 20L,
                totalMedia = 100,
                processedMedia = 100,
                succeededMedia = 100
            )
        )

        dao.coalesceOrCreate(
            run = SmartScanRunEntity(
                runId = "refresh",
                trigger = SmartScanTrigger.MANUAL,
                requestedFeatures = SmartScanFeature.METADATA.bit,
                userVisible = true,
                fullRefresh = true,
                workId = "refresh-work",
                requestedAt = 30L
            ),
            phases = listOf(
                SmartScanPhaseEntity(
                    runId = "refresh",
                    phase = SmartScanPhase.METADATA,
                    updatedAt = 30L
                )
            )
        )

        val phase = dao.getPhase("active", SmartScanPhase.METADATA)
        assertEquals(SmartScanStatus.QUEUED, phase?.status)
        assertEquals(0, phase?.processedMedia)
        assertEquals(true, dao.getRun("active")?.fullRefresh)
    }

    @Test
    fun scopedFullRefreshRequeuesOnlyMatchingPhases() = runBlocking {
        dao.upsertRun(
            SmartScanRunEntity(
                runId = "active",
                trigger = SmartScanTrigger.AUTOMATIC,
                requestedFeatures = SmartScanFeature.ALL_MASK,
                workId = "existing-work",
                requestedAt = 10L
            )
        )
        listOf(
            SmartScanPhase.SOURCE_SYNC,
            SmartScanPhase.METADATA,
            SmartScanPhase.SEARCH_INDEX,
            SmartScanPhase.CATEGORY_CLASSIFICATION
        ).forEach { phase ->
            dao.upsertPhase(
                SmartScanPhaseEntity(
                    runId = "active",
                    phase = phase,
                    status = SmartScanStatus.SUCCEEDED,
                    finishedAt = 20L,
                    updatedAt = 20L,
                    totalMedia = 100,
                    processedMedia = 100,
                    succeededMedia = 100
                )
            )
        }

        dao.coalesceOrCreate(
            run = SmartScanRunEntity(
                runId = "refresh",
                trigger = SmartScanTrigger.MANUAL,
                requestedFeatures = SmartScanFeature.expandDependencies(SmartScanFeature.CATEGORIES.bit),
                userVisible = true,
                fullRefreshFeatures = SmartScanFeature.CATEGORIES.bit,
                workId = "refresh-work",
                requestedAt = 30L
            ),
            phases = listOf(
                SmartScanPhase.SOURCE_SYNC,
                SmartScanPhase.SEARCH_INDEX,
                SmartScanPhase.CATEGORY_CLASSIFICATION
            ).map { SmartScanPhaseEntity(runId = "refresh", phase = it, updatedAt = 30L) }
        )

        // Only the named feature's phase is rebuilt; dependencies and SOURCE_SYNC stay put.
        assertEquals(
            SmartScanStatus.QUEUED,
            dao.getPhase("active", SmartScanPhase.CATEGORY_CLASSIFICATION)?.status
        )
        assertEquals(
            SmartScanStatus.SUCCEEDED,
            dao.getPhase("active", SmartScanPhase.SEARCH_INDEX)?.status
        )
        assertEquals(SmartScanStatus.SUCCEEDED, dao.getPhase("active", SmartScanPhase.METADATA)?.status)
        assertEquals(
            SmartScanStatus.SUCCEEDED,
            dao.getPhase("active", SmartScanPhase.SOURCE_SYNC)?.status
        )
        val run = dao.getRun("active")
        assertEquals(false, run?.fullRefresh)
        assertEquals(SmartScanFeature.CATEGORIES.bit, run?.fullRefreshFeatures)
    }

    @Test
    fun scopedFullRefreshRequestsAccumulateFeatureMask() = runBlocking {
        dao.upsertRun(
            SmartScanRunEntity(
                runId = "active",
                trigger = SmartScanTrigger.AUTOMATIC,
                requestedFeatures = SmartScanFeature.ALL_MASK,
                workId = "existing-work",
                requestedAt = 10L
            )
        )

        dao.coalesceOrCreate(
            run = SmartScanRunEntity(
                runId = "first",
                trigger = SmartScanTrigger.MANUAL,
                requestedFeatures = SmartScanFeature.expandDependencies(SmartScanFeature.CATEGORIES.bit),
                userVisible = true,
                fullRefreshFeatures = SmartScanFeature.CATEGORIES.bit,
                workId = "first-work",
                requestedAt = 20L
            ),
            phases = emptyList()
        )
        assertEquals(SmartScanFeature.CATEGORIES.bit, dao.getRun("active")?.fullRefreshFeatures)

        dao.coalesceOrCreate(
            run = SmartScanRunEntity(
                runId = "second",
                trigger = SmartScanTrigger.MANUAL,
                requestedFeatures = SmartScanFeature.PERSONS.bit,
                userVisible = true,
                fullRefreshFeatures = SmartScanFeature.PERSONS.bit,
                workId = "second-work",
                requestedAt = 30L
            ),
            phases = emptyList()
        )

        val run = dao.getRun("active")
        assertEquals(
            SmartScanFeature.CATEGORIES.bit or SmartScanFeature.PERSONS.bit,
            run?.fullRefreshFeatures
        )
        assertEquals(false, run?.fullRefresh)
    }

    @Test
    fun manualRequestPromotesQueuedAutomaticWork() = runBlocking {
        dao.upsertRun(
            SmartScanRunEntity(
                runId = "active",
                trigger = SmartScanTrigger.AUTOMATIC,
                requestedFeatures = SmartScanFeature.METADATA.bit,
                workId = "automatic-work",
                requestedAt = 10L
            )
        )

        val result = dao.coalesceOrCreate(
            run = SmartScanRunEntity(
                runId = "manual",
                trigger = SmartScanTrigger.MANUAL,
                requestedFeatures = SmartScanFeature.METADATA.bit,
                userVisible = true,
                workId = "manual-work",
                requestedAt = 20L
            ),
            phases = listOf(
                SmartScanPhaseEntity(
                    runId = "manual",
                    phase = SmartScanPhase.METADATA,
                    updatedAt = 20L
                )
            )
        )

        assertEquals("active", result.runId)
        assertEquals("manual-work", result.workId)
        assertEquals(true, dao.getRun("active")?.userVisible)
        assertEquals("manual-work", dao.getRun("active")?.workId)
    }

    @Test
    fun recoveryRequeuesOwnedWorkWithoutChangingRequestedFeatures() = runBlocking {
        dao.upsertRun(
            SmartScanRunEntity(
                runId = "resume",
                trigger = SmartScanTrigger.MANUAL,
                requestedFeatures = SmartScanFeature.PERSONS.bit,
                userVisible = true,
                workId = "old-work",
                requestedAt = 10L
            )
        )
        dao.upsertPhase(
            SmartScanPhaseEntity(
                runId = "resume",
                phase = SmartScanPhase.FACE_INDEX,
                updatedAt = 10L
            )
        )
        dao.upsertFeatureState(
            MediaFeatureStateEntity(
                mediaId = 9L,
                feature = MediaFeature.FACE_DETECTION,
                updatedAt = 10L
            )
        )
        assertEquals(1, dao.claimRunLease("resume", "old-work", 11L, 100L))
        assertEquals(1, dao.claimPhaseLease("resume", SmartScanPhase.FACE_INDEX, "old-work", 11L, 100L, "v1"))
        assertEquals(1, dao.claimFeatureLease(9L, MediaFeature.FACE_DETECTION, "resume", "item", 11L, 100L))

        assertEquals(true, dao.prepareRunRecovery("resume", "new-work", 20L))

        val run = dao.getRun("resume")
        assertEquals(SmartScanFeature.PERSONS.bit, run?.requestedFeatures)
        assertEquals(true, run?.userVisible)
        assertEquals("new-work", run?.workId)
        assertEquals(SmartScanStatus.QUEUED, run?.status)
        assertEquals(SmartScanStatus.QUEUED, dao.getPhase("resume", SmartScanPhase.FACE_INDEX)?.status)
        assertEquals(MediaFeatureStatus.PENDING, dao.getFeatureState(9L, MediaFeature.FACE_DETECTION)?.status)
    }

    @Test
    fun pruningIsBoundedAndCascadesPhases() = runBlocking {
        repeat(4) { index ->
            val runId = "run-$index"
            dao.upsertRun(
                SmartScanRunEntity(
                    runId = runId,
                    trigger = SmartScanTrigger.AUTOMATIC,
                    status = SmartScanStatus.SUCCEEDED,
                    requestedAt = index.toLong(),
                    finishedAt = index.toLong(),
                    updatedAt = index.toLong()
                )
            )
            dao.upsertPhase(
                SmartScanPhaseEntity(
                    runId = runId,
                    phase = SmartScanPhase.METADATA,
                    status = SmartScanStatus.SUCCEEDED,
                    updatedAt = index.toLong()
                )
            )
        }

        assertEquals(2, dao.pruneTerminalRuns(keepLatest = 1, maxDelete = 2))
        assertEquals("run-3", dao.getRun("run-3")?.runId)
        assertNull(dao.getRun("run-1"))
        assertEquals(0, dao.observePhases("run-1").first().size)
        assertEquals(1, dao.pruneTerminalRuns(keepLatest = 1, maxDelete = 10))
        assertEquals("run-3", dao.observeLatestRun().first()?.runId)
    }
}
