/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.dot.gallery.core.encryption.EncryptionBackendState
import com.dot.gallery.core.encryption.EncryptionRuntimeState
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.components.rememberPreference
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.METADATA_ISOLATION_HYBRID
import com.dot.gallery.domain.settings.METADATA_ISOLATION_PER_FILE
import com.dot.gallery.domain.settings.METADATA_ISOLATION_SHARED
import com.dot.gallery.domain.settings.SettingsKeys
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.security_data_protection_header
import com.dot.gallery.feature.settings.generated.resources.security_encryption_active
import com.dot.gallery.feature.settings.generated.resources.security_encryption_checking
import com.dot.gallery.feature.settings.generated.resources.security_encryption_database_fallback
import com.dot.gallery.feature.settings.generated.resources.security_encryption_plaintext_fallback
import com.dot.gallery.feature.settings.generated.resources.security_encryption_settings_fallback
import com.dot.gallery.feature.settings.generated.resources.security_encryption_status
import com.dot.gallery.feature.settings.generated.resources.security_enforced_by_advanced_protection
import com.dot.gallery.feature.settings.generated.resources.security_enforced_by_advanced_protection_short
import com.dot.gallery.feature.settings.generated.resources.security_metadata_isolation
import com.dot.gallery.feature.settings.generated.resources.security_metadata_isolation_hybrid
import com.dot.gallery.feature.settings.generated.resources.security_metadata_isolation_hybrid_summary
import com.dot.gallery.feature.settings.generated.resources.security_metadata_isolation_per_file
import com.dot.gallery.feature.settings.generated.resources.security_metadata_isolation_per_file_summary
import com.dot.gallery.feature.settings.generated.resources.security_metadata_isolation_shared
import com.dot.gallery.feature.settings.generated.resources.security_metadata_isolation_shared_summary
import com.dot.gallery.feature.settings.generated.resources.security_metadata_isolation_summary
import com.dot.gallery.feature.settings.generated.resources.security_private_folder
import com.dot.gallery.feature.settings.generated.resources.security_private_folder_summary
import com.dot.gallery.feature.settings.generated.resources.security_private_folder_summary_none
import com.dot.gallery.feature.settings.generated.resources.security_sandbox_header
import com.dot.gallery.feature.settings.generated.resources.security_sandboxed_decode
import com.dot.gallery.feature.settings.generated.resources.security_sandboxed_decode_description
import com.dot.gallery.feature.settings.generated.resources.security_sandboxed_decode_summary
import com.dot.gallery.feature.settings.generated.resources.settings_security

