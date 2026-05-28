/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.sandbox.PrivateFolderManager
import com.dot.gallery.feature_node.presentation.settings.components.BaseSettingsScreen
import com.dot.gallery.feature_node.presentation.settings.components.ChooserPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.PreferenceOption
import com.dot.gallery.feature_node.presentation.settings.components.SwitchPreferenceDetailScreen
import com.dot.gallery.feature_node.presentation.settings.components.rememberPreference
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import kotlinx.coroutines.launch

private const val DETAIL_METADATA_ISOLATION = "metadata_isolation"
private const val DETAIL_SANDBOXED_DECODE = "sandboxed_decode"
private const val DETAIL_PRIVATE_FOLDER = "private_folder"

@Composable
fun SettingsSecurityScreen() {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var metadataIsolationMode by Settings.Security.rememberMetadataIsolationMode()
    var sandboxedDecode by Settings.Security.rememberSandboxedDecode()
    var privateFolderUri by Settings.Security.rememberPrivateFolderUri()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                PrivateFolderManager.onFolderPicked(context, it)
                privateFolderUri = it.toString()
            }
        }
    }

    when (detailKey) {
        DETAIL_METADATA_ISOLATION -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(R.string.security_metadata_isolation),
                description = stringResource(R.string.security_metadata_isolation_summary),
                options = listOf(
                    PreferenceOption(
                        Settings.Security.METADATA_ISOLATION_SHARED,
                        stringResource(R.string.security_metadata_isolation_shared),
                        metadataIsolationMode == Settings.Security.METADATA_ISOLATION_SHARED,
                        stringResource(R.string.security_metadata_isolation_shared_summary)
                    ),
                    PreferenceOption(
                        Settings.Security.METADATA_ISOLATION_HYBRID,
                        stringResource(R.string.security_metadata_isolation_hybrid),
                        metadataIsolationMode == Settings.Security.METADATA_ISOLATION_HYBRID,
                        stringResource(R.string.security_metadata_isolation_hybrid_summary)
                    ),
                    PreferenceOption(
                        Settings.Security.METADATA_ISOLATION_PER_FILE,
                        stringResource(R.string.security_metadata_isolation_per_file),
                        metadataIsolationMode == Settings.Security.METADATA_ISOLATION_PER_FILE,
                        stringResource(R.string.security_metadata_isolation_per_file_summary)
                    ),
                ),
                onOptionSelected = { metadataIsolationMode = it },
            )
        }
        DETAIL_SANDBOXED_DECODE -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(R.string.security_sandboxed_decode),
                isChecked = sandboxedDecode,
                onCheckedChange = { sandboxedDecode = it },
                description = stringResource(R.string.security_sandboxed_decode_description),
            )
        }
        DETAIL_PRIVATE_FOLDER -> {
            BackHandler { detailKey = null }
            PrivateFolderDetailScreen(
                currentUri = privateFolderUri,
                onPickFolder = {
                    folderPickerLauncher.launch(null)
                },
                onClearFolder = {
                    scope.launch {
                        PrivateFolderManager.clearFolder(context)
                        privateFolderUri = ""
                    }
                },
                onClearFolderAndReveal = {
                    scope.launch {
                        PrivateFolderManager.clearFolderAndReveal(context)
                        privateFolderUri = ""
                    }
                },
            )
        }
        else -> {
            SecurityListScreen(
                metadataIsolationMode = metadataIsolationMode,
                sandboxedDecode = sandboxedDecode,
                onSandboxedDecodeChange = { sandboxedDecode = it },
                privateFolderUri = privateFolderUri,
                onDetailClick = { detailKey = it },
            )
        }
    }
}

@Composable
private fun SecurityListScreen(
    metadataIsolationMode: String,
    sandboxedDecode: Boolean,
    onSandboxedDecodeChange: (Boolean) -> Unit,
    privateFolderUri: String,
    onDetailClick: (String) -> Unit,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val res = LocalResources.current

        val sandboxHeaderPref = remember(res) {
            SettingsEntity.Header(title = res.getString(R.string.security_sandbox_header))
        }

        val metadataIsolationSummary = when (metadataIsolationMode) {
            Settings.Security.METADATA_ISOLATION_HYBRID -> stringResource(R.string.security_metadata_isolation_hybrid)
            Settings.Security.METADATA_ISOLATION_PER_FILE -> stringResource(R.string.security_metadata_isolation_per_file)
            else -> stringResource(R.string.security_metadata_isolation_shared)
        }
        val metadataIsolationPref = rememberPreference(
            metadataIsolationMode,
            title = stringResource(R.string.security_metadata_isolation),
            summary = metadataIsolationSummary,
            onClick = { onDetailClick(DETAIL_METADATA_ISOLATION) },
            screenPosition = Position.Top
        )

        val sandboxedDecodePref = rememberSwitchPreference(
            sandboxedDecode,
            title = stringResource(R.string.security_sandboxed_decode),
            summary = stringResource(R.string.security_sandboxed_decode_summary),
            isChecked = sandboxedDecode,
            onCheck = onSandboxedDecodeChange,
            onClick = { onDetailClick(DETAIL_SANDBOXED_DECODE) },
            screenPosition = Position.Bottom
        )

        val dataProtectionHeaderPref = remember(res) {
            SettingsEntity.Header(title = res.getString(R.string.security_data_protection_header))
        }

        val encryptionStatusPref = rememberPreference(
            title = stringResource(R.string.security_encryption_status),
            summary = stringResource(R.string.security_encryption_active),
            enabled = false,
            onClick = {},
            screenPosition = Position.Top
        )

        val privateFolderSummary = if (privateFolderUri.isEmpty()) {
            stringResource(R.string.security_private_folder_summary_none)
        } else {
            stringResource(R.string.security_private_folder_summary, privateFolderUri)
        }
        val privateFolderPref = rememberPreference(
            privateFolderUri,
            title = stringResource(R.string.security_private_folder),
            summary = privateFolderSummary,
            onClick = { onDetailClick(DETAIL_PRIVATE_FOLDER) },
            screenPosition = Position.Bottom
        )

        return remember(
            metadataIsolationPref, sandboxedDecodePref,
            encryptionStatusPref, privateFolderPref
        ) {
            mutableStateListOf(
                sandboxHeaderPref,
                metadataIsolationPref,
                sandboxedDecodePref,
                dataProtectionHeaderPref,
                encryptionStatusPref,
                privateFolderPref,
            )
        }
    }

    BaseSettingsScreen(
        title = stringResource(R.string.settings_security),
        settingsList = settings(),
    )
}
