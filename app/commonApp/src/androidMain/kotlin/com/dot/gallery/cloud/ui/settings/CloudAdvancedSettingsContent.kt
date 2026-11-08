/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.EditOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.cloud.ui.components.CloudNote
import com.dot.gallery.cloud.ui.components.CloudPage
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_adv_cache
import com.dot.gallery.generated.resources.cloud_adv_clear_cache
import com.dot.gallery.generated.resources.cloud_adv_clear_cache_summary
import com.dot.gallery.generated.resources.cloud_adv_clearing_cache
import com.dot.gallery.generated.resources.cloud_adv_display
import com.dot.gallery.generated.resources.cloud_adv_experimental_note
import com.dot.gallery.generated.resources.cloud_adv_footnote
import com.dot.gallery.generated.resources.cloud_adv_readonly
import com.dot.gallery.generated.resources.cloud_adv_readonly_summary
import com.dot.gallery.generated.resources.cloud_adv_sync
import com.dot.gallery.generated.resources.cloud_adv_sync_deletions
import com.dot.gallery.generated.resources.cloud_adv_sync_deletions_desc
import com.dot.gallery.generated.resources.cloud_adv_troubleshooting
import com.dot.gallery.generated.resources.cloud_adv_verbose
import com.dot.gallery.generated.resources.cloud_adv_verbose_summary
import com.dot.gallery.generated.resources.cloud_advanced

/** Test tag of the experimental note under the Sync group. */
internal const val CloudAdvancedExperimentalNoteTag = "cloud-advanced-experimental-note"

/**
 * Stateless per-account advanced options on a [CloudPage] titled with the account name.
 * [CloudAdvancedSettingsScreen] loads the config and the global cache-clearing flag and
 * delegates here; the docs capture tier supplies a sample config and an in-memory busy flag.
 *
 * Sync remote deletions is experimental: that is said in a note under its group rather than in
 * the row's summary. Clearing the image cache is an action row that turns into a disabled
 * "Clearing…" row while [cacheClearing].
 */
@Composable
fun CloudAdvancedSettingsContent(
    config: CloudServerConfigEntity,
    cacheClearing: Boolean,
    onUpdateConfig: (CloudServerConfigEntity.() -> CloudServerConfigEntity) -> Unit,
    onClearImageCache: () -> Unit,
) {
    val troubleshooting = config.verboseLogging
    val readOnlyMode = config.readOnlyMode
    val syncDeletions = config.syncRemoteDeletions
    val troubleshootingHeader = stringResource(Res.string.cloud_adv_troubleshooting)
    val verboseTitle = stringResource(Res.string.cloud_adv_verbose)
    val verboseSummary = stringResource(Res.string.cloud_adv_verbose_summary)
    val displayHeader = stringResource(Res.string.cloud_adv_display)
    val readOnlyTitle = stringResource(Res.string.cloud_adv_readonly)
    val readOnlySummary = stringResource(Res.string.cloud_adv_readonly_summary)
    val syncHeader = stringResource(Res.string.cloud_adv_sync)
    val syncDeletionsTitle = stringResource(Res.string.cloud_adv_sync_deletions)
    val syncDeletionsSummary = stringResource(Res.string.cloud_adv_sync_deletions_desc)
    val cacheHeader = stringResource(Res.string.cloud_adv_cache)
    val clearingCacheTitle = stringResource(Res.string.cloud_adv_clearing_cache)
    val clearCacheTitle = stringResource(Res.string.cloud_adv_clear_cache)
    val clearCacheSummary = stringResource(Res.string.cloud_adv_clear_cache_summary)

    val upperGroups = remember(troubleshooting, readOnlyMode, syncDeletions, troubleshootingHeader, verboseTitle,
        verboseSummary, displayHeader, readOnlyTitle, readOnlySummary, syncHeader, syncDeletionsTitle, syncDeletionsSummary) {
        listOf(
            SettingsEntity.Header(title = troubleshootingHeader),
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.BugReport,
                title = verboseTitle,
                summary = verboseSummary,
                isChecked = troubleshooting,
                onCheck = { onUpdateConfig { copy(verboseLogging = it) } },
                screenPosition = Position.Alone
            ),
            SettingsEntity.Header(title = displayHeader),
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.EditOff,
                title = readOnlyTitle,
                summary = readOnlySummary,
                isChecked = readOnlyMode,
                onCheck = { onUpdateConfig { copy(readOnlyMode = it) } },
                screenPosition = Position.Alone
            ),
            SettingsEntity.Header(title = syncHeader),
            SettingsEntity.SwitchPreference(
                icon = Icons.Outlined.DeleteSweep,
                title = syncDeletionsTitle,
                summary = syncDeletionsSummary,
                isChecked = syncDeletions,
                onCheck = { onUpdateConfig { copy(syncRemoteDeletions = it) } },
                screenPosition = Position.Alone
            ),
        )
    }
    val cacheGroup = remember(cacheClearing, cacheHeader, clearingCacheTitle, clearCacheTitle, clearCacheSummary) {
        listOf(
            SettingsEntity.Header(title = cacheHeader),
            SettingsEntity.Preference(
                icon = Icons.Outlined.CleaningServices,
                title = if (cacheClearing) clearingCacheTitle else clearCacheTitle,
                summary = clearCacheSummary,
                enabled = !cacheClearing,
                onClick = { onClearImageCache() },
                screenPosition = Position.Alone
            ),
        )
    }

    val account = config.accountLabel()
    CloudPage(title = stringResource(Res.string.cloud_advanced), count = account) {
        items(upperGroups, key = { it.title }) { SettingsItem(item = it) }
        item(key = "experimental") {
            CloudNote(
                icon = Icons.Outlined.Science,
                text = stringResource(Res.string.cloud_adv_experimental_note),
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag(CloudAdvancedExperimentalNoteTag),
            )
        }
        // Keyed by position, not title: the action row's title changes while clearing.
        items(cacheGroup.size, key = { "cache_$it" }) { SettingsItem(item = cacheGroup[it]) }
        item(key = "footnote") {
            CloudNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.cloud_adv_footnote, account))
        }
    }
}
