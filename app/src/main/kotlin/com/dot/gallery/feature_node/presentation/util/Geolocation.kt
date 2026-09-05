package com.dot.gallery.feature_node.presentation.util

import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.text.isDigitsOnly

@Composable
fun rememberGeocoder(): Geocoder? {
    val geocoder = Geocoder(LocalContext.current)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && Geocoder.isPresent())
        geocoder else null
}

fun Geocoder.getLocation(lat: Double, long: Double, onLocationFound: (Address?) -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getFromLocation(
            /* latitude = */ lat, /* longitude = */ long, /* maxResults = */ 1
        ) {
            if (it.isEmpty()) onLocationFound(null)
            else onLocationFound(it.first())
        }
    } else {
        onLocationFound(null)
    }
}

val Address.formattedAddress: String get() {
    var address = ""
    if (!featureName.isNullOrBlank() && !featureName.isDigitsOnly()) address += featureName
    else if (!subLocality.isNullOrBlank()) address += subLocality
    if (!locality.isNullOrBlank()) {
        address += if (address.isEmpty()) locality
        else ", $locality"
    }
    if (!countryName.isNullOrBlank()) {
        address += if (address.isEmpty()) countryName
        else ", $countryName"
    }

    return address
}

val Address.locationGroupName: String?
    get() = sequenceOf(locality, subAdminArea, adminArea, subLocality, formattedAddress)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()

val Address.locationTag: String get() =
    if (!featureName.isNullOrBlank() && !featureName.isDigitsOnly()) featureName
    else if (!subLocality.isNullOrBlank()) subLocality
    else locality