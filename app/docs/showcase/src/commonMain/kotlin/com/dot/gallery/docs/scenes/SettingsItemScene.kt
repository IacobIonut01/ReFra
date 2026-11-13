/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs.scenes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.settings

/** Settings rows in their common configurations. */
@Composable
fun SettingsItemScene(state: String?) {
    when (state) {
        "split" -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                SettingsItem(
                    item = SettingsEntity.SwitchPreference(
                        title = "Album sections",
                        summary = "Groups albums into custom sections",
                        isChecked = true,
                        onCheck = {},
                        onClick = {},
                        screenPosition = Position.Alone,
                    )
                )
            }
        }

        "disabled" -> LazyColumn(Modifier.fillMaxSize()) {
            settings {
                Preference(
                    title = "Secure folder",
                    summary = "Unavailable without biometric unlock",
                    icon = Icons.Outlined.Star,
                    enabled = false,
                )
            }
        }

        "horizontal" -> LazyColumn(Modifier.fillMaxSize()) {
            settings {
                Preference(
                    title = "Latest backup",
                    summary = "2 hours ago",
                    horizontalLayout = true,
                )
            }
        }

        else -> LazyColumn(Modifier.fillMaxSize()) {
            settings {
                Preference(
                    title = "Notifications",
                    summary = "New imports and sync status",
                    icon = Icons.Outlined.Notifications,
                    onClick = {},
                )
            }
        }
    }
}

/** Standalone variants, stacked for a single-scene overview. */
@Composable
fun SettingsItemVariants() {
    Column(Modifier.fillMaxWidth()) {
        SettingsItem(item = SettingsEntity.Preference(title = "Default row"))
        SettingsItem(
            item = SettingsEntity.Preference(
                title = "With summary",
                summary = "Explains what the toggle does",
            )
        )
        SettingsItem(
            item = SettingsEntity.Preference(
                title = "Disabled",
                summary = "Cannot be changed here",
                enabled = false,
            )
        )
    }
}
