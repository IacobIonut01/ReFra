package com.dot.gallery.feature_node.presentation.settings.subsettings

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dot.gallery.R
import com.dot.gallery.core.Position
import com.dot.gallery.core.Settings
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.settings.components.rememberSwitchPreference
import com.dot.gallery.ui.core.icons.Albums
import com.dot.gallery.ui.theme.colorSchemeFromSeed
import com.dot.gallery.ui.theme.isDarkTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteScreen() {
    var themeColorSeed by Settings.Misc.rememberThemeColorSeed()
    var forceTheme by Settings.Misc.rememberForceTheme()
    var darkModeValue by Settings.Misc.rememberIsDarkMode()
    var amoledModeValue by Settings.Misc.rememberIsAmoledMode()
    val isDark = isDarkTheme()

    val context = LocalContext.current
    var selectedTab by rememberSaveable(themeColorSeed) {
        mutableIntStateOf(
            if (themeColorSeed == Settings.Misc.THEME_SEED_SYSTEM) 0
            else if (presetPalettes.any { it.hexKey == themeColorSeed }) 1
            else 0
        )
    }

    // Extract wallpaper color variations from dynamic color scheme
    val wallpaperPalettes = remember(isDark) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dynamicScheme = if (isDark) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            listOf(
                dynamicScheme.primary,
                dynamicScheme.secondary,
                dynamicScheme.tertiary
            ).mapIndexed { index, color ->
                val argb = color.toArgb()
                val hex = String.format("%08X", argb)
                ColorPaletteOption(
                    name = when (index) {
                        0 -> "Primary"
                        1 -> "Secondary"
                        else -> "Tertiary"
                    },
                    seedArgb = argb,
                    hexKey = hex
                )
            }.distinctBy { it.hexKey }
        } else {
            emptyList()
        }
    }

    val previewScheme = remember(themeColorSeed, isDark, amoledModeValue) {
        if (themeColorSeed == Settings.Misc.THEME_SEED_SYSTEM) {
            null
        } else {
            val seedArgb = themeColorSeed.toLongOrNull(16)?.toInt() ?: return@remember null
            colorSchemeFromSeed(seedArgb, isDark, amoledModeValue)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_theme)
                    )
                },
                navigationIcon = {
                    NavigationBackButton()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        val orientation = LocalConfiguration.current.orientation
        val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

        // Theme switches (shared between layouts)
        val followSystemPref = rememberSwitchPreference(
            forceTheme,
            title = stringResource(R.string.settings_follow_system_theme_title),
            isChecked = !forceTheme,
            onCheck = { forceTheme = !it },
            screenPosition = Position.Top
        )
        val darkModePref = rememberSwitchPreference(
            darkModeValue, forceTheme,
            title = stringResource(R.string.settings_dark_mode_title),
            enabled = forceTheme,
            isChecked = darkModeValue,
            onCheck = { darkModeValue = it },
            screenPosition = Position.Middle
        )
        val amoledModePref = rememberSwitchPreference(
            amoledModeValue,
            title = stringResource(R.string.amoled_mode_title),
            summary = stringResource(R.string.amoled_mode_summary),
            isChecked = amoledModeValue,
            onCheck = { amoledModeValue = it },
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
                if (tab == 0) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "system") {
                            SystemColorOption(
                                isSelected = themeColorSeed == Settings.Misc.THEME_SEED_SYSTEM,
                                onClick = { themeColorSeed = Settings.Misc.THEME_SEED_SYSTEM }
                            )
                        }
                        items(wallpaperPalettes, key = { it.hexKey }) { palette ->
                            val isSelected = themeColorSeed == palette.hexKey
                            val scheme = remember(palette.seedArgb, isDark, amoledModeValue) {
                                colorSchemeFromSeed(palette.seedArgb, isDark, amoledModeValue)
                            }
                            ColorCircleItem(
                                scheme = scheme,
                                isSelected = isSelected,
                                onClick = { themeColorSeed = palette.hexKey }
                            )
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(presetPalettes, key = { it.hexKey }) { palette ->
                            val isSelected = themeColorSeed == palette.hexKey
                            val scheme = remember(palette.seedArgb, isDark, amoledModeValue) {
                                colorSchemeFromSeed(palette.seedArgb, isDark, amoledModeValue)
                            }
                            ColorCircleItem(
                                scheme = scheme,
                                isSelected = isSelected,
                                onClick = { themeColorSeed = palette.hexKey }
                            )
                        }
                    }
                }
            }
        }

        val tabsContent: @Composable () -> Unit = {
            Surface(
                modifier = Modifier.padding(horizontal = 24.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PillTab(
                        selected = selectedTab == 0,
                        text = stringResource(R.string.color_palette_wallpaper_colors),
                        onClick = { selectedTab = 0 }
                    )
                    PillTab(
                        selected = selectedTab == 1,
                        text = stringResource(R.string.color_palette_other_colors),
                        onClick = { selectedTab = 1 }
                    )
                }
            }
        }

        if (isLandscape) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Left: Phone preview
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PhonePreview(
                        colorScheme = previewScheme,
                        isLandscape = true,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(2f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Right: Controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.color_palette_preview_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    swatchesContent()

                    Spacer(modifier = Modifier.height(16.dp))

                    tabsContent()

                    Spacer(modifier = Modifier.height(24.dp))

                    SettingsItem(item = followSystemPref)
                    SettingsItem(item = darkModePref)
                    SettingsItem(item = amoledModePref)

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                PhonePreview(
                    colorScheme = previewScheme,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .aspectRatio(0.5f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.color_palette_preview_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                swatchesContent()

                Spacer(modifier = Modifier.height(16.dp))

                tabsContent()

                Spacer(modifier = Modifier.height(24.dp))

                SettingsItem(item = followSystemPref)
                SettingsItem(item = darkModePref)
                SettingsItem(item = amoledModePref)

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun SystemColorOption(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) Modifier.border(
                    3.dp, MaterialTheme.colorScheme.primary, CircleShape
                ) else Modifier
            )
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ColorCircleItem(
    scheme: ColorScheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 3.dp else 0.dp,
        label = "borderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) scheme.primary else Color.Transparent,
        label = "borderColor"
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .border(borderWidth, borderColor, CircleShape)
            .padding(borderWidth)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.matchParentSize()) {
            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(scheme.primary)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(scheme.secondary)
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(scheme.tertiary)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(scheme.primaryContainer)
                )
            }
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun PhonePreview(
    colorScheme: ColorScheme?,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = false
) {
    val currentScheme = MaterialTheme.colorScheme
    val scheme = colorScheme ?: currentScheme

    MaterialTheme(colorScheme = scheme) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(if (isLandscape) 20.dp else 28.dp),
            color = MaterialTheme.colorScheme.background,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            if (isLandscape) {
                LandscapePreviewContent()
            } else {
                PortraitPreviewContent()
            }
        }
    }
}

