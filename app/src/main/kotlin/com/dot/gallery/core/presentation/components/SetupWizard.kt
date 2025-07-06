package com.dot.gallery.core.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dot.gallery.feature_node.presentation.util.LocalHazeState
import com.dot.gallery.ui.theme.GalleryTheme
import dev.chrisbanes.haze.hazeSource

@Composable
fun SetupWizard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    contentPadding: Dp = 32.dp,
    content: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit
) = SetupWizard(
    modifier = modifier,
    iconComponent = { modifier, color ->
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = modifier,
            tint = color
        )
    },
    title = title,
    subtitle = subtitle,
    contentPadding = contentPadding,
    content = content,
    bottomBar = bottomBar
)

@Composable
fun SetupWizard(
    modifier: Modifier = Modifier,
    painter: Painter,
    title: String,
    subtitle: String,
    contentPadding: Dp = 32.dp,
    content: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit
) = SetupWizard(
    modifier = modifier,
    iconComponent = { modifier, color ->
        Image(
            painter = painter,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(color)
        )
    },
    title = title,
    subtitle = subtitle,
    contentPadding = contentPadding,
    content = content,
    bottomBar = bottomBar
)

@Composable
fun SetupWizard(
    modifier: Modifier = Modifier,
    iconComponent: @Composable (Modifier, Color) -> Unit,
    title: String,
    subtitle: String,
    contentPadding: Dp = 32.dp,
    content: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val colorPrimary = MaterialTheme.colorScheme.primaryContainer
    val colorTertiary = MaterialTheme.colorScheme.tertiaryContainer
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)

    val transition = rememberInfiniteTransition()
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000),
            repeatMode = RepeatMode.Reverse
        )
    )
    Scaffold(
        modifier = modifier
            .hazeSource(LocalHazeState.current)
            .fillMaxSize()
            .drawWithCache {
                val cx = size.width - size.width * fraction
                val cy = size.height * fraction

                val gradient = Brush.radialGradient(
                    colors = listOf(colorPrimary, colorTertiary),
                    center = Offset(cx, cy),
                    radius = 1400f
                )

                onDrawBehind {
                    drawRoundRect(
                        brush = gradient
                    )
                }
            },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                bottomBar()
            }
        },
        containerColor = containerColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .background(Color.Transparent)
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(top = 24.dp)
                .verticalScroll(state = rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            iconComponent(
                Modifier.size(64.dp),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
            Text(
                text = buildAnnotatedString {
                    val headLineMedium = MaterialTheme.typography.headlineMedium.toSpanStyle()
                    val bodyLarge = MaterialTheme.typography.bodyLarge.toSpanStyle()
                    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                    withStyle(style = ParagraphStyle(textAlign = TextAlign.Center)) {
                        withStyle(
                            style = headLineMedium
                        ) {
                            append(title)
                        }
                        appendLine()
                        withStyle(
                            style = bodyLarge
                                .copy(color = onSurfaceVariant)
                        ) {
                            append(subtitle)
                        }
                    }
                }
            )
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(contentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                content()
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    GalleryTheme {
        SetupWizard(
            icon = Icons.Outlined.Settings,
            title = "Title",
            subtitle = "Subtitle",
            content = {
                Text("Content")
            },
            bottomBar = {
                Text("Bottom bar")
            }
        )
    }
}