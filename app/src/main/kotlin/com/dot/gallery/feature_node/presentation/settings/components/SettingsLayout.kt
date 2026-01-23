package com.dot.gallery.feature_node.presentation.settings.components

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyScopeMarker
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun LazyGridScope.settings(
    preferenceItemBuilder: @Composable (item: SettingsEntity, modifier: Modifier) -> Unit = { item, modifier ->
        SettingsItem(
            item = item,
            modifier = modifier
        )
    },
    content: SettingsOptionsScope.() -> Unit
) {

    val options = SettingsOptionsScope()
        .apply(content)
        .build()

    itemsIndexed(
        items = options,
        span = { _, _ -> GridItemSpan(maxLineSpan) },
        key = { _, item ->
            item.toString() + Clock.System.now().toEpochMilliseconds() + Random.nextInt()
        }
    ) { index, item ->
        val position: Position = remember(options, index, item) {
            when (index) {
                0 -> {
                    val next = options[(index + 1).coerceAtMost(options.lastIndex)]
                    if (options.size == 1 || next is SettingsEntity.Header) Position.Alone
                    else Position.Top
                }

                options.lastIndex -> {
                    if (options[(index - 1).coerceAtLeast(0)] is SettingsEntity.Header) {
                        Position.Alone
                    } else Position.Bottom
                }

                else -> {
                    val previous = options[(index - 1).coerceAtLeast(0)]
                    val next = options[(index + 1).coerceAtMost(options.lastIndex)]
                    if (previous is SettingsEntity.Header && next is SettingsEntity.Header) {
                        Position.Alone
                    } else if (previous is SettingsEntity.Header) {
                        Position.Top
                    } else if (next is SettingsEntity.Header) {
                        Position.Bottom
                    } else {
                        Position.Middle
                    }
                }
            }
        }
        val newItem = remember(item) {
            when (item) {
                is SettingsEntity.Preference -> item.copy(
                    screenPosition = position,
                )

                is SettingsEntity.SwitchPreference -> item.copy(
                    screenPosition = position
                )

                is SettingsEntity.SeekPreference -> item.copy(
                    screenPosition = position
                )

                is SettingsEntity.AlbumPreference -> item.copy(
                    screenPosition = position
                )

                else -> item
            }
        }

        preferenceItemBuilder(newItem, Modifier.animateItem())
    }
}

@OptIn(ExperimentalTime::class)
fun LazyListScope.settings(
    preferenceItemBuilder: @Composable (item: SettingsEntity, modifier: Modifier) -> Unit = { item, modifier ->
        SettingsItem(
            item = item,
            modifier = modifier
        )
    },
    content: SettingsOptionsScope.() -> Unit
) {
    val options = SettingsOptionsScope()
        .apply(content)
        .build()

    itemsIndexed(
        items = options,
        key = { _, item ->
            item.toString() + Clock.System.now().toEpochMilliseconds() + Random.nextInt()
        }
    ) { index, item ->
        val position: Position = remember(options, index, item) {
            when (index) {
                0 -> {
                    val next = options[(index + 1).coerceAtMost(options.lastIndex)]
                    if (options.size == 1 || next is SettingsEntity.Header) Position.Alone
                    else Position.Top
                }

                options.lastIndex -> {
                    if (options[(index - 1).coerceAtLeast(0)] is SettingsEntity.Header) {
                        Position.Alone
                    } else Position.Bottom
                }

                else -> {
                    val previous = options[(index - 1).coerceAtLeast(0)]
                    val next = options[(index + 1).coerceAtMost(options.lastIndex)]
                    if (previous is SettingsEntity.Header && next is SettingsEntity.Header) {
                        Position.Alone
                    } else if (previous is SettingsEntity.Header) {
                        Position.Top
                    } else if (next is SettingsEntity.Header) {
                        Position.Bottom
                    } else {
                        Position.Middle
                    }
                }
            }
        }
        val newItem = remember(item) {
            when (item) {
                is SettingsEntity.Preference -> item.copy(
                    screenPosition = position,
                )

                is SettingsEntity.SwitchPreference -> item.copy(
                    screenPosition = position
                )

                is SettingsEntity.SeekPreference -> item.copy(
                    screenPosition = position
                )

                is SettingsEntity.AlbumPreference -> item.copy(
                    screenPosition = position
                )

                else -> item
            }
        }
        preferenceItemBuilder(newItem, Modifier.animateItem())
    }
}