private const val DETAIL_METADATA_ISOLATION = "metadata_isolation"
private const val DETAIL_SANDBOXED_DECODE = "sandboxed_decode"
private const val DETAIL_PRIVATE_FOLDER = "private_folder"

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsSecurityContent(
    state: SettingsSecurityState,
    actions: SettingsSecurityActions = SettingsSecurityActions(),
    searchRoute: String? = null,
    searchSink: ((String, List<String>) -> Unit)? = null,
) {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    var metadataIsolationMode by rememberPreference(SettingsKeys.metadataIsolationMode)
    var sandboxedDecode by rememberPreference(SettingsKeys.sandboxedDecode)
    var privateFolderUri by rememberPreference(SettingsKeys.privateFolderUri)

    // When Android Advanced Protection Mode (AAPM) is enabled, sandboxed decoding
    // is forced on and metadata isolation is raised to at least Hybrid. The stored
    // user preferences are left untouched; we only override what is shown/applied.
    val advancedProtection = state.advancedProtection
    val effectiveSandboxedDecode = sandboxedDecode || advancedProtection
    val effectiveMetadataIsolationMode =
        if (advancedProtection && metadataIsolationMode == METADATA_ISOLATION_SHARED)
            METADATA_ISOLATION_HYBRID
        else metadataIsolationMode

    when (detailKey) {
        DETAIL_METADATA_ISOLATION -> {
            BackHandler { detailKey = null }
            val isolationDescription = if (advancedProtection) {
                stringResource(Res.string.security_metadata_isolation_summary) + "\n\n" +
                    stringResource(Res.string.security_enforced_by_advanced_protection)
            } else {
                stringResource(Res.string.security_metadata_isolation_summary)
            }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.security_metadata_isolation),
                description = isolationDescription,
                options = listOf(
                    PreferenceOption(
                        METADATA_ISOLATION_SHARED,
                        stringResource(Res.string.security_metadata_isolation_shared),
                        effectiveMetadataIsolationMode == METADATA_ISOLATION_SHARED,
                        stringResource(Res.string.security_metadata_isolation_shared_summary)
                    ),
                    PreferenceOption(
                        METADATA_ISOLATION_HYBRID,
                        stringResource(Res.string.security_metadata_isolation_hybrid),
                        effectiveMetadataIsolationMode == METADATA_ISOLATION_HYBRID,
                        stringResource(Res.string.security_metadata_isolation_hybrid_summary)
                    ),
                    PreferenceOption(
                        METADATA_ISOLATION_PER_FILE,
                        stringResource(Res.string.security_metadata_isolation_per_file),
                        effectiveMetadataIsolationMode == METADATA_ISOLATION_PER_FILE,
                        stringResource(Res.string.security_metadata_isolation_per_file_summary)
                    ),
                ),
                onOptionSelected = {
                    // Under AAPM the "shared" mode is not allowed; ignore that selection.
                    if (!(advancedProtection && it == METADATA_ISOLATION_SHARED)) {
                        metadataIsolationMode = it
                    }
                },
            )
        }
        DETAIL_SANDBOXED_DECODE -> {
            BackHandler { detailKey = null }
            val sandboxDescription = if (advancedProtection) {
                stringResource(Res.string.security_sandboxed_decode_description) + "\n\n" +
                    stringResource(Res.string.security_enforced_by_advanced_protection)
            } else {
                stringResource(Res.string.security_sandboxed_decode_description)
            }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.security_sandboxed_decode),
                isChecked = effectiveSandboxedDecode,
                onCheckedChange = { if (!advancedProtection) sandboxedDecode = it },
                description = sandboxDescription,
                enabled = !advancedProtection,
            )
        }
        DETAIL_PRIVATE_FOLDER -> {
            BackHandler { detailKey = null }
            PrivateFolderDetailScreen(
                currentUri = privateFolderUri,
                stats = if (privateFolderUri.isNotEmpty()) actions.privateFolderStats(privateFolderUri) else null,
                onOpenFolder = actions.onOpenPrivateFolder,
                onPickFolder = actions.onPickPrivateFolder,
                onClearFolder = {
                    actions.onClearPrivateFolder()
                    privateFolderUri = ""
                },
                onClearFolderAndReveal = {
                    actions.onClearPrivateFolderAndReveal()
                    privateFolderUri = ""
                },
            )
        }
        else -> {
            SecurityListScreen(
                metadataIsolationMode = effectiveMetadataIsolationMode,
                sandboxedDecode = effectiveSandboxedDecode,
                onSandboxedDecodeChange = { if (!advancedProtection) sandboxedDecode = it },
                advancedProtection = advancedProtection,
                encryptionState = state.encryptionState,
                privateFolderUri = privateFolderUri,
                onDetailClick = { detailKey = it },
                listState = listState,
                searchRoute = searchRoute,
                searchSink = searchSink,
            )
        }
    }
}

