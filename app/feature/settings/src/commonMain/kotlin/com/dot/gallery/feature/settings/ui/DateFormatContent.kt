package com.dot.gallery.feature.settings.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import com.dot.gallery.core.Position
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.sheetGroupedShape
import com.dot.gallery.core.presentation.util.LocalHazeState
import com.dot.gallery.domain.date.SystemDateFormatField
import com.dot.gallery.domain.date.getDate
import com.dot.gallery.core.ui.components.PreviewHost
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.core.ui.util.supportsBlur
import com.dot.gallery.domain.date.isValidDatePattern
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.dot.gallery.domain.settings.SettingsKeys
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalWindowInfo

import com.dot.gallery.core.presentation.util.hazeEffectScaled
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import com.dot.gallery.core.ui.util.rememberedDerivedState
import com.dot.gallery.feature.settings.generated.resources.Res
import com.dot.gallery.feature.settings.generated.resources.back_cd
import com.dot.gallery.feature.settings.generated.resources.classic_date
import com.dot.gallery.feature.settings.generated.resources.date_format_following_system
import com.dot.gallery.feature.settings.generated.resources.date_format_info_action
import com.dot.gallery.feature.settings.generated.resources.date_format_settings
import com.dot.gallery.feature.settings.generated.resources.date_header_title
import com.dot.gallery.feature.settings.generated.resources.exif_date
import com.dot.gallery.feature.settings.generated.resources.extended_date
import com.dot.gallery.feature.settings.generated.resources.extended_date_header_title
import com.dot.gallery.feature.settings.generated.resources.image_add_description
import com.dot.gallery.feature.settings.generated.resources.media_grid
import com.dot.gallery.feature.settings.generated.resources.media_info
import com.dot.gallery.feature.settings.generated.resources.media_view
import com.dot.gallery.feature.settings.generated.resources.reset
import com.dot.gallery.feature.settings.generated.resources.weekly_date
import com.dot.gallery.core.presentation.components.GalleryPageTopBar
import com.dot.gallery.core.presentation.components.rememberScrolledUnderTopBar
import dev.chrisbanes.haze.rememberHazeState



