/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.code_none_found
import com.dot.gallery.feature.viewer.generated.resources.code_scan
import com.dot.gallery.feature.viewer.generated.resources.code_scan_stop
import org.jetbrains.compose.resources.stringResource

/**
 * The top-bar Scan button shown when code scanning runs only on request. While a scan runs it
 * turns primary, and a second tap stops it.
 */
@Composable
fun CodeScanButton(
    scanning: Boolean,
    followTheme: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        scanning -> colors.onPrimary
        followTheme -> LocalContentColor.current
        else -> Color.White
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .background(if (scanning) colors.primary else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.QrCodeScanner,
            contentDescription = stringResource(if (scanning) Res.string.code_scan_stop else Res.string.code_scan),
            tint = tint,
        )
    }
}

/** Shows "No codes in this photo" after a scan the person asked for comes back empty. */
@Composable
fun CodeScanNothingFoundToast(controller: InsightController<*>) {
    val context = LocalContext.current
    val message = stringResource(Res.string.code_none_found)
    LaunchedEffect(controller) {
        controller.nothingFound.collect {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
