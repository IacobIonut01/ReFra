package com.dot.gallery.feature_node.presentation.dateformat

import android.content.res.Configuration
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composeunstyled.LocalTextStyle
import com.dot.gallery.R
import com.dot.gallery.core.DefaultEventHandler
import com.dot.gallery.core.LocalEventHandler
import com.dot.gallery.core.Settings.Misc.rememberAllowBlur
import com.dot.gallery.core.Settings.Misc.rememberGridSize
import com.dot.gallery.core.Settings.Misc.rememberRawDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberRawDefaultDateFormat
import com.dot.gallery.core.Settings.Misc.rememberRawExifDateFormat
import com.dot.gallery.core.Settings.Misc.rememberRawExtendedDateFormat
import com.dot.gallery.core.Settings.Misc.rememberRawExtendedDateHeaderFormat
import com.dot.gallery.core.Settings.Misc.rememberRawWeeklyDateFormat
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.NavigationBackButton
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.feature_node.presentation.util.SystemDateFormatField
import com.dot.gallery.feature_node.presentation.util.getDate
import com.dot.gallery.feature_node.presentation.util.systemDateTimePattern
import com.dot.gallery.ui.theme.GalleryTheme
import com.dot.gallery.feature_node.presentation.util.hazeEffectScaled
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.materials.HazeMaterials
import java.text.SimpleDateFormat
import java.util.Locale

