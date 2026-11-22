package com.dot.gallery.app.presentation.edit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.action_cancel
import com.dot.gallery.generated.resources.editor_overwrite_confirm_message
import com.dot.gallery.generated.resources.editor_overwrite_confirm_title
import com.dot.gallery.generated.resources.override
import org.jetbrains.compose.resources.stringResource

internal const val TAG_EDIT_OVERWRITE = "edit.topBar.overwrite"
internal const val TAG_EDIT_OVERWRITE_CONFIRM = "edit.overwrite.confirm"

/**
 * Overwrite, shown as an outlined pill beside the filled Save copy so the
 * copy stays the prominent choice. It only asks; [OverwriteConfirmDialog]
 * does the overwrite.
 */
@Composable
internal fun EditOverwriteButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        border = BorderStroke(
            1.dp,
            Color.White.copy(alpha = if (enabled) 0.6f else 0.2f)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.38f)
        ),
        modifier = modifier.testTag(TAG_EDIT_OVERWRITE)
    ) {
        Text(
            text = stringResource(Res.string.override),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Confirms an overwrite. The editor backs the original up before it
 * writes, and the dialog says so: Revert to Original brings it back.
 */
@Composable
internal fun OverwriteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.editor_overwrite_confirm_title)) },
        text = { Text(stringResource(Res.string.editor_overwrite_confirm_message)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag(TAG_EDIT_OVERWRITE_CONFIRM)
            ) {
                Text(stringResource(Res.string.override))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        }
    )
}
