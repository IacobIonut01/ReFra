/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.mediaview

import com.dot.gallery.cloud.core.CloudAccountRuntimeSettings
import com.dot.gallery.cloud.core.CloudRuntimeSettings
import com.dot.gallery.feature_node.domain.model.Media
import com.dot.gallery.feature_node.domain.util.canMakeActions
import com.dot.gallery.feature_node.domain.util.getUri
import com.dot.gallery.feature_node.domain.util.isCloud
import com.dot.gallery.feature_node.domain.util.isEncrypted
import com.dot.gallery.feature_node.domain.util.isImage
import com.dot.gallery.feature_node.domain.util.isVideo
import com.dot.gallery.feature_node.domain.util.readUriOnly

/** Inputs that describe the source and provider guarantees for one viewer item. */
data class MediaActionPolicyInput(
    val isCloud: Boolean = false,
    val isEncrypted: Boolean = false,
    val isImage: Boolean = false,
    val isVideo: Boolean = false,
    val isReadUriOnly: Boolean = false,
    val sourceAllowsMutation: Boolean = false,
    val sourceAllowsDelete: Boolean = sourceAllowsMutation,
    val cloudReadOnly: Boolean = false,
    val providerSupportsFavorite: Boolean = false,
    val providerSupportsTrash: Boolean = false,
    val platformSupportsFavorite: Boolean = false,
    val vaultRestoreAvailable: Boolean = false,
    val vaultDeleteAvailable: Boolean = false,
)

/**
 * The complete set of viewer actions allowed for a media item.
 *
 * Action surfaces consume this value rather than duplicating source/read-only/provider checks. This
 * is intentionally immutable and platform-free so the policy matrix can be covered by JVM tests.
 */
data class MediaActionCapabilities(
    val share: Boolean,
    val visualSearch: Boolean,
    val copyToClipboard: Boolean,
    val favorite: Boolean,
    val edit: Boolean,
    val rotate: Boolean,
    val trash: Boolean,
    val hideInVault: Boolean,
    val restoreFromVault: Boolean,
    val copyOrMove: Boolean,
    val addToCollection: Boolean,
    val setAlbumCover: Boolean,
    val openExternally: Boolean,
    val download: Boolean,
    val extractFrames: Boolean,
    val exportMotionVideo: Boolean,
    val cutout: Boolean,
)

object MediaActionCapabilityPolicy {
    fun resolve(input: MediaActionPolicyInput): MediaActionCapabilities {
        val cloudLocked = input.isCloud && input.cloudReadOnly
        val mutableLocalSource = input.sourceAllowsMutation &&
            !input.isCloud &&
            !input.isEncrypted &&
            !input.isReadUriOnly
        val exportAllowed = !cloudLocked

        return MediaActionCapabilities(
            share = exportAllowed,
            // Sending to a visual-search provider is the same data-leaving-device action as
            // share, plus it needs a still image — the source itself or the current video frame.
            visualSearch = exportAllowed && (input.isImage || input.isVideo),
            copyToClipboard = exportAllowed,
            favorite = !cloudLocked && when {
                input.isCloud -> input.providerSupportsFavorite
                else -> mutableLocalSource && input.platformSupportsFavorite
            },
            edit = mutableLocalSource,
            rotate = mutableLocalSource && input.isImage,
            trash = !cloudLocked && when {
                input.isCloud -> input.providerSupportsTrash || input.sourceAllowsDelete
                input.isEncrypted -> input.vaultDeleteAvailable
                else -> input.sourceAllowsDelete
            },
            hideInVault = mutableLocalSource,
            restoreFromVault = input.isEncrypted && input.vaultRestoreAvailable,
            copyOrMove = mutableLocalSource || input.isCloud,
            addToCollection = mutableLocalSource,
            setAlbumCover = mutableLocalSource,
            openExternally = !input.isEncrypted && !input.isCloud,
            download = input.isCloud,
            extractFrames = exportAllowed,
            exportMotionVideo = mutableLocalSource,
            cutout = input.isImage && exportAllowed,
        )
    }
}

fun cloudMediaReadOnly(
    uriString: String,
    settingsByConfigId: Map<Long, CloudAccountRuntimeSettings>,
): Boolean = CloudRuntimeSettings
    .settingsForCloudUri(uriString, settingsByConfigId)
    .readOnlyMode

fun Media.isReadOnlyCloudMedia(
    settingsByConfigId: Map<Long, CloudAccountRuntimeSettings>,
): Boolean = isCloud && cloudMediaReadOnly(getUri().toString(), settingsByConfigId)

fun Media.viewerActionCapabilities(
    settingsByConfigId: Map<Long, CloudAccountRuntimeSettings>,
    providerSupportsFavorite: Boolean = false,
    providerSupportsTrash: Boolean = false,
    platformSupportsFavorite: Boolean = false,
    sourceAllowsDelete: Boolean = canMakeActions,
    vaultRestoreAvailable: Boolean = false,
    vaultDeleteAvailable: Boolean = false,
): MediaActionCapabilities = MediaActionCapabilityPolicy.resolve(
    MediaActionPolicyInput(
        isCloud = isCloud,
        isEncrypted = isEncrypted,
        isImage = isImage,
        isVideo = isVideo,
        isReadUriOnly = readUriOnly,
        sourceAllowsMutation = canMakeActions,
        sourceAllowsDelete = sourceAllowsDelete,
        cloudReadOnly = isReadOnlyCloudMedia(settingsByConfigId),
        providerSupportsFavorite = providerSupportsFavorite,
        providerSupportsTrash = providerSupportsTrash,
        platformSupportsFavorite = platformSupportsFavorite,
        vaultRestoreAvailable = vaultRestoreAvailable,
        vaultDeleteAvailable = vaultDeleteAvailable,
    )
)
