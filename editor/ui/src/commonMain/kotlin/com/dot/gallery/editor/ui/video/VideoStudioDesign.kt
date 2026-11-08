package com.dot.gallery.editor.ui.video

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.CropRotate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material.icons.outlined.ViewTimeline
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import com.dot.gallery.editor.ui.VideoToolCardDark
import com.dot.gallery.editor.ui.VideoToolCardLight
import com.dot.gallery.editor.ui.chromeBlur
import com.dot.gallery.editor.ui.editorSheetBlur
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The video studio's design vocabulary: one place for the geometry the
 * screens share (corner radii, control heights, the glass material) and
 * the expressive shapes — the scalloped "cookie" that marks a ready
 * primary control and the soft squircle it springs into while active.
 * Every colour comes from the Material roles of the hosting theme; no
 * parallel palette lives here.
 */
internal object VideoStudio {
    /** Outer radius of every floating card — timeline, panel, dock, sheet. */
    val CardCorner = 28.dp

    /** Inner joins of a connected button group. */
    val JoinCorner = 6.dp

    /** The connected group's outer ends at 44 dp tall controls. */
    val GroupEnd = 22.dp

    /** The connected group's outer ends at 52 dp tall segments. */
    val SegmentEnd = 26.dp

    /** Header and dock controls — the shared 48 dp touch floor. */
    val Control = 48.dp

    /** Command-row buttons in the timeline card. */
    val Command = 44.dp

    /** Horizontal gap between connected group members. */
    val GroupGap = 2.dp

    /** Lane gutter beside the timeline lanes. */
    val Gutter = 32.dp

    /** The ruler strip above the lanes. */
    val Ruler = 24.dp

    /** Corner of an empty lane well. */
    val LaneCorner = 12.dp

    /** The connected clip corners — outer ends and the joins between clips. */
    val ClipOuter = 12.dp
    val ClipJoin = 4.dp
    val ClipSelected = 16.dp

    /** Audio clip corners. */
    val AudioClip = 10.dp

    /** A labelled rail slot; with the 8 dp gap it keeps the photo rail's 62 dp pitch. */
    val RailSlot = 54.dp

    /** A bar slot when the bar hugs its items — the photo editor's large-portrait pitch. */
    val BarSlot = 85.dp
}

/**
 * The corner shape of member [index] of a connected group of [count]:
 * [outer] at the group's two ends, [inner] at every join. A selected
 * member springs to a full pill via [selectedCorner].
 */
internal fun videoConnectedShape(
    index: Int,
    count: Int,
    outer: Dp,
    inner: Dp = VideoStudio.JoinCorner,
    selectedCorner: Dp? = null
): RoundedCornerShape {
    if (selectedCorner != null) return RoundedCornerShape(selectedCorner)
    val start = if (index == 0) outer else inner
    val end = if (index == count - 1) outer else inner
    return RoundedCornerShape(
        topStart = start, bottomStart = start,
        topEnd = end, bottomEnd = end
    )
}

/**
 * The raised tone for controls that sit on a container-coloured card or
 * sheet (segments, rows, chips, tiles, steppers). Themes that collapse
 * `surfaceContainerHigh` onto `surfaceContainer` would make those
 * controls vanish, so the slab steps up to the highest container there.
 */
@Composable
internal fun videoSlabColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surfaceContainerHigh == scheme.surfaceContainer) {
        scheme.surfaceContainerHighest
    } else {
        scheme.surfaceContainerHigh
    }
}

/**
 * The floating chrome material shared by the header capsules and the dock —
 * the photo editor's [chromeBlur]: surfaceContainerHigh at 60 % over a
 * backdrop blur, opaque at identical geometry when the host disables blur.
 */
@Composable
internal fun Modifier.videoGlass(shape: Shape): Modifier = chromeBlur(shape)

/** The tool card's base colour: the photo tool card in dark, its light twin otherwise. */
@Composable
internal fun videoToolCardColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < .5f) VideoToolCardDark else VideoToolCardLight

/**
 * The tool card material — the photo editor's PhotoToolSheetSurface recipe
 * (32 dp backdrop blur under the card colour at 82 %), opaque when blur is off.
 */