@Suppress("FunctionName")
@LazyScopeMarker
class SettingsOptionsScope {
    private val items = listOf<SettingsEntity>()
        .toMutableStateList()

    fun Preference(
        title: String
    ) {
        items += SettingsEntity.Preference(
            title = title
        )
    }

    fun Preference(
        title: String,
        summary: String
    ) {
        items += SettingsEntity.Preference(
            title = title,
            summary = summary
        )
    }

    fun Preference(
        title: String,
        summary: String? = null,
        rightText: String? = null,
        enabled: Boolean = true,
        horizontalLayout: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        items += SettingsEntity.Preference(
            title = title,
            summary = summary,
            rightText = rightText,
            enabled = enabled,
            horizontalLayout = horizontalLayout,
            onClick = onClick
        )
    }

    fun Preference(
        title: String,
        icon: ImageVector? = null,
        summary: String? = null,
        rightText: String? = null,
        enabled: Boolean = true,
        horizontalLayout: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        items += SettingsEntity.Preference(
            title = title,
            icon = icon,
            summary = summary,
            rightText = rightText,
            enabled = enabled,
            horizontalLayout = horizontalLayout,
            onClick = onClick
        )
    }

    fun Preference(
        title: String,
        icon: Int? = null,
        summary: String? = null,
        rightText: String? = null,
        enabled: Boolean = true,
        horizontalLayout: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        items += SettingsEntity.Preference(
            title = title,
            iconRes = icon,
            summary = summary,
            rightText = rightText,
            enabled = enabled,
            horizontalLayout = horizontalLayout,
            onClick = onClick
        )
    }

    fun Preference(
        title: String,
        icon: String? = null,
        summary: String? = null,
        rightText: String? = null,
        enabled: Boolean = true,
        horizontalLayout: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        items += SettingsEntity.Preference(
            title = title,
            iconUri = icon,
            summary = summary,
            rightText = rightText,
            enabled = enabled,
            horizontalLayout = horizontalLayout,
            onClick = onClick
        )
    }

    fun SwitchPreference(
        title: String,
        enabled: Boolean = true,
        isChecked: Boolean,
        onCheck: (Boolean) -> Unit
    ) {
        items += SettingsEntity.SwitchPreference(
            title = title,
            isChecked = isChecked,
            onCheck = onCheck,
            enabled = enabled
        )
    }

    fun SwitchPreference(
        title: String,
        summary: String? = null,
        enabled: Boolean = true,
        isChecked: Boolean,
        onCheck: (Boolean) -> Unit
    ) {
        items += SettingsEntity.SwitchPreference(
            title = title,
            summary = summary,
            isChecked = isChecked,
            onCheck = onCheck,
            enabled = enabled
        )
    }

    fun SwitchPreference(
        title: String,
        icon: ImageVector? = null,
        summary: String? = null,
        enabled: Boolean = true,
        isChecked: Boolean,
        onCheck: (Boolean) -> Unit
    ) {
        items += SettingsEntity.SwitchPreference(
            title = title,
            icon = icon,
            summary = summary,
            isChecked = isChecked,
            onCheck = onCheck,
            enabled = enabled
        )
    }

    fun SwitchPreference(
        title: String,
        icon: String? = null,
        summary: String? = null,
        enabled: Boolean = true,
        isChecked: Boolean,
        onCheck: (Boolean) -> Unit
    ) {
        items += SettingsEntity.SwitchPreference(
            title = title,
            iconUri = icon,
            summary = summary,
            isChecked = isChecked,
            onCheck = onCheck,
            enabled = enabled
        )
    }

