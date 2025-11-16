package com.dot.gallery.feature_node.presentation.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.Position
import com.dot.gallery.core.SettingsEntity
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.feature_node.presentation.common.components.OptionPosition.ALONE
import com.dot.gallery.feature_node.presentation.common.components.OptionPosition.BOTTOM
import com.dot.gallery.feature_node.presentation.common.components.OptionPosition.MIDDLE
import com.dot.gallery.feature_node.presentation.common.components.OptionPosition.TOP
import com.dot.gallery.feature_node.presentation.mediaview.rememberedDerivedState
import com.dot.gallery.feature_node.presentation.settings.components.SettingsItem
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun OptionSheet(
    state: AppBottomSheetState,
    onDismiss: (() -> Unit)? = null,
    headerContent: @Composable (ColumnScope.() -> Unit)? = null,
    vararg optionList: SnapshotStateList<OptionItem>
) {
    ModalSheet(
        sheetState = state,
        onDismissRequest = { onDismiss?.invoke() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        content = {
            headerContent?.invoke(this)
            optionList.forEach { list ->
                OptionLayout(
                    modifier = Modifier.fillMaxWidth(),
                    optionList = list
                )
            }
        }
    )
}

fun LazyListScope.SettingsOptionLayout(
    modifier: Modifier = Modifier,
    optionList: List<SettingsEntity>,
    slimLayout: Boolean = false,
    swipeToDismiss: Boolean = false,
    onDismiss: ((SettingsEntity) -> Unit)? = null
) {
    itemsIndexed(
        items = optionList,
        key = { index, item -> item.toString() }
    ) { index, item ->
        val position: Position = remember(index, item) {
            when (index) {
                0 -> {
                    if (optionList.size == 1) Position.Alone
                    else Position.Top
                }

                optionList.lastIndex -> {
                    if (optionList[(index - 1).coerceAtLeast(0)] is SettingsEntity.Header) {
                        Position.Alone
                    } else Position.Bottom
                }

                else -> {
                    val previous = optionList[(index - 1).coerceAtLeast(0)]
                    val next = optionList[(index + 1).coerceAtMost(optionList.lastIndex)]
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

                else -> item
            }
        }
        if (swipeToDismiss && newItem !is SettingsEntity.Header) {
            val swipeToDismissBoxState = rememberSwipeToDismissBoxState(
                SwipeToDismissBoxValue.Settled,
                SwipeToDismissBoxDefaults.positionalThreshold
            )

            SwipeToDismissBox(
                state = swipeToDismissBoxState,
                modifier = modifier
                    .fillMaxWidth()
                    .animateItem(),
                enableDismissFromStartToEnd = false,
                backgroundContent = {
                    val shape by rememberedDerivedState(position) {
                        when (position) {
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
                    val paddingModifier by rememberedDerivedState(position) {
                        when (position) {
                            Position.Alone -> Modifier.padding(bottom = 16.dp)
                            Position.Bottom -> Modifier.padding(top = 1.dp, bottom = 16.dp)
                            Position.Middle -> Modifier.padding(vertical = 1.dp)
                            Position.Top -> Modifier.padding(bottom = 1.dp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .then(paddingModifier)
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = shape
                            ),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                },
                onDismiss = {
                    if (it == SwipeToDismissBoxValue.EndToStart) {
                        onDismiss?.invoke(newItem)
                    }
                },
                content = {
                    SettingsItem(
                        modifier = Modifier.animateItem(),
                        item = newItem,
                        slimLayout = slimLayout
                    )
                }
            )
        } else {
            SettingsItem(
                modifier = Modifier.animateItem(),
                item = newItem,
                slimLayout = slimLayout
            )
        }
    }
}


@Composable
fun OptionLayout(
    modifier: Modifier = Modifier,
    optionList: SnapshotStateList<OptionItem>
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        optionList.forEachIndexed { index, item ->
            val position: OptionPosition = remember(index, item) {
                when (index) {
                    0 -> {
                        if (optionList.size == 1) ALONE
                        else TOP
                    }

                    optionList.lastIndex -> BOTTOM
                    else -> MIDDLE
                }
            }
            val summary: (@Composable () -> Unit)? = if (item.summary.isNullOrBlank()) null else {
                {
                    Text(text = item.summary)
                }
            }
            OptionButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeSource(LocalHazeState.current),
                icon = item.icon,
                textContainer = {
                    Text(text = item.text)
                },
                summaryContainer = summary,
                enabled = item.enabled,
                containerColor = item.containerColor
                    ?: MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = item.contentColor
                    ?: MaterialTheme.colorScheme.onSurface,
                position = position,
                onClick = {
                    item.onClick(item.summary.toString())
                }
            )
        }
    }
}

@Composable
fun OptionButton(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: ImageVector? = null,
    textContainer: @Composable () -> Unit,
    summaryContainer: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    position: OptionPosition = ALONE,
    onClick: () -> Unit
) {
    val mod = modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .background(
            color = containerColor,
            shape = position.shape()
        )
        .clip(position.shape())
        .clickable(
            enabled = enabled,
            onClick = onClick
        )
        .alpha(if (enabled) 1f else 0.4f)
        .padding(16.dp)
    Row(
        modifier = mod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.alpha(if (enabled) 1f else 0.4f)
            )
        }
        if (summaryContainer != null) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelLarge.copy(
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    textContainer()
                }
                ProvideTextStyle(
                    value = MaterialTheme.typography.labelMedium.copy(
                        color = contentColor,
                        fontWeight = FontWeight.Normal
                    )
                ) {
                    summaryContainer()
                }
            }
        } else {
            Box(
                contentAlignment = Alignment.Center
            ) {
                ProvideTextStyle(
                    value = MaterialTheme.typography.bodyMedium.copy(color = contentColor)
                ) {
                    textContainer()
                }
            }
        }
    }
}

data class OptionItem(
    val icon: ImageVector? = null,
    val text: String,
    val summary: String? = null,
    val onClick: (summary: String) -> Unit,
    val enabled: Boolean = true,
    val containerColor: Color? = null,
    val contentColor: Color? = null,
)

object OptionShape {

    val Top = RoundedCornerShape(
        topEnd = 12.dp,
        topStart = 12.dp,
        bottomEnd = 1.dp,
        bottomStart = 1.dp
    )

    val Middle = RoundedCornerShape(
        topEnd = 1.dp,
        topStart = 1.dp,
        bottomEnd = 1.dp,
        bottomStart = 1.dp
    )

    val Bottom = RoundedCornerShape(
        topEnd = 1.dp,
        topStart = 1.dp,
        bottomEnd = 12.dp,
        bottomStart = 12.dp
    )

    val Alone = RoundedCornerShape(
        topEnd = 12.dp,
        topStart = 12.dp,
        bottomEnd = 12.dp,
        bottomStart = 12.dp
    )
}

enum class OptionPosition {
    TOP, MIDDLE, BOTTOM, ALONE
}

fun OptionPosition.shape(): RoundedCornerShape = when (this) {
    TOP -> OptionShape.Top
    MIDDLE -> OptionShape.Middle
    BOTTOM -> OptionShape.Bottom
    ALONE -> OptionShape.Alone
}