internal fun dateFormatEditorText(rawFormat: String): String = rawFormat

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class
)
@Composable
fun DateFormatScreen() {
    val context = LocalContext.current
    // Raw stored patterns: blank means "follow the system" (see #953).
    var rawDateHeaderFormat by rememberRawDateHeaderFormat()
    var rawExtendedDateHeaderFormat by rememberRawExtendedDateHeaderFormat()
    var rawExifDateFormat by rememberRawExifDateFormat()
    var rawDefaultDateFormat by rememberRawDefaultDateFormat()
    var rawExtendedDateFormat by rememberRawExtendedDateFormat()
    var rawWeeklyDateFormat by rememberRawWeeklyDateFormat()

    // Effective patterns actually rendered (raw override, else system-derived).
    LocalConfiguration.current // recompose on locale / 24h change
    val dateHeaderFormat = rawDateHeaderFormat.ifBlank {
        systemDateTimePattern(context, SystemDateFormatField.HEADER)
    }
    val extendedDateHeaderFormat = rawExtendedDateHeaderFormat.ifBlank {
        systemDateTimePattern(context, SystemDateFormatField.EXTENDED_HEADER)
    }
    val exifDateFormat = rawExifDateFormat.ifBlank {
        systemDateTimePattern(context, SystemDateFormatField.EXIF)
    }
    val defaultDateFormat = rawDefaultDateFormat.ifBlank {
        systemDateTimePattern(context, SystemDateFormatField.DEFAULT)
    }
    val extendedDateFormat = rawExtendedDateFormat.ifBlank {
        systemDateTimePattern(context, SystemDateFormatField.EXTENDED)
    }
    val weeklyDateFormat = rawWeeklyDateFormat.ifBlank {
        systemDateTimePattern(context, SystemDateFormatField.WEEKLY)
    }

    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        DateFormatInfoSheet(onDismiss = { showInfo = false })
    }

    val currentMillis = remember { System.currentTimeMillis() / 1000 }
    val textStyle = LocalTextStyle.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.date_format_settings))
                },
                navigationIcon = {
                    NavigationBackButton()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        FlowRow(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            DateFormatPreview(
                modifier = Modifier,
                title = stringResource(R.string.date_header_title),
                location = stringResource(R.string.media_view),
                dateFormat = dateHeaderFormat,
                rawFormat = rawDateHeaderFormat,
                onDateFormatChange = { rawDateHeaderFormat = it },
                onInfoClick = { showInfo = true },
            ) { dateFormat ->
                Image(
                    painter = painterResource(R.drawable.image_sample_2),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(32.dp)
                        .hazeSource(LocalHazeState.current),
                    contentScale = ContentScale.Crop
                )
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
                    val allowBlur by rememberAllowBlur()
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
                            contentDescription = stringResource(R.string.back_cd),
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
                            contentDescription = stringResource(R.string.date_format_info_action),
                            tint = contentColor,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }
            }

            DateFormatPreview(
                modifier = Modifier,
                title = stringResource(R.string.extended_date_header_title),
                location = stringResource(R.string.media_view),
                dateFormat = extendedDateHeaderFormat,
                rawFormat = rawExtendedDateHeaderFormat,
                onDateFormatChange = { rawExtendedDateHeaderFormat = it },
                onInfoClick = { showInfo = true },
            ) { dateFormat ->
                Image(
                    painter = painterResource(R.drawable.image_sample_2),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(32.dp)
                        .hazeSource(LocalHazeState.current),
                    contentScale = ContentScale.Crop
                )
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
                    val allowBlur by rememberAllowBlur()
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
                            contentDescription = stringResource(R.string.back_cd),
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
                            contentDescription = stringResource(R.string.date_format_info_action),
                            tint = contentColor,
                            modifier = Modifier.height(48.dp)
                        )
                    }
                }
            }

            DateFormatPreview(
                title = stringResource(R.string.exif_date),
                location = stringResource(R.string.media_info),
                dateFormat = exifDateFormat,
                rawFormat = rawExifDateFormat,
                onDateFormatChange = { rawExifDateFormat = it },
                onInfoClick = { showInfo = true },
            ) { dateFormat ->
                Image(
                    painter = painterResource(R.drawable.image_sample_2),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(32.dp),
                    contentScale = ContentScale.Crop
                )
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
                        text = stringResource(R.string.image_add_description),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start
                    )
                }
            }

            DateFormatPreview(
                title = stringResource(R.string.weekly_date),
                location = stringResource(R.string.media_grid),
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
                    val gridColumns by rememberGridSize()
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
                title = stringResource(R.string.classic_date),
                location = stringResource(R.string.media_grid),
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
                    val gridColumns by rememberGridSize()
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
                title = stringResource(R.string.extended_date),
                location = stringResource(R.string.media_grid),
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
                    val gridColumns by rememberGridSize()
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

@Composable
fun DateFormatPreview(
    modifier: Modifier = Modifier,
    title: String,
    location: String,
    dateFormat: String,
    rawFormat: String,
    onDateFormatChange: (String) -> Unit,
    onInfoClick: () -> Unit,
    dateFormatPreview: @Composable() (BoxScope.(String) -> Unit),
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        modifier = modifier
            .fillMaxWidth(if (isLandscape) 0.5f else 1f)
            .then(
                if (isLandscape) Modifier.padding(8.dp) else Modifier
            )
    ) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            headlineContent = {
                Text(title)
            },
            overlineContent = {
                Text(location)
            },
            supportingContent = {
                if (rawFormat.isBlank()) {
                    Text(stringResource(R.string.date_format_following_system))
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onInfoClick) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.date_format_info_action)
                        )
                    }
                    TextButton(
                        onClick = { onDateFormatChange("") },
                        enabled = rawFormat.isNotBlank()
                    ) {
                        Text(stringResource(R.string.reset))
                    }
                }
            }
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 128.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomEnd = 2.dp,
                        bottomStart = 2.dp
                    )
                )
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
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                textFieldValue = newValue
                try {
                    SimpleDateFormat(newValue.text, Locale.getDefault())
                    onDateFormatChange(newValue.text)
                    isDateHeaderFormatError = false
                } catch (_: IllegalArgumentException) {
                    isDateHeaderFormatError = true
                }
            },
            placeholder = {
                Text(
                    text = dateFormat,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            shape = RoundedCornerShape(
                topStart = 2.dp,
                topEnd = 2.dp,
                bottomEnd = 16.dp,
                bottomStart = 16.dp
            ),
            isError = isDateHeaderFormatError,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                errorBorderColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        )
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL,
    device = "spec:parent=pixel_6"
)
@Composable
private fun Preview() {
    GalleryTheme {
        CompositionLocalProvider(LocalEventHandler provides DefaultEventHandler()) {
            DateFormatScreen()
        }
    }
}