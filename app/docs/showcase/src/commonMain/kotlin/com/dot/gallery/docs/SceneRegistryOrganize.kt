/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import com.dot.gallery.docs.scenes.DocsCaptureDateScene
import com.dot.gallery.docs.scenes.DocsGroupMembersScene
import com.dot.gallery.docs.scenes.DocsIgnoredScene
import com.dot.gallery.docs.scenes.DocsMetadataEditScene
import com.dot.gallery.docs.scenes.DocsMetadataFieldsScene
import com.dot.gallery.docs.scenes.DocsMetadataRemovalScene
import com.dot.gallery.docs.scenes.DocsPickerScene
import com.dot.gallery.docs.scenes.DocsMoreActionsScene
import com.dot.gallery.docs.scenes.DocsQuickActionsScene
import com.dot.gallery.docs.scenes.DocsSlideshowControlsScene
import com.dot.gallery.docs.scenes.DocsTransferProgressScene

/** Organization and media-action surfaces. */
val organizeScenes: List<DocsScene> = listOf(
    DocsScene(
        id = "ignored-rules",
        title = "Ignored albums",
        pathTrace = listOf("Library", "Ignored"),
    ) { state -> DocsIgnoredScene(state) },
    DocsScene(
        id = "picker-photos",
        title = "Photo picker",
        pathTrace = listOf("System picker", "Select photos"),
        states = listOf("default", "selected", "scrolled"),
        swipedStates = listOf("scrolled"),
    ) { state -> DocsPickerScene(state) },
    DocsScene(
        id = "transfer-progress",
        title = "Copy progress",
        pathTrace = listOf("Select photos", "Copy to…"),
        states = listOf("copying", "complete"),
        presentation = "component",
        widthDp = 412,
        heightDp = 260,
    ) { state -> DocsTransferProgressScene(state) },
    DocsScene(
        id = "group-members",
        title = "Burst group strip",
        pathTrace = listOf("Viewer", "Group members"),
        states = listOf("single", "multi"),
        presentation = "component",
        widthDp = 412,
        heightDp = 220,
    ) { state -> DocsGroupMembersScene(state) },
    DocsScene(
        id = "media-quick-actions",
        title = "Photo quick actions",
        pathTrace = listOf("Viewer", "Bottom bar"),
        presentation = "component",
        widthDp = 412,
        heightDp = 140,
    ) { DocsQuickActionsScene(it) },
    DocsScene(
        id = "photo-more-actions",
        title = "Photo actions",
        pathTrace = listOf("Photo", "Info panel", "Actions"),
        presentation = "component",
        widthDp = 412,
        heightDp = 560,
    ) { DocsMoreActionsScene(it) },
    DocsScene(
        id = "metadata-fields",
        title = "All metadata",
        pathTrace = listOf("Photo", "Details", "View all metadata"),
    ) { DocsMetadataFieldsScene(it) },
    DocsScene(
        id = "metadata-edit",
        title = "Edit label and description",
        pathTrace = listOf("Photo", "Details", "Edit"),
        captureWindow = true,
    ) { DocsMetadataEditScene(it) },
    DocsScene(
        id = "capture-date",
        title = "Edit capture date",
        pathTrace = listOf("Photo", "Details", "Edit capture date"),
        states = listOf("edit", "copy-required"),
        captureWindow = true,
    ) { state -> DocsCaptureDateScene(state) },
    DocsScene(
        id = "metadata-removal",
        title = "Remove metadata",
        pathTrace = listOf("Photo", "Details", "Remove metadata"),
        states = listOf("location-supported", "unsupported-format"),
        captureWindow = true,
    ) { state -> DocsMetadataRemovalScene(state) },
    DocsScene(
        id = "slideshow-controls",
        title = "Slideshow controls",
        pathTrace = listOf("Slideshow", "Playing"),
        presentation = "component",
        widthDp = 412,
        heightDp = 104,
    ) { DocsSlideshowControlsScene(it) },
)
