/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.duplicates

import android.content.Context
import com.dot.gallery.core.MediaDistributor
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.injection.Singleton
import com.dot.gallery.data.InternalDatabase
import com.dot.gallery.domain.model.UriMedia
import com.dot.gallery.domain.settings.DataStoreSettingsStore
import com.dot.gallery.domain.settings.SettingsKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** How many duplicate sets exist and how much space their extra copies take. */
data class DuplicateSummary(val sets: Int, val extraBytes: Long)

/**
 * The live exact-duplicate sets: stored hashes joined with the timeline's local media, so
 * ignored and locked albums, trashed items and files changed since they were hashed never show,
 * and each set's keeper follows the user's folder preferences.
 */
@Singleton
class DuplicatesSource @Inject constructor(
    private val database: InternalDatabase,
    private val distributor: MediaDistributor,
    private val context: Context,
) {
    private val settings by lazy { DataStoreSettingsStore(context.activeDataStore) }

    val rules: Flow<KeeperRules> by lazy {
        combine(
            settings.observe(SettingsKeys.duplicateKeepFolders),
            settings.observe(SettingsKeys.duplicateRemoveFolders),
        ) { keep, remove -> KeeperRules(keep.toList(), remove.toList()) }
            .distinctUntilChanged()
    }

    val sets: Flow<List<DuplicateSet<UriMedia>>> by lazy {
        combine(
            database.getMediaContentHashDao().observeDuplicates(),
            distributor.timelineMediaFlow.map { state ->
                // Cloud items share the timeline but have negative ids; only local files are hashed.
                state.media.filter { it.id >= 0 }.associateBy { it.id }
            },
            rules,
        ) { rows, media, rules -> buildDuplicateSets(rows, media, rules) { it.copyInfo() } }
            .flowOn(Dispatchers.Default)
    }

    val summary: Flow<DuplicateSummary> by lazy {
        sets.map { list -> DuplicateSummary(list.size, list.sumOf { it.extraBytes }) }
            .distinctUntilChanged()
    }
}
