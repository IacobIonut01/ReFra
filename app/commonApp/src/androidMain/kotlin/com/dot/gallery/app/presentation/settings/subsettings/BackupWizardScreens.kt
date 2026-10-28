/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.settings.subsettings

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.core.backup.BackupSection
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.core.util.SdkCompat
import com.dot.gallery.feature.vault.ui.components.VaultPasswordUnlockSheet
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.dot.gallery.core.ui.components.settings
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.feature.vault.VaultViewModel
import com.dot.gallery.feature.settings.ui.BackupProgressContent
import com.dot.gallery.feature.settings.ui.titleRes
import com.dot.gallery.feature.settings.ui.BackupStatusContent
import com.dot.gallery.feature.settings.ui.BackupWizardScaffold
import com.dot.gallery.feature.settings.ui.ExportEncryptContent
import com.dot.gallery.feature.settings.ui.ExportSelectContent
import com.dot.gallery.feature.settings.ui.ExportStep
import com.dot.gallery.feature.settings.ui.ExportStepSaver
import com.dot.gallery.feature.settings.ui.ImportInspectingContent
import com.dot.gallery.feature.settings.ui.ImportPasswordContent
import com.dot.gallery.feature.settings.ui.ImportPickingContent
import com.dot.gallery.feature.settings.ui.ImportSelectContent
import com.dot.gallery.cloud.core.hasAnyRemoteProvider
import com.dot.gallery.core.ui.util.rememberAppBottomSheetState
import com.dot.gallery.core.logging.printError
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.settings.subsettings.BackupRestoreViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.backup_error
import com.dot.gallery.generated.resources.backup_export
import com.dot.gallery.generated.resources.backup_exporting
import com.dot.gallery.generated.resources.backup_import
import com.dot.gallery.generated.resources.backup_import_pending_favorites
import com.dot.gallery.generated.resources.backup_include_cloud_configs
import com.dot.gallery.generated.resources.backup_include_cloud_configs_summary
import com.dot.gallery.generated.resources.backup_include_cloud_favorites
import com.dot.gallery.generated.resources.backup_include_settings
import com.dot.gallery.generated.resources.backup_include_vaults
import com.dot.gallery.generated.resources.backup_restoring
import com.dot.gallery.generated.resources.backup_result_exported
import com.dot.gallery.generated.resources.backup_result_imported
import com.dot.gallery.generated.resources.backup_stat_favorites
import com.dot.gallery.generated.resources.backup_try_again
import com.dot.gallery.generated.resources.backup_unlock_vault_subtitle
import com.dot.gallery.generated.resources.backup_unlock_vaults_gate_subtitle
import com.dot.gallery.generated.resources.backup_vaults_value
import com.dot.gallery.generated.resources.backup_verify_export_subtitle
import com.dot.gallery.generated.resources.biometric_authentication
import com.dot.gallery.generated.resources.vault_locked_out
import com.dot.gallery.generated.resources.vault_wrong_password_attempts


/**
 * Ordered list of sections shown in the export/import wizards. Cloud accounts
 * (server configurations) are omitted on offline variants that ship without any
 * remote cloud provider, mirroring how the Cloud accounts entry is gated elsewhere.
 */
private val ALL_SECTIONS: List<BackupSection> = buildList {
    add(BackupSection.SETTINGS)
    add(BackupSection.LOCAL_FAVORITES)
    add(BackupSection.CLOUD_FAVORITES)
    if (ProviderType.hasAnyRemoteProvider()) add(BackupSection.CLOUD_CONFIGS)
    add(BackupSection.VAULTS)
    add(BackupSection.ALBUMS)
    add(BackupSection.COLLECTIONS)
    add(BackupSection.CATEGORIES)
    add(BackupSection.METADATA)
    add(BackupSection.SEARCH_HISTORY)
    add(BackupSection.PEOPLE)
    if (ProviderType.hasAnyRemoteProvider()) add(BackupSection.CLOUD_PREFS)
    add(BackupSection.EDIT_BACKUPS)
    add(BackupSection.DEVICE_UI)
}

// =================================================================================================
// Export wizard
// =================================================================================================

// ExportStep + ExportStepSaver moved to :feature:settings (BackupWizardContents.kt).

