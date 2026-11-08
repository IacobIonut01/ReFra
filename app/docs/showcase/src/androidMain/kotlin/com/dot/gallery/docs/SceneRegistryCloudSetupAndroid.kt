/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dot.gallery.cloud.core.ConnectionState
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.availableRemoteTypes
import com.dot.gallery.cloud.ui.AddServerUiState
import com.dot.gallery.cloud.ui.CloudAccountsContent
import com.dot.gallery.cloud.ui.CloudAccountsContentActions
import com.dot.gallery.cloud.ui.CloudAccountsContentState
import com.dot.gallery.cloud.ui.CloudAddServerActions
import com.dot.gallery.cloud.ui.CloudAddServerContent
import com.dot.gallery.cloud.ui.CloudAuthenticationState
import com.dot.gallery.cloud.ui.CloudSetupStep
import com.dot.gallery.data.entity.CloudServerConfigEntity

/**
 * Cloud accounts and per-provider setup wizard scenes. They host the real
 * [CloudAccountsContent] and [CloudAddServerContent] (same composables the
 * production screens delegate to) with sample accounts — every URL, credential
 * and display name here is fictional, every callback is in-memory, and no
 * network request, auth flow or database write is possible.
 */
val cloudSetupScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "cloud-accounts",
        title = "Cloud accounts",
        pathTrace = listOf("Settings", "Cloud accounts"),
        states = listOf("empty", "connected", "error", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { state -> DocsCloudAccountsScene(state) },
    DocsScene(
        id = "cloud-setup-immich",
        title = "Immich setup",
        pathTrace = listOf("Settings", "Cloud accounts", "Add Immich"),
        states = listOf("server", "credentials", "review"),
    ) { state -> DocsCloudSetupScene(ProviderType.IMMICH, state) },
    DocsScene(
        id = "cloud-setup-nextcloud",
        title = "Nextcloud setup",
        pathTrace = listOf("Settings", "Cloud accounts", "Add Nextcloud"),
        states = listOf("server", "browser", "manual", "verified", "waiting"),
    ) { state -> DocsCloudSetupScene(ProviderType.NEXTCLOUD, state) },
    DocsScene(
        id = "cloud-setup-owncloud",
        title = "ownCloud setup",
        pathTrace = listOf("Settings", "Cloud accounts", "Add ownCloud"),
        states = listOf("server", "credentials"),
    ) { state -> DocsCloudSetupScene(ProviderType.OWNCLOUD, state) },
    DocsScene(
        id = "cloud-setup-webdav",
        title = "WebDAV setup",
        pathTrace = listOf("Settings", "Cloud accounts", "Add WebDAV"),
        states = listOf("server", "credentials"),
    ) { state -> DocsCloudSetupScene(ProviderType.WEBDAV, state) },
    DocsScene(
        id = "cloud-setup-smb",
        title = "SMB setup",
        pathTrace = listOf("Settings", "Cloud accounts", "Add SMB share"),
        states = listOf("server", "credentials", "guest"),
    ) { state -> DocsCloudSetupScene(ProviderType.SMB, state) },
    DocsScene(
        id = "cloud-setup-nfs",
        title = "NFS setup",
        pathTrace = listOf("Settings", "Cloud accounts", "Add NFS export"),
        states = listOf("server", "credentials"),
    ) { state -> DocsCloudSetupScene(ProviderType.NFS, state) },
    DocsScene(
        id = "cloud-setup-options",
        title = "Cloud setup options",
        pathTrace = listOf("Settings", "Cloud accounts", "Add Immich"),
        states = listOf("networking", "folders", "review"),
    ) { state -> DocsCloudSetupScene(ProviderType.IMMICH, state) },
)

// ── Accounts list ─────────────────────────────────────────────────────────

private val immichCapabilities = setOf(
    ProviderCapability.REMOTE_ASSETS, ProviderCapability.REMOTE_ALBUMS,
    ProviderCapability.SYNC, ProviderCapability.ALBUM_WRITE,
    ProviderCapability.SMART_SEARCH, ProviderCapability.PEOPLE,
    ProviderCapability.MAP, ProviderCapability.ARCHIVE,
    ProviderCapability.MEMORIES, ProviderCapability.FAVORITE,
    ProviderCapability.TRASH, ProviderCapability.SHARE_CREATE,
    ProviderCapability.SHARE_MANAGE, ProviderCapability.TAGS,
    ProviderCapability.OCR, ProviderCapability.TEXT_SEARCH,
)

private val smbCapabilities = setOf(
    ProviderCapability.REMOTE_ASSETS, ProviderCapability.REMOTE_ALBUMS,
    ProviderCapability.SYNC, ProviderCapability.ALBUM_WRITE,
    ProviderCapability.TEXT_SEARCH,
)

