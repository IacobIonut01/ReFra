/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.setup

import com.dot.gallery.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.generated.resources.cloud_provider_desc_nfs
import com.dot.gallery.generated.resources.cloud_provider_desc_smb
import com.dot.gallery.generated.resources.cloud_provider_desc_webdav
import com.dot.gallery.generated.resources.cloud_provider_desc_owncloud
import com.dot.gallery.generated.resources.cloud_provider_desc_nextcloud
import com.dot.gallery.generated.resources.cloud_provider_desc_immich
import com.dot.gallery.cloud.ui.components.ProviderBadge
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.LocalActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.ui.CloudAccountsViewModel
import com.dot.gallery.cloud.ui.descriptor.ProviderUiDescriptors
import com.dot.gallery.core.AppConstants
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings
import com.dot.gallery.core.Settings.Misc.rememberAppLogoAlias
import com.dot.gallery.core.Settings.Misc.rememberAppNameAlias
import com.dot.gallery.core.ml.ModelGroup
import com.dot.gallery.core.ml.hasBundledModelAssets
import com.dot.gallery.core.navigate
import com.dot.gallery.core.presentation.components.util.MediaAccessState
import com.dot.gallery.feature.setup.ui.SetupActions
import com.dot.gallery.feature.setup.ui.SetupAiModelsActions
import com.dot.gallery.feature.setup.ui.SetupAiModelsState
import com.dot.gallery.feature.setup.ui.SetupCloudAccountUi
import com.dot.gallery.feature.setup.ui.SetupCloudActions
import com.dot.gallery.feature.setup.ui.SetupCloudState
import com.dot.gallery.feature.setup.ui.SetupContent
import com.dot.gallery.feature.setup.ui.LocalSetupReduceMotion
import com.dot.gallery.feature.help.ui.rememberReduceMotion
import com.dot.gallery.feature.setup.ui.SetupLooksFeelActions
import com.dot.gallery.feature.setup.ui.SetupLooksFeelState
import com.dot.gallery.feature.setup.ui.SetupMediaAccess
import com.dot.gallery.feature.setup.ui.SetupModelUi
import com.dot.gallery.feature.setup.ui.SetupPage
import com.dot.gallery.feature.setup.ui.SetupPermission
import com.dot.gallery.feature.setup.ui.SetupPermissionUi
import com.dot.gallery.feature.setup.ui.SetupPermissionsActions
import com.dot.gallery.feature.setup.ui.SetupPermissionsState
import com.dot.gallery.feature.setup.ui.SetupProviderCapability
import com.dot.gallery.feature.setup.ui.SetupProviderUi
import com.dot.gallery.feature.setup.ui.SetupState
import com.dot.gallery.feature.settings.subsettings.SmartFeaturesViewModel
import com.dot.gallery.core.presentation.util.RepeatOnResume
import com.dot.gallery.core.presentation.util.Screen
import com.dot.gallery.app.presentation.util.changeAppAlias
import com.dot.gallery.app.presentation.util.currentLauncherAlias
import com.dot.gallery.app.presentation.util.launcherAliasFor
import com.dot.gallery.app.presentation.util.restartApplication
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.dot.gallery.core.presentation.components.util.hasMediaAccess
import com.dot.gallery.app.presentation.util.isManageFilesAllowed
import com.dot.gallery.app.presentation.util.launchManageMedia
import com.dot.gallery.cloud.core.availableRemoteTypes
import com.dot.gallery.feature.setup.ui.defaultSetupCapabilities
import com.dot.gallery.cloud.core.hasAnyRemoteProvider
import com.dot.gallery.app.presentation.util.launchManageFiles
import com.dot.gallery.core.presentation.components.util.mediaAccessState
import com.dot.gallery.injection.injectViewModel
import com.dot.gallery.R


