/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.action_cancel
import com.dot.gallery.core.ui.generated.resources.cutout_save_choice_title
import com.dot.gallery.core.ui.generated.resources.cutout_save_flatten_black
import com.dot.gallery.core.ui.generated.resources.cutout_save_flatten_white
import com.dot.gallery.core.ui.generated.resources.cutout_save_transparent_png
import org.jetbrains.compose.resources.stringResource

/** The user's choice for how to save a transparent cut-out edit. */
enum class CutoutSaveChoice { TRANSPARENT_PNG, FLATTEN_WHITE, FLATTEN_BLACK }

/**
 * Bottom sheet shown when saving an image that contains a background-removal (cut-out) edit. JPEG
 * and most source formats can't store transparency, so the user picks between a transparent PNG or
 * flattening the subject onto a solid colour (keeping the source format).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CutoutSaveChoiceSheet(
    onChoice: (CutoutSaveChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
    ) {
        ExpressiveSheetColumn {
            CutoutSaveChoiceContent(onChoice = onChoice, onDismiss = onDismiss)
        }
    }
}

/** Stateless body of [CutoutSaveChoiceSheet]: three preview cards, then Cancel. */
@Composable
fun ColumnScope.CutoutSaveChoiceContent(
    onChoice: (CutoutSaveChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetHeader(title = stringResource(Res.string.cutout_save_choice_title))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CutoutChoiceCard(
            label = stringResource(Res.string.cutout_save_transparent_png),
            background = null,
            onClick = { onChoice(CutoutSaveChoice.TRANSPARENT_PNG) },
            modifier = Modifier.weight(1f),
        )
        CutoutChoiceCard(
            label = stringResource(Res.string.cutout_save_flatten_white),
            background = Color.White,
            onClick = { onChoice(CutoutSaveChoice.FLATTEN_WHITE) },
            modifier = Modifier.weight(1f),
        )
        CutoutChoiceCard(
            label = stringResource(Res.string.cutout_save_flatten_black),
            background = Color.Black,
            onClick = { onChoice(CutoutSaveChoice.FLATTEN_BLACK) },
            modifier = Modifier.weight(1f),
        )
    }
    SheetActions {
        SheetButton(
            text = stringResource(Res.string.action_cancel),
            onClick = onDismiss,
            style = SheetButtonStyle.Tonal,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A choice card: a square preview of the background ([background] null = checkerboard) and a label. */
@Composable
private fun CutoutChoiceCard(
    label: String,
    background: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val previewShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(previewShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, previewShape),
            contentAlignment = Alignment.Center,
        ) {
            if (background == null) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell = size.width / 6f
                    drawRect(Color.White)
                    for (row in 0 until 6) for (col in 0 until 6) {
                        if ((row + col) % 2 == 0) {
                            drawRect(Color(0xFFDADADA), Offset(col * cell, row * cell), Size(cell, cell))
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(background))
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}
