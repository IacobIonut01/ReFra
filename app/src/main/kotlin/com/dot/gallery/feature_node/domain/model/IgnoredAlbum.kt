package com.dot.gallery.feature_node.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dot.gallery.feature_node.domain.util.volume
import kotlinx.parcelize.Parcelize

@Entity(tableName = "blacklist")
@Parcelize
@Immutable
data class IgnoredAlbum(
    @PrimaryKey(autoGenerate = false)
    val id: Long,
    val label: String,
    val wildcard: String? = null,
    @ColumnInfo(defaultValue = ALBUMS_ONLY.toString())
    val location: Int = ALBUMS_ONLY,
    @ColumnInfo(defaultValue = "[]")
    val matchedAlbums: List<String> = emptyList()
) : Parcelable {

    private val hiddenInBoth get() = location == ALBUMS_AND_TIMELINE
    val hiddenInAlbums get() = location == ALBUMS_ONLY || hiddenInBoth
    val hiddenInTimeline get() = location == TIMELINE_ONLY || hiddenInBoth

    fun matchesMedia(media: Media): Boolean =
        matches(
            id = media.albumID,
            path = media.path,
            relativePath = media.relativePath,
            volume = media.volume,
            shouldRemove = hiddenInTimeline
        )

    fun matchesAlbum(album: Album): Boolean =
        matches(
            id = album.id,
            path = album.pathToThumbnail,
            relativePath = album.relativePath,
            volume = album.volume,
            shouldRemove = hiddenInAlbums
        )

    private fun matches(
        id: Long,
        path: String,
        relativePath: String,
        volume: String,
        shouldRemove: Boolean
    ): Boolean {
        val matchesId = this.id == id
        if (matchesId) return shouldRemove
        val regex = wildcard?.toRegex()
        return regex?.let {
            path.matches(it) || relativePath.matches(it) || volume.matches(it)
        } ?: false
    }

    companion object {
        const val ALBUMS_ONLY = 0
        const val TIMELINE_ONLY = 1
        const val ALBUMS_AND_TIMELINE = 2
    }
}

fun Regex.matchesAlbum(album: Album): Boolean {
    return album.pathToThumbnail.matches(this) || album.relativePath.matches(this) || album.volume.matches(this)
}
fun IgnoredAlbum.shouldIgnore(media: Media, albumId: Long = -1L) =
    matchesMedia(media) && (hiddenInTimeline && albumId == -1L || hiddenInAlbums && albumId != -1L)
