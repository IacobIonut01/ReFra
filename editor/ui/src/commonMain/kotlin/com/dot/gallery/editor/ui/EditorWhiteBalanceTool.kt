package com.dot.gallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.core.model.PhotoEdits
import com.dot.gallery.editor.core.model.PhotoWhiteBalance
import com.dot.gallery.editor.core.model.toEdits
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_temperature
import com.dot.gallery.editor.ui.generated.resources.editor_tint
import com.dot.gallery.editor.ui.generated.resources.editor_wb_preset_auto
import com.dot.gallery.editor.ui.generated.resources.editor_wb_preset_cloudy
import com.dot.gallery.editor.ui.generated.resources.editor_wb_preset_daylight
import com.dot.gallery.editor.ui.generated.resources.editor_wb_preset_fluorescent
import com.dot.gallery.editor.ui.generated.resources.editor_wb_pick_neutral
import com.dot.gallery.editor.ui.generated.resources.editor_wb_preset_tungsten
import com.dot.gallery.editor.ui.icons.AutoAwesome
import com.dot.gallery.editor.ui.icons.Cloud
import com.dot.gallery.editor.ui.icons.Colorize
import com.dot.gallery.editor.ui.icons.EditorSymbols
import com.dot.gallery.editor.ui.icons.WbIncandescent
import com.dot.gallery.editor.ui.icons.WbIridescent
import com.dot.gallery.editor.ui.icons.WbSunny
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

internal const val TAG_CATEGORY_WHITE_BALANCE = "editor.category.whiteBalance"
internal const val TAG_WB_PICK = "editor.wb.pick"

private class WhiteBalancePreset(
    val key: String,
    val labelRes: StringResource,
    val temperatureKelvin: Float,
    val tint: Float,
    val icon: ImageVector
)

/**
 * The named presets — Auto is the neutral 6500 K / 0 recipe (a label,
 * not an image-analysis claim); the rest are the classic white-balance
 * anchor points.
 */
private val WhiteBalancePresets = listOf(
    WhiteBalancePreset(
        "auto", Res.string.editor_wb_preset_auto, 6500f, 0f,
        EditorSymbols.AutoAwesome
    ),
    WhiteBalancePreset(
        "daylight", Res.string.editor_wb_preset_daylight, 5500f, 0f,
        EditorSymbols.WbSunny
    ),
    WhiteBalancePreset(
        "cloudy", Res.string.editor_wb_preset_cloudy, 6500f, 4f,
        EditorSymbols.Cloud
    ),
    WhiteBalancePreset(
        "tungsten", Res.string.editor_wb_preset_tungsten, 3200f, 0f,
        EditorSymbols.WbIncandescent
    ),
    WhiteBalancePreset(
        "fluorescent", Res.string.editor_wb_preset_fluorescent,
        4200f, 12f,
        EditorSymbols.WbIridescent
    )
)

internal val WhiteBalanceParameters = listOf(
    FinishingParam(
        key = "temperature",
        labelRes = Res.string.editor_temperature,
        displayRange = 2000f..12000f,
        step = 50f,
        neutral = 6500f,
        signed = false,
        unitSuffix = " K",
        trackGradient = listOf(Color(0xFF5B9BFF), Color(0xFFE6E2D6), Color(0xFFFFB74D)),
        read = { it.finishing.whiteBalance.temperatureKelvin },
        write = { e, v ->
            e.copy(
                finishing = e.finishing.copy(
                    whiteBalance = e.finishing.whiteBalance.copy(
                        temperatureKelvin = v
                    )
                )
            )
        }
    ),
    FinishingParam(
        key = "tint",
        labelRes = Res.string.editor_tint,
        displayRange = -100f..100f,
        step = 1f,
        neutral = 0f,
        trackGradient = listOf(Color(0xFF66BB6A), Color(0xFFE6E2D6), Color(0xFFD16BD1)),
        read = { it.finishing.whiteBalance.tint },
        write = { e, v ->
            e.copy(
                finishing = e.finishing.copy(
                    whiteBalance = e.finishing.whiteBalance.copy(tint = v)
                )
            )
        }
    )
)

/**
 * The preset tile strip (selected when both fields match the preset's
 * recipe), shared by the White balance tool and the Colour tool's mode. It
 * opens with the Pick neutral tile (V3 "Pick a neutral point"): it toggles
 * [PhotoToolUiState.whiteBalancePicking], which hands canvas input to
 * [WhiteBalancePickOverlay]. It leads the strip so it stays reachable in
 * the collapsed sheet.
 */
