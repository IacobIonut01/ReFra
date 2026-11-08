/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs.scenes

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.docs.showcase.generated.resources.Res
import com.dot.gallery.docs.showcase.generated.resources.docs_logo_gallery
import com.dot.gallery.docs.showcase.generated.resources.docs_logo_gallery_mono
import com.dot.gallery.docs.showcase.generated.resources.docs_logo_mono
import com.dot.gallery.docs.showcase.generated.resources.docs_logo_refra
import com.dot.gallery.docs.DocsGalleryData
import com.dot.gallery.docs.LocalDocsGallerySession
import com.dot.gallery.docs.LocalDocsNavigator
import com.dot.gallery.feature.setup.ui.SetupActions
import com.dot.gallery.feature.setup.ui.SetupAiModelsState
import com.dot.gallery.feature.setup.ui.SetupCloudState
import com.dot.gallery.feature.setup.ui.SetupContent
import com.dot.gallery.feature.setup.ui.SetupMediaAccess
import com.dot.gallery.feature.setup.ui.SetupPage
import com.dot.gallery.feature.setup.ui.SetupLooksFeelActions
import com.dot.gallery.feature.setup.ui.SetupPermission
import com.dot.gallery.feature.setup.ui.SetupPermissionUi
import com.dot.gallery.feature.setup.ui.SetupPermissionsActions
import com.dot.gallery.feature.setup.ui.SetupPermissionsState
import com.dot.gallery.feature.setup.ui.SetupProviderUi
import com.dot.gallery.feature.setup.ui.SetupState
import com.dot.gallery.feature.setup.ui.defaultSetupCapabilities
import com.dot.gallery.feature.vault.ui.VaultGateSetupContent
import com.dot.gallery.feature.vault.ui.VaultSelectContent
import com.dot.gallery.feature.vault.ui.VaultSetupContent
import com.dot.gallery.feature.vault.ui.PrivateFolderSecuritySetupContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.dot.gallery.domain.settings.ALIAS_GALLERY
import com.dot.gallery.domain.settings.ALIAS_GALLERY_MONO
import com.dot.gallery.domain.settings.ALIAS_MONO
import com.dot.gallery.domain.settings.ALIAS_REFRA
import org.jetbrains.compose.resources.painterResource

/**
 * Paints a launcher-style icon: the adaptive-icon background colour behind the
 * real foreground vector (copied from `commonApp` mipmaps into docs resources).
 */
private class DocsLogoPainter(
    private val background: Color,
    private val foreground: Painter,
) : Painter() {
    override val intrinsicSize: Size get() = foreground.intrinsicSize
    override fun DrawScope.onDraw() {
        drawRect(background, size = size)
        with(foreground) { draw(size) }
    }
}

/** Alias → real launcher artwork, matching the app's mipmap mapping. */
@Composable
internal fun docsLogoPainter(alias: String): Painter = when (alias) {
    ALIAS_GALLERY -> DocsLogoPainter(Color(0xFFFFFFFF), painterResource(Res.drawable.docs_logo_gallery))
    ALIAS_MONO -> DocsLogoPainter(Color(0xFFFFFFFF), painterResource(Res.drawable.docs_logo_mono))
    ALIAS_GALLERY_MONO -> DocsLogoPainter(Color(0xFFFFFFFF), painterResource(Res.drawable.docs_logo_gallery_mono))
    else -> DocsLogoPainter(Color(0xFFE5E5E8), painterResource(Res.drawable.docs_logo_refra))
}

private fun docsProviderIcon(type: ProviderType) = when (type) {
    ProviderType.IMMICH -> Icons.Outlined.Cloud
    ProviderType.OWNCLOUD, ProviderType.NEXTCLOUD -> Icons.Outlined.CloudQueue
    ProviderType.WEBDAV -> Icons.Outlined.CloudSync
    ProviderType.SMB, ProviderType.NFS -> Icons.Outlined.Storage
    else -> Icons.Outlined.Cloud
}

/**
 * The real first-launch wizard — every page is a named state; Continue/Back and
 * the page pickers drive the live preview without touching device permissions
 * or provider backends.
 */