/**
 * Android shell for [SetupContent]: wires the runtime permission launchers, the cloud
 * account/provider state, the AI model manager, the launcher-alias plumbing and the
 * finish/restart dance. The wizard UI itself lives in `:feature:setup`.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SetupScreen(onComplete: () -> Unit = {}) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handler = LocalEventHandler.current
    val appNameAlias by rememberAppNameAlias()
    val appLogoAlias by rememberAppLogoAlias()

    val variant = remember {
        SetupVariant(
            declaredPermissions = context.declaredPermissions(),
            sdkInt = Build.VERSION.SDK_INT,
            allowAllFilesAccess = isManageFilesAllowed,
            hasRemoteProvider = ProviderType.hasAnyRemoteProvider(),
            modelsBundled = context.hasBundledModelAssets(),
        )
    }
    val pages = remember(variant) { variant.pages }

    // region Permissions page — runtime grants stay app-side (accompanist + platform intents)
    var mediaAccess by remember { mutableStateOf(context.mediaAccessState()) }
    val mediaPermissions = rememberMultiplePermissionsState(
        permissions = AppConstants.PERMISSIONS,
        onPermissionsResult = { mediaAccess = context.mediaAccessState() }
    )
    var locationGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_MEDIA_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var locationRequestAttempted by rememberSaveable { mutableStateOf(false) }
    val locationPermission = rememberPermissionState(
        permission = Manifest.permission.ACCESS_MEDIA_LOCATION,
        onPermissionResult = {
            locationRequestAttempted = true
            locationGranted = it
        }
    )
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val notificationPermission = rememberPermissionState(
        permission = Manifest.permission.POST_NOTIFICATIONS,
        onPermissionResult = { notificationsGranted = it }
    )
    var localNetworkGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 37 ||
                ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_LOCAL_NETWORK
                ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val localNetworkPermission = rememberPermissionState(
        permission = Manifest.permission.ACCESS_LOCAL_NETWORK,
        onPermissionResult = { localNetworkGranted = it }
    )
    var isStorageManager by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Environment.isExternalStorageManager())
    }
    var canManageMedia by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context))
    }

    RepeatOnResume {
        mediaAccess = context.mediaAccessState()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            locationGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isStorageManager = Environment.isExternalStorageManager()
            canManageMedia = MediaStore.canManageMedia(context)
            if (Build.VERSION.SDK_INT >= 37) {
                localNetworkGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_LOCAL_NETWORK
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    val coreGranted = mediaAccess != MediaAccessState.NONE
    val permissionItems = remember(
        mediaAccess, locationGranted, locationRequestAttempted,
        localNetworkGranted, notificationsGranted, canManageMedia, isStorageManager
    ) {
        variant.permissions.map { permission ->
            when (permission) {
                SetupPermission.IMAGES, SetupPermission.VIDEO -> SetupPermissionUi(permission, granted = coreGranted)
                SetupPermission.MEDIA_LOCATION -> SetupPermissionUi(
                    permission,
                    granted = locationGranted,
                    requestAttempted = locationRequestAttempted
                )
                SetupPermission.INTERNET -> SetupPermissionUi(permission)
                SetupPermission.LOCAL_NETWORK -> SetupPermissionUi(permission, granted = localNetworkGranted)
                SetupPermission.NOTIFICATIONS -> SetupPermissionUi(permission, granted = notificationsGranted)
                SetupPermission.MANAGE_MEDIA -> SetupPermissionUi(permission, granted = canManageMedia)
                SetupPermission.MANAGE_FILES -> SetupPermissionUi(permission, granted = isStorageManager)
            }
        }
    }

    val checkMediaAccess = {
        mediaAccess = context.mediaAccessState()
        mediaAccess != MediaAccessState.NONE
    }

    val onGrantPermission: (SetupPermission) -> Unit = { permission ->
        when (permission) {
            SetupPermission.MEDIA_LOCATION -> {
                if (locationRequestAttempted && !locationGranted) {
                    context.startActivity(
                        Intent(
                            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                    )
                } else {
                    locationPermission.launchPermissionRequest()
                }
            }
            SetupPermission.LOCAL_NETWORK -> localNetworkPermission.launchPermissionRequest()
            SetupPermission.NOTIFICATIONS -> notificationPermission.launchPermissionRequest()
            SetupPermission.MANAGE_MEDIA -> scope.launch { context.launchManageMedia() }
            SetupPermission.MANAGE_FILES -> scope.launch { context.launchManageFiles() }
            else -> Unit
        }
    }
    // endregion

    // region Cloud page — account list + provider cards (cloud builds only)
    val cloudState = if (SetupPage.CLOUD in pages) {
        val cloudViewModel = injectViewModel<CloudAccountsViewModel> {cloudAccountsViewModel}
        val configs by cloudViewModel.accountState.collectAsStateWithLifecycle()
        val providerTypes = remember { ProviderType.availableRemoteTypes() }
        SetupCloudState(
            accounts = configs.map { config ->
                SetupCloudAccountUi(
                    providerName = config.providerType.displayName,
                    serverUrl = config.serverUrl
                )
            },
            providers = providerTypes.map { type ->
                val live = remember(type) { cloudViewModel.capabilitiesOf(type) }
                SetupProviderUi(
                    type = type,
                    icon = remember(type) { ProviderUiDescriptors.forType(type).icon },
                    capabilities = if (live.isNotEmpty()) {
                        live.mapToSetupCapabilities()
                    } else {
                        defaultSetupCapabilities(type)
                    },
                    description = setupProviderDescription(type),
                    badge = { ProviderBadge(providerType = type) },
                )
            }
        )
    } else {
        SetupCloudState()
    }
    // endregion

    // region AI models page — ModelManager flows (skipped in offline builds without bundled models)
    var aiModelsState = SetupAiModelsState()
    var aiModelsActions = SetupAiModelsActions()
    if (SetupPage.AI_MODELS in pages) {
        val smartViewModel = injectViewModel<SmartFeaturesViewModel> {smartFeaturesViewModel}
        val searchModel by smartViewModel.activeSearchModel.collectAsStateWithLifecycle()
        val searchStatus by smartViewModel.modelStatus(searchModel.group).collectAsStateWithLifecycle()
        val searchProgress by smartViewModel.downloadProgress(searchModel.group).collectAsStateWithLifecycle()
        val cutoutStatus by smartViewModel.modelStatus(ModelGroup.CUTOUT).collectAsStateWithLifecycle()
        val cutoutProgress by smartViewModel.downloadProgress(ModelGroup.CUTOUT).collectAsStateWithLifecycle()
        val faceDetectStatus by smartViewModel.modelStatus(ModelGroup.FACE_DETECT).collectAsStateWithLifecycle()
        val faceDetectProgress by smartViewModel.downloadProgress(ModelGroup.FACE_DETECT).collectAsStateWithLifecycle()
        val faceRecogStatus by smartViewModel.modelStatus(ModelGroup.FACE_RECOGNITION).collectAsStateWithLifecycle()
        val faceRecogProgress by smartViewModel.downloadProgress(ModelGroup.FACE_RECOGNITION).collectAsStateWithLifecycle()
        aiModelsState = SetupAiModelsState(
            isBundled = variant.modelsBundled,
            hasInternet = variant.hasInternet,
            searchGroup = searchModel.group,
            search = SetupModelUi(status = searchStatus, progress = searchProgress),
            cutout = SetupModelUi(status = cutoutStatus, progress = cutoutProgress),
            faceDetect = SetupModelUi(status = faceDetectStatus, progress = faceDetectProgress),
            faceRecognition = SetupModelUi(status = faceRecogStatus, progress = faceRecogProgress),
        )
        aiModelsActions = SetupAiModelsActions(
            onDownload = smartViewModel::downloadModels,
            onOpenFeature = { name ->
                handler.navigate(Screen.IntelligenceFeatureScreen.feature(name, offer = true))
            },
        )
    }
    // endregion

    // Launcher artwork is mipmap-bound — resolved here and handed to the wizard as a painter.
    val logoPainter: @Composable (String) -> Painter = { alias ->
        rememberDrawablePainter(
            drawable = AppCompatResources.getDrawable(context, launcherIconFor(alias))
        )
    }

    CompositionLocalProvider(LocalSetupReduceMotion provides rememberReduceMotion()) {
    SetupContent(
        state = SetupState(
            pages = pages,
            permissions = SetupPermissionsState(
                mediaAccess = mediaAccess.toSetupMediaAccess(),
                permissions = permissionItems
            ),
            looksFeel = SetupLooksFeelState(),
            cloud = cloudState,
            aiModels = aiModelsState,
        ),
        actions = SetupActions(
            onExit = { activity?.finish() },
            hasMediaAccess = checkMediaAccess,
            onFinish = {
                // Apply the remembered app name + logo now that setup is complete. Restart the
                // app (so the launcher reflects the change immediately) only when it actually
                // differs from what is currently applied; otherwise continue normally.
                if (context.currentLauncherAlias() != launcherAliasFor(appNameAlias, appLogoAlias)) {
                    context.changeAppAlias(appNameAlias, appLogoAlias)
                    // Give DataStore a moment to persist the completed-setup flag before the
                    // process is killed and relaunched, otherwise setup could show again.
                    scope.launch {
                        delay(300)
                        context.restartApplication()
                    }
                } else {
                    onComplete()
                }
            },
            permissions = SetupPermissionsActions(
                onRequestCorePermissions = {
                    scope.launch { mediaPermissions.launchMultiplePermissionRequest() }
                },
                recheckMediaAccess = checkMediaAccess,
                onGrant = onGrantPermission
            ),
            looksFeel = SetupLooksFeelActions(
                onOpenAppearance = { handler.navigate(Screen.SettingsAppearanceScreen()) }
            ),
            cloud = SetupCloudActions(
                onSetupProvider = { type ->
                    handler.navigate(Screen.CloudAddServerScreen.providerType(type.name))
                }
            ),
            aiModels = aiModelsActions
        ),
        logoPainter = logoPainter
    )
    }
}

private fun MediaAccessState.toSetupMediaAccess(): SetupMediaAccess = when (this) {
    MediaAccessState.NONE -> SetupMediaAccess.NONE
    MediaAccessState.LIMITED -> SetupMediaAccess.LIMITED
    MediaAccessState.FULL -> SetupMediaAccess.FULL
}

private fun Set<ProviderCapability>.mapToSetupCapabilities(): Set<SetupProviderCapability> =
    mapNotNullTo(mutableSetOf()) { capability ->
        runCatching { SetupProviderCapability.valueOf(capability.name) }.getOrNull()
    }

/** Round launcher mipmap for the user's chosen logo alias. */
internal fun launcherIconFor(logoAlias: String): Int =
    when (logoAlias) {
        Settings.Misc.ALIAS_GALLERY -> R.mipmap.ic_launcher_gallery_round
        Settings.Misc.ALIAS_MONO -> R.mipmap.ic_launcher_mono_round
        Settings.Misc.ALIAS_GALLERY_MONO -> R.mipmap.ic_launcher_gallery_mono_round
        else -> R.mipmap.ic_launcher_round
    }

/** The add-account list's one-line description of each provider, reused on the setup Cloud step. */
@Composable
private fun setupProviderDescription(type: ProviderType): String = when (type) {
    ProviderType.IMMICH -> stringResource(Res.string.cloud_provider_desc_immich)
    ProviderType.NEXTCLOUD -> stringResource(Res.string.cloud_provider_desc_nextcloud)
    ProviderType.OWNCLOUD -> stringResource(Res.string.cloud_provider_desc_owncloud)
    ProviderType.WEBDAV -> stringResource(Res.string.cloud_provider_desc_webdav)
    ProviderType.SMB -> stringResource(Res.string.cloud_provider_desc_smb)
    ProviderType.NFS -> stringResource(Res.string.cloud_provider_desc_nfs)
    else -> type.displayName
}
