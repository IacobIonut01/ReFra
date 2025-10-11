package com.dot.gallery.feature_node.presentation.ignored.setup.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.dot.gallery.R
import com.dot.gallery.core.presentation.components.SetupWizard
import com.dot.gallery.feature_node.domain.model.Album
import com.dot.gallery.feature_node.domain.model.AlbumState
import com.dot.gallery.feature_node.domain.model.IgnoredAlbum
import com.dot.gallery.feature_node.presentation.ignored.setup.SelectAlbumSheet
import com.dot.gallery.feature_node.presentation.util.GlideInvalidation
import com.dot.gallery.feature_node.presentation.util.rememberAppBottomSheetState
import kotlinx.coroutines.launch

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun SetupTypeSelectionScreen(
    onGoBack: () -> Unit,
    onNext: () -> Unit,
    initialAlbum: Album?,
    ignoredAlbums: List<IgnoredAlbum>,
    albumsState: State<AlbumState>,
    onAlbumChanged: (Album?) -> Unit,
) {
    var album by remember { mutableStateOf(initialAlbum) }
    LaunchedEffect(album) {
        onAlbumChanged(album)
    }

    SetupWizard(
        title = stringResource(R.string.setup_type_selection_title),
        subtitle = stringResource(R.string.setup_type_selection_subtitle),
        icon = Icons.Outlined.PhotoAlbum,
        contentPadding = 0.dp,
        bottomBar = {
            OutlinedButton(
                onClick = onGoBack
            ) {
                Text(text = stringResource(id = R.string.go_back))
            }

            Button(
                onClick = onNext,
                enabled = album != null
            ) {
                Text(text = stringResource(R.string.continue_string))
            }
        },
        content = {
            val shapeA = remember {
                RoundedPolygon(
                    12,
                    rounding = CornerRounding(0.4f)
                )
            }
            val shapeB = remember {
                RoundedPolygon.star(
                    12,
                    rounding = CornerRounding(0.2f)
                )
            }
            val morph = remember {
                Morph(shapeA, shapeB)
            }
            val infiniteTransition = rememberInfiniteTransition("infinite outline movement")
            val animatedProgress = infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "animatedMorphProgress"
            )
            val animatedRotation = infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    tween(6000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "animatedMorphProgress"
            )

            val pickAlbumState = rememberAppBottomSheetState()
            val scope = rememberCoroutineScope()
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(
                            CustomRotatingMorphShape(
                                morph,
                                animatedProgress.value,
                                animatedRotation.value
                            )
                        )
                        .background(color = MaterialTheme.colorScheme.primaryContainer)
                        .clickable {
                            scope.launch {
                                pickAlbumState.show()
                            }
                        }
                ) {
                    this@Column.AnimatedVisibility(album != null) {
                        GlideImage(
                            model = album!!.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            requestBuilderTransform = {
                                it.signature(GlideInvalidation.signature(album!!))
                            }
                        )
                    }

                    this@Column.AnimatedVisibility(album == null) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.setup_type_selection_add_album),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxSize().padding(64.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                val pickAlbumTitle = stringResource(R.string.setup_type_selection_pick_album)
                val albumTitle = remember(album) {
                    if (album == null) pickAlbumTitle else album!!.label
                }

                Text(
                    buildAnnotatedString {
                        withStyle(
                            style = MaterialTheme.typography.headlineSmall.toSpanStyle()
                        ) {
                            append(albumTitle)
                        }

                        if (album != null) {
                            appendLine()
                            withStyle(
                                style = MaterialTheme.typography.bodyMedium.toSpanStyle()
                            ) {
                                append(stringResource(R.string.s_items, album!!.count))
                            }
                        }
                    },
                    textAlign = TextAlign.Center,
                )
            }

            SelectAlbumSheet(
                sheetState = pickAlbumState,
                ignoredAlbums = ignoredAlbums,
                albumState = albumsState,
            ) { pickedAlbum ->
                album = pickedAlbum
                onAlbumChanged(pickedAlbum)
            }
        }
    )
}

private class CustomRotatingMorphShape(
    private val morph: Morph,
    private val percentage: Float,
    private val rotation: Float
) : Shape {

    private val matrix = Matrix()
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        // Below assumes that you haven't changed the default radius of 1f, nor the centerX and centerY of 0f
        // By default this stretches the path to the size of the container, if you don't want stretching, use the same size.width for both x and y.
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        matrix.rotateZ(rotation)

        val path = morph.toPath(progress = percentage).asComposePath()
        path.transform(matrix)

        return Outline.Generic(path)
    }
}