/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.data.entity.MediaFeature
import com.dot.gallery.data.entity.MediaFeatureStateEntity
import com.dot.gallery.data.entity.MediaFeatureStatus
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.data.entity.SmartScanPhaseEntity
import com.dot.gallery.data.entity.SmartScanRunEntity
import com.dot.gallery.data.entity.SmartScanStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartScanDao {
    @Query(
        """
        SELECT * FROM smart_scan_runs
        WHERE status IN ('queued', 'running')
        ORDER BY requestedAt DESC
        """
    )
    fun observeActiveRuns(): Flow<List<SmartScanRunEntity>>

    @Query(
        """
        SELECT * FROM smart_scan_runs
        WHERE status IN ('queued', 'running')
        ORDER BY requestedAt DESC
        LIMIT 1
        """
    )
    fun observeActiveRun(): Flow<SmartScanRunEntity?>

    @Query("SELECT * FROM smart_scan_runs ORDER BY requestedAt DESC LIMIT 1")
    fun observeLatestRun(): Flow<SmartScanRunEntity?>

    @Query(
        """
        SELECT * FROM smart_scan_runs
        WHERE status IN ('failed', 'partial', 'blocked', 'interrupted')
        ORDER BY COALESCE(finishedAt, updatedAt) DESC
        LIMIT 1
        """
    )
    suspend fun getLatestRetryableRun(): SmartScanRunEntity?

    @Query("SELECT * FROM smart_scan_runs WHERE runId = :runId LIMIT 1")
    fun observeRun(runId: String): Flow<SmartScanRunEntity?>

    @Query("SELECT * FROM smart_scan_runs WHERE runId = :runId LIMIT 1")
    suspend fun getRun(runId: String): SmartScanRunEntity?

    @Query(
        """
        SELECT * FROM smart_scan_runs
        WHERE status IN ('queued', 'running')
        ORDER BY requestedAt DESC
        LIMIT 1
        """
    )
    suspend fun getActiveRun(): SmartScanRunEntity?

    @Query(
        """
        UPDATE smart_scan_runs
        SET requestedFeatures = requestedFeatures | :features,
            userVisible = CASE WHEN :userVisible = 1 THEN 1 ELSE userVisible END,
            fullRefresh = CASE WHEN :fullRefresh = 1 THEN 1 ELSE fullRefresh END,
            fullRefreshFeatures = fullRefreshFeatures | :fullRefreshFeatures,
            trigger = CASE WHEN :userVisible = 1 THEN 'manual' ELSE trigger END,
            updatedAt = :now
        WHERE runId = :runId AND status IN ('queued', 'running')
        """
    )
    suspend fun coalesceRequest(
        runId: String,
        features: Int,
        userVisible: Boolean,
        fullRefresh: Boolean,
        fullRefreshFeatures: Int,
        now: Long
    ): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET workId = :workId, updatedAt = :now
        WHERE runId = :runId AND status IN ('queued', 'running')
        """
    )
    suspend fun attachWork(runId: String, workId: String, now: Long): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET status = 'queued', workId = :workId, leaseOwner = NULL, leaseExpiresAt = NULL,
            updatedAt = :now
        WHERE runId = :runId AND status IN ('queued', 'running')
        """
    )
    suspend fun resetRunForRecovery(runId: String, workId: String, now: Long): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET status = 'queued', leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE runId = :runId AND status = 'running'
        """
    )
    suspend fun resetPhasesForRecovery(runId: String, now: Long): Int

    @Query(
        """
        UPDATE media_feature_state
        SET status = 'pending', leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE runId = :runId AND status = 'processing'
        """
    )
    suspend fun resetFeaturesForRecovery(runId: String, now: Long): Int

    @Transaction
    suspend fun prepareRunRecovery(runId: String, workId: String, now: Long): Boolean {
        if (resetRunForRecovery(runId, workId, now) != 1) return false
        resetPhasesForRecovery(runId, now)
        resetFeaturesForRecovery(runId, now)
        return true
    }

    @Query(
        """
        UPDATE smart_scan_runs
        SET currentPhase = :phase, sourceSnapshot = :sourceSnapshot, updatedAt = :now
        WHERE runId = :runId AND status = 'running' AND leaseOwner = :owner
        """
    )
    suspend fun updateCurrentPhase(
        runId: String,
        owner: String,
        phase: SmartScanPhase,
        sourceSnapshot: String,
        now: Long
    ): Int

    @Query("SELECT * FROM smart_scan_phases WHERE runId = :runId ORDER BY rowid")
    fun observePhases(runId: String): Flow<List<SmartScanPhaseEntity>>

    @Query("SELECT * FROM smart_scan_phases WHERE runId = :runId ORDER BY rowid")
    suspend fun getPhases(runId: String): List<SmartScanPhaseEntity>

    @Query("SELECT * FROM smart_scan_phases WHERE runId = :runId AND phase = :phase LIMIT 1")
    suspend fun getPhase(runId: String, phase: SmartScanPhase): SmartScanPhaseEntity?

    @Query(
        """
        SELECT * FROM smart_scan_phases
        WHERE phase = :phase AND status IN ('succeeded', 'blocked')
        ORDER BY COALESCE(finishedAt, updatedAt) DESC
        LIMIT 1
        """
    )
    suspend fun getLatestCurrentPhase(phase: SmartScanPhase): SmartScanPhaseEntity?

    @Query(
        """
        SELECT * FROM smart_scan_phases
        WHERE phase = :phase AND status = 'succeeded'
        ORDER BY COALESCE(finishedAt, updatedAt) DESC
        LIMIT 1
        """
    )
    suspend fun getLatestSuccessfulPhase(phase: SmartScanPhase): SmartScanPhaseEntity?

    @Query("SELECT * FROM media_feature_state WHERE mediaId = :mediaId AND feature = :feature LIMIT 1")
    suspend fun getFeatureState(mediaId: Long, feature: MediaFeature): MediaFeatureStateEntity?

    @Query("SELECT * FROM media_feature_state WHERE feature = :feature")
    suspend fun getFeatureStates(feature: MediaFeature): List<MediaFeatureStateEntity>

    @Query("SELECT * FROM media_feature_state WHERE mediaId = :mediaId")
    fun observeFeatureStates(mediaId: Long): Flow<List<MediaFeatureStateEntity>>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM media_feature_state WHERE feature = :feature")
    suspend fun getFeatureGeneration(feature: MediaFeature): Long

    @Upsert
    suspend fun upsertRun(run: SmartScanRunEntity)

    @Upsert
    suspend fun upsertPhase(phase: SmartScanPhaseEntity)

    @Upsert
    suspend fun upsertPhases(phases: List<SmartScanPhaseEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPhasesIfAbsent(phases: List<SmartScanPhaseEntity>): List<Long>

    @Query(
        """
        UPDATE smart_scan_phases
        SET status = 'queued', startedAt = NULL, finishedAt = NULL, updatedAt = :now,
            leaseOwner = NULL, leaseExpiresAt = NULL, totalMedia = 0, processedMedia = 0,
            succeededMedia = 0, skippedMedia = 0, failedMedia = 0, lastErrorCode = NULL
        WHERE runId = :runId AND phase = :phase
          AND status IN ('succeeded', 'partial', 'blocked', 'interrupted', 'failed', 'cancelled')
        """
    )
    suspend fun requeueTerminalPhase(runId: String, phase: SmartScanPhase, now: Long): Int

    @Transaction
    suspend fun coalesceOrCreate(
        run: SmartScanRunEntity,
        phases: List<SmartScanPhaseEntity>
    ): SmartScanScheduleResult {
        val active = getActiveRun()
        if (active != null) {
            coalesceRequest(
                active.runId,
                run.requestedFeatures,
                run.userVisible,
                run.fullRefresh,
                run.fullRefreshFeatures,
                run.updatedAt
            )
            val activePhases = phases.map { it.copy(runId = active.runId) }
            insertPhasesIfAbsent(activePhases)
            // A whole-library rebuild requeues every incoming phase; a scoped rebuild
            // requeues only the phases of the features it names. SOURCE_SYNC is left
            // alone for scoped requests — the run already synced its source.
            activePhases.forEach { incoming ->
                val rebuildsPhase = run.fullRefresh ||
                    SmartScanPlan.featureFor(incoming.phase)
                        ?.let { it.bit and run.fullRefreshFeatures != 0 } == true
                if (rebuildsPhase) {
                    requeueTerminalPhase(active.runId, incoming.phase, run.updatedAt)
                }
            }
            val promotedWorkId = run.workId.takeIf {
                run.userVisible && !active.userVisible && active.status == SmartScanStatus.QUEUED
            }
            val workId = promotedWorkId?.also {
                attachWork(active.runId, it, run.updatedAt)
            } ?: active.workId ?: run.workId?.also {
                attachWork(active.runId, it, run.updatedAt)
            }
            return SmartScanScheduleResult(active.runId, created = false, workId = workId)
        }
        upsertRun(run)
        insertPhasesIfAbsent(phases)
        return SmartScanScheduleResult(run.runId, created = true, workId = run.workId)
    }

    @Upsert
    suspend fun upsertFeatureState(state: MediaFeatureStateEntity)

    @Upsert
    suspend fun upsertFeatureStates(states: List<MediaFeatureStateEntity>)

    @Transaction
    suspend fun upsertRunWithPhases(run: SmartScanRunEntity, phases: List<SmartScanPhaseEntity>) {
        upsertRun(run)
        upsertPhases(phases)
    }

    @Query(
        """
        UPDATE smart_scan_runs
        SET status = 'running',
            startedAt = COALESCE(startedAt, :now),
            updatedAt = :now,
            leaseOwner = :owner,
            leaseExpiresAt = :leaseExpiresAt
        WHERE runId = :runId
          AND (
            status = 'queued'
            OR (status = 'running' AND (
                leaseOwner = :owner OR leaseExpiresAt IS NULL OR leaseExpiresAt <= :now
            ))
          )
        """
    )
    suspend fun claimRunLease(runId: String, owner: String, now: Long, leaseExpiresAt: Long): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET updatedAt = :now, leaseExpiresAt = :leaseExpiresAt
        WHERE runId = :runId AND status = 'running' AND leaseOwner = :owner
        """
    )
    suspend fun renewRunLease(runId: String, owner: String, now: Long, leaseExpiresAt: Long): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET status = 'queued', leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE status = 'running' AND leaseExpiresAt IS NOT NULL AND leaseExpiresAt <= :now
        """
    )
    suspend fun recoverExpiredRunLeases(now: Long): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET status = 'queued', leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE runId = :runId AND status = 'running' AND leaseOwner = :owner
        """
    )
    suspend fun releaseRunLease(runId: String, owner: String, now: Long): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET status = 'running',
            startedAt = :now,
            updatedAt = :now,
            leaseOwner = :owner,
            leaseExpiresAt = :leaseExpiresAt,
            processorRevision = :processorRevision,
            attemptCount = attemptCount + 1
        WHERE runId = :runId AND phase = :phase
          AND (
            status = 'queued'
            OR (status = 'running' AND (
                leaseOwner = :owner OR leaseExpiresAt IS NULL OR leaseExpiresAt <= :now
            ))
          )
        """
    )
    suspend fun claimPhaseLease(
        runId: String,
        phase: SmartScanPhase,
        owner: String,
        now: Long,
        leaseExpiresAt: Long,
        processorRevision: String
    ): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET updatedAt = :now, leaseExpiresAt = :leaseExpiresAt
        WHERE runId = :runId AND phase = :phase
          AND status = 'running' AND leaseOwner = :owner
        """
    )
    suspend fun renewPhaseLease(
        runId: String,
        phase: SmartScanPhase,
        owner: String,
        now: Long,
        leaseExpiresAt: Long
    ): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET status = 'queued', leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE status = 'running' AND leaseExpiresAt IS NOT NULL AND leaseExpiresAt <= :now
        """
    )
    suspend fun recoverExpiredPhaseLeases(now: Long): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET status = 'queued', leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE runId = :runId AND phase = :phase AND status = 'running' AND leaseOwner = :owner
        """
    )
    suspend fun releasePhaseLease(
        runId: String,
        phase: SmartScanPhase,
        owner: String,
        now: Long
    ): Int

    @Query(
        """
        SELECT * FROM media_feature_state
        WHERE feature = :feature
          AND (
            status = 'pending'
            OR (status = 'failed' AND (nextRetryAt IS NULL OR nextRetryAt <= :now))
            OR (status = 'processing' AND leaseExpiresAt IS NOT NULL AND leaseExpiresAt <= :now)
          )
        ORDER BY updatedAt, mediaId
        LIMIT :limit
        """
    )
    suspend fun getFeatureClaimCandidates(
        feature: MediaFeature,
        now: Long,
        limit: Int
    ): List<MediaFeatureStateEntity>

    @Query(
        """
        UPDATE media_feature_state
        SET status = 'processing',
            attemptCount = attemptCount + 1,
            updatedAt = :now,
            lastAttemptAt = :now,
            leaseOwner = :owner,
            leaseExpiresAt = :leaseExpiresAt,
            runId = :runId,
            lastErrorCode = NULL
        WHERE mediaId = :mediaId AND feature = :feature
          AND (
            status = 'pending'
            OR (status = 'failed' AND (nextRetryAt IS NULL OR nextRetryAt <= :now))
            OR (status = 'processing' AND leaseExpiresAt IS NOT NULL AND leaseExpiresAt <= :now)
          )
        """
    )
    suspend fun claimFeatureLease(
        mediaId: Long,
        feature: MediaFeature,
        runId: String,
        owner: String,
        now: Long,
        leaseExpiresAt: Long
    ): Int

    @Query(
        """
        UPDATE media_feature_state
        SET updatedAt = :now, leaseExpiresAt = :leaseExpiresAt
        WHERE mediaId = :mediaId AND feature = :feature
          AND status = 'processing' AND leaseOwner = :owner
        """
    )
    suspend fun renewFeatureLease(
        mediaId: Long,
        feature: MediaFeature,
        owner: String,
        now: Long,
        leaseExpiresAt: Long
    ): Int

    @Query(
        """
        UPDATE media_feature_state
        SET status = 'pending', leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE status = 'processing' AND leaseExpiresAt IS NOT NULL AND leaseExpiresAt <= :now
        """
    )
    suspend fun recoverExpiredFeatureLeases(now: Long): Int

    @Query(
        """
        UPDATE media_feature_state
        SET status = 'pending', leaseOwner = NULL, leaseExpiresAt = NULL, updatedAt = :now
        WHERE runId = :runId AND status = 'processing' AND leaseOwner LIKE :ownerPrefix || ':%'
        """
    )
    suspend fun releaseFeatureLeases(runId: String, ownerPrefix: String, now: Long): Int

    @Query(
        """
        UPDATE media_feature_state
        SET status = :status,
            resultRevision = :resultRevision,
            updatedAt = :now,
            nextRetryAt = :nextRetryAt,
            leaseOwner = NULL,
            leaseExpiresAt = NULL,
            lastErrorCode = :lastErrorCode
        WHERE mediaId = :mediaId AND feature = :feature
          AND status = 'processing' AND leaseOwner = :owner
        """
    )
    suspend fun finishFeature(
        mediaId: Long,
        feature: MediaFeature,
        owner: String,
        status: MediaFeatureStatus,
        resultRevision: String,
        now: Long,
        nextRetryAt: Long? = null,
        lastErrorCode: String? = null
    ): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET totalMedia = :totalMedia,
            processedMedia = :processedMedia,
            succeededMedia = :succeededMedia,
            skippedMedia = :skippedMedia,
            failedMedia = :failedMedia,
            updatedAt = :now
        WHERE runId = :runId
        """
    )
    suspend fun updateRunSummary(
        runId: String,
        totalMedia: Int,
        processedMedia: Int,
        succeededMedia: Int,
        skippedMedia: Int,
        failedMedia: Int,
        now: Long
    ): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET totalMedia = :totalMedia,
            processedMedia = :processedMedia,
            succeededMedia = :succeededMedia,
            skippedMedia = :skippedMedia,
            failedMedia = :failedMedia,
            updatedAt = :now
        WHERE runId = :runId AND phase = :phase
        """
    )
    suspend fun updatePhaseSummary(
        runId: String,
        phase: SmartScanPhase,
        totalMedia: Int,
        processedMedia: Int,
        succeededMedia: Int,
        skippedMedia: Int,
        failedMedia: Int,
        now: Long
    ): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET status = :status,
            finishedAt = :finishedAt,
            updatedAt = :finishedAt,
            leaseOwner = NULL,
            leaseExpiresAt = NULL,
            lastErrorCode = :lastErrorCode
        WHERE runId = :runId AND status = 'running' AND leaseOwner = :owner
          AND NOT EXISTS (
              SELECT 1 FROM smart_scan_phases
              WHERE runId = :runId AND status IN ('queued', 'running')
          )
        """
    )
    suspend fun finishRunIfComplete(
        runId: String,
        owner: String,
        status: SmartScanStatus,
        finishedAt: Long,
        lastErrorCode: String? = null
    ): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET status = :status,
            finishedAt = :finishedAt,
            updatedAt = :finishedAt,
            leaseOwner = NULL,
            leaseExpiresAt = NULL,
            lastErrorCode = :lastErrorCode
        WHERE runId = :runId AND status IN ('queued', 'running')
        """
    )
    suspend fun finishRun(
        runId: String,
        status: SmartScanStatus,
        finishedAt: Long,
        lastErrorCode: String? = null
    ): Int

    @Query(
        """
        UPDATE smart_scan_runs
        SET status = :status,
            finishedAt = :finishedAt,
            updatedAt = :finishedAt,
            leaseOwner = NULL,
            leaseExpiresAt = NULL,
            lastErrorCode = :lastErrorCode
        WHERE runId = :runId AND status = 'running' AND leaseOwner = :owner
        """
    )
    suspend fun finishRunOwned(
        runId: String,
        owner: String,
        status: SmartScanStatus,
        finishedAt: Long,
        lastErrorCode: String? = null
    ): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET status = :status,
            finishedAt = :finishedAt,
            updatedAt = :finishedAt,
            leaseOwner = NULL,
            leaseExpiresAt = NULL,
            lastErrorCode = :lastErrorCode
        WHERE runId = :runId AND phase = :phase
          AND status = 'running' AND leaseOwner = :owner
        """
    )
    suspend fun finishPhaseOwned(
        runId: String,
        phase: SmartScanPhase,
        owner: String,
        status: SmartScanStatus,
        finishedAt: Long,
        lastErrorCode: String? = null
    ): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET status = :status,
            finishedAt = :finishedAt,
            updatedAt = :finishedAt,
            leaseOwner = NULL,
            leaseExpiresAt = NULL,
            lastErrorCode = :lastErrorCode
        WHERE runId = :runId AND phase = :phase AND status IN ('queued', 'running')
        """
    )
    suspend fun finishPhase(
        runId: String,
        phase: SmartScanPhase,
        status: SmartScanStatus,
        finishedAt: Long,
        lastErrorCode: String? = null
    ): Int

    @Query(
        """
        UPDATE smart_scan_phases
        SET status = 'cancelled', finishedAt = :now, updatedAt = :now,
            leaseOwner = NULL, leaseExpiresAt = NULL, lastErrorCode = 'cancelled'
        WHERE runId = :runId AND status IN ('queued', 'running')
        """
    )
    suspend fun cancelPhases(runId: String, now: Long): Int

    @Query(
        """
        UPDATE media_feature_state
        SET status = 'pending', updatedAt = :now, leaseOwner = NULL, leaseExpiresAt = NULL, runId = NULL
        WHERE runId = :runId AND status = 'processing'
        """
    )
    suspend fun releaseRunFeatureLeases(runId: String, now: Long): Int

    @Transaction
    suspend fun cancelRun(runId: String, now: Long): SmartScanRunEntity? {
        val run = getRun(runId) ?: return null
        releaseRunFeatureLeases(runId, now)
        finishRun(runId, SmartScanStatus.CANCELLED, now, "cancelled")
        cancelPhases(runId, now)
        return run
    }

    @Query(
        """
        DELETE FROM smart_scan_runs
        WHERE runId IN (
            SELECT runId FROM smart_scan_runs
            WHERE status IN ('succeeded', 'partial', 'blocked', 'interrupted', 'failed', 'cancelled')
            ORDER BY COALESCE(finishedAt, updatedAt) DESC
            LIMIT :maxDelete OFFSET :keepLatest
        )
        """
    )
    suspend fun pruneTerminalRuns(keepLatest: Int, maxDelete: Int): Int
}
