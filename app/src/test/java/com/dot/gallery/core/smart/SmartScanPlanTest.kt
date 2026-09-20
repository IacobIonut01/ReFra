/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.data.entity.CloudMediaEntity
import com.dot.gallery.cloud.data.entity.DetectedFaceEntity
import com.dot.gallery.feature_node.data.data_source.MediaFeature
import com.dot.gallery.feature_node.data.data_source.MediaFeatureStateEntity
import com.dot.gallery.feature_node.data.data_source.MediaFeatureStatus
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.data.data_source.SmartScanPhase
import com.dot.gallery.feature_node.data.data_source.SmartScanPhaseEntity
import com.dot.gallery.feature_node.data.data_source.SmartScanRunEntity
import com.dot.gallery.feature_node.data.data_source.SmartScanStatus
import com.dot.gallery.feature_node.data.data_source.SmartScanTrigger
import com.dot.gallery.feature_node.domain.model.ImageEmbedding
import com.dot.gallery.feature_node.domain.model.MediaCategory
import com.dot.gallery.feature_node.domain.util.FloatVectorCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import kotlin.math.sqrt

class SmartScanPlanTest {
    @Test
    fun categoriesExpandToEmbeddings() {
        val expanded = SmartScanPlan.expandedFeatures(SmartScanFeature.CATEGORIES.bit)

        assertTrue(expanded and SmartScanFeature.CATEGORIES.bit != 0)
        assertTrue(expanded and SmartScanFeature.EMBEDDINGS.bit != 0)
    }

    @Test
    fun independentBranchesRunTogetherAfterSourceSync() {
        assertEquals(
            listOf(
                listOf(SmartScanPhase.METADATA),
                listOf(SmartScanPhase.SEARCH_INDEX, SmartScanPhase.CATEGORY_CLASSIFICATION),
                listOf(SmartScanPhase.FACE_INDEX, SmartScanPhase.FACE_CLUSTER)
            ),
            SmartScanPlan.executionBranches(SmartScanFeature.ALL_MASK)
        )
        assertEquals(
            listOf(listOf(SmartScanPhase.FACE_INDEX, SmartScanPhase.FACE_CLUSTER)),
            SmartScanPlan.executionBranches(SmartScanFeature.PERSONS.bit)
        )
    }

    @Test
    fun categoryClassificationSharesOnlyTheSearchDependencyBranch() {
        assertEquals(
            listOf(listOf(SmartScanPhase.SEARCH_INDEX, SmartScanPhase.CATEGORY_CLASSIFICATION)),
            SmartScanPlan.executionBranches(SmartScanFeature.CATEGORIES.bit)
        )
    }

    @Test
    fun automaticHeavyRunsRequireChargingButManualRunsDoNot() {
        assertEquals(
            false,
            smartScanConstraintsFor(listOf(SmartScanPhase.SOURCE_SYNC, SmartScanPhase.METADATA), userVisible = false)
                .requiresCharging()
        )
        assertEquals(
            true,
            smartScanConstraintsFor(SmartScanPlan.phasesFor(SmartScanFeature.ALL_MASK), userVisible = false)
                .requiresCharging()
        )
        assertEquals(
            true,
            smartScanConstraintsFor(SmartScanPlan.phasesFor(SmartScanFeature.PERSONS.bit), userVisible = false)
                .requiresBatteryNotLow()
        )
        assertEquals(
            false,
            smartScanConstraintsFor(SmartScanPlan.phasesFor(SmartScanFeature.ALL_MASK), userVisible = true)
                .requiresCharging()
        )
    }

    @Test
    fun nullRunRequestsNoFeature() {
        assertEquals(false, SmartScanPlan.runRequestsFeature(null, SmartScanFeature.CATEGORIES))
        assertEquals(false, SmartScanPlan.runRequestsFeature(null, SmartScanFeature.METADATA))
    }

    @Test
    fun runRequestsFeatureMatchesOnlyRequestedBits() {
        val run = SmartScanRunEntity(
            runId = "run",
            trigger = SmartScanTrigger.AUTOMATIC,
            requestedFeatures = SmartScanFeature.CATEGORIES.bit or SmartScanFeature.EMBEDDINGS.bit,
            requestedAt = 1L
        )

        assertEquals(true, SmartScanPlan.runRequestsFeature(run, SmartScanFeature.CATEGORIES))
        assertEquals(true, SmartScanPlan.runRequestsFeature(run, SmartScanFeature.EMBEDDINGS))
        assertEquals(false, SmartScanPlan.runRequestsFeature(run, SmartScanFeature.PERSONS))
        assertEquals(
            false,
            SmartScanPlan.runRequestsFeature(run.copy(requestedFeatures = 0), SmartScanFeature.CATEGORIES)
        )
    }

