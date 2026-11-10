/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.insight.InsightKey
import com.dot.gallery.core.insight.TextInsight
import com.dot.gallery.domain.model.Media
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.text_copied
import com.dot.gallery.feature.viewer.generated.resources.text_none_found
import org.jetbrains.compose.resources.stringResource

/**
 * The Text chip and, in selection mode, the selection header and bar. Drawn by the viewer above the
 * pager; [onSearch] opens Search with the selection (hidden for vault photos, which are never searchable).
 */
@Composable
fun TextInsightChrome(
    controller: InsightController<Media>,
    holder: TextSelectionHolder,
    media: Media?,
    key: InsightKey?,
    onRequest: Boolean,
    chromeVisible: Boolean,
    isVault: Boolean,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by controller.state.collectAsState()
    val insight = state.insights.filterIsInstance<TextInsight>().firstOrNull()?.takeIf { state.key == key }
    val noneFound = stringResource(Res.string.text_none_found)
    val copied = stringResource(Res.string.text_copied)
    LaunchedEffect(controller) {
        controller.nothingFound.collect { Toast.makeText(context, noneFound, Toast.LENGTH_SHORT).show() }
    }
    BackHandler(enabled = holder.active) { holder.close() }

    Box(modifier.fillMaxSize()) {
        val selection = holder.selection
        val selected = holder.insight
        if (selection != null && selected != null) {
            TextSelectionHeader(
                count = selection.count,
                onClose = holder::close,
                modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.statusBars).padding(16.dp),
            )
            TextSelectionBar(
                showSearch = !isVault,
                onCopy = { copyText(context, holder.selectedText, copied) },
                onShare = { shareText(context, holder.selectedText) },
                onSelectAll = { holder.update(TextSelection.all(selected.lines.size)) },
                onSearch = { onSearch(holder.selectedText); holder.close() },
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 24.dp),
            )
        } else if (chromeVisible && media != null && key != null) {
            val chipModifier = Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, bottom = 92.dp)
            when {
                insight != null -> TextChip(TextChipMode.FOUND, insight.lines.size, onClick = { holder.start(insight) }, modifier = chipModifier)
                onRequest && state.manualScanRunning && state.key == key ->
                    TextChip(TextChipMode.READING, 0, onClick = controller::cancel, modifier = chipModifier)
                onRequest -> TextChip(TextChipMode.READ, 0, onClick = { controller.request(key, media, InsightTrigger.MANUAL) }, modifier = chipModifier)
            }
        }
    }
}

private fun copyText(context: Context, text: String, confirmation: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("text", text))
    Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
