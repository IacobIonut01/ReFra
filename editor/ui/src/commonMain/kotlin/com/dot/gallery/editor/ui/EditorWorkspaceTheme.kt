package com.dot.gallery.editor.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The V3 workbench presentation contract: the editor always runs on its own
 * neutral scheme — never the host's coloured containers. Only the colour
 * scheme is swapped: the host's typography, shapes and motion scheme pass
 * straight through so a Gallery-hosted editor keeps Gallery's type and
 * motion language over the neutral V3 surfaces.
 *
 * Dark or light is derived from the host scheme's `surface` luminance, so a
 * dark host still gets the dark editor and vice versa. The scheme is
 * supplied by a nested [MaterialTheme] seeded with the ambient typography,
 * shapes and motion scheme — nothing outside the colour roles is replaced.
 *
 * [LocalEditorColors] adds the editor-only semantic tokens: the always-dark
 * canvas surround (#111315, so it never tints colour judgement), its
 * checkerboard and the on-canvas accent derived from the live scheme.
 *
 * Every editor surface reads its colours through MaterialTheme or
 * [LocalEditorColors] — no per-widget hex.
 */
@Composable
fun EditorWorkspaceTheme(content: @Composable () -> Unit) {
    WorkspaceTheme(V3DarkColors, V3LightColors, content)
}

@Composable
private fun WorkspaceTheme(
    dark: ColorScheme,
    light: ColorScheme,
    content: @Composable () -> Unit
) {
    val host = MaterialTheme.colorScheme
    WorkspaceTheme(if (host.surface.luminance() < .5f) dark else light, content)
}

@Composable
private fun WorkspaceTheme(
    scheme: ColorScheme,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        motionScheme = MaterialTheme.motionScheme
    ) {
        CompositionLocalProvider(
            LocalEditorColors provides EditorColors(
                selection = scheme.primary,
                onSelection = scheme.onPrimary
            ),
            content = content
        )
    }
}

/** The single source of truth for the V3 scheme — shared by [EditorTheme]. */
internal fun v3ColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) V3DarkColors else V3LightColors

// V3 dark: black surfaces, #121212 low containers, #202020 raised, #353535
// outlines, desaturated light-blue primary.
internal val V3DarkColors = darkColorScheme(
    // Full-page surfaces (the settings page) paint background, not surface.
    background = Color.Black,
    onBackground = Color(0xFFF4F4F4),
    tertiary = Color(0xFFDBBDE2),
    onTertiary = Color(0xFF3E2845),
    tertiaryContainer = Color(0xFF553F5D),
    onTertiaryContainer = Color(0xFFF8D8FE),
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF182331),
    primaryContainer = Color(0xFF2A3D57),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondaryContainer = Color(0xFF475466),
    onSecondaryContainer = Color(0xFFF4F4F4),
    surface = Color.Black,
    onSurface = Color(0xFFF4F4F4),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF262626),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFB8B8B8),
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFF353535)
)

// V3 light: white surfaces, #F5F5F5 low containers, #EAEAEA raised, #DCDCDC
// outlines, dark steel-blue primary.
internal val V3LightColors = lightColorScheme(
    background = Color.White,
    onBackground = Color(0xFF171717),
    tertiary = Color(0xFF6E5676),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8D8FE),
    onTertiaryContainer = Color(0xFF27132F),
    primary = Color(0xFF244C76),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF0E2440),
    secondaryContainer = Color(0xFFDCDCDC),
    onSecondaryContainer = Color(0xFF171717),
    surface = Color.White,
    onSurface = Color(0xFF171717),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFEAEAEA),
    surfaceContainerHighest = Color(0xFFDDDDDD),
    surfaceVariant = Color(0xFFEAEAEA),
    onSurfaceVariant = Color(0xFF595959),
    outline = Color(0xFF888888),
    outlineVariant = Color(0xFFDCDCDC)
)

/** The tool card's base: the photo editor's 0xFF141416 in dark, its light twin otherwise. */
internal val VideoToolCardDark = Color(0xFF0B0B0B)
internal val VideoToolCardLight = Color(0xFFF1F2F4)
