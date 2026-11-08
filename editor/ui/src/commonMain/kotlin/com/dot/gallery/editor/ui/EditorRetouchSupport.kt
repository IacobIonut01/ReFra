package com.dot.gallery.editor.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoCutoutMatte
import com.dot.gallery.editor.render.SrgbPixels
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_black
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_blue
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_brown
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_green
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_magenta
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_orange
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_pink
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_red
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_slate
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_teal
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_violet
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_white
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_colour_yellow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** One include or exclude prompt in normalized output coordinates. */
data class EditorSegmentPoint(val x: Float, val y: Float, val include: Boolean)

/** A face rectangle in normalized output coordinates. */
data class EditorFaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** A running subject segmentation over one encoded picture. */
interface EditorSegmenterSession : AutoCloseable {
    /** The mask for [points], or null when no subject could be selected. */
    suspend fun segment(points: List<EditorSegmentPoint>): PhotoCutoutMatte?
}

/** What a host may add to the editor beyond the shared rendering engine. */
class EditorHostTools(
    /**
     * Encodes the picture for interactive selection. Null, or a host whose
     * models are not installed, leaves Cutout and Remove background out of
     * the catalogue. The returned session is null when encoding fails.
     */
    val openSegmenter: (suspend (SrgbPixels) -> EditorSegmenterSession?)? = null,
    /** Finds faces for the blur/mosaic faces actions; null hides them. */
    val detectFaces: (suspend (SrgbPixels) -> List<EditorFaceBox>)? = null,
    /** Puts the transparent subject on the clipboard; true on success. */
    val copySubject: (suspend (SrgbPixels) -> Boolean)? = null,
    /** Opens the share sheet for the transparent subject; true on success. */
    val shareSubject: (suspend (SrgbPixels) -> Boolean)? = null,
    /** Hands the source photo to another app's editor. */
    val openInExternalEditor: (() -> Unit)? = null
)

val LocalEditorHostTools = staticCompositionLocalOf { EditorHostTools() }

/** The retouch cards' inner padding, matching the design's 12 dp panel. */
internal val RetouchCardPadding = PaddingValues(12.dp)

/** How far below the safe area floating tool chrome clears the header. */
internal val RETOUCH_HEADER_CLEARANCE = 72.dp

internal class RetouchColour(val argb: Int, val nameRes: StringResource)

/** The shared ink palette of Markup, text labels and Borders. */
internal val RetouchColours = listOf(
    RetouchColour(0xFF1A1A1A.toInt(), Res.string.editor_retouch_colour_black),
    RetouchColour(0xFFFF0000.toInt(), Res.string.editor_retouch_colour_red),
    RetouchColour(0xFFFF6D00.toInt(), Res.string.editor_retouch_colour_orange),
    RetouchColour(0xFFFFFF00.toInt(), Res.string.editor_retouch_colour_yellow),
    RetouchColour(0xFF00C853.toInt(), Res.string.editor_retouch_colour_green),
    RetouchColour(0xFF00BFA5.toInt(), Res.string.editor_retouch_colour_teal),
    RetouchColour(0xFF2962FF.toInt(), Res.string.editor_retouch_colour_blue),
    RetouchColour(0xFF6200EA.toInt(), Res.string.editor_retouch_colour_violet),
    RetouchColour(0xFFFF00FF.toInt(), Res.string.editor_retouch_colour_magenta),
    RetouchColour(0xFFFF80AB.toInt(), Res.string.editor_retouch_colour_pink),
    RetouchColour(0xFF8D6E63.toInt(), Res.string.editor_retouch_colour_brown),
    RetouchColour(0xFF78909C.toInt(), Res.string.editor_retouch_colour_slate),
    RetouchColour(0xFFFFFFFF.toInt(), Res.string.editor_retouch_colour_white)
)

/**
 * The swatch row: 44 by 48 dp targets, and the selected swatch grows and
 * squares off a little so the choice reads without relying on hue alone.
 */
