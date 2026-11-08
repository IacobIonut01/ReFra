package com.dot.gallery.editor.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoColourTone
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.icons.ColorLens
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.InvertColors
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_black_white
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_blue_tone
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_hue
import com.dot.gallery.editor.ui.generated.resources.editor_retouch_skin_tone
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_COLOUR_TONE = "editor.category.colourTone"

private inline fun PhotoEdits.tone(update: (PhotoColourTone) -> PhotoColourTone): PhotoEdits =
    copy(retouch = retouch.copy(tone = update(retouch.tone)))

private val ColourToneParameters = listOf(
    FinishingParam(
        key = "toneSkin",
        labelRes = Res.string.editor_retouch_skin_tone,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        trackGradient = listOf(Color(0xFFF0C7A8), Color(0xFFE6E2D6), Color(0xFFA86A48)),
        read = { it.retouch.tone.skin * 100f },
        write = { e, v -> e.tone { it.copy(skin = v / 100f) } }
    ),
    FinishingParam(
        key = "toneBlue",
        labelRes = Res.string.editor_retouch_blue_tone,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        trackGradient = listOf(Color(0xFF4DD0E1), Color(0xFFE6E2D6), Color(0xFF5C6BC0)),
        read = { it.retouch.tone.blue * 100f },
        write = { e, v -> e.tone { it.copy(blue = v / 100f) } }
    ),
    FinishingParam(
        key = "toneHue",
        labelRes = Res.string.editor_retouch_hue,
        displayRange = -180f..180f,
        step = 1f,
        neutral = 0f,
        unitSuffix = "°",
        trackGradient = listOf(
            Color(0xFF4DE8FF), Color(0xFF4DFF88), Color(0xFFFFDB4D), Color(0xFFFF4D4D),
            Color(0xFFF34DFF), Color(0xFF4D58FF), Color(0xFF4DE8FF)
        ),
        read = { it.retouch.tone.hue * 180f },
        write = { e, v -> e.tone { it.copy(hue = v / 180f) } }
    ),
    FinishingParam(
        key = "toneBlackWhite",
        labelRes = Res.string.editor_retouch_black_white,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        read = { it.retouch.tone.blackWhite * 100f },
        write = { e, v -> e.tone { it.copy(blackWhite = v / 100f) } }
    )
)

private val ColourToneIcons
    get() = listOf(
        EditorSymbols.InvertColors,
        RetouchSymbols.Waves,
        EditorSymbols.ColorLens,
        RetouchSymbols.Contrast
    )

/**
 * Colour tone: the old colour tools the Colour board does not cover. Chips pick
 * the parameter on narrow layouts and the shared ruler edits it.
 */
@Composable
internal fun ColourToneToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val labels = ColourToneParameters.map { stringResource(it.labelRes) }
    val sourceRef = state.info?.reference
    var activeIndex by rememberSaveable(sourceRef) { mutableIntStateOf(0) }
    val index = activeIndex.coerceIn(ColourToneParameters.indices)
    val enabled = state.document != null && !state.opening
    val latestActions by rememberUpdatedState(actions)
    val active = ColourToneParameters[index]
    val edits = state.document?.toEdits() ?: PhotoEdits()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(RetouchCardPadding)
            .testTag("editor.colourTone")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColourToneParameters.forEachIndexed { i, param ->
                ColourParamChip(
                    label = labels[i],
                    icon = ColourToneIcons[i],
                    value = param.displayOf(edits),
                    modified = param.read(edits) != param.neutral,
                    selected = i == index,
                    enabled = enabled,
                    tag = "editor.colourTone.tab.${param.key}",
                    onClick = {
                        if (i != activeIndex) {
                            latestActions.cancelEdit()
                            activeIndex = i
                        }
                    }
                )
            }
        }
        FinishingParamControls(
            state = state,
            actions = actions,
            param = active,
            label = labels[index],
            enabled = enabled,
            identity = sourceRef to active.key,
            showMetaRow = false,
            limitLabels = true,
            insetContent = false,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}
