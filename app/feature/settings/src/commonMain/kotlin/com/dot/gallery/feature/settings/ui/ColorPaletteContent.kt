/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.settings.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FontDownload
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.location.MapAppearance
import com.dot.gallery.core.presentation.components.GalleryFilterChip
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.core.ui.components.ChooserPreferenceDetailScreen
import com.dot.gallery.core.ui.components.MiniHomeScreen
import com.dot.gallery.core.ui.components.PreferenceOption
import com.dot.gallery.core.ui.components.PreferencePreviewStage
import com.dot.gallery.core.ui.components.PreferencePreviewTile
import com.dot.gallery.core.ui.components.PreviewIconBadge
import com.dot.gallery.core.ui.components.PreviewPhoto
import com.dot.gallery.core.ui.components.RequestInitialSettingsFocus
import com.dot.gallery.core.ui.components.SettingsItem
import com.dot.gallery.core.ui.components.SwitchPreferenceDetailScreen
import com.dot.gallery.core.ui.components.rememberPreference
import com.dot.gallery.core.ui.components.rememberSettingsFocusState
import com.dot.gallery.core.ui.components.rememberSwitchPreference
import com.dot.gallery.core.ui.components.settingsContentWidth
import com.dot.gallery.core.ui.components.settingsFocusGroup
import com.dot.gallery.core.ui.components.settingsFocusTarget
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.amoled_mode_description
import com.dot.gallery.feature.settings.generated.resources.amoled_mode_requires_dark
import com.dot.gallery.feature.settings.generated.resources.amoled_mode_requires_dark_description
import com.dot.gallery.feature.settings.generated.resources.amoled_mode_summary
import com.dot.gallery.feature.settings.generated.resources.amoled_mode_title
import com.dot.gallery.feature.settings.generated.resources.appearance_colors_header
import com.dot.gallery.feature.settings.generated.resources.appearance_theme_header
import com.dot.gallery.feature.settings.generated.resources.auto
import com.dot.gallery.feature.settings.generated.resources.auto_contrast
import com.dot.gallery.feature.settings.generated.resources.auto_contrast_description
import com.dot.gallery.feature.settings.generated.resources.auto_contrast_summary
import com.dot.gallery.feature.settings.generated.resources.cancel
import com.dot.gallery.feature.settings.generated.resources.carto_basemap_key_configured
import com.dot.gallery.feature.settings.generated.resources.carto_basemap_key_description
import com.dot.gallery.feature.settings.generated.resources.carto_basemap_key_field
import com.dot.gallery.feature.settings.generated.resources.carto_basemap_key_not_configured
import com.dot.gallery.feature.settings.generated.resources.carto_basemap_key_title
import com.dot.gallery.feature.settings.generated.resources.color_palette_other_colors
import com.dot.gallery.feature.settings.generated.resources.color_palette_preview_description
import com.dot.gallery.feature.settings.generated.resources.color_palette_wallpaper_colors
import com.dot.gallery.feature.settings.generated.resources.fancy_blur
import com.dot.gallery.feature.settings.generated.resources.fancy_blur_description
import com.dot.gallery.feature.settings.generated.resources.fancy_blur_requires_android_12
import com.dot.gallery.feature.settings.generated.resources.fancy_blur_summary
import com.dot.gallery.feature.settings.generated.resources.map_appearance_dark
import com.dot.gallery.feature.settings.generated.resources.map_appearance_description
import com.dot.gallery.feature.settings.generated.resources.map_appearance_header
import com.dot.gallery.feature.settings.generated.resources.map_appearance_light
import com.dot.gallery.feature.settings.generated.resources.map_appearance_system
import com.dot.gallery.feature.settings.generated.resources.map_appearance_title
import com.dot.gallery.feature.settings.generated.resources.save
import com.dot.gallery.feature.settings.generated.resources.settings_appearance
import com.dot.gallery.feature.settings.generated.resources.settings_font_header
import com.dot.gallery.feature.settings.generated.resources.settings_visual_effects_header
import com.dot.gallery.feature.settings.generated.resources.shared_elements
import com.dot.gallery.feature.settings.generated.resources.shared_elements_description
import com.dot.gallery.feature.settings.generated.resources.shared_elements_summary
import com.dot.gallery.feature.settings.generated.resources.theme_mode_dark
import com.dot.gallery.feature.settings.generated.resources.theme_mode_light
import com.dot.gallery.feature.settings.generated.resources.use_system_font_description
import com.dot.gallery.feature.settings.generated.resources.use_system_font_summary
import com.dot.gallery.feature.settings.generated.resources.use_system_font_title
import com.dot.gallery.ui.theme.GalleryPreviewTheme
import com.dot.gallery.ui.theme.THEME_SEED_NEUTRAL
import com.dot.gallery.ui.theme.THEME_SEED_SYSTEM
import com.dot.gallery.ui.theme.colorSchemeFromSeed
import com.dot.gallery.ui.theme.isDarkTheme
import com.dot.gallery.ui.theme.neutralColorScheme
import dev.chrisbanes.haze.hazeSource
import org.jetbrains.compose.resources.stringResource

