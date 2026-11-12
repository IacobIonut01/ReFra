/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.dot.gallery.domain.settings.PrefKey
import com.dot.gallery.domain.settings.SettingsStore
import com.dot.gallery.domain.settings.writePreference
import kotlinx.coroutines.launch

/** Platform-provided settings backend — app wraps `activeDataStore`, docs uses localStorage. */
val LocalSettingsStore = compositionLocalOf<SettingsStore?> { null }

/** Encoded snapshot (key name -> `PrefKey.encode` output) for synchronous first-frame reads. */
val LocalPreferenceSnapshot = compositionLocalOf<Map<String, String>> { emptyMap() }

/**
 * A value the user chose whose write failed. The setting shows it until the store emits a
 * new value or a later write succeeds, so the control does not snap back.
 */
class UnsavedPreference<T>(val value: T)

/**
 * Portable repoint of `Settings.*.rememberX()`: shared callers observe
 * [PrefKey] through [LocalSettingsStore]; writes round-trip through the same
 * store the app's androidx `rememberPreference` edits. A failed write is logged
 * by [writePreference] and the chosen value is kept in memory.
 */
@Composable
fun <T> rememberPreference(key: PrefKey<T>): MutableState<T> {
    val store = checkNotNull(LocalSettingsStore.current) {
        "rememberPreference requires LocalSettingsStore — provide a SettingsStore at the composition root"
    }
    val scope = rememberCoroutineScope()
    val snapshot = LocalPreferenceSnapshot.current
    val initial = snapshot[key.name]?.let(key::decode) ?: key.default
    val stored by store.observe(key).collectAsState(initial)
    var unsaved by remember(key) { mutableStateOf<UnsavedPreference<T>?>(null) }
    LaunchedEffect(stored) { unsaved = null }
    val state = unsaved.let { if (it != null) it.value else stored }
    return remember(state) {
        object : MutableState<T> {
            override var value: T
                get() = state
                set(value) {
                    scope.launch {
                        val saved = writePreference(key.name) { store.put(key, value) }
                        unsaved = if (saved) null else UnsavedPreference(value)
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
