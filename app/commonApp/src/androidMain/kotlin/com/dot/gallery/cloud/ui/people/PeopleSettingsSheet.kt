/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_people_unknown
import com.dot.gallery.generated.resources.people_settings_people_count
import com.dot.gallery.generated.resources.people_settings
import com.dot.gallery.generated.resources.people_settings_hidden
import com.dot.gallery.generated.resources.people_settings_hidden_desc
import com.dot.gallery.generated.resources.people_settings_no_hidden
import com.dot.gallery.generated.resources.people_settings_source_local
import com.dot.gallery.generated.resources.people_settings_sources
import com.dot.gallery.generated.resources.people_settings_sources_desc
import com.dot.gallery.generated.resources.person_hidden_banner_action
import com.github.panpf.sketch.AsyncImage
import com.github.panpf.sketch.request.ComposableImageRequest
import org.jetbrains.compose.resources.stringResource

/**
 * People settings: which sources (on-device people, each connected account) appear on the page,
 * and every hidden on-device person with a way to unhide them. The last visible source can't be
 * turned off, so the page never goes blank by accident.
 */
@Composable
fun PeopleSettingsSheet(
    sheetState: AppBottomSheetState,
    sources: List<PeopleSource>,
    hiddenSources: Set<String>,
    hiddenPeople: List<PersonInfo>,
    onSourceVisibleChange: (String, Boolean) -> Unit,
    onUnhide: (PersonInfo) -> Unit,
) {
    ModalSheet(
        sheetState = sheetState,
        title = stringResource(Res.string.people_settings),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        content = {
            SheetLabel(
                title = stringResource(Res.string.people_settings_sources),
                description = stringResource(Res.string.people_settings_sources_desc)
            )
            val visibleCount = sources.count { it.key !in hiddenSources }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                sources.forEachIndexed { index, source ->
                    val visible = source.key !in hiddenSources
                    val canToggle = !visible || visibleCount > 1
                    SheetRow(shape = connectedShape(index, sources.size)) {
                        Icon(
                            imageVector = if (source.key == LOCAL_PEOPLE_SOURCE) Icons.Outlined.Smartphone
                                else Icons.Outlined.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.title ?: stringResource(Res.string.people_settings_source_local),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(Res.string.people_settings_people_count, source.peopleCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(
                            checked = visible,
                            enabled = canToggle,
                            onCheckedChange = { onSourceVisibleChange(source.key, it) }
                        )
                    }
                }
            }

            SheetLabel(
                title = stringResource(Res.string.people_settings_hidden, hiddenPeople.size),
                description = stringResource(Res.string.people_settings_hidden_desc)
            )
            if (hiddenPeople.isEmpty()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    text = stringResource(Res.string.people_settings_no_hidden),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    hiddenPeople.forEachIndexed { index, person ->
                        SheetRow(shape = connectedShape(index, hiddenPeople.size)) {
                            HiddenFace(person)
                            Text(
                                modifier = Modifier.weight(1f),
                                text = person.name.ifBlank { stringResource(Res.string.cloud_people_unknown) },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = { onUnhide(person) }) {
                                Text(stringResource(Res.string.person_hidden_banner_action))
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun connectedShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 8.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@Composable
private fun SheetLabel(title: String, description: String) {
    // The shared settings header; the sheet already pads its content, so the row adds none.
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsItem(item = SettingsEntity.Header(title = title), applyPaddings = false)
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SheetRow(shape: RoundedCornerShape, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) { content() }
}

@Composable
private fun HiddenFace(person: PersonInfo) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val thumbnailUrl = person.thumbnailUrl
        if (thumbnailUrl != null) {
            AsyncImage(
                request = ComposableImageRequest(thumbnailUrl),
                contentDescription = person.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
