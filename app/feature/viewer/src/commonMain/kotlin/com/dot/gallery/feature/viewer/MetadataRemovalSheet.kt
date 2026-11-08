package com.dot.gallery.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LayersClear
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dot.gallery.core.metadata.MetadataCategory
import com.dot.gallery.core.metadata.MetadataPolicy
import com.dot.gallery.core.metadata.MetadataSaveMode
import com.dot.gallery.core.metadata.SanitizationCapability
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Repeat
import com.dot.gallery.core.presentation.components.DragHandle
import com.dot.gallery.core.presentation.components.ExpressiveSheetShape
import com.dot.gallery.core.presentation.components.SheetButton
import com.dot.gallery.core.presentation.components.SheetButtonStyle
import com.dot.gallery.core.presentation.components.SheetHeader
import com.dot.gallery.core.ui.util.AppBottomSheetState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.dot.gallery.feature.viewer.generated.resources.Res
import com.dot.gallery.feature.viewer.generated.resources.metadata_advanced
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_capture
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_description
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_device
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_edits
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_location
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_people
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_provenance
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_thumbnail
import com.dot.gallery.feature.viewer.generated.resources.metadata_category_timestamps
import com.dot.gallery.feature.viewer.generated.resources.metadata_found
import com.dot.gallery.feature.viewer.generated.resources.metadata_none_found
import com.dot.gallery.feature.viewer.generated.resources.metadata_other_found
import com.dot.gallery.feature.viewer.generated.resources.metadata_preserved_line
import com.dot.gallery.feature.viewer.generated.resources.metadata_preset_all
import com.dot.gallery.feature.viewer.generated.resources.metadata_preset_all_description
import com.dot.gallery.feature.viewer.generated.resources.metadata_preset_location
import com.dot.gallery.feature.viewer.generated.resources.metadata_preset_location_description
import com.dot.gallery.feature.viewer.generated.resources.metadata_replace_original
import com.dot.gallery.feature.viewer.generated.resources.metadata_replace_original_action
import com.dot.gallery.feature.viewer.generated.resources.metadata_replace_original_description
import com.dot.gallery.feature.viewer.generated.resources.metadata_save_copy
import com.dot.gallery.feature.viewer.generated.resources.metadata_save_copy_action
import com.dot.gallery.feature.viewer.generated.resources.metadata_save_copy_description
import com.dot.gallery.feature.viewer.generated.resources.remove_metadata
import com.dot.gallery.feature.viewer.generated.resources.remove_metadata_running

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataRemovalSheet(
    state: AppBottomSheetState,
    capability: SanitizationCapability?,
    isBusy: Boolean,
    canReplaceOriginal: Boolean,
    onConfirm: (Set<MetadataCategory>, MetadataSaveMode) -> Unit,
    onDismissed: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var selection by remember { mutableStateOf(MetadataPolicy.ALL) }
    var saveMode by remember { mutableStateOf(MetadataSaveMode.SAVE_COPY) }
    var advancedExpanded by remember { mutableStateOf(false) }
    if (!state.isVisible) return

    val removable = capability?.removableCategories.orEmpty()
    val present = capability?.removablePresentCategories.orEmpty()
    val hasLocation = MetadataCategory.LOCATION in present
    val allSelected = selection == removable
    val locationSelected = selection == MetadataPolicy.LOCATION
    LaunchedEffect(capability) {
        if (capability != null) selection = capability.removableCategories
        saveMode = MetadataSaveMode.SAVE_COPY
        advancedExpanded = false
    }
    // Replace can be unavailable for this media even if it was picked for a previous one.
    val mode = if (canReplaceOriginal) saveMode else MetadataSaveMode.SAVE_COPY
    val removesSomething = capability != null && capability.supports(selection) && (
        present.any { it in selection } || (allSelected && capability.hasOtherMetadata)
    )

    ModalBottomSheet(
        sheetState = state.sheetState,
        onDismissRequest = {
            if (!isBusy) {
                scope.launch { state.hide() }
                // The probe result outlives this sheet in the ViewModel — without a reset
                // the phase stays Ready and the sheet re-opens on every view of the media.
                onDismissed()
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shape = ExpressiveSheetShape,
        dragHandle = { DragHandle(modifier = Modifier.padding(vertical = 5.dp)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.remove_metadata),
                body = summaryLine(capability, present),
                modifier = Modifier.padding(bottom = 0.dp),
            )

            val limitation = capability?.limitation
            if (!limitation.isNullOrBlank()) {
                Text(
                    text = limitation,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (capability.isSupported) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }

            if (capability?.isSupported == true) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetCard(
                        icon = Icons.Outlined.LayersClear,
                        title = stringResource(Res.string.metadata_preset_all),
                        description = stringResource(Res.string.metadata_preset_all_description),
                        selected = allSelected,
                        enabled = !isBusy && capability.hasRemovableMetadata,
                        onClick = { selection = removable },
                        modifier = Modifier.weight(1f),
                    )
                    PresetCard(
                        icon = Icons.Outlined.LocationOff,
                        title = stringResource(Res.string.metadata_preset_location),
                        description = stringResource(Res.string.metadata_preset_location_description),
                        selected = locationSelected,
                        enabled = !isBusy && hasLocation,
                        onClick = { selection = MetadataPolicy.LOCATION },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (present.isNotEmpty()) {
                    AdvancedSection(
                        expanded = advancedExpanded,
                        onToggleExpanded = { advancedExpanded = !advancedExpanded },
                        categories = present,
                        previews = capability.previews,
                        selection = selection,
                        enabled = !isBusy,
                        onToggle = { category, checked ->
                            selection = if (checked) selection + category else selection - category
                        },
                    )
                }

                Text(
                    text = stringResource(Res.string.metadata_preserved_line),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetadataSaveMode.entries.forEach { entry ->
                        PresetCard(
                            icon = if (entry == MetadataSaveMode.SAVE_COPY) Icons.Outlined.FileCopy else Icons.Outlined.Repeat,
                            title = entry.title(),
                            description = entry.description(),
                            selected = mode == entry,
                            enabled = !isBusy && (entry != MetadataSaveMode.REPLACE_ORIGINAL || canReplaceOriginal),
                            onClick = { saveMode = entry },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            val replacing = mode == MetadataSaveMode.REPLACE_ORIGINAL
            SheetButton(
                text = if (isBusy) stringResource(Res.string.remove_metadata_running) else mode.actionTitle(),
                onClick = { onConfirm(selection, mode) },
                enabled = removesSomething && !isBusy,
                style = if (replacing) SheetButtonStyle.Error else SheetButtonStyle.Filled,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun summaryLine(capability: SanitizationCapability?, present: List<MetadataCategory>): String {
    val format = capability?.format?.name?.replace('_', ' ') ?: return ""
    val labels = present.map { stringResource(it.label()) }
    return when {
        labels.isNotEmpty() -> stringResource(
            Res.string.metadata_found,
            format,
            labels.joinToString(", ").lowercase()
        )
        capability.isSupported && capability.hasOtherMetadata -> stringResource(Res.string.metadata_other_found, format)
        else -> stringResource(Res.string.metadata_none_found, format)
    }
}

@Composable
private fun PresetCard(
    icon: ImageVector,
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (selected && enabled) colors.secondaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected && enabled) colors.onSecondaryContainer else colors.onSurface,
        border = if (selected && enabled) BorderStroke(2.dp, colors.secondary) else null,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected && enabled) colors.onSecondaryContainer else colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AdvancedSection(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    categories: List<MetadataCategory>,
    previews: Map<MetadataCategory, String>,
    selection: Set<MetadataCategory>,
    enabled: Boolean,
    onToggle: (MetadataCategory, Boolean) -> Unit,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.metadata_advanced),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${categories.count { it in selection }}/${categories.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).rotate(rotation),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    categories.forEach { category ->
                        val checked = category in selection
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled, role = Role.Checkbox) { onToggle(category, !checked) }
                                .padding(start = 16.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                                Text(stringResource(category.label()), style = MaterialTheme.typography.bodyLarge)
                                previews[category]?.let { preview ->
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun MetadataCategory.label(): StringResource = when (this) {
    MetadataCategory.LOCATION -> Res.string.metadata_category_location
    MetadataCategory.TIMESTAMPS -> Res.string.metadata_category_timestamps
    MetadataCategory.IDENTITY_DEVICE -> Res.string.metadata_category_device
    MetadataCategory.PEOPLE -> Res.string.metadata_category_people
    MetadataCategory.DESCRIPTION_AUTHORSHIP -> Res.string.metadata_category_description
    MetadataCategory.CAPTURE_SETTINGS -> Res.string.metadata_category_capture
    MetadataCategory.EDIT_HISTORY -> Res.string.metadata_category_edits
    MetadataCategory.PROVENANCE -> Res.string.metadata_category_provenance
    MetadataCategory.THUMBNAIL_PREVIEW -> Res.string.metadata_category_thumbnail
    // Never removable, so never listed.
    MetadataCategory.COLOR_HDR, MetadataCategory.STRUCTURAL_FUNCTIONAL -> Res.string.metadata_category_description
}

@Composable
private fun MetadataSaveMode.title(): String = when (this) {
    MetadataSaveMode.SAVE_COPY -> stringResource(Res.string.metadata_save_copy)
    MetadataSaveMode.REPLACE_ORIGINAL -> stringResource(Res.string.metadata_replace_original)
}

@Composable
private fun MetadataSaveMode.description(): String = when (this) {
    MetadataSaveMode.SAVE_COPY -> stringResource(Res.string.metadata_save_copy_description)
    MetadataSaveMode.REPLACE_ORIGINAL -> stringResource(Res.string.metadata_replace_original_description)
}

@Composable
private fun MetadataSaveMode.actionTitle(): String = when (this) {
    MetadataSaveMode.SAVE_COPY -> stringResource(Res.string.metadata_save_copy_action)
    MetadataSaveMode.REPLACE_ORIGINAL -> stringResource(Res.string.metadata_replace_original_action)
}
