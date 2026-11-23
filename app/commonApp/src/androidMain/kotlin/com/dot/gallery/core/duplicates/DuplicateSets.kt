/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.duplicates

import com.dot.gallery.data.entity.MediaContentHashEntity
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.SettingsKeys

/** Default *Keep copies in* folders. */
val DefaultKeepFolders: List<String> = SettingsKeys.duplicateKeepFolders.default.toList()

/** Default *Prefer to remove copies in* folders. */
val DefaultRemoveFolders: List<String> = SettingsKeys.duplicateRemoveFolders.default.toList()

/** The user's folder preferences for choosing which copy of a set to keep. */
data class KeeperRules(
    val keepFolders: List<String> = DefaultKeepFolders,
    val removeFolders: List<String> = DefaultRemoveFolders,
)

/** The facts about one copy that set building and keeper choice look at. */
data class CopyInfo(
    val id: Long,
    val relativePath: String,
    val favorite: Boolean,
    val size: Long,
    val modifiedSeconds: Long,
    val path: String,
    val trashed: Boolean,
)

fun UriMedia.copyInfo(): CopyInfo = CopyInfo(
    id = id,
    relativePath = relativePath,
    favorite = favorite > 0,
    size = size,
    modifiedSeconds = timestamp,
    path = path,
    trashed = trashed != 0,
)

/** Byte-identical files: one [keeper] and the extra copies that can go. */
data class DuplicateSet<T : Any>(
    val sha1: String,
    val items: List<T>,
    val keeper: T,
    /** Size of each copy in bytes. */
    val itemSize: Long,
) {
    /** Space the extra copies take. */
    val extraBytes: Long get() = itemSize * (items.size - 1)
}

/**
 * Whether [relativePath] (a MediaStore relative path such as `DCIM/Camera/`) lies in [folder].
 * [folder]'s segments must appear as a run of the path's segments, ignoring case, so `Screenshots`
 * matches `Pictures/Screenshots/` and `DCIM/Screenshots/`, and `WhatsApp Images` matches it under
 * `Android/media/com.whatsapp/`.
 */
fun folderMatches(relativePath: String, folder: String): Boolean {
    val wanted = folder.split('/').map(String::trim).filter(String::isNotEmpty)
    if (wanted.isEmpty()) return false
    val segments = relativePath.split('/').filter(String::isNotEmpty)
    if (segments.size < wanted.size) return false
    return (0..segments.size - wanted.size).any { start ->
        wanted.indices.all { segments[start + it].equals(wanted[it], ignoreCase = true) }
    }
}

/**
 * The copy to keep: a favourite first, then a copy in a keep folder that is not also in a remove
 * folder, then one outside the remove folders, then the earliest added (MediaStore ids grow with
 * each insert, so the lowest id is the oldest copy).
 */
fun <T : Any> chooseKeeper(items: List<T>, rules: KeeperRules, info: (T) -> CopyInfo): T {
    require(items.isNotEmpty())
    fun disfavoured(copy: CopyInfo) = rules.removeFolders.any { folderMatches(copy.relativePath, it) }
    fun preferred(copy: CopyInfo) =
        !disfavoured(copy) && rules.keepFolders.any { folderMatches(copy.relativePath, it) }
    return items.sortedWith(
        compareByDescending<T> { info(it).favorite }
            .thenByDescending { preferred(info(it)) }
            .thenBy { disfavoured(info(it)) }
            .thenBy { info(it).id }
    ).first()
}

/**
 * Builds the duplicate sets from stored hash rows and the live local [media]. Rows whose media is
 * gone, trashed, or changed since it was hashed are ignored, so a set never offers a file that no
 * longer matches. Sets are ordered by the space their extra copies take, largest first.
 */
fun <T : Any> buildDuplicateSets(
    rows: List<MediaContentHashEntity>,
    media: Map<Long, T>,
    rules: KeeperRules,
    info: (T) -> CopyInfo,
): List<DuplicateSet<T>> = rows
    .asSequence()
    .mapNotNull { row ->
        val sha1 = row.sha1 ?: return@mapNotNull null
        val item = media[row.mediaId] ?: return@mapNotNull null
        val copy = info(item)
        if (copy.trashed) return@mapNotNull null
        if (!row.isFreshFor(copy.size, copy.modifiedSeconds, copy.path)) return@mapNotNull null
        sha1 to item
    }
    .groupBy({ it.first }, { it.second })
    .filterValues { it.size > 1 }
    .map { (sha1, items) ->
        val sorted = items.sortedBy { info(it).id }
        DuplicateSet(
            sha1 = sha1,
            items = sorted,
            keeper = chooseKeeper(sorted, rules, info),
            itemSize = info(sorted.first()).size,
        )
    }
    .sortedWith(compareByDescending<DuplicateSet<T>> { it.extraBytes }.thenBy { it.sha1 })
