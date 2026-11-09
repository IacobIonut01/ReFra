/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.viewer.insight

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.insight.CodeInsight
import com.dot.gallery.core.insight.CodePayload
import com.dot.gallery.core.insight.CodePayloadParser
import com.dot.gallery.core.insight.WifiSecurity
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.core.ui.util.AppBottomSheetState
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.code_action_add_contact
import com.dot.gallery.feature.viewer.generated.resources.code_action_add_event
import com.dot.gallery.feature.viewer.generated.resources.code_action_call
import com.dot.gallery.feature.viewer.generated.resources.code_action_connect
import com.dot.gallery.feature.viewer.generated.resources.code_action_copy
import com.dot.gallery.feature.viewer.generated.resources.code_action_copy_password
import com.dot.gallery.feature.viewer.generated.resources.code_action_hide_password
import com.dot.gallery.feature.viewer.generated.resources.code_action_open_link
import com.dot.gallery.feature.viewer.generated.resources.code_action_open_map
import com.dot.gallery.feature.viewer.generated.resources.code_action_send_message
import com.dot.gallery.feature.viewer.generated.resources.code_action_share
import com.dot.gallery.feature.viewer.generated.resources.code_action_show_password
import com.dot.gallery.feature.viewer.generated.resources.code_action_write_email
import com.dot.gallery.feature.viewer.generated.resources.code_copied
import com.dot.gallery.feature.viewer.generated.resources.code_found_in_photo
import com.dot.gallery.feature.viewer.generated.resources.code_hint_http
import com.dot.gallery.feature.viewer.generated.resources.code_hint_https
import com.dot.gallery.feature.viewer.generated.resources.code_hint_international
import com.dot.gallery.feature.viewer.generated.resources.code_index_cd
import com.dot.gallery.feature.viewer.generated.resources.code_list_title
import com.dot.gallery.feature.viewer.generated.resources.code_no_app
import com.dot.gallery.feature.viewer.generated.resources.code_wifi_hidden
import com.dot.gallery.feature.viewer.generated.resources.code_wifi_open
import com.dot.gallery.feature.viewer.generated.resources.code_wifi_password
import com.dot.gallery.feature.viewer.generated.resources.code_wifi_security
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * The sheet a code chip or a tapped code opens. With [selected] null and several codes it lists
 * them; otherwise it shows one code's value and its actions. Nothing happens until a button is
 * tapped.
 */
@Composable
fun CodeResultSheet(
    sheetState: AppBottomSheetState,
    insights: List<CodeInsight>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = selected?.let(insights::getOrNull) ?: insights.singleOrNull()
    ModalSheet(
        sheetState = sheetState,
        onDismissRequest = { onDismiss() },
        // The details view draws its own header with the type icon; only the list needs the
        // sheet's title. ModalSheet already scrolls and pads its content.
        title = if (current == null) stringResource(Res.string.code_list_title) else null,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (current == null) {
                    CodeList(insights = insights, onSelect = onSelect)
                } else {
                    CodeDetails(insight = current)
                }
            }
        },
    )
}