/** Fictional sample accounts — no host resolves, nothing is contacted. */
private val sampleImmich = CloudServerConfigEntity(
    id = 11L,
    providerType = ProviderType.IMMICH,
    serverUrl = "https://photos.example.com",
    displayName = "Home photos",
)

private val sampleSmb = CloudServerConfigEntity(
    id = 12L,
    providerType = ProviderType.SMB,
    serverUrl = "smb://nas.example.com/Photos",
    displayName = "My NAS",
    username = "alex",
)

@Composable
fun DocsCloudAccountsScene(state: String?) {
    // Keep connection states mutable so a Sync tap visibly flips a card to
    // "authenticating" — entirely in memory.
    var connStates by remember(state) {
        mutableStateOf(
            when (state) {
                "connected" -> mapOf(
                    11L to ConnectionState.CONNECTED,
                    12L to ConnectionState.CONNECTED,
                )
                "error" -> mapOf(12L to ConnectionState.ERROR)
                else -> emptyMap()
            }
        )
    }
    val contentState = when (state) {
        "connected" -> CloudAccountsContentState(
            configs = listOf(sampleImmich, sampleSmb),
            connectionStates = connStates,
            assetCounts = mapOf(11L to 12, 12L to 12),
            availableProviders = ProviderType.availableRemoteTypes(),
            capabilitiesByType = mapOf(
                ProviderType.IMMICH to immichCapabilities,
                ProviderType.SMB to smbCapabilities,
            ),
            onLocalNetwork = true,
        )
        "error" -> CloudAccountsContentState(
            configs = listOf(sampleSmb),
            connectionStates = connStates,
            assetCounts = mapOf(12L to 12),
            availableProviders = ProviderType.availableRemoteTypes(),
            capabilitiesByType = mapOf(ProviderType.SMB to smbCapabilities),
            onLocalNetwork = false,
        )
        else -> CloudAccountsContentState(
            configs = emptyList(),
            connectionStates = connStates,
            availableProviders = ProviderType.availableRemoteTypes(),
            capabilitiesByType = emptyMap(),
            onLocalNetwork = true,
        )
    }
    CloudAccountsContent(
        state = contentState,
        actions = CloudAccountsContentActions(
            onSync = { connStates = connStates + (it to ConnectionState.AUTHENTICATING) },
        ),
    )
}

// ── Setup wizard ──────────────────────────────────────────────────────────

private fun setupStateFor(type: ProviderType, state: String?): AddServerUiState {
    val base = AddServerUiState(providerType = type)
    return when (type) {
        ProviderType.IMMICH -> when (state) {
            "server" -> base.copy(serverUrl = "https://photos.example.com")
            "credentials" -> base.copy(
                serverUrl = "https://photos.example.com",
                apiKey = "example-api-key",
                displayName = "Home photos",
            )
            // Used by both cloud-setup-immich/review and cloud-setup-options.
            "networking" -> base.copy(
                serverUrl = "https://photos.example.com",
                apiKey = "example-api-key",
                displayName = "Home photos",
                autoUrlSwitch = true,
                localWifiSsid = "Home Wi-Fi",
                localServerUrl = "https://photos.home.example.com",
                testSuccess = true,
            )
            "folders" -> base.copy(
                serverUrl = "https://photos.example.com",
                apiKey = "example-api-key",
                displayName = "Home photos",
                autoUrlSwitch = true,
                localWifiSsid = "Home Wi-Fi",
                localServerUrl = "https://photos.home.example.com",
                testSuccess = true,
                selectedLocalAlbumIds = setOf(DocsGalleryData.ALBUM_CAMERA),
            )
            "review" -> base.copy(
                serverUrl = "https://photos.example.com",
                apiKey = "example-api-key",
                displayName = "Home photos",
                autoUrlSwitch = true,
                localWifiSsid = "Home Wi-Fi",
                localServerUrl = "https://photos.home.example.com",
                testSuccess = true,
                selectedLocalAlbumIds = setOf(DocsGalleryData.ALBUM_CAMERA),
            )
            else -> base
        }
        ProviderType.NEXTCLOUD -> when (state) {
            "server" -> base.copy(serverUrl = "https://cloud.example.com/nextcloud")
            "browser" -> base.copy(serverUrl = "https://cloud.example.com/nextcloud")
            "manual" -> base.copy(
                serverUrl = "https://cloud.example.com/nextcloud",
                username = "alex",
                password = "example-app-password",
                displayName = "Work Nextcloud",
            )
            "verified" -> base.copy(
                serverUrl = "https://cloud.example.com/nextcloud",
                // A verified browser sign-in still carries the account
                // credentials, so the credential predicate enables Next.
                username = "alex",
                password = "example-app-password",
                displayName = "Work Nextcloud",
                authenticationState = CloudAuthenticationState.Verified(
                    serverUrl = "https://cloud.example.com/nextcloud",
                    username = "alex",
                ),
                testSuccess = true,
            )
            "waiting" -> base.copy(
                serverUrl = "https://cloud.example.com/nextcloud",
                authenticationState = CloudAuthenticationState.WaitingForBrowser,
            )
            else -> base
        }
        ProviderType.OWNCLOUD -> when (state) {
            "server" -> base.copy(serverUrl = "https://cloud.example.com/owncloud")
            "credentials" -> base.copy(
                serverUrl = "https://cloud.example.com/owncloud",
                username = "alex",
                password = "example-app-password",
            )
            else -> base
        }
        ProviderType.WEBDAV -> when (state) {
            "server" -> base.copy(serverUrl = "https://files.example.com/dav/photos/")
            "credentials" -> base.copy(
                serverUrl = "https://files.example.com/dav/photos/",
                username = "alex",
                password = "example-app-password",
            )
            else -> base
        }
        ProviderType.SMB -> when (state) {
            "server" -> base.copy(serverUrl = "smb://nas.example.com/Photos/Family")
            "credentials" -> base.copy(
                serverUrl = "smb://nas.example.com/Photos/Family",
                username = "alex",
                password = "example-password",
                displayName = "My NAS",
            )
            // Guest access: no credentials at all.
            "guest" -> base.copy(serverUrl = "smb://nas.example.com/Photos/Family")
            else -> base
        }
        ProviderType.NFS -> when (state) {
            "server" -> base.copy(serverUrl = "nfs://nas.example.com/export/photos")
            "credentials" -> base.copy(
                serverUrl = "nfs://nas.example.com/export/photos",
                // The NFS descriptor reuses the username field for uid:gid.
                username = "1000:1000",
                displayName = "NAS photos",
            )
            else -> base
        }
        else -> base
    }
}

