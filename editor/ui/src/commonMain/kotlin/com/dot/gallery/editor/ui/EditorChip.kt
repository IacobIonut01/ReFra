package com.dot.gallery.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.editor.ui.icons.Check
import com.dot.gallery.editor.ui.icons.EditorSymbols

/**
 * The editor's selector chip: a tonal pill — no outline — that scrolls
 * sideways in an [EditorChipRow]. The visual pill is 36 dp inside a 48 dp
 * touch target; a selected chip fills `primaryContainer` and leads with a
 * check in place of its icon. A changed, unselected chip carries a 6 dp
 * primary dot at its top end.
 */
@Composable
internal fun EditorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    value: String? = null,
    valueModified: Boolean = false,
    leadingIcon: ImageVector? = null,
    tag: String? = null
) {
    val background by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "editorChipBackground"
    )
    val labelColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val valueColor = when {
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        valueModified -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val showLeading = selected || leadingIcon != null
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Tab,
                onClickLabel = label
            ) { onClick() }
            .semantics { this.selected = selected }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(CircleShape)
                .background(background)
                .alpha(if (enabled) 1f else 0.38f)
                .padding(
                    start = if (showLeading) 12.dp else 16.dp,
                    end = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selected) {
                Icon(
                    imageVector = EditorSymbols.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(18.dp)
                        .then(
                            if (tag != null) Modifier.testTag("$tag.check")
                            else Modifier
                        )
                )
            } else if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                maxLines = 1,
                softWrap = false
            )
            if (!value.isNullOrEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    color = valueColor,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        if (valueModified && !selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 4.dp)
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }
    }
}

/**
 * One sideways-scrolling row of [EditorChip]s — 8 dp between chips, never
 * wraps; [contentPadding] insets the strip's ends inside the scroll.
 */
@Composable
internal fun EditorChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