@Composable
fun SettingsBackupExportScreen(navigateUp: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = injectViewModel<BackupRestoreViewModel> {backupRestoreViewModel}
    val vaultViewModel = injectViewModel<VaultViewModel> {vaultViewModel}
    val phase by viewModel.exportPhase.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val vaultState by vaultViewModel.vaultState.collectAsStateWithLifecycle()

    var step by rememberSaveable(stateSaver = ExportStepSaver) { mutableStateOf(ExportStep.SELECT) }
    var protect by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var pendingPassword by rememberSaveable { mutableStateOf<String?>(null) }

    // Per-vault export authentication. Because the archive decrypts every selected vault,
    // each one is verified with its OWN credential (PIN/pattern/password), falling back to
    // device auth only for vaults that have no custom password set.
    val unlockSheet = rememberAppBottomSheetState()
    var unlockAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var vaultQueue by remember { mutableStateOf<List<Vault>>(emptyList()) }
    var currentVault by remember { mutableStateOf<Vault?>(null) }
    var deviceVerified by remember { mutableStateOf(false) }
    var biometricTick by remember { mutableIntStateOf(0) }

    // Enabling the Vaults section reveals the vault list, so it is gated behind the vault
    // selector password (if one is configured) and is OFF by default.
    val revealGateSheet = rememberAppBottomSheetState()
    var revealGateAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
    var revealGateError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.startExport(it, pendingPassword)
        }
    }

    fun launchSaf() {
        val ext = if (pendingPassword != null) "rfbk" else "zip"
        val name = "refra-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.$ext"
        exportLauncher.launch(name)
    }

    val wrongPasswordStr = stringResource(Res.string.vault_wrong_password_attempts)
    val lockedOutStr = stringResource(Res.string.vault_locked_out)

    // Advances the per-vault verification queue: prompts the next vault's own credential,
    // uses device auth for vaults without a custom password, and writes the file once all pass.
    fun processVaultQueue() {
        val next = vaultQueue.firstOrNull()
        if (next == null) {
            currentVault = null
            launchSaf()
            return
        }
        currentVault = next
        scope.launch {
            val type = VaultPasswordManager.getAuthType(context, next.uuid)
            when {
                type != null -> {
                    unlockAuthType = type
                    unlockError = null
                    unlockSheet.show()
                }
                // A vault with no custom password is covered by a single device auth.
                deviceVerified -> {
                    vaultQueue = vaultQueue.drop(1)
                    processVaultQueue()
                }
                else -> biometricTick++
            }
        }
    }

    val exportBiometric = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.backup_verify_export_subtitle),
        onSuccess = {
            deviceVerified = true
            vaultQueue = vaultQueue.drop(1)
            processVaultQueue()
        },
        onFailed = {
            // User cancelled: abort the whole gate, nothing is exported.
            vaultQueue = emptyList()
            currentVault = null
        }
    )

    // Device auth is imperative, so trigger it from an effect when the queue requests it.
    LaunchedEffect(biometricTick) {
        if (biometricTick > 0 && currentVault != null) {
            if (exportBiometric.isSupported) {
                exportBiometric.authenticate()
            } else {
                // No device security configured; this vault has nothing to verify against.
                vaultQueue = vaultQueue.drop(1)
                processVaultQueue()
            }
        }
    }

    fun beginExport() {
        pendingPassword = if (protect && password.isNotBlank()) password else null
        val selectedVaults = if (selection.vaults) {
            vaultState.vaults.filter { selection.isVaultSelected(it.uuid.toString()) }
        } else emptyList()
        if (selectedVaults.isEmpty()) {
            launchSaf()
            return
        }
        deviceVerified = false
        vaultQueue = selectedVaults
        processVaultQueue()
    }

    // The Vaults section is only offered when at least one vault exists on the device.
    val hasVaults = vaultState.vaults.isNotEmpty()
    val exportSections = ALL_SECTIONS.filter { it != BackupSection.VAULTS || hasVaults }
    val vaultIds = vaultState.vaults.map { it.uuid.toString() }

    // Once vaults have loaded and there are none, make sure the section isn't left selected.
    LaunchedEffect(vaultState.isLoading, hasVaults) {
        if (!vaultState.isLoading && !hasVaults && selection.vaults) {
            viewModel.updateSelection { copy(vaults = false) }
        }
    }

    fun toggleVault(id: String, checked: Boolean) {
        viewModel.updateSelection {
            val current = selectedVaultIds ?: vaultIds.toSet()
            copy(selectedVaultIds = if (checked) current + id else current - id)
        }
    }

    // Vault backup starts OFF; the list is only revealed after passing the selector gate.
    var vaultsDefaultApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!vaultsDefaultApplied) {
            viewModel.updateSelection { copy(vaults = false, selectedVaultIds = null) }
            vaultsDefaultApplied = true
        }
    }

    fun enableVaults() {
        viewModel.updateSelection { copy(vaults = true, selectedVaultIds = null) }
    }

    val revealGateBiometric = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.backup_verify_export_subtitle),
        onSuccess = { enableVaults() },
        onFailed = { }
    )

    // Verifies the vault selector credential (if any) before exposing the vault list.
    fun requestRevealVaults() {
        scope.launch {
            when (VaultPasswordManager.getGateMode(context)) {
                GateMode.CUSTOM -> {
                    val type = VaultPasswordManager.getAuthType(context, VaultPasswordManager.GATE_UUID)
                    when {
                        type != null -> {
                            revealGateAuthType = type
                            revealGateError = null
                            revealGateSheet.show()
                        }
                        revealGateBiometric.isSupported -> revealGateBiometric.authenticate()
                        else -> enableVaults()
                    }
                }
                GateMode.DEVICE ->
                    if (revealGateBiometric.isSupported) revealGateBiometric.authenticate()
                    else enableVaults()
                GateMode.NONE -> enableVaults()
            }
        }
    }

    val anySelected = exportSections.any { selection.isOn(it) } &&
        // If only the vaults section is on, at least one vault must remain selected.
        (!selection.vaults || exportSections.any { it != BackupSection.VAULTS && selection.isOn(it) } ||
            vaultIds.any { selection.isVaultSelected(it) })
    val passwordsValid = !protect || (password.isNotBlank() && password == confirm)

    BackupWizardScaffold(title = stringResource(Res.string.backup_export)) { padding ->
        when (phase) {
            is BackupRestoreViewModel.ExportPhase.Running ->
                BackupProgressContent(padding, exportSections.filter { selection.isOn(it) }, progress, stringResource(Res.string.backup_exporting))

            is BackupRestoreViewModel.ExportPhase.Done -> {
                val r = (phase as BackupRestoreViewModel.ExportPhase.Done).result
                BackupStatusContent(
                    padding = padding,
                    success = true,
                    title = stringResource(Res.string.backup_result_exported),
                    rows = buildList {
                        add(stringResource(Res.string.backup_include_settings) to "${r.settingsCount}")
                        add(
                            stringResource(Res.string.backup_stat_favorites) to
                                "${r.localFavoritesCount + r.cloudFavoritesCount}"
                        )
                        add(stringResource(Res.string.backup_include_cloud_configs) to "${r.cloudConfigsCount}")
                        add(
                            stringResource(Res.string.backup_include_vaults) to
                                stringResource(Res.string.backup_vaults_value, r.vaultCount, r.vaultMediaCount)
                        )
                        listOf(
                            BackupSection.ALBUMS,
                            BackupSection.COLLECTIONS,
                            BackupSection.CATEGORIES,
                            BackupSection.METADATA,
                            BackupSection.SEARCH_HISTORY,
                            BackupSection.PEOPLE,
                            BackupSection.CLOUD_PREFS,
                            BackupSection.EDIT_BACKUPS,
                            BackupSection.DEVICE_UI
                        ).forEach { section ->
                            val count = r.sectionCounts[section] ?: 0
                            if (count > 0) {
                                add(stringResource(section.titleRes()) to "$count")
                            }
                        }
                    },
                    note = null,
                    onDone = navigateUp
                )
            }

            is BackupRestoreViewModel.ExportPhase.Failed -> {
                val message = (phase as BackupRestoreViewModel.ExportPhase.Failed).message
                BackupStatusContent(
                    padding = padding,
                    success = false,
                    title = stringResource(Res.string.backup_error, message),
                    rows = emptyList(),
                    note = null,
                    onDone = { viewModel.resetExport() },
                    doneLabel = stringResource(Res.string.backup_try_again)
                )
            }

            BackupRestoreViewModel.ExportPhase.Configuring -> {
                if (step == ExportStep.SELECT) {
                    ExportSelectContent(
                        padding = padding,
                        sections = exportSections,
                        isSelected = { selection.isOn(it) },
                        onToggle = { section, checked ->
                            if (section == BackupSection.VAULTS) {
                                if (checked) requestRevealVaults()
                                else viewModel.updateSelection { copy(vaults = false) }
                            } else {
                                viewModel.updateSelection { with(section, checked) }
                            }
                        },
                        vaultItems = vaultState.vaults.map {
                            Triple(it.uuid.toString(), it.name, null)
                        },
                        vaultIsSelected = { selection.isVaultSelected(it) },
                        onToggleVault = ::toggleVault,
                        showVaultList = selection.vaults && hasVaults,
                        nextEnabled = anySelected,
                        onNext = { step = ExportStep.ENCRYPT }
                    )
                } else {
                    ExportEncryptContent(
                        padding = padding,
                        protect = protect,
                        onProtectChange = { protect = it },
                        password = password,
                        onPasswordChange = { password = it },
                        confirm = confirm,
                        onConfirmChange = { confirm = it },
                        showPassword = showPassword,
                        onShowPasswordChange = { showPassword = it },
                        saveEnabled = passwordsValid,
                        onSave = { beginExport() },
                        onBack = { step = ExportStep.SELECT }
                    )
                }
            }
        }
    }

    VaultPasswordUnlockSheet(
        state = unlockSheet,
        authType = unlockAuthType,
        subtitle = currentVault?.let {
            stringResource(Res.string.backup_unlock_vault_subtitle, it.name)
        },
        onDismiss = {
            // Cancelling a vault's prompt aborts the whole export gate.
            unlockError = null
            vaultQueue = emptyList()
            currentVault = null
        },
        onSubmit = { secret ->
            val vault = currentVault ?: return@VaultPasswordUnlockSheet
            scope.launch {
                when (val result = VaultPasswordManager.verifyPassword(
                    context, vault.uuid, secret
                )) {
                    is VerifyResult.Success -> {
                        unlockError = null
                        unlockSheet.hide()
                        vaultQueue = vaultQueue.drop(1)
                        processVaultQueue()
                    }
                    is VerifyResult.Failed ->
                        unlockError = String.format(wrongPasswordStr, result.attemptsLeft)
                    is VerifyResult.LockedOut ->
                        unlockError = String.format(lockedOutStr, result.cooldownMs / 1000)
                }
            }
        },
        errorMessage = unlockError
    )

    // Selector-gate verification shown when turning the Vaults section on.
    VaultPasswordUnlockSheet(
        state = revealGateSheet,
        authType = revealGateAuthType,
        subtitle = stringResource(Res.string.backup_unlock_vaults_gate_subtitle),
        onDismiss = { revealGateError = null },
        onSubmit = { secret ->
            scope.launch {
                when (val result = VaultPasswordManager.verifyPassword(
                    context, VaultPasswordManager.GATE_UUID, secret
                )) {
                    is VerifyResult.Success -> {
                        revealGateError = null
                        revealGateSheet.hide()
                        enableVaults()
                    }
                    is VerifyResult.Failed ->
                        revealGateError = String.format(wrongPasswordStr, result.attemptsLeft)
                    is VerifyResult.LockedOut ->
                        revealGateError = String.format(lockedOutStr, result.cooldownMs / 1000)
                }
            }
        },
        errorMessage = revealGateError
    )
}

