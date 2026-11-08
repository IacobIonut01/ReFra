/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.app_name
import com.dot.gallery.feature.setup.generated.resources.setup_chip_private
import com.dot.gallery.feature.setup.generated.resources.setup_chip_search
import com.dot.gallery.feature.setup.generated.resources.setup_chip_servers
import com.dot.gallery.feature.setup.generated.resources.setup_lets_go
import com.dot.gallery.feature.setup.generated.resources.setup_welcome_body
import com.dot.gallery.feature.setup.generated.resources.setup_welcome_title
import com.dot.gallery.ui.theme.GalleryGlass
import org.jetbrains.compose.resources.stringResource

/**
 * Intro page of the setup wizard: the launcher icon the user has picked ([appIcon], supplied by
 * the shell) in a glass tile, a greeting, three glass chips and a full-width Let's go.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupWelcomeContent(
    appIcon: Painter,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SetupStepScaffold(
        modifier = modifier,
        centered = true,
        bottomBar = {
            SetupPrimaryButton(
                label = stringResource(Res.string.setup_lets_go),
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Box(
            modifier = Modifier
                .padding(top = 24.dp)
                .size(144.dp)
                .clip(RoundedCornerShape(44.dp))
                .background(GalleryGlass.chrome),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = appIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(28.dp)),
            )
        }
        Text(
            text = stringResource(Res.string.setup_welcome_title, stringResource(Res.string.app_name)),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 32.dp)
                .semantics { heading() },
        )
        Text(
            text = stringResource(Res.string.setup_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetupGlassChip(Icons.Outlined.Shield, stringResource(Res.string.setup_chip_private))
            SetupGlassChip(Icons.Outlined.AutoAwesome, stringResource(Res.string.setup_chip_search))
            SetupGlassChip(Icons.Outlined.Cloud, stringResource(Res.string.setup_chip_servers))
        }
    }
}
