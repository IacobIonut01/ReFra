/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */
package com.dot.gallery.domain.util

import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.AlbumSection
import com.dot.gallery.domain.model.AlbumSectionMember
import com.dot.gallery.domain.model.AlbumSectionType
import com.dot.gallery.domain.model.AlbumSectionWithAlbums
import com.dot.gallery.domain.model.sectionType

object AlbumClassifier {

    private val COMMON_LABELS = setOf(
        "camera", "dcim", "screenshots", "pictures", "downloads", "download",
        "screen recordings", "screenrecorder", "screen record",
        "videos", "movies", "bluetooth", "recordings", "raw",
        "panoramas", "photospheres", "restored", "recent",
    )

    private val MEDIA_ROOT_PATHS = setOf(
        "pictures/", "dcim/", "movies/", "download/", "downloads/",
    )

    fun classify(album: Album): AlbumSectionType {
        if (album.relativePath.startsWith("cloud/")) return AlbumSectionType.UNCATEGORIZED

        val labelLower = album.label.lowercase()

        // Check if it's a common system folder by label
        if (labelLower in COMMON_LABELS) return AlbumSectionType.COMMON

        // Check if it's a root-level media folder (e.g., "DCIM/", "Pictures/")
        val relPath = album.relativePath.lowercase()
        for (root in MEDIA_ROOT_PATHS) {
            if (relPath.equals(root, ignoreCase = true) ||
                relPath.trimEnd('/').equals(root.trimEnd('/'), ignoreCase = true)
            ) {
                return AlbumSectionType.COMMON
            }
        }

        // Check if it's an app folder (subfolder of a media root)
        if (isAppFolder(relPath, labelLower)) return AlbumSectionType.APPS

        return AlbumSectionType.UNCATEGORIZED
    }

    private fun isAppFolder(relPathLower: String, labelLower: String): Boolean {
        for (root in MEDIA_ROOT_PATHS) {
            if (relPathLower.startsWith(root)) {
                val subfolder = relPathLower.removePrefix(root).trimEnd('/')
                // Single-level subfolder under a media root → likely an app folder
                if (subfolder.isNotEmpty() && !subfolder.contains("/") && subfolder !in COMMON_LABELS) {
                    return true
                }
            }
        }
        return false
    }

    fun classifyAlbums(
        albums: List<Album>,
        manualOverrides: Map<Long, Long>, // albumId -> sectionId
        sectionIdByType: Map<AlbumSectionType, Long> // type -> sectionId
    ): Map<Long, List<Album>> {
        val result = mutableMapOf<Long, MutableList<Album>>()

        for (album in albums) {
            // Check manual override first
            val overrideSectionId = manualOverrides[album.id]
            if (overrideSectionId != null) {
                result.getOrPut(overrideSectionId) { mutableListOf() }.add(album)
                continue
            }

            // Auto-classify
            val type = classify(album)
            val sectionId = sectionIdByType[type]
            if (sectionId != null) {
                result.getOrPut(sectionId) { mutableListOf() }.add(album)
            }
        }

        return result
    }

    /** The sections the Albums page shows: visible and non-empty, in [sections] order. */
    fun sectionsWithAlbums(
        albums: List<Album>,
        sections: List<AlbumSection>,
        members: List<AlbumSectionMember>,
    ): List<AlbumSectionWithAlbums> {
        val manualOverrides = members.associate { it.albumId to it.sectionId }
        val sectionIdByType = sections.associate { it.sectionType to it.id }
        val classified = classifyAlbums(albums, manualOverrides, sectionIdByType)
        return sections
            .filter { it.isVisible }
            .map { section ->
                AlbumSectionWithAlbums(
                    section = section,
                    albums = classified[section.id] ?: emptyList()
                )
            }
            .filter { it.albums.isNotEmpty() }
    }
}
