/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

const val FloatingProgressPillTag = "Gallery.FloatingProgressPill"

/**
 * One long-running operation shown by [FloatingProgressHost]. Callers pass already-resolved
 * text so any tool (rotate, copy, upload, …) can post a task without the host knowing about it.
 */
@Immutable
data class FloatingProgressTask(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val completed: Int = 0,
    val total: Int = 0,
    val state: State = State.Running,
) {
    enum class State { Running, Succeeded, Failed }

    /** Determinate fraction, or null while the total is unknown. */
    val fraction: Float?
        get() = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else null
}

/**
 * App-wide list of floating progress tasks. Producers [post] and [dismiss] by id; the newest
 * task is the one [FloatingProgressHost] shows.
 */
class FloatingProgressRegistry {
    private val _tasks = MutableStateFlow<List<FloatingProgressTask>>(emptyList())
    val tasks: StateFlow<List<FloatingProgressTask>> = _tasks.asStateFlow()

    /** Adds [task], or replaces the task with the same id in place. */
    fun post(task: FloatingProgressTask) {
        _tasks.update { current ->
            val index = current.indexOfFirst { it.id == task.id }
            if (index < 0) current + task
            else current.toMutableList().also { it[index] = task }
        }
    }

    fun update(id: String, transform: (FloatingProgressTask) -> FloatingProgressTask) {
        _tasks.update { current -> current.map { if (it.id == id) transform(it) else it } }
    }

    fun dismiss(id: String) {
        _tasks.update { current -> current.filterNot { it.id == id } }
    }
}

val LocalFloatingProgressRegistry = staticCompositionLocalOf<FloatingProgressRegistry?> { null }

/** Shows the newest task from [LocalFloatingProgressRegistry] as a floating blurred pill. */
@Composable
fun FloatingProgressHost(modifier: Modifier = Modifier) {
    val registry = LocalFloatingProgressRegistry.current ?: return
    val tasks by registry.tasks.collectAsStateWithLifecycle()
    val task = tasks.lastOrNull()
    AnimatedVisibility(
        modifier = modifier,
        visible = task != null,
        enter = slideInVertically { it * 2 } + fadeIn(),
        exit = slideOutVertically { it * 2 } + fadeOut(),
    ) {
        // Keeps the last task drawn through the exit transition (not state: read-only cache).
        val lastShown = remember { arrayOfNulls<FloatingProgressTask>(1) }
        if (task != null) lastShown[0] = task
        lastShown[0]?.let { FloatingProgressPill(task = it, extraTasks = (tasks.size - 1).coerceAtLeast(0)) }
    }
}

/** Stateless pill: progress ring around the task icon, label and a count. Fully rounded. */
@Composable
fun FloatingProgressPill(
    task: FloatingProgressTask,
    modifier: Modifier = Modifier,
    extraTasks: Int = 0,
) {
    val surface = galleryFloatingSurface(CircleShape)
    val progress by animateFloatAsState(
        targetValue = if (task.state == FloatingProgressTask.State.Running) task.fraction ?: 0f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "floatingProgress",
    )
    Row(
        modifier = modifier
            .widthIn(min = 160.dp, max = 420.dp)
            .height(56.dp)
            .then(surface)
            .padding(start = 8.dp, end = 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag(FloatingProgressPillTag),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = task.state,
            transitionSpec = {
                (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.6f) + fadeOut())
            },
            label = "floatingProgressLeading",
        ) { state ->
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                when (state) {
                    FloatingProgressTask.State.Running -> {
                        if (task.fraction != null) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(40.dp),
                                strokeWidth = 3.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                        }
                        Icon(
                            imageVector = task.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    FloatingProgressTask.State.Succeeded -> Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    FloatingProgressTask.State.Failed -> Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        AnimatedContent(
            modifier = Modifier.weight(1f, fill = false),
            targetState = task.label,
            transitionSpec = {
                (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) using
                    SizeTransform(clip = false)
            },
            label = "floatingProgressLabel",
        ) { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (task.total > 0 && task.state == FloatingProgressTask.State.Running) {
            Text(
                text = "${task.completed}/${task.total}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (extraTasks > 0) {
            Text(
                text = "+$extraTasks",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
