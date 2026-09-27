package com.dot.gallery.feature_node.presentation.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3

val LocalHazeState = compositionLocalOf { HazeState() }

/**
 * When true, [hazeEffectScaled] renders [hazeGlass] (Liquid-Glass-style refraction) instead of
 * plain [hazeBlur]. Provided per-activity next to [LocalHazeState] and gated on the allow-blur
 * preference, so turning blur off still yields the fallback scrim everywhere.
 */
val LocalHazeGlassEnabled = compositionLocalOf { false }

/**
 * Unified frosted-surface effect: [hazeGlass] when [LocalHazeGlassEnabled] is set, otherwise
 * [hazeBlur] over the shared [HazeState] sources.
 *
 * The default performance mode already downsamples the blur input for large radii — visually
 * identical output at a fraction of the shader cost, matching the old HazeInputScale.Auto
 * behavior of this helper.
 */
@OptIn(ExperimentalHazeApi::class)
@Stable
fun Modifier.hazeEffectScaled(
    state: HazeState,
    style: HazeBlurStyle,
    /**
     * Accent color for glass mode only — surfaces whose blur [style] carries a non-surface
     * containerColor (e.g. primaryFixed/tertiaryFixed action buttons) must pass it here too,
     * since [HazeBlurStyle] is opaque and its color can't be read back out. Drives both the
     * glass background and the tint, so the surface keeps its colored-material look.
     */
    glassContainerColor: Color? = null,
): Modifier = composed {
    if (LocalHazeGlassEnabled.current) {
        // Haze picks its built-in light/dark glass response from the platform uiMode, which knows
        // nothing about the app's forced theme — replay the matching response so the glass follows
        // the app theme (an unresolved forced-dark UI on a light system renders light glass, i.e.
        // a white-ish surface).
        val dark = isDarkTheme()
        val container = glassContainerColor ?: MaterialTheme.colorScheme.surfaceContainer
        hazeGlass(
            input = HazeInput.Backdrop(state),
            style = GlassStyle.Material3(
                containerColor = container,
                // The shader mixes tint.rgb over the refracted content by tint.a — this is the
                // solidity dial: high enough for foreground contrast, low enough for the
                // refraction to stay visible.
                tint = container.copy(alpha = 0.5f),
            ).then {
                // The regular preset only refracts a 20.dp edge band; Surface refracts across
                // the whole material with a stronger, taller lens so the effect reads as glass.
                optics(
                    GlassDefaults.optics.copy(
                        refractionStrength = 0.9f,
                        refractionDisplacement = 60.dp,
                        refractionHeightFraction = 0.35f,
                        refractionDetailIntensity = 0.5f,
                        refractionProfile = RefractionProfile.Surface,
                    ),
                )
                chromaticAberrationStrength(0.15f)
                if (dark) {
                    // Mirrors the built-in regularDark response (internal to haze — keep in sync
                    // with GlassStyle.regularDark when bumping the version).
                    specularIntensity(0.38f)
                    edgeShadow(Color.Black.copy(alpha = 0.32f))
                    ambientResponse(0.08f)
                    contrast(0.08f)
                    whitePoint(-0.22f)
                    chromaMultiplier(1.1f)
                } else {
                    specularIntensity(GlassDefaults.specularIntensity)
                    edgeShadow(GlassDefaults.edgeShadow)
                    ambientResponse(GlassDefaults.ambientResponse)
                    contrast(GlassDefaults.contrast)
                    whitePoint(GlassDefaults.whitePoint)
                    chromaMultiplier(GlassDefaults.chromaMultiplier)
                }
            },
        )
    } else {
        hazeBlur(
            input = HazeInput.Backdrop(state),
            style = style,
        )
    }
}