@Composable
private fun CodeList(insights: List<CodeInsight>, onSelect: (Int?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        insights.forEachIndexed { index, insight ->
            val shape = when {
                insights.size == 1 -> RoundedCornerShape(24.dp)
                index == 0 -> RoundedCornerShape(24.dp, 24.dp, 6.dp, 6.dp)
                index == insights.lastIndex -> RoundedCornerShape(6.dp, 6.dp, 24.dp, 24.dp)
                else -> RoundedCornerShape(6.dp)
            }
            val kind = stringResource(insight.payload.kindLabel())
            val summary = CodePayloadParser.summary(insight.payload)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable(role = Role.Button) { onSelect(index) }
                    .heightIn(min = 64.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IndexBadge(index + 1, stringResource(Res.string.code_index_cd, index + 1, kind))
                Column(modifier = Modifier.weight(1f)) {
                    Text(kind, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

@Composable
private fun IndexBadge(number: Int, description: String) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun CodeDetails(insight: CodeInsight) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val payload = insight.payload
    val notify: (Boolean) -> Unit = { handled ->
        if (!handled) {
            scope.launch {
                Toast.makeText(context, getString(Res.string.code_no_app), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val copy: (String, Boolean) -> Unit = { text, sensitive ->
        CodeActions.copy(context, text, sensitive)
        // Android 13+ shows its own clipboard confirmation.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            scope.launch { Toast.makeText(context, getString(Res.string.code_copied), Toast.LENGTH_SHORT).show() }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(payload.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Column {
            Text(
                text = stringResource(payload.kindLabel()),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.code_found_in_photo, insight.format.displayName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    PayloadCard(payload = payload, rawText = insight.text)

    when (payload) {
        is CodePayload.Url -> {
            PrimaryAction(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(Res.string.code_action_open_link)) {
                notify(CodeActions.openLink(context, payload))
            }
            CopyShareRow(onCopy = { copy(payload.url, false) }, onShare = { notify(CodeActions.share(context, payload.url)) })
        }
        is CodePayload.Wifi -> {
            PrimaryAction(Icons.Outlined.Wifi, stringResource(Res.string.code_action_connect)) {
                // Without a system suggestion the person types the password in Settings, so copy it.
                if (!CodeActions.canSuggestWifi(payload)) payload.password?.let { copy(it, true) }
                notify(CodeActions.connectWifi(context, payload))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                payload.password?.let { password ->
                    SecondaryAction(
                        Icons.Outlined.ContentCopy,
                        stringResource(Res.string.code_action_copy_password),
                        Modifier.weight(1f),
                    ) { copy(password, true) }
                }
                SecondaryAction(Icons.Outlined.Share, stringResource(Res.string.code_action_share), Modifier.weight(1f)) {
                    notify(CodeActions.share(context, payload.ssid))
                }
            }
        }
        is CodePayload.Contact -> {
            PrimaryAction(Icons.Outlined.PersonAdd, stringResource(Res.string.code_action_add_contact)) {
                notify(CodeActions.addContact(context, payload))
            }
            CopyShareRow(onCopy = { copy(insight.text, false) }, onShare = { notify(CodeActions.share(context, insight.text)) })
        }
        is CodePayload.Email -> {
            PrimaryAction(Icons.Outlined.Email, stringResource(Res.string.code_action_write_email)) {
                notify(CodeActions.writeEmail(context, payload))
            }
            CopyShareRow(onCopy = { copy(payload.to, false) }, onShare = { notify(CodeActions.share(context, payload.to)) })
        }
        is CodePayload.Phone -> {
            PrimaryAction(Icons.Outlined.Call, stringResource(Res.string.code_action_call)) {
                notify(CodeActions.dial(context, payload.number))
            }
            CopyShareRow(onCopy = { copy(payload.number, false) }, onShare = { notify(CodeActions.share(context, payload.number)) })
        }
        is CodePayload.Sms -> {
            PrimaryAction(Icons.Outlined.Sms, stringResource(Res.string.code_action_send_message)) {
                notify(CodeActions.sendMessage(context, payload))
            }
            CopyShareRow(onCopy = { copy(payload.number, false) }, onShare = { notify(CodeActions.share(context, insight.text)) })
        }
        is CodePayload.Geo -> {
            PrimaryAction(Icons.Outlined.Map, stringResource(Res.string.code_action_open_map)) {
                notify(CodeActions.openMap(context, payload))
            }
            CopyShareRow(onCopy = { copy(insight.text, false) }, onShare = { notify(CodeActions.share(context, insight.text)) })
        }
        is CodePayload.Event -> {
            PrimaryAction(Icons.Outlined.Event, stringResource(Res.string.code_action_add_event)) {
                notify(CodeActions.addEvent(context, payload))
            }
            CopyShareRow(onCopy = { copy(insight.text, false) }, onShare = { notify(CodeActions.share(context, insight.text)) })
        }
        is CodePayload.Product, is CodePayload.Text -> {
            CopyShareRow(onCopy = { copy(insight.text, false) }, onShare = { notify(CodeActions.share(context, insight.text)) })
        }
    }
}

/** The decoded value, selectable, never shortened. */
@Composable
private fun PayloadCard(payload: CodePayload, rawText: String) {
    var revealPassword by rememberSaveable(rawText) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val headline = CodePayloadParser.summary(payload)
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (headline.isNotEmpty()) {
                    Text(headline, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                val body = when (payload) {
                    is CodePayload.Url -> payload.url
                    is CodePayload.Wifi, is CodePayload.Product -> null
                    else -> rawText.takeIf { it.trim() != headline }
                }
                body?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (payload is CodePayload.Wifi) {
                    val security = when (payload.security) {
                        WifiSecurity.OPEN -> stringResource(Res.string.code_wifi_open)
                        WifiSecurity.WEP -> "WEP"
                        WifiSecurity.WPA -> "WPA/WPA2"
                        WifiSecurity.SAE -> "WPA3"
                    }
                    Text(
                        stringResource(Res.string.code_wifi_security, security),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (payload.hidden) {
                        Text(
                            stringResource(Res.string.code_wifi_hidden),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    payload.password?.let { password ->
                        val shown = if (revealPassword) password else MASK.repeat(password.length.coerceIn(8, 16))
                        Text(
                            stringResource(Res.string.code_wifi_password, shown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        when (payload) {
            is CodePayload.Url -> {
                Hint(
                    icon = if (payload.isSecure) Icons.Outlined.Lock else Icons.Outlined.Warning,
                    text = stringResource(if (payload.isSecure) Res.string.code_hint_https else Res.string.code_hint_http),
                    warning = !payload.isSecure,
                )
                if (payload.isInternationalHost) {
                    Hint(Icons.Outlined.Warning, stringResource(Res.string.code_hint_international), warning = true)
                }
            }
            is CodePayload.Wifi -> if (payload.password != null) {
                TextButton(onClick = { revealPassword = !revealPassword }) {
                    Icon(
                        if (revealPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(
                            if (revealPassword) Res.string.code_action_hide_password else Res.string.code_action_show_password
                        )
                    )
                }
            }
            else -> Unit
        }
    }
}

private const val MASK = "•"

@Composable
private fun Hint(icon: ImageVector, text: String, warning: Boolean) {
    val color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun PrimaryAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun SecondaryAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.heightIn(min = 56.dp)) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun CopyShareRow(onCopy: () -> Unit, onShare: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SecondaryAction(Icons.Outlined.ContentCopy, stringResource(Res.string.code_action_copy), Modifier.weight(1f), onCopy)
        SecondaryAction(Icons.Outlined.Share, stringResource(Res.string.code_action_share), Modifier.weight(1f), onShare)
    }
}
