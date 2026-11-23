/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.duplicates

import com.dot.gallery.core.duplicates.DuplicateSet

/**
 * The user's choices on the Duplicates screen: a different copy to keep for some sets
 * ([keeperOverrides], by set hash) and copies they chose not to remove ([kept]).
 * Everything else that is not a set's keeper starts selected for the trash.
 */
data class DuplicateChoices(
    val keeperOverrides: Map<String, Long> = emptyMap(),
    val kept: Set<Long> = emptySet(),
)

/** The id of the copy that stays in [set]: the user's pick while it is still in the set. */
fun <T : Any> DuplicateSet<T>.keeperId(choices: DuplicateChoices, id: (T) -> Long): Long =
    choices.keeperOverrides[sha1]?.takeIf { pick -> items.any { id(it) == pick } } ?: id(keeper)

/** The copies of [set] selected for the trash. */
fun <T : Any> DuplicateSet<T>.selected(choices: DuplicateChoices, id: (T) -> Long): List<T> {
    val keeper = keeperId(choices, id)
    return items.filter { id(it) != keeper && id(it) !in choices.kept }
}

/** Every copy selected across [sets]. */
fun <T : Any> selectedCopies(
    sets: List<DuplicateSet<T>>,
    choices: DuplicateChoices,
    id: (T) -> Long,
): List<T> = sets.flatMap { it.selected(choices, id) }

/** Selects or clears one copy. The keeper can't be selected. */
fun <T : Any> DuplicateChoices.toggle(set: DuplicateSet<T>, copyId: Long, id: (T) -> Long): DuplicateChoices {
    if (set.keeperId(this, id) == copyId) return this
    return copy(kept = if (copyId in kept) kept - copyId else kept + copyId)
}

/**
 * Makes [copyId] the copy that stays in [set]. The previous keeper becomes an extra copy and is
 * selected, unless the user had chosen to leave it alone before.
 */
fun <T : Any> DuplicateChoices.keep(set: DuplicateSet<T>, copyId: Long, id: (T) -> Long): DuplicateChoices {
    if (set.items.none { id(it) == copyId }) return this
    val previous = set.keeperId(this, id)
    return copy(
        keeperOverrides = keeperOverrides + (set.sha1 to copyId),
        kept = (kept - copyId - previous),
    )
}

/**
 * How each copy's folder is named on the screen: its album name, or its whole relative path
 * where two different folders in the set share an album name (`Pictures/Trip`, `Download/Trip`).
 * [album] and [relativePath] read the copy's album name and MediaStore relative path.
 */
fun <T> folderLabels(
    items: List<T>,
    id: (T) -> Long,
    album: (T) -> String,
    relativePath: (T) -> String,
): Map<Long, String> {
    fun path(item: T) = relativePath(item).trim('/')
    fun short(item: T) = album(item).ifBlank { path(item).substringAfterLast('/') }
    val clashing = items.groupBy(::short)
        .filterValues { group -> group.map(::path).distinct().size > 1 }
        .keys
    return items.associate { item ->
        val name = short(item)
        id(item) to if (name in clashing) path(item).ifBlank { name } else name.ifBlank { path(item) }
    }
}
