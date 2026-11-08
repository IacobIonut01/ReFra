package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.action_cancel
import com.dot.gallery.core.ui.generated.resources.yes
import com.dot.gallery.core.ui.util.AppBottomSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmation in the redesigned sheet style: an optional icon badge in [tone], a centred title
 * and body, an optional [details] tile showing what is affected, then Cancel and the action.
 * A [SheetTone.Error] tone makes the action button the error style.
 */
@Composable
fun ConfirmationSheet(
    state: AppBottomSheetState,
    title: String,
    summary: String,
    confirmText: String = stringResource(Res.string.yes),
    icon: ImageVector? = null,
    tone: SheetTone = SheetTone.Primary,
    confirmIcon: ImageVector? = null,
    details: (@Composable ColumnScope.() -> Unit)? = null,
    onConfirm: () -> Unit
) {
    val scope = rememberCoroutineScope()
    ExpressiveSheet(sheetState = state, paneTitle = title) {
        ConfirmationSheetContent(
            title = title,
            summary = summary,
            confirmText = confirmText,
            dismissText = stringResource(Res.string.action_cancel),
            icon = icon,
            tone = tone,
            confirmIcon = confirmIcon,
            details = details,
            onDismiss = { scope.launch { state.hide() } },
            onConfirm = {
                onConfirm()
                scope.launch { state.hide() }
            },
        )
    }
}

/** Stateless body of [ConfirmationSheet]. */
@Composable
fun ColumnScope.ConfirmationSheetContent(
    title: String,
    summary: String,
    confirmText: String,
    dismissText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    icon: ImageVector? = null,
    tone: SheetTone = SheetTone.Primary,
    confirmIcon: ImageVector? = null,
    details: (@Composable ColumnScope.() -> Unit)? = null,
) {
    SheetHeader(title = title, body = summary, icon = icon, tone = tone, centered = true)
    details?.invoke(this)
    SheetDismissConfirmActions(
        dismissText = dismissText,
        onDismiss = onDismiss,
        confirmText = confirmText,
        onConfirm = onConfirm,
        confirmStyle = if (tone == SheetTone.Error) SheetButtonStyle.Error else SheetButtonStyle.Filled,
        confirmIcon = confirmIcon,
    )
}