@Composable
private fun PortraitPreviewContent() {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Status bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "12:30",
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            Box(
                modifier = Modifier
                    .size(width = 10.dp, height = 6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            )
        }

        // Search bar row with favorites + settings buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Search bar
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = stringResource(R.string.search),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Favorites button
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryFixed
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryFixed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            // Settings button
            Surface(
                modifier = Modifier.size(32.dp),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.tertiaryFixed
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryFixed,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // "Today" header
        Text(
            text = "Today",
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )

        // Photo grid (4 columns)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    repeat(4) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom GalleryNavBar
        Surface(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .height(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Timeline (selected)
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(12.dp)
                    )
                }
                // Albums
                Icon(
                    imageVector = com.dot.gallery.ui.core.Icons.Albums,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
                // Library
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Home indicator
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(42.dp)
                .height(2.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun LandscapePreviewContent() {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val h = maxHeight
        val w = maxWidth

        val fontSize = (h.value * 0.022f).sp
        val searchFontSize = (h.value * 0.018f).sp
        val btnSize = h * 0.065f
        val iconSize = h * 0.035f
        val sidePad = w * 0.03f
        val navBarH = h * 0.075f
        val navPillW = h * 0.09f
        val navPillH = h * 0.045f
        val navIconSize = h * 0.04f
        val navSpacing = w * 0.018f

        Column(modifier = Modifier.fillMaxSize()) {
            // Status bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = sidePad, end = sidePad, top = h * 0.04f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "12:30",
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .requiredSize(width = h * 0.04f, height = h * 0.02f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                )
            }

            // Search bar + buttons at top right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = h * 0.02f, end = sidePad),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .requiredWidth(w * 0.09f)
                        .requiredHeight(btnSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = w * 0.008f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.requiredSize(iconSize)
                        )
                        Spacer(modifier = Modifier.requiredWidth(w * 0.004f))
                        Text(
                            text = stringResource(R.string.search),
                            fontSize = searchFontSize,
                            lineHeight = searchFontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.requiredWidth(w * 0.008f))
                Surface(
                    modifier = Modifier.requiredSize(btnSize),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryFixed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryFixed,
                            modifier = Modifier.requiredSize(iconSize)
                        )
                    }
                }
                Spacer(modifier = Modifier.requiredWidth(w * 0.008f))
                Surface(
                    modifier = Modifier.requiredSize(btnSize),
                    shape = RoundedCornerShape(h * 0.015f),
                    color = MaterialTheme.colorScheme.tertiaryFixed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryFixed,
                            modifier = Modifier.requiredSize(iconSize)
                        )
                    }
                }
            }

            // "Today" header
            Text(
                text = "Today",
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    start = sidePad,
                    top = h * 0.03f,
                    bottom = h * 0.02f
                )
            )

            // Photo grid (square cells) with floating nav bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = w * 0.008f)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    repeat(3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            repeat(8) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                )
                            }
                        }
                    }
                }

                // GalleryNavBar floating at bottom right
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = sidePad, bottom = h * 0.02f)
                        .requiredHeight(navBarH),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = navSpacing),
                        horizontalArrangement = Arrangement.spacedBy(navSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Timeline (selected)
                        Box(
                            modifier = Modifier
                                .requiredWidth(navPillW)
                                .requiredHeight(navPillH)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Photo,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.requiredSize(navIconSize)
                            )
                        }
                        // Albums
                        Icon(
                            imageVector = com.dot.gallery.ui.core.Icons.Albums,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.requiredSize(navIconSize)
                        )
                        // Library
                        Icon(
                            imageVector = Icons.Outlined.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.requiredSize(navIconSize)
                        )
                    }
                }
            }

            // Home indicator
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = h * 0.015f)
                    .requiredWidth(w * 0.12f)
                    .requiredHeight(h * 0.008f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun PillTab(
    selected: Boolean,
    text: String,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "pillTabBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "pillTabText"
    )
    Surface(
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
        shape = CircleShape,
        color = backgroundColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
    }
}
