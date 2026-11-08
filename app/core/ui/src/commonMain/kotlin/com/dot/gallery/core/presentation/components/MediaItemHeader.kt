/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dot.gallery.core.LocalMediaSelector
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.core.Constants.Animation.enterAnimation
import com.dot.gallery.core.Constants.Animation.exitAnimation
import com.dot.gallery.core.ui.generated.resources.Res
import com.dot.gallery.core.ui.generated.resources.timeline_open_place
import org.jetbrains.compose.resources.stringResource


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaItemHeader(
    modifier: Modifier = Modifier,
    date: String,
    showAsBig: Boolean = false,
    bigHeaderOnly: Boolean = false,
    isChecked: MutableState<Boolean>,
    /** The day's most photographed city, shown after the date on small headers. */
    place: String? = null,
    /** Opens the place; while selecting, a tap on the place toggles the day like the date does. */
    onPlaceClick: (() -> Unit)? = null,
    onChecked: (() -> Unit)? = null
) {
    val isCheckVisible by LocalMediaSelector.current.isSelectionActive.collectAsStateWithLifecycle()
    val smallModifier = remember(modifier) {
        modifier
            .padding(
                horizontal = 16.dp,
                vertical = 20.dp
            )
            .fillMaxWidth()
    }
    val bigModifier = remember(modifier, bigHeaderOnly) {
        modifier
            .padding(horizontal = 16.dp)
            .padding(top = 80.dp, bottom = if (bigHeaderOnly) 16.dp else 0.dp)
    }
    val bigTextStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.Bold
    )
    val smallTextStyle = MaterialTheme.typography.labelLarge
    val headerModifier by rememberedDerivedState(showAsBig) {
        if (showAsBig) bigModifier else smallModifier
    }
    val headerTextStyle by rememberedDerivedState(showAsBig) {
        if (showAsBig) bigTextStyle else smallTextStyle
    }
    Row(
        modifier = headerModifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = date,
                style = headerTextStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.then(
                    if (!showAsBig) Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onLongClick = {
                            onChecked?.invoke()
                        },
                        onClick = {
                            if (isCheckVisible) onChecked?.invoke()
                        }
                    ) else Modifier
                )
            )
            if (!showAsBig && !place.isNullOrBlank()) {
                val openPlace = stringResource(Res.string.timeline_open_place, place)
                Text(
                    text = place,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            enabled = onPlaceClick != null || onChecked != null,
                            onClickLabel = openPlace,
                            onLongClick = { onChecked?.invoke() },
                            onClick = {
                                if (isCheckVisible) onChecked?.invoke() else onPlaceClick?.invoke()
                            },
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = isCheckVisible && !showAsBig && onChecked != null,
            enter = enterAnimation,
            exit = exitAnimation
        ) {
            CheckBox(
                isChecked = isChecked.value,
                onCheck = onChecked,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}