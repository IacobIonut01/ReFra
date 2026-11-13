/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dataset
import androidx.compose.material.icons.outlined.EnhancedEncryption
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.encryption.EncryptionBackendState
import com.dot.gallery.core.encryption.EncryptionRuntimeState
import com.dot.gallery.core.ui.components.BaseSettingsScreen
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.PreviewPhoto
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.components.rememberPreference
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.ENCRYPT_ASK
import com.dot.gallery.domain.settings.ENCRYPT_DELETE
import com.dot.gallery.domain.settings.ENCRYPT_KEEP
import com.dot.gallery.domain.settings.METADATA_ISOLATION_HYBRID
import com.dot.gallery.domain.settings.METADATA_ISOLATION_PER_FILE
import com.dot.gallery.domain.settings.METADATA_ISOLATION_SHARED
import com.dot.gallery.domain.settings.SettingsKeys
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.secure_mode_description
import com.dot.gallery.feature.settings.generated.resources.secure_mode_preview_app
import com.dot.gallery.feature.settings.generated.resources.secure_mode_summary
import com.dot.gallery.feature.settings.generated.resources.secure_mode_title
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
import com.dot.gallery.feature.settings.generated.resources.settings_privacy_header
import com.dot.gallery.feature.settings.generated.resources.settings_security
import com.dot.gallery.feature.settings.generated.resources.vault
import com.dot.gallery.feature.settings.generated.resources.vault_encrypt_ask
import com.dot.gallery.feature.settings.generated.resources.vault_encrypt_behavior
import com.dot.gallery.feature.settings.generated.resources.vault_encrypt_behavior_summary
import com.dot.gallery.feature.settings.generated.resources.vault_encrypt_delete
import com.dot.gallery.feature.settings.generated.resources.vault_encrypt_keep

