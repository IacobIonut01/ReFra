/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.app.presentation.picker.components

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.setFollowTheme
import com.dot.gallery.domain.model.AlbumState
import com.dot.gallery.domain.model.AllowedMedia
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.model.VaultState
import com.dot.gallery.feature.albums.ui.components.AlbumImage
import com.dot.gallery.feature.picker.ui.PickerActions
import com.dot.gallery.feature.picker.ui.PickerContent
import com.dot.gallery.feature.picker.ui.PickerState
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import com.dot.gallery.feature.vault.domain.model.VerifyResult
import com.dot.gallery.feature.picker.PickerViewModel
import com.dot.gallery.app.presentation.util.rememberWindowInsetsController
import com.dot.gallery.app.presentation.util.toggleOrientation
import com.dot.gallery.feature.vault.ui.components.VaultPasswordUnlockDialog
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import kotlinx.coroutines.launch
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberAllowGlass
import com.dot.gallery.core.presentation.util.LocalHazeGlassEnabled
import com.dot.gallery.core.presentation.util.LocalHazeState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import com.dot.gallery.app.presentation.mediaview.MediaViewScreenRoute
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.biometric_authentication
import com.dot.gallery.generated.resources.private_folder_unlock
import com.dot.gallery.generated.resources.private_folder_unlock_subtitle
import com.dot.gallery.generated.resources.unlock_album_biometric_subtitle
import com.dot.gallery.generated.resources.vault_locked_out
import com.dot.gallery.generated.resources.vault_wrong_password_attempts
import com.dot.gallery.generated.resources.verify_identity


