/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature.library.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.ml.ModelStatus
import com.dot.gallery.core.presentation.components.LocalMediaImageRenderer
import com.dot.gallery.core.ui.settings.rememberPreference
import com.dot.gallery.domain.settings.SECTION_CATEGORIES
import com.dot.gallery.domain.settings.SECTION_PEOPLE
import com.dot.gallery.domain.settings.SettingsKeys
import com.dot.gallery.domain.util.getUri
import com.dot.gallery.feature.library.generated.resources.Res
import com.dot.gallery.feature.library.generated.resources.categories
import com.dot.gallery.feature.library.generated.resources.cloud_people
import com.dot.gallery.feature.library.generated.resources.locations
import com.dot.gallery.feature.library.ui.components.EditableLibraryShortcutsGrid
import com.dot.gallery.feature.library.ui.components.MapPreviewCard
import com.dot.gallery.ui.theme.BlackScrim
import org.jetbrains.compose.resources.stringResource

/** How much room the header has: phone column, full-width tablet column, or side pane. */
enum class LibraryHeaderLayout { COMPACT, WIDE, SIDE_PANE }

/** Which sections have something to show and are not hidden by the user. */
@Immutable
data class LibrarySections(
    val locations: Boolean,
    val people: Boolean,
    val categories: Boolean,
) {
    val count: Int get() = listOf(locations, people, categories).count { it }
}

enum class ExploreArrangement { NONE, TALL_MAP_BESIDE_PAIR, ROW, STACKED }

/** The block reflows to what is available, so a missing section never leaves a placeholder. */
fun exploreArrangement(sections: LibrarySections, layout: LibraryHeaderLayout): ExploreArrangement =
    when {
        sections.count == 0 -> ExploreArrangement.NONE
        layout == LibraryHeaderLayout.SIDE_PANE -> ExploreArrangement.STACKED
        layout == LibraryHeaderLayout.COMPACT && sections.count == 3 -> ExploreArrangement.TALL_MAP_BESIDE_PAIR
        else -> ExploreArrangement.ROW
    }

@Composable
fun rememberLibrarySections(state: LibraryState): LibrarySections {
    val snapshot = state.snapshot
    val cloud = snapshot.cloud
    val showLocations by rememberPreference(SettingsKeys.showLocationCategories)
    val hiddenSections by rememberPreference(SettingsKeys.hiddenSections)
    val noClassification by rememberPreference(SettingsKeys.noClassification)
    val categoriesEnabled = SECTION_CATEGORIES !in hiddenSections && state.aiAvailable && !noClassification
    val awaitingFirstCategory = noCategories(snapshot.categories) && state.modelStatus == ModelStatus.READY
    return LibrarySections(
        locations = showLocations && !snapshot.locations.isNullOrEmpty(),
        people = SECTION_PEOPLE !in hiddenSections && cloud.hasPeople &&
            (cloud.people.isNotEmpty() || cloud.hiddenPeopleCount > 0),
        categories = categoriesEnabled && (!snapshot.categories.isNullOrEmpty() || awaitingFirstCategory),
    )
}

/**
 * The bounded top of the Library page: shortcuts, then Locations, People and Categories as compact
 * entry points to their full screens. Stays short so the album grid below is reachable.
 */
