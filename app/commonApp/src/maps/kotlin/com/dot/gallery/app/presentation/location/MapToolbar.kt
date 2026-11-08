package com.dot.gallery.app.presentation.location

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.core.location.MapAppearance
import com.dot.gallery.generated.resources.Res
import com.dot.gallery.generated.resources.map_appearance_action
import com.dot.gallery.generated.resources.map_appearance_dark
import com.dot.gallery.generated.resources.map_appearance_light
import com.dot.gallery.generated.resources.map_appearance_system
import com.dot.gallery.core.presentation.components.GalleryPageTopBarAction



/** Map style action for the map's top bar: shows the current style and opens the choices. */
@Composable
internal fun MapAppearanceAction(
    appearance: MapAppearance,
    onAppearanceChange: (MapAppearance) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        GalleryPageTopBarAction(
            icon = when (appearance) {
                MapAppearance.SYSTEM -> Icons.Outlined.SettingsBrightness
                MapAppearance.LIGHT -> Icons.Outlined.LightMode
                MapAppearance.DARK -> Icons.Outlined.DarkMode
            },
            contentDescription = stringResource(Res.string.map_appearance_action),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MapAppearance.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (option) {
                                MapAppearance.SYSTEM -> stringResource(Res.string.map_appearance_system)
                                MapAppearance.LIGHT -> stringResource(Res.string.map_appearance_light)
                                MapAppearance.DARK -> stringResource(Res.string.map_appearance_dark)
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (option) {
                                MapAppearance.SYSTEM -> Icons.Outlined.SettingsBrightness
                                MapAppearance.LIGHT -> Icons.Outlined.LightMode
                                MapAppearance.DARK -> Icons.Outlined.DarkMode
                            },
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        onAppearanceChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
