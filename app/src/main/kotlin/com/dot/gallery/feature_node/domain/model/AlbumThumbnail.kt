package com.dot.gallery.feature_node.domain.model

import android.net.Uri
import androidx.room.Entity
import com.dot.gallery.feature_node.domain.util.UriSerializer
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "album_thumbnail", primaryKeys = ["albumId"])
data class AlbumThumbnail(
    val albumId: Long,
    @Serializable(with = UriSerializer::class)
    val thumbnailUri: Uri
)
