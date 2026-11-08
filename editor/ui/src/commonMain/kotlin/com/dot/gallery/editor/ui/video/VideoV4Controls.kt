package com.dot.gallery.editor.ui.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The inspector vocabulary every video tool surface shares, drawn in the
 * studio language: connected segment groups, tonal rows, switch rows,
 * tonal actions, the primary pill and small captions. Each control keeps
 * a 48 dp touch floor and grows with the font scale instead of clipping.
 */

/** One option of a [VideoPillSegments] group. */
internal class VideoSegment(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val tag: String? = null,
    val enabled: Boolean = true
)

/**
 * The connected segment group — 52 dp members with square-ish 8 dp joins
 * and round 26 dp ends; the chosen member springs to a full primary pill
 * and takes a little more width. Options are tabs: selection is state.
 */
@Composable
internal fun VideoPillSegments(
    options: List<VideoSegment>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VideoStudio.GroupGap)
    ) {
        options.forEachIndexed { index, option ->
            val start by animateDpAsState(
                if (option.selected || index == 0) VideoStudio.SegmentEnd else 8.dp,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                label = "segmentStart"
            )
            val end by animateDpAsState(
                if (option.selected || index == options.lastIndex) VideoStudio.SegmentEnd else 8.dp,
                spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
                label = "segmentEnd"
            )
            val weight by animateFloatAsState(
                if (option.selected) 1.35f else 1f,
                spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow),
                label = "segmentWeight"
            )
            val container by animateColorAsState(
                if (option.selected) MaterialTheme.colorScheme.primary
                else videoSlabColor(),
                label = "segmentContainer"
            )
            val ink = when {
                !option.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                option.selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Box(
                modifier = Modifier
                    .weight(weight)
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(start, end, end, start))
                    .background(container)
                    .clickable(
                        enabled = option.enabled,
                        role = Role.Tab,
                        onClickLabel = option.label,
                        onClick = option.onClick
                    )
                    .semantics { selected = option.selected }
                    .then(if (option.tag != null) Modifier.testTag(option.tag) else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = ink,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * A tonal inspector row: 16 dp corners on the high container, label
 * start, value end; a chosen row springs to a 24 dp pill in the primary
 * container. With [onClick] the whole row is the target; [trailing]
 * replaces the value text.
 */
@Composable
internal fun VideoV4Row(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    selected: Boolean? = null,
    icon: ImageVector? = null,
    shape: RoundedCornerShape? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val chosen = selected == true
    val corner by animateDpAsState(
        if (chosen) 24.dp else 16.dp,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "rowCorner"
    )
    val container by animateColorAsState(
        if (chosen) MaterialTheme.colorScheme.primaryContainer
        else videoSlabColor(),
        label = "rowContainer"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape ?: RoundedCornerShape(corner))
            .background(container)
            .then(if (selected != null) Modifier.semantics { this.selected = chosen } else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClickLabel = label,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val labelColor = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            chosen -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon, null, tint = labelColor,
                modifier = Modifier.padding(end = 0.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = if (value != null) FontWeight.Normal else FontWeight.Medium,
                color = if (value != null && !chosen) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else labelColor
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = 14.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                textAlign = TextAlign.End
            )
        }
    }
}

/** A tonal command — the panel's 52 dp action cell. */
@Composable
internal fun VideoV4Command(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    icon: ImageVector? = null,
    danger: Boolean = false
) {
    VideoTonalAction(
        label = label,
        onClick = onClick,
        modifier = modifier,
        icon = icon,
        enabled = enabled,
        on = selected,
        danger = danger
    )
}