@Composable
private fun SecurityListScreen(
    metadataIsolationMode: String,
    sandboxedDecode: Boolean,
    onSandboxedDecodeChange: (Boolean) -> Unit,
    advancedProtection: Boolean,
    encryptionState: EncryptionRuntimeState,
    privateFolderUri: String,
    onDetailClick: (String) -> Unit,
    listState: LazyListState,
    searchRoute: String?,
    searchSink: ((String, List<String>) -> Unit)?,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val sandboxHeader = stringResource(Res.string.security_sandbox_header)
        val dataProtectionHeader = stringResource(Res.string.security_data_protection_header)
        val sandboxHeaderPref = remember(sandboxHeader) {
            SettingsEntity.Header(title = sandboxHeader)
        }
        val dataProtectionHeaderPref = remember(dataProtectionHeader) {
            SettingsEntity.Header(title = dataProtectionHeader)
        }

        val enforcedSuffix = if (advancedProtection)
            " • " + stringResource(Res.string.security_enforced_by_advanced_protection_short) else ""
        val metadataIsolationSummary = when (metadataIsolationMode) {
            METADATA_ISOLATION_HYBRID -> stringResource(Res.string.security_metadata_isolation_hybrid)
            METADATA_ISOLATION_PER_FILE -> stringResource(Res.string.security_metadata_isolation_per_file)
            else -> stringResource(Res.string.security_metadata_isolation_shared)
        } + enforcedSuffix
        val metadataIsolationPref = rememberPreference(
            metadataIsolationMode,
            title = stringResource(Res.string.security_metadata_isolation),
            icon = Icons.Outlined.Dataset,
            summary = metadataIsolationSummary,
            onClick = { onDetailClick(DETAIL_METADATA_ISOLATION) },
            screenPosition = Position.Top
        )

        val sandboxedDecodePref = rememberSwitchPreference(
            sandboxedDecode,
            title = stringResource(Res.string.security_sandboxed_decode),
            icon = Icons.Outlined.Shield,
            summary = stringResource(Res.string.security_sandboxed_decode_summary) + enforcedSuffix,
            isChecked = sandboxedDecode,
            onCheck = onSandboxedDecodeChange,
            onClick = { onDetailClick(DETAIL_SANDBOXED_DECODE) },
            screenPosition = Position.Bottom
        )

        val encryptionSummary = stringResource(
            when {
                encryptionState.fullyEncrypted -> Res.string.security_encryption_active
                encryptionState.settings == EncryptionBackendState.PLAINTEXT_FALLBACK &&
                    encryptionState.database == EncryptionBackendState.PLAINTEXT_FALLBACK ->
                    Res.string.security_encryption_plaintext_fallback
                encryptionState.settings == EncryptionBackendState.PLAINTEXT_FALLBACK ->
                    Res.string.security_encryption_settings_fallback
                encryptionState.database == EncryptionBackendState.PLAINTEXT_FALLBACK ->
                    Res.string.security_encryption_database_fallback
                else -> Res.string.security_encryption_checking
            }
        )
        val encryptionStatusPref = rememberPreference(
            encryptionState,
            title = stringResource(Res.string.security_encryption_status),
            icon = Icons.Outlined.Lock,
            summary = encryptionSummary,
            enabled = false,
            onClick = {},
            screenPosition = Position.Top
        )

        val privateFolderSummary = if (privateFolderUri.isEmpty()) {
            stringResource(Res.string.security_private_folder_summary_none)
        } else {
            stringResource(Res.string.security_private_folder_summary, privateFolderDisplayPath(privateFolderUri))
        }
        val privateFolderPref = rememberPreference(
            privateFolderUri,
            title = stringResource(Res.string.security_private_folder),
            icon = Icons.Outlined.FolderSpecial,
            summary = privateFolderSummary,
            onClick = { onDetailClick(DETAIL_PRIVATE_FOLDER) },
            screenPosition = Position.Bottom
        )

        return remember(
            sandboxHeaderPref, metadataIsolationPref, sandboxedDecodePref,
            dataProtectionHeaderPref, encryptionStatusPref, privateFolderPref
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
        title = stringResource(Res.string.settings_security),
        settingsList = settings(),
        listState = listState,
        searchRoute = searchRoute,
        searchSink = searchSink,
    )
}
