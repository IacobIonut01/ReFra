package com.dot.gallery.feature_node.presentation.settings.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.dot.gallery.core.Position
import com.dot.gallery.core.PreferenceType
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.util.PreviewHost
import com.dot.gallery.feature_node.presentation.util.maybeApply
import com.dot.gallery.ui.core.Icons as GalleryIcons
import com.dot.gallery.ui.core.icons.RegularExpression
import com.github.panpf.sketch.AsyncImage
import kotlin.math.roundToLong

/**
 * A single preference item, which can be of type default, switch or seek.
 * @param item The preference item data.
 * @param modifier The modifier to be applied to the item.
 * @param tintIcon Whether to tint the icon with the current content color.
 * @param slimLayout Whether to use a more compact layout.
 * @param backgroundColor The background color of the item.
 * @param customIcon A custom composable to display the icon.
 */
@Composable
fun SettingsItem(
    item: SettingsEntity,
    modifier: Modifier = Modifier,
    tintIcon: Boolean = true,
    slimLayout: Boolean = false,
    applyPaddings: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderModifier: @Composable (Shape) -> Modifier = { Modifier },
    customIcon: (@Composable (icon: ImageVector?, iconUri: String?, iconRes: Int?) -> Unit)? = null
) {
    val mutableInteractionSource = remember {
        MutableInteractionSource()
    }
    var checked by remember(item.isChecked) {
        mutableStateOf(item.isChecked == true)
    }

    @Composable
    fun summaryContent() {
        val summaryText = item.summaryAnnotated ?: item.summary?.let { AnnotatedString(it) }
        summaryText?.let {
            Text(
                modifier = Modifier.maybeApply(
                    condition = !item.horizontalLayout,
                    modifier = Modifier.padding(top = 2.dp)
                ),
                text = it,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    fun rightTextContent() {
        val rightTextValue = item.rightTextAnnotated ?: item.rightText?.let { AnnotatedString(it) }
        rightTextValue?.let {
            Text(
                text = it,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun iconComponent() {
        val imageVector = item.icon
        val imageUri = item.iconUri
        val imageRes = item.iconRes
        if (imageUri != null) {
            AsyncImage(
                uri = imageUri,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(color = Color.White)
                    .padding(4.dp),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = if (tintIcon) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
            )
        } else if (imageVector != null) {
            Image(
                imageVector = imageVector,
                modifier = Modifier.size(24.dp),
                contentDescription = null,
                colorFilter = if (tintIcon) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
            )
        } else if (imageRes != null) {
            Image(
                painter = painterResource(imageRes),
                modifier = Modifier.size(24.dp),
                contentDescription = null,
                colorFilter = if (tintIcon) ColorFilter.tint(MaterialTheme.colorScheme.onSurface) else null
            )
        }
    }

    val icon: @Composable () -> Unit = {
        require(item.icon != null || item.iconUri != null || item.iconRes != null) { "Icon at this stage cannot be null" }
        customIcon?.let {
            customIcon(item.icon, item.iconUri, item.iconRes)
        } ?: iconComponent()
    }
    val summary: @Composable () -> Unit = {
        require(!item.summary.isNullOrEmpty() || item.summaryAnnotated != null) { "Summary at this stage cannot be null or empty" }
        summaryContent()
    }
    val switch: @Composable () -> Unit = {
        Switch(
            modifier = Modifier.padding(start = 16.dp),
            checked = checked,
            onCheckedChange = { isChecked ->
                item.onCheck?.let {
                    checked = isChecked
                    it(isChecked)
                }
            },
        )
    }

    val isPressed = mutableInteractionSource.collectIsPressedAsState()
    val isFocused = mutableInteractionSource.collectIsFocusedAsState()
    val isDragged = mutableInteractionSource.collectIsDraggedAsState()
    val isHovered = mutableInteractionSource.collectIsHoveredAsState()
    val isInteracting by rememberedDerivedState {
        isPressed.value || isFocused.value || isDragged.value || isHovered.value
    }
    val fullCornerRadius by animateDpAsState(
        targetValue = if (isInteracting) 48.dp else 24.dp,
        label = "fullCornerRadius"
    )
    val normalCornerRadius by animateDpAsState(
        targetValue = if (isInteracting) 48.dp else 8.dp,
        label = "normalCornerRadius"
    )

    val shape by rememberedDerivedState(item.screenPosition, fullCornerRadius, normalCornerRadius) {
        when (item.screenPosition) {
            Position.Alone -> RoundedCornerShape(fullCornerRadius)
            Position.Bottom -> RoundedCornerShape(
                topStart = normalCornerRadius,
                topEnd = normalCornerRadius,
                bottomStart = fullCornerRadius,
                bottomEnd = fullCornerRadius
            )

            Position.Middle -> RoundedCornerShape(
                topStart = normalCornerRadius,
                topEnd = normalCornerRadius,
                bottomStart = normalCornerRadius,
                bottomEnd = normalCornerRadius
            )

            Position.Top -> RoundedCornerShape(
                topStart = fullCornerRadius,
                topEnd = fullCornerRadius,
                bottomStart = normalCornerRadius,
                bottomEnd = normalCornerRadius
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
        PreferenceType.Default, PreferenceType.Switch, PreferenceType.Seek ->
            if (!item.summary.isNullOrEmpty() || item.summaryAnnotated != null) summary else null

        else -> null
    }

    val rightTextTrailing: @Composable () -> Unit = {
        if (item.rightText != null || item.rightTextAnnotated != null) {
            rightTextContent()
        }
    }

    val trailingContent: (@Composable () -> Unit)? = when (item.type) {
        PreferenceType.Switch -> switch
        PreferenceType.Seek -> seekTrailing
        else -> {
            if (item.rightText.isNullOrEmpty() && item.rightTextAnnotated == null) null
            else rightTextTrailing
        }
    }
    val clickableModifier =
        if (item.type != PreferenceType.Seek && !item.isHeader && (item.onClick != null || item.onCheck != null))
            Modifier.clickable(
                enabled = item.enabled,
                interactionSource = mutableInteractionSource,
                indication = LocalIndication.current,
                onClick = {
                    if (item.type == PreferenceType.Switch) {
                        item.onCheck?.let {
                            checked = !checked
                            it(checked)
                        }
                    } else item.onClick?.invoke()
                },
            )
        else Modifier
    if (item.isHeader) {
        if (item.titleAnnotated != null) {
            Text(
                text = item.titleAnnotated!!,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 8.dp)
                    .padding(bottom = 8.dp)
            )
        } else {
            Text(
                text = item.title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 8.dp)
                    .padding(bottom = 8.dp)
            )
        }
    } else {
        val alpha by animateFloatAsState(
            targetValue = if (item.enabled) 1f else 0.4f,
            label = "alpha"
        )
        Column(
            modifier = modifier
                .then(paddingModifier)
                .maybeApply(
                    condition = applyPaddings,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                .clip(shape)
                .background(
                    color = backgroundColor
                )
                .then(borderModifier(shape))
                .then(clickableModifier)
                .padding(horizontal = 8.dp)
                .then(if (!slimLayout) Modifier.padding(vertical = 4.dp) else Modifier)
                .fillMaxWidth()
                .alpha(alpha)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.icon != null || item.iconUri != null || item.iconRes != null) {
                    Box(
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        icon()
                    }
                }

                if (item.horizontalLayout) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (item.icon != null) Modifier.padding(end = 16.dp) else Modifier
                            ),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        supportingContent?.invoke()
                        Text(
                            text = item.titleAnnotated ?: AnnotatedString(item.title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .then(
                                if (item.icon != null || item.iconUri != null) Modifier.padding(end = 16.dp) else Modifier
                            ),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = item.titleAnnotated ?: AnnotatedString(item.title),
                            style = MaterialTheme.typography.titleMedium,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        supportingContent?.invoke()
                    }
                }
                trailingContent?.invoke()
            }
            if (item.type == PreferenceType.Seek) {
                seekContent()
            }
        }
    }
}

/**
 * A preference item that displays an album thumbnail.
 * Used for displaying ignored albums with their cover images.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun AlbumPreferenceItem(
    item: SettingsEntity.AlbumPreference,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val mutableInteractionSource = remember { MutableInteractionSource() }

    val isPressed = mutableInteractionSource.collectIsPressedAsState()
    val isFocused = mutableInteractionSource.collectIsFocusedAsState()
    val isDragged = mutableInteractionSource.collectIsDraggedAsState()
    val isHovered = mutableInteractionSource.collectIsHoveredAsState()
    val isInteracting by rememberedDerivedState {
        isPressed.value || isFocused.value || isDragged.value || isHovered.value
    }
    val fullCornerRadius by animateDpAsState(
        targetValue = if (isInteracting) 48.dp else 24.dp,
        label = "fullCornerRadius"
    )
    val normalCornerRadius by animateDpAsState(
        targetValue = if (isInteracting) 48.dp else 8.dp,
        label = "normalCornerRadius"
    )

    val shape by rememberedDerivedState(item.screenPosition, fullCornerRadius, normalCornerRadius) {
        when (item.screenPosition) {
            Position.Alone -> RoundedCornerShape(fullCornerRadius)
            Position.Bottom -> RoundedCornerShape(
                topStart = normalCornerRadius,
                topEnd = normalCornerRadius,
                bottomStart = fullCornerRadius,
                bottomEnd = fullCornerRadius
            )
            Position.Middle -> RoundedCornerShape(
                topStart = normalCornerRadius,
                topEnd = normalCornerRadius,
                bottomStart = normalCornerRadius,
                bottomEnd = normalCornerRadius
            )
            Position.Top -> RoundedCornerShape(
                topStart = fullCornerRadius,
                topEnd = fullCornerRadius,
                bottomStart = normalCornerRadius,
                bottomEnd = normalCornerRadius
            )
        }
    }

    val paddingModifier = when (item.screenPosition) {
        Position.Alone -> Modifier.padding(bottom = 16.dp)
        Position.Bottom -> Modifier.padding(top = 1.dp, bottom = 16.dp)
        Position.Middle -> Modifier.padding(vertical = 1.dp)
        Position.Top -> Modifier.padding(bottom = 1.dp)
    }

    val clickableModifier = if (item.onClick != null) {
        Modifier.clickable(
            enabled = item.enabled,
            interactionSource = mutableInteractionSource,
            indication = LocalIndication.current,
            onClick = { item.onClick.invoke() }
        )
    } else Modifier

    val alpha by animateFloatAsState(
        targetValue = if (item.enabled) 1f else 0.4f,
        label = "alpha"
    )

    Row(
        modifier = modifier
            .then(paddingModifier)
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(color = backgroundColor)
            .then(clickableModifier)
            .padding(12.dp)
            .fillMaxWidth()
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Album Thumbnail(s) - Stacked for multiple albums
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item.isMultiple && item.albumUri != null && item.secondaryAlbumUri != null) {
                // Stacked thumbnails for multiple albums (paper stack effect)
                // Back thumbnail (secondary) - offset to top-left with dark scrim
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    GlideImage(
                        model = item.secondaryAlbumUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        requestBuilderTransform = {
                            it.centerCrop()
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                        }
                    )
                    // Dark scrim overlay on back thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                    )
                }
                // Front thumbnail (primary) - offset to bottom-right
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .offset(x = 4.dp, y = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    GlideImage(
                        model = item.albumUri,
                        contentDescription = item.albumLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        requestBuilderTransform = {
                            it.centerCrop()
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                        }
                    )
                }
                // +N badge showing total matched albums count
                if (item.matchedAlbumsCount > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${item.matchedAlbumsCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            } else {
                // Single thumbnail or fallback icons
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        item.albumUri != null -> {
                            GlideImage(
                                model = item.albumUri,
                                contentDescription = item.albumLabel,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                requestBuilderTransform = {
                                    it.centerCrop()
                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                }
                            )
                        }
                        item.isWildcard -> {
                            Icon(
                                imageVector = GalleryIcons.RegularExpression,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Outlined.PhotoAlbum,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Text content
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.titleAnnotated ?: AnnotatedString(item.title),
                style = MaterialTheme.typography.titleMedium,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.summary.isNullOrEmpty() || item.summaryAnnotated != null) {
                Text(
                    modifier = Modifier.padding(top = 2.dp),
                    text = item.summaryAnnotated ?: AnnotatedString(item.summary ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ========== AlbumPreferenceItem Previews ==========

@Preview(showBackground = true, name = "Single Album")
@Composable
private fun AlbumPreferenceItemSinglePreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "Camera",
                summary = "DCIM/Camera",
                albumLabel = "Camera",
                albumUri = null,
                isWildcard = false,
                isMultiple = false,
                screenPosition = Position.Alone
            )
        )
    }
}

@Preview(showBackground = true, name = "Multiple Albums - Stacked")
@Composable
private fun AlbumPreferenceItemStackedPreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "Screenshots, Camera, Downloads",
                summary = "Hidden from albums",
                albumLabel = "Screenshots",
                albumUri = null,
                secondaryAlbumUri = null,
                isWildcard = false,
                isMultiple = true,
                matchedAlbumsCount = 3,
                screenPosition = Position.Alone
            )
        )
    }
}

@Preview(showBackground = true, name = "Regex/Wildcard")
@Composable
private fun AlbumPreferenceItemWildcardPreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "^Screenshot.*",
                summary = "Matches 5 albums",
                albumLabel = null,
                albumUri = null,
                isWildcard = true,
                isMultiple = false,
                matchedAlbumsCount = 5,
                screenPosition = Position.Alone
            )
        )
    }
}

@Preview(showBackground = true, name = "Large Badge Count")
@Composable
private fun AlbumPreferenceItemLargeBadgePreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "Backup Albums",
                summary = "Hidden from timeline",
                albumLabel = "Backup",
                albumUri = null,
                secondaryAlbumUri = null,
                isWildcard = false,
                isMultiple = true,
                matchedAlbumsCount = 25,
                screenPosition = Position.Alone
            )
        )
    }
}

@Preview(showBackground = true, name = "Position Top")
@Composable
private fun AlbumPreferenceItemTopPositionPreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "First Album",
                summary = "Top position in list",
                albumLabel = "First Album",
                albumUri = null,
                isWildcard = false,
                isMultiple = false,
                screenPosition = Position.Top
            )
        )
    }
}

@Preview(showBackground = true, name = "Position Middle")
@Composable
private fun AlbumPreferenceItemMiddlePositionPreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "Middle Album",
                summary = "Middle position in list",
                albumLabel = "Middle Album",
                albumUri = null,
                isWildcard = false,
                isMultiple = false,
                screenPosition = Position.Middle
            )
        )
    }
}

@Preview(showBackground = true, name = "Position Bottom")
@Composable
private fun AlbumPreferenceItemBottomPositionPreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "Last Album",
                summary = "Bottom position in list",
                albumLabel = "Last Album",
                albumUri = null,
                isWildcard = false,
                isMultiple = false,
                screenPosition = Position.Bottom
            )
        )
    }
}

@Preview(showBackground = true, name = "Disabled")
@Composable
private fun AlbumPreferenceItemDisabledPreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "Disabled Album",
                summary = "Cannot be clicked",
                albumLabel = "Disabled",
                albumUri = null,
                isWildcard = false,
                isMultiple = false,
                screenPosition = Position.Alone,
                enabled = false
            )
        )
    }
}

@Preview(showBackground = true, name = "No Summary")
@Composable
private fun AlbumPreferenceItemNoSummaryPreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "Album Without Summary",
                summary = null,
                albumLabel = "Plain Album",
                albumUri = null,
                isWildcard = false,
                isMultiple = false,
                screenPosition = Position.Alone
            )
        )
    }
}

@Preview(showBackground = true, name = "Long Title")
@Composable
private fun AlbumPreferenceItemLongTitlePreview() {
    PreviewHost {
        AlbumPreferenceItem(
            item = SettingsEntity.AlbumPreference(
                title = "This Is A Very Long Album Name That Should Be Truncated",
                summary = "Pictures/Very Long Folder Name/Subfolder",
                albumLabel = "Long Name Album",
                albumUri = null,
                isWildcard = false,
                isMultiple = false,
                screenPosition = Position.Alone
            )
        )
    }
}

@Composable
fun CustomCircleIcon(
    iconVector: ImageVector?,
    iconUri: String?,
    iconRes: Int?,
    containerColor: Color = MaterialTheme.colorScheme.tertiary,
    contentColor: Color = MaterialTheme.colorScheme.onTertiary
) {
    val modifier = Modifier
        .size(48.dp)
        .background(
            color = containerColor,
            shape = CircleShape
        )
        .padding(12.dp)
    if (iconRes != null) {
        Icon(
            modifier = modifier,
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor
        )
    } else if (iconVector != null) {
        Icon(
            modifier = modifier,
            imageVector = iconVector,
            contentDescription = null,
            tint = contentColor
        )
    } else if (iconUri != null) {
        AsyncImage(
            modifier = modifier,
            uri = iconUri,
            contentDescription = null,
            colorFilter = ColorFilter.tint(contentColor)
        )
    }
}

@Preview
@Composable
private fun SettingsItemPreview() {
    PreviewHost {
        LazyColumn(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface
                )
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp)
        ) {
            settings(
                preferenceItemBuilder = { item, modifier ->
                    SettingsItem(
                        item = item,
                        modifier = modifier
                    )
                }
            ) {
                Header(title = "This is a header")
                Preference(title = "This is a preference")
                Preference(
                    title = "[Disabled] Preference",
                    enabled = false
                )
                Preference(
                    title = "This is a preference with a summary",
                    summary = "This is a summary"
                )
                Preference(
                    title = "Payments",
                    summary = "Service",
                    horizontalLayout = true
                )
                Preference(
                    title = "Payments",
                    summary = "Service",
                    rightText = "£120"
                )
                Preference(
                    title = "Preference with icon",
                    icon = Icons.Outlined.WavingHand
                )
                Preference(
                    "Preference with icon and summary",
                    icon = Icons.Outlined.WavingHand,
                    summary = "This is a summary"
                )
                Preference(
                    "Preference with icon and long summary",
                    icon = Icons.Outlined.WavingHand,
                    summary = "This is a very long summary that should be truncated when it exceeds two lines in length. Let's see how it looks!"
                )
                Preference(
                    "[Disabled] Preference with icon and summary",
                    icon = Icons.Outlined.WavingHand,
                    summary = "This is a summary",
                    enabled = false
                )

                Header(title = "This is another header")
                SwitchPreference(
                    title = "This is a switch preference",
                    isChecked = true,
                    onCheck = {}
                )
                SwitchPreference(
                    title = "[Disabled] This is a switch preference",
                    isChecked = false,
                    onCheck = {},
                    enabled = false
                )
                SwitchPreference(
                    title = "This is a switch preference with a summary",
                    summary = "This is a summary",
                    isChecked = true,
                    onCheck = {}
                )
                SwitchPreference(
                    title = "This is a switch preference with icon",
                    icon = Icons.Outlined.WavingHand,
                    isChecked = false,
                    onCheck = {},
                )
                SwitchPreference(
                    title = "This is a switch preference with icon and summary",
                    summary = "This is a summary",
                    icon = Icons.Outlined.WavingHand,
                    isChecked = true,
                    onCheck = {}
                )
                SwitchPreference(
                    title = "[Disabled] This is a switch preference with icon and summary",
                    summary = "This is a summary",
                    icon = Icons.Outlined.WavingHand,
                    isChecked = true,
                    enabled = false,
                    onCheck = {}
                )

                Header(title = "AnnotatedString Examples")
                Preference(
                    title = buildAnnotatedString {
                        append("Title with ")
                        withStyle(
                            style = SpanStyle(
                                color = Color.Blue,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append("highlighted")
                        }
                        append(" text")
                    }
                )
                Preference(
                    title = buildAnnotatedString { append("AnnotatedString Summary") },
                    summary = buildAnnotatedString {
                        append("Summary with ")
                        withStyle(style = SpanStyle(textDecoration = TextDecoration.Underline)) {
                            append("underlined")
                        }
                        append(" text and ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("bold")
                        }
                        append(" text")
                    }
                )
                Preference(
                    title = buildAnnotatedString { append("AnnotatedString Right Text") },
                    rightText = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color.Red)) {
                            append("£")
                        }
                        append("120")
                    }
                )
                SwitchPreference(
                    title = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color.Green)) {
                            append("Switch")
                        }
                        append(" with ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("AnnotatedString")
                        }
                    },
                    isChecked = true,
                    onCheck = {}
                )
            }
        }
    }
}