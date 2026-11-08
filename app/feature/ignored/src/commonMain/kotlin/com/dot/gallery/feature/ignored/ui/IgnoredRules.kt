/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored.ui

import com.dot.gallery.domain.model.IgnoredAlbum
import com.dot.gallery.feature.ignored.ui.setup.IgnoreMethod
import com.dot.gallery.feature.ignored.ui.setup.IgnoredSetupUiState

enum class IgnoredRuleKind { SINGLE, SEVERAL, PATTERN }

/** Labels the previous setup flow generated for every rule ("Single #1", "Regex #2"). */
private val GENERATED_LABEL = Regex("^(Single|Multiple|Regex) #\\d+$")

/**
 * A rule stored before multi-album rules existed: its own [IgnoredAlbum.id] is the hidden album's
 * id. Its primary key is that album, so changing the album means writing a new row.
 */
val IgnoredAlbum.isLegacySingle: Boolean get() = albumIds.isEmpty() && wildcard == null

/** Albums the rule hides by selection; empty for path patterns. */
val IgnoredAlbum.ruleAlbumIds: List<Long>
    get() = albumIds.ifEmpty { if (wildcard == null) listOf(id) else emptyList() }

val IgnoredAlbum.kind: IgnoredRuleKind
    get() = when {
        wildcard != null -> IgnoredRuleKind.PATTERN
        ruleAlbumIds.size > 1 -> IgnoredRuleKind.SEVERAL
        else -> IgnoredRuleKind.SINGLE
    }

/** The name the user gave the rule, ignoring the generated labels of the old setup flow. */
val IgnoredAlbum.customLabel: String?
    get() = label?.takeUnless { it.isBlank() || GENERATED_LABEL.matches(it) }

/** Title for lists and sheets: the custom name, else the pattern, else the album names. */
fun IgnoredAlbum.displayTitle(unknown: String): String =
    customLabel ?: wildcard ?: matchedAlbums.joinToString().ifEmpty { unknown }

fun IgnoredAlbum.coversAlbum(albumId: Long): Boolean = albumId in ruleAlbumIds

/** What saving the setup sheet writes. [remove] is deleted before [save] is upserted. */
data class IgnoredRuleWrite(val save: IgnoredAlbum, val remove: IgnoredAlbum? = null)

/**
 * Builds the rule the setup sheet describes. Editing keeps the rule's id, except for a legacy
 * single-album rule whose album changes: that one is replaced by a row with a fresh id.
 */
fun IgnoredSetupUiState.toRuleWrite(newId: () -> Long): IgnoredRuleWrite {
    val original = editing
    val label = name.trim().ifEmpty { null }
    val matched = matchedAlbums.map { it.label }
    if (method == IgnoreMethod.ALBUMS && original != null && original.isLegacySingle &&
        selectedAlbumIds == listOf(original.id)
    ) {
        return IgnoredRuleWrite(original.copy(label = label, location = location, matchedAlbums = matched))
    }
    val replaced = original?.takeIf { it.isLegacySingle }
    val rule = IgnoredAlbum(
        id = original?.takeUnless { it.isLegacySingle }?.id ?: newId(),
        label = label,
        wildcard = regex.takeIf { method == IgnoreMethod.PATTERN },
        albumIds = if (method == IgnoreMethod.ALBUMS) selectedAlbumIds else emptyList(),
        location = location,
        matchedAlbums = matched,
    )
    return IgnoredRuleWrite(save = rule, remove = replaced)
}
