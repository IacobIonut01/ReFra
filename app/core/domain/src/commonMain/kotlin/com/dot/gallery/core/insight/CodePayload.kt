/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import androidx.compose.runtime.Immutable

/** What a decoded code means, so the viewer can offer the right actions. */
@Immutable
sealed interface CodePayload {

    /** An http(s) address. [isSecure] is false for plain http. */
    data class Url(
        val url: String,
        val host: String,
        val isSecure: Boolean,
        /** The host contains punycode or non-ASCII characters, which can imitate another site. */
        val isInternationalHost: Boolean,
    ) : CodePayload

    data class Wifi(
        val ssid: String,
        val password: String?,
        val security: WifiSecurity,
        val hidden: Boolean,
    ) : CodePayload

    data class Contact(
        val name: String?,
        val organization: String?,
        val phones: List<String>,
        val emails: List<String>,
        val urls: List<String>,
        val address: String?,
        val note: String?,
    ) : CodePayload

    data class Email(val to: String, val subject: String?, val body: String?) : CodePayload

    data class Phone(val number: String) : CodePayload

    data class Sms(val number: String, val body: String?) : CodePayload

    data class Geo(val latitude: Double, val longitude: Double, val query: String?) : CodePayload

    /** A calendar event; dates are kept as written (iCalendar `DTSTART`/`DTEND` values). */
    data class Event(
        val summary: String?,
        val start: String?,
        val end: String?,
        val location: String?,
        val description: String?,
    ) : CodePayload

    /** A retail product number (EAN/UPC). */
    data class Product(val code: String) : CodePayload

    data class Text(val text: String) : CodePayload
}
