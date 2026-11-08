package com.dot.gallery.app.presentation.vault

import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.feature.vault.domain.model.GateMode
import com.dot.gallery.feature.vault.domain.util.VaultGatePolicy
import com.dot.gallery.feature.vault.ui.VaultContent
import com.dot.gallery.feature.vault.ui.components.VaultDeviceAuthBridge
import com.dot.gallery.feature.viewer.components.media.ViewerDismissExitVisual
import com.dot.gallery.feature.viewer.components.media.ViewerDismissFlightLayer
import com.dot.gallery.app.presentation.util.SecureWindow
import com.dot.gallery.feature.vault.utils.VaultPasswordManager
import com.dot.gallery.feature.vault.utils.rememberBiometricState
import com.dot.gallery.app.presentation.mediaview.MediaViewScreenRoute
import com.dot.gallery.core.logging.printInfo
import com.dot.gallery.feature.vault.utils.rememberBiometricManager
import com.dot.gallery.core.logging.printError
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.feature.vault.VaultViewModel
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.biometric_authentication
import com.dot.gallery.generated.resources.verify_identity

private const val TAG = "vault.ui"

/**
 * Host wrapper: the vault flow UI (internal NavHost, gate + per-vault auth, setup
 * wizard, media viewer overlay) lives in `:feature:vault` ([VaultContent]); this
 * composable supplies the app-side seams — [VaultViewModel] state/actions,
 * [VaultPasswordManager] credential-store reads and writes, BiometricPrompt
 * device-auth bridges, the process lifecycle, edge-to-edge styling — plus the
 * app-only slots for [VaultDisplay], [MediaViewScreenRoute] and the
 * viewer-dismiss exit-visual/flight layers.
 */
