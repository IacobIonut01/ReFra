/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import com.dot.gallery.cloud.core.CloudUri
import com.dot.gallery.cloud.core.MediaCapabilityProvider
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.cloud.core.ProviderRegistry
import com.dot.gallery.core.MediaHandler
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Owns the cloud/remote-selection business logic that used to live inline in
 * [com.dot.gallery.core.presentation.components.SelectionSheet]:
 *  - resolving which provider(s) back a selection and the actions they support,
 *  - delegating favorite / trash / delete / download to the [MediaHandler].
 *
 * The composable stays purely presentational: it observes [capabilitiesFor] to
 * decide which buttons to show and calls the action methods on user intent.
 */
@HiltViewModel
class CloudSelectionViewModel @Inject constructor(
    private val registry: ProviderRegistry,
    private val mediaHandler: MediaHandler,
) : ViewModel() {

    /** True when [media] is non-empty and every item is remote/cloud. */
    fun isCloudSelection(media: List<Media>): Boolean =
        media.isNotEmpty() && media.all { it.isCloud }

    /**
     * Intersection of provider capabilities across every item in [media] — an action
     * is only offered when *all* selected items' providers support it. Returns an empty
     * set when the selection isn't entirely cloud media.
     */
    fun capabilitiesFor(media: List<Media>): Set<ProviderCapability> {
        if (!isCloudSelection(media)) return emptySet()
        return media
            .map { providerFor(it)?.capabilities ?: emptySet() }
            .reduceOrNull { acc, caps -> acc intersect caps }
            ?: emptySet()
    }

    fun supportsFavorite(media: List<Media>): Boolean =
        ProviderCapability.FAVORITE in capabilitiesFor(media)

    fun supportsTrash(media: List<Media>): Boolean =
        ProviderCapability.TRASH in capabilitiesFor(media)

    fun supportsTrash(media: Media): Boolean = supportsTrash(listOf(media))

    /** Resolve only the exact account carried by the media URI; actions never cross accounts. */
    private fun providerFor(media: Media): MediaCapabilityProvider? {
        val cloudUri = CloudUri.parse(media.getUri().toString()) ?: return null
        if (cloudUri.configId <= 0L) return null
        return registry.getByConfigId(cloudUri.configId)
            ?.takeIf { it.providerType == cloudUri.providerType }
    }

    // === Actions (thin delegations to the shared MediaHandler) ===

    suspend fun <T : Media> toggleFavorite(
        result: ActivityResultLauncher<IntentSenderRequest>,
        media: List<T>
    ) = mediaHandler.toggleFavorite(result, media)

    /** Downloads the selected cloud originals into the device's MediaStore. Returns the saved count. */
    suspend fun <T : Media> download(media: List<T>): Result<Int> =
        mediaHandler.downloadCloudMedia(media)

    /** Recoverable server-side trash (providers that declare [ProviderCapability.TRASH]). */
    suspend fun <T : Media> trash(
        result: ActivityResultLauncher<IntentSenderRequest>,
        media: List<T>
    ) = mediaHandler.trashMedia(result, media, trash = true)

    /** Permanent hard delete on the server. */
    suspend fun <T : Media> delete(
        result: ActivityResultLauncher<IntentSenderRequest>,
        media: List<T>
    ) = mediaHandler.deleteMedia(result, media)
}
