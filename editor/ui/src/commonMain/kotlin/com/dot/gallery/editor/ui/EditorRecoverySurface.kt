package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_recovery_body
import com.dot.gallery.editor.ui.generated.resources.editor_recovery_discard
import com.dot.gallery.editor.ui.generated.resources.editor_recovery_relink
import com.dot.gallery.editor.ui.generated.resources.editor_recovery_retry
import com.dot.gallery.editor.ui.generated.resources.editor_recovery_title
import org.jetbrains.compose.resources.stringResource

internal const val TAG_RECOVERY_SURFACE = "editor.recovery"
internal const val TAG_RECOVERY_RETRY = "editor.recovery.retry"
internal const val TAG_RECOVERY_DISCARD = "editor.recovery.discard"
internal const val TAG_RECOVERY_RELINK_PREFIX = "editor.recovery.relink."

/**
 * The blocked-restore state screen — the x-importing/x-empty template:
 * centred on the page colour with a title, a body explaining what the
 * saved project needs, one row per missing reference carrying its Relink
 * action, then the Try again / Discard saved edits choices. Every control
 * is real: retry re-probes every reference, discard abandons the pending
 * recipe, and relink hands the reference to the host's media picker —
 * nothing is silently flattened or dropped.
 *
 * Rendered as a full-size overlay so a recovery blocks the workspace
 * until the user decides — the same way the importing state owns its
 * surface while it runs.
 */
@Composable
internal fun EditorRecoverySurface(
    missingReferences: List<String>,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onRelink: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxSize()
            .testTag(TAG_RECOVERY_SURFACE)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = stringResource(Res.string.editor_recovery_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = stringResource(Res.string.editor_recovery_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            // One row per missing reference — the display tail (file
            // name or URI leaf) plus its own Relink affordance.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                missingReferences.forEach { reference ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = reference.substringAfterLast('/'),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onRelink(reference) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(TAG_RECOVERY_RELINK_PREFIX + reference)
                        ) {
                            Text(
                                stringResource(
                                    Res.string.editor_recovery_relink
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .height(48.dp)
                    .testTag(TAG_RECOVERY_RETRY)
            ) {
                Text(
                    stringResource(Res.string.editor_recovery_retry),
                    maxLines = 1
                )
            }
            FilledTonalButton(
                onClick = onDiscard,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(48.dp)
                    .testTag(TAG_RECOVERY_DISCARD)
            ) {
                Text(
                    stringResource(Res.string.editor_recovery_discard),
                    maxLines = 1
                )
            }
        }
    }
}