@Composable
fun DocsSetupWizardScene(state: String?) {
    val navigator = LocalDocsNavigator.current
    var mediaAccess by rememberSaveable { mutableStateOf(SetupMediaAccess.FULL) }
    var permissions by remember {
        mutableStateOf(
            listOf(
                SetupPermissionUi(SetupPermission.IMAGES, granted = true, requestAttempted = true),
                SetupPermissionUi(SetupPermission.VIDEO, granted = true, requestAttempted = true),
                SetupPermissionUi(SetupPermission.NOTIFICATIONS),
            )
        )
    }
    SetupContent(
        state = SetupState(
            pages = SetupPage.entries.toList(),
            permissions = SetupPermissionsState(
                mediaAccess = mediaAccess,
                permissions = permissions,
            ),
            cloud = SetupCloudState(
                providers = ProviderType.remoteTypes().map { type ->
                    SetupProviderUi(
                        type = type,
                        icon = docsProviderIcon(type),
                        capabilities = defaultSetupCapabilities(type),
                        // Docs copy of the add-account descriptions (the app reads them from commonApp).
                        description = when (type) {
                            ProviderType.IMMICH -> "Keeps albums, people and shared links"
                            ProviderType.NEXTCLOUD -> "Sign in with your browser"
                            ProviderType.OWNCLOUD -> "Server address, username and app password"
                            ProviderType.WEBDAV -> "Any server that speaks WebDAV"
                            ProviderType.SMB -> "Windows shares and most NAS boxes"
                            ProviderType.NFS -> "Linux and NAS exports"
                            else -> null
                        },
                    )
                },
            ),
            aiModels = SetupAiModelsState(),
        ),
        actions = SetupActions(
            onExit = {},
            hasMediaAccess = { true },
            onFinish = { navigator?.navigateTo("browse-timeline") },
            looksFeel = SetupLooksFeelActions(
                onOpenAppearance = { navigator?.navigateTo("settings-appearance") },
            ),
            permissions = SetupPermissionsActions(
                onRequestCorePermissions = {
                    mediaAccess = SetupMediaAccess.FULL
                    permissions = permissions.map {
                        it.copy(granted = true, requestAttempted = true)
                    }
                },
                onGrant = { permission ->
                    permissions = permissions.map {
                        if (it.permission == permission) it.copy(granted = true, requestAttempted = true)
                        else it
                    }
                },
            ),
        ),
        logoPainter = { alias -> docsLogoPainter(alias) },
        initialPage = when (state) {
            "welcome" -> SetupPage.WELCOME
            "permissions" -> SetupPage.PERMISSIONS
            "appearance" -> SetupPage.LOOKS
            "cloud" -> SetupPage.CLOUD
            "models" -> SetupPage.AI_MODELS
            "tips" -> SetupPage.TIPS
            else -> null
        },
    )
}

@Composable
fun DocsVaultListScene(state: String?) {
    val session = LocalDocsGallerySession.current
        ?: error("DocsVaultListScene requires LocalDocsGallerySession")
    val navigator = LocalDocsNavigator.current
    val vaultState by session.distributor.vaultsMediaFlow.collectAsState()
    VaultSelectContent(
        vaultState = vaultState,
        onVaultSelected = {},
        onCreateVault = { navigator?.navigateTo("vault-setup") },
        onChangeGateSecurity = { navigator?.navigateTo("vault-gate") },
        onNavigateUp = { navigator?.navigateUp() },
    )
}

@Composable
fun DocsVaultSetupScene(state: String?) {
    val session = LocalDocsGallerySession.current!!
    val navigator = LocalDocsNavigator.current
    val vaultState by session.distributor.vaultsMediaFlow.collectAsState()
    VaultSetupContent(
        existingVaults = vaultState.vaults,
        deviceSecurityAvailable = state != "no-screen-lock",
        onCancel = { navigator?.navigateUp() },
        onCreate = { vault ->
            session.distributor.addVault(vault)
            navigator?.navigateTo("vault-list")
        },
    )
}

@Composable
fun DocsVaultGateScene(state: String?) {
    val navigator = LocalDocsNavigator.current
    var lockAll by rememberSaveable { mutableStateOf(true) }
    VaultGateSetupContent(
        lockAllVaults = lockAll,
        onBack = { navigator?.navigateUp() },
        onNoneSelected = {},
        onDeviceSecuritySelected = {},
        onCustomSecretSet = { _, _ -> },
        onLockAllVaultsChanged = { lockAll = it },
    )
}

@Composable
fun DocsPrivateFolderSecurityScene(state: String?) {
    val navigator = LocalDocsNavigator.current
    PrivateFolderSecuritySetupContent(
        onBack = { navigator?.navigateUp() },
        onNoneSelected = {},
        onDeviceSecuritySelected = {},
        onCustomSecretSet = { _, _ -> },
    )
}
