/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.CODE_SCAN_GROUP_LINEAR
import com.dot.gallery.domain.settings.CODE_SCAN_GROUP_MATRIX
import com.dot.gallery.domain.settings.CODE_SCAN_GROUP_PRODUCT
import com.dot.gallery.domain.settings.CODE_SCAN_GROUP_QR
import com.dot.gallery.domain.settings.CODE_SCAN_MODE_AUTO
import com.dot.gallery.domain.settings.CODE_SCAN_MODE_ON_TAP
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.code_scan_description
import com.dot.gallery.feature.settings.generated.resources.code_scan_header
import com.dot.gallery.feature.settings.generated.resources.code_scan_mode_auto
import com.dot.gallery.feature.settings.generated.resources.code_scan_mode_auto_summary
import com.dot.gallery.feature.settings.generated.resources.code_scan_mode_description
import com.dot.gallery.feature.settings.generated.resources.code_scan_mode_on_tap
import com.dot.gallery.feature.settings.generated.resources.code_scan_mode_on_tap_summary
import com.dot.gallery.feature.settings.generated.resources.code_scan_mode_title
import com.dot.gallery.feature.settings.generated.resources.code_scan_summary
import com.dot.gallery.feature.settings.generated.resources.code_scan_title
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_description
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_linear
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_linear_summary
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_matrix
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_matrix_summary
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_none
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_product
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_product_summary
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_qr
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_qr_summary
import com.dot.gallery.feature.settings.generated.resources.code_scan_types_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val DETAIL_CODE_SCAN = "code_scan"
internal const val DETAIL_CODE_SCAN_MODE = "code_scan_mode"
internal const val DETAIL_CODE_SCAN_TYPES = "code_scan_types"

private data class CodeGroup(val key: String, val title: StringResource, val summary: StringResource)

private val codeGroups = listOf(
    CodeGroup(CODE_SCAN_GROUP_QR, Res.string.code_scan_types_qr, Res.string.code_scan_types_qr_summary),
    CodeGroup(CODE_SCAN_GROUP_MATRIX, Res.string.code_scan_types_matrix, Res.string.code_scan_types_matrix_summary),
    CodeGroup(CODE_SCAN_GROUP_PRODUCT, Res.string.code_scan_types_product, Res.string.code_scan_types_product_summary),
    CodeGroup(CODE_SCAN_GROUP_LINEAR, Res.string.code_scan_types_linear, Res.string.code_scan_types_linear_summary),
)

/**
 * The "Codes in photos" section of Settings > Media viewer: the master switch, then When to look
 * and Code types while it is on.
 */
@Composable
internal fun rememberCodeScanSettings(onDetailClick: (String) -> Unit): List<SettingsEntity> {
    var enabled by rememberPreference(SettingsKeys.codeScanEnabled)
    val mode by rememberPreference(SettingsKeys.codeScanMode)
    val groups by rememberPreference(SettingsKeys.codeScanFormats)

    val header = stringResource(Res.string.code_scan_header)
    val title = stringResource(Res.string.code_scan_title)
    val summary = stringResource(Res.string.code_scan_summary)
    val modeTitle = stringResource(Res.string.code_scan_mode_title)
    val modeSummary = stringResource(
        if (mode == CODE_SCAN_MODE_ON_TAP) Res.string.code_scan_mode_on_tap else Res.string.code_scan_mode_auto
    )
    val typesTitle = stringResource(Res.string.code_scan_types_title)
    val groupTitles = codeGroups.map { it.key to stringResource(it.title) }
    val noneLabel = stringResource(Res.string.code_scan_types_none)
    val typesSummary = groupTitles.filter { it.first in groups }.joinToString(", ") { it.second }.ifEmpty { noneLabel }

    return remember(enabled, header, title, summary, modeTitle, modeSummary, typesTitle, typesSummary) {
        buildList {
            add(SettingsEntity.Header(title = header))
            add(
                SettingsEntity.SwitchPreference(
                    title = title,
                    icon = Icons.Outlined.QrCodeScanner,
                    summary = summary,
                    isChecked = enabled,
                    onCheck = { enabled = it },
                    onClick = { onDetailClick(DETAIL_CODE_SCAN) },
                    screenPosition = if (enabled) Position.Top else Position.Alone,
                )
            )
            if (enabled) {
                add(
                    SettingsEntity.Preference(
                        title = modeTitle,
                        icon = Icons.Outlined.Tune,
                        summary = modeSummary,
                        onClick = { onDetailClick(DETAIL_CODE_SCAN_MODE) },
                        screenPosition = Position.Middle,
                    )
                )
                add(
                    SettingsEntity.Preference(
                        title = typesTitle,
                        icon = Icons.Outlined.QrCode2,
                        summary = typesSummary,
                        onClick = { onDetailClick(DETAIL_CODE_SCAN_TYPES) },
                        screenPosition = Position.Bottom,
                    )
                )
            }
        }
    }
}

/** Detail screens for the section; returns false for keys it does not own. */
@Composable
internal fun CodeScanSettingsDetail(detailKey: String?): Boolean {
    when (detailKey) {
        DETAIL_CODE_SCAN -> {
            var enabled by rememberPreference(SettingsKeys.codeScanEnabled)
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.code_scan_title),
                isChecked = enabled,
                onCheckedChange = { enabled = it },
                description = stringResource(Res.string.code_scan_description),
            )
        }
        DETAIL_CODE_SCAN_MODE -> {
            var mode by rememberPreference(SettingsKeys.codeScanMode)
            val autoLabel = stringResource(Res.string.code_scan_mode_auto)
            val autoSummary = stringResource(Res.string.code_scan_mode_auto_summary)
            val tapLabel = stringResource(Res.string.code_scan_mode_on_tap)
            val tapSummary = stringResource(Res.string.code_scan_mode_on_tap_summary)
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.code_scan_mode_title),
                description = stringResource(Res.string.code_scan_mode_description),
                options = listOf(
                    PreferenceOption(CODE_SCAN_MODE_AUTO, autoLabel, mode != CODE_SCAN_MODE_ON_TAP, autoSummary),
                    PreferenceOption(CODE_SCAN_MODE_ON_TAP, tapLabel, mode == CODE_SCAN_MODE_ON_TAP, tapSummary),
                ),
                onOptionSelected = { mode = it },
            )
        }
        DETAIL_CODE_SCAN_TYPES -> {
            var groups by rememberPreference(SettingsKeys.codeScanFormats)
            val rows = codeGroups.mapIndexed { index, group ->
                SettingsEntity.SwitchPreference(
                    title = stringResource(group.title),
                    summary = stringResource(group.summary),
                    isChecked = group.key in groups,
                    onCheck = { checked -> groups = if (checked) groups + group.key else groups - group.key },
                    screenPosition = when (index) {
                        0 -> Position.Top
                        codeGroups.lastIndex -> Position.Bottom
                        else -> Position.Middle
                    },
                )
            }
            ChooserPreferenceDetailScreen<String>(
                title = stringResource(Res.string.code_scan_types_title),
                description = stringResource(Res.string.code_scan_types_description),
                customContent = {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        rows.forEach { SettingsItem(item = it) }
                    }
                },
            )
        }
        else -> return false
    }
    return true
}
