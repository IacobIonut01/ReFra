package com.dot.gallery.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.render.photo.PhotoEditorState
import com.dot.gallery.editor.ui.generated.resources.Res
import com.dot.gallery.editor.ui.generated.resources.editor_cancel
import com.dot.gallery.editor.ui.generated.resources.editor_value_confirm
import com.dot.gallery.editor.ui.generated.resources.editor_value_invalid
import com.dot.gallery.editor.ui.generated.resources.editor_value_title
import org.jetbrains.compose.resources.stringResource

internal const val TAG_VALUE_FIELD = "editor.valueField"
internal const val TAG_VALUE_FIELD_SECOND = "editor.valueField.second"
internal const val TAG_VALUE_CONFIRM = "editor.valueConfirm"
internal const val TAG_VALUE_CANCEL = "editor.valueCancel"

/**
 * Ownership state for a [NumericEntryDialog]: the dialog is a real
 * interaction draft while open — an export or the host's dirty-close
 * receipt must see `interactionActive`, not a clean idle session.
 * Ownership is claimed optimistically; the session's own drafting guard
 * makes a stray cancel a no-op. Shared by every exact-entry surface so the
 * guard logic lives in exactly one place.
 */
internal class NumericEntryOwner {
    var showDialog by mutableStateOf(false)
        private set
    var ownsDraft by mutableStateOf(false)
        private set
    var sourceRef by mutableStateOf<String?>(null)
        private set

    /** Shows the dialog and claims the interaction draft just opened. */
    fun open(source: String?) {
        showDialog = true
        ownsDraft = true
        sourceRef = source
    }

    /** Closes the visuals without touching history (a confirmed commit). */
    fun hide() {
        showDialog = false
        ownsDraft = false
    }

    /** An external path ended the interaction — drop ownership and close. */
    fun onInteractionEnded() {
        ownsDraft = false
        showDialog = false
    }

    /**
     * Dismiss: cancels only a draft this dialog opened, and only while it
     * still belongs to the same source — a stale dismiss must never touch
     * the new document's history.
     */
    fun dismiss(currentSource: String?, cancelEdit: () -> Unit) {
        if (ownsDraft && sourceRef == currentSource) cancelEdit()
        onInteractionEnded()
    }
}

/**
 * The guards around a [NumericEntryOwner]: the dialog closes when its
 * interaction ends elsewhere (tool switch, Apply, disposal guard), when
 * the source changes, and when the owning control leaves composition
 * mid-entry.
 */
@Composable
internal fun NumericEntryOwnerEffects(
    owner: NumericEntryOwner,
    state: PhotoEditorState,
    actions: PhotoEditorActions
) {
    val latestActions by rememberUpdatedState(actions)
    val latestState by rememberUpdatedState(state)
    LaunchedEffect(state.interactionActive) {
        if (!state.interactionActive) owner.onInteractionEnded()
    }
    LaunchedEffect(state.info?.reference) {
        if (owner.showDialog) {
            owner.dismiss(latestState.info?.reference, latestActions.cancelEdit)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            owner.dismiss(latestState.info?.reference, latestActions.cancelEdit)
        }
    }
}

/**
 * Precise numeric entry for one adjustment parameter, opened by tapping a
 * parameter's value readout. The text parses leniently — decimal commas
 * and unicode minus signs normalize — but an out-of-range or unparsable
 * value is a visible range error, never a silent clamp. Confirming (button
 * or IME Done) is exactly one undoable apply; cancelling leaves the
 * parameter untouched.
 */
@Composable
internal fun NumericEntryDialog(
    label: String,
    initialValue: Float,
    range: ClosedFloatingPointRange<Float>,
    onConfirm: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(trimTrailingZeros(initialValue))
    }
    var showError by remember { mutableStateOf(false) }

    fun parsed(): Float? =
        text.trim()
            .replace('−', '-') // U+2212 minus
            .replace(',', '.')
            .toFloatOrNull()
            ?.takeIf { it.isFinite() && it in range }

    fun confirm() {
        val value = parsed()
        if (value == null) {
            showError = true
        } else {
            onConfirm(value)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.editor_value_title, label))
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    if (showError && parsed() != null) showError = false
                },
                isError = showError,
                supportingText = {
                    if (showError) {
                        Text(
                            stringResource(
                                Res.string.editor_value_invalid,
                                trimTrailingZeros(range.start),
                                trimTrailingZeros(range.endInclusive)
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_VALUE_FIELD)
            )
        },
        confirmButton = {
            TextButton(
                onClick = ::confirm,
                modifier = Modifier.testTag(TAG_VALUE_CONFIRM)
            ) {
                Text(stringResource(Res.string.editor_value_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TAG_VALUE_CANCEL)
            ) {
                Text(stringResource(Res.string.editor_cancel))
            }
        }
    )
}

