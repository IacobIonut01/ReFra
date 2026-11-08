/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored.ui.setup

import com.dot.gallery.domain.model.Album
import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.domain.model.matchesAlbum
import com.dot.gallery.feature.ignored.ui.coversAlbum

/** How a rule picks its albums: an explicit selection, or a path pattern. */
enum class IgnoreMethod { ALBUMS, PATTERN }

enum class PatternError { INVALID, IN_USE }

data class IgnoredSetupUiState(
    val currentStep: SetupStep = SetupStep.CHOOSE,
    /** The rule being edited, or null while creating one. */
    val editing: IgnoredAlbum? = null,
    /** The step the sheet opened on; going back from it closes the sheet. */
    val entryStep: SetupStep = SetupStep.CHOOSE,
    val method: IgnoreMethod = IgnoreMethod.ALBUMS,
    val selectedAlbumIds: List<Long> = emptyList(),
    val regex: String = "",
    val location: Int = IgnoredAlbum.ALBUMS_AND_TIMELINE,
    val name: String = "",
    val search: String = "",
    val albums: List<Album> = emptyList(),
    val ignoredAlbums: List<IgnoredAlbum> = emptyList(),
) {
    val isEditing: Boolean get() = editing != null

    val canGoBack: Boolean get() = currentStep != entryStep

    val selectedAlbums: List<Album>
        get() = selectedAlbumIds.mapNotNull { id -> albums.firstOrNull { it.id == id } }

    private val compiledRegex: Regex? by lazy {
        regex.takeIf { it.isNotBlank() }?.let { runCatching { it.toRegex() }.getOrNull() }
    }

    val patternError: PatternError? by lazy {
        when {
            regex.isBlank() -> null
            compiledRegex == null -> PatternError.INVALID
            ignoredAlbums.any { it.id != editing?.id && it.wildcard == regex } -> PatternError.IN_USE
            else -> null
        }
    }

    val patternMatches: List<Album> by lazy {
        compiledRegex?.let { pattern -> albums.filter(pattern::matchesAlbum) } ?: emptyList()
    }

    /** The albums the rule hides once saved. */
    val matchedAlbums: List<Album>
        get() = if (method == IgnoreMethod.PATTERN) patternMatches else selectedAlbums

    val visibleAlbums: List<Album> by lazy {
        val query = search.trim()
        if (query.isEmpty()) albums else albums.filter { it.label.contains(query, ignoreCase = true) }
    }

    val canContinue: Boolean
        get() = when (method) {
            IgnoreMethod.ALBUMS -> selectedAlbumIds.isNotEmpty()
            IgnoreMethod.PATTERN -> regex.isNotBlank() && patternError == null
        }

    /** True when another rule already hides the album by selection, so it can't be picked again. */
    fun isIgnoredElsewhere(albumId: Long): Boolean =
        ignoredAlbums.any { it.id != editing?.id && it.coversAlbum(albumId) }
}
