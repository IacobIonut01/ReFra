package com.dot.gallery.feature_node.domain.model

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dot.gallery.feature_node.presentation.util.formattedAddress
import com.dot.gallery.feature_node.presentation.util.getLocation
import com.dot.gallery.feature_node.presentation.util.rememberGeocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Stable
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val location: String
)

@Composable
fun rememberLocationData(
    exifMetadata: MediaMetadata?
): LocationData? {
    val geocoder = rememberGeocoder()
    var locationName by remember { mutableStateOf(exifMetadata?.formattedCords) }
    LaunchedEffect(geocoder, exifMetadata) {
        withContext(Dispatchers.IO) {
            if (exifMetadata?.gpsLongitude != null && exifMetadata.gpsLatitude != null) {
                geocoder?.getLocation(
                    exifMetadata.gpsLatitude,
                    exifMetadata.gpsLongitude
                ) { address ->
                    address?.let {
                        val addressName = it.formattedAddress
                        if (addressName.isNotEmpty()) {
                            locationName = addressName
                        }
                    }
                }
            }
        }
    }
    return remember(exifMetadata, locationName) {
        exifMetadata?.let {
            if (it.gpsLatitude == null || it.gpsLongitude == null) return@let null
            LocationData(
                latitude = it.gpsLatitude,
                longitude = it.gpsLongitude,
                location = locationName ?: "Unknown"
            )
        }
    }
}