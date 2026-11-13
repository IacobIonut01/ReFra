/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.components.rememberPreference
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.DELETE_SCOPE_ASK
import com.dot.gallery.domain.settings.DELETE_SCOPE_BOTH
import com.dot.gallery.domain.settings.DELETE_SCOPE_CLOUD
import com.dot.gallery.domain.settings.DELETE_SCOPE_DEVICE
import com.dot.gallery.domain.settings.SettingsKeys
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.allow_vibrations
import com.dot.gallery.feature.settings.generated.resources.allow_vibrations_description
import com.dot.gallery.feature.settings.generated.resources.allow_vibrations_summary
import com.dot.gallery.feature.settings.generated.resources.delete_scope_ask
import com.dot.gallery.feature.settings.generated.resources.delete_scope_from_both
import com.dot.gallery.feature.settings.generated.resources.delete_scope_from_cloud
import com.dot.gallery.feature.settings.generated.resources.delete_scope_from_device
import com.dot.gallery.feature.settings.generated.resources.other
import com.dot.gallery.feature.settings.generated.resources.settings_delete_scope_summary
import com.dot.gallery.feature.settings.generated.resources.settings_delete_scope_title
import com.dot.gallery.feature.settings.generated.resources.settings_general
import com.dot.gallery.feature.settings.generated.resources.settings_open_trash
import com.dot.gallery.feature.settings.generated.resources.settings_open_trash_summary
import com.dot.gallery.feature.settings.generated.resources.settings_trash_confirmation_requires_trash
import com.dot.gallery.feature.settings.generated.resources.settings_trash_confirmation_summary
import com.dot.gallery.feature.settings.generated.resources.settings_trash_confirmation_title
import com.dot.gallery.feature.settings.generated.resources.settings_trash_summary
import com.dot.gallery.feature.settings.generated.resources.settings_trash_title
import com.dot.gallery.feature.settings.generated.resources.trash
import com.dot.gallery.feature.settings.generated.resources.trash_confirmation_description
import com.dot.gallery.feature.settings.generated.resources.trash_confirmation_requires_trash_description
import com.dot.gallery.feature.settings.generated.resources.trash_enabled_description

private const val DETAIL_TRASH = "trash"
private const val DETAIL_TRASH_CONFIRM = "trash_confirm"
private const val DETAIL_DELETE_SCOPE = "delete_scope"
private const val DETAIL_VIBRATIONS = "vibrations"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsGeneralContent(
    state: SettingsGeneralState,
    actions: SettingsGeneralActions,
) {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }

    var trashCanEnabled by rememberPreference(SettingsKeys.trashEnabled)
    var trashConfirmationEnabled by rememberPreference(SettingsKeys.trashConfirmEnabled)
    var deleteScope by rememberPreference(SettingsKeys.cloudDeleteScope)
    var allowVibrations by rememberPreference(SettingsKeys.allowVibrations)

    when (detailKey) {
        DETAIL_TRASH -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.settings_trash_title),
                isChecked = trashCanEnabled,
                onCheckedChange = { trashCanEnabled = it },
                description = stringResource(Res.string.trash_enabled_description),
            )
        }
        DETAIL_TRASH_CONFIRM -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.settings_trash_confirmation_title),
                isChecked = trashConfirmationEnabled,
                onCheckedChange = { trashConfirmationEnabled = it },
                description = if (trashCanEnabled) {
                    stringResource(Res.string.trash_confirmation_description)
                } else {
                    stringResource(Res.string.trash_confirmation_requires_trash_description)
                },
                enabled = trashCanEnabled,
            )
        }
        DETAIL_DELETE_SCOPE -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.settings_delete_scope_title),
                description = stringResource(Res.string.settings_delete_scope_summary),
                options = listOf(
                    PreferenceOption(
                        DELETE_SCOPE_ASK,
                        stringResource(Res.string.delete_scope_ask),
                        deleteScope == DELETE_SCOPE_ASK
                    ),
                    PreferenceOption(
                        DELETE_SCOPE_DEVICE,
                        stringResource(Res.string.delete_scope_from_device),
                        deleteScope == DELETE_SCOPE_DEVICE
                    ),
                    PreferenceOption(
                        DELETE_SCOPE_CLOUD,
                        stringResource(Res.string.delete_scope_from_cloud),
                        deleteScope == DELETE_SCOPE_CLOUD
                    ),
                    PreferenceOption(
                        DELETE_SCOPE_BOTH,
                        stringResource(Res.string.delete_scope_from_both),
                        deleteScope == DELETE_SCOPE_BOTH
                    ),
                ),
                onOptionSelected = { deleteScope = it },
            )
        }
        DETAIL_VIBRATIONS -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.allow_vibrations),
                isChecked = allowVibrations,
                onCheckedChange = { allowVibrations = it },
                description = stringResource(Res.string.allow_vibrations_description),
            )
        }
        else -> {
            GeneralListScreen(
                trashCanEnabled = trashCanEnabled,
                onTrashChange = { trashCanEnabled = it },
                trashConfirmationEnabled = trashConfirmationEnabled,
                onTrashConfirmChange = { trashConfirmationEnabled = it },
                deleteScope = deleteScope,
                allowVibrations = allowVibrations,
                onVibrationsChange = { allowVibrations = it },
                supportsTrash = state.supportsTrash,
                onDetailClick = { detailKey = it },
                onOpenTrash = actions.onOpenTrash,
                searchRoute = actions.searchRoute,
                searchSink = actions.searchSink,
            )
        }
    }
}