/**
 * App-side shell for the media picker: owns the `PickerViewModel`, the
 * credential prompts (`VaultPasswordManager` + BiometricPrompt) and the
 * pick-result hand-off. All UI lives in `:feature:picker` ([PickerContent]);
 * continuations passed through [PickerActions] drive its internal navigation
 * once a gate clears.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PickerScreen(
    title: String,
    allowedMedia: AllowedMedia,
    allowSelection: Boolean,
    excludedVaultUuid: String? = null,
    onClose: () -> Unit,
    sendMediaAsResult: (List<Uri>) -> Unit,
    sendMediaAsMediaResult: (List<Media>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val mediaVM = injectViewModel<PickerViewModel> {pickerViewModel}.apply {
        this.allowedMedia = allowedMedia
    }
    val albumsState = mediaVM.albumsState.collectAsStateWithLifecycle()
    val metadataState = mediaVM.metadataState.collectAsStateWithLifecycle()
    val allVaultsState = mediaVM.vaultState.collectAsStateWithLifecycle()
    // Adding to a vault opens the picker with that vault hidden, so it can't pick from itself.
    val vaultState = remember(excludedVaultUuid) {
        derivedStateOf {
            excludedVaultState(allVaultsState.value, excludedVaultUuid)
        }
    }

    val context = LocalContext.current
    val allowBlur by Settings.Misc.rememberAllowBlur()
    val eventHandler = LocalEventHandler.current
    val activity = LocalActivity.current as? ComponentActivity
    val windowInsetsController = rememberWindowInsetsController()

    val wrongPasswordStr = stringResource(Res.string.vault_wrong_password_attempts)
    val lockedOutStr = stringResource(Res.string.vault_locked_out)

    // Locked-album auth: the continuation lives in the module, the prompt here.
    var pendingAlbumOpen by remember { mutableStateOf<(() -> Unit)?>(null) }
    val albumBiometricState = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.unlock_album_biometric_subtitle),
        onSuccess = {
            pendingAlbumOpen?.invoke()
            pendingAlbumOpen = null
        },
        onFailed = {
            pendingAlbumOpen = null
        }
    )

    // Private folder auth state
    var pendingPrivateFolderOpen by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showPrivateFolderPasswordDialog by remember { mutableStateOf(false) }
    var privateFolderAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
    var privateFolderPasswordError by remember { mutableStateOf<String?>(null) }

    val privateFolderBiometricState = rememberBiometricState(
        title = stringResource(Res.string.private_folder_unlock),
        subtitle = stringResource(Res.string.private_folder_unlock_subtitle),
        onSuccess = {
            pendingPrivateFolderOpen?.invoke()
            pendingPrivateFolderOpen = null
        },
        onFailed = {
            pendingPrivateFolderOpen = null
        }
    )

    // Per-vault auth state (mirrors VaultScreen)
    var pendingVaultOpen by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingAuthVault by remember { mutableStateOf<Vault?>(null) }
    var showVaultPasswordDialog by remember { mutableStateOf(false) }
    var vaultAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
    var vaultPasswordError by remember { mutableStateOf<String?>(null) }

    // Vault-list gate auth state (mirrors VaultScreen gate). Persists for the session so the
    // selector password/biometric isn't re-prompted every time the Vault card is tapped.
    var pendingVaultsReveal by remember { mutableStateOf<(() -> Unit)?>(null) }
    var isGateAuthenticated by rememberSaveable { mutableStateOf(false) }
    var showGatePasswordDialog by remember { mutableStateOf(false) }
    var gateAuthType by remember { mutableStateOf<VaultAuthType?>(null) }
    var gatePasswordError by remember { mutableStateOf<String?>(null) }

    fun onVaultAuthSuccess() {
        showVaultPasswordDialog = false
        vaultPasswordError = null
        pendingVaultOpen?.invoke()
        pendingVaultOpen = null
        pendingAuthVault = null
    }

    val vaultBiometricState = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.verify_identity),
        onSuccess = { onVaultAuthSuccess() },
        onFailed = {
            pendingVaultOpen = null
            pendingAuthVault = null
        }
    )

    fun onGateAuthSuccess() {
        isGateAuthenticated = true
        showGatePasswordDialog = false
        gatePasswordError = null
        pendingVaultsReveal?.invoke()
        pendingVaultsReveal = null
    }

    val gateBiometricState = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.verify_identity),
        onSuccess = { onGateAuthSuccess() },
        onFailed = {
            pendingVaultsReveal = null
        }
    )

    val allowGlass by rememberAllowGlass()
    val hazeState = rememberHazeState()
    // The picker runs in its own activity, so it provides the frosted-surface locals the floating
    // dock samples (MainActivity's providers never reach it).
    CompositionLocalProvider(
        LocalHazeState provides hazeState,
        LocalHazeBlurStyle provides HazeMaterials.regular(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ).then { blurEnabled(allowBlur) },
        LocalHazeGlassEnabled provides (allowBlur && allowGlass)
    ) {
    PickerContent(
        state = PickerState(
            title = title,
            allowSelection = allowSelection,
            albumsState = albumsState,
            metadataState = metadataState,
            privateFolderMediaState = mediaVM.privateFolderMediaState,
            cloudMediaState = mediaVM.cloudMediaState,
            vaultState = vaultState,
            cloudAvailable = mediaVM.cloudAvailable,
            mediaStateFor = { albumId ->
                mediaVM.albumId = albumId
                mediaVM.mediaState.value
            },
            vaultMediaStateFor = mediaVM::createVaultMediaState,
        ),
        actions = PickerActions(
            onClose = onClose,
            onLockedAlbum = { album, open, noDeviceSecurity ->
                if (!albumBiometricState.isSupported) {
                    noDeviceSecurity()
                } else {
                    pendingAlbumOpen = open
                    albumBiometricState.authenticate()
                }
            },
            onPrivateFolder = { open ->
                pendingPrivateFolderOpen = open
                scope.launch {
                    when (VaultPasswordManager.getPrivateFolderMode(context)) {
                        GateMode.NONE -> open()
                        GateMode.DEVICE -> {
                            if (privateFolderBiometricState.isSupported) {
                                privateFolderBiometricState.authenticate()
                            } else {
                                open()
                            }
                        }
                        GateMode.CUSTOM -> {
                            val authType = VaultPasswordManager.getAuthType(
                                context, VaultPasswordManager.PRIVATE_FOLDER_UUID
                            )
                            if (authType != null) {
                                privateFolderAuthType = authType
                                showPrivateFolderPasswordDialog = true
                            } else {
                                open()
                            }
                        }
                    }
                }
            },
            onVaultGate = { reveal ->
                pendingVaultsReveal = reveal
                if (isGateAuthenticated) {
                    reveal()
                } else {
                    scope.launch {
                        when (VaultPasswordManager.getGateMode(context)) {
                            GateMode.NONE -> onGateAuthSuccess()
                            GateMode.DEVICE -> {
                                if (gateBiometricState.isSupported) {
                                    gateBiometricState.authenticate()
                                } else {
                                    onGateAuthSuccess()
                                }
                            }
                            GateMode.CUSTOM -> {
                                val authType = VaultPasswordManager.getAuthType(
                                    context, VaultPasswordManager.GATE_UUID
                                )
                                if (authType != null) {
                                    gateAuthType = authType
                                    showGatePasswordDialog = true
                                } else {
                                    onGateAuthSuccess()
                                }
                            }
                        }
                    }
                }
            },
            onVault = { vault, open ->
                pendingVaultOpen = open
                pendingAuthVault = vault
                scope.launch {
                    val authType = VaultPasswordManager.getAuthType(context, vault.uuid)
                    if (authType != null) {
                        vaultAuthType = authType
                        showVaultPasswordDialog = true
                    } else if (vaultBiometricState.isSupported) {
                        vaultAuthType = null
                        vaultBiometricState.authenticate()
                    } else {
                        onVaultAuthSuccess()
                    }
                }
            },
            onPickConfirmed = { selected, onProgress ->
                val uris = mediaVM.materializeToShareableUris(selected, onProgress)
                if (uris.isEmpty()) {
                    false
                } else {
                    sendMediaAsResult(uris)
                    sendMediaAsMediaResult(selected)
                    true
                }
            },
        ),
        albumImage = { album, onClick ->
            AlbumImage(
                album = album,
                isEnabled = true,
                onItemClick = onClick,
                onItemLongClick = null
            )
        },
        previewContent = { sharedTransitionScope, animatedVisibilityScope, mediaState, mediaId, onDismiss ->
            val emptyAlbumState = remember { mutableStateOf(AlbumState()) }
            val emptyVaultState = remember { mutableStateOf(VaultState(isLoading = false)) }
            val previousNavigateUp = remember { eventHandler.navigateUpAction }
            val previousLightStatusBars =
                remember { windowInsetsController.isAppearanceLightStatusBars }
            LaunchedEffect(Unit) {
                windowInsetsController.isAppearanceLightStatusBars = false
                eventHandler.setFollowTheme(false)
                eventHandler.navigateUpAction = {
                    windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                    eventHandler.setFollowTheme(true)
                    onDismiss()
                }
            }

            Scaffold { paddingValues ->
                MediaViewScreenRoute(
                    toggleRotate = { activity?.toggleOrientation() },
                    paddingValues = paddingValues,
                    isStandalone = true,
                    mediaId = mediaId,
                    allowBlur = allowBlur,
                    mediaState = mediaState,
                    metadataState = metadataState,
                    albumsState = emptyAlbumState,
                    vaultState = emptyVaultState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedVisibilityScope
                )
            }

            BackHandler {
                windowInsetsController.isAppearanceLightStatusBars = previousLightStatusBars
                eventHandler.navigateUpAction = previousNavigateUp
                eventHandler.setFollowTheme(true)
                onDismiss()
            }
        },
    )
    }

    AnimatedVisibility(
        visible = showPrivateFolderPasswordDialog,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            VaultPasswordUnlockDialog(
                authType = privateFolderAuthType,
                onDismiss = {
                    showPrivateFolderPasswordDialog = false
                    privateFolderPasswordError = null
                    pendingPrivateFolderOpen = null
                },
                onSubmit = { secret ->
                    scope.launch {
                        val result = VaultPasswordManager.verifyPassword(
                            context, VaultPasswordManager.PRIVATE_FOLDER_UUID, secret
                        )
                        when (result) {
                            is VerifyResult.Success -> {
                                showPrivateFolderPasswordDialog = false
                                privateFolderPasswordError = null
                                pendingPrivateFolderOpen?.invoke()
                                pendingPrivateFolderOpen = null
                            }
                            is VerifyResult.Failed -> {
                                privateFolderPasswordError = String.format(
                                    wrongPasswordStr,
                                    result.attemptsLeft
                                )
                            }
                            is VerifyResult.LockedOut -> {
                                val seconds = result.cooldownMs / 1000
                                privateFolderPasswordError = String.format(
                                    lockedOutStr, seconds
                                )
                            }
                        }
                    }
                },
                errorMessage = privateFolderPasswordError
            )
        }
    }

    if (showVaultPasswordDialog) {
        Dialog(
            onDismissRequest = {
                showVaultPasswordDialog = false
                vaultPasswordError = null
                pendingVaultOpen = null
                pendingAuthVault = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                VaultPasswordUnlockDialog(
                    authType = vaultAuthType,
                    onDismiss = {
                        showVaultPasswordDialog = false
                        vaultPasswordError = null
                        pendingVaultOpen = null
                        pendingAuthVault = null
                    },
                    onSubmit = { secret ->
                        val vault = pendingAuthVault
                        if (vault != null) {
                            scope.launch {
                                when (val result = VaultPasswordManager.verifyPassword(
                                    context, vault.uuid, secret
                                )) {
                                    is VerifyResult.Success -> {
                                        onVaultAuthSuccess()
                                    }
                                    is VerifyResult.Failed -> {
                                        vaultPasswordError = String.format(
                                            wrongPasswordStr, result.attemptsLeft
                                        )
                                    }
                                    is VerifyResult.LockedOut -> {
                                        val seconds = result.cooldownMs / 1000
                                        vaultPasswordError = String.format(lockedOutStr, seconds)
                                    }
                                }
                            }
                        }
                    },
                    errorMessage = vaultPasswordError
                )
            }
        }
    }

    if (showGatePasswordDialog) {
        Dialog(
            onDismissRequest = {
                showGatePasswordDialog = false
                gatePasswordError = null
                pendingVaultsReveal = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                VaultPasswordUnlockDialog(
                    authType = gateAuthType,
                    onDismiss = {
                        showGatePasswordDialog = false
                        gatePasswordError = null
                        pendingVaultsReveal = null
                    },
                    onSubmit = { secret ->
                        scope.launch {
                            when (val result = VaultPasswordManager.verifyPassword(
                                context, VaultPasswordManager.GATE_UUID, secret
                            )) {
                                is VerifyResult.Success -> onGateAuthSuccess()
                                is VerifyResult.Failed -> {
                                    gatePasswordError = String.format(
                                        wrongPasswordStr, result.attemptsLeft
                                    )
                                }
                                is VerifyResult.LockedOut -> {
                                    val seconds = result.cooldownMs / 1000
                                    gatePasswordError = String.format(lockedOutStr, seconds)
                                }
                            }
                        }
                    },
                    errorMessage = gatePasswordError
                )
            }
        }
    }
}

/** [state] without the vault whose uuid is [excludedVaultUuid] (the vault being added to). */
internal fun excludedVaultState(state: VaultState, excludedVaultUuid: String?): VaultState =
    if (excludedVaultUuid == null) state
    else state.copy(vaults = state.vaults.filterNot { it.uuid.toString() == excludedVaultUuid })
