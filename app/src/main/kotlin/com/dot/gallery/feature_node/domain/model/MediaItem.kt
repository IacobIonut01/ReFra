/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.feature_node.domain.model

import androidx.compose.runtime.Stable

@Stable
sealed class MediaItem<T: Media> {
    abstract val key: String

    @Stable
    data class Header<T: Media> (
        override val key: String,
        val text: String,
        val data: Set<Long>
    ) : MediaItem<T>()

    @Stable
    data class MediaViewItem<T: Media> (
        override val key: String,
        val media: T
    ) : MediaItem<T>()

}

val Any.isHeaderKey: Boolean
    get() = this is String && this.startsWith("header_")

val Any.isSmallHeaderKey: Boolean
    get() = this is String && isHeaderKey && !isBigHeaderKey

val Any.isBigHeaderKey: Boolean
    get() = this is String && this.startsWith("header_big_")

val Any.isIgnoredKey: Boolean
    get() = this is String && this.contains("aboveGrid")