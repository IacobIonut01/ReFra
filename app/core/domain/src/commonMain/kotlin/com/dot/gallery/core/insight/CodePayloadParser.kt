/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

/**
 * Parses decoded code text into a [CodePayload]. Pure Kotlin and side-effect free; anything it
 * does not recognise becomes [CodePayload.Text], so no input can fail.
 */
object CodePayloadParser {

    fun parse(text: String, format: CodeFormat): CodePayload {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return CodePayload.Text(text)
        if (format.isRetailProduct && trimmed.all { it.isDigit() }) return CodePayload.Product(trimmed)
        val upper = trimmed.take(16).uppercase()
        return when {
            upper.startsWith("HTTP://") || upper.startsWith("HTTPS://") -> parseUrl(trimmed)
            upper.startsWith("WWW.") && trimmed.none { it.isWhitespace() } -> parseUrl("https://$trimmed")
            upper.startsWith("WIFI:") -> parseWifi(trimmed)
            upper.startsWith("MECARD:") -> parseMeCard(trimmed)
            upper.startsWith("BEGIN:VCARD") -> parseVCard(trimmed)
            upper.startsWith("BEGIN:VEVENT") || upper.startsWith("BEGIN:VCALENDAR") -> parseEvent(trimmed)
            upper.startsWith("MAILTO:") -> parseMailto(trimmed)
            upper.startsWith("MATMSG:") -> parseMatMsg(trimmed)
            upper.startsWith("TEL:") -> phone(trimmed.substring(4))
            upper.startsWith("SMSTO:") || upper.startsWith("MMSTO:") -> parseSmsTo(trimmed)
            upper.startsWith("SMS:") -> parseSmsUri(trimmed)
            upper.startsWith("GEO:") -> parseGeo(trimmed)
            else -> null
        } ?: CodePayload.Text(text)
    }

    /** A short value for the chip: host, network name, contact name, number. */
    fun summary(payload: CodePayload): String = when (payload) {
        is CodePayload.Url -> payload.host
        is CodePayload.Wifi -> payload.ssid
        is CodePayload.Contact -> payload.name ?: payload.organization ?: payload.phones.firstOrNull()
            ?: payload.emails.firstOrNull() ?: ""
        is CodePayload.Email -> payload.to
        is CodePayload.Phone -> payload.number
        is CodePayload.Sms -> payload.number
        is CodePayload.Geo -> payload.query ?: "${payload.latitude.short()}, ${payload.longitude.short()}"
        is CodePayload.Event -> payload.summary ?: payload.location ?: ""
        is CodePayload.Product -> payload.code
        is CodePayload.Text -> payload.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun Double.short(): String {
        val scaled = kotlin.math.round(this * 10_000.0) / 10_000.0
        return scaled.toString()
    }

    // ---- URL ----

    private fun parseUrl(url: String): CodePayload? {
        if (url.any { it.isWhitespace() }) return null
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        val rest = url.substring(schemeEnd + 3)
        val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
        val hostPort = authority.substringAfterLast('@')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringBefore(']') + "]"
        } else {
            hostPort.substringBefore(':')
        }.lowercase()
        if (host.isEmpty()) return null
        val international = host.split('.').any { it.startsWith("xn--") } || host.any { it.code > 127 }
        return CodePayload.Url(
            url = url,
            host = host,
            isSecure = scheme == "https",
            isInternationalHost = international,
        )
    }

    // ---- Wi-Fi: WIFI:T:WPA;S:name;P:pass;H:true;; ----

    private fun parseWifi(text: String): CodePayload? {
        val fields = splitEscaped(text.substring(5))
        var ssid: String? = null
        var password: String? = null
        var type: String? = null
        var hidden = false
        for ((key, value) in fields) {
            when (key.uppercase()) {
                "S" -> ssid = value
                "P" -> password = value
                "T" -> type = value
                "H" -> hidden = value.equals("true", ignoreCase = true)
            }
        }
        val name = ssid?.takeIf { it.isNotEmpty() } ?: return null
        val security = when (type?.uppercase()) {
            null, "", "NOPASS" -> if (password.isNullOrEmpty()) WifiSecurity.OPEN else WifiSecurity.WPA
            "WEP" -> WifiSecurity.WEP
            "SAE", "WPA3" -> WifiSecurity.SAE
            else -> WifiSecurity.WPA
        }
        return CodePayload.Wifi(
            ssid = name,
            password = password?.takeIf { it.isNotEmpty() && security != WifiSecurity.OPEN },
            security = security,
            hidden = hidden,
        )
    }

