package com.dot.gallery.feature_node.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "image_embeddings")
@Serializable
data class ImageEmbedding(
    @PrimaryKey(autoGenerate = false)
    val id: Long,
    val date: Long,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ImageEmbedding

        if (id != other.id) return false
        if (date != other.date) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + date.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}