@Composable
internal fun Modifier.videoToolCard(shape: Shape): Modifier =
    editorSheetBlur(shape = shape, baseColor = videoToolCardColor(), blurRadius = 32.dp)

/** The radius of the polar outline at angle [a] for [kind]. */
private fun polarRadius(kind: Int, a: Double): Double = when (kind) {
    // Nine soft scallops around a 0.46 base.
    0 -> 0.46 + 0.04 * cos(a * 9)
    // A superellipse (p = 4.4) scaled to 94 % — the soft square.
    else -> {
        val c = abs(cos(a))
        val s = abs(sin(a))
        val p = 4.4
        0.5 * (c.pow(p) + s.pow(p)).pow(-1.0 / p) * 0.94
    }
}

/**
 * A shape morphing between the scalloped cookie ([progress] 0) and the
 * soft squircle ([progress] 1). Both outlines share the same 72 polar
 * vertices, so any in-between progress is a valid outline and a spring
 * on [progress] animates the play / record buttons without a hard swap.
 */
internal class VideoMorphShape(private val progress: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val n = 72
        val t = progress.coerceIn(0f, 1f).toDouble()
        for (i in 0 until n) {
            val a = i.toDouble() / n * PI * 2.0
            val r = polarRadius(0, a) * (1.0 - t) + polarRadius(1, a) * t
            val x = (0.5 + r * cos(a)).toFloat() * size.width
            val y = (0.5 + r * sin(a)).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

internal val VideoCookieShape: Shape = VideoMorphShape(0f)
/**
 * The expressive progress line: a gentle sine over the elapsed part that
 * moves while [playing], a flat rest otherwise, and the remaining track
 * after an 8 dp break. Purely decorative — the timeline ruler owns the
 * seek semantics.
 */
@Composable
internal fun VideoWavyProgress(
    fraction: Float,
    playing: Boolean,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 3.dp,
    amplitude: Dp = 2.4.dp,
    height: Dp = 10.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant
) {
    val phase = if (playing) {
        val transition = rememberInfiniteTransition(label = "videoWave")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                tween(1200, easing = LinearEasing),
                RepeatMode.Restart
            ),
            label = "videoWavePhase"
        )
        value
    } else {
        0f
    }
    Canvas(modifier.fillMaxWidth().height(height)) {
        val stroke = strokeWidth.toPx()
        val mid = size.height / 2f
        val end = fraction.coerceIn(0f, 1f) * size.width
        val amp = if (playing) amplitude.toPx() else 0f
        val gap = 8.dp.toPx()
        if (end > 1f) {
            val path = Path().apply {
                moveTo(0f, mid)
                var x = 2.dp.toPx()
                val step = 2.dp.toPx()
                // One wavelength every ~84 dp, like the reference line.
                val k = (2f * PI / 84.dp.toPx()).toFloat()
                while (x <= end) {
                    lineTo(x, mid + sin(x * k + phase) * amp)
                    x += step
                }
            }
            drawPath(
                path = path,
                color = activeColor,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        val trackStart = (end + gap).coerceAtMost(size.width)
        if (trackStart < size.width) {
            drawLine(
                color = trackColor,
                start = Offset(trackStart, mid),
                end = Offset(size.width, mid),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * The Material Symbols the video studio uses, in one place so every
 * surface reads the same glyph for the same command.
 */
internal object VideoGlyphs {
    val Close: ImageVector get() = Icons.Outlined.Close
    val Undo: ImageVector get() = Icons.AutoMirrored.Outlined.Undo
    val Redo: ImageVector get() = Icons.AutoMirrored.Outlined.Redo
    val ExpandMore: ImageVector get() = Icons.Outlined.ExpandMore
    val Check: ImageVector get() = Icons.Outlined.Check
    val SkipPrevious: ImageVector get() = Icons.Outlined.SkipPrevious
    val SkipNext: ImageVector get() = Icons.Outlined.SkipNext
    val Play: ImageVector get() = Icons.Filled.PlayArrow
    val Pause: ImageVector get() = Icons.Filled.Pause
    val Stop: ImageVector get() = Icons.Filled.Stop
    val VolumeUp: ImageVector get() = Icons.AutoMirrored.Outlined.VolumeUp
    val VolumeOff: ImageVector get() = Icons.AutoMirrored.Outlined.VolumeOff
    val OpenInFull: ImageVector get() = Icons.Outlined.OpenInFull
    val CloseFullscreen: ImageVector get() = Icons.Outlined.CloseFullscreen
    val Trim: ImageVector get() = Icons.Outlined.ViewWeek
    val Split: ImageVector get() = Icons.Outlined.ContentCut
    val Delete: ImageVector get() = Icons.Outlined.Delete
    val Arrange: ImageVector get() = Icons.Outlined.ViewTimeline
    val Remove: ImageVector get() = Icons.Outlined.Remove
    val Add: ImageVector get() = Icons.Outlined.Add
    val Overlay: ImageVector get() = Icons.Outlined.PictureInPicture
    val Movie: ImageVector get() = Icons.Outlined.Movie
    val MusicNote: ImageVector get() = Icons.Outlined.MusicNote
    val Mic: ImageVector get() = Icons.Outlined.Mic
    val Media: ImageVector get() = Icons.Outlined.PermMedia
    val Layers: ImageVector get() = Icons.Outlined.Layers
    val Audio: ImageVector get() = Icons.Outlined.GraphicEq
    val Tune: ImageVector get() = Icons.Outlined.Tune
    val Reset: ImageVector get() = Icons.Outlined.RestartAlt
    val Transform: ImageVector get() = Icons.Outlined.CropRotate
    val Palette: ImageVector get() = Icons.Outlined.Palette
    val LibraryMusic: ImageVector get() = Icons.Outlined.LibraryMusic
    val Routing: ImageVector get() = Icons.Outlined.AltRoute
    val Ducking: ImageVector get() = Icons.Outlined.Compress
    val Eq: ImageVector get() = Icons.Outlined.Equalizer
    val Search: ImageVector get() = Icons.Outlined.Search
    val Apps: ImageVector get() = Icons.Outlined.Apps
    val AppendEnd: ImageVector get() = Icons.AutoMirrored.Outlined.PlaylistAdd
    val InsertPlayhead: ImageVector get() = Icons.Outlined.VerticalAlignCenter
    val Scale: ImageVector get() = Icons.Outlined.ZoomOutMap
    val Rotation: ImageVector get() = Icons.Outlined.RotateRight
    val PositionX: ImageVector get() = Icons.Outlined.SwapHoriz
    val PositionY: ImageVector get() = Icons.Outlined.SwapVert
    val Opacity: ImageVector get() = Icons.Outlined.Opacity
    val Exposure: ImageVector get() = Icons.Outlined.Exposure
    val Contrast: ImageVector get() = Icons.Outlined.Contrast
    val Highlights: ImageVector get() = Icons.Outlined.Layers
    val Shadows: ImageVector get() = Icons.Outlined.FilterDrama
    val Saturation: ImageVector get() = Icons.Outlined.WaterDrop
    val Warmth: ImageVector get() = Icons.Outlined.Thermostat
    val Pan: ImageVector get() = Icons.Outlined.SurroundSound
    val FadeIn: ImageVector get() = Icons.AutoMirrored.Outlined.TrendingUp
    val FadeOut: ImageVector get() = Icons.AutoMirrored.Outlined.TrendingDown
    val Left: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowBack
    val Right: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowForward
    val Up: ImageVector get() = Icons.Outlined.ArrowUpward
    val Down: ImageVector get() = Icons.Outlined.ArrowDownward
    val Low: ImageVector get() = Icons.Outlined.Speaker
    val Mid: ImageVector get() = Icons.Outlined.RecordVoiceOver
    val HighPass: ImageVector get() = Icons.Outlined.FilterAlt
    val Centre: ImageVector get() = Icons.Outlined.CenterFocusStrong
    val Flip: ImageVector get() = Icons.Outlined.Flip
    val Duplicate: ImageVector get() = Icons.Outlined.ContentCopy
    val Visible: ImageVector get() = Icons.Outlined.Visibility
    val Hidden: ImageVector get() = Icons.Outlined.VisibilityOff
    val Lock: ImageVector get() = Icons.Outlined.Lock
    val Unlock: ImageVector get() = Icons.Outlined.LockOpen
}