private const val DETAIL_SECURE = "secure"
private const val DETAIL_METADATA_ISOLATION = "metadata_isolation"
private const val DETAIL_SANDBOXED_DECODE = "sandboxed_decode"
private const val DETAIL_PRIVATE_FOLDER = "private_folder"
private const val DETAIL_VAULT_ENCRYPT = "vault_encrypt"

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

    var secureMode by rememberPreference(SettingsKeys.secureMode)
    var metadataIsolationMode by rememberPreference(SettingsKeys.metadataIsolationMode)
    var sandboxedDecode by rememberPreference(SettingsKeys.sandboxedDecode)
    var privateFolderUri by rememberPreference(SettingsKeys.privateFolderUri)
    var vaultEncryptBehavior by rememberPreference(SettingsKeys.vaultEncryptBehavior)

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
        DETAIL_SECURE -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.secure_mode_title),
                isChecked = secureMode,
                onCheckedChange = { secureMode = it },
                description = stringResource(Res.string.secure_mode_description),
                preview = { checked -> SecureModePreview(checked) },
            )
        }
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
        DETAIL_VAULT_ENCRYPT -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.vault_encrypt_behavior),
                description = stringResource(Res.string.vault_encrypt_behavior_summary),
                options = listOf(
                    PreferenceOption(
                        ENCRYPT_ASK,
                        stringResource(Res.string.vault_encrypt_ask),
                        vaultEncryptBehavior == ENCRYPT_ASK
                    ),
                    PreferenceOption(
                        ENCRYPT_DELETE,
                        stringResource(Res.string.vault_encrypt_delete),
                        vaultEncryptBehavior == ENCRYPT_DELETE
                    ),
                    PreferenceOption(
                        ENCRYPT_KEEP,
                        stringResource(Res.string.vault_encrypt_keep),
                        vaultEncryptBehavior == ENCRYPT_KEEP
                    ),
                ),
                onOptionSelected = { vaultEncryptBehavior = it },
            )
        }
        else -> {
            SecurityListScreen(
                secureMode = secureMode,
                onSecureChange = { secureMode = it },
                metadataIsolationMode = effectiveMetadataIsolationMode,
                sandboxedDecode = effectiveSandboxedDecode,
                onSandboxedDecodeChange = { if (!advancedProtection) sandboxedDecode = it },
                advancedProtection = advancedProtection,
                encryptionState = state.encryptionState,
                privateFolderUri = privateFolderUri,
                vaultEncryptBehavior = vaultEncryptBehavior,
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
    secureMode: Boolean,
    onSecureChange: (Boolean) -> Unit,
    metadataIsolationMode: String,
    sandboxedDecode: Boolean,
    onSandboxedDecodeChange: (Boolean) -> Unit,
    advancedProtection: Boolean,
    encryptionState: EncryptionRuntimeState,
    privateFolderUri: String,
    vaultEncryptBehavior: String,
    onDetailClick: (String) -> Unit,
    listState: LazyListState,
    searchRoute: String?,
    searchSink: ((String, List<String>) -> Unit)?,
) {
    @Composable
    fun settings(): SnapshotStateList<SettingsEntity> {
        val privacyHeader = stringResource(Res.string.settings_privacy_header)
        val sandboxHeader = stringResource(Res.string.security_sandbox_header)
        val dataProtectionHeader = stringResource(Res.string.security_data_protection_header)
        val vaultHeader = stringResource(Res.string.vault)
        val privacyHeaderPref = remember(privacyHeader) {
            SettingsEntity.Header(title = privacyHeader)
        }
        val sandboxHeaderPref = remember(sandboxHeader) {
            SettingsEntity.Header(title = sandboxHeader)
        }
        val dataProtectionHeaderPref = remember(dataProtectionHeader) {
            SettingsEntity.Header(title = dataProtectionHeader)
        }
        val vaultHeaderPref = remember(vaultHeader) {
            SettingsEntity.Header(title = vaultHeader)
        }

        val secureModePref = rememberSwitchPreference(
            secureMode,
            title = stringResource(Res.string.secure_mode_title),
            icon = Icons.Outlined.ScreenLockPortrait,
            summary = stringResource(Res.string.secure_mode_summary),
            isChecked = secureMode,
            onCheck = onSecureChange,
            onClick = { onDetailClick(DETAIL_SECURE) },
            screenPosition = Position.Alone
        )

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

        val vaultEncryptBehaviorSummary = when (vaultEncryptBehavior) {
            ENCRYPT_DELETE -> stringResource(Res.string.vault_encrypt_delete)
            ENCRYPT_KEEP -> stringResource(Res.string.vault_encrypt_keep)
            else -> stringResource(Res.string.vault_encrypt_ask)
        }
        val vaultEncryptPref = rememberPreference(
            vaultEncryptBehavior,
            title = stringResource(Res.string.vault_encrypt_behavior),
            icon = Icons.Outlined.EnhancedEncryption,
            summary = vaultEncryptBehaviorSummary,
            onClick = { onDetailClick(DETAIL_VAULT_ENCRYPT) },
            screenPosition = Position.Alone
        )

        return remember(
            privacyHeaderPref, secureModePref,
            sandboxHeaderPref, metadataIsolationPref, sandboxedDecodePref,
            dataProtectionHeaderPref, encryptionStatusPref, privateFolderPref,
            vaultHeaderPref, vaultEncryptPref
        ) {
            mutableStateListOf(
                privacyHeaderPref,
                secureModePref,
                sandboxHeaderPref,
                metadataIsolationPref,
                sandboxedDecodePref,
                dataProtectionHeaderPref,
                encryptionStatusPref,
                privateFolderPref,
                vaultHeaderPref,
                vaultEncryptPref,
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

/**
 * ReFra's card in the recent apps overview: the gallery shows through, or secure mode blanks it
 * and screenshots of it.
 */
@Composable
private fun SecureModePreview(isChecked: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Text(
                text = stringResource(Res.string.secure_mode_preview_app),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            modifier = Modifier
                .size(width = 100.dp, height = 156.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isChecked) MaterialTheme.colorScheme.surfaceContainerHighest
                    else MaterialTheme.colorScheme.surface
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Outlined.VisibilityOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(5.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    repeat(4) { row ->
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(2) { col ->
                                PreviewPhoto(
                                    index = row * 2 + col,
                                    modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
