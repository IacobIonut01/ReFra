/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.exif.ui

import com.dot.gallery.core.metadata.MetadataDirectory

data class MetadataViewState(
    val isLoading: Boolean = true,
    val directories: List<MetadataDirectory> = emptyList(),
    /** Details of the file shown in the card above the fields; all optional. */
    val file: MetadataFileInfo? = null,
) {
    val fieldCount: Int get() = directories.sumOf { it.tags.size }
}

data class MetadataFileInfo(
    val name: String? = null,
    val sizeBytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    /** Anything [com.dot.gallery.core.presentation.components.LocalMediaImageRenderer] can load. */
    val thumbnailModel: Any? = null,
)

/**
 * The directories with only the tags matching [query] (case-insensitive, on the tag name, its
 * value or the directory name); directories left empty are dropped. A blank query keeps all.
 */
fun List<MetadataDirectory>.filterMetadata(query: String): List<MetadataDirectory> {
    val needle = query.trim()
    if (needle.isEmpty()) return this
    return mapNotNull { directory ->
        if (directory.name.contains(needle, ignoreCase = true)) return@mapNotNull directory
        val tags = directory.tags.filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.description.contains(needle, ignoreCase = true)
        }
        if (tags.isEmpty()) null else directory.copy(tags = tags)
    }
}

/** Plain-text dump used by "Copy all metadata": a heading per directory, one tag per line. */
fun List<MetadataDirectory>.toClipboardText(): String = joinToString("\n\n") { directory ->
    buildString {
        append(directory.name)
        directory.tags.forEach { append("\n").append(it.name).append(": ").append(it.description) }
    }
}
