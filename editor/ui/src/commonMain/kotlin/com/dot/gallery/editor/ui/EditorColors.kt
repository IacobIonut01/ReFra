package com.dot.gallery.editor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours that belong to the editor surface rather than the host
 * theme: the neutral surround behind the photo (always dark, whatever the
 * chrome theme, so it never tints colour judgement), the checkerboard drawn
 * only under translucent pixels, and the accent used for on-canvas active
 * affordances. [EditorTheme] provides theme-appropriate values; hosts that
 * wrap the editor in their own theme get these defaults — a neutral,
 * theme-independent canvas by design.
 */
internal class EditorColors(
    // The frame behind the photo is pure black: the editor's own surface
    // must never tint the image, and a letterboxed photo should read as
    // film against a dark room, not as a grey card.
    val canvas: Color = Color.Black,
    val checkerA: Color = Color(0xFF24272D),
    val checkerB: Color = Color(0xFF34383F),
    val onCanvas: Color = Color(0xFFF1F3F5),
    val selection: Color = Color(0xFFA7C8FF),
    val onSelection: Color = Color(0xFF003060)
)

internal val LocalEditorColors = staticCompositionLocalOf { EditorColors() }