@Composable
fun LibraryHeader(
    state: LibraryState,
    actions: LibraryActions,
    routes: LibraryRoutes,
    layout: LibraryHeaderLayout,
    modifier: Modifier = Modifier,
    mapContent: (@Composable BoxScope.(latitude: Double, longitude: Double) -> Unit)? = null,
) {
    val sections = rememberLibrarySections(state)
    Column(modifier = modifier.fillMaxWidth()) {
        LibraryShortcuts(
            state = state,
            routes = routes,
            editMode = false,
            layout = layout,
            onNavigate = actions.onNavigate,
        )
        LibraryExploreCards(
            state = state,
            routes = routes,
            sections = sections,
            layout = layout,
            mapContent = mapContent,
            onNavigate = actions.onNavigate,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
internal fun LibraryShortcuts(
    state: LibraryState,
    routes: LibraryRoutes,
    editMode: Boolean,
    layout: LibraryHeaderLayout,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = rememberLibraryRuntimeShortcuts(
        indicatorState = state.snapshot.indicators,
        cloudState = state.snapshot.cloud,
        routes = routes,
        supportsTrash = state.supportsTrash,
        supportsFavorites = state.supportsFavorites
    )
    var shortcutsLayout by rememberPreference(libraryShortcutsLayoutKey)
    val working = remember(runtime, shortcutsLayout) {
        mergeShortcutPrefs(shortcutsLayout, runtime).map { it.pref }
    }
    EditableLibraryShortcutsGrid(
        modifier = modifier,
        working = working,
        runtime = runtime,
        editMode = editMode,
        columns = if (layout == LibraryHeaderLayout.WIDE) 4 else LibraryShortcutSpan.COLUMNS,
        collapsible = layout == LibraryHeaderLayout.COMPACT,
        onClick = onNavigate,
        onChange = { newWorking ->
            val availableIds = runtime.keys.map { it.id }.toSet()
            val leftovers = shortcutsLayout.filter { it.id !in availableIds }
            shortcutsLayout = newWorking + leftovers
        }
    )
}

@Composable
internal fun LibraryExploreCards(
    state: LibraryState,
    routes: LibraryRoutes,
    sections: LibrarySections,
    layout: LibraryHeaderLayout,
    mapContent: (@Composable BoxScope.(latitude: Double, longitude: Double) -> Unit)?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locations: @Composable (Modifier) -> Unit = { cardModifier ->
        LocationsCard(state, mapContent, cardModifier) { onNavigate(routes.locations) }
    }
    val people: @Composable (Modifier) -> Unit = { cardModifier ->
        PeopleCard(state, cardModifier) { onNavigate(routes.people) }
    }
    val categories: @Composable (Modifier) -> Unit = { cardModifier ->
        CategoriesCard(state, cardModifier) { onNavigate(routes.categories) }
    }
    val spacing = Arrangement.spacedBy(8.dp)
    when (exploreArrangement(sections, layout)) {
        ExploreArrangement.NONE -> Unit
        ExploreArrangement.TALL_MAP_BESIDE_PAIR -> Row(
            modifier = modifier.fillMaxWidth().height(184.dp),
            horizontalArrangement = spacing,
        ) {
            locations(Modifier.weight(1f).fillMaxHeight())
            Column(modifier = Modifier.weight(1f), verticalArrangement = spacing) {
                people(Modifier.weight(1f).fillMaxWidth())
                categories(Modifier.weight(1f).fillMaxWidth())
            }
        }
        ExploreArrangement.ROW -> Row(
            modifier = modifier
                .fillMaxWidth()
                .height(
                    when {
                        layout == LibraryHeaderLayout.WIDE -> 168.dp
                        sections.count == 2 -> 160.dp
                        else -> 120.dp
                    }
                ),
            horizontalArrangement = spacing,
        ) {
            val cardModifier = Modifier.weight(1f).fillMaxHeight()
            if (sections.locations) locations(cardModifier)
            if (sections.people) people(cardModifier)
            if (sections.categories) categories(cardModifier)
        }
        ExploreArrangement.STACKED -> Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = spacing,
        ) {
            if (sections.locations) locations(Modifier.fillMaxWidth().height(160.dp))
            if (sections.people) people(Modifier.fillMaxWidth().height(96.dp))
            if (sections.categories) categories(Modifier.fillMaxWidth().height(96.dp))
        }
    }
}

private fun Modifier.exploreCard(onClick: () -> Unit): Modifier =
    clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)

private fun exploreCount(count: Int): String =
    if (count > 99) "99+" else count.toString()

/** Name and count, on one baseline, at the bottom of every card so all three line up. */
@Composable
private fun ExploreLabel(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier.weight(1f).alignByBaseline(),
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (count > 0) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = exploreCount(count),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

@Composable
private fun LocationsCard(
    state: LibraryState,
    mapContent: (@Composable BoxScope.(latitude: Double, longitude: Double) -> Unit)?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val latestGeo = state.snapshot.latestGeo
    Box(modifier = modifier.testTag("library-locations").exploreCard(onClick)) {
        MapPreviewCard(
            modifier = Modifier.fillMaxSize(),
            height = null,
            thumbnailSize = 56.dp,
            // Keeps the photo clear of the label pill on the shorter cards.
            thumbnailBottomInset = 40.dp,
            latestMedia = latestGeo?.media,
            latitude = latestGeo?.latitude,
            longitude = latestGeo?.longitude,
            mapContent = if (state.mapsEnabled) mapContent else null,
        )
        // A compact pill, so the map stays the content of the card.
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = stringResource(Res.string.locations),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (state.snapshot.locationCount > 0) {
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = exploreCount(state.snapshot.locationCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PeopleCard(
    state: LibraryState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val renderer = LocalMediaImageRenderer.current
    val people = state.snapshot.cloud.people
    val surface = MaterialTheme.colorScheme.surfaceContainer
    Column(
        modifier = modifier
            .testTag("library-people")
            .exploreCard(onClick)
            .background(surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val faces = (maxWidth / FaceStep).toInt().coerceAtLeast(1).coerceAtMost(people.size)
            Row(horizontalArrangement = Arrangement.spacedBy(-FaceOverlap)) {
                people.take(faces).forEach { person ->
                    Box(
                        modifier = Modifier
                            .size(FaceSize)
                            .clip(CircleShape)
                            .background(surface)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (person.thumbnailUrl != null) {
                            renderer.RenderImage(
                                model = person.thumbnailUrl,
                                contentDescription = person.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.People,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        ExploreLabel(
            title = stringResource(Res.string.cloud_people),
            count = state.snapshot.peopleCount,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val CategoryLabelScrim = Brush.verticalGradient(listOf(Color.Transparent, BlackScrim))
private val FaceSize = 36.dp
private val FaceOverlap = 6.dp
private val FaceStep = FaceSize - FaceOverlap

@Composable
private fun CategoriesCard(
    state: LibraryState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val renderer = LocalMediaImageRenderer.current
    val thumbnail = state.snapshot.categories.orEmpty().firstNotNullOfOrNull { it.thumbnailMedia }
    Box(
        modifier = modifier
            .testTag("library-categories")
            .exploreCard(onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (thumbnail != null) {
            renderer.RenderImage(
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                model = thumbnail.getUri(),
                contentDescription = null,
                signature = thumbnail,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.ImageSearch,
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp).size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The scrim is only there to keep the label readable over a photo.
        ExploreLabel(
            title = stringResource(Res.string.categories),
            count = state.snapshot.categoryCount,
            color = if (thumbnail != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .then(if (thumbnail != null) Modifier.background(CategoryLabelScrim) else Modifier)
                .padding(start = 14.dp, end = 14.dp, top = 24.dp, bottom = 10.dp),
        )
    }
}
