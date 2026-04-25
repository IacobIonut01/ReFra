package com.dot.gallery.feature_node.presentation.edit.components.markup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.dot.gallery.R
import com.dot.gallery.feature_node.presentation.util.horizontalFadingEdge

private val textPresetColors = listOf(
    Color(0xFF1A1A1A),
    Color.Red,
    Color(0xFFFF6D00),
    Color.Yellow,
    Color(0xFF00C853),
    Color(0xFF00BFA5),
    Color(0xFF2962FF),
    Color(0xFF6200EA),
    Color.Magenta,
    Color(0xFFFF80AB),
    Color(0xFF8D6E63),
    Color(0xFF78909C),
    Color.White
)

@Composable
fun TextMarkupOverlay(
    modifier: Modifier = Modifier,
    onDone: (text: String, color: Color) -> Unit,
    onRemove: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(Color.White) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .imePadding(),
    ) {
        // Text input area — fills available space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isEmpty()) {
                Text(
                    text = stringResource(R.string.editor_type_something),
                    style = TextStyle(
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 32.sp
                    )
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                textStyle = TextStyle(
                    color = selectedColor,
                    fontSize = 32.sp
                ),
                cursorBrush = SolidColor(selectedColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        // Color dots row + Remove/Done
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Color dots
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .horizontalFadingEdge(0.06f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                textPresetColors.forEach { color ->
                    val isSelected = selectedColor == color
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .then(
                                if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                                else Modifier
                            )
                            .padding(3.dp)
                            .background(color = color, shape = CircleShape)
                            .clip(CircleShape)
                            .clickable { selectedColor = color }
                    )
                }
            }

            // Remove / Done buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onRemove) {
                    Text(
                        text = stringResource(R.string.editor_remove),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) {
                            onDone(text, selectedColor)
                        } else {
                            onRemove()
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.editor_done),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