    @Test
    fun automaticPreparationStaysHiddenUntilActualWorkIsFound() {
        assertEquals(false, SmartScanPlan.shouldShowRun(userVisible = false, totalMedia = 0))
        assertEquals(true, SmartScanPlan.shouldShowRun(userVisible = false, totalMedia = 1))
        assertEquals(true, SmartScanPlan.shouldShowRun(userVisible = true, totalMedia = 0))
    }

    @Test
    fun currentMediaVersionReusesCachedSourceUnlessFullRefreshWasRequested() {
        assertEquals(false, shouldRefreshSmartLocalSource(fullRefresh = false, mediaVersionCurrent = true))
        assertEquals(true, shouldRefreshSmartLocalSource(fullRefresh = false, mediaVersionCurrent = false))
        assertEquals(true, shouldRefreshSmartLocalSource(fullRefresh = true, mediaVersionCurrent = true))
    }

    @Test
    fun terminalSourceFromAnOlderProcessorRevisionIsRequeued() {
        assertEquals(
            true,
            SmartScanPlan.shouldRequeueForRevision(SmartScanStatus.SUCCEEDED, "source-v1", "source-v2")
        )
        assertEquals(
            false,
            SmartScanPlan.shouldRequeueForRevision(SmartScanStatus.SUCCEEDED, "source-v2", "source-v2")
        )
        assertEquals(
            false,
            SmartScanPlan.shouldRequeueForRevision(SmartScanStatus.RUNNING, "source-v1", "source-v2")
        )
    }

    @Test
    fun automaticScanIsSkippedOnlyWhenMediaAndProcessorRevisionsAreCurrent() {
        val expected = mapOf(
            SmartScanPhase.METADATA to "metadata-v1",
            SmartScanPhase.SEARCH_INDEX to "clip-v2"
        )

        assertEquals(true, SmartScanPlan.isAutomaticScanCurrent(true, expected, expected))
        assertEquals(false, SmartScanPlan.isAutomaticScanCurrent(false, expected, expected))
        assertEquals(
            false,
            SmartScanPlan.isAutomaticScanCurrent(
                true,
                expected,
                expected + (SmartScanPhase.SEARCH_INDEX to "clip-v1")
            )
        )
        assertEquals(
            false,
            SmartScanPlan.isAutomaticScanCurrent(
                true,
                expected,
                mapOf(SmartScanPhase.METADATA to "metadata-v1")
            )
        )
    }

    @Test
    fun phaseCheckpointMustBelongToTheCurrentSourceSnapshot() {
        assertEquals(
            true,
            SmartScanPlan.isPhaseCheckpointCurrent("v2", "v2", "source-2", "source-2")
        )
        assertEquals(
            false,
            SmartScanPlan.isPhaseCheckpointCurrent("v2", "v2", "source-2", "source-1")
        )
        assertEquals(
            false,
            SmartScanPlan.isPhaseCheckpointCurrent("v2", "v1", "source-2", "source-2")
        )
        assertEquals(
            false,
            SmartScanPlan.isPhaseCheckpointCurrent("v2", "v2", null, "source-2")
        )
    }

    @Test
    fun incrementalSkipsCurrentItemsButFullRefreshProcessesThem() {
        assertEquals(false, SmartScanPlan.shouldProcess(fullRefresh = false, isCurrent = true))
        assertEquals(true, SmartScanPlan.shouldProcess(fullRefresh = false, isCurrent = false))
        assertEquals(true, SmartScanPlan.shouldProcess(fullRefresh = true, isCurrent = true))
    }

    @Test
    fun featureBackoffIsPreservedForUnchangedInput() {
        val state = MediaFeatureStateEntity(
            mediaId = 7L,
            feature = MediaFeature.SEARCH_EMBEDDING,
            status = MediaFeatureStatus.FAILED,
            sourceRevision = "source",
            resultRevision = "model",
            attemptCount = 3,
            updatedAt = 10L,
            nextRetryAt = 1_000L,
            lastErrorCode = "failed"
        )

        val deferred = prepareFeatureWork(
            state,
            7L,
            MediaFeature.SEARCH_EMBEDDING,
            "source",
            "model",
            fullRefresh = false,
            now = 999L
        )
        val eligible = prepareFeatureWork(
            state,
            7L,
            MediaFeature.SEARCH_EMBEDDING,
            "source",
            "model",
            fullRefresh = false,
            now = 1_000L
        )

        assertEquals(false, deferred.shouldProcess)
        assertEquals(null, deferred.stateToPersist)
        assertEquals(true, eligible.shouldProcess)
        assertEquals(null, eligible.stateToPersist)
    }

