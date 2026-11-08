/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs.scenes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_CUTOUT
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_NONE
import com.dot.gallery.domain.settings.LONG_PRESS_ACTION_ROTATE

/**
 * The real [ChooserPreferenceDetailScreen] shell the media-viewer settings
 * pane uses for the long-press action chooser.
 * Every selection mutates local state so the live preview stays interactive;
 * `cutout-unavailable` mirrors the staging build before the Subject Cutout
 * model is downloaded, with the option disabled and a hint attached.
 */
@Composable
fun DocsLongPressActionScene(state: String?) {
    var selected by rememberSaveable { mutableStateOf(LONG_PRESS_ACTION_ROTATE) }
    val cutoutUnavailable = state == "cutout-unavailable"
    // feature.settings Res accessors are module-internal; literals mirror
    // long_press_action_* strings verbatim.
    ChooserPreferenceDetailScreen(
        title = "Long-press action",
        description = "Choose what a long-press on an image does in the viewer. " +
            "Rotate turns the image 90° for viewing; you can save the rotation " +
            "from the button that appears. Cut out subject starts an on-device " +
            "subject cut-out. None does nothing. When long-press cuts out a " +
            "subject, a Rotate button is shown on screen instead. You can " +
            "always cut out a subject from the built-in editor's Cutout tool.",
        options = listOf(
            PreferenceOption(
                value = LONG_PRESS_ACTION_ROTATE,
                label = "Rotate",
                isSelected = selected == LONG_PRESS_ACTION_ROTATE,
            ),
            PreferenceOption(
                value = LONG_PRESS_ACTION_CUTOUT,
                label = "Cut out subject",
                isSelected = selected == LONG_PRESS_ACTION_CUTOUT,
                description = if (cutoutUnavailable) {
                    "Download the Subject Cutout model from AI models to use this"
                } else {
                    null
                },
                enabled = !cutoutUnavailable,
            ),
            PreferenceOption(
                value = LONG_PRESS_ACTION_NONE,
                label = "None",
                isSelected = selected == LONG_PRESS_ACTION_NONE,
            ),
        ),
        onOptionSelected = { selected = it },
    )
}
