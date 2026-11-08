/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.docs

import androidx.compose.runtime.Composable

/**
 * One renderable docs scene: a composable plus the metadata the site needs to
 * embed it (stable id for URLs/iframes, human title, and the in-app path trace
 * shown next to the live render).
 *
 * [presentation] is `screen` for a full app screen (rendered at the shared
 * device preset dimensions) or `component` for a focused widget rendered at
 * its own [widthDp] x [heightDp] logical size.
 */
data class DocsScene(
    val id: String,
    val title: String,
    val pathTrace: List<String> = emptyList(),
    val states: List<String> = emptyList(),
    val presentation: String = "screen",
    val widthDp: Int = 412,
    val heightDp: Int = 915,
    /**
     * Scene renders a separate-window overlay (ModalBottomSheet/Dialog). The
     * capture test must then photograph the whole window and crop to the root
     * rect — a root-only `captureToImage` misses the modal layer.
     */
    val captureWindow: Boolean = false,
    /**
     * Extra settle budget for scenes whose real content arrives off the compose
     * clock — Sketch decode, ExoPlayer prepare, GL first frame. The capture test
     * lets the auto-advance clock run for this long before freezing; ignored by
     * the live embed.
     */
    val settleMillis: Long = 0L,
    /**
     * Semantics tag the scene emits (a zero-size tagged node is enough) once
     * its genuinely-async content is ready — e.g. a map's first fully-rendered
     * tile frame, which neither layout size nor a settle delay can prove. The
     * capture test waits up to 20s for a node carrying this tag before the
     * screenshot; null skips the wait entirely.
     */
    val requiredReadyTag: String? = null,
    /**
     * States meant to ship as recorded clips, not stills — the capture test
     * skips them by default and refuses to snapshot them on an explicit
     * filter, so a clip is never silently overwritten by a frame. They remain
     * in [states] so the manifest keeps the full key set.
     */
    val recordedStates: List<String> = emptyList(),
    /**
     * Scene composes a full-bleed scaffold that already accepts paddingValues
     * (the browse pages) — the host hands its contentPadding down via
     * [LocalDocsContentPadding] instead of insetting the whole scene box, so
     * top scrims still paint from y0 and only the content reserves the space.
     */
    val handlesContentPadding: Boolean = false,
    /**
     * States the capture test reaches by swiping the scene's content up before the screenshot,
     * for screens that take no initial scroll position: their transparent blur top bar only
     * shows once content has scrolled under it. The live embed shows these states at rest.
     */
    val swipedStates: List<String> = emptyList(),
    /**
     * The app themes this scene is captured in. A surface that only ships one
     * theme (the ReFra editor is dark-only for now) lists just that one, so a
     * light-and-dark sweep never records frames the app cannot show.
     */
    val themes: List<String> = listOf("light", "dark"),
    val content: @Composable (state: String?) -> Unit,
) {
    init {
        require(presentation == "screen" || presentation == "component") {
            "Scene '$id' presentation must be \"screen\" or \"component\", got \"$presentation\""
        }
        require(widthDp > 0 && heightDp > 0) {
            "Scene '$id' must declare positive dimensions, got ${widthDp}x${heightDp}"
        }
        require(recordedStates.all { it in states }) {
            "Scene '$id' recordedStates must be declared in states, got $recordedStates"
        }
        require(swipedStates.all { it in states }) {
            "Scene '$id' swipedStates must be declared in states, got $swipedStates"
        }
        require(themes.isNotEmpty() && themes.all { it == "light" || it == "dark" }) {
            "Scene '$id' themes must be a non-empty subset of light and dark, got $themes"
        }
    }
}
