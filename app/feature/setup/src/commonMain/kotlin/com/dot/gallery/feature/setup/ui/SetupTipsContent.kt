/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.setup.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Celebration
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.TipsAndUpdates
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dot.gallery.feature.setup.generated.resources.Res
import com.dot.gallery.feature.setup.generated.resources.setup_back
import com.dot.gallery.feature.setup.generated.resources.setup_done_subtitle
import com.dot.gallery.feature.setup.generated.resources.setup_done_title
import com.dot.gallery.feature.setup.generated.resources.setup_start_browsing
import com.dot.gallery.feature.setup.generated.resources.setup_step_done
import com.dot.gallery.feature.setup.generated.resources.setup_tip_help
import com.dot.gallery.feature.setup.generated.resources.setup_tip_help_summary
import com.dot.gallery.feature.setup.generated.resources.setup_tip_pinch
import com.dot.gallery.feature.setup.generated.resources.setup_tip_pinch_summary
import com.dot.gallery.feature.setup.generated.resources.setup_tip_select
import com.dot.gallery.feature.setup.generated.resources.setup_tip_select_summary
import org.jetbrains.compose.resources.stringResource

/** Step 5 · All set: the old Tips page, folded into the finish screen with three short tips. */
@Composable
fun SetupAllSetContent(
    stepNumber: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SetupStepScaffold(
        modifier = modifier,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        stepName = stringResource(Res.string.setup_step_done),
        hero = SetupHero(
            icon = Icons.Outlined.Celebration,
            shape = SetupHeroShape.Done.toShape(),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        title = stringResource(Res.string.setup_done_title),
        subtitle = stringResource(Res.string.setup_done_subtitle),
        onBack = onBack,
        bottomBar = {
            SetupBottomActions(
                primaryLabel = stringResource(Res.string.setup_start_browsing),
                onPrimary = onFinish,
                secondaryLabel = stringResource(Res.string.setup_back),
                onSecondary = onBack,
            )
        },
    ) {
        SetupGlassPanel {
            SetupGlassRow(
                title = stringResource(Res.string.setup_tip_pinch),
                summary = stringResource(Res.string.setup_tip_pinch_summary),
                icon = Icons.Outlined.GridView,
                position = SetupRowPosition.Top,
            )
            SetupGlassRow(
                title = stringResource(Res.string.setup_tip_select),
                summary = stringResource(Res.string.setup_tip_select_summary),
                icon = Icons.Outlined.Check,
                position = SetupRowPosition.Middle,
            )
            SetupGlassRow(
                title = stringResource(Res.string.setup_tip_help),
                summary = stringResource(Res.string.setup_tip_help_summary),
                icon = Icons.Outlined.TipsAndUpdates,
                position = SetupRowPosition.Bottom,
            )
        }
    }
}
