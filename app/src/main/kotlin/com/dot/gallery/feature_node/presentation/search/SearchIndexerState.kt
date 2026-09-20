package com.dot.gallery.feature_node.presentation.search

import com.dot.gallery.core.smart.SmartScanPlan
import com.dot.gallery.feature_node.data.data_source.SmartScanFeature
import com.dot.gallery.feature_node.data.data_source.SmartScanPhase
import com.dot.gallery.feature_node.data.data_source.SmartScanPhaseEntity
import com.dot.gallery.feature_node.data.data_source.SmartScanRunEntity
import com.dot.gallery.feature_node.data.data_source.SmartScanStatus

data class SearchIndexerState(
    val isIndexing: Boolean = false,
    val status: SmartScanStatus? = null,
    val progress: Float = 0f,
    val processed: Int = 0,
    val total: Int = 0,
    val stageNumber: Int = 0,
    val stageCount: Int = 0,
    val estimatedRemainingMillis: Long? = null
)

internal fun deriveSearchIndexerState(
    run: SmartScanRunEntity?,
    phases: List<SmartScanPhaseEntity>
): SearchIndexerState {
    if (run == null || !SmartScanPlan.runRequestsFeature(run, SmartScanFeature.EMBEDDINGS)) {
        return SearchIndexerState()
    }
    val searchPhase = phases.firstOrNull { it.phase == SmartScanPhase.SEARCH_INDEX }
        ?.takeIf {
            it.status == SmartScanStatus.RUNNING &&
                it.totalMedia > 0 && it.processedMedia < it.totalMedia
        } ?: return SearchIndexerState()
    val planned = SmartScanPlan.phasesFor(run.requestedFeatures)
    return SearchIndexerState(
        isIndexing = true,
        status = searchPhase.status,
        progress = (searchPhase.processedMedia.toFloat() / searchPhase.totalMedia).coerceIn(0f, 1f),
        processed = searchPhase.processedMedia,
        total = searchPhase.totalMedia,
        stageNumber = planned.indexOf(SmartScanPhase.SEARCH_INDEX) + 1,
        stageCount = planned.size,
        estimatedRemainingMillis = SmartScanPlan.estimatedRemainingMillis(
            searchPhase.totalMedia,
            searchPhase.processedMedia,
            searchPhase.startedAt
        )
    )
}
