/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.util

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.dot.gallery.core.ui.settings.LocalSettingsStore
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.PrefKey
import com.dot.gallery.domain.settings.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class RememberPreferenceLiveReadTest {

    @get:Rule
    val rule = createComposeRule()

    private class FakeSettingsStore : SettingsStore {
        val values = MutableStateFlow<Map<String, Any?>>(emptyMap())

        @Suppress("UNCHECKED_CAST")
        override fun <T> observe(key: PrefKey<T>): Flow<T> =
            values.map { (it[key.name] as T?) ?: key.default }

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T> put(key: PrefKey<T>, value: T) {
            values.value = values.value + (key.name to value)
        }

        override suspend fun snapshot(): Map<String, String> = emptyMap()

        fun set(name: String, value: Any?) {
            values.value = values.value + (name to value)
        }
    }

    private class FakeDataStore : DataStore<Preferences> {
        val inner = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = inner
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            val updated = transform(inner.value)
            inner.value = updated
            return updated
        }
    }

    @Test
    fun portablePreferenceDerivedReadFollowsStoreEmission() {
        val key = PrefKey.bool("live_read_portable", default = false)
        val store = FakeSettingsStore()
        var derivedValue by mutableStateOf<Boolean?>(null)
        rule.setContent {
            CompositionLocalProvider(LocalSettingsStore provides store) {
                val pref = rememberPreference(key)
                val derived by remember { derivedStateOf { pref.value } }
                derivedValue = derived
            }
        }
        rule.waitUntil("initial value read", 5_000) { derivedValue == false }

        rule.runOnUiThread { store.set(key.name, true) }
        rule.waitUntil("derived read observes store emission", 5_000) { derivedValue == true }
        rule.runOnIdle { assertEquals(true, derivedValue) }
    }

    @Test
    fun androidxPreferenceDerivedReadFollowsStoreEmission() {
        val key = booleanPreferencesKey("live_read_androidx")
        val store = FakeDataStore()
        var derivedValue by mutableStateOf<Boolean?>(null)
        rule.setContent {
            CompositionLocalProvider(LocalPreferenceStore provides store) {
                val pref = rememberPreference(key, false)
                val derived by remember { derivedStateOf { pref.value } }
                derivedValue = derived
            }
        }
        rule.waitUntil("initial value read", 5_000) { derivedValue == false }

        rule.runOnUiThread { store.inner.value = preferencesOf(key to true) }
        rule.waitUntil("derived read observes store emission", 5_000) { derivedValue == true }
        rule.runOnIdle { assertEquals(true, derivedValue) }
    }
}