@Composable
fun VaultScreen(
    paddingValues: PaddingValues,
    toggleRotate: () -> Unit,
    @Suppress("UNUSED_PARAMETER") shouldSkipAuth: MutableState<Boolean>,
    allowBlur: Boolean
) = SecureWindow {
    val context = LocalContext.current
    val viewModel = injectViewModel<VaultViewModel> {vaultViewModel}
    val vaultState = viewModel.vaultState.collectAsStateWithLifecycle()
    val albumState = viewModel.albumsState.collectAsStateWithLifecycle()
    val metadataState = viewModel.metadataState.collectAsStateWithLifecycle()
    val eventHandler = remember { DefaultEventHandler() }

    // Device-credential prompt bridges — each writes a one-shot outcome the shared
    // flow consumes (true = unlocked, false = failed/cancelled).
    val vaultOutcome = remember { mutableStateOf<Boolean?>(null) }
    val vaultBiometricState = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.verify_identity),
        onSuccess = { vaultOutcome.value = true },
        onFailed = { vaultOutcome.value = false }
    )
    val vaultDeviceAuth = remember(vaultBiometricState.isSupported) {
        VaultDeviceAuthBridge(
            isSupported = vaultBiometricState.isSupported,
            authenticateAction = vaultBiometricState::authenticate,
            cancelAction = vaultBiometricState::cancelAuthentication,
            outcomeState = vaultOutcome
        )
    }

    val gateOutcome = remember { mutableStateOf<Boolean?>(null) }
    val gateBiometricState = rememberBiometricState(
        title = stringResource(Res.string.biometric_authentication),
        subtitle = stringResource(Res.string.verify_identity),
        onSuccess = { gateOutcome.value = true },
        onFailed = { gateOutcome.value = false }
    )
    val gateDeviceAuth = remember(gateBiometricState.isSupported) {
        VaultDeviceAuthBridge(
            isSupported = gateBiometricState.isSupported,
            authenticateAction = gateBiometricState::authenticate,
            cancelAction = gateBiometricState::cancelAuthentication,
            outcomeState = gateOutcome
        )
    }

    val biometricManager = rememberBiometricManager()
    val deviceSecurityAvailable = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            biometricManager.canAuthenticate(
                BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            ) == BIOMETRIC_SUCCESS
        } else {
            val keyguardManager = context.getSystemService(KeyguardManager::class.java)
            keyguardManager?.isDeviceSecure == true
        }
    }

    VaultContent(
        vaultState = vaultState,
        currentVault = viewModel.currentVault,
        setVault = { vault, onFailed, onSuccess ->
            viewModel.setVault(
                vault = vault,
                onFailed = {
                    printError(TAG, "Vault set failed: $it")
                    onFailed(it)
                },
                onSuccess = {
                    printInfo(TAG, "Vault switched to ${vault.name}")
                    onSuccess()
                }
            )
        },
        eventHandler = eventHandler,
        lifecycleOwner = ProcessLifecycleOwner.get(),
        gateDeviceAuth = gateDeviceAuth,
        vaultDeviceAuth = vaultDeviceAuth,
        deviceSecurityAvailable = deviceSecurityAvailable,
        resolveGateAction = {
            val mode = VaultPasswordManager.getGateMode(context)
            val credential = if (mode == GateMode.CUSTOM) {
                VaultPasswordManager.getCredentialStatus(
                    context,
                    VaultPasswordManager.GATE_UUID
                )
            } else null
            VaultGatePolicy.resolve(mode, gateDeviceAuth.isSupported, credential)
        },
        shouldSkipVaultAuth = {
            VaultGatePolicy.shouldSkipVaultAuth(
                mode = VaultPasswordManager.getGateMode(context),
                lockAllVaults = VaultPasswordManager.getVaultLockAll(context)
            )
        },
        vaultCredentialStatus = { vault ->
            VaultPasswordManager.getCredentialStatus(context, vault.uuid)
        },
        verifyGateSecret = { secret ->
            VaultPasswordManager.verifyPassword(
                context,
                VaultPasswordManager.GATE_UUID,
                secret
            )
        },
        verifyVaultSecret = { vault, secret ->
            VaultPasswordManager.verifyPassword(context, vault.uuid, secret)
        },
        readLockAllVaults = { VaultPasswordManager.getVaultLockAll(context) },
        writeGateMode = { mode -> VaultPasswordManager.setGateMode(context, mode) },
        writeGateSecret = { type, secret ->
            VaultPasswordManager.setPassword(
                context,
                VaultPasswordManager.GATE_UUID,
                secret,
                type
            )
        },
        writeVaultSecret = { vault, type, secret ->
            VaultPasswordManager.setPassword(context, vault.uuid, secret, type)
        },
        writeLockAllVaults = { enabled ->
            VaultPasswordManager.setVaultLockAll(context, enabled)
        },
        onSystemBarStyleChange = { darkIcons ->
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                ) { darkIcons },
                navigationBarStyle = SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT
                ) { darkIcons }
            )
        },
        readGateMode = { VaultPasswordManager.getGateMode(context) },
        openSecuritySettings = {
            runCatching {
                context.startActivity(
                    Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure { printError(TAG, "Could not open security settings", it) }
        },
        vaultDisplay = { sharedTransitionScope,
                         animatedContentScope,
                         onNavigateUp,
                         onMediaClick,
                         onVaultDeleted,
                         onAuthenticateVault,
                         requestAddMedia,
                         onAddMediaRequestHandled ->
            VaultDisplay(
                requestAddMedia = requestAddMedia,
                onAddMediaRequestHandled = onAddMediaRequestHandled,
                globalNavigateUp = onNavigateUp,
                vaultState = vaultState,
                currentVault = viewModel.currentVault,
                createMediaState = viewModel::createMediaState,
                deleteLeftovers = viewModel::deleteLeftovers,
                deleteVault = viewModel::deleteVault,
                setVault = { vault -> viewModel.setVault(vault) {} },
                onAuthenticateVault = onAuthenticateVault,
                onVaultDeleted = onVaultDeleted,
                restoreVault = viewModel::restoreVault,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedContentScope,
                workerProgress = viewModel.progress,
                workerIsRunning = viewModel.isRunning,
                metadataState = metadataState,
                encryptAndRequestDeletion = viewModel::encryptAndRequestDeletion,
                addMediaKeepOriginals = viewModel::addMediaKeepOriginals,
                pendingDeletions = viewModel.pendingDeletions,
                userMessage = viewModel.userMessage,
                onMediaClick = onMediaClick,
            )
        },
        mediaViewer = { sharedTransitionScope,
                        animatedVisibilityScope,
                        mediaId,
                        onDismissRequest,
                        viewerSessionKey,
                        dismissBridge ->
            val currentVaultValue by viewModel.currentVault.collectAsStateWithLifecycle()
            val mediaState = remember(currentVaultValue) {
                viewModel.createMediaState(currentVaultValue)
            }.collectAsStateWithLifecycle()
            MediaViewScreenRoute(
                toggleRotate = toggleRotate,
                paddingValues = paddingValues,
                mediaId = mediaId,
                mediaState = mediaState,
                vaultState = vaultState,
                albumsState = albumState,
                metadataState = metadataState,
                currentVault = currentVaultValue,
                restoreMedia = viewModel::restoreMedia,
                deleteMedia = viewModel::deleteMedia,
                allowBlur = allowBlur,
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = animatedVisibilityScope,
                onDismissRequest = onDismissRequest,
                viewerSessionKey = viewerSessionKey,
                dismissBridge = dismissBridge,
            )
        },
        viewerExitVisual = { bridge -> ViewerDismissExitVisual(bridge) },
        viewerFlightLayer = { bridge -> ViewerDismissFlightLayer(bridge) },
    )
}
