package com.dot.gallery.app.presentation.edit

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.editor_redo
import com.dot.gallery.generated.resources.editor_undo
import org.jetbrains.compose.resources.stringResource

internal const val TAG_EDIT_UNDO = "edit.topBar.undo"
internal const val TAG_EDIT_REDO = "edit.topBar.redo"

/**
 * The top bar's Undo and Redo. Both always stay in place so the bar never
 * shifts; one that has nothing to step through is greyed out and disabled.
 */
@Composable
internal fun EditUndoRedoButtons(
    canUndo: Boolean,
    canRedo: Boolean,
    enabled: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.38f)
    )
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onUndo,
            enabled = enabled && canUndo,
            colors = colors,
            modifier = Modifier.size(40.dp).testTag(TAG_EDIT_UNDO)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Undo,
                contentDescription = stringResource(Res.string.editor_undo),
                modifier = Modifier.size(22.dp)
            )
        }
        IconButton(
            onClick = onRedo,
            enabled = enabled && canRedo,
            colors = colors,
            modifier = Modifier.size(40.dp).testTag(TAG_EDIT_REDO)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Redo,
                contentDescription = stringResource(Res.string.editor_redo),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
