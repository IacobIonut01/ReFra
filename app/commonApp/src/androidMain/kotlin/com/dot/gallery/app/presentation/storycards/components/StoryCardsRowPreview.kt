/*
 * SPDX-FileCopyrightText: 2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.app.presentation.storycards.components

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ui.components.PreviewHost
import com.dot.gallery.domain.model.StoryCard

@Preview(name = "Phone", widthDp = 412, heightDp = 236)
@Preview(name = "Dark", widthDp = 412, heightDp = 236, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large text", widthDp = 320, heightDp = 236, fontScale = 1.5f)
@Composable
private fun StoryCardsRowPreview(
    @PreviewParameter(StoryCardsPreviewProvider::class) cards: List<StoryCard>?,
) {
    PreviewHost(darkTheme = isSystemInDarkTheme()) {
        StoryCardsRow(cards = cards, onCardClick = {}, modifier = Modifier.padding(vertical = 8.dp))
    }
}