@Composable
private fun GeneralListScreen(
    trashCanEnabled: Boolean,
    onTrashChange: (Boolean) -> Unit,
    trashConfirmationEnabled: Boolean,
    onTrashConfirmChange: (Boolean) -> Unit,
    deleteScope: String,
    allowVibrations: Boolean,
    onVibrationsChange: (Boolean) -> Unit,
    supportsTrash: Boolean,
    onDetailClick: (String) -> Unit,
    onOpenTrash: () -> Unit,
    searchRoute: String?,
    searchSink: ((String, List<String>) -> Unit)?,
) {
    val listState = rememberLazyListState()

    val openTrashPref = rememberPreference(
        title = stringResource(Res.string.settings_open_trash),
        icon = Icons.Outlined.Delete,
        summary = stringResource(Res.string.settings_open_trash_summary),
        onClick = onOpenTrash,
        screenPosition = Position.Top,
    )

    val trashCanEnabledPref = rememberSwitchPreference(
        trashCanEnabled,
        title = stringResource(Res.string.settings_trash_title),
        icon = Icons.Outlined.RestoreFromTrash,
        summary = stringResource(Res.string.settings_trash_summary),
        isChecked = trashCanEnabled,
        onCheck = onTrashChange,
        onClick = { onDetailClick(DETAIL_TRASH) },
        screenPosition = Position.Middle
    )

    val trashConfirmationEnabledPref = rememberSwitchPreference(
        trashConfirmationEnabled,
        trashCanEnabled,
        title = stringResource(Res.string.settings_trash_confirmation_title),
        icon = Icons.Outlined.TaskAlt,
        summary = stringResource(
            if (trashCanEnabled) {
                Res.string.settings_trash_confirmation_summary
            } else {
                Res.string.settings_trash_confirmation_requires_trash
            }
        ),
        enabled = trashCanEnabled,
        isChecked = trashConfirmationEnabled,
        onCheck = onTrashConfirmChange,
        onClick = { onDetailClick(DETAIL_TRASH_CONFIRM) },
        screenPosition = Position.Bottom
    )

    val allowVibrationsPref = rememberSwitchPreference(
        allowVibrations,
        title = stringResource(Res.string.allow_vibrations),
        icon = Icons.Outlined.Vibration,
        summary = stringResource(Res.string.allow_vibrations_summary),
        isChecked = allowVibrations,
        onCheck = onVibrationsChange,
        onClick = { onDetailClick(DETAIL_VIBRATIONS) },
        screenPosition = Position.Top
    )

    val deleteScopeSummary = when (deleteScope) {
        DELETE_SCOPE_DEVICE -> stringResource(Res.string.delete_scope_from_device)
        DELETE_SCOPE_CLOUD -> stringResource(Res.string.delete_scope_from_cloud)
        DELETE_SCOPE_BOTH -> stringResource(Res.string.delete_scope_from_both)
        else -> stringResource(Res.string.delete_scope_ask)
    }
    val deleteScopePref = rememberPreference(
        deleteScope,
        title = stringResource(Res.string.settings_delete_scope_title),
        icon = Icons.Outlined.PhonelinkErase,
        summary = deleteScopeSummary,
        onClick = { onDetailClick(DETAIL_DELETE_SCOPE) },
        screenPosition = Position.Bottom
    )

    val trashHeader = stringResource(Res.string.trash)
    val otherHeader = stringResource(Res.string.other)

    val settingsList: SnapshotStateList<SettingsEntity> = remember(
        openTrashPref, trashCanEnabledPref, trashConfirmationEnabledPref,
        allowVibrationsPref, deleteScopePref, trashHeader, otherHeader,
        supportsTrash,
    ) {
        mutableStateListOf<SettingsEntity>().apply {
            if (supportsTrash) {
                add(SettingsEntity.Header(title = trashHeader))
                add(openTrashPref)
                add(trashCanEnabledPref)
                add(trashConfirmationEnabledPref)
            }
            add(SettingsEntity.Header(title = otherHeader))
            add(allowVibrationsPref)
            add(deleteScopePref)
        }
    }

    BaseSettingsScreen(
        title = stringResource(Res.string.settings_general),
        settingsList = settingsList,
        listState = listState,
        searchRoute = searchRoute,
        searchSink = searchSink,
    )
}
