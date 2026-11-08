/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.ignored.ui.setup

import com.dot.gallery.domain.model.Album

sealed interface IgnoredSetupAction {
    data object NavigateBack : IgnoredSetupAction
    data object NavigateNext : IgnoredSetupAction
    data object Cancel : IgnoredSetupAction
    data object Confirm : IgnoredSetupAction
    data class SetMethod(val method: IgnoreMethod) : IgnoredSetupAction
    data class ToggleAlbum(val album: Album) : IgnoredSetupAction
    data class SetRegex(val regex: String) : IgnoredSetupAction
    data class SetSearch(val query: String) : IgnoredSetupAction
    data class SetLocation(val location: Int) : IgnoredSetupAction
    data class SetName(val name: String) : IgnoredSetupAction
}
