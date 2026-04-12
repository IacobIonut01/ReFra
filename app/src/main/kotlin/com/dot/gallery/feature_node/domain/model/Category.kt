/*
 * SPDX-FileCopyrightText: 2023 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.feature_node.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a category for organizing media.
 * Categories can be either system-generated (using CLIP embeddings for semantic matching)
 * or user-created (using text search terms).
 *
 * @param id Unique identifier for the category
 * @param name Display name of the category
 * @param searchTerms Comma-separated search terms used for matching (e.g., "sunset, sunrise, golden hour")
 * @param embedding Optional pre-computed text embedding for the category (for faster matching)
 * @param threshold Minimum similarity score (0-1) for media to be included in this category
 * @param isUserCreated Whether this category was created by the user or auto-generated
 * @param isPinned Whether this category is pinned/favorited by the user
 * @param createdAt Timestamp when the category was created
 * @param updatedAt Timestamp when the category was last updated
 */
@Entity(tableName = "categories")
@Serializable
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val searchTerms: String,
    val embedding: FloatArray? = null,
    val threshold: Float = DEFAULT_THRESHOLD,
    val isUserCreated: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Category

        if (id != other.id) return false
        if (name != other.name) return false
        if (searchTerms != other.searchTerms) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false
        if (threshold != other.threshold) return false
        if (isUserCreated != other.isUserCreated) return false
        if (isPinned != other.isPinned) return false
        if (createdAt != other.createdAt) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + searchTerms.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + threshold.hashCode()
        result = 31 * result + isUserCreated.hashCode()
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.20f
        const val MIN_THRESHOLD = 0.15f
        const val MAX_THRESHOLD = 0.5f

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