private fun setupStepFor(state: String?): CloudSetupStep = when (state) {
    "credentials", "browser", "manual", "verified", "waiting", "guest" -> CloudSetupStep.CREDENTIALS
    "networking" -> CloudSetupStep.NETWORKING
    "folders" -> CloudSetupStep.SYNC
    "review" -> CloudSetupStep.REVIEW
    else -> CloudSetupStep.SERVER
}

/**
 * The real wizard posed on the requested step. Field edits round-trip through
 * in-memory state so captures stay interactive; test-connection and auth are
 * never invoked — only the posed [AddServerUiState] decides which auth affordance
 * is on screen.
 */
@Composable
fun DocsCloudSetupScene(providerType: ProviderType, state: String?) {
    val localAlbums = remember { DocsGalleryData.albums() }
    var serverState by remember(providerType, state) {
        mutableStateOf(setupStateFor(providerType, state))
    }
    CloudAddServerContent(
        state = serverState,
        localAlbums = localAlbums,
        isEditMode = false,
        // Interactive (browser) auth exists only for the Nextcloud flow.
        supportsInteractiveAuth = providerType == ProviderType.NEXTCLOUD,
        actions = CloudAddServerActions(
            updateServerUrl = { serverState = serverState.copy(serverUrl = it) },
            updateDisplayName = { serverState = serverState.copy(displayName = it) },
            updateApiKey = { serverState = serverState.copy(apiKey = it) },
            updateUsername = { serverState = serverState.copy(username = it) },
            updatePassword = { serverState = serverState.copy(password = it) },
            updateAutoUrlSwitch = { serverState = serverState.copy(autoUrlSwitch = it) },
            updateLocalWifiSsid = { serverState = serverState.copy(localWifiSsid = it) },
            updateLocalServerUrl = { serverState = serverState.copy(localServerUrl = it) },
            toggleLocalAlbum = { id ->
                serverState = serverState.copy(
                    selectedLocalAlbumIds = if (id in serverState.selectedLocalAlbumIds)
                        serverState.selectedLocalAlbumIds - id
                    else serverState.selectedLocalAlbumIds + id,
                )
            },
        ),
        onNavigateUp = {},
        initialStep = setupStepFor(state),
        // Nextcloud shows the manual form collapsed behind its sign-in button;
        // other providers have no interactive auth, so null keeps the default
        // (manual form shown) behavior.
        initialManualAuth = when {
            state == "manual" -> true
            providerType == ProviderType.NEXTCLOUD -> false
            else -> null
        },
    )
}