    @Test
    fun successfulFaceScanWithNoRowsIsCurrent() {
        val state = MediaFeatureStateEntity(
            mediaId = 7L,
            feature = MediaFeature.FACE_DETECTION,
            status = MediaFeatureStatus.SUCCEEDED,
            sourceRevision = "source",
            resultRevision = "face-v2",
            updatedAt = 10L
        )

        assertEquals(
            true,
            isCurrentFaceDetection(state, "source", "face-v2", timestamp = 100L, headers = emptyList())
        )
        assertEquals(
            false,
            isCurrentFaceDetection(state, "changed", "face-v2", timestamp = 100L, headers = emptyList())
        )
        assertEquals(
            false,
            isCurrentFaceDetection(state, "source", "face-v3", timestamp = 100L, headers = emptyList())
        )
        assertEquals(
            false,
            isCurrentFaceDetection(
                state.copy(status = MediaFeatureStatus.PENDING),
                "source",
                "face-v2",
                timestamp = 100L,
                headers = emptyList()
            )
        )
    }

    @Test
    fun changedInputResetsFailedFeatureWithoutOverwritingActiveLease() {
        val failed = MediaFeatureStateEntity(
            mediaId = 7L,
            feature = MediaFeature.METADATA,
            status = MediaFeatureStatus.FAILED,
            sourceRevision = "old",
            resultRevision = "metadata-v1",
            attemptCount = 3,
            updatedAt = 10L,
            nextRetryAt = 10_000L,
            lastErrorCode = "failed"
        )
        val active = failed.copy(
            status = MediaFeatureStatus.PROCESSING,
            leaseOwner = "owner",
            leaseExpiresAt = 2_000L
        )

        val changed = prepareFeatureWork(
            failed,
            7L,
            MediaFeature.METADATA,
            "new",
            "metadata-v1",
            fullRefresh = false,
            now = 1_000L
        )
        val leased = prepareFeatureWork(
            active,
            7L,
            MediaFeature.METADATA,
            "new",
            "metadata-v1",
            fullRefresh = false,
            now = 1_000L
        )

        assertEquals(true, changed.shouldProcess)
        assertEquals(MediaFeatureStatus.PENDING, changed.stateToPersist?.status)
        assertEquals("new", changed.stateToPersist?.sourceRevision)
        assertEquals(0, changed.stateToPersist?.attemptCount)
        assertEquals(null, changed.stateToPersist?.nextRetryAt)
        assertEquals(false, leased.shouldProcess)
        assertEquals(null, leased.stateToPersist)
    }

    @Test
    fun progressAggregatesAcrossPhases() {
        val result = SmartScanPlan.aggregate(
            listOf(
                SmartScanProgress(10, 10, 8, 1, 1),
                SmartScanProgress(20, 5, 4, 0, 1)
            )
        )

        assertEquals(SmartScanProgress(30, 15, 12, 1, 2), result)
        assertEquals(50, result.percent)
    }

    @Test
    fun overallProgressWeightsEachStageEqually() {
        val phases = listOf(
            SmartScanPhaseEntity(
                runId = "run",
                phase = SmartScanPhase.SOURCE_SYNC,
                status = SmartScanStatus.SUCCEEDED,
                updatedAt = 1L,
                totalMedia = 10_000,
                processedMedia = 10_000,
                succeededMedia = 10_000
            ),
            SmartScanPhaseEntity(
                runId = "run",
                phase = SmartScanPhase.METADATA,
                status = SmartScanStatus.RUNNING,
                updatedAt = 2L,
                totalMedia = 100,
                processedMedia = 25,
                succeededMedia = 25
            ),
            SmartScanPhaseEntity(
                runId = "run",
                phase = SmartScanPhase.SEARCH_INDEX,
                status = SmartScanStatus.QUEUED,
                updatedAt = 3L
            )
        )

        assertEquals(0.4167f, SmartScanPlan.overallProgress(phases), 0.0001f)
    }