    /**
     * Splits `K:v;K:v;;` fields, honouring backslash escapes (`\;`, `\,`, `\:`, `\\`, `\"`) as
     * the ZXing Wi-Fi and MECARD conventions define them.
     */
    internal fun splitEscaped(body: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val current = StringBuilder()
        var key: String? = null
        var i = 0
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    current.append(body[i + 1])
                    i += 2
                    continue
                }
                c == ':' && key == null -> {
                    key = current.toString()
                    current.clear()
                }
                c == ';' -> {
                    if (key != null) out += key to current.toString()
                    key = null
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        if (key != null) out += key to current.toString()
        return out
    }

    // ---- Contacts ----

    private fun parseMeCard(text: String): CodePayload? {
        val fields = splitEscaped(text.substring(7))
        if (fields.isEmpty()) return null
        var name: String? = null
        var org: String? = null
        var address: String? = null
        var note: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val urls = mutableListOf<String>()
        for ((key, value) in fields) {
            if (value.isBlank()) continue
            when (key.uppercase()) {
                "N" -> name = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .let { parts -> if (parts.size == 2) "${parts[1]} ${parts[0]}" else parts.joinToString(" ") }
                "ORG" -> org = value
                "TEL" -> phones += value
                "EMAIL" -> emails += value
                "URL" -> urls += value
                "ADR" -> address = value
                "NOTE" -> note = value
            }
        }
        return contactOrNull(name, org, phones, emails, urls, address, note)
    }

    private fun parseVCard(text: String): CodePayload? {
        var formatted: String? = null
        var structured: String? = null
        var org: String? = null
        var address: String? = null
        var note: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()
        val urls = mutableListOf<String>()
        for ((name, value) in contentLines(text)) {
            if (value.isBlank()) continue
            when (name) {
                "FN" -> formatted = value
                "N" -> structured = value.split(';').let { p ->
                    listOfNotNull(p.getOrNull(1), p.getOrNull(0)).filter { it.isNotBlank() }.joinToString(" ")
                }
                "ORG" -> org = value.replace(';', ' ').trim()
                "TEL" -> phones += value.removePrefix("tel:")
                "EMAIL" -> emails += value
                "URL" -> urls += value
                "ADR" -> address = value.split(';').filter { it.isNotBlank() }.joinToString(", ")
                "NOTE" -> note = value
            }
        }
        return contactOrNull(formatted ?: structured, org, phones, emails, urls, address, note)
    }

    private fun contactOrNull(
        name: String?,
        org: String?,
        phones: List<String>,
        emails: List<String>,
        urls: List<String>,
        address: String?,
        note: String?,
    ): CodePayload? {
        if (name.isNullOrBlank() && org.isNullOrBlank() && phones.isEmpty() && emails.isEmpty()) return null
        return CodePayload.Contact(
            name = name?.takeIf { it.isNotBlank() },
            organization = org?.takeIf { it.isNotBlank() },
            phones = phones,
            emails = emails,
            urls = urls,
            address = address,
            note = note,
        )
    }

