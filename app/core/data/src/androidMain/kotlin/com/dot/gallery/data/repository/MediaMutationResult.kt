/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.data.repository

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.datastore.preferences.core.Preferences
import com.dot.gallery.core.Resource
import com.dot.gallery.core.decoder.format.ImageReencoder
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationCapability
import com.dot.gallery.core.metadata.SanitizationResult
import com.dot.gallery.data.dao.CategoryWithMediaCount
import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumGroup
import com.dot.gallery.domain.model.AlbumGroupMember
import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.AlbumSectionMember
import com.dot.gallery.domain.model.AlbumThumbnail
import com.dot.gallery.domain.model.Category
import com.dot.gallery.domain.model.Collection
import com.dot.gallery.domain.model.CollectionWithCount
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.model.ImageEmbedding
import com.dot.gallery.domain.model.Media
import com.dot.gallery.domain.model.ClassifiedMedia
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.data.entity.MediaMetadata
import com.dot.gallery.domain.model.LockedAlbum
import com.dot.gallery.domain.model.MergedSubfolderAlbum
import com.dot.gallery.domain.model.PinnedAlbum
import com.dot.gallery.domain.model.TimelineSettings
import com.dot.gallery.domain.model.Vault
import com.dot.gallery.domain.util.MediaOrder
import com.dot.gallery.domain.model.AllowedMedia
import kotlinx.coroutines.flow.Flow
import java.io.File
import com.dot.gallery.core.util.or
import com.dot.gallery.domain.uri.path
import com.dot.gallery.domain.util.getCategory


enum class MediaMutationResult {
    COMPLETED,
    REQUEST_LAUNCHED,
    FAILED,
}