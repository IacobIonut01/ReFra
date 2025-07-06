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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.presentation.components.ModalSheet
import com.dot.gallery.feature_node.presentation.common.components.OptionPosition.ALONE
import com.dot.gallery.feature_node.presentation.common.components.OptionPosition.BOTTOM
import com.dot.gallery.feature_node.presentation.common.components.OptionPosition.MIDDLE
import com.dot.gallery.feature_node.presentation.common.components.OptionPosition.TOP
import com.dot.gallery.feature_node.presentation.util.AppBottomSheetState
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun OptionSheet(
    state: AppBottomSheetState,
    onDismiss: (() -> Unit)? = null,
    headerContent: @Composable (ColumnScope.() -> Unit)? = null,
    vararg optionList: List<OptionItem>
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

@Composable
fun OptionLayout(
    modifier: Modifier = Modifier,
    optionList: List<OptionItem>
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