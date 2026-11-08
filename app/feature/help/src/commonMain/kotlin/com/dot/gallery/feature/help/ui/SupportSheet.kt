/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.help.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.VolunteerActivism
import com.dot.gallery.core.presentation.components.ExpressiveSheet
import com.dot.gallery.core.presentation.components.OptionItem
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.presentation.components.SheetOptionRow
import com.dot.gallery.core.presentation.components.SheetRowTrailing
import com.dot.gallery.core.presentation.components.SheetTone
import com.dot.gallery.core.presentation.components.sheetRowPosition
import com.dot.gallery.core.ui.util.AppBottomSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.help.generated.resources.Res
import com.dot.gallery.feature.help.generated.resources.click_to_copy
import com.dot.gallery.feature.help.generated.resources.support_crypto
import com.dot.gallery.feature.help.generated.resources.support_paypal
import com.dot.gallery.feature.help.generated.resources.support_revolut
import com.dot.gallery.feature.help.generated.resources.support_the_project


@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun SupportSheet(
    state: AppBottomSheetState
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var showCryptoOptions by rememberSaveable {
        mutableStateOf(false)
    }
    val clipboard = LocalClipboardManager.current
    val paypalTitle = stringResource(Res.string.support_paypal)
    val revolutTitle = stringResource(Res.string.support_revolut)
    val cryptoTitle = stringResource(Res.string.support_crypto)
    val mainOptions = remember(paypalTitle, revolutTitle, cryptoTitle) {
        listOf(
            OptionItem(
                text = paypalTitle,
                onClick = {
                    uriHandler.openUri("https://www.paypal.com/paypalme/iacobionut01")
                }
            ),
            OptionItem(
                text = revolutTitle,
                onClick = {
                    uriHandler.openUri("https://revolut.me/somaldoaca")
                }
            ),
            OptionItem(
                text = cryptoTitle,
                onClick = {
                    showCryptoOptions = true
                }
            )
        )
    }
    val cryptoOnClick: (String) -> Unit = {
        clipboard.setText(AnnotatedString(it))
    }
    val cryptoOptions = remember {
        mapOf(
            "ETH" to "0x707eF0E95e814E05efadFD3d3783401cfbE8D11E",
            "POLYGON" to "0x707eF0E95e814E05efadFD3d3783401cfbE8D11E",
            "AVALANCHE" to "0x707eF0E95e814E05efadFD3d3783401cfbE8D11E",
            "BNB" to "0x707eF0E95e814E05efadFD3d3783401cfbE8D11E",
            "XDC" to "0x707eF0E95e814E05efadFD3d3783401cfbE8D11E",
            "XMR" to "48JMiDR8mZiEis8V5pWtxv3r121K6fqqZfhajiewCbff27GnucdyLfkQ12Fr6ZM1eR9Botdu3Rx1bidmHyLTZBWnJ4AJKot",
            "EPIC" to "esWaroB8AQXZuaEJWtraPomNH3Lg1JRP3EVxr6batoFuqvf3hrHP@epicbox.epic.tech",
            "XRP" to "rPjYh6XMMra3zHFqDn6ZnYFCmPWPnbTHkc",
            "ADA" to "addr1q978p8x80z2d4je5gutav0ypplgtpw0mhmwky8k5mscds9ru9nma366mryl0ln8ump7ysj5wa9sg20c4x7ywjyzvacxseapv0y",
            "XLM" to "GAHXB7JI4QEZW2ZM4CA6PQ37Y3636UXWWXDHAZLHCJV5HGXYWQJBIE5Q",
            "INJ" to "inj1wpl0p627s98qtmadl57n0q6qrna735g72dner7",
            "SUI" to "0xe135452c381f3298e0ddb17c3e1ede8e1d6aaefb3bc3734219dd6d14ce2177ce",
            "SEI" to "sei1t5c9dmjdempk7hklw0mm4wkxwzauncvqsgs3l4",
            "HBAR" to "0.0.4688681-szsjz"
        ).map { (coin, address) ->
            OptionItem(
                text = coin,
                summary = address,
                onClick = cryptoOnClick
            )
        }
    }
    if (state.isVisible) {
        BackHandler(showCryptoOptions) {
            showCryptoOptions = false
        }
    }
    ExpressiveSheet(
        sheetState = state,
        paneTitle = stringResource(Res.string.support_the_project),
        onDismissRequest = { showCryptoOptions = false },
    ) {
        SupportSheetContent(
            options = if (showCryptoOptions) cryptoOptions else mainOptions,
            showingAddresses = showCryptoOptions,
        )
    }
}

/**
 * Stateless body of [SupportSheet]: a badge and title, then each option as a grouped row. While
 * [showingAddresses] the rows are crypto addresses that copy on tap.
 */
@Composable
fun ColumnScope.SupportSheetContent(
    options: List<OptionItem>,
    showingAddresses: Boolean,
) {
    SheetHeader(
        title = stringResource(Res.string.support_the_project),
        body = if (showingAddresses) stringResource(Res.string.click_to_copy) else null,
        icon = Icons.Outlined.VolunteerActivism,
        tone = SheetTone.Tertiary,
    )
    options.forEachIndexed { index, item ->
        SheetOptionRow(
            title = item.text,
            summary = item.summary,
            icon = if (showingAddresses) Icons.Outlined.ContentCopy else Icons.AutoMirrored.Outlined.OpenInNew,
            position = sheetRowPosition(index, options.size),
            trailing = if (showingAddresses) SheetRowTrailing.None else SheetRowTrailing.Chevron,
            onClick = { item.onClick(item.summary.toString()) },
        )
    }
}