@Composable
internal fun RetouchColourRow(
    selected: Int,
    enabled: Boolean,
    tag: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RetouchColours.forEach { colour ->
            val active = colour.argb == selected
            val name = stringResource(colour.nameRes)
            val size by animateDpAsState(
                if (active) 34.dp else 26.dp,
                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label = "swatch"
            )
            val shape = RoundedCornerShape(if (active) 12.dp else 13.dp)
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 48.dp)
                    .clickable(enabled = enabled, role = Role.Button, onClickLabel = name) {
                        onSelect(colour.argb)
                    }
                    .semantics {
                        contentDescription = name
                        this.selected = active
                    }
                    .testTag("$tag.${colour.argb.toUInt().toString(16)}"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(shape)
                        .background(Color(colour.argb))
                        .border(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.35f),
                            shape = shape
                        )
                )
            }
        }
    }
}

/** A 52 dp tonal action in the tools' foot row. */
@Composable
internal fun RetouchActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = tint,
            maxLines = 1
        )
    }
}

/** A small one-line hint under the tool's controls. */
@Composable
internal fun RetouchHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 8.dp)
    )
}

/**
 * The floating pill behind the Draw, Move and Erase choice: 48 dp targets over
 * a 40 dp selection disc.
 */
@Composable
internal fun RetouchPillItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics {
                contentDescription = label
                this.selected = selected
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private val symbolCache = HashMap<String, ImageVector>()

private fun retouchSymbol(
    name: String,
    pathData: String,
    viewport: Float = 24f
): ImageVector =
    symbolCache.getOrPut(name) {
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = viewport,
            viewportHeight = viewport
        ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
    }

/** The Material Symbols the retouch tools use, kept beside the tools. */
internal object RetouchSymbols {
    val Draw get() = retouchSymbol(
        "Draw",
        "M18.85 10.39l1.06-1.06c.78-.78.78-2.05 0-2.83L18.5 5.09c-.78-.78-2.05-.78-2.83 0l-1.06 1.06 4.24 4.24zm-5.66-2.83L4 16.76V21h4.24l9.19-9.19-4.24-4.25zM19 17.5c0 2.19-2.54 3.5-5 3.5-.55 0-1-.45-1-1s.45-1 1-1c1.54 0 3-.73 3-1.5 0-.47-.48-.87-1.23-1.2l1.48-1.48c1.07.63 1.75 1.47 1.75 2.68zM4.58 13.35C3.61 12.79 3 12.06 3 11c0-1.8 1.89-2.63 3.56-3.36C7.59 7.18 9 6.56 9 6c0-.41-.78-1-2-1-1.26 0-1.8.61-1.83.64-.35.41-.98.46-1.4.12-.41-.34-.49-.95-.15-1.38C3.73 4.24 4.76 3 7 3s4 1.32 4 3c0 1.87-1.93 2.72-3.64 3.47C6.42 9.88 5 10.5 5 11c0 .31.43.6 1.07.86l-1.49 1.49z"
    )
    val Pen get() = retouchSymbol(
        "Pen",
        "M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"
    )
    val Highlighter get() = retouchSymbol(
        "Highlighter",
        "M544 560L440 456L240 656Q240 656 240 656Q240 656 240 656L344 760Q344 760 344 760Q344 760 344 760L544 560ZM497 399L601 503L800 304Q800 304 800 304Q800 304 800 304L696 200Q696 200 696 200Q696 200 696 200L497 399ZM413 371L629 587L400 816Q376 840 344 840Q312 840 288 816L286 814L260 840L60 840L186 714L184 712Q160 688 160 656Q160 624 184 600L413 371ZM413 371L640 144Q664 120 696 120Q728 120 752 144L856 248Q880 272 880 304Q880 336 856 360L629 587L413 371Z",
        viewport = 960f
    )
    val Mosaic get() = retouchSymbol(
        "Mosaic",
        "M3 3v8h8V3H3zm6 6H5V5h4v4zm-6 4v8h8v-8H3zm6 6H5v-4h4v4zm4-16v8h8V3h-8zm6 6h-4V5h4v4zm-6 4v8h8v-8h-8zm6 6h-4v-4h4v4z"
    )
    val Text get() = retouchSymbol(
        "TextFields",
        "M2.5 4v3h5v12h3V7h5V4h-13zm19 5h-9v3h3v7h3v-7h3V9z"
    )
    val Pan get() = retouchSymbol(
        "PanTool",
        "M23 5.5V20c0 2.2-1.8 4-4 4h-7.3c-1.08 0-2.1-.43-2.85-1.19L1 14.83s1.26-1.23 1.3-1.25c.22-.19.49-.29.79-.29.22 0 .42.06.6.16.04.01 4.31 2.46 4.31 2.46V4c0-.83.67-1.5 1.5-1.5S11 3.17 11 4v7h1V1.5c0-.83.67-1.5 1.5-1.5S15 .67 15 1.5V11h1V2.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5V11h1V5.5c0-.83.67-1.5 1.5-1.5s1.5.67 1.5 1.5z"
    )
    val Eraser get() = retouchSymbol(
        "Eraser",
        "M690 720h190v80H610Zm-500 80l-85 -85q-23 -23 -23.5 -57t22.5 -58l440 -456q23 -24 56.5 -24t56.5 23l199 199q23 23 23 57t-23 57L520 800Zm296 -80l314 -322l-198 -198l-442 456l64 64Zm-6 -240",
        viewport = 960f
    )
    val Add get() = retouchSymbol("Add", "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z")
    val Remove get() = retouchSymbol("Remove", "M19 13H5v-2h14v2z")
    val Share get() = retouchSymbol(
        "Share",
        "M18 16.08c-.76 0-1.44.3-1.96.77L8.91 12.7c.05-.23.09-.46.09-.7s-.04-.47-.09-.7l7.05-4.11c.54.5 1.25.81 2.04.81 1.66 0 3-1.34 3-3s-1.34-3-3-3-3 1.34-3 3c0 .24.04.47.09.7L8.04 9.81C7.5 9.31 6.79 9 6 9c-1.66 0-3 1.34-3 3s1.34 3 3 3c.79 0 1.5-.31 2.04-.81l7.12 4.16c-.05.21-.08.43-.08.65 0 1.61 1.31 2.92 2.92 2.92s2.92-1.31 2.92-2.92-1.31-2.92-2.92-2.92z"
    )
    val Copy get() = retouchSymbol(
        "ContentCopy",
        "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"
    )
    val Face get() = retouchSymbol(
        "Face",
        "M9 11.75c-.69 0-1.25.56-1.25 1.25s.56 1.25 1.25 1.25 1.25-.56 1.25-1.25-.56-1.25-1.25-1.25zm6 0c-.69 0-1.25.56-1.25 1.25s.56 1.25 1.25 1.25 1.25-.56 1.25-1.25-.56-1.25-1.25-1.25zM12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8 0-.29.02-.58.05-.86 2.36-1.05 4.23-2.98 5.21-5.37C11.07 8.33 14.05 10 17.42 10c.78 0 1.53-.09 2.25-.26.21.71.33 1.47.33 2.26 0 4.41-3.59 8-8 8z"
    )
    val Texture get() = retouchSymbol(
        "Texture",
        "M19.51 3.08L3.08 19.51c.09.34.27.65.51.9.25.24.56.42.9.51L20.93 4.49c-.19-.69-.73-1.23-1.42-1.41zM11.88 3L3 11.88v2.83L14.71 3h-2.83zM5 3c-1.1 0-2 .9-2 2v2l4-4H5zm14 18c.55 0 1.05-.22 1.41-.59.37-.36.59-.86.59-1.41v-2l-4 4h2zm-9.71 0h2.83L21 12.12V9.29L9.29 21z"
    )
    val GridOn get() = retouchSymbol(
        "GridOn",
        "M20 2H4c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM8 20H4v-4h4v4zm0-6H4v-4h4v4zm0-6H4V4h4v4zm6 12h-4v-4h4v4zm0-6h-4v-4h4v4zm0-6h-4V4h4v4zm6 12h-4v-4h4v4zm0-6h-4v-4h4v4zm0-6h-4V4h4v4z"
    )
    val CropDin get() = retouchSymbol(
        "CropDin",
        "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V5h14v14z"
    )
    val Waves get() = retouchSymbol(
        "Waves",
        "M17 16.99c-1.35 0-2.2.42-2.95.8-.65.33-1.18.6-2.05.6-.9 0-1.4-.25-2.05-.6-.75-.38-1.57-.8-2.95-.8s-2.2.42-2.95.8c-.65.33-1.17.6-2.05.6v1.95c1.35 0 2.2-.42 2.95-.8.65-.33 1.17-.6 2.05-.6s1.4.25 2.05.6c.75.38 1.57.8 2.95.8s2.2-.42 2.95-.8c.65-.33 1.18-.6 2.05-.6.9 0 1.4.25 2.05.6.75.38 1.58.8 2.95.8v-1.95c-.9 0-1.4-.25-2.05-.6-.75-.38-1.6-.8-2.95-.8zm0-4.45c-1.35 0-2.2.43-2.95.8-.65.32-1.18.6-2.05.6-.9 0-1.4-.25-2.05-.6-.75-.38-1.57-.8-2.95-.8s-2.2.43-2.95.8c-.65.32-1.17.6-2.05.6v1.95c1.35 0 2.2-.43 2.95-.8.65-.35 1.15-.6 2.05-.6s1.4.25 2.05.6c.75.38 1.57.8 2.95.8s2.2-.43 2.95-.8c.65-.35 1.15-.6 2.05-.6s1.4.25 2.05.6c.75.38 1.58.8 2.95.8v-1.95c-.9 0-1.4-.25-2.05-.6-.75-.38-1.6-.8-2.95-.8zm2.95-8.08c-.75-.38-1.58-.8-2.95-.8s-2.2.42-2.95.8c-.65.32-1.18.6-2.05.6-.9 0-1.4-.25-2.05-.6-.75-.37-1.57-.8-2.95-.8s-2.2.42-2.95.8c-.65.33-1.17.6-2.05.6v1.93c1.35 0 2.2-.43 2.95-.8.65-.33 1.17-.6 2.05-.6s1.4.25 2.05.6c.75.38 1.57.8 2.95.8s2.2-.43 2.95-.8c.65-.32 1.18-.6 2.05-.6.9 0 1.4.25 2.05.6.75.38 1.58.8 2.95.8V5.04c-.9 0-1.4-.25-2.05-.58zM17 8.09c-1.35 0-2.2.43-2.95.8-.65.35-1.15.6-2.05.6s-1.4-.25-2.05-.6c-.75-.38-1.57-.8-2.95-.8s-2.2.43-2.95.8c-.65.35-1.15.6-2.05.6v1.95c1.35 0 2.2-.43 2.95-.8.65-.32 1.18-.6 2.05-.6s1.4.25 2.05.6c.75.38 1.57.8 2.95.8s2.2-.43 2.95-.8c.65-.32 1.18-.6 2.05-.6.9 0 1.4.25 2.05.6.75.38 1.58.8 2.95.8V9.49c-.9 0-1.4-.25-2.05-.6-.75-.38-1.6-.8-2.95-.8z"
    )
    val Contrast get() = retouchSymbol(
        "Contrast",
        "M12 22c5.52 0 10-4.48 10-10S17.52 2 12 2 2 6.48 2 12s4.48 10 10 10zm1-17.93c3.94.49 7 3.85 7 7.93s-3.05 7.44-7 7.93V4.07z"
    )
    val LineWeight get() = retouchSymbol(
        "LineWeight",
        "M3 17h18v-2H3v2zm0 3h18v-1H3v1zm0-7h18v-3H3v3zm0-9v4h18V4H3z"
    )
    val Image get() = retouchSymbol(
        "Image",
        "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3.01L14.5 12l4.5 6H5l3.5-4.5z"
    )
    val RawOn get() = retouchSymbol(
        "RawOn",
        "M6.5 9H3v6h1.5v-2h1.1l.9 2H8l-.9-2.1c.5-.3.9-.8.9-1.4v-1C8 9.7 7.3 9 6.5 9zm0 2.5h-2v-1h2v1zM11.5 9c-.8 0-1.5.7-1.5 1.5V15h1.5v-1.5h1V15H14v-4.5c0-.8-.7-1.5-1.5-1.5h-1zm1 3h-1v-1.5h1V12zM19.5 9l-.75 3.25L18 9h-1.5l-.75 3.25L15 9h-1.5l1.5 6h1.5l.75-3 .75 3H19.5L21 9z"
    )
    val OpenInFull get() = retouchSymbol(
        "OpenInFull",
        "M21 11V3h-8l3.29 3.29-10 10L3 13v8h8l-3.29-3.29 10-10z"
    )
    val OpenInNew get() = retouchSymbol(
        "OpenInNew",
        "M19 19H5V5h7V3H5c-1.11 0-2 .9-2 2v14c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2v-7h-2v7zM14 3v2h3.59l-9.83 9.83 1.41 1.41L19 6.41V10h2V3h-7z"
    )
}

