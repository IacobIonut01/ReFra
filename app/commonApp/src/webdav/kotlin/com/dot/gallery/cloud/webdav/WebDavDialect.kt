/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.webdav

import com.dot.gallery.cloud.core.CloudServerInfo
import com.dot.gallery.cloud.core.CloudStorageInfo
import com.dot.gallery.cloud.core.ProviderType
import com.dot.gallery.cloud.core.ThumbnailSize
import com.dot.gallery.core.util.or
import com.dot.gallery.cloud.core.ProviderCapability
import com.dot.gallery.core.presentation.util.add
import com.dot.gallery.domain.uri.path


/**
 * Optional server features a [WebDavDialect] can opt into. The base
 * [WebDavMediaProvider] inspects this set to derive the provider's advertised
 * [com.dot.gallery.cloud.core.ProviderCapability]s and to decide whether to
 * call the corresponding dialect hook.
 *
 * To extend the framework with a brand-new capability: add a key here, override
 * the matching hook in your dialect, list the key in [WebDavDialect.features],
 * and (if it should surface in the UI) map it in [WebDavMediaProvider].
 */
enum class WebDavFeatureKey {
    /** Server-side preview/thumbnail endpoint (e.g. `/index.php/core/preview`). */
    SERVER_PREVIEW,

    /** Favorites/starring via `oc:favorite` PROPPATCH + PROPFIND. */
    FAVORITES,

    /** Storage quota reporting (e.g. OCS user endpoint). */
    QUOTA,

    /** Public share-link creation. */
    SHARE_LINK,

    /** Rich server identity/version (e.g. OCS capabilities). */
    SERVER_INFO,

    /** Server-computed photo capture time (`nc:metadata-photos-original_date_time`, Nextcloud 28+). */
    PHOTO_CAPTURE_TIME
}

/**
 * Describes a concrete WebDAV server flavor. A dialect is pure behavior — it
 * holds no per-connection state; everything it needs is passed via [WebDavSession].
 *
 * Defaults implement the lowest-common-denominator generic WebDAV server: only
 * the basic file operations, no previews, favorites, quota or sharing. Server
 * flavors (ownCloud, Nextcloud, …) override only the hooks they support and
 * declare them in [features].
 */
abstract class WebDavDialect {

    abstract val providerType: ProviderType
    abstract val displayName: String

    /** Optional capabilities this server supports. Empty = plain WebDAV. */
    open val features: Set<WebDavFeatureKey> = emptySet()

    /**
     * Path (relative to the server URL) of the user's files collection.
     * ownCloud/Nextcloud use `/remote.php/dav/files/{user}`. Generic servers
     * whose root is already the collection should return an empty string.
     */
    open fun filesEndpoint(username: String): String = ""

    /** Default remote folder used as the upload target when none is supplied. */
    open val defaultUploadFolder: String = "Photos"

    /** Whether PROPFIND-reported favorite flags should be trusted/surfaced. */
    open val readsFavoriteFlag: Boolean
        get() = WebDavFeatureKey.FAVORITES in features

    // === Optional feature hooks (only invoked when the matching key is in [features]) ===

    /** Server-side preview URL for a media item, or null to fall back to the original. */
    open fun previewUrl(
        session: WebDavSession,
        fileId: String,
        remotePath: String,
        size: ThumbnailSize
    ): String? = null

    open suspend fun toggleFavorite(
        session: WebDavSession,
        remotePath: String,
        favorite: Boolean
    ): Result<Unit> = unsupported("favorites")

    open suspend fun storageInfo(session: WebDavSession): Result<CloudStorageInfo> =
        unsupported("storage info")

    open suspend fun createShareLink(
        session: WebDavSession,
        path: String,
        expiresAt: Long?
    ): Result<String> = unsupported("share links")

    open suspend fun serverVersion(session: WebDavSession): Result<String> =
        unsupported("server version")

    // === Identity hooks (always available, with sensible generic defaults) ===

    /** Server identity reported after a successful connection test. */
    open suspend fun serverInfo(session: WebDavSession): CloudServerInfo =
        CloudServerInfo(version = "unknown", serverName = displayName)

    /** Optional authenticated-user resolution (id/email). Null = use the configured username. */
    open suspend fun currentUserId(session: WebDavSession): String? = null

    open suspend fun currentUserEmail(session: WebDavSession): String? = null

    protected fun <T> unsupported(what: String): Result<T> =
        Result.failure(UnsupportedOperationException("$displayName does not support $what"))
}