// =================================================================================================
// Import wizard
// =================================================================================================

@Composable
fun SettingsBackupImportScreen(navigateUp: () -> Unit) {
    val context = LocalContext.current
    val viewModel = injectViewModel<BackupRestoreViewModel> {backupRestoreViewModel}
    val phase by viewModel.importPhase.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val favoriteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onImportFilePicked(uri)
        } else {
            navigateUp()
        }
    }

    fun pickFile() {
        importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
    }

    // Auto-open the file picker the first time the screen is shown.
    LaunchedEffect(Unit) {
        if (phase is BackupRestoreViewModel.ImportPhase.PickingFile) pickFile()
    }

    // After a successful import, fire the system favorite-consent dialog for matched local media.
    LaunchedEffect(phase) {
        val done = phase as? BackupRestoreViewModel.ImportPhase.Done ?: return@LaunchedEffect
        val uris = done.result.pendingLocalFavoriteUris
        if (uris.isNotEmpty() && SdkCompat.supportsFavorites) {
            try {
                val intentSender = MediaStore.createFavoriteRequest(
                    context.contentResolver, uris, true
                ).intentSender
                val request = IntentSenderRequest.Builder(intentSender)
                    .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                    .build()
                favoriteConsentLauncher.launch(request)
            } catch (e: Exception) {
                printError("SettingsBackupImportScreen: failed to request local favorites: ${e.message}")
            }
        }
    }

    BackupWizardScaffold(title = stringResource(Res.string.backup_import)) { padding ->
        when (val current = phase) {
            BackupRestoreViewModel.ImportPhase.PickingFile ->
                ImportPickingContent(padding = padding, onPickFile = { pickFile() })

            BackupRestoreViewModel.ImportPhase.Inspecting ->
                ImportInspectingContent(padding = padding)

            is BackupRestoreViewModel.ImportPhase.NeedsPassword ->
                ImportPasswordContent(
                    padding = padding,
                    wrongPassword = current.wrongPassword,
                    onSubmit = { viewModel.submitImportPassword(it) }
                )

            is BackupRestoreViewModel.ImportPhase.Configuring ->
                ImportSelectContent(
                    padding = padding,
                    sections = ALL_SECTIONS.filter { current.contents.has(it) },
                    contents = current.contents,
                    selection = selection,
                    onToggle = { section, checked -> viewModel.updateSelection { with(section, checked) } },
                    onToggleVault = { id, checked ->
                        viewModel.updateSelection {
                            val allIds = current.contents.vaults.map { it.uuid }.toSet()
                            val cur = selectedVaultIds ?: allIds
                            copy(selectedVaultIds = if (checked) cur + id else cur - id)
                        }
                    },
                    onRestore = { viewModel.startImport() }
                )

            BackupRestoreViewModel.ImportPhase.Running ->
                BackupProgressContent(padding, ALL_SECTIONS.filter { selection.isOn(it) }, progress, stringResource(Res.string.backup_restoring))

            is BackupRestoreViewModel.ImportPhase.Done -> {
                val r = current.result
                fun restoredValue(restored: Int, skipped: Int): String =
                    if (skipped == 0) "$restored" else "$restored/${restored + skipped}"

                val pendingFavoritesNote = if (r.pendingLocalFavoriteUris.isNotEmpty()) {
                    stringResource(Res.string.backup_import_pending_favorites, r.pendingLocalFavoriteUris.size)
                } else null
                val authenticationNote = if (r.cloudConfigsRequiringAuthentication > 0) {
                    stringResource(Res.string.backup_include_cloud_configs_summary)
                } else null
                val issueNote = r.issues.takeIf { it.isNotEmpty() }?.joinToString("\n") {
                    "${it.item}: ${it.message}"
                }
                BackupStatusContent(
                    padding = padding,
                    success = r.issues.isEmpty(),
                    title = stringResource(Res.string.backup_result_imported),
                    rows = buildList {
                        add(
                            stringResource(Res.string.backup_include_settings) to
                                restoredValue(r.settingsRestored, r.settingsSkipped)
                        )
                        add(
                            stringResource(Res.string.backup_include_cloud_favorites) to
                                restoredValue(r.cloudFavoritesRestored, r.cloudFavoritesSkipped)
                        )
                        add(
                            stringResource(Res.string.backup_include_cloud_configs) to
                                restoredValue(r.cloudConfigsRestored, r.cloudConfigsSkipped)
                        )
                        add(
                            stringResource(Res.string.backup_include_vaults) to
                                stringResource(
                                    Res.string.backup_vaults_value,
                                    r.vaultsRestored,
                                    r.vaultMediaRestored
                                )
                        )
                        listOf(
                            BackupSection.ALBUMS,
                            BackupSection.COLLECTIONS,
                            BackupSection.CATEGORIES,
                            BackupSection.METADATA,
                            BackupSection.SEARCH_HISTORY,
                            BackupSection.PEOPLE,
                            BackupSection.CLOUD_PREFS,
                            BackupSection.EDIT_BACKUPS,
                            BackupSection.DEVICE_UI
                        ).forEach { section ->
                            r.sectionResults[section]?.let { result ->
                                add(
                                    stringResource(section.titleRes()) to
                                        restoredValue(result.restored + result.merged, result.skipped)
                                )
                            }
                        }
                    },
                    note = listOfNotNull(pendingFavoritesNote, authenticationNote, issueNote)
                        .joinToString("\n")
                        .ifEmpty { null },
                    onDone = navigateUp
                )
            }

            is BackupRestoreViewModel.ImportPhase.Failed ->
                BackupStatusContent(
                    padding = padding,
                    success = false,
                    title = stringResource(Res.string.backup_error, current.message),
                    rows = emptyList(),
                    note = null,
                    onDone = {
                        viewModel.resetImport()
                        pickFile()
                    },
                    doneLabel = stringResource(Res.string.backup_try_again)
                )
        }
    }
}
