/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.domain.model

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a category for organizing media.
 * Categories can be either system-generated (using the active search model's
 * embeddings for semantic matching) or user-created (using text search terms).
 *
 * @param id Unique identifier for the category
 * @param name Display name of the category
 * @param searchTerms Comma-separated search terms used for matching (e.g., "sunset, sunrise, golden hour")
 * @param embedding Optional pre-computed text embedding for the category (for faster matching)
 * @param referenceImageIds List of media IDs used as image-to-image reference for matching
 * @param threshold Match sensitivity (0-1): 0 admits the model's noise floor, 1 only
 *   near-exact matches. Model-agnostic — the active [com.dot.gallery.core.ml.SearchModel]
 *   maps it onto its own cosine band before scoring, so the same value stays meaningful
 *   after switching search engines.
 * @param isUserCreated Whether this category was created by the user or auto-generated
 * @param isPinned Whether this category is pinned/favorited by the user
 * @param createdAt Timestamp when the category was created
 * @param updatedAt Timestamp when the category was last updated
 * @param coverMediaId Media the user picked as the cover; null uses the best-scored member
 */
@Entity(tableName = "categories")
@Serializable
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val searchTerms: String,
    val embedding: FloatArray? = null,
    @ColumnInfo(defaultValue = "[]")
    val referenceImageIds: List<Long> = emptyList(),
    val threshold: Float = DEFAULT_THRESHOLD,
    val isUserCreated: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
    val updatedAt: Long = kotlin.time.Clock.System.now().toEpochMilliseconds(),
    @ColumnInfo(defaultValue = "NULL")
    val coverMediaId: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Category) return false

        if (id != other.id) return false
        if (name != other.name) return false
        if (searchTerms != other.searchTerms) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (referenceImageIds != other.referenceImageIds) return false
        if (threshold != other.threshold) return false
        if (isUserCreated != other.isUserCreated) return false
        if (isPinned != other.isPinned) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false
        if (coverMediaId != other.coverMediaId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + searchTerms.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + referenceImageIds.hashCode()
        result = 31 * result + threshold.hashCode()
        result = 31 * result + isUserCreated.hashCode()
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + (coverMediaId?.hashCode() ?: 0)
        return result
    }

    companion object {
        // `threshold` is a normalized sensitivity, not a cosine score: each search
        // engine maps it onto its own similarity band (SearchModel.thresholdFromSensitivity),
        // which keeps one stored value meaningful across CLIP and EmbeddingGemma 2.
        // 0.25 gives EG2's 0.60 operating point and a mid-low CLIP cutoff (~0.31).
        const val DEFAULT_THRESHOLD = 0.25f
        const val MIN_THRESHOLD = 0.0f
        const val MAX_THRESHOLD = 1.0f

        // Default system categories
        val DEFAULT_CATEGORIES = listOf(
            Category(
                name = "Nature",
                searchTerms = "nature, landscape, forest, trees, plants, flowers, garden, outdoor scenery"
            ),
            Category(
                name = "Animals",
                searchTerms = "animals, pets, dogs, cats, wildlife, birds, fish, zoo"
            ),
            Category(
                name = "People",
                searchTerms = "people, portrait, selfie, group photo, family, friends, faces"
            ),
            Category(
                name = "Food",
                searchTerms = "food, meal, dish, cooking, restaurant, cuisine, eating, dinner, lunch"
            ),
            Category(
                name = "Travel",
                searchTerms = "travel, vacation, trip, tourism, landmark, monument, sightseeing, adventure"
            ),
            Category(
                name = "Architecture",
                searchTerms = "architecture, building, house, city, urban, skyline, structure, interior design"
            ),
            Category(
                name = "Art",
                searchTerms = "art, painting, drawing, sculpture, museum, gallery, creative, artistic"
            ),
            Category(
                name = "Sports",
                searchTerms = "sports, fitness, exercise, gym, athletics, game, match, competition"
            ),
            Category(
                name = "Events",
                searchTerms = "event, party, celebration, wedding, birthday, concert, festival, gathering"
            ),
            Category(
                name = "Documents",
                searchTerms = "document, text, paper, screenshot, receipt, letter, form, certificate"
            ),
            Category(
                name = "Vehicles",
                searchTerms = "car, vehicle, motorcycle, bike, transport, automobile, truck, boat"
            ),
            Category(
                name = "Night",
                searchTerms = "night, dark, stars, moon, nighttime, evening, lights, city at night"
            ),
            Category(
                name = "Beach",
                searchTerms = "beach, ocean, sea, coast, sand, waves, tropical, seaside, summer"
            ),
            Category(
                name = "Mountains",
                searchTerms = "mountain, hiking, peak, summit, alpine, hills, climbing, trail"
            ),
            Category(
                name = "Sunset",
                searchTerms = "sunset, sunrise, golden hour, sky, dusk, dawn, colorful sky"
            )
        )
    }
}
