/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.runtime.Composable
import com.dot.gallery.core.logging.AreaGroup
import com.dot.gallery.core.logging.parseLogSessionTs
import com.dot.gallery.domain.date.formatEpochSec
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.dev_crash_logs
import com.dot.gallery.feature.settings.generated.resources.dev_errors_count
import com.dot.gallery.feature.settings.generated.resources.dev_group_counts
import com.dot.gallery.feature.settings.generated.resources.dev_session_header
import com.dot.gallery.feature.settings.generated.resources.dev_ungrouped
import com.dot.gallery.feature.settings.generated.resources.dev_warnings_count

@Composable
internal fun sessionLabel(sessionId: String?): String = if (sessionId == null) {
    stringResource(Res.string.dev_crash_logs)
} else {
    val parsed = parseLogSessionTs(sessionId)
        ?.let { (it / 1000).formatEpochSec("MMM d, HH:mm") }
        ?: sessionId
    stringResource(Res.string.dev_session_header, parsed)
}

@Composable
internal fun areaLabel(group: AreaGroup): String = when (group.kind) {
    AreaGroup.Kind.UNGROUPED -> stringResource(Res.string.dev_ungrouped)
    AreaGroup.Kind.SCREEN -> group.id
        .removeSuffix("Screen")
        .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")

    else -> group.id
}

@Composable
internal fun groupSummary(group: AreaGroup): String {
    val parts = buildList {
        if (group.errorCount > 0) {
            add(stringResource(Res.string.dev_errors_count, group.errorCount))
        }
        if (group.warnCount > 0) {
            add(stringResource(Res.string.dev_warnings_count, group.warnCount))
        }
        add(stringResource(Res.string.dev_group_counts, group.entries.size))
    }
    return parts.joinToString(" · ")
}

/** Portable `%.1f` byte-size formatter — used by developer diagnostics and private folder stats. */
fun formatBytes(bytes: Long): String {
    fun tenths(value: Float): String {
        val t = (value * 10).toLong()
        return "${t / 10}.${t % 10}"
    }
    return when {
        bytes >= 1_073_741_824L -> "${tenths(bytes / 1_073_741_824f)} GB"
        bytes >= 1_048_576L -> "${tenths(bytes / 1_048_576f)} MB"
        bytes >= 1_024L -> "${tenths(bytes / 1_024f)} KB"
        else -> "$bytes B"
    }
}