    @Test
    fun terminalStageCountsAsCompleteEvenWhenBlocked() {
        val phases = listOf(
            SmartScanPhaseEntity(
                runId = "run",
                phase = SmartScanPhase.SOURCE_SYNC,
                status = SmartScanStatus.SUCCEEDED,
                updatedAt = 1L
            ),
            SmartScanPhaseEntity(
                runId = "run",
                phase = SmartScanPhase.SEARCH_INDEX,
                status = SmartScanStatus.BLOCKED,
                updatedAt = 2L
            )
        )

        assertEquals(1f, SmartScanPlan.overallProgress(phases), 0f)
    }

    @Test
    fun etaUsesCurrentStageThroughput() {
        assertEquals(
            30_000L,
            SmartScanPlan.estimatedRemainingMillis(
                total = 100,
                processed = 25,
                startedAt = 10_000L,
                now = 20_000L
            )
        )
        assertEquals(null, SmartScanPlan.estimatedRemainingMillis(100, 0, 10_000L, 20_000L))
    }

    @Test
    fun largeLibrariesRequireForegroundProcessing() {
        assertEquals(false, SmartScanPlan.requiresForeground(1_000))
        assertEquals(true, SmartScanPlan.requiresForeground(1_001))
    }

    @Test
    fun sourceFingerprintIsStableAndDetectsAnyCloudRevisionChange() {
        val revisions = listOf("id-1:100:image/jpeg:path-a", "id-2:200:image/png:path-b")

        assertEquals(smartSourceFingerprint(revisions), smartSourceFingerprint(revisions.reversed()))
        assertEquals(
            false,
            smartSourceFingerprint(revisions) == smartSourceFingerprint(revisions.dropLast(1) + "id-2:201:image/png:path-b")
        )
    }

    @Test
    fun sourceSnapshotIncludesCloudIdentity() {
        val first = CloudMediaEntity(
            remoteId = "asset-a",
            providerType = ProviderType.IMMICH,
            serverConfigId = 1L,
            path = "same.jpg",
            mimeType = "image/jpeg",
            timestamp = 1_000L,
            size = 100L
        )
        val second = CloudMediaEntity(
            remoteId = "asset-b",
            providerType = ProviderType.IMMICH,
            serverConfigId = 1L,
            path = "same.jpg",
            mimeType = "image/jpeg",
            timestamp = 1_000L,
            size = 100L
        )

        assertEquals(false, smartSourceSnapshot("1/v", emptyList(), listOf(first)) ==
            smartSourceSnapshot("1/v", emptyList(), listOf(second)))
    }

    @Test
    fun binaryFloatVectorsRoundTripExactly() {
        val values = floatArrayOf(-1f, 0f, 0.25f, Float.MAX_VALUE)

        assertEquals(true, FloatVectorCodec.decode(FloatVectorCodec.encode(values)).contentEquals(values))
    }

    @Test
    fun validExistingSearchEmbeddingCanBeAdopted() {
        val embedding = ImageEmbedding(
            id = 7L,
            date = 100L,
            embedding = FloatArray(512) { 1f / sqrt(512f) }
        )

        assertEquals(true, canAdoptExistingSearchEmbedding(embedding, 100L, "clip-v2"))
        assertEquals(false, canAdoptExistingSearchEmbedding(embedding, 101L, "clip-v2"))
        assertEquals(true, canAdoptExistingSearchEmbedding(embedding.copy(resultRevision = "clip-v2"), 100L, "clip-v2"))
        assertEquals(false, canAdoptExistingSearchEmbedding(embedding.copy(resultRevision = "clip-v1"), 100L, "clip-v2"))
    }

    @Test
    fun invalidExistingSearchEmbeddingMustBeRebuilt() {
        assertEquals(
            false,
            canAdoptExistingSearchEmbedding(ImageEmbedding(1L, 1L, floatArrayOf()), 1L, "clip-v2")
        )
        assertEquals(
            false,
            canAdoptExistingSearchEmbedding(ImageEmbedding(1L, 1L, floatArrayOf(Float.NaN)), 1L, "clip-v2")
        )
        assertEquals(
            false,
            canAdoptExistingSearchEmbedding(ImageEmbedding(1L, 1L, floatArrayOf(0f, 0f)), 1L, "clip-v2")
        )
        assertEquals(
            false,
            canAdoptExistingSearchEmbedding(ImageEmbedding(1L, 1L, floatArrayOf(0.6f, 0.8f)), 1L, "clip-v2")
        )
    }

