package com.dot.gallery.editor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_cancel_export
import com.dot.gallery.editor.ui.generated.resources.editor_export_cancelling
import com.dot.gallery.editor.ui.generated.resources.editor_exporting
import com.dot.gallery.editor.ui.generated.resources.editor_save_progress
import com.dot.gallery.editor.ui.generated.resources.editor_save_progress_detail
import com.dot.gallery.editor.ui.icons.Download
import com.dot.gallery.editor.ui.icons.EditorSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

internal const val TAG_SAVE_PROGRESS = "editor.save.progress"
internal const val TAG_SAVE_PROGRESS_PERCENT = "editor.save.progress.percent"
internal const val TAG_SAVE_PROGRESS_THUMBNAIL = "editor.save.progress.thumbnail"

/** Whole percent, rounded down so the bar never reads 100 % before the file is published. */
internal fun saveProgressPercent(progress: Float?): Int =
    floor(((progress ?: 0f).coerceIn(0f, 1f)) * 100f).toInt()

/** The tool-card black — the same floating material as every editor card. */
private val SaveCardColor = Color(0xFF141416)

/**
 * The modal shown while the session exports a copy.
 *
 * The editor's own material, not the platform dialog card: a near-black
 * 28 dp surface with the copy preview, a centred title and detail, the
 * real export progress on a flat accent bar, and a single secondary
 * Cancel action. The bar and the percentage are the export strip's real
 * progress; Cancel cancels the export job and is disabled while it winds
 * down. The thumbnail is the preview only when it describes the recipe
 * being saved. Back and Escape go to [onBack] (the editor's close unwind,
 * which asks before cancelling); a tap outside does nothing.
 */
@Composable
internal fun PhotoSaveProgressDialog(
    state: PhotoEditorState,
    onCancel: () -> Unit,
    onBack: () -> Unit
) {
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val title = stringResource(
            if (state.exportCancelling) Res.string.editor_export_cancelling
            else Res.string.editor_exporting
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SaveCardColor,
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onBack()
                        true
                    } else {
                        false
                    }
                }
                .semantics { paneTitle = title }
                .testTag(TAG_SAVE_PROGRESS)
        ) {
            // Scrolls when large text outgrows the window.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val pixels = currentPreviewPixels(state)
                val thumbnail by produceState<ImageBitmap?>(null, pixels) {
                    value = pixels?.let {
                        withContext(Dispatchers.Default) { it.toEditorImageBitmap() }
                    }
                }
                thumbnail?.let { bitmap ->
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .testTag(TAG_SAVE_PROGRESS_THUMBNAIL)
                    )
                }
                // The reference centres the spinner, title and detail.
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = EditorSymbols.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = stringResource(Res.string.editor_save_progress_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                val progress = (state.exportProgress ?: 0f).coerceIn(0f, 1f)
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(240),
                    label = "saveProgress"
                )
                val percent = saveProgressPercent(state.exportProgress)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.editor_save_progress),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(TAG_SAVE_PROGRESS_PERCENT)
                    )
                }
                // A flat 4 dp accent bar — no platform gap or stop dot.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .semantics {
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                // Keyboard focus starts on Cancel, inside the modal, so
                // Escape reaches it and nothing behind it takes keys.
                val cancelFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { cancelFocus.requestFocus() }
                val cancelLabel = stringResource(Res.string.editor_cancel_export)
                // A plain label like the reference, with a pressed fade
                // instead of a state layer: the modal opens with Cancel
                // focused, and a focus layer would paint a pill at rest.
                val cancelInteraction = remember { MutableInteractionSource() }
                val cancelPressed by cancelInteraction.collectIsPressedAsState()
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .height(48.dp)
                        .clip(RoundedCornerShape(20.dp))
                        // The requester must precede the focusable modifier
                        // the click creates.
                        .focusRequester(cancelFocus)
                        .clickable(
                            interactionSource = cancelInteraction,
                            indication = null,
                            enabled = !state.exportCancelling,
                            onClickLabel = cancelLabel,
                            onClick = onCancel
                        )
                        .semantics { contentDescription = cancelLabel }
                        .padding(horizontal = 16.dp)
                        .testTag(TAG_EXPORT_CANCEL),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = cancelLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            state.exportCancelling -> MaterialTheme.colorScheme
                                .onSurface.copy(alpha = 0.38f)
                            cancelPressed -> MaterialTheme.colorScheme
                                .onSurface.copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}
