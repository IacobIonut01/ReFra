/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.library
import com.dot.gallery.feature.settings.generated.resources.search
import com.dot.gallery.feature.settings.generated.resources.timeline
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.Library
import org.jetbrains.compose.resources.stringResource

const val ClassicNavPreviewTabTag = "ClassicNavPreview.Tab"
const val ClassicNavPreviewSearchTag = "ClassicNavPreview.Search"

/**
 * Preview for the "Classic navigation style" setting on the Appearance page.
 *
 * [isChecked] draws the labelled Material bar — Timeline (active), Search, Library in
 * that order, matching [com.dot.gallery.core.presentation.components.ClassicNavigationBarContent]'s
 * middle Search slot. Off draws the default floating dock: Timeline and Library tabs in
 * one pill plus a separate search button, matching the app's two-tab navigation.
 */
@Composable
fun ClassicNavigationPreview(isChecked: Boolean) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    val timelineLabel = stringResource(Res.string.timeline)
    val searchLabel = stringResource(Res.string.search)
    val libraryLabel = stringResource(Res.string.library)

    if (isChecked) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(container)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ClassicNavBarItem(
                icon = Icons.Outlined.Photo,
                label = timelineLabel,
                active = true,
                tag = ClassicNavPreviewTabTag,
            )
            ClassicNavBarItem(
                icon = Icons.Outlined.Search,
                label = searchLabel,
                active = false,
                tag = ClassicNavPreviewSearchTag,
            )
            ClassicNavBarItem(
                icon = GalleryIcons.Library,
                label = libraryLabel,
                active = false,
                tag = ClassicNavPreviewTabTag,
            )
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .clip(CircleShape)
                    .background(container)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClassicNavDockTab(icon = Icons.Outlined.Photo, active = true)
                ClassicNavDockTab(icon = GalleryIcons.Library, active = false)
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(container)
                    .testTag(ClassicNavPreviewSearchTag),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = searchLabel,
                    tint = inactive,
                )
            }
        }
    }
}

@Composable
private fun ClassicNavBarItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    tag: String,
) {
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier.testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent
                )
                .padding(horizontal = 16.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else inactive,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) MaterialTheme.colorScheme.onSurface else inactive,
        )
    }
}

@Composable
private fun ClassicNavDockTab(icon: ImageVector, active: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 40.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .testTag(ClassicNavPreviewTabTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