    @Test
    fun validExistingCategoryResultsCanBeAdopted() {
        val mappings = listOf(MediaCategory(mediaId = 7L, categoryId = 2L, similarityScore = 0.8f))

        assertEquals(true, canAdoptExistingCategoryResults(mappings, setOf(7L), "categories-v2"))
        assertEquals(false, canAdoptExistingCategoryResults(mappings, emptySet(), "categories-v2"))
        assertEquals(false, canAdoptExistingCategoryResults(emptyList(), setOf(7L), "categories-v2"))
        assertEquals(
            false,
            canAdoptExistingCategoryResults(
                mappings.map { it.copy(resultRevision = "categories-v1") },
                setOf(7L),
                "categories-v2"
            )
        )
    }

    @Test
    fun validExistingFaceResultsCanBeAdopted() {
        val faceEmbedding = FloatArray(512) { 1f / sqrt(512f) }
        val faceBytes = ByteBuffer.allocate(faceEmbedding.size * Float.SIZE_BYTES).apply {
            faceEmbedding.forEach(::putFloat)
        }.array()
        val face = DetectedFaceEntity(
            mediaId = 7L,
            embedding = faceBytes,
            left = 0.1f,
            top = 0.1f,
            right = 0.8f,
            bottom = 0.8f,
            confidence = 0.9f,
            timestamp = 100L
        )
        val ambiguousNoFaceMarker = DetectedFaceEntity(mediaId = 7L, timestamp = 100L)

        assertEquals(true, canAdoptExistingFaceResults(listOf(face), 100L, "face-v2"))
        assertEquals(false, canAdoptExistingFaceResults(listOf(ambiguousNoFaceMarker), 100L, "face-v2"))
        assertEquals(false, canAdoptExistingFaceResults(listOf(face), 101L, "face-v2"))
        assertEquals(false, canAdoptExistingFaceResults(emptyList(), 100L, "face-v2"))
        assertEquals(
            false,
            canAdoptExistingFaceResults(
                listOf(face.copy(resultRevision = "face-v1")),
                100L,
                "face-v2"
            )
        )
    }

    @Test
    fun invalidExistingFaceResultsMustBeRebuilt() {
        assertEquals(
            false,
            canAdoptExistingFaceResults(
                listOf(
                    DetectedFaceEntity(
                        mediaId = 7L,
                        left = Float.NaN,
                        timestamp = 100L
                    )
                ),
                100L,
                "face-v2"
            )
        )
    }

    @Test
    fun ignoredTimelineAlbumsAreExcludedFromSmartFeaturesUnlessEnabled() {
        val media = listOf(1L, 2L)

        assertEquals(
            listOf(1L),
            smartFeatureMediaPool(
                media,
                includeIgnoredAlbums = false,
                isIgnored = { it == 2L },
                isLocked = { false }
            )
        )
        assertEquals(
            media,
            smartFeatureMediaPool(
                media,
                includeIgnoredAlbums = true,
                isIgnored = { it == 2L },
                isLocked = { false }
            )
        )
    }

    @Test
    fun lockedAlbumsAreAlwaysExcludedFromSmartFeatures() {
        assertEquals(
            listOf(1L),
            smartFeatureMediaPool(
                listOf(1L, 2L),
                includeIgnoredAlbums = true,
                isIgnored = { false },
                isLocked = { it == 2L }
            )
        )
    }

    @Test
    fun terminalStatusPreservesPartialBlockedAndFailedMeaning() {
        assertEquals(
            SmartScanStatus.SUCCEEDED,
            SmartScanPlan.terminalStatus(listOf(SmartScanStatus.SUCCEEDED, SmartScanStatus.SUCCEEDED))
        )
        assertEquals(
            SmartScanStatus.BLOCKED,
            SmartScanPlan.terminalStatus(listOf(SmartScanStatus.BLOCKED))
        )
        assertEquals(
            SmartScanStatus.FAILED,
            SmartScanPlan.terminalStatus(listOf(SmartScanStatus.FAILED))
        )
        assertEquals(
            SmartScanStatus.PARTIAL,
            SmartScanPlan.terminalStatus(listOf(SmartScanStatus.SUCCEEDED, SmartScanStatus.BLOCKED))
        )
        assertEquals(
            SmartScanStatus.PARTIAL,
            SmartScanPlan.terminalStatus(listOf(SmartScanStatus.SUCCEEDED, SmartScanStatus.FAILED))
        )
    }
}
