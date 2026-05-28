package com.dot.gallery.feature_node.domain.model

/**
 * Represents a single embedded subtitle / text track inside a video.
 *
 * @param groupIndex       Index of the [TrackGroup] inside the player's current [Tracks].
 * @param trackIndex       Index of the track inside that group.
 * @param label            Human-readable label (e.g. "English", "日本語").
 * @param language         BCP-47 language tag when available, otherwise `null`.
 * @param isSelected       Whether this track is currently selected for rendering.
 * @param isManuallyAdded  Whether this track was added manually via external file.
 * @param manualIndex      Index into the list of manually-added subtitle configurations (for removal).
 */
data class SubtitleTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val isSelected: Boolean = false,
    val isManuallyAdded: Boolean = false,
    val manualIndex: Int = -1
)