/**
 * The paired variant of [NumericEntryDialog] — two labelled fields
 * validated against their own ranges, confirmed atomically: the confirm
 * fires only when *both* parse in range, so one click lands one local
 * interaction on both values (the Transform `X · Y` and
 * `Scale · Rotation` rows). A field's error shows independently and
 * clears once its own text parses again.
 */
@Composable
internal fun NumericPairEntryDialog(
    label: String,
    firstLabel: String,
    firstInitial: Float,
    firstRange: ClosedFloatingPointRange<Float>,
    secondLabel: String,
    secondInitial: Float,
    secondRange: ClosedFloatingPointRange<Float>,
    onConfirm: (Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var firstText by remember { mutableStateOf(trimTrailingZeros(firstInitial)) }
    var secondText by remember { mutableStateOf(trimTrailingZeros(secondInitial)) }
    var firstError by remember { mutableStateOf(false) }
    var secondError by remember { mutableStateOf(false) }

    fun parse(
        text: String,
        range: ClosedFloatingPointRange<Float>
    ): Float? = text.trim()
        .replace('−', '-') // U+2212 minus
        .replace(',', '.')
        .toFloatOrNull()
        ?.takeIf { it.isFinite() && it in range }

    fun confirm() {
        val first = parse(firstText, firstRange)
        val second = parse(secondText, secondRange)
        if (first == null || second == null) {
            firstError = first == null
            secondError = second == null
        } else {
            onConfirm(first, second)
        }
    }

    @Composable
    fun field(
        text: String,
        fieldLabel: String,
        range: ClosedFloatingPointRange<Float>,
        error: Boolean,
        tag: String,
        imeAction: ImeAction,
        onText: (String) -> Unit
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onText,
            label = { Text(fieldLabel) },
            isError = error,
            supportingText = {
                if (error) {
                    Text(
                        stringResource(
                            Res.string.editor_value_invalid,
                            trimTrailingZeros(range.start),
                            trimTrailingZeros(range.endInclusive)
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = imeAction
            ),
            keyboardActions = KeyboardActions(onDone = { confirm() }),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.editor_value_title, label))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field(
                    text = firstText,
                    fieldLabel = firstLabel,
                    range = firstRange,
                    error = firstError,
                    tag = TAG_VALUE_FIELD,
                    imeAction = ImeAction.Next
                ) {
                    firstText = it
                    if (firstError && parse(it, firstRange) != null) {
                        firstError = false
                    }
                }
                field(
                    text = secondText,
                    fieldLabel = secondLabel,
                    range = secondRange,
                    error = secondError,
                    tag = TAG_VALUE_FIELD_SECOND,
                    imeAction = ImeAction.Done
                ) {
                    secondText = it
                    if (secondError && parse(it, secondRange) != null) {
                        secondError = false
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::confirm,
                modifier = Modifier.testTag(TAG_VALUE_CONFIRM)
            ) {
                Text(stringResource(Res.string.editor_value_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TAG_VALUE_CANCEL)
            ) {
                Text(stringResource(Res.string.editor_cancel))
            }
        }
    )
}

/**
 * `1.0` → "1", `-10.00` → "-10", `2.5` → "2.5" — manual, KMP-safe.
 * Only a plain ".0" suffix is stripped: trailing zeros inside a scientific
 * notation exponent (`1.0E-10`) are significant, so the string is never
 * trimmed character-by-character past 'E'.
 */
internal fun trimTrailingZeros(value: Float): String {
    if (value == value.toLong().toFloat() && value.isFinite() &&
        value in -1e15f..1e15f
    ) {
        return value.toLong().toString()
    }
    val s = value.toString()
    return if (s.endsWith(".0")) s.removeSuffix(".0") else s
}
