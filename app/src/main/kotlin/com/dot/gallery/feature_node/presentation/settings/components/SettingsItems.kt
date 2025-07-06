/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.presentation.settings.components

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.SettingsEntity.Header
import com.dot.gallery.core.SettingsEntity.Preference
import com.dot.gallery.core.SettingsEntity.SeekPreference
import com.dot.gallery.core.SettingsEntity.SwitchPreference
import com.dot.gallery.core.SettingsType
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.ui.core.icons.RegularExpression
import com.dot.gallery.ui.theme.GalleryTheme
import kotlin.math.roundToLong

@Composable
fun SettingsItem(
    item: SettingsEntity,
    tintIcon: Boolean = true,
    customizeIcon: (@Composable (icon: ImageVector) -> Unit)? = null
) {
    var checked by remember(item.isChecked) {
        mutableStateOf(item.isChecked == true)
    }
    val icon: @Composable () -> Unit = {
        require(item.icon != null) { "Icon at this stage cannot be null" }
        customizeIcon?.let {
            customizeIcon(item.icon!!)
        } ?: Image(
            imageVector = item.icon!!,
            modifier = Modifier.size(24.dp),
            contentDescription = null,
            colorFilter = if (tintIcon) {
                ColorFilter.tint(
                    MaterialTheme.colorScheme.onSurface
                )
            } else null
        )
    }
    val summary: @Composable () -> Unit = {
        require(!item.summary.isNullOrEmpty()) { "Summary at this stage cannot be null or empty" }
        Text(text = item.summary!!)
    }
    val switch: @Composable () -> Unit = {
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }

    val shape = remember(item.screenPosition) {
        when (item.screenPosition) {
            Position.Alone -> RoundedCornerShape(24.dp)
            Position.Bottom -> RoundedCornerShape(
                topStart = 8.dp,
                topEnd = 8.dp,
                bottomStart = 24.dp,
                bottomEnd = 24.dp
            )

            Position.Middle -> RoundedCornerShape(
                topStart = 8.dp,
                topEnd = 8.dp,
                bottomStart = 8.dp,
                bottomEnd = 8.dp
            )

            Position.Top -> RoundedCornerShape(
                topStart = 24.dp,
                topEnd = 24.dp,
                bottomStart = 8.dp,
                bottomEnd = 8.dp
            )
        }
    }
    val paddingModifier =
        when (item.screenPosition) {
            Position.Alone -> Modifier.padding(bottom = 16.dp)
            Position.Bottom -> Modifier.padding(top = 1.dp, bottom = 16.dp)
            Position.Middle -> Modifier.padding(vertical = 1.dp)
            Position.Top -> Modifier.padding(bottom = 1.dp)
        }

    var currentSeekValue by remember(item.currentValue) {
        mutableStateOf(item.currentValue?.div(item.valueMultiplier.toFloat()))
    }
    val seekTrailing: @Composable () -> Unit = {
        require(item.currentValue != null) { "Current value must not be null" }
        val text by rememberedDerivedState {
            val value = currentSeekValue?.times(item.valueMultiplier)?.roundToLong().toString()
            if (!item.seekSuffix.isNullOrEmpty()) "$value ${item.seekSuffix}" else value
        }
        Text(
            text = text,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp)
        )
    }
    val seekContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            require(item.currentValue != null) { "Current value must not be null" }
            require(item.minValue != null) { "Min value must not be null" }
            require(item.maxValue != null) { "Max value must not be null" }
            require(item.onSeek != null) { "onSeek must not be null" }
            Slider(
                value = currentSeekValue!!,
                onValueChange = { currentSeekValue = it },
                valueRange = item.minValue!!..item.maxValue!!,
                onValueChangeFinished = {
                    item.onSeek!!.invoke(currentSeekValue!! * item.valueMultiplier)
                },
                steps = item.step
            )
        }
    }
    val supportingContent: (@Composable () -> Unit)? = when (item.type) {
        SettingsType.Default, SettingsType.Switch, SettingsType.Seek ->
            if (!item.summary.isNullOrEmpty()) summary else null

        else -> null
    }
    val trailingContent: (@Composable () -> Unit)? = when (item.type) {
        SettingsType.Switch -> switch
        SettingsType.Seek -> seekTrailing
        else -> null
    }
    val clickableModifier =
        if (item.type != SettingsType.Seek && !item.isHeader)
            Modifier.clickable(item.enabled) {
                if (item.type == SettingsType.Switch) {
                    item.onCheck?.let {
                        checked = !checked
                        it(checked)
                    }
                } else item.onClick?.invoke()
            }
        else Modifier
    if (item.isHeader) {
        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 8.dp)
                .padding(bottom = 8.dp)
        )
    } else {
        val alpha by animateFloatAsState(
            targetValue = if (item.enabled) 1f else 0.4f,
            label = "alpha"
        )
        Column(
            modifier = Modifier
                .then(paddingModifier)
                .padding(horizontal = 16.dp)
                .clip(shape)
                .background(
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
                .then(clickableModifier)
                .padding(8.dp)
                .fillMaxWidth()
                .alpha(alpha)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                },
                supportingContent = supportingContent,
                trailingContent = trailingContent,
                leadingContent = if (item.icon != null) icon else null,
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            if (item.type == SettingsType.Seek) {
                seekContent()
            }
        }
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Composable
fun SettingsItemGroupPreview() =
    GalleryTheme {
        Surface {
            Column(
                modifier = Modifier.wrapContentHeight()
            ) {
                SettingsItem(
                    item = Preference(
                        title = "Preview Alone Title",
                        summary = "Preview Summary"
                    )
                )
                SettingsItem(
                    item = Header(
                        title = "Preview Header Title"
                    )
                )
                SettingsItem(
                    item = Preference(
                        icon = Icons.Outlined.Settings,
                        title = "Preview Top Title",
                        summary = "Preview Summary",
                        screenPosition = Position.Top
                    )
                )
                SettingsItem(
                    item = SeekPreference(
                        icon = com.dot.gallery.ui.core.Icons.RegularExpression,
                        title = "Preview Middle Title",
                        summary = "Preview Summary",
                        currentValue = 256f,
                        minValue = 32f,
                        maxValue = 512f,
                        step = 16,
                        onSeek = {},
                        seekSuffix = "MB",
                        screenPosition = Position.Middle
                    )
                )
                SettingsItem(
                    item = SwitchPreference(
                        title = "Preview Middle Title",
                        summary = "Preview Summary\nSecond Line\nThird Line",
                        screenPosition = Position.Middle
                    )
                )
                SettingsItem(
                    item = Preference(
                        title = "Preview Bottom Title",
                        summary = "Preview Summary",
                        screenPosition = Position.Bottom
                    )
                )
            }
        }
    }