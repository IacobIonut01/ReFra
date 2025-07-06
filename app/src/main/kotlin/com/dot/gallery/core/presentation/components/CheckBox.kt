/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState

@Composable
fun CheckBox(
    modifier: Modifier = Modifier,
    isChecked: Boolean,
    number: Int? = null,
    onCheck: (() -> Unit)? = null
) {
    val image = if (isChecked) Icons.Filled.CheckCircle else Icons.Outlined.Circle
    val color = if (isChecked) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface
    if (onCheck != null) {
        IconButton(
            onClick = onCheck,
            modifier = modifier
        ) {
            Image(
                imageVector = image,
                colorFilter = ColorFilter.tint(color),
                contentDescription = null
            )
        }
    } else {
        if (number != null) {
            val sizeModifier by rememberedDerivedState(number) {
                if (number > 99) {
                    Modifier.padding(horizontal = 4.dp)
                } else Modifier
            }
            Text(
                text = "$number",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .defaultMinSize(24.dp, 24.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(100)
                    )
                    .then(sizeModifier)
            )
        } else {
            Image(
                imageVector = image,
                colorFilter = ColorFilter.tint(color),
                modifier = modifier,
                contentDescription = null
            )
        }
    }
}