/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.util.rememberFeedbackManager
import com.dot.gallery.feature.vault.domain.model.VaultAuthType
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.toggle_password_visibility_cd
import com.dot.gallery.feature.vault.generated.resources.vault_continue
import com.dot.gallery.feature.vault.generated.resources.vault_enter_password
import com.dot.gallery.feature.vault.generated.resources.vault_pattern_progress
import com.dot.gallery.feature.vault.generated.resources.vault_pin_length
import com.dot.gallery.feature.vault.generated.resources.vault_pin_progress
import com.dot.gallery.feature.vault.generated.resources.vault_redraw

/** Shortest secret accepted for each lock type when creating one. */
fun VaultAuthType.minimumLength(): Int = if (this == VaultAuthType.PIN) 6 else 4

/**
 * Longest PIN the keypad accepts, when creating and when unlocking. Locks set before 6.0.0
 * allowed up to 16 digits, so lowering this would lock those users out.
 */
const val VaultPinMaxLength = 16

/**
 * Helper line under a PIN being created: progress towards the minimum, then the count against
 * the maximum once the PIN is long enough, so a longer PIN never reads as "8 of 6 digits".
 */
@Composable
internal fun vaultPinCreateHelper(length: Int, min: Int): String =
    if (length < min) stringResource(Res.string.vault_pin_progress, length, min)
    else stringResource(Res.string.vault_pin_length, length, VaultPinMaxLength)

/**
 * Filled text field of the vault pages: the high container with 16 dp corners and a 2 dp
 * primary (or error) ring while focused instead of an underline.
 */
@Composable
fun VaultTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    enabled: Boolean = true,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var visible by remember { mutableStateOf(false) }
    val ringColor = when {
        isError -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    val shape = RoundedCornerShape(16.dp)
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(2.dp, ringColor, shape)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        enabled = enabled,
        singleLine = true,
        isError = isError,
        label = { Text(label) },
        shape = shape,
        interactionSource = interaction,
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        trailingIcon = if (isPassword) {
            {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(Res.string.toggle_password_visibility_cd)
                    )
                }
            }
        } else null,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * Entry for one lock secret, laid out for a bounded column: PIN dots with the keypad anchored
 * to the bottom, the pattern pad with Redraw and Continue, or a password field with a submit
 * button. [creating] shows the progress towards the minimum length and waits for the user to
 * confirm a drawn pattern; unlocking submits a pattern as soon as it is lifted.
 *
 * [errorMessage] marks the entry as wrong; the next key press or stroke clears the entry and
 * calls [onEdit] so the caller can drop the message. Change [resetKey] to start over.
 */
@Composable
fun ColumnScope.VaultSecretInput(
    type: VaultAuthType,
    errorMessage: String?,
    creating: Boolean,
    submitLabel: String,
    onSubmit: (String) -> Unit,
    onEdit: () -> Unit = {},
    resetKey: Any? = Unit,
) {
    val feedbackManager = rememberFeedbackManager()
    LaunchedEffect(errorMessage) { if (errorMessage != null) feedbackManager.vibrateStrong() }
    key(type, resetKey) {
        when (type) {
            VaultAuthType.PIN -> {
                var pin by remember { mutableStateOf("") }
                val min = if (creating) type.minimumLength() else 1
                fun edit(next: String) {
                    if (errorMessage != null) {
                        onEdit()
                        pin = next.takeLast(1).takeIf { next.length > pin.length }.orEmpty()
                    } else {
                        pin = next
                    }
                }
                VaultPinDots(
                    count = pin.length,
                    minSlots = if (creating) min else 0,
                    isError = errorMessage != null,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                SecretMessage(
                    error = errorMessage,
                    helper = if (creating) vaultPinCreateHelper(pin.length, min) else null,
                )
                Spacer(Modifier.weight(1f))
                VaultKeypad(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onDigit = { if (pin.length < VaultPinMaxLength) edit(pin + it) },
                    onDelete = { if (pin.isNotEmpty()) edit(pin.dropLast(1)) },
                    onConfirm = { onSubmit(pin) },
                    confirmEnabled = errorMessage == null && pin.length >= min,
                )
            }

            VaultAuthType.PATTERN -> {
                var connected by remember { mutableIntStateOf(0) }
                var drawn by remember { mutableStateOf<String?>(null) }
                var padKey by remember { mutableIntStateOf(0) }
                key(padKey) {
                    VaultPatternLock(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = 320.dp),
                        isError = errorMessage != null,
                        onProgress = { count ->
                            if (count > 0 && errorMessage != null) onEdit()
                            connected = count
                        },
                        onPatternComplete = { pattern ->
                            if (creating) drawn = pattern else onSubmit(pattern)
                        },
                    )
                }
                SecretMessage(
                    error = errorMessage,
                    helper = if (creating && connected > 0) {
                        pluralStringResource(Res.plurals.vault_pattern_progress, connected, connected)
                    } else null,
                )
                Spacer(Modifier.weight(1f))
                if (creating) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VaultButton(
                            text = stringResource(Res.string.vault_redraw),
                            style = VaultButtonStyle.Tonal,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                drawn = null
                                connected = 0
                                padKey++
                                onEdit()
                            },
                        )
                        VaultButton(
                            text = submitLabel.ifEmpty { stringResource(Res.string.vault_continue) },
                            modifier = Modifier.weight(1f),
                            enabled = drawn != null && errorMessage == null,
                            onClick = { drawn?.let(onSubmit) },
                        )
                    }
                }
            }

            VaultAuthType.PASSWORD -> {
                var password by remember { mutableStateOf("") }
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                val canSubmit = password.length >= (if (creating) type.minimumLength() else 1)
                VaultTextField(
                    value = password,
                    onValueChange = {
                        if (errorMessage != null) onEdit()
                        password = it
                    },
                    label = stringResource(Res.string.vault_enter_password),
                    isError = errorMessage != null,
                    isPassword = true,
                    focusRequester = focusRequester,
                    onImeAction = { if (canSubmit) onSubmit(password) },
                )
                SecretMessage(error = errorMessage, helper = null)
                Spacer(Modifier.weight(1f))
                VaultButton(
                    text = submitLabel,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canSubmit,
                    onClick = { onSubmit(password) },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SecretMessage(error: String?, helper: String?) {
    val text = error ?: helper ?: return
    Text(
        text = text,
        style = if (error != null) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
        color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(horizontal = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}
