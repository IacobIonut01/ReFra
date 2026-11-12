/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import com.dot.gallery.core.activeDataStore
import com.dot.gallery.core.ui.settings.UnsavedPreference
import com.dot.gallery.domain.settings.writePreference


val LocalInitialPreferences = staticCompositionLocalOf<Preferences?> { null }

val LocalPreferenceStore = staticCompositionLocalOf<DataStore<Preferences>?> { null }

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = LocalPreferenceStore.current ?: context.activeDataStore
    val initial = LocalInitialPreferences.current?.get(key) ?: defaultValue
    val stored by remember(store, key, defaultValue) {
        store.data
            .map { it[key] ?: defaultValue }
    }.collectAsStateWithLifecycle(initialValue = initial)
    var unsaved by remember(key) { mutableStateOf<UnsavedPreference<T>?>(null) }
    LaunchedEffect(stored) { unsaved = null }
    val state = unsaved.let { if (it != null) it.value else stored }

    return remember(state) {
        object : MutableState<T> {
            override var value: T
                get() = state
                set(value) {
                    coroutineScope.launch {
                        val saved = writePreference(key.name) {
                            store.edit {
                                it[key] = value
                            }
                        }
                        unsaved = if (saved) null else UnsavedPreference(value)
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T> rememberPreferenceSerializable(
    keyString: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val store = LocalPreferenceStore.current ?: context.activeDataStore
    val initial = LocalInitialPreferences.current?.get(keyString)
        ?: Json.encodeToString(defaultValue)
    val stored by remember(store, keyString, defaultValue) {
        store.data
            .map { it[keyString] ?: Json.encodeToString(defaultValue) }
    }.collectAsStateWithLifecycle(initialValue = initial)
    var unsaved by remember(keyString) { mutableStateOf<String?>(null) }
    LaunchedEffect(stored) { unsaved = null }
    val state = unsaved ?: stored

    return remember(state) {
        object : MutableState<T> {
            override var value: T
                get() = Json.decodeFromString(state)
                set(value) {
                    val encoded = Json.encodeToString(value)
                    coroutineScope.launch {
                        val saved = writePreference(keyString.name) {
                            store.edit {
                                it[keyString] = encoded
                            }
                        }
                        unsaved = if (saved) null else encoded
                    }
                }

            override fun component1() = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}