    /**
     * Unfolds iCalendar/vCard content lines (a line starting with a space or tab continues the
     * previous one) and yields upper-cased property names without parameters, with values
     * unescaped.
     */
    internal fun contentLines(text: String): List<Pair<String, String>> {
        val unfolded = mutableListOf<StringBuilder>()
        for (raw in text.split("\r\n", "\n", "\r")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && unfolded.isNotEmpty()) {
                unfolded.last().append(raw, 1, raw.length)
            } else {
                unfolded += StringBuilder(raw)
            }
        }
        return unfolded.mapNotNull { line ->
            val s = line.toString()
            val colon = s.indexOf(':')
            if (colon <= 0) return@mapNotNull null
            val name = s.substring(0, colon).substringBefore(';').substringAfterLast('.').uppercase()
            val value = s.substring(colon + 1)
                .replace("\\n", "\n").replace("\\N", "\n")
                .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
            name to value
        }
    }

    // ---- Events ----

    private fun parseEvent(text: String): CodePayload? {
        var summary: String? = null
        var start: String? = null
        var end: String? = null
        var location: String? = null
        var description: String? = null
        var inEvent = !text.trimStart().uppercase().startsWith("BEGIN:VCALENDAR")
        for ((name, value) in contentLines(text)) {
            if (name == "BEGIN" && value.equals("VEVENT", ignoreCase = true)) inEvent = true
            if (!inEvent) continue
            when (name) {
                "SUMMARY" -> summary = value
                "DTSTART" -> start = value
                "DTEND" -> end = value
                "LOCATION" -> location = value
                "DESCRIPTION" -> description = value
                "END" -> if (value.equals("VEVENT", ignoreCase = true)) inEvent = false
            }
        }
        if (summary == null && start == null) return null
        return CodePayload.Event(summary, start, end, location, description)
    }

    // ---- Email, phone, SMS ----

    private fun parseMailto(text: String): CodePayload? {
        val body = text.substring(7)
        val to = percentDecode(body.substringBefore('?')).trim()
        val query = queryParams(body.substringAfter('?', ""))
        val recipient = to.ifEmpty { query["to"].orEmpty() }
        if (recipient.isEmpty()) return null
        return CodePayload.Email(recipient, query["subject"], query["body"])
    }

    private fun parseMatMsg(text: String): CodePayload? {
        val fields = splitEscaped(text.substring(7)).associate { (k, v) -> k.uppercase() to v }
        val to = fields["TO"]?.trim().orEmpty()
        if (to.isEmpty()) return null
        return CodePayload.Email(to, fields["SUB"]?.takeIf { it.isNotEmpty() }, fields["BODY"]?.takeIf { it.isNotEmpty() })
    }

    private fun phone(raw: String): CodePayload? {
        val number = percentDecode(raw).trim()
        return if (number.any { it.isDigit() }) CodePayload.Phone(number) else null
    }

    private fun parseSmsTo(text: String): CodePayload? {
        val body = text.substring(6)
        val number = body.substringBefore(':').trim()
        if (number.none { it.isDigit() }) return null
        return CodePayload.Sms(number, body.substringAfter(':', "").takeIf { it.isNotEmpty() })
    }

    private fun parseSmsUri(text: String): CodePayload? {
        val body = text.substring(4)
        val number = percentDecode(body.substringBefore('?')).trim()
        if (number.none { it.isDigit() }) return null
        return CodePayload.Sms(number, queryParams(body.substringAfter('?', ""))["body"])
    }

    // ---- Geo: geo:lat,lon[,alt][;crs=..][?q=label] ----

    private fun parseGeo(text: String): CodePayload? {
        val body = text.substring(4)
        val coords = body.substringBefore('?').substringBefore(';').split(',')
        val lat = coords.getOrNull(0)?.trim()?.toDoubleOrNull() ?: return null
        val lon = coords.getOrNull(1)?.trim()?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val query = queryParams(body.substringAfter('?', ""))["q"]?.takeIf { it.isNotBlank() }
        return CodePayload.Geo(lat, lon, query)
    }

    // ---- helpers ----

    private fun queryParams(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        return query.split('&').mapNotNull { part ->
            val key = part.substringBefore('=').lowercase()
            if (key.isEmpty()) null else key to percentDecode(part.substringAfter('=', "").replace('+', ' '))
        }.toMap()
    }

    internal fun percentDecode(value: String): String {
        if ('%' !in value) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '%' && i + 2 < value.length) {
                val hex = value.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) {
                    bytes += hex.toByte()
                    i += 3
                    continue
                }
            }
            // Copy the run up to the next '%' in one go so surrogate pairs stay together.
            var end = value.indexOf('%', i + 1)
            if (end < 0) end = value.length
            value.substring(i, end).encodeToByteArray().forEach { bytes += it }
            i = end
        }
        return bytes.toByteArray().decodeToString()
    }
}
