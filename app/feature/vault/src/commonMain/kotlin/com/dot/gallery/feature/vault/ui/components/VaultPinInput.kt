/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.vault.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.util.rememberFeedbackManager
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.vault.generated.resources.Res
import com.dot.gallery.feature.vault.generated.resources.action_confirm
import com.dot.gallery.feature.vault.generated.resources.backspace_cd

/**
 * Entered-digit dots. With [minSlots] the empty slots up to that length are drawn as rings, so
 * a new PIN shows how far it is from the minimum; unlock passes 0 so the PIN length stays hidden.
 */
@Composable
fun VaultPinDots(
    count: Int,
    modifier: Modifier = Modifier,
    minSlots: Int = 0,
    isError: Boolean = false,
) {
    val filled = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val ring = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier.heightIn(min = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(maxOf(count, minSlots)) { index ->
            val on = index < count
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (on) filled else Color.Transparent)
                    .then(if (on) Modifier else Modifier.border(2.dp, ring, CircleShape))
            )
        }
    }
}

/**
 * Numeric keypad: 1–9, then delete, 0 and a filled confirm key. Keys are 64 dp pills that share
 * the width, capped so they stay thumb-sized on tablets.
 */
@Composable
fun VaultKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val feedbackManager = rememberFeedbackManager()
    val rows = listOf("123", "456", "789")
    Column(
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    KeypadKey(
                        modifier = Modifier.weight(1f),
                        container = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = {
                            feedbackManager.vibrate()
                            onDigit(digit)
                        },
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val deleteDescription = stringResource(Res.string.backspace_cd)
            KeypadKey(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = deleteDescription },
                container = Color.Transparent,
                onClick = {
                    feedbackManager.vibrate()
                    onDelete()
                },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            KeypadKey(
                modifier = Modifier.weight(1f),
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = {
                    feedbackManager.vibrate()
                    onDigit('0')
                },
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val confirmContainer by animateColorAsState(
                if (confirmEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                label = "vaultKeypadConfirm",
            )
            val confirmDescription = stringResource(Res.string.action_confirm)
            KeypadKey(
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = confirmDescription },
                container = confirmContainer,
                enabled = confirmEnabled,
                onClick = {
                    feedbackManager.vibrateStrong()
                    onConfirm()
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = if (confirmEnabled) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
            }
        }
    }
}

@Composable
private fun KeypadKey(
    container: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * PIN dots over the [VaultKeypad]. Confirm submits the PIN; it is enabled once at least
 * [minLength] digits are in (any digit when 0).
 */
@Composable
fun VaultPinInput(
    pin: String,
    maxLength: Int = VaultPinMaxLength,
    isError: Boolean = false,
    minSlots: Int = 0,
    minLength: Int = 1,
    onPinChange: (String) -> Unit,
    onPinComplete: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        VaultPinDots(count = pin.length, minSlots = minSlots, isError = isError)
        Spacer(modifier = Modifier.height(32.dp))
        VaultKeypad(
            onDigit = { if (pin.length < maxLength) onPinChange(pin + it) },
            onDelete = { if (pin.isNotEmpty()) onPinChange(pin.dropLast(1)) },
            onConfirm = { onPinComplete(pin) },
            confirmEnabled = pin.length >= maxOf(minLength, 1),
        )
    }
}
