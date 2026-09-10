package com.dot.gallery.feature_node.domain.model

import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale

@Stable
@Serializable
data class LocationMedia(
    val media: Media,
    val location: String,
    val city: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

internal fun locationLabelKey(location: String): String = location.normalizedLocationPart()

internal fun locationCoordinateKey(latitude: Double?, longitude: Double?): String? {
    if (latitude == null || longitude == null) return null
    val canonicalLatitude = if (latitude == 0.0) 0.0 else latitude
    val canonicalLongitude = if (longitude == 0.0) 0.0 else longitude
    return "${canonicalLatitude.toBits()}/${canonicalLongitude.toBits()}"
}

internal fun LocationMedia.locationIdentityKey(): String {
    if (!city.isNullOrBlank() || !country.isNullOrBlank()) {
        return "name:${locationLabelKey(location)}"
    }
    return locationCoordinateKey(latitude, longitude)
        ?.let { "coordinates:$it" }
        ?: "media:${media.id}"
}

internal fun matchesLocationName(
    candidateCity: String?,
    candidateCountry: String?,
    city: String,
    country: String,
): Boolean {
    val normalizedCity = city.normalizedLocationPart()
    val normalizedCountry = country.normalizedLocationPart()
    if (normalizedCity.isEmpty() && normalizedCountry.isEmpty()) return false
    val candidateLabel = normalizedLocationLabel(candidateCity, candidateCountry)
    val targetLabel = normalizedLocationLabel(city, country)
    if (candidateLabel == targetLabel) return true
    return (normalizedCity.isEmpty() || candidateCity.normalizedLocationPart() == normalizedCity) &&
            (normalizedCountry.isEmpty() || candidateCountry.normalizedLocationPart() == normalizedCountry)
}

internal fun matchesLocationCoordinates(
    candidateLatitude: Double?,
    candidateLongitude: Double?,
    latitude: Double?,
    longitude: Double?,
): Boolean = latitude != null && longitude != null &&
        candidateLatitude == latitude && candidateLongitude == longitude

private fun normalizedLocationLabel(city: String?, country: String?): String =
    listOf(city, country)
        .map { it.normalizedLocationPart() }
        .filter(String::isNotEmpty)
        .joinToString(", ")

private fun String?.normalizedLocationPart(): String {
    val normalized = Normalizer.normalize(orEmpty(), Normalizer.Form.NFKC)
    return buildString(normalized.length) {
        var previousWasSpace = true
        normalized.forEach { character ->
            val isSpace = character.isWhitespace() || Character.isSpaceChar(character)
            if (isSpace) {
                if (!previousWasSpace) append(' ')
            } else {
                append(character)
            }
            previousWasSpace = isSpace
        }
    }.trimEnd().lowercase(Locale.ROOT)
}