fun dateFormatEditorText(rawFormat: String): String = rawFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalTime::class)
@Composable
fun DateFormatContent(
    resolveSystemPattern: (SystemDateFormatField) -> String,
    gridColumns: Int,
    previewImage: @Composable BoxScope.() -> Unit,
) {
    // Raw stored patterns: blank means "follow the system" (see ).
    var rawDateHeaderFormat by rememberPreference(SettingsKeys.rawDateHeaderFormat)
    var rawExtendedDateHeaderFormat by rememberPreference(SettingsKeys.rawExtendedDateHeaderFormat)
    var rawExifDateFormat by rememberPreference(SettingsKeys.rawExifDateFormat)
    var rawDefaultDateFormat by rememberPreference(SettingsKeys.rawDefaultDateFormat)
    var rawExtendedDateFormat by rememberPreference(SettingsKeys.rawExtendedDateFormat)
    var rawWeeklyDateFormat by rememberPreference(SettingsKeys.rawWeeklyDateFormat)

    // Effective patterns actually rendered (raw override, else system-derived).
    val dateHeaderFormat = rawDateHeaderFormat.ifBlank {
        resolveSystemPattern(SystemDateFormatField.HEADER)
    }
    val extendedDateHeaderFormat = rawExtendedDateHeaderFormat.ifBlank {
        resolveSystemPattern(SystemDateFormatField.EXTENDED_HEADER)
    }
    val exifDateFormat = rawExifDateFormat.ifBlank {
        resolveSystemPattern(SystemDateFormatField.EXIF)
    }
    val defaultDateFormat = rawDefaultDateFormat.ifBlank {
        resolveSystemPattern(SystemDateFormatField.DEFAULT)
    }
    val extendedDateFormat = rawExtendedDateFormat.ifBlank {
        resolveSystemPattern(SystemDateFormatField.EXTENDED)
    }
    val weeklyDateFormat = rawWeeklyDateFormat.ifBlank {
        resolveSystemPattern(SystemDateFormatField.WEEKLY)
    }

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        DateFormatInfoSheet(onDismiss = { showInfo = false })
    }

    val currentMillis = remember { Clock.System.now().toEpochMilliseconds() / 1000 }
    val textStyle = LocalTextStyle.current

    // The previews below blur LocalHazeState themselves, so the bar gets a state of its own
    // rather than making the scrolling column a source of the state its children draw from.
    val barHazeState = rememberHazeState()
    val scrollState = rememberScrollState()
    Scaffold(
        topBar = {
            GalleryPageTopBar(
                title = stringResource(Res.string.date_format_settings),
                hazeState = barHazeState,
                scrolled = rememberScrolledUnderTopBar(scrollState),
            )
        }
    ) { innerPadding ->
        FlowRow(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(barHazeState)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            DateFormatSectionLabel(stringResource(Res.string.media_view))
            DateFormatPreview(
                modifier = Modifier,
                title = stringResource(Res.string.date_header_title),
                dateFormat = dateHeaderFormat,
                rawFormat = rawDateHeaderFormat,
                onDateFormatChange = { rawDateHeaderFormat = it },
                onInfoClick = { showInfo = true },
            ) { dateFormat ->
                previewImage()
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.Black.copy(alpha = 0.1f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val allowBlur by rememberPreference(SettingsKeys.allowBlur.withDefault(supportsBlur))
                    val followTheme = remember(allowBlur) { !allowBlur }
                    val contentColor by animateColorAsState(
                        targetValue = if (followTheme) MaterialTheme.colorScheme.onSurface else Color.White,
                        label = "AppBarContentColor"
                    )
                    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer.copy(0.5f)
                    val backgroundModifier = remember(allowBlur) {
                        if (!allowBlur) {
                            Modifier.background(
                                color = surfaceContainer,
                                shape = CircleShape
                            )
                        } else Modifier
                    }
                    val currentDate by rememberedDerivedState(
                        currentMillis,
                        dateHeaderFormat
                    ) {
                        buildAnnotatedString {
                            val date = currentMillis.getDate(dateHeaderFormat)
                            if (date.isNotEmpty()) {
                                val top = date.substringBefore("\n")
                                val bottom = date.substringAfter("\n")
                                withStyle(
                                    style = textStyle.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ).toSpanStyle()
                                ) {
                                    appendLine(top)
                                }
                                withStyle(
                                    style = textStyle.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp
                                    ).toSpanStyle()
                                ) {
                                    append(bottom)
                                }
                            }
                        }
                    }
                    IconButton(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(CircleShape)
                            .then(backgroundModifier)
                            .hazeEffectScaled(
                                state = LocalHazeState.current,
                                style = HazeMaterials.ultraThin(
                                    containerColor = surfaceContainer
                                )
                            ),
                        onClick = {  }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.back_cd),
                            tint = contentColor,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                    Text(
                        text = currentDate,
                        modifier = Modifier,
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(CircleShape)
                            .then(backgroundModifier)
                            .hazeEffectScaled(
                                state = LocalHazeState.current,
                                style = HazeMaterials.ultraThin(
                                    containerColor = surfaceContainer
                                )
                            ),
                        onClick = { showInfo = true }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(Res.string.date_format_info_action),
                            tint = contentColor,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }
            }

            DateFormatPreview(
                modifier = Modifier,
                title = stringResource(Res.string.extended_date_header_title),
                dateFormat = extendedDateHeaderFormat,
                rawFormat = rawExtendedDateHeaderFormat,
                onDateFormatChange = { rawExtendedDateHeaderFormat = it },
                onInfoClick = { showInfo = true },
            ) { dateFormat ->
                previewImage()
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.Black.copy(alpha = 0.1f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val allowBlur by rememberPreference(SettingsKeys.allowBlur.withDefault(supportsBlur))
                    val followTheme = remember(allowBlur) { !allowBlur }
                    val contentColor by animateColorAsState(
                        targetValue = if (followTheme) MaterialTheme.colorScheme.onSurface else Color.White,
                        label = "AppBarContentColor"
                    )
                    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer.copy(0.5f)
                    val backgroundModifier = remember(allowBlur) {
                        if (!allowBlur) {
                            Modifier.background(
                                color = surfaceContainer,
                                shape = CircleShape
                            )
                        } else Modifier
                    }
                    val currentDate by rememberedDerivedState(
                        currentMillis,
                        extendedDateHeaderFormat
                    ) {
                        buildAnnotatedString {
                            val date = currentMillis.getDate(extendedDateHeaderFormat)
                            if (date.isNotEmpty()) {
                                val top = date.substringBefore("\n")
                                val bottom = date.substringAfter("\n")
                                withStyle(
                                    style = textStyle.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ).toSpanStyle()
                                ) {
                                    appendLine(top)
                                }
                                withStyle(
                                    style = textStyle.copy(
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp
                                    ).toSpanStyle()
                                ) {
                                    append(bottom)
                                }
                            }
                        }
                    }
                    IconButton(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(CircleShape)
                            .then(backgroundModifier)
                            .hazeEffectScaled(
                                state = LocalHazeState.current,
                                style = HazeMaterials.ultraThin(
                                    containerColor = surfaceContainer
                                )
                            ),
                        onClick = {  }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(Res.string.back_cd),
                            tint = contentColor,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                    Text(
                        text = currentDate,
                        modifier = Modifier,
                        style = MaterialTheme.typography.titleSmall,
                        color = contentColor,
                        textAlign = TextAlign.Center
                    )
                    IconButton(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clip(CircleShape)
                            .then(backgroundModifier)
                            .hazeEffectScaled(
                                state = LocalHazeState.current,
                                style = HazeMaterials.ultraThin(
                                    containerColor = surfaceContainer
                                )
                            ),
                        onClick = { showInfo = true }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(Res.string.date_format_info_action),
                            tint = contentColor,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }
            }

            DateFormatSectionLabel(stringResource(Res.string.media_info))
            DateFormatPreview(
                title = stringResource(Res.string.exif_date),
                dateFormat = exifDateFormat,
                rawFormat = rawExifDateFormat,
                onDateFormatChange = { rawExifDateFormat = it },
                onInfoClick = { showInfo = true },
            ) { dateFormat ->
                previewImage()
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.Black.copy(alpha = 0.1f))
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp)
                        .padding(horizontal = 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                        )
                        .align(Alignment.Center)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DragHandle()
                    Text(
                        text = currentMillis.getDate(dateFormat),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                        fontSize = 18.sp
                    )
                    Text(
                        text = stringResource(Res.string.image_add_description),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                }
            }

            DateFormatSectionLabel(stringResource(Res.string.media_grid))
            DateFormatPreview(
                title = stringResource(Res.string.weekly_date),
                dateFormat = weeklyDateFormat,
                rawFormat = rawWeeklyDateFormat,
                onDateFormatChange = { rawWeeklyDateFormat = it },
                onInfoClick = { showInfo = true },
            ) {
                Column {
                    Text(
                        text = currentMillis.getDate(weeklyDateFormat),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        // 3.dp is the elevation the LargeTopAppBar use
                                        MaterialTheme.colorScheme.surfaceColorAtElevation(
                                            3.dp
                                        ),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 24.dp)
                            .fillMaxWidth()
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(gridColumns / 2) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        }
                    }
                }
            }

            DateFormatPreview(
                title = stringResource(Res.string.classic_date),
                dateFormat = defaultDateFormat,
                rawFormat = rawDefaultDateFormat,
                onDateFormatChange = { rawDefaultDateFormat = it },
                onInfoClick = { showInfo = true },
            ) {
                Column {
                    Text(
                        text = currentMillis.getDate(defaultDateFormat),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        // 3.dp is the elevation the LargeTopAppBar use
                                        MaterialTheme.colorScheme.surfaceColorAtElevation(
                                            3.dp
                                        ),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 24.dp)
                            .fillMaxWidth()
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(gridColumns) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        }
                    }
                }
            }

            DateFormatPreview(
                title = stringResource(Res.string.extended_date),
                dateFormat = extendedDateFormat,
                rawFormat = rawExtendedDateFormat,
                onDateFormatChange = { rawExtendedDateFormat = it },
                onInfoClick = { showInfo = true },
            ) {
                Column {
                    Text(
                        text = currentMillis.getDate(extendedDateFormat),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        // 3.dp is the elevation the LargeTopAppBar use
                                        MaterialTheme.colorScheme.surfaceColorAtElevation(
                                            3.dp
                                        ),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 24.dp)
                            .fillMaxWidth()
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(gridColumns) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(128.dp))
        }
    }

}

