/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.ui.people

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.cloud.core.PersonInfo
import com.dot.gallery.cloud.local.PersonMergeSuggestion
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.cloud_people_unknown
import com.dot.gallery.generated.resources.people_different_people
import com.dot.gallery.generated.resources.people_merge_review_title
import com.dot.gallery.generated.resources.people_photo_count
import com.dot.gallery.generated.resources.people_same_person


/**
 * Review queue for "possible duplicates": each row shows two people side by side
 * and asks "are these the same person?". Confirming merges them (writing durable
 * INCLUDE links); rejecting writes EXCLUDE links so the pair never resurfaces.
 * The list shrinks reactively as decisions land, and the sheet closes itself once
 * it is empty. It stays open between answers because a merge can surface new pairs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeReviewSheet(
    suggestions: List<PersonMergeSuggestion>,
    onDismiss: () -> Unit,
    onSamePerson: (PersonMergeSuggestion) -> Unit,
    onDifferent: (PersonMergeSuggestion) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val reviewed = suggestions.isEmpty()
    LaunchedEffect(reviewed) {
        if (reviewed) {
            sheetState.hide()
            onDismiss()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(Res.string.people_merge_review_title),
                style = MaterialTheme.typography.titleLarge
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = suggestions,
                    key = { "${it.first.id}:${it.second.id}" }
                ) { suggestion ->
                    MergeReviewRow(
                        suggestion = suggestion,
                        onSamePerson = { onSamePerson(suggestion) },
                        onDifferent = { onDifferent(suggestion) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MergeReviewRow(
    suggestion: PersonMergeSuggestion,
    onSamePerson: () -> Unit,
    onDifferent: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MergeReviewPerson(suggestion.first)
            MergeReviewPerson(suggestion.second)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDifferent,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(Res.string.people_different_people))
            }
            FilledTonalButton(
                onClick = onSamePerson,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(Res.string.people_same_person))
            }
        }
    }
}

@Composable
private fun MergeReviewPerson(person: PersonInfo) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PersonAvatar(person = person, size = 88.dp)
        Text(
            text = person.name.ifBlank { stringResource(Res.string.cloud_people_unknown) },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (person.assetCount > 0) {
            Text(
                text = stringResource(Res.string.people_photo_count, person.assetCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
