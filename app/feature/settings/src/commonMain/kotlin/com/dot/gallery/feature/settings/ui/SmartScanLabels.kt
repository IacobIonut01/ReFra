/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Composable
import com.dot.gallery.data.entity.SmartScanPhase
import com.dot.gallery.data.entity.SmartScanStatus
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.smart_scan_eta_hours
import com.dot.gallery.feature.settings.generated.resources.smart_scan_eta_minutes
import com.dot.gallery.feature.settings.generated.resources.smart_scan_eta_under_minute
import com.dot.gallery.feature.settings.generated.resources.smart_scan_phase_categories
import com.dot.gallery.feature.settings.generated.resources.smart_scan_phase_metadata
import com.dot.gallery.feature.settings.generated.resources.smart_scan_phase_people
import com.dot.gallery.feature.settings.generated.resources.smart_scan_phase_people_grouping
import com.dot.gallery.feature.settings.generated.resources.smart_scan_phase_search_index
import com.dot.gallery.feature.settings.generated.resources.smart_scan_phase_source_sync
import com.dot.gallery.feature.settings.generated.resources.smart_scan_phase_text
import com.dot.gallery.feature.settings.generated.resources.smart_scan_status_blocked
import com.dot.gallery.feature.settings.generated.resources.smart_scan_status_cancelled
import com.dot.gallery.feature.settings.generated.resources.smart_scan_status_complete
import com.dot.gallery.feature.settings.generated.resources.smart_scan_status_failed
import com.dot.gallery.feature.settings.generated.resources.smart_scan_status_interrupted
import com.dot.gallery.feature.settings.generated.resources.smart_scan_status_partial
import com.dot.gallery.feature.settings.generated.resources.smart_scan_status_queued
import com.dot.gallery.feature.settings.generated.resources.smart_scan_status_running

@Composable
fun smartScanEtaLabel(estimatedRemainingMillis: Long): String {
    val totalMinutes = (estimatedRemainingMillis / 60_000L).coerceAtLeast(0L)
    if (totalMinutes < 1) return stringResource(Res.string.smart_scan_eta_under_minute)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(Res.string.smart_scan_eta_hours, hours, minutes)
    } else {
        stringResource(Res.string.smart_scan_eta_minutes, totalMinutes)
    }
}

@Composable
fun SmartScanPhase.label(): String = stringResource(
    when (this) {
        SmartScanPhase.SOURCE_SYNC -> Res.string.smart_scan_phase_source_sync
        SmartScanPhase.METADATA -> Res.string.smart_scan_phase_metadata
        SmartScanPhase.SEARCH_INDEX -> Res.string.smart_scan_phase_search_index
        SmartScanPhase.CATEGORY_CLASSIFICATION -> Res.string.smart_scan_phase_categories
        SmartScanPhase.FACE_INDEX -> Res.string.smart_scan_phase_people
        SmartScanPhase.FACE_CLUSTER -> Res.string.smart_scan_phase_people_grouping
        SmartScanPhase.OCR_INDEX -> Res.string.smart_scan_phase_text
    }
)

@Composable
fun SmartScanStatus.label(): String = stringResource(
    when (this) {
        SmartScanStatus.QUEUED -> Res.string.smart_scan_status_queued
        SmartScanStatus.RUNNING -> Res.string.smart_scan_status_running
        SmartScanStatus.SUCCEEDED -> Res.string.smart_scan_status_complete
        SmartScanStatus.PARTIAL -> Res.string.smart_scan_status_partial
        SmartScanStatus.BLOCKED -> Res.string.smart_scan_status_blocked
        SmartScanStatus.INTERRUPTED -> Res.string.smart_scan_status_interrupted
        SmartScanStatus.FAILED -> Res.string.smart_scan_status_failed
        SmartScanStatus.CANCELLED -> Res.string.smart_scan_status_cancelled
    }
)