    fun SwitchPreference(
        title: String,
        icon: Int? = null,
        summary: String? = null,
        enabled: Boolean = true,
        isChecked: Boolean,
        onCheck: (Boolean) -> Unit
    ) {
        items += SettingsEntity.SwitchPreference(
            title = title,
            iconRes = icon,
            summary = summary,
            isChecked = isChecked,
            onCheck = onCheck,
            enabled = enabled
        )
    }

    fun Header(title: String) {
        items += SettingsEntity.Header(title)
    }

    // AnnotatedString versions
    fun Header(title: AnnotatedString) {
        items += SettingsEntity.Header(
            title = title.text,
            titleAnnotated = title
        )
    }

    fun Preference(
        title: AnnotatedString,
        icon: ImageVector? = null,
        iconUri: String? = null,
        iconRes: Int? = null,
        summary: AnnotatedString? = null,
        rightText: AnnotatedString? = null,
        enabled: Boolean = true,
        horizontalLayout: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        items += SettingsEntity.Preference(
            title = title.text,
            titleAnnotated = title,
            icon = icon,
            iconUri = iconUri,
            iconRes = iconRes,
            summary = summary?.text,
            summaryAnnotated = summary,
            rightText = rightText?.text,
            rightTextAnnotated = rightText,
            enabled = enabled,
            horizontalLayout = horizontalLayout,
            onClick = onClick
        )
    }

    fun Preference(
        title: String,
        icon: ImageVector? = null,
        iconUri: String? = null,
        iconRes: Int? = null,
        summary: AnnotatedString? = null,
        rightText: AnnotatedString? = null,
        enabled: Boolean = true,
        horizontalLayout: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        items += SettingsEntity.Preference(
            title = title,
            icon = icon,
            iconUri = iconUri,
            iconRes = iconRes,
            summary = summary?.text,
            summaryAnnotated = summary,
            rightText = rightText?.text,
            rightTextAnnotated = rightText,
            enabled = enabled,
            horizontalLayout = horizontalLayout,
            onClick = onClick
        )
    }

    fun Preference(
        title: String,
        icon: ImageVector? = null,
        iconUri: String? = null,
        iconRes: Int? = null,
        summary: AnnotatedString? = null,
        rightText: String? = null,
        enabled: Boolean = true,
        horizontalLayout: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        items += SettingsEntity.Preference(
            title = title,
            icon = icon,
            iconUri = iconUri,
            iconRes = iconRes,
            summary = summary?.text,
            summaryAnnotated = summary,
            rightText = rightText,
            enabled = enabled,
            horizontalLayout = horizontalLayout,
            onClick = onClick
        )
    }

    fun SwitchPreference(
        title: AnnotatedString,
        isChecked: Boolean,
        onCheck: (Boolean) -> Unit,
        icon: ImageVector? = null,
        iconUri: String? = null,
        iconRes: Int? = null,
        summary: AnnotatedString? = null,
        enabled: Boolean = true
    ) {
        items += SettingsEntity.SwitchPreference(
            title = title.text,
            titleAnnotated = title,
            icon = icon,
            iconUri = iconUri,
            iconRes = iconRes,
            summary = summary?.text,
            summaryAnnotated = summary,
            isChecked = isChecked,
            onCheck = onCheck,
            enabled = enabled
        )
    }

    fun AlbumPreference(
        title: String,
        summary: String? = null,
        albumUri: Any? = null,
        secondaryAlbumUri: Any? = null,
        albumLabel: String? = null,
        albumCount: Int = 0,
        matchedAlbumsCount: Int = 0,
        isMultiple: Boolean = false,
        isWildcard: Boolean = false,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null
    ) {
        items += SettingsEntity.AlbumPreference(
            title = title,
            summary = summary,
            albumUri = albumUri,
            secondaryAlbumUri = secondaryAlbumUri,
            albumLabel = albumLabel,
            albumCount = albumCount,
            matchedAlbumsCount = matchedAlbumsCount,
            isMultiple = isMultiple,
            isWildcard = isWildcard,
            enabled = enabled,
            onClick = onClick
        )
    }

    internal fun build(): List<SettingsEntity> = items
}