/** Section label above a group of preview cards, as on the other settings pages. */
@Composable
private fun DateFormatSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .semantics { heading() }
    )
}

/**
 * One date format as a grouped card: a header row with the info and reset actions, the live
 * preview, then the pattern field, joined with the 24/8 dp grouped corners.
 */
@Composable
fun DateFormatPreview(
    modifier: Modifier = Modifier,
    title: String,
    dateFormat: String,
    rawFormat: String,
    onDateFormatChange: (String) -> Unit,
    onInfoClick: () -> Unit,
    dateFormatPreview: @Composable() (BoxScope.(String) -> Unit),
) {
    val containerSize = LocalWindowInfo.current.containerSize
    val isLandscape = containerSize.width > containerSize.height
    Column(
        modifier = modifier
            .fillMaxWidth(if (isLandscape) 0.5f else 1f)
            .padding(bottom = 16.dp)
            .then(
                if (isLandscape) Modifier.padding(horizontal = 8.dp) else Modifier
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sheetGroupedShape(Position.Top))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (rawFormat.isBlank()) {
                    Text(
                        text = stringResource(Res.string.date_format_following_system),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = stringResource(Res.string.date_format_info_action),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(
                onClick = { onDateFormatChange("") },
                enabled = rawFormat.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Outlined.RestartAlt,
                    contentDescription = stringResource(Res.string.reset)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 128.dp)
                .clip(sheetGroupedShape(Position.Middle))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            dateFormatPreview(dateFormat)
        }
        var isDateHeaderFormatError by rememberSaveable {
            mutableStateOf(false)
        }
        var textFieldValue by remember {
            mutableStateOf(TextFieldValue(dateFormatEditorText(rawFormat)))
        }
        LaunchedEffect(rawFormat) {
            val editorText = dateFormatEditorText(rawFormat)
            if (editorText != textFieldValue.text) {
                textFieldValue = TextFieldValue(
                    text = editorText,
                    selection = TextRange(editorText.length)
                )
            }
        }
        TextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                if (isValidDatePattern(newValue.text)) {
                    onDateFormatChange(newValue.text)
                    isDateHeaderFormatError = false
                } else {
                    isDateHeaderFormatError = true
                }
            },
            placeholder = {
                Text(
                    text = dateFormat,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = sheetGroupedShape(Position.Bottom),
            isError = isDateHeaderFormatError,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            )
        )
    }
}

@Preview
@Composable
private fun DateFormatContentPreview() {
    PreviewHost {
        DateFormatContent(
            resolveSystemPattern = { field -> field.name },
            gridColumns = 4,
            previewImage = {},
        )
    }
}
