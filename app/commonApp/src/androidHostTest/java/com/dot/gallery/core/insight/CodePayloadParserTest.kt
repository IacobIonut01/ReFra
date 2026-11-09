/*
 * SPDX-FileCopyrightText: 2023-2026 IacobIacob01
 * SPDX-License-Identifier: Apache-2.0
 */

package com.dot.gallery.core.insight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodePayloadParserTest {

    private fun parse(text: String, format: CodeFormat = CodeFormat.QR_CODE) = CodePayloadParser.parse(text, format)

    @Test
    fun httpsUrlKeepsFullAddressAndLowercasesHost() {
        val payload = parse("https://CanalNights.example/programme?from=poster") as CodePayload.Url
        assertEquals("https://CanalNights.example/programme?from=poster", payload.url)
        assertEquals("canalnights.example", payload.host)
        assertTrue(payload.isSecure)
        assertFalse(payload.isInternationalHost)
    }

    @Test
    fun plainHttpIsNotSecure() {
        val payload = parse("http://user@example.org:8080/path") as CodePayload.Url
        assertEquals("example.org", payload.host)
        assertFalse(payload.isSecure)
    }

    @Test
    fun punycodeAndNonAsciiHostsAreFlagged() {
        assertTrue((parse("https://xn--pple-43d.com/") as CodePayload.Url).isInternationalHost)
        assertTrue((parse("https://аpple.com/") as CodePayload.Url).isInternationalHost)
    }

    @Test
    fun bareWwwBecomesHttps() {
        val payload = parse("www.example.com/a") as CodePayload.Url
        assertEquals("https://www.example.com/a", payload.url)
    }

    @Test
    fun urlWithWhitespaceIsText() {
        assertTrue(parse("https://example.com and more") is CodePayload.Text)
    }

    @Test
    fun otherSchemesStayText() {
        // intent: and javascript: must never become something the sheet can open.
        assertTrue(parse("intent://scan/#Intent;scheme=zxing;end") is CodePayload.Text)
        assertTrue(parse("javascript:alert(1)") is CodePayload.Text)
    }

    @Test
    fun wifiWithEscapes() {
        val payload = parse("WIFI:T:WPA;S:Ponte\\;Guest;P:pa\\:ss\\\\word;H:true;;") as CodePayload.Wifi
        assertEquals("Ponte;Guest", payload.ssid)
        assertEquals("pa:ss\\word", payload.password)
        assertEquals(WifiSecurity.WPA, payload.security)
        assertTrue(payload.hidden)
    }

    @Test
    fun wifiFieldOrderAndCaseDoNotMatter() {
        val payload = parse("wifi:s:Home;t:WEP;p:12345;;") as CodePayload.Wifi
        assertEquals("Home", payload.ssid)
        assertEquals(WifiSecurity.WEP, payload.security)
        assertEquals("12345", payload.password)
    }

    @Test
    fun openWifiHasNoPassword() {
        val payload = parse("WIFI:T:nopass;S:Cafe;P:;;") as CodePayload.Wifi
        assertEquals(WifiSecurity.OPEN, payload.security)
        assertNull(payload.password)
    }

    @Test
    fun wpa3IsSae() {
        assertEquals(WifiSecurity.SAE, (parse("WIFI:T:SAE;S:Lab;P:secret;;") as CodePayload.Wifi).security)
    }

    @Test
    fun wifiWithoutSsidIsText() {
        assertTrue(parse("WIFI:T:WPA;P:secret;;") is CodePayload.Text)
    }

    @Test
    fun meCard() {
        val payload = parse("MECARD:N:Doe,Jane;TEL:+15550100;EMAIL:jane@example.com;ORG:Acme;;") as CodePayload.Contact
        assertEquals("Jane Doe", payload.name)
        assertEquals(listOf("+15550100"), payload.phones)
        assertEquals(listOf("jane@example.com"), payload.emails)
        assertEquals("Acme", payload.organization)
    }

    @Test
    fun vCardWithFoldedLinesAndParameters() {
        val text = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Doe;Jane;;;\r\nFN:Jane\r\n  Doe\r\n" +
            "TEL;TYPE=CELL:+15550100\r\nEMAIL;TYPE=WORK:jane@example.com\r\n" +
            "ADR;TYPE=HOME:;;1 Main St;Springfield;;12345;USA\r\nNOTE:Line\\nTwo\r\nEND:VCARD"
        val payload = parse(text) as CodePayload.Contact
        assertEquals("Jane Doe", payload.name)
        assertEquals(listOf("+15550100"), payload.phones)
        assertEquals("1 Main St, Springfield, 12345, USA", payload.address)
        assertEquals("Line\nTwo", payload.note)
    }

    @Test
    fun mailtoWithQuery() {
        val payload = parse("mailto:info@example.com?subject=Hello%20there&body=Hi+all") as CodePayload.Email
        assertEquals("info@example.com", payload.to)
        assertEquals("Hello there", payload.subject)
        assertEquals("Hi all", payload.body)
    }

    @Test
    fun matMsg() {
        val payload = parse("MATMSG:TO:a@b.c;SUB:Subj;BODY:Text;;") as CodePayload.Email
        assertEquals("a@b.c", payload.to)
        assertEquals("Subj", payload.subject)
        assertEquals("Text", payload.body)
    }

    @Test
    fun telAndSms() {
        assertEquals("+1 555 0100", (parse("tel:+1%20555%200100") as CodePayload.Phone).number)
        val smsto = parse("SMSTO:+15550100:Hello: world") as CodePayload.Sms
        assertEquals("+15550100", smsto.number)
        assertEquals("Hello: world", smsto.body)
        val sms = parse("sms:+15550100?body=Hi%21") as CodePayload.Sms
        assertEquals("Hi!", sms.body)
    }

    @Test
    fun geoWithLabel() {
        val payload = parse("geo:45.4371,12.3326?q=Rialto") as CodePayload.Geo
        assertEquals(45.4371, payload.latitude, 1e-9)
        assertEquals(12.3326, payload.longitude, 1e-9)
        assertEquals("Rialto", payload.query)
    }

    @Test
    fun geoOutOfRangeIsText() {
        assertTrue(parse("geo:95,10") is CodePayload.Text)
    }

    @Test
    fun eventInsideCalendar() {
        val text = "BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:Canal Nights\nDTSTART:20260814T203000\n" +
            "DTEND:20260814T230000\nLOCATION:Venice\nEND:VEVENT\nEND:VCALENDAR"
        val payload = parse(text) as CodePayload.Event
        assertEquals("Canal Nights", payload.summary)
        assertEquals("20260814T203000", payload.start)
        assertEquals("Venice", payload.location)
    }

    @Test
    fun retailFormatsGiveProducts() {
        assertEquals(CodePayload.Product("4006381333931"), parse("4006381333931", CodeFormat.EAN_13))
        // The same digits in a QR code are plain text.
        assertTrue(parse("4006381333931", CodeFormat.QR_CODE) is CodePayload.Text)
    }

    @Test
    fun anythingElseIsTextAndKeepsOriginal() {
        val payload = parse("  hello\nworld  ") as CodePayload.Text
        assertEquals("  hello\nworld  ", payload.text)
        assertEquals("hello", CodePayloadParser.summary(payload))
    }

    @Test
    fun percentDecodingKeepsMultiByteCharacters() {
        val payload = parse("mailto:a@b.c?subject=caf%C3%A9%20😀") as CodePayload.Email
        assertEquals("café 😀", payload.subject)
    }

    @Test
    fun summariesAreShortValues() {
        assertEquals("canalnights.example", CodePayloadParser.summary(parse("https://canalnights.example/x")))
        assertEquals("Ponte Guest", CodePayloadParser.summary(parse("WIFI:S:Ponte Guest;T:WPA;P:x;;")))
        assertEquals("Rialto", CodePayloadParser.summary(parse("geo:45.4,12.3?q=Rialto")))
    }
}
