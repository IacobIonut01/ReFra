package com.dot.gallery.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp


/**
 * The card chrome for free-form sections of the media-details sheet (motion-photo shots and
 * other app-side slot content): a [viewerSheetRowColor] fill with 24 dp corners and a 600 dp
 * width cap. It never blurs on its own; the sheet's single blur shows through the tint.
 */
@Composable
fun MediaInfoSheetCard(
    modifier: Modifier = Modifier,
    innerPadding: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ViewerSheetOuterCorner))
            .background(viewerSheetRowColor())
            .then(innerPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}
