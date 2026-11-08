package com.dot.gallery.editor.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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

/**
 * The video editor's workspace: the neutral surfaces of [V3VideoDarkColors]
 * and [V3VideoLightColors] (whose tonal steps its floating panels rely on)
 * with the host's accent roles, so the studio wears the app's live theme
 * colour while its surfaces stay neutral.
 */
@Composable
internal fun VideoWorkspaceTheme(content: @Composable () -> Unit) {
    val host = MaterialTheme.colorScheme
    val neutral = if (host.surface.luminance() < .5f) V3VideoDarkColors else V3VideoLightColors
    WorkspaceTheme(remember(host, neutral) { neutral.withAccentsOf(host) }, content)
}

/** [this] neutral scheme carrying [host]'s accent, container and error roles. */
internal fun ColorScheme.withAccentsOf(host: ColorScheme): ColorScheme = copy(
    primary = host.primary,
    onPrimary = host.onPrimary,
    primaryContainer = host.primaryContainer,
    onPrimaryContainer = host.onPrimaryContainer,
    inversePrimary = host.inversePrimary,
    secondary = host.secondary,
    onSecondary = host.onSecondary,
    secondaryContainer = host.secondaryContainer,
    onSecondaryContainer = host.onSecondaryContainer,
    tertiary = host.tertiary,
    onTertiary = host.onTertiary,
    tertiaryContainer = host.tertiaryContainer,
    onTertiaryContainer = host.onTertiaryContainer,
    error = host.error,
    onError = host.onError,
    errorContainer = host.errorContainer,
    onErrorContainer = host.onErrorContainer,
    surfaceTint = host.primary
)

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
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF182331),
    secondaryContainer = Color(0xFF475466),
    onSecondaryContainer = Color(0xFFF4F4F4),
    surface = Color.Black,
    onSurface = Color(0xFFF4F4F4),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF111315),
    surfaceContainer = Color(0xFF1B1D20),
    surfaceContainerHigh = Color(0xFF1B1D20),
    surfaceContainerHighest = Color(0xFF2B3038),
    surfaceVariant = Color(0xFF1B1D20),
    onSurfaceVariant = Color(0xFFB8B8B8),
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFF353535)
)

// V3 light: white surfaces, #F5F5F5 low containers, #EAEAEA raised, #DCDCDC
// outlines, dark steel-blue primary.
internal val V3LightColors = lightColorScheme(
    primary = Color(0xFF244C76),
    onPrimary = Color.White,
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

// Video dark: the studio palette on the photo editor's darker workspace —
// a black surround and near-black containers. Controls on a panel sit one
// tonal step above it, so surfaceContainerHigh must stay distinct from
// surfaceContainer. Accents are samples: VideoWorkspaceTheme replaces them
// with the host's.
internal val V3VideoDarkColors = V3DarkColors.copy(
    primary = Color(0xFFA7C8FF),
    onPrimary = Color(0xFF003060),
    primaryContainer = Color(0xFF004787),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondaryContainer = Color(0xFF3D4758),
    onSecondaryContainer = Color(0xFFD7E3F8),
    tertiary = Color(0xFFDBBDE2),
    onTertiary = Color(0xFF3E2845),
    tertiaryContainer = Color(0xFF553F5D),
    onTertiaryContainer = Color(0xFFF8D8FE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    onSurface = Color(0xFFE3E2E6),
    surfaceContainerLowest = Color(0xFF08090A),
    surfaceContainerLow = Color(0xFF111315),
    surfaceContainer = Color(0xFF1B1D20),
    surfaceContainerHigh = Color(0xFF26292E),
    surfaceContainerHighest = Color(0xFF33373E),
    surfaceVariant = Color(0xFF26292E),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E)
)

// Video light: fully light — white surround and the same tonal steps on
// light neutrals, blue and lilac in place of Material's default purple.
internal val V3VideoLightColors = V3LightColors.copy(
    primary = Color(0xFF005EB1),
    primaryContainer = Color(0xFFD5E3FF),
    onPrimaryContainer = Color(0xFF001C3B),
    secondaryContainer = Color(0xFFD9E3F8),
    onSecondaryContainer = Color(0xFF121C2B),
    tertiary = Color(0xFF6E5676),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8D8FE),
    onTertiaryContainer = Color(0xFF27132F),
    onSurface = Color(0xFF1A1C1E),
    surfaceContainerLow = Color(0xFFF4F4F6),
    surfaceContainer = Color(0xFFECEDF0),
    surfaceContainerHigh = Color(0xFFE2E3E7),
    surfaceContainerHighest = Color(0xFFD6D8DD),
    surfaceVariant = Color(0xFFE2E3E7),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF)
)

/** The tool card's base: the photo editor's 0xFF141416 in dark, its light twin otherwise. */
internal val VideoToolCardDark = Color(0xFF141416)
internal val VideoToolCardLight = Color(0xFFF1F2F4)
