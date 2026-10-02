/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.cloud.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "detected_faces",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["personId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class DetectedFaceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val mediaId: Long,
    val personId: String? = null,
    val embedding: ByteArray? = null,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val confidence: Float = 0f,
    val timestamp: Long = 0L,
    @ColumnInfo(defaultValue = "''")
    val resultRevision: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DetectedFaceEntity) return false
        return id == other.id && mediaId == other.mediaId && personId == other.personId
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + mediaId.hashCode()
        result = 31 * result + (personId?.hashCode() ?: 0)
        return result
    }
}

@Entity(
    tableName = "face_clusters",
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FaceClusterEntity(
    @PrimaryKey
    val personId: String,
    val centroid: FloatArray,
    val faceCount: Int,
    val updatedAt: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FaceClusterEntity) return false
        return personId == other.personId && centroid.contentEquals(other.centroid) &&
            faceCount == other.faceCount && updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = personId.hashCode()
        result = 31 * result + centroid.contentHashCode()
        result = 31 * result + faceCount
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

/**
 * "This media does not contain this person" — a user assertion recorded when a photo is
 * removed from a person. Kept separate from `detected_faces` so it survives re-detection
 * (full-refresh scans delete face rows); the face indexers skip clusters excluded for the
 * media being clustered instead of re-adding the face to the same person.
 */
@Entity(
    tableName = "face_exclusions",
    primaryKeys = ["mediaId", "personId"],
    indices = [Index(value = ["personId"])],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FaceExclusionEntity(
    val mediaId: Long,
    val personId: String,
    val createdAt: Long = 0L
)

enum class FaceLinkKind(val storedValue: String) {
    INCLUDE("include"),
    EXCLUDE("exclude");

    companion object {
        fun fromStored(value: String): FaceLinkKind =
            entries.first { it.storedValue == value }
    }
}

/**
 * A face-scoped user assertion that survives re-detection and batch re-clustering —
 * the durable record behind manual merges ("these faces ARE this person") and
 * face-level / person-pair corrections ("this face is NOT that person").
 *
 * The asserted face is identified by its media + bounding box, matched to live
 * `detected_faces` rows by IoU at apply time (face ids regenerate on re-detection,
 * so a foreign key to them would be useless).
 */
@Entity(
    tableName = "face_links",
    indices = [
        Index(value = ["personId"]),
        Index(value = ["mediaId", "left", "top", "right", "bottom", "personId", "kind"], unique = true)
    ],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class FaceLinkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val mediaId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val personId: String,
    val kind: FaceLinkKind,
    val createdAt: Long = 0L
)

/**
 * "Never cluster this face into any person" — written when a person group is
 * deleted (#1262). Keyed by media + bounding box and matched to live
 * `detected_faces` rows by IoU, exactly like [FaceLinkEntity], so it survives
 * face re-detection and carries no person FK (the person is gone by design).
 */
@Entity(
    tableName = "face_suppressions",
    primaryKeys = ["mediaId", "left", "top", "right", "bottom"]
)
data class FaceSuppressionEntity(
    val mediaId: Long,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val createdAt: Long = 0L
)