@Composable
internal fun WhiteBalancePresetStrip(
    state: PhotoEditorState,
    actions: PhotoEditorActions
) {
    val presetLabels = WhiteBalancePresets.map { stringResource(it.labelRes) }
    val edits = state.document?.toEdits() ?: PhotoEdits()
    val whiteBalance = edits.finishing.whiteBalance
    val enabled = state.document != null && !state.opening
    val latestState by rememberUpdatedState(state)
    val latestActions by rememberUpdatedState(actions)
    val pickLabel = stringResource(Res.string.editor_wb_pick_neutral)
    val toolUiState = LocalPhotoToolUiState.current
        ?: remember { PhotoToolUiState() }
    // Pick mode belongs to this tool's session; leaving the tool ends it.
    DisposableEffect(toolUiState) {
        onDispose { toolUiState.whiteBalancePicking = false }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WhiteBalanceTile(
            label = pickLabel,
            icon = EditorSymbols.Colorize,
            selected = toolUiState.whiteBalancePicking,
            selectedColor = MaterialTheme.colorScheme.tertiary,
            selectedContent = MaterialTheme.colorScheme.onTertiary,
            restingColor = MaterialTheme.colorScheme.tertiaryContainer,
            restingContent = MaterialTheme.colorScheme.onTertiaryContainer,
            enabled = enabled && !state.interactionActive,
            tag = TAG_WB_PICK,
            onClick = {
                toolUiState.whiteBalancePicking =
                    !toolUiState.whiteBalancePicking
            }
        )
        WhiteBalancePresets.forEachIndexed { i, preset ->
            WhiteBalanceTile(
                label = presetLabels[i],
                icon = preset.icon,
                selected = whiteBalance.temperatureKelvin ==
                    preset.temperatureKelvin &&
                    whiteBalance.tint == preset.tint,
                selectedColor = MaterialTheme.colorScheme.primaryContainer,
                selectedContent = MaterialTheme.colorScheme.onPrimaryContainer,
                restingColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                restingContent = MaterialTheme.colorScheme.onSurface,
                enabled = enabled && !state.interactionActive,
                tag = "editor.wb.preset.${preset.key}",
                onClick = {
                    val current = latestState.document?.toEdits()
                        ?: return@WhiteBalanceTile
                    latestActions.applyEdits(
                        presetLabels[i],
                        current.copy(
                            finishing = current.finishing.copy(
                                whiteBalance = PhotoWhiteBalance(
                                    temperatureKelvin =
                                        preset.temperatureKelvin,
                                    tint = preset.tint
                                )
                            )
                        )
                    )
                }
            )
        }
    }
}

/**
 * A preset tile, the middle level of the Colour hierarchy: an icon over its
 * label in a squircle that springs to fully round when selected.
 */
@Composable
private fun WhiteBalanceTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    selectedColor: Color,
    selectedContent: Color,
    restingColor: Color,
    restingContent: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        if (selected) 34.dp else 16.dp,
        spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
        label = "wbTileCorner"
    )
    val background by animateColorAsState(
        if (selected) selectedColor else restingColor,
        label = "wbTileBackground"
    )
    val content by animateColorAsState(
        if (selected) selectedContent else restingContent,
        label = "wbTileContent"
    )
    Column(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(corner))
            .background(background)
            .clickable(enabled = enabled, role = Role.Tab, onClickLabel = label) {
                onClick()
            }
            .semantics { this.selected = selected }
            .testTag(tag)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * The White balance tool per the approved `p-white-balance-inline`
 * frame: the preset strip above the Temperature and Tint precision rulers
 * with their exact-entry value rows.
 */
@Composable
internal fun WhiteBalanceToolContent(
    state: PhotoEditorState,
    actions: PhotoEditorActions,
    modifier: Modifier = Modifier
) {
    val paramLabels = WhiteBalanceParameters.map { stringResource(it.labelRes) }
    val enabled = state.document != null && !state.opening

    Column(modifier = modifier.fillMaxWidth()) {
        WhiteBalancePresetStrip(state, actions)
        WhiteBalanceParameters.forEachIndexed { i, param ->
            FinishingParamControls(
                state = state,
                actions = actions,
                param = param,
                label = paramLabels[i],
                enabled = enabled,
                identity = state.info?.reference to param.key,
                // Two live parameters share one More menu — visible reset
                // icons serve both instead.
                publishResetToMenu = false
            )
        }
    }
}

/** The tools that host the preset strip, and so the neutral-point picker. */
internal fun photoToolOwnsWhiteBalancePick(tool: EditorToolId?): Boolean =
    tool == EditorToolIds.PhotoWhiteBalance || tool == EditorToolIds.PhotoColour
