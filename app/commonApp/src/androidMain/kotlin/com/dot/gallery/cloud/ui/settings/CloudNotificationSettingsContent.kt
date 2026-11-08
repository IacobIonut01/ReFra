/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.settings

import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.cloud.ui.components.CloudNote
import com.dot.gallery.cloud.ui.components.CloudPage
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.data.entity.CloudServerConfigEntity
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_notif_background_backup
import com.dot.gallery.generated.resources.cloud_notif_detail_progress
import com.dot.gallery.generated.resources.cloud_notif_detail_progress_summary
import com.dot.gallery.generated.resources.cloud_notif_failures
import com.dot.gallery.generated.resources.cloud_notif_failures_summary
import com.dot.gallery.generated.resources.cloud_notif_footnote
import com.dot.gallery.generated.resources.cloud_notif_total_progress
import com.dot.gallery.generated.resources.cloud_notif_total_progress_summary
import com.dot.gallery.generated.resources.cloud_notifications


/**
 * Stateless per-account notification switches on a [CloudPage] titled with the account name. [CloudNotificationSettingsScreen]
 * loads the config and delegates here; the docs capture tier supplies a sample.
 */
@Composable
fun CloudNotificationSettingsContent(
    config: CloudServerConfigEntity,
    onUpdateConfig: (CloudServerConfigEntity.() -> CloudServerConfigEntity) -> Unit,
) {
    val showTotalProgress = config.showBackupTotalProgress
    val showDetailProgress = config.showBackupDetailProgress
    val notifyFailures = config.notifyBackupFailures
    val backgroundBackupHeader = stringResource(Res.string.cloud_notif_background_backup)
    val totalProgressTitle = stringResource(Res.string.cloud_notif_total_progress)
    val totalProgressSummary = stringResource(Res.string.cloud_notif_total_progress_summary)
    val detailProgressTitle = stringResource(Res.string.cloud_notif_detail_progress)
    val detailProgressSummary = stringResource(Res.string.cloud_notif_detail_progress_summary)
    val failuresTitle = stringResource(Res.string.cloud_notif_failures)
    val failuresSummary = stringResource(Res.string.cloud_notif_failures_summary)
    val resourceStrings = listOf(
        backgroundBackupHeader,
        totalProgressTitle,
        totalProgressSummary,
        detailProgressTitle,
        detailProgressSummary,
        failuresTitle,
        failuresSummary,
    )

    val settingsList = remember(
        showTotalProgress,
        showDetailProgress,
        notifyFailures,
        resourceStrings,
    ) {
        buildList {
            add(SettingsEntity.Header(title = backgroundBackupHeader))
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.Timelapse,
                    title = totalProgressTitle,
                    summary = totalProgressSummary,
                    isChecked = showTotalProgress,
                    onCheck = { onUpdateConfig { copy(showBackupTotalProgress = it) } },
                    screenPosition = Position.Top
                )
            )
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.AutoMirrored.Outlined.ListAlt,
                    title = detailProgressTitle,
                    summary = detailProgressSummary,
                    isChecked = showDetailProgress,
                    onCheck = { onUpdateConfig { copy(showBackupDetailProgress = it) } },
                    screenPosition = Position.Middle
                )
            )
            add(
                SettingsEntity.SwitchPreference(
                    icon = Icons.Outlined.ErrorOutline,
                    title = failuresTitle,
                    summary = failuresSummary,
                    isChecked = notifyFailures,
                    onCheck = { onUpdateConfig { copy(notifyBackupFailures = it) } },
                    screenPosition = Position.Bottom
                )
            )
        }.toMutableStateList()
    }

    CloudPage(
        title = stringResource(Res.string.cloud_notifications),
        count = config.accountLabel(),
    ) {
        items(settingsList, key = { it.title }) { SettingsItem(item = it) }
        item(key = "footnote") {
            CloudNote(icon = Icons.Outlined.Info, text = stringResource(Res.string.cloud_notif_footnote))
        }
    }
}

/** The account's display name, or its provider's name when the user left it blank. */
internal fun CloudServerConfigEntity.accountLabel(): String = displayName.ifBlank { providerType.displayName }
