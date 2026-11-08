/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.domain.util

/**
 * A collection shows photos saved into it and photos that currently sit in one
 * of its albums. A photo the user removed stays out until it is added again.
 */
fun isCollectionMember(
    mediaId: Long,
    albumId: Long,
    explicitMediaIds: Set<Long>,
    memberAlbumIds: Set<Long>,
    excludedMediaIds: Set<Long>,
): Boolean {
    if (mediaId in excludedMediaIds) return false
    return mediaId in explicitMediaIds || albumId in memberAlbumIds
}
