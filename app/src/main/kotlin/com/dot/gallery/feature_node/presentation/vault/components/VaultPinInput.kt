package com.dot.gallery.feature_node.presentation.vault.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.rememberFeedbackManager

/**
 * A numeric PIN input with dot indicators and a dial pad.
 * Dots appear only for entered digits (PIN length is not revealed).
 * The user must tap the confirm button to submit.
 */
@Composable
fun VaultPinInput(
    pin: String,
    maxLength: Int = 16,
    isError: Boolean = false,
    onPinChange: (String) -> Unit,
    onPinComplete: (String) -> Unit
) {
    val feedbackManager = rememberFeedbackManager()
    val errorColor = MaterialTheme.colorScheme.error
    val activeColor = MaterialTheme.colorScheme.primary
    val dotColor = if (isError) errorColor else activeColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Dot indicators — only show dots for entered digits
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(20.dp)
        ) {
            for (i in 0 until pin.length) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Keypad
        val keys = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "✓")
        )
        for (row in keys) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (key in row) {
                    when (key) {
                        "✓" -> {
                            ConfirmButton(
                                visible = pin.isNotEmpty(),
                                onClick = {
                                    feedbackManager.vibrateStrong()
                                    onPinComplete(pin)
                                }
                            )
                        }
                        "⌫" -> {
                            IconButton(
                                onClick = {
                                    if (pin.isNotEmpty()) {
                                        feedbackManager.vibrate()
                                        onPinChange(pin.dropLast(1))
                                    }
                                },
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                                    contentDescription = stringResource(R.string.backspace_cd),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        else -> {
                            FilledTonalIconButton(
                                onClick = {
                                    if (pin.length < maxLength) {
                                        val newPin = pin + key
                                        onPinChange(newPin)
                                        feedbackManager.vibrate()
                                    }
                                },
                                modifier = Modifier.size(80.dp)
                            ) {
                                Text(
                                    text = key,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    if (key != row.last()) {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConfirmButton(
    visible: Boolean,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.size(80.dp)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            FilledTonalIconButton(
                onClick = onClick,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.action_confirm),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
