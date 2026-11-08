/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.local.PersonMergeSuggestion

/**
 * Callbacks [PeopleListContent] delegates upward — the screen wires them to
 * [PeopleListViewModel], previews/docs can bind them to in-memory state.
 */
data class PeopleListActions(
    val onPersonClick: (PersonInfo) -> Unit = {},
    val scanForPeople: () -> Unit = {},
    val regroupFaces: () -> Unit = {},
    val setShowHidden: (Boolean) -> Unit = {},
    val cancelScan: () -> Unit = {},
    val hidePerson: (PersonInfo) -> Unit = {},
    val unhidePerson: (PersonInfo) -> Unit = {},
    val confirmMerge: (PersonMergeSuggestion) -> Unit = {},
    val rejectMerge: (PersonMergeSuggestion) -> Unit = {},
    val renamePerson: (PersonInfo, String) -> Unit = { _, _ -> },
    /** Opens the Face grouping preview when the face models are not installed yet. */
    val setupFaceGrouping: () -> Unit = {},
    /** Turns a People source (on-device people or an account) on or off. */
    val setSourceVisible: (String, Boolean) -> Unit = { _, _ -> },
)