private const val DETAIL_AMOLED = "amoled"
private const val DETAIL_BLUR = "blur"
private const val DETAIL_AUTO_CONTRAST = "auto_contrast"
private const val DETAIL_SHARED = "shared"
private const val DETAIL_SYSTEM_FONT = "system_font"
private const val DETAIL_MAP_APPEARANCE = "map_appearance"

/** Which tile of the Appearance theme picker is selected. */
private enum class ThemeMode { Auto, Light, Dark }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ColorPaletteContent(state: ColorPaletteState) {
    var detailKey by rememberSaveable { mutableStateOf<String?>(null) }
    val contentScrollState = rememberScrollState()
    val initialFocusRequester = remember { FocusRequester() }
    RequestInitialSettingsFocus(initialFocusRequester, detailKey == null)
    var themeColorSeed by rememberPreference(SettingsKeys.themeColorSeed)
    var forceTheme by rememberPreference(SettingsKeys.forceTheme)
    var darkModeValue by rememberPreference(SettingsKeys.darkMode)
    var amoledModeValue by rememberPreference(SettingsKeys.amoledMode)
    var allowBlur by rememberPreference(SettingsKeys.allowBlur)
    var autoContrast by rememberPreference(SettingsKeys.autoContrast)
    var sharedElements by rememberPreference(SettingsKeys.sharedElements)
    var useSystemFont by rememberPreference(SettingsKeys.useSystemFont)
    var mapAppearance by rememberMapAppearance()
    var userCartoKey by rememberPreference(SettingsKeys.cartoBasemapKey)
    var cartoKeyDraft by remember { mutableStateOf("") }
    var showCartoKeyDialog by remember { mutableStateOf(false) }
    val isDark = isDarkTheme()

