package com.dot.gallery.core.presentation.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dot.gallery.ui.theme.isDarkTheme
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3



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
 * Blur cost follows the resolved [dev.chrisbanes.haze.HazePerformanceMode] (the style's own
 * `performanceMode`, else [dev.chrisbanes.haze.LocalHazePerformanceMode]). It shrinks the content
 * captured behind the effect by a fixed factor of `sqrt(0.25 + 0.75 * qualityFraction)` before
 * blurring, and scales the blur radius by the same factor: Default/Balanced ≈ 0.79, Performance
 * 0.5, Quality 1.0. The factor does not depend on the radius.
 */
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
            style = GlassStyle.material3(
                containerColor = container,
                // The shader mixes tint.rgb over the refracted content by tint.a — this is the
                // solidity dial: high enough for foreground contrast, low enough for the
                // refraction to stay visible.
                tint = container.copy(alpha = 0.5f),
            ).then {
                // The regular preset only refracts a 20.dp edge band; Surface refracts across
                // the whole material with a stronger, taller lens so the effect reads as glass.
                optics(
                    GlassOptics.regular.copy(
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

/**
 * Transparent top bar over scrolling content: a progressive blur strongest at the top edge that
 * fades to nothing before the bar's bottom edge, with no tint, so the bar meets the content
 * without a seam. Inherits `blurEnabled` from [LocalHazeBlurStyle]; with blur off it falls back to
 * the opaque surface.
 */
@Composable
fun rememberTopBarBlurStyle(): HazeBlurStyle {
    val fallback = MaterialTheme.colorScheme.surface
    return remember(fallback) {
        HazeBlurStyle {
            blurRadius(24.dp)
            noiseFactor(0f)
            // The source list is transparent between rows; fill it with the page colour so the
            // inherited material container doesn't show through as a grey band.
            backgroundColor(fallback)
            colorEffects(emptyList())
            fallbackColorEffect(HazeColorEffect.tint(fallback))
            progressive(HazeProgressive.Brush(fadeOutTowards(bottom = true)))
        }
    }
}

/**
 * [rememberTopBarBlurStyle] for a bar over a SurfaceView that reaches haze as a low-resolution
 * snapshot (the map): a stronger blur hides the snapshot's pixels, and a mask fades the whole
 * effect out toward the bar's bottom edge so the snapshot never shows next to the live surface.
 */
@Composable
fun rememberSurfaceTopBarBlurStyle(): HazeBlurStyle {
    val fallback = MaterialTheme.colorScheme.surface
    return remember(fallback) {
        HazeBlurStyle {
            blurRadius(32.dp)
            noiseFactor(0f)
            backgroundColor(fallback)
            colorEffects(emptyList())
            fallbackColorEffect(HazeColorEffect.tint(fallback))
            progressive(HazeProgressive.Brush(fadeOutTowards(bottom = true)))
            // Fully on through the title row, then fades out over the bar's last fifth.
            mask(Brush.verticalGradient(0f to Color.Black, 0.8f to Color.Black, 1f to Color.Transparent))
        }
    }
}

/**
 * Transparent bottom bar over scrolling content: a progressive blur strongest at the bottom edge
 * with no tint. Content under it needs about 112 dp of bottom padding.
 */
@Composable
fun rememberBottomBarBlurStyle(): HazeBlurStyle {
    val fallback = MaterialTheme.colorScheme.surface
    return remember(fallback) {
        HazeBlurStyle {
            blurRadius(24.dp)
            noiseFactor(0f)
            backgroundColor(fallback)
            colorEffects(emptyList())
            fallbackColorEffect(HazeColorEffect.tint(fallback))
            progressive(HazeProgressive.Brush(fadeOutTowards(bottom = false)))
        }
    }
}

/**
 * Blur intensity for a transparent bar: full at the outer edge, still strong across the middle
 * where the title and buttons sit, and zero exactly at the inner edge so the bar meets the
 * content without a seam. No tint is drawn, since a tint that has not fully cleared at the inner
 * edge shows as a hard line.
 */
private fun fadeOutTowards(bottom: Boolean): Brush = if (bottom) {
    Brush.verticalGradient(0f to Color.Black, 0.55f to Color.Black.copy(alpha = 0.7f), 1f to Color.Transparent)
} else {
    Brush.verticalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.7f), 1f to Color.Black)
}
