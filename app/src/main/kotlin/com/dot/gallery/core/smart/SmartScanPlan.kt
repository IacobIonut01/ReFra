/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.smart

import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.data.data_source.SmartScanPhase
import com.dot.gallery.feature_node.data.data_source.SmartScanPhaseEntity
import com.dot.gallery.feature_node.data.data_source.SmartScanRunEntity
import com.dot.gallery.feature_node.data.data_source.SmartScanStatus

data class SmartScanProgress(
    val total: Int,
    val processed: Int,
    val succeeded: Int,
    val skipped: Int,
    val failed: Int
) {
    init {
        require(total >= 0 && processed in 0..total)
        require(succeeded >= 0 && skipped >= 0 && failed >= 0)
        require(succeeded + skipped + failed <= processed)
    }

    val percent: Int
        get() = if (total <= 0) 0 else ((processed.coerceAtMost(total) * 100L) / total).toInt()

    operator fun plus(other: SmartScanProgress) = SmartScanProgress(
        total = total + other.total,
        processed = processed + other.processed,
        succeeded = succeeded + other.succeeded,
        skipped = skipped + other.skipped,
        failed = failed + other.failed
    )

    companion object {
        val EMPTY = SmartScanProgress(0, 0, 0, 0, 0)
    }
}

object SmartScanPlan {
    const val FOREGROUND_MEDIA_THRESHOLD = 1_000

    val orderedPhases = listOf(
        SmartScanPhase.SOURCE_SYNC,
        SmartScanPhase.METADATA,
        SmartScanPhase.SEARCH_INDEX,
        SmartScanPhase.CATEGORY_CLASSIFICATION,
        SmartScanPhase.FACE_INDEX,
        SmartScanPhase.FACE_CLUSTER
    )

    fun expandedFeatures(features: Int): Int = SmartScanFeature.expandDependencies(features)

    fun shouldProcess(fullRefresh: Boolean, isCurrent: Boolean): Boolean = fullRefresh || !isCurrent

    fun phasesFor(features: Int): List<SmartScanPhase> {
        val expanded = expandedFeatures(features)
        return buildList {
            add(SmartScanPhase.SOURCE_SYNC)
            if (expanded and SmartScanFeature.METADATA.bit != 0) add(SmartScanPhase.METADATA)
            if (expanded and SmartScanFeature.EMBEDDINGS.bit != 0) add(SmartScanPhase.SEARCH_INDEX)
            if (expanded and SmartScanFeature.CATEGORIES.bit != 0) add(SmartScanPhase.CATEGORY_CLASSIFICATION)
            if (expanded and SmartScanFeature.PERSONS.bit != 0) {
                add(SmartScanPhase.FACE_INDEX)
                add(SmartScanPhase.FACE_CLUSTER)
            }
        }
    }

    fun executionBranches(features: Int): List<List<SmartScanPhase>> {
        val phases = phasesFor(features).toSet()
        return listOfNotNull(
            listOf(SmartScanPhase.METADATA).takeIf { SmartScanPhase.METADATA in phases },
            buildList {
                if (SmartScanPhase.SEARCH_INDEX in phases) add(SmartScanPhase.SEARCH_INDEX)
                if (SmartScanPhase.CATEGORY_CLASSIFICATION in phases) add(SmartScanPhase.CATEGORY_CLASSIFICATION)
            }.takeIf { it.isNotEmpty() },
            buildList {
                if (SmartScanPhase.FACE_INDEX in phases) add(SmartScanPhase.FACE_INDEX)
                if (SmartScanPhase.FACE_CLUSTER in phases) add(SmartScanPhase.FACE_CLUSTER)
            }.takeIf { it.isNotEmpty() }
        )
    }

    fun isAutomaticScanCurrent(
        mediaCurrent: Boolean,
        expectedProcessorRevisions: Map<SmartScanPhase, String>,
        latestProcessorRevisions: Map<SmartScanPhase, String>
    ): Boolean = mediaCurrent && expectedProcessorRevisions.all { (phase, revision) ->
        latestProcessorRevisions[phase] == revision
    }

    fun isPhaseCheckpointCurrent(
        expectedRevision: String,
        latestRevision: String?,
        currentSourceSnapshot: String?,
        phaseSourceSnapshot: String?
    ): Boolean = currentSourceSnapshot?.isNotEmpty() == true &&
        latestRevision == expectedRevision && phaseSourceSnapshot == currentSourceSnapshot

    fun aggregate(progress: Iterable<SmartScanProgress>): SmartScanProgress =
        progress.fold(SmartScanProgress.EMPTY, SmartScanProgress::plus)

    fun estimatedRemainingMillis(
        total: Int,
        processed: Int,
        startedAt: Long?,
        now: Long = System.currentTimeMillis()
    ): Long? {
        if (startedAt == null || total <= 0 || processed <= 0 || processed >= total || now <= startedAt) return null
        val elapsed = now - startedAt
        return (((total - processed).toDouble() * elapsed) / processed)
            .toLong()
            .coerceAtLeast(0L)
    }

    fun requiresForeground(mediaCount: Int): Boolean = mediaCount > FOREGROUND_MEDIA_THRESHOLD

    fun shouldShowRun(userVisible: Boolean, totalMedia: Int): Boolean = userVisible || totalMedia > 0

    fun runRequestsFeature(run: SmartScanRunEntity?, feature: SmartScanFeature): Boolean =
        run != null && run.requestedFeatures and feature.bit != 0

    fun shouldRequeueForRevision(
        status: SmartScanStatus,
        storedRevision: String,
        expectedRevision: String
    ): Boolean = status !in setOf(SmartScanStatus.QUEUED, SmartScanStatus.RUNNING) &&
        storedRevision != expectedRevision

    fun overallProgress(phases: List<SmartScanPhaseEntity>): Float {
        if (phases.isEmpty()) return 0f
        return phases.sumOf { phase ->
            when (phase.status) {
                SmartScanStatus.SUCCEEDED,
                SmartScanStatus.PARTIAL,
                SmartScanStatus.BLOCKED,
                SmartScanStatus.FAILED,
                SmartScanStatus.CANCELLED -> 1.0
                SmartScanStatus.RUNNING -> if (phase.totalMedia <= 0) 0.0
                else phase.processedMedia.coerceIn(0, phase.totalMedia).toDouble() / phase.totalMedia
                SmartScanStatus.QUEUED,
                SmartScanStatus.INTERRUPTED -> 0.0
            }
        }.div(phases.size).toFloat().coerceIn(0f, 1f)
    }

    fun terminalStatus(statuses: Iterable<SmartScanStatus>): SmartScanStatus {
        val values = statuses.toList()
        val completed = values.count { it == SmartScanStatus.SUCCEEDED }
        val partial = values.count { it == SmartScanStatus.PARTIAL }
        val blocked = values.count { it == SmartScanStatus.BLOCKED }
        val failed = values.count { it == SmartScanStatus.FAILED }
        return when {
            partial > 0 -> SmartScanStatus.PARTIAL
            failed > 0 && completed == 0 && blocked == 0 -> SmartScanStatus.FAILED
            blocked > 0 && completed == 0 && failed == 0 -> SmartScanStatus.BLOCKED
            failed > 0 || blocked > 0 -> SmartScanStatus.PARTIAL
            values.isNotEmpty() && completed == values.size -> SmartScanStatus.SUCCEEDED
            else -> SmartScanStatus.FAILED
        }
    }
}