    when (detailKey) {
        DETAIL_AMOLED -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.amoled_mode_title),
                isChecked = amoledModeValue,
                onCheckedChange = { amoledModeValue = it },
                description = stringResource(
                    if (isDark) Res.string.amoled_mode_description
                    else Res.string.amoled_mode_requires_dark_description
                ),
                preview = { checked -> AmoledPreview(checked) },
                enabled = isDark,
            )
            return
        }
        DETAIL_BLUR -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.fancy_blur),
                isChecked = allowBlur,
                onCheckedChange = { allowBlur = it },
                description = stringResource(Res.string.fancy_blur_description),
                preview = { checked -> BlurPreview(checked) },
                enabled = state.supportsBlur,
            )
            return
        }
        DETAIL_AUTO_CONTRAST -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.auto_contrast),
                isChecked = autoContrast,
                onCheckedChange = { autoContrast = it },
                description = stringResource(Res.string.auto_contrast_description),
            )
            return
        }
        DETAIL_SHARED -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.shared_elements),
                isChecked = sharedElements,
                onCheckedChange = { sharedElements = it },
                description = stringResource(Res.string.shared_elements_description),
            )
            return
        }
        DETAIL_SYSTEM_FONT -> {
            BackHandler { detailKey = null }
            SwitchPreferenceDetailScreen(
                title = stringResource(Res.string.use_system_font_title),
                isChecked = useSystemFont,
                onCheckedChange = { useSystemFont = it },
                description = stringResource(Res.string.use_system_font_description),
            )
            return
        }
        DETAIL_MAP_APPEARANCE -> {
            BackHandler { detailKey = null }
            ChooserPreferenceDetailScreen(
                title = stringResource(Res.string.map_appearance_title),
                description = stringResource(Res.string.map_appearance_description),
                options = listOf(
                    PreferenceOption(MapAppearance.SYSTEM, stringResource(Res.string.map_appearance_system), mapAppearance == MapAppearance.SYSTEM),
                    PreferenceOption(MapAppearance.LIGHT, stringResource(Res.string.map_appearance_light), mapAppearance == MapAppearance.LIGHT),
                    PreferenceOption(MapAppearance.DARK, stringResource(Res.string.map_appearance_dark), mapAppearance == MapAppearance.DARK),
                ),
                onOptionSelected = { mapAppearance = it },
            )
            return
        }
    }

    var selectedTab by rememberSaveable(themeColorSeed) {
        mutableIntStateOf(
            if (themeColorSeed == THEME_SEED_SYSTEM) 0
            else if (presetPalettes.any { it.hexKey == themeColorSeed }) 1
            else 0
        )
    }

    val themeMode = when {
        !forceTheme -> ThemeMode.Auto
        darkModeValue -> ThemeMode.Dark
        else -> ThemeMode.Light
    }
    val onThemeModeSelected: (ThemeMode) -> Unit = { mode ->
        when (mode) {
            ThemeMode.Auto -> forceTheme = false
            ThemeMode.Light -> {
                forceTheme = true
                darkModeValue = false
            }
            ThemeMode.Dark -> {
                forceTheme = true
                darkModeValue = true
            }
        }
    }

    val amoledModePref = rememberSwitchPreference(
        amoledModeValue,
        isDark,
        title = stringResource(Res.string.amoled_mode_title),
        icon = Icons.Outlined.Contrast,
        summary = stringResource(
            if (isDark) Res.string.amoled_mode_summary
            else Res.string.amoled_mode_requires_dark
        ),
        enabled = isDark,
        isChecked = amoledModeValue,
        onCheck = { amoledModeValue = it },
        onClick = { detailKey = DETAIL_AMOLED },
        screenPosition = Position.Alone
    )
    val effectsHeaderTitle = stringResource(Res.string.settings_visual_effects_header)
    val effectsHeader = remember(effectsHeaderTitle) { SettingsEntity.Header(title = effectsHeaderTitle) }
    val allowBlurPref = rememberSwitchPreference(
        allowBlur,
        state.supportsBlur,
        title = stringResource(Res.string.fancy_blur),
        icon = Icons.Outlined.BlurOn,
        summary = stringResource(
            if (state.supportsBlur) Res.string.fancy_blur_summary
            else Res.string.fancy_blur_requires_android_12
        ),
        isChecked = allowBlur,
        onCheck = { allowBlur = it },
        onClick = { detailKey = DETAIL_BLUR },
        enabled = state.supportsBlur,
        screenPosition = Position.Top
    )
    val autoContrastPref = rememberSwitchPreference(
        autoContrast,
        title = stringResource(Res.string.auto_contrast),
        icon = Icons.Outlined.Tune,
        summary = stringResource(Res.string.auto_contrast_summary),
        isChecked = autoContrast,
        onCheck = { autoContrast = it },
        onClick = { detailKey = DETAIL_AUTO_CONTRAST },
        screenPosition = Position.Middle
    )
    val sharedElementsPref = rememberSwitchPreference(
        sharedElements,
        title = stringResource(Res.string.shared_elements),
        icon = Icons.Outlined.Animation,
        summary = stringResource(Res.string.shared_elements_summary),
        isChecked = sharedElements,
        onCheck = { sharedElements = it },
        onClick = { detailKey = DETAIL_SHARED },
        screenPosition = Position.Bottom
    )
    val fontHeaderTitle = stringResource(Res.string.settings_font_header)
    val fontHeader = remember(fontHeaderTitle) { SettingsEntity.Header(title = fontHeaderTitle) }
    val useSystemFontPref = rememberSwitchPreference(
        useSystemFont,
        title = stringResource(Res.string.use_system_font_title),
        icon = Icons.Outlined.FontDownload,
        summary = stringResource(Res.string.use_system_font_summary),
        isChecked = useSystemFont,
        onCheck = { useSystemFont = it },
        onClick = { detailKey = DETAIL_SYSTEM_FONT },
        screenPosition = Position.Alone
    )
    val mapAppearanceLabel = when (mapAppearance) {
        MapAppearance.SYSTEM -> stringResource(Res.string.map_appearance_system)
        MapAppearance.LIGHT -> stringResource(Res.string.map_appearance_light)
        MapAppearance.DARK -> stringResource(Res.string.map_appearance_dark)
    }
    val mapAppearancePref = rememberPreference(
        mapAppearance,
        title = stringResource(Res.string.map_appearance_title),
        icon = Icons.Outlined.Map,
        summary = mapAppearanceLabel,
        onClick = { detailKey = DETAIL_MAP_APPEARANCE },
        screenPosition = if (state.showManualCartoKey) Position.Top else Position.Alone
    )
    val cartoKeyPref = rememberPreference(
        userCartoKey,
        title = stringResource(Res.string.carto_basemap_key_title),
        icon = Icons.Outlined.Key,
        summary = stringResource(
            if (userCartoKey.isBlank()) Res.string.carto_basemap_key_not_configured
            else Res.string.carto_basemap_key_configured
        ),
        onClick = {
            cartoKeyDraft = userCartoKey
            showCartoKeyDialog = true
        },
        screenPosition = Position.Bottom
    )

    val swatchesContent: @Composable () -> Unit = {
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally { fullWidth -> direction * fullWidth } + fadeIn())
                    .togetherWith(slideOutHorizontally { fullWidth -> -direction * fullWidth } + fadeOut())
                    .using(SizeTransform(clip = false))
            },
            label = "swatchesAnimation"
        ) { tab ->
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tab == 0) {
                    item(key = "system") {
                        SystemColorOption(
                            isSelected = themeColorSeed == THEME_SEED_SYSTEM,
                            onClick = { themeColorSeed = THEME_SEED_SYSTEM }
                        )
                    }
                    items(state.wallpaperPalettes, key = { it.hexKey }) { palette ->
                        val scheme = remember(palette.seedArgb, isDark, amoledModeValue) {
                            colorSchemeFromSeed(palette.seedArgb, isDark, amoledModeValue)
                        }
                        ColorCircleItem(
                            scheme = scheme,
                            isSelected = themeColorSeed == palette.hexKey,
                            onClick = { themeColorSeed = palette.hexKey }
                        )
                    }
                    item(key = "neutral_divider") {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                    item(key = "neutral") {
                        val neutralScheme = remember(isDark, amoledModeValue) {
                            neutralColorScheme(isDark, amoledModeValue)
                        }
                        ColorCircleItem(
                            scheme = neutralScheme,
                            isSelected = themeColorSeed == THEME_SEED_NEUTRAL,
                            onClick = { themeColorSeed = THEME_SEED_NEUTRAL }
                        )
                    }
                } else {
                    items(presetPalettes, key = { it.hexKey }) { palette ->
                        val scheme = remember(palette.seedArgb, isDark, amoledModeValue) {
                            colorSchemeFromSeed(palette.seedArgb, isDark, amoledModeValue)
                        }
                        ColorCircleItem(
                            scheme = scheme,
                            isSelected = themeColorSeed == palette.hexKey,
                            onClick = { themeColorSeed = palette.hexKey }
                        )
                    }
                }
            }
        }
    }

    val previewCard: @Composable (Dp) -> Unit = { phoneWidth ->
        ThemePreviewCard(phoneWidth = phoneWidth)
    }

    val controls: @Composable ColumnScope.() -> Unit = {
        SettingsItem(item = SettingsEntity.Header(title = stringResource(Res.string.appearance_colors_header)))
        Column(
            modifier = Modifier
                .settingsContentWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GalleryFilterChip(
                    label = stringResource(Res.string.color_palette_wallpaper_colors),
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.focusRequester(initialFocusRequester),
                )
                GalleryFilterChip(
                    label = stringResource(Res.string.color_palette_other_colors),
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
            }
            swatchesContent()
        }

        SettingsItem(item = SettingsEntity.Header(title = stringResource(Res.string.appearance_theme_header)))
        ThemeModePicker(
            selected = themeMode,
            amoled = amoledModeValue,
            onSelect = onThemeModeSelected,
        )
        Spacer(Modifier.height(8.dp))
        SettingsItem(item = amoledModePref)

        SettingsItem(item = effectsHeader)
        SettingsItem(item = allowBlurPref)
        SettingsItem(item = autoContrastPref)
        SettingsItem(item = sharedElementsPref)

        SettingsItem(item = fontHeader)
        SettingsItem(item = useSystemFontPref)

        if (state.mapsEnabled) {
            SettingsItem(item = SettingsEntity.Header(title = stringResource(Res.string.map_appearance_header)))
            SettingsItem(item = mapAppearancePref)
            if (state.showManualCartoKey) SettingsItem(item = cartoKeyPref)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.settings_appearance),
                hazeState = LocalHazeState.current,
                scrolled = rememberScrolledUnderTopBar(contentScrollState),
            )
        }
    ) { padding ->
        val containerSize = LocalWindowInfo.current.containerSize
        val isLandscape = containerSize.width > containerSize.height

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    previewCard(104.dp)
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .hazeSource(LocalHazeState.current)
                        .settingsFocusGroup()
                        .verticalScroll(contentScrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    content = controls,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(LocalHazeState.current)
                    .settingsFocusGroup()
                    .verticalScroll(contentScrollState)
                    .padding(padding)
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                previewCard(156.dp)
                controls()
            }
        }
    }

    if (state.showManualCartoKey && showCartoKeyDialog) {
        AlertDialog(
            onDismissRequest = {
                cartoKeyDraft = ""
                showCartoKeyDialog = false
            },
            title = { Text(stringResource(Res.string.carto_basemap_key_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(Res.string.carto_basemap_key_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = cartoKeyDraft,
                        onValueChange = { cartoKeyDraft = it },
                        label = { Text(stringResource(Res.string.carto_basemap_key_field)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        userCartoKey = cartoKeyDraft.trim()
                        cartoKeyDraft = ""
                        showCartoKeyDialog = false
                    }
                ) {
                    Text(stringResource(Res.string.save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        cartoKeyDraft = ""
                        showCartoKeyDialog = false
                    }
                ) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}

/**
 * The card at the top of Appearance: the Timeline home drawn in the current palette, with one
 * caption under it.
 */
@Composable
private fun ThemePreviewCard(phoneWidth: Dp) {
    val phoneShape = RoundedCornerShape(phoneWidth * (14f / 104f))
    Column(
        modifier = Modifier
            .settingsContentWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MiniHomeScreen(
            modifier = Modifier
                .width(phoneWidth)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, phoneShape)
                .clip(phoneShape)
        )
        Text(
            text = stringResource(Res.string.color_palette_preview_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Auto / Light / Dark tiles: Auto follows the system, the other two force a theme. */
@Composable
private fun ThemeModePicker(
    selected: ThemeMode,
    amoled: Boolean,
    onSelect: (ThemeMode) -> Unit,
) {
    PreferencePreviewStage {
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                PreferencePreviewTile(
                    selected = selected == mode,
                    label = stringResource(
                        when (mode) {
                            ThemeMode.Auto -> Res.string.auto
                            ThemeMode.Light -> Res.string.theme_mode_light
                            ThemeMode.Dark -> Res.string.theme_mode_dark
                        }
                    ),
                    onClick = { onSelect(mode) },
                    canvasHeight = 152.dp,
                    expandCanvas = true,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    when (mode) {
                        ThemeMode.Auto -> PreviewIconBadge(Icons.Outlined.BrightnessAuto)
                        ThemeMode.Light -> GalleryPreviewTheme(darkTheme = false) {
                            MiniHomeScreen(modifier = Modifier.width(78.dp))
                        }
                        ThemeMode.Dark -> GalleryPreviewTheme(darkTheme = true, amoledMode = amoled) {
                            MiniHomeScreen(modifier = Modifier.width(78.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemColorOption(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberSettingsFocusState()
    Box(
        modifier = modifier
            .settingsFocusTarget(focusState)
            .size(56.dp)
            .clip(CircleShape)
            .then(
                if (isSelected || focusState.hasFocus) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, CircleShape
                ) else Modifier
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Outlined.Check else Icons.Outlined.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ColorCircleItem(
    scheme: ColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val focusState = rememberSettingsFocusState()
    val ringWidth by animateDpAsState(
        targetValue = if (isSelected || focusState.hasFocus) 3.dp else 0.dp,
        label = "ringWidth"
    )
    val ringColor by animateColorAsState(
        targetValue = if (isSelected || focusState.hasFocus) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "ringColor"
    )
    val inset by animateDpAsState(if (isSelected) 7.dp else 4.dp, label = "swatchInset")

    Box(
        modifier = Modifier
            .settingsFocusTarget(focusState)
            .size(56.dp)
            .clip(CircleShape)
            .border(ringWidth, ringColor, CircleShape)
            .clickable(onClick = onClick)
            .padding(inset)
    ) {
        Column(modifier = Modifier.fillMaxSize().clip(CircleShape)) {
            Row(modifier = Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.primary))
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.secondary))
            }
            Row(modifier = Modifier.weight(1f)) {
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.tertiary))
                Box(Modifier.weight(1f).fillMaxHeight().background(scheme.primaryContainer))
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/** Dark grey against pure black: the same home screen in the user's dark palette. */
@Composable
private fun AmoledPreview(isChecked: Boolean) {
    GalleryPreviewTheme(darkTheme = true, amoledMode = isChecked) {
        MiniHomeScreen(modifier = Modifier.width(104.dp))
    }
}

/** The viewer with a photo over a soft blur of itself, or over plain black. */
@Composable
private fun BlurPreview(isChecked: Boolean) {
    Box(
        modifier = Modifier
            .width(104.dp)
            .height(184.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
    ) {
        if (isChecked) {
            PreviewPhoto(
                index = 3,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
        }
        PreviewPhoto(
            index = 3,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(68.dp)
        )
        val chrome = Color.White.copy(alpha = 0.6f)
        Box(
            Modifier.padding(start = 6.dp, top = 8.dp).size(18.dp).clip(CircleShape).background(chrome)
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 8.dp)
                .size(40.dp, 18.dp).clip(RoundedCornerShape(9.dp)).background(chrome)
        )
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                .size(76.dp, 20.dp).clip(RoundedCornerShape(10.dp)).background(chrome)
        )
